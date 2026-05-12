# ADR-016 — IM uses the MM-form-gen kernel only, not the RegBB framework

| | |
|---|---|
| Status | Accepted |
| Date | 2026-05-06 |
| Decider | Aare Laponin |
| Related | ADR-012 (kernel as domain-agnostic); ADR-013 (metamodel reuse beyond RegBB); ADR-017 (mm_service as namespace-only for IM); im-module SAD §1, §4.1, §9; migration-plan.md revision 3 §0; subsidy-to-im-backlog.md gate. |

## Context

The platform is composed in three layers:

1. **MM-form-gen kernel** — domain-agnostic. Reads `mm_screen` + `mm_field` rows and synthesises a Joget form at render time. Knows nothing about citizens, registrations, eligibility, applications. ADR-012 codifies this.
2. **RegBB framework** — citizen-services orchestration. Implements the GovStack RegBB spec on top of the kernel: `mm_registration` (a sub-programme of an `mm_service`), `mm_required_doc`, `mm_benefit`, application status lifecycle, eligibility/applicability evaluation through `RoutingEvaluator`, operator decision binders, single-window catalogue page.
3. **Subsidy module** — concrete content. Four 2025 programme registrations + their determinants + their actions, all expressed as `mm_*` rows the framework interprets.

When Phase 3 IM was scoped, the question was whether IM should sit on top of RegBB (as the Subsidy module does), bypass it (and use the kernel directly), or live somewhere else entirely.

IM is **operational logistics**, not a citizen service:

* Operators (procurement officer, district coordinator, extension officer, supplier) use it to manage stock and dispatch vouchers.
* Farmers experience IM as a downstream consequence of having been approved on a subsidy application — they don't "register for IM."
* The transactional lifecycle (issue → redeem → reconcile) is not the application/eligibility/decision shape RegBB encodes.

If IM sat on top of RegBB, it would carry conceptual weight that doesn't apply: an `mm_registration` for "voucher redemption" is a category error.

But IM does benefit from:

* Stable native-Joget forms for catalogue + supplier + inventory + voucher etc. (most IM forms have stable shape, no per-context variation).
* A few kernel-shaped forms for surfaces whose fields vary by configuration (allocation-line by programme; voucher redemption with programme-specific cells).
* Rule infrastructure (`mm_determinant` + `RoutingEvaluator`) for IM business rules — voucher redemption gates, stock alert thresholds, allocation roster filters.
* Side-effect dispatch (`mm_action` + `WorkflowDispatcher`) for IM workflow triggers — voucher-issued SMS, stock-alert email, redemption-reconciled notification.

ADR-013 already established that `mm_determinant` and `mm_action` are general-purpose, not RegBB-specific. So IM can reuse the rule and dispatch infrastructure without inheriting the citizen-services framing.

## Decision

**IM uses the MM-form-gen kernel only, not the RegBB framework.** Within that boundary IM also reuses two metamodel entities — `mm_determinant` for business rules and `mm_action` for workflow triggers — per ADR-013. IM does **not** use:

* `mm_registration` — there is no sub-programme-of-a-service shape for IM.
* `mm_required_doc` — no citizen-supplied documents on the IM path.
* `mm_benefit` — citizen benefits are owned by the subsidy programme; IM is the supply-chain side that delivers them.
* `mm_role` / `mm_role_screen` — IM operator UX is role-managed natively in Joget (group memberships, userview permissions), not through the framework's role-screen masking.
* `RegBbApplicationStoreBinder`, `RegBbOperatorDecisionBinder`, `RegBbEvalApi`, `MetaWizardElement(readonly=Y)` workaround, or any RegBB-specific Java code.

Cross-module integration (subsidy approval → IM entitlement issuance) flows through `mm_action` (subsidy-side `ISSUE_IM_ENTITLEMENT`) dispatching a sysadmin-authored XPDL workflow that writes the `imEntitlement` row. The XPDL workflow consumes the application's row data and IM consumes the `imEntitlement` row downstream — neither module touches the other's code.

## Alternatives considered

1. **IM on top of RegBB.** Rejected: forces IM into a citizen-services shape it doesn't fit. `mm_registration` for "voucher redemption" is a category error; `mm_required_doc` for IM has no analogue; the RegBB application status lifecycle (submitted/approved/rejected) doesn't map to the voucher lifecycle (issued/redeemed/reconciled).

2. **IM as a fully independent stack — own kernel, own metamodel, own rule engine.** Rejected: reinvents `RoutingEvaluator`, `WorkflowDispatcher`, the audit table, the rule-authoring UX. Multiplies platform surface area. The two general-purpose metamodel entities (`mm_determinant`, `mm_action`) cover IM's needs without RegBB's citizen-services trappings.

3. **IM as a thin extension of RegBB — reuse mostly, ignore parts that don't fit.** Rejected: produces a partly-applied framework that's harder to reason about than a clean boundary. Future maintainers have to learn "RegBB except for these five things in IM." A clean boundary ("IM = kernel + two metamodel entities") is simpler to teach and maintain.

## Consequences

**Positive:**

* IM authoring is conceptually simpler than subsidy authoring. No `mm_registration` / `mm_required_doc` / `mm_benefit` to populate per IM artefact. Operator-analyst onboarding is shorter.
* IM forms get the kernel's metadata-driven advantages where shape varies by configuration (allocation-line per programme), and stay native-Joget where shape is stable (supplier registry, input catalogue). Quality goal IM-Q2 (zero non-native widgets) is satisfied by construction.
* Cross-module boundary is clean: subsidy fires `mm_action`, IM consumes the resulting `imEntitlement` row. No code reach-through.
* `mm_determinant` and `mm_action` reuse pays dividends — one rule editor, one dispatcher, one audit table across both modules.

**Negative:**

* IM-specific business logic that doesn't fit a `mm_determinant` rule or a `mm_action` dispatch (e.g. complex stock-balance arithmetic, multi-row inventory adjustments, voucher chain-integrity checks across statuses) lands as Java code in the future `im-runtime` bundle. That bundle exists outside the kernel/framework partition — it's IM-specific. Trade-off vs. inventing more general extension points: prefer the small bundle.
* The "IM does not use RegBB" rule must be enforced in code review. It's tempting for someone to reach into `RegBbApplicationStoreBinder` from an IM voucher binder. The structural separation (different bundle, different package namespace, no Maven dependency) is the enforcement.

**Trade-off named:** Reuse vs. boundary clarity. ADR-013 chose to reuse two metamodel entities across modules; this ADR chose to *not* reuse the rest of the framework. The combination — share the parts that generalise (rule + dispatch), separate the parts that don't (citizen-services orchestration) — is what produces a useful platform for both citizen-services and operational-logistics modules.

**Documents updated:** im-module SAD §4.1 + §9; subsidy-to-im-backlog.md gate; migration-plan.md revision 3 §0 ("IM module added as Phase 3 — uses MM-form-gen kernel only, NOT the RegBB framework"); decision-log.md (D-number TBD on next batch).
