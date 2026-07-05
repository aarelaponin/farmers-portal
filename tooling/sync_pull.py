#!/usr/bin/env python3
"""
sync_pull.py — pull deployed Joget app state into the repo (ADR-033).

Pulls four streams from Postgres and writes them into the repo as
source-of-truth files:

  1. app_form        → app/forms/<id>.json                       (one file per form)
  2. app_datalist    → app/datalists/<id>.json                    (one file per datalist)
  3. app_userview    → app/userviews/<id>.json                    (one file per userview)
  4. app_fd_md*  +
     app_fd_mm_*    → app/seeds/master-data/<table-id>.yaml      (one YAML per table)

All output is run through credential-placeholder substitution before
write. The script is idempotent — running it overwrites the local
files with the current deployed state. Run after any App Composer
session, then `git diff app/`, then commit + push.

Sync ritual (per ADR-033):

    python3 tooling/sync_pull.py
    git diff app/                 # review what changed
    git add app/
    git commit -m "sync: pull App Composer edits from Joget"
    git push origin main

HARD-RULE compliance: only SELECT queries against app_form / app_datalist
/ app_userview / app_fd_* tables. No writes. Joget metadata is not
modified at all.
"""
import os
import pathlib
import psycopg2

APP_ID = "farmersPortal"

PG = dict(
    host=os.environ.get("PGHOST", "joget-pgsql-sa.postgres.database.azure.com"),
    dbname=os.environ.get("PGDATABASE", "jogetdb"),
    user=os.environ.get("PGUSER", "jogetadmin"),
    password=os.environ.get("PGPASSWORD", ""),
    port=int(os.environ.get("PGPORT", "5432")),
    sslmode="require",
)

# Credentials we never want committed even by accident
PLACEHOLDERS = {
    "a5af1181f77b4a62b481725b6410e965": "<JOGET_API_KEY>",
    "Joget@DB#2026!":                   "<PGPASSWORD>",
}


def scrub(text: str) -> str:
    """Apply credential placeholder substitution to any string going to disk."""
    if not isinstance(text, str):
        return text
    for k, v in PLACEHOLDERS.items():
        text = text.replace(k, v)
    return text


# ─── Form / datalist / userview pull ──────────────────────────────────────

def pull_definitions(cur, table: str, id_col: str, target_dir: str) -> int:
    """Pull all rows from app_form / app_datalist / app_userview as
    individual JSON files into target_dir, one file per id.

    Note: app_form uses 'formid' as its id column; app_datalist and
    app_userview use 'id'. Also filters to the latest appversion only."""
    pathlib.Path(target_dir).mkdir(parents=True, exist_ok=True)
    cur.execute(
        f"""SELECT {id_col}, json FROM {table}
             WHERE appid=%s
               AND appversion=(SELECT max(appversion) FROM {table} WHERE appid=%s)
             ORDER BY {id_col}""",
        (APP_ID, APP_ID))
    rows = cur.fetchall()
    for id_, body in rows:
        # body is text; scrub before writing
        path = pathlib.Path(target_dir) / f"{id_}.json"
        path.write_text(scrub(body or ""), encoding="utf-8")
    return len(rows)


# ─── Form-data table pull (shared helper) ───────────────────────────────────

def get_table_columns(cur, table: str):
    cur.execute("""
        SELECT column_name FROM information_schema.columns
         WHERE table_name=%s
         ORDER BY ordinal_position
    """, (table,))
    return [r[0] for r in cur.fetchall()]


def _dump_table_as_yaml(cur, table: str, target_dir: pathlib.Path) -> int:
    """Dump one app_fd_* table to <target_dir>/<short_table>.yaml.
    Returns row count written."""
    cols = get_table_columns(cur, table)
    col_list = ", ".join(cols)
    cur.execute(f"SELECT {col_list} FROM {table} ORDER BY id")
    rows = cur.fetchall()

    short = table[7:] if table.startswith("app_fd_") else table
    path = target_dir / f"{short}.yaml"

    if not rows:
        path.write_text(f"# {table}\n# (no rows at sync time)\n[]\n",
                        encoding="utf-8")
        return 0

    lines = [f"# {table}\n# {len(rows)} row(s) at sync time\n"]
    for row in rows:
        lines.append("- ")
        first = True
        for col, val in zip(cols, row):
            if val is None:
                rendered = "null"
            elif isinstance(val, bool):
                rendered = "true" if val else "false"
            elif isinstance(val, (int, float)):
                rendered = str(val)
            else:
                s = scrub(str(val))
                if "\n" in s:
                    indented = "\n".join("    " + ln for ln in s.split("\n"))
                    rendered = "|-\n" + indented
                else:
                    esc = s.replace("\\", "\\\\").replace('"', '\\"')
                    rendered = f'"{esc}"'
            prefix = "" if first else "  "
            lines.append(f"{prefix}{col}: {rendered}\n")
            first = False
        lines.append("\n")

    path.write_text("".join(lines), encoding="utf-8")
    return len(rows)


# ─── Master-data pull (md_* + mm_* — the configuration catalogs) ────────────

def pull_master_data(cur, target_dir: str) -> tuple[int, int]:
    """Pull every app_fd_md* + app_fd_mm_* table as per-table YAML.
    These are the master-data / metamodel catalogs — analyst-authored
    configuration. Returns (table_count, total_row_count)."""
    pathlib.Path(target_dir).mkdir(parents=True, exist_ok=True)
    cur.execute("""
        SELECT table_name FROM information_schema.tables
         WHERE table_schema='public'
           AND (table_name LIKE 'app_fd_md%' OR table_name LIKE 'app_fd_mm_%')
         ORDER BY table_name
    """)
    tables = [r[0] for r in cur.fetchall()]
    total_rows = 0
    for table in tables:
        total_rows += _dump_table_as_yaml(cur, table, pathlib.Path(target_dir))
    return len(tables), total_rows


# ─── Operational data pull (everything else app_fd_*) ───────────────────────

def _owned_operational_tables(cur, existing_data_dir: str) -> set:
    """Return the set of physical app_fd_* tables that belong to APP_ID.

    On a SHARED Joget database (several apps in one schema — as on the
    Lesotho training server, which also hosts a driving-licence / national-ID
    suite) app_fd_* form-data tables are NOT tagged by appid, so a blind
    "pull every app_fd_*" scoops up other apps' tables. We scope to this
    app by the union of:

      1. tables declared by APP_ID's own forms (app_form.tablename), and
      2. app_fd_* tables already tracked in the repo (app/seeds/data/*.yaml)
         — this grandfathers in plugin-created tables that have no form
         definition (e.g. reg_bb_eval_audit written by reg-bb-engine's
         RoutingEvaluator).

    Tables that are neither form-backed nor already tracked (i.e. another
    app's tables) are excluded, so the pull never pollutes this repo with a
    foreign app's data.
    """
    cur.execute(
        "SELECT DISTINCT 'app_fd_' || tablename FROM app_form WHERE appid=%s",
        (APP_ID,))
    owned = {r[0] for r in cur.fetchall() if r[0]}
    ddir = pathlib.Path(existing_data_dir)
    if ddir.exists():
        for f in ddir.glob("*.yaml"):
            owned.add("app_fd_" + f.stem)
    return owned


def pull_operational_data(cur, target_dir: str) -> tuple[int, int]:
    """Pull APP_ID's operational app_fd_* tables (NOT md_*/mm_*) as per-table
    YAML. This is operational data — applications, vouchers, registry entries,
    audit logs, budget ledger, notification history, etc.

    Scoping (see _owned_operational_tables): on a shared Joget DB the pull is
    restricted to tables owned by APP_ID (form-declared + already-tracked),
    so foreign apps sharing the database are never captured.

    Per ADR-033 this is *normally* excluded from the routine sync because
    on a production-shaped instance it would contain real citizen data.
    On this dev environment (fictional data, no real PII), the operator
    has explicitly opted in to capture it for full restore capability.

    HARD-RULE compliance: SELECT only; restore path goes through
    /formcreator/seed which uses Joget's FormDataDao, not raw SQL.
    Returns (table_count, total_row_count)."""
    pathlib.Path(target_dir).mkdir(parents=True, exist_ok=True)
    owned = _owned_operational_tables(cur, target_dir)
    cur.execute("""
        SELECT table_name FROM information_schema.tables
         WHERE table_schema='public'
           AND table_name LIKE 'app_fd_%'
           AND table_name NOT LIKE 'app_fd_md%'
           AND table_name NOT LIKE 'app_fd_mm_%'
         ORDER BY table_name
    """)
    tables = [r[0] for r in cur.fetchall() if r[0] in owned]
    total_rows = 0
    for table in tables:
        total_rows += _dump_table_as_yaml(cur, table, pathlib.Path(target_dir))
    return len(tables), total_rows


# ─── Environment variables pull (Id Generator counters, etc.) ───────────────

def pull_environment_variables(cur, target_path: str) -> int:
    """Pull env-var rows to a single YAML. These hold runtime state Joget
    uses across requests — most notably the Id Generator field's monotonic
    counter (FR-XXXXXX, PC-XXXXXX, AP-XXXXXX).

    Joget version drift: DX 8.x has used different table names for env
    vars over the years. Probe information_schema to find the actual name
    on this instance rather than hard-coding."""
    cur.execute("""
        SELECT table_name FROM information_schema.tables
         WHERE table_schema='public'
           AND (table_name='app_environment_variable'
             OR table_name='app_env_variable'
             OR table_name='app_envvariable'
             OR table_name='env_variable')
         ORDER BY table_name
         LIMIT 1
    """)
    row = cur.fetchone()
    if not row:
        # No env-var table on this Joget — record an empty file and move on.
        pathlib.Path(target_path).parent.mkdir(parents=True, exist_ok=True)
        pathlib.Path(target_path).write_text(
            "# env-var table not found on this Joget version\n[]\n",
            encoding="utf-8")
        return 0
    env_table = row[0]
    cur.execute(f"""
        SELECT id, value, remarks FROM {env_table}
         WHERE appid=%s
           AND appversion=(SELECT max(appversion) FROM {env_table}
                            WHERE appid=%s)
         ORDER BY id
    """, (APP_ID, APP_ID))
    rows = cur.fetchall()
    p = pathlib.Path(target_path)
    p.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        p.write_text("# app_environment_variable\n# (no rows at sync time)\n[]\n",
                     encoding="utf-8")
        return 0
    lines = [f"# app_environment_variable\n# {len(rows)} row(s) at sync time\n"]
    for id_, value, remarks in rows:
        v = scrub(str(value or ""))
        r = scrub(str(remarks or ""))
        v_esc = v.replace("\\", "\\\\").replace('"', '\\"')
        r_esc = r.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'- id: "{id_}"\n')
        lines.append(f'  value: "{v_esc}"\n')
        lines.append(f'  remarks: "{r_esc}"\n\n')
    p.write_text("".join(lines), encoding="utf-8")
    return len(rows)


# ─── App Builder (API endpoint definitions) pull ────────────────────────────

def pull_app_builder(cur, target_dir: str) -> int:
    """Pull every row from app_builder where type='api' for our app as
    individual JSON files. These are the API endpoint definitions consumed
    by /jw/api/<apiPath>/... routes — NID auto-fill, MDM list, dashboard
    /jw/api/<form>/data fetches, etc. NOT exported by JWA. The JSON column
    holds the API definition; we scrub credential placeholders before write.

    Schema (verified May 2026): app_builder(id, name, type, json, appid,
    appversion, datecreated, datemodified, createdby, modifiedby, description)
    where id is the API-<UUID> and name is the api path (e.g. 'formcreator',
    'regbb', 'gis', '01.01', 'farmer-basic-info', etc.).

    HARD-RULE compliance: SELECT only; never writes app_builder."""
    pathlib.Path(target_dir).mkdir(parents=True, exist_ok=True)

    # Discover the actual columns so we don't break if Joget adds a column.
    cur.execute("""
        SELECT column_name FROM information_schema.columns
         WHERE table_name='app_builder'
         ORDER BY ordinal_position
    """)
    cols = [r[0] for r in cur.fetchall()]
    if not cols:
        # Table doesn't exist on this Joget version — skip silently.
        return 0

    col_list = ", ".join(cols)
    cur.execute(
        f"""SELECT {col_list} FROM app_builder
             WHERE appid=%s
               AND type='api'
               AND appversion=(SELECT max(appversion) FROM app_builder
                                WHERE appid=%s AND type='api')
             ORDER BY name""",
        (APP_ID, APP_ID))
    rows = cur.fetchall()

    for row in rows:
        rd = dict(zip(cols, row))
        # File name is the api path. Some paths contain dots (e.g. '01.01')
        # which are filesystem-safe but visually noisy — keep them verbatim
        # so the round-trip is faithful.
        name = rd.get("name") or rd.get("id")
        # Build a JSON envelope with everything we'll need to re-provision
        # this API on a fresh install.
        envelope = {
            "id":           rd.get("id"),
            "name":         name,
            "type":         rd.get("type"),
            "appId":        rd.get("appid"),
            "appVersion":   rd.get("appversion"),
            "description":  rd.get("description"),
            # The actual API definition — Joget stores this as a JSON string
            # in the `json` column. Keep as-is (with credential scrub) so
            # install_app.py can POST it verbatim to /formcreator/apis.
            "json":         scrub(rd.get("json") or ""),
        }
        out = pathlib.Path(target_dir) / f"{name}.json"
        # Pretty-print the envelope; the embedded 'json' string stays opaque.
        import json as _json
        out.write_text(_json.dumps(envelope, indent=2, sort_keys=False,
                                    default=str),
                       encoding="utf-8")
    return len(rows)


# ─── Main ──────────────────────────────────────────────────────────────────

def main():
    if not PG["password"]:
        print("ERROR: PGPASSWORD env var must be set.", flush=True)
        return 1

    here = pathlib.Path(__file__).resolve().parent.parent  # repo root
    app_dir = here / "app"

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()

    try:
        print(f"Pulling app state for appid='{APP_ID}' from "
              f"{PG['host']}…\n", flush=True)

        f = pull_definitions(cur, "app_form",     "formid", str(app_dir / "forms"))
        d = pull_definitions(cur, "app_datalist", "id",     str(app_dir / "datalists"))
        u = pull_definitions(cur, "app_userview", "id",     str(app_dir / "userviews"))
        t, r = pull_master_data(cur, str(app_dir / "seeds" / "master-data"))
        a = pull_app_builder(cur, str(app_dir / "api-builder"))
        ot, orw = pull_operational_data(cur, str(app_dir / "seeds" / "data"))
        ev = pull_environment_variables(cur,
                                        str(app_dir / "seeds" / "environment.yaml"))

        print(f"  Forms       : {f:>4d} → app/forms/")
        print(f"  Datalists   : {d:>4d} → app/datalists/")
        print(f"  Userviews   : {u:>4d} → app/userviews/")
        print(f"  Master data : {t:>4d} tables / {r:>4d} rows → "
              f"app/seeds/master-data/")
        print(f"  API Builder : {a:>4d} endpoints → app/api-builder/")
        print(f"  Operational : {ot:>4d} tables / {orw:>4d} rows → "
              f"app/seeds/data/")
        print(f"  Env vars    : {ev:>4d} rows → app/seeds/environment.yaml")
        print()
        print("Done. Next steps:")
        print("  git diff app/                                # review")
        print("  git add app/")
        print("  git commit -m \"sync: pull App Composer edits from Joget\"")
        print("  git push origin main")

    finally:
        cur.close()
        conn.close()

    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
