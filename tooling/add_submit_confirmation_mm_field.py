#!/usr/bin/env python3
"""
W3.4 — author the submit_confirmation checkbox on the citizen wizard's
final tab (APP_REVIEW screen).

The mm_field carries widget=checkbox with one option labelled "I confirm
submission of this application". When ticked, the citizen's save on the
final tab transitions the application's lifecycle DRAFT → SUBMITTED
(see RegBbApplicationStoreBinder.applyLifecycleTransition in build-133).
When left unticked, the application stays in DRAFT and the citizen can
return later to continue editing.

The checkbox's underlying CheckBox element stores selected option values
comma-joined; with a single option value="Y", a ticked box writes "Y" to
the c_submit_confirmation column.

For the checkbox option, we use the simplest possible binder shape
(static option in optionsBinderJson) so MetaScreenElement renders the
single Y/Yes option without needing an options catalog lookup.

Idempotent: skips if mm_field already present on the screen.
"""
import argparse, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}

SCREEN_ID   = "0496bcb6-419d-477a-ab3b-1406f519e565"  # APP_REVIEW
STORAGE_KEY = "submit_confirmation"

# CheckBox with one option. addCatalogOptions in MetaScreenElement reads
# optionsBinderJson for static options when no catalog is specified.
ROW = {
    "screenId":              SCREEN_ID,
    "storageKey":            STORAGE_KEY,
    "label":                 "Submission",
    "widget":                "checkbox",
    "dataType":              "string",
    "defaultBehaviorOnError":"optional",
    "orderIndex":            "20",
    "helpText":              "Tick the box and save to formally submit your application. Without this tick, your changes are saved as DRAFT and you can return later.",
    "optionsBinderJson":     json.dumps({
        "className": "org.joget.apps.form.lib.StaticBinder",
        "properties": {
            "options": [
                {"value": "Y", "label": "I confirm submission of this application"}
            ]
        }
    }),
}


def push():
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {
                "formId":      "mm_field",
                "businessKey": "storageKey",
                "rows":        [ROW]
            }
        ]
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/seed",
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
    cur.execute("""SELECT id FROM app_fd_mm_field
                   WHERE c_screenid=%s AND c_storagekey=%s""",
                (SCREEN_ID, STORAGE_KEY))
    if cur.fetchone():
        print(f"Already present on screen {SCREEN_ID}.")
        return 0

    print(f"Planning mm_field on screen {SCREEN_ID} (APP_REVIEW):")
    for k, v in ROW.items(): print(f"  {k} = {v}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push()
    print(f"\nHTTP {s}: {b[:400]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
