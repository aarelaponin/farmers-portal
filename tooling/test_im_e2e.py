#!/usr/bin/env python3
"""
IM end-to-end integration test (Slice C).

Drives a single fresh applicant through the full Inputs Management lifecycle
and asserts the inter-slice contracts:

  applicant identity (fresh NID)
       ↓ submit_application via citizen API
  application row → eligibility worker → auto_approved
       ↓ EligibilityProcessingWorker auto-issuance hook
  voucher row (status=issued) + budget COMMITMENT event
       ↓ POST /budget/redeem-voucher
  voucher status=redeemed + redemption row + inventory decremented + budget EXPENSE event
       ↓ insert distribution receipt via formcreator /seed
  im_distribution row exists, links back to voucher

Plus a 30-line "report smoke" pass at the end: each of the 5 IM reports'
SQL is executed via psycopg2 — asserts only that the query parses and returns
without error. Catches column-name drift introduced today.

Re-runnability: uses an existing customer NID (Profile A — Mants'ali Panyane,
066257236627) that already has a row in the farmer registry. The test does
NOT seed registry data — registry tables hold customer-owned data and must
not be polluted. Before each run, any prior application for this NID +
programme is deleted via the citizen API so DET_NO_DUPLICATE_PRG_001 doesn't
auto-reject the resubmit. Voucher / redemption / receipt rows accumulate
across runs as a harmless audit trail.

Usage:
    python3 tooling/test_im_e2e.py            # full chain + report smoke
    python3 tooling/test_im_e2e.py --skip-receipt
    python3 tooling/test_im_e2e.py --reports-only

Per CLAUDE.md HARD RULE: writes go through Joget APIs (citizen / budget /
formcreator). DB access is read-only SELECT for assertions.
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid  # used only to make distribution-receipt code unique per run

# ---------------------------------------------------------------------------
# Configuration — same defaults as run_l4_scenarios.py
# ---------------------------------------------------------------------------

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))
FORMCREATOR_API_ID = os.environ.get(
    "JOGET_FORMCREATOR_API_ID",
    "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
)
BUDGET_API_ID = "API-BUDGET"
APP_ID = "farmersPortal"

_HERE = os.path.dirname(os.path.abspath(__file__))
ENDPOINTS_FILE = os.path.normpath(
    os.path.join(_HERE, os.pardir, "app", "seeds", ".api_endpoints.json"))
DATALISTS_DIR = os.path.normpath(
    os.path.join(_HERE, os.pardir, "app", "datalists"))

PROGRAMME_CODE = "PRG_2025_001"  # lowlands maize seed — Profile A passes

# Use Profile A's existing customer-data NID. The farmer registry is
# customer-owned data and must NOT be polluted with test rows. The duplicate-
# guard rule (DET_NO_DUPLICATE_PRG_001) fires if Profile A already has an
# active application for this programme — the test pre-cleans it before
# submitting (DELETE via API per HARD RULE) and again at end.
APPLICANT = {
    "national_id":           "066257236627",
    "full_name":             "Mants'ali Panyane",
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
# HTTP helpers (lifted from run_l4_scenarios.py)
# ---------------------------------------------------------------------------

def _request(url, method, body=None, api_id=None, content_type="application/json"):
    headers = {
        "Content-Type": content_type,
        "api_id":  api_id,
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


def post(url, body, api_id):
    return _request(url, "POST", body, api_id)


def get(url, api_id):
    return _request(url, "GET", None, api_id)


def parse(raw):
    """API Builder wraps every response in {date, code, message: <stringified-json>}."""
    try:
        outer = json.loads(raw)
        msg = outer.get("message", outer)
        if isinstance(msg, str):
            try:
                return json.loads(msg)
            except json.JSONDecodeError:
                return msg
        return msg
    except json.JSONDecodeError:
        return raw


def load_endpoints():
    if not os.path.exists(ENDPOINTS_FILE):
        sys.exit(f"FATAL: {ENDPOINTS_FILE} not found. Run seed.py first.")
    with open(ENDPOINTS_FILE) as f:
        m = json.load(f)
    citizen = m["API_SUBSIDY_APP_2025_CITIZEN"]
    return {
        "citizen_api_id": citizen["apiId"],
        "citizen_url":    f"{JOGET_BASE_URL}/api/form/{citizen['formId']}",
    }

# ---------------------------------------------------------------------------
# DB helpers — read-only per HARD RULE
# ---------------------------------------------------------------------------

def db_connect():
    import psycopg2
    return psycopg2.connect(
        host=os.environ.get("PGHOST", "joget-pgsql-sa.postgres.database.azure.com"),
        dbname=os.environ.get("PGDATABASE", "jogetdb"),
        user=os.environ.get("PGUSER", "jogetadmin"),
        password=os.environ.get("PGPASSWORD", os.environ.get("PGPASSWORD", "")),
        port=int(os.environ.get("PGPORT", "5432")),
        sslmode="require")


def query_one(sql, params=()):
    conn = db_connect()
    try:
        cur = conn.cursor()
        cur.execute(sql, params)
        return cur.fetchone()
    finally:
        conn.close()


def query_all(sql, params=()):
    conn = db_connect()
    try:
        cur = conn.cursor()
        cur.execute(sql, params)
        return cur.fetchall()
    finally:
        conn.close()

# ---------------------------------------------------------------------------
# Chain steps
# ---------------------------------------------------------------------------

def precleanup_applications(endpoints, national_id):
    """Delete any prior applications for this NID + programme so the
    DET_NO_DUPLICATE_PRG_001 applicability rule doesn't fire on a fresh
    test run. Read uses SELECT (HARD-RULE allowed); delete goes through
    the citizen API (HARD-RULE compliant). Lifted from
    run_l4_scenarios.precleanup_for_fixtures."""
    try:
        import psycopg2
    except ImportError:
        return 0
    conn = db_connect()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT id FROM app_fd_subsidy_app_2025 "
            "WHERE c_national_id = %s AND c_applied_programme = %s",
            (national_id, PROGRAMME_CODE))
        ids = [r[0] for r in cur.fetchall()]
    finally:
        conn.close()
    deleted = 0
    for app_id in ids:
        url = f"{endpoints['citizen_url']}/{app_id}"
        st, _ = _request(url, "DELETE", None, endpoints["citizen_api_id"])
        if st == 200:
            deleted += 1
    return deleted


def submit_application(endpoints, applicant):
    body = dict(applicant)
    body["applied_programme"] = PROGRAMME_CODE
    status, raw = post(endpoints["citizen_url"], body, endpoints["citizen_api_id"])
    parsed = parse(raw)
    if status != 200:
        return None, f"HTTP {status}: {raw[:200]}"
    if isinstance(parsed, dict) and "id" in parsed:
        return parsed["id"], None
    return None, f"unexpected response: {parsed!r}"


def trigger_eligibility_worker():
    """Drain processing_queue. Best-effort — fall through if endpoint is
    not enabled in API Builder; poll loop in poll_for_status will absorb
    the lag from the scheduled worker pass."""
    url = JOGET_BASE_URL + "/api/budget/run-eligibility-worker"
    try:
        _request(url, "POST", b"{}", BUDGET_API_ID)
    except Exception:
        pass


def poll_for_status(endpoints, app_id, retries=24, delay=0.5):
    url = f"{endpoints['citizen_url']}/{app_id}"
    for _ in range(retries):
        status, raw = get(url, endpoints["citizen_api_id"])
        if status == 200:
            parsed = parse(raw)
            if isinstance(parsed, dict):
                row_status = parsed.get("status", "")
                if row_status in ("auto_approved", "approved",
                                  "auto_rejected", "rejected"):
                    return row_status
        time.sleep(delay)
    return "(timeout)"


def find_voucher_by_application(app_id, retries=10, delay=0.5):
    sql = ("SELECT c_code, c_status, c_input_code, c_point_code, c_quantity, "
           "       c_programme_code, c_farmer_nid "
           "FROM app_fd_im_voucher WHERE c_application_id = %s LIMIT 1")
    for _ in range(retries):
        row = query_one(sql, (app_id,))
        if row:
            return row
        time.sleep(delay)
    return None


def find_inventory(point_code, input_code):
    return query_one(
        "SELECT c_quantity_on_hand FROM app_fd_im_inventory "
        "WHERE c_point_code = %s AND c_input_code = %s",
        (point_code, input_code))


def find_budget_events_by_idempotency(idem):
    """Idempotency key (e.g. 'voucher_issued:VCH-20260506-0003') lives in
    c_idempotency_key. c_correlation_id holds the bare voucher/redemption
    code (no prefix). Source-verified May 2026 against live data."""
    return query_all(
        "SELECT c_event_type, c_direction, c_amount, c_correlation_id "
        "FROM app_fd_budget_event WHERE c_idempotency_key = %s ORDER BY id",
        (idem,))


def redeem_voucher(voucher_code, point_code, by_name):
    qs = (f"voucherCode={voucher_code}&"
          f"redemptionPoint={point_code}&"
          f"redeemedBy={by_name.replace(' ', '%20')}")
    url = JOGET_BASE_URL + f"/api/budget/redeem-voucher?{qs}"
    status, raw = post(url, b"", BUDGET_API_ID)
    return status, parse(raw)


def insert_distribution_receipt(voucher_code, operator_name):
    receipt_code = "DR-E2E-" + uuid.uuid4().hex[:6].upper()
    payload = {
        "appId": APP_ID,
        "fixtures": [{
            "formId":      "im_distribution",
            "businessKey": "code",
            "rows": [{
                "code":              receipt_code,
                "voucher_code":      voucher_code,
                "distribution_date": time.strftime("%Y-%m-%d"),
                "operator_name":     operator_name,
                "witness_name":      "(test — no witness)",
                "notes":             "Inserted by test_im_e2e.py via formcreator /seed",
                # farmer_signature / operator_signature: blank — /seed bypasses
                # form validators, so the mandatory check doesn't fire.
            }]
        }]
    }
    url = JOGET_BASE_URL + "/api/formcreator/formcreator/seed"
    status, raw = post(url, payload, FORMCREATOR_API_ID)
    return status, parse(raw), receipt_code

# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------

def run_e2e(skip_receipt=False):
    print("=" * 78)
    print("IM End-to-End Integration Test")
    print("=" * 78)
    applicant = APPLICANT
    print(f"Applicant: {applicant['full_name']}")
    print(f"NID: {applicant['national_id']} (existing customer registry row)")
    print(f"Programme: {PROGRAMME_CODE}")
    print()

    endpoints = load_endpoints()

    # Step 0: precleanup — delete any prior application for this NID +
    # programme so DET_NO_DUPLICATE_PRG_001 doesn't auto-reject on resubmit.
    # Reads farmer registry but never writes to it (customer-owned data).
    n_deleted = precleanup_applications(endpoints, applicant["national_id"])
    print(f"[0/9] OK   precleanup: deleted {n_deleted} prior application(s) for "
          f"{applicant['national_id']} × {PROGRAMME_CODE}")

    # Step 1: submit application
    app_id, err = submit_application(endpoints, applicant)
    if err:
        print(f"[1/9] FAIL submit: {err}")
        return 1
    print(f"[1/9] OK   application submitted, id={app_id}")

    # Step 2: trigger eligibility worker
    trigger_eligibility_worker()
    print(f"[2/9] OK   eligibility worker triggered")

    # Step 3: poll for status
    final_status = poll_for_status(endpoints, app_id)
    if final_status != "auto_approved":
        print(f"[3/9] FAIL expected auto_approved, got '{final_status}'")
        return 1
    print(f"[3/9] OK   application status → auto_approved")

    # Step 4: voucher exists with status=issued
    voucher = find_voucher_by_application(app_id)
    if not voucher:
        print(f"[4/9] FAIL no voucher row for application {app_id}")
        return 1
    voucher_code, vstatus, input_code, point_code, qty_str, prog, nid = voucher
    if vstatus != "issued":
        print(f"[4/9] FAIL voucher status='{vstatus}', expected 'issued'")
        return 1
    qty = float(qty_str) if qty_str else 0
    print(f"[4/9] OK   voucher issued: code={voucher_code} input={input_code} "
          f"point={point_code} qty={qty}")

    # Step 5: COMMITMENT event posted with idempotency key
    idem_issue = f"voucher_issued:{voucher_code}"
    issue_evts = find_budget_events_by_idempotency(idem_issue)
    if not issue_evts:
        print(f"[5/9] FAIL no budget events with idempotency_key={idem_issue!r}")
        return 1
    print(f"[5/9] OK   COMMITMENT event recorded: {len(issue_evts)} ledger lines "
          f"(double-entry: {[e[1] for e in issue_evts]})")

    # Step 6: redeem voucher
    inv_before = find_inventory(point_code, input_code)
    inv_qty_before = float(inv_before[0]) if inv_before and inv_before[0] else 0
    if inv_qty_before < qty:
        print(f"[6/9] WARN inventory ({inv_qty_before}) < voucher qty ({qty}). "
              f"Redemption will likely fail with 'insufficient stock'. "
              f"Restock {point_code}.{input_code} before re-running.")

    rstatus, rresp = redeem_voucher(voucher_code, point_code,
                                     applicant['full_name'])
    if rstatus != 200:
        print(f"[6/9] FAIL redeem HTTP {rstatus}: {rresp}")
        return 1
    rstate = rresp.get("status") if isinstance(rresp, dict) else None
    if rstate != "ok":
        print(f"[6/9] FAIL redeem returned status='{rstate}': {rresp}")
        return 1
    redemption_code = rresp.get("redemptionCode") if isinstance(rresp, dict) else None
    print(f"[6/9] OK   voucher redeemed via API, redemption_code={redemption_code}")

    # Step 7: voucher state → redeemed AND redemption row exists
    new_state = query_one(
        "SELECT c_status FROM app_fd_im_voucher WHERE c_code = %s",
        (voucher_code,))
    if not new_state or new_state[0] != "redeemed":
        print(f"[7/9] FAIL voucher status after redeem: {new_state}, expected 'redeemed'")
        return 1
    rdm_row = query_one(
        "SELECT c_code, c_redemption_point, c_quantity_redeemed "
        "FROM app_fd_im_voucher_redemption WHERE c_voucher_code = %s LIMIT 1",
        (voucher_code,))
    if not rdm_row:
        print(f"[7/9] FAIL no redemption row for voucher {voucher_code}")
        return 1
    print(f"[7/9] OK   voucher → redeemed, redemption row written: {rdm_row[0]}")

    # Step 8: inventory decremented + EXPENSE event posted
    inv_after = find_inventory(point_code, input_code)
    inv_qty_after = float(inv_after[0]) if inv_after and inv_after[0] else 0
    expected_after = inv_qty_before - qty
    if abs(inv_qty_after - expected_after) > 0.01:
        print(f"[8/9] FAIL inventory delta wrong: {inv_qty_before} → {inv_qty_after}, "
              f"expected {expected_after}")
        return 1
    # Slice 11 (partial redemption) appended the redemption code to the
    # idempotency key so multi-call redemptions get distinct keys. The
    # current shape is voucher_redeemed:{voucher}:{redemption}; fall back
    # to the legacy single-form for older data, both rows confirm the
    # event landed.
    idem_redeem_new = f"voucher_redeemed:{voucher_code}:{redemption_code}"
    idem_redeem_legacy = f"voucher_redeemed:{voucher_code}"
    expense_evts = (find_budget_events_by_idempotency(idem_redeem_new)
                    or find_budget_events_by_idempotency(idem_redeem_legacy))
    if not expense_evts:
        print(f"[8/9] FAIL no budget events for redemption "
              f"(tried key={idem_redeem_new!r} and legacy={idem_redeem_legacy!r})")
        return 1
    print(f"[8/9] OK   inventory {inv_qty_before} → {inv_qty_after} (Δ-{qty}); "
          f"EXPENSE event: {len(expense_evts)} ledger lines")

    # Step 9: distribution receipt
    if skip_receipt:
        print("[9/9] SKIP distribution receipt (--skip-receipt)")
    else:
        rcpt_status, rcpt_resp, rcpt_code = insert_distribution_receipt(
            voucher_code, applicant['full_name'])
        if rcpt_status != 200:
            print(f"[9/9] FAIL receipt insert HTTP {rcpt_status}: {rcpt_resp}")
            return 1
        # Verify the row landed
        rcpt_row = query_one(
            "SELECT c_code, c_voucher_code, c_distribution_date "
            "FROM app_fd_im_distribution WHERE c_code = %s",
            (rcpt_code,))
        if not rcpt_row:
            print(f"[9/9] FAIL receipt row {rcpt_code} not found in DB")
            return 1
        print(f"[9/9] OK   distribution receipt {rcpt_row[0]} written, "
              f"voucher_code={rcpt_row[1]}")

    print()
    print("=" * 78)
    print("All e2e contracts hold")
    print("=" * 78)
    return 0


def run_report_smoke():
    print()
    print("--- IM Report Smoke ---")
    reports = [
        "dl_im_voucher_utilisation",
        "dl_im_consumption_by_centre",
        "dl_im_supplier_performance",
        "dl_im_campaign_reconciliation",
        "dl_im_dashboard_summary",
    ]
    failures = []
    for r in reports:
        path = os.path.join(DATALISTS_DIR, r + ".json")
        try:
            with open(path) as f:
                spec = json.load(f)
            sql = spec["binder"]["properties"]["sql"]
            # psycopg2 interprets `%` as placeholder; escape literal %
            # (FormatPercent in to_char etc.) when params is empty.
            sql_escaped = sql.replace("%", "%%")
            rows = query_all(sql_escaped)
            print(f"  OK   {r:<38s}: {len(rows)} rows")
        except Exception as e:
            err = str(e)[:120]
            print(f"  FAIL {r:<38s}: {err}")
            failures.append((r, err))
    if failures:
        print()
        print(f"{len(failures)}/5 reports failed.")
        return 1
    print()
    print("All 5 reports execute cleanly")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--skip-receipt", action="store_true",
                    help="Skip the distribution-receipt step.")
    ap.add_argument("--reports-only", action="store_true",
                    help="Skip the e2e chain, only run report smoke.")
    args = ap.parse_args()

    rc = 0
    if not args.reports_only:
        rc |= run_e2e(skip_receipt=args.skip_receipt)
        if rc != 0:
            print("\ne2e chain failed — skipping report smoke.")
            return rc
    rc |= run_report_smoke()
    return rc


if __name__ == "__main__":
    sys.exit(main())
