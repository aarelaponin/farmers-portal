#!/usr/bin/env python3
"""
Rename the Notification Queue menu from "Notification Queue (Scheduled & Failed)"
to plain "Notification Queue" — now that the form holds ALL notifications
(immediate dispatches too, post-W2.6), the parenthetical is misleading.

Idempotent: no-op if label is already updated.
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

NEW_LABEL = "Notification Queue"


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

    changed = False
    for cat in uv["categories"]:
        for m in cat.get("menus", []):
            if m["properties"].get("datalistId") == "list_notification_queue":
                old = m["properties"].get("label", "")
                if old != NEW_LABEL:
                    print(f"Renaming menu: '{old}' → '{NEW_LABEL}'")
                    m["properties"]["label"] = NEW_LABEL
                    changed = True
                else:
                    print(f"Menu already named '{NEW_LABEL}' — nothing to do.")
                    return 0

    if not changed:
        print("Menu not found")
        return 2

    if args.dry_run:
        print("Dry-run — not pushing.")
        return 0

    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.preRenameNotifMenu.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)
    print(f"[backup] {backup}")

    s, b = push_userview(uv)
    print(f"HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
