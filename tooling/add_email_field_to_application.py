#!/usr/bin/env python3
"""
Add the email_address field to the citizen application's Applicant
Identity tab (APP_APPLICANT screen) AND wire it to auto-fill from the
farmer registry on NID entry.

Two operations:
  1) Insert mm_field email_address on screen APP_APPLICANT.
  2) Update mm_action AUTOFILL_FROM_FARMER_REGISTRY.configJson to add
     a new fieldMapping: target=email_address sourceField=email.

The FarmerByNidAdapter already exposes 'email' → c_email_address on
farmerBasicInfo (see registry/FarmerByNidAdapter.java line 67) so no
plugin change is needed. The bot_pull JS attached by MetaScreenElement
on the national_id field will pick up the new mapping and auto-fill
this column when the citizen types their NID.

Idempotent: skips if mm_field already present and the action mapping
already contains the email target.
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

SCREEN_ID = "89de041b-6a9f-4940-ae7e-3471f12eec5d"   # APP_APPLICANT
STORAGE_KEY = "email_address"

MM_FIELD = {
    "screenId":               SCREEN_ID,
    "storageKey":             STORAGE_KEY,
    "label":                  "Email address",
    "widget":                 "text",
    "dataType":               "string",
    "defaultBehaviorOnError": "optional",
    "orderIndex":             "55",   # between contact_phone (5) and signature (6)
    "helpText":               "Used for application notifications. Auto-filled from the farmer registry when you enter your National ID.",
}

ACTION_CODE = "AUTOFILL_FROM_FARMER_REGISTRY"
NEW_MAPPING = {
    "target":          "email_address",
    "sourceCapability": "farmer",
    "sourceField":     "email"
}


def push_mm_field():
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {"formId": "mm_field", "businessKey": "storageKey", "rows": [MM_FIELD]}
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


def push_mm_action(action_row):
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {"formId": "mm_action", "businessKey": "code", "rows": [action_row]}
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

    conn = psycopg2.connect(**PG); cur = conn.cursor()

    # 1. Check mm_field state.
    cur.execute("""SELECT id FROM app_fd_mm_field
                   WHERE c_screenid=%s AND c_storagekey=%s""",
                (SCREEN_ID, STORAGE_KEY))
    field_exists = cur.fetchone() is not None
    print(f"mm_field {STORAGE_KEY} already on APP_APPLICANT? {field_exists}")

    # 2. Read current AUTOFILL configJson.
    cur.execute("""SELECT id, c_code, c_name, c_kind, c_configjson, c_triggerjson
                   FROM app_fd_mm_action WHERE c_code=%s""", (ACTION_CODE,))
    arow = cur.fetchone()
    if not arow:
        print(f"ERROR: mm_action '{ACTION_CODE}' not found", file=sys.stderr)
        return 2
    action_id, code, name, kind, configjson, triggerjson = arow
    cfg = json.loads(configjson)
    mappings = cfg.get("fieldMappings", [])
    targets = [m.get("target") for m in mappings]
    mapping_present = "email_address" in targets
    print(f"existing fieldMappings targets: {targets}")
    print(f"email_address mapping already present? {mapping_present}")

    if field_exists and mapping_present:
        print("\nNothing to do — already wired.")
        return 0

    if not mapping_present:
        new_cfg = copy.deepcopy(cfg)
        new_cfg["fieldMappings"].append(NEW_MAPPING)
        print(f"\nProposed configJson update — appending mapping:\n  {NEW_MAPPING}")
    else:
        new_cfg = None

    if args.dry_run:
        print("\nDry-run — not applying.")
        return 0

    # Apply.
    if not field_exists:
        print("\nInserting mm_field...")
        s, b = push_mm_field()
        print(f"  HTTP {s}: {b[:300]}")
        if s != 200: return 1

    if new_cfg is not None:
        print("\nUpdating mm_action configJson...")
        action_row = {
            "code":         code,
            "name":         name,
            "kind":         kind,
            "configJson":   json.dumps(new_cfg, indent=2),
            "triggerJson":  triggerjson or ""
        }
        s, b = push_mm_action(action_row)
        print(f"  HTTP {s}: {b[:300]}")
        if s != 200: return 1

    print("\nDone. Hard-refresh the citizen wizard's Applicant Identity tab —")
    print("the Email address field should appear and auto-fill on NID entry.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
