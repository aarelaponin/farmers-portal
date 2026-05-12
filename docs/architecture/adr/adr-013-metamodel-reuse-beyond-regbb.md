# ADR-013 — Metamodel reuse beyond RegBB

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-012 (kernel as domain-agnostic); ADR-016 (IM does not use the RegBB framework); ADR-025 (rule-based budget governance); solution-level SAD §4.2. |

## Context

The `mm_*` metamodel was designed for RegBB: `mm_service` represents a citizen service offering, `mm_registration` represents a sub-programme of that service, `mm_determinant` represents an eligibility/applicability rule, `mm_action` represents a side effect on lifecycle events. Used together, these entities encode "a citizen registers for a public-sector service the state offers."

When the IM module's design started, the question came up: does IM also have services, registrations, determinants, actions? In the *citizen-services* sense — no. IM is operational logistics; it has supply chains, allocations, vouchers, distribution events. Not citizen services.

Two of the metamodel entities turn out to be **general-purpose**, not RegBB-specific:

- `mm_determinant` is a rule: a boolean predicate (or arithmetic expression) authored as a row, evaluated by `RoutingEvaluator`. Whether the rule is "is this applicant eligible for PRG_2025_001" or "is this voucher redemption authorised at this point" is just a label (the `scope` column).
- `mm_action` is a declarative side-effect trigger: when condition X holds, dispatch Y. Whether Y is a notification workflow, a budget event, or a NID auto-fill is just a kind label.

`mm_service`, `mm_registration` are RegBB-specific. They mean specific things in the citizen-services pattern.

## Decision

Three reuse patterns govern the metamodel:

1. **`mm_service` is a namespace bundle** for any module that wants its `mm_*` rows scoped together. RegBB interprets `mm_service` semantically (it represents a citizen service offering); IM uses it purely as a string that scopes its determinants and actions ("INPUTS_2025"). Future modules use it the same way. Solution-level SAD §4.2 names this distinction explicitly.

2. **`mm_determinant` is the general rule-management infrastructure**. The `scope` column distinguishes use cases: `applicability` (RegBB eligibility), `field` (kernel conditional UI), `im_voucher_redemption` (IM business rule), `budget_tolerance` (budget governance), `sla_decision` (operator SLA), `initial_status_assignment` (status policy), etc. Same evaluator, same audit, different scope label. Adding a new use case is a new scope value, not new code.

3. **`mm_action` is the general side-effect trigger**. The `kind` column distinguishes dispatcher targets: `status_change` (XPDL workflow), `bot_pull` (NID auto-fill), `notification` (SMS/email), `budget_event` (Budget Engine), `sla_alert` (deadline notification). Each kind has its own listener/dispatcher; new kinds are new listeners, not changes to existing ones.

`mm_registration`, `mm_required_doc`, `mm_benefit`, `mm_role`, `mm_role_screen` remain RegBB-specific and are **not** reused by IM or other non-citizen-service domains.

## Consequences

**Positive:**

- Two metamodel entities (the rule infrastructure and the side-effect dispatcher) generalise from one domain to many, with no cost.
- Future modules don't reinvent rule engines or dispatch mechanisms. The platform-level investment in `RoutingEvaluator` + `WorkflowDispatcher` pays back across every module.
- "If it's policy, it's a rule" (the broader principle) has a natural home: the rule lives as an `mm_determinant`, regardless of which module's policy it expresses.
- Audit consolidates: `reg_bb_eval_audit` captures every rule evaluation across every module's rules.

**Negative:**

- The `mm_determinant.scope` column accretes values over time. Without discipline, the scope namespace becomes noisy. Mitigation: each new scope is documented in the relevant component SAD; the kernel and framework SADs list canonical scopes.
- `mm_action.kind` namespace similarly grows. Same mitigation.
- The naming `mm_service` is misleading for IM (IM's `INPUTS_2025` is not a service in the public-services sense). Mitigation: documentation calls out the namespace-only usage explicitly. A future rename is possible but not worth the disruption.

**Trade-off named:** Reuse vs. clarity-of-naming. We chose reuse — adopting the RegBB-derived names for general-purpose use — because building a parallel `gm_*` (general metamodel) namespace would multiply the surface area. The cost is a slightly misleading name for IM use cases; the benefit is one less authoring story to learn. ADR-022 / ADR-023 / ADR-024 / ADR-025 codify how the Budget Engine uses the same pattern.

**Documents updated:** Solution-level SAD §4.2 (metamodel table + 3 reuse patterns); IM-module SAD §4.4; Budget Engine SAD §4.3; convergence-framework.md §9 (these new scope values are framework-internal, not Lesotho-instance extensions, so no audit re-opening required).
