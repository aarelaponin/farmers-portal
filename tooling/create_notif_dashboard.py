#!/usr/bin/env python3
"""
W2.9 — Notification Dashboard.

Builds two artefacts:
  1) list_notif_dashboard_feed — JdbcDataListBinder returning the last 500
     dispatch rows (one row per send attempt). Fed straight to the dashboard
     JS via /jw/api/formcreator/formcreator/data/list, no aggregation in SQL.
     Keeps the binder shape boring; the dashboard does all roll-ups in JS.
  2) notification_dashboard — HtmlPage menu added under the Admin category
     of the userview. 4 KPI cards (24 h totals + success rate + failed +
     dead-letter), 3 Chart.js charts (status mix 7 d, top events 7 d,
     hourly trend 24 h).

HARD RULE compliant: pushes via form-creator-api + userview API.
"""
import argparse, datetime, json, os, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = "a5af1181f77b4a62b481725b6410e965"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}

# ─────────────────────────────────────────────────────────────────────────
# 1) Feed datalist — last 500 rows, JdbcDataListBinder
# ─────────────────────────────────────────────────────────────────────────
FEED_SQL = """
SELECT
    id                       AS row_id,
    datecreated              AS when_at,
    c_eventCode              AS event_code,
    c_channel                AS channel,
    c_status                 AS status,
    c_backend                AS backend,
    c_intendedRecipientStatus AS intended_status,
    c_testMode               AS test_mode
FROM app_fd_notification_queue
ORDER BY datecreated DESC
LIMIT 500
""".strip()

FEED = {
    "id":   "list_notif_dashboard_feed",
    "name": "Dashboard feed: Notifications (last 500)",
    "useSession":           "false",
    "showPageSizeSelector": "false",
    "pageSize":             500,
    "orderBy":              "when_at",
    "order":                "DESC",
    "binder": {
        "className": "org.joget.plugin.enterprise.JdbcDataListBinder",
        "properties": {
            "jdbcDatasource": "default",
            "primaryKey":     "row_id",
            "sql":            FEED_SQL,
        }
    },
    "columns": [
        {"id":"c1","name":"when_at",         "label":"When"},
        {"id":"c2","name":"event_code",      "label":"Event"},
        {"id":"c3","name":"channel",         "label":"Channel"},
        {"id":"c4","name":"status",          "label":"Status"},
        {"id":"c5","name":"backend",         "label":"Backend"},
        {"id":"c6","name":"intended_status", "label":"Intended Status"},
        {"id":"c7","name":"test_mode",       "label":"Test?"},
    ],
    "filters":    [],
    "rowActions": [],
    "actions":    []
}

# ─────────────────────────────────────────────────────────────────────────
# 2) HtmlPage content
# ─────────────────────────────────────────────────────────────────────────
DASHBOARD_CONTENT = r"""
<style>
  .nd { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #263238; padding: 18px 22px 32px 22px; background: #fafbfc; }
  .nd h1 { font-size: 24px; font-weight: 700; color: #1565C0; margin: 0 0 4px 0; }
  .nd .sub { color: #607D8B; font-size: 13px; margin-bottom: 16px; }
  .nd .stamp { float: right; color: #90A4AE; font-size: 12px; padding-top: 6px; }
  .nd .err { background: #FFEBEE; color: #C62828; padding: 12px 16px; border-radius: 6px; margin: 12px 0; font-size: 13px; }
  .nd .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 22px; }
  .nd .kpi { background: #fff; border-radius: 10px; padding: 16px 18px; border: 1px solid #E0E7EE; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
  .nd .kpi h3 { margin: 0; font-size: 12px; font-weight: 600; color: #607D8B; text-transform: uppercase; letter-spacing: 0.04em; }
  .nd .kpi .v { font-size: 28px; font-weight: 700; margin-top: 6px; color: #1565C0; }
  .nd .kpi .v.ok    { color: #2E7D32; }
  .nd .kpi .v.fail  { color: #C62828; }
  .nd .kpi .v.dead  { color: #6A1B9A; }
  .nd .kpi .note { color: #90A4AE; font-size: 11px; margin-top: 4px; }
  .nd .charts { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
  .nd .chart { background: #fff; border-radius: 10px; padding: 14px 18px; border: 1px solid #E0E7EE; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
  .nd .chart h2 { margin: 0 0 8px 0; font-size: 14px; font-weight: 600; color: #37474F; }
  .nd .chart .canvas-wrap { position: relative; height: 220px; }
  .nd .chart.wide { grid-column: 1 / -1; }
  .nd .chart.wide .canvas-wrap { height: 180px; }
</style>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<div class="nd" id="nd-root">
  <div class="stamp" id="nd-stamp">Loading…</div>
  <h1>Notification Dashboard</h1>
  <div class="sub">Live operational view of email + SMS dispatches from the Farmers Portal. Aggregates over the most recent 500 dispatches.</div>
  <div id="nd-err"></div>
  <div class="kpis">
    <div class="kpi"><h3>Last 24 h — total</h3><div class="v" id="kpi-24h">—</div><div class="note">All channels combined</div></div>
    <div class="kpi"><h3>Last 24 h — success rate</h3><div class="v ok" id="kpi-rate">—</div><div class="note">sent / (sent + failed)</div></div>
    <div class="kpi"><h3>Last 24 h — failed</h3><div class="v fail" id="kpi-fail">—</div><div class="note">retryable failures</div></div>
    <div class="kpi"><h3>Dead-letter (all time)</h3><div class="v dead" id="kpi-dead">—</div><div class="note">terminally buried</div></div>
  </div>
  <div class="charts">
    <div class="chart">
      <h2>Status mix (last 7 days)</h2>
      <div class="canvas-wrap"><canvas id="ch-status"></canvas></div>
    </div>
    <div class="chart">
      <h2>Top events by volume (last 7 days)</h2>
      <div class="canvas-wrap"><canvas id="ch-events"></canvas></div>
    </div>
    <div class="chart wide">
      <h2>Dispatches per hour (last 24 h)</h2>
      <div class="canvas-wrap"><canvas id="ch-hourly"></canvas></div>
    </div>
  </div>
</div>
<script>
(function() {
  var APP  = 'farmersPortal';
  var BASE = '/jw/api/formcreator/formcreator/data/list';
  var LIST = 'list_notif_dashboard_feed';
  function jget(listId) {
    return fetch(BASE + '?appId=' + APP + '&listId=' + listId + '&api_id=__API_ID__&api_key=__API_KEY__',
                 { credentials: 'same-origin',
                   headers: { 'Accept': 'application/json', 'api_id': '__API_ID__', 'api_key': '__API_KEY__' } })
      .then(function(r) {
        if (!r.ok) throw new Error('HTTP ' + r.status + ' on ' + listId);
        return r.json().then(function(env) {
          return (env && typeof env.message === 'string') ? JSON.parse(env.message) : env;
        });
      })
      .then(function(d) { if (d.error) throw new Error('API: ' + JSON.stringify(d.error)); return d.data || []; });
  }
  function showErr(msg) { document.getElementById('nd-err').innerHTML = '<div class="err">' + msg + '</div>'; }

  jget(LIST).then(function(rows) {
    var now = new Date();
    var ms24 = 24*60*60*1000;
    var ms7d = 7 * ms24;

    function rowDate(r) { return new Date((r.when_at || '').replace(' ', 'T') + 'Z'); }
    function within(r, ms) { var d = rowDate(r); return (now - d) <= ms; }

    var rows24 = rows.filter(function(r) { return within(r, ms24); });
    var rows7d = rows.filter(function(r) { return within(r, ms7d); });

    // KPIs
    var sent24    = rows24.filter(function(r){return r.status==='sent';}).length;
    var fail24    = rows24.filter(function(r){return r.status==='failed';}).length;
    var rate      = (sent24 + fail24) > 0 ? Math.round(100 * sent24 / (sent24 + fail24)) : 100;
    var deadAll   = rows.filter(function(r){return r.status==='dead_letter';}).length;
    document.getElementById('kpi-24h').textContent  = rows24.length;
    document.getElementById('kpi-rate').textContent = rate + '%';
    document.getElementById('kpi-fail').textContent = fail24;
    document.getElementById('kpi-dead').textContent = deadAll;

    // Status mix (last 7d) — bar
    var statusCounts = {};
    rows7d.forEach(function(r){ statusCounts[r.status] = (statusCounts[r.status]||0) + 1; });
    var statuses = Object.keys(statusCounts);
    new Chart(document.getElementById('ch-status'), {
      type: 'bar',
      data: { labels: statuses, datasets: [{
        data: statuses.map(function(s){ return statusCounts[s]; }),
        backgroundColor: statuses.map(function(s){
          if (s==='sent') return '#2E7D32';
          if (s==='pending') return '#FBC02D';
          if (s==='failed') return '#C62828';
          if (s==='skipped') return '#90A4AE';
          if (s==='dead_letter') return '#6A1B9A';
          return '#1565C0';
        })
      }] },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } },
                 scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    // Top events (last 7d) — horizontal bar, top 8
    var evCounts = {};
    rows7d.forEach(function(r){ evCounts[r.event_code] = (evCounts[r.event_code]||0) + 1; });
    var topEvents = Object.keys(evCounts)
      .map(function(k){ return {k:k, v:evCounts[k]}; })
      .sort(function(a,b){ return b.v - a.v; })
      .slice(0, 8);
    new Chart(document.getElementById('ch-events'), {
      type: 'bar',
      data: { labels: topEvents.map(function(e){ return e.k; }),
              datasets: [{ data: topEvents.map(function(e){ return e.v; }), backgroundColor: '#1565C0' }] },
      options: { indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                 plugins: { legend: { display: false } },
                 scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    // Hourly trend (last 24h) — line
    var hourBuckets = new Array(24).fill(0);
    rows24.forEach(function(r) {
      var d = rowDate(r);
      var hoursAgo = Math.floor((now - d) / (60*60*1000));
      if (hoursAgo >= 0 && hoursAgo < 24) hourBuckets[23 - hoursAgo]++;
    });
    var hourLabels = [];
    for (var i = 23; i >= 0; i--) {
      var d = new Date(now.getTime() - i*60*60*1000);
      hourLabels.push(('0'+d.getHours()).slice(-2) + ':00');
    }
    new Chart(document.getElementById('ch-hourly'), {
      type: 'line',
      data: { labels: hourLabels, datasets: [{
        label: 'Dispatches', data: hourBuckets,
        fill: true, borderColor: '#1565C0', backgroundColor: 'rgba(21,101,192,0.12)',
        tension: 0.25, pointRadius: 2
      }] },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } },
                 scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    document.getElementById('nd-stamp').textContent =
      'Loaded ' + rows.length + ' rows · ' + now.toISOString().replace('T',' ').slice(0,19) + ' UTC';
  }).catch(function(err) {
    showErr('Could not load dashboard data: ' + err.message);
    document.getElementById('nd-stamp').textContent = 'Load failed';
  });
})();
</script>
""".replace("__API_ID__", API_ID).replace("__API_KEY__", API_KEY)


# ─────────────────────────────────────────────────────────────────────────
def push_datalist():
    payload = {
        "appId":        "farmersPortal",
        "datalistId":   FEED["id"],
        "datalistName": FEED["name"],
        "json":         json.dumps(FEED),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/datalists",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def push_userview(uv):
    payload = {
        "appId":        "farmersPortal",
        "userviewId":   uv["properties"]["id"],
        "userviewName": uv["properties"].get("name", uv["properties"]["id"]),
        "json":         json.dumps(uv),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/userviews",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def add_menu_to_admin_category(uv):
    """Add the HtmlPage menu under the Admin category. Idempotent."""
    target_menu = {
        "className": "org.joget.apps.userview.lib.HtmlPage",
        "properties": {
            "id":       "notification_dashboard",
            "label":    "Notification Dashboard",
            "customId": "notification_dashboard",
            "content":  DASHBOARD_CONTENT,
        }
    }
    for cat in uv["categories"]:
        cp = cat.get("properties", {})
        # Strip HTML tags out of label before comparing — userview labels
        # include FA icon markup like '<i class="fa fa-shield"></i> Admin'.
        import re as _re
        plain = _re.sub(r'<[^>]+>', '', cp.get("label","")).strip().lower()
        if plain == "admin" or "admin" in cp.get("id","").lower():
            menus = cat.setdefault("menus", [])
            for m in menus:
                if m.get("properties", {}).get("id") == "notification_dashboard":
                    print(f"  menu already in '{cp.get('label')}' — replacing content")
                    m["properties"]["content"] = DASHBOARD_CONTENT
                    return True
            menus.append(target_menu)
            print(f"  added 'Notification Dashboard' to category '{cp.get('label')}'")
            return True
    return False


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    if args.dry_run:
        print("Datalist:", FEED["id"], "with", len(FEED["columns"]), "columns")
        print("HtmlPage content:", len(DASHBOARD_CONTENT), "chars")
        print("Will add menu to Admin category.")
        print("Dry-run — not pushing.")
        return 0

    print("Pushing feed datalist...")
    s, b = push_datalist()
    print(f"  HTTP {s}: {b[:150]}")
    if s != 200:
        return 1

    print("Pulling userview...")
    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    if not add_menu_to_admin_category(uv):
        print("  ERROR: no Admin category found in userview")
        return 1

    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.preNotifDashboard.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)
    print(f"  [backup] {backup}")

    print("Pushing userview...")
    s, b = push_userview(uv)
    print(f"  HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
