# ADR-022 — Budget &amp; Commitment Engine as a separate module

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Supersedes | — |
| Superseded by | — |
| Related | ADR-023 (funnel state model), ADR-024 (mm_action.kind=budget_event integration), ADR-025 (rule-based budget governance), ADR-026 (rule-to-SQL compiler); component SAD `_design/architecture/components/budget-engine.md`; **methodology** `_design/architecture/components/budget-accounting-methodology.md` (D34, May 2026). |

## D34 amendment (May 2026) — implementation contract

The Budget Engine implements the public-sector fund-accounting methodology in `budget-accounting-methodology.md` faithfully. The methodology — chart of accounts, transaction catalogue, invariants, audit-trail requirements — is the contract for any code that posts to the engine. Divergence requires a methodology amendment first (a D-numbered entry in the decision log), not a silent code-side workaround. This applies retroactively: the storage schema and listener-logic prescriptions in this ADR's *Consequences* section are reframed as "the engine renders the methodology's accounts and transactions in PostgreSQL + Joget DAOs"; the methodology is the source of truth for what those accounts and transactions ARE.

## L3-1 1B amendment (May 2026) — single-bundle implementation

The original ADR called for a separate OSGi bundle `reg-bb-budget-engine`. In practice for Lesotho-MAFSN scale, the Budget Engine is always co-deployed with `reg-bb-engine` — the listener directly invokes `RoutingEvaluator` (already in `reg-bb-engine`) on every dispatch, and there is no scenario in which Budget Engine ships without the rest of the engine. Per the same Convention-over-Invention reasoning that collapsed slice-1A's four-bundle plan to one (ADR-002 r2), the Budget Engine implementation lives at `gs-plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/budget/` as a sub-package of the existing bundle.

The two principles in tension here:

- **SRP** — Budget concerns are "different" from determinant evaluation; should they live separately?
- **Convention over Invention + YAGNI** — At single-team / single-customer scale, an additional bundle costs cross-classloader complexity (CLAUDE.md "Cross-bundle reflection" trap), an extra build/deploy cycle, and an extra entry in Manage Plugins, with no offsetting benefit.

YAGNI wins because the bundles share a deployment unit, an audit destination, and a determinant-evaluator dependency. Sub-package separation gives us most of SRP's value (different folder, different entry points) without bundle-boundary tax.

If the Budget Engine ever needs to ship independently (e.g., another country adopts only the budget tracking, not the rest of RegBB), the sub-package can be lifted into its own bundle in a small refactor — but until that scenario is concrete, deferred.

## Context

The architecture as drafted through May 2026 had no first-class concern for programme budgets. Subsidy applications were evaluated for eligibility, decided by operators, and dispatched to workflows — but nothing in the system tracked: (a) whether a programme's expected cost aligned with its allocated budget, (b) how much budget remained after pending and approved applications were factored in, (c) the financial trail when vouchers were issued and redeemed. For a public-sector subsidy system this is not optional — most public budget rules require commitment accounting (IPSAS-aligned), and operators making approval decisions need decision-time visibility into remaining budget.

Two principles in tension here:

- **Convention over Invention** — use what already exists. The framework has `mm_action` dispatch + `mm_determinant` rule infrastructure + audit. Most of what budget tracking needs is already there.
- **Single Responsibility** — keep budget concerns separate from subsidy/IM lifecycle code. A subsidy storeBinder that also knows about budget envelopes is overloaded; budget invariants (append-only ledger, projection consistency) belong somewhere they can be enforced.

A third option — putting budget into the framework — was considered. Rejected because the framework is RegBB-specific (citizen-services-shaped), and budget concerns generalise beyond that. IM module is not RegBB-shaped but still emits budget events. Future modules might too.

## Decision

The Budget &amp; Commitment Engine is **a separate cross-cutting module**, parallel to the Reporting engine. It owns three tables (`budget_envelope`, `budget_event`, `budget_projection`), one OSGi bundle (`regbb-budget-engine`), and the Cost Estimation Service. Subsidy and IM modules emit lifecycle events through `mm_action.kind=budget_event` rows; the engine subscribes and writes ledger entries.

The engine is intentionally small: storage + event capture + projection + CES + operator UX surfaces. It does not contain policy. **Every policy decision** (tolerance thresholds, overrun policy, authorisation, SLA targets, programme launch gates, amount formulas) **is an `mm_determinant` rule** in one of the new scopes defined by ADR-025.

## Consequences

**Positive:**

- Budget concerns are isolated. Subsidy/IM lifecycle code knows about its own state and emits events; it does not import budget code.
- Adding new budget rules (new tolerance, new authorisation, new SLA) is a configuration change, not a code change.
- Future modules that need budget tracking integrate via `mm_action.kind=budget_event` without touching the engine.
- Auditors get a single forensic surface (`budget_event` ledger + `reg_bb_eval_audit` for rule decisions on top).
- Reconciliation is structured: ledger total per envelope = source-of-truth tally per module. Mismatches are surfaceable.

**Negative:**

- One more module to deploy and maintain. The bundle adds 1500–2500 LoC.
- Concurrency edge case: two operators approving simultaneously can over-commit. Documented; mitigated by reconciliation reports + optional pessimistic locking via the `budget_overrun_policy` rule.
- Initial data migration burden: existing applications need backfill RESERVATION + PRE_COMMITMENT events.

**Trade-off named:** Convention over Invention won where possible (reuse `mm_action`, `mm_determinant`, `RoutingEvaluator`, `AuditWriter`); SRP won on isolation of budget tables and the bundle boundary. The principle that pulled the other way is "keep adding stuff to reg-bb-engine bundle until split is forced" — rejected because budget concerns generalise beyond RegBB and the split is cheap to do at greenfield rather than retrofit.

**Documents updated:** `_design/architecture/solution-architecture.md` §4.1 (layering), §4.2 (metamodel), §1.2 (quality goal #6); `_design/architecture/components/budget-engine.md` (new); subsidy-module SAD §4.4-bis; IM-module SAD §4.4-bis; reporting-engine SAD §4.3-bis.
