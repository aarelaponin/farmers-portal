# ADR-027 — `initial_status_assignment` scope — initial status from disposition is rule-driven

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-013 (metamodel reuse), ADR-025 (rule-based budget governance), `_design/policy-to-rules-migration.md` §3.1, `_design/architecture/components/reg-bb-framework.md` §5.4. |

## Context

`RegBbApplicationStoreBinder.statusForDisposition` maps the eligibility disposition to the application's initial status:

```java
case "eligibility_passed"           → "auto_approved"
case "eligibility_failed_mandatory" → "auto_rejected"
case "eligibility_pending_review"   → "pending_operator_review"
case "indeterminate"                → "pending_data_clarification"
```

This is hardcoded across two locations (the binder + `RegBbEvalApi.statusForDisposition`) and applied uniformly to every programme. But the mapping is **policy**: an emergency-relief programme might never auto-approve (always require human review on full eligibility pass); a pilot programme might never auto-reject (always send to operator with the failure reason). Different programmes deserve different policies.

Per the policy-to-rules migration plan, this is the highest-impact migration item.

## Decision

A new `mm_determinant.scope = initial_status_assignment`. Rules in this scope each map *one disposition outcome* to *one target status*. The binder iterates the matching rules in priority order; the first rule that evaluates TRUE provides the status. Hardcoded mapping remains as a defensive fallback if no rules are configured for a programme.

`mm_determinant` gains one optional column: `targetValue`. For `scope=initial_status_assignment` rules, `targetValue` carries the status name to assign when the rule's expression evaluates TRUE.

Sample default rules (shipped in seed fixture, programme-agnostic):

```yaml
mm_determinant:
  - code: INIT_STATUS_DEFAULT_AUTO_APPROVED
    scope: initial_status_assignment
    serviceId: ""                     # service-wide default
    registrationId: ""                # programme-wide default
    ruleType: assignment
    ruleJson: disposition == 'eligibility_passed'
    targetValue: auto_approved

  - code: INIT_STATUS_DEFAULT_AUTO_REJECTED
    scope: initial_status_assignment
    ruleJson: disposition == 'eligibility_failed_mandatory'
    targetValue: auto_rejected

  - code: INIT_STATUS_DEFAULT_PENDING_REVIEW
    scope: initial_status_assignment
    ruleJson: disposition == 'eligibility_pending_review'
    targetValue: pending_operator_review

  - code: INIT_STATUS_DEFAULT_PENDING_CLARIFICATION
    scope: initial_status_assignment
    ruleJson: disposition == 'indeterminate'
    targetValue: pending_data_clarification
```

A programme-specific override (e.g. PRG_2025_004 — drought emergency relief — never auto-approve):

```yaml
mm_determinant:
  - code: INIT_STATUS_PRG_2025_004_HUMAN_REVIEW_ALWAYS
    scope: initial_status_assignment
    serviceId: SUBSIDY_2025
    registrationId: PRG_2025_004
    ruleType: assignment
    ruleJson: disposition == 'eligibility_passed' OR disposition == 'eligibility_pending_review'
    targetValue: pending_operator_review
```

The binder's resolution order: programme-specific rules first (matched by `registrationId`), then service-wide (`serviceId` set, `registrationId` blank), then global (both blank). First TRUE wins.

## Consequences

**Positive:**

- Per-programme initial-status policy without code changes.
- New programmes that need different semantics author their own rules.
- Audit captures every status assignment evaluation (via the framework's `AuditWriter`); the forensic question "why did this application start in pending_operator_review instead of auto_approved" is answerable.
- The hardcoded fallback ensures backwards compatibility — existing applications continue to work even if the seed rules are missing.

**Negative:**

- One new column on `mm_determinant` (`targetValue`). Adds a small surface to the form definition; existing rules that don't use it leave it blank.
- Two new evaluation calls per application save (one for each disposition match attempt). Acceptable: ~3-5ms overhead, negligible compared to the eligibility evaluation already running.
- A misauthored rule (e.g. a programme override that's always TRUE) silently changes status assignment. Mitigation: a `targetValue` validator that ensures the value is one of the known status enum values.

## Compliance

- Backwards-compatible: when no rules in the scope exist, the hardcoded default applies.
- Default rules ship in the seed fixture; programmes that don't override get the same behaviour as today.
- `RegBbEvalApi.submit` uses the same resolver as the storeBinder — single source of truth.

**Documents updated:** `_design/policy-to-rules-migration.md` §4 (item ships); decision-log D-entry forthcoming. Code: `_forms/mm/mm-determinant.json` (new column); `_seeds/lesotho-mm-fixture.yaml` (default rules); `RegBbApplicationStoreBinder.statusForDisposition` and `RegBbEvalApi.statusForDisposition` refactored to call new resolver.
