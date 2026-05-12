#!/usr/bin/env python3
"""
Convert the visible `lifecycleState` TextField on subsidyApplication2025
to a HiddenField. The column still gets created and the value still
flows through Joget's Hibernate mapping; it just doesn't render to the
citizen on every wizard tab.

W3.1 added it as a TextField (placeholder showing valid states) so the
column would materialise after the form save triggered schema
reconciliation. That's done — now the field can hide. Citizens shouldn't
see (let alone type into) an internal lifecycle column; the canonical
write path is AppAudit.transition() from the storeBinder.

Idempotent: skips if already HiddenField.
HARD RULE compliant: pushes via form-creator-api.
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


def find_lifecycle(elements):
    """Return (parent_list, index) of any element whose id == lifecycleState."""
    for i, e in enumerate(elements or []):
        if e.get("properties", {}).get("id") == "lifecycleState":
            return (elements, i)
        sub = e.get("elements")
        if sub:
            r = find_lifecycle(sub)
            if r is not None: return r
    return None


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
    ap.add_argument("--form-id", default="subsidyApplication2025")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_form WHERE formid=%s AND appid='farmersPortal'", (args.form_id,))
    row = cur.fetchone()
    if not row:
        print(f"Form {args.form_id} not found", file=sys.stderr); return 2
    form = json.loads(row[0])

    loc = find_lifecycle(form.get("elements", []))
    if loc is None:
        print("No lifecycleState element found — nothing to do.")
        return 0
    parent_list, idx = loc
    cur_el = parent_list[idx]
    cur_cls = cur_el.get("className", "")
    if cur_cls == "org.joget.apps.form.lib.HiddenField":
        print("lifecycleState already HiddenField — nothing to do.")
        return 0

    print(f"Found lifecycleState as {cur_cls.split('.')[-1]}")
    out = copy.deepcopy(form)
    loc2 = find_lifecycle(out.get("elements", []))
    parent_list2, idx2 = loc2
    parent_list2[idx2] = {
        "className": "org.joget.apps.form.lib.HiddenField",
        "properties": {
            "id":    "lifecycleState",
            "label": "",
            "value": ""
        }
    }
    print("Replaced with HiddenField.")

    if args.dry_run:
        print("Dry-run — not pushing.")
        return 0

    s, b = push_form(out)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
