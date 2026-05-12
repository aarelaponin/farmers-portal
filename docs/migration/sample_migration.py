#!/usr/bin/env python3
"""
Sample pilot-data migration script — Lesotho Farmers Portal.

Reads a CSV of pilot farmer records and creates corresponding
farmerBasicInfo + farmerResidency rows in the portal via REST.

Resumable: re-running skips records whose national_id already exists.
Failure-tolerant: errors are logged to a CSV; the migration continues.
MDM-validated: district / agro-zone / etc. codes are checked against
the portal's master-data BEFORE the migration starts; unknown codes
halt the run with a clear error.

Run:
    python3 sample_migration.py pilot_farmers.csv

Expected CSV columns (headers required, snake_case):
    national_id, first_name, last_name, gender, dob,
    phone, email, district, village, agro_zone,
    gps_lat, gps_lon, owned_rented_land_ha, cultivated_land_ha

Adapt to your pilot's schema by editing PILOT_TO_PORTAL.

LICENCE: Use this freely as a starting point. No warranty.
"""

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.request

# ---------------------------------------------------------------------------
# Configuration — UPDATE FOR YOUR ENVIRONMENT
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("PORTAL_BASE", "http://20.87.213.78:8080/jw")
API_KEY  = os.environ.get("PORTAL_KEY",  os.environ.get("JOGET_API_KEY", ""))

# API IDs — stable across environments.
API_FARMER_BASIC_INFO = "API-a7735b09-36be-453d-a385-cdff0c3df7b0"
API_FARMER_RESIDENCY  = "API-30dcfc48-44bf-4aaf-95df-8370590be7b4"
# RegBB API — used for the MDM list endpoint (build-109+).
API_REGBB             = "API-168e3678-1f9a-46fc-8c19-d0d9a917eb73"

# Mapping: pilot CSV column → portal field. Edit this to match your pilot's
# actual column names.
PILOT_TO_PORTAL = {
    # farmerBasicInfo
    "national_id":  "national_id",
    "first_name":   "first_name",
    "last_name":    "last_name",
    "gender":       "gender",
    "dob":          "date_of_birth",
    "phone":        "mobile_number",
    "email":        "email_address",
    # farmerResidency
    "district":           "district",
    "village":            "village",
    "agro_zone":          "agroEcologicalZone",
    "gps_lat":            "gpsLatitude",
    "gps_lon":            "gpsLongitude",
    "owned_rented_land_ha": "ownedRentedLand",
    "cultivated_land_ha":   "cultivatedLand",
}

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def _request(url, method, body, api_id):
    headers = {"api_id": api_id, "api_key": API_KEY}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except urllib.error.URLError as e:
        return -1, str(e)


def post(form_id, body, api_id):
    return _request(f"{BASE_URL}/api/form/{form_id}", "POST", body, api_id)


def parse(raw):
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


# ---------------------------------------------------------------------------
# MDM lookup pre-flight
# ---------------------------------------------------------------------------
# Two sources, in this priority:
#   1. Live API endpoint /jw/api/regbb/mdm/list?formId=<form>  (build-109+)
#      — always fresh; returns the current state of the portal.
#   2. Static CSVs in mdm_reference/  — fallback for offline development
#      OR if the portal endpoint isn't available yet.
#
# (The standard Joget per-form /list endpoint is enabled in API config but
# AppFormAPI doesn't implement it — calls return `{}`. So we route through
# the dedicated reg-bb-engine endpoint instead.)

MDM_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mdm_reference")


def load_lookup_live(form_id, key_field="code"):
    """Fetch master-data via the reg-bb-engine /mdm/list endpoint.
    Returns {key_field: name} dict, or None if the endpoint failed.

    NOTE: API Builder wraps every response in an envelope:
      {"date":"...", "code":"200", "message":"<inner JSON as string>"}
    So we must JSON-decode the outer envelope, then JSON-decode the
    `message` string to get the actual handler response."""
    url = f"{BASE_URL}/api/regbb/mdm/list?formId={form_id}"
    st, raw = _request(url, "GET", None, API_REGBB)
    if st != 200:
        return None
    try:
        outer = json.loads(raw)
        msg = outer.get("message", outer)
        body = json.loads(msg) if isinstance(msg, str) else msg
    except (json.JSONDecodeError, AttributeError):
        return None
    if not isinstance(body, dict) or body.get("status") != "ok":
        return None
    rows = body.get("rows", [])
    return {r[key_field]: r.get("name", r.get(key_field))
            for r in rows if r.get(key_field)}


def load_lookup_csv(filename, key_field="code"):
    """Fallback: load a master-data CSV from mdm_reference/ → {key_field: name}."""
    path = os.path.join(MDM_DIR, filename)
    if not os.path.exists(path):
        sys.exit(f"FATAL: master-data file not found: {path}\n"
                 f"Make sure the mdm_reference/ folder ships next to this script,\n"
                 f"OR ensure the portal's /regbb/mdm/list endpoint is reachable.")
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        return {row[key_field]: row.get("name", row[key_field])
                for row in reader if row.get(key_field)}


def load_lookup(form_id, csv_fallback, key_field="code"):
    """Try live endpoint; fall back to static CSV. Tells user which source."""
    live = load_lookup_live(form_id, key_field)
    if live is not None:
        return live, "live"
    return load_lookup_csv(csv_fallback, key_field), "csv-fallback"


def validate_codes(rows, lookup, csv_field, label):
    """Walk the CSV, check that every distinct value in csv_field is a key
    in `lookup`. Return list of unknown values. Doesn't stop — collects them."""
    seen = set()
    unknown = set()
    for row in rows:
        val = (row.get(csv_field) or "").strip()
        if not val:
            continue
        if val in seen:
            continue
        seen.add(val)
        if val not in lookup:
            unknown.add(val)
    return unknown


# ---------------------------------------------------------------------------
# Per-row migration
# ---------------------------------------------------------------------------

def existing_nids():
    """Resume safety: load the set of NIDs already in the registry from a
    file the migration writes itself (alongside the migration log). On the
    very first run this returns an empty set; on subsequent re-runs it
    contains the NIDs that succeeded last time so we don't double-import.

    NOTE: this is local-state-based, not portal-based, because the portal's
    `/list` endpoint isn't implemented (returns {}). If you have a CSV
    export of currently-migrated NIDs from MAFSN ICT, drop it at
    'already_migrated.txt' (one NID per line) and we'll pre-populate the
    skip set from there too."""
    seen = set()
    for path in ("already_migrated.txt", "migration_log.csv"):
        if not os.path.exists(path):
            continue
        with open(path, newline="") as f:
            if path.endswith(".csv"):
                # Pull NIDs that previously succeeded
                reader = csv.DictReader(f)
                for r in reader:
                    if r.get("status") in ("ok", "partial") and r.get("national_id"):
                        seen.add(r["national_id"])
            else:
                for line in f:
                    nid = line.strip()
                    if nid:
                        seen.add(nid)
    return seen


def migrate_row(row):
    """Migrate one pilot row. Returns (status, message)."""
    nid = row.get("national_id", "").strip()
    if not nid:
        return "error", "missing national_id"

    # Build farmerBasicInfo body
    fbi_body = {}
    for csv_col, portal_field in PILOT_TO_PORTAL.items():
        if portal_field in {"district", "village", "agroEcologicalZone",
                            "gpsLatitude", "gpsLongitude",
                            "ownedRentedLand", "cultivatedLand"}:
            continue  # those go in farmerResidency
        val = (row.get(csv_col) or "").strip()
        if val:
            fbi_body[portal_field] = val

    # Normalise gender to lowercase (portal expects 'male'/'female')
    if "gender" in fbi_body:
        fbi_body["gender"] = fbi_body["gender"].lower()

    # POST farmerBasicInfo
    st, raw = post("farmerBasicInfo", fbi_body, API_FARMER_BASIC_INFO)
    if st != 200:
        # HTTP 400/401/403/5xx — these are infrastructure/auth problems,
        # not field-level validation. Caller should usually STOP the run.
        return "error", f"farmerBasicInfo HTTP {st}: {raw[:200]}"
    fbi = parse(raw)
    if not isinstance(fbi, dict):
        return "error", f"unexpected response: {raw[:200]}"
    # Joget returns HTTP 200 even on validation failure; check `errors` + `id`.
    if fbi.get("errors"):
        err_str = "; ".join(f"{k}={v}" for k, v in fbi["errors"].items())
        return "error", f"validation: {err_str}"
    farmer_uuid = fbi.get("id")
    if not farmer_uuid:
        return "error", f"empty id in response: {raw[:200]}"

    # Build farmerResidency body, linking parent_id to the basicInfo row
    res_body = {"parent_id": farmer_uuid}
    for csv_col, portal_field in PILOT_TO_PORTAL.items():
        if portal_field not in {"district", "village", "agroEcologicalZone",
                                "gpsLatitude", "gpsLongitude",
                                "ownedRentedLand", "cultivatedLand"}:
            continue
        val = (row.get(csv_col) or "").strip()
        if val:
            res_body[portal_field] = val

    if "district" in res_body:
        # POST farmerResidency only if we have at least the mandatory district
        st, raw = post("farmerResidency", res_body, API_FARMER_RESIDENCY)
        if st != 200:
            return "partial", (f"basicInfo OK ({farmer_uuid}); "
                               f"residency HTTP {st}: {raw[:200]}")
        rp = parse(raw)
        if isinstance(rp, dict) and rp.get("errors"):
            err_str = "; ".join(f"{k}={v}" for k, v in rp["errors"].items())
            return "partial", (f"basicInfo OK ({farmer_uuid}); "
                               f"residency validation: {err_str}")

    return "ok", farmer_uuid


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv_path", help="Pilot CSV file")
    ap.add_argument("--log", default="migration_log.csv",
                    help="Output: per-row result log (default: migration_log.csv)")
    ap.add_argument("--dry-run", action="store_true",
                    help="Validate MDM codes only; don't write any rows")
    args = ap.parse_args()

    if not os.path.exists(args.csv_path):
        sys.exit(f"CSV not found: {args.csv_path}")

    print(f"Base URL : {BASE_URL}")
    print(f"CSV file : {args.csv_path}")
    print(f"Log file : {args.log}")
    print()

    # Step 1 — load source CSV
    with open(args.csv_path, newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    print(f"[1/4] Loaded {len(rows)} rows from CSV")

    # Step 2 — load + validate MDM (live endpoint with CSV fallback)
    print("[2/4] Loading MDM lookups...")
    districts, src1 = load_lookup("md03district", "districts.csv")
    print(f"      {len(districts)} districts ({src1})")
    agro_zones, src2 = load_lookup("md04agroEcologicalZo", "agro_zones.csv")
    print(f"      {len(agro_zones)} agro-zones ({src2})")

    halt = False
    for csv_col, lookup, label in [
        ("district",  districts,  "district"),
        ("agro_zone", agro_zones, "agro_zone"),
    ]:
        unknown = validate_codes(rows, lookup, csv_col, label)
        if unknown:
            print(f"      WARNING: {len(unknown)} unknown {label} value(s) in pilot CSV:")
            for u in sorted(unknown)[:20]:
                print(f"        - {u!r}")
            halt = True

    if halt:
        print("\nUnknown master-data values found. Build a mapping table and ")
        print("either translate them in the CSV or add the missing rows to MDM ")
        print("(via MAFSN authorised process) before continuing.")
        if not args.dry_run:
            sys.exit(1)

    if args.dry_run:
        print("\n--dry-run: stopping before any writes.")
        return 0

    # Step 3 — load already-migrated NIDs (from prior log, if any)
    print("[3/4] Checking already_migrated.txt + migration_log.csv ...")
    already = existing_nids()
    print(f"      {len(already)} NIDs to skip on resume")

    # Step 4 — migrate each row
    print(f"[4/4] Migrating {len(rows)} rows...\n")
    log_fh = open(args.log, "w", newline="")
    log = csv.writer(log_fh)
    log.writerow(["national_id", "status", "detail", "timestamp"])

    counts = {"ok": 0, "skip": 0, "error": 0, "partial": 0}
    for i, row in enumerate(rows, 1):
        nid = (row.get("national_id") or "").strip()
        if not nid:
            log.writerow(["", "error", "missing national_id", time.time()])
            counts["error"] += 1
            continue
        if nid in already:
            log.writerow([nid, "skip", "already migrated", time.time()])
            counts["skip"] += 1
            continue
        try:
            status, detail = migrate_row(row)
        except Exception as e:
            status, detail = "error", f"{type(e).__name__}: {e}"
        log.writerow([nid, status, detail, time.time()])
        counts[status] += 1
        if i % 50 == 0:
            print(f"  {i}/{len(rows)}: ok={counts['ok']} "
                  f"partial={counts['partial']} skip={counts['skip']} "
                  f"error={counts['error']}")

    log_fh.close()
    print()
    print("=" * 60)
    print(f"Migration complete:")
    print(f"  OK      : {counts['ok']}")
    print(f"  Partial : {counts['partial']}  (basicInfo OK, residency failed)")
    print(f"  Skipped : {counts['skip']}     (already migrated)")
    print(f"  Errors  : {counts['error']}")
    print(f"  Log     : {args.log}")
    print("=" * 60)
    return 0 if counts["error"] == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
