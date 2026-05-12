# ADR-028 — `decision_to_status` scope — operator decision → status mapping is rule-driven

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-027 (sibling — initial status assignment), `_design/policy-to-rules-migration.md` §3.1, `_design/architecture/components/reg-bb-framework.md` §5.5. |

## Context

`RegBbOperatorDecisionBinder.statusForDecision` hardcodes the operator decision → status mapping:

```java
"approve"   → "approved"
"reject"    → "rejected"
"send_back" → "sent_back"
```

This is policy. Different services or programmes might want different decisions or different status semantics — an appeal flow might add `escalate_to_supervisor`; a multi-step approval might add `conditional_approve`; a programme with mandatory dual-control might add `pending_supervisor_confirm`.

Per the policy-to-rules migration plan, this is the second highest-impact migration item, paired with ADR-027.

## Decision

A new `mm_determinant.scope = decision_to_status`. Rules in this scope each map *one decision value* to *one target status*. Same pattern as ADR-027: the binder iterates rules in priority order (programme-specific → service-wide → global), the first rule that evaluates TRUE provides the status. Hardcoded mapping remains as a defensive fallback.

The `targetValue` column added by ADR-027 is reused for this scope.

Sample default rules:

```yaml
mm_determinant:
  - code: DECISION_DEFAULT_APPROVE
    scope: decision_to_status
    ruleType: assignment
    ruleJson: decision_value == 'approve'
    targetValue: approved

  - code: DECISION_DEFAULT_REJECT
    scope: decision_to_status
    ruleJson: decision_value == 'reject'
    targetValue: rejected

  - code: DECISION_DEFAULT_SEND_BACK
    scope: decision_to_status
    ruleJson: decision_value == 'send_back'
    targetValue: sent_back
```

A service-specific override (e.g. for an appeals service that adds `escalate`):

```yaml
mm_determinant:
  - code: DECISION_APPEALS_ESCALATE
    scope: decision_to_status
    serviceId: APPEALS_2025
    ruleType: assignment
    ruleJson: decision_value == 'escalate'
    targetValue: pending_supervisor_review
```

Resolution order matches ADR-027: programme-specific first, service-wide next, global last.

## Consequences

**Positive:**

- New decision values can be added per service without code changes — write the catalog entry (mm_catalog), write the decision-to-status rule, the binder picks it up.
- Audit captures every decision-to-status evaluation; forensic answer to "why did this approve land in pending_supervisor_review instead of approved" lives in the audit table.

**Negative:**

- Same caveats as ADR-027: misauthored rules silently change behaviour; mitigation is the targetValue validator.
- Decision values are still constrained by the `mm_catalog: DECISION` options the form's Radio renders. Adding a new decision value requires updating the catalog AND adding a corresponding decision-to-status rule. Documented as an authoring story.

## Compliance

- Backwards-compatible: hardcoded fallback preserved.
- Defaults shipped in seed.
- Single resolver shared between binder and any REST endpoint.

**Documents updated:** Same as ADR-027 plus `RegBbOperatorDecisionBinder.statusForDecision` refactor.
