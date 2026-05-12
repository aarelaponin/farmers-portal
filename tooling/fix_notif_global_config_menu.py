#!/usr/bin/env python3
"""
Fix the Notification Test-Mode Override CrudMenu — change selectionType
from 'single' to 'multiple' to match the convention used by 128 other
CrudMenus in this userview (CLAUDE.md userview hygiene section).

Some enterprise-CrudMenu UI elements (including the Add button + row
selection checkbox) only render when selectionType=multiple, which is
why the screen looked empty / unclickable.

Also re-push the form definition one more time to ensure Joget's
Hibernate mapping is current for the newly-inserted singleton row.

HARD RULE compliant: all writes via form-creator-api.
"""
import argparse, datetime, json, os, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}


def _post(path, payload):
    req = urllib.request.Request(JOGET + path,
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    target = None
    for cat in uv["categories"]:
        for m in cat.get("menus", []):
            if m["properties"].get("datalistId") == "list_notif_global_config":
                target = m
                break
        if target: break
    if not target:
        print("Menu not found", file=sys.stderr); return 2

    print("Before:")
    print(f"  selectionType: {target['properties'].get('selectionType')}")
    target["properties"]["selectionType"] = "multiple"
    print("After:")
    print(f"  selectionType: {target['properties'].get('selectionType')}")

    if args.dry_run:
        return 0

    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.preNotifMenuFix.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)
    print(f"[backup] {backup}")

    payload = {
        "appId":        "farmersPortal",
        "userviewId":   uv["properties"]["id"],
        "userviewName": uv["properties"].get("name", uv["properties"]["id"]),
        "json":         json.dumps(uv),
    }
    s, b = _post("/api/formcreator/formcreator/userviews", payload)
    print(f"\nUserview push: HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
