# Policy-to-Rules Migration Plan

| | |
|---|---|
| Status | Plan — DRAFT |
| Date | 2026-05-02 |
| Author | Aare Laponin with engineering team |
| Related | Solution-level SAD §4.2; ADR-025 (rule-based budget governance); component SADs across `docs/architecture/architecture/components/`. |
| Scope | Every module *except* the farmer + parcel registries (those stay native Joget per the May 2026 architectural decision). |
| Excluded | Farmer registry, parcel registry, anything technical/mechanical (cache TTLs, parser internals, OSGi plumbing). |

## 1. The principle

> **If it's policy, it's a rule.**

Anything that's a *policy decision* — a choice that could reasonably differ between programmes, or be tightened/loosened over time, or require different actors to authorise — lives as an `mm_determinant` row in the appropriate scope. Engine code reads the rule's outcome; engine code does not encode the policy.

Mechanical / technical decisions (fast-vs-SQL routing, parser AST structure, file upload temp-path lifecycle, cache TTL, OSGi service registration order) are NOT policy. They stay in code.

The discipline is already applied (or being applied) to:

- **Eligibility evaluation** — `mm_determinant` scope `applicability` (D6, ADR-001r2).
- **Conditional UI** — `mm_field.visibilityDeterminantId`, `requirednessDeterminantId` (§6.4 Conditional UI).
- **Workflow dispatch triggers** — `mm_action` rows (Phase 2-b).
- **IM business rules** — `mm_determinant` scopes `im_voucher_redemption`, `im_stock_alert`, `im_voucher_issuance` (planned).
- **Budget governance** — `mm_determinant` scopes `budget_amount`, `budget_tolerance`, `programme_launch_gate`, `budget_overrun_policy`, `budget_authorisation`, `sla_decision` (ADR-025, planned).

This document inventories the remaining hardcoded-policy spots across the architecture and plans the cleanup.

## 2. Inventory of remaining hardcoded policy

### 2.1 RegBB framework

| Code location | Current behaviour | What it actually is | Cleanup |
|---|---|---|---|
| `RegBbApplicationStoreBinder.statusForDisposition()` | Hardcoded mapping: `eligibility_passed → auto_approved`; `eligibility_failed_mandatory → auto_rejected`; `eligibility_pending_review → pending_operator_review`; `indeterminate → pending_data_clarification`. | **Policy.** Different programmes might want different initial-status assignment. An emergency-relief programme might never auto-approve (require human review even on full eligibility pass). A pilot programme might never auto-reject (always send to operator with the failure reason). | New scope `initial_status_assignment`. Rule per programme: `EvalContext.data = { disposition, applied_programme }`; rule returns the target status name. Default rule preserves current mapping. |
| `RegBbOperatorDecisionBinder.statusForDecision()` | Hardcoded: `approve → approved`; `reject → rejected`; `send_back → sent_back`. | **Policy.** Different services might allow different decisions or different status semantics. An appeal flow might add `escalate_to_supervisor` or `request_evidence`. | New scope `decision_to_status`. Rule per service: `EvalContext.data = { decision_value }`; returns target status. |
| Operator decision form's allowed values | The `decision` field is a Radio with options `approve / reject / send_back`. Hardcoded in the form definition and the binder's switch. | **Policy.** Programme-specific. | Two-part: (a) `mm_field.optionsCatalogId` already drives the radio's options — but the catalog `DECISION` is service-wide. Phase 2: per-service decision catalogs, plus a `decision_authorisation` rule that filters which decisions a particular operator can pick (a director might see a fourth option `override_eligibility` that the regular operator doesn't). |
| `RegBbApplicationStoreBinder` aggregation strategies | `all_must_pass` and `score_based` are hardcoded code paths. `mm_registration.evaluationStrategy` selects between them. | **Borderline.** The shape (which strategies exist) is policy; the implementation is mechanical. Adding a third strategy (`weighted_scoring`, `composite_with_veto`) is currently a code change. | Future scope `aggregation_logic` could express the strategy as a rule. Lower priority; `score_based` covers most current programmes. Defer to a future phase if a third strategy becomes needed. |
| Audit retention TTL | Documented in CLAUDE.md as a quarterly archival ritual; no code enforces it. | **Policy.** Different services might want different retention (e.g. drought relief — keep forever for transparency). | Future scope `audit_retention_days` per service. Document but defer. The current ritual works; rule-driven retention is overhead until multiple services have different needs. |

### 2.2 Subsidy module (configuration; mostly already rules)

| Configuration spot | Current state | Cleanup |
|---|---|---|
| Eligibility rules | Already in `mm_determinant`. | Done. |
| Required-doc filter logic ("programme-specific docs only when applied_programme matches the doc's registrationId") | Hardcoded match in `MetaScreenElement.synthesiseDocumentChildren`. | **Mechanical, not policy.** The policy IS the `mm_required_doc.registrationId` value; the matching is mechanical. Stays in code. |
| Initial status assignment per disposition | Inherited from framework (see above). | Migrates with framework. |
| Wizard tab order | Hardcoded by `mm_screen.orderIndex`. | **Configuration, not policy.** Stays as `orderIndex`. |
| Citizen catalogue page (Phase 1 close-out) | Each programme's applicability is evaluated by its `applicabilityDeterminantId` rule. | Already rule-driven. |

### 2.3 IM module (forward-looking; mostly already rules)

| Concern | Current plan | Cleanup |
|---|---|---|
| Voucher redemption constraints (e.g. "at allocated point only") | Already planned as `mm_determinant` scope `im_voucher_redemption`. | Already rule-driven (in design). |
| Stock alert thresholds | Already planned as `mm_determinant` scope `im_stock_alert`. | Already rule-driven (in design). |
| Voucher state transitions (issued → redeemed → reconciled) — who can do what | Currently sketched as Joget's `joget-status-framework` transition map + database FKs. | New scope `voucher_transition_authorisation`. Rule context: `{ from_state, to_state, user_roles, voucher_amount }`. The status-framework still enforces the mechanical transitions; the rule enforces authorisation policy. |
| Input pricing policy (current vs. issuance vs. redemption price) | `mm_determinant.scope=budget_amount` rules name the policy per programme. | Already rule-driven (in design via Budget Engine integration). |
| Distribution event validation rules | Planned as `mm_determinant` scope `im_distribution_validation`. | Already rule-driven (in design). |

### 2.4 Reporting engine

| Concern | Current state | Cleanup |
|---|---|---|
| Access control (who can see which report) | Today: Joget user/role membership on the userview page. | **Borderline.** Joget user/role for authentication stays. Authorisation policy on top — "operator role can see PII; policy lead role sees only district aggregates" — should be expressible as `mm_determinant.scope=report_access` rules evaluated when the report is opened. Defer until cross-role reports actually exist (today operator and policy reports are different datalists; the access-control answer is implicit). |
| What columns are sortable/filterable | Configuration in datalist JSON. | **Configuration, not policy.** Stays in JSON. |
| Default sort order | Configuration in datalist JSON. | **Configuration.** Stays in JSON. |
| Aggregation choices in pivot reports | Configuration in JSON SQL. | **Configuration.** Stays in JSON. |

### 2.5 Budget Engine

All budget governance is rule-driven by design (per ADR-025). No legacy hardcoded policy to migrate; the engine is built rule-driven from the start.

### 2.6 Kernel (mm-form-gen)

| Concern | Current state | Cleanup |
|---|---|---|
| Widget pass-through dispatch (the switch statement on `mm_field.widget`) | Hardcoded switch. | **Mechanical, not policy.** Each widget kind maps to one Joget element class. Adding a new widget kind is a code change because the mapping is a code-level fact. Stays as a switch (or evolves to a registry per ADR-015 — that's a code-architecture concern, not policy). |
| `getDynamicFieldNames` field-name set | Computed from `mm_screen` + `mm_field` + `mm_required_doc`. | **Configuration, not policy.** Stays. |
| Conditional UI fail-open default (`null/error → field stays visible`) | Hardcoded. | **Borderline.** Could be a rule (`conditional_ui_default_visibility`). But the cost (every kernel render evaluates one more rule) outweighs the benefit (programmes never want to fail-closed by default; if they did, a programme-level override could be added later). Defer indefinitely. |

### 2.7 Farmer + parcel registries — out of scope

Per the May 2026 architectural decision, these stay native Joget. No policy-to-rules migration is planned. The integration surface this architecture uses (read-only column reads via `$registry.*`) does not require policy in the registries.

## 3. Cleanup priorities

### 3.1 High priority — sustainable, real impact

1. **`statusForDisposition` → `initial_status_assignment` rule.** Every programme launch in the next year would benefit from this; programmes are starting to want different initial-status semantics. Effort: ~half-day (one new scope, default rule shipped, framework binder reads rule instead of switch).

2. **`statusForDecision` → `decision_to_status` rule.** Same shape. Half-day.

### 3.2 Medium priority — useful but not blocking

3. **Decision authorisation rule.** Allows roles like supervisor / director to see expanded decision options. Half-day; ties into Phase 2 MetaReviewElement work.

4. **IM voucher transition authorisation rule.** Pairs with status-framework. Sketch in IM-module SAD already; lands when IM module is built (Phase 3).

### 3.3 Low priority — defer

5. **Aggregation logic as rule** (third+ strategies beyond `all_must_pass` + `score_based`).
6. **Audit retention TTL as per-service rule.**
7. **Conditional UI default visibility as rule.**
8. **Report access control as rule.**

These can be added when there's evidence of a need — multiple services with diverging policies is the trigger.

## 4. Implementation sequencing

The first two cleanups (high-priority) can ship as a single PR:

- Add scopes `initial_status_assignment` and `decision_to_status` to mm_determinant (both already supported by the form — just new scope label values).
- Ship default rules in the seed fixture: `DEFAULT_STATUS_ASSIGNMENT` (mirrors current behaviour), `DEFAULT_DECISION_TO_STATUS` (same).
- Refactor `RegBbApplicationStoreBinder.statusForDisposition` to call `RoutingEvaluator` against `initial_status_assignment` scope rules. If no rule matches the programme, fall back to default rule. If default not found, hardcoded fallback (defensive).
- Refactor `RegBbOperatorDecisionBinder.statusForDecision` similarly.
- Bump `reg-bb-engine` build number; redeploy.

Estimated effort: 1 day for both refactors + tests + seed updates. Backward-compatible: existing applications continue to work; new programmes can override.

The medium-priority items follow as Phase 2 / Phase 3 work lands.

## 5. The ADR backlog this triggers

| ADR | Title |
|---|---|
| ADR-027 *(forthcoming)* | `initial_status_assignment` scope — initial status from disposition is rule-driven |
| ADR-028 *(forthcoming)* | `decision_to_status` scope — operator decision → status mapping is rule-driven |
| ADR-029 *(forthcoming)* | `decision_authorisation` scope — operator role determines available decisions |
| ADR-030 *(forthcoming)* | `voucher_transition_authorisation` scope (IM) |

ADRs 027 + 028 ship with the high-priority refactor. ADRs 029 + 030 ship when the dependent work (Phase 2 MetaReviewElement, Phase 3 IM module) lands.

## 6. Decision-log entries

| D# | Decision |
|---|---|
| D25 *(forthcoming)* | Budget &amp; Commitment Engine as a separate module (per ADR-022) |
| D26 *(forthcoming)* | Commitment funnel state model (per ADR-023) |
| D27 *(forthcoming)* | `mm_action.kind=budget_event` integration pattern (per ADR-024) |
| D28 *(forthcoming)* | Rule-based budget governance scopes (per ADR-025) |
| D29 *(forthcoming)* | Rule-to-SQL compiler for fast-path determinants (per ADR-026) |
| D30 *(forthcoming)* | "If it's policy, it's a rule" applied universally — initial-status + decision-to-status are first cleanup |
| D31 *(forthcoming)* | Farmer + parcel registries are explicitly excluded from policy-to-rules migration (they stay native Joget) |

These will be added to `docs/architecture/decision-log.md` when the corresponding ADRs are accepted.

## 7. Standing principle

When a future PR introduces a new code path that branches on a policy decision (a threshold, an authorisation, a behavioural variant per programme), the reviewer should ask: **"Is this a rule?"** If yes, it goes in `mm_determinant`, not in code. If no (it's mechanical), document why in the PR description. This is how the discipline stays applied as the system grows.
