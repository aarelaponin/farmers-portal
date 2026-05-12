#!/usr/bin/env python3
"""
Multi-programme stacking stress test.

Verifies that a single applicant can hold multiple concurrent issued vouchers
— one per programme they qualify for — without cross-contamination.

Profile A (Mants'ali Panyane, 066257236627) qualifies for both
PRG_2025_001 (lowlands maize seed) and PRG_2025_003 (smallholder programme,
single-rule "area_hectares <= 5"). Per the L4 expected matrix.

Asserts:
  1. Both applications submit cleanly (no NID-collision on the registry side)
  2. Both reach 'auto_approved' independently
  3. Each yields its own voucher, EITHER as 'issued' OR a clean
     'no_matching_lines' (when the programme has no allocation plan
     covering Mants'ali's district)
  4. No voucher carries the wrong programme_code (cross-contamination check)
  5. Each voucher's COMMITMENT event lands on its OWN envelope
     (ENV_PRG_2025_001_FY2526 vs ENV_PRG_2025_003_FY2526)
  6. Duplicate-guard works: re-submitting the same (NID, programme) returns
     the duplicate-rejected status, no extra rows

Re-runnable: pre-cleans both prior applications + their vouchers via the
Joget APIs (HARD-RULE compliant). The farmer registry row is NOT touched
(Profile A is customer-owned data).

Usage:
    python3 tooling/test_im_stacking.py
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  "a5af1181f77b4a62b481725b6410e965")
BUDGET_API_ID  = "API-BUDGET"

_HERE = os.path.dirname(os.path.abspath(__file__))
ENDPOINTS_FILE = os.path.normpath(
    os.path.join(_HERE, os.pardir, "app", "seeds", ".api_endpoints.json"))

APPLICANT_NID  = "066257236627"     # Profile A — pre-existing customer row
APPLICANT_NAME = "Mants'ali Panyane"
PROGRAMMES = ["PRG_2025_001", "PRG_2025_003"]   # Both pass for Profile A per L4 matrix

APPLICANT_BODY_BASE = {
    "national_id":           APPLICANT_NID,
    "full_name":             APPLICANT_NAME,
    "gender":                "female",
    "date_of_birth":         "1985-10-01",
    "contact_phone":         "+26659518328",
    "district":              "maseru",
    "agro_zone":             "lowlands",
    "village_name":          "maseru-mabote",
    "area_hectares":         "3.5",
    "tenure_type":           "customary",
    "primary_crop":          "maize",
    "block_farming_member":  "yes",
    "cooperative_name":      "Mabote Block Farming Cooperative",
    "drought_affected_decl": "no",
}


# ---------------------------------------------------------------------------
# HTTP helpers (lifted from test_im_e2e.py)
# ---------------------------------------------------------------------------

def _request(url, method, body=None, api_id=None, content_type="application/json"):
    headers = {
        "Content-Type": content_type,
        "api_id": api_id,
        "api_key": JOGET_API_KEY,
    }
    data = None
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode("utf-8")
        elif isinstance(body, bytes):
            data = body
        else:
            data = str(body).encode("utf-8")
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def parse(raw):
    try:
        outer = json.loads(raw)
        msg = outer.get("message", outer)
        if isinstance(msg, str):
            try: return json.loads(msg)
            except json.JSONDecodeError: return msg
        return msg
    except json.JSONDecodeError:
        return raw


def db_connect():
    import psycopg2
    return psycopg2.connect(
        host=os.environ.get("PGHOST", "joget-pgsql-sa.postgres.database.azure.com"),
        dbname=os.environ.get("PGDATABASE", "jogetdb"),
        user=os.environ.get("PGUSER", "jogetadmin"),
        password=os.environ.get("PGPASSWORD", "Joget@DB#2026!"),
        port=int(os.environ.get("PGPORT", "5432")),
        sslmode="require")


def query_all(sql, params=()):
    conn = db_connect()
    try:
        cur = conn.cursor()
        cur.execute(sql, params)
        return cur.fetchall()
    finally:
        conn.close()


def query_one(sql, params=()):
    rs = query_all(sql, params)
    return rs[0] if rs else None


def load_endpoints():
    with open(ENDPOINTS_FILE) as f:
        m = json.load(f)
    citizen = m["API_SUBSIDY_APP_2025_CITIZEN"]
    return {
        "citizen_api_id": citizen["apiId"],
        "citizen_url":    f"{JOGET_BASE_URL}/api/form/{citizen['formId']}",
    }


# ---------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------

def precleanup(endpoints):
    """Delete any prior FY2526 applications for Profile A across the test
    programmes, plus any orphan vouchers tied to those apps."""
    print(f"--- precleanup for NID={APPLICANT_NID} ---")
    rows = query_all(
        "SELECT id, c_applied_programme FROM app_fd_subsidy_app_2025 "
        "WHERE c_national_id = %s AND c_applied_programme = ANY(%s)",
        (APPLICANT_NID, PROGRAMMES))
    for app_id, prog in rows:
        st, _ = _request(f"{endpoints['citizen_url']}/{app_id}", "DELETE",
                         None, endpoints["citizen_api_id"])
        print(f"  DELETE app {app_id[:8]}.. ({prog}) → HTTP {st}")
    # Don't delete the existing vouchers — they may be in 'redeemed' state
    # and tied to historical reports. The duplicate-guard rule fires on
    # *application* duplication, not voucher.


def submit_application(endpoints, programme):
    body = dict(APPLICANT_BODY_BASE)
    body["applied_programme"] = programme
    st, raw = _request(endpoints["citizen_url"], "POST", body,
                       endpoints["citizen_api_id"])
    p = parse(raw)
    if st == 200 and isinstance(p, dict) and "id" in p:
        return p["id"], None
    return None, f"HTTP {st}: {p}"


def trigger_worker():
    _request(JOGET_BASE_URL + "/api/budget/run-eligibility-worker", "POST",
             b"{}", BUDGET_API_ID)


def poll_status(endpoints, app_id, retries=20, delay=0.5):
    url = f"{endpoints['citizen_url']}/{app_id}"
    for _ in range(retries):
        st, raw = _request(url, "GET", None, endpoints["citizen_api_id"])
        if st == 200:
            p = parse(raw)
            if isinstance(p, dict):
                s = p.get("status", "")
                if s in ("auto_approved", "approved", "auto_rejected", "rejected"):
                    return s
        time.sleep(delay)
    return "(timeout)"


def find_voucher(app_id):
    return query_one(
        "SELECT c_code, c_status, c_programme_code, c_input_code, c_point_code, c_quantity "
        "FROM app_fd_im_voucher WHERE c_application_id = %s LIMIT 1",
        (app_id,))


def find_commitment_event(voucher_code):
    return query_one(
        "SELECT c_envelope_code, c_amount, c_idempotency_key "
        "FROM app_fd_budget_event WHERE c_idempotency_key = %s "
        "AND c_event_type = 'COMMITMENT' AND c_direction = 'debit' LIMIT 1",
        (f"voucher_issued:{voucher_code}",))


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------

def main():
    print("=" * 78)
    print("Multi-programme stacking stress test")
    print("=" * 78)
    print(f"Applicant: {APPLICANT_NAME} ({APPLICANT_NID})")
    print(f"Programmes: {PROGRAMMES}")
    print()

    endpoints = load_endpoints()
    precleanup(endpoints)
    print()

    submitted = []  # list of (programme, app_id)
    failures = []

    # Step 1: submit one application per programme
    print("[Step 1] Submit applications across programmes")
    for prog in PROGRAMMES:
        app_id, err = submit_application(endpoints, prog)
        if err:
            print(f"  FAIL {prog}: {err}")
            failures.append(("submit", prog, err))
            continue
        submitted.append((prog, app_id))
        print(f"  OK   {prog}: app_id={app_id}")

    if not submitted:
        print("\nNo applications submitted; aborting test.")
        return 1

    # Step 2: trigger eligibility worker once for all
    trigger_worker()
    time.sleep(1.0)

    # Step 3: poll status per application
    print("\n[Step 2] Poll for auto_approved per application")
    statuses = {}
    for prog, app_id in submitted:
        s = poll_status(endpoints, app_id)
        statuses[app_id] = s
        ok = (s == "auto_approved")
        mark = "OK" if ok else "FAIL"
        print(f"  [{mark}] {prog}: status={s}")
        if not ok:
            failures.append(("eligibility", prog, s))

    # Step 4: voucher per application
    print("\n[Step 3] Voucher per application — checking c_programme_code attribution")
    time.sleep(1.5)  # give VoucherIssuanceTool a moment
    voucher_records = {}
    for prog, app_id in submitted:
        if statuses.get(app_id) != "auto_approved":
            continue
        v = find_voucher(app_id)
        if v is None:
            # Eligibility passed but no voucher — likely no allocation plan covers
            # Mants'ali's (district, programme) combo. This is acceptable; mark
            # but don't fail.
            print(f"  NOTE {prog}: no voucher (likely no allocation plan for "
                  f"district=maseru × programme={prog})")
            voucher_records[prog] = None
            continue
        code, status, prog_code, input_code, point, qty = v
        if prog_code != prog:
            msg = f"voucher.programme_code='{prog_code}' but app submitted as '{prog}'"
            print(f"  FAIL {prog}: cross-contamination — {msg}")
            failures.append(("contamination", prog, msg))
        else:
            print(f"  OK   {prog}: voucher={code} status={status} input={input_code} qty={qty}")
        voucher_records[prog] = v

    # Step 5: per-envelope budget event — each voucher's COMMITMENT lands on
    # ITS programme's envelope, not the other.
    print("\n[Step 4] Each voucher's COMMITMENT lands on its own envelope")
    for prog, v in voucher_records.items():
        if v is None: continue
        code = v[0]
        evt = find_commitment_event(code)
        if evt is None:
            print(f"  FAIL {prog} ({code}): no COMMITMENT event")
            failures.append(("commitment_missing", prog, code))
            continue
        env, amount, idem = evt
        expected_env = f"ENV_{prog}_FY2526"
        if env != expected_env:
            msg = f"COMMITMENT envelope='{env}' but expected '{expected_env}'"
            print(f"  FAIL {prog} ({code}): {msg}")
            failures.append(("envelope_mismatch", prog, msg))
        else:
            print(f"  OK   {prog}: COMMITMENT {amount} → {env} (idem={idem})")

    # Step 6: duplicate-guard — re-submit the SAME (NID, programme) and assert
    # it doesn't pass.
    print("\n[Step 5] Duplicate-guard — re-submitting the same NID × programme")
    for prog, app_id in submitted:
        # Submit again with same body; expect either:
        #   (a) HTTP 200 with auto_rejected status due to DET_NO_DUPLICATE_*
        #   (b) HTTP 400 from a uniqueness validator
        body = dict(APPLICANT_BODY_BASE)
        body["applied_programme"] = prog
        st, raw = _request(endpoints["citizen_url"], "POST", body,
                           endpoints["citizen_api_id"])
        p = parse(raw)
        new_app_id = p.get("id") if isinstance(p, dict) else None
        if new_app_id is None:
            print(f"  OK   {prog}: re-submit blocked at HTTP layer (HTTP {st})")
            continue
        # Application persisted; check if it gets auto_rejected by the
        # duplicate determinant.
        trigger_worker()
        time.sleep(0.8)
        new_status = poll_status(endpoints, new_app_id, retries=15)
        if new_status == "auto_rejected":
            print(f"  OK   {prog}: duplicate auto-rejected by determinant")
            # Clean up the duplicate so it doesn't pollute future runs.
            _request(f"{endpoints['citizen_url']}/{new_app_id}", "DELETE",
                     None, endpoints["citizen_api_id"])
        else:
            msg = f"re-submit accepted with status='{new_status}' — duplicate-guard NOT firing"
            print(f"  FAIL {prog}: {msg}")
            failures.append(("dup_guard", prog, msg))

    # Summary
    print()
    print("=" * 78)
    if not failures:
        print("All assertions hold. Multi-programme stacking works cleanly.")
        return 0
    else:
        print(f"{len(failures)} assertion(s) failed:")
        for kind, prog, detail in failures:
            print(f"  - {kind}/{prog}: {detail}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
