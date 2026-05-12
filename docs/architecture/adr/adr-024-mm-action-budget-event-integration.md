# ADR-024 — `mm_action.kind=budget_event` integration pattern

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-022 (budget engine boundary), ADR-023 (funnel state model), `_design/architecture/components/budget-engine.md` §4.3 + §5.3; **methodology** `_design/architecture/components/budget-accounting-methodology.md` §3 (D34, May 2026). |

## D34 amendment (May 2026) — listener contract extends to source proration + sub-ledger maintenance

The `BudgetEventListener`'s contract on each `mm_action.kind=budget_event` dispatch is now (per the methodology):

1. Read `triggerJson.eventType` and `triggerJson.amountFormulaRule`.
2. Optional condition rule — skip if FALSE.
3. Resolve the envelope-level amount: `RoutingEvaluator.evaluate(amountFormulaRule, ctx)`.
4. **Resolve source proration**: read the envelope's `budget_envelope_source` rows and split the amount by allocated share, with banker's rounding and largest-source-rounds-last per methodology §6.3.
5. **Verify authority**: evaluate the appropriate `budget_authorisation` rule (per transaction type per methodology §3 + §7); refuse posting if FALSE.
6. **Verify invariants pre-post**: `AVAILABLE` won't go negative (or `budget_overrun_policy` rule authorises overrun); sub-ledger lifecycle ordering respected.
7. Write the journal entries: envelope-level + each source-contribution-level entry, all balanced.
8. **Maintain sub-ledgers**: open `BNF_<applicationId>` on PRE_COMMITMENT, open `VND_<vendorCode>` on COMMITMENT, close on RELEASE / fully-EXPENSED.
9. Refresh the `budget_projection` materialised view for the affected envelope.
10. Record audit trail per methodology §9 (actor, authority basis, rule version, idempotency key, etc.).

The original ADR-024 simplified flow (steps 1-3 + write event + refresh projection) is preserved as the *core* shape; steps 4-8 are the sub-ledger + multi-fund cross-cuts the methodology requires.

## Context

The Budget Engine needs to receive lifecycle events from the subsidy module (citizen submission, operator approval, rejection, withdrawal) and the IM module (voucher issuance, redemption, cancellation, expiry). The naive option is direct API calls from the subsidy/IM storeBinders into the Budget Engine — but this couples modules and means every new lifecycle hook is a code change.

The framework already has a generic dispatch mechanism: `mm_action` rows + `WorkflowDispatcher`. Today it dispatches to Joget XPDL workflows on `kind=status_change`, kind=`bot_pull` for NID auto-fill (planned), kind=`notification` for SMS dispatch (planned). Adding budget events as another `kind` reuses the dispatcher unchanged.

## Decision

A new `mm_action.kind=budget_event`. The Budget Engine registers a listener that subscribes to dispatches of this kind. Each `mm_action` row's `triggerJson` carries:

- `onStatus` (or `onVoucherStatus` for IM) — the lifecycle state that triggers the event.
- `eventType` — one of the funnel event types from ADR-023.
- `amountFormulaRule` — the `mm_determinant.code` whose evaluation produces the event amount (a number).
- Optional `conditionRule` — additional gate; if FALSE, no event is written.

Sample wiring:

```yaml
mm_action:
  - code: BUDGET_RESERVE_ON_SUBMIT
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": ["pending_operator_review", "auto_approved"],
        "eventType": "RESERVATION",
        "amountFormulaRule": "BENEFIT_AMOUNT_BY_PROGRAMME" }
```

Subsidy module emits via the existing `WorkflowDispatcher.dispatch(...)` call in `RegBbApplicationStoreBinder`; the dispatcher fires every matching `mm_action` regardless of kind. The XPDL-dispatch path and the budget-event path coexist — both are matched, both fire.

## Consequences

**Positive:**

- Subsidy and IM do not import Budget Engine code. Events flow through configuration (mm_action rows), not Java imports.
- Adding new lifecycle hooks is configuration: write an mm_action row, the engine starts seeing the event. No subsidy/IM code change.
- Same audit + observability surface as XPDL dispatch — every fired event is visible in the framework's audit trail (`reg_bb_eval_audit` for the rule evaluation, plus the `budget_event` row itself).
- Failure isolation: if budget event write fails, the operator's decision is still committed (per the framework's never-null discipline in ADR-007).

**Negative:**

- The dispatcher fires events synchronously. A slow budget event listener could affect operator response time. Mitigation: keep the listener fast (single SQL insert + projection refresh ≈ < 50ms) and run the projection refresh out of band if it grows.
- An mm_action row that's misconfigured (wrong eventType, dangling amountFormulaRule reference) silently skips the event. Mitigation: a publish-time validator checks that every referenced rule exists.
- Two different kinds of dispatcher consumers (XPDL workflows, budget event listener) need to coexist cleanly. The dispatcher's design already accommodates multiple consumers per dispatch — no new mechanism, just one more listener.

**Trade-off named:** Direct API call (tight coupling, simpler to trace) vs. event dispatch (loose coupling, configuration-driven). We chose dispatch because it preserves the architectural separation: subsidy and IM modules don't need to know that the Budget Engine exists, only that lifecycle transitions emit events. The cost is one extra hop in the call chain at decision time; the benefit is that future modules can subscribe to the same events without any subsidy/IM modification.

**Documents updated:** Budget Engine SAD §4.3, §5.3, §6.2–§6.4; subsidy module SAD §4.4-bis; IM module SAD §4.4-bis.
