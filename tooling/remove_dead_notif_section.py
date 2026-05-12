#!/usr/bin/env python3
"""
Remove the dead `section_notifications` EmbeddedDatalist section
I added to spApplicationDecision earlier (W2.8 v1). The wizard
doesn't load this form, so the section never rendered — it's
harmless but should be removed to keep the form definition clean.

Replaced by the proper notification_timeline mm_field widget on
the OP_DECISION screen (build-132 + add_notif_timeline_mm_field.py).

Idempotent: skips if section already absent.
"""
import argparse, copy, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}


def push_form(form):
    payload = {
        "targetAppId":    "farmersPortal",
        "formId":         form["properties"]["id"],
        "formName":       form["properties"].get("name", form["properties"]["id"]),
        "tableName":      form["properties"].get("tableName", form["properties"]["id"]),
        "formDefinition": json.dumps(form),
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
    cur.execute("SELECT json FROM app_form WHERE formid='spApplicationDecision' AND appid='farmersPortal'")
    form = json.loads(cur.fetchone()[0])

    sections = form.get("elements", [])
    before = len(sections)
    out = copy.deepcopy(form)
    out["elements"] = [s for s in out["elements"]
                       if s.get("properties", {}).get("id") != "section_notifications"]
    after = len(out["elements"])

    if before == after:
        print("section_notifications already absent — nothing to do.")
        return 0

    print(f"Removed section_notifications. Sections {before} → {after}.")
    print("Remaining sections:")
    for s in out["elements"]:
        p = s.get("properties", {})
        print(f"  - id={p.get('id'):30s} label={p.get('label','')[:60]}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push_form(out)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
