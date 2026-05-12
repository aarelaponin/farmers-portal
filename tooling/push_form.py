#!/usr/bin/env python3
"""
Push a Joget form definition through form-creator-api.

Usage:
    python3 push_form.py app/forms/mm/mm-field.json
    python3 push_form.py app/forms/mm/mm-field.json --create-crud
    python3 push_form.py app/forms/subsidyApplicationOperator2025.json --create-crud

Wraps the raw form JSON in the envelope the /forms endpoint expects
({formId, formName, tableName, formDefinition, createCrud}), reading the
three required metadata fields out of the form JSON's top-level
`properties` block. Auth uses the same JOGET_API_ID / JOGET_API_KEY env
vars seed.py reads — see CLAUDE.md "Calling form-creator-api endpoints".

Triggering this for an mm_* form is what makes Joget reconcile the
schema (actionType=0): adds any new columns the updated form definition
declares. Triggering it with --create-crud regenerates the form's
companion datalist + userview menu — the path used after flagging
mm_field rows with displayOnList=Y to refresh the operator inbox.

Per CLAUDE.md HARD RULE: this script never touches app_form / app_fd_*
directly; all writes go through the form-creator-api → FormDefinitionDao
path.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  "a5af1181f77b4a62b481725b6410e965")
FORMS_URL      = JOGET_BASE_URL + "/api/formcreator/formcreator/forms"


def push(form_path: str, create_crud: bool, app_id: str = None) -> int:
    if not os.path.isfile(form_path):
        print(f"error: file not found: {form_path}", file=sys.stderr)
        return 2

    with open(form_path, "r", encoding="utf-8") as f:
        raw = f.read()

    # Validate it's parsable JSON and pull metadata from the form's
    # top-level properties block — same fields Joget itself uses.
    try:
        form = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"error: {form_path} is not valid JSON: {e}", file=sys.stderr)
        return 2

    props = form.get("properties", {})
    form_id    = props.get("id")
    form_name  = props.get("name")
    table_name = props.get("tableName")
    if not (form_id and form_name and table_name):
        print(f"error: {form_path} missing one of properties.id / name / tableName "
              f"(got id={form_id!r}, name={form_name!r}, tableName={table_name!r})",
              file=sys.stderr)
        return 2

    # The form-creator-api endpoint expects the form JSON as an embedded
    # STRING under the `formDefinition` key — not a nested object. Send
    # the raw text we read so escape semantics stay byte-exact.
    payload = {
        "formId":         form_id,
        "formName":       form_name,
        "tableName":      table_name,
        "formDefinition": raw,
        "createCrud":     bool(create_crud),
    }

    url = FORMS_URL
    if app_id:
        url += "?appId=" + urllib.parse.quote(app_id)

    req = urllib.request.Request(
        url, method="POST",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "api_id":  JOGET_API_ID,
            "api_key": JOGET_API_KEY,
        })

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = resp.read().decode("utf-8")
            print(f"[{resp.status}] {form_id} ({'crud' if create_crud else 'no-crud'})")
            print(body)
            return 0 if 200 <= resp.status < 300 else 1
    except urllib.error.HTTPError as e:
        print(f"[{e.code}] {form_id}", file=sys.stderr)
        print(e.read().decode("utf-8"), file=sys.stderr)
        return 1
    except urllib.error.URLError as e:
        print(f"network error: {e}", file=sys.stderr)
        return 1


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    p.add_argument("form_path", help="Path to the form definition JSON")
    p.add_argument("--create-crud", action="store_true",
                   help="Also (re)generate the companion datalist + userview menu")
    p.add_argument("--app-id", default=None,
                   help="Override target appId (defaults to current app per Joget)")
    args = p.parse_args(argv)
    return push(args.form_path, args.create_crud, args.app_id)


if __name__ == "__main__":
    sys.exit(main())
