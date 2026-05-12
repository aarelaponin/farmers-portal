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


# ─── Master-data pull ─────────────────────────────────────────────────────

def list_master_data_tables(cur):
    """Return all app_fd_md* and app_fd_mm_* tables in alphabetical order."""
    cur.execute("""
        SELECT table_name FROM information_schema.tables
         WHERE table_schema='public'
           AND (table_name LIKE 'app_fd_md%' OR table_name LIKE 'app_fd_mm_%')
         ORDER BY table_name
    """)
    return [r[0] for r in cur.fetchall()]


def get_table_columns(cur, table: str):
    cur.execute("""
        SELECT column_name FROM information_schema.columns
         WHERE table_name=%s
         ORDER BY ordinal_position
    """, (table,))
    return [r[0] for r in cur.fetchall()]


def pull_master_data(cur, target_dir: str) -> tuple[int, int]:
    """Pull every app_fd_md* + app_fd_mm_* table as per-table YAML files.
    Returns (table_count, total_row_count)."""
    pathlib.Path(target_dir).mkdir(parents=True, exist_ok=True)
    tables = list_master_data_tables(cur)
    total_rows = 0

    for table in tables:
        cols = get_table_columns(cur, table)
        col_list = ", ".join(cols)
        cur.execute(f"SELECT {col_list} FROM {table} ORDER BY id")
        rows = cur.fetchall()
        total_rows += len(rows)

        # Format as YAML by hand (no PyYAML required — keeps the script
        # dependency-free). One row per list entry, columns as dict keys.
        # File name drops the "app_fd_" prefix for readability.
        short = table[7:] if table.startswith("app_fd_") else table
        path = pathlib.Path(target_dir) / f"{short}.yaml"

        if not rows:
            # Still write an empty file so the table presence is recorded
            path.write_text(f"# {table}\n# (no rows at sync time)\n[]\n",
                            encoding="utf-8")
            continue

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
                    # All other types: scrub + quote
                    s = scrub(str(val))
                    # If contains newlines or YAML-special chars, use literal block style
                    if "\n" in s:
                        # Indent each line by 4 spaces, use |- block scalar
                        indented = "\n".join("    " + ln for ln in s.split("\n"))
                        rendered = "|-\n" + indented
                    else:
                        # Escape backslashes and quotes; double-quote
                        esc = s.replace("\\", "\\\\").replace('"', '\\"')
                        rendered = f'"{esc}"'
                prefix = "" if first else "  "
                lines.append(f"{prefix}{col}: {rendered}\n")
                first = False
            lines.append("\n")

        path.write_text("".join(lines), encoding="utf-8")

    return len(tables), total_rows


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

        print(f"  Forms       : {f:>4d} → app/forms/")
        print(f"  Datalists   : {d:>4d} → app/datalists/")
        print(f"  Userviews   : {u:>4d} → app/userviews/")
        print(f"  Master data : {t:>4d} tables / {r:>4d} rows → "
              f"app/seeds/master-data/")
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
