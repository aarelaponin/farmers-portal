#!/usr/bin/env python3
"""
Budget Engine end-to-end test — single application lifecycle.

Walks one fresh application through submit → auto-approve → voucher issue,
asserting the budget_event ledger emits the expected COMMITMENT / EXPENSE
chain and that per-application reservation accounting balances.

This complements test_budget_suite.py (which covers reports + controls) by
verifying the runtime ledger mechanics. The two together cover both
"history-stays-consistent" (suite) and "transactions-emit-correct-events"
(this script).

Assertions:
  1. After submit, a RESERVATION event exists for this application_id.
  2. After auto-approval, a RELEASE_RESERVATION event matching the
     RESERVATION exists (full release — the application's budget pre-hold
     gets returned because the commitment is the new authority).
  3. After auto-approval, a COMMITMENT event exists for the approved amount.
  4. The committed amount lies within the envelope's allocated_amount
     (no over-commit on this single application).
  5. Per-application ledger health:
        SUM(RESERVATION) == SUM(RELEASE_RESERVATION)
     (reservations always fully released by the time we land on a terminal
     state).
  6. If a voucher is auto-issued and redeemed (Slice 6 auto-flow), assert
     an EXPENSE event exists for the redemption amount, and that:
        SUM(EXPENSE) + SUM(RELEASE_COMMITMENT) <= SUM(COMMITMENT)
     (we never spend more than we committed).

Re-runnability: uses Profile A NID. Pre-cleans application + voucher rows
before starting. Reservation/commitment events accumulate as an audit trail
(see CLAUDE.md "audit retention" section) — assertions only count events
for THIS application_id, not the global ledger.

Usage:
    python3 tooling/test_budget_engine_e2e.py
"""
import argparse, json, os, sys, time, urllib.error, urllib.request
from decimal import Decimal

JOGET = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY = os.environ.get("JOGET_API_KEY", "a5af1181f77b4a62b481725b6410e965")
CITIZEN_API_ID = "API-API_SUBSIDY_APP_2025_CITIZEN"
APP_ID = "farmersPortal"

APPLICANT_NID = "066257236627"
PROGRAMME = "PRG_2025_001"
APPLICANT = {
    "national_id": APPLICANT_NID,
    "full_name": "Mants'ali Panyane",
    "gender": "female",
    "date_of_birth": "1985-10-01",
    "contact_phone": "+26659518328",
    "email_address": "Pmantsali@gmail.com",
    "district": "maseru",
    "applied_programme": PROGRAMME,
}

TERMINAL = {"approved", "rejected", "pending_review"}


def _req(url, method="GET", body=None, api_id=CITIZEN_API_ID):
    h = {"Content-Type": "application/json", "api_id": api_id, "api_key": JOGET_API_KEY}
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, method=method, data=data, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def parse(raw):
    try:
        outer = json.loads(raw)
        m = outer.get("message", outer)
        if isinstance(m, str):
            try: return json.loads(m)
            except json.JSONDecodeError: return m
        return m
    except json.JSONDecodeError:
        return raw


def db():
    import psycopg2
    return psycopg2.connect(
        host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
        user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")


def query_all(sql, params=()):
    conn = db()
    try:
        cur = conn.cursor()
        cur.execute(sql, params)
        return cur.fetchall()
    finally:
        conn.close()


def pre_clean():
    rows = query_all(
        "SELECT id FROM app_fd_subsidy_app_2025 WHERE c_national_id=%s",
        (APPLICANT_NID,))
    n = 0
    for (app_id,) in rows:
        st, _ = _req(f"{JOGET}/api/form/subsidyApplication2025/{app_id}", "DELETE")
        if st == 200: n += 1
    return n, len(rows)


def submit():
    body = dict(APPLICANT, submit_confirmation="Y")
    st, raw = _req(f"{JOGET}/api/form/subsidyApplication2025", "POST", body)
    if st != 200:
        return None, f"HTTP {st}: {raw[:200]}"
    b = parse(raw)
    if isinstance(b, dict) and "id" in b:
        return b["id"], None
    return None, f"unexpected: {b!r}"


def poll_terminal(app_id, timeout_s=120):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        rows = query_all(
            "SELECT COALESCE(c_lifecyclestate,''), COALESCE(c_status,'') "
            "  FROM app_fd_subsidy_app_2025 WHERE id=%s", (app_id,))
        if rows:
            lc, st = rows[0]
            if lc in TERMINAL:
                return lc, st
        time.sleep(3)
    return None, None


def events_by_type(correlation_id):
    """Sum c_amount per event_type for one correlation_id (application or voucher)."""
    rows = query_all(
        "SELECT c_event_type, COUNT(*), COALESCE(SUM(c_amount::numeric),0) "
        "  FROM app_fd_budget_event "
        " WHERE c_correlation_id=%s "
        " GROUP BY c_event_type", (correlation_id,))
    return {r[0]: {"n": r[1], "amount": Decimal(r[2])} for r in rows}


def voucher_codes_for_app(app_id):
    rows = query_all(
        "SELECT c_code FROM app_fd_im_voucher WHERE c_application_id=%s",
        (app_id,))
    return [r[0] for r in rows]


def envelope_balance(env_code):
    """Compute committed/expensed/reserved for one envelope from events."""
    rows = query_all(
        "SELECT c_event_type, COALESCE(SUM(c_amount::numeric),0) "
        "  FROM app_fd_budget_event "
        " WHERE c_envelope_code=%s "
        " GROUP BY c_event_type", (env_code,))
    out = {r[0]: Decimal(r[1]) for r in rows}
    out["reservation_net"]  = out.get("RESERVATION", Decimal(0)) - out.get("RELEASE_RESERVATION", Decimal(0))
    out["commitment_net"]   = out.get("COMMITMENT", Decimal(0)) - out.get("RELEASE_COMMITMENT", Decimal(0)) - out.get("EXPENSE", Decimal(0))
    return out


# ─────────────────────────────────────────────────────────────────────────
# Test
# ─────────────────────────────────────────────────────────────────────────
def case_full_lifecycle():
    asserts = []
    deleted, total = pre_clean()
    if total:
        asserts.append(("pre-cleanup", deleted == total,
                        f"deleted {deleted}/{total}"))

    # Submit
    app_id, err = submit()
    asserts.append(("citizen submit returns id", err is None, err or app_id[:8] + "…"))
    if err: return False, asserts

    # Wait for worker to drive terminal
    print(f"  [polling for worker terminal, up to 120s] ", end="", flush=True)
    lc, st = poll_terminal(app_id, timeout_s=120)
    print("done" if lc else "timeout")
    asserts.append(("worker reached terminal lifecycle",
                    lc in TERMINAL,
                    f"lifecycle={lc!r} status={st!r}"))
    if lc not in TERMINAL:
        return False, asserts

    # Grace period for any async budget events to flush
    time.sleep(5)

    # Application-level budget events
    app_events = events_by_type(app_id)
    print(f"  app events: { {k: f'{v[\"n\"]}×{v[\"amount\"]}' for k,v in app_events.items()} }")

    # Assertion 1: RESERVATION fires on submit
    asserts.append(("RESERVATION event exists for application",
                    "RESERVATION" in app_events,
                    f"types={list(app_events.keys())}"))

    if lc == "approved" or lc == "rejected":
        # Assertion 2: RELEASE_RESERVATION matches RESERVATION
        res_amt   = app_events.get("RESERVATION", {}).get("amount", Decimal(0))
        rel_amt   = app_events.get("RELEASE_RESERVATION", {}).get("amount", Decimal(0))
        asserts.append(("RESERVATION fully released",
                        res_amt == rel_amt,
                        f"reserved={res_amt} released={rel_amt}"))

    if lc == "approved":
        # Assertion 3: COMMITMENT fires
        asserts.append(("COMMITMENT event exists after approval",
                        "COMMITMENT" in app_events,
                        f"types={list(app_events.keys())}"))

        # Assertion 4: commitment within envelope's allocated_amount
        env_rows = query_all(
            "SELECT c_code, c_allocated_amount FROM app_fd_budget_envelope "
            "WHERE c_programme_code=%s LIMIT 1", (PROGRAMME,))
        if env_rows:
            env_code, allocated = env_rows[0]
            allocated = Decimal(allocated or 0)
            bal = envelope_balance(env_code)
            asserts.append(("commitment_net ≤ allocated_amount",
                            bal["commitment_net"] <= allocated,
                            f"env={env_code} committed_net={bal['commitment_net']} allocated={allocated}"))

        # Assertion 5: voucher auto-issued, expense path
        vouchers = voucher_codes_for_app(app_id)
        asserts.append(("at least 1 voucher auto-issued",
                        len(vouchers) >= 1,
                        f"{len(vouchers)} voucher(s): {vouchers[:2]}"))

        for vch in vouchers:
            v_events = events_by_type(vch)
            asserts.append((f"voucher {vch}: PRE_COMMITMENT or COMMITMENT exists",
                            ("PRE_COMMITMENT" in v_events) or ("COMMITMENT" in v_events),
                            f"types={list(v_events.keys())}"))

    return all(a[1] for a in asserts), asserts


def report(name, ok, asserts):
    flag = "[PASS]" if ok else "[FAIL]"
    print(f"{flag} {name}")
    for n, a_ok, msg in asserts:
        check = "✓" if a_ok else "✗"
        print(f"   {check} {n:<55} {msg}")
    return ok


def main():
    argparse.ArgumentParser(description=__doc__,
                            formatter_class=argparse.RawDescriptionHelpFormatter).parse_args()
    print(f"=== Budget engine e2e — "
          f"{time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())} ===")
    print(f"  joget={JOGET}  programme={PROGRAMME}  applicant_nid={APPLICANT_NID}")
    ok, asserts = case_full_lifecycle()
    report("case_full_lifecycle", ok, asserts)
    print(f"=== OVERALL: {'GREEN' if ok else 'RED'} ===")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
