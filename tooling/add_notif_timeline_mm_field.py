#!/usr/bin/env python3
"""
W2.8 (redo) — author one mm_field row that drops the
notification_timeline panel onto the Operator Decision tab.

The panel renders inside the Operator Decision step of the
subsidyApplicationOperator2025 wizard, same shape as the existing
Eligibility Outcome + Budget Impact cards. The Java side is
NotificationTimelineRenderer (build-132+); the mm_field row just
tells MetaScreenElement to synthesise it on this screen.

Idempotent: skips if a row with the same storageKey already exists
on this screen.

HARD RULE compliant: pushes via form-creator-api/seed.
"""
import argparse, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}

SCREEN_ID = "b9b3b0ff-3c1a-43cb-ac51-e8098a40455d"  # OP_DECISION
STORAGE_KEY = "notification_timeline_panel"

ROW = {
    "screenId":              SCREEN_ID,
    "storageKey":            STORAGE_KEY,
    "label":                 "Notifications sent for this application",
    "widget":                "notification_timeline",
    "dataType":              "string",
    "defaultBehaviorOnError":"optional",
    "orderIndex":            "10",   # after the existing decision fields
    "helpText":              "Every email/SMS dispatched for this application — auto-generated, read-only.",
}


def push():
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {
                "formId":       "mm_field",
                "businessKey":  "storageKey",
                "rows": [ROW]
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
    cur.execute("""SELECT id, c_storagekey, c_widget FROM app_fd_mm_field
                   WHERE c_screenid=%s AND c_storagekey=%s""",
                (SCREEN_ID, STORAGE_KEY))
    existing = cur.fetchone()
    if existing:
        print(f"Already present: id={existing[0]} storageKey={existing[1]} widget={existing[2]}")
        print("Nothing to do.")
        return 0

    print(f"Planning to insert mm_field on screen {SCREEN_ID}:")
    for k, v in ROW.items(): print(f"  {k} = {v}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push()
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
