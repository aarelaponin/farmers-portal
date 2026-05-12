#!/usr/bin/env python3
"""
Rewrite the 5 dashboard HtmlPage menus to fetch via the new ROLE_USER-
accessible API Builder endpoint instead of the admin-only data JSON URL.

Why this exists. Joget's stock /jw/web/json/data/list/{appId}/{listId} is
gated by Spring Security's /web/json/** rule (ROLE_ADMIN, ROLE_APPADMIN
only — applicationContext.xml line 124). Non-admin users (district
supervisors, finance officers) get HTTP 403, so the dashboards break for
everyone except admin. The form-creator-api build-024 adds a companion
endpoint at /jw/api/formcreator/formcreator/data/list which is gated by
the /api/** rule (ROLE_USER + ROLE_ANONYMOUS allowed) and authenticated
via api_id + api_key headers — same data, accessible to non-admins.

This script walks the live userview, finds every HtmlPage menu whose
htmlBody contains the legacy URL pattern, rewrites those fetch calls to
the new endpoint, and pushes the userview back via form-creator-api.

Usage:
    tooling/rewrite_dashboard_urls.py --dry-run
    tooling/rewrite_dashboard_urls.py --apply
"""

import argparse
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.request

import psycopg2

PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb", user="jogetadmin",
    password="Joget@DB#2026!", port=5432, sslmode="require",
)
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = "a5af1181f77b4a62b481725b6410e965"

LEGACY_BASE = "/jw/web/json/data/list/"
NEW_BASE    = "/jw/api/formcreator/formcreator/data/list"


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
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


# ---- Body rewriter -------------------------------------------------------
# We rewrite at the source level — the dashboards build URLs like:
#   var BASE = '/jw/web/json/data/list/' + APP + '/';
#   fetch(BASE + listId, { credentials: 'same-origin', headers: {Accept: 'application/json'} })
# The new endpoint takes appId + listId as query params and api_id/api_key
# as headers. So we transform:
#   /jw/web/json/data/list/{APP}/{LIST}  ->  /jw/api/formcreator/formcreator/data/list?appId={APP}&listId={LIST}
# and inject the api_id/api_key headers next to the existing Accept header.
#
# The dashboards have multiple shapes (string concat, template literals,
# direct fetch URL). We use targeted regex passes that cover the patterns
# observed in this app's HtmlPage source.

INJECT_HEADERS_LINE = (
    "'api_id': '" + API_ID + "', 'api_key': '" + API_KEY + "'"
)


def rewrite_body(html, app_id="farmersPortal"):
    """Apply URL + envelope-unwrap rewrites. Returns (new_html, changes_count).

    Strategy: put api_id + api_key in the URL query string (API Builder
    accepts both header + query-param auth — see ApiBuilder.java in
    api-builder/apibuilder_plugins/). This avoids needing to inject headers
    blocks into fetches with different shapes (some have headers blocks,
    some don't). One pattern fits all fetch styles.

    For dashboards that already had the JAR-untouched BASE-variable shape
    (Executive Overview), we keep BASE as-is and append auth in Pattern 3.
    """
    if html is None:
        return html, 0
    new = html
    changes = 0

    auth_qs = f"api_id={API_ID}&api_key={API_KEY}"

    # Pattern 1: var BASE = '/jw/web/json/data/list/' + APP + '/';
    # Rewrite to the new endpoint base; we'll append ?appId=&listId= in P3.
    p1 = re.compile(
        r"(var\s+BASE\s*=\s*['\"])/jw/web/json/data/list/(['\"])\s*\+\s*(\w+)\s*\+\s*['\"]/['\"]\s*;"
    )
    def repl1(m):
        q1, q2, _ = m.group(1), m.group(2), m.group(3)
        return q1 + NEW_BASE + q2 + ";  // routed via API Builder (ROLE_USER ok)"
    new2, n = p1.subn(repl1, new)
    if n:
        changes += n
        new = new2

    # Pattern 2: literal '/jw/web/json/data/list/farmersPortal/<listId>'
    # Rewrite to the new endpoint with auth + listId in the query string.
    p2 = re.compile(
        r"['\"]/jw/web/json/data/list/" + re.escape(app_id) + r"/([\w\-]+)['\"]"
    )
    def repl2(m):
        list_id = m.group(1)
        return (
            f"'{NEW_BASE}?appId={app_id}&listId={list_id}&{auth_qs}'  "
            f"/* routed via API Builder (ROLE_USER ok) */"
        )
    new2, n = p2.subn(repl2, new)
    if n:
        changes += n
        new = new2

    # Pattern 3: BASE + listId  =>  BASE + '?appId=...&listId=' + listId + '&api_id=...&api_key=...'
    # Only rewrites when Pattern 1 fired (marker comment present).
    if "// routed via API Builder" in new:
        p3 = re.compile(r"BASE\s*\+\s*(\w+)\b")
        def repl3(m):
            list_var = m.group(1)
            return (
                f"BASE + '?appId=' + APP + '&listId=' + {list_var} + "
                f"'&{auth_qs}'"
            )
        new2, n = p3.subn(repl3, new)
        if n:
            changes += n
            new = new2

    # Pattern 2b: literal URL already on /jw/api/... but missing api_id/api_key
    # auth (shape we left from a previous push that hadn't added auth yet).
    # Append &api_id=...&api_key=... to the URL.
    p2b = re.compile(
        r"['\"]" + re.escape(NEW_BASE)
        + r"\?appId=" + re.escape(app_id)
        + r"&listId=([\w\-]+)['\"]"
    )
    def repl2b(m):
        list_id = m.group(1)
        return (
            f"'{NEW_BASE}?appId={app_id}&listId={list_id}&{auth_qs}'  "
            f"/* routed via API Builder (ROLE_USER ok) */"
        )
    new2, n = p2b.subn(repl2b, new)
    if n:
        changes += n
        new = new2

    # Pattern 3b: BASE-concat shape that already has ?appId/&listId but
    # missing &api_id/&api_key. Append the auth query params.
    p3b = re.compile(
        r"(BASE\s*\+\s*['\"]\?appId=['\"]\s*\+\s*APP\s*\+\s*['\"]&listId=['\"]\s*\+\s*\w+)\b"
        r"(?!\s*\+\s*['\"]&api_id)"
    )
    def repl3b(m):
        return m.group(1) + " + '&" + auth_qs + "'"
    new2, n = p3b.subn(repl3b, new)
    if n:
        changes += n
        new = new2

    # Pattern 4: API Builder wraps responses in {date, code, message: "<json>"}
    # Unwrap envelope after r.json(). Trigger whenever the new endpoint URL
    # is present anywhere in the body (covers both BASE-variable + literal-
    # URL fetch shapes).
    if NEW_BASE in new and "JSON.parse(env.message)" not in new:
        p4 = re.compile(r"return\s+r\.json\(\)\s*;")
        unwrap = (
            "return r.json().then(function(env) { "
            "return (env && typeof env.message === 'string') "
            "? JSON.parse(env.message) : env; });"
        )
        new2, n = p4.subn(unwrap, new)
        if n:
            changes += n
            new = new2

    return new, changes


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    if args.apply:
        ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        backup = f"_backups/v.preDashboardURLRewrite.{ts}.pretty.json"
        os.makedirs(os.path.dirname(backup), exist_ok=True)
        with open(backup, "w") as f:
            json.dump(uv, f, indent=2)
        print(f"[backup] {backup}")

    total_changes = 0
    affected_menus = []
    for cat in uv["categories"]:
        for menu in cat.get("menus", []):
            cls = menu.get("className", "")
            if "HtmlPage" not in cls:
                continue
            props = menu.get("properties", {})
            label = props.get("label", "")
            # Try common content properties used by HtmlPage.
            for body_key in ("htmlBody", "customHtml", "content"):
                if body_key in props and isinstance(props[body_key], str):
                    new_body, n = rewrite_body(props[body_key])
                    if n:
                        props[body_key] = new_body
                        total_changes += n
                        affected_menus.append((label, body_key, n))

    print(f"\nRewrote {total_changes} URL/header occurrences across "
          f"{len(affected_menus)} menu bodies:")
    for label, key, n in affected_menus:
        print(f"  {label!r:55s}  {key:12s}  +{n}")

    if total_changes == 0:
        print("\nNo dashboards needed rewriting — already on new endpoint, or none found.")
        return 0

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\nPushing via form-creator-api...")
    status, body = push_userview(uv)
    print(f"HTTP {status}: {body[:300]}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
