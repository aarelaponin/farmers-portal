# Architectural landscape — Farmers Portal × GovStack BB suite

**Status:** Phase 1 of a two-phase GovStack alignment review. This document is the descriptive comparison. Phase 2 (gap analysis) builds on it and lands as a separate document.
**Last updated:** 2026-05-12.
**Audience:** Architecture lead, MAFSN ICT, GovStack reviewers.
**Authoritative inputs:** `docs/architecture/convergence-framework.md` (r2 2026-05-07), `docs/architecture/regbb-conformance-checklist.md` (2026-04-27, stale on §10), `docs/architecture/architecture-overview.md` (2026-04-28, stale on notifications/lifecycle/budget). External GovStack canon at `specs.govstack.global` (GovStack 2.0.0 core spec) referenced where relevant but not re-derived here — the three project docs are taken as the canonical GovStack interpretation.
**Scope of comparison:** Six GovStack BBs in scope — RegBB, Information Mediator, Messaging (Notification), Payments, Identity, Consent. Plus the cross-functional architecture document. The GovStack catalogue has nine foundational BBs; Cloud Infrastructure, Digital Registries, Scheduler, Workflow, Content Management System, E-Marketplace, E-Signature, GIS, and Wallet are out of scope for this review (some, like Workflow, are implicit in the platform; others, like Wallet, don't touch the subsidy lifecycle).

---

## §1 — GovStack BB suite reference

GovStack 2.0.0 defines its nine foundational BBs as composable, interoperable software modules exposing REST APIs and following nine architecture principles (openness, modularity, interoperability, sustainability, security, data ownership, user-centered design, API-first access, reusability). The Information Mediator is the canonical cross-BB bus — when one BB needs data another BB owns, the call goes through Information Mediator, not as a point-to-point integration.

### §1.1 The six BBs in scope and what they own

| BB | Canonical responsibility |
|---|---|
| **Registration (RegBB)** | The lifecycle of a citizen-submitted application: service catalogue, identity verification, application submission, document submission, eligibility evaluation, decision management, audit. Decomposed into seven services (per architecture-overview §3.1) plus three cross-cutting concerns (workflow, notifications, payments). RegBB is the BB the Farmers Portal primarily implements. |
| **Information Mediator** | Secure cross-boundary data exchange. The bus that lets a Determinant rule on the citizen-portal Joget read `$registry.farmer.parcels[*].size_ha` from a back-office Joget without point-to-point coupling. Designed-for via `reg-bb-im-connector` in the convergence framework; not yet built. |
| **Messaging (Notification)** | Templated communication with citizens and operators over email / SMS / push / USSD. Multi-channel routing, retry, audit trail. In GovStack this is a separate BB to which RegBB delegates the dispatch step. |
| **Payments** | Disbursement, collection, and reconciliation of money flows tied to a registered service. RegBB integrates with Payment BB at the "decided → granted → disbursed" boundary. Out of scope for FP pilot per `payment_integration_scoping_note.md`. |
| **Identity** | Foundational citizen identity and authentication. Issues the identifier RegBB consumes for "lookup by foundational identifier". Separate from RegBB's *application* identity (the application UUID is RegBB-owned). |
| **Consent** | Records and enforces the citizen's permission to share their data between systems. The GovStack architecture-document calls consent a first-class concern; in practice it gates Information Mediator's cross-system reads. |

### §1.2 How the BBs compose

In a fully-composed GovStack deployment, the picture is roughly:

```
                              ┌───────────────────┐
                              │     Identity      │   issues foundational ID
                              └─────────┬─────────┘
                                        │
                                        ↓
   ┌───────────┐    ┌─────────────────────────────┐    ┌────────────────┐
   │  Consent  │←──→│  Registration (RegBB)       │←──→│  Information   │←─→  registries,
   └───────────┘    │  ┌─Service catalog          │    │  Mediator      │     other BBs
                    │  ├─Identity verification    │    └────────────────┘
                    │  ├─Application submission   │
                    │  ├─Document submission      │
                    │  ├─Eligibility evaluation   │←──→ ┌──────────────┐
                    │  ├─Decision management      │     │  Messaging   │
                    │  └─Audit & observability    │     │ (Notification)│
                    └──────────────┬──────────────┘     └──────────────┘
                                   │ disbursement
                                   ↓
                         ┌──────────────────┐
                         │     Payments     │
                         └──────────────────┘
```

The Farmers Portal implements the central RegBB box, partially addresses Messaging via internal queues + EmailDispatcher, has a designed-for-but-not-wired connector pattern for Information Mediator, defers Payments per scoping, uses Joget-local auth for Identity, and does not explicitly address Consent. §3 below maps each in detail.

---

## §2 — Farmers Portal architectural surface

This section establishes the lens — what the project actually looks like as of May 2026 — so that §3's BB-by-BB mapping is anchored in reality rather than in the slightly-stale alignment docs.

### §2.1 The five-layer shape

The architecture-overview's §5.1 layered diagram, updated for W1-W4:

<svg viewBox="0 0 760 540" xmlns="http://www.w3.org/2000/svg" role="img">
  <title>Farmers Portal layered architecture — May 2026</title>
  <desc>Five layers from interfaces at top through REST API gateway, RegBB domain plugins, cross-cutting platform engines, and the foundation. W1-W4 additions in green outline.</desc>
  <style>
    .layer { fill: #f6f7f9; stroke: #2c2f36; stroke-width: 1.2; }
    .layer-title { font: 600 12px system-ui, sans-serif; fill: #2c2f36; }
    .label { font: 11px system-ui, sans-serif; fill: #2c2f36; }
    .label-small { font: 10px system-ui, sans-serif; fill: #555; }
    .new { fill: #e7f5ec; stroke: #1b7e3e; stroke-width: 1.2; }
    .new-label { font: 11px system-ui, sans-serif; fill: #1b7e3e; }
    .arrow { stroke: #2c2f36; stroke-width: 1; fill: none; marker-end: url(#a); }
  </style>
  <defs>
    <marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="#2c2f36"/>
    </marker>
  </defs>
  <!-- Layer 1: Interfaces -->
  <rect class="layer" x="20" y="15" width="720" height="80" rx="4"/>
  <text class="layer-title" x="30" y="33">Interfaces</text>
  <rect class="layer" x="40" y="42" width="200" height="42" rx="2"/>
  <text class="label" x="50" y="60">Citizen portal (Joget userview)</text>
  <text class="label-small" x="50" y="74">+ kiosk, mobile browser</text>
  <rect class="layer" x="260" y="42" width="200" height="42" rx="2"/>
  <text class="label" x="270" y="60">Operator workspace</text>
  <text class="label-small" x="270" y="74">5 categories, RBAC-gated (W1)</text>
  <rect class="layer" x="480" y="42" width="240" height="42" rx="2"/>
  <text class="label" x="490" y="60">Admin (MM-Config + Master Data)</text>
  <text class="label-small" x="490" y="74">analyst-authored metadata</text>
  <!-- Arrow down -->
  <line class="arrow" x1="380" y1="98" x2="380" y2="115"/>
  <!-- Layer 2: REST API gateway -->
  <rect class="layer" x="20" y="118" width="720" height="58" rx="4"/>
  <text class="layer-title" x="30" y="136">REST API gateway (API Builder)</text>
  <text class="label" x="40" y="156">/api/formcreator · /api/regbb · /api/budget · /api/gis · /api/rules · /api/smartsearch · /api/appdef</text>
  <text class="label-small" x="40" y="170">57 endpoints across 7 providers (auto-generated reference, May 2026)</text>
  <line class="arrow" x1="380" y1="179" x2="380" y2="196"/>
  <!-- Layer 3: RegBB domain plugins -->
  <rect class="layer" x="20" y="199" width="720" height="142" rx="4"/>
  <text class="layer-title" x="30" y="217">RegBB domain plugins (OSGi bundles under plugins/)</text>
  <!-- Row 1 -->
  <rect class="layer" x="35" y="225" width="160" height="42" rx="2"/>
  <text class="label" x="42" y="241">reg-bb-engine</text>
  <text class="label-small" x="42" y="257">metadata, lifecycle, eligibility</text>
  <rect class="layer" x="205" y="225" width="160" height="42" rx="2"/>
  <text class="label" x="212" y="241">reg-bb-publisher</text>
  <text class="label-small" x="212" y="257">forms, datalists, userviews</text>
  <rect class="layer" x="375" y="225" width="160" height="42" rx="2"/>
  <text class="label" x="382" y="241">identity-resolver-rt</text>
  <text class="label-small" x="382" y="257">NID lookup + field map</text>
  <rect class="layer" x="545" y="225" width="180" height="42" rx="2"/>
  <text class="label" x="552" y="241">application-engine-rt</text>
  <text class="label-small" x="552" y="257">seeds dynamic forms</text>
  <!-- Row 2 -->
  <rect class="layer" x="35" y="277" width="160" height="42" rx="2"/>
  <text class="label" x="42" y="293">DSL rule stack (×4)</text>
  <text class="label-small" x="42" y="309">grammar+api+editor+runner</text>
  <rect class="new" x="205" y="277" width="160" height="42" rx="2"/>
  <text class="new-label" x="212" y="293">Budget Engine ★</text>
  <text class="label-small" x="212" y="309">commitment + voucher ledger</text>
  <rect class="new" x="375" y="277" width="160" height="42" rx="2"/>
  <text class="new-label" x="382" y="293">IM module ★</text>
  <text class="label-small" x="382" y="309">inventory · alloc · voucher · dist</text>
  <rect class="layer" x="545" y="277" width="180" height="42" rx="2"/>
  <text class="label" x="552" y="293">Submission backbone (×3)</text>
  <text class="label-small" x="552" y="309">wf-activator+doc-submitter+ps</text>
  <line class="arrow" x1="380" y1="344" x2="380" y2="361"/>
  <!-- Layer 4: Cross-cutting platform engines -->
  <rect class="layer" x="20" y="364" width="720" height="92" rx="4"/>
  <text class="layer-title" x="30" y="382">Cross-cutting platform engines (configurable, domain-agnostic)</text>
  <rect class="layer" x="35" y="390" width="160" height="56" rx="2"/>
  <text class="label" x="42" y="406">joget-status-framework</text>
  <text class="label-small" x="42" y="420">EntityType + lifecycle</text>
  <text class="label-small" x="42" y="434">+ audit_log writer</text>
  <rect class="layer" x="205" y="390" width="160" height="56" rx="2"/>
  <text class="label" x="212" y="406">form-quality-runtime</text>
  <text class="label-small" x="212" y="420">SQL rule engine + banner</text>
  <text class="label-small" x="212" y="434">(unified per ADR-031)</text>
  <rect class="layer" x="375" y="390" width="160" height="56" rx="2"/>
  <text class="label" x="382" y="406">form-creator-api</text>
  <text class="label-small" x="382" y="420">two-cache-safe deploy</text>
  <text class="label-small" x="382" y="434">+ datalists + userviews</text>
  <rect class="new" x="545" y="390" width="180" height="56" rx="2"/>
  <text class="new-label" x="552" y="406">EmailDispatcher ★</text>
  <text class="label-small" x="552" y="420">notification_queue (W2)</text>
  <text class="label-small" x="552" y="434">+ state machine (W3.1)</text>
  <line class="arrow" x1="380" y1="459" x2="380" y2="476"/>
  <!-- Layer 5: Foundation -->
  <rect class="layer" x="20" y="479" width="720" height="48" rx="4"/>
  <text class="layer-title" x="30" y="497">Foundation</text>
  <text class="label" x="40" y="517">Joget DX 8.1 Enterprise · PostgreSQL (Azure-managed) · OpenJDK 11 · Apache Tomcat</text>
  <!-- Legend -->
  <rect class="new" x="600" y="14" width="14" height="14"/>
  <text class="label-small" x="620" y="25">added in W1-W4</text>
</svg>

The W1-W4 additions (green outline) — Budget Engine, IM module, EmailDispatcher + notification queue — are NOT yet reflected in either the conformance checklist (2026-04-27) or the architecture overview (2026-04-28). The convergence framework r2 (2026-05-07) is the most current of the three but still pre-dates W3 lifecycle and W4 perf work. This drift is one of the gap-analysis findings Phase 2 will close.

### §2.2 The architectural "trio" (from convergence-framework §2)

The most important framing the project has settled on: a RegBB-conformant generator requires three properties simultaneously, none independently sufficient.

<svg viewBox="0 0 700 320" xmlns="http://www.w3.org/2000/svg" role="img">
  <title>The metadata-engine-grammar trio</title>
  <desc>Three vertices: closed metadata (mm_* tables), interpretive engine (reg-bb-engine), and closed grammar (Determinant DSL). All three required for the generator to be a generator.</desc>
  <style>
    .vertex { fill: #f6f7f9; stroke: #2c2f36; stroke-width: 1.5; }
    .title { font: 600 13px system-ui, sans-serif; fill: #2c2f36; }
    .label { font: 11px system-ui, sans-serif; fill: #2c2f36; }
    .label-small { font: 10px system-ui, sans-serif; fill: #555; }
    .edge { stroke: #888; stroke-width: 1.5; stroke-dasharray: 4 3; fill: none; }
    .edge-label { font: italic 10px system-ui, sans-serif; fill: #555; }
  </style>
  <!-- Vertex 1: Metadata (top) -->
  <rect class="vertex" x="260" y="20" width="180" height="80" rx="4"/>
  <text class="title" x="270" y="40">1. Closed metadata</text>
  <text class="label" x="270" y="58">12 mm_* entities</text>
  <text class="label-small" x="270" y="73">mm_screen · mm_field · mm_catalog</text>
  <text class="label-small" x="270" y="86">mm_determinant · mm_role · mm_action…</text>
  <!-- Vertex 2: Engine (bottom-left) -->
  <rect class="vertex" x="40" y="220" width="200" height="80" rx="4"/>
  <text class="title" x="50" y="240">2. Interpretive engine</text>
  <text class="label" x="50" y="258">reg-bb-engine</text>
  <text class="label-small" x="50" y="273">MetaScreenElement, lifecycle,</text>
  <text class="label-small" x="50" y="286">decision binder, audit hooks</text>
  <!-- Vertex 3: Grammar (bottom-right) -->
  <rect class="vertex" x="460" y="220" width="200" height="80" rx="4"/>
  <text class="title" x="470" y="240">3. Closed grammar</text>
  <text class="label" x="470" y="258">Determinant DSL</text>
  <text class="label-small" x="470" y="273">closed-20 operator fast-path +</text>
  <text class="label-small" x="470" y="286">DSL extensions (per ADR-001r2)</text>
  <!-- Edges -->
  <line class="edge" x1="350" y1="103" x2="200" y2="220"/>
  <text class="edge-label" x="220" y="170">engine reads metadata</text>
  <line class="edge" x1="350" y1="103" x2="500" y2="220"/>
  <text class="edge-label" x="430" y="170">grammar is metadata content</text>
  <line class="edge" x1="240" y1="270" x2="460" y2="270"/>
  <text class="edge-label" x="290" y="263">engine evaluates grammar</text>
</svg>

Per convergence-framework §3, three commitments follow:
- **§3.1 The meta-model is the source of truth.** Every form, rule, fee, document requirement lives in `mm_*` rows. No parallel source.
- **§3.2 The Determinant is the rule grammar.** One DSL grammar with the closed-20 operator subset as the engine's fast-path. ADR-001r2 records the decision.
- **§3.3 Citizen-portal / back-office decoupling is real.** Multi-instance topology with submission backbone + Information Mediator as the two cross-instance bridges. Architecturally committed; operationally we currently run single-instance for the pilot.

### §2.3 The audit spine

A single `audit_log` table written to by everything that changes state: `joget-status-framework` on every lifecycle transition, `decision-engine-runtime` on every operator decision, `reg-bb-engine` on every eligibility evaluation (the `reg_bb_eval_audit` companion table, indexed in W4 on `c_application_id`), the budget engine on every commitment/expense/release event (the `budget_event` table, indexed on `c_idempotency_key`), the notification queue on every send attempt, and the case-log v1 on every operator note. Audit-by-default is one of the three RegBB conformance tests and the project meets it.

---

## §3 — BB-by-BB mapping

For each BB: canonical responsibility (recap), what we implement, what we delegate or omit, and what's deferred.

### §3.1 Registration (RegBB) — primary BB, heavy implementation

This is the BB the project IS. The conformance checklist's 12 sections map to RegBB's seven services + three cross-cutting concerns + cross-cutting non-functionals + REST API surface. Updated status as of May 2026:

| RegBB service | Status May 2026 | Notes |
|---|---|---|
| Service Catalog | ✅ — `spProgramMain` + 9 tabs (business-facing) AND `mm_screen` / `mm_field` rows (machine-facing) | Convergence on `mm_*` as canonical SoT is mid-flight; both views co-exist during transition. |
| Identity Verification (lookup by foundational ID) | ✅ — `identity-resolver-runtime` + `farmerByNid` resolver config | Working. eGov ID delegation is H3. |
| Application Submission (citizen data entry) | ✅ — `application-engine-runtime` build-012, MetaWizardElement renders multi-tab wizard | End-to-end demo proven. |
| Application Submission (RegBB transmission) | 🟡 — submission backbone built; `farmers_registry.yml` proven; **`farmer_application.yml` not wired** | Single-Joget-instance pilot bypasses the cross-instance path; backbone is dormant but ready. |
| Document Submission | ✅ — `spApplicationDocV2` + MD23/MD64/MD65 master data | Per-programme requirements seeded from `sp_doc_requirement_row`. |
| Eligibility Evaluation (citizen self-attestation) | ✅ — flat criteria + form-quality-runtime (unified into `mm_determinant` per ADR-031) | Was the dormant case at 2026-04-27; ADR-031 closed it. |
| Eligibility Evaluation (server-side authoritative) | ✅ — DSL stack + RoutingEvaluator + BotPullEvaluator, all under `mm_determinant.scope` | ADR-031 unification: one table, multiple consumers discriminated by `scope`. |
| Decision Management | ✅ — `decision-engine-runtime` build-007 + operator decision binder + entitlement issuance | Operator-driven; lifecycle states `APPROVED / REJECTED / PENDING_REVIEW`. |
| Audit & Observability — status transitions | ✅ — `audit_log` via `joget-status-framework` | Per-lifecycle write per transition. |
| Audit & Observability — data-change events | 🟡 — `reg_bb_eval_audit` covers eligibility events; `budget_event` covers budget; `notification_queue` covers email; partial coverage | No single unified data-event stream yet; multiple per-domain audit tables. |
| Audit & Observability — case-log v1 ★ | ✅ — `case_note` table + `note_thread` kernel widget | **Added W3; not in conformance checklist.** Operator-only append-only notes thread on each application. |
| Workflow | ✅ — Joget XPDL + the W3.1 application lifecycle state machine via joget-status-framework | Lifecycle: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED/WITHDRAWN. |
| Notifications ★ | ✅ — `notification_queue` + state machine + EmailDispatcher + 12 markdown templates | **Was ❌ H2 in checklist; W2 closed this gap.** SMS deferred per user decision. |
| Payments | ❌ — deferred per `payment_integration_scoping_note.md` | The Budget Engine handles internal commitment accounting but does not move money. |

The RegBB coverage is substantially complete for the H1 ("internal Joget consumer") horizon defined in the conformance checklist. H2 (systematic REST API surface) and H3 (external BB connectors) remain.

### §3.2 Information Mediator — designed-for, not wired

GovStack's canonical bus for cross-BB and cross-system data exchange. The project's position is articulated in convergence-framework §3.3 and §6.3:

- **Designed-for:** A `reg-bb-im-connector` plugin slot in the converged architecture handles `$registry.*` references in Determinants. When a Determinant cites `$registry.farmer.parcels[*].size_ha`, the connector resolves it via IM rather than via direct JDBC.
- **Currently:** Single-Joget-instance deployment means cross-instance traffic is theoretical; Determinants resolve registry references via local JOINs and the SqlPathEvaluator. Functionally correct, architecturally the right shape for pilot scale, but the IM-conformant call pattern is bypassed.
- **What unblocks the wired version:** A second Joget instance (MAFSN back-office vs citizen portal), or a non-Joget peer registry the project needs to read from. Neither is in the FY26-27 roadmap.

This is the cleanest example in the project of "we built the BB-conformant slot; we run a simpler implementation in it for pilot scale."

### §3.3 Messaging (Notification) — substantially implemented, not yet wired to external BB

The conformance checklist (2026-04-27) says ❌ H2 on Notifications. **That entry is stale.** W2's work landed:

- 12 lifecycle email templates as markdown source-of-truth in `docs/notifications/`
- `spNotifTemplate` form for operator-editable templates (subject + body subs)
- `notification_queue` table with full state machine via `joget-status-framework` (pending → sent / skipped / dead_letter; retry semantics; test-mode override)
- `EmailDispatcher` Java class in `reg-bb-engine` — façade over Joget's `AppUtil.createEmail()` + Apache Commons HtmlEmail
- Per-template Java helper classes (`VoucherIssuedEmail`, `ApplicationOutcomeEmail`, etc.)
- BackgroundWorkerScheduler firing pre/post-decision and per-event emails
- Operator-facing notification timeline as a kernel widget (`notification_timeline`) on each application

What's NOT done: SMS dispatch (deferred per user decision; backend has log-only mode), and delegation to an external GovStack Messaging BB (which would replace the EmailDispatcher with an OIDC-authenticated POST to the BB's REST surface). The latter is H3 work, gated on the external BB existing.

For pilot purposes, the Messaging BB is *implemented internally* rather than *delegated externally*. The interfaces and persistence shape are conformant; only the recipient is local (Gmail via app password for dev, MAFSN-managed SMTP for production per Khotso request).

### §3.4 Payments — deferred

Scoping note exists for the customer-comms record (archived in `x_archive/non-code/customer-comms/payment_integration_scoping_note.md` after the Pass A restructure). Four options considered (A direct gateway, B aggregator, C mobile money operator, D card processor); recommendation is phased A → B → C with D skipped. **Out of scope for FY26-27 pilot.**

What we *do* implement that's payment-adjacent: the **Budget Engine**. This handles internal commitment accounting — when a voucher is issued, a COMMITMENT event lands against the programme's budget envelope; when a voucher is redeemed, an EXPENSE event releases the commitment and posts the spend. This is finance-engine work that lives upstream of any actual payment rail. The Budget Engine does NOT move money; it tracks the obligation. If a Payment BB is later wired in, the redemption→expense event would be the trigger to call out for a real disbursement.

The Budget Engine is *not in the conformance checklist at all* (the checklist's §11 Payments is marked ❌ H3, but the Budget Engine's concern is internal commitment tracking, not external payment). This is a gap in the checklist's coverage that Phase 2 will name.

### §3.5 Identity — Joget-local auth + identity-resolver-runtime; eGov ID delegation is H3

Two layers of "identity" to keep distinct:

- **Authentication identity** — who is the citizen / operator logged in. Today: Joget's built-in user store. RBAC by group membership. Production target: Keycloak realm operated by MAFSN ICT, with OIDC into Joget's Directory Manager. Khotso request out; gateway decision pending.
- **Verification identity** — given a National ID, is this the right person and what data do we already have for them? Today: `identity-resolver-runtime` looks up `farmerBasicInfo` by NID and pre-fills the application's applicant tab. Works. eGov ID delegation (call out to a foundational Identity BB) is designed-for in the resolver's swap-in pattern but not wired.

The split is correct. RegBB §3 cleanly distinguishes "lookup by foundational identifier" (what we do) from "authentication / identity proofing" (what an eGov ID BB does). The H3 gap is real but bounded.

### §3.6 Consent — not explicitly addressed

This is the cleanest gap in the architecture. GovStack treats Consent as a first-class concern: a citizen's permission to share their data between systems. In the canonical composition, when RegBB's Identity Verification calls Information Mediator to read a citizen's parcel data, the Consent BB has previously recorded "yes, the citizen authorised this".

The Farmers Portal:
- Does NOT have a consent-record entity.
- Does NOT prompt the citizen to consent before the resolver pre-fills.
- Operates on the operational assumption that "applying for a subsidy implies consent to verify against the registry."

This is defensible for the pilot scope (single-ministry, single-domain, citizen-initiated) but is a real gap against the GovStack spec and would need explicit modelling before any cross-ministry data exchange (e.g. reading the citizen's health-ministry-held data to evaluate a multi-criteria programme). Phase 2 should record this as a deliberate scope-narrowing, not an oversight.

---

## §4 — Deviations from GovStack canon

Five places where the project deliberately departs from the strict spec interpretation, with reasoning.

### §4.1 Four identified RegBB spec gaps (convergence-framework §9)

The project has surfaced four places where the RegBB spec is silent but a real implementation needs to act. Each is documented as candidate upstream feedback to GovStack:

1. **§9.1 Master Data / Catalog as a first-class entity.** The spec mentions "catalog" three times in passing; the project promotes it to a peer of the 12 RegBB entities (`mm_catalog` with `scope ∈ {instance, service}`, `source ∈ {static, registry}`, `itemsJson` / `imCapabilityRef`). Candidate spec language: add §7.2.13 Catalog (Master Data) marked REQUIRED.
2. **§9.2 Cascading / hierarchical lookup between fields.** The spec has no field-to-field option-filter mechanism. Real screens always need cascade (pick district → villages refilter). Project fill: optional `optionsFilterField` + `optionsFilterColumn` on `mm_field`. Candidate spec language: extend §7.2.8 Field with an optional `optionsFilter` sub-object.
3. **§9.3 Rule grammar canonicity** (resolved by ADR-001r2). The spec leaves rule expression open between closed-set vocabulary and free-form DSL. Project chose DSL canonical with closed-20 fast-path. Recorded.
4. **§9.4 Wizard / multi-screen sequence as a first-class element.** The spec mentions "wizard" in passing but doesn't model it. Project fill: `MetaWizardElement` reading `mm_screen` rows in `orderIndex` order with per-tab validation. Candidate spec language: add `flowKind ∈ {single_page, wizard}` to `mm_service` / `mm_registration`.

These are intentional and well-reasoned. They strengthen RegBB-conformance for any non-trivial implementation; they don't deviate from canon, they extend canon.

### §4.2 Single-instance pilot vs the canonical multi-instance topology

Per convergence-framework §3.3, the architecture commits to multi-instance (citizen portal Joget + many back-office Joget instances + an admin instance). The Information Mediator and the submission backbone are the two cross-instance bridges.

**For the FY26-27 pilot, the project runs single-instance.** Citizen portal + back-office operator surfaces + admin metadata authoring all live in one Joget. The submission backbone is built and tested for `farmers_registry.yml` but inactive for `farmer_application`. The Information Mediator connector slot exists but is bypassed by local JDBC.

This is a deliberate scope narrowing, not an architectural compromise. The plugin set is identical between single-instance and multi-instance; only the deployment configuration differs. Per the convergence framework's wording: "Adding another back-office is operationally a deployment exercise, not an architectural change."

### §4.3 DSL grammar extension (ADR-001r2)

The canonical GovStack rule expression model — closed-set operator vocabulary — is preserved as the engine's fast-path. But the project authors rules in the broader DSL grammar, with the engine routing each rule to either the in-memory tree-walker (fast-path) or the compile-to-SQL evaluator (DSL-extensions). Operators see one editor, one grammar.

Convergence-framework §3.2 acknowledges the trade-off: cross-jurisdiction rule portability is partial. Fast-path-subset rules are portable across any RegBB-conformant peer; DSL-extension rules (aggregation, `$registry.*`, temporal) are this implementation's superset. Documented and accepted.

### §4.4 The Budget Engine extends RegBB §7 with finance-engine concerns

RegBB §7 (Decision Management) ends at "grant issued". The project's Budget Engine adds:

- Programme budget envelopes with allocated / committed / expensed amounts
- Per-application reservation on submission
- Commitment on approval (with envelope-utilisation gate)
- Expense on voucher redemption (with idempotency key per dispatch)
- Reservation release on rejection / withdrawal
- Commitment release on voucher cancellation or expiry
- Donor-grade financial reports
- Threshold automation (>75% utilised → email finance officer; frozen → block new approvals)

This is finance-engine territory, not strictly RegBB. The conformance checklist doesn't have a "Budget Engine" section; it sits awkwardly between RegBB §7 (Decision) and the Payments BB (which is deferred). Phase 2 should propose either (a) extending the conformance checklist with a "Budget Engine / commitment accounting" section, or (b) carving it out as Lesotho-specific extension and citing it under §4 deviations rather than §3 conformance.

### §4.5 The IM (Inputs Management) module extends beyond Registration

RegBB scope ends at "decision recorded, grant issued". The Farmers Portal continues past that point through:

- Input catalogue + supplier registry (md27input, md44Input, im_supplier)
- Inventory tracking per Resource Centre (im_inventory, im_stock_transaction)
- Allocation planning (im_allocation_plan + line grid)
- Voucher issuance (im_voucher) — automatic on approval
- Voucher redemption (im_voucher_redemption) — including partial redemption
- Distribution receipts with signatures (im_distribution)
- End-of-cycle reconciliation reports

This is post-registration *delivery* — conceptually adjacent to RegBB but architecturally a sibling domain. In a fully-composed GovStack deployment this might live in a "Distribution BB" or under the Payments BB's reconciliation surface. As of GovStack 2.0.0 there's no canonical home for this; the project ships it as a first-class module hooked into the same metadata/engine/grammar trio.

For Phase 2: name this explicitly as a Lesotho extension to the canonical RegBB scope, with a clear migration path if a future GovStack revision adds a Distribution / Subsidy-delivery BB.

---

## §5 — Composition picture

### §5.1 GovStack BB suite coverage map

Where the Farmers Portal sits in the canonical 6-BB picture:

<svg viewBox="0 0 760 340" xmlns="http://www.w3.org/2000/svg" role="img">
  <title>GovStack BB suite coverage by Farmers Portal</title>
  <desc>Six BBs arranged around Information Mediator as the central bus. Color-coded by Farmers Portal coverage status: implemented, partial, delegated, deferred, not addressed.</desc>
  <style>
    .bus { fill: #fff8e6; stroke: #c89e3b; stroke-width: 1.5; }
    .impl { fill: #d8efdc; stroke: #1b7e3e; stroke-width: 1.5; }
    .partial { fill: #fbf2cf; stroke: #b88a1b; stroke-width: 1.5; }
    .defer { fill: #f2e3e3; stroke: #a35454; stroke-width: 1.5; }
    .gap { fill: #e9eaee; stroke: #6d6f76; stroke-width: 1.5; stroke-dasharray: 4 3; }
    .title { font: 600 13px system-ui, sans-serif; fill: #2c2f36; }
    .label { font: 11px system-ui, sans-serif; fill: #2c2f36; }
    .label-small { font: 10px system-ui, sans-serif; fill: #555; }
    .legend-label { font: 11px system-ui, sans-serif; fill: #2c2f36; }
    .arrow { stroke: #888; stroke-width: 1.2; fill: none; marker-end: url(#a); }
  </style>
  <defs>
    <marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="#888"/>
    </marker>
  </defs>
  <!-- Information Mediator (centre) -->
  <rect class="bus" x="280" y="120" width="200" height="80" rx="6"/>
  <text class="title" x="293" y="142">Information Mediator</text>
  <text class="label" x="293" y="160">cross-BB / cross-system bus</text>
  <text class="label-small" x="293" y="178">FP: connector slot designed,</text>
  <text class="label-small" x="293" y="190">bypassed for single-instance pilot</text>
  <!-- Identity (top) -->
  <rect class="partial" x="310" y="15" width="140" height="64" rx="4"/>
  <text class="title" x="320" y="35">Identity</text>
  <text class="label" x="320" y="51">Joget-local auth +</text>
  <text class="label" x="320" y="64">resolver-runtime</text>
  <text class="label-small" x="320" y="75">eGov ID delegation H3</text>
  <!-- Consent (top-left) -->
  <rect class="gap" x="40" y="15" width="140" height="64" rx="4"/>
  <text class="title" x="50" y="35">Consent</text>
  <text class="label" x="50" y="51">not addressed</text>
  <text class="label-small" x="50" y="65">implicit at pilot scale;</text>
  <text class="label-small" x="50" y="77">explicit modelling deferred</text>
  <!-- RegBB (left, large) -->
  <rect class="impl" x="40" y="120" width="200" height="80" rx="6"/>
  <text class="title" x="53" y="142">Registration (RegBB)</text>
  <text class="label" x="53" y="160">primary BB — heavy impl</text>
  <text class="label-small" x="53" y="178">7 services + audit + workflow;</text>
  <text class="label-small" x="53" y="190">H1 conformance substantially met</text>
  <!-- Messaging (right) -->
  <rect class="impl" x="540" y="120" width="180" height="80" rx="6"/>
  <text class="title" x="553" y="142">Messaging (Notification)</text>
  <text class="label" x="553" y="160">EmailDispatcher + queue</text>
  <text class="label-small" x="553" y="178">12 templates wired (W2);</text>
  <text class="label-small" x="553" y="190">SMS deferred; external BB H3</text>
  <!-- Payments (bottom-right) -->
  <rect class="defer" x="540" y="240" width="180" height="64" rx="4"/>
  <text class="title" x="553" y="260">Payments</text>
  <text class="label" x="553" y="276">deferred for pilot</text>
  <text class="label-small" x="553" y="290">scoping note done; Budget Engine</text>
  <text class="label-small" x="553" y="302">handles internal commitment</text>
  <!-- Extra: Lesotho extension box (bottom-left) -->
  <rect class="impl" x="40" y="240" width="200" height="64" rx="4"/>
  <text class="title" x="53" y="260">Lesotho extensions</text>
  <text class="label" x="53" y="276">Budget Engine, IM module</text>
  <text class="label-small" x="53" y="290">finance-engine + post-registration</text>
  <text class="label-small" x="53" y="302">delivery (no canonical BB home)</text>
  <!-- Edges to bus -->
  <line class="arrow" x1="380" y1="79" x2="380" y2="120"/>
  <line class="arrow" x1="240" y1="160" x2="280" y2="160"/>
  <line class="arrow" x1="480" y1="160" x2="540" y2="160"/>
  <line class="arrow" x1="630" y1="200" x2="630" y2="240"/>
  <line class="arrow" x1="180" y1="47" x2="280" y2="120"/>
  <line class="arrow" x1="240" y1="200" x2="240" y2="240"/>
  <!-- Legend -->
  <rect class="impl" x="40" y="320" width="14" height="14"/><text class="legend-label" x="58" y="332">implemented</text>
  <rect class="partial" x="180" y="320" width="14" height="14"/><text class="legend-label" x="198" y="332">partial/internal</text>
  <rect class="defer" x="340" y="320" width="14" height="14"/><text class="legend-label" x="358" y="332">deferred</text>
  <rect class="gap" x="440" y="320" width="14" height="14"/><text class="legend-label" x="458" y="332">not addressed</text>
  <rect class="bus" x="580" y="320" width="14" height="14"/><text class="legend-label" x="598" y="332">designed-for, bypassed</text>
</svg>

### §5.2 End-to-end application lifecycle across BBs

The 12-step canonical journey of a single citizen application, with BB attribution per step:

<svg viewBox="0 0 760 480" xmlns="http://www.w3.org/2000/svg" role="img">
  <title>End-to-end application lifecycle across the BB suite</title>
  <desc>Twelve steps from citizen registration through redemption and audit, showing which BB each step engages.</desc>
  <style>
    .step { fill: #f6f7f9; stroke: #2c2f36; stroke-width: 1; }
    .step-num { font: 600 11px system-ui, sans-serif; fill: #1b7e3e; }
    .step-title { font: 600 11px system-ui, sans-serif; fill: #2c2f36; }
    .step-label { font: 10px system-ui, sans-serif; fill: #555; }
    .bb { font: italic 10px system-ui, sans-serif; fill: #1b7e3e; }
    .arrow { stroke: #888; stroke-width: 1; fill: none; marker-end: url(#a2); }
    .lane { fill: none; stroke: #ddd; stroke-width: 1; stroke-dasharray: 2 3; }
    .lane-label { font: 600 10px system-ui, sans-serif; fill: #888; }
  </style>
  <defs>
    <marker id="a2" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="#888"/>
    </marker>
  </defs>
  <!-- Lane labels -->
  <text class="lane-label" x="10" y="50">Identity</text>
  <text class="lane-label" x="10" y="125">RegBB</text>
  <text class="lane-label" x="10" y="200">RegBB</text>
  <text class="lane-label" x="10" y="275">Notification</text>
  <text class="lane-label" x="10" y="350">Budget+IM</text>
  <text class="lane-label" x="10" y="425">Audit</text>
  <line class="lane" x1="70" y1="20" x2="70" y2="450"/>
  <!-- Steps arranged in a flow -->
  <!-- Step 1: NID lookup -->
  <rect class="step" x="90" y="30" width="130" height="50" rx="3"/>
  <text class="step-num" x="100" y="46">1</text>
  <text class="step-title" x="115" y="46">NID lookup</text>
  <text class="step-label" x="100" y="62">resolver pre-fills tab 1</text>
  <text class="bb" x="100" y="75">Identity (FP-local)</text>
  <line class="arrow" x1="220" y1="55" x2="240" y2="55"/>
  <!-- Step 2: Wizard tabs -->
  <rect class="step" x="240" y="30" width="130" height="50" rx="3"/>
  <text class="step-num" x="250" y="46">2</text>
  <text class="step-title" x="265" y="46">Wizard tabs</text>
  <text class="step-label" x="250" y="62">eligibility, benefits, docs</text>
  <text class="bb" x="250" y="75">RegBB §4/5/6</text>
  <line class="arrow" x1="370" y1="55" x2="390" y2="55"/>
  <!-- Step 3: form-quality -->
  <rect class="step" x="390" y="30" width="130" height="50" rx="3"/>
  <text class="step-num" x="400" y="46">3</text>
  <text class="step-title" x="415" y="46">Quality rules fire</text>
  <text class="step-label" x="400" y="62">on every save</text>
  <text class="bb" x="400" y="75">RegBB §6 (FP-local)</text>
  <line class="arrow" x1="520" y1="55" x2="540" y2="55"/>
  <!-- Step 4: Submit -->
  <rect class="step" x="540" y="30" width="130" height="50" rx="3"/>
  <text class="step-num" x="550" y="46">4</text>
  <text class="step-title" x="565" y="46">Submit confirmed</text>
  <text class="step-label" x="550" y="62">DRAFT → SUBMITTED</text>
  <text class="bb" x="550" y="75">RegBB §4 (W3.1)</text>
  <!-- Step 5: server-side eval -->
  <line class="arrow" x1="605" y1="80" x2="605" y2="105"/>
  <rect class="step" x="540" y="105" width="130" height="50" rx="3"/>
  <text class="step-num" x="550" y="121">5</text>
  <text class="step-title" x="565" y="121">Server eligibility</text>
  <text class="step-label" x="550" y="137">RoutingEvaluator</text>
  <text class="bb" x="550" y="150">RegBB §6 server-side</text>
  <!-- Step 6: Decision -->
  <line class="arrow" x1="540" y1="130" x2="520" y2="130"/>
  <rect class="step" x="390" y="105" width="130" height="50" rx="3"/>
  <text class="step-num" x="400" y="121">6</text>
  <text class="step-title" x="415" y="121">Decision</text>
  <text class="step-label" x="400" y="137">auto or operator</text>
  <text class="bb" x="400" y="150">RegBB §7</text>
  <!-- Step 7: Outcome email -->
  <line class="arrow" x1="455" y1="155" x2="455" y2="180"/>
  <rect class="step" x="390" y="180" width="130" height="50" rx="3"/>
  <text class="step-num" x="400" y="196">7</text>
  <text class="step-title" x="415" y="196">Outcome email</text>
  <text class="step-label" x="400" y="212">approve/reject/review</text>
  <text class="bb" x="400" y="225">Messaging (W2)</text>
  <!-- Step 8: budget commitment -->
  <line class="arrow" x1="455" y1="230" x2="455" y2="255"/>
  <rect class="step" x="390" y="255" width="130" height="50" rx="3"/>
  <text class="step-num" x="400" y="271">8</text>
  <text class="step-title" x="415" y="271">Budget COMMIT</text>
  <text class="step-label" x="400" y="287">envelope event</text>
  <text class="bb" x="400" y="300">Budget Engine</text>
  <!-- Step 9: voucher issued -->
  <line class="arrow" x1="520" y1="280" x2="540" y2="280"/>
  <rect class="step" x="540" y="255" width="130" height="50" rx="3"/>
  <text class="step-num" x="550" y="271">9</text>
  <text class="step-title" x="565" y="271">Voucher issued</text>
  <text class="step-label" x="550" y="287">VCH-XXXXXX</text>
  <text class="bb" x="550" y="300">IM (post-RegBB)</text>
  <!-- Step 10: voucher SMS -->
  <line class="arrow" x1="605" y1="305" x2="605" y2="330"/>
  <rect class="step" x="540" y="330" width="130" height="50" rx="3"/>
  <text class="step-num" x="550" y="346">10</text>
  <text class="step-title" x="565" y="346">Voucher email</text>
  <text class="step-label" x="550" y="362">+SMS (when wired)</text>
  <text class="bb" x="550" y="375">Messaging</text>
  <!-- Step 11: redemption -->
  <line class="arrow" x1="540" y1="355" x2="520" y2="355"/>
  <rect class="step" x="390" y="330" width="130" height="50" rx="3"/>
  <text class="step-num" x="400" y="346">11</text>
  <text class="step-title" x="415" y="346">Redemption</text>
  <text class="step-label" x="400" y="362">+ EXPENSE event</text>
  <text class="bb" x="400" y="375">IM + Budget</text>
  <!-- Step 12: audit -->
  <line class="arrow" x1="455" y1="380" x2="455" y2="405"/>
  <rect class="step" x="390" y="405" width="130" height="50" rx="3"/>
  <text class="step-num" x="400" y="421">12</text>
  <text class="step-title" x="415" y="421">Audit forever</text>
  <text class="step-label" x="400" y="437">5 audit tables</text>
  <text class="bb" x="400" y="450">RegBB §8</text>
</svg>

Every step is implemented. Steps 1, 3, 5, 6, 8 hit the Determinant rule engine via different `scope` values (per ADR-031: `bot_pull`, `quality`, `eligibility`, `decision_to_status`, `budget_amount`). Steps 7, 10 fire via the W2 EmailDispatcher. Steps 8, 11 emit budget events tracked by the W4-indexed `c_idempotency_key` and `c_application_id`.

### §5.3 The audit spine

Single `audit_log` table, written to by every domain that changes state. Sources:

- `joget-status-framework` → application lifecycle transitions (DRAFT → SUBMITTED → …)
- `decision-engine-runtime` → operator decisions
- `reg-bb-engine` → eligibility evaluations (also lands in companion `reg_bb_eval_audit`)
- Budget Engine → COMMITMENT / EXPENSE / RESERVATION / RELEASE_* events (also lands in `budget_event`)
- Notification queue → email send attempts
- Case-log v1 → operator notes per application

Per CLAUDE.md's audit-retention section, the spine grows unbounded by design — operators rely on it for forensic investigation. Retention policy is documented; quarterly archival to cold storage, indexes on `c_application_id` (added W4) and `c_idempotency_key` (already in place) keep per-applicant lookups fast.

---

## §6 — Implications for Phase 2 gap analysis

The Phase 2 deliverable (`docs/architecture/govstack-alignment-may2026.md`, half-day) will produce a per-BB status verdict: conformant / partial / deferred / out-of-scope, with named follow-ups for each "partial". This Phase 1 review surfaces six findings Phase 2 should address:

1. **Refresh the regbb-conformance-checklist's stale rows.** Specifically: §10 Notifications (was ❌, now ✅ for templates + queue, 🟡 for SMS, ❌ H3 for external BB delegation); §7 Decision Management "Notify applicant on decision" (was 🟡 H2, now ✅); §8 Audit & Observability (add case-log v1 row); §13 Cross-cutting Authorisation (mention W1 category-level GroupPermission). Either re-author the checklist or supersede it with the Phase 2 alignment report.

2. **Add Budget Engine to the conformance scope.** Either as a §11-bis (commitment accounting, distinct from §11 Payments) or as a §4-style Lesotho extension. Today it's an unowned row.

3. **Add IM module to the conformance scope.** Post-registration delivery is genuinely outside RegBB but inside the project. Decide whether to model it as a Lesotho extension or to argue for a future GovStack "Distribution BB" slot. The IM end-to-end overview docx already exists; the architectural framing is the gap.

4. **Name Consent explicitly as deferred.** Not addressed in any current doc. Phase 2 should record the deliberate scope-narrowing for pilot, with a trigger condition for revisiting (the first cross-ministry data exchange).

5. **Map the 31 ADRs to BB sections.** The convergence-framework and conformance-checklist cite ADRs piecemeal. A cross-reference matrix — "for each ADR, which BB does it touch and what does it commit to" — would help future contributors orient quickly. Phase 2 deliverable.

6. **Decide whether the cross-functional NFR sweep (RegBB §13) belongs in Phase 2 or in W7-W8 of the solo plan.** Today the checklist has rows for authentication, authorisation, i18n, accessibility, performance, DR — all marked 🟡. The W7 accessibility audit and W8 L1/L2 runbooks address some; the perf baseline (W4) and backup-runbook address others. Phase 2 should consolidate.

The path from Phase 1 (this) to Phase 2 (gap analysis) is roughly: take the 6 findings above, walk through each, produce per-BB status verdicts with named follow-ups, and ship as `docs/architecture/govstack-alignment-may2026.md`. Half-day work, predicated on this Phase 1 landing.

---

## §7 — Closing

The Farmers Portal is a substantially-conformant RegBB implementation at the H1 ("internal Joget consumer") horizon. Substantial because every RegBB service is implemented and the W1-W4 work closed gaps the conformance checklist still records as open. Not yet fully conformant because (a) the systematic REST API surface (H2) is partial, (b) external BB connectors (H3) are designed-for but not wired, (c) Consent is not addressed, and (d) the Budget Engine + IM module live outside the canonical RegBB scope and need explicit architectural framing.

The three alignment docs (convergence-framework r2, regbb-conformance-checklist, architecture-overview) gave a sound lens for this review. They're 1-2 weeks behind the W1-W4 work — manageable drift, addressable in Phase 2. The architectural commitments (the trio, the three settled questions, the plugin classifications) hold. The drift is in the conformance accounting, not in the architecture itself.

Phase 2 (`docs/architecture/govstack-alignment-may2026.md`) is unblocked. Recommended order: refresh stale rows → name Budget + IM extensions → record Consent gap → ADR cross-reference matrix → consolidate NFR sweep. Half-day deliverable.

---

*Document v1, 2026-05-12. Inputs: convergence-framework.md (r2), regbb-conformance-checklist.md (draft), architecture-overview.md (living). Companion: production_readiness_roadmap.md and uat_prep_pause_resume_plan.md in `docs/implementation/`.*
