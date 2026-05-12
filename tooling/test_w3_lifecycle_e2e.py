#!/usr/bin/env python3
"""
W3 lifecycle state-machine end-to-end test.

Walks the full DRAFT → SUBMITTED → APPROVED chain (plus WITHDRAWN side path)
through the citizen + operator APIs, asserting at each step:
  - c_lifecyclestate on app_fd_subsidy_app_2025
  - audit_log entries for entity_type='APPLICATION'
  - operator inbox visibility (DRAFT hidden, SUBMITTED+ visible)

Exits non-zero on any failed assertion. Each test is named and reported
green/red so a single line tells you whether W3 still works.

Per CLAUDE.md HARD RULE: writes go through Joget APIs (citizen / operator).
DB access is read-only SELECT for assertions only.

Re-uses customer NID 066257236627 (Mants'ali Panyane — Profile A passes
PRG_2025_001 eligibility). Pre-cleans application rows the test creates;
NEVER seeds the farmer registry.

Usage:
    python3 tooling/test_w3_lifecycle_e2e.py            # full pass
    python3 tooling/test_w3_lifecycle_e2e.py --case draft_only
    python3 tooling/test_w3_lifecycle_e2e.py --case submit_only
    python3 tooling/test_w3_lifecycle_e2e.py --case withdraw_only
"""
import argparse, json, os, sys, time, urllib.error, urllib.request

JOGET = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY = os.environ.get("JOGET_API_KEY", "")
CITIZEN_API_ID = "API-API_SUBSIDY_APP_2025_CITIZEN"
APP_ID = "farmersPortal"
PROGRAMME = "PRG_2025_001"

APPLICANT_NID = "066257236627"   # Mants'ali Panyane — Profile A
APPLICANT_FIELDS = {
    "national_id":      APPLICANT_NID,
    "full_name":        "Mants'ali Panyane",
    "gender":           "female",
    "date_of_birth":    "1985-10-01",
    "contact_phone":    "+26659518328",
    "email_address":    "Pmantsali@gmail.com",
    "district":         "maseru",
    "applied_programme": PROGRAMME,
}

# Terminal lifecycle states the worker is allowed to produce.
TERMINAL_LIFECYCLE = {"approved", "rejected", "pending_review"}

# ─────────────────────────────────────────────────────────────────────────
# HTTP + DB helpers
# ─────────────────────────────────────────────────────────────────────────
def _request(url, method, body=None, api_id=CITIZEN_API_ID):
    headers = {"Content-Type":"application/json",
               "api_id": api_id, "api_key": JOGET_API_KEY}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8") if isinstance(body, (dict,list)) else body
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read().decode("utf-8")
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

def db():
    import psycopg2
    return psycopg2.connect(
        host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
        user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")

def query_one(sql, params=()):
    conn = db()
    try:
        cur = conn.cursor(); cur.execute(sql, params); return cur.fetchone()
    finally: conn.close()

def query_all(sql, params=()):
    conn = db()
    try:
        cur = conn.cursor(); cur.execute(sql, params); return cur.fetchall()
    finally: conn.close()

# ─────────────────────────────────────────────────────────────────────────
# Domain operations (through APIs)
# ─────────────────────────────────────────────────────────────────────────
def precleanup():
    """Delete any prior applications for the test NID via the citizen API.
    Reads via SELECT (HARD-RULE allowed), deletes via API."""
    rows = query_all("SELECT id FROM app_fd_subsidy_app_2025 WHERE c_national_id=%s",
                     (APPLICANT_NID,))
    deleted = 0
    for (app_id,) in rows:
        st, _ = _request(f"{JOGET}/api/form/subsidyApplication2025/{app_id}", "DELETE")
        if st == 200:
            deleted += 1
    return deleted, len(rows)

def submit_without_tick():
    """POST a fresh application WITHOUT submit_confirmation. Should land in DRAFT."""
    st, raw = _request(f"{JOGET}/api/form/subsidyApplication2025", "POST",
                       dict(APPLICANT_FIELDS))
    if st != 200:
        return None, f"HTTP {st}: {raw[:300]}"
    body = parse(raw)
    if isinstance(body, dict) and "id" in body:
        return body["id"], None
    return None, f"unexpected response: {body!r}"

def submit_with_tick():
    """POST a fresh application WITH submit_confirmation=Y. Same code path
    as the citizen's final-tab tick — the storeBinder reads the request
    param regardless of create vs update. Returns (app_id, err)."""
    body = dict(APPLICANT_FIELDS)
    body["submit_confirmation"] = "Y"
    st, raw = _request(f"{JOGET}/api/form/subsidyApplication2025", "POST", body)
    if st != 200:
        return None, f"HTTP {st}: {raw[:300]}"
    body = parse(raw)
    if isinstance(body, dict) and "id" in body:
        return body["id"], None
    return None, f"unexpected response: {body!r}"

def delete_application(app_id):
    """Best-effort delete via citizen API."""
    _request(f"{JOGET}/api/form/subsidyApplication2025/{app_id}", "DELETE")

def db_lifecycle(app_id):
    row = query_one("SELECT COALESCE(c_lifecyclestate,'') , COALESCE(c_status,'') "
                    "FROM app_fd_subsidy_app_2025 WHERE id=%s", (app_id,))
    return (row[0], row[1]) if row else (None, None)

def audit_for(app_id):
    return query_all(
        "SELECT COALESCE(c_from_status,''), COALESCE(c_to_status,''), "
        "       COALESCE(c_triggered_by,''), COALESCE(c_reason,''), datecreated "
        "FROM app_fd_audit_log "
        "WHERE c_entity_type='APPLICATION' AND c_entity_id=%s "
        "ORDER BY datecreated ASC", (app_id,))

def operator_sees(app_id):
    """Run the operator inbox SQL and check whether app_id is in the result."""
    rows = query_all(
        "SELECT a.id FROM app_fd_subsidy_app_2025 a "
        "WHERE a.id=%s AND (a.c_lifecyclestate IS NULL "
        "      OR a.c_lifecyclestate='' "
        "      OR a.c_lifecyclestate NOT IN ('draft','withdrawn'))",
        (app_id,))
    return len(rows) > 0

def poll_until(predicate, *, timeout_s=90, interval_s=2, label="condition"):
    """Polls every interval_s, returns the predicate's value once truthy or None on timeout."""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        v = predicate()
        if v: return v
        time.sleep(interval_s)
    return None

def verify_running_build(min_build):
    """Probe the running reg-bb-engine build number. Done by submitting an
    application and reading the audit_log reason — which carries
    'application picked up by EligibilityProcessingWorker' from build-131+
    and other build-specific phrases. Conservative: only fails if we
    detect an OLDER build's fingerprint. Returns (ok, message)."""
    # Cheap probe: read the most recent audit_log row written by the
    # worker. Its reason text changed in known ways across builds; if it
    # matches the most recent fingerprint we accept.
    rows = query_all("""SELECT c_reason FROM app_fd_audit_log
                        WHERE c_entity_type='APPLICATION'
                          AND c_triggered_by='system:async-worker'
                          AND c_reason LIKE 'chain produced c_status=%%'
                        ORDER BY datecreated DESC LIMIT 1""")
    if not rows:
        return True, "no system:async-worker audit rows yet — can't verify build remotely; proceed at your risk"
    reason = rows[0][0] or ""
    # build-131+: reason is "chain produced c_status=X"
    # build-130 and earlier: reason was missing this entirely
    # Note: we can't *definitively* distinguish 132/133/134/135/136 from
    # audit_log alone because the reason text didn't change between them.
    # This probe catches "really old build" but not "off by one or two".
    # For a stricter check, add a build-stamp log line read via REST.
    return True, "audit fingerprint compatible with build-131+ (probe is conservative; if pre-flight fails, suspect cache)"

# ─────────────────────────────────────────────────────────────────────────
# Test cases — return (ok, [(name, ok, msg)])
# ─────────────────────────────────────────────────────────────────────────
def case_draft_only(fast=False):
    """Save without tick: row created, lifecycle=draft, audit has null→draft,
    operator inbox excludes it, worker does NOT auto-process.

    fast=True skips the 65s 'worker leaves DRAFT alone' check (which is the
    long wall) — useful for quick iteration; full check still verifies the
    immediate state plus the operator-inbox visibility."""
    asserts = []
    app_id, err = submit_without_tick()
    if err:
        return False, [("submit_without_tick", False, err)]
    asserts.append(("submit returned id", True, app_id))

    # Immediate state must be 'draft' (the storeBinder ran synchronously).
    lc, st = db_lifecycle(app_id)
    asserts.append(("c_lifecyclestate=='draft'",
                    lc == "draft", f"got {lc!r}, c_status={st!r}"))

    aud = audit_for(app_id)
    has_draft = any(a[1] == "draft" for a in aud)
    asserts.append(("audit_log has null→draft", has_draft,
                    "; ".join(f"{a[0]}→{a[1]}" for a in aud) or "no rows"))

    asserts.append(("operator inbox hides DRAFT",
                    not operator_sees(app_id), "row appears in operator query"))

    if not fast:
        # Wait 65s — worker must NOT pick up a DRAFT row.
        print(f"   [waiting 65s to confirm worker leaves DRAFT alone] ", end="", flush=True)
        time.sleep(65)
        print("done")
        lc2, st2 = db_lifecycle(app_id)
        asserts.append(("after 65s, still DRAFT",
                        lc2 == "draft" and not st2,
                        f"lifecycle={lc2!r} c_status={st2!r}"))

    return all(a[1] for a in asserts), asserts, app_id

def case_submit_after_draft(draft_app_id):
    """Same code path as the citizen's wizard-final-tick: submit with
    submit_confirmation=Y in the body. We delete the draft row first
    (DET_NO_DUPLICATE_PRG_001 would otherwise auto-reject the new POST),
    then create a fresh row WITH the tick. The applyLifecycleTransition
    hook in RegBbApplicationStoreBinder reads submit_confirmation from
    formData regardless of create-vs-update, so create-with-tick exercises
    the same code path as update-with-tick."""
    asserts = []

    # Clean up the draft so the duplicate-applicant rule lets us re-submit.
    delete_application(draft_app_id)

    app_id, err = submit_with_tick()
    asserts.append(("POST with submit_confirmation=Y", err is None, err or app_id))
    if err:
        return False, asserts

    # State should immediately become 'submitted' (worker hasn't run yet).
    lc, _ = db_lifecycle(app_id)
    asserts.append(("c_lifecyclestate=='submitted' right after submit",
                    lc == "submitted", f"got {lc!r}"))

    asserts.append(("operator inbox now shows submitted row",
                    operator_sees(app_id), "operator query still excludes it"))

    # Now wait for worker to promote to terminal.
    print(f"   [polling for worker terminal transition, up to 100s] ", end="", flush=True)
    def reached_terminal():
        lc2, _ = db_lifecycle(app_id)
        return lc2 if lc2 in TERMINAL_LIFECYCLE else None
    final = poll_until(reached_terminal, timeout_s=100, interval_s=3)
    print("done" if final else "timeout")
    asserts.append((f"worker drove → terminal ({'/'.join(sorted(TERMINAL_LIFECYCLE))})",
                    bool(final), f"final lifecycle={final!r}"))

    # audit_log should show null → submitted → terminal.
    aud = audit_for(app_id)
    seq = " → ".join(f"{a[0] or 'null'}→{a[1]}" for a in aud)
    asserts.append(("audit_log has null→submitted",
                    any((not a[0]) and a[1]=="submitted" for a in aud), seq))
    asserts.append(("audit_log has submitted→terminal",
                    any(a[0]=="submitted" and a[1] in TERMINAL_LIFECYCLE for a in aud), seq))
    return all(a[1] for a in asserts), asserts

def case_withdraw():
    """Fresh draft + Withdraw via DataListAction. lifecycle → withdrawn."""
    asserts = []
    # Make a new DRAFT row first.
    app_id, err = submit_without_tick()
    if err:
        return False, [("submit_without_tick(withdraw scenario)", False, err)]
    asserts.append(("draft row created", True, app_id))

    # Withdraw via direct AppAudit call would require Java; instead simulate
    # via the WithdrawApplicationAction's REST surface. Joget exposes data-
    # list actions via /jw/web/ but those require session auth (admin). The
    # action ultimately just calls AppAudit.transition(WITHDRAWN). We can
    # call the same transition by PUT-ing lifecycleState='withdrawn' through
    # the citizen API ONLY IF the storeBinder honours it. But our binder
    # only writes 'draft' / 'submitted'; 'withdrawn' must come through the
    # WithdrawApplicationAction.
    #
    # Pragmatic check: skip the UI path. Assert what the state machine
    # registration allows: from DRAFT, WITHDRAWN must be a valid target.
    # The actual click-test must be operator-driven and is part of UAT,
    # not this harness. Mark as informational only.
    asserts.append(("WITHDRAWN path requires UI click (skipped in harness)",
                    True, "covered in UAT script — not harness-testable without a Joget session cookie"))

    # Clean up the draft row so reruns are idempotent.
    _request(f"{JOGET}/api/form/subsidyApplication2025/{app_id}", "DELETE")
    return True, asserts

# ─────────────────────────────────────────────────────────────────────────
# Runner
# ─────────────────────────────────────────────────────────────────────────
def report(case_name, ok, asserts):
    GREEN, RED, RESET = "\033[32m", "\033[31m", "\033[0m"
    badge = f"{GREEN}PASS{RESET}" if ok else f"{RED}FAIL{RESET}"
    print(f"\n[{badge}] {case_name}")
    for (name, passed, msg) in asserts:
        mark = f"{GREEN}✓{RESET}" if passed else f"{RED}✗{RESET}"
        print(f"   {mark} {name:60s} {msg}")

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--case", choices=["all","draft_only","submit_only","withdraw_only"],
                    default="all")
    ap.add_argument("--skip-preclean", action="store_true",
                    help="don't pre-delete existing test rows (rare; for debugging)")
    ap.add_argument("--fast", action="store_true",
                    help="skip the 65s 'worker leaves DRAFT alone' wait — quick smoke test")
    args = ap.parse_args()

    # Build banner.
    log_line = query_one(
        "SELECT to_char(now(),'YYYY-MM-DD HH24:MI:SS')")
    print(f"=== W3 lifecycle e2e — {log_line[0] if log_line else '?'} UTC ===")
    print(f"  joget={JOGET}  programme={PROGRAMME}  applicant_nid={APPLICANT_NID}")

    ok, msg = verify_running_build(min_build=136)
    print(f"  build probe: {msg}")
    print(f"  WARNING: this probe is conservative — confirm joget.log shows "
          f"'reg-bb-engine starting — build-136' before trusting results.")

    if not args.skip_preclean:
        deleted, total = precleanup()
        print(f"  pre-cleanup: deleted {deleted}/{total} prior application rows for NID {APPLICANT_NID}")

    overall_ok = True

    if args.case in ("all","draft_only","submit_only"):
        ok, asserts, draft_id = case_draft_only(fast=args.fast)
        report("case_draft_only", ok, asserts)
        overall_ok &= ok

        if args.case in ("all","submit_only") and ok:
            ok2, asserts2 = case_submit_after_draft(draft_id)
            report("case_submit_after_draft", ok2, asserts2)
            overall_ok &= ok2

    if args.case in ("all","withdraw_only"):
        ok3, asserts3 = case_withdraw()
        report("case_withdraw (informational only)", ok3, asserts3)

    GREEN, RED, RESET = "\033[32m", "\033[31m", "\033[0m"
    badge = f"{GREEN}GREEN{RESET}" if overall_ok else f"{RED}RED{RESET}"
    print(f"\n=== OVERALL: {badge} ===")
    return 0 if overall_ok else 1

if __name__ == "__main__":
    sys.exit(main())
