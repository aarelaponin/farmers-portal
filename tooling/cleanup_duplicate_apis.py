#!/usr/bin/env python3
"""
Delete duplicate App Builder API definitions from app_builder.

Background. The May 2026 IM Phase 3 audit found that 30 API names had 2-3
rows each (88 rows total, 58 of them duplicates), all created on
2026-01-26 in three batched seeder runs at ~16:10/16:44/17:03 UTC. That
predates the form-creator-api `/apis` endpoint becoming upsert-by-code
idempotent (L4-1, May 2026). The triplets show up in App Composer and as
"plugin not installed" / "duplicate API" warnings on form load.

Strategy. For each (appId, name) group with count > 1, keep the EARLIEST
row by datecreated, delete the rest via the `/formcreator/apis/delete`
endpoint (form-creator-api build-021+). Per CLAUDE.md HARD RULE the
delete goes through Joget's BuilderDefinitionDao — no raw SQL on
app_builder.

Usage:
    python3 tooling/cleanup_duplicate_apis.py             # dry-run; prints what would be deleted
    python3 tooling/cleanup_duplicate_apis.py --apply     # actually delete

Requires:
- form-creator-api build-021 or later deployed (provides the new
  /formcreator/apis/delete POST endpoint).
- The formCreatorApi's enabledPaths must include `post:/formcreator/apis/delete`
  (run --update-enabled-paths once before --apply; idempotent).
- psycopg2 for the audit query.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from collections import defaultdict

try:
    import psycopg2
except ImportError:  # pragma: no cover
    sys.exit("psycopg2 required: pip install psycopg2-binary")


JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  "a5af1181f77b4a62b481725b6410e965")
APP_ID         = os.environ.get("JOGET_APP_ID",   "farmersPortal")

PG_HOST     = os.environ.get("PGHOST",     "joget-pgsql-sa.postgres.database.azure.com")
PG_DATABASE = os.environ.get("PGDATABASE", "jogetdb")
PG_USER     = os.environ.get("PGUSER",     "jogetadmin")
PG_PASSWORD = os.environ.get("PGPASSWORD", "Joget@DB#2026!")
PG_PORT     = int(os.environ.get("PGPORT", "5432"))

DELETE_URL = JOGET_BASE_URL + "/api/formcreator/formcreator/apis/delete"
APIS_URL   = JOGET_BASE_URL + "/api/formcreator/formcreator/apis"


def find_duplicates():
    """Return [{name, keep_id, delete_ids: [..]}] for each (name) with >1 row.

    The earliest datecreated row is kept; the rest are queued for delete.
    Read-only — SELECT only, not a write to app_builder."""
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, port=PG_PORT, sslmode="require")
    cur = conn.cursor()
    cur.execute("""
        SELECT id, name, datecreated
          FROM app_builder
         WHERE appid = %s
           AND type = 'api'
         ORDER BY name, datecreated ASC
    """, (APP_ID,))
    by_name = defaultdict(list)
    for row in cur.fetchall():
        by_name[row[1]].append({"id": row[0], "datecreated": row[2]})
    conn.close()

    # NEVER delete rows whose name matches a protected API. formCreatorApi is
    # protected because that's the API serving this script's own request (and
    # all other tooling — tooling/seed.py, push_*.py reference its api_id).
    PROTECTED_NAMES = {"formCreatorApi"}

    out = []
    for name, rows in by_name.items():
        if len(rows) <= 1:
            continue
        if name in PROTECTED_NAMES:
            print(f"  (skip) {name}: {len(rows)} rows present but protected — will NOT auto-delete; "
                  f"resolve manually in App Composer.")
            continue
        # Keep earliest by datecreated (first in our ASC order).
        keep = rows[0]
        delete = rows[1:]
        out.append({
            "name":       name,
            "keep_id":    keep["id"],
            "keep_dt":    str(keep["datecreated"]),
            "delete_ids": [r["id"] for r in delete],
            "delete_dts": [str(r["datecreated"]) for r in delete],
        })
    return out


def post_json(url, payload, timeout=60, api_id=None):
    """POST a JSON body. Uses JOGET_API_ID by default; pass api_id=... to
       override (needed for /formcreator/apis/delete which is enabled only
       on the API-formCreatorApi row, not on the legacy UUID row, until
       App Composer updates the legacy row's enabled paths)."""
    req = urllib.request.Request(url, method="POST",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json",
                 "api_id":  api_id or JOGET_API_ID,
                 "api_key": JOGET_API_KEY})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def update_enabled_paths():
    """Re-upsert formCreatorApi with the additional post:/formcreator/apis/delete
       enabled path. Idempotent — running it multiple times is safe.

       The existing formCreatorApi is a plugin-class API (className-based, not
       formId-based). Use the upsert /formcreator/apis with className +
       enabledPaths. Code = 'formCreatorApi' (matches existing app_builder.id
       suffix prefix-stripping logic in form-creator-api)."""
    payload = {
        "appId":        APP_ID,
        "code":         "formCreatorApi",
        "name":         "formCreatorApi",
        "className":    "global.govstack.formcreator.lib.FormCreatorServiceProvider",
        "enabledPaths": ("post:/formcreator/forms;post:/formcreator/clear;"
                         "post:/formcreator/seed;post:/formcreator/datalists;"
                         "post:/formcreator/userviews;post:/regbb/eval;"
                         "post:/regbb/submit;post:/formcreator/apis;"
                         "post:/formcreator/apis/delete"),
        "apiKind":      "plugin",
    }
    status, raw = post_json(APIS_URL, payload)
    print(f"  POST /formcreator/apis (formCreatorApi enabledPaths refresh): HTTP {status}")
    print(f"  {raw[:300]}")


def parse_message(raw):
    try:
        outer = json.loads(raw)
        msg = outer.get("message", outer)
        return json.loads(msg) if isinstance(msg, str) else msg
    except Exception:
        return {"raw": raw}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true",
                    help="Actually delete (otherwise dry-run only).")
    ap.add_argument("--update-enabled-paths", action="store_true",
                    help="Refresh formCreatorApi.enabledPaths to include "
                         "post:/formcreator/apis/delete (run once before "
                         "first --apply).")
    args = ap.parse_args()

    if args.update_enabled_paths:
        print("=== Updating formCreatorApi enabledPaths ===")
        update_enabled_paths()
        return 0

    dupes = find_duplicates()
    if not dupes:
        print("No duplicate API rows found. Nothing to do.")
        return 0

    total_to_delete = sum(len(d["delete_ids"]) for d in dupes)
    print(f"=== Found {len(dupes)} API names with duplicates "
          f"({total_to_delete} rows to delete; keeping the earliest of each) ===")
    print()
    for d in dupes:
        print(f"  {d['name']}")
        print(f"    KEEP   {d['keep_id']}  ({d['keep_dt']})")
        for did, dt in zip(d["delete_ids"], d["delete_dts"]):
            print(f"    DELETE {did}  ({dt})")
        print()

    if not args.apply:
        print("Dry-run only. Re-run with --apply to actually delete.")
        return 0

    # Apply: send all the delete ids in one POST. Endpoint is idempotent
    # (skips ids that don't exist, returns per-id error in 'failed' list).
    #
    # api_id="API-formCreatorApi": the only API row with /formcreator/apis/delete
    # enabled is the one created by --update-enabled-paths. The legacy UUID-id
    # row (referenced by all other tooling) won't accept the call until its
    # enabledPaths is patched manually in App Composer (post-cleanup step).
    all_ids = [did for d in dupes for did in d["delete_ids"]]
    payload = {"appId": APP_ID, "apiIds": all_ids}
    print(f"=== Calling DELETE for {len(all_ids)} ids (via api_id=API-formCreatorApi) ===")
    status, raw = post_json(DELETE_URL, payload, timeout=120,
                            api_id="API-formCreatorApi")
    print(f"HTTP {status}")
    parsed = parse_message(raw)
    print(f"  status:  {parsed.get('status')}")
    print(f"  deleted: {parsed.get('deleted')}")
    failed = parsed.get("failed") or []
    if failed:
        print(f"  failed: {len(failed)}")
        for f in failed[:10]:
            print(f"    ! {f}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
