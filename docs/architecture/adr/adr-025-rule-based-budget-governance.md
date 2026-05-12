# ADR-025 — Rule-based budget governance — new `mm_determinant.scope` values

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-022 (budget engine boundary), ADR-024 (mm_action.kind=budget_event), `_design/policy-to-rules-migration.md`, `_design/architecture/components/budget-engine.md` §4.4–§4.7. |

## Context

The Budget Engine needs many policy decisions: what tolerance is acceptable when comparing cost estimate to allocated budget; whether a programme can launch given that comparison; whether an operator can approve an application that would breach the envelope; who can create or reallocate envelopes; what counts as SLA breach for operator review. An earlier draft put these as schema attributes (`tolerance_pct`, `overrun_policy`, `auto_release_days`) on `budget_envelope`, with code reading the attributes and branching.

Aare's directive (May 2026): **"if it's policy, it's a rule."** Every policy decision is an `mm_determinant` row, evaluated by the framework's `RoutingEvaluator`. No engine code reads policy attributes; no policy attributes exist on engine tables.

The principle generalises beyond the Budget Engine — see `_design/policy-to-rules-migration.md` for the broader cleanup. This ADR scopes specifically to the budget-related rule scopes.

## Decision

Six new `mm_determinant.scope` values, each addressing a specific class of policy decision:

| Scope | Decision the rule answers | Sample expression |
|---|---|---|
| `budget_amount` | What is the per-event amount? | `applied_programme == 'PRG_2025_001' ? 2000 : 0` (or per-programme rules; arithmetic via closed-twenty grammar) |
| `budget_tolerance` | Is the cost estimate within acceptable variance of the allocated budget? | `cost_estimate <= budget_allocated * 1.05` |
| `programme_launch_gate` | Can this programme transition `draft → published`? Composite of CES result + tolerance + completeness checks. | `cost_estimate_ok AND has_acceptance_window AND has_at_least_one_eligibility_rule` |
| `budget_overrun_policy` | At operator decision time, does this approval breach the envelope, and is that allowed? | `(remaining_after_pre_commit >= 0) OR user_roles in ['MOA_DIRECTOR']` |
| `budget_authorisation` | Can this user perform this budget operation (envelope create / reallocate / manual adjust)? | `user_roles in ['MOA_BUDGET_ADMIN', 'MOA_FINANCE_DIRECTOR']` |
| `sla_decision` | For SLA reporting / alerting: has this application breached the operator-review SLA for its programme? | `days_since_submitted > 5` (per programme; tighter for emergency-relief programmes) |

Each scope defines its `EvalContext.data` shape — what variables are available to the rule. The Budget Engine populates the context at evaluation time. For example:

- `programme_launch_gate` context: `{ cost_estimate, eligible_count, budget_allocated, registry_coverage_ratio, has_acceptance_window, has_eligibility_rules, user_roles }`.
- `budget_overrun_policy` context: `{ amount, allocated, reserved, pre_committed, committed, expensed, remaining_after_pre_commit, user_roles, applied_programme }`.

Rules in any of these scopes are authored by operator-analysts via the same admin CRUDs used for eligibility determinants. The framework's `RoutingEvaluator` evaluates them; the framework's `AuditWriter` logs each evaluation to `reg_bb_eval_audit`.

## Consequences

**Positive:**

- Authoring a new programme can change *every* policy by writing different rules. No engine code change needed for any policy variation.
- New programmes can have different tolerances, different SLAs, different overrun policies, different launch gates — all configuration.
- Auditability is uniform: every policy decision lands in `reg_bb_eval_audit` with rule source + outcome + context.
- The same scope mechanism handles future rules (e.g. an emergency-override rule that's only TRUE during declared crisis periods). New rules are mm_determinant rows, not new code paths.

**Negative:**

- More rules to author up front (each programme typically needs 3–6 budget-governance rules).
- A misauthored rule can silently misbehave: a `budget_overrun_policy` rule that always returns TRUE allows unlimited overrun. Mitigation: each scope ships a "default" rule that the engine references unless a programme-specific override exists; default rules are conservative.
- Operator-analysts authoring rules need to understand what context variables are available per scope. Mitigation: documentation per scope (the rule editor UI shows available variables).
- Performance: each operator decision evaluates 2–3 rules (`budget_overrun_policy`, `budget_authorisation`, possibly `programme_launch_gate` for re-publication). At ~3–5ms per fast-path eval, ≈ 10–15ms total — acceptable.

**Trade-off named:** Convention over Invention won here against the simpler "schema attributes for tolerance" alternative. Schema attributes would be 1 day of work; rule-based scopes are 1 week. But rule-based gets every future tolerance change, every per-programme override, every cross-cutting policy concern (like "programme X requires director sign-off above threshold Y") for free, with consistent audit. The cost of upfront investment buys long-term sustainability.

**Documents updated:** Budget Engine SAD §4.4–§4.7, §5.4, §10; convergence framework §9 (these are not Lesotho-instance extensions per se — they're use of existing `mm_determinant` infrastructure with new scope labels, so no audit re-opening required).
