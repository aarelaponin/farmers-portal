#!/usr/bin/env python3
"""
W3.1 — Add lifecycleState TextField to the subsidyApplication2025 form.

The W3.1 lifecycle state machine (DRAFT / SUBMITTED / UNDER_REVIEW /
APPROVED / REJECTED / PENDING_REVIEW / WITHDRAWN) lives in a parallel
column to c_status. c_status keeps its rules-driven fine-grained values
(auto_approved, auto_rejected, pending_data_clarification) so existing
dashboards and reports keep working; c_lifecycleState is the coarse-
grained operator-facing audit-anchored phase.

This script adds the field, anchored after the existing status column.
Idempotent: skips if already present.

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

NEW_FIELD = {
    "className": "org.joget.apps.form.lib.TextField",
    "properties": {
        "id":          "lifecycleState",
        "label":       "Lifecycle State",
        "value":       "",
        "placeholder": "draft | submitted | under_review | approved | rejected | pending_review | withdrawn",
        "readonly":    "true",
    }
}


def find_field(elements, fid):
    """Recursive scan — return (parent_list, index) or None."""
    for i, e in enumerate(elements or []):
        if e.get("properties", {}).get("id") == fid:
            return (elements, i)
        sub = e.get("elements")
        if sub:
            r = find_field(sub, fid)
            if r is not None: return r
    return None


def already_present(form_json):
    return find_field(form_json.get("elements", []), "lifecycleState") is not None


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
    ap.add_argument("--form-id", default="subsidyApplication2025",
                    help="form to patch (default subsidyApplication2025)")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_form WHERE formid=%s AND appid='farmersPortal'",
                (args.form_id,))
    row = cur.fetchone()
    if not row:
        print(f"Form {args.form_id} not found.", file=sys.stderr); return 2
    form = json.loads(row[0])

    if already_present(form):
        print(f"lifecycleState already present in {args.form_id} — nothing to do.")
        return 0

    out = copy.deepcopy(form)

    # Anchor after status field, OR append to the last section's first column.
    status_loc = find_field(out.get("elements", []), "status")
    if status_loc is not None:
        parent_list, idx = status_loc
        parent_list.insert(idx + 1, NEW_FIELD)
        print("Inserted lifecycleState after status field.")
    else:
        # Fall back to appending in the first section/column
        sec = out.get("elements", [])
        if sec and sec[0].get("elements"):
            col = sec[0]["elements"][0]
            col.setdefault("elements", []).append(NEW_FIELD)
            print("Status field not found; appended lifecycleState to first column.")
        else:
            print("Could not find a column to insert into.", file=sys.stderr); return 2

    if args.dry_run:
        print("Dry-run — not pushing.")
        return 0

    s, b = push_form(out)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
