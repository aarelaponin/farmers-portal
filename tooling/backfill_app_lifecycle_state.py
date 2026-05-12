#!/usr/bin/env python3
"""
W3.1 — DEFERRED backfill of c_lifecycleState for existing applications.

This was intended to seed the new c_lifecycleState column on the 19
pre-W3.1 application rows. Hit form-creator-api's seed-by-id limitation
(businessKey='id' raises UnknownPathException, known issue from W2.5).

Resolution: no backfill is needed at runtime. AppAudit.transition() does
an implicit create() with seed=AppLifecycleMapper.fromStatus(c_status)
before any transition on a row whose c_lifecycleState is empty. So:

  - Existing 19 rows: c_lifecycleState stays empty until the operator
    re-opens one and triggers a save. On that save, the decision binder
    will write the lifecycle row + audit_log entry, seeding the row.
  - New rows (post build-131): EligibilityProcessingWorker writes
    SUBMITTED on pickup; chain produces the terminal state via
    AppAudit.transition; both rows in audit_log get created cleanly.

Keeping this script as documentation of the path NOT taken. Run with
--dry-run to see what the planned-but-not-applied mapping would be.
"""
import argparse, datetime, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}


def map_status_to_lifecycle(c_status):
    """One-way mirror of AppLifecycleMapper.fromStatus(). Keep in sync."""
    if not c_status: return "submitted"
    s = c_status.strip().lower()
    if s in ("approved", "auto_approved"):                     return "approved"
    if s in ("rejected", "auto_rejected"):                     return "rejected"
    if s in ("pending_review", "pending_data_clarification", "pending"):
        return "pending_review"
    if s in ("withdrawn", "cancelled"):                        return "withdrawn"
    if s == "draft":                                            return "draft"
    if s == "submitted":                                        return "submitted"
    if s in ("under_review", "in_review"):                     return "under_review"
    return "submitted"  # safe default — known-bad bucket


def seed_audit_log_via_formcreator(applications):
    """Use form-creator-api's /seed endpoint to bulk-insert audit_log rows."""
    rows = []
    now_iso = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    for app_id, target in applications:
        rows.append({
            "id":              "_uuid_",
            "entity_type":     "APPLICATION",
            "entity_id":       app_id,
            "from_status":     "",
            "to_status":       target,
            "triggered_by":    "system:backfill",
            "reason":          "W3.1 backfill — initial lifecycle state seeded from c_status",
            "transitioned_at": now_iso,
        })
    payload = {
        "appId":  "farmersPortal",
        "formId": "audit_log",
        "rows":   rows,
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/seed",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def update_lifecycle_state_via_formcreator(app_id, lifecycle):
    """Use form-creator-api's /seed in upsert mode to flip c_lifecycleState
    on a single row. We pass id + the field; the DAO does an UPDATE."""
    payload = {
        "appId":  "farmersPortal",
        "formId": "subsidyApplication2025",
        "rows": [
            {
                "id":             app_id,
                "lifecycleState": lifecycle,
            }
        ],
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

    # Verify the column exists before backfilling (it's created by the
    # form save on subsidyApplication2025 after add_lifecycle_state_to_application.py).
    cur.execute("""SELECT column_name FROM information_schema.columns
                   WHERE table_name='app_fd_subsidy_app_2025'
                     AND column_name='c_lifecyclestate'""")
    column_exists = bool(cur.fetchone())
    if not column_exists:
        print("Note: c_lifecyclestate column not yet on table — Joget will create it")
        print("on the first row write that includes the new key. Proceeding.")

    if column_exists:
        cur.execute("""SELECT id, c_status, COALESCE(c_lifecyclestate, '')
                       FROM app_fd_subsidy_app_2025
                       ORDER BY datecreated DESC""")
        rows = cur.fetchall()
    else:
        cur.execute("""SELECT id, c_status, ''
                       FROM app_fd_subsidy_app_2025
                       ORDER BY datecreated DESC""")
        rows = cur.fetchall()

    todo = []
    skipped = 0
    for app_id, cstatus, lifecycle in rows:
        if lifecycle and lifecycle.strip():
            skipped += 1
            continue
        target = map_status_to_lifecycle(cstatus)
        todo.append((app_id, cstatus, target))

    print(f"Total rows: {len(rows)} | already seeded: {skipped} | to backfill: {len(todo)}")
    if not todo:
        print("Nothing to do.")
        return 0

    print("\nPlanned changes:")
    print(f"  {'app_id':38s} {'c_status':32s} {'→ lifecycleState':20s}")
    for app_id, cstatus, target in todo:
        print(f"  {app_id:38s} {cstatus or '':32s} → {target}")

    if args.dry_run:
        print("\nDry-run — not applying.")
        return 0

    # 1) Update lifecycleState on each application
    print(f"\nUpdating lifecycleState on {len(todo)} rows...")
    ok = 0; bad = 0
    for app_id, _, target in todo:
        s, b = update_lifecycle_state_via_formcreator(app_id, target)
        if s == 200:
            ok += 1
        else:
            bad += 1
            print(f"  FAIL {app_id}: HTTP {s} {b[:200]}")
    print(f"  {ok} updated, {bad} failed.")

    # 2) Seed audit_log for the successfully-updated rows
    if ok > 0:
        seed_pairs = [(a, t) for (a, _, t) in todo]
        print(f"\nSeeding {len(seed_pairs)} audit_log rows...")
        s, b = seed_audit_log_via_formcreator(seed_pairs)
        print(f"  HTTP {s}: {b[:300]}")

    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
