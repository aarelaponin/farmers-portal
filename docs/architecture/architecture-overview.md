# Farmers Portal — Architecture Overview

**Lesotho Agricultural Subsidy Management System (ASMS)**
**Alignment with the GovStack Registration Building Block**

| | |
|---|---|
| Status | Living document |
| Last updated | 2026-04-28 |
| Audience | Engineering, policy, GovStack reviewers |
| Owner | Farmers Portal architecture team |

---

## 1. Executive summary

The Farmers Portal is the digital front-door for Lesotho's agricultural subsidy programmes. It is being implemented on Joget DX 8.x with PostgreSQL, but the *architectural* intent is broader than a single Joget app: we are building toward a system that conforms to the **GovStack Registration Building Block (RegBB)** — that is, a registration system whose seven concerns (service catalog, identity verification, application submission, document submission, eligibility evaluation, decision management, and audit) are each implemented as a distinct, configurable, API-addressable component, composable with other GovStack Building Blocks (Identity BB, Notification BB, Payment BB) at well-defined boundaries.

We have the foundation, four cross-cutting platform services (`joget-status-framework`, `form-quality-runtime`, `identity-resolver-runtime`, `form-creator-api`), a working programme designer, farmer/parcel registries anchored on the National ID, a working dynamic application engine that seeds eligibility / benefits / document requirements from the chosen programme's spec, and a decision engine with grant issuance. The end-to-end loop has been demonstrated on `prog001 → AP-4140E0 → DECISION` audit. What remains for Horizon 1 is closing the wiring on capabilities that are *built but dormant on the application path*: the form-quality service for `farmer_application`, the RegBB-conformant submission backbone (the `WorkflowActivator → DocSubmitter → ProcessingServer` triad driven by `services.yml`), the DSL eligibility engine integration, and the snapshot-at-decision tool. None of those gaps require new plugin invention — only configuration and wiring.

The single most important architectural commitment we have already made — and the one this document is built around — is **configuration over code**: any new programme, rule, lifecycle, or document requirement is added by editing master-data forms and configuration tables, not by writing Java. The plugins we have built (and the ones we propose) are *generic engines* that read declarative configuration. This is precisely what the RegBB specification demands.

Two architectural reframings are baked into this revision. First, **the submission backbone (`WorkflowActivator → DocSubmitter → ProcessingServer`) is not auxiliary inter-instance plumbing — it is the central RegBB Application Submission mechanism**, async and workflow-orchestrated, with `{serviceId}.yml` as the machine-readable Service Catalog metamodel. This matters because in modern digital-government landscapes the citizen portal and the back-office (e.g. Ministry of Agriculture's case-worker workspace) are normally separate systems, integrated via stable API contracts; the submission backbone is what makes that decoupling architecturally real. Second, **eligibility evaluation has two complementary layers**, not one: citizen self-attestation during data entry (handled by `form-quality-runtime`), and authoritative server-side evaluation against the registry at submission (handled by the DSL rule engine — `rules-grammar` + `joget-rules-api` + `joget-rule-editor` + `subsidy-eligibility-runtime`). They serve different concerns, run at different lifecycle stages, and both are required for full RegBB conformance.

---

## 2. Where we are today — current state inventory

### 2.1 Foundation layer

* **Joget DX 8.x Enterprise** — form builder, datalist engine, workflow engine (XPDL 1.0), userview navigation, Dx8 Trimeda theme, OSGi plugin runtime
* **PostgreSQL** (Azure-managed) — all form tables, audit logs, master data
* **OpenJDK 11** runtime
* **Maven** for plugin builds (with a sandbox-friendly fallback `repack.sh` that uses bare `javac` against the local m2 cache)

### 2.2 Cross-cutting platform services (built — generic, domain-free)

These are the load-bearing engines for everything else. Each is intentionally agnostic to "farmers" or "subsidies": they operate on opaque entity types, rule libraries, configurations, or form definitions. Each occupies the architectural slot in which "configuration over code" is delivered. Today there are four, with a fifth ready to be lifted out of a domain-specific plugin (see §2.7).

**`joget-status-framework`** (`plugins/joget-status-framework`) — defines `EntityType` and `Status` interfaces, a transition map (allowed `from → to` pairs per entity type), and an audit table (`audit_log`) that captures every transition with timestamp, actor, and reason. Used by `gam-framework` (banking domain), `form-quality-runtime`, and `decision-engine-runtime`. The pattern is: a plugin registers its lifecycle once at bundle startup, then anywhere the plugin needs to move state it calls `StatusFramework.transition(...)`, which validates and audits in one operation.

**`form-quality-runtime`** (`plugins/form-quality-runtime`, currently `build-006`) — a generic rule engine wired to Joget's form post-processor hook. Capabilities: SQL rule evaluation against form tables (rule SQL with `#recordId#` and `#formId#` placeholders, returns ≥1 row → rule fires); severity-aware issue persistence (`qa_issue`); per-record status (`qa_record_status`); status-transition gating (`qa_gate`); audit trail; reusable banner element (`QualityBannerElement`) that renders the inline status pill. Configuration lives in seven admin forms (`qa_service`, `qa_tab`, `qa_rule`, `qa_gate`, `qa_issue`, `qa_record_status`, `audit_log`) and is editable by any Joget operator without code changes.

Four services are registered; three are live, the fourth is configured but dormant:

| Service ID | Primary form | Rules | Gate (status blocks) | State |
|---|---|---|---|---|
| `farmers_subsidy` | `spProgramMain` | 5 (4 ERROR, 1 WARNING) | `APPROVED, ACTIVE` | Live — `prog001` shows `verified` |
| `farmer_registration` | `farmerRegistrationForm` | 5 (4 ERROR, 1 WARNING) | `ACTIVE, VERIFIED` | Live |
| `parcel_registration` | `parcelRegistration` | 5 (4 ERROR, 1 WARNING) | `ACTIVE, VERIFIED` | Live |
| `farmer_application` | `spApplication` | 6 (5 ERROR, 1 WARNING) | `SUBMITTED, UNDER_REVIEW, APPROVED` | Configured but dormant — `FormQualityPostProcessor` not yet wired on application forms (see §6.1 Horizon 1 closeout) |

**`identity-resolver-runtime`** (`plugins/identity-resolver-runtime`, currently `build-014`) — a configurable identity resolver. Given a foundational identifier (in our case, National ID), it loads the matching record from a designated source form, walks any chained subforms via configured field maps, and projects the resolved data into the calling form's fields. Configuration lives in two admin forms: `app_resolver_config` (one row per resolver, defining source form, lookup field, multiple-matches policy, not-found action URL) and `app_resolver_field_map` (per-resolver field mappings). Exposes both an in-Joget element (`IdentityResolverElement`) for form rendering and a REST endpoint for external callers. One resolver in production today (`farmerByNid`, 8 field mappings to `farmerBasicInfo`); adding a new resolver is one row in `app_resolver_config` plus a few field-map rows, no Java change.

**`form-creator-api`** (`plugins/form-creator-api`) — REST surface for deploying form, datalist, and userview definitions through Joget's `FormDefinitionDao` (which evicts both the AppDefinition cache and the per-form Hibernate ORM mapping atomically). Endpoint: `POST /jw/api/formcreator/formcreator/forms`. Upsert by `formId`. This is the deployment infrastructure that lets the project obey the no-raw-SQL rule documented in `CLAUDE.md`: every form change goes through this API rather than `UPDATE app_form`, sidestepping the two-cache silent-data-loss trap that Joget DX 8 has on direct DB writes. Functionally platform-tier and listed here accordingly, even though it is a recent addition to the canonical stack.

### 2.3 Domain layer (built — Lesotho-specific)

**Programme designer** (`spProgramMain` + 9 tab subforms) — a `MultiPagedForm` wizard that captures: identity (name, type, season, campaign), timeline & budget, geography (districts), beneficiary target groups, benefit items (with units, subsidy rates, per-farmer caps), eligibility criteria, approval rules, monitoring KPIs, and document requirements. Each programme is, in RegBB vocabulary, a *registrable service*. The data model is rich enough to drive a dynamic application form, and as of this revision it does so via the application engine described below.

**Farmer registry** (`farmerRegistrationForm` + 7 tab subforms) — household-level farmer record anchored on the National ID. Tabs: basic info, residency, agricultural activities, household members, crops & livestock, income & programmes, declaration. Companion forms for parcels held by the farmer.

**Parcel registry** (`parcelRegistration` + tab subforms) — parcel records with full GIS polygon capture, classification, crops grown. Linked to farmer via FK.

**Application engine** (`plugins/application-engine-runtime`, currently `build-012`) — owns the dynamic application form's runtime behaviour. Three responsibilities:

* `ProgrammeSpecReader` reads the chosen programme's spec from `sp_program` plus its tab subforms (eligibility, benefits, documents) into an in-memory `ProgrammeSpec` value object. The reader joins `md23documentType` for document snapshot fields (label, accepted formats, max size), with override-wins semantics per field.
* `SeedingService` seeds the application's three child grids (eligibility check, benefit request, document request) with one row per programme criterion / benefit / document requirement. Idempotent — keyed on parent_id existence in each child table; already-seeded children are skipped on subsequent runs.
* `SeedingTabLoadBinder` (load-side, fires on every tab render) and `ApplicationSeedingTool` (post-processor, fires on every save) provision the three tab subform records on first navigation, mirror their ids onto the wrapper's `tab_*` columns, auto-generate `application_code` (`AP-XXXXXX`), and call `SeedingService.seed(...)` to populate child grids.

**Application wizard** (`spApplication` + 5 tab subforms + 3 grid row forms) — the citizen-facing 6-page wizard. Tab 1 Applicant & Programme (with Identity Resolver), Tab 2 Eligibility (self-attestation grid over `spApplicationEligibility`), Tab 3 Benefits requested (grid over `spApplicationBenefitReq`), Tab 4 Documents (grid over `spApplicationDocV2`, supersedes the legacy static `spApplicationDoc`), Tab 5 Declaration, Tab 6 Decision (operator-only). End-to-end demo loop runs against `prog001`: applicant data resolved by NID, eligibility / benefits / documents tabs seeded automatically, decision recorded, grant issued.

**Decision engine** (`plugins/decision-engine-runtime`, currently `build-007`) — applies the operator's decision (APPROVED with optional reduction, REJECTED with reason, NEEDS_INFO), issues a row in `imEntitlement` keyed `EN-<6-char-app-prefix>` with one `imEntitlementItem` per benefit (soft-cap policy: operator's `approved_qty` wins, fallback to `requested_qty`), and writes a `DECISION` audit row. Live: `audit_log` shows decision activity. The snapshot-at-decision tool (architecture §5.5 #5) is named in the design but not yet built (see §6.1 Horizon 1 closeout).

**Master data** — 100+ MD lookups (districts, villages, crops, livestock types, agro-ecological zones, target categories, document types, document statuses, rejection reasons, etc.) all created from form deployment, all editable through standard Joget UI. Notable additions for the documents flow: `md23documentType` (14 document types with allowed formats and max sizes), `md64documentStatus` (7 lifecycle states: MISSING, PENDING, UPLOADED, VERIFIED, REJECTED, RESUBMIT, EXPIRED), `md65docRejReason` (7 rejection reasons).

### 2.4 DSL eligibility engine sub-system (built — dormant on application path)

Eligibility evaluation has two complementary layers (see also §3.1 and §5.3): citizen self-attestation during data entry, handled by `form-quality-runtime`; and authoritative server-side evaluation against the registry at submission, handled by a dedicated DSL rule engine. The DSL stack is a fully-developed sub-system of four plugins, all built and partially deployed:

* **`rules-grammar`** — ANTLR 4 parser for the *Rules Script DSL*. Pure parser/AST; no Joget dependency.
* **`joget-rules-api`** — REST API plugin. Compiles DSL → SQL via `RuleScriptCompiler`, manages rulesets in `app_fd_jreruleset`, exposes field-scope management against `app_fd_jrefieldscope` and `app_fd_jrefielddefinition`. Endpoints under `/jw/api/jre/jre/...` (notably `loadRuleset?contextType=PROGRAM&contextCode=<programCode>` and `compile`).
* **`joget-rule-editor`** — admin authoring UI. The operator-facing editor for Rules Script DSL: validates syntax in-place, compiles on save, supports test-against-sample-applicant. This is the operator UX for non-trivial eligibility rules — far better than the flat field/operator/value criteria available in the programme designer's eligibility tab.
* **`subsidy-eligibility-runtime`** — the per-applicant evaluator. Workflow tool plugin (`EligibilityRuntime`) that loads a programme's bound ruleset over JRE REST, compiles to SQL, runs the SQL filtered to one applicant, persists per-rule pass/fail to `spEligRuleResult` and overall to `spEligResult`, sets workflow variable `eligibilityDecision = PASS | FAIL | ERROR`.

The schema is in place: `app_fd_jreruleset`, `app_fd_jrefielddefinition`, `app_fd_jrefieldscope`, `spEligResult`, `spEligRuleResult` all exist as forms with their tables. What's missing is configuration: zero rulesets bound to programmes, zero rows seeded into `jreFieldDefinition` for the `FARMER_APPLICATION` scope, and no workflow exists in which `EligibilityRuntime` could run. Integration plan in §6.1 Horizon 1 closeout.

The relationship to flat criteria: simple programmes use only the flat criteria captured in `sp_elig_criterion` (programme designer's eligibility tab) for citizen self-attestation. Complex programmes also link a DSL ruleset for authoritative server-side evaluation. The two stacks are complementary and target different criteria patterns:

* *"You have not received subsidy from another programme this season"* — citizen-attested only (registry can't reliably check)
* *"Your registered parcel is ≤ 2 ha"* — server-side only (we don't ask the citizen, we look it up)
* *"You are an active farmer"* — both layers, with discrepancy as a case-worker review flag

### 2.5 RegBB Application Submission backbone (built — dormant for `farmer_application`)

The submission backbone is the canonical RegBB-conformant mechanism for transmitting a citizen's submitted application: async, workflow-orchestrated, schema-driven, with a stable HTTP contract. It is a three-plugin triad driven by per-service YAML metamodels. **It is not auxiliary inter-instance plumbing.** It is the central submission mechanism — the layer between citizen-facing data entry and back-office processing — and is critical to the architectural reality that in modern digital-government landscapes the citizen portal and the back-office (e.g. AgriMin's case-worker workspace) are normally separate systems integrated via stable API contracts.

The triad:

* **`wf-activator`** — Post Processing Tool plugin. Wired on a source form's `postProcessor` slot. Sets the workflow variable `serviceId`, validates its format, starts the convention-named `{serviceId}_submission` workflow process. Single configuration point.
* **`doc-submitter`** — workflow Tool activity that runs *inside* the started process. Reads `serviceId` from workflow variables, loads the corresponding `{serviceId}.yml` from its bundled resources, extracts form data per the YAML's field mappings, transforms each field per the YAML's type rules (date / number / checkbox / master-data lookup / yes-no normalisation), POSTs the resulting GovStack-conformant JSON to the receiver's endpoint. Despite the name, "documents" here is GovStack-spec language for *structured payloads*, not files.
* **`processing-server`** — REST receiver. Endpoint `/services/{serviceId}/applications`. Extracts `serviceId` from URL path, loads the same `{serviceId}.yml` (must match between sender and receiver), validates the inbound JSON's structure, maps fields back to Joget form rows, persists via `FormDataDao.saveOrUpdate`, optionally starts a workflow on the receiving side.

The Service Catalog metamodel — `{serviceId}.yml` — captures: service identity (id, name, version, GovStack-version compatibility); entity model (e.g., `Person` with identifier types `NationalId`, `FarmerRegistrationNumber`, `BeneficiaryCode`); service-level metadata (which fields are master-data references, normalisation rules); per-form mappings (form id, table name, primary key, UUID reference field, field-by-field translation between Joget field ids and GovStack JSON paths). This is RegBB Service Catalog content in machine-readable form, complementary to the *business-facing* Service Catalog stored in the programme designer.

State today: the triad is built and proven (`farmers_registry.yml` is fully written and tested; the receive endpoint operates). For the `farmersPortal` application path — `spApplication` — the triad is **completely unwired**: no `farmer_application.yml` exists, no `WorkflowActivator` is wired on any application form, no `farmer_application_submission` workflow package exists (the four packages on the dev server belong to other apps). Wiring this is one of the four remaining Horizon-1 commits (see §6.1).

### 2.6 Custom form / datalist elements (built — already in production)

Reusable form-builder building blocks. Each is generic — not domain-specific — and consumed by domain forms via classNames in JSON. None of these are platform-tier engines (they don't consume admin-side configuration); they are widget-tier components.

| Plugin | What it provides |
|---|---|
| `joget-gis-server` + `joget-gis-ui` | Backend REST + Leaflet polygon-capture form element (used on parcel geometry) |
| `joget-concat-field` | `ConcatFieldElement` — derive a concatenated value from N source fields |
| `joget-smart-search` | `SmartSearchElement` — typeahead search with configurable display columns, filter chains, fuzzy match |
| `embedded-datalist` | Read-only inline datalist within a form, filterable by parent fields |
| `joget-advanced-filters` | `CascadingMdmSelectFilterType`, `DateRangeFilterType` for datalist filter UX |
| `joget-lookup-field` | `LookupFieldElement` — built but **not installed** in this Joget instance; do not use in new forms |

### 2.7 Pending generalisation: `derived-snapshot-runtime`

`farmer-derived-plugin` (`plugins/farmer-derived-plugin`) is currently a domain-specific workflow tool: reads a hardcoded list of farmer-related forms, projects computed values into the `spFarmerDerived` snapshot table keyed by `farmerCode`. The mechanism (read N source forms, project derived values into a snapshot) is generic; only the implementation is domain-specific. Generalisation work — rename to `derived-snapshot-runtime`, package as `global.govstack.derived.*`, lift hardcoded source-form list / target form / upsert key / derivation rules into two new admin forms (`app_derived_config` + `app_derived_field_map`) parallel to identity-resolver's pattern — is queued for after Horizon-1 closeout, but should land before any second domain implements its own hardcoded `*-derived` plugin. Once generalised, this becomes the fifth platform-tier configurable engine.

### 2.8 What works today

End-to-end demo loop runs against `prog001 / PRG-2025-001`:

1. A farmer record can be created, the form-quality engine validates it on every save, the QUALITY pill shows status in real time, the gate prevents `ACTIVE` while ERRORs exist.
2. A programme can be designed via `spProgramMain` (now 9 tabs including documents), the form-quality engine validates it, the gate prevents `APPROVED, ACTIVE` while ERRORs exist. `prog001` has 4 eligibility criteria, 3 benefit items (SEED-MAIZE, FERT-NPK, FERT-UREA at 80% subsidy), 3 document requirements (NATIONAL_ID, LAND_TITLE, HARVEST_RECORDS), and `qa_record_status` shows `verified`.
3. The identity resolver pulls farmer registry data into the application's applicant tab when the citizen enters their National ID. One resolver in production (`farmerByNid`) with 8 field mappings.
4. The application engine seeds the application's eligibility, benefits, and documents tabs from the chosen programme's spec on first navigation. `AP-4140E0` against `prog001` has 4 eligibility-check rows, 3 benefit-request rows, 3 document rows correctly seeded.
5. The decision engine applies the operator's decision and writes a `DECISION` audit row. Two such rows exist on the dev server — proof the plugin has fired in earnest.
6. Status transitions are tracked through `joget-status-framework`'s shared `audit_log`.

Five rule libraries operate in parallel — one per registered service in the form-quality engine, three live and one configured-but-dormant — proving the engine is generic. An operator can author a new rule by inserting a row in `qa_rule` (no code change). An operator can author a new programme using only Joget forms (no Java change). An operator can author a new identity resolver by inserting one row in `app_resolver_config` plus a few field-map rows (no Java change).

### 2.9 What does not yet exist

* **Form-quality wiring on `farmer_application`** — the service is registered with 6 rules and a gate, but `FormQualityPostProcessor` is wired on zero application forms (each application form has `ApplicationSeedingTool` as its sole post-processor; Joget supports only one per form). The 6 rules are dormant. Closing this needs `MultiTools` chaining the two post-processors plus a rewrite of the four mismatched-recordId rules (see §6.1 Horizon 1 closeout).
* **Submission backbone wiring for `farmer_application`** — the triad in §2.5 is built and proven for `farmers_registry`, but `farmer_application.yml` doesn't exist, no `WorkflowActivator` is wired, no `farmer_application_submission` workflow package exists. RegBB-conformant submission is therefore not yet operational on the application path.
* **DSL eligibility engine integration on application path** — the DSL stack (§2.4) is dormant. Five integration layers needed: programme designer can pick a ruleset; field registry seeded for `FARMER_APPLICATION`; at least one ruleset bound to `prog001`; workflow Tool activity wiring; case-worker review UI.
* **Snapshot-at-decision tool** — architecture §5.5 #5 names it; not built. On `DRAFT → SUBMITTED`, this should freeze resolver-populated values into immutable application columns so the decision is reviewable years later against unchanged inputs.
* **Audit spine — data-change events** — only status transitions are captured today (3 rows total in `audit_log`). Data-change events not captured.
* **Systematic REST API surface** — partial: endpoints exist for form deployment (`form-creator-api`), rule engine (`joget-rules-api`), submission (`processing-server`), identity resolution (`identity-resolver-runtime`), GIS (`joget-gis-server`). The systematic per-RegBB-service surface (`/api/services`, `/api/applications`, `/api/decisions`, `/api/documents`, `/api/audit`) does not yet exist, nor does an OpenAPI specification.
* **Notifications** — `spNotification` and `spNotifTemplate` exist as table stubs; no `notification-dispatcher-runtime` plugin yet.
* **External BB connectors** — no integration with GovStack Identity BB, Notification BB, or Payment BB.

---

## 3. The GovStack Registration BB target

RegBB is a specification, not an implementation. It decomposes "registration" into seven services and three cross-cutting concerns, each of which a conforming system must address through a configurable, API-addressable component.

### 3.1 The seven RegBB services

| Service | What it does | How we deliver it | Why it matters |
|---|---|---|---|
| **Service Catalog** | Stores the set of registrable services and their data schemas | Two complementary views: business-facing programme designer (`spProgramMain`); machine-facing `{serviceId}.yml` consumed by the submission backbone | A new programme is just a new catalog entry, not a new app |
| **Identity Verification** | Confirms an applicant's foundational identity and pulls existing data | `identity-resolver-runtime` driven by `app_resolver_config` + `app_resolver_field_map` admin forms | "Once-only" principle — citizen never re-types known data |
| **Application Submission** | Generates and accepts dynamic forms based on the chosen service's schema | Two layers: (a) citizen data entry — `application-engine-runtime` seeds the dynamic wizard from the programme spec; (b) RegBB-conformant transmission — `WorkflowActivator → DocSubmitter → ProcessingServer` triad driven by `services.yml` | Adding a programme requires zero front-end code; back-office can be a separate system |
| **Document Submission** | Collects, stores, and validates the required documentary artifacts | Per-programme document requirements seeded onto each application from `sp_doc_requirement_row` into `sp_application_doc_v2`, with `md23documentType` / `md64documentStatus` / `md65docRejReason` master data | Each service declares its own doc requirements |
| **Eligibility Evaluation** | Runs rules over applicant + programme + supporting data | Two complementary layers: (a) citizen self-attestation — flat criteria in `sp_elig_criterion` validated by `form-quality-runtime`; (b) authoritative server-side — DSL rule engine (`rules-grammar` + `joget-rules-api` + `joget-rule-editor` + `subsidy-eligibility-runtime`) evaluating against registry data at submission | Pluggable rule engine, decisions are auditable, two layers handle different concerns |
| **Decision Management** | Approve/reject/grant lifecycle, with reasoning | `decision-engine-runtime` — decision tools, grant issuance into `imEntitlement`, audit row writers | Defensible, transparent, machine-readable decisions |
| **Audit & Observability** | Captures every state change and data event | `joget-status-framework` writes to shared `audit_log` on every transition; consumers include decision engine and form-quality engine | Required for citizen trust, regulatory review, debug |

### 3.2 The three cross-cutting concerns

* **Workflow** — orchestrates the lifecycle (draft → submitted → under review → decided → granted/rejected)
* **Notifications** — communicates with applicants at every transition
* **Payments** — disburses subsidies on approved applications

### 3.3 What "RegBB-conformant" actually means

Three concrete tests:

1. **Configuration-not-code** — adding a new service to the catalog must not require a Java change or a deploy
2. **API-addressable** — every service exposes a stable REST contract that any UI (mobile, kiosk, web, USSD adapter, batch processor) can consume
3. **Composable with other BBs** — Identity Verification can call out to a foundational Identity BB; Notifications can hand off to a Notification BB; Payments to a Payment BB. The boundaries are clean enough to swap implementations.

---

## 4. Gap analysis — where each RegBB service stands

| RegBB service | Current implementation | Status | Remaining gap |
|---|---|---|---|
| Service Catalog (business-facing) | Programme designer (`spProgramMain` + 9 tabs) | ✅ strong | None for the business-facing side |
| Service Catalog (machine-facing) | `{serviceId}.yml` consumed by submission backbone | 🟡 partial | Only `farmers_registry.yml` written; `farmer_application.yml` and others missing |
| Identity Verification | `identity-resolver-runtime` + `farmerByNid` config | ✅ strong | Deepen second-resolver use cases as more domains arrive |
| Application Submission (citizen data entry) | `application-engine-runtime` build-012 | ✅ strong | None — end-to-end demo runs |
| Application Submission (RegBB transmission) | Submission backbone built; `farmers_registry.yml` proven; **unwired for `farmer_application`** | 🟡 partial | Author `farmer_application.yml`, wire `WorkflowActivator`, author `farmer_application_submission` workflow package |
| Document Submission | `spApplicationDocV2` seeded from programme spec; FileUpload widget; MD23/MD64/MD65 master data live | ✅ strong | Document payload transmission flows through the same submission backbone once that's wired |
| Eligibility Evaluation (citizen self-attestation) | Flat criteria in `sp_elig_criterion`; form-quality service `farmer_application` registered with 6 rules | 🟡 partial | `FormQualityPostProcessor` not wired on application forms; rules dormant |
| Eligibility Evaluation (server-side authoritative) | DSL stack built (`rules-grammar` + `joget-rules-api` + `joget-rule-editor` + `subsidy-eligibility-runtime`); schema in place | 🟡 partial | Five integration layers needed (programme link, field registry seed, ruleset for `prog001`, workflow Tool wiring, case-worker UI) |
| Decision Management | `decision-engine-runtime` build-007; `DecisionStoreBinder`, `DecisionRunner`, `EntitlementGenerator`, `AuditLogger`; live (2 DECISION audit rows) | 🟡 partial | Snapshot-at-decision tool not built |
| Audit & Observability | `audit_log` via `joget-status-framework`; status transitions captured | 🟡 partial | Data-change events not captured |
| Workflow | XPDL engine in Joget (capable, unused for `farmersPortal`) | 🟡 latent | Need `farmer_application_submission` process definition |
| Notifications | Table stubs (`spNotification`, `spNotifTemplate`) | ❌ missing | Need a notification dispatcher plugin |
| Payments | Not started | ❌ missing | Lowest priority — gated on decisions being real first |
| REST API surface | Partial: `form-creator-api`, `joget-rules-api`, `processing-server`, `identity-resolver-runtime`, `joget-gis-server` all expose endpoints | 🟡 partial | Systematic per-RegBB-service surface (`/api/services`, `/api/applications`, `/api/decisions`, `/api/documents`, `/api/audit`) and OpenAPI spec missing |

The three pivotal gaps from the original document — dynamic application form, identity verification, decision management — are **closed or substantially closed**. The remaining gaps form a tighter list, all closeable in Horizon 1:

1. **Form-quality wiring on `farmer_application`** — closes the loop on the rule library that has been seeded but never fires. Smallest commit.
2. **Submission-backbone wiring for `farmer_application`** — closes the loop on RegBB-conformant async submission. Most architecturally significant remaining gap because it's what makes the citizen portal / back-office decoupling real.
3. **DSL eligibility engine integration** — turns on the operator-facing rule-authoring UX and authoritative server-side eligibility evaluation. Five layers, the first three of which can land independently.
4. **Snapshot-at-decision + audit-spine extension** — completes audit defensibility.

After those four, Horizon 1's success criterion (§6.1) is honestly green and the focus moves to Horizon 2's systematic REST API surface.

---

## 5. Target architecture

### 5.1 Layered shape

```
╔══════════════════════════════════════════════════════════════════════╗
║  CITIZEN INTERFACES                       OPERATOR / BACK-OFFICE      ║
║  Farmer portal (Joget userview)           Case worker workspace       ║
║  Mobile / kiosk / USSD adapter            Programme designer / admin  ║
║  (consume REST API)                       (Joget App Composer + portal│
║                                            — possibly a separate      ║
║                                            Joget instance, e.g.       ║
║                                            AgriMin back-office)       ║
╚══════════════════════════════════════════════════════════════════════╝
                                  ↓ HTTPS
╔══════════════════════════════════════════════════════════════════════╗
║  REST API GATEWAY                                                    ║
║  /api/services            /api/identity/resolve   /api/applications  ║
║  /api/eligibility         /api/decisions          /api/documents     ║
║  /api/notifications (out) /services/{id}/applications (RegBB submit) ║
║  /api/jre/...             /api/audit                                 ║
╚══════════════════════════════════════════════════════════════════════╝
                                  ↓
╔══════════════════════════════════════════════════════════════════════╗
║  REGBB DOMAIN PLUGINS  (each a separate OSGi bundle)                 ║
║                                                                      ║
║  ┌──────────────┐ ┌─────────────┐ ┌──────────────────────────────┐   ║
║  │ Service      │ │ Identity    │ │ Application Engine           │   ║
║  │ Catalog      │ │ Resolver    │ │ (seeds dynamic forms from    │   ║
║  │ (programme   │ │ (NID lookup,│ │  service spec; runs at       │   ║
║  │ designer +   │ │ field map)  │ │  application creation)       │   ║
║  │ services.yml)│ │             │ │                              │   ║
║  └──────────────┘ └─────────────┘ └──────────────────────────────┘   ║
║                                                                      ║
║  ┌──────────────┐ ┌─────────────┐ ┌──────────────────────────────┐   ║
║  │ Form Quality │ │ DSL Eligi-  │ │ Decision Engine              │   ║
║  │ Engine       │ │ bility      │ │ (decision tools + grant      │   ║
║  │ (data-entry  │ │ Engine      │ │  issuance + entitlement)     │   ║
║  │ validation,  │ │ (server-    │ │                              │   ║
║  │ self-attest) │ │ side eval)  │ │                              │   ║
║  └──────────────┘ └─────────────┘ └──────────────────────────────┘   ║
║                                                                      ║
║  ┌──────────────────────────────────┐ ┌─────────────────────────┐    ║
║  │ Submission Backbone              │ │ Notification Dispatcher │    ║
║  │ (WorkflowActivator → DocSubmitter│ │ (email / SMS / push —   │    ║
║  │  → ProcessingServer driven by    │ │  Horizon 2)             │    ║
║  │  {serviceId}.yml metamodel)      │ │                         │    ║
║  └──────────────────────────────────┘ └─────────────────────────┘    ║
╚══════════════════════════════════════════════════════════════════════╝
                                  ↓
╔══════════════════════════════════════════════════════════════════════╗
║  CROSS-CUTTING PLATFORM ENGINES (built — generic, configurable)      ║
║                                                                      ║
║   joget-status-framework | form-quality-runtime |                    ║
║   identity-resolver-runtime | form-creator-api |                     ║
║   (future: derived-snapshot-runtime)                                 ║
║                                                                      ║
║         ┌──────────────────────────────────────────────────┐         ║
║         │  AUDIT SPINE                                     │         ║
║         │  status transitions + data-change events         │         ║
║         │  (single audit_log, queryable, append-only)      │         ║
║         └──────────────────────────────────────────────────┘         ║
╚══════════════════════════════════════════════════════════════════════╝
                                  ↓
╔══════════════════════════════════════════════════════════════════════╗
║  FOUNDATION                                                          ║
║  Joget DX 8.x Enterprise + PostgreSQL                                ║
║  (forms · datalists · workflow · userview · OSGi runtime)            ║
╚══════════════════════════════════════════════════════════════════════╝
                                  ↑
╔══════════════════════════════════════════════════════════════════════╗
║  EXTERNAL BB CONNECTORS (Horizon 3)                                  ║
║  Identity BB · Notification BB · Payment BB · DRC / x-Road exchange  ║
║  (the Submission Backbone above already speaks the contract these   ║
║   external systems would consume — same triad either targets our    ║
║   own ProcessingServer or a peer government's RegBB receiver)        ║
╚══════════════════════════════════════════════════════════════════════╝
```

Two notable shifts from the prior diagram:

* **Eligibility now appears as two boxes**, not one. Form Quality (data-entry validation, citizen self-attestation) and DSL Eligibility (server-side authoritative evaluation) are separate engines running at different lifecycle stages — see §3.1 and §5.3 for how they interlock.
* **The Submission Backbone now appears as an explicit RegBB-domain plugin**, sitting alongside the other domain plugins. It is the layer that transmits a submitted application from the citizen-facing Joget to the back-office (potentially a separate Joget instance or a non-Joget receiver implementing the same contract). It was missing from the prior diagram entirely.

### 5.2 Component boundaries — one plugin per RegBB service

Each domain plugin lives at `plugins/<service-name>-runtime/` and has a consistent shape:

* `Activator.java` — registers OSGi services on bundle start, prints `build-NNN @ timestamp` log line for traceability
* `Build.java` — generated build stamp, surfaced in plugin descriptions
* `service/` — pure-Java domain logic, no Joget API in signatures
* `hook/` — Joget post-processor / store binder integrations
* `element/` — Joget form-builder elements (drag-and-drop UX in App Composer)
* `api/` — `ApiPluginAbstract`-based REST endpoints (Horizon 2)
* `deploy/repack.sh` — sandbox-friendly build helper
* `src/main/resources/forms/` — admin forms for plugin configuration (deployed via JWA)

Why one bundle per service: independent deploy cycles, independent versioning, independent failure isolation, and a clean mapping from RegBB spec to code.

The submission-backbone plugins (`wf-activator`, `doc-submitter`, `processing-server`) and the DSL eligibility plugins (`rules-grammar`, `joget-rules-api`, `joget-rule-editor`, `subsidy-eligibility-runtime`) predate this naming convention and are kept under their original names rather than renamed to fit. Future plugin authoring should follow the `-runtime` convention; existing plugins are not in scope for renaming.

### 5.3 Data flow for a single farmer application

Walking the canonical citizen path through the layers:

1. Farmer enters National ID → `IdentityResolverElement` calls `/api/identity/resolve?config=farmerByNid&value=...`
2. Resolver queries `farmerBasicInfo` + chained subforms, returns mapped fields
3. Form auto-fills applicant section (read-only after resolve)
4. Farmer picks programme via `SmartSearchElement` → fires `/api/services/<programmeId>`
5. `application-engine-runtime`'s `SeedingTabLoadBinder` and `ApplicationSeedingTool` read the programme's eligibility / benefits / document-requirement child rows; INSERT corresponding seed rows in three application child tables (`sp_application_elig_chk`, `sp_application_ben_req`, `sp_application_doc_v2`). Document requirements are joined against `md23documentType` for snapshot fields (label, formats, max size).
6. Farmer fills in eligibility self-attestation, requests benefit quantities, uploads documents, signs declaration.
7. `form-quality-runtime` evaluates rules on every save; QUALITY pill shows status; status-gate prevents `DRAFT → SUBMITTED` while ERRORs exist. (For `farmer_application` this is configured but currently dormant — see §6.1 Horizon 1 closeout.)
8. On `DRAFT → SUBMITTED` transition: snapshot tool copies current resolved values into immutable application columns (audit-faithful). *Tool not yet built.*
9. **Submission backbone fires.** `WorkflowActivator` (post-processor on `spApplication`) starts the `farmer_application_submission` workflow process. The process runs `DocSubmitter` as a Tool activity, which loads `farmer_application.yml`, extracts form data, transforms to GovStack JSON per the field mappings, POSTs to `/services/farmer_application/applications`. `ProcessingServer` receives, validates structure against the same YAML, persists. *Triad built and proven on `farmers_registry`; not yet wired for `farmer_application`.*
10. **DSL eligibility evaluation fires** as a Tool activity in the same workflow. `EligibilityRuntime` loads the programme's bound ruleset over JRE REST, compiles to SQL, runs filtered to one applicant, persists per-rule pass/fail in `spEligRuleResult` and overall in `spEligResult`, sets workflow variable `eligibilityDecision`. *DSL stack built; not yet wired into application path.*
11. Workflow routes to case worker. Operator reviews citizen self-attestation (Tab 2 / `spApplicationEligibility`) alongside server-side evaluation (`spEligRuleResult`), with discrepancies flagged. Decides via `spApplicationDecision` tab. `decision-engine-runtime`'s `DecisionRunner` applies approve/reject, `EntitlementGenerator` creates a row in `imEntitlement` with one `imEntitlementItem` per benefit (soft-cap policy: operator's `approved_qty` wins, fallback to `requested_qty`), `AuditLogger` writes a `DECISION` audit row.
12. `NotificationDispatcher` sends email/SMS at every transition; row written to `audit_log`. *Plugin not yet built.*

Every arrow in that flow is either an existing capability or a clearly-scoped Horizon-1 component. None of it requires reimagining the system. The italicised "*not yet*" markers are the four Horizon-1 closeout commits enumerated in §6.1.

### 5.4 Naming conventions

* **Plugin bundles** — `plugins/<reg-bb-service>-runtime/`, e.g. `identity-resolver-runtime`, `application-engine-runtime`, `decision-engine-runtime`
* **Service IDs** (in `qa_service` and elsewhere) — `snake_case`, semantic: `farmer_registration`, `parcel_registration`, `farmers_subsidy`, `farmer_application`
* **Form IDs** — ≤24 chars (Joget table-name limit), e.g. `spApplication`, `spApplicationDoc`
* **Plugin classes** — `<Verb><Noun>Element` for form elements, `<Verb><Noun>PostProcessor` for hooks, `<Noun>Service` for domain logic, `<Noun>Repository` for persistence
* **Config tables** — `app_<concept>_config` for the master, `app_<concept>_<sub>` for child rows
* **REST paths** — `/api/<reg-bb-service>/<resource>`, lower-kebab-case, versioned later via `/v1/`

### 5.5 Design principles

1. **Configuration over code.** Adding a programme, a rule, a field mapping, a notification template — all done through Joget master-data forms. Java is reserved for the *engines* that consume configuration.
2. **Generic-then-specialise.** Build the platform service generic (e.g. `joget-status-framework`), then layer the domain on top (`form-quality-runtime` registers `FORM_QUALITY_ISSUE` lifecycle). Every new plugin should ask: is there a generic abstraction here that could serve other domains?
3. **Audit by default.** Every plugin writes to the shared audit spine. No silent state changes.
4. **API-first for service surfaces.** Every service plugin exposes both an in-Joget hook (for the Joget UX) and a REST endpoint (for everything else). Same logic, two doors.
5. **Snapshot at decision time.** Live data while in `DRAFT`; frozen at `SUBMITTED`. Audit-faithful: 5 years from now, the decision is reviewable against exactly the data it was made on.
6. **Build counter visible.** Every plugin's description carries `build-NNN @ timestamp` so operators can confirm the deployed JAR matches the code in source.

---

## 6. Three-horizon roadmap

### 6.1 Horizon 1 — internal completeness (next 4–8 weeks)

**Goal:** demonstrate one farmer subsidy programme end-to-end, from programme design through application submission, eligibility evaluation, decision, and grant — with no Java needed for any new programme.

**Deliverables (status as of 2026-04-28):**

* ✅ `identity-resolver-runtime` plugin — `IdentityResolverElement`, `ResolverService`, `app_resolver_config` + `app_resolver_field_map` master data. **Built (build-014), live, one config in production.**
* ✅ `application-engine-runtime` plugin — `ApplicationSeedingTool` post-processor, `SeedingTabLoadBinder` load-side hook, three application child tables (eligibility, benefits, documents), `spApplication` wizard with resolver and seeding wired. **Built (build-012), live, end-to-end demo runs against `prog001 → AP-4140E0`.**
* ✅ `decision-engine-runtime` plugin — approve / reject decision tools, grant issuance into `imEntitlement`. **Built (build-007), fires (2 DECISION audit rows on dev server).**
* 🟡 **Form-quality wiring on `farmer_application`** — service registered with 6 rules and a gate, but `FormQualityPostProcessor` not wired on application forms. *Closeout: chain via `MultiTools` after `ApplicationSeedingTool` on `spApplication` wrapper; rewrite the four mismatched-recordId rules to be wrapper-aware with joins; add the documents rule. ~1 hour.*
* 🟡 **Submission backbone wiring for `farmer_application`** — triad built and proven for `farmers_registry`; not yet wired for the application path. *Closeout: author `farmer_application.yml` (Service Catalog metamodel covering all application forms); deploy to both `doc-submitter` and `processing-server` bundles; wire `WorkflowActivator` on `spApplication` (chained alongside the other post-processors); author the `farmer_application_submission` workflow package with a `DocSubmitter` Tool activity. ~4 hours, dominated by YAML authoring.*
* 🟡 **DSL eligibility engine integration on application path** — DSL stack built and partially deployed; integration dormant. *Closeout in five layers: (1) add `c_ruleset_code` field to `spProgramEligibility` so the programme designer can pick a ruleset; (2) seed `jreFieldScope` for `FARMER_APPLICATION` plus 30–40 `jreFieldDefinition` rows; (3) author one DSL ruleset for `prog001` via `joget-rule-editor`; (4) add `EligibilityRuntime` Tool activity to the submission workflow (depends on the previous deliverable); (5) add the case-worker eligibility-evaluation panel to `spApplicationDecision`. ~5–7 hours total. Layers 1–3 can land independently of the submission backbone and validate the DSL stack early via the editor's test-against-sample-applicant action.*
* 🟡 **Snapshot-at-decision tool** — workflow tool that on `DRAFT → SUBMITTED` freezes resolver-populated values into immutable application columns, satisfying §5.5 #5. *Not yet built. ~3–4 hours including audit-spine extension below.*
* 🟡 **Audit spine extension** — capture data-change events, not only status transitions. Currently `audit_log` has 3 rows (status transitions only). *Not yet built. Lands together with the snapshot tool.*

**Closeout sequencing:** the four remaining commits land in this order. Form-quality wiring first (smallest, unblocks nothing else but cleanest commit). Submission-backbone wiring second (needed before DSL Layers 4–5, also creates the workflow that other tools can hook into). DSL integration third (Layers 1–3 can be earlier; 4–5 require the workflow to exist). Snapshot tool + audit-spine extension last (logically self-contained, cleaner with the eligibility result already being captured by the previous commit).

**Success criterion:** an operator designs a new subsidy programme using only Joget forms; a farmer submits an application against it; eligibility is evaluated both by self-attestation and server-side; the decision is recorded; the grant is issued; the submission travels through the RegBB-conformant backbone (so a back-office on a separate Joget instance could receive it); nothing in this loop required a Java change.

### 6.2 Horizon 2 — RegBB shape (months 3–6)

**Goal:** convert the system from "Joget app with plugins" to "RegBB-conformant Building Block exposing a stable API surface."

**Deliverables:**

* REST API surface for every domain plugin (`/api/services`, `/api/identity/resolve`, `/api/applications`, `/api/eligibility/evaluate`, `/api/decisions`, `/api/documents`, `/api/audit`)
* OpenAPI specification published, versioned (`/api/v1/...`)
* `notification-dispatcher-runtime` plugin — email/SMS dispatch with templates from `spNotifTemplate`
* Authentication and authorisation hardening (API keys, role scopes per endpoint)
* Initial mobile or USSD adapter as a proof point that the API surface is real
* Container/deployment artifacts (Dockerfile, helm chart, runbooks)

**Success criterion:** an external developer can build a complete alternative front-end against our REST API in under a week, without reading the Joget docs.

### 6.3 Horizon 3 — external BB integration (months 6–12)

**Goal:** connect to other GovStack Building Blocks and to peer government registries; achieve formal RegBB conformance.

**Deliverables:**

* Identity BB connector — replace internal NID lookup with calls to a foundational Identity BB
* Notification BB connector — delegate dispatch to a national Notification BB
* Payment BB connector — disburse approved subsidies through a Payment BB
* Cross-registry data exchange (DRC / x-Road style) — allow other government services to query the farmer registry under appropriate authorisation
* Formal RegBB conformance review and accreditation

**Success criterion:** the system can be cited as a reference RegBB implementation in GovStack documentation, and at least one other government service composes against our API.

---

## 7. Decision log (decisions already taken — for the record)

| # | Decision | Rationale | Date |
|---|---|---|---|
| 1 | Use Joget DX 8.x Enterprise as the foundation | Existing license, form-builder fits operator UX, OSGi plugin model fits our generic-then-specialise principle | 2026-04 |
| 2 | Extract `joget-status-framework` as a shared bundle | Banking and form-quality both needed lifecycle + audit; one impl is correct | 2026-04 |
| 3 | Build `form-quality-runtime` as a generic engine, configured per service | Avoids one-rule-per-form Java code; same engine governs programmes, farmers, parcels | 2026-04 |
| 4 | Configure plugin via Joget master-data forms (not config files) | Operators can edit configuration in production without a deploy | 2026-04 |
| 5 | Reusable `QualityBannerElement` instead of inline CustomHTML per form | Single source of truth for banner UX; configurable via property panel | 2026-04 |
| 6 | Build counter baked into every plugin JAR via `Build.java` + `repack.sh` | Operators can verify in App Composer that the deployed JAR matches the source | 2026-04 |
| 7 | Hard rule: no raw SQL on Joget metadata or form data | Joget DX 8 keeps form definitions in two independent JVM caches (AppDefinition + per-form Hibernate ORM mapping). Raw `INSERT/UPDATE` evicts neither, causing silent data loss on subsequent saves. Enforced via the `form-creator-api` plugin and CLAUDE.md operating notes. | 2026-04-28 |
| 8 | Promote `form-creator-api` to platform tier | Every form deployment now goes through this plugin's REST endpoint to evict both caches atomically. It is the deployment infrastructure that makes "configuration over code" operationally safe. | 2026-04-28 |
| 9 | Replace legacy `spApplicationDoc` with `spApplicationDocV2` driven by application engine | Legacy form was shaped for free-form admin doc upload with no programme awareness; retrofit cost > new-form cost; new flow seeds documents from programme spec via `application-engine-runtime`. | 2026-04-28 |
| 10 | Use design names for documents work (`sp_application_doc_v2`, `sp_doc_requirement_row`, status enum `MISSING/UPLOADED/VERIFIED/REJECTED`) | Already cited as conformance evidence in `regbb-conformance-checklist.md` and `application-engine-design.md`; minimum doc churn. | 2026-04-28 |
| 11 | Hard FK + derive-at-seed for `md23documentType` integration | Programme designer picks a document type from MD.23; format / size hints derive from MD.23 at seed time with optional per-row overrides. Reuses 14 curated MD.23 rows; matches the legacy `spApplicationDoc` pattern of binding to MD.23. | 2026-04-28 |
| 12 | Harmonise `md64documentStatus` (added MISSING and UPLOADED rows) | Avoids forking the status taxonomy across legacy and new flows. Single canon for document statuses. | 2026-04-28 |
| 13 | Dedicated 9th programme tab `spProgramDocumentsTab` over folding into `spProgramApproval` | Symmetry with eligibility / benefits — three child schemas, three tabs. Mirrors application-side Tab 4. | 2026-04-28 |
| 14 | `program_id` FK column on `spDocRequirementRow` (matching sibling `spBenefitItemRow` / `spEligCriterionRow`) | Pattern consistency with existing programme-side line-items. | 2026-04-28 |
| 15 | Snapshot at seed for `accepted_formats` / `max_size_kb` (not lookup-at-render) | Audit-stable: even if MD.23 changes later, the application's recorded constraint doesn't shift retroactively. Matches engine's existing snapshot pattern for benefit / eligibility rows. | 2026-04-28 |
| 16 | Recognise the submission-backbone triad (`WorkflowActivator → DocSubmitter → ProcessingServer`) as central RegBB Application Submission, not auxiliary plumbing | The triad implements canonical async, workflow-orchestrated, schema-driven submission with a stable HTTP contract. It is the layer that decouples citizen portal from back-office processing — required for real digital-government landscapes where the two sides are normally separate systems. | 2026-04-28 |
| 17 | Recognise eligibility evaluation as two complementary layers (citizen self-attestation by form-quality + server-side authoritative by DSL stack) | They serve different concerns at different lifecycle stages. Both are required for full RegBB conformance and operator-facing rule-authoring UX. | 2026-04-28 |
| 18 | Queue `farmer-derived-plugin` generalisation to `derived-snapshot-runtime` after Horizon-1 closeout | Currently domain-specific by accident of implementation; the mechanism is generic. Generalisation lifts it into the platform-tier configurable-engine slot alongside the other four. Land before any second domain implements its own hardcoded `*-derived` plugin. | 2026-04-28 |

Decisions still to make are tracked in a separate `docs/architecture/open-questions.md` (to be created when the first ambiguity demands it).

---

## 8. References

* GovStack Building Blocks specification — https://govstack.gitbook.io/specification
* GovStack Registration BB — current spec version under https://govstack.gitbook.io/specification/building-blocks/registration
* GovStack Identity BB — companion BB our Identity Resolver should align with
* Lesotho Subsidy Policy 2003 — domain reference (file in this repo)
* Joget DX 8 documentation — https://dev.joget.org/community/display/DX8/Joget+DX+8+Documentation
* `CLAUDE.md` (project root) — operating constraints, especially the HARD RULE on raw SQL, native paths table, and the two-cache trap explanation
* `docs/architecture/application-engine-design.md` — detailed application-engine design including §4.3 (`sp_application_doc_v2`), §4.5 (`sp_doc_requirement_row`), §5.2 (seeding tool algorithm)
* `docs/architecture/regbb-conformance-checklist.md` — RegBB conformance evidence per service
* `docs/architecture/subsidy-application-scope.md` — six work blocks for subsidy management
* `docs/architecture/identity-resolver-design.md` — identity resolver detail
* `app/forms/component03/` — programme designer canonical forms
* `plugins/<plugin-name>/CLAUDE.md` and `plugins/<plugin-name>/README.md` — per-plugin operating notes (notably `form-creator-api`, `doc-submitter`, `processing-server`, `joget-rules-api`, `joget-status-framework`, `form-quality-runtime`)

---

## Appendix A — Component inventory

### Plugins by architectural role (24 on disk under `plugins/`)

#### Cross-cutting platform engines (configurable, generic)

| Bundle | Build | Purpose |
|---|---|---|
| `joget-status-framework` | (stable) | `EntityType` / `Status` interfaces, transition map, audit |
| `form-quality-runtime` | `build-006` | Generic SQL rule engine, post-processor, banner element, status gating |
| `identity-resolver-runtime` | `build-014` | NID lookup + field mapping driven by `app_resolver_config` + `app_resolver_field_map` |
| `form-creator-api` | (stable) | REST surface for form / datalist / userview deployment via `FormDefinitionDao` |

Also classified here once generalised: `farmer-derived-plugin` → future `derived-snapshot-runtime` (see §2.7).

#### RegBB Application Submission backbone

| Bundle | Build | Purpose |
|---|---|---|
| `wf-activator` | (stable) | Post-processing tool that starts `{serviceId}_submission` workflow |
| `doc-submitter` | (stable) | Workflow Tool: extracts form data per YAML mapping, transforms to GovStack JSON, POSTs to receiver |
| `processing-server` | (stable) | REST receiver `/services/{id}/applications`, validates structure, persists via `FormDataDao` |

Driven by per-service `{serviceId}.yml` Service Catalog metamodel, currently populated for `farmers_registry` only.

#### DSL eligibility evaluation engine

| Bundle | Build | Purpose |
|---|---|---|
| `rules-grammar` | (stable) | ANTLR 4 parser for Rules Script DSL |
| `joget-rules-api` | (stable) | REST API: ruleset CRUD, DSL → SQL compilation, field-scope management |
| `joget-rule-editor` | (stable) | Admin authoring UI for Rules Script DSL |
| `subsidy-eligibility-runtime` | (stable) | Workflow Tool `EligibilityRuntime`: per-applicant evaluation, persists results |

#### Application engine (citizen-facing dynamic application)

| Bundle | Build | Purpose |
|---|---|---|
| `application-engine-runtime` | `build-012` | Programme spec reader, seeding service, tab-load binder, application post-processor |

#### Decision engine

| Bundle | Build | Purpose |
|---|---|---|
| `decision-engine-runtime` | `build-007` | Decision store binder, decision runner, entitlement generator, audit logger |

#### Custom form / datalist elements

| Bundle | Purpose |
|---|---|
| `joget-gis-server` + `joget-gis-ui` | Backend REST + Leaflet polygon-capture form element |
| `joget-concat-field` | `ConcatFieldElement` |
| `joget-smart-search` | `SmartSearchElement` typeahead |
| `embedded-datalist` | Read-only inline datalist within a form |
| `joget-advanced-filters` | `CascadingMdmSelectFilterType`, `DateRangeFilterType` for datalists |
| `joget-lookup-field` | Built but **not installed** in this Joget instance — do not use |

#### Domain helpers (currently miscategorised — see §2.7 for generalisation plan)

| Bundle | Purpose |
|---|---|
| `farmer-derived-plugin` | Hardcoded farmer-snapshot computation; pending generalisation to `derived-snapshot-runtime` |

#### Banking / financial domain (separate horizon)

| Bundle | Purpose |
|---|---|
| `gam-framework` | Banking / grants domain (consumer of `joget-status-framework`) |

#### Undocumented / loose ends

| Bundle | State |
|---|---|
| `app-def-provider` | Large jar with `FormDefinitionExtractor`, `DatalistDefinitionExtractor`, `UserviewDefinitionExtractor`, `CrudPackageExtractor` — likely export/import utility; no README. Document or remove. |
| `registry-overview-plugin` | No README; unclear purpose. Document or remove. |
| `diagnostic-tool` | `alignment-diagnostic-1.0.0.jar`; no README. Document or remove. |

#### Plugins (proposed Horizon 2)

| Bundle | Purpose |
|---|---|
| `notification-dispatcher-runtime` | Email/SMS notifications with templates from `spNotifTemplate` |

### Domain forms (built)

| Domain | Wrapper form | Tab subforms / row forms | Notes |
|---|---|---|---|
| Programme designer | `spProgramMain` | 9 tabs (identity, timeline, geography, beneficiary, benefits, eligibility, approval, monitoring, **documents**) | Documents tab added 2026-04-28 |
| Farmer registry | `farmerRegistrationForm` | 7 tabs | |
| Parcel registry | `parcelRegistration` | tab subforms | GIS polygon capture |
| Application | `spApplication` | 6 tabs (applicant, eligibility, benefits, **documents**, declaration, decision) + grid row forms `spApplicationEligibility`, `spApplicationBenefitReq`, `spApplicationDocV2` | Documents tab added 2026-04-28; supersedes legacy `spApplicationDoc` |
| Programme line items | — | `spBenefitItemRow`, `spEligCriterionRow`, `spDocRequirementRow` | grid row forms for the programme designer |
| Application children | — | `sp_application_elig_chk`, `sp_application_ben_req`, `sp_application_doc_v2` (table names) | seeded by `application-engine-runtime` from programme spec |
| District allocations | — | `spDistrictAllocation` | within programme geography tab |

### Form-quality services and gates

| Service | Primary form | Rules | Gate (status blocks) | State |
|---|---|---|---|---|
| `farmers_subsidy` | `spProgramMain` | 5 (4 ERROR, 1 WARNING) | `APPROVED, ACTIVE` | Live |
| `farmer_registration` | `farmerRegistrationForm` | 5 | `ACTIVE, VERIFIED` | Live |
| `parcel_registration` | `parcelRegistration` | 5 | `ACTIVE, VERIFIED` | Live |
| `farmer_application` | `spApplication` | 6 | `SUBMITTED, UNDER_REVIEW, APPROVED` | Configured but dormant — closeout in §6.1 |

### Master data (selected)

100+ MD lookups under `app/forms/master-data/` (md01–md90+). Notable for the application path: `md23documentType` (14 document types with formats and max sizes), `md64documentStatus` (7 lifecycle states after harmonisation), `md65docRejReason` (7 rejection reasons). Other key MDs: districts, villages, agro-ecological zones, target categories, crops, livestock, input categories, input subcategories, suppliers, units of measure, benefit item types, eligibility operators, programme statuses, distribution models.

### Service catalogue YAML metamodels (in `doc-submitter` and `processing-server` resources)

| `{serviceId}.yml` | State |
|---|---|
| `farmers_registry.yml` | ✅ written, populated, proven |
| `farmer_application.yml` | ❌ not yet written — Horizon 1 closeout |
| `parcel_registration.yml` | ❌ not yet written |
| `farmers_subsidy.yml` | ❌ not yet written |

---

## Appendix B — Glossary

* **Building Block (BB)** — a GovStack-defined functional component with a stable API contract
* **RegBB** — Registration Building Block (this system aims to conform)
* **Foundational ID** — a citizen's primary government-issued identifier (here: National ID)
* **Service Catalog** — the registrable set of services a citizen can apply for. Has *two complementary views*: business-facing (programme designer in `spProgramMain`) and machine-facing (`{serviceId}.yml` consumed by the submission backbone).
* **Service Catalog metamodel** — the `{serviceId}.yml` file describing a service's structural shape (forms, fields, GovStack JSON paths, master-data references, normalisation rules). Distinct from the business-facing programme designer content.
* **Submission backbone** — the `WorkflowActivator → DocSubmitter → ProcessingServer` triad driven by `services.yml`. The RegBB-conformant async submission mechanism that decouples citizen portal from back-office processing.
* **Citizen self-attestation** — eligibility checks where the citizen answers Yes/No to programme criteria. Runs at DRAFT, validated by `form-quality-runtime`.
* **Server-side eligibility evaluation** — eligibility checks where the system runs a DSL ruleset against registry data. Runs at SUBMITTED → UNDER_REVIEW, executed by `subsidy-eligibility-runtime`.
* **Configurable engine** — a generic plugin that consumes declarative configuration from admin-edited forms. The architectural slot in which "configuration over code" is delivered. Five today (status-framework, form-quality-runtime, identity-resolver-runtime, form-creator-api, eventually derived-snapshot-runtime).
* **Once-only principle** — citizen never re-types data the system already has
* **Snapshot at decision time** — capturing the data state at the moment of decision so the decision is auditable
* **Configuration over code** — extending the system through admin-edited forms, not Java changes
* **Programme** (Lesotho domain term) — equivalent to a *registrable service* in RegBB vocabulary

---

*End of document. Comments welcome on the draft.*
