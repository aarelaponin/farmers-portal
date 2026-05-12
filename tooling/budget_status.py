#!/usr/bin/env python3
"""
Print a human-readable Budget Engine status snapshot:

  - Per envelope: Allocated, Available, Reserved, Pre-Committed, Committed,
    Expensed; identity check (allocated == available + reserved + pre +
    committed + expensed); event count and last event timestamp.
  - Per (envelope × source contribution): same six subaccounts; closure
    check (sum of source-stage = envelope-stage at every stage).
  - Open beneficiary sub-ledger balances (1B+ — empty in 1A).

Refreshes the materialised views first so the snapshot is current.

Usage:
    python3 tooling/budget_status.py
    python3 tooling/budget_status.py --envelope ENV_PRG_2025_002_FY2526
    python3 tooling/budget_status.py --no-refresh    # skip the refresh

Per CLAUDE.md HARD RULE: read-only. SELECT against the engine's own
storage and materialised views.
"""

import argparse
import os
import sys

try:
    import psycopg2
except ImportError:
    sys.exit("psycopg2 required: pip install psycopg2-binary")


PG_HOST     = os.environ.get("PGHOST",     "joget-pgsql-sa.postgres.database.azure.com")
PG_DATABASE = os.environ.get("PGDATABASE", "jogetdb")
PG_USER     = os.environ.get("PGUSER",     "jogetadmin")
PG_PASSWORD = os.environ.get("PGPASSWORD", "Joget@DB#2026!")
PG_PORT     = int(os.environ.get("PGPORT", "5432"))


def connect():
    return psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                            password=PG_PASSWORD, port=PG_PORT, sslmode="require")


def fmt_money(n):
    if n is None:
        return "       —"
    return f"{float(n):>12,.2f}"


def refresh(conn):
    cur = conn.cursor()
    for view in ("budget_projection", "budget_projection_by_source"):
        try:
            cur.execute(f"REFRESH MATERIALIZED VIEW CONCURRENTLY {view}")
        except psycopg2.Error:
            conn.rollback()
            cur = conn.cursor()
            cur.execute(f"REFRESH MATERIALIZED VIEW {view}")
    conn.commit()


def envelope_summary(conn, envelope_filter=None):
    cur = conn.cursor()
    sql = """
        SELECT bp.envelope_code, bp.allocated, bp.available, bp.reserved,
               bp.pre_committed, bp.committed, bp.expensed,
               bp.event_count, bp.last_event_at,
               bi.invariant_status, bi.imbalance,
               be.c_programme_code, be.c_status, be.c_fiscal_year
        FROM budget_projection bp
        LEFT JOIN budget_invariants bi ON bi.envelope_code = bp.envelope_code
        LEFT JOIN app_fd_budget_envelope be ON be.c_code = bp.envelope_code
    """
    args = []
    if envelope_filter:
        sql += " WHERE bp.envelope_code = %s"
        args.append(envelope_filter)
    sql += " ORDER BY bp.envelope_code"
    cur.execute(sql, args)
    return cur.fetchall()


def source_summary(conn, envelope_filter=None):
    cur = conn.cursor()
    sql = """
        SELECT envelope_code, source_contribution_code,
               allocated, available, reserved, pre_committed, committed, expensed,
               event_count
        FROM budget_projection_by_source
    """
    args = []
    if envelope_filter:
        sql += " WHERE envelope_code = %s"
        args.append(envelope_filter)
    sql += " ORDER BY envelope_code, source_contribution_code"
    cur.execute(sql, args)
    return cur.fetchall()


def beneficiary_open(conn, envelope_filter=None):
    """Return open beneficiary sub-ledger balances. Empty in 1A."""
    cur = conn.cursor()
    cur.execute("""
        SELECT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_name = 'app_fd_beneficiary_subledger'
        )
    """)
    if not cur.fetchone()[0]:
        return None  # form not yet deployed
    sql = """
        SELECT c_envelope_code, COUNT(*),
               COALESCE(SUM(CAST(NULLIF(c_amount_total, '') AS NUMERIC)), 0)
        FROM app_fd_beneficiary_subledger
        WHERE c_status IN ('open', 'partially_expensed')
    """
    args = []
    if envelope_filter:
        sql += " AND c_envelope_code = %s"
        args.append(envelope_filter)
    sql += " GROUP BY c_envelope_code ORDER BY c_envelope_code"
    cur.execute(sql, args)
    return cur.fetchall()


def main():
    ap = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--envelope", help="Filter to a single envelope code.")
    ap.add_argument("--no-refresh", action="store_true",
                    help="Skip refresh of materialised views.")
    args = ap.parse_args()

    conn = connect()
    try:
        if not args.no_refresh:
            refresh(conn)

        print("=" * 110)
        print(f"{'Envelope':<32} {'Programme':<14} {'Allocated':>13} {'Available':>13} "
              f"{'Reserved':>13} {'PreCommit':>13} {'Committed':>13} {'Expensed':>13}  Inv")
        print("-" * 110)
        rows = envelope_summary(conn, args.envelope)
        if not rows:
            print("(no envelopes — has the seed run?)")
        for r in rows:
            (env, alloc, avail, res, pre, comm, exp,
             ev_count, last_at, inv_status, imbal,
             prog_code, status, fy) = r
            inv_marker = "OK " if inv_status == "ok" else "!! "
            print(f"{env:<32} {(prog_code or '?'):<14} "
                  f"{fmt_money(alloc)} {fmt_money(avail)} {fmt_money(res)} "
                  f"{fmt_money(pre)} {fmt_money(comm)} {fmt_money(exp)}  "
                  f"{inv_marker}")
            if inv_status != "ok":
                print(f"     INVARIANT VIOLATION: imbalance = {imbal}")

        # Source-level breakdown
        print()
        print("Source contributions:")
        print("-" * 110)
        print(f"{'Envelope':<32} {'Source':<40} {'Allocated':>13} {'Available':>13} "
              f"{'Reserved':>13} {'Pre+Comm+Exp':>13}")
        for r in source_summary(conn, args.envelope):
            (env, src, alloc, avail, res, pre, comm, exp, ev_count) = r
            in_funnel = (pre or 0) + (comm or 0) + (exp or 0)
            print(f"{env:<32} {src:<40} {fmt_money(alloc)} {fmt_money(avail)} "
                  f"{fmt_money(res)} {fmt_money(in_funnel)}")

        # Beneficiary sub-ledger
        bnf = beneficiary_open(conn, args.envelope)
        print()
        if bnf is None:
            print("Beneficiary sub-ledger: form not yet deployed (1B activates this).")
        elif not bnf:
            print("Beneficiary sub-ledger: no open accounts (expected in 1A — listener is 1B work).")
        else:
            print("Beneficiary sub-ledger (open accounts):")
            print("-" * 60)
            for env, n, total in bnf:
                print(f"  {env:<32} count={n:>4}   open balance={fmt_money(total)}")
        print()

        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
