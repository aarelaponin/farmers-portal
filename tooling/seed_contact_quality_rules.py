#!/usr/bin/env python3
"""
Seed/upsert four operator-editable quality rules on `farmer_registration`
service, replacing the legacy `identity.mobile_required` policy (which
made mobile mandatory) with a more permissive contact requirement: at
least one of email or mobile must be present.

Rules authored (all scope=quality, businessKey=code, upserted via
form-creator-api /seed):

  identity.has_contact          (severity=error)   — at least 1 channel
  identity.email_format_valid   (severity=warning) — if email set, must look like email
  identity.phone_format_valid   (severity=warning) — if mobile set, must look like Lesotho number

Plus retire `identity.mobile_required` by flipping its severity to
'warning' (operator can re-enable strict mode later if needed) — non-
destructive: row stays, only severity changes.

HARD RULE compliant: all writes via form-creator-api, no raw SQL.
"""

import argparse, datetime, json, sys, urllib.error, urllib.request

JOGET   = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = os.environ.get("JOGET_API_KEY", "")
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}

# Postgres-flavoured SQL — same dialect as the existing quality rules.
# All use #recordId# substitution from form-quality-runtime.
# Pattern: SELECT 1 ... → rule FAILS when ≥1 row returned.

EMAIL_REGEX = r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"

RULES = [
    # ────────────────────────────────────────────────────────────────────
    # 1. At least one contact channel — replaces the rigid mobile-required rule.
    # ────────────────────────────────────────────────────────────────────
    {
        "code":           "identity.has_contact",
        "name":           "Identity Has At Least One Contact",
        "scope":          "quality",
        "severity":       "error",
        "ruletype":       "",
        "rulejson": (
            "SELECT 1 FROM app_fd_farmerbasicinfo "
            "WHERE c_parent_id='#recordId#' "
            "  AND COALESCE(TRIM(c_mobile_number),'') = '' "
            "  AND COALESCE(TRIM(c_email_address),'') = ''"
        ),
        "failmessage":    "At least one contact channel is required — provide mobile number, email address, or both. Without contact details, the Ministry cannot notify you about your applications.",
        "affectedfields": "mobile_number,email_address",
        "triggeron":      "save",
        "serviceid":      "farmer_registration",
        "description":    "Operator-editable: change either the failmessage, or the SQL to allow contact-less registration in special cases (e.g. paper-only applicants).",
        "actionjson":     "",
        "targetvalue":    "",
        "score":          "",
        "aggregation":    "",
        "registrationid": "",
        "tabcode":        "",
        "allowslowpath":  "",
    },

    # ────────────────────────────────────────────────────────────────────
    # 2. Email format validation — only fires if email_address is non-blank
    # ────────────────────────────────────────────────────────────────────
    {
        "code":           "identity.email_format_valid",
        "name":           "Identity Email Format Valid",
        "scope":          "quality",
        "severity":       "warning",
        "ruletype":       "",
        "rulejson": (
            "SELECT 1 FROM app_fd_farmerbasicinfo "
            "WHERE c_parent_id='#recordId#' "
            "  AND COALESCE(TRIM(c_email_address),'') <> '' "
            f"  AND c_email_address !~ '{EMAIL_REGEX}'"
        ),
        "failmessage":    "Email address does not look valid. Expected format: name@example.com",
        "affectedfields": "email_address",
        "triggeron":      "save",
        "serviceid":      "farmer_registration",
        "description":    "Operator-editable: tighten or loosen the regex via this rule. Default is lenient RFC-like check.",
        "actionjson":     "",
        "targetvalue":    "",
        "score":          "",
        "aggregation":    "",
        "registrationid": "",
        "tabcode":        "",
        "allowslowpath":  "",
    },

    # ────────────────────────────────────────────────────────────────────
    # 3. Phone format — Lesotho 8-digit core, optional +266 prefix
    # ────────────────────────────────────────────────────────────────────
    {
        "code":           "identity.phone_format_valid",
        "name":           "Identity Phone Format Valid",
        "scope":          "quality",
        "severity":       "warning",
        "ruletype":       "",
        # Strip non-digits; expect 8 (local) or 11 (with country code) digits.
        # Lesotho mobile prefix 5/6/7/8.
        "rulejson": (
            "SELECT 1 FROM app_fd_farmerbasicinfo "
            "WHERE c_parent_id='#recordId#' "
            "  AND COALESCE(TRIM(c_mobile_number),'') <> '' "
            "  AND length(regexp_replace(c_mobile_number, '[^0-9]', '', 'g')) NOT IN (8, 11)"
        ),
        "failmessage":    "Mobile number does not look valid for Lesotho. Expected 8 digits (e.g. 58515039) or +266 + 8 digits (e.g. +266 58515039).",
        "affectedfields": "mobile_number",
        "triggeron":      "save",
        "serviceid":      "farmer_registration",
        "description":    "Operator-editable: relax the digit-count check or add country-specific prefix validation here.",
        "actionjson":     "",
        "targetvalue":    "",
        "score":          "",
        "aggregation":    "",
        "registrationid": "",
        "tabcode":        "",
        "allowslowpath":  "",
    },

    # ────────────────────────────────────────────────────────────────────
    # 4. Application-side recipient check — the applicant's registry row
    # must have at least one contact channel. Belt-and-braces; the
    # registry rule above is the primary gate, this catches data that
    # may have been imported with no contact.
    # ────────────────────────────────────────────────────────────────────
    {
        "code":           "application.applicant_has_recipient",
        "name":           "Application Applicant Has Contact Channel",
        "scope":          "quality",
        "severity":       "error",
        "ruletype":       "",
        "rulejson": (
            "SELECT 1 FROM app_fd_subsidy_app_2025 a "
            "LEFT JOIN app_fd_farmerbasicinfo f "
            "  ON f.c_national_id = a.c_applicant_national_id "
            "WHERE a.id = '#recordId#' "
            "  AND COALESCE(TRIM(f.c_email_address),'') = '' "
            "  AND COALESCE(TRIM(f.c_mobile_number),'') = ''"
        ),
        "failmessage":    "The applicant's farmer registry has no contact channels. The applicant cannot be notified about this application. Update the farmer's mobile or email before submitting.",
        "affectedfields": "applicant_national_id",
        "triggeron":      "save",
        "serviceid":      "farmer_application",
        "description":    "Operator-editable: tweak the failmessage or extend the SQL to also require a verified contact, once a verification flow lands.",
        "actionjson":     "",
        "targetvalue":    "",
        "score":          "",
        "aggregation":    "",
        "registrationid": "",
        "tabcode":        "",
        "allowslowpath":  "",
    },

    # ────────────────────────────────────────────────────────────────────
    # 5. Retire identity.mobile_required by downgrading severity to warning.
    # Existing field set retained; only severity flips. Operators can
    # later disable entirely via the MM-Rules edit form.
    # ────────────────────────────────────────────────────────────────────
    {
        "code":           "identity.mobile_required",
        "name":           "Identity Mobile Required (legacy — retired by identity.has_contact)",
        "scope":          "quality",
        "severity":       "warning",
        "ruletype":       "",
        "rulejson": (
            "SELECT 1 FROM app_fd_farmerbasicinfo "
            "WHERE c_parent_id='#recordId#' "
            "  AND (c_mobile_number IS NULL OR TRIM(c_mobile_number)='')"
        ),
        "failmessage":    "Mobile number is missing. Email may be used as the sole channel if preferred. (This rule is retained as a warning only — the binding gate is identity.has_contact.)",
        "affectedfields": "mobile_number",
        "triggeron":      "save",
        "serviceid":      "farmer_registration",
        "description":    "Retired (May 2026). Replaced by identity.has_contact which permits email-only farmers. Kept active as a soft hint so MAFSN staff are still nudged to capture mobile when possible.",
        "actionjson":     "",
        "targetvalue":    "",
        "score":          "",
        "aggregation":    "",
        "registrationid": "",
        "tabcode":        "",
        "allowslowpath":  "",
    },
]


def upsert():
    rows = []
    for r in RULES:
        # Field IDs on mm_determinant form are CAMELCASE (verified May 2026 via
        # the form-definition JSON). Postgres column names fold to lowercase
        # (c_rulejson, c_failmessage) but Joget's Hibernate mapping reads
        # camelCase from the form definition. Sending lowercase keys = field
        # values silently dropped.
        rows.append({
            "code":           r["code"],
            "name":           r["name"],
            "scope":          r["scope"],
            "severity":       r["severity"],
            "ruleJson":       r["rulejson"],
            "ruleType":       r["ruletype"],
            "failMessage":    r["failmessage"],
            "affectedFields": r["affectedfields"],
            "triggerOn":      r["triggeron"],
            "serviceId":      r["serviceid"],
            "description":    r["description"],
            "actionJson":     r["actionjson"],
            "targetValue":    r["targetvalue"],
            "score":          r["score"],
            "aggregation":    r["aggregation"],
            "registrationId": r["registrationid"],
            "tabCode":        r["tabcode"],
            "allowSlowPath":  r["allowslowpath"],
        })

    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {
                "formId": "mm_determinant",
                "businessKey": "code",
                "rows": rows,
            }
        ]
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/seed",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    print(f"\n=== Seeding {len(RULES)} rules (4 new + 1 retire) ===\n")
    for r in RULES:
        print(f"  {r['severity']:<8} {r['code']:<40} → {r['serviceid']}")
        print(f"  {'':<8} {r['failmessage'][:90]}{'…' if len(r['failmessage'])>90 else ''}")
        print()

    if args.dry_run:
        print("Dry-run — not pushing.")
        return 0

    print("Applying via /seed with businessKey=code ...")
    status, body = upsert()
    print(f"HTTP {status}")
    print(body[:600])
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
