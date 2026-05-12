#!/usr/bin/env python3
"""
Remove the Notification Test-Mode Override CrudMenu from the userview.

W2.5 step 4c (May 2026): test-mode override redesigned from a DB-backed
singleton form to JVM system properties (regbb.notif.testMode etc.). The
menu and its underlying form/datalist are now dormant — the dispatcher
no longer reads from app_fd_notif_global_config.

This script removes the menu so operators don't see broken UI.
The form definition + datalist + Postgres table stay in place because
the HARD RULE (CLAUDE.md) forbids raw SQL deletes on Joget-managed
tables. They are inert orphans and can be cleaned up later through
App Composer's "Delete Form" UI if desired.

Idempotent — no-op if the menu is already gone.
"""
import argparse, datetime, json, os, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}


def push_userview(uv):
    payload = {
        "appId": "farmersPortal",
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


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    removed = False
    for cat in uv["categories"]:
        menus = cat.get("menus", [])
        before = len(menus)
        cat["menus"] = [m for m in menus
                        if m.get("properties", {}).get("datalistId") != "list_notif_global_config"]
        if len(cat["menus"]) < before:
            removed = True
            print(f"Removed menu from category '{cat['properties'].get('label')}': "
                  f"{before} → {len(cat['menus'])} menus")

    if not removed:
        print("Menu not present — nothing to do.")
        return 0

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.preRemoveNotifGlobalConfig.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)
    print(f"[backup] {backup}")

    s, b = push_userview(uv)
    print(f"HTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
