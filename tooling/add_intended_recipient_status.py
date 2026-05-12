#!/usr/bin/env python3
"""
Add intendedRecipientStatus TextField to the notification_queue form.

Reason: when an applicant has no contact on the registry (no email_address /
no mobile), the resolver returns 0 recipients and the dispatcher writes an
empty string to intendedRecipient. The bare empty string is ambiguous in
the operator UI — could be "applicant has no contact" or "we never tried
to resolve". This column records the explicit reason so operators get a
forensic flag they can filter on.

Allowed values today (written by EmailDispatcher / SmsDispatcher build-128+):
  resolved                 — resolver returned ≥1 recipient
  no_contact_on_registry   — applicant has no email/phone on registry
  operator_list_empty      — OPERATOR_LIST resolver, free-text list blank
  resolver_error           — resolver threw an exception

Idempotent: if the field already exists, leaves it in place.
HARD RULE compliant: pushes via form-creator-api.
"""
import argparse, copy, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}

NEW_FIELD = {
    "className": "org.joget.apps.form.lib.TextField",
    "properties": {
        "id":          "intendedRecipientStatus",
        "label":       "Intended Recipient Status",
        "value":       "",
        "placeholder": "resolved | no_contact_on_registry | operator_list_empty | resolver_error",
        "readonly":    "true",
    }
}


def find_column(form_json):
    section = form_json["elements"][0]
    column  = section["elements"][0]
    return column["elements"]


def insert_after(elements, anchor_id, new_field):
    """Idempotent insert. Returns True if added, False if already present."""
    for e in elements:
        if e.get("properties", {}).get("id") == new_field["properties"]["id"]:
            return False
    for i, e in enumerate(elements):
        if e.get("properties", {}).get("id") == anchor_id:
            elements.insert(i + 1, new_field)
            return True
    elements.append(new_field)
    return True


def push_form(form_json):
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
    cur.execute("SELECT json FROM app_form WHERE formid='notification_queue' AND appid='farmersPortal'")
    form = json.loads(cur.fetchone()[0])

    out = copy.deepcopy(form)
    elements = find_column(out)

    # Anchor immediately after intendedRecipient so the two columns sit together.
    added = insert_after(elements, "intendedRecipient", NEW_FIELD)
    if not added:
        print("Field already present — nothing to do.")
        return 0

    print("Inserted: intendedRecipientStatus (after intendedRecipient)")
    print("\nNew field order around the insertion point:")
    for i, e in enumerate(elements):
        fid = e.get("properties", {}).get("id", "?")
        marker = "+ " if fid == "intendedRecipientStatus" else "  "
        print(f"  {marker}{i:>2}. {fid}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push_form(out)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
