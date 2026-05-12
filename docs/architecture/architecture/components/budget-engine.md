# Component SAD — Budget &amp; Commitment Engine

| Field             | Value                                                                                                                                                                                                                                                                                                                                                            |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | Budget &amp; Commitment Engine                                                                                                                                                                                                                                                                                                                                  |
| Document title    | Software Architecture Description (component level)                                                                                                                                                                                                                                                                                                              |
| Version           | 0.2 — DRAFT (D34 amendment May 2026: methodology-aligned; multi-fund + sub-ledger structure added)                                                                                                                                                                                                                                                                |
| Date              | 2026-05-02 / amended 2026-05-03                                                                                                                                                                                                                                                                                                                                  |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                                |
| Related           | Solution-level SAD; framework SAD (rule grammar + audit); subsidy-module SAD (budget integration on application lifecycle); IM-module SAD (voucher → COMMIT/EXPENSE events); reporting-engine SAD (budget dashboard + SLA reports); ADRs 022–026; `docs/architecture/policy-to-rules-migration.md`; **methodology** `budget-accounting-methodology.md` (D34, May 2026 — implementation contract). |
| What this is      | The component-level SAD for the Budget &amp; Commitment Engine — a cross-cutting module that tracks **public-finance commitment funnel** for programme budgets (Reservation → Pre-commitment → Commitment → Expense), provides a **Cost Estimation Service** for programme-design-time budget alignment, and exposes the funnel through datalists for operator UX. **All policy decisions (tolerance, overrun, authorisation, SLA) are mm_determinant rules, not engine code or schema attributes.** Per D34 (May 2026), the engine implements `budget-accounting-methodology.md` as its business contract — chart of accounts, transaction catalogue, invariants, audit trail. Schema/listener prescriptions in this SAD are reframed as "the engine renders the methodology's accounts and transactions in PostgreSQL + Joget DAOs"; the methodology document is the source of truth for what those accounts and transactions ARE. |
| What this is not  | A general-ledger system across the whole MAFSN. A payment integration. An ERP-grade finance module. A consolidated-fund integration (statutory IPSAS reports for the auditor-general are out-of-scope until/unless explicitly requested). The engine tracks budget commitment for the four 2025 programmes (Phase 3 expansion: IM voucher integration). |

> **Reader's pointer.** For business-level "what does this mean?" questions — chart of accounts, what each transaction posts in debit/credit, invariants, worked examples through Mants'ali's lifecycle, IPSAS terminology mapping — read `budget-accounting-methodology.md` first. This SAD covers the implementation shape (storage tables, listener wiring, DAO classes); the methodology covers the business model the implementation must conform to. Code reviews require both: implementation matches methodology AND SAD describes how it's wired.

---

## 1. Introduction and goals

### 1.1 Purpose and scope

Public-sector subsidy programmes operate against allocated budgets. A programme designer commits MAFSN to a benefit shape (e.g. M2,000 per smallholder under PRG_2025_001) and a target population (e.g. 600 smallholders); the implied programme cost (M1,200,000) must align with the budget envelope authorised by Treasury or the Ministry's finance officer. As applications arrive, the system must track the funnel:

1. **Reservation** — citizen submits a finalised application → soft hold against the envelope.
2. **Pre-commitment** — operator approves → hard obligation; programme is committed to deliver the benefit.
3. **Commitment** — voucher issued (IM module) → funds earmarked to a specific physical claim with a specific input + price.
4. **Expense** — voucher redeemed → actual money out the door.

Plus the inverse events: rejection, cancellation, unredeemed expiry → release back to available.

Without this funnel the system has three holes: (a) the operator deciding has no visibility into remaining budget, (b) MAFSN management has no visibility into programme burn rate, (c) financial reconciliation at quarter-end has no ground truth to reconcile against.

The Budget Engine fills those three holes with one cross-cutting component, integrated into the subsidy and IM lifecycles via the existing `mm_action` dispatch mechanism. It is deliberately small: storage + event ledger + projection + Cost Estimation Service. **Every policy decision** (what tolerance is acceptable, who can authorise an overrun, what counts as SLA breach) **is an `mm_determinant` rule**. The engine has no policy attributes.

In scope:

- `budget_envelope` — one row per programme per fiscal year, carrying allocated amount.
- `budget_event` — append-only ledger; one row per state transition.
- `budget_projection` — derived view; current-state per envelope.
- **Cost Estimation Service (CES)** — programme-design-time count + amount calculator. Walks the eligibility rules against the farmer registry, computes expected eligible count × per-applicant amount, returns an estimate.
- **Programme launch gate** — a rule evaluation that checks the CES output against the envelope before allowing a programme to transition `draft → published`.
- **Funnel event capture** — the engine subscribes to `mm_action.kind=budget_event` dispatches and writes ledger rows.
- **Operator-decision-time budget visibility** — inline hint on the operator review form showing "approving will pre-commit M285k of M1.2M remaining."
- **Budget dashboard reports** — aggregations over the projection (utilisation %, burn rate, available balance), built per the reporting engine pattern.

Out of scope:

- General-ledger accounting (debits/credits, journal entries, statutory financials).
- Payment provider integration (the engine records that an expense happened; the actual disbursement to a supplier or citizen is downstream).
- Multi-currency, FX rates, treasury reconciliation.
- Budget request workflow (the upstream process by which Treasury allocates an envelope).
- IM stock valuation (the engine tracks expense per voucher redemption; aggregate stock value is an IM concern).

### 1.2 Quality goals (component-level)

| Rank | Quality goal                          | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                                                |
| ---- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | **Policy in rules, not in code**      | Tolerance thresholds, overrun policy, authorisation, SLA targets are `mm_determinant` rows in scopes `budget_tolerance`, `programme_launch_gate`, `budget_overrun_policy`, `budget_authorisation`, `sla_decision`. Engine code has zero hardcoded policy values. Authoring a new programme can change *every* policy by writing different rules. Target: zero PRs to engine code for policy changes.       |
| 2    | **Funnel integrity — events never lost** | Every state transition that should produce a budget event produces one. `RegBbApplicationStoreBinder` firing without a corresponding `Reservation` event is a defect. The funnel is reconcilable at any time: `sum(committed events) = sum(active vouchers' values)`; `sum(expense events) = sum(redeemed vouchers' realised amounts)`.                                                                       |
| 3    | **Cost Estimation Service is honest about its inputs**| CES output reports the registry coverage ratio (% of farmers with the data needed to evaluate the programme's rules). If 60% of farmers have parcel data, CES says "estimated count = N; based on 60% registry coverage; raw count among complete records = M". The system never silently extrapolates; it reports what it knows. Operations addresses registry gaps as a separate concern.              |
| 4    | **UX — operator sees budget impact at decision time** | The operator review tab shows, before they click Approve: *"Approving will pre-commit M285,000. Envelope after: M915,000 / M1,200,000 (76% utilised, 24% remaining)."* If the rule `budget_overrun_policy` would block the approval, the Approve button is disabled with the rule's failMessage shown. If a warning rule would fire, the operator sees the warning + can proceed.                            |
| 5    | **Auditability — every cent is traceable** | For every cent in `expensed`, there is an unbroken chain: `expense_event → voucher → pre-commit_event → application_approval → reservation_event → application_submission`. The chain is queryable through `correlation_id`. A finance auditor asking "where did this M50,000 go" gets a complete answer from the ledger alone.                                                                                                       |

### 1.3 Stakeholders

| Stakeholder                       | Concerns                                                                                                                                                          |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MAFSN finance officer**         | Programme budgets are honoured; no programme launches without budget alignment; reconciliation at quarter-end matches the ledger; overrun is visible and controlled. |
| **MAFSN programme lead**          | Programme cost is estimated honestly before launch; budget burn rate is visible in real time; reallocation is possible mid-cycle.                                  |
| **Operator (case worker)**        | Decision-time visibility into budget impact; no "phantom" approvals that blow the budget without warning.                                                          |
| **Operator-analyst (programme designer)** | Cost estimation runs cheaply; CES output is interpretable; the launch gate rule is configurable by them, not by engineering.                                |
| **Auditor / inspector**           | The ledger is append-only; every event traces to a transaction; manual adjustments require notes and are auditable.                                                 |
| **Engineering team**              | The engine is small; policy is configuration; new programmes don't require engine code changes.                                                                     |

---

## 2. Architecture constraints

Inherits all solution-level + framework constraints. Engine-specific:

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                                              |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BE-C1 | **Append-only ledger.** `budget_event` rows are never updated, never deleted. Corrections are new rows (e.g. `RECONCILIATION_ADJUSTMENT` with a `corrects=<event_id>` reference). The ledger is the forensic record; mutating it falsifies the record.                                                                                                                                                       |
| BE-C2 | **Policy via rules, not attributes.** No `tolerance_pct`, `overrun_policy`, `auto_release_days` columns on `budget_envelope`. Every policy decision is an `mm_determinant` rule evaluated at the relevant decision point. Adding a column for policy is a violation of this principle.                                                                                                                  |
| BE-C3 | **Engine reads farmer + parcel registries via the framework's capability adapter pattern, not directly.** CES uses the same `$registry.*` reference scope that eligibility rules use, routed through `RoutingEvaluator` + `SqlPathEvaluator`. No direct JDBC SELECTs against `app_fd_farmerbasicinfo` or `app_fd_parcelregistration` from the Budget Engine.                                                |
| BE-C4 | **Engine writes ledger only.** No subsidy or IM table writes. Budget events are RECEIVED (via `mm_action` dispatch); the engine does not push side effects back into other modules. The decision binders own their own state; the engine reflects, doesn't drive.                                                                                                                                              |
| BE-C5 | **Programme launch gate is rule-evaluated, not gate-coded.** The transition `mm_registration.status: draft → published` is gated by a `programme_launch_gate` rule. Engine code does not contain `if (cost > budget * 1.05) reject`; that's the rule's job.                                                                                                                                                  |
| BE-C6 | **CES output is read-only data, never authoritative state.** A CES estimate from yesterday is stale by tomorrow (registry grows; rules change; benefit prices shift). The estimate is recomputed on demand or on a schedule; it is never persisted as the "real" cost. Only `budget_event` rows are authoritative.                                                                                          |

---

## 3. Context and scope

### 3.1 Component context

```
                ┌──────────────────────────────────────────────────────┐
                │   Subsidy module       IM module       Operator UI   │
                │   (lifecycle events)   (voucher        (decision     │
                │                         events)         + dashboard) │
                └────────────────┬───────────────┬───────────┬─────────┘
                                 │               │           │
                                 │ mm_action     │ mm_action │ datalist
                                 │ kind=budget_  │ kind=     │ render
                                 │ event         │ budget_   │
                                 │ dispatched    │ event     │
                                 ▼               ▼           ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │              BUDGET &amp; COMMITMENT ENGINE                          │
   │                                                                  │
   │  ┌──────────────────────┐    ┌─────────────────────────────────┐ │
   │  │ Cost Estimation      │    │ Funnel Event Listener           │ │
   │  │ Service              │    │ (subscribes to mm_action        │ │
   │  │ (count × amount,     │    │  dispatches with kind=          │ │
   │  │  rules-driven gate)  │    │  budget_event)                  │ │
   │  └────────┬─────────────┘    └────────┬────────────────────────┘ │
   │           │                           │                          │
   │           │ uses framework's          │ writes                   │
   │           │ RoutingEvaluator + SQL    │                          │
   │           │ to count eligible farmers │                          │
   │           ▼                           ▼                          │
   │  ┌──────────────────────────────────────────────────────────┐    │
   │  │ Storage:                                                 │    │
   │  │   budget_envelope  (per programme × fiscal year)         │    │
   │  │   budget_event     (append-only ledger)                  │    │
   │  │   budget_projection (derived view; refreshed on event)   │    │
   │  └──────────────────────────────────────────────────────────┘    │
   │                                                                  │
   │  ┌──────────────────────────────────────────────────────────┐    │
   │  │ Operator UX surfaces (rendered through Joget userviews): │    │
   │  │   - Inline budget hint on operator decision tab          │    │
   │  │   - Programme launch wizard (CES + gate)                 │    │
   │  │   - Budget dashboard (per programme + per envelope)      │    │
   │  │   - Manual adjustment surface (budget admin only)        │    │
   │  └──────────────────────────────────────────────────────────┘    │
   └──────────────────────────────────────────────────────────────────┘
                                 │ uses
                                 ▼
                ┌──────────────────────────────────────────────────────┐
                │  RegBB framework (rule grammar, audit, mm_action)    │
                │  MM-form-gen kernel (form rendering for adj. surface)│
                └──────────────────────────────────────────────────────┘
```

### 3.2 Upstream consumers

| Consumer                          | Interaction                                                                                                                                                                                                                |
| --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Subsidy module                    | Application lifecycle transitions emit budget events via `mm_action.kind=budget_event` rows (see §4.3). The decision binder also calls CES at decision time to fetch current envelope state for the inline budget hint.       |
| IM module (Phase 3)               | Voucher lifecycle (issuance → redemption → cancellation) emits budget events (Commitment, Expense, Release).                                                                                                                |
| Programme designer (operator-analyst) | Runs CES from the Programme Builder admin UI; reads the result; clicks Publish (gated by the `programme_launch_gate` rule).                                                                                              |
| Operator UI                       | Reads `budget_projection` to display the inline budget hint at decision time; reads dashboards.                                                                                                                              |
| Budget admin                      | Creates and reallocates envelopes through the manual adjustment surface (gated by `budget_authorisation` rules).                                                                                                            |
| Reporting engine                  | Consumes `budget_event`, `budget_projection`, and `budget_envelope` tables for programme financial reports.                                                                                                                  |

### 3.3 Downstream dependencies

| Dependency                                                  | What for                                                                                                                                                                                                |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RegBB framework — `RoutingEvaluator`                        | CES uses the evaluator to test eligibility rules against farmer-registry rows. Same evaluator, batch invocation.                                                                                          |
| RegBB framework — `mm_determinant` infrastructure           | All policy lives here. New scope values: `budget_tolerance`, `programme_launch_gate`, `budget_overrun_policy`, `budget_authorisation`, `sla_decision`.                                                  |
| RegBB framework — `mm_action` dispatch                      | Subscribes to `kind=budget_event` to receive lifecycle events from subsidy + IM.                                                                                                                          |
| RegBB framework — `AuditWriter` / `reg_bb_eval_audit`        | The evaluator's audit goes to `reg_bb_eval_audit` as usual. Budget events go to a dedicated `budget_event` table — semantically different (transactional events, not rule evaluations).                  |
| MM-form-gen kernel                                          | The manual adjustment surface for budget admin is a kernel-rendered form (`mm_screen` + `mm_field` for envelope edit + audit-required notes field).                                                       |
| Farmer registry tables (read-only via capability adapter)    | CES iterates farmers by NID-keyed lookups through the capability adapter pattern.                                                                                                                          |
| Joget enterprise plugins                                    | `JdbcDataListBinder` for the budget dashboard reports.                                                                                                                                                    |

---

## 4. Solution strategy

### 4.1 Funnel state model

```
   ┌────────────┐  citizen        ┌──────────────┐  approve         ┌─────────────────┐
   │  draft     │   submits       │  Reserved    │  decision        │  Pre-committed  │
   │ (citizen   │ ──────────────▶ │  on envelope │ ──────────────▶  │  on envelope    │
   │  wizard)   │                 │              │                  │                 │
   └────────────┘                 └──────────────┘                  └────────┬────────┘
                                          │                                  │
                                          │ reject /                         │ voucher
                                          │ withdraw                         │ issued (IM)
                                          ▼                                  ▼
                                  ┌──────────────────┐             ┌─────────────────┐
                                  │  Released        │             │  Committed      │
                                  │  (back to avail.)│             │  (asset-level   │
                                  └──────────────────┘             │   earmark)      │
                                                                   └────────┬────────┘
                                                                            │ voucher
                                                                            │ redeemed (IM)
                                                                            ▼
                                                                   ┌─────────────────┐
                                                                   │  Expensed       │
                                                                   │  (real spend)   │
                                                                   └─────────────────┘

   Inverse events at every stage: reject, cancel, expire, return, refund.
```

The state of an envelope at any moment is the sum of events that have hit it. The projection materialises the totals.

### 4.2 Event types

| Event type                     | When it fires                                                              | Effect on projection                                |
| ------------------------------ | -------------------------------------------------------------------------- | --------------------------------------------------- |
| `BUDGET_ALLOCATION`            | Budget admin creates envelope or adds funds                                 | `allocated += amount`                                |
| `BUDGET_REALLOCATION`          | Budget admin moves funds between envelopes                                  | `allocated += amount` on dest, `-= amount` on src    |
| `RESERVATION`                  | Citizen submits a finalised application                                    | `reserved += amount`                                 |
| `RELEASE_RESERVATION`          | Application rejected / withdrawn                                            | `reserved -= amount`                                 |
| `PRE_COMMITMENT`               | Operator approves application                                               | `reserved -= amount`, `pre_committed += amount`      |
| `RELEASE_PRE_COMMITMENT`       | Approval reversed (rare; e.g. supervisor override)                          | `pre_committed -= amount`, `reserved += amount`      |
| `COMMITMENT`                   | Voucher issued (IM)                                                         | `pre_committed -= amount`, `committed += amount`     |
| `RELEASE_COMMITMENT`           | Voucher cancelled / expired unredeemed                                      | `committed -= amount`, then either back to pre_committed (if entitlement still active) or to allocated (if entitlement expires) |
| `EXPENSE`                      | Voucher redeemed                                                            | `committed -= amount`, `expensed += amount`          |
| `EXPENSE_ADJUSTMENT`           | Realised redemption amount differs from committed amount (price drift)     | `expensed += delta` (positive or negative)           |
| `RECONCILIATION_ADJUSTMENT`    | Manual reconciliation by budget admin (with required notes)                  | Adjusts any of the projection fields per the event   |

### 4.3 Integration via `mm_action.kind=budget_event`

Each subsidy/IM lifecycle hook is wired to a budget event by an `mm_action` row:

```yaml
mm_action:
  - code: BUDGET_RESERVE_ON_SUBMIT
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": ["pending_operator_review", "pending_data_clarification"],
        "eventType": "RESERVATION",
        "amountFormulaRule": "BENEFIT_AMOUNT_PRG_2025_001" }

  - code: BUDGET_RELEASE_ON_REJECT
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": "rejected",
        "eventType": "RELEASE_RESERVATION" }

  - code: BUDGET_PRE_COMMIT_ON_APPROVE
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": "approved",
        "eventType": "PRE_COMMITMENT",
        "amountFormulaRule": "BENEFIT_AMOUNT_PRG_2025_001" }

  - code: BUDGET_COMMIT_ON_VOUCHER_ISSUE
    serviceId: INPUTS_2025
    kind: budget_event
    triggerJson: |
      { "onVoucherStatus": "issued",
        "eventType": "COMMITMENT",
        "amountFormulaRule": "VOUCHER_COMMITMENT_AMOUNT" }

  - code: BUDGET_EXPENSE_ON_REDEMPTION
    serviceId: INPUTS_2025
    kind: budget_event
    triggerJson: |
      { "onVoucherStatus": "redeemed",
        "eventType": "EXPENSE",
        "amountFormulaRule": "VOUCHER_REALISED_AMOUNT" }
```

The `amountFormulaRule` value names a `mm_determinant` row whose rule expression evaluates to a number — the amount for this event. Examples:

```yaml
mm_determinant:
  - code: BENEFIT_AMOUNT_PRG_2025_001
    scope: budget_amount
    ruleType: arithmetic
    ruleJson: 2000   # M2,000 flat per applicant for PRG_2025_001

  - code: BENEFIT_AMOUNT_PRG_2025_004
    scope: budget_amount
    ruleType: arithmetic
    ruleJson: 5000 + (household_dependents_under_18 * 1500)   # base + per-dependent
```

The rule grammar's arithmetic operators handle this; the evaluator's output is read as a number rather than a boolean.

### 4.4 Cost Estimation Service

CES is invoked by the Programme Builder UI:

```
1. Operator-analyst clicks "Estimate cost" on a draft programme.
2. CES reads:
     mm_registration             → strategy, applicabilityDeterminantId
     mm_determinant (eligibility) → the rule set
     mm_benefit                  → benefit shape + amount formula rule
     budget_envelope             → allocated amount for this programme
3. CES counts eligible farmers:
   For fast-path-eligible rules (no $registry.* references): use rule-to-SQL compiler (ADR-026)
     to issue ONE Postgres count query.
   For rules with $registry.* references: iterative — page through farmers, evaluate per row,
     accumulate count.
4. CES computes per-applicant amount:
   Evaluate the BENEFIT_AMOUNT_PRG_X rule against a "representative applicant" context
   (averaged registry values for the eligible cohort, plus benefit-line constants).
5. CES output:
   {
     "estimated_cost": 1185000.0,
     "eligible_count": 593,
     "per_applicant_amount": 2000.0,
     "registry_coverage_ratio": 0.94,
     "breakdown_by_district": { "maseru": 142, "berea": 89, ... },
     "evaluated_at": "2026-05-02T10:30:00Z",
     "rule_versions": { "DET_LOWLANDS": "20260428", "DET_AREA_NONZERO": "20260428" }
   }
6. UI renders the breakdown for the operator-analyst.
```

CES does NOT decide whether the programme can launch. The decision is the next step:

### 4.5 Programme launch gate

```
1. Operator-analyst clicks "Publish" on the programme.
2. Framework evaluates programme_launch_gate rule for this programme:
     EvalContext.data = {
       cost_estimate: 1185000.0,
       budget_allocated: 1200000.0,
       eligible_count: 593,
       registry_coverage_ratio: 0.94,
       user_roles: ["MOA_PROGRAMME_DESIGNER"],
       has_acceptance_window: true,
       has_eligibility_rules: true,
       ...
     }
3. The rule (authored once per service or per programme) evaluates whatever the policy says:
     Example A (5% tolerance, no role override):
       cost_estimate <= budget_allocated * 1.05
     Example B (5% tolerance, director override):
       (cost_estimate <= budget_allocated * 1.05) OR (user_roles in ['MOA_DIRECTOR'])
     Example C (composite):
       cost_estimate <= budget_allocated * 1.05
       AND has_acceptance_window
       AND has_eligibility_rules
       AND registry_coverage_ratio >= 0.80
4. TRUE → mm_registration.status: draft → published; programme can now receive applications.
   FALSE → block; show the rule's failMessage to the user.
```

Different programmes can have different gate rules. An emergency-relief programme might have looser tolerance + director override. A pilot programme might have tighter tolerance + analyst-only authority. None of this is in engine code.

### 4.6 Authorisation as rules

Sample rules:

```yaml
mm_determinant:
  - code: AUTH_CREATE_ENVELOPE
    scope: budget_authorisation
    ruleType: inclusion
    ruleJson: user_roles in ['MOA_BUDGET_ADMIN', 'MOA_FINANCE_DIRECTOR']
    failMessage: Only budget admins and the finance director can create envelopes.

  - code: AUTH_REALLOCATE_BETWEEN_PROGRAMMES
    scope: budget_authorisation
    ruleType: inclusion
    ruleJson: user_roles in ['MOA_FINANCE_DIRECTOR']
    failMessage: Inter-programme reallocation requires finance director.

  - code: AUTH_OVERRIDE_OVERRUN_AT_DECISION_TIME
    scope: budget_authorisation
    ruleType: inclusion
    ruleJson: user_roles in ['MOA_DIRECTOR'] AND decision_value == 'approve'
    failMessage: Approving an application that would exceed the budget envelope requires the director.
```

The Budget Engine's manual-adjustment surface and the operator decision flow each call `RoutingEvaluator.evaluate(rule, ctx)` with the proposed operation in `ctx.data`. The rules can be edited by anyone with authority to author rules (typically via the Programme Builder admin UI in Phase 2).

### 4.7 SLA monitoring as reporting

`sla_decision` scope rules feed the reporting engine, not the Budget Engine. Sample:

```yaml
mm_determinant:
  - code: SLA_DECISION_PRG_2025_001
    scope: sla_decision
    ruleType: inclusion
    ruleJson: days_since_submitted > 5
    failMessage: SLA breach — operator review pending more than 5 days.

  - code: SLA_DECISION_PRG_2025_004
    scope: sla_decision
    ruleType: inclusion
    ruleJson: days_since_submitted > 2   # drought emergency relief — tighter SLA
    failMessage: SLA breach — drought relief operator review pending more than 2 days.
```

Reporting datalists evaluate these rules per-row (via the framework's `RoutingEvaluator` invoked from a small custom column formatter, or via SQL translation when feasible). SLA breach reports are part of the reporting engine, not the Budget Engine. Optional: a scheduled `mm_action.kind=sla_alert` fires when a rule turns TRUE for a given application, triggering a notification workflow.

---

## 5. Building block view

### 5.1 Whitebox — packages within the engine

```
global.govstack.regbb.budget/                    ← new OSGi bundle (proposed)
   Activator.java                                 — registers plugins
   Build.java                                     — build stamp
   model/
      BudgetEvent.java                            — value object for one ledger row
      BudgetProjection.java                       — value object for current state
      EventType.java                              — enum
      AmountResolver.java                         — evaluates amount rules
   dao/
      BudgetEventDao.java                         — append + query
      BudgetEnvelopeDao.java                      — read + (admin only) write
      BudgetProjectionDao.java                    — refresh + read
   listener/
      BudgetEventListener.java                    — subscribes to mm_action.kind=budget_event
                                                    via a pluggable dispatch pattern
   ces/
      CostEstimationService.java                  — count + amount; uses RoutingEvaluator
      RuleToSqlCompiler.java                      — ADR-026; closed-twenty → Postgres WHERE
   ui/
      BudgetHintFormatter.java                    — column formatter for the operator decision
                                                    tab inline hint
```

Estimated bundle size: 1500–2500 LoC at first complete cut.

### 5.2 Storage schema

**`budget_envelope`** (one row per programme × fiscal year):

```
id (UUID) | dateCreated | dateModified | createdBy | modifiedBy
c_code               (UPPER_SNAKE_CASE; e.g. ENV_PRG_2025_001_FY2526)
c_programme_code     (FK by code → mm_registration.code)
c_fiscal_year        ('2025/26')
c_currency           ('LSL')
c_allocated_amount   (numeric — cumulative from all BUDGET_ALLOCATION events)
c_status             (active | frozen | closed)
c_revision           (integer; bumped on each reallocation)
c_notes              (free text)
```

**`budget_event`** (append-only ledger):

```
id (UUID) | dateCreated | createdBy
c_envelope_code      (FK by code → budget_envelope.code)
c_event_type         (RESERVATION | PRE_COMMITMENT | COMMITMENT | EXPENSE | RELEASE_* | ALLOCATION | REALLOCATION | RECONCILIATION_ADJUSTMENT | EXPENSE_ADJUSTMENT)
c_amount             (numeric; positive — direction is implied by event_type)
c_currency           ('LSL')
c_correlation_id     (the applicationId, voucherId, redemptionId that drove the event)
c_correlation_type   (subsidy_application | voucher | redemption | manual)
c_source_module      (subsidy | im | manual | migration)
c_actor              (Joget user-id; or 'system' for automated events)
c_notes              (free text; required for manual adjustments)
c_correction_of      (event_id of the event this corrects, for RECONCILIATION_ADJUSTMENT and EXPENSE_ADJUSTMENT)
c_action_code        (the mm_action.code that dispatched this event, for traceability)
c_rule_version       (the BENEFIT_AMOUNT_* rule's version at evaluation time)
```

**`budget_projection`** (PostgreSQL materialised view, refreshed on each event write):

```sql
CREATE MATERIALIZED VIEW budget_projection AS
SELECT
  envelope_code,
  SUM(CASE WHEN event_type IN ('BUDGET_ALLOCATION','BUDGET_REALLOCATION') THEN amount ELSE 0 END) AS allocated,
  SUM(CASE WHEN event_type = 'RESERVATION' THEN amount
            WHEN event_type = 'RELEASE_RESERVATION' THEN -amount
            WHEN event_type = 'PRE_COMMITMENT' THEN -amount
            ELSE 0 END) AS reserved,
  SUM(CASE WHEN event_type = 'PRE_COMMITMENT' THEN amount
            WHEN event_type = 'RELEASE_PRE_COMMITMENT' THEN -amount
            WHEN event_type = 'COMMITMENT' THEN -amount
            ELSE 0 END) AS pre_committed,
  SUM(CASE WHEN event_type = 'COMMITMENT' THEN amount
            WHEN event_type = 'RELEASE_COMMITMENT' THEN -amount
            WHEN event_type = 'EXPENSE' THEN -amount
            ELSE 0 END) AS committed,
  SUM(CASE WHEN event_type = 'EXPENSE' THEN amount
            WHEN event_type = 'EXPENSE_ADJUSTMENT' THEN amount
            ELSE 0 END) AS expensed,
  MAX(dateCreated) AS last_event_at
FROM (SELECT * FROM app_fd_budget_event) be
GROUP BY envelope_code;
```

Refresh on each event write via a trigger or a post-insert listener; cheap because it's a GROUP BY over one indexed column.

`available = allocated - reserved - pre_committed - committed - expensed`.

### 5.3 BudgetEventListener — the event hook

The Listener subscribes to `mm_action.kind=budget_event` dispatches. When the framework's `WorkflowDispatcher` dispatches a `mm_action` of this kind:

1. Listener reads `triggerJson`: `eventType`, `amountFormulaRule`, optional condition rule.
2. Optional condition: if a condition rule is set (e.g. only fire on certain dispositions), evaluate it; skip if FALSE.
3. Resolve the amount: invoke `RoutingEvaluator` on the named `amountFormulaRule` against the application/voucher row's data.
4. Resolve the envelope: from `mm_registration.budgetEnvelopeCode` (or from the action's `triggerJson.envelopeCode`).
5. Write the `budget_event` row.
6. Refresh `budget_projection` for the envelope.

This is the only write path into the Budget Engine from subsidy/IM. No direct API calls.

### 5.4 Operator UX surfaces

| Surface                        | Mechanism                                                                                                                                                                                                |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Inline budget hint**         | Custom column formatter on the operator decision tab. Reads the projection for the application's programme; renders a small panel: "Approving will pre-commit M285,000. Envelope after: M915,000 / M1,200,000 (76% utilised)." Backed by a single SELECT against `budget_projection`. |
| **Programme launch wizard**    | A multi-step UI in the Programme Builder that runs CES, displays the breakdown, evaluates `programme_launch_gate`, and either publishes the programme or shows the rule's failMessage.                       |
| **Budget dashboard**           | Standard datalist over `budget_envelope` joined to `budget_projection`. Per programme: progress bar (allocated → reserved → pre-committed → committed → expensed), available balance, utilisation %, last-event date. |
| **Manual adjustment surface**  | Kernel-rendered form (`mm_screen` + `mm_field`) for envelope edits + budget admin reallocations + reconciliation adjustments. Every save requires a notes field. Each save writes the appropriate event type to the ledger. Authorisation gated by `budget_authorisation` rules. |
| **Funnel ledger view**          | A datalist over `budget_event` filtered to one envelope, showing the chronological event stream. For forensic queries.                                                                                      |
| **Funnel reconciliation**      | A scheduled report that compares: sum(committed events for active vouchers) vs sum(active vouchers' values); sum(expense events) vs sum(redeemed vouchers' realised amounts). Mismatches surface as an alert.   |

---

## 6. Runtime view

### 6.1 Programme launch — operator-analyst publishes a draft programme

```
1. Operator-analyst opens Programme Builder, drafts PRG_2026_001 with:
   - eligibility rules (DET_LOWLANDS, DET_AREA_NONZERO, DET_FARMER_REGISTERED)
   - benefit (M2000 per applicant, BENEFIT_AMOUNT_PRG_2026_001)
   - target acceptance window (Sep 1 - Dec 31, 2026)
2. Analyst creates envelope:
   - ENV_PRG_2026_001_FY2627, allocated M1,200,000
   - Save → BUDGET_ALLOCATION event written; projection refreshed
3. Analyst clicks "Estimate cost":
   - CES runs:
     - SQL count for fast-path rules: 643 farmers
     - Iterative count for $registry.farmer.first_name rule: filters to 612 (some farmer-registry rows have null first_name — registry data quality issue, surfaced)
     - Per-applicant amount: 2000
     - Estimated cost: M1,224,000
     - Registry coverage ratio: 0.94
4. UI shows breakdown; analyst sees they're about M24,000 over.
5. Analyst clicks "Publish" anyway — programme_launch_gate rule fires:
   EvalContext = { cost_estimate: 1224000, budget_allocated: 1200000, ... }
   Rule: cost_estimate <= budget_allocated * 1.05  (allow 5% over)
       1224000 <= 1260000  → TRUE
   ✓ Programme transitions draft → published.
6. PRG_2026_001 is now live. Citizens can apply.
```

If the analyst hadn't been within 5%, they'd:
- Tighten eligibility (add a rule), re-estimate.
- Reduce per-applicant amount.
- Request more budget (BUDGET_REALLOCATION from another envelope).
- Escalate to director (different rule allows director override).

### 6.2 Citizen submits — Reservation event

```
1. Citizen completes the wizard, clicks Submit on the Review tab.
2. RegBbApplicationStoreBinder fires:
   - tx 1: persists the row with status=pending_operator_review
   - tx 2: evaluates eligibility, persists outcome JSON, audit rows, etc.
   - tx 3: WorkflowDispatcher.dispatch(SUBSIDY_2025, registrationId, status_change, ...)
3. Dispatch matches BUDGET_RESERVE_ON_SUBMIT mm_action (kind=budget_event).
4. BudgetEventListener:
   - Resolves amount: evaluates BENEFIT_AMOUNT_PRG_2025_001 → 2000
   - Resolves envelope: ENV_PRG_2025_001_FY2526
   - Writes RESERVATION event: amount=2000, correlation_id=applicationId
   - Refreshes projection: reserved += 2000
5. Operator inbox now shows applicant + projection's "reserved" goes up by M2000.
```

### 6.3 Operator approves — Pre-commitment event + budget hint

```
1. Operator opens application; sees the inline budget hint:
   "Approving will pre-commit M2,000. Envelope after: M915,000 / M1,200,000 (76% utilised)."
2. budget_overrun_policy rule evaluated: would the approval breach the envelope after pre-commit?
   Rule: (allocated - committed - expensed - pre_committed - amount) >= 0
   In this case: yes, comfortable margin. Approve button enabled.
3. Operator clicks Approve.
4. RegBbOperatorDecisionBinder fires (tx 1 + tx 2 as documented).
5. WorkflowDispatcher dispatches BUDGET_PRE_COMMIT_ON_APPROVE mm_action.
6. BudgetEventListener:
   - Resolves amount: 2000 (evaluator caches the rule result; no re-computation)
   - Writes PRE_COMMITMENT event
   - Projection: reserved -= 2000, pre_committed += 2000
```

If the approval would have breached the envelope, `budget_overrun_policy` rule returns FALSE, the Approve button is shown disabled with the rule's failMessage; operator must escalate.

### 6.4 Voucher issued and redeemed (Phase 3)

```
1. IM workflow (sysadmin-authored XPDL) fires when an application is approved:
   - Creates imEntitlement row
   - Creates im_voucher row with status=issued, qty=50kg (maize seed), expiry=Dec 31
2. Voucher save dispatches BUDGET_COMMIT_ON_VOUCHER_ISSUE mm_action.
3. Listener writes COMMITMENT event:
   - Amount = qty * current_unit_price (resolved by VOUCHER_COMMITMENT_AMOUNT rule)
   - Projection: pre_committed -= amount, committed += amount
4. Some weeks later, farmer arrives at distribution point.
5. Extension officer redeems voucher.
6. RegBbVoucherRedemptionBinder dispatches BUDGET_EXPENSE_ON_REDEMPTION mm_action.
7. Listener writes EXPENSE event:
   - Amount = realised_qty * realised_unit_price (may differ from commitment due to price drift)
   - If different: also writes EXPENSE_ADJUSTMENT for the delta
   - Projection: committed -= committed_amount, expensed += realised_amount
8. Voucher chain complete; ledger fully reconciled for this application.
```

### 6.5 Reconciliation (quarterly)

A scheduled tool runs a reconciliation report:

```sql
-- Should always be true at any moment:
SELECT envelope_code,
  (allocated - reserved - pre_committed - committed - expensed) AS available,
  CASE WHEN allocated - reserved - pre_committed - committed - expensed < 0
       THEN 'BREACH' ELSE 'OK' END AS state
FROM budget_projection;

-- Cross-check 1: sum of committed events for active vouchers = sum of active voucher values
SELECT v.envelope, SUM(v.committed_amount) AS voucher_total,
       (SELECT COALESCE(SUM(amount),0) FROM app_fd_budget_event WHERE event_type='COMMITMENT'
        AND envelope_code = v.envelope) -
       (SELECT COALESCE(SUM(amount),0) FROM app_fd_budget_event WHERE event_type IN ('RELEASE_COMMITMENT','EXPENSE')
        AND envelope_code = v.envelope) AS ledger_total
FROM (SELECT envelope_code AS envelope, SUM(amount) AS committed_amount
      FROM app_fd_im_voucher WHERE status='issued'
      GROUP BY envelope_code) v;

-- Difference should be zero. If non-zero: investigate.
```

Mismatches are flagged for the budget admin to investigate; resolution may be a `RECONCILIATION_ADJUSTMENT` event with notes explaining the cause.

---

## 7. Deployment view

The Budget Engine is delivered as:

1. **A new OSGi bundle `regbb-budget-engine`** in `plugins/`. Estimated 1500–2500 LoC. Contents per §5.1.
2. **Form definitions** for `budget_envelope`, `budget_event` (storage; rendered in admin CRUDs for forensic browse), and the manual adjustment surface forms — JSON files under `app/forms/budget/`.
3. **Datalist definitions** for the dashboard, ledger view, reconciliation report, manual-adjust list — JSON files under `app/datalists/`.
4. **Userview menu entries** for the Budget category in the operator userview.
5. **Seed data**:
   - Initial `budget_envelope` rows for the four 2025 programmes.
   - `mm_action` rows for each lifecycle hook (BUDGET_RESERVE_ON_SUBMIT, BUDGET_PRE_COMMIT_ON_APPROVE, etc.).
   - `mm_determinant` rows for amount formulas (BENEFIT_AMOUNT_PRG_2025_001, etc.) and policy gates (programme_launch_gate, budget_overrun_policy, budget_authorisation, sla_decision).
6. **Schema** for the materialised view `budget_projection` and any indexes (e.g. `(envelope_code, dateCreated)` on `budget_event`).

Deployment sequence:

```
1. Build + upload the bundle JAR (Manage Plugins).
2. Push form definitions: budget_envelope, budget_event, manual_adjustment forms.
3. Trigger schema reconciliation: re-save each form via form-creator-api.
4. Create the materialised view + indexes (one-time migration script).
5. Push datalists: dashboard, ledger view, reconciliation.
6. Push userview update: add Budget category menus.
7. Seed envelopes, mm_action, mm_determinant rows for the four programmes.
```

---

## 8. Cross-cutting concepts

### 8.1 Concurrency

Budget event writes are append-only with no shared mutable state in the engine. Two operators approving simultaneously each issue an `mm_action` dispatch which produces an event each; both events land. The `budget_projection` reflects both.

The race is at the **decision-time check**: operator A and operator B both look at the projection at time T and see "M5,000 available"; both approve a M3,000 application; both succeed; the envelope is now M1,000 over. Resolution patterns:

- **Optimistic warning** (default): the projection at decision time is approximate; the `budget_overrun_policy` rule allows the approval; reconciliation surfaces the over-commitment. Most subsidy programmes have enough headroom that this is rare.
- **Pessimistic locking** (configurable per envelope, via the `budget_overrun_policy` rule): the rule reads the current projection at evaluation time AND issues a lock on the envelope row. Two simultaneous evaluations serialise; second one sees the first's commitment.

The default is optimistic warning — the cost of pessimistic locking (operator delays at decision time) outweighs the cost of occasional over-commitment, which is reconciled within the sprint.

### 8.2 Audit trail

Two audit surfaces:

- `reg_bb_eval_audit` — captures every `mm_determinant` evaluation, including budget rules (programme_launch_gate, budget_overrun_policy, budget_authorisation, sla_decision, BENEFIT_AMOUNT_*). Records which rule was evaluated, with what context, what outcome.
- `budget_event` — captures every state transition. Required notes on manual adjustments. `correlation_id` traces back to the originating application/voucher.

A finance auditor's forensic query path:
1. Find the envelope.
2. List all `budget_event` rows for the envelope, chronologically.
3. For each event, follow `correlation_id` back to the source row (application, voucher).
4. For each evaluation in the path, look up the matching `reg_bb_eval_audit` rows.

### 8.3 Currency handling

V1: single currency per envelope (LSL — Lesotho Loti). No FX. No multi-currency reporting.

The `currency` column is on `budget_envelope` and `budget_event`. A future multi-currency expansion would add an `exchange_rate_at_event` column and a `reporting_currency_amount` derived value. Out of scope for v1.

### 8.4 Fiscal year boundaries

Envelopes carry `fiscal_year`. At fiscal-year close, two procedures:

- **Close**: envelope status → `closed`. No new events allowed except `RELEASE_*` and reconciliation. Outstanding pre-commitments and commitments either roll forward (a new envelope for FY2627 takes them) or release back to allocated.
- **Roll-forward** (optional): a `BUDGET_REALLOCATION` event moves uncommitted balance from FY2526 envelope to FY2627 envelope.

This is an operations playbook, not engine code. The `mm_determinant` `budget_authorisation` rules govern who can perform the close.

### 8.5 Budget hint performance

The inline budget hint runs on every render of the operator decision tab. To avoid hitting `budget_projection` 30 times per page render:

- Joget caches custom column formatter output per render pass.
- The projection itself is a materialised view, refreshed on event write — a SELECT on it is one indexed read.
- Worst case: ~30 reads × ~5ms each = 150ms; fine.

If the projection grows to many envelopes (Phase 4+ if MAFSN runs many programmes), consider per-envelope cache TTLs.

### 8.6 No write-back to subsidy / IM

The Budget Engine does NOT update the application's status, the voucher's state, or anything else outside its own tables. It reflects what other modules do; it doesn't drive them. If the budget is exhausted, the engine's response is to make the next decision-time rule evaluation return FALSE — the operator is then unable to approve. The Engine does not "automatically reject pending applications" or "freeze a programme".

This is what keeps the engine small. It is a ledger and a projection. Action lives in the modules that call it.

---

## 9. Architecture decisions

| ADR / decision-log entry         | Title                                                                                                                                                       | Status                |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| **ADR-022** *(forthcoming)*       | Budget engine as separate module — storage + event ledger + CES; no policy attributes                                                                      | Pending — codifies §1 + §2.  |
| **ADR-023** *(forthcoming)*       | Commitment funnel state model — Reservation → Pre-commitment → Commitment → Expense                                                                        | Pending — codifies §4.1.    |
| **ADR-024** *(forthcoming)*       | `mm_action.kind=budget_event` integration pattern                                                                                                          | Pending — codifies §4.3.    |
| **ADR-025** *(forthcoming)*       | New `mm_determinant.scope` values for budget governance — `budget_amount`, `budget_tolerance`, `programme_launch_gate`, `budget_overrun_policy`, `budget_authorisation`, `sla_decision` | Pending — codifies §4.4–§4.7. |
| **ADR-026** *(forthcoming)*       | Rule-to-SQL compiler for fast-path determinants (enables CES to count efficiently against the registry)                                                     | Pending — codifies §5.4.   |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                                  | Source              | Stimulus                                                                | Artifact                       | Environment    | Response                                                                                                                                          | Measure                                          |
| ------------------------------------- | ------------------- | ----------------------------------------------------------------------- | ------------------------------ | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| Policy in rules                       | Operator-analyst    | Wants to change tolerance for PRG_2025_001 from 5% to 10%               | `mm_determinant: programme_launch_gate` | Phase 3       | Edits the rule's `ruleJson`; saves; next launch attempt uses the new rule                                                                         | No engine code change, no JAR deploy             |
| Funnel integrity                      | Auditor             | Compares sum of EXPENSE events with sum of redeemed vouchers' amounts    | reconciliation report          | Quarterly      | Numbers match within rounding error                                                                                                              | Discrepancy ≤ 0.1% of total expense              |
| CES honesty                           | Operator-analyst    | Designs a programme; CES output reports registry coverage = 0.62        | Programme Builder              | Phase 3        | Sees "estimated cost based on 62% coverage; raw count among complete records = N"                                                                | UI clearly shows coverage; analyst makes informed decision |
| Operator decision-time visibility     | Operator            | Opens an application for review                                          | Inline budget hint             | Phase 3        | Sees envelope state inline before clicking Approve                                                                                                | Hint renders within 100ms of page load          |
| Auditability                          | Finance auditor     | Asks "where did this M50,000 expense come from"                          | Ledger + audit                 | Production     | Follows `correlation_id` to voucher → application → reservation event; full chain shown                                                          | Question answered in ≤ 5 minutes                 |
| Sustainability                        | Engineering         | A new programme launches with new tolerance and new amount formula      | Engine + rules                 | Phase 3+       | Engine is unchanged; new mm_determinant rows + new mm_action rows; no JAR deploy                                                                  | 100% of programme launches do not require code   |

### 10.2 ISO/IEC 25010 mapping

| Goal                              | 25010 characteristic                                |
| --------------------------------- | --------------------------------------------------- |
| Policy in rules                   | Maintainability — Modifiability                     |
| Funnel integrity                  | Reliability — Faultlessness + Recoverability        |
| CES honesty                       | Functional suitability — Functional correctness      |
| Operator decision-time visibility | Interaction capability — User error protection      |
| Auditability                      | Reliability — Recoverability + Security — Accountability |
| Sustainability                    | Maintainability — Modifiability + Reusability      |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                                                              | Severity | Mitigation                                                                                                                                                                                                       |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BE-R1 | **Rule-to-SQL compiler is non-trivial.** The closed-twenty grammar must translate to Postgres `WHERE` clauses faithfully. Bugs in translation → silent CES errors. ADR-026 sets the scope; implementation needs care.                                              | High     | Comprehensive tests: a translation-correctness suite that runs each grammar production through both fast-path interpretation and SQL execution and asserts equivalence. Iterative fallback for any rule that fails translation. |
| BE-R2 | **Optimistic concurrency can over-commit.** Two operators approving simultaneously could both pass the overrun check, both succeed, envelope ends over.                                                                                                            | Medium   | Documented behaviour. Reconciliation surfaces over-commitment within the sprint. Pessimistic locking is a configurable alternative via the `budget_overrun_policy` rule (the rule itself can issue a lock).        |
| BE-R3 | **Budget event capture failure is silent for the operator.** If `BudgetEventListener` fails to write a budget event (DB issue, projection refresh failure), the operator's decision still succeeds. Operator's not informed. The funnel diverges silently.       | High     | Audit row of the failed listener; an alert mechanism that surfaces unprocessed budget events to ops. Consider a "pending budget events" backfill report for ops to monitor.                                       |
| BE-R4 | **CES iteration over a 200k-farmer registry is slow when rules use `$registry.*`.** SQL translation handles fast-path; iterative fallback is the bottleneck.                                                                                                       | Medium   | Phase 3+ optimisation: pre-computed eligibility view (refreshed nightly) for `$registry.*`-touching rules. Materialised view of `(farmer_nid, programme_code, eligible)` keyed by registry version.            |
| BE-R5 | **Currency assumption (single currency per envelope).** Future expansion to multi-currency (FX-rate handling, dual-currency reporting) is a real schema + UX change.                                                                                                | Low      | Documented out-of-scope for v1.                                                                                                                                                                                   |
| BE-R6 | **Manual adjustment surface is the primary fraud surface.** A budget admin with malicious intent could write `RECONCILIATION_ADJUSTMENT` events to mask over-commitment. The `budget_authorisation` rules + required notes + audit trail mitigate but don't prevent. | Medium   | Operational discipline: dual-control on adjustments above a threshold (a `mm_determinant: AUTH_ADJUSTMENT_LARGE` rule that requires two approvers). Review by external audit on a schedule.                       |
| BE-R7 | **Initial data migration.** Existing applications in `app_fd_subsidy_app_2025` (5 seed rows; later real ones) need backfill RESERVATION + (if approved) PRE_COMMITMENT events to reflect their state in the projection.                                              | Medium   | One-shot migration script during Phase 3 deployment. Documented as part of the deployment runbook.                                                                                                              |
| BE-R8 | **CES is best-effort, not contractually accurate.** The estimate at programme-design time may be off from realised cost (registry data shifts, prices change, applicant cohort differs from estimate). The launch gate trades off this uncertainty against budget control. | Low      | CES output explicitly labelled "estimate"; documented uncertainty bounds; no claim of accuracy. The system's behaviour after launch is governed by per-application overrun policy rules, not by the launch estimate. |

---

## 12. Glossary (engine-specific)

| Term                       | Definition                                                                                                                                                                                                       |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Budget envelope**        | A row in `budget_envelope` representing the allocated budget for one programme in one fiscal year. The unit of accounting in this engine.                                                                       |
| **Budget event**           | A row in `budget_event` representing one state transition. Append-only.                                                                                                                                          |
| **Funnel**                 | The four-stage pipeline: Reservation → Pre-commitment → Commitment → Expense, plus inverse releases.                                                                                                            |
| **Reservation**            | The first funnel stage. Soft hold on the envelope when the citizen submits the finalised application.                                                                                                            |
| **Pre-commitment**         | The second funnel stage. Hard obligation on the envelope when the operator approves.                                                                                                                              |
| **Commitment**             | The third funnel stage. Specific physical-claim earmark when the IM voucher is issued.                                                                                                                            |
| **Expense**                | The fourth funnel stage. Actual money out the door when the voucher is redeemed.                                                                                                                                  |
| **Cost Estimation Service (CES)** | The component-internal service that estimates programme cost at design time by counting eligible farmers and computing per-applicant amount.                                                                |
| **Programme launch gate**  | A `mm_determinant` rule (scope `programme_launch_gate`) evaluated when a programme transitions `draft → published`. CES output feeds the rule; the rule decides.                                                |
| **Budget overrun policy**  | A `mm_determinant` rule (scope `budget_overrun_policy`) evaluated at operator-decision time when an approval would breach the envelope. The rule decides whether to block or allow.                              |
| **Budget authorisation**   | A `mm_determinant` rule (scope `budget_authorisation`) evaluated at any budget operation (envelope create, reallocate, manual adjust). The rule decides who can do what.                                          |
| **Reconciliation**         | The periodic check that the ledger's `committed` and `expensed` totals match the source-of-truth in subsidy + IM tables. Discrepancies are investigated; corrections are events with `correction_of` references. |
