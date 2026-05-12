#!/usr/bin/env python3
"""
Fix the submit_confirmation checkbox so it actually renders a tickable
option.

Root cause: I authored the mm_field with optionsBinderJson (StaticBinder
inline options), but MetaScreenElement.addCatalogOptions reads ONLY from
mm_field.optionsCatalogId (the catalog-code path). So the checkbox
rendered with zero options and was invisible.

Fix: create an mm_catalog `SUBMIT_CONFIRMATION` with one item, then
point the mm_field's optionsCatalogId at it.

Idempotent.
"""
import argparse, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}

CATALOG_ROW = {
    "code":      "SUBMIT_CONFIRMATION",
    "name":      "Submission confirmation",
    "scope":     "form",
    "itemsJson": json.dumps([
        {"value": "Y", "label": "I confirm submission of this application"}
    ])
}

MM_FIELD_UPDATE = {
    "screenId":              "0496bcb6-419d-477a-ab3b-1406f519e565",  # APP_REVIEW
    "storageKey":            "submit_confirmation",
    "label":                 "Submission",
    "widget":                "checkbox",
    "dataType":              "string",
    "defaultBehaviorOnError":"optional",
    "orderIndex":            "20",
    "helpText":              "Tick the box and save to formally submit your application. Without this tick, your changes are saved as DRAFT and you can return later.",
    "optionsCatalogId":      "SUBMIT_CONFIRMATION",
    "optionsBinderJson":     ""
}


def push(form_id, business_key, row):
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {"formId": form_id, "businessKey": business_key, "rows": [row]}
        ]
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/seed",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    print("1) Will upsert mm_catalog SUBMIT_CONFIRMATION (single item: Y / \"I confirm submission...\").")
    print("2) Will upsert mm_field submit_confirmation with optionsCatalogId=SUBMIT_CONFIRMATION.")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\nUpserting mm_catalog SUBMIT_CONFIRMATION...")
    s, b = push("mm_catalog", "code", CATALOG_ROW)
    print(f"  HTTP {s}: {b[:200]}")
    if s != 200: return 1

    print("\nUpdating mm_field submit_confirmation...")
    s, b = push("mm_field", "storageKey", MM_FIELD_UPDATE)
    print(f"  HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
