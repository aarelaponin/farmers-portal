#!/usr/bin/env python3
"""
L4-1 / L4-3 parity-test runner.

Submits the cartesian product (fixtures × programmes) through the citizen
API endpoint and asserts each application's resulting eligibility outcome
against a ground-truth expectation. Reads endpoint ids from the seeder's
generated app/seeds/.api_endpoints.json so this script doesn't have to know
about the (code → apiId) convention.

Usage:
    python3 tooling/run_l4_scenarios.py                # run all 12 scenarios
    python3 tooling/run_l4_scenarios.py --filter A     # only Profile A scenarios
    python3 tooling/run_l4_scenarios.py --no-cleanup   # leave applications in DB

Per CLAUDE.md HARD RULE: writes to subsidy_app_2025 go via the citizen API
which uses Joget's FormDataDao internally. Reads use the operator API in
read_only mode. Cleanup also goes through the API.
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))
APP_ID         = os.environ.get("JOGET_APP_ID",   "farmersPortal")

_HERE = os.path.dirname(os.path.abspath(__file__))
ENDPOINTS_FILE = os.path.normpath(os.path.join(
    _HERE, os.pardir, "app", "seeds", ".api_endpoints.json"))
TEST_FILES_DIR = os.path.normpath(os.path.join(
    _HERE, os.pardir, "app", "seeds", "test-files"))

# ---------------------------------------------------------------------------
# Test fixtures and ground truth
# ---------------------------------------------------------------------------
#
# Three farmer-registry-resident NIDs × four programmes = 12 scenarios.
# Each FIXTURE row carries the inputs the citizen wizard requires; each
# row of EXPECTED carries the disposition+status combo we expect from
# the engine after submit. Names mirror the L4-1 manual dry-run profiles.

FIXTURES = {
    "A": {
        "label":                  "Mants'ali Panyane (lowlands maseru)",
        "national_id":            "066257236627",
        "full_name":              "Mants'ali Panyane",
        "gender":                 "female",
        "date_of_birth":          "1985-10-01",
        "contact_phone":          "+26659518328",
        "district":               "maseru",
        "agro_zone":              "lowlands",
        "village_name":           "maseru-mabote",
        "area_hectares":          "3.5",
        "tenure_type":            "customary",
        "primary_crop":           "maize",
        "block_farming_member":   "yes",
        "cooperative_name":       "Mabote Block Farming Cooperative",
        "drought_affected_decl":  "no",
    },
    "B": {
        "label":                  "Mosito Nkhets'e (mountains thaba_tseka)",
        "national_id":            "0000022116106827",
        "full_name":              "Mosito Nkhets'e",
        "gender":                 "male",
        "date_of_birth":          "1987-04-17",
        "contact_phone":          "+26663082744",
        "district":               "thaba_tseka",
        "agro_zone":              "mountains",
        # No mountain village seeded in md_village; using one of the
        # mokhotlong codes is fine for cross-cutting eligibility — none
        # of the rules read village_name, only the cascading select does.
        "village_name":           "mokhotlong-sehonghong",
        "area_hectares":          "2.8",
        "tenure_type":            "customary",
        "primary_crop":           "wheat",
        "block_farming_member":   "no",
        "cooperative_name":       "",
        "drought_affected_decl":  "no",
    },
    "C": {
        "label":                  "Lineo Mathaba (lowlands mafeteng drought=Y)",
        "national_id":            "0123456789101",
        "full_name":              "Lineo 10 Mathaba",
        "gender":                 "female",
        "date_of_birth":          "1964-02-05",
        "contact_phone":          "+26657504979",
        "district":               "mafeteng",
        "agro_zone":              "lowlands",
        "village_name":           "mafeteng-phamotse",
        "area_hectares":          "4.0",
        "tenure_type":            "customary",
        "primary_crop":           "maize",
        "block_farming_member":   "no",
        "cooperative_name":       "",
        "drought_affected_decl":  "yes",
    },
    # ---- L4-3 extension fixtures -----------------------------------
    # Picked to exercise rule branches the first three didn't: D for the
    # "applicant qualifies for nothing" all-rejected case + the >5ha
    # smallholder boundary; E for the senqu_river_valley arm of
    # DET_MOUNTAINS_OR_SENQU and the quthing arm of DET_DROUGHT_DECLARED.
    "D": {
        "label":                  "Monkhi Ntsalong (foothills berea, 6.5ha)",
        "national_id":            "000044509",
        "full_name":              "Monkhi Ntsalong Bethuel",
        "gender":                 "male",
        "date_of_birth":          "1975-05-05",
        "contact_phone":          "56707808",
        "district":               "berea",
        "agro_zone":              "foothills",       # not in lowlands NOR mountains/senqu
        "village_name":           "berea-mantsi",
        "area_hectares":          "6.5",             # >5 → fails DET_SMALLHOLDER
        "tenure_type":            "customary",
        "primary_crop":           "maize",
        "block_farming_member":   "no",
        "cooperative_name":       "",
        "drought_affected_decl":  "no",
    },
    "E": {
        "label":                  "Rorisang Monokoane (senqu quthing drought=Y)",
        "national_id":            "011251286921",
        "full_name":              "Rorisang Monokoane",
        "gender":                 "female",
        "date_of_birth":          "1988-07-29",
        "contact_phone":          "50172975",
        "district":               "quthing",         # in drought list (not mafeteng)
        "agro_zone":              "senqu_river_valley",  # OR-branch of mountain rule
        "village_name":           "mokhotlong-letseng",  # using mokhotlong code as proxy
        "area_hectares":          "1.2",             # ≤5 → passes DET_SMALLHOLDER
        "tenure_type":            "customary",
        "primary_crop":           "wheat",
        "block_farming_member":   "no",
        "cooperative_name":       "",
        "drought_affected_decl":  "yes",
    },
}

PROGRAMMES = ["PRG_2025_001", "PRG_2025_002", "PRG_2025_003", "PRG_2025_004"]

# Ground truth: (profile, programme) → (expected_disposition, expected_status).
# Derived from the determinant rules in lesotho-mm-fixture.yaml combined
# with all_must_pass strategy on every programme post-D-004 fix.
EXPECTED = {
    ("A", "PRG_2025_001"): ("eligibility_passed",          "auto_approved"),
    ("A", "PRG_2025_002"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("A", "PRG_2025_003"): ("eligibility_passed",          "auto_approved"),
    ("A", "PRG_2025_004"): ("eligibility_failed_mandatory", "auto_rejected"),

    ("B", "PRG_2025_001"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("B", "PRG_2025_002"): ("eligibility_passed",          "auto_approved"),
    ("B", "PRG_2025_003"): ("eligibility_passed",          "auto_approved"),
    ("B", "PRG_2025_004"): ("eligibility_failed_mandatory", "auto_rejected"),

    ("C", "PRG_2025_001"): ("eligibility_passed",          "auto_approved"),
    ("C", "PRG_2025_002"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("C", "PRG_2025_003"): ("eligibility_passed",          "auto_approved"),
    ("C", "PRG_2025_004"): ("eligibility_passed",          "auto_approved"),

    # Profile D — registered foothills/berea applicant with a 6.5ha farm.
    # Doesn't qualify for ANY of the four programmes: foothills isn't
    # lowlands (001) or mountains/senqu (002), 6.5 > 5 fails smallholder
    # (003), berea isn't in the drought-declared list (004). Stress-tests
    # that the engine cleanly rejects all four without false-positives
    # in any rule branch.
    ("D", "PRG_2025_001"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("D", "PRG_2025_002"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("D", "PRG_2025_003"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("D", "PRG_2025_004"): ("eligibility_failed_mandatory", "auto_rejected"),

    # Profile E — registered senqu_river_valley applicant in quthing
    # (drought-declared, not mafeteng). Exercises:
    #   * The senqu_river_valley arm of DET_MOUNTAINS_OR_SENQU (passes 002)
    #   * The quthing arm of DET_DROUGHT_DECLARED (passes 004 — not the
    #     mafeteng case Profile C covers).
    # Fails 001 (not lowlands), passes 002, 003, 004.
    ("E", "PRG_2025_001"): ("eligibility_failed_mandatory", "auto_rejected"),
    ("E", "PRG_2025_002"): ("eligibility_passed",          "auto_approved"),
    ("E", "PRG_2025_003"): ("eligibility_passed",          "auto_approved"),
    ("E", "PRG_2025_004"): ("eligibility_passed",          "auto_approved"),
}


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def _request(url, method, body=None, api_id=None, content_type="application/json"):
    headers = {
        "Content-Type": content_type,
        "api_id":       api_id,
        "api_key":      JOGET_API_KEY,
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


def delete(url, api_id):
    return _request(url, "DELETE", None, api_id)


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


# ---------------------------------------------------------------------------
# Endpoint resolution
# ---------------------------------------------------------------------------

def load_endpoints():
    if not os.path.exists(ENDPOINTS_FILE):
        sys.exit(f"FATAL: {ENDPOINTS_FILE} not found. Run seed.py first to "
                 "provision the mm_api endpoints.")
    with open(ENDPOINTS_FILE) as f:
        m = json.load(f)
    required = ["API_SUBSIDY_APP_2025_CITIZEN", "API_SUBSIDY_APP_2025_OPERATOR"]
    missing = [c for c in required if c not in m]
    if missing:
        sys.exit(f"FATAL: missing endpoints {missing} in {ENDPOINTS_FILE}.")
    citizen  = m["API_SUBSIDY_APP_2025_CITIZEN"]
    operator = m["API_SUBSIDY_APP_2025_OPERATOR"]
    # URL convention verified against the running instance: AppFormAPI
    # endpoints live at /jw/api/form/<formDefId>/<action>. The api_id is
    # sent as a header, NOT a URL path component (api-builder/.../service/
    # ApiBuilder.java reads it from request headers). The first cut of
    # this script encoded api_id in the URL like /api/<apiId>/form/... and
    # got 400 Bad Request before any handler ran — the URL must start
    # with the plugin's tag (form/<formDefId>) right after /api/.
    return {
        "citizen_api_id":  citizen["apiId"],
        "operator_api_id": operator["apiId"],
        "citizen_post_url":  f"{JOGET_BASE_URL}/api/form/{citizen['formId']}",
        "operator_get_url":  f"{JOGET_BASE_URL}/api/form/{operator['formId']}",
    }


# ---------------------------------------------------------------------------
# Scenario runner
# ---------------------------------------------------------------------------

_BUDGET_API_ID = "API-BUDGET"


def trigger_eligibility_worker():
    """Drain the pending applications queue (ADR-030). Best-effort —
    if the endpoint isn't enabled in API Builder yet, fall through and
    let fetch_outcome's polling handle the lag. The shared api_key
    (JOGET_API_KEY) covers every API on this instance."""
    url = JOGET_BASE_URL + "/api/budget/run-eligibility-worker"
    try:
        _request(url, "POST", b"{}", _BUDGET_API_ID)
    except Exception:
        pass


def submit_application(endpoints, profile_key, programme_code):
    fixture = FIXTURES[profile_key]
    body = {k: v for k, v in fixture.items() if k != "label"}
    body["applied_programme"] = programme_code
    # Citizen API uses POST / on AppFormAPI's CRUD shape. Joget echoes the
    # saved row (with generated id) back under message.
    status, raw = post(endpoints["citizen_post_url"],
                       body, endpoints["citizen_api_id"])
    parsed = parse(raw)
    if status != 200:
        return None, f"HTTP {status}: {str(raw)[:300]}"
    # AppFormAPI's "post:/" returns the persisted row; id is in `id`.
    if isinstance(parsed, dict) and "id" in parsed:
        return parsed["id"], None
    return None, f"unexpected response shape: {str(parsed)[:300]}"


def fetch_outcome(endpoints, application_id, retries=8, delay=0.5):
    """Poll the citizen-side read API until the row's eligibility_outcome
    is populated. Switched from operator-side because the operator form
    is kernel-rendered (MetaScreenElement) and has no static field set
    in app_form.json — so AppFormAPI's auto-discovery of fields-to-return
    yields an empty schema. The citizen form is a normal Joget form with
    explicit eligibility_outcome + status fields, so reading through it
    returns those values.
    Returns (disposition, status, raw_outcome)."""
    url = f"{endpoints['citizen_post_url']}/{application_id}"
    for _ in range(retries):
        status, raw = get(url, endpoints["citizen_api_id"])
        if status == 200:
            parsed = parse(raw)
            if isinstance(parsed, dict):
                outcome_str = parsed.get("eligibility_outcome", "")
                row_status  = parsed.get("status", "")
                if outcome_str:
                    try:
                        outcome = json.loads(outcome_str)
                    except json.JSONDecodeError:
                        outcome = {"raw": outcome_str}
                    return outcome.get("disposition", ""), row_status, outcome
                if row_status:
                    return "(outcome-empty)", row_status, {}
        time.sleep(delay)
    return "(timeout)", "(timeout)", {}


def precleanup_for_fixtures(endpoints):
    """Delete any prior applications from our three test NIDs so the
    DET_NO_DUPLICATE_PRG_xxx applicability rule doesn't fire on a fresh
    automated run.

    Read path is a direct DB SELECT (CLAUDE.md HARD RULE permits read-only
    SQL — only writes are forbidden). Delete path is the citizen API's
    DELETE /{recordId} which goes through Joget's FormDataDao, so cache
    invalidation and audit attribution are correct.

    Why not just call /list: AppFormAPI doesn't actually implement a
    /list endpoint despite ENABLED_PATHS advertising one. Calling it
    returns {} silently. Source-verified May 2026 against
    api-builder/apibuilder_plugins/.../AppFormAPI.java — only single-row
    operations exist (/, /{id}, /saveOrUpdate, /addWithFiles, etc).
    """
    try:
        import psycopg2
    except ImportError:
        print("pre-cleanup: psycopg2 not installed — skipping. Install with "
              "`pip install psycopg2-binary` or run via tooling/.venv.")
        return 0
    nids = {f["national_id"] for f in FIXTURES.values()}
    conn = psycopg2.connect(
        host=os.environ.get("PGHOST", "joget-pgsql-sa.postgres.database.azure.com"),
        dbname=os.environ.get("PGDATABASE", "jogetdb"),
        user=os.environ.get("PGUSER", "jogetadmin"),
        password=os.environ.get("PGPASSWORD", os.environ.get("PGPASSWORD", "")),
        port=int(os.environ.get("PGPORT", "5432")),
        sslmode="require")
    cur = conn.cursor()
    cur.execute(
        "SELECT id FROM app_fd_subsidy_app_2025 WHERE c_national_id IN %s",
        (tuple(nids),))
    ids_to_delete = [r[0] for r in cur.fetchall()]
    conn.close()
    if not ids_to_delete:
        print("Pre-cleanup: no prior applications for fixture NIDs")
        return 0
    deleted = 0
    for app_id in ids_to_delete:
        url = f"{endpoints['citizen_post_url']}/{app_id}"
        st, _ = delete(url, endpoints["citizen_api_id"])
        if st == 200:
            deleted += 1
    print(f"Pre-cleanup: deleted {deleted}/{len(ids_to_delete)} prior applications")
    return deleted


def cleanup_application(endpoints, application_id):
    url = f"{endpoints['citizen_post_url']}/{application_id}"
    status, raw = delete(url, endpoints["citizen_api_id"])
    return status == 200


def run(filter_profile=None, cleanup=True):
    endpoints = load_endpoints()
    print(f"Citizen API:  {endpoints['citizen_post_url']}")
    print(f"Operator API: {endpoints['operator_get_url']}")
    print()

    # Wipe any prior submissions for our three fixture NIDs so the
    # duplicate-guard applicability rule (D-003) doesn't auto-reject
    # everything as "you already applied".
    precleanup_for_fixtures(endpoints)
    print()

    profiles = ["A", "B", "C", "D", "E"] if filter_profile is None else [filter_profile]
    rows = []
    submitted_ids = []
    for p in profiles:
        for prog in PROGRAMMES:
            label_left = f"{p} {FIXTURES[p]['label']:<48s} → {prog}"
            exp_disp, exp_status = EXPECTED[(p, prog)]
            print(f"--- {label_left}")
            print(f"    expected: {exp_disp:<32s} / {exp_status}")
            app_id, err = submit_application(endpoints, p, prog)
            if err:
                print(f"    SUBMIT FAILED: {err}")
                rows.append((p, prog, exp_disp, exp_status, "(submit-failed)",
                             "(submit-failed)", False))
                continue
            submitted_ids.append(app_id)
            print(f"    submitted id={app_id}")
            # ADR-030 (post-2026-05-05 redesign): submit is fast (no
            # eligibility chain inside the request). Trigger the worker
            # to process the just-submitted application before fetching
            # its outcome. In production this happens on a schedule;
            # the test triggers it explicitly so feedback is fast.
            trigger_eligibility_worker()
            disp, st, _outcome = fetch_outcome(endpoints, app_id)
            ok = (disp == exp_disp and st == exp_status)
            mark = "OK" if ok else "FAIL"
            print(f"    actual:   {disp:<32s} / {st}   [{mark}]")
            rows.append((p, prog, exp_disp, exp_status, disp, st, ok))
            print()

    # Summary
    passed = sum(1 for r in rows if r[6])
    print("=" * 78)
    print(f"L4-1 parity: {passed}/{len(rows)} scenarios passed")
    print("=" * 78)
    for p, prog, ed, es, ad, ast, ok in rows:
        mark = "OK" if ok else "FAIL"
        print(f"  [{mark}] {p} × {prog}: expected {ed}/{es}  actual {ad}/{ast}")

    if cleanup and submitted_ids:
        print()
        print(f"Cleaning up {len(submitted_ids)} application rows...")
        for aid in submitted_ids:
            cleanup_application(endpoints, aid)
        print("Cleanup done.")

    return 0 if passed == len(rows) else 1


def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--filter", choices=["A", "B", "C", "D", "E"],
                    help="Run only one profile's scenarios.")
    ap.add_argument("--no-cleanup", action="store_true",
                    help="Leave submitted applications in the DB after the run.")
    args = ap.parse_args()
    return run(filter_profile=args.filter, cleanup=not args.no_cleanup)


if __name__ == "__main__":
    sys.exit(main())
