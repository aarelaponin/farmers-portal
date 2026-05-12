#!/usr/bin/env python3
"""
Targeted seeder: posts only the budget_envelope, budget_envelope_source,
and budget_event sections of lesotho-mm-fixture.yaml.

Why this exists: the full tooling/seed.py loops through every mm_*
fixture section, which on the dev instance takes longer than the sandbox
shell's 45-second per-call cap. For the L3-1 1A bring-up, we only need
the four budget envelopes + their source contributions + the initial
ALLOCATION events; this targeted runner finishes in ~10 seconds.

The full seed.py remains canonical for end-to-end environment setup.
"""

import json
import os
import sys
import urllib.request
import urllib.error
import datetime

try:
    import yaml
except ImportError:
    sys.exit("PyYAML required: pip install pyyaml")

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  "a5af1181f77b4a62b481725b6410e965")
APP_ID         = os.environ.get("JOGET_APP_ID",   "farmersPortal")

_HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_FIXTURE = os.path.normpath(os.path.join(
    _HERE, os.pardir, "app", "seeds", "lesotho-mm-fixture.yaml"))

SEED_URL  = JOGET_BASE_URL + "/api/formcreator/formcreator/seed"
CLEAR_URL = JOGET_BASE_URL + "/api/formcreator/formcreator/clear"


class _DateAwareEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, (datetime.date, datetime.datetime)):
            return o.isoformat()
        return super().default(o)


def post(url, payload):
    body = json.dumps(payload, cls=_DateAwareEncoder).encode()
    req = urllib.request.Request(url, method="POST", data=body, headers={
        "Content-Type": "application/json",
        "api_id":  JOGET_API_ID,
        "api_key": JOGET_API_KEY,
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def parse_results(raw):
    try:
        outer = json.loads(raw)
        msg = outer.get("message", outer)
        if isinstance(msg, str):
            return json.loads(msg)
        return msg
    except Exception:
        return raw


def seed_one(form_id, rows, business_key="code"):
    if not rows:
        print(f"  {form_id:30s}: (empty)")
        return 0
    payload = {
        "appId": APP_ID,
        "fixtures": [{
            "formId":      form_id,
            "businessKey": business_key,
            "rows":        rows,
        }],
    }
    status, raw = post(SEED_URL, payload)
    parsed = parse_results(raw)
    if status != 200:
        print(f"  {form_id:30s}: HTTP {status} → {raw[:200]}")
        return 1
    result = parsed.get("results", [{}])[0]
    print(f"  {form_id:30s}: inserted={result.get('inserted',0)}, "
          f"updated={result.get('updated',0)}, errors={len(result.get('errors',[]))}")
    for e in result.get("errors", []):
        print(f"      ! {e}")
    return 0


def main():
    with open(DEFAULT_FIXTURE) as f:
        fx = yaml.safe_load(f)

    # Pre-clear append-only / runtime-derived tables. budget_event is the
    # ledger; beneficiary_subledger is a derived record opened by the
    # listener at PRE_COMMITMENT — both should reset to empty before a
    # fresh seed so the post-seed state is the canonical "envelopes
    # allocated, no transactions yet" baseline.
    print("=== Pre-clearing budget_event + beneficiary_subledger ===")
    status, raw = post(CLEAR_URL, {"appId": APP_ID,
                                   "formIds": ["budget_event", "beneficiary_subledger"]})
    parsed = parse_results(raw)
    for r in parsed.get("results", []):
        print(f"  pre-clear {r.get('formId')}: deleted={r.get('deleted',0)}")

    print()
    print("=== Seeding budget sections ===")
    rc  = seed_one("budget_envelope",        fx.get("budget_envelope",        []))
    rc |= seed_one("budget_envelope_source", fx.get("budget_envelope_source", []))
    # budget_event is the append-only ledger — multiple journal lines share
    # the same transaction_id by design. We do NOT use transaction_id as a
    # de-dup key (that would collapse balanced multi-line postings to a
    # single row). The pre-clear above gives us a clean slate; the default
    # business_key="code" doesn't match any field on these rows so every
    # row inserts as fresh.
    rc |= seed_one("budget_event",           fx.get("budget_event",           []))

    # Targeted re-seed of just the budget-related mm_determinant + mm_action
    # rows. Avoids the full seed.py pass (which exceeds the sandbox's 45s
    # per-call cap because it iterates all 17 sections). Filters by code
    # prefix; uses upsert-by-code semantics (default business_key).
    print()
    print("=== Seeding budget mm_determinant rows (BENEFIT_AMOUNT_* + GATE_LAUNCH_*) ===")
    det_rows = [r for r in (fx.get("mm_determinant") or [])
                if r.get("code", "").startswith("BENEFIT_AMOUNT_")
                or r.get("code", "").startswith("GATE_LAUNCH_")]
    rc |= seed_one("mm_determinant", det_rows)

    print()
    print("=== Seeding budget mm_action rows (BUDGET_*) ===")
    act_rows = [r for r in (fx.get("mm_action") or [])
                if r.get("code", "").startswith("BUDGET_")]
    rc |= seed_one("mm_action", act_rows)

    return rc


if __name__ == "__main__":
    sys.exit(main())
