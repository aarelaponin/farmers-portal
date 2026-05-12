#!/usr/bin/env python3
"""
W3.2 — wire ApplicationOpenLoadBinder onto subsidyApplicationOperator2025.

The new loadBinder extends Joget's WorkflowFormBinder, so it does the
standard row load AND fires the SUBMITTED → UNDER_REVIEW lifecycle
transition the first time an operator opens an application.

Idempotent: skips if loadBinder.className already points at our class.
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

NEW_BINDER = {
    "className": "global.govstack.regbb.engine.binder.ApplicationOpenLoadBinder",
    "properties": {}
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
    cur.execute("SELECT json FROM app_form WHERE formid='subsidyApplicationOperator2025' AND appid='farmersPortal'")
    form = json.loads(cur.fetchone()[0])

    cur_binder = form.get("loadBinder", {})
    cur_class  = cur_binder.get("className", "(default)")
    if cur_class == NEW_BINDER["className"]:
        print(f"loadBinder already wired to {cur_class} — nothing to do.")
        return 0

    print(f"Replacing loadBinder: {cur_class} → {NEW_BINDER['className']}")
    out = copy.deepcopy(form)
    out["loadBinder"] = NEW_BINDER

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push_form(out)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
