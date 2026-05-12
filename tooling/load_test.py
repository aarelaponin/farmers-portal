#!/usr/bin/env python3
"""
Load test for the Farmers Portal (D8 — production-readiness).

Two modes, both runnable safely against the dev or prod-mirror DB. Both
modes are *measurement only* with respect to citizen-owned data: the
report mode is read-only; the budget mode posts synthetic events that
can be deleted in one query at the end.

Modes:

  reports   Hit the 10 most-touched datalist SQL queries N times each
            with C concurrent workers, measure p50 / p95 / max wall-clock
            time per query. Read-only. Use this to verify the operator
            pages still render under load (e.g. 30 operators logged in
            during a voucher distribution week).

  http      Hit a safe read-only Joget endpoint (`/budget/timeseries`)
            via HTTP and measure the full Joget request lifecycle (API
            Builder auth → handler → JDBC pool → query → JSON
            serialisation). Mirrors what an operator's browser does
            loading a dashboard tile. No writes.

Usage:
    python3 tooling/load_test.py reports               # 100 hits per query × 8 workers
    python3 tooling/load_test.py reports --count 500 --workers 16
    python3 tooling/load_test.py http --count 100      # 100 GETs × 4 workers (default)
    python3 tooling/load_test.py http --count 200 --workers 8

Output: a single block per mode with min/p50/p95/max in ms, throughput
in ops/sec, error count. Designed to be eyeballed, not parsed.

Per CLAUDE.md HARD RULE: read-only against app_fd_* and the budget
projection materialised view; HTTP mode hits a published REST endpoint.
"""

import argparse
import json
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed

import psycopg2

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))

PG_HOST     = os.environ.get("PGHOST",     "joget-pgsql-sa.postgres.database.azure.com")
PG_DATABASE = os.environ.get("PGDATABASE", "jogetdb")
PG_USER     = os.environ.get("PGUSER",     "jogetadmin")
PG_PASSWORD = os.environ.get("PGPASSWORD", os.environ.get("PGPASSWORD", ""))


# ---------------------------------------------------------------------------
# Mode 1: Reports (read-only)
# ---------------------------------------------------------------------------

# The top-10 datalists by operator usage frequency, embedded as their core
# SELECT body so this script doesn't need to read live datalist JSON. Each
# query stands alone (no parameter substitution from `requestParam` —
# stripped to the unfiltered baseline).
REPORT_QUERIES = {
    "envelope_summary": "SELECT bp.envelope_code, bp.allocated, bp.committed, bp.expensed FROM budget_projection bp LEFT JOIN app_fd_budget_envelope env ON env.c_code = bp.envelope_code ORDER BY env.c_programme_code NULLS LAST LIMIT 50",
    "voucher_list": "SELECT v.id, v.c_code, v.c_status, v.c_farmer_nid, v.c_farmer_name, v.c_programme_code, v.c_input_code, v.c_point_code, v.c_quantity, v.c_expiry_date FROM app_fd_im_voucher v ORDER BY v.c_issued_date DESC NULLS LAST LIMIT 100",
    "operator_inbox": "SELECT a.id, a.c_full_name, a.c_national_id, a.c_applied_programme, a.c_status, a.c_eligibility_outcome, a.datecreated FROM app_fd_subsidy_app_2025 a WHERE a.c_status IN ('pending_operator_review', 'pending_data_clarification') ORDER BY a.datecreated DESC LIMIT 100",
    "audit_trail": "SELECT 'voucher_issued' AS event, c_code AS code, c_farmer_nid, c_status, c_issued_date AS ts FROM app_fd_im_voucher UNION ALL SELECT 'redemption' AS event, c_code, '' AS nid, c_status, c_redemption_date AS ts FROM app_fd_im_voucher_redemption UNION ALL SELECT 'distribution' AS event, c_code, '' AS nid, 'signed' AS status, c_distribution_date AS ts FROM app_fd_im_distribution ORDER BY ts DESC NULLS LAST LIMIT 200",
    "eligibility_audit": "SELECT id, c_application_id, c_national_id, c_applicant_name, c_outcome, c_determinant_code, c_eval_started_at FROM app_fd_reg_bb_eval_audit ORDER BY datecreated DESC LIMIT 200",
    "budget_events": "SELECT id, datecreated, c_event_type, c_envelope_code, c_account_path, c_direction, c_amount FROM app_fd_budget_event ORDER BY datecreated DESC LIMIT 200",
    "im_inventory": "SELECT id, c_input_code, c_point_code, c_quantity_on_hand FROM app_fd_im_inventory ORDER BY c_input_code, c_point_code LIMIT 200",
    "funding_by_donor": "WITH per_donor AS (SELECT d.c_code AS donor_code, d.c_name AS donor_name, COUNT(DISTINCT pf.c_programme_code) AS num_programmes, COALESCE(SUM(COALESCE(CAST(env.c_allocated_amount AS NUMERIC),0) * CAST(NULLIF(pf.c_share_percent,'') AS NUMERIC) / 100.0), 0) AS allocated_num FROM app_fd_md_donor d LEFT JOIN app_fd_programme_funding pf ON pf.c_donor_code = d.c_code LEFT JOIN app_fd_budget_envelope env ON env.c_programme_code = pf.c_programme_code GROUP BY d.c_code, d.c_name) SELECT donor_code, donor_name, num_programmes, ROUND(allocated_num,2) FROM per_donor ORDER BY allocated_num DESC NULLS LAST",
    "campaign_reconciliation": "SELECT v.c_programme_code, count(*) AS issued, count(*) FILTER (WHERE v.c_status='redeemed') AS redeemed, count(*) FILTER (WHERE v.c_status='issued') AS active FROM app_fd_im_voucher v GROUP BY v.c_programme_code ORDER BY v.c_programme_code",
    "forensic_search_warm": "WITH q AS (SELECT 'mants' AS term) SELECT * FROM (SELECT id, c_national_id, c_full_name, datecreated FROM app_fd_subsidy_app_2025 a, q WHERE LOWER(c_full_name) LIKE '%' || q.term || '%' UNION ALL SELECT id, c_farmer_nid, c_farmer_name, datecreated FROM app_fd_im_voucher v, q WHERE LOWER(c_farmer_name) LIKE '%' || q.term || '%') s ORDER BY datecreated DESC LIMIT 50",
}


_TLS = threading.local()


def _get_conn():
    """One connection per worker thread, reused across all queries on
    that thread. Mirrors how Joget's JDBC pool serves concurrent
    requests — without this, the SSL handshake to Azure Postgres
    dominates the timing and the numbers are noise."""
    conn = getattr(_TLS, "conn", None)
    if conn is None or conn.closed:
        conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE, user=PG_USER,
                                password=PG_PASSWORD, connect_timeout=10)
        _TLS.conn = conn
    return conn


def time_query(sql):
    try:
        conn = _get_conn()
        cur = conn.cursor()
        t0 = time.perf_counter()
        cur.execute(sql)
        rows = cur.fetchall()
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        return elapsed_ms, len(rows), None
    except Exception as e:
        # Reset connection on error so the next call gets a fresh one
        try:
            if getattr(_TLS, "conn", None):
                _TLS.conn.close()
        except Exception:
            pass
        _TLS.conn = None
        return None, 0, str(e)[:200]


def run_reports_mode(count_per_query, workers):
    print(f"=== reports mode: {count_per_query} hits × {len(REPORT_QUERIES)} queries, {workers} workers ===\n")
    print(f"{'query':30s} {'p50':>9s} {'p95':>9s} {'max':>9s} {'rows':>8s} {'errors':>8s}")
    print("-" * 80)
    overall_start = time.perf_counter()
    total_calls = 0
    total_errors = 0
    for name, sql in REPORT_QUERIES.items():
        results = []
        errors = []
        with ThreadPoolExecutor(max_workers=workers) as ex:
            futures = [ex.submit(time_query, sql) for _ in range(count_per_query)]
            for f in as_completed(futures):
                ms, rows, err = f.result()
                if err:
                    errors.append(err)
                else:
                    results.append((ms, rows))
        total_calls += count_per_query
        total_errors += len(errors)
        if not results:
            print(f"{name:30s} {'ALL FAILED':>27s}  {len(errors):>8d}")
            if errors:
                print(f"  first error: {errors[0]}")
            continue
        latencies = sorted(r[0] for r in results)
        p50 = latencies[len(latencies) // 2]
        p95 = latencies[int(len(latencies) * 0.95)] if len(latencies) > 1 else latencies[0]
        mx  = latencies[-1]
        rows = results[0][1]
        print(f"{name:30s} {p50:>7.1f}ms {p95:>7.1f}ms {mx:>7.1f}ms {rows:>8d} {len(errors):>8d}")

    overall_elapsed = time.perf_counter() - overall_start
    print("-" * 80)
    print(f"total: {total_calls} calls in {overall_elapsed:.1f}s "
          f"= {total_calls / overall_elapsed:.1f} q/s, {total_errors} errors")


# ---------------------------------------------------------------------------
# Mode 2: HTTP round-trip (read-only)
# ---------------------------------------------------------------------------
# Hits a safe read-only endpoint (/budget/timeseries) and measures the full
# Joget request lifecycle: API Builder auth check, BudgetApi handler,
# JDBC pool acquisition, query execution, JSON serialisation, response
# back to client. Mirrors what an operator's browser experiences when
# loading a budget dashboard tile. No writes, no cleanup needed.

def hit_timeseries_endpoint(envelope_code):
    qs = f"envelopeCode={envelope_code}&days=30"
    url = JOGET_BASE_URL + "/api/budget/timeseries?" + qs
    headers = {
        "api_id":  "API-BUDGET",
        "api_key": JOGET_API_KEY,
    }
    req = urllib.request.Request(url, method="GET", headers=headers)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read()
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            return elapsed_ms, resp.status, len(body), None
    except urllib.error.HTTPError as e:
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        body = e.read().decode("utf-8", errors="replace")[:120]
        return elapsed_ms, e.code, 0, body
    except Exception as e:
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        return elapsed_ms, 0, 0, str(e)[:120]


def run_http_mode(count, workers):
    envelope_code = "ENV_PRG_2025_001_FY2526"
    print(f"=== http mode: {count} GETs × {workers} workers ===")
    print(f"endpoint: GET /api/budget/timeseries?envelopeCode={envelope_code}&days=30")
    print(f"(measures the full Joget request lifecycle, read-only)\n")

    results = []
    errors = []
    overall_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futures = [ex.submit(hit_timeseries_endpoint, envelope_code) for _ in range(count)]
        for f in as_completed(futures):
            ms, code, body_len, err = f.result()
            results.append(ms)
            if err or (code and code >= 400):
                errors.append((code, err))

    overall_elapsed = time.perf_counter() - overall_start
    latencies = sorted(results)
    p50 = latencies[len(latencies) // 2]
    p95 = latencies[int(len(latencies) * 0.95)] if len(latencies) > 1 else latencies[0]
    mx  = latencies[-1]
    print(f"results: {count} GETs in {overall_elapsed:.2f}s")
    print(f"throughput: {count / overall_elapsed:.1f} req/s")
    print(f"latency: p50 {p50:.1f}ms / p95 {p95:.1f}ms / max {mx:.1f}ms")
    print(f"errors: {len(errors)}")
    if errors:
        print(f"  first 3: {errors[:3]}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", choices=["reports", "http"])
    ap.add_argument("--count", type=int, default=None,
                    help="reports: hits per query (default 100). http: total GETs (default 100).")
    ap.add_argument("--workers", type=int, default=None,
                    help="parallel worker threads (reports default 8, http default 4).")
    args = ap.parse_args()

    if args.mode == "reports":
        run_reports_mode(args.count or 100, args.workers or 8)
    elif args.mode == "http":
        run_http_mode(args.count or 100, args.workers or 4)


if __name__ == "__main__":
    main()
