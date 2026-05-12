# ADR-030 — Asynchronous submit processing (queue-and-worker)

| | |
|---|---|
| Status | **Accepted** (2026-05-05) |
| Date | 2026-05-05 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering, MAFSN ops |
| Supersedes | none |
| Related | ADR-005 (save hook), ADR-007 (save-evaluate atomicity), ADR-022 (Budget Engine as separate module), ADR-027 (initial-status assignment), `_design/migration-plan.md` (Stage-2 XPDL drop), L4-3 parity test, `_tooling/run_l4_scenarios.py` (load-test artefact that surfaced the issue) |

---

## 1. Context

### 1.1 What we have today (Phase 1, ADR-005 + ADR-007)

`RegBbApplicationStoreBinder.store()` runs the full chain *synchronously inside the form-save HTTP request*:

1. Standard form save (`super.store`) — persists `app_fd_subsidy_app_2025` row (~50–100 ms).
2. Eligibility evaluation across ~14 Determinants — fast on its own, ~1–3 ms each, but multiplied across rule chains.
3. Persist `c_eligibility_outcome` + transition `c_status` (~100–200 ms).
4. Audit row writes (~50–100 ms).
5. `BudgetEngine.fireForLifecycle` → 2 dispatches → 2 `REFRESH MATERIALIZED VIEW` calls (~2–5 s combined, even with `CONCURRENTLY`).

**Total request thread holding time: 5–10 seconds per submission.** With Joget's default Tomcat thread pool (200 threads, but per-app limits much lower in practice), bursty load saturates the pool quickly. Other requests against the same Joget instance (operator UI page loads, citizen polling for status, datalist refreshes) queue behind.

### 1.2 What surfaced the issue

`_tooling/run_l4_scenarios.py` submits 19 applications back-to-back and times out at scenario 6–8 with `URLError: timed out` on a *fetch* (not a submit). Diagnosis:

* Eligibility evaluator latency p95 = 3 ms, max 32 ms — **not the bottleneck.**
* Single submit + chain (idle Joget) = 0.7 s — **fine in isolation.**
* Materialised view refreshes already moved to `CONCURRENTLY` (build-093) — modest improvement.
* During the test burst, Joget's request thread pool saturates because each submit holds its slot for the full 5–10 s chain. Subsequent fetches queue behind. The 60 s HTTP timeout in the test driver fires.

The L4 test is synthetic, but the architectural defect it exposes is real: **a single MAFSN field office of 5 operators all clicking "Save" within a few seconds will hit the same wall.** And the threshold for hitting it is far too low for a national subsidy programme — peak loads at programme launch (e.g. Drought Relief 2025) easily produce hundreds of concurrent submissions.

### 1.3 Why the original synchronous design was OK for Phase 1

* Demos and L4 parity needed *correctness*, not throughput.
* Synchronous makes the audit trail trivially complete-by-end-of-request.
* No queue infrastructure, no worker process, no "what's its status now" polling complexity — all those costs deferred.
* Joget's storeBinder API is naturally synchronous; honouring it kept the integration shape simple.

These trade-offs were correct for Phase 1 (ADR-002 r2: YAGNI/KISS over premature concurrency complexity at single-team scale). They're wrong for production-scale operations, which is what we're building toward now.

### 1.4 Why "just sleep between scenarios" is unacceptable

The naive workaround for the L4 test is to add `time.sleep(2)` between submissions. We rejected this on principle: "this is a system for governments, not a load-test fixture." A government deployment must:

* Survive a Joget restart without losing in-flight applications.
* Process applications even when the citizen-facing endpoint is slow or temporarily unavailable.
* Provide observable lag ("there are 47 applications waiting to be evaluated, oldest 12 minutes ago") so ops can scale capacity or alert on backlog.
* Retry transient failures (DB hiccup, plugin reload, JVM GC pause) without operator intervention.
* Fail loudly and traceably when retries exhaust, with a clear path for manual recovery.

None of those properties exist with the current synchronous-in-binder design.

---

## 2. Decision

**Decouple submit from processing via a durable queue + worker pattern, implemented entirely within Joget's native infrastructure** (no external queue broker — same simplicity discipline as the rest of the system).

### 2.1 The three layers

**Layer 1 — Submit endpoint (request-thread, target &lt; 200 ms):**
* Validate form data (today's validators).
* Persist `app_fd_subsidy_app_2025` row with `c_status = 'submitted'` and a placeholder `c_eligibility_outcome` of `{}`.
* INSERT a row into `app_fd_processing_queue` with `(application_id, programme_code, applicant_nid, submitted_at, attempt_count=0, next_attempt_at=now())`.
* Return application_id to the caller.
* Citizen sees `status = submitted` immediately.

**Layer 2 — Eligibility worker (off the request thread):**
* Reads `app_fd_processing_queue` rows where `next_attempt_at <= now()` and (status is null or `pending`), one at a time, FOR UPDATE SKIP LOCKED so multiple workers don't double-process.
* Loads the application data, runs the eligibility chain (today's `RegBbApplicationStoreBinder.runEligibilityChain` extracted into a callable service).
* On success: writes `c_eligibility_outcome`, transitions `c_status` to `auto_approved` / `auto_rejected` / `pending_operator_review`, writes audit row, sets queue row's `processed_at = now()` and removes from active queue.
* On exception: increments `attempt_count`, sets `next_attempt_at = now() + exponential_backoff(attempt_count)`, leaves queue row visible. After `max_attempts` (default 5): moves to dead-letter table `app_fd_processing_dead_letter`, alerts ops via `app_fd_budget_threshold_alert` (re-using the existing alert infrastructure with a new severity = `PROCESSING_FAILED`).

**Layer 3 — Budget worker (separate from eligibility worker, after eligibility completes):**
* Reads applications where `c_status` is in `auto_approved` / `auto_rejected` / `pending_operator_review` and `c_budget_dispatched_at` is NULL.
* Calls `BudgetEngine.fireForLifecycle` (today's logic, unchanged).
* Sets `c_budget_dispatched_at = now()` on success (idempotency by column, in addition to the engine's own idempotency key).
* On exception: same retry/backoff/dead-letter as Layer 2.

**Mat view refresh moves to a scheduled job:**
* New `BudgetProjectionRefreshJob` (Joget process tool plugin, scheduled every 30 s).
* Removes `refreshProjection()` calls from `BudgetEngine.dispatch` and `BudgetEngine.dispatchDirect`.
* Trade-off: dashboard sees up to 30 s of lag between event posting and projection update. Acceptable for budget monitoring; alerts and operator decisions still see the raw `app_fd_budget_event` data.

### 2.2 Worker implementation

Two new Joget process tool plugins, both following the proven `BudgetThresholdMonitor` pattern:

* `EligibilityProcessingWorker` — wired into a Joget workflow process with a tool step + 30-second schedule (configurable).
* `BudgetDispatchWorker` — same pattern.

Wiring uses the same Joget scheduler that already runs `BudgetThresholdMonitor` (once that's scheduled). No new infrastructure — only new plugins of the same shape.

### 2.3 Observability

Two new datalists, exposed in the existing **Budget** category:

* **Processing — pending queue** (`dl_processing_queue`) — pending + retrying + dead-letter applications, oldest first, with attempt count and next-attempt time.
* **Processing — recent activity** (`dl_processing_recent`) — last 100 processed applications with chain timing (submit → eligibility done → budget done).

Operators see queue depth, processing lag, and failure modes at a glance. Ops can drain backlog by scheduling the worker more aggressively or by running it on demand via a REST endpoint (same pattern as `/budget/run-threshold-monitor`).

### 2.4 Citizen-facing impact

The citizen submit response goes from "here is your eligibility outcome" (5–10 s) to "your application is submitted, application ID = …" (&lt; 200 ms). The citizen UI polls `/jw/api/form/subsidyApplication2025/{id}` every 2 s for up to 30 s, then shows the outcome when `c_status` transitions. If the worker is keeping up, total citizen-facing time is a few seconds — same as today, but the request thread is freed immediately after submit.

The polling is already implemented in `run_l4_scenarios.py::fetch_outcome` — that pattern is the canonical client.

---

## 3. Trade-offs

### 3.1 What we gain

* **Submit response time bounded.** Always &lt; 200 ms regardless of evaluation cost.
* **Throughput limited only by worker pool, not request thread pool.** Workers can scale by adding more scheduled instances.
* **Durability.** Queue rows survive Joget restart; in-flight applications never lost.
* **Observable backlog.** Ops can see queue depth in real time.
* **Retry semantics.** Transient failures self-heal without operator intervention.
* **Decoupled failure domains.** Budget engine outage doesn't break submit; eligibility worker failure doesn't break budget engine.
* **Per-stage SLOs measurable.** "Eligibility lag p95 &lt; 30 s" becomes a monitorable target.

### 3.2 What we lose / pay for

* **Citizen UX changes.** Citizen no longer sees outcome on submit — sees "submitted, processing" then status updates. Acceptable; matches every other government-grade submit pattern (tax filing, passport application, etc).
* **Code complexity grows.** Two new workers, one new queue table, one new dead-letter table, two new datalists. ~600 lines of Java + JSON.
* **Operational surface grows.** Workers need to be scheduled correctly, monitored for health, alerted on dead-letter accumulation.
* **Testing surface grows.** L4 test driver needs to wait for worker to process before fetching outcome — but it already does this via `fetch_outcome`'s polling loop.
* **Mat view lag introduces window for read-after-write inconsistency.** Operator decision form's budget hint may show 30-second-stale data. Mitigation: refresh on demand from operator UI when the operator opens the decision form (cheap because it's per-envelope).

### 3.3 What we explicitly do NOT do

* **No external queue broker (RabbitMQ, Kafka, Redis).** Joget's existing scheduler + form-data tables are sufficient. Adding infrastructure violates "Convention over Invention" (CLAUDE.md) and adds operational burden disproportionate to scale.
* **No XPDL/Stage-2 workflow process per application.** That was deferred per migration-plan.md; we replicate just the queue+worker shape, not full workflow orchestration. If we later need conditional branching, parallel reviews, escalation timers, etc., we'll revisit XPDL.
* **No retry on Joget-internal errors.** Plugin crashes / classloader errors are infrastructure problems; they land in the dead letter and require ops intervention.
* **No transactional cross-cutting (saga, 2PC).** Each worker stage is idempotent on its own; partial failures are recovered by retry, not by rollback.

---

## 4. Counter-principles considered

Per CLAUDE.md "Architectural decisions name two principles, not one":

* **YAGNI / KISS** says "don't build queues until you need them; keep the synchronous binder." Lost because we *do* need them — the synchronous design has demonstrably failed under load that's well below production peak. Phase 1 was the right time to defer this; Phase 3 (IM module landing) is the wrong time to keep deferring.
* **Convention over Invention** says "use Joget's workflow engine." Acknowledged — we *are* using Joget's scheduler and form-data DAO, just not the full XPDL workflow engine. The minimal queue+worker pattern is a strict subset of XPDL's capabilities, so this isn't a competing design — it's a slice of the same approach. If queue+worker proves insufficient (e.g., we need parallel reviewers per ADR-022), full XPDL becomes the next step.
* **Single Responsibility** says "split eligibility worker and budget worker." Honoured — they're separate plugins, separate failure domains.

---

## 5. Migration plan

Six steps, each independently shippable.

### Step 1 — Queue table + dead-letter table

* New form `processing_queue` (fields: application_id, programme_code, applicant_nid, submitted_at, attempt_count, next_attempt_at, last_error, processed_at).
* New form `processing_dead_letter` (same shape + `dead_lettered_at`, `dead_letter_reason`).
* Seed via existing pipeline.

### Step 2 — Refactor RegBbApplicationStoreBinder

* Extract eligibility chain logic into `EligibilityChainService` (new class, callable from binder OR worker).
* Binder's `store()` becomes: super.store + write queue row + return.
* Eligibility chain logic stays in the codebase but is no longer called from `store()`.

### Step 3 — EligibilityProcessingWorker

* New process tool plugin extending `DefaultApplicationPlugin`.
* `execute(properties)` reads queue (FOR UPDATE SKIP LOCKED), processes one row, writes outcome, removes queue row. Loop until queue empty or quota hit (e.g., 50 rows per invocation).
* Schedule via Joget's tool scheduler — every 30 seconds initially.

### Step 4 — BudgetDispatchWorker

* Same pattern as Step 3.
* Reads applications where eligibility completed but budget not dispatched (`c_status` populated, `c_budget_dispatched_at` NULL).
* Calls `BudgetEngine.fireForLifecycle` and stamps `c_budget_dispatched_at`.

### Step 5 — Mat view refresh job

* Move `refreshProjection()` calls out of `BudgetEngine`.
* New `BudgetProjectionRefreshJob` plugin; schedule every 30 s.
* Operator decision form's budget hint widget invokes refresh on demand for its specific envelope (cheap, single-envelope refresh ~50ms).

### Step 6 — Observability + ops

* Two new datalists (pending queue, recent activity).
* New Budget category menu entries.
* `_tooling/test_budget_suite.py` extended with queue depth assertions.
* Documentation update: `_design/SESSION-RESUME.md` + relevant SAD components.

### Estimated effort

2–3 focused days. Step 1 + Step 2 unlock the new flow (1 day). Steps 3–4 add the workers (1 day). Steps 5–6 polish and observability (half a day each).

### Backward compatibility

* The synchronous code path is removed in Step 2, not left as a fallback. Once shipped, every submit goes through the queue. Reverting requires the same effort as advancing — there is no "soft launch" window.
* Existing applications (already evaluated) are unaffected — no migration of in-flight data.
* L4-3 parity test must still pass after all steps. The test driver's polling loop already accommodates async outcomes; no test change required.

---

## 6. Open questions

* **Single worker instance vs multiple?** Default to one. Multi-worker requires `FOR UPDATE SKIP LOCKED` on the queue and Postgres advisory locks for hot envelopes; manageable but adds complexity. Defer until we observe queue depth growing past 100 rows in production.
* **Schedule frequency.** 30 s is a round number; the real choice depends on observed lag. Make it configurable via plugin properties.
* **Queue table retention.** Processed rows can be archived after 30 days (same pattern as `archive_eval_audit.py`). Worker doesn't read processed rows; they're kept for audit.
* **Citizen UI polling pattern.** The poll-after-submit shape is well-known but increases citizen-facing complexity. The wizard currently shows the outcome on the same page after submit; we'll need to add a "processing" intermediate state. UX work, not architecture work.

---

## 7. Acceptance criteria

When this ADR's plan is complete:

1. `python3 _tooling/run_l4_scenarios.py` completes 20/20 in &lt; 90 seconds with the script's default 60 s per-request timeout.
2. The pending queue datalist shows queue depth and processing lag.
3. A simulated worker failure (kill the plugin mid-execution) recovers on next schedule without manual intervention; the application's status updates correctly.
4. A submit returns in &lt; 500 ms (HTTP wall time) for 99% of requests, measured over 100 sequential submissions.
5. Budget engine event ordering is preserved per applicant — no race where a later state transition's budget event posts before the earlier one's.
