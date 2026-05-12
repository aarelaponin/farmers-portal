#!/usr/bin/env python3
"""
Drive the L3-1 1B-i BudgetEngine through its REST dispatch endpoint.

Sequence: walks Mants'ali (or any applicant) through RESERVATION →
PRE_COMMITMENT → optional RELEASE — printing the projection state after
each step. Used to verify the engine's transaction posting end-to-end
without first wiring it into the subsidy storeBinder (1B-ii work).

Usage:
    python3 tooling/budget_dispatch.py reserve   PRG_2025_002 app-uuid-1
    python3 tooling/budget_dispatch.py approve   PRG_2025_002 app-uuid-1
    python3 tooling/budget_dispatch.py release   PRG_2025_002 app-uuid-1
    python3 tooling/budget_dispatch.py scenario  PRG_2025_002    # all-in-one demo
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))

# api_id for budget API — created by a one-shot POST to
# /formcreator/apis with className=BudgetApi after deploying form-creator-api
# build-020+. Per the analyst-driven API pattern, the id is API-<code>
# where code = "BUDGET".
BUDGET_API_ID = os.environ.get("BUDGET_API_ID", "API-BUDGET")

DISPATCH_URL = JOGET_BASE_URL + "/api/budget/dispatch"


PROGRAMME_TO_ENVELOPE = {
    "PRG_2025_001": "ENV_PRG_2025_001_FY2526",
    "PRG_2025_002": "ENV_PRG_2025_002_FY2526",
    "PRG_2025_003": "ENV_PRG_2025_003_FY2526",
    "PRG_2025_004": "ENV_PRG_2025_004_FY2526",
}

ACTION_BY_VERB = {
    "reserve": "BUDGET_RESERVE_ON_SUBMIT",
    "release": "BUDGET_RELEASE_ON_REJECT",
    "approve": "BUDGET_PRE_COMMIT_ON_APPROVE",
}


def post(payload):
    body = json.dumps(payload).encode()
    req = urllib.request.Request(DISPATCH_URL, method="POST", data=body, headers={
        "Content-Type": "application/json",
        "api_id":  BUDGET_API_ID,
        "api_key": JOGET_API_KEY,
    })
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def parse(raw):
    try:
        outer = json.loads(raw)
        msg = outer.get("message", outer)
        if isinstance(msg, str):
            return json.loads(msg)
        return msg
    except Exception:
        return raw


def dispatch(verb, programme, application_id):
    # Action code convention: BUDGET_<VERB>_PRG_<NNN>
    # e.g. BUDGET_RESERVE_ON_SUBMIT_PRG_002 (3-digit suffix; matches the
    # short form used in the seed YAML).
    suffix = programme.split("_")[-1]   # "002" from "PRG_2025_002"
    action_code = ACTION_BY_VERB[verb] + "_PRG_" + suffix

    envelope = PROGRAMME_TO_ENVELOPE[programme]

    # Pull applicant data — for 1B-i tests we use a minimal placeholder.
    payload = {
        "actionCode":      action_code,
        "applicationId":   application_id,
        "envelopeCode":    envelope,
        "actor":           "test_driver",
        "correlationType": "subsidy_application",
        "sourceModule":    "subsidy",
        "applicantData":   {
            "applied_programme": programme,
            "national_id":       "066257236627",
            "full_name":         "Test Applicant",
        },
    }

    print(f"--- {verb.upper()}  {action_code} ---")
    status, raw = post(payload)
    parsed = parse(raw)
    if status != 200:
        print(f"   HTTP {status}  {raw[:200]}")
        return False
    print(f"   status:    {parsed.get('status')}")
    print(f"   txId:      {parsed.get('transactionId')}")
    print(f"   envelope:  {parsed.get('envelopeCode')}")
    print(f"   eventType: {parsed.get('eventType')}")
    print(f"   amount:    {parsed.get('amount')}")
    if parsed.get("beneficiaryAccountCode"):
        print(f"   bnf:       {parsed.get('beneficiaryAccountCode')}")
    if parsed.get("errorCause"):
        print(f"   errorCause: {parsed.get('errorCause')}")
    print(f"   elapsedMs: {parsed.get('elapsedMs')}")
    lines = parsed.get("journalLines", [])
    if lines:
        print(f"   journal lines: {len(lines)}")
        for ln in lines[:8]:
            print(f"     {ln['direction']:6s} {ln['amount']:>12s}  {ln['accountPath']}")
        if len(lines) > 8:
            print(f"     ...and {len(lines)-8} more")
    print()
    return parsed.get("status") in ("posted", "no_op_idempotent", "skipped_condition")


def scenario(programme):
    """Run a full RESERVE → APPROVE → RELEASE scenario with a fresh app id."""
    app_id = str(uuid.uuid4())
    print(f"Scenario: {programme}, fresh applicationId={app_id}")
    print()
    ok = True
    ok &= dispatch("reserve", programme, app_id)
    time.sleep(0.5)
    ok &= dispatch("approve", programme, app_id)
    time.sleep(0.5)
    print("Final state — call tooling/budget_status.py to inspect.")
    return ok


def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("verb", choices=list(ACTION_BY_VERB) + ["scenario"])
    ap.add_argument("programme", choices=list(PROGRAMME_TO_ENVELOPE))
    ap.add_argument("application_id", nargs="?", help="Application UUID (omit for scenario).")
    args = ap.parse_args()
    if args.verb == "scenario":
        return 0 if scenario(args.programme) else 1
    if not args.application_id:
        sys.exit("application_id required for direct verb dispatches.")
    return 0 if dispatch(args.verb, args.programme, args.application_id) else 1


if __name__ == "__main__":
    sys.exit(main())
