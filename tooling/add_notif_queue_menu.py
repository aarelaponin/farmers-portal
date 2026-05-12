#!/usr/bin/env python3
"""
Add a CrudMenu "Notification Queue" under Admin category so operators
can inspect / retry / mark-failed queued notifications. Permission:
role_sysadmin only (operational, sensitive: re-running a failed alert
can fan out duplicate emails).

Idempotent: scans for an existing menu pointing at datalist
list_notification_queue and skips if present.
"""
import argparse, json, sys, urllib.error, urllib.request
import datetime, os
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


def make_menu():
    return {
        "className": "org.joget.plugin.enterprise.CrudMenu",
        "properties": {
            "id":            "menu-notif-queue",
            "customId":      "notif_queue",
            "label":         "Notification Queue (Scheduled & Failed)",
            "addFormId":     "notification_queue",
            "editFormId":    "notification_queue",
            "datalistId":    "list_notification_queue",
            "selectionType": "multiple",
            "showRowActions": "Yes",
            "icon":           "fa fa-clock-o",
            "iconIncluded":   True,
            "permission": {
                "className": "org.joget.apps.userview.lib.GroupPermission",
                "properties": {"allowedGroupIds": "role_sysadmin"}
            }
        }
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    admin_cat = None
    for cat in uv["categories"]:
        if "admin" in cat["properties"].get("label", "").lower():
            admin_cat = cat
            break
    if admin_cat is None:
        print("Admin category not found", file=sys.stderr); return 2

    for m in admin_cat.get("menus", []):
        if m["properties"].get("datalistId") == "list_notification_queue":
            print("Menu already present; nothing to do.")
            return 0

    if args.apply:
        ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        backup = f"_backups/v.preNotifQueueMenu.{ts}.json"
        os.makedirs(os.path.dirname(backup), exist_ok=True)
        with open(backup, "w") as f: json.dump(uv, f, indent=2)
        print(f"[backup] {backup}")

    new_menu = make_menu()
    admin_cat.setdefault("menus", []).append(new_menu)
    print(f"Adding '{new_menu['properties']['label']}' under Admin")

    if args.dry_run:
        return 0

    print("Applying...")
    s, b = push_userview(uv)
    print(f"HTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
