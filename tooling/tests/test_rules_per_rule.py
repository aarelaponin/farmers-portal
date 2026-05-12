"""
Layer 1 — Per-rule structural and behavioural tests.

For every rule in `qa_rule` and `mm_determinant`:
- Schema validity (recognised scope, severity, etc.)
- SQL parses against the live DB (with #recordId# substituted)
- SQL references real tables / columns (no typos in `app_fd_*`)
- For qa_rule: the probe returns 0 rows when run against a known-clean
  record (i.e., one already marked 'verified' in qa_record_status).

This is the per-rule regression net mentioned in `docs/architecture/test_strategy.md`.
Catches: column-rename drift, table typos, broken DSL expressions.

NB — the deeper "fire when expected, clear when fixed" fixtures are
deferred to a separate harness (test_rules_engine_equivalence.py) since
they require seeding records into transactional tables, which is more
invasive than a per-rule structural smoke.
"""
import json
import re

import psycopg2
import pytest

from conftest import PG_HOST, PG_DATABASE, PG_USER, PG_PASSWORD


# ---------------------------------------------------------------------------
# Rule fetch (module load)
# ---------------------------------------------------------------------------

def _fetch_rules(table, sql):
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, connect_timeout=10)
    try:
        cur = conn.cursor()
        cur.execute(sql)
        return cur.fetchall()
    finally:
        conn.close()


_QA_RULES = _fetch_rules(
    "qa_rule",
    """SELECT c_rulecode, c_severity, c_serviceid, c_tabcode,
              c_affectedfields, c_message, c_rulescript, c_isactive
       FROM app_fd_qa_rule
       WHERE c_isactive = 'Y' OR c_isactive IS NULL
       ORDER BY c_rulecode"""
)

_MM_DETERMINANTS = _fetch_rules(
    "mm_determinant",
    """SELECT c_code, c_scope, c_ruletype, c_registrationid, c_rulejson,
              c_targetvalue, c_failmessage, c_score
       FROM app_fd_mm_determinant
       ORDER BY c_code"""
)

# Pull one known-clean recordId per service from qa_record_status.
# A rule from service S should be evaluated against a verified record
# from S — running a service-X rule against a service-Y record produces
# false positives because the record isn't of the type the rule expects.
def _fetch_clean_recordid_per_service():
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, connect_timeout=10)
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT DISTINCT ON (c_serviceid) c_serviceid, c_recordid
               FROM app_fd_qa_record_status
               WHERE c_status = 'verified'"""
        )
        return {svc: rec for svc, rec in cur.fetchall()}
    finally:
        conn.close()


_CLEAN_BY_SERVICE = _fetch_clean_recordid_per_service()


# ---------------------------------------------------------------------------
# Schema constraints
# ---------------------------------------------------------------------------

VALID_QA_SEVERITIES = {"ERROR", "WARNING", "INFO"}
VALID_MM_SCOPES = {
    "eligibility", "applicability", "initial_status_assignment",
    "decision_to_status", "programme_launch_gate", "budget_amount",
    "field", "bot_pull", "score_based", "pending_review", "quality",
}
VALID_MM_RULETYPES = {
    # In active use today (live in mm_determinant):
    "assignment",  # rules that compute / assign a value (e.g. budget_amount, decision_to_status)
    "inclusion",   # boolean rules that include / exclude an applicant (eligibility-style)
    # Reserved for future use (cited in ADR-001 / ADR-003 grammar):
    "exclusion", "score", "boolean", "validation",
}


# ---------------------------------------------------------------------------
# qa_rule tests — parametrize by ruleCode for fine-grained reporting
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("rule", _QA_RULES, ids=[r[0] for r in _QA_RULES])
def test_qa_rule_severity_recognised(rule):
    """Every qa_rule severity must be in the valid set."""
    code, severity = rule[0], rule[1]
    assert severity in VALID_QA_SEVERITIES, (
        f"{code}: severity {severity!r} not in {VALID_QA_SEVERITIES}"
    )


@pytest.mark.parametrize("rule", _QA_RULES, ids=[r[0] for r in _QA_RULES])
def test_qa_rule_has_message(rule):
    """Every qa_rule must have a non-trivial operator-facing message."""
    code, message = rule[0], rule[5]
    assert message and len(message.strip()) >= 5, (
        f"{code}: message is empty or too short ({message!r})"
    )


@pytest.mark.parametrize("rule", _QA_RULES, ids=[r[0] for r in _QA_RULES])
def test_qa_rule_has_rulescript(rule):
    """Every qa_rule must have a non-empty SQL probe."""
    code, script = rule[0], rule[6]
    assert script and script.strip(), (
        f"{code}: ruleScript is empty"
    )
    assert "SELECT" in script.upper(), (
        f"{code}: ruleScript doesn't contain a SELECT — not a probe"
    )


@pytest.mark.parametrize("rule", _QA_RULES, ids=[r[0] for r in _QA_RULES])
def test_qa_rule_uses_record_id_token(rule):
    """Every qa_rule should reference #recordId# in its SQL —
    otherwise it doesn't scope to the record being saved."""
    code, script = rule[0], rule[6]
    if "#recordId#" not in script:
        # Some legitimate rules might be record-agnostic (e.g. a global
        # "stale data > 30 days" probe), but we don't have any today;
        # flag for review.
        pytest.fail(
            f"{code}: ruleScript doesn't reference #recordId# — confirm "
            f"this is intentional. Probes that don't scope to the current "
            f"record fire on every save and are usually wrong."
        )


@pytest.mark.parametrize("rule", _QA_RULES, ids=[r[0] for r in _QA_RULES])
def test_qa_rule_sql_parses_with_substituted_token(pg_conn, rule):
    """Every qa_rule's SQL must parse and execute against the live DB
    when #recordId# is substituted with a placeholder. Catches: typos
    in table names, columns that don't exist, syntax errors."""
    code, script = rule[0], rule[6]
    # Substitute with a UUID-shaped string that's unlikely to match
    # anything but is type-compatible with id columns.
    test_id = "rule-test-00000000-0000-0000-0000-000000000000"
    sql = script.replace("#recordId#", test_id)
    cur = pg_conn.cursor()
    try:
        cur.execute(sql)
        cur.fetchall()
    except Exception as e:
        pytest.fail(
            f"{code}: SQL failed to parse:\n  {type(e).__name__}: "
            f"{str(e)[:300]}"
        )
    finally:
        cur.close()


@pytest.mark.parametrize("rule", _QA_RULES, ids=[r[0] for r in _QA_RULES])
def test_qa_rule_returns_zero_rows_on_clean_record(pg_conn, rule):
    """Every qa_rule's SQL, run against a verified record from the SAME
    service, should return 0 rows. If a rule fires on a clean record of
    its own service, the rule's SQL is over-eager — false-positive.

    Skip the rule if no verified record exists for its service — that's
    a coverage gap, not a rule defect."""
    code, script, service_id = rule[0], rule[6], rule[2]
    clean_record = _CLEAN_BY_SERVICE.get(service_id)
    if not clean_record:
        pytest.skip(
            f"{code}: no verified record available for service "
            f"{service_id!r} — skipping clean-record probe"
        )
        return
    sql = script.replace("#recordId#", clean_record)
    cur = pg_conn.cursor()
    try:
        cur.execute(sql)
        rows = cur.fetchall()
    except Exception:
        pytest.skip(f"{code}: SQL didn't execute (covered by parse test)")
        return
    finally:
        cur.close()
    assert len(rows) == 0, (
        f"{code}: probe returned {len(rows)} row(s) when run against "
        f"verified record {clean_record!r} from its own service "
        f"({service_id}). Rule's WHERE clause is too loose, or the "
        f"'verified' record is stale."
    )


# ---------------------------------------------------------------------------
# mm_determinant tests — schema-level only (DSL evaluation tested via L4)
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("rule", _MM_DETERMINANTS, ids=[r[0] for r in _MM_DETERMINANTS])
def test_mm_determinant_scope_recognised(rule):
    """Every mm_determinant scope must be in the valid set. Catches
    typos like 'eligbility' that would break the evaluator routing."""
    code, scope = rule[0], rule[1]
    assert scope in VALID_MM_SCOPES, (
        f"{code}: scope {scope!r} not in {VALID_MM_SCOPES}"
    )


@pytest.mark.parametrize("rule", _MM_DETERMINANTS, ids=[r[0] for r in _MM_DETERMINANTS])
def test_mm_determinant_ruletype_recognised(rule):
    """For eligibility-scope rules, ruletype must be in the valid set.
    Quality-scope rules use severity instead and have ruletype = NULL —
    that's the dual-discipline shape from ADR-031 D43."""
    code, scope, ruletype = rule[0], rule[1], rule[2]
    if scope == "quality":
        # Quality rules use severity, not ruletype. Skip the check.
        if ruletype is None or ruletype == "":
            return
        # If a quality rule sets ruletype anyway, that's fine but unusual —
        # don't fail it, just continue to the standard check below.
    assert ruletype in VALID_MM_RULETYPES, (
        f"{code}: ruletype {ruletype!r} not in {VALID_MM_RULETYPES}"
    )


def test_mm_determinant_registration_refs_resolve(cur):
    """Every mm_determinant.registrationid (where set) should reference
    a live programme in mm_registration."""
    cur.execute(
        "SELECT c_code FROM app_fd_mm_registration WHERE c_code IS NOT NULL"
    )
    programmes = {r[0] for r in cur.fetchall()}

    orphans = []
    for r in _MM_DETERMINANTS:
        code, _, _, regid, *_ = r
        if regid and regid not in programmes:
            orphans.append((code, regid))

    if orphans:
        msg = "\n".join(f"  {code}: registrationId={regid!r} not in mm_registration"
                        for code, regid in orphans)
        pytest.fail(f"{len(orphans)} mm_determinant rule(s) reference missing programmes:\n{msg}")


def test_inventory_summary():
    """Print summary of what was tested (visibility, not assertion)."""
    services = ", ".join(f"{svc}={rec[:8]}..." for svc, rec in _CLEAN_BY_SERVICE.items())
    print(
        f"\n  Active qa_rule rows tested:    {len(_QA_RULES)}\n"
        f"  mm_determinant rows tested:    {len(_MM_DETERMINANTS)}\n"
        f"  Clean records by service:      {services or '(none found)'}"
    )
