#!/usr/bin/env python3
"""
install_app.py — install the Farmers Portal into a fresh Joget instance.

The inverse of sync_pull.py. Reads every JSON / YAML under `app/` and pushes
it into the target Joget via form-creator-api. Idempotent — re-running upserts
every artefact. Designed for a new developer cloning the repo and standing up
a working farmersPortal without contacting the original team.

Pre-conditions (see INSTALL.md for the full playbook):
  1. Joget DX 8.1.x is running and reachable at $JOGET_BASE_URL.
  2. An empty Joget app with id=$JOGET_APP_ID exists (create it via App
     Composer → Design Apps → New App). Default id is `farmersPortal`.
  3. The 12 mandatory plugin JARs are uploaded (form-creator-api FIRST).
     See plugins/BUILD.md.
  4. An API Builder credential is configured against the `formcreator` API;
     api_id and api_key are in $JOGET_API_ID and $JOGET_API_KEY.
  5. .env is sourced (or all env vars set manually).

What it does, in order:
  1. Push every `app/forms/*.json` via /formcreator/forms
  2. Push every `app/datalists/*.json` via /formcreator/datalists
  3. Push every `app/userviews/*.json` via /formcreator/userviews
  4. Seed every `app/seeds/master-data/*.yaml` (the md*+mm_* tables that
     sync_pull.py dumped) via /formcreator/seed

What it does NOT do:
  * Upload plugin JARs (manual step via App Composer — security boundary)
  * Create the Joget app shell (one-time manual step in App Composer)
  * Run the mm_* fixture seed from lesotho-mm-fixture.yaml — that flow lives
    in `seed.py` and serves a different audience (analysts seeding new
    metadata). For a fresh-install reproduction of the current deployed
    state, the master-data YAMLs are authoritative.

Per CLAUDE.md HARD RULE: no raw SQL is ever issued. Every write goes through
form-creator-api → FormDefinitionDao / DatalistDefinitionDao / FormDataDao.

Usage:
    python3 tooling/install_app.py             # full install
    python3 tooling/install_app.py --skip-data # forms+datalists+userviews only
    python3 tooling/install_app.py --only-data # master-data seeds only
    python3 tooling/install_app.py --dry-run   # print plan, don't push
"""

import argparse
import glob
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

# Master-data YAML reading uses PyYAML (already required by seed.py).
try:
    import yaml
except ImportError:
    print("ERROR: PyYAML missing. Run `bash tooling/bootstrap.sh` first, then",
          file=sys.stderr)
    print("       activate the venv: source tooling/.venv/bin/activate",
          file=sys.stderr)
    sys.exit(2)


# ─── Configuration ──────────────────────────────────────────────────────────

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://localhost:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  "")
APP_ID         = os.environ.get("JOGET_APP_ID",   "farmersPortal")

FORMS_URL      = JOGET_BASE_URL + "/api/formcreator/formcreator/forms"
DATALISTS_URL  = JOGET_BASE_URL + "/api/formcreator/formcreator/datalists"
USERVIEWS_URL  = JOGET_BASE_URL + "/api/formcreator/formcreator/userviews"
SEED_URL       = JOGET_BASE_URL + "/api/formcreator/formcreator/seed"

_HERE       = pathlib.Path(__file__).resolve().parent
_REPO_ROOT  = _HERE.parent
FORMS_DIR     = _REPO_ROOT / "app" / "forms"
DATALISTS_DIR = _REPO_ROOT / "app" / "datalists"
USERVIEWS_DIR = _REPO_ROOT / "app" / "userviews"
SEEDS_DIR     = _REPO_ROOT / "app" / "seeds" / "master-data"

# Columns produced by sync_pull.py that we MUST NOT push back via /seed —
# they're metadata Joget assigns. id is regenerated; the rest are populated
# by AppService.storeFormData automatically.
_SYSTEM_COLS = {
    "id", "datecreated", "datemodified",
    "createdby", "createdbyname",
    "modifiedby", "modifiedbyname",
}


# ─── HTTP plumbing ──────────────────────────────────────────────────────────

def _check_env():
    missing = []
    if not JOGET_API_ID:  missing.append("JOGET_API_ID")
    if not JOGET_API_KEY: missing.append("JOGET_API_KEY")
    if missing:
        print(f"ERROR: missing required env vars: {', '.join(missing)}",
              file=sys.stderr)
        print("       Copy .env.example to .env, fill in the values, and source it.",
              file=sys.stderr)
        return False
    return True


def post_json(url, payload, timeout=60):
    req = urllib.request.Request(
        url, method="POST",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "api_id":  JOGET_API_ID,
            "api_key": JOGET_API_KEY,
        })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except urllib.error.URLError as e:
        return 0, str(e)


def unwrap_message(body_text):
    """form-creator-api wraps responses as {date,code,message:<json-string>}."""
    try:
        outer = json.loads(body_text)
        inner = outer.get("message", outer)
        if isinstance(inner, str):
            try:
                return json.loads(inner)
            except json.JSONDecodeError:
                return inner
        return inner
    except Exception:
        return body_text


# ─── Phase 1: forms ─────────────────────────────────────────────────────────

def push_forms(dry_run=False):
    files = sorted(FORMS_DIR.glob("*.json"))
    print(f"\n=== Phase 1: forms ({len(files)} files) ===")
    ok = err = 0
    for f in files:
        raw = f.read_text(encoding="utf-8")
        try:
            form = json.loads(raw)
        except json.JSONDecodeError as e:
            print(f"  {f.name:50s} BAD JSON: {e}")
            err += 1
            continue
        props = form.get("properties", {})
        form_id    = props.get("id")
        form_name  = props.get("name")
        table_name = props.get("tableName")
        if not (form_id and form_name and table_name):
            print(f"  {f.name:50s} missing id/name/tableName")
            err += 1
            continue
        if dry_run:
            print(f"  {form_id:40s} (would push)")
            ok += 1
            continue
        payload = {
            "formId":         form_id,
            "formName":       form_name,
            "tableName":      table_name,
            "formDefinition": raw,
            "createCrud":     False,
        }
        url = FORMS_URL + "?appId=" + urllib.parse.quote(APP_ID)
        status, body = post_json(url, payload)
        if status == 200:
            print(f"  {form_id:40s} ok")
            ok += 1
        else:
            print(f"  {form_id:40s} HTTP {status} — {body[:150]}")
            err += 1
    print(f"--- Phase 1 done: {ok} ok, {err} errors ---")
    return err


# ─── Phase 2: datalists ─────────────────────────────────────────────────────

def push_datalists(dry_run=False):
    files = sorted(DATALISTS_DIR.glob("*.json"))
    print(f"\n=== Phase 2: datalists ({len(files)} files) ===")
    ok = err = 0
    for f in files:
        raw = f.read_text(encoding="utf-8")
        try:
            d = json.loads(raw)
        except json.JSONDecodeError as e:
            print(f"  {f.name:50s} BAD JSON: {e}")
            err += 1
            continue
        dl_id   = d.get("id")
        dl_name = d.get("name", dl_id)
        if not dl_id:
            print(f"  {f.name:50s} missing 'id'")
            err += 1
            continue
        if dry_run:
            print(f"  {dl_id:40s} (would push)")
            ok += 1
            continue
        payload = {
            "appId":        APP_ID,
            "datalistId":   dl_id,
            "datalistName": dl_name,
            "json":         json.dumps(d, separators=(",", ":")),
        }
        status, body = post_json(DATALISTS_URL, payload)
        if status == 200:
            inner = unwrap_message(body) or {}
            op = inner.get("operation", "?") if isinstance(inner, dict) else "?"
            print(f"  {dl_id:40s} {op}")
            ok += 1
        else:
            print(f"  {dl_id:40s} HTTP {status} — {body[:150]}")
            err += 1
    print(f"--- Phase 2 done: {ok} ok, {err} errors ---")
    return err


# ─── Phase 3: userviews ─────────────────────────────────────────────────────

def push_userviews(dry_run=False):
    files = sorted(USERVIEWS_DIR.glob("*.json"))
    print(f"\n=== Phase 3: userviews ({len(files)} files) ===")
    ok = err = 0
    for f in files:
        raw = f.read_text(encoding="utf-8")
        try:
            d = json.loads(raw)
        except json.JSONDecodeError as e:
            print(f"  {f.name:50s} BAD JSON: {e}")
            err += 1
            continue
        setting_props = (d.get("setting") or {}).get("properties") or {}
        uv_id   = d.get("id") or setting_props.get("userviewId")
        uv_name = d.get("name") or setting_props.get("userviewName") or uv_id
        if not uv_id:
            print(f"  {f.name:50s} missing userview id")
            err += 1
            continue
        if dry_run:
            print(f"  {uv_id:40s} (would push)")
            ok += 1
            continue
        payload = {
            "appId":        APP_ID,
            "userviewId":   uv_id,
            "userviewName": uv_name,
            "json":         json.dumps(d, separators=(",", ":")),
        }
        status, body = post_json(USERVIEWS_URL, payload)
        if status == 200:
            print(f"  {uv_id:40s} ok")
            ok += 1
        else:
            print(f"  {uv_id:40s} HTTP {status} — {body[:150]}")
            err += 1
    print(f"--- Phase 3 done: {ok} ok, {err} errors ---")
    return err


# ─── Phase 4: master-data seeds ─────────────────────────────────────────────

def _yaml_rows_to_seed_rows(yaml_rows):
    """Strip `c_` prefix and drop Joget metadata columns. Returns the row list
    in the shape `/formcreator/seed` expects."""
    seed_rows = []
    for row in yaml_rows:
        if not isinstance(row, dict):
            continue
        clean = {}
        for k, v in row.items():
            if k in _SYSTEM_COLS:
                continue
            field = k[2:] if k.startswith("c_") else k
            # Skip None values — the seeder treats absent and None identically
            # but None-as-string lands as the literal string "None" in some
            # paths. Safer to omit.
            if v is None:
                continue
            clean[field] = v
        if clean:
            seed_rows.append(clean)
    return seed_rows


def seed_master_data(dry_run=False):
    files = sorted(SEEDS_DIR.glob("*.yaml"))
    print(f"\n=== Phase 4: master-data seeds ({len(files)} files) ===")
    ok = err = empty = 0
    for f in files:
        # Filename like md03district.yaml or mm_catalog.yaml → form_id is the
        # stem. (sync_pull.py strips the app_fd_ prefix when writing.)
        form_id = f.stem
        try:
            data = yaml.safe_load(f.read_text(encoding="utf-8"))
        except yaml.YAMLError as e:
            print(f"  {form_id:30s} BAD YAML: {e}")
            err += 1
            continue
        if not data:
            print(f"  {form_id:30s} (empty)")
            empty += 1
            continue
        seed_rows = _yaml_rows_to_seed_rows(data)
        if not seed_rows:
            print(f"  {form_id:30s} (no rows after cleaning)")
            empty += 1
            continue
        if dry_run:
            print(f"  {form_id:30s} (would seed {len(seed_rows)} rows)")
            ok += 1
            continue
        payload = {
            "appId": APP_ID,
            "fixtures": [{
                "formId":      form_id,
                "businessKey": "code",
                "rows":        seed_rows,
            }],
        }
        status, body = post_json(SEED_URL, payload)
        if status == 200:
            inner = unwrap_message(body) or {}
            results = inner.get("results", []) if isinstance(inner, dict) else []
            if results:
                r = results[0]
                ins = r.get("inserted", 0)
                upd = r.get("updated", 0)
                errs = r.get("errors", [])
                if errs:
                    print(f"  {form_id:30s} inserted={ins} updated={upd} "
                          f"errors={len(errs)} (first: {errs[0]!s:.80s})")
                    err += 1
                else:
                    print(f"  {form_id:30s} inserted={ins} updated={upd}")
                    ok += 1
            else:
                print(f"  {form_id:30s} ok (no result detail)")
                ok += 1
        else:
            print(f"  {form_id:30s} HTTP {status} — {body[:150]}")
            err += 1
    print(f"--- Phase 4 done: {ok} ok, {err} errors, {empty} empty ---")
    return err


# ─── Orchestration ──────────────────────────────────────────────────────────

def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    p.add_argument("--skip-data", action="store_true",
                   help="Skip Phase 4 (master-data seeds)")
    p.add_argument("--only-data", action="store_true",
                   help="Skip Phases 1-3, run only master-data seeding")
    p.add_argument("--dry-run", action="store_true",
                   help="Print what would happen, don't actually push")
    args = p.parse_args()

    if not args.dry_run and not _check_env():
        return 2

    print(f"Target Joget : {JOGET_BASE_URL}")
    print(f"App id       : {APP_ID}")
    print(f"Repo root    : {_REPO_ROOT}")
    if args.dry_run:
        print("DRY RUN — no writes will occur.")

    total_errs = 0
    if not args.only_data:
        total_errs += push_forms(args.dry_run)
        total_errs += push_datalists(args.dry_run)
        total_errs += push_userviews(args.dry_run)
    if not args.skip_data:
        total_errs += seed_master_data(args.dry_run)

    print()
    if total_errs == 0:
        print("install_app.py: all phases completed successfully.")
        print("Next: run `make test` to verify the install.")
        return 0
    print(f"install_app.py: completed with {total_errs} error(s). Review the log.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
