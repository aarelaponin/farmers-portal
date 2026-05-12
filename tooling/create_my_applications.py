#!/usr/bin/env python3
"""
W3.3 — "My Applications" citizen surface.

Creates:
  1) list_my_applications — JdbcDataListBinder filtered to the current
     citizen's national_id. Shows their applications with lifecycle
     state + outcome columns and a per-row Withdraw action.
  2) "My Applications" menu under the Registration Forms category.

The filter pulls the citizen's NID via #currentUser.firstName# or similar
Joget request-param tokens; safest path is to use a username → NID lookup
binder (which already exists in this app's MM-Configuration). For now,
the list is unfiltered with a national_id text filter at the top so the
citizen can self-identify in dev/UAT; the production fix is a small
session-scoped filter we can add when MAFSN's SSO mapping is finalised.

HARD RULE compliant: pushes via form-creator-api + userview endpoints.
"""
import argparse, datetime, json, os, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = os.environ.get("JOGET_API_KEY", "")
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}

SQL = """
SELECT
    a.id                AS row_id,
    a.datecreated       AS when_at,
    a.c_full_name       AS applicant,
    a.c_national_id     AS national_id,
    a.c_applied_programme AS programme,
    COALESCE(NULLIF(a.c_lifecycleState, ''), 'submitted') AS lifecycle,
    a.c_status          AS outcome,
    a.c_decision_comment AS notes
FROM app_fd_subsidy_app_2025 a
WHERE ('#requestParam.national_id#' = ''
       OR a.c_national_id = '#requestParam.national_id#')
ORDER BY a.datecreated DESC
""".strip()

DATALIST = {
    "id":   "list_my_applications",
    "name": "List: My Applications",
    "useSession":           "false",
    "showPageSizeSelector": "true",
    "pageSize":             20,
    "pageSizeSelectorOptions": "20,50,100",
    "orderBy":              "when_at",
    "order":                "DESC",
    "buttonPosition":       "bothLeft",
    "checkboxPosition":     "left",
    "binder": {
        "className": "org.joget.plugin.enterprise.JdbcDataListBinder",
        "properties": {
            "jdbcDatasource": "default",
            "primaryKey":     "row_id",
            "sql":            SQL
        }
    },
    "columns": [
        {"id":"c_when",     "name":"when_at",     "label":"Submitted",  "sortable":"true",
         "format": {
             "className": "org.joget.plugin.enterprise.DateFormatter",
             "properties": {
                 "format":   "yyyy-MM-dd HH:mm",
                 "rawFormat": "yyyy-MM-dd HH:mm:ss"
             }
         }},
        {"id":"c_app",      "name":"applicant",   "label":"Applicant",  "sortable":"true"},
        {"id":"c_nid",      "name":"national_id", "label":"NID",        "sortable":"true"},
        {"id":"c_prog",     "name":"programme",   "label":"Programme",  "sortable":"true"},
        {"id":"c_lc",       "name":"lifecycle",   "label":"State",      "sortable":"true"},
        {"id":"c_oc",       "name":"outcome",     "label":"Outcome",    "sortable":"true"},
        {"id":"c_notes",    "name":"notes",       "label":"Notes"}
    ],
    "filters": [
        {"id":"f_nid",  "name":"national_id", "label":"My NID",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}}
    ],
    "rowActions": [],
    "actions": [
        {"id":"action_withdraw", "name":"action_withdraw", "label":"Withdraw Selected",
         "className": "global.govstack.regbb.engine.lifecycle.WithdrawApplicationAction",
         "properties": {}}
    ]
}


def push_datalist():
    payload = {
        "appId":        "farmersPortal",
        "datalistId":   DATALIST["id"],
        "datalistName": DATALIST["name"],
        "json":         json.dumps(DATALIST),
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


def add_menu(uv):
    """Add 'My Applications' menu under Registration Forms category."""
    menu = {
        "className": "org.joget.plugin.enterprise.CrudMenu",
        "properties": {
            "id":         "my_applications",
            "customId":   "my_applications",
            "label":      "My Applications",
            "datalistId": "list_my_applications",
            "addFormId":  "",
            "editFormId": "",
            "addLabel":   "",
            "editLabel":  "View",
            "selectionType": "multiple",
            "rowAction":     "edit"
        }
    }
    import re as _re
    for cat in uv["categories"]:
        cp = cat.get("properties", {})
        plain = _re.sub(r'<[^>]+>', '', cp.get("label","")).strip().lower()
        if plain == "registration forms" or "registration" in cp.get("id","").lower():
            menus = cat.setdefault("menus", [])
            for m in menus:
                if m.get("properties", {}).get("id") == "my_applications":
                    print(f"  menu already in '{cp.get('label')}' — nothing to do")
                    return True
            menus.append(menu)
            print(f"  added 'My Applications' to category '{cp.get('label')}'")
            return True
    return False


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    if args.dry_run:
        print("Datalist:", DATALIST["id"], "with", len(DATALIST["columns"]), "columns +",
              len(DATALIST["actions"]), "actions")
        print("Will add menu to Registration Forms category.")
        print("Dry-run — not pushing.")
        return 0

    print("Pushing datalist...")
    s, b = push_datalist()
    print(f"  HTTP {s}: {b[:200]}")
    if s != 200:
        return 1

    print("Pulling userview...")
    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    if not add_menu(uv):
        print("  ERROR: Registration Forms category not found")
        return 1

    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.preMyApps.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)
    print(f"  [backup] {backup}")

    print("Pushing userview...")
    s, b = push_userview(uv)
    print(f"  HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
