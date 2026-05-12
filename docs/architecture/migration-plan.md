# Migration plan — Lesotho Farmers Portal subsidy convergence

| | |
|---|---|
| Status | **Revision 3 — May 2026 reframe.** Supersedes revision 2 (2026-04-28). |
| Date | 2026-05-02 |
| Owner | Farmers Portal architecture team |
| Authority | Decisions on retirement gates rest with the project lead (Aare) — no waiting periods |
| Companion documents | `docs/architecture/architecture/solution-architecture.md` (the canonical architecture); `convergence-framework.md`; `mm-completeness-audit.md`; per-component SADs under `docs/architecture/architecture/components/`; ADRs 001–028; `docs/architecture/policy-to-rules-migration.md`. |
| What this document is | Phase sequencing for the convergence: subsidy module live in Phase 1; cutover + `MetaReviewElement` in Phase 2; Budget Engine in Phase 2.5; IM module in Phase 3 (uses MM-form-gen kernel only, NOT the RegBB framework). |
| What this document is not | A weekly task plan, a configuration deliverable, a cutover runbook. |

> **Revision 3 reframe (May 2026).** Two scope cuts, two additions vs revision 2:
>
> 1. **No XPDL workflow generation by RegBB** (per ADR-011). Phase 2's "XPDL design + authoring" + "submission backbone" tracks are both withdrawn. Workflows are sysadmin-authored in Joget's native process designer; RegBB dispatches via `mm_action`.
> 2. **Stage 2 (farmer/parcel migration to mm_*) is dropped.** The registries remain native Joget permanently — see component SAD `farmer-parcel-registry-integration.md`. Out of convergence scope.
> 3. **IM module added as Phase 3.** Inputs Management uses the MM-form-gen kernel only (not RegBB framework). The "IM as second-domain probe for RegBB maturity" framing is also withdrawn — IM is supply-chain logistics, not citizen services. See `docs/architecture/architecture/components/im-module.md`.
> 4. **Budget &amp; Commitment Engine added as Phase 2.5.** New cross-cutting module. Per-programme budget envelopes; commitment funnel (Reservation → Pre-commitment → Commitment → Expense); rule-driven governance. See `docs/architecture/architecture/components/budget-engine.md` + ADRs 022–026.
> 5. **"If it's policy, it's a rule"** — applied universally across all modules except farmer + parcel registries. See `docs/architecture/policy-to-rules-migration.md`. Two cleanups already shipped (ADR-027 + ADR-028 in build-049): initial status and decision-to-status mappings are now rule-driven.
>
> The §5 "Phase 2 — original scope" text below is stale and superseded by §5-revised inside that section. The §10 "Stage 2 — completing the convergence" content is fully withdrawn; the registries do not migrate.

---

## 0. Revised phase shape at a glance

| Phase | Goal | Calendar (rough) | Status (May 2026) |
|---|---|---|---|
| 0 | Alignment — framework + audit + ADR + spec consistent | Day 0 | ✅ done |
| 1 | Subsidy module live: engine + generated UX + 4 programmes from `mm_*` + 4-programme parity test | Weeks 1–12 | 🟡 ~70% — Phase 1 close-out backlog includes repeating-group / GIS / signature / smart-search / cascading dropdowns / capability registry / single-window catalogue / first dry-run + parity test |
| 2 | `MetaReviewElement` per RegBB §7.2 + Programme Builder admin UI + cutover (retire `application-engine-runtime`, application-side rules in `form-quality-runtime`, `identity-resolver-runtime` for application path) | Weeks 13–17 | 🟡 ~10% — operator decision binder + mm_action dispatch already shipped |
| **2.5 (new)** | Budget &amp; Commitment Engine: storage + funnel ledger + Cost Estimation Service + dashboards + rule-driven governance + initial seed for the four 2025 programmes | Weeks 16–19 (overlaps Phase 2) | ⏸ architecture documented; bundle not yet built |
| **3 (new)** | IM module on the MM-form-gen kernel: catalog, supplier, inventory, allocation, voucher, distribution, transaction log + cross-module integration (subsidy approval → entitlement issuance) | Weeks 20–28 | ⏸ business specification done in `inputs-mgmt-workflow/`; implementation not started |
| ~~Stage 2 (registries)~~ | ~~migrate farmer + parcel to mm_*~~ | ~~deferred~~ | ❌ **withdrawn — registries stay native Joget permanently** |

The total compressed timeline is ~28 weeks from Phase 1 start (April 2026) to IM module live (~Q4 2026). Subsidy go-live is bounded by Phase 1 + 2 = ~17 weeks; Budget Engine + IM are additive and can overlap.

---

## 1. Operating assumptions

The plan rests on seven explicit assumptions, all confirmed:

1. **Capacity:** 2 FTE engineers, working in parallel where the work splits.
2. **Operational continuity:** The system is in dev/test, not production. Citizens are not depending on the application path. **The plan is therefore aggressive: legacy and converged subsidy stacks can swap freely; cutover is a flag flip.**
3. **Stage 1 scope — subsidy services first; farmer/parcel deferred to Stage 2.** Full convergence remains the destination; this stage delivers the highest-value capability with the lowest risk to working code:
   - **In scope (Stage 1):** programme designer (currently `spProgramMain` + 9 tabs), the application wizard (currently `spApplication` + child grids), the engine that processes them. These migrate to `mm_*` rows interpreted by `reg-bb-engine`.
   - **Deferred to Stage 2:** farmer registration (`farmerRegistrationForm` + 7 tabs), parcel registration (`parcelRegistration` + 4 tabs). They continue running as hardcoded Joget forms during Stage 1. Their convergence is sketched in §10 but is not part of this plan's commitments.
   - **Bridge during Stage 1:** subsidy Determinants read farmer and parcel data via `$registry.farmers.*` and `$registry.parcels.*` references. These references resolve against the legacy `app_fd_farms_registry`, `app_fd_farm_location`, `app_fd_parcelRegistration` etc. tables through the IM-connector pattern. No change to farmer/parcel data models is required during Stage 1; the bridge becomes a no-op when Stage 2 migrates them, because by then `$registry.farmers.*` resolves against `mm_*`-driven tables instead.
   - **Stage 1 does not foreclose Stage 2.** No design choice in Stage 1 makes farmer/parcel migration harder later. Specifically: the IM-connector capabilities registered in Stage 1 (`farmers.byNid`, `farmers.parcels.summary`) have stable contracts that survive an underlying-data migration; the `$registry.*` references in Determinants do not need to change when Stage 2 lands.
4. **Programmes:** 4 subsidy programmes exist today; the plan's empirical test is that one converged engine processes all 4 from `mm_*` rows without per-programme Java. Adding a 5th, 6th, 7th programme is a configuration task; this is the practical value the convergence delivers.
5. **Service-shape modelling:** The 4 programmes become **1 `mm_service` ("Farmers Subsidy") + 4 `mm_registration` rows** — the Single-Window pattern from spec §4.1.3 / §6.3.1.9. The application wizard's screens are defined once on the service; per-programme variation lives in `mm_determinant` (eligibility), `mm_benefit` (programme-specific benefits — Lesotho-instance extension per audit §6.2), and `mm_required_doc` (programme-specific documents), all scoped by `registrationId`. The citizen selects programme(s) at the Guide screen → `app_application.selectedRegistrationIds` → engine merges per-Registration requirements per spec §6.1.6.
6. **Multi-instance topology:** Deferred to Stage 2 or later. Stage 1 runs everything in a single Joget instance — converged subsidy stack alongside the unchanged farmer/parcel registration. The submission backbone runs in self-call topology during Stage 1. This is a deliberate choice to minimise Stage 1 risk; the architecture supports the split when Stage 2 calls for it.
7. **Spec posture, decision authority:** All `mm_*` extensions land as Lesotho-instance extensions (no upstream blocking); Aare decides retirement gates promptly.

These assumptions narrow the *current stage* of the plan, not the convergence as a whole. The trio's commitments (framework §2) apply to in-scope components in Stage 1; farmer and parcel registration remain outside the trio's scope *during Stage 1* because they are deferred, not because they are excluded from the convergence's destination. Their parallel-source-of-truth status (legacy form definitions outside `mm_*`) is a transitional state by design — Stage 2 closes it.

---

## 2. The phase shape at a glance

(After D4 merged the original Phase 1 + Phase 2 into a single Phase 1 — Path B — and D17 added userview generation + operator read-only inspection to Phase 1, the shape compresses:)

| Phase | Goal | Calendar | Retirement at end |
|---|---|---|---|
| 0 | Alignment — framework, audit, ADR, spec, scope statement consistent | Day 0 (mostly done) | None |
| 1 | Stand up `reg-bb-engine` + `reg-bb-publisher`; all 4 programmes in `mm_*`; SQL-path evaluator wired; eligibility against legacy farmer/parcel data; **citizen catalogue + wizard generated, operator inbox + read-only inspection generated**; four-programme parity test passes through generated UX; **go/no-go gate** | Weeks 1–12 | None |
| 2 | Add workflow capabilities on top: XPDL per service, `MetaReviewElement` with decision affordances, submission backbone wiring | Weeks 13–17 | None |
| 3 | Cutover the application path; programme designer to `mm_*` admin; retire `application-engine-runtime` and the SQL-rule layer of `form-quality-runtime` | Weeks 18–21 | Yes — see §6.4 |

Total: ~21 calendar weeks (~5 months) from Phase 1 start to converged subsidy stack live. No Phase 4. The schedule is illustrative; the plan's commitments are scope and parity gates, not dates.

---

## 3. Phase 0 — Alignment

**Goal.** Documents tell a consistent story for the in-scope work. Out-of-scope components (farmer/parcel) are not edited.

**Status.** Mostly complete:

- ✅ `mm-completeness-audit.md` — green verdict; the audit's farmer-registration findings remain valid and recorded but not acted on
- ✅ `adr-001-rule-grammar-canon.md` revision 2
- ✅ `regbb-solution-architecture-spec.md` updated for the partitioned grammar
- ✅ `convergence-framework.md` revision 2

**Remaining:**

- 🟡 `docs/architecture/decision-log.md` (create file) records: (a) acceptance of ADR-001r2; (b) acceptance of the subsidy-only scope choice in this plan; (c) the four-programme empirical test as the convergence validation criterion (replacing the audit's farmer-vs-subsidy two-service test, which depended on farmer migration that is no longer in scope).

**Explicitly NOT in Phase 0:**
- `architecture-overview.md` is not edited. Its description of the current state remains accurate for in-scope and out-of-scope components alike.
- The audit's farmer-registration findings (§3 of the audit) are not retracted; they remain on file for any future expansion of convergence scope.

**Effort.** ~1 day, single engineer.

**Phase 0 ends when:** Aare has reviewed and authorised Phase 1 to start.

---

## 4. Phase 1 — Engine skeleton + simplest programme as fixture

**Goal.** `reg-bb-engine` renders an application wizard for one (the simplest) of the 4 programmes, dynamically from `mm_*` rows. A citizen can submit an application against that programme through the converged stack. Identity resolution from National-ID auto-fills applicant fields by reading the legacy `app_fd_farms_registry` table.

### 4.1 Deliverables

**Engine skeleton (`reg-bb-engine`):**
- `MetaScreenElement` — Joget form element class that walks `mm_screen` + `mm_field` rows at render time and emits the corresponding Joget element classes (TextField, SelectBox, FileUpload, DatePicker, Hidden, Radio, CheckBox, TextArea, Signature). Custom widgets used by the application path: `SmartSearchElement` for the programme picker, `IdGeneratorField` for application code (handled as engine-side PK generation).
- `DeterminantEvaluator` interface — fast-path tree-walker only in this phase (closed twenty subset, in-memory). SQL-path evaluator stub is in place but unwired; it lands in Phase 2.
- `EvalContext` carrying `applicationId`, `serviceId`, `serviceVersion`, `selectedRegistrationIds`, `data`, `currentUsername`, `correlationId`.
- L1 cache (`ThreadLocal`); L2/L3 deferred.
- `Activator.java`, build counter, OSGi descriptor.

**Publisher (`reg-bb-publisher`) — validation + userview generation per D17:**
- Validates a service's Determinants against the DSL grammar; checks refs resolve; checks targets exist; classifies fast-path vs SQL-path per spec §4.3.4.
- Flips `mm_service.status` from `draft` to `published`; invalidates engine cache on republish.
- **Generates citizen userview menu + catalogue page** per published `mm_service` (per D17, was Phase 3).
- **Generates operator userview category** with inbox + applications-list datalist per published `mm_service` (per D17, was Phase 3).

**`mm_*` schema deployment:**
- Twelve `mm_*` form definitions deployed via `form-creator-api` (no raw SQL on Joget metadata per `CLAUDE.md`).
- Lesotho-instance extensions needed for Phase 1:
  - `mm_field.widget=repeating_group` with reference to a child `mm_screen` set (used by the application's seeded grids — eligibility, benefits, documents).
  - `mm_field.optionsFilterJson` for cascading dropdowns (deferred — application path doesn't strictly need it in Phase 1; revisit if a Phase 1 fixture surfaces the need).
  - `mm_benefit` schema (the heavy extension) — deployed in Phase 1 because the simplest programme already needs it. Sibling of `mm_fee` with `kind ∈ {charge, subsidy, in_kind}`, `quantity`, `unit`, `unitCost`, `subsidyPercent`, `farmerContribution`, `formulaJson`. Belongs to `mm_registration`. **Design carefully — this is the most architecturally significant single addition in the plan.**
  - `mm_registration.attributesJson` — typed-attributes JSON for `programmeType`, `seasonCode`, `campaignType`, `legalBasis`, `fundingSource`. Programme-level facts that aren't structural. (Per D16: lives on registration, not service, because under D4's Single-Window framing programme-level facts vary per registration.)
  - `mm_required_doc.captureFieldsJson` — list of per-document fields the citizen fills (issue date, expiry, document number).
- Admin CrudMenus over the twelve `mm_*` forms (the operator authoring surface — Phase 3 turns this into the canonical programme designer).

**Migrate the simplest of the 4 programmes to `mm_*` as a fixture:**
- Choice of "simplest" is operator's call — likely `prog001` or whichever programme has the fewest eligibility criteria, fewest benefit items, fewest documents. The choice is recorded in the decision log.
- One `mm_service` (`farmers_subsidy`) version `1.0` (umbrella with no programme-specific attributes per D16). The 4 `mm_registration` rows each carry their own `attributesJson` with programme metadata and `acceptanceWindow{From,To}` from `applicationStartDate`/`applicationEndDate`.
- One `mm_registration` for the chosen programme.
- 6 `mm_screen` rows for the application wizard: `kind='guide'` (programme intro/selection), `kind='form'` ×4 (applicant & programme, eligibility self-attestation, benefits requested, declaration), `kind='confirmation'`. The legacy `spApplication`'s 6-tab structure maps cleanly.
- ~30–40 `mm_field` rows for the wizard structure plus seeded child grids referenced via `mm_field.widget=repeating_group`.
- 4–6 `mm_determinant` rows covering the chosen programme's eligibility criteria. Mix of fast-path (`$applicant.X eq Y`-style) and SQL-path (those needing `$registry.farmers.*` references — note these will not yet evaluate correctly in Phase 1 because the SQL-path is not wired; Phase 1 stubs them as fast-path with `$applicant.<field>` references for self-attestation only, and Phase 2 wires the SQL path to make them authoritative).
- N `mm_benefit` rows (typically 2–4 per programme — e.g., SEED-MAIZE, FERT-NPK).
- N `mm_required_doc` rows (typically 2–4 per programme).
- One `mm_action` kind=`bot_pull` triggered on `national_id` field_change at the applicant tab. The action's `imCapabilityRef` points at a `farmers.byNid` capability registered with the `reg-bb-im-connector`.

**`reg-bb-im-connector` configuration for legacy table reads:**
- Register an `im_capability` row: `source='farmers'`, `path='byNid'`, response shape covering all fields the application path references.
- The connector's adapter reads from `app_fd_farms_registry` directly (since this is single-instance, no X-Road envelopes; just a JDBC query against the legacy form's table). Read-only — the connector never writes back.
- Register a second capability `farmers.parcels.summary` returning `{totalParcelHectares, parcelCount, hasGisPolygon}` aggregated from `app_fd_parcelRegistration` joined to `app_fd_parcelGeometry` for the given farmer. **This is the denormalisation discipline applied at the connector layer rather than via `derived-snapshot-runtime`** — the audit's §5.3 honesty test requires aggregation to live somewhere, and putting it in the connector keeps it close to the data.

**Authoring tool integration:**
- `joget-rule-editor` configured to author Determinants over `mm_determinant.ruleJson`.
- Editor produces the JSON AST shape for fast-path-eligible rules and DSL source for SQL-path rules per ADR-001r2; routing decision made at compile time.

### 4.2 Out of scope for Phase 1

(Phase 1 absorbed substantial scope per D4 (Path B) and D17 (UX). The accurate Phase 1 scope is documented in `phase-1-plan.md` rev2; the items still genuinely out of scope:)

- `MetaReviewElement` with full role-scoped sections + decision affordances (Phase 3).
- XPDL workflow authoring per service (Phase 3).
- Submission backbone wiring (`wf-activator → DocSubmitter → ProcessingServer`) (Phase 3).
- Programme Builder composite admin UI (Phase 3 — Phase 1 authors via 13 separate `mm_*` CrudMenus).
- Decision affordances (approve / reject / return-for-correction) (Phase 3).

### 4.3 Parity criterion

Five test applications can be submitted through the converged stack against the chosen Phase 1 programme, with:
- National-ID auto-fill correctly populating applicant fields from the legacy `app_fd_farms_registry`.
- Citizen self-attestation eligibility producing the expected outcome (PASS/FAIL) per the programme's mm_determinant rules.
- Benefit request grid showing the programme's benefits per the `mm_benefit` rows.
- Document upload capturing the per-document metadata (`captureFieldsJson`).
- The submitted `app_application.dataJson` preserving all citizen-entered data.

The five test applications cover: clean PASS, eligibility-FAIL, full document set, partial document set, applicant whose farmer record has multiple parcels. **Farmer/parcel data is read but never written.**

### 4.4 Retirement gate

**None.** Both the legacy `spApplication` and the converged stack run in parallel. Citizens (in dev/test) can apply through either. Operators choose which to use case-by-case.

### 4.5 Risk surface

- **`mm_benefit` design.** The most architecturally significant single addition. Three concrete risks: (a) collapsing `mm_fee` and `mm_benefit` is tempting but a category error per ADR-001r2 reasoning; resist. (b) Relationship to `mm_required_doc` for benefit-bearing documents (delivery confirmation, etc.) is non-trivial. (c) Quantity formulas must use the closed twenty arithmetic operators (`add`, `mul`, `if`) only. Mitigation: design review with paired test against vehicle-registration and cash-transfer mental models; document why each design choice was made; formal review with Aare before deployment.
- **IM-connector aggregation.** The `farmers.parcels.summary` capability does a JOIN+SUM at evaluation time. For Phase 1's 5-application scope this is fine; for Phase 2's broader test load, evaluate whether to push aggregation into a `derived-snapshot-runtime` snapshot maintained on the farmer record. **Phase 1 doesn't pre-decide this**; Phase 2 measures and chooses.
- **`MetaScreenElement` UX vs. legacy `spApplication`.** Subtle differences in field rendering, validation timing, error messaging. Mitigation: explicit visual-parity test pass, side-by-side comparison.
- **`bot_pull` against legacy `app_fd_farms_registry`.** The current `identity-resolver-runtime` plugin already does this; the converged path replaces it with `mm_action` + `reg-bb-im-connector`. Risk: the field map differs slightly (column names, transformation rules). Mitigation: derive the new field map directly from `identity-resolver-runtime`'s `app_resolver_field_map` rows for `farmerByNid`, with field-by-field verification.

### 4.6 Effort sketch

| Track | Work | Person-weeks |
|---|---|---|
| Engine | `MetaScreenElement` + fast-path evaluator + L1 cache + Activator | 4 |
| Engine | `reg-bb-publisher` skeleton + validator | 1.5 |
| Schema | `mm_*` form deployments + the four Phase 1 extensions (`repeating_group`, `mm_benefit`, `mm_registration.attributesJson`, `mm_required_doc.captureFieldsJson`) | 2.5 |
| Schema | Admin CrudMenus over `mm_*` | 1 |
| Connector | IM-connector capability registrations + adapter for legacy tables | 1.5 |
| Content | Simplest-programme migration to `mm_*` | 1.5 |
| Editor | `joget-rule-editor` integration with `mm_determinant` | 1 |
| Test | Parity testing across 5 applications | 1 |
| **Total** | | **14 person-weeks ≈ 6 calendar weeks at 2 FTE** |

---

## 5. Phase 2 — Workflow capabilities + decision affordances

> **Scope superseded by D4 + D17.** The original Phase 2 content (4 programmes + SQL-path evaluator + four-programme test) moved into Phase 1 (per Path B and the UX expansion). Residual Phase 2 work is the workflow-and-decision-affordance layer: XPDL per service, `MetaReviewElement` with full decision affordances, submission backbone wiring. The detailed deliverables below describe the *original* Phase 2 plan and need a rev3 rewrite to reflect the merged shape; for the canonical Phase 1 plan see `phase-1-plan.md` rev2.

**Goal (under D4 + D17).** Workflow capabilities layered onto the Phase 1 stack: XPDL workflows per service, `MetaReviewElement` with role-scoped decision affordances per spec §7.2, submission backbone (`wf-activator → DocSubmitter → ProcessingServer`) wired in self-call topology. Operators move from "inspect" to "approve / reject / return-for-correction" through real workflow.

**Goal (original — superseded).** All 4 programmes run from `mm_*`. The SQL-path evaluator is wired. Eligibility Determinants resolve `$registry.farmers.*` and `$registry.parcels.*` against legacy tables and produce authoritative outcomes. **The four-programme test passes:** the same converged engine processes 4 distinct programmes with different eligibility criteria, different benefits, different document requirements, with no per-programme Java code.

### 5.1 Deliverables

**Lesotho-instance extensions completed:**
- `mm_field.optionsFilterJson` — cascading dropdowns. Used in the application path if a programme's eligibility involves district/zone selection (the chosen programme determines whether this is needed in Phase 2).
- `mm_registration.acceptanceWindowFrom`/`mm_registration.acceptanceWindowTo` — date-range visibility gate per D16. The catalogue exposes only registrations whose `acceptanceWindow` covers `now()`.

**SQL-path evaluator integration:**
- `DeterminantEvaluator` AST analysis routing per spec §4.3.0 — fast-path-eligible if AST contains only operators in the closed twenty subset *and* references only `$applicant.*` / `$constant.*` / `$service.*` / `$registration.*` / `$selected_registrations.*`. SQL-path otherwise.
- Routing decision computed at publish, cached on the AST.
- SQL-path evaluator delegates to `joget-rules-api`'s `RuleScriptCompiler` for compile-to-SQL. The compiler emits parameterised SQL targeting the `app_fd_*` tables of the legacy farmer/parcel registry.
- L1/L2/L3 cache integration with `evaluator='fast'|'sql'` discriminator on `reg_bb_eval_audit` rows.
- Audit shape: SQL-path evaluations store rule source + compiled SQL hash + result-set summary.

**Migrate remaining 3 programmes to `mm_*`:**
- 3 more `mm_registration` rows under the same `farmers_subsidy` `mm_service`.
- Per-programme `mm_determinant` rows covering each programme's eligibility criteria. The mix of fast-path (self-attestation: `$applicant.member_of_cooperative eq 'Y'`) and SQL-path (registry-touching: `$registry.farmers.<NID>.totalParcelHectares lte 2`) is now real because the SQL-path evaluator is wired.
- Per-programme `mm_benefit` rows.
- Per-programme `mm_required_doc` rows.
- Programme-level metadata (`mm_registration.attributesJson` per D16 — varies per registration).
- The application wizard's `mm_screen` rows are unchanged from Phase 1 — same six screens, the engine merges per-Registration requirements at runtime per spec §6.1.6.

**Single-Window selection at the Guide screen:**
- The `kind='guide'` screen renders the catalogue of `mm_registration` rows under the `farmers_subsidy` service.
- For each Registration, the engine evaluates `applicabilityDeterminant` against the current applicant — if the Determinant returns true, the Registration is shown as applicable; if false, it is shown as unavailable with the reason.
- Citizen selects one or more Registrations (the operational reality is probably one-at-a-time, but the Single-Window pattern allows multiple). Selection is persisted in `app_application.selectedRegistrationIds`.
- The wizard's subsequent screens render fields, eligibility checks, benefit requests, and document uploads merged across the selected Registrations — duplicates deduplicated, strictest requirement winning.

**Cross-registry test (the four-programme test):**
- Pick 5 representative farmer profiles from the legacy `app_fd_farms_registry`. Each profile differs in district, parcel count, parcel size, cooperative membership, prior-subsidy history.
- For each profile × each of the 4 programmes = 20 application scenarios. For each scenario, the converged engine produces an eligibility outcome. The outcomes are validated against operator expectation — these are the cases Aare or a domain analyst pre-specifies as PASS/FAIL ground truth.
- **The four-programme test passes if the 20 outcomes match expectation, or any divergence has a clear root cause that resolves to a single fix (a Determinant correction, a capability registration, an extension scope clarification — *not* a Java engine change).**
- A divergence whose root cause is "the meta-model cannot express what the programme needs" reopens the audit per framework §7. This is the named risk; the team's discipline is to take that reading seriously.

### 5.2 Out of scope for Phase 2

- Userview generation (Phase 3).
- Operator inboxes (Phase 3).
- XPDL workflow per programme (Phase 3 — applications are processed manually through admin access in Phase 2).
- Programme designer in `mm_*` admin (Phase 3).
- `decision-engine-runtime` integration with the converged path — the legacy plugin continues to issue `imEntitlement` rows from operator decisions captured via direct admin access in Phase 2.

### 5.3 Parity criterion

The four-programme test (above) passes. Specifically:

1. All 4 programmes are configured in `mm_*` rows; no per-programme Java was added to the engine.
2. The engine selects which Registrations to show in the Guide screen by evaluating each programme's `applicabilityDeterminant` against the current applicant — including SQL-path Determinants that touch `$registry.parcels.*`.
3. Eligibility self-attestation (citizen-side, fast-path) and authoritative evaluation (server-side, SQL-path) produce consistent outcomes for the 5 farmer profiles × 4 programmes = 20 scenarios.
4. Benefit request grids correctly render each programme's `mm_benefit` rows; benefit subsidy formulas (using closed-twenty arithmetic operators) compute correct values.
5. Document upload grids correctly render each programme's `mm_required_doc` rows with `captureFieldsJson` per-document metadata fields.

If the four-programme test passes, the convergence has proven the metadata-driven property for the subsidy domain empirically. If it doesn't, the audit is re-opened per framework §7.

### 5.4 Retirement gate

**None.** Both stacks still running. The legacy `application-engine-runtime` continues to seed eligibility/benefit/document grids on the legacy `spApplication` form for the 4 programmes; the converged stack does the same for those programmes via `MetaScreenElement` + `repeating_group` widgets.

### 5.5 Risk surface

- **The four-programme test as audit-validation.** This is the empirical convergence test. A divergence may be a content bug (a `mm_determinant` rule mistranslated), a connector gap (a `$registry.*` capability missing a field), an extension gap (an `mm_*` shape can't express something), or genuine evidence that the audit's verdict was wrong. The risk is the team's discipline to surface the third or fourth reading rather than papering over with a quick fix.
- **SQL-path evaluator performance.** First end-to-end test against `$registry.parcels.summary` aggregation will reveal cache + connector + compiler interaction. Mitigation: small load test with 50 representative applicants in Phase 2 mid-point; if performance is unacceptable, decide whether to push aggregation into `derived-snapshot-runtime` snapshots.
- **Cross-programme determinant authoring drift.** With 4 programmes authored, the temptation to create slightly-different-shaped Determinants for similar concerns ("parcel size ≤ 2 ha" vs "total parcel area not exceeding 2") is real. Mitigation: review of all 4 programmes' Determinants together, with a discipline that semantically-equivalent rules use identical AST shape.
- **`mm_registration.acceptanceWindow` semantics.** When a registration's window closes, what happens to in-flight applications? Per spec §4.4.2 they are version-pinned and continue. But the citizen catalogue stops showing the registration. Edge case: a citizen who started a draft before window close and submits after. Mitigation: explicit policy in the test, decided in Phase 2 design.

### 5.6 Effort sketch

| Track | Work | Person-weeks |
|---|---|---|
| Schema | `optionsFilterJson` extension + `acceptanceWindow` extension | 1 |
| Engine | SQL-path evaluator integration + AST routing + cache + audit | 3 |
| Connector | Expand IM-connector capabilities for `$registry.farmers.*` and `$registry.parcels.*` references needed by the 4 programmes' Determinants | 2 |
| Content | Migrate remaining 3 programmes to `mm_*` | 2 |
| Content | Single-Window Guide screen rendering + selection logic | 1.5 |
| Test | 5 farmer profiles × 4 programmes = 20 scenarios; ground-truth specification with Aare | 1.5 |
| Test | Four-programme test execution + iteration | 2 |
| Buffer | Risk-surface mitigation, performance tuning | 2 |
| **Total** | | **15 person-weeks ≈ 8 calendar weeks at 2 FTE** |

Phase 2 is the empirical convergence test — when it passes, the metadata-driven property holds for the subsidy domain.

---

## 6. Phase 3 — Cutover, programme designer to `mm_*`, retirement

> **Scope updated by D17.** Userview generation moved from Phase 3 to Phase 1. Phase 3's userview-related work narrows to *switching the live menus* from legacy `spProgramMain`/`spApplication` to the publisher-generated catalogue + inbox; the *generation* itself is already done.

**Goal.** Citizens use the converged stack for subsidy applications. Operators design programmes through the `mm_*` admin CrudMenus. Legacy application-path components retire. Farmer and parcel registration are unchanged.

### 6.1 Deliverables

**Userview cutover (was: generation; per D17 generation moved to Phase 1):**
- Switch the citizen userview's home from the legacy menu structure to the publisher-generated catalogue (already deployed in Phase 1 — Phase 3 just changes which menu users land on by default).
- Switch the operator userview's inbox from the legacy `farmer_application` datalist to publisher-generated inboxes (similarly already deployed; Phase 3 changes default routing).
- Joget groups per `mm_role` created at publish (already in publisher per D17).

**Workflow authoring (XPDL):**
- One workflow: `farmers_subsidy_submission`, hand-built per spec §5.2's binding contract.
- `app_fd_reg_bb_binding` row hand-authored linking `(farmers_subsidy, 1.0)` to `regbb_farmers_subsidy#1#farmers_subsidy_process`.
- Submission backbone wired in self-call topology — `wf-activator` on the application's `kind='confirmation'` screen post-processor; `DocSubmitter` and `ProcessingServer` operate on the same instance.
- `RegBbEvaluatorTool` activities at workflow gateways for authoritative SQL-path eligibility evaluation.

**Programme designer migrates to `mm_*` admin:**
- The legacy `spProgramMain` + 9 tabs is the current programme-designer UX. Under convergence, programme design happens through the `mm_*` admin CrudMenus deployed in Phase 1: operators add a `mm_registration` row to the `farmers_subsidy` service, attach `mm_determinant` rows for eligibility, `mm_benefit` rows for what the programme delivers, `mm_required_doc` rows for documents.
- This is workable but the UX is a regression from `spProgramMain`'s wizard. **Phase 3 deliverable includes a small "Programme Builder" composite admin UI** that groups the relevant `mm_*` CrudMenus into a single guided flow — the operator sees Service → Registration → Eligibility → Benefits → Documents as four guided steps, each step writing to the relevant `mm_*` table. This is *not* `MetaScreenElement` rendering admin screens; it's a thin admin-side wizard that drives the same `mm_*` writes the bare CrudMenus would.
- After Phase 3 cutover, all programme design happens through Programme Builder. `spProgramMain` is deprecated.

**`MetaReviewElement` for operator review:**
- Render back-office processing screens dynamically from `mm_role_screen.sectionsJson` per spec §7.2.
- Phase 3 deploys `MetaReviewElement` for the subsidy service only (one `mm_role_screen` row per role).

**Cutover:**
- Switch the citizen userview's home from the legacy menu to the publisher-generated catalogue.
- Switch the operator userview's inbox from the legacy `farmer_application` datalist to publisher-generated inboxes.
- For one week, the converged stack handles all subsidy applications end-to-end. Legacy `spApplication` accepts no new submissions but remains readable.
- After 1 week of clean operation, retire.

### 6.2 Out of scope for Phase 3

- Farmer and parcel registration (continue running unchanged).
- Multi-instance topology (deferred indefinitely).
- `derived-snapshot-runtime` generalisation (separate cadence, neutral to convergence per framework §5.2).
- `notification-dispatcher-runtime` (Horizon-2 work; out of convergence).
- `reg-bb-credential-issuer` (later horizon; `decision-engine-runtime` continues running through Phase 3 and beyond).

### 6.3 Parity criterion

For one full week, the converged stack handles all subsidy applications. Legacy `spApplication` accepts no new submissions. Operators design programmes through the Programme Builder. Decisions are issued correctly through workflow + `decision-engine-runtime`. The four-programme test from Phase 2 continues to pass on Phase 3 infrastructure.

If at any point during the cutover week a critical issue requires reverting to the legacy stack, **revert immediately**, debug, restart the cutover week from day 1.

### 6.4 Retirement gate

Retirements at two checkpoints, both on Aare's go-ahead:

- **Checkpoint A (end of Week 17, ~1 week into cutover):**
  - Decommission `application-engine-runtime` (its function is now in `reg-bb-engine`).
  - Disable `FormQualityPostProcessor` on the `spApplication` family of forms; the SQL rule layer of `form-quality-runtime` no longer fires on the application path. **The plugin itself stays** — its other capabilities (status framework integration, banner element, registrations on `farmer_registration` and `parcel_registration` services) remain useful and orthogonal to convergence.
  - Decommission `identity-resolver-runtime` for the application path. The `mm_action` kind=`bot_pull` covers the `farmerByNid` use case in the converged stack. **Important: `identity-resolver-runtime` continues running on the farmer/parcel registration path if it's used there** — verify this before retirement; if it is used for parcel→farmer lookup, keep the plugin for that purpose.
  - Mark `spProgramMain` and its 9 tab subforms as deprecated; no new edits accepted; existing programme data exported to a forensic archive.
  - Mark `spApplication` and its child forms as deprecated; existing applications retained (read-only, accessible through legacy admin URLs for forensics).

- **Checkpoint B (end of Week 18, ~4 weeks of converged operation):**
  - Drop legacy `spProgramMain` form definitions from the JWA.
  - Drop legacy `spApplication` form definitions from the JWA.
  - Tables `app_fd_sp_program*` and `app_fd_spApplication*` are NOT dropped — keep them for forensics indefinitely. They are simply no longer referenced by any active form.

`decision-engine-runtime` continues running. Its retirement is gated by `reg-bb-credential-issuer` reaching parity, which is post-convergence work.

`form-quality-runtime` plugin continues running for `farmer_registration` and `parcel_registration` services (both out of convergence scope). Only its application-path role retires.

### 6.5 Risk surface

- **Programme Builder UX regression vs. `spProgramMain`.** The guided wizard `spProgramMain` provides — programme code generation, status workflow, validation across tabs — is rich. The Programme Builder over `mm_*` CrudMenus is a leaner experience by design. Mitigation: dry-run with operator(s) before Phase 3 ends; surface specific UX gaps; decide whether to invest in additional Programme Builder polish or accept the leaner UX.
- **XPDL workflow correctness.** First time the analyst-side workflow authoring is tested. Mitigation: start XPDL design during Phase 2 so it's ready at Phase 3 start; pair-author the XPDL with a dry-run against a representative application.
- **Operator review screens via `MetaReviewElement`.** First non-citizen-side use of metadata-driven rendering. The Lesotho operator workflow has nuances (verification statuses, rejection reasons, decision tracks) that may not transfer cleanly to a generated review surface. Mitigation: dry-run with case worker during Week 17 prep; revise `mm_role_screen.sectionsJson` based on feedback; reserve `mode='hand_built'` as opt-out.
- **Submission backbone self-call coupling.** Self-call topology in single-instance is cleaner than mocking, but has subtle coupling risk. Mitigation: keep sender and receiver configurations cleanly separable so any future split is trivial.
- **Forensic data preservation.** Dropping `app_fd_sp_program*` form definitions in Checkpoint B is reversible (the data tables stay), but operationally, accessing the data without the form definitions requires raw `SELECT` queries. Mitigation: forensic archive as PostgreSQL dump before Checkpoint B; document the retrieval procedure.

### 6.6 Effort sketch

| Track | Work | Person-weeks |
|---|---|---|
| Engine | `MetaReviewElement` design + implementation | 1.5 |
| Engine | Userview generation in `reg-bb-publisher` | 1 |
| Workflow | XPDL design + authoring for the subsidy workflow | 1.5 |
| Workflow | Submission backbone wiring (self-call) | 0.5 |
| Admin UX | Programme Builder composite admin UI | 1.5 |
| Cutover | Pre-cutover dry runs + operator UX review + ground-truth validation | 1 |
| Cutover | Cutover week + stabilisation | 1 |
| Retirement | Plugin decommissioning + forensic archive | 1 |
| **Total** | | **9 person-weeks ≈ 4–5 calendar weeks at 2 FTE** |

---

## 7. Standing rules during convergence

These apply throughout, in addition to the framework's own rules (§4 / §8 of `convergence-framework.md`).

### 7.1 Out-of-scope discipline

Farmer registration and parcel registration are explicitly out of convergence scope. Three operational consequences:

- **No mm_* migration of farmer/parcel forms.** Even if a Phase 1 or Phase 2 task surfaces a "natural" reason to migrate something on the farmer/parcel side, the answer is no. The reason for being out-of-scope is to deliver subsidy convergence quickly; relaxing that scope is a decision Aare makes deliberately, not one a team member makes mid-task.
- **Legacy farmer/parcel forms continue accepting writes.** Determinants reading via `$registry.*` are read-only; the IM connector never mutates legacy tables.
- **No new code dependencies from the converged stack onto farmer/parcel-specific Java.** The IM connector's adapters are generic JDBC reads, not Lesotho-specific code. (`farmer-derived-plugin` continues running; nothing in convergence depends on its internals.)

### 7.2 Drift discipline (unchanged from framework)

Every architectural decision during convergence is classified per framework §2: serves the trio, neutral, competes. Decisions classified as "competes" are refused; the refusal proposes an alternative.

### 7.3 Parity gates are real

A phase does not start until the previous phase's parity criterion is demonstrably met. The four-programme test in Phase 2 is the convergence's empirical validation; if it fails for reasons that look like meta-model gaps, the audit is re-opened.

### 7.4 Decision authority and decision log

Aare decides retirement gates, scope changes, audit-reopening events. Every such decision lands a one-paragraph entry in `docs/architecture/decision-log.md`.

### 7.5 Drift mode 2 vigilance

Six `mm_*` extensions are pre-authorised by the audit's multi-service evidence. **No other extension is added during the convergence without a fresh audit.** If a phase surfaces an apparent need for a new extension, the team's response is: pause, document, look for a workaround, escalate to Aare with evidence. Adding "just one more field" is the signature of drift mode 2 (framework §4.2).

### 7.6 Pre-production freedom

The plan exploits dev/test status: data loss between phases is acceptable; UX regressions during cutover weeks are acceptable; revert-and-retry preferred. **This freedom evaporates at production launch.** Convergence completes before any production launch.

---

## 8. Open questions (tracked, not blocking)

| # | Question | Resolve during |
|---|---|---|
| Q1 | Which of the 4 programmes is the simplest? Operator's call. Recorded in decision log. | Phase 1 design week |
| Q2 | Final `mm_benefit` shape — separate entity, polymorphic with `mm_fee`, or a `mm_fee` extension with a `kind` discriminator? | Phase 1 design week |
| Q3 | Where does aggregation for `$registry.farmers.<NID>.totalParcelHectares` live? Connector-side query (Phase 1 default) or `derived-snapshot-runtime` snapshot (if Phase 2 performance forces it)? | Phase 2 mid-point |
| Q4 | What happens to applications submitted with `mm_registration.acceptanceWindow` open at draft-time but closed at submit-time? Policy decision. | Phase 2 design |
| Q5 | The Programme Builder admin UI — wizard scope. Light grouping of CrudMenus or a richer guided flow? Trade-off: engineering effort vs operator UX. | Phase 3 design |
| Q6 | Does `decision-engine-runtime` integrate with the converged stack's decision capture, or does the converged stack's decision flow into `imEntitlement` directly via a workflow tool? Affects Phase 3 scope. | Phase 3 design |
| Q7 | When does Stage 2 (farmer/parcel migration) start? Trigger criteria: Stage 1 has been operating cleanly for at least 3 months in production; a business need surfaces (e.g., a second back-office wants to consume farmer data via a stable API; a new field on the farmer record is needed and would be cleaner authored in `mm_*`); team capacity permits. | After Stage 1 stabilisation |

---

## 9. Calendar at a glance

Assuming Phase 0 completes 2026-04-30 and Phase 1 starts 2026-05-04:

| Phase | Start | End (estimated) | Calendar weeks |
|---|---|---|---|
| 0 | (running) | 2026-04-30 | — |
| 1 | 2026-05-04 | 2026-06-12 | 6 |
| 2 | 2026-06-15 | 2026-08-07 | 8 |
| 3 | 2026-08-10 | 2026-09-04 | 4 |
| **Subsidy convergence complete** | | **2026-09-04** | |

Total: ~18 calendar weeks (~4.5 months) from Phase 1 start to Stage 1 complete (converged subsidy stack live). The schedule is illustrative; the plan's commitments are scope and parity gates, not dates.

---

## 10. Stage 2 — completing the convergence (sketch only)

Stage 2 takes the convergence to its full destination: farmer registration and parcel registration migrate to `mm_*`, the submission backbone splits into citizen-portal vs back-office topology if business need calls for it, and the Lesotho system reaches the trio's commitments across all services.

This section is a **sketch, not a plan**. Detailed phasing for Stage 2 happens after Stage 1 stabilises in production.

### 10.1 What Stage 2 unlocks

- The framework's §3.1 commitment ("the meta-model is the source of truth") becomes literally true for the Lesotho system. Today's parallel-source-of-truth state (legacy farmer/parcel forms alongside `mm_*` for subsidies) closes.
- The audit's farmer-registration findings (`mm-completeness-audit.md` §3) translate into deployment work — the two extensions farmer registration needs (`mm_field.widget=repeating_group` already deployed in Stage 1, cascading select via `optionsFilterJson` likely already deployed too) are reused; no new extensions are introduced unless a new audit finds them.
- A second back-office (e.g., MoH) can be onboarded as configuration: `mm_*` rows + a new Joget instance + IM connector capabilities, no engineering tickets.
- The `derived-snapshot-runtime` generalisation (architecture-overview §2.7) becomes a natural Stage-2 task because the snapshot's source-form list is now `mm_*`-driven rather than hardcoded.

### 10.2 Likely shape of Stage 2 phases

A first-cut decomposition (subject to revision when Stage 2 is actually planned):

- **Stage-2 Phase 1: Migrate parcel registration to `mm_*`.** The smaller of the two; reuses Stage 1's engine and the `repeating_group` extension. Run alongside the legacy `parcelRegistration` form during cutover.
- **Stage-2 Phase 2: Migrate farmer registration to `mm_*`.** The larger one. Includes the household-members repeating section, the crops/livestock repeating section, and the GIS polygon as widget=`gis_polygon` per spec §4.1.6.
- **Stage-2 Phase 3: Multi-instance split if business need calls for it.** Citizen portal vs AgriMin back-office, per framework §6. The submission backbone moves from self-call to inter-instance.
- **Stage-2 Phase 4: Onboard a second back-office** (if any).

Effort estimate (rough, 2 FTE): Stage-2 Phase 1 ~6 weeks; Stage-2 Phase 2 ~10 weeks; Stage-2 Phase 3 ~6 weeks (only if executed); Stage-2 Phase 4 highly variable.

### 10.3 What Stage 2 inherits from Stage 1

Stage 2 starts with most of the engine and schema work already done. Specifically:

- `reg-bb-engine` and `reg-bb-publisher` exist and are battle-tested on the subsidy services.
- All twelve `mm_*` form definitions are deployed.
- Three of the six Lesotho-instance extensions (`repeating_group`, `mm_benefit`, `mm_registration.attributesJson`) are in production. The other three (`mm_required_doc.captureFieldsJson`, `mm_registration.acceptanceWindow`, `optionsFilterJson`) are deployed if Stage 1 needed them.
- The DSL grammar / fast-path partition is wired and audited.
- The IM connector's adapter pattern for legacy table reads is proven.
- `joget-rule-editor` is the canonical Determinant authoring tool.
- The `MetaScreenElement` rendering pipeline handles wizards, conditional fields, repeating sections, and custom widgets.

This is what makes Stage 2 cheaper than a fresh start. The framework's commitments and the audit's classifications carry forward unchanged.

### 10.4 What needs deciding before Stage 2 starts

Five questions, none of which need answers now:

1. Has the audit's verdict held empirically? Stage 1's four-programme test, plus six months of operational data, is evidence about whether the meta-model handles Lesotho's domain. Stage 2 should be re-validated against fresh data before commitment.
2. Are there new domains (services beyond subsidies) joining the system? Stage 2's effort might absorb them as a side benefit.
3. What is the operational profile of Stage 1 in production? Performance bottlenecks, operator UX gaps, edge cases — these inform Stage 2's design priorities.
4. Has `farmer-derived-plugin` been generalised into `derived-snapshot-runtime` per architecture-overview §2.7? If yes, Stage 2 can rely on it; if no, Stage 2 may need to do that generalisation as part of its work.
5. Is the multi-instance split actually required, or has single-instance proved adequate? Stage-2 Phase 3 happens only if the answer is "required."

---

*End of migration plan revision 2. Open for review and revision; not yet accepted.*
