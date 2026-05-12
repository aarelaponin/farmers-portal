# GovStack alignment — Lesotho Farmers Portal, May 2026

**Status:** Phase 2 of a two-phase GovStack alignment review. This document is the gap-analysis report with per-BB verdicts and a consolidated action register. Phase 1 (descriptive landscape) is at `docs/architecture/govstack-landscape-review-may2026.md`.
**Last updated:** 2026-05-12.
**Audience:** Architecture lead, MAFSN ICT, GovStack reviewers preparing UAT entry.
**Supersedes:** `docs/architecture/regbb-conformance-checklist.md` (draft 2026-04-27). The checklist remains in place as a historical artefact; for current conformance verdicts, read this document.
**Inputs:** Phase 1 landscape report, `convergence-framework.md` (r2 2026-05-07), `regbb-conformance-checklist.md` (stale), `architecture-overview.md` (stale on §10/§7/§8), 28 ADRs under `docs/architecture/adr/`, W1-W4 work (RBAC, notifications, lifecycle, perf indexes, case-log v1).

---

## §1 — Executive verdict

The Farmers Portal is **substantially conformant** with the GovStack Registration BB at the H1 ("internal Joget consumer") horizon: every one of the seven RegBB services has a working implementation backed by the metadata-engine-grammar trio, and the W1-W4 work closed three gaps the conformance checklist still records as open (notifications, decision-notify-applicant, case-log audit). The remaining gaps fall into three buckets: **H2 work** (systematic REST API surface + OpenAPI publication), **H3 work** (external BB connectors for Identity, Messaging, Payments, Information Mediator), and **explicit architectural deviations** that need framing — the Budget Engine and IM module both live outside canonical RegBB and have no current home in the conformance documentation; Consent is not addressed anywhere.

### §1.1 Headline status by area

| Area | Verdict | Notes |
|---|---|---|
| **RegBB §1 Architectural principles** | ✅ conformant | All five principles met; configuration-over-code demonstrably operational. |
| **RegBB §2 Service Catalog** | ✅ conformant | Business-facing + machine-facing; both via `mm_*` + `spProgramMain`. |
| **RegBB §3 Identity Verification** | ✅ conformant | `identity-resolver-runtime` + `farmerByNid` config. eGov ID delegation H3. |
| **RegBB §4 Application Submission** | ✅ conformant for citizen data entry; 🟡 partial for RegBB-conformant transmission | Submission backbone built, `farmer_application.yml` unwired (single-instance pilot bypasses). |
| **RegBB §5 Document Submission** | ✅ conformant | `spApplicationDocV2` + MD23/MD64/MD65 master data. |
| **RegBB §6 Eligibility Evaluation** | ✅ conformant (both layers) | Fast-path + DSL-extension via unified `mm_determinant` (ADR-031). |
| **RegBB §7 Decision Management** | ✅ conformant | Operator decisions + entitlement issuance + outcome emails wired. |
| **RegBB §8 Audit & Observability** | ✅ conformant (status + data events + case-log); 🟡 partial on tamper-evidence | Multiple per-domain audit tables; cryptographic chain deferred. |
| **RegBB §9 Workflow** | ✅ conformant | Joget XPDL + W3.1 application lifecycle state machine. |
| **RegBB §10 Notifications** | ✅ internally implemented; 🟡 partial for SMS; ❌ H3 for external Messaging BB | **Was ❌ H2 in checklist; W2 closed it.** |
| **RegBB §11 Payments** | ❌ deferred per scoping note | Budget Engine handles internal commitment; no payment rail. |
| **RegBB §12 API surface** | 🟡 partial — 57 endpoints across 7 providers, no OpenAPI yet | Auto-generated `api_reference.md` exists. |
| **RegBB §13 Cross-functional NFR** | 🟡 partial across all 6 rows | See §3.13 below. |
| **Information Mediator (BB)** | 🟡 designed-for, bypassed for single-instance pilot | `reg-bb-im-connector` slot exists. |
| **Identity BB (external)** | ❌ H3 | Joget-local auth + resolver-runtime is the H1 implementation. |
| **Messaging BB (external)** | ❌ H3 | EmailDispatcher is internal; external BB delegation deferred. |
| **Payments BB (external)** | ❌ deferred | Out of scope per FY26-27 scoping. |
| **Consent BB** | ❌ not addressed | Explicit architectural gap (this document). |
| **Budget Engine (extension)** | ✅ implemented, ★ needs explicit conformance framing | No GovStack BB home. |
| **IM module (extension)** | ✅ implemented, ★ needs explicit conformance framing | Post-registration delivery; no GovStack BB home. |

### §1.2 What this verdict authorises

Per Phase 1 §7, the project is UAT-ready from a conformance perspective. The remaining gaps are either H2/H3 by design (deliberately phased) or pilot-scope-narrowing decisions (single-instance topology, deferred SMS, deferred external BB connectors, deferred Consent). None of them block UAT entry. The action register in §7 prioritises follow-ups that would close conformance-relevant gaps before pilot launch (mostly: refresh the conformance documentation; add Consent recording; complete the H2 REST surface).

---

## §2 — Methodology

### §2.1 Verdict scheme

Five verdict values, applied at the row level:

- **✅ conformant** — the requirement is met by a working, deployed implementation. Evidence cited.
- **🟡 partial** — the requirement is partially met; remainder explicitly named with a horizon (H2 / H3 / out-of-scope).
- **❌ deferred** — not met today; deliberate decision to defer with rationale (scoping note, ADR, or this document).
- **❌ not addressed** — not met today; no explicit decision either way. Identified as a gap requiring attention.
- **★ extension** — not in the canonical RegBB scope; built as a Lesotho extension with no GovStack BB home.

A row marked `🟡` is conformant for the horizon it names. A row marked `❌` is not conformant; the rationale clarifies whether it should be (gap) or shouldn't be (deferred).

### §2.2 Scope and supersession

This document supersedes the 2026-04-27 `regbb-conformance-checklist.md`. The checklist's structure (12 RegBB sections + cross-functional + API + summary) is preserved here; verdicts and evidence are refreshed against May-2026 reality including the W1-W4 work. New sections cover (a) the other five BBs (IM, Messaging, Payments, Identity, Consent), (b) Lesotho domain extensions (Budget Engine, IM module), (c) the 28 ADRs cross-referenced to BB sections, (d) a consolidated action register.

The checklist remains readable for the historical record; if its content disagrees with this document, this document wins.

### §2.3 What this document is not

It is not a re-derivation of GovStack BB specs from canon. The project's three alignment docs (convergence-framework, conformance-checklist, architecture-overview) are taken as the canonical GovStack interpretation per Phase 1's decision. It is not a new conformance test suite — the test scripts in `tooling/test_*_e2e.py` continue to serve that role. It is not a roadmap — `production_readiness_roadmap.md` and `solo_implementation_plan.md` cover sequencing.

---

## §3 — RegBB conformance, per section

Twelve sections + cross-functional, matching the canonical RegBB structure. Each row carries verdict, evidence (file / plugin / ADR), and the gap or follow-up.

### §3.1 Architectural principles

| Principle | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Configuration over code | ✅ | `mm_*` 12-entity meta-model; new programmes / rules / fields authored as MD rows | None for H1. |
| API-first | 🟡 H2 | 57 endpoints across 7 providers (auto-generated `api_reference.md`) | Systematic per-RegBB-service surface missing; OpenAPI publication deferred. |
| Composability with other BBs | 🟡 H3 | Identity resolver designed for swap-in; Notification BB hand-off slot exists in EmailDispatcher | External BB connectors not yet wired. |
| Audit by default | ✅ | `audit_log` via `joget-status-framework`; per-domain audit tables (reg_bb_eval_audit, budget_event, notification_queue, case_note) | Tamper-evidence (hash chain) deferred to H2. |
| Privacy by design | 🟡 | `app_resolver_field_map` enforces field-level data minimisation; session auth | Field-level redaction in operator views deferred; Consent BB not addressed (see §4.5). |

### §3.2 Service Catalog

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Stores the set of registrable services | ✅ | Business-facing: `spProgramMain` + 9 tabs. Machine-facing: `mm_screen` + `mm_field` rows. | Convergence between the two views is mid-flight — both co-exist during transition. Tracked in convergence-framework §3.1. |
| Each service has machine-readable spec (eligibility, benefits, documents) | ✅ | Programme child tables + `mm_*` rows. | None. |
| Services have lifecycle (draft / active / closed) | ✅ | `programme.status`; gated by quality rules (post-ADR-031 in `mm_determinant`). | None. |
| Services discoverable by API | 🟡 H2 | Internal datalist works; `/api/services` REST surface deferred. | H2 deliverable. |
| Schema versioning | 🟡 H2 | Joget app versioning covers structural changes; row-level versioning deferred. | H2 deliverable. |

### §3.3 Identity Verification

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Lookup by foundational identifier | ✅ | `IdentityResolverElement` + `ResolverService`, `farmerByNid` config | None. |
| Field-level data minimisation | ✅ | `app_resolver_field_map` enumerates exactly which fields are exposed. | None. |
| "Once-only" principle | ✅ | Resolver pre-fills target form on input; citizen never re-types known data. | None. |
| Configurable per consumer | ✅ | `app_resolver_config` defines named resolvers; adding a resolver is one row + field-maps. | None. |
| Delegate to external Identity BB | ❌ H3 | Migration path documented in `identity-resolver-design.md` Appendix A. | Deferred to H3; gated on eGov ID availability. |
| Authentication / identity proofing | ❌ H3 | We trust the entered NID at H1; H3 with Identity BB. | Out of scope for FY26-27 pilot. |
| Audit every resolve | ✅ | `audit_log` event on every `ResolverService.resolve()` call. | None. |

### §3.4 Application Submission

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Forms generated from service spec | ✅ | `application-engine-runtime` build-012; `ApplicationSeedingTool` seeds child grids from programme spec. MetaWizardElement renders multi-tab wizard from `mm_screen`. | None. |
| Real-time validation feedback | ✅ | Form-quality (now unified into `mm_determinant.scope='quality'` per ADR-031) + QualityBannerElement. | None. |
| Save-as-draft and resume | ✅ | Joget native + `DRAFT` lifecycle state. | None. |
| Submit triggers downstream workflow | ✅ | DRAFT → SUBMITTED transition (W3.1) routes through EligibilityProcessingWorker. | None. |
| Submission immutable after the act | ✅ | Lifecycle status gate prevents farmer-side edits after SUBMITTED. | None. |
| API-addressable submission | 🟡 H2 | Internal Joget UI works; `/api/applications` REST endpoint exists for citizen API but not under canonical RegBB shape. | H2 deliverable. |
| Multi-channel intake | 🟡 H2 | Web works; kiosk / mobile / USSD need REST surface. | H2 deliverable. |
| RegBB-conformant async transmission (backbone) | 🟡 | Submission backbone built; `farmers_registry.yml` proven; **`farmer_application.yml` not wired** | Single-instance pilot bypasses cross-instance path. Wire when second instance arrives. |

### §3.5 Document Submission

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Document requirements declared per service | ✅ | `sp_doc_requirement_row` programme child + MD23 document types. | None. |
| Citizen-facing upload UI matched to requirements | ✅ | `spApplicationDocV2` FormGrid on `spApplication` Tab 4. | None. |
| File format and size validation | ✅ | Per-row `accepted_formats` + `max_size_kb`, enforced at upload. | None. |
| Verification status tracked | ✅ | `status` column: MISSING / UPLOADED / VERIFIED / REJECTED. | None. |
| Mandatory docs gate submission | ✅ | Quality rule `application.required_docs_uploaded`. | None. |
| Document storage compliant with retention policy | 🟡 | Joget default file storage; retention policy review deferred. | H2 deliverable (operator retention SOP). |
| Verifier identity captured | ✅ | `verifier_note` + audit row (createdBy on update). | None. |

### §3.6 Eligibility Evaluation

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Pluggable rule library per service | ✅ | `mm_determinant` (unified per ADR-031); scope discriminates consumers. | None. |
| Rules editable by operators (no code) | ✅ | `joget-rule-editor` admin UI; analyst picks scope and authors DSL. | None. |
| Rules can read application form data | ✅ | DSL `$applicant.*` references; fast-path tree-walker handles per-keystroke. | None. |
| Rules can read related registry data | ✅ | DSL `$registry.*` references; SqlPathEvaluator compiles to SQL JOIN. | Information Mediator wiring deferred until cross-instance topology activates. |
| Severity levels (block vs warn) | ✅ | `mm_determinant.severity = ERROR / WARNING` (post-ADR-031 unified). | None. |
| Decisions auditable back to rule outcomes | ✅ | `reg_bb_eval_audit` rows per evaluation (indexed on `c_application_id` per W4). | None. |
| Re-evaluation on data change | ✅ | Post-processor fires on every save; quality and bot_pull scopes both rerun. | None. |
| Two-layer evaluation (self-attest + server-side) | ✅ | Fast-path (citizen self-attest) + DSL-extension (server-side authoritative). Per ADR-001r2. | None. |

### §3.7 Decision Management

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Approve / reject / pending-review workflow | ✅ | W3.1 application lifecycle state machine + RoutingEvaluator + DecisionMapper (per ADR-028). | None. |
| Reason captured with every decision | ✅ | `decision_reason` column + audit row. | None. |
| Decision routed to authorised role | ✅ | RBAC categories (W1) gate the operator-decision menu; group-permission applied. | None. |
| Grant issuance on approval | ✅ | `decision-engine-runtime` issues `imEntitlement` row; voucher auto-issuance on approved (W2.4 + IM Slice 6b). | None. |
| Notify applicant on decision | ✅ — **was ❌ H2 in checklist** | EmailDispatcher + per-template helpers (W2). 3 templates: APP_APPROVED / APP_REJECTED / APP_UNDER_REVIEW. | SMS deferred per user decision; external Messaging BB delegation H3. |
| Appeal process | 🟡 | `spAppeal` form exists as stub. | Full flow deferred to H2. |
| Decision API | 🟡 H2 | Internal Joget UI + decision binders work; `/api/decisions` REST wrapper deferred. | H2 deliverable. |

### §3.8 Audit & Observability

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Append-only audit log of state changes | ✅ | `audit_log` via `joget-status-framework` writes on every transition. | None. |
| Audit captures actor, timestamp, reason | ✅ | `triggered_by`, `timestamp`, `reason` columns. | None. |
| Audit captures data-change events | ✅ — **was 🟡 in checklist** | Multiple per-domain audit tables: `reg_bb_eval_audit` (eligibility), `budget_event` (budget), `notification_queue` (email), `case_note` (operator notes, W3). | Unified data-event stream deferred — multiple tables, queries hit each. |
| Case-log v1 ★ | ✅ — **not in original checklist** | `case_note` form + `note_thread` kernel widget; operator-only append-only notes thread on each application. Per W3. | New addition; covered in this document. |
| Tamper-evidence (hash chain) | 🟡 | Joget tables append-only by convention; cryptographic chain deferred to H2. | H2 deliverable. |
| Query API | 🟡 H2 | Internal datalists work; `/api/audit?entityId=...` deferred. | H2 deliverable. |
| Retention policy | 🟡 | Documented in CLAUDE.md "Audit retention" section; quarterly archival ritual specified. | Operational SOP needs MAFSN sign-off before go-live. |

### §3.9 Workflow

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Engine for service-specific processes | ✅ | Joget XPDL 1.0 + W3.1 application lifecycle state machine via joget-status-framework. | None. |
| Process-as-config (not code) | ✅ | XPDL JSON/XML; states + transitions in `joget-status-framework`. | None. |
| Multi-actor routing | ✅ | XPDL participants; RBAC categories (W1). | None. |
| SLA / deadline support | ✅ | XPDL deadlines + escalation activities. | None. |
| Process versioning | ✅ | Joget app versioning. | None. |

### §3.10 Notifications

**Major status update from 2026-04-27 checklist (which marked everything ❌ H2):**

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Templated messages per event | ✅ — **was ❌ H2** | 12 markdown templates in `email_templates/`; `spNotifTemplate` operator-editable; per-template Java helpers (W2). | None for email. |
| Multi-channel (email / SMS / push) | 🟡 — **was ❌ H2** | Email fully wired; SMS deferred per user decision (log-only backend); push not in scope. | SMS unblock awaiting Khotso reply on gateway choice. |
| Delegation to external Notification BB | ❌ H3 | Designed-for: EmailDispatcher is a façade that could be swapped for an external-BB POST. | H3 deliverable; gated on external BB availability. |
| State machine + audit on notification dispatch | ✅ — **not in original checklist** | `notification_queue` form + joget-status-framework state machine: pending → sent / skipped / dead_letter; retry semantics; test-mode override (W2.5). | None. |
| Operator-facing notification timeline | ✅ — **not in original checklist** | `notification_timeline` kernel widget renders per-application send history on operator review form (W2.8 redo). | None. |
| Test-mode banner on every operator page | ✅ | W2.7 — banner indicates when test-mode override is active. | None. |

### §3.11 Payments

| Requirement | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Disbursement on approved applications | ❌ deferred | Per `payment_integration_scoping_note.md`; gated on Decision Management being real first (which it now is). | Out of scope for FY26-27 pilot. |
| Delegation to external Payment BB | ❌ deferred | Designed-for: Budget Engine's EXPENSE event could trigger an external Payment BB call. | H3+ deliverable. |
| Reconciliation | ❌ deferred | Donor-grade reports exist (md_donor + programme_funding); reconciliation of actual money flows deferred. | H3+ deliverable. |
| ★ Internal commitment accounting (Budget Engine) | ✅ extension — **NOT IN CANONICAL RegBB** | RESERVATION / COMMITMENT / EXPENSE / RELEASE_* event ledger on `app_fd_budget_event`. See §5.1. | Needs explicit conformance framing — Lesotho extension, not RegBB §11. |

### §3.12 API surface

| Endpoint family | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| `GET /services` | 🟡 H2 | Internal datalist works; canonical REST endpoint deferred. | H2 deliverable. |
| `POST /identity/resolve` | ✅ internal | Implemented via `identity-resolver-runtime`; session-auth at H1, public REST at H2. | None for H1. |
| `POST /applications` | 🟡 H2 | Internal Joget UI; canonical REST deferred. | H2 deliverable. |
| `GET /applications/{id}` | 🟡 H2 | Internal datalist; canonical REST deferred. | H2 deliverable. |
| `POST /eligibility/evaluate` | 🟡 H2 | Engine works internally; canonical REST wrapper deferred. | H2 deliverable. |
| `POST /decisions/{id}` | 🟡 H2 | Internal tools work; canonical REST wrapper deferred. | H2 deliverable. |
| `GET /audit?entity={id}` | 🟡 H2 | Internal datalist; canonical REST deferred. | H2 deliverable. |
| **Auto-generated reference** | ✅ — **not in original checklist** | `docs/developer/api_reference.md` lists 57 endpoints across 7 providers; regenerated from `@Operation` annotations. | None. |
| OpenAPI 3.x specification | 🟡 H2 | Annotation set present in plugin code; full OpenAPI publication deferred. | H2 deliverable. |

### §3.13 Cross-functional NFR

| Concern | Verdict | Evidence | Gap / follow-up |
|---|---|---|---|
| Authentication | 🟡 | Joget login at H1; Keycloak OIDC planned. **Khotso request out 2026-05-11; pending response.** | Top external dependency on the UAT critical path. |
| Authorisation | ✅ — **was 🟡 in checklist** | W1 — category-level GroupPermission on every userview category; dashboards routed via `/api/formcreator/formcreator/data/list`. Per RBAC v2 tooling. | None. |
| Internationalisation (Sesotho UI) | ❌ | English-only at H1; Sesotho i18n scaffolding deferred to W7.3 of solo plan. | Scaffolding before pilot; translation post-pilot. |
| Accessibility (WCAG 2.1 AA) | 🟡 | Trimeda theme partially compliant. W3.3 mobile audit found no P1; W7.2 audit pending. | W7.2 follow-up. |
| Performance | ✅ — **was 🟡 in checklist** | W4 perf re-baseline + Postgres index tuning (this session); `EXPLAIN ANALYZE` shows server-side queries 1-2 ms; wall-clock dominated by sandbox→Azure RTT. | None for dev numbers; re-baseline from production. |
| Disaster recovery (backup/restore) | 🟡 | `backup_restore_runbook.md` exists with tested procedure; first production drill pending. | Pre-pilot drill. |
| Operational monitoring | 🟡 | W4.4 Prometheus + Grafana POC deferred; only audit-trail observability today. | W4.4 deliverable (post-UAT acceptable). |
| Documentation handover | 🟡 | MAFSN ICT TO-DO, backup runbook, API reference all exist; L1/L2 runbooks deferred to W8. | W8 deliverable. |

---

## §4 — Other BBs in scope

### §4.1 Information Mediator (BB)

**Verdict: 🟡 designed-for, bypassed for single-instance pilot.**

**What exists.** A `reg-bb-im-connector` plugin slot in the converged architecture (per convergence-framework §3.3 and §6). When a Determinant references `$registry.*`, the connector is the intended call path.

**What we run.** Local JDBC reads via SqlPathEvaluator. Functionally correct, architecturally the canonical pattern for single-instance pilot scale.

**What unblocks the wired version.** A second Joget instance (back-office split from citizen portal), or a non-Joget peer registry the project needs to read from. Neither in the FY26-27 roadmap.

**Action.** No follow-up before pilot. After pilot, if MAFSN moves to multi-ministry deployment, wire the connector and route `$registry.*` references through IM.

### §4.2 Messaging (BB)

Captured in §3.10 above. **Internal implementation conformant for the email channel; external BB delegation H3.**

### §4.3 Payments (BB)

Captured in §3.11 above. **Deferred per scoping note.** The Budget Engine (§5.1) covers internal commitment but does not move money.

### §4.4 Identity (BB)

**Verdict: ❌ H3 for external Identity BB delegation; identity verification side covered by `identity-resolver-runtime` per §3.3 above.**

The auth-side gap (Keycloak vs Joget local users) is in §3.13. The verification side (NID-lookup) is in §3.3. Separating the two is correct per RegBB §3 boundary.

### §4.5 Consent (BB)

**Verdict: ❌ not addressed.** Explicit architectural gap, surfaced in Phase 1 §3.6 and recorded here.

**What's missing.** Consent BB records and enforces the citizen's permission to share their data between systems. Today the Farmers Portal:

- Has no consent-record entity
- Does not prompt the citizen to consent before the resolver pre-fills
- Operates on the operational assumption "applying for a subsidy implies consent to verify against the registry"

**Why it's a gap.** Defensible for pilot scope (single-ministry, citizen-initiated, single-domain). Not defensible for cross-ministry data exchange (e.g. reading health-ministry data for a multi-criteria programme).

**Trigger condition for revisiting.** The first cross-ministry data exchange use case. If the FY27-28 roadmap adds such a use case, Consent BB integration becomes pre-launch work for that scope.

**Action.** Phase 3 (post-pilot). For now: record explicitly in the decision log as a deliberate deferral with named trigger condition.

---

## §5 — Lesotho domain extensions

Two substantial modules sit outside the canonical RegBB scope and have no current home in the GovStack BB taxonomy. Both are well-implemented and load-bearing for the FY26-27 pilot. The architectural framing question is: **how do we describe them to a GovStack reviewer?**

### §5.1 Budget Engine

**Verdict: ✅ implemented, ★ Lesotho extension.**

**What it does.** Internal commitment accounting for the subsidy lifecycle. Tracks per-programme budget envelopes with allocated / committed / expensed amounts. Emits events through a state machine:

- RESERVATION (on application submit, holds budget for the requested amount)
- RELEASE_RESERVATION (on auto-approval or rejection)
- COMMITMENT (on voucher issuance, with envelope-utilisation gate)
- EXPENSE (on voucher redemption, with idempotency key per dispatch)
- RELEASE_COMMITMENT (on voucher cancellation or expiry)

**What it isn't.** It does not move money. There is no payment rail. The Budget Engine is upstream of any Payment BB integration — the EXPENSE event would trigger such an integration if it existed.

**Why it's an extension.** RegBB §7 (Decision Management) ends at "grant issued". RegBB §11 (Payments) is about moving money. Internal commitment accounting between those two is neither, and it doesn't fit either neatly.

**Framing recommendation.** Document the Budget Engine as a **finance-engine extension** between RegBB §7 and Payment BB. Cite it in the Phase 3 retrospective as a candidate for either (a) a future GovStack "Commitment Accounting" BB, or (b) a clarification in RegBB §7 of how internal financial commitments are tracked between decision and payment. **Decision log entry recommended.**

**Backed by ADRs:** ADR-022 (separate module), ADR-023 (commitment funnel state model), ADR-024 (mm_action + budget_event integration), ADR-025 (rule-based budget governance), ADR-026 (rule-to-SQL compiler).

### §5.2 IM (Inputs Management) module

**Verdict: ✅ implemented, ★ Lesotho extension.**

**What it does.** Post-registration delivery of the subsidy. Covers:

- Input catalogue (md27input) + supplier registry (im_supplier)
- Inventory per Resource Centre (im_inventory, im_stock_transaction)
- Allocation planning (im_allocation_plan + line grid)
- Voucher issuance (im_voucher) — auto on approval
- Voucher redemption (im_voucher_redemption) — including partial redemption per Slice 11
- Distribution receipts with signatures (im_distribution)
- End-of-cycle reconciliation reports

**What it isn't.** It's not Registration (RegBB ends at "grant issued"). It's not Payment (no money rail). It's not Workflow (it uses workflow but isn't itself one). The closest GovStack analogue is **post-approval distribution** — no canonical BB for that today.

**Why it's an extension.** Real subsidy programmes need the full delivery pipeline. A registration-only system would be functionally incomplete for MAFSN's use case.

**Framing recommendation.** Document the IM module as a **post-registration delivery extension**. In a fully-composed GovStack deployment, IM would either be (a) a separate "Distribution BB" (no such thing in GovStack 2.0.0), (b) a Lesotho-specific concern paralleling RegBB, or (c) an extension to RegBB §7 covering the post-grant lifecycle. The training docx (`_07_Training/_im_overview/`) already exists; the architectural framing is the gap.

**Backed by ADRs:** ADR-016 (IM uses kernel not framework), ADR-017 (mm_service namespace only for IM).

---

## §6 — ADR-to-BB cross-reference matrix

The 28 ADRs in `docs/architecture/adr/` grouped by the BB they primarily touch. Some ADRs touch multiple BBs; primary attribution is shown.

### §6.1 Architectural foundations (cross-BB)

| ADR | Title | What it commits |
|---|---|---|
| ADR-001 r2 | Rule-grammar canon | DSL canonical, closed-twenty fast-path subset. Touches RegBB §6. |
| ADR-002 | mm-DAO placement | Repository layer for mm_* CRUD. Touches RegBB §2 + §6. |
| ADR-003 | Rule storage shape | mm_determinant column shape. Touches RegBB §6. |
| ADR-004 | Eval context data shape | EvalContext envelope for rule evaluation. Touches RegBB §6. |
| ADR-005 | Phase 1 save hook | Form post-processor hook for rule evaluation. Touches RegBB §4 + §6. |
| ADR-006 | Outcome persistence schema | reg_bb_eval_audit row shape. Touches RegBB §6 + §8. |
| ADR-007 | Save-evaluate atomicity | Save and evaluate happen in one transaction. Touches RegBB §4 + §6. |
| ADR-008 | Cache layering | L1 + L2 cache for rule evaluation. Touches RegBB §6 (performance NFR). |
| ADR-009 | Concurrency model | Worker queue, idempotency keys. Touches RegBB §4 (async submit). |
| ADR-010 | Publisher validation depth | What reg-bb-publisher validates vs delegates. Touches RegBB §2. |

### §6.2 Kernel / framework boundary (mm-form-gen scope)

| ADR | Title | What it commits |
|---|---|---|
| ADR-011 | No XPDL generation by RegBB | Workflow definitions stay XPDL; RegBB doesn't generate them. Touches RegBB §9. |
| ADR-012 | mm-form-gen as domain-agnostic kernel | Kernel reads only mm_* + Joget primitives; no domain concepts. Touches architecture overall. |
| ADR-013 | Metamodel reuse beyond RegBB | mm_* tables usable by non-RegBB callers (e.g. IM). Touches RegBB scope + IM extension. |
| ADR-014 | Conditional UI dual path | Visibility/required toggles via Determinant (fast-path + DSL). Touches RegBB §4 + §6. |
| ADR-015 | Widget pass-through dispatch | MetaScreenElement synthesises stock Joget widgets. Touches RegBB §4. |

### §6.3 IM module + capability adapters

| ADR | Title | What it commits |
|---|---|---|
| ADR-016 | IM uses kernel not framework | IM rides on mm-form-gen; doesn't embed RegBB. Touches IM extension. |
| ADR-017 | mm_service namespace only for IM | IM's "service" namespace is internal, separate from RegBB §2. Touches IM extension. |
| ADR-020 | Capability adapter registry | Pluggable bot_pull / catalogue / decision adapters via mm_capability. Touches RegBB §6 + IM. |

### §6.4 Budget Engine (Lesotho extension)

| ADR | Title | What it commits |
|---|---|---|
| ADR-022 | Budget Engine as separate module | Separate plugin, separate concerns from RegBB §7. Touches Budget Engine extension. |
| ADR-023 | Commitment funnel state model | RESERVATION → COMMITMENT → EXPENSE state machine. Touches Budget Engine. |
| ADR-024 | mm_action + budget_event integration | Operator decisions emit budget events. Touches RegBB §7 + Budget Engine. |
| ADR-025 | Rule-based budget governance | Threshold automation as Determinant rules. Touches RegBB §6 + Budget Engine. |
| ADR-026 | Rule-to-SQL compiler | DSL → SQL for $registry.* references and budget rules. Touches RegBB §6. |

### §6.5 Lifecycle + async processing

| ADR | Title | What it commits |
|---|---|---|
| ADR-027 | Initial status assignment rule | DRAFT initial state, transition to SUBMITTED on submit_confirmation. Touches RegBB §4 + §7. |
| ADR-028 | Decision-to-status rule | DecisionMapper maps RoutingEvaluator outcomes to application status. Touches RegBB §7. |
| ADR-029 | Widget config overrides | mm_field.widgetConfig JSON allowlist per widget. Touches RegBB §4. |
| ADR-030 | Async submit processing | EligibilityProcessingWorker drains a queue; sync chain removed. Touches RegBB §4 + §6. |
| ADR-031 | Unified rule engine | One mm_determinant table, multiple consumers via scope column. Touches RegBB §6 + §8. |

### §6.6 Coverage observation

All 28 ADRs map to RegBB sections + the two Lesotho extensions. **No ADR touches the BBs we don't address** (Consent, external Identity BB, external Messaging BB, external Payments BB) — which is consistent with §4's verdicts of deferred / not addressed for those.

---

## §7 — Action register

Consolidated follow-ups across this conformance review. Prioritised by horizon and dependency.

### §7.1 Before UAT entry (solo-doable, ≤2 weeks of work)

| Action | From | Effort | Notes |
|---|---|---|---|
| Add a Consent BB record to `decision-log.md` as deliberate deferral with named trigger condition | §4.5 | 30 min | Trigger: first cross-ministry data exchange. |
| Add a Budget Engine framing decision to `decision-log.md` | §5.1 | 30 min | Position as finance-engine extension between RegBB §7 and Payment BB. |
| Add an IM module framing decision to `decision-log.md` | §5.2 | 30 min | Position as post-registration delivery extension. |
| Wire Khotso's SMTP credentials into EmailDispatcher when reply arrives | §3.13 | 0.5 day | Production-grade email. Awaiting Khotso. |
| Decide SMS path: full deferral or stub-with-real-template-bodies | §3.10 | 1 day | If full deferral, mark template SMS bodies "deferred" in markdown. |
| Stand up UAT instance with seeded fixtures | Solo plan W5 | 1 week | Top priority on resume. |
| Author `uat_entry_exit_criteria.md` (signable artefact) | Solo plan W8.3 | 1 day | Cite this document's §1.1 headline table. |
| Pick + configure defect tracker | Solo plan W7.1 | 1 day | Azure Boards default. |

### §7.2 During UAT (joint with MAFSN)

| Action | From | Effort | Notes |
|---|---|---|---|
| Keycloak realm provisioning + OIDC connector | §3.13 | 2-3 weeks | MAFSN-owned blocker. |
| Pen test + remediation | Roadmap §3.5 | 2-4 weeks | Run mid-UAT. |
| UAT scenarios re-run from `UAT_Guide.md` | UAT Guide | 1 week on-site | 15 scenarios. |
| Audit retention policy MAFSN sign-off | §3.8 | 1 day | Operational SOP from CLAUDE.md audit-retention section. |
| Backup/restore production drill | §3.13 | 1 day | First drill against prod-like UAT environment. |

### §7.3 H2 (post-UAT, RegBB API surface)

| Action | From | Effort | Notes |
|---|---|---|---|
| Systematic per-RegBB-service REST surface | §3.2, §3.4, §3.7, §3.8 | 2-3 weeks | `/api/services`, `/api/applications`, `/api/decisions`, `/api/audit`. |
| OpenAPI 3.x publication | §3.12 | 1 week | Annotations already present. |
| Tamper-evident audit (hash chain) | §3.8 | 1 week | Cryptographic chain over `audit_log`. |
| Operator retention SOP for documents | §3.5 | 0.5 day | Sign-off. |
| Submission backbone wired for `farmer_application` if multi-instance arrives | §3.4 | 1 week | Gated on second Joget instance. |
| Sesotho i18n scaffolding | §3.13 | 1 week | Scaffolding only; translation by MAFSN comms. |
| W4.4 Prometheus + Grafana POC | §3.13 | 2 weeks | Production observability. |

### §7.4 H3 (external BB connectors)

| Action | From | Effort | Notes |
|---|---|---|---|
| Identity BB connector (eGov ID delegation) | §3.3, §4.4 | 2-4 weeks | Gated on eGov ID availability in Lesotho. |
| Messaging BB connector (external) | §3.10, §4.2 | 2-3 weeks | Gated on national Notification BB. |
| Payment BB connector | §3.11, §4.3 | 4-8 weeks | Per scoping note's phased plan. |
| Information Mediator wiring | §4.1 | 2-4 weeks | Gated on second Joget instance. |
| Field-level redaction in operator views | §3.1 (Privacy by design) | 1 week | Per-role redaction config. |

### §7.5 Phase 3 retrospective (post-pilot)

| Action | From | Effort | Notes |
|---|---|---|---|
| Re-verify alignment against the next GovStack BB spec release | §2 | 1 week | If GovStack 2.1+ ships, refresh this document. |
| Consent BB integration if cross-ministry use case lands | §4.5 | 4-6 weeks | Trigger-conditional. |
| Decide Budget Engine future — propose as GovStack upstream candidate? | §5.1 | 1 day | Memo to GovStack working group. |
| Decide IM module future — same | §5.2 | 1 day | Same. |
| Cross-jurisdiction rule portability test | convergence §3.2 | 1 week | Validate fast-path rules port across RegBB peers. |

---

## §8 — Closing

The Farmers Portal in May 2026 is a substantially-conformant GovStack RegBB H1 implementation, with two well-defined Lesotho extensions (Budget Engine, IM module) and three explicit deferrals (external Identity BB, external Messaging BB, Payments BB) gated on FY27+ work. One genuine gap is Consent — defensible at pilot scope, real and named for post-pilot.

Of the seven RegBB services, all seven have working implementations. Of the three cross-cutting concerns (Workflow, Notifications, Payments), Workflow is conformant, Notifications is internally conformant (W2 closed the previous ❌ status), and Payments is the deferral. Of the cross-functional NFRs, performance is ✅ (W4 baseline + indexes), authorisation is ✅ (W1 RBAC), and the remaining 6 are 🟡 with named follow-ups in §7.

**The conformance accounting is closer to ✅ than the 2026-04-27 checklist suggested.** Three rows flipped from ❌ to ✅ (notifications, decision-notify-applicant, authorisation). Two domain extensions need explicit framing decisions before pilot launch (Budget Engine + IM module). One BB needs a deliberate-deferral decision logged (Consent). All other gaps are on the published H2/H3 roadmap.

UAT-entry is not gated on closing any further conformance gap. It is gated on the items in §7.1 — most of which are 30-minute decision-log entries, one of which is the W5 UAT instance + fixtures (the actual blocker), and two of which are awaiting Khotso (email + SMS gateway).

Phase 2 deliverable closed. The next document this informs is `uat_entry_exit_criteria.md` (Solo plan W8.3), which cites §1.1 of this document for the conformance-status entry criterion. Phase 3 (post-pilot retrospective) is not scheduled.

---

*Document v1, 2026-05-12. Phase 1 input: `docs/architecture/govstack-landscape-review-may2026.md`. Supersedes: `docs/architecture/regbb-conformance-checklist.md` (2026-04-27). Companion docs: `convergence-framework.md`, `architecture-overview.md`.*
