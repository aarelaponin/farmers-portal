# Lesotho Farmers Portal — Complete Inventory

Snapshot as of 2026-04-27. See `next-session-prompt.md` for the handoff
document to continue in a fresh session.

---

## 1. Project overview

**Lesotho Farmers Registration System (FRS)** — GovStack Registration
Building Block (RegBB) implementation for agricultural subsidy management
on **Joget DX 8.x Enterprise**.

- Deployed at `http://20.87.213.78:8080` (app id: `farmersPortal`).
- PostgreSQL on Azure: `joget-pgsql-sa.postgres.database.azure.com /
  jogetdb / jogetadmin / Joget@DB#2026!`.
- Repo: `/Users/aarelaponin/IdeaProjects/rsr/lst-frm-prj/`.

**End-to-end demo loop works today:** register farmer → design programme →
farmer applies (National ID auto-fills applicant identity, eligibility &
benefit grids self-seed from programme spec) → operator reviews Tab 3 and
optionally adjusts approved quantities → operator approves on Tab 5 →
application flips to APPROVED, audit row written, entitlement `EN-<id>`
issued with per-line items and correct subsidy math.

---

## 2. Custom Joget plugins built (`gs-plugins/`)

| Plugin | Latest build | Purpose |
|---|---|---|
| **`joget-status-framework`** | pre-existing | Generic status-transition engine + `StatusManager` API. Used by form-quality and decision engines to write to `app_fd_audit_log`. |
| **`form-quality-runtime`** | pre-existing | Server-side rules engine (`RuleRepository`, `RuleEvaluator`, `IssueRepository`). Post-processor scans forms on save, writes `qa_issue` rows, updates `qa_record_status`. Owns `QualityBannerElement` (form-builder-selectable status banner with green / amber / red chips). |
| **`identity-resolver-runtime`** | build-014 | Configurable identity lookup. `ResolverService` (Java service), `IdentityResolverElement` (form element supporting `useExternalInput=Y` mode so persistence stays Joget-native), `/api/identity/resolve` REST endpoint, two admin tables (`app_resolver_config` + `app_resolver_field_map`). Chained subform reads (farmer → farmerResidency for district / village). |
| **`application-engine-runtime`** | build-011 | Application wizard runtime. `ApplicationSeedingTool` (post-processor that seeds Tab 2 eligibility-check rows and Tab 3 benefit-request rows from the chosen programme spec), `SeedingTabLoadBinder` (load-side hook that provisions tab records + mirrors state to wrapper). Solves Joget's MultiPagedForm partial-store bypass with load-side hooks. |
| **`decision-engine-runtime`** | build-007 | Operator decision flow. `DecisionStoreBinder` (store-side hook on Tab 5), `DecisionRunner`, `EntitlementGenerator` (soft-cap policy: operator's `approved_qty` wins, fallback to `requested_qty`), `AuditLogger` (writes to existing `app_fd_audit_log` schema). |
| **`joget-gis-ui`** | pre-existing | GIS polygon capture element (`GisPolygonCaptureElement`). |
| **`parcel-zone-centring`** | pre-existing | `AutoCenterBootstrapElement` — invisible element that pre-populates GIS coords from MD.95 (district × eco-zone) centroids. |
| **`joget-smart-search`** | pre-existing | `SmartSearchElement` for typeahead lookup against MD forms. |
| **`concat-field`** | pre-existing | `ConcatFieldElement` for derived fields. |

All plugins built from source via the sandbox `deploy/repack.sh` pattern
(no Maven required). Ready-to-upload JARs live in
`/Users/aarelaponin/IdeaProjects/rsr/lst-frm-prj/_deploy/`.

Latest deploy artefacts:
- `identity-resolver-runtime-8.1-build014.jar`
- `application-engine-runtime-8.1-build011.jar`
- `decision-engine-runtime-8.1-build007.jar`

---

## 3. Custom Joget forms authored

### Application wizard (Block 3)

Wrapper + 5 tabs + 2 child schemas.

| Form id | Purpose |
|---|---|
| `spApplication` | 5-page MultiPagedForm wrapper. Declares 9 HiddenFields for runtime column access (`status`, `programme_id`, `application_code`, `applicant_id`, `tab_applicant`, `tab_eligibility`, `tab_benefits`, `tab_decision`, `tab_declaration`). Hosts the QualityBannerElement. |
| `spApplicationApplicant` | Tab 1 — Applicant & Programme. Programme SelectBox + IdentityResolverElement (external-input mode) + resolved identity fields. |
| `spApplicationEligibilityTab` | Tab 2 — Eligibility. FormGrid over `spApplicationEligibility`. |
| `spApplicationBenefitsTab` | Tab 3 — Benefits. FormGrid over `spApplicationBenefitReq` (columns: item, unit, subsidy %, default qty, requested qty, **approved qty (operator)**, status). |
| `spApplicationDeclaration` | Tab 4 — Declaration & signature. |
| `spApplicationDecision` | Tab 5 — Operator decision. SelectBox (APPROVE / REJECT / REQUEST_INFO) + mandatory reason + signoff. Wired to `DecisionStoreBinder`. |
| `spApplicationEligibility` | Eligibility-check child schema (one row per criterion). |
| `spApplicationBenefitReq` | Benefit-request child schema (one row per benefit item). |

### Identity Resolver (Block 2)

| Form id | Purpose |
|---|---|
| `app_resolver_config` | Named resolvers (`id`, `sourceFormId`, `sourceLookupField`, `notFoundMessage`, `notFoundActionUrl`). |
| `app_resolver_field_map` | Source → target field mappings, including `chainedSourceFormId` + `chainedJoinField` for cross-form reads. |

### System

| Form id | Purpose |
|---|---|
| `auditLog` | Audit trail. Schema aligned with the existing form-quality-runtime rows (`entity_type`, `entity_id`, `from_status`, `to_status`, `triggered_by`, `reason`, `timestamp`). |

### Datalists

- `list_spApplication` — SQL-backed with joins to applicant tab, programme
  (for programme code), district (for label). Columns: Application Code,
  Applicant, District, Programme, Status, Created.
- Resolver admin: `list_app_resolver_config`, `list_app_resolver_field_map`.

---

## 4. Design docs (`_design/`)

| Document | Purpose |
|---|---|
| `architecture-overview.md` | RegBB conformance, layered architecture, three-horizon roadmap. |
| `subsidy-application-scope.md` | Six work blocks with acceptance criteria. |
| `identity-resolver-design.md` | Block 2 detailed spec. |
| `application-engine-design.md` | Block 3 detailed spec. |
| `regbb-conformance-checklist.md` | RegBB spec section → deliverable mapping. |
| `regbb_architecture_diagram.svg` | Visual architecture. |
| **`next-session-prompt.md`** | Self-contained handoff for continuing (a) document submission in a fresh session. |
| **`project-inventory.md`** | This document. |

---

## 5. Institutional knowledge captured in `CLAUDE.md`

Bolded items are the ones that cost hours of debugging to discover.

- **Joget DX 8 two-cache invalidation rule** — AppDefinition cache
  (JSON) evicts on any form save; per-form Hibernate ORM mapping only
  refreshes when *that specific form* is saved. Direct SQL patches
  require: save another form → hard-refresh → verify canvas → save the
  patched form. Skipping the hard-refresh is the silent-data-loss trap.
- **`dao.load` and `dao.saveOrUpdate` only see Hibernate-mapped columns.**
  A column physically in the table is silently invisible to reads and
  writes if the form has no field declared for it. Fix: declare a
  HiddenField (no `value` default) and re-save the form.
- **MultiPagedForm partial-store skips postProcessor.** Use load-binder
  (`SeedingTabLoadBinder`) or store-binder (`DecisionStoreBinder`) hooks
  instead.
- Whitelist of custom element classes actually installed in this Joget
  vs "NOT installed" traps (`LinkDataListAction`,
  `DefaultFormOptionsBinder`, `LookupFieldElement`, `TextAreaField`,
  `HyperlinkFormatter`, community-edition `CrudMenu`,
  `SelectBoxDataListFilterType`).
- **FormGrid foreignKey convention** — child rows link to TAB
  subform's id, not the wizard wrapper's id. FormGrid stores column
  config under property `options` (not `columns`), needs both `loadBinder`
  AND `storeBinder`.
- **HARD RULE**: never raw SQL on `app_form`, `app_userview`,
  `app_datalist`, `app_fd_*` form-data tables. Use form-creator-api
  (`POST /jw/api/formcreator/formcreator/forms`), App Composer, or the
  form-data REST API. Reads are fine.
- **Sync ritual (ADR-033)** — `make sync` pulls DB state back to repo.
- **Postgres case-folding** — column `c_programCode` → actual
  `c_programcode` → property `programcode` (lowercase). Try both cases
  when doing `getProperty`.
- **DatePicker format dialect** — uses `yy`→`yyyy`, `mm`→`MM`,
  `MM`→`MMMMM`, NOT Java SimpleDateFormat. Java pattern in Joget config
  triggers eight-digit years.
- **24-char form id cap** so `app_fd_<id>` stays ≤ 32.
- **`c_correlation_id` vs `c_idempotency_key`** on `app_fd_budget_event`
  — different semantics, easy to mix up in forensics.
- **API Builder auth** — `/jw/api/*` endpoints need `api_id` + `api_key`
  headers, NOT basic auth. Each API has its own UUID; api_key is
  app-scoped.
- **Cross-bundle reflection** — pin the target's classloader explicitly
  (`Class.forName` doesn't see across un-exported bundle packages).
- Every plugin class extending a Joget SPI **must** be registered in
  `Activator.start()` via `context.registerService(...)`.
- **Field id whitelist** for every FormGrid child form (which column ids
  are safe to reference).
- Full log of hard-won gotchas at the end of `CLAUDE.md` (~30 items).

---

## 6. Sandbox / dev tooling

- **`deploy/repack.sh`** in every plugin — sandbox-friendly compile
  (`javac --release 11`) + bundle-manifest + jar packing. Auto-bumps
  `Build.java` counter + timestamp. Uses `~/.m2/repository` from a
  pre-populated cache, no Maven required.
- **Skill packs** (`skills/` and `.claude/skills/`) — Joget-specific
  generators: `joget-form-gen`, `joget-datalist-gen`, `joget-userview-gen`,
  `joget-workflow-gen`, `joget-plugin-dev`, `joget-req-analyst`,
  `joget-instance-setup`, `joget-db-inspect`. Broader helper skills:
  `arms-dev-workflow`, `mtca-data-platform`, etc.

---

## 7. Live test artefacts

- **prog001** (`PRG-2025-001`) — canonical demo programme with 4
  eligibility criteria + 3 benefit items (SEED-MAIZE / FERT-NPK /
  FERT-UREA), 80 % subsidy across the board.
- **AP-4140E0** — live end-to-end test application, applicant
  Mants'ali Panyane, NID `066257236627`.
- **farmerByNid** — resolver config with 8 field mappings (first name,
  last name, DOB, gender, mobile, email + district and village via
  chained subform read of `farmerResidency`).
- **EN-4140E0** — issued entitlement with 3 items, subsidy 2 440 /
  contrib 610 / value 3 050. Operator reduced SEED-MAIZE from farmer's
  requested 30 to approved 20 (proving the soft-cap override path).

---

## 8. Status per scope block

| Block | Status |
|---|---|
| 1 — Foundation extensions | partial (audit writes ✅ from form-quality and decision-engine; eligibility-eval context ⏳) |
| 2 — Identity Resolver | ✅ done |
| 3 — Application Engine | mostly done (3.1 / 3.2 / 3.4 ✅; 3.3 snapshot tool, 3.5 quality service, 3.6 e2e test still open; document submission — the next task) |
| 4 — Decision Engine | core ✅ (4.1 XPDL deliberately skipped in favour of form-driven flow) |
| 5 — Operator workspace | ⏳ not started |
| 6 — Documentation | 4 of 5 docs ✅ (operator-runbook still open) |

---

## 9. Next steps

Prioritised menu for future sessions:

1. **(a) Document submission** — closes the citizen-side gap. Two new
   forms per side (programme + application), seeding extension, wizard
   goes 5 → 6 tabs. Fully specified in `next-session-prompt.md`
   Section 5. Recommended next.
2. **(b) Form-quality rules for `farmer_application`** — 6–8 starter
   rules gating submission. Depends on (a) for the docs rule.
3. **(c) Operator workspace (Block 5)** — pure Joget configuration.
   Datalists per status / per district / per case-worker inbox.
4. **(d) Snapshot tool (Block 3.3)** — freeze resolver-populated values
   on DRAFT → SUBMITTED.
5. **(e) Eligibility auto-evaluation (Block 1.1)** — server-side
   evaluator writes PASS / FAIL / N/A into `applicant_response`.
6. **(f) End-to-end demo polish** — runbook + walkthrough script.
7. **(g) XPDL workflow (Block 4.1)** — only when real assignment /
   inboxes become a requirement.

The handoff prompt in `next-session-prompt.md` covers (a) in detail and
lists (b)–(g) as reserved future work.
