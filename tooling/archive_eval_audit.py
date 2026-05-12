#!/usr/bin/env python3
"""
Archive-then-prune the reg_bb_eval_audit table.

Implements the retention policy described in CLAUDE.md "Audit retention":
copy rows older than the retention horizon into a cold archive (gzipped
CSV by default; optionally a sibling `_archive` table), then DELETE from
the live table. Conservative defaults: 90-day retention, dry-run unless
--apply is passed.

Why this is allowed under the HARD RULE: reg_bb_eval_audit is reg-bb-
engine-owned audit data, not Joget metadata. It has no Hibernate mapping
beyond its own form, no per-form caches that go stale, and its schema is
stable. Direct SQL DELETE here is safe; the rule is about Joget-managed
forms (app_form / app_fd_*), which this isn't.

Usage:
    python3 tooling/archive_eval_audit.py                       # dry-run, 90-day retention
    python3 tooling/archive_eval_audit.py --retention-days 180  # custom horizon
    python3 tooling/archive_eval_audit.py --apply               # actually delete
    python3 tooling/archive_eval_audit.py --apply --to-table    # archive into reg_bb_eval_audit_archive instead of CSV

Recommended cadence: quarterly. Operator triggers manually after each
subsidy cycle closes or via a Joget scheduled-task. Don't automate
without verifying archive integrity at least once.
"""

import argparse
import csv
import datetime
import gzip
import os
import sys

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    sys.exit("psycopg2 required: pip install psycopg2-binary")


PG_HOST     = os.environ.get("PGHOST",     "joget-pgsql-sa.postgres.database.azure.com")
PG_DATABASE = os.environ.get("PGDATABASE", "jogetdb")
PG_USER     = os.environ.get("PGUSER",     "jogetadmin")
PG_PASSWORD = os.environ.get("PGPASSWORD", "Joget@DB#2026!")
PG_PORT     = int(os.environ.get("PGPORT", "5432"))

LIVE_TABLE    = "app_fd_reg_bb_eval_audit"
ARCHIVE_TABLE = "app_fd_reg_bb_eval_audit_archive"

ARCHIVE_DIR = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    os.pardir, "app", "seeds", "audit-archives"))


def connect():
    return psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, port=PG_PORT, sslmode="require")


def archive_to_csv(conn, cutoff):
    """Stream rows older than cutoff into a gzipped CSV. Returns the path."""
    os.makedirs(ARCHIVE_DIR, exist_ok=True)
    fname = f"reg_bb_eval_audit_pre_{cutoff.isoformat()}.csv.gz"
    path = os.path.join(ARCHIVE_DIR, fname)
    cur = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
    cur.execute(
        f"SELECT * FROM {LIVE_TABLE} WHERE datecreated < %s ORDER BY datecreated",
        (cutoff,))
    rows = cur.fetchall()
    if not rows:
        return None, 0
    with gzip.open(path, "wt", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        for r in rows:
            w.writerow({k: ("" if v is None else v) for k, v in dict(r).items()})
    return path, len(rows)


def archive_to_table(conn, cutoff):
    """Copy rows into ARCHIVE_TABLE (creates it on first use). Returns count."""
    cur = conn.cursor()
    cur.execute(
        f"CREATE TABLE IF NOT EXISTS {ARCHIVE_TABLE} (LIKE {LIVE_TABLE} INCLUDING ALL)")
    cur.execute(
        f"INSERT INTO {ARCHIVE_TABLE} SELECT * FROM {LIVE_TABLE} "
        f"WHERE datecreated < %s ON CONFLICT (id) DO NOTHING",
        (cutoff,))
    return cur.rowcount


def prune_live(conn, cutoff):
    cur = conn.cursor()
    cur.execute(
        f"DELETE FROM {LIVE_TABLE} WHERE datecreated < %s", (cutoff,))
    return cur.rowcount


def report_state(conn):
    cur = conn.cursor()
    cur.execute(f"SELECT COUNT(*), MIN(datecreated), MAX(datecreated) FROM {LIVE_TABLE}")
    n, oldest, newest = cur.fetchone()
    return n, oldest, newest


def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--retention-days", type=int, default=90,
                    help="Rows older than this are archived + pruned (default: 90).")
    ap.add_argument("--apply", action="store_true",
                    help="Actually run the DELETE. Without it, this is a dry-run.")
    ap.add_argument("--to-table", action="store_true",
                    help=f"Archive into {ARCHIVE_TABLE} instead of gzipped CSV.")
    args = ap.parse_args()

    cutoff = datetime.datetime.utcnow() - datetime.timedelta(days=args.retention_days)
    print(f"Cutoff: rows older than {cutoff.isoformat()} (-{args.retention_days} days)")

    conn = connect()
    try:
        n0, oldest, newest = report_state(conn)
        print(f"Live table: {n0} rows, range {oldest} → {newest}")

        cur = conn.cursor()
        cur.execute(
            f"SELECT COUNT(*) FROM {LIVE_TABLE} WHERE datecreated < %s", (cutoff,))
        candidates = cur.fetchone()[0]
        print(f"Candidates for archival: {candidates} rows")

        if candidates == 0:
            print("Nothing to archive. Exiting.")
            return 0

        if not args.apply:
            print("\nDRY-RUN: pass --apply to execute. No changes made.")
            return 0

        if args.to_table:
            archived = archive_to_table(conn, cutoff)
            print(f"Archived {archived} rows into {ARCHIVE_TABLE}")
        else:
            path, archived = archive_to_csv(conn, cutoff)
            print(f"Archived {archived} rows → {path} ({os.path.getsize(path) // 1024} KB)")

        pruned = prune_live(conn, cutoff)
        if pruned != archived:
            conn.rollback()
            sys.exit(f"FATAL: archived {archived} rows but DELETE would remove {pruned}; "
                     "rolling back. Investigate before re-running.")
        conn.commit()
        n1, oldest1, newest1 = report_state(conn)
        print(f"Pruned {pruned} rows. Live table now: {n1} rows, "
              f"range {oldest1} → {newest1}")
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
