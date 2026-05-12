# Session resume — Lesotho Farmers Portal (LST-FRP)

**Last working session:** 2026-05-06 (Wed). End-of-day state.

This document is the single source of truth for *where the project stands*
and *what to do next* when you sit back down. It supersedes scattered notes
in chat history.

---

## TL;DR

The **subsidy module is functionally complete** with end-to-end budget
monitoring, control, and reporting. The next major architectural deliverable
is the **IM (Input Management) module — Phase 3**. All Gate-to-IM-Phase-3
conditions are met. There are no in-flight items blocking either the user
or the next phase.

Latest deployed build: **`reg-bb-engine` build-099** (`plugins/reg-bb-engine/reg-bb-engine-8.1-SNAPSHOT.jar`,
2026-05-06T07:01Z). L4-3 parity: **20/20 passing**. The architecture as
shipped:

* **Async submit processing (ADR-030).** `RegBbApplicationStoreBinder.store()`
  is now `super.store + return` — nothing else. Submit response &lt; 200 ms.
  `EligibilityProcessingWorker` discovers pending applications by polling
  `app_fd_subsidy_app_2025` for rows with `c_status IS NULL OR ''`; runs
  the eligibility chain (which includes budget dispatch); chain writes
  `c_status` to a terminal value, naturally excluding the row from next
  poll. No queue table on the happy path. Mat view refresh moved to
  `BudgetProjectionRefreshJob` (every 30s).
* **Metadata-driven capability adapter (Slice B / ADR-020 follow-up).**
  Three Java adapters (Farmer / Parcels / Subsidy) replaced by
  `MetaCapabilityAdapter` reading `mm_capability` + `mm_capability_field`
  rows. Adding a new capability for IM = YAML edit + seed run, no rebuild.
* **Caches (Slice A).** Static metadata cache with 30-second TTL on
  `mm_capability_field` reads + per-`EvalContext` value cache so same
  `(capability, field, applicant)` only resolves once per evaluation chain.

Test suite: **19/19 green** via `python3 tooling/test_budget_suite.py`
(~40s). Run this any time to verify nothing's regressed.

---

## What's done (subsidy module)

### L1 — citizen wizard
* Meta-model-driven form/screen rendering (`MetaScreenElement`,
  `MetaWizardElement`). Walks `mm_screen` + `mm_field` rows; synthesises
  Joget elements for every supported widget type (text, select, radio,
  checkbox, date, textarea, gis_polygon, file_upload, signature,
  cascading_select, repeating_group, plus the L3-1 budget_hint and L3-1
  eligibility_summary widgets).
* §6.4 conditional UI (visibility/required toggles via Determinant rule).
* Catalogue panel on the first wizard tab (which programmes the applicant
  is eligible for, before they pick).
* Identity header on every step + bot_pull NID auto-fill.

### L2 — capability adapter pattern (RegBB §3.2)
* `FarmerByNidAdapter`, `ParcelsSummaryAdapter`,
  `SubsidyApplicationCountAdapter`. Adapter registry resolves at evaluator
  time so rules can reference `$registry.farmer.first_name`,
  `$application.count` etc.
* Capability adapter unblocks D-003 (duplicate-application guard) +
  parcel-aware rules.

### L3-1 — Budget Engine (12 transaction types)
* **Storage layer** (Slice 1A): four-layer chart of accounts (Envelope /
  Source / Beneficiary / Vendor) per the methodology document. Every event
  is a balanced double-entry journal posting to `app_fd_budget_event`.
  `budget_projection` + `budget_projection_by_source` materialised views
  derive the six envelope subaccounts (Allocated / Available / Reserved /
  PreCommitted / Committed / Expensed) from the ledger. `budget_invariants`
  view asserts §4.1 identity per envelope.
* **Engine** (1B-i): `BudgetEngine.dispatch()` — resolves `mm_action`,
  evaluates condition, computes amount via `mm_determinant.targetValue`,
  prorates across sources (banker's rounding + largest-residual absorption),
  posts journal, refreshes projection. Idempotent on
  `(actionCode, applicationId, eventType)`.
* **Subsidy lifecycle wiring** (1B-ii): `RegBbApplicationStoreBinder` and
  `RegBbOperatorDecisionBinder` call `BudgetEngine.fireForLifecycle()` on
  every status transition. `auto_approved` → RESERVATION + PRE_COMMITMENT;
  `auto_rejected` → RESERVATION + RELEASE_RESERVATION (audit-preserving).
* **Cost Estimation Service** (1C): `/budget/ces/estimate` REST endpoint.
  Returns coverage ratio % and per-source breakdown for a
  `(programme, expectedApplicantCount)` pair. Programme launch gates
  (`mm_determinant`s like `GATE_LAUNCH_DEFAULT`) evaluated and surfaced.
* **Budget hint widget** (3a): `BudgetHintFormatter` (operator inbox column)
  + `MetaScreenElement` synthesises a `budget_hint` CustomHTML on the
  operator decision form. Both surfaces share `BudgetHintRenderer`.
* **Eligibility summary widget**: same pattern as budget_hint. Visible on
  operator decision form AND on citizen review tab (when
  `formData.getPrimaryKeyValue()` is non-empty — i.e., for re-opened
  applications).
* **Manual adjustments with maker-checker**: `budget_adjustment_request`
  form (TOP_UP / CLAWBACK / REALLOCATION). `BudgetAdjustmentBinder` enforces
  maker ≠ checker on approval, dispatches `MANUAL_TOP_UP` /
  `MANUAL_CLAWBACK` (or both legs for REALLOCATION sharing one
  correlation_id) via the new `BudgetEngine.dispatchDirect()`. Idempotent;
  `dispatch_outcome=ok:...` recorded on the request row to prevent
  re-dispatch.
* **Envelope freeze + threshold monitor**: `BudgetThresholdMonitor` (process
  tool plugin) scans `budget_projection`, posts to
  `app_fd_budget_threshold_alert` at 80% (WATCH), 100% (OVER), 110%
  (AUTO_FREEZE). At AUTO_FREEZE it also flips `app_fd_budget_envelope.c_status`
  to `frozen` with reason + timestamp. `BudgetEngine.dispatch` and
  `dispatchDirect` reject forward-funnel events on frozen/closed envelopes
  (RELEASE_* and MANUAL_CLAWBACK still allowed, so investigators can
  unstick funds during freeze).

### Reports (10 datalists in the **Budget** userview category)
* **Envelope state** (`dl_budget_envelopes`) — live state with utilisation,
  alert pill, 60-day sparkline, drill-down link, beneficiary count,
  per-applicant amount, top districts. Other columns hidden by default
  (toggle via Joget show/hide).
* **Per-source breakdown** (`dl_budget_sources`).
* **Recent ledger entries** (`dl_budget_events`).
* **Pending pipeline** (`dl_budget_pending_pipeline`) — applications in
  `pending_review` with potential exposure + risk indicator.
* **Variance report** (`dl_budget_variance`) — budgeted vs actual with
  date-range filter.
* **Manual adjustments** (`dl_budget_adjustments`) — pending requests at
  top, history below.
* **General Ledger** (`dl_budget_gl`) — per (envelope × account_path)
  opening balance + period activity + closing balance, date-range filter.
* **Roll-forward** (`dl_budget_rollforward`) — six-subaccount roll-forward
  per envelope, date-range filter.
* **Donor disbursement** (`dl_budget_donor_disbursement`) — per-source ×
  period view, generic shape ready to feed donor-specific templates.
* **Alerts** (`dl_budget_alerts`) — threshold-monitor output with
  acknowledgement workflow.

All 10 menus live in the **Budget** category of userview `v`.

### REST endpoints (`reg-bb-engine` BudgetApi)
* `POST /budget/dispatch` — fire a budget event for an mm_action+application.
* `POST /budget/ces/estimate` — programme cost projection + launch gates.
* `GET  /budget/timeseries` — daily event-count + commitment series for charts.
* `POST /budget/run-threshold-monitor` — invoke the monitor on demand.
  **NOT YET in API Builder enabledPaths** — see "What's next #1".

### L4 — convergence gates
* L4-1 D-001 / D-001b / D-002 / D-003 / D-004: all closed. Documented in
  CLAUDE.md "Hard-won Joget gotchas".
* L4-3 full parity test: 20/20 scenarios pass via
  `python3 tooling/run_l4_scenarios.py`.
* L4-4 operator UX qualitative sign-off: **deferred** until post-IM.
  Operators see the platform as one tool, sign-off makes more sense after
  IM ships and they can evaluate the full subsidy→IM journey.
* L4-5 render performance: partial improvement (~25s → 8s wizard render)
  via `MetaScreenElement` per-render cache. Full pass deferred until IM.

---

## What's next (priority order)

### 1. Register the new REST endpoints in API Builder
**Why:** Several endpoints are deployed in build-097 but unreachable until
added to the BudgetApi's `enabledPaths`.

**Where:** Joget App Composer → API Builder → `RegBB Budget API`
(API-BUDGET) → edit → in the BudgetApi element's `ENABLED_PATHS` field,
set to:

```
post:/dispatch;post:/ces/estimate;get:/timeseries;post:/run-threshold-monitor;post:/run-eligibility-worker;post:/run-projection-refresh
```

After that, swap the SQL-state assertions in
`test_budget_suite.py::test_threshold_monitor_via_db` with REST calls to
the same endpoints.

### 2. Schedule the workers (ADR-030 Steps 3 + 5)
Three workers want scheduling:
- `EligibilityProcessingWorker` — every 30 s. Drains processing_queue.
- `BudgetProjectionRefreshJob` — every 30 s. Refreshes the materialised views.
- `BudgetThresholdMonitor` — every 10–15 min. Posts utilisation alerts.

**How:** in App Composer, create one workflow process per worker with a
single tool step pointing at the plugin (registered via OSGi). Attach a
schedule trigger.

Two-minute click. After that, replace the SQL-state assertions in
`test_budget_suite.py::test_threshold_monitor_via_db` with a single
`http_post("/api/budget/run-threshold-monitor", {}, "API-BUDGET")` call
and assert the summary string contains the expected counts.

### 2. Schedule the threshold monitor
**Why:** the monitor exists but only runs when invoked. For real ops it
should run every 10–15 minutes.

**How:** in App Composer, create a workflow process with a single tool
step pointing at `BudgetThresholdMonitor` (registered via OSGi). Attach a
schedule trigger. Or use an external cron calling the REST endpoint from
#1 once that's enabled.

### 3. IM Phase 3 — the big next module
**Gate conditions:** L1 ✓, L2 ✓, L4-3 ✓, Budget Engine ✓, L4-5 partial
(acceptable), L4-4 deferred (acceptable). **All conditions met.**

**Scope:** the IM (Input Management) module covers procurement of input
packages (seeds, fertiliser, equipment) for approved beneficiaries,
e-voucher generation, vendor redemption, and beneficiary sub-ledger
maintenance. Architectural design lives in
`docs/architecture/architecture/components/im-module.md` (SAD task #92, completed).

**First slice (when starting):** scaffold `im-engine` plugin alongside
`reg-bb-engine`, define the `im_campaign` / `im_distribution_point` /
`im_entitlement` form set (already defined as `mm_*` configurable —
operators authoring through the existing meta-model). Wire the
COMMITMENT → EXPENSE transition in BudgetEngine to fire when an
e-voucher is redeemed.

### 4. Donor-template adapters (small, on-demand)
The `dl_budget_donor_disbursement` list emits the data; specific donors
(IFAD, World Bank, EU, AFD) want per-template formatting. Build one
adapter per donor as the agencies are named. Half a day each.

### 5. Smaller deferred items (any-time)
* **L3-1 Slice 3b** — RuleToSqlCompiler. Today's fast-path evaluator
  (`FastPathEvaluator`) handles all 20/20 production rules. The SQL-path
  bridge to the rules-API compiler is plumbed but not exercised yet.
  Land it when the first rule needs DB-side filtering (e.g., a parcel
  count rule).
* **Reconciliation report scheduled job** — auto-checks `budget_invariants`
  view daily, emails the budget officer on any imbalance. Today this is a
  manual SQL.
* **SAD diagrams (task #86)** — C4 context, sequence diagrams for key
  flows, ER, deployment. Useful for onboarding new team members. Half-day
  per diagram.
* **L4-4 operator UX sign-off** — post-IM, qualitative review with MAFSN
  staff.
* **L4-5 full perf pass** — post-IM, profile end-to-end including IM flows.

---

## How to verify "everything still works" when you come back

```bash
# 1. Test suite (40s, exit code 0 on green).
cd /Users/aarelaponin/IdeaProjects/rsr/lst-frm-prj
python3 tooling/test_budget_suite.py

# 2. L4 parity scenarios (5 fixtures × 4 programmes).
python3 tooling/run_l4_scenarios.py

# 3. Visual smoke test:
#    Open http://20.87.213.78:8080/jw/web/userview/farmersPortal/v
#    → Budget category should have 10 menus
#    → Envelope state list should show 4 programmes with utilisation pills
#    → Click "view →" on an envelope row → drills into operator inbox
#    → Open any submitted application → operator decision tab shows
#      Eligibility Outcome + Budget Impact panels
```

---

## Where things live

| Artefact | Location |
|---|---|
| Plugin source | `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/` |
| Plugin build | `bash plugins/reg-bb-engine/deploy/repack.sh` (auto-detects JDK) |
| Form definitions | `app/forms/<module>/<form>.json` |
| Datalist definitions | `app/datalists/<list>.json` |
| Userview definition | `app/userviews/v.json` |
| Seeds | `app/seeds/lesotho-mm-fixture.yaml`, `app/seeds/migrations/*.sql` |
| Test scripts | `tooling/*.py` (test_budget_suite, run_l4_scenarios, seed, push_*) |
| Architecture docs | `docs/architecture/architecture/` |
| Decision log | `docs/architecture/decision-log.md` |
| Methodology | `docs/architecture/architecture/components/budget-accounting-methodology.md` |
| Hard-won gotchas | `CLAUDE.md` |

## Build + deploy cycle (memorise this)

```bash
# 1. Edit Java source under plugins/<plugin>/src/...
# 2. Build:
JDK=/path/to/jdk-11 bash plugins/reg-bb-engine/deploy/repack.sh
# 3. Drop JAR into Joget /wflow/app_plugins/ (Manage Plugins UI handles upload)
# 4. Watch joget.log for "reg-bb-engine starting — build-NNN"
# 5. For form/datalist/userview changes:
python3 tooling/push_form.py app/forms/path/to/form.json
python3 tooling/push_datalist.py <listId>
python3 tooling/push_userview.py v
```

## API Builder credentials

| API | api_id | api_key |
|---|---|---|
| `formcreator` | `API-e7878006-c15a-425e-9c36-bebc7c4d085c` | `<JOGET_API_KEY>` |
| `regbb` (eval, submit) | `API-168e3678-1f9a-46fc-8c19-d0d9a917eb73` | (same) |
| `budget` | `API-BUDGET` | (same) |
| `gis` | `API-d5e0a0cc-a12a-4360-84b5-d794691c732e` | (same) |

## Known gotchas worth re-reading before resuming

In `CLAUDE.md`, the section **"Hard-won Joget gotchas that don't show up
until production"** has the full list. Highlights you'll bump into when
restarting:

* DatePicker dialect: `yy-mm-dd` not `yyyy-MM-dd`
* GIS captureMode/defaultMode: UPPERCASE only
* Postgres unquoted column folding: camelCase form-field id → lowercase
  column on disk
* `pg`/`pr`-shaped table aliases break under Joget's pgjdbc driver
* DSL string literals don't support SQL `''` escape — use the other quote
  char if value contains one
* psycopg2 `%` characters in raw SQL with `params=()` are interpreted as
  placeholders — escape `%` as `%%` in LIKE patterns
* CustomHTML elements must have non-null `label` (freemarker NPE)
* `Class.forName` doesn't see across un-exported bundle packages — use
  `PluginManager.getPlugin()` for cross-bundle plugin lookups

## Pending tasks that survive the session

Task #86 (SAD diagrams — C4 + sequence + ER + deployment) has been
pending since the SAD writing sprint. Half-day of work; pick up when
ready to onboard new contributors.
