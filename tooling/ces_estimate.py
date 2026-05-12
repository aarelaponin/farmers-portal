#!/usr/bin/env python3
"""
L3-1 1C — Cost Estimation Service test driver.

Walks each of the four 2025 programmes with an admin-supplied
expectedApplicantCount, prints the projected cost / coverage / source
breakdown / launch-gate verdict.

Usage:
    python3 tooling/ces_estimate.py PRG_2025_002 200
    python3 tooling/ces_estimate.py all                 # all four with sensible defaults
"""

import json
import os
import sys
import urllib.error
import urllib.request

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))
BUDGET_API_ID  = os.environ.get("BUDGET_API_ID",  "API-BUDGET")

ENDPOINT = JOGET_BASE_URL + "/api/budget/ces/estimate"

# Default applicant counts — rough estimates per programme that exercise
# the rule's coverage thresholds. Adjust freely for ad-hoc planning.
DEFAULT_COUNTS = {
    "PRG_2025_001": 600,    # M2k each → M1.2M (envelope M1.2M, 100%)
    "PRG_2025_002": 200,    # M2k each → M400k (envelope M450k, 89%)
    "PRG_2025_003": 100,    # M1.5k each → M150k (envelope M180k, 83%)
    "PRG_2025_004": 450,    # M5k each → M2.25M (envelope M2.5M, 90%)
}


def post(payload):
    body = json.dumps(payload).encode()
    req = urllib.request.Request(ENDPOINT, method="POST", data=body, headers={
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


def estimate(programme, count):
    print(f"=== {programme}  (expected applicants: {count}) ===")
    status, raw = post({"programmeCode": programme, "expectedApplicantCount": count})
    if status != 200:
        print(f"  HTTP {status}  {raw[:200]}")
        return False
    p = parse(raw)
    if p.get("status") == "error":
        print(f"  error: {p.get('errorCause')}")
        return False
    print(f"  envelope:           {p.get('envelopeCode')}")
    print(f"  envelope allocated: {fmt(p.get('envelopeAllocated'))}  {p.get('currency','')}")
    print(f"  per-applicant:      {fmt(p.get('perApplicantAmount'))}")
    print(f"  estimated cost:     {fmt(p.get('estimatedCost'))}")
    print(f"  coverage ratio:     {p.get('coverageRatioPct','?')}%")
    print(f"  launch gate:        {'GREEN' if p.get('launchGateGreen') else 'RED'}")
    if not p.get("launchGateGreen"):
        print(f"  fail message:       {p.get('launchGateFailMessage','')}")
    print(f"  source breakdown:")
    for src, amt in (p.get("sourceBreakdown") or {}).items():
        print(f"    {src:<48s}  {fmt(amt):>14}")
    print(f"  launch-gate rules evaluated:")
    for g in (p.get("launchGateRules") or []):
        print(f"    {g['ruleCode']:<32s} → {g['outcome']}")
    print()
    return True


def fmt(v):
    if v is None or v == "":
        return "—"
    try:
        return f"{float(v):,.2f}"
    except (ValueError, TypeError):
        return str(v)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    if sys.argv[1] == "all":
        ok = True
        for prog, count in DEFAULT_COUNTS.items():
            ok &= estimate(prog, count)
        return 0 if ok else 1
    programme = sys.argv[1]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_COUNTS.get(programme, 100)
    return 0 if estimate(programme, count) else 1


if __name__ == "__main__":
    sys.exit(main())
