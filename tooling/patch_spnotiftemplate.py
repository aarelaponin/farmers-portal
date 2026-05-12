#!/usr/bin/env python3
"""
Extend spNotifTemplate form with four new operator-editable fields:

  recipientResolver       — SelectBox enum: APPLICANT / OPERATOR_LIST /
                            FIELD_OFFICER_OF_DISTRICT / FINANCE_OFFICERS /
                            SUPERVISOR_OF_DISTRICT
  operatorRecipients      — TextArea, semicolon-separated email addresses
                            (consulted only when recipientResolver = OPERATOR_LIST)
  emailBodyPlaintext      — TextArea, plaintext alt for EN body
  emailBodyPlaintextSt    — TextArea, plaintext alt for ST body

Idempotent: if any of the 4 fields already present, leaves them alone.

HARD RULE compliant: pushes via form-creator-api (POST /jw/api/formcreator/
formcreator/forms), never raw SQL on app_form.
"""
import argparse, copy, json, sys, urllib.error, urllib.request

import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")

JOGET   = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = os.environ.get("JOGET_API_KEY", "")
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}


# ─── New field definitions (Joget DX 8.x SelectBox / TextArea shape) ─────────

ROUTING_HEADER = {
    "className": "org.joget.apps.form.lib.CustomHTML",
    "properties": {
        "id":    "routingSection",
        "label": "Recipient Routing",
        "value": ("<h4 style=\"margin:24px 0 8px 0;padding:8px 12px;"
                  "background:#1e6091;color:white;border-radius:4px;\">"
                  "📬 Recipient Routing"
                  "</h4>"
                  "<p style=\"margin:4px 0 16px 0;color:#555;\">"
                  "Where should this notification be delivered? The dispatcher "
                  "resolves the address at send time, per template. Change the "
                  "resolver below to redirect a whole event class without "
                  "touching code."
                  "</p>")
    }
}

RECIPIENT_RESOLVER = {
    "className": "org.joget.apps.form.lib.SelectBox",
    "properties": {
        "id":            "recipientResolver",
        "label":         "Recipient Resolver",
        "value":         "APPLICANT",
        "options": [
            {"value": "APPLICANT",
             "label": "APPLICANT — send to the citizen the event is about (looked up from registry by national ID)"},
            {"value": "OPERATOR_LIST",
             "label": "OPERATOR_LIST — send to the email list in 'Operator Recipients' below"},
            {"value": "FIELD_OFFICER_OF_DISTRICT",
             "label": "FIELD_OFFICER_OF_DISTRICT — send to the field officer of the applicant's district"},
            {"value": "FINANCE_OFFICERS",
             "label": "FINANCE_OFFICERS — send to all users in role_finance_officer"},
            {"value": "SUPERVISOR_OF_DISTRICT",
             "label": "SUPERVISOR_OF_DISTRICT — send to the district supervisor for the applicant's district"}
        ],
        "validator": {
            "className": "org.joget.apps.form.lib.DefaultValidator",
            "properties": {"type": "", "message": "", "mandatory": "true"}
        },
        "addEmptyOption":"false"
    }
}

OPERATOR_RECIPIENTS = {
    "className": "org.joget.apps.form.lib.TextArea",
    "properties": {
        "id":          "operatorRecipients",
        "label":       "Operator Recipients (only if resolver = OPERATOR_LIST)",
        "rows":        "3",
        "placeholder": "finance@mafsn.gov.ls;supervisor@mafsn.gov.ls",
        "value":       ""
    }
}

EMAIL_BODY_PLAINTEXT_EN = {
    "className": "org.joget.apps.form.lib.TextArea",
    "properties": {
        "id":    "emailBodyPlaintext",
        "label": "Email Body — Plaintext Alt (English, optional)",
        "rows":  "6",
        "placeholder": "Leave blank to auto-derive from HTML body by stripping tags.",
        "value": ""
    }
}

EMAIL_BODY_PLAINTEXT_ST = {
    "className": "org.joget.apps.form.lib.TextArea",
    "properties": {
        "id":    "emailBodyPlaintextSt",
        "label": "Email Body — Plaintext Alt (Sesotho, optional)",
        "rows":  "6",
        "placeholder": "Leave blank to auto-derive from HTML body by stripping tags.",
        "value": ""
    }
}


def find_column(form_json):
    """Return the (parent, list, index) tuple for the inner Column that
    holds the field cascade. spNotifTemplate has Form → Section → Column → [...fields]."""
    section = form_json["elements"][0]
    column  = section["elements"][0]
    return column, column["elements"]


def insert_after(elements, anchor_id, *new):
    """Insert new elements after the element with id=anchor_id. Idempotent
    on each new element's id."""
    existing_ids = {e.get("properties", {}).get("id") for e in elements}
    to_add = [n for n in new if n["properties"]["id"] not in existing_ids]
    if not to_add:
        return 0
    for i, e in enumerate(elements):
        if e.get("properties", {}).get("id") == anchor_id:
            for offset, n in enumerate(to_add):
                elements.insert(i + 1 + offset, n)
            return len(to_add)
    return 0


def push_form(form_json):
    # form-creator-api request field names per
    # plugins/form-creator-api/.../constants/ApiConstants.java:
    #   formDefinition, formId, formName, tableName, targetAppId.
    payload = {
        "targetAppId":    "farmersPortal",
        "formId":         form_json["properties"]["id"],
        "formName":       form_json["properties"].get("name",
                           form_json["properties"]["id"]),
        "tableName":      form_json["properties"].get("tableName",
                           form_json["properties"]["id"]),
        "formDefinition": json.dumps(form_json),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/forms",
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

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_form WHERE formid='spNotifTemplate' AND appid='farmersPortal'")
    form = json.loads(cur.fetchone()[0])

    # Make a working copy
    out = copy.deepcopy(form)
    column, elements = find_column(out)

    added = 0
    added += insert_after(elements, "triggerEvent",
                          ROUTING_HEADER, RECIPIENT_RESOLVER, OPERATOR_RECIPIENTS)
    added += insert_after(elements, "emailBodyEn", EMAIL_BODY_PLAINTEXT_EN)
    added += insert_after(elements, "emailBodySt", EMAIL_BODY_PLAINTEXT_ST)

    # Snapshot of new element-id list for verification
    final_ids = [e.get("properties", {}).get("id") for e in elements]
    print(f"\n{added} new fields inserted. Final field order:")
    for i, fid in enumerate(final_ids):
        marker = "  +" if fid in ("routingSection","recipientResolver","operatorRecipients","emailBodyPlaintext","emailBodyPlaintextSt") else "   "
        print(f"  {marker} {i:>2}. {fid}")

    if added == 0:
        print("\nAll 4 fields already present — nothing to do.")
        return 0

    if args.dry_run:
        # Drop a copy locally for inspection
        with open("/tmp/forms/spNotifTemplate.patched.json", "w") as f:
            json.dump(out, f, indent=2)
        print("\nDry-run — patched form saved to /tmp/forms/spNotifTemplate.patched.json")
        return 0

    print("\nApplying via /forms ...")
    status, body = push_form(out)
    print(f"HTTP {status}")
    print(body[:600])
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
