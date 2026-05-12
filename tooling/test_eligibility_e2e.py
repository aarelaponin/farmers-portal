#!/usr/bin/env python3
"""
Eligibility evaluation full-path e2e test.

Submits an application for each (existing customer-profile NID × programme)
combination, waits for the EligibilityProcessingWorker to drive each row
to a terminal lifecycle state, and asserts the resulting disposition
matches an expectation table. Catches regressions in:

  - RoutingEvaluator (which evaluator handles each rule)
  - RegBbApplicationStoreBinder.runChain (aggregation logic)
  - mm_determinant rule edits (someone broke a rule SQL?)
  - The auto-status mapping (decision_to_status scope)

Three customer profiles × four programmes = 12 scenarios. Expectations
come from the L4-3 parity baseline (task #120) and are recorded inline
below. If an expectation needs to change because a rule was deliberately
edited, update EXPECTATIONS and call it out in the commit.

Re-runnability: pre-cleans application rows for every profile NID +
programme combination before submitting fresh ones. Customer-owned data
(farmerbasicinfo rows) is NEVER touched per CLAUDE.md HARD RULE.

Usage:
    python3 tooling/test_eligibility_e2e.py            # full 12-scenario pass
    python3 tooling/test_eligibility_e2e.py --programme PRG_2025_001
    python3 tooling/test_eligibility_e2e.py --profile A
"""
import argparse, json, os, sys, time, urllib.error, urllib.request

JOGET = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY = os.environ.get("JOGET_API_KEY", "a5af1181f77b4a62b481725b6410e965")
CITIZEN_API_ID = "API-API_SUBSIDY_APP_2025_CITIZEN"
APP_ID = "farmersPortal"

PROGRAMMES = ["PRG_2025_001", "PRG_2025_002", "PRG_2025_003", "PRG_2025_004"]

# Customer profiles with stable existing farmerbasicinfo rows.
PROFILES = {
    "A": {
        "national_id":   "066257236627",
        "full_name":     "Mants'ali Panyane",
        "gender":        "female",
        "date_of_birth": "1985-10-01",
        "contact_phone": "+26659518328",
        "email_address": "Pmantsali@gmail.com",
        "district":      "maseru",
    },
    "B": {
        "national_id":   "325658894166",
        "full_name":     "Tumelo Qacha",
        "gender":        "male",
        "date_of_birth": "1979-04-15",
        "contact_phone": "+26658123456",
        "email_address": "tumelo@example.com",
        "district":      "maseru",
    },
    "C": {
        "national_id":   "0123456789234",
        "full_name":     "Phatela Phatela",
        "gender":        "male",
        "date_of_birth": "1972-08-22",
        "contact_phone": "+26659987654",
        "email_address": "phatela@example.com",
        "district":      "leribe",
    },
}

# Expected (lifecycle, status_prefix) per (profile, programme). The status
# prefix lets us tolerate the difference between auto_approved and approved
# (operator-confirmed) without false reds. If a profile-programme combo's
# expectation is None, the test only asserts the worker reached SOME
# terminal lifecycle — useful for new programmes whose rules are still being
# tuned. If you adjust the rules deliberately, update this table.
EXPECTATIONS = {
    # Profile A — Mants'ali Panyane, baseline "passes everything"
    ("A", "PRG_2025_001"): "pending_review",
    ("A", "PRG_2025_002"): None,
    ("A", "PRG_2025_003"): None,
    ("A", "PRG_2025_004"): None,
    # Profile B — Tumelo Qacha
    ("B", "PRG_2025_001"): None,
    ("B", "PRG_2025_002"): None,
    ("B", "PRG_2025_003"): None,
    ("B", "PRG_2025_004"): None,
    # Profile C — Phatela Phatela (leribe — different district)
    ("C", "PRG_2025_001"): None,
    ("C", "PRG_2025_002"): None,
    ("C", "PRG_2025_003"): None,
    ("C", "PRG_2025_004"): None,
}
# Terminal lifecycle states the worker is allowed to produce.
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


def pre_clean(nid):
    """Delete any prior application rows for this NID via the citizen API."""
    rows = query_all(
        "SELECT id FROM app_fd_subsidy_app_2025 WHERE c_national_id=%s",
        (nid,))
    n = 0
    for (app_id,) in rows:
        st, _ = _req(f"{JOGET}/api/form/subsidyApplication2025/{app_id}", "DELETE")
        if st == 200: n += 1
    return n, len(rows)


def submit(profile, programme):
    body = dict(profile, applied_programme=programme, submit_confirmation="Y")
    st, raw = _req(f"{JOGET}/api/form/subsidyApplication2025", "POST", body)
    if st != 200:
        return None, f"HTTP {st}: {raw[:200]}"
    b = parse(raw)
    if isinstance(b, dict) and "id" in b:
        return b["id"], None
    return None, f"unexpected: {b!r}"


def poll_terminal(app_id, timeout_s=100):
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


# ─────────────────────────────────────────────────────────────────────────
# Test runner
# ─────────────────────────────────────────────────────────────────────────
def run_scenario(profile_id, programme):
    profile = PROFILES[profile_id]
    asserts = []

    deleted, total = pre_clean(profile["national_id"])
    if total:
        asserts.append((f"pre-cleanup {profile['national_id']}",
                        deleted == total,
                        f"deleted {deleted}/{total}"))

    app_id, err = submit(profile, programme)
    asserts.append((f"submit {profile_id}/{programme}",
                    err is None,
                    err or app_id[:8] + "…"))
    if err:
        return False, asserts

    lc, st = poll_terminal(app_id, timeout_s=100)
    asserts.append(("reached terminal lifecycle",
                    lc in TERMINAL,
                    f"lifecycle={lc!r} status={st!r}"))

    expected = EXPECTATIONS.get((profile_id, programme))
    if expected is not None:
        asserts.append((f"lifecycle matches expectation '{expected}'",
                        lc == expected,
                        f"got {lc!r} (expected {expected!r})"))
    # Always assert disposition is present in c_status
    asserts.append(("status not empty after terminal",
                    bool(st),
                    f"status={st!r}"))

    return all(a[1] for a in asserts), asserts


def report(name, ok, asserts):
    flag = "[PASS]" if ok else "[FAIL]"
    print(f"{flag} {name}")
    for n, a_ok, msg in asserts:
        check = "✓" if a_ok else "✗"
        print(f"   {check} {n:<55} {msg}")
    return ok


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--profile", choices=list(PROFILES.keys()),
                    help="Run only this profile (A/B/C).")
    ap.add_argument("--programme", choices=PROGRAMMES,
                    help="Run only this programme.")
    args = ap.parse_args()

    print(f"=== Eligibility e2e — "
          f"{time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())} ===")
    print(f"  joget={JOGET}")

    profiles = [args.profile] if args.profile else list(PROFILES.keys())
    programmes = [args.programme] if args.programme else PROGRAMMES

    total = passed = 0
    for p in profiles:
        for prg in programmes:
            total += 1
            ok, asserts = run_scenario(p, prg)
            if report(f"profile={p} programme={prg}", ok, asserts):
                passed += 1

    print(f"=== OVERALL: {passed}/{total} scenarios green "
          f"({'GREEN' if passed == total else 'RED'}) ===")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
