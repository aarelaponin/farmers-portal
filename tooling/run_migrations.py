#!/usr/bin/env python3
"""
Run idempotent SQL migration scripts in app/seeds/migrations/ in numeric order.

Each script lives at app/seeds/migrations/NNN_*.sql; this runner executes
each script once per call (the scripts are written to be idempotent —
DROP IF EXISTS / CREATE IF NOT EXISTS — so re-running is safe). A simple
schema_migrations table tracks which scripts have already run, so a
no-op pass is fast.

Usage:
    python3 tooling/run_migrations.py                 # apply all pending
    python3 tooling/run_migrations.py --force         # re-apply all (idempotent)
    python3 tooling/run_migrations.py --refresh-only  # only REFRESH MATERIALIZED VIEW

Per CLAUDE.md HARD RULE: this script's writes are explicitly NOT to
Joget metadata or app_fd_* form-data tables. Migrations create / refresh
materialised views and supporting indexes over those tables (read-only
derived objects) and add a single tracking table (schema_migrations)
that's outside Joget's mapping. Permitted; documented in CLAUDE.md
"Audit retention" pattern (same justification: the data layer is
reg-bb-engine-owned, not Joget-managed).
"""

import argparse
import glob
import hashlib
import os
import sys

try:
    import psycopg2
except ImportError:
    sys.exit("psycopg2 required: pip install psycopg2-binary")


PG_HOST     = os.environ.get("PGHOST",     "joget-pgsql-sa.postgres.database.azure.com")
PG_DATABASE = os.environ.get("PGDATABASE", "jogetdb")
PG_USER     = os.environ.get("PGUSER",     "jogetadmin")
PG_PASSWORD = os.environ.get("PGPASSWORD", os.environ.get("PGPASSWORD", ""))
PG_PORT     = int(os.environ.get("PGPORT", "5432"))

_HERE = os.path.dirname(os.path.abspath(__file__))
MIGRATIONS_DIR = os.path.normpath(os.path.join(
    _HERE, os.pardir, "app", "seeds", "migrations"))


def connect():
    return psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, port=PG_PORT, sslmode="require")


def ensure_tracking_table(conn):
    """Create the schema_migrations tracking table if missing."""
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS schema_migrations (
            filename     varchar(255) PRIMARY KEY,
            sha256       char(64)     NOT NULL,
            applied_at   timestamp    NOT NULL DEFAULT now()
        )
    """)
    conn.commit()


def script_sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def applied_set(conn):
    cur = conn.cursor()
    cur.execute("SELECT filename, sha256 FROM schema_migrations")
    return {row[0]: row[1] for row in cur.fetchall()}


def list_migrations():
    files = sorted(glob.glob(os.path.join(MIGRATIONS_DIR, "[0-9]*.sql")))
    return files


def run_script(conn, path, force=False):
    fname = os.path.basename(path)
    sha = script_sha(path)
    cur = conn.cursor()
    cur.execute("SELECT sha256 FROM schema_migrations WHERE filename = %s", (fname,))
    existing = cur.fetchone()
    if existing is not None and not force:
        if existing[0] == sha:
            print(f"  {fname}: already applied (sha matches), skipping")
            return False
        print(f"  {fname}: sha changed since last apply (was {existing[0][:8]}, "
              f"now {sha[:8]}); re-applying")
    with open(path) as f:
        sql = f.read()
    cur.execute(sql)
    cur.execute("""
        INSERT INTO schema_migrations (filename, sha256, applied_at)
        VALUES (%s, %s, now())
        ON CONFLICT (filename) DO UPDATE SET sha256 = EXCLUDED.sha256, applied_at = now()
    """, (fname, sha))
    conn.commit()
    print(f"  {fname}: applied")
    return True


def refresh_only(conn):
    """Refresh all known materialised views without re-running migrations."""
    cur = conn.cursor()
    for view in ("budget_projection", "budget_projection_by_source"):
        try:
            cur.execute(f"REFRESH MATERIALIZED VIEW CONCURRENTLY {view}")
            print(f"  refreshed {view} (concurrently)")
        except psycopg2.Error:
            conn.rollback()
            cur = conn.cursor()
            cur.execute(f"REFRESH MATERIALIZED VIEW {view}")
            print(f"  refreshed {view}")
    conn.commit()


def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--force", action="store_true",
                    help="Re-apply every script even if already applied (scripts are idempotent).")
    ap.add_argument("--refresh-only", action="store_true",
                    help="Only refresh known materialised views; skip migrations.")
    args = ap.parse_args()

    conn = connect()
    try:
        if args.refresh_only:
            refresh_only(conn)
            return 0
        ensure_tracking_table(conn)
        files = list_migrations()
        if not files:
            print(f"No migrations found in {MIGRATIONS_DIR}")
            return 0
        print(f"Found {len(files)} migration script(s):")
        applied = 0
        for path in files:
            if run_script(conn, path, force=args.force):
                applied += 1
        print(f"Applied {applied} migration(s); skipped {len(files) - applied}.")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
