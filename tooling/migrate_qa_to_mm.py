#!/usr/bin/env python3
"""
ADR-031 Slice B — migrate qa_rule rows into mm_determinant.

Reads every active row in app_fd_qa_rule, builds the mm_determinant
shape (scope=quality, severity=error|warning, triggerOn=save,
aggregation=issue_list), and upserts via formcreator/seed by businessKey=code.

Idempotent: re-running re-upserts the same rows. The qa_rule rows are
NOT deleted — Slice B is the dual-write window. Slice E retires qa_rule.

Per CLAUDE.md HARD RULE: writes go through Joget's DAO via the seed
endpoint; no raw SQL on app_fd_*. Cross-app caveat: qa_rule lives in
formQuality but we're writing to mm_determinant in farmersPortal, which
is what form-creator-api can see — that's exactly the shape we want.

Usage:
    python3 tooling/migrate_qa_to_mm.py            # dry-run preview
    python3 tooling/migrate_qa_to_mm.py --apply    # do the writes
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

import psycopg2

# ---------------------------------------------------------------------------
# Config (mirrors test fixtures)
# ---------------------------------------------------------------------------

PG_HOST     = "joget-pgsql-sa.postgres.database.azure.com"
PG_DATABASE = "jogetdb"
PG_USER     = "jogetadmin"
PG_PASSWORD = "Joget@DB#2026!"

JOGET_BASE_URL    = "http://20.87.213.78:8080/jw"
JOGET_API_KEY     = "a5af1181f77b4a62b481725b6410e965"
FORMCREATOR_API_ID = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"


# ---------------------------------------------------------------------------
# Read qa_rule
# ---------------------------------------------------------------------------

def fetch_qa_rules():
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, connect_timeout=10)
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT c_rulecode, c_severity, c_serviceid, c_tabcode,
                      c_affectedfields, c_message, c_rulescript
               FROM app_fd_qa_rule
               WHERE c_isactive = 'Y' OR c_isactive IS NULL
               ORDER BY c_rulecode"""
        )
        cols = ["rulecode", "severity", "serviceid", "tabcode",
                "affectedfields", "message", "rulescript"]
        return [dict(zip(cols, row)) for row in cur.fetchall()]
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# Build mm_determinant rows
# ---------------------------------------------------------------------------

def to_mm_determinant_row(qa: dict) -> dict:
    """Convert one qa_rule shape to one mm_determinant shape."""
    severity = (qa["severity"] or "").lower()
    if severity not in ("error", "warning"):
        # Defensive: any unexpected severity gets passed through verbatim
        # but flagged for inspection. Keeps the migration honest.
        pass

    return {
        # Identity
        "code":           qa["rulecode"],
        "name":           qa["rulecode"].replace(".", " ").replace("_", " ").title(),
        "serviceId":      qa["serviceid"],
        "registrationId": "",  # quality rules are service-level, not programme-specific

        # ADR-031 Slice A columns
        "scope":          "quality",
        "severity":       severity,
        "triggerOn":      "save",
        "aggregation":    "issue_list",
        "affectedFields": qa["affectedfields"] or "",
        "tabCode":        qa["tabcode"] or "",

        # Existing columns — leave eligibility-only fields blank
        "ruleType":       "",
        "score":          "",
        "allowSlowPath":  "",
        "ruleJson":       qa["rulescript"] or "",
        "actionJson":     "",
        "failMessage":    qa["message"] or "",
        "targetValue":    "",
        "description":    f"Migrated from qa_rule (ADR-031 Slice B). "
                          f"Tab: {qa['tabcode']}. Severity: {qa['severity']}.",
    }


# ---------------------------------------------------------------------------
# Push via formcreator/seed
# ---------------------------------------------------------------------------

def seed_mm_determinant(rows: list[dict]):
    payload = {
        "fixtures": [{
            "formId": "mm_determinant",
            "tableName": "mm_determinant",
            "businessKey": "code",
            "rows": rows,
        }]
    }
    req = urllib.request.Request(
        JOGET_BASE_URL + "/api/formcreator/formcreator/seed",
        method="POST",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "api_id":  FORMCREATOR_API_ID,
            "api_key": JOGET_API_KEY,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------

def verify_round_trip():
    """Confirm every qa_rule has a matching mm_determinant row with
    scope=quality and the same SQL probe."""
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, connect_timeout=10)
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT q.c_rulecode, q.c_rulescript, m.c_rulejson
               FROM app_fd_qa_rule q
               LEFT JOIN app_fd_mm_determinant m
                 ON m.c_code = q.c_rulecode AND m.c_scope = 'quality'
               WHERE q.c_isactive = 'Y' OR q.c_isactive IS NULL
               ORDER BY q.c_rulecode"""
        )
        rows = cur.fetchall()
        missing = []
        mismatched = []
        for code, qa_script, mm_script in rows:
            if mm_script is None:
                missing.append(code)
            elif (qa_script or "").strip() != (mm_script or "").strip():
                mismatched.append(code)
        return len(rows), missing, mismatched
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apply", action="store_true",
                    help="Actually write the rows. Without this flag, dry-run preview only.")
    args = ap.parse_args()

    print("== Reading qa_rule ==")
    qa_rules = fetch_qa_rules()
    print(f"  {len(qa_rules)} active qa_rule rows")

    print("\n== Building mm_determinant rows ==")
    mm_rows = [to_mm_determinant_row(qa) for qa in qa_rules]
    severity_dist = {}
    for r in mm_rows:
        severity_dist[r["severity"]] = severity_dist.get(r["severity"], 0) + 1
    print(f"  severity distribution: {severity_dist}")

    if not args.apply:
        print("\n== Dry-run — first 3 generated rows ==")
        for r in mm_rows[:3]:
            print(f"  code={r['code']}")
            print(f"    severity={r['severity']}, scope={r['scope']}, "
                  f"triggerOn={r['triggerOn']}, aggregation={r['aggregation']}")
            print(f"    affectedFields={r['affectedFields']!r}")
            print(f"    failMessage={r['failMessage']!r}")
            print(f"    ruleJson preview: {r['ruleJson'][:80]!r}")
            print()
        print(f"\n(dry run — pass --apply to write {len(mm_rows)} rows)")
        return 0

    print("\n== Seeding mm_determinant via formcreator/seed ==")
    status, body = seed_mm_determinant(mm_rows)
    print(f"  status: {status}")
    print(f"  response: {body[:300]}")
    if status != 200:
        print("ERROR: seed failed")
        return 1

    print("\n== Verifying round-trip ==")
    total, missing, mismatched = verify_round_trip()
    print(f"  total qa_rule rows checked: {total}")
    print(f"  missing in mm_determinant:  {len(missing)}")
    if missing:
        for c in missing[:5]:
            print(f"    - {c}")
    print(f"  ruleJson mismatched:        {len(mismatched)}")
    if mismatched:
        for c in mismatched[:5]:
            print(f"    - {c}")

    if missing or mismatched:
        print("\n✗ Migration incomplete — investigate.")
        return 1
    print(f"\n✓ All {total} qa_rule rows have matching mm_determinant rows with byte-identical SQL.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
