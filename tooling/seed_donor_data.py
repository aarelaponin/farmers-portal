#!/usr/bin/env python3
"""
Seed sample donor + programme funding data for the donor-grade financial slice (C6).

Per CLAUDE.md HARD RULE: writes go through formcreator/seed (FormDataDao path),
never raw SQL.

Usage:
    python3 tooling/seed_donor_data.py
"""

import json
import os
import sys
import urllib.request
import urllib.error

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  "a5af1181f77b4a62b481725b6410e965")

SEED_URL = JOGET_BASE_URL + "/api/formcreator/formcreator/seed"


def post_seed(form_id, rows):
    payload = {
        "fixtures": [{
            "formId": form_id,
            "tableName": form_id,
            "rows": rows,
        }]
    }
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        SEED_URL,
        method="POST",
        data=body,
        headers={
            "Content-Type": "application/json",
            "api_id": JOGET_API_ID,
            "api_key": JOGET_API_KEY,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            txt = resp.read().decode("utf-8")
            print(f"[{resp.status}] {form_id}: {txt[:240]}")
            return resp.status, txt
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"[{e.code}] {form_id}: {body[:240]}", file=sys.stderr)
        return e.code, body


# -----------------------------------------------------------------------------
# Donor master data (md_donor)
# -----------------------------------------------------------------------------

DONORS = [
    {
        "code": "WFP",
        "name": "World Food Programme",
        "country": "International (UN)",
        "focal_point": "Khauta Faku",
        "contact_email": "khauta.faku@wfp.org",
        "notes": "Lead donor on Lesotho subsidy pilot 2025/26. SADP-II co-financier.",
    },
    {
        "code": "EU",
        "name": "European Union (DG INTPA)",
        "country": "International (EU)",
        "focal_point": "(TBD)",
        "contact_email": "",
        "notes": "Tentative co-financier; engagement under exploration as of 2026 Q2.",
    },
    {
        "code": "MAFSN_DOMESTIC",
        "name": "MAFSN Domestic Budget",
        "country": "Lesotho",
        "focal_point": "Khotso Mosoeu",
        "contact_email": "khotso.mosoeu@gov.ls",
        "notes": "Government of Lesotho domestic financing (Treasury appropriation).",
    },
    {
        "code": "WORLD_BANK",
        "name": "World Bank (IDA)",
        "country": "International (IBRD/IDA)",
        "focal_point": "(TBD)",
        "contact_email": "",
        "notes": "Historical co-financier; reserved code for future engagement.",
    },
]


# -----------------------------------------------------------------------------
# Programme funding linkage
# -----------------------------------------------------------------------------
# PRG_2025_001 (Fertilizer & Seed Subsidy 2025/26) is co-financed by:
#   WFP             60%
#   MAFSN_DOMESTIC  40%

PROGRAMME_FUNDING = [
    {
        "code": "FUND-202601",
        "programme_code": "PRG_2025_001",
        "donor_code": "WFP",
        "share_percent": "60",
        "grant_reference": "WFP-LSO-2026-001",
        "effective_from": "2025-10-01",
        "effective_to": "2026-09-30",
        "notes": "WFP-financed share of the 2025/26 Fertilizer & Seed Subsidy envelope.",
    },
    {
        "code": "FUND-202602",
        "programme_code": "PRG_2025_001",
        "donor_code": "MAFSN_DOMESTIC",
        "share_percent": "40",
        "grant_reference": "GOL-MAFSN-2026-FY",
        "effective_from": "2025-10-01",
        "effective_to": "2026-09-30",
        "notes": "Government of Lesotho domestic counterpart financing.",
    },
]


def main():
    print("== Seeding donor master data ==")
    post_seed("md_donor", DONORS)
    print()
    print("== Seeding programme funding linkage ==")
    post_seed("programme_funding", PROGRAMME_FUNDING)
    print()
    print("Done.")


if __name__ == "__main__":
    main()
