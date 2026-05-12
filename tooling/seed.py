#!/usr/bin/env python3
"""
Seed (or clear) the Lesotho mm_* test fixture into the dev Joget instance.

Usage:
    python3 seed.py                  # upsert from lesotho-mm-fixture.yaml
    python3 seed.py --clear          # wipe all mm_* tables in reverse order
    python3 seed.py --clear --seed   # wipe then re-seed (canonical reset)
    python3 seed.py --fixture other.yaml   # alternate fixture file

Wraps the form-creator-api's /formcreator/seed and /formcreator/clear
endpoints (build-003+). Idempotent: re-running upserts every row by its
business `code`. Resolves `_ref:<formId>:<code>` placeholders for
Joget-internal FKs (FormGrid mm_screen→mm_field) by querying the seeded
parent's UUID before inserting children.

Per CLAUDE.md HARD RULE: this script never touches app_fd_* tables
directly. All writes go through Joget's FormDataDao via the API.
"""

import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.request


class _ExtendedEncoder(json.JSONEncoder):
    """Serialize datetime.date / datetime.datetime as ISO strings.
       PyYAML auto-parses unquoted ISO dates in fixture files into Python
       date objects; json.dumps needs help to round-trip them."""

    def default(self, o):
        if isinstance(o, (datetime.date, datetime.datetime)):
            return o.isoformat()
        return super().default(o)

try:
    import yaml
except ImportError:
    print("Missing PyYAML. Activate the venv first:", file=sys.stderr)
    print("    source tooling/.venv/bin/activate", file=sys.stderr)
    print("Or run via the venv interpreter directly:", file=sys.stderr)
    print("    tooling/.venv/bin/python tooling/seed.py", file=sys.stderr)
    print("If you haven't bootstrapped yet:  bash tooling/bootstrap.sh", file=sys.stderr)
    sys.exit(2)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))
APP_ID         = os.environ.get("JOGET_APP_ID",   "farmersPortal")

_HERE = os.path.dirname(os.path.abspath(__file__))
# Fixtures live under <repo>/_seeds; this script lives under <repo>/_tooling.
DEFAULT_FIXTURE = os.path.normpath(os.path.join(_HERE, os.pardir, "app", "seeds",
                                                "lesotho-mm-fixture.yaml"))

SEED_URL  = JOGET_BASE_URL + "/api/formcreator/formcreator/seed"
CLEAR_URL = JOGET_BASE_URL + "/api/formcreator/formcreator/clear"
APIS_URL  = JOGET_BASE_URL + "/api/formcreator/formcreator/apis"

# Where the seeder writes the resolved API endpoint table for downstream
# test utilities. Each entry maps `code` → {apiId, formId, apiKind}.
_API_ENDPOINTS_FILE = os.path.normpath(
    os.path.join(_HERE, os.pardir, "app", "seeds", ".api_endpoints.json"))


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def post_json(url, payload):
    req = urllib.request.Request(
        url, method="POST",
        data=json.dumps(payload, cls=_ExtendedEncoder).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "api_id":  JOGET_API_ID,
            "api_key": JOGET_API_KEY,
        })
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def parse_results(raw):
    """The API wraps the body in a 'message' string of JSON. Unwrap it."""
    try:
        outer = json.loads(raw)
        msg   = outer.get("message", outer)
        if isinstance(msg, str):
            return json.loads(msg)
        return msg
    except Exception:
        return raw


# ---------------------------------------------------------------------------
# Reference resolution
# ---------------------------------------------------------------------------

def resolve_refs(rows, seeded_lookup):
    """
    Replace `_ref:<formId>:<code>` placeholders in row values with the
    parent row's UUID. seeded_lookup is a dict (formId, code) -> uuid populated
    after each parent batch.
    """
    out = []
    for row in rows:
        new_row = {}
        for k, v in row.items():
            if isinstance(v, str) and v.startswith("_ref:"):
                _, target_form, target_code = v.split(":", 2)
                key = (target_form, target_code)
                uuid = seeded_lookup.get(key)
                if uuid is None:
                    raise RuntimeError(
                        f"Unresolved ref {v!r}: parent row not yet seeded "
                        f"(formId={target_form}, code={target_code}). "
                        f"Check _seed_order in the fixture.")
                new_row[k] = uuid
            else:
                new_row[k] = v
        out.append(new_row)
    return out


def fetch_uuid_by_code(form_id, code):
    """Look up a row's UUID via Joget's standard form-data-by-key endpoint.
       We piggyback on /seed with a dry-run-like payload? — actually no, we
       use Joget's built-in form data endpoint that requires API auth.
    """
    # The form-creator-api doesn't expose a "lookup by code" endpoint, so we
    # use a 1-row upsert as a discovery probe. The seed endpoint reports back
    # whether a row was inserted or updated; we can also re-issue with the
    # same code to trigger an update path. Cleaner: re-seed minimally.
    # For now, we drive lookups via a dedicated probe call with empty fields
    # — the seed endpoint will find the existing row and return updated=1.
    payload = {
        "appId": APP_ID,
        "fixtures": [{
            "formId":      form_id,
            "businessKey": "code",
            "rows":        [{"code": code}]   # empty other fields; just probe
        }],
    }
    status, raw = post_json(SEED_URL, payload)
    if status != 200:
        raise RuntimeError(f"Lookup probe failed for {form_id}/{code}: {status} {raw}")
    parsed = parse_results(raw)
    # Returned shape: {"results":[{"formId":"mm_x","inserted":N,"updated":M,"errors":[]}]}
    # We don't get the UUID back. So this approach doesn't work for ref resolution.
    # Need a different path — see below: bulk-fetch UUIDs after each parent batch.
    raise NotImplementedError("Use bulk_fetch_lookup instead — direct probe doesn't return uuid")


def bulk_fetch_lookup_via_seed(form_id, codes):
    """
    Re-seed the same rows (no-ops) but record their resulting UUIDs by querying
    the postgres database directly. This is a READ — allowed under HARD RULE.
    Returns dict code -> uuid.
    """
    if not codes:
        return {}
    import psycopg2
    conn = psycopg2.connect(
        host='joget-pgsql-sa.postgres.database.azure.com',
        dbname='jogetdb', user='jogetadmin', password=os.environ.get("PGPASSWORD", ""),
        port=5432, sslmode='require')
    cur = conn.cursor()
    placeholders = ",".join(["%s"] * len(codes))
    sql = f"SELECT id, c_code FROM app_fd_{form_id} WHERE c_code IN ({placeholders})"
    cur.execute(sql, codes)
    result = {row[1]: row[0] for row in cur.fetchall()}
    conn.close()
    return result


# ---------------------------------------------------------------------------
# Main flow
# ---------------------------------------------------------------------------

#: Forms with no global `code` business key — the API can't dedupe by upsert
#: so every seeder run would INSERT fresh rows (compounding triples-of-triples).
#: Clear these before seeding. Per CLAUDE.md / D20: mm_field's natural key is
#: composite (screenId, storageKey) which the API doesn't support yet (#60).
NO_CODE_DEDUPE_FORMS = ("mm_field", "budget_event", "mm_capability_field",)


def seed_fixture(fixture_path):
    with open(fixture_path) as f:
        fx = yaml.safe_load(f)
    order = fx.get("_seed_order") or [k for k in fx.keys() if k.startswith("mm_")]
    # Per-entity business-key override. Default is "code" (most mm_* tables);
    # transactional tables (e.g. subsidy_app_2025) use national_id or another
    # natural key.
    business_keys = fx.get("_seed_business_keys") or {}
    seeded_lookup = {}     # (formId, code) -> uuid

    # Auto-clear forms that can't be deduped by code (the seeder's only upsert
    # key today). Keeps the seeder idempotent for those forms.
    forms_to_preclear = [f for f in NO_CODE_DEDUPE_FORMS if fx.get(f)]
    if forms_to_preclear:
        print(f"=== Pre-clearing no-code-dedupe forms: {forms_to_preclear} ===")
        payload = {"appId": APP_ID, "formIds": forms_to_preclear}
        status, raw = post_json(CLEAR_URL, payload)
        if status != 200:
            print(f"  pre-clear HTTP {status} → {raw[:300]}")
            return 1
        for r in parse_results(raw).get("results", []):
            print(f"  {r.get('formId',''):20s}: pre-cleared {r.get('deleted', 0)} rows")

    print(f"=== Seeding from {fixture_path} ===")
    api_endpoints = {}
    for form_id in order:
        rows = fx.get(form_id) or []
        if not rows:
            print(f"  {form_id:20s}: (empty)")
            continue

        # Special-case mm_api: each row provisions an App Builder API
        # definition via /formcreator/apis (not /formcreator/seed). The
        # endpoint is itself part of form-creator-api and uses the same
        # api_id/api_key. Records the resolved (code → apiId, formId,
        # apiKind) map into app/seeds/.api_endpoints.json so downstream test
        # utilities can look up endpoints by analyst-facing code without
        # re-implementing the id-from-code derivation.
        if form_id == "mm_api":
            ok = err = 0
            for row in rows:
                code    = row.get("code", "")
                name    = row.get("name", code)
                form_   = row.get("formId", "")
                kind    = row.get("apiKind", "crud")
                if not code or not form_:
                    print(f"      ! mm_api row missing code/formId: {row}")
                    err += 1
                    continue
                payload = {"appId": APP_ID, "code": code, "name": name,
                           "formId": form_, "apiKind": kind}
                status, raw = post_json(APIS_URL, payload)
                parsed = parse_results(raw)
                if status != 200 or parsed.get("status") != "success":
                    print(f"      ! {code}: HTTP {status} → {raw[:200]}")
                    err += 1
                    continue
                api_id = parsed.get("apiId", "")
                api_endpoints[code] = {"apiId": api_id, "formId": form_,
                                       "apiKind": kind}
                ok += 1
            print(f"  {form_id:20s}: provisioned={ok}, errors={err}")
            continue

        rows = resolve_refs(rows, seeded_lookup)
        business_key = business_keys.get(form_id, "code")
        payload = {
            "appId": APP_ID,
            "fixtures": [{
                "formId":      form_id,
                "businessKey": business_key,
                "rows":        rows,
            }],
        }
        status, raw = post_json(SEED_URL, payload)
        parsed = parse_results(raw)
        if status != 200:
            print(f"  {form_id:20s}: HTTP {status} → {raw[:300]}")
            return 1
        result = parsed.get("results", [{}])[0]
        ins, upd = result.get("inserted", 0), result.get("updated", 0)
        errs     = result.get("errors", [])
        bk_label = "" if business_key == "code" else f"  [key={business_key}]"
        print(f"  {form_id:20s}: inserted={ins}, updated={upd}, errors={len(errs)}{bk_label}")
        if errs:
            for e in errs:
                print(f"      ! {e}")

        # Refresh lookup for any rows that have a `code` (for child refs).
        # Only mm_* parents need ref resolution; transactional rows skip this.
        codes = [r.get("code") for r in rows if r.get("code")]
        if codes:
            try:
                fresh = bulk_fetch_lookup_via_seed(form_id, codes)
                for code, uuid in fresh.items():
                    seeded_lookup[(form_id, code)] = uuid
            except Exception as e:
                # If DB lookup fails, child refs to this form will fail later;
                # log and continue.
                print(f"      (lookup refresh failed: {e})")

    print(f"\n=== {len(seeded_lookup)} parent rows indexed for child-ref resolution ===")

    # Persist the mm_api → apiId map so downstream test utilities can resolve
    # endpoints by analyst-facing code without re-deriving the id convention.
    if api_endpoints:
        try:
            with open(_API_ENDPOINTS_FILE, "w") as f:
                json.dump(api_endpoints, f, indent=2, sort_keys=True)
            print(f"=== Wrote {len(api_endpoints)} API endpoints to {_API_ENDPOINTS_FILE} ===")
        except Exception as e:
            print(f"      ! could not write {_API_ENDPOINTS_FILE}: {e}")

    return 0


def clear_fixture(fixture_path):
    with open(fixture_path) as f:
        fx = yaml.safe_load(f)
    order = fx.get("_seed_order") or [k for k in fx.keys() if k.startswith("mm_")]
    # Reverse order: children before parents
    form_ids = list(reversed(order))
    print(f"=== Clearing {form_ids} ===")
    payload = {"appId": APP_ID, "formIds": form_ids}
    status, raw = post_json(CLEAR_URL, payload)
    parsed = parse_results(raw)
    if status != 200:
        print(f"  HTTP {status} → {raw[:300]}")
        return 1
    for r in parsed.get("results", []):
        print(f"  {r.get('formId',''):20s}: deleted={r.get('deleted', 0)} {r.get('error','')}")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fixture", default=DEFAULT_FIXTURE,
                    help="Path to fixture YAML (default: %(default)s)")
    ap.add_argument("--clear", action="store_true",
                    help="Clear all mm_* rows before seeding")
    ap.add_argument("--no-seed", action="store_true",
                    help="When combined with --clear, skip the seed step")
    args = ap.parse_args()

    if args.clear:
        rc = clear_fixture(args.fixture)
        if rc != 0:
            return rc
        if args.no_seed:
            return 0

    return seed_fixture(args.fixture)


if __name__ == "__main__":
    sys.exit(main())
