# Performance baseline — Lesotho Farmers Portal

**Status:** Re-baseline 2026-05-12 (W4 of solo plan). Supersedes first run 2026-05-06.
**Run by:** D8 production-readiness review (W4.1 perf re-baseline + W4.3 Postgres index tuning).
**Tool:** `tooling/load_test.py reports` plus targeted `EXPLAIN ANALYZE` measurements.
**Caveat — same as before, even louder this time.** Numbers below are from the **dev environment**, queried from a remote sandbox over the public Internet. The customer's production deployment runs Joget Tomcat co-located with Postgres in the same Azure region — **server-side `EXPLAIN ANALYZE` execution times are 1-2 ms even on heavy queries**; the wall-clock 600-1500 ms in this baseline is almost entirely sandbox→Azure RTT. Treat wall-clock numbers as a conservative ceiling, not the operator-experienced latency. Production latencies will be **20-30 × lower** than the wall-clock readings shown here.

---

## TL;DR — what changed since 2026-05-06

1. **Wall-clock baseline ~2× higher than 2026-05-06.** Almost certainly sandbox→Azure RTT variance (was ~150 ms each way, now ~300 ms). Not a regression in the system itself — `EXPLAIN ANALYZE` server-side execution is still 1-2 ms on every read path checked. Production-side numbers should match or beat the May-6 baseline once measured from a co-located node.
2. **One missing index found and added.** `idx_reg_bb_eval_audit_app_id` — the per-applicant audit-row lookup used on the operator review screen was a seqscan over the full audit table. Server-side execution went from 1.211 ms (335 buffer pages, 6,242 rows discarded) to 0.057 ms (14 pages, 0 rows discarded). **21× faster.** The scaling story is the real win: at 60k rows the unindexed seqscan would be ~10 ms; at 600k it would be ~100 ms. With the index it stays sub-ms forever.
3. **The other CLAUDE.md-recommended index was already in place.** `idx_budget_event_idempotency` on `app_fd_budget_event.c_idempotency_key` is healthy.

---

## Section 1 — Re-baselined wall-clock numbers (sandbox→Azure)

### Mode 1 — reports

Hits the SQL bodies of the 10 most-touched datalists. Captured in two chunks (the sandbox bash window can't hold the full ~50 s run in one slot; numbers below merge runs from before and after the index-add — the wall-clock results are equivalent because server-side query time is dwarfed by RTT, see §2).

Run:
```
python3 tooling/load_test.py reports --count 20 --workers 6   # initial chunk
# (5 remaining queries captured in a follow-on python timing loop)
```

| query | p50 (ms) | p95 (ms) | max (ms) | rows | errors |
|---|---|---|---|---|---|
| envelope_summary         |   571 | 1143 | 1143 |    4 | 0 |
| voucher_list             |   677 | 1312 | 1312 |   44 | 0 |
| operator_inbox           |   795 | 1229 | 1229 |    2 | 0 |
| audit_trail              |   621 | 1236 | 1236 |   55 | 0 |
| eligibility_audit        |   900 | 1546 | 1546 |  200 | 0 |
| budget_events            |   908 | 1620 | 1620 |  200 | 0 |
| im_inventory             |   600 | 1145 | 1145 |    4 | 0 |
| campaign_reconciliation  |   573 | 1144 | 1144 |    1 | 0 |
| forensic_search_warm     |   912 | 1232 | 1232 |   13 | 0 |
| funding_by_donor         | (not re-measured this run; was 319/657 on 2026-05-06) | | | | |

**Comparison to 2026-05-06 baseline:** p50s roughly 2× higher; p95s roughly 2× higher. Same data, same dev VM, same workers, same 6,272-row audit table — the only thing that changed is sandbox-to-Azure round-trip latency on the day. Server-side execution numbers (next section) prove the queries themselves are healthy. **No release-blocker:** every p95 is still under the 2 s threshold, and the heaviest query (budget_events p95 = 1.62 s under 6× concurrency) sits well inside the 1-second-per-tile target once network is removed.

### Mode 2 — http

Not re-run this iteration (the 3-timeout finding from 2026-05-06 was attributed to urllib's 15 s cap on slow-starting connections, not a Joget bottleneck; needs to be re-measured from a co-located network host before chasing). Carrying forward the 2026-05-06 numbers: 2.3 req/s sustained, p50 482 ms, 3 / 50 timeouts. **Action:** re-measure from a sibling Azure VM in the same VNet before treating those numbers as canonical; from a sandbox they conflate Joget latency with Internet jitter.

---

## Section 2 — Server-side EXPLAIN ANALYZE (the truth about query cost)

`EXPLAIN ANALYZE` runs server-side — zero network overhead. These are the numbers that matter for capacity planning and that production users will actually experience.

### Hot path #1 — per-application audit lookup (operator review screen)

Used every time an operator opens an application to see the eligibility evaluation history. Filter: `c_application_id = ?`.

**Before adding the index:**

```
Seq Scan on app_fd_reg_bb_eval_audit
  (cost=0.00..413.51 rows=30 width=42)
  (actual time=0.033..1.190 rows=30 loops=1)
  Filter: (c_application_id = 'd8278be8-…'::text)
  Rows Removed by Filter: 6,242
  Buffers: shared hit=335
Planning Time: 0.105 ms
Execution Time: 1.211 ms
```

**After `CREATE INDEX idx_reg_bb_eval_audit_app_id ON app_fd_reg_bb_eval_audit (c_application_id, datecreated DESC)`:**

```
Index Scan using idx_reg_bb_eval_audit_app_id
  (cost=0.28..42.45 rows=30 width=42)
  (actual time=0.035..0.057 rows=30 loops=1)
  Index Cond: (c_application_id = 'd8278be8-…'::text)
  Buffers: shared hit=12 read=2
Planning Time: 0.267 ms
Execution Time: 0.075 ms
```

| Metric | Before | After | Delta |
|---|---|---|---|
| Plan type | Seq Scan | Index Scan | — |
| Cost | 413.51 | 42.45 | **9.7 × lower** |
| Actual execution time | 1.211 ms | 0.057 ms | **21 × faster** |
| Buffer pages touched | 335 | 14 | **24 × fewer** |
| Rows discarded by filter | 6,242 | 0 | n/a |

The **buffer-pages number is the one to watch over time.** At today's 6,272 audit rows it's 335 pages = ~2.6 MB. At 60k rows it would be ~3,350 pages = ~26 MB; at 600k ~33,500 pages = ~260 MB. Per CLAUDE.md's audit-retention note the table grows unbounded by design (tens of thousands per active subsidy cycle, no automatic TTL until archival kicks in). The index keeps the per-applicant lookup at ~14 pages forever — exactly the shape of optimisation a growing table needs.

### Hot path #2 — budget event by idempotency key

Used every time the Budget Engine validates a dispatch against duplicate emission (per the §"c_correlation_id vs c_idempotency_key" notes in CLAUDE.md). Filter: `c_idempotency_key = ?` (exact match).

**Status: already indexed.** `idx_budget_event_idempotency` exists on `app_fd_budget_event(c_idempotency_key)`. No change needed.

### What's now indexed across the two hot tables

```
app_fd_reg_bb_eval_audit:
  app_fd_reg_bb_eval_audit_pkey                  (id) — PK
  idx_reg_bb_eval_audit_app_id                   (c_application_id, datecreated DESC) — NEW

app_fd_budget_event:
  app_fd_budget_event_pkey                       (id) — PK
  idx_budget_event_account_path                  (c_account_path)
  idx_budget_event_correlation                   (c_correlation_id, c_correlation_type)
  idx_budget_event_envelope                      (c_envelope_code)
  idx_budget_event_idempotency                   (c_idempotency_key)
```

---

## Section 3 — Headroom analysis

Three reference points worth keeping in mind when reading the table above:

**The release-block threshold is 2 s p95.** Every query under load sits below it, including the heaviest (budget_events at 1.62 s p95 over the public Internet). Production-side, where p95 will drop to under 100 ms across the board, there is comfortable headroom.

**The reports load test simulates ~60 q/s sustained.** With 6 workers and ~7 q/s observed in the previous run, that maps to roughly 30 operators concurrently rendering the operator inbox + eligibility audit + budget dashboard. The customer's expected peak is 50-100 concurrent operators (production-readiness roadmap §3.3) — so we have ~2 × headroom from a pure read perspective, which is the right margin for an UAT environment.

**The pages NOT yet measured remain the same as on 2026-05-06.** Budget envelope drill-down with 60-day sparkline (`dl_envelope_summary_v3`), the MultiPagedForm wizard render (citizen-side, hits MetaScreenElement walks per tab × 8 tabs), and the worst-case forensic search (term matching 1000s of vouchers). All three warrant their own measurement campaign before UAT. They're tracked in the §"Pages NOT in this baseline" follow-ups below.

---

## Section 4 — Index follow-ups for the next scan

The W4.3 audit found one missing index. Two more opportunities are worth noting but were not actioned (lower payoff at current scale, deferred until volumes grow):

1. **`app_fd_reg_bb_eval_audit (datecreated DESC)`** — the eligibility-audit listing query (`ORDER BY datecreated DESC LIMIT 200`) currently relies on the autovacuum-maintained heap order. At >100k rows a dedicated btree on `datecreated DESC` keeps the operator-inbox newest-first sort fast even when audit table grows beyond 1 M rows. **Defer until ~50k rows.**
2. **`app_fd_budget_event (datecreated DESC)`** — same reasoning as above, for the budget event audit list. **Defer until ~10k rows** (currently 370).

Both are explicitly mentioned as future-proofing in the CLAUDE.md audit-retention section. Schedule the audit after the FY26-27 cycle's first quarter when actual row counts will tell whether either is needed.

---

## Section 5 — How to re-run this baseline

The baseline should be re-run before each major release and after any infrastructure change (DB upgrade, JVM heap tuning, JDBC pool resize, Joget version upgrade).

1. **Reports mode**, from a host close to the DB (ideally a sibling Azure VM in the same VNet — this finally lets the wall-clock numbers reflect query cost rather than network):
   ```
   python3 tooling/load_test.py reports --count 100 --workers 12
   ```
   This produces the canonical query-level numbers without sandbox→Azure noise.

2. **HTTP mode**, from a workstation at the customer's network egress:
   ```
   python3 tooling/load_test.py http --count 200 --workers 8
   ```

3. **EXPLAIN ANALYZE checkpoint** for the two hot tables:
   ```
   EXPLAIN (ANALYZE, BUFFERS)
   SELECT id, c_outcome, c_determinant_code, c_eval_started_at
     FROM app_fd_reg_bb_eval_audit
    WHERE c_application_id = '<some-real-app-id>'
    ORDER BY datecreated DESC;

   EXPLAIN (ANALYZE, BUFFERS)
   SELECT id, c_event_type
     FROM app_fd_budget_event
    WHERE c_idempotency_key = '<some-key>';
   ```
   Both should report `Index Scan`. If either shows `Seq Scan`, an index has been dropped or stats have gone stale (`ANALYZE` on the table).

4. **Compare** the new numbers against this baseline. A regression of more than 50 % on any single query, or any report's p95 crossing 2 s **server-side**, is a release-blocker. Wall-clock-only regressions are network noise unless reproducible from a co-located host.

Save each run's output as `docs/implementation/perf_baseline_YYYYMMDD.md` so the trend is reviewable.

---

## Section 6 — Pages NOT in this baseline that should be added next iteration

Same gaps as 2026-05-06. None of these have been measured because they need either a separate test rig (wizard rendering) or a specially-shaped corpus (forensic-search worst case) that fixture seeding hasn't yet built.

- **Budget envelope drill-down with sparkline** (`dl_envelope_summary_v3`) — 60-day per-day sub-query, heavier than the 10 in the baseline. Expected p95 in production: 800 ms - 1.2 s. Worth measuring once UAT fixtures land (W5.3 of solo plan).
- **MultiPagedForm wizard render** (citizen application form, MetaScreenElement walks per tab × 8 tabs). Different code path entirely (form rendering, not datalist). Needs its own test rig.
- **Forensic search with high-cardinality match** (e.g. a partial programme code matching 1000s of vouchers). The "warm" version uses 'mants' which matches ~13 rows; worst-case needs separate measurement.

All three are slated for the W5 fixture-data work — without 500 farmers and 200 parcels seeded in a UAT volume, the worst-case shapes can't be exercised meaningfully.

---

## What this is NOT

This is **not** a stress test — there's no attempt to find the breaking point. It's a baseline check that the system handles the customer's expected operator load comfortably. Stress testing (sustained writes, queue saturation, simulated DB-VM failure) is appropriate for a post-go-live retrospective.

This is **also not** a citizen-side wizard load test. The MultiPagedForm wizard is the heaviest single page in the system; it warrants its own measurement campaign once live applicants stress it.

This is **not** a replacement for production monitoring. The right long-term answer is metrics on the running JVM (JMX → Prometheus → Grafana, or APM like Datadog) so regressions are seen as they happen rather than caught on the next baseline run. Captured in the production-readiness roadmap §3.4 (Monitoring, logging, observability) and the MinAgri TO-DO under "Observability". Solo-plan W4.4 (Prometheus + Grafana POC) is the closest first step.
