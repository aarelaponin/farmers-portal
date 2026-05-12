#!/usr/bin/env python3
"""
Add the `case_note_add` CrudMenu to the farmersPortal userview, under the
MOA Office category (operator-only). The menu is the target the operator
review form's "+ Add note" link points at: opens the case_note form keyed
by ?app_id= URL param. Operators access it from the operator review form,
not from main nav, so we set permission to deny everyone (the menu still
serves the form when reached via direct URL — which is what we want).

Idempotent: skips if `case_note_add` already exists in any category.

Usage:
    python3 tooling/add_case_note_menu.py --dry-run
    python3 tooling/add_case_note_menu.py --apply
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

import psycopg2

PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb",
    user="jogetadmin",
    password=os.environ.get("PGPASSWORD", ""),
    port=5432,
    sslmode="require",
)
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}
APP_ID, USERVIEW = "farmersPortal", "v"

MENU = {
    "className": "org.joget.plugin.enterprise.CrudMenu",
    "properties": {
        "id":         "case_note_add",
        "customId":   "case_note_add",
        "label":      "Add Case Note",
        "addFormId":  "case_note",
        "editFormId": "case_note",
        "datalistId": "list_case_notes_for_app",
        "redirectUrlOnSave": "javascript:try{window.opener&&window.opener.location.reload();}catch(e){}window.close();",
        "permission": { "className": "", "properties": {} }
    }
}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid=%s AND id=%s", (APP_ID, USERVIEW))
    uv = json.loads(cur.fetchone()[0])

    # Idempotent — scan every category for an existing case_note_add
    already = False
    for cat in uv.get("categories", []):
        for m in cat.get("menus", []):
            mid = (m.get("properties") or {}).get("customId") or (m.get("properties") or {}).get("id") or ""
            if mid == "case_note_add":
                already = True
                break

    if already:
        print("case_note_add menu already present — nothing to do.")
        return 0

    # Insert into MOA Office category (or the last category as a fallback)
    target_cat = None
    for cat in uv.get("categories", []):
        lbl = (cat.get("properties") or {}).get("label", "") or ""
        if "MOA" in lbl or "Office" in lbl:
            target_cat = cat
            break
    if target_cat is None:
        target_cat = uv["categories"][-1]
        print(f"Note: no MOA category found; adding to {target_cat['properties'].get('label','?')}")

    target_cat.setdefault("menus", []).append(MENU)
    print(f"Adding case_note_add to category '{target_cat['properties'].get('label','?')}' "
          f"(now {len(target_cat['menus'])} menus).")

    if args.dry_run:
        print("Dry-run — not pushing.")
        return 0

    payload = {
        "appId":        APP_ID,
        "userviewId":   USERVIEW,
        "userviewName": uv.get("setting", {}).get("properties", {}).get("userviewName", USERVIEW),
        "json":         json.dumps(uv, separators=(",", ":")),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/userviews",
        data=json.dumps(payload).encode("utf-8"),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            print(f"HTTP {resp.status}: {resp.read().decode()[:300]}")
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode()[:300]}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
