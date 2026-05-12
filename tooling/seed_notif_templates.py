#!/usr/bin/env python3
"""
Seed operator-editable email templates into app_fd_spNotifTemplate.

Populates the 12 email_subject_en / email_body_en (HTML) columns from the
markdown spec at docs/notifications/, and adds 6
new rows for trigger events that don't yet have a template (voucher
redeemed, expired, cancelled; supervisor digest; budget 75% / frozen).

After this seeds, EmailDispatcher.sendByEvent() picks the DB row and
substitutes {var} placeholders at send time. Operator edits via App
Composer → Master Data → Notification Template propagate to the next
send with no plugin rebuild.

Usage:
    tooling/seed_notif_templates.py --dry-run
    tooling/seed_notif_templates.py --apply

HARD RULE: pushes via form-creator-api (POST /jw/api/formcreator/...),
never raw SQL on app_fd_spNotifTemplate.
"""

import argparse
import json
import sys
import urllib.error
import urllib.request

import psycopg2

PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb", user="jogetadmin",
    password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require",
)
JOGET   = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = os.environ.get("JOGET_API_KEY", "")
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}

# -----------------------------------------------------------------------------
# Template content — subject + HTML body, EN. ST translation deferred to
# MAFSN's translator. {var} placeholders resolve at send time via the
# vars map each call site builds.
# -----------------------------------------------------------------------------
TEMPLATES = [
    {
        "templateCode":    "APP_SUBMITTED",
        "triggerEvent":    "APP_SUBMITTED",
        "templateName":    "Application Submitted Confirmation",
        "category":        "APPLICATION",
        "priority":        "NORMAL",
        "subjectEn":       "Your subsidy application has been received — {application_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>Thank you. Your application for <strong>{program_name}</strong> has been received by the Ministry of Agriculture, Food Security and Nutrition.</p>
<p><strong>Application reference:</strong> {application_code}<br>
<strong>Submitted:</strong> {submitted_date}<br>
<strong>Programme:</strong> {program_name}</p>
<p><strong>What happens next:</strong></p>
<ol>
<li>Our team reviews your application against the eligibility rules.</li>
<li>If everything checks out, you will receive a follow-up email within 3 working days.</li>
<li>If approved, an electronic voucher will be issued automatically.</li>
</ol>
<p>You do not need to do anything right now.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "APP_APPROVED",
        "triggerEvent":    "APP_APPROVED",
        "templateName":    "Application Auto-Approved",
        "category":        "APPLICATION",
        "priority":        "HIGH",
        "subjectEn":       "Good news — your subsidy application is approved ({application_code})",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p><strong>Your application for {program_name} has been APPROVED.</strong></p>
<p><strong>Application reference:</strong> {application_code}<br>
<strong>Decision date:</strong> {decision_date}<br>
<strong>Programme:</strong> {program_name}</p>
<p><strong>What happens next:</strong></p>
<ol>
<li>An electronic voucher is being issued to you. You will receive a separate email confirming the voucher details.</li>
<li>To redeem, visit any approved distribution point in your district. Bring your National ID.</li>
<li>If you do not redeem within {validity_days} days, the voucher expires and the benefit returns to the programme budget.</li>
</ol>
<p>You do not need to visit any office to claim approval.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "APP_UNDER_REVIEW",
        "triggerEvent":    "APP_UNDER_REVIEW",
        "templateName":    "Application Under Review",
        "category":        "APPLICATION",
        "priority":        "NORMAL",
        "subjectEn":       "Your application is under review — {application_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>Your application for <strong>{program_name}</strong> is being reviewed by our team.</p>
<p><strong>Application reference:</strong> {application_code}<br>
<strong>Status:</strong> Under review</p>
<p><strong>Why this review:</strong> some details on your application need a closer look from our staff. This is normal and does not mean anything is wrong.</p>
<p><strong>What happens next:</strong></p>
<ol>
<li>A district supervisor reviews your application. Most reviews complete within 3 working days.</li>
<li>You will receive a follow-up email when the decision is made.</li>
<li>You do not need to do anything right now.</li>
</ol>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "APP_REJECTED",
        "triggerEvent":    "APP_REJECTED",
        "templateName":    "Application Rejected",
        "category":        "APPLICATION",
        "priority":        "HIGH",
        "subjectEn":       "Decision on your subsidy application — {application_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>We have completed the review of your application for <strong>{program_name}</strong>.</p>
<p><strong>Application reference:</strong> {application_code}<br>
<strong>Decision:</strong> Not approved</p>
<p><strong>Reason:</strong></p>
<blockquote style="border-left:4px solid #6c757d;padding-left:16px;color:#495057;">{reason}</blockquote>
<p>This programme does not match your circumstances at this time. This is not a reflection on you — programmes are designed for specific groups. If your circumstances change, or if a future programme matches your situation better, you can apply again.</p>
<p><strong>What you can do next:</strong></p>
<ol>
<li>Other agricultural programmes may be available. Visit your district office to discuss what you might qualify for.</li>
<li>If you believe this decision is incorrect, visit your district office with supporting documents.</li>
</ol>
<p>We thank you for your interest in this programme.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "APP_DECISION_PENDING",
        "triggerEvent":    "APP_DECISION_PENDING",
        "templateName":    "Supervisor Digest — Pending Decisions",
        "category":        "OPERATOR",
        "priority":        "NORMAL",
        "subjectEn":       "{pending_count} applications waiting for supervisor review",
        "bodyEn": """<p>Hello <strong>{supervisor_name}</strong>,</p>
<p>You have <strong>{pending_count} applications</strong> waiting for a supervisor decision. Some have been waiting more than 24 hours.</p>
<p><strong>Oldest waiting:</strong> {oldest_waiting_days} days<br>
<strong>Total in queue:</strong> {pending_count}</p>
<p><a href="{portal_url}" style="background:#1e6091;color:white;padding:10px 20px;text-decoration:none;border-radius:4px;display:inline-block;">Open operator inbox</a></p>
<p>Reviewing applications promptly helps citizens plan their season.</p>
<p>— Farmers Portal automation</p>""",
    },
    {
        "templateCode":    "VOUCHER_ISSUED",
        "triggerEvent":    "VOUCHER_ISSUED",
        "templateName":    "Voucher Issued",
        "category":        "PAYMENT",
        "priority":        "HIGH",
        "subjectEn":       "Your voucher is ready — {voucher_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>Your subsidy voucher has been issued. You can use this voucher at any approved distribution point to claim your benefit.</p>
<p><strong>Voucher code:</strong> <span style="font-family:monospace;font-size:1.3em;color:#1e6091;">{voucher_code}</span><br>
<strong>Programme:</strong> {programme_name}<br>
<strong>Issued on:</strong> {issued_date}<br>
<strong>Valid until:</strong> {expiry_date}<br>
<strong>Benefit:</strong> {benefit}</p>
<p><strong>How to redeem:</strong></p>
<ol>
<li>Visit any approved distribution point in your district.</li>
<li>Bring your National ID and the voucher code above.</li>
<li>The distribution-point operator will verify your identity and dispense your benefit.</li>
<li>You will receive a separate confirmation email after redemption.</li>
</ol>
<p><strong>Important:</strong> if you do not redeem by <strong>{expiry_date}</strong>, this voucher will expire and the benefit returns to the programme budget. A reminder will be sent 7 days before expiry.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "VOUCHER_REDEEMED",
        "triggerEvent":    "VOUCHER_REDEEMED",
        "templateName":    "Voucher Redeemed",
        "category":        "PAYMENT",
        "priority":        "NORMAL",
        "subjectEn":       "Voucher redemption confirmed — {redemption_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>Your voucher has been redeemed at <strong>{point_name}</strong> on <strong>{redemption_date}</strong>.</p>
<p><strong>Redemption reference:</strong> {redemption_code}<br>
<strong>Voucher code:</strong> {voucher_code}<br>
<strong>Distribution point:</strong> {point_name}<br>
<strong>Date redeemed:</strong> {redemption_date}<br>
<strong>Benefit dispensed:</strong> {quantity_dispensed} of {benefit}</p>
<p><strong>Remaining on this voucher:</strong> {remaining_qty} of {benefit}</p>
<p>If your voucher still has a remaining quantity, you may visit a distribution point again before <strong>{expiry_date}</strong> to claim the remainder.</p>
<p style="background:#fff3e0;border-left:4px solid #ef6c00;padding:12px 16px;">Keep this email as proof of redemption. If anything looks wrong, contact your district office immediately.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "VOUCHER_EXPIRING",
        "triggerEvent":    "VOUCHER_EXPIRING",
        "templateName":    "Voucher Expiring in 7 Days",
        "category":        "PAYMENT",
        "priority":        "HIGH",
        "subjectEn":       "Reminder — your voucher expires in 7 days ({voucher_code})",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>This is a friendly reminder. Your subsidy voucher expires in 7 days.</p>
<p><strong>Voucher code:</strong> {voucher_code}<br>
<strong>Programme:</strong> {programme_name}<br>
<strong>Expires on:</strong> <span style="color:#c62828;"><strong>{expiry_date}</strong></span><br>
<strong>Remaining benefit:</strong> {remaining_qty} of {benefit}</p>
<p style="background:#fff3e0;border-left:4px solid #ef6c00;padding:12px 16px;">If you do not redeem before the expiry date, this voucher will lapse and the benefit returns to the programme budget.</p>
<p>To redeem, visit any approved distribution point in your district. Bring your National ID and the voucher code above.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "VOUCHER_EXPIRED",
        "triggerEvent":    "VOUCHER_EXPIRED",
        "templateName":    "Voucher Expired (Unredeemed)",
        "category":        "PAYMENT",
        "priority":        "NORMAL",
        "subjectEn":       "Your voucher has expired — {voucher_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>This is a notification that your subsidy voucher has expired without being redeemed (or fully redeemed).</p>
<p><strong>Voucher code:</strong> {voucher_code}<br>
<strong>Programme:</strong> {programme_name}<br>
<strong>Issued on:</strong> {issued_date}<br>
<strong>Expired on:</strong> {expiry_date}<br>
<strong>Unredeemed quantity:</strong> {unredeemed_qty} of {benefit}</p>
<p><strong>What this means:</strong> the unredeemed portion has been returned to the programme budget for redistribution. You will not be charged anything; the system simply withdrew the unused benefit.</p>
<p>If you tried to redeem and were unable to, contact your district office within 14 days. We can investigate and, where appropriate, re-issue.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "VOUCHER_CANCELLED",
        "triggerEvent":    "VOUCHER_CANCELLED",
        "templateName":    "Voucher Cancelled",
        "category":        "PAYMENT",
        "priority":        "HIGH",
        "subjectEn":       "Your voucher has been cancelled — {voucher_code}",
        "bodyEn": """<p>Hello <strong>{first_name}</strong>,</p>
<p>Your subsidy voucher has been cancelled by Ministry staff.</p>
<p><strong>Voucher code:</strong> {voucher_code}<br>
<strong>Programme:</strong> {programme_name}<br>
<strong>Cancelled on:</strong> {cancelled_date}</p>
<p><strong>Reason:</strong></p>
<blockquote style="border-left:4px solid #6c757d;padding-left:16px;color:#495057;">{reason}</blockquote>
<p>The voucher is no longer valid. If you visit a distribution point with this voucher code, the system will refuse the redemption.</p>
<p>If you believe this cancellation is in error, contact your district office within 7 days.</p>
<p>— Ministry of Agriculture, Food Security and Nutrition</p>""",
    },
    {
        "templateCode":    "BUDGET_75PCT",
        "triggerEvent":    "BUDGET_75PCT",
        "templateName":    "Budget Envelope at 75%",
        "category":        "OPERATOR",
        "priority":        "NORMAL",
        "subjectEn":       "Budget alert: {programme_name} at {utilisation_pct}%",
        "bodyEn": """<p>Hello <strong>{recipient_name}</strong>,</p>
<p>Programme budget envelope crossed 75% utilisation:</p>
<p><strong>Programme:</strong> {programme_name}<br>
<strong>Envelope:</strong> {envelope_code}<br>
<strong>Allocated:</strong> LSL {allocated}<br>
<strong>Committed:</strong> LSL {committed}<br>
<strong>Expensed:</strong> LSL {expensed}<br>
<strong>Available:</strong> LSL {available}<br>
<strong>Utilisation:</strong> {utilisation_pct}%</p>
<p style="background:#fff3e0;border-left:4px solid #ef6c00;padding:12px 16px;">At 90% utilisation the envelope automatically freezes (no new approvals).</p>
<p><a href="{portal_url}">Open budget dashboard</a></p>
<p>— Farmers Portal automation</p>""",
    },
    {
        "templateCode":    "BUDGET_FROZEN",
        "triggerEvent":    "BUDGET_FROZEN",
        "templateName":    "Budget Envelope Frozen",
        "category":        "OPERATOR",
        "priority":        "HIGH",
        "subjectEn":       "Budget envelope FROZEN — no new approvals for {programme_name}",
        "bodyEn": """<p>Hello <strong>{recipient_name}</strong>,</p>
<p><strong>The budget envelope for {programme_name} has been frozen.</strong> No new applications can be approved for this programme until the envelope is unfrozen or topped up.</p>
<p><strong>Programme:</strong> {programme_name}<br>
<strong>Envelope:</strong> {envelope_code}<br>
<strong>Frozen at:</strong> {frozen_at}<br>
<strong>Reason:</strong> {reason}<br>
<strong>Allocated:</strong> LSL {allocated}<br>
<strong>Committed:</strong> LSL {committed}<br>
<strong>Expensed:</strong> LSL {expensed}<br>
<strong>Utilisation:</strong> {utilisation_pct}%</p>
<p><strong>What this means for citizens:</strong> existing vouchers continue to work; new applications will be auto-rejected.</p>
<p><strong>What this means for operators:</strong> field officers should not start new applications; pending applications can still be approved if balance covers them.</p>
<p><strong>What to do next:</strong></p>
<ol>
<li>Top up the envelope via Budget → Manual adjustments.</li>
<li>Re-allocate from another programme's available balance.</li>
<li>Roll forward unused balance from a prior cycle.</li>
</ol>
<p><a href="{portal_url}">Open budget dashboard</a></p>
<p>— Farmers Portal automation</p>""",
    },
]


def upsert_all(templates):
    """POST to formcreator-api /seed with a single fixture for spNotifTemplate.
    Uses businessKey=triggerevent so existing rows update and new ones insert."""
    # Joget Hibernate field IDs are camelCase (per the form definition);
    # the column names are lowercase only because Postgres folds unquoted
    # identifiers. The /seed endpoint reads the field IDs.
    #
    # recipientResolver per event:
    #   APPLICANT          → citizen-facing (the farmer the event is about)
    #   OPERATOR_LIST      → goes to the static address(es) in operatorRecipients
    #   FIELD_OFFICER_OF_DISTRICT
    #   FINANCE_OFFICERS
    #   SUPERVISOR_OF_DISTRICT
    RESOLVER_BY_EVENT = {
        "APP_SUBMITTED":         ("APPLICANT",              ""),
        "APP_APPROVED":          ("APPLICANT",              ""),
        "APP_UNDER_REVIEW":      ("APPLICANT",              ""),
        "APP_REJECTED":          ("APPLICANT",              ""),
        "APP_DECISION_PENDING":  ("SUPERVISOR_OF_DISTRICT", ""),
        "VOUCHER_ISSUED":        ("APPLICANT",              ""),
        "VOUCHER_REDEEMED":      ("APPLICANT",              ""),
        "VOUCHER_EXPIRING":      ("APPLICANT",              ""),
        "VOUCHER_EXPIRED":       ("APPLICANT",              ""),
        "VOUCHER_CANCELLED":     ("APPLICANT",              ""),
        "BUDGET_75PCT":          ("FINANCE_OFFICERS",       ""),
        "BUDGET_FROZEN":         ("FINANCE_OFFICERS",       ""),
    }

    rows = []
    for t in templates:
        evt = t["triggerEvent"]
        resolver, op_recipients = RESOLVER_BY_EVENT.get(evt, ("OPERATOR_LIST", "aarelaponin@gmail.com"))
        rows.append({
            "templateCode":         t["templateCode"],
            "triggerEvent":         t["triggerEvent"],
            "templateName":         t["templateName"],
            "category":             t["category"],
            "priority":             t["priority"],
            "emailEnabled":         "Y",
            "smsEnabled":           "Y",
            "isActive":             "Y",
            "sendImmediately":      "IMMEDIATE",
            "delayMinutes":         "0",
            "emailSubjectEn":       t["subjectEn"],
            "emailBodyEn":          t["bodyEn"],
            "recipientResolver":    resolver,
            "operatorRecipients":   op_recipients,
        })
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {
                "formId": "spNotifTemplate",
                "businessKey": "triggerEvent",
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
    g.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    # Pull existing rows to find which trigger events already have a row
    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    cur.execute("SELECT id, c_triggerevent FROM app_fd_spNotifTemplate")
    existing = {r[1].upper(): r[0] for r in cur.fetchall() if r[1]}
    cur.close(); conn.close()

    print(f"\n=== Found {len(existing)} existing rows; will seed {len(TEMPLATES)} templates ===\n")
    for tpl in TEMPLATES:
        trigger = tpl["triggerEvent"]
        existing_id = existing.get(trigger.upper())
        action = "UPDATE" if existing_id else "INSERT"
        print(f"  {action:<6} {trigger:<25} {tpl['templateName']}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\nApplying via /seed with businessKey=triggerevent ...")
    status, body = upsert_all(TEMPLATES)
    print(f"HTTP {status}")
    print(body[:600])
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
