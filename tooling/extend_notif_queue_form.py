#!/usr/bin/env python3
"""
Extend notification_queue form with the audit-shape fields needed by W2.6
(state machine + operator transparency):

  backend            — TextField — which dispatcher backend handled it
                       (gmail, ses, LOG_ONLY, http, mailtrap)
  intendedRecipient  — TextField — where the resolver said it should go
                       (real applicant email/phone, before test-mode redirect)
  actualRecipient    — TextField — where it was actually sent
                       (test inbox in test mode, real address in live mode)
  correlationId      — TextField — parent business record id (application_id,
                       voucher_code, envelope_code) for cross-reference
  subject            — TextField — rendered subject line (for emails, blank for SMS)
  testMode           — Radio Y/N — was the redirect active when this fired?

Idempotent: if a field already exists, leaves it in place.

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


def text(fid, label, **kw):
    p = {"id": fid, "label": label, "value": "", **kw}
    return {"className": "org.joget.apps.form.lib.TextField", "properties": p}

def radio(fid, label, options, **kw):
    return {
        "className": "org.joget.apps.form.lib.Radio",
        "properties": {
            "id": fid, "label": label,
            "options": [{"value": v, "label": l} for v, l in options],
            **kw
        }
    }


NEW_FIELDS = [
    text("backend",           "Backend",
         placeholder="gmail, ses, LOG_ONLY, http, mailtrap"),
    text("intendedRecipient", "Intended Recipient",
         placeholder="real applicant address (before test-mode redirect)"),
    text("actualRecipient",   "Actual Recipient",
         placeholder="where actually sent (test inbox in test mode)"),
    text("correlationId",     "Correlation ID",
         placeholder="application_id / voucher_code / envelope_code"),
    text("subject",           "Subject",
         placeholder="rendered subject line"),
    radio("testMode", "Was Test Mode active?",
          [("Y","Y — redirect was in effect"),("N","N — live recipient")],
          value="Y"),
]


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
    # Anchor not found — append at end
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

    # Anchor each insertion at a sensible existing field
    anchor_map = {
        "backend":           "channel",
        "intendedRecipient": "backend",
        "actualRecipient":   "intendedRecipient",
        "correlationId":     "eventCode",
        "subject":           "varsJson",
        "testMode":          "actualRecipient",
    }
    added = []
    for f in NEW_FIELDS:
        fid = f["properties"]["id"]
        anchor = anchor_map.get(fid, "eventCode")
        if insert_after(elements, anchor, f):
            added.append(fid)
    print(f"\nInserted {len(added)} new fields: {added}")

    final_ids = [e.get("properties", {}).get("id") for e in elements]
    print("\nFinal field order:")
    for i, fid in enumerate(final_ids):
        marker = "+ " if fid in added else "  "
        print(f"  {marker}{i:>2}. {fid}")

    if not added:
        print("\nAll fields already present — nothing to do.")
        return 0

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\nApplying...")
    s, b = push_form(out)
    print(f"HTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
