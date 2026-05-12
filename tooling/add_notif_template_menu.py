#!/usr/bin/env python3
"""
Add a CrudMenu "Notification Templates" under MM-Configuration so operators
can edit subject/body for the 12 email templates from the userview UI.

Idempotent: if the menu already exists (matched by editFormId=spNotifTemplate
inside the MM-Config category), do nothing.
"""

import argparse, datetime, json, os, sys, urllib.error, urllib.request
import uuid as _uuid
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
    """Minimal CrudMenu for spNotifTemplate. Permissioned to sysadmin + analyst
    (per rbac_taxonomy.md MM-Configuration band)."""
    return {
        "className": "org.joget.plugin.enterprise.CrudMenu",
        "properties": {
            "id":          "menu-notif-templates",
            "customId":    "notif_templates",
            "label":       "Notification Templates",
            "addFormId":   "spNotifTemplate",
            "editFormId":  "spNotifTemplate",
            "datalistId":  "list_spNotifTemplate",
            "selectionType": "multiple",
            "showRowActions": "Yes",
            "icon": "fa fa-envelope-open-text",
            "iconIncluded": True,
            "permission": {
                "className": "org.joget.apps.userview.lib.GroupPermission",
                "properties": {
                    "allowedGroupIds": "role_sysadmin;role_analyst"
                }
            }
        }
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    # Find MM-Configuration category
    mm_cat = None
    for cat in uv["categories"]:
        lbl = cat["properties"].get("label", "").lower()
        if "mm" in lbl and "config" in lbl:
            mm_cat = cat
            break
    if mm_cat is None:
        print("MM-Configuration category not found", file=sys.stderr); return 2

    # Idempotency
    for m in mm_cat.get("menus", []):
        if m["properties"].get("editFormId") == "spNotifTemplate":
            print("Menu already present; nothing to do.")
            return 0

    if args.apply:
        ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        backup = f"_backups/v.preNotifMenu.{ts}.pretty.json"
        os.makedirs(os.path.dirname(backup), exist_ok=True)
        with open(backup, "w") as f: json.dump(uv, f, indent=2)
        print(f"[backup] {backup}")

    new_menu = make_menu()
    print(f"\nAdding menu '{new_menu['properties']['label']}' to category "
          f"'{mm_cat['properties'].get('label')}'")
    mm_cat.setdefault("menus", []).append(new_menu)

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\nApplying...")
    status, body = push_userview(uv)
    print(f"HTTP {status}: {body[:300]}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
