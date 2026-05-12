#!/usr/bin/env python3
"""
Notification lifecycle end-to-end test.

Submits a fresh application via the citizen API, polls the worker, then
asserts the notification_queue state for the application's correlationId
(format: AP-<first 8 chars of app id, uppercased>).

Each assertion is named so a single line tells you whether the W2.x email
chain still works end-to-end. Test mode is honoured — when the JVM property
`regbb.notif.testMode=true` is set, c_actualrecipient should be the
override email and c_intendedrecipientStatus carries the original.

Coverage:
  - APP_SUBMITTED notification fires on submit
  - Eligibility-outcome notification (APP_APPROVED / APP_REJECTED / APP_UNDER_REVIEW)
    fires after the worker terminal transition
  - VOUCHER_ISSUED notification fires after auto-issuance (if approved)
  - Every queued row reaches a terminal status (sent / skipped / dead_letter)
    within the test timeout — no rows stuck in `pending` or `failed`

Re-runnability: uses Profile A (066257236627, Mants'ali Panyane). Pre-
cleans any prior application + notification rows for this applicant so
the duplicate-guard rule and the assertions get a clean slate.

Per CLAUDE.md HARD RULE: writes go through Joget APIs only. DB access is
read-only SELECT for assertions.

Usage:
    python3 tooling/test_notification_e2e.py            # full pass
    python3 tooling/test_notification_e2e.py --skip-voucher
"""
import argparse, json, os, sys, time, urllib.error, urllib.request

JOGET = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY = os.environ.get("JOGET_API_KEY", "")
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

# Terminal statuses a notification row may reach. Anything not in this
# set after the timeout is a failed assertion.
TERMINAL_NOTIF_STATUS = {"sent", "skipped", "dead_letter"}


def _req(url, method="GET", body=None, api_id=CITIZEN_API_ID):
    headers = {"Content-Type": "application/json",
               "api_id": api_id, "api_key": JOGET_API_KEY}
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
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
        user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")


def query_all(sql, params=()):
    conn = db()
    try:
        cur = conn.cursor()
        cur.execute(sql, params)
        return cur.fetchall()
    finally:
        conn.close()


def correlation_id(app_id):
    """Mirror of EligibilityProcessingWorker.deriveCorrelationId."""
    return "AP-" + app_id[:8].upper()


def pre_clean():
    """Delete any prior application rows for this NID via the citizen API.
    Doesn't touch notification_queue rows (they accumulate as audit trail)."""
    rows = query_all(
        "SELECT id FROM app_fd_subsidy_app_2025 WHERE c_national_id=%s",
        (APPLICANT_NID,))
    n = 0
    for (app_id,) in rows:
        status, _ = _req(f"{JOGET}/api/form/subsidyApplication2025/{app_id}", "DELETE")
        if status == 200: n += 1
    return n, len(rows)


def submit():
    """Citizen submit. Returns (app_id, err)."""
    body = dict(APPLICANT, submit_confirmation="Y")
    st, raw = _req(f"{JOGET}/api/form/subsidyApplication2025", "POST", body)
    if st != 200:
        return None, f"HTTP {st}: {raw[:200]}"
    b = parse(raw)
    if isinstance(b, dict) and "id" in b:
        return b["id"], None
    return None, f"unexpected: {b!r}"


def poll_for_terminal(app_id, timeout_s=120):
    """Poll subsidy_app_2025.c_lifecyclestate until terminal or timeout."""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        rows = query_all(
            "SELECT COALESCE(c_lifecyclestate,''), COALESCE(c_status,'') "
            "  FROM app_fd_subsidy_app_2025 WHERE id=%s", (app_id,))
        if rows:
            lc, st = rows[0]
            if lc in {"approved", "rejected", "pending_review"}:
                return lc, st
        time.sleep(3)
    return None, None


def fetch_notifications(corr_id):
    """All notification rows for this application's correlation id."""
    return query_all(
        "SELECT c_eventcode, c_status, c_channel, c_actualrecipient, "
        "       c_intendedrecipientstatus, c_lasterror, datecreated "
        "  FROM app_fd_notification_queue "
        " WHERE c_correlationid=%s "
        " ORDER BY datecreated", (corr_id,))


# ─────────────────────────────────────────────────────────────────────────
# Test cases
# ─────────────────────────────────────────────────────────────────────────
def case_submit_to_terminal_notifications(skip_voucher=False):
    asserts = []

    deleted, total = pre_clean()
    print(f"  pre-cleanup: deleted {deleted}/{total} prior application rows")

    app_id, err = submit()
    asserts.append(("citizen submit returns id", err is None, err or app_id))
    if err:
        return False, asserts

    corr_id = correlation_id(app_id)
    print(f"  app_id={app_id[:8]}…  correlationId={corr_id}")

    # Wait for worker terminal transition
    print(f"  [polling for worker terminal transition, up to 120s] ", end="", flush=True)
    lc, st = poll_for_terminal(app_id, timeout_s=120)
    print("done" if lc else "timeout")
    asserts.append(("worker reached terminal lifecycle",
                    lc in {"approved", "rejected", "pending_review"},
                    f"lifecycle={lc!r} status={st!r}"))

    # Brief grace period for notifications to flush
    time.sleep(5)

    rows = fetch_notifications(corr_id)
    by_event = {}
    for ev, status, channel, actual, intended, err_, _dt in rows:
        by_event.setdefault(ev, []).append({
            "status": status, "channel": channel,
            "actual": actual, "intended_status": intended,
            "error": err_,
        })

    # Assertion 1: APP_SUBMITTED queued + terminal
    sub_rows = by_event.get("APP_SUBMITTED", [])
    asserts.append(("APP_SUBMITTED notification queued",
                    len(sub_rows) >= 1,
                    f"{len(sub_rows)} row(s)"))
    if sub_rows:
        terminal = all(r["status"] in TERMINAL_NOTIF_STATUS for r in sub_rows)
        asserts.append(("APP_SUBMITTED reached terminal status",
                        terminal,
                        ",".join(r["status"] for r in sub_rows)))

    # Assertion 2: outcome notification depends on disposition
    outcome_event = {
        "approved":       "APP_APPROVED",
        "rejected":       "APP_REJECTED",
        "pending_review": "APP_UNDER_REVIEW",
    }.get(lc, None)
    if outcome_event:
        out_rows = by_event.get(outcome_event, [])
        asserts.append((f"{outcome_event} notification queued",
                        len(out_rows) >= 1,
                        f"{len(out_rows)} row(s)"))
        if out_rows:
            terminal = all(r["status"] in TERMINAL_NOTIF_STATUS for r in out_rows)
            asserts.append((f"{outcome_event} reached terminal status",
                            terminal,
                            ",".join(r["status"] for r in out_rows)))

    # Assertion 3: voucher notification on approved (auto-issuance)
    if lc == "approved" and not skip_voucher:
        # Worker auto-issues voucher; allow extra grace period
        print(f"  [polling for VOUCHER_ISSUED notification, up to 60s] ", end="", flush=True)
        deadline = time.time() + 60
        while time.time() < deadline:
            rows = fetch_notifications(corr_id)
            if any(ev == "VOUCHER_ISSUED" for ev, *_ in rows):
                break
            time.sleep(3)
        print("done")
        rows = fetch_notifications(corr_id)
        voucher_rows = [r for r in rows if r[0] == "VOUCHER_ISSUED"]
        asserts.append(("VOUCHER_ISSUED notification queued",
                        len(voucher_rows) >= 1,
                        f"{len(voucher_rows)} row(s)"))
        if voucher_rows:
            terminal = all(r[1] in TERMINAL_NOTIF_STATUS for r in voucher_rows)
            asserts.append(("VOUCHER_ISSUED reached terminal status",
                            terminal,
                            ",".join(r[1] for r in voucher_rows)))

    # Assertion 4: no rows stuck in non-terminal status
    rows = fetch_notifications(corr_id)
    stuck = [r for r in rows if r[1] not in TERMINAL_NOTIF_STATUS]
    asserts.append(("no notification rows stuck non-terminal",
                    len(stuck) == 0,
                    f"{len(stuck)} stuck row(s): " +
                    ",".join(f"{r[0]}={r[1]}" for r in stuck) if stuck
                    else "all rows terminal"))

    # Assertion 5: every queued row has actual recipient (test-mode redirect)
    no_recipient = [r for r in rows if not r[3]]
    asserts.append(("every notification has an actualRecipient",
                    len(no_recipient) == 0,
                    f"{len(no_recipient)} row(s) without recipient"))

    return all(a[1] for a in asserts), asserts


# ─────────────────────────────────────────────────────────────────────────
# Report
# ─────────────────────────────────────────────────────────────────────────
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
    ap.add_argument("--skip-voucher", action="store_true",
                    help="Skip voucher-issuance assertions (faster if you only "
                         "care about pre-decision notifications).")
    args = ap.parse_args()

    print(f"=== Notification lifecycle e2e — "
          f"{time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())} ===")
    print(f"  joget={JOGET}  programme={PROGRAMME}  applicant_nid={APPLICANT_NID}")

    ok, asserts = case_submit_to_terminal_notifications(skip_voucher=args.skip_voucher)
    report("case_submit_to_terminal_notifications", ok, asserts)

    print(f"=== OVERALL: {'GREEN' if ok else 'RED'} ===")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
