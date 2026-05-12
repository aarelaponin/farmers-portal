"""
Layer 5 — Rule-engine equivalence harness.

This is the regression net for ADR-031 (unified rule engine).
Per `docs/architecture/test_strategy.md` §13:

  > Before any slice of ADR-031 begins, author this harness and run
  > it green against the legacy engine alone. That confirms the test
  > infrastructure works. Only then begin Slice A.

Two modes:

1. Determinism (the "today" check) — snapshot the legacy engine's
   outputs, snapshot again, assert byte-for-byte equivalence. Confirms
   outputs are stable and the corpus is well-defined.

2. Equivalence (the "Slice C" check) — snapshot legacy engine, snapshot
   unified engine, assert byte-for-byte equivalence. Today the unified
   engine doesn't exist; the placeholder mode raises NotImplementedError
   so it doesn't silently pass with no comparison.

The harness scope is intentionally focused on the QUALITY side
(`qa_rule` probes). Eligibility-side equivalence is covered by the
L4 parity test (`tooling/run_l4_scenarios.py`) which is already part
of `make test-l4`. Adding a duplicate eligibility check here would
create two sources of truth.

CLI usage (for cross-session baseline comparisons):

    # Capture baseline before Slice A
    python3 tooling/tests/test_rules_engine_equivalence.py --save baseline.json

    # After Slice A / B / C, compare current state to baseline
    python3 tooling/tests/test_rules_engine_equivalence.py --compare baseline.json
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from typing import Any

import psycopg2

# pytest is needed to RUN the test functions but not for the CLI path
# (--print-signature / --save / --compare). Make it optional so a venv
# without pytest can still drive the CLI.
try:
    import pytest  # type: ignore
except ImportError:  # pragma: no cover
    pytest = None  # type: ignore

from conftest import PG_HOST, PG_DATABASE, PG_USER, PG_PASSWORD


# ---------------------------------------------------------------------------
# Snapshot construction — pure SQL, deterministic
# ---------------------------------------------------------------------------

def _open_conn():
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, connect_timeout=10)
    conn.autocommit = True
    return conn


def _fetch_active_qa_rules(cur) -> list[dict]:
    cur.execute(
        """SELECT c_rulecode, c_severity, c_serviceid, c_tabcode,
                  c_affectedfields, c_message, c_rulescript
           FROM app_fd_qa_rule
           WHERE c_isactive = 'Y' OR c_isactive IS NULL
           ORDER BY c_rulecode"""
    )
    cols = ["ruleCode", "severity", "serviceId", "tabCode",
            "affectedFields", "message", "ruleScript"]
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def _fetch_corpus_records(cur) -> list[dict]:
    """Records to probe each rule against. We use every row in
    qa_record_status (verified, has_warnings, has_errors) — that's the
    complete set of records the engine has ever evaluated. The
    deterministic shape: each (ruleCode × recordId) pair is one
    fixture entry."""
    cur.execute(
        """SELECT c_serviceid, c_formid, c_recordid, c_status,
                  c_errorcount, c_warningcount
           FROM app_fd_qa_record_status
           WHERE c_recordid IS NOT NULL AND c_recordid != ''
           ORDER BY c_serviceid, c_recordid"""
    )
    cols = ["serviceId", "formId", "recordId",
            "currentStatus", "currentErrors", "currentWarnings"]
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def _evaluate_rule_against_record(cur, rule: dict, record: dict) -> dict:
    """Run one rule's SQL probe against one record. Returns a small
    deterministic dict with the outcome — *not* timestamps, *not*
    actor identity, *not* anything that can drift between runs."""
    sql = rule["ruleScript"].replace("#recordId#", record["recordId"])
    try:
        cur.execute(sql)
        rows = cur.fetchall()
        return {
            "fired": len(rows) > 0,
            "rowCount": len(rows),
            "error": None,
        }
    except Exception as e:
        # SQL parse / execution error. Captured in the snapshot so a
        # rule that starts erroring across the migration is visible.
        return {
            "fired": None,
            "rowCount": None,
            "error": f"{type(e).__name__}: {str(e)[:200]}",
        }


def snapshot_legacy_engine() -> dict[str, Any]:
    """Build the canonical snapshot of legacy engine behaviour.

    Returns a dict shaped:
        {
          "outcomes": {
            "<ruleCode>::<recordId>": {fired, rowCount, error},
            ...
          },
          "rule_count": int,
          "record_count": int,
          "fixture_count": int,
        }

    The outcomes dict is sorted-key for deterministic JSON serialisation."""
    conn = _open_conn()
    try:
        cur = conn.cursor()
        rules = _fetch_active_qa_rules(cur)
        records = _fetch_corpus_records(cur)

        outcomes: dict[str, dict] = {}
        for rule in rules:
            # Only run a rule against records of its own service — that
            # mirrors how the runtime actually scopes evaluation. A
            # service-X rule against a service-Y record is a false fixture.
            for rec in records:
                if rec["serviceId"] != rule["serviceId"]:
                    continue
                key = f"{rule['ruleCode']}::{rec['recordId']}"
                outcomes[key] = _evaluate_rule_against_record(cur, rule, rec)
        cur.close()
    finally:
        conn.close()

    # Sort the outcomes dict by key so JSON serialisation is deterministic
    sorted_outcomes = {k: outcomes[k] for k in sorted(outcomes)}
    return {
        "outcomes": sorted_outcomes,
        "rule_count": len(rules),
        "record_count": len(records),
        "fixture_count": len(sorted_outcomes),
    }


def snapshot_unified_engine() -> dict[str, Any]:
    """Snapshot via the unified engine. Not implemented until ADR-031
    Slice C lands. When it does, this function calls into the unified
    evaluator's API surface in the same shape as the legacy snapshot."""
    raise NotImplementedError(
        "Unified engine doesn't exist yet — see ADR-031. This stub keeps "
        "the equivalence-test interface stable so the eventual switch is "
        "a one-line change in the test."
    )


# ---------------------------------------------------------------------------
# Comparison
# ---------------------------------------------------------------------------

def diff_snapshots(s1: dict, s2: dict) -> list[dict]:
    """Return a list of differences between two snapshots. Empty list
    means equivalent. Each entry shows the key + the two outcome dicts."""
    diffs = []
    keys = set(s1.get("outcomes", {})) | set(s2.get("outcomes", {}))
    for k in sorted(keys):
        a = s1.get("outcomes", {}).get(k)
        b = s2.get("outcomes", {}).get(k)
        if a != b:
            diffs.append({"key": k, "snapshot_1": a, "snapshot_2": b})
    return diffs


def snapshot_signature(snap: dict) -> str:
    """Stable hash of a snapshot's outcomes — useful for quick equality
    checks without manually diffing 200 keys."""
    payload = json.dumps(snap.get("outcomes", {}), sort_keys=True).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()[:16]


# ---------------------------------------------------------------------------
# pytest tests
# ---------------------------------------------------------------------------

def test_snapshot_is_non_empty():
    """The snapshot must contain at least some fixtures, otherwise the
    determinism check below is meaningless."""
    snap = snapshot_legacy_engine()
    assert snap["rule_count"] > 0, "no qa_rule rows found"
    assert snap["record_count"] > 0, "no qa_record_status rows found"
    assert snap["fixture_count"] > 0, (
        "no rule × record fixtures matched — every rule's serviceId "
        "differs from every record's serviceId, which suggests a data "
        "shape problem (or an empty corpus)"
    )


def test_legacy_engine_is_deterministic():
    """The legacy engine must produce identical outputs on two
    consecutive snapshot runs. Non-determinism here is a data-shape
    or rule-shape problem; resolve it before attempting ADR-031."""
    snap1 = snapshot_legacy_engine()
    snap2 = snapshot_legacy_engine()
    diffs = diff_snapshots(snap1, snap2)
    if diffs:
        msg = "\n".join(
            f"  {d['key']}:\n    run-1: {d['snapshot_1']}\n    run-2: {d['snapshot_2']}"
            for d in diffs[:5]
        )
        more = f"\n  ...and {len(diffs)-5} more" if len(diffs) > 5 else ""
        pytest.fail(
            f"Legacy engine produced {len(diffs)} differing outcomes "
            f"between two consecutive runs:\n{msg}{more}"
        )


def test_snapshot_signature_is_stable():
    """The SHA256 prefix of the snapshot's outcomes must be byte-stable
    across runs. This is a single-line equivalent of the determinism
    test above and is what cross-session baselines compare against."""
    sig1 = snapshot_signature(snapshot_legacy_engine())
    sig2 = snapshot_signature(snapshot_legacy_engine())
    assert sig1 == sig2, (
        f"Snapshot signature differs across runs: {sig1} vs {sig2}. "
        f"Run test_legacy_engine_is_deterministic for the diff."
    )


def test_snapshot_is_json_serialisable():
    """The snapshot must round-trip through JSON without loss — that's
    what enables `--save baseline.json` and `--compare baseline.json`
    for cross-session ADR-031 slice gates."""
    snap = snapshot_legacy_engine()
    serialised = json.dumps(snap, sort_keys=True, indent=2)
    restored = json.loads(serialised)
    diffs = diff_snapshots(snap, restored)
    assert not diffs, f"snapshot did not round-trip through JSON: {diffs[:3]}"


def test_unified_engine_stub_raises_until_implemented():
    """Until ADR-031 Slice C lands, the unified-engine snapshot must
    raise NotImplementedError. This test exists so we can't accidentally
    declare equivalence when there's nothing to compare against."""
    with pytest.raises(NotImplementedError):
        snapshot_unified_engine()


# ---------------------------------------------------------------------------
# CLI — for cross-session baseline capture
# ---------------------------------------------------------------------------

def _cli_main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--save", metavar="FILE",
                   help="Snapshot the legacy engine and save to FILE.")
    g.add_argument("--compare", metavar="FILE",
                   help="Snapshot the legacy engine NOW and compare to FILE.")
    g.add_argument("--print-signature", action="store_true",
                   help="Print the current snapshot's signature.")
    args = ap.parse_args()

    if args.save:
        snap = snapshot_legacy_engine()
        with open(args.save, "w") as f:
            json.dump(snap, f, sort_keys=True, indent=2)
        print(f"Saved snapshot to {args.save}")
        print(f"  rule_count:    {snap['rule_count']}")
        print(f"  record_count:  {snap['record_count']}")
        print(f"  fixture_count: {snap['fixture_count']}")
        print(f"  signature:     {snapshot_signature(snap)}")
        return 0

    if args.compare:
        with open(args.compare) as f:
            baseline = json.load(f)
        current = snapshot_legacy_engine()
        diffs = diff_snapshots(baseline, current)
        print(f"Baseline signature: {snapshot_signature(baseline)}")
        print(f"Current signature:  {snapshot_signature(current)}")
        if not diffs:
            print(f"\n✓ Snapshots equivalent ({current['fixture_count']} fixtures).")
            return 0
        print(f"\n✗ {len(diffs)} differences:")
        for d in diffs[:10]:
            print(f"  {d['key']}:")
            print(f"    baseline: {d['snapshot_1']}")
            print(f"    current:  {d['snapshot_2']}")
        if len(diffs) > 10:
            print(f"  ... and {len(diffs) - 10} more")
        return 1

    if args.print_signature:
        snap = snapshot_legacy_engine()
        print(snapshot_signature(snap))
        return 0


if __name__ == "__main__":
    sys.exit(_cli_main())
