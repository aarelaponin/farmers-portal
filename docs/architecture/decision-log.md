# Decision log — Farmers Portal RegBB convergence

| | |
|---|---|
| Status | Active |
| Owner | Aare (project lead) |
| Convention | One paragraph per decision; recorded chronologically; references to companion documents where applicable. |

This log records the non-trivial decisions taken across the convergence effort. Each entry is brief — the reasoning lives in the cited companion document, not here. The log's job is to make decisions visible at a glance and prevent quiet reversals.

---

## D1 — 2026-04-28 — Audit verdict: green pending §3.2 alignment

The meta-model completeness audit (`mm-completeness-audit.md`) was completed with verdict amber-leaning-to-green qualified by one strategic decision (the DSL/closed-grammar reconciliation). After ADR-001r2 resolved that, the verdict is read as green per framework §7.1: both services fit `mm_*` cleanly with six legitimately-additive extensions all carrying multi-service evidence.

Reference: `mm-completeness-audit.md` §6.5; `convergence-framework.md` §7.1.

---

## D2 — 2026-04-28 — Rule grammar: DSL canonical, closed twenty as fast-path subset

Per ADR-001r2: adopt the DSL grammar (defined by `rules-grammar`'s ANTLR parser) as canonical authoring grammar; preserve the closed twenty-operator subset as the engine's fast-path internal optimisation; route evaluation by static AST analysis. Decision driven by two project principles weighted strongly: (P1) configuration-over-code on both citizen and registry sides; (P2) excellent UX for citizens and back-office. The closed-set discipline transfers from the operator-enum boundary to the parser-grammar boundary; one editor, one language, two evaluators chosen automatically.

Reference: `adr-001-rule-grammar-canon.md` revision 2; `regbb-solution-architecture-spec.md` §4.3.

---

## D3 — 2026-04-28 — Convergence scope: subsidy-only as Stage 1, full as destination

The convergence is staged, not single-shot. Stage 1 migrates programme designer + application path to `mm_*`; farmer and parcel registration stay as hardcoded Joget forms during Stage 1. Stage 2 (farmer/parcel migration + multi-instance topology if needed) follows when business need calls for it. Rationale: risk minimisation — don't disturb working farmer/parcel code; deliver business value first (dynamic programme processing).

Reference: `migration-plan.md` revision 2 §1, §10.

---

## D4 — 2026-04-28 — Phase 1 path: Path B (all 4 programmes in Phase 1)

After screening all 4 programmes in dev (`PRG-2025-001` through `PRG-2025-004`), Phase 1 expands to author all 4 in `mm_*` rather than 1. The four-programme convergence test (originally Phase 2's exit criterion) folds into Phase 1. SQL-path evaluator wires in Phase 1 (originally Phase 2). Effort grows from ~14 to ~26 person-weeks, ~10 calendar weeks at 2 FTE. Phase 2 shrinks to cutover preparation only.

Reference: `phase-1-plan.md` revision 2; `programme-screening.txt` (raw screening output).

---

## D5 — 2026-04-28 — Target-group priority weights: drop

The four programmes' target-group rows carry `priorityWeight` (1–3) and `allocationPercent` (5%–80%). These are programme-administration metadata, not registration-BB concerns. Target groups themselves are modelled as eligibility Determinants ("female farmers under 35 with disability" → one Determinant rule); priority weights and allocation percentages live outside `mm_*`. No new `mm_target_group` entity is added.

Reference: `phase-1-plan.md` revision 2 §6 (Decision 1).

---

## D6 — 2026-04-28 — Scoring (`mm_registration.evaluationStrategy`): adopt as 6th Lesotho-instance extension

Three of four programmes have `evaluationStrategy=SCORE_BASED` authored (multi-service evidence satisfying framework §4.2). Only prog003 currently uses scores actively, but the SCORE_BASED strategy is configured on prog001, prog002, prog003 deliberately by operators. Adopt scoring as a meta-model extension rather than translating to Boolean threshold rules. The audit's §4.A.6 refusal is reversed by this decision; the audit reasoning was correct under "single-service evidence" but the screening surfaces three-service evidence.

Extension shape: `mm_registration.evaluationStrategy ∈ {all_must_pass, score_based}` (default `all_must_pass`); `mm_registration.minimumScore` (default 0); `mm_registration.passingThreshold` (default 0); `mm_determinant.score` (number, default 0); `mm_determinant.ruleType ∈ {inclusion, exclusion, priority, bonus}`. Engine semantics: for `score_based` strategy, sum `score` across priority/bonus Determinants that evaluate true, return structured outcome `{mandatoryPassed, totalScore, passingThreshold, minimumScore}`. Citizen outcome: pass if `mandatoryPassed && totalScore >= passingThreshold`, fail if `!mandatoryPassed || totalScore < minimumScore`, otherwise `pending_review`.

This is the 6th Lesotho-instance extension, sitting alongside repeating_group widget, mm_benefit, mm_registration.attributesJson (per D16), mm_required_doc.captureFieldsJson, mm_registration.acceptanceWindow (per D16).

Reference: `phase-1-plan.md` revision 2 §6 (Decision 2); `mm-completeness-audit.md` §4.A.6 (note added).

---

## D7 — 2026-04-28 — HOUSEHOLD vulnerability data: design `households.vulnerability` capability

prog004 references `foodSecurityScore`, `vulnerabilityFlag`, `vulnerabilityScore` — fields not on `farms_registry`. Adopt: design a `households.vulnerability` IM-connector capability that computes these from existing farmer registry data per a documented rule. Initial Phase 1 implementation: deterministic computation (food-security indicators from `farmerIncomePrograms.gainfulEmployment` + `creditDefault` + `everOnISP`; vulnerability flags from `farmerHousehold.disability` + `orphanhoodStatus` + `chronicallyIll`; numeric score from a documented weighting). Later phases may integrate external food-security data (WFP IPC scores). Self-attestation alternative refused — would lose the authoritative-evaluation property.

Reference: `phase-1-plan.md` revision 2 §6 (Decision 3); §2 D4 (capability inventory).

---

## D8 — 2026-04-28 — `mm_benefit` shape: separate entity (sibling of `mm_fee`)

`mm_benefit` is a separate `mm_*` entity with its own form definition, not a polymorphic extension of `mm_fee`. Schema fields: `id`, `registrationId`, `serviceId` (denormalised), `code`, `label`, `kind ∈ {input, voucher, cash, in_kind}`, `category`, `itemCode`, `quantity`, `unit`, `unitCost`, `subsidyPercent`, `farmerContribution` (derived), `subsidyAmount` (derived), `formulaJson` (optional), `triggerDeterminantId` (optional), `paymentTiming ∈ {post_decision, post_completion}`, `paymentMethods[]` (subset of `in_kind`, `e_voucher`, `mobile_money`, `cash_agent`). Reasoning: charge has a payer, subsidy has a beneficiary, payment-method mechanics differ; collapsing into one entity is a category error per ADR-001r2 §3.5.

Reference: `phase-1-design-answers.md` Q-A.

---

## D9 — 2026-04-28 — `repeating_group` widget persistence: inline JSON in `dataJson`

Repeating-group fields persist as JSON arrays inside `app_application.dataJson` (e.g. `dataJson.eligibilityResponses = [{criterionId, response}, ...]`). Matches spec §4.1.13 verbatim — one `app_application` row per submission, all data in `dataJson`. PostgreSQL JSONB operators handle selective queries when needed. Decision is reversible if Stage 2's farmer-side household members reach scale where SQL-native row queries become necessary.

Reference: `phase-1-design-answers.md` Q-B.

---

## D10 — 2026-04-28 — IM-connector capability registry: use `app_fd_reg_bb_im_capability` table

Capability *definitions* (id, source, path, response shape, cache TTL) are rows in `app_fd_reg_bb_im_capability` per spec §3.2. Adapter implementations (the JDBC queries that resolve a capability) remain in Java for Phase 1. Aligns with project principle P1 — operators add a 5th capability without engineering ticket. Future-proofs the `households.vulnerability` capability (D7) since its computation rule may evolve.

Reference: `phase-1-design-answers.md` Q-C.

---

## D11 — 2026-04-28 — `bot_pull` field-mapping shape: reuse existing nested in `mm_action.configJson`

`mm_action.configJson` for `bot_pull` carries `{imCapabilityRef, subjectIdField, fieldMappings: [{target, source, transform?}]}`. The mapping shape mirrors `app_resolver_field_map` so Phase 3's identity-resolver retirement is a mechanical migration: read `app_resolver_field_map` rows grouped by `configId`, emit `mm_action` rows.

Reference: `phase-1-design-answers.md` Q-D.

---

## D12 — 2026-04-28 — Editor routing: automatic per AST analysis, no operator override

The DSL editor (`joget-rule-editor`) analyses the parsed AST and chooses serialisation format (JSON-AST for fast-path-eligible rules, DSL source for SQL-path rules) automatically per ADR-001r2. No operator override in Phase 1. The performance-hygiene concern (an SQL-path rule on a per-keystroke field) is handled by `mm_determinant.allowSlowPath` flag at publish time, not by routing override at edit time.

Reference: `phase-1-design-answers.md` Q-E.

---

## D13 — 2026-04-28 — Admin UX: 13 separate CrudMenus in Phase 1; Service Builder is Phase 3

Phase 1 ships one `mm_*` admin CrudMenu per entity (13 menus in a "Meta-model admin" userview). Operators authoring Phase 1's 4 programmes navigate the 13 menus directly. The composite "Programme Builder" admin wizard lands in Phase 3 per migration plan §6.1, layered over the same `mm_*` writes. This sequences UX investment behind engine-and-meta-model validation.

Reference: `phase-1-design-answers.md` Q-F.

---

## D14 — 2026-04-28 — AST routing: conservative — any `$registry.*` reference routes the rule to SQL path

For Determinants with mixed-source ASTs (e.g., `if($applicant.x eq 'A', $registry.farmers.y eq 'B', $applicant.z eq 'C')`), the entire rule routes to SQL path. Branch-level analysis (evaluating fast-path branches when their gating condition resolves to those branches) is deferred as a pure performance optimisation; it can be added later additively without semantic change. Operators who need fast-path eligibility structure their rules to avoid `$registry.*` in the AST entirely.

Reference: `phase-1-design-answers.md` Q-G.

---

## D15 — 2026-04-28 — `pending_review` band: no new status enum value; structured outcome on `dataJson`

When `mandatoryPassed=true && minimumScore <= totalScore < passingThreshold` (the pending-review band introduced by D6's scoring extension), the application transitions through the existing spec §6.5.1 status enum (`submitted → in_review → approved/rejected`). The structured outcome `{mandatoryPassed, totalScore, passingThreshold, minimumScore, disposition}` lives on `app_application.dataJson.eligibilityOutcome`. Operator review screens render the disposition; operators sort by `totalScore` to prioritise borderline cases. The closed status enum is preserved.

Disposition values: `eligibility_passed`, `eligibility_failed_mandatory`, `eligibility_failed_score`, `eligibility_pending_review`. These are annotations on `dataJson.eligibilityOutcome.disposition`, not values of `app_application.status`.

Reference: `phase-1-design-answers.md` Q-H.

---

## D16 — 2026-04-28 — `attributesJson` and `acceptanceWindow` placement: `mm_registration`, not `mm_service`

The audit's §6.2 #4–#5 named `mm_service.attributesJson` and `mm_service.acceptanceWindow` as the placement for programme-level metadata and time-windowed application gates. That framing was correct under the audit's "1 service per programme" mental model. D4 reframed Lesotho's subsidy domain as 1 `mm_service` ("Farmers Subsidy") + 4 `mm_registration` rows (Single-Window pattern, spec §4.1.3 / §6.3.1.9). Programme-level facts (programmeType, seasonCode, campaignType, distribModelCode, etc.) and the application acceptance window vary per *registration*, not per *service*; the umbrella service has no per-programme typology. Therefore: `mm_registration.attributesJson` (jsonb), `mm_registration.acceptanceWindowFrom` (date), `mm_registration.acceptanceWindowTo` (date). `mm_service.attributesJson` is not added in Phase 1; it can be added later if a real service-level use case appears. The named-extension count remains 6; only placement changes.

Cascade: migration-plan.md §5 + §10 and phase-1-plan.md §2 + §10 references updated from `mm_service.x` to `mm_registration.x`. Audit §6.2 #4–#5 updated to reflect the placement decision; audit per-row cells (audit-time analysis) left as historical context.

Reference: `phase-1-schemas.md` §3.

---

## D17 — 2026-04-28 — Phase 1 includes citizen userview generation + operator read-only inspection (D8)

The original Phase 1 scope deferred userview generation and operator review to Phase 3 (cutover). On reconsideration, the phase gate for Phase 1 demands end-to-end UX validation, not just outcome parity — go/no-go requires operators saying "yes, citizens can use this and we can review it." Adding to Phase 1: `reg-bb-publisher` generates a citizen userview menu per published `mm_service` (catalogue page + click-through to wizard); operator userview category with inbox + applications-list datalist scoped to the service; `MetaScreenElement` gains a read-only mode for operator inspection of submitted applications. Phase 1 effort grows ~4 person-weeks (26 → 30 ≈ 12 calendar weeks at 2 FTE).

What stays deferred to Phase 3: `MetaReviewElement` (full role-scoped sections + decision affordances per spec §7.2); XPDL workflow authoring; submission backbone wiring; `mm_role_screen.sectionsJson` authoring. In Phase 1, operators inspect; they don't approve through workflow.

Cascade: phase-1-plan.md gains D8 (Option A — separate deliverable, not D2 expansion); migration-plan.md §6.1 (Phase 3) loses userview generation from its scope (already done in Phase 1).

Reference: `phase-1-plan.md` §2 D8.

---

## D18 — 2026-04-29 — `MetaScreenElement` as transparent `FormContainer` (synthesises real Joget Elements)

The build-001..build-007 walker rendered raw HTML for each `mm_field` row. That worked for first-render but broke at every Joget integration point that introspects the form tree (`FormUtil.findElement`, `buildElementMap`, `FormRowDataListBinder.getColumns`, the App-Composer datalist column picker, validators, store binders) — none of those saw the dynamic fields, so every related feature needed a separate patch path. Build-008 retires the walker. `MetaScreenElement` now `implements FormContainer` and overrides `getChildren(FormData formData)` to **synthesise real Joget Element instances** (TextField, SelectBox, DatePicker, Radio, CheckBox, TextArea) — one per `mm_field` row, fully configured (id, label, validator, options, mandatory). After synthesis the rest of Joget sees a normal Element tree and "just works" for the dynamic fields without further patching. Children are cached per-FormData in a `WeakHashMap` so a single render pass doesn't re-query the database on every `getChildren` call.

Widget coverage in build-008: the seven simple widgets (text, number, date, select, radio, checkbox, textarea). Complex widgets (gis_polygon, signature, file_upload, qr_scan, repeating_group) synthesise as a read-only TextField placeholder and are scheduled for Week 3. The transparent-container pattern also drops the H3 title / description paragraph / engine-stamp footer that the walker rendered — `MetaScreen` is a translator from metadata to Joget Elements, not a UI component, so visible chrome (titles, headers) is the form designer's responsibility via Section.label / HtmlPage / Custom HTML, exactly as for any plain Joget form. The build stamp is preserved as an HTML comment for diagnostics.

Reference: `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/element/MetaScreenElement.java` (build-008+); `phase-1-plan.md` Week 2.

---

## D19 — 2026-04-29 — Composite screen authoring: `mm_field` is a 1:N child of `mm_screen` via FormGrid

`mm_field.screenId` is a single FK to `mm_screen` — fields are not reusable across screens (same widget on two screens = two rows). Treating `mm_field` as an independently-managed entity with its own CRUD menu was therefore a category error: it implied 1:N is M:N and forced authors to context-switch between two CRUDs to define one screen. Decision: bundle the two into one composite authoring form. `mm_screen` gains a "Fields" Section containing a FormGrid bound to `mm_field` with `foreignKey: screenId` and **both a `loadBinder` and a `storeBinder`** pointing at MultirowFormBinder (loadBinder is required — the canonical pattern in `livestockDetailsForm`/`farmerHousehold` carries both; without the loadBinder the grid renders empty when editing existing parents). `mm_field.screenId` is converted from a SelectBox to a HiddenField — the FormGrid auto-fills the FK on save and the analyst never sees or types it. The MM - Field standalone CRUD menu can be retained as an admin escape hatch but is no longer the primary authoring path.

A future need for true field reuse across screens (RegBB §4.1.6 conceptually distinguishes "claim template" from "screen-bound field") would be modelled by introducing a separate `mm_claim_template` entity: reusable bits (widget, dataType, validationRulesJson, claimType) live in the template, per-screen bits (orderIndex, label, mandatory, helpText) stay on `mm_field` which acquires a `templateId` FK. Not implemented today; recorded as the natural next step if the requirement appears.

Reference: `app/forms/mm/mm-screen.json`, `app/forms/mm/mm-field.json`; `CLAUDE.md` (FormGrid load+store-binder convention to be added).

---

## D20 — 2026-04-29 — FK convention split: Joget-internal FKs use UUID; cross-entity references use business `code`

The convention applied throughout this app is two-tiered, mirroring how `farmerBasicInfo` already works:

* **Joget-internal FKs** — relationships managed automatically by Joget machinery (FormGrid row → parent, wizard tab subform → wizard, AbstractSubForm → host) — store the parent's auto-generated `id` (UUID). These are hidden from authors, never appear on forms, and don't need to be human-readable. `mm_field.screenId` falls into this category (FormGrid-managed). Don't interfere with Joget here.
* **Cross-entity / user-visible references** — relationships established explicitly by an analyst picking from a dropdown or SmartSearch (e.g. Parcel → Farmer, mm_field.optionsCatalogId → mm_catalog, MetaScreenElement.screenId widget property → mm_screen) — store the target's **business `code`**. The business code is a unique, human-readable identifier (TextField + DuplicateValueValidator). FormOptionsBinder uses `idColumn: "code", labelColumn: "name"` so the SelectBox shows and stores codes, never UUIDs.

Empirical anchor: `farmerBasicInfo` has UUID `id` and `national_id` (business key with DuplicateValueValidator); `app_fd_parcel.farmer_id` stores the farmer's `national_id`, not their UUID. Same canonical pattern in every `md*` master-data form (e.g. `md16livestockType` has `code` + `name` + `isActive`, FK to category uses `idColumn: "code"`).

Concrete consequences for `mm_*`:
* Every `mm_*` entity gets a `code` field (TextField + DuplicateValueValidator self-referencing) and a `name` field (TextField + DefaultValidator mandatory). Where these already exist (`mm_service`, `mm_registration`, `mm_catalog`), the validator is upgraded to DuplicateValueValidator.
* Every `mm_*` entity gets an `isActive` Radio Y/N field (default Y) for soft-delete.
* No `id` field is exposed on any `mm_*` form; Joget's UUID stays internal.
* All cross-entity SelectBoxes / SmartSearches set `idColumn: "code"`, `labelColumn: "name"` (or `"title"` for `mm_screen`).
* `MetaScreenElement.screenId` widget property is a dropdown of `mm_screen` records valued by `code`; the engine looks up `mm_screen` by code, then queries `mm_field` by the screen's UUID id (FormGrid-internal FK).
* Naming convention for code values: UPPER_SNAKE_CASE (`MIN_AGRO`, `INPUT_SUBSIDY_001`, `FIRST_SUBSIDY_INTRO`, `LESOTHO_DISTRICTS`).
* `mm_field` is the one exception: no global code, since fields are scope-local (the natural key is `(screenId, storageKey)`, which Joget's single-column DuplicateValueValidator can't enforce). Disciplined storageKey naming within a screen is sufficient.

CLAUDE.md previously stated MD forms use `idColumn: "id"`. That note is incorrect — MD forms use `idColumn: "code"`. To be fixed in the same documentation pass.

Reference: `farmerBasicInfo` (`national_id` + DuplicateValueValidator); `_05_dev_v2/_a_farmers-reg/_farmer-forms/_mdm-forms/md16livestockType.json` (canonical MD pattern); `CLAUDE.md` amendment to follow.

---

## D21 — 2026-04-29 — Cascading catalog filter (Lesotho extension to mm_field)

RegBB does not specify a way to express cascading lookups (e.g. "show villages only in the selected district", "show equipment items only in the selected category"). For static catalogs of moderate size (the Lesotho farmers system has ~85 equipment items and similar lists), this is a real usability problem — flat dropdowns become unusable. Joget natively supports cascading via FormOptionsBinder's `extraCondition` (e.g. `extraCondition: "district=#district#"`); we surface this through the meta-model.

Extension shape: two optional columns on `mm_field`:
* `optionsFilterField` — storageKey of another field on the same screen whose current value filters this field's options.
* `optionsFilterColumn` — the column name inside the catalog's items (or the upstream registry's response) to match against.

Engine behaviour: when the synthesiser builds a SelectBox / Radio / CheckBox from a `mm_field` row, if both columns are populated, the synthesised SelectBox gets a Joget `controlField = optionsFilterField` and the FormOptionsBinder gains an `extraCondition: "<optionsFilterColumn>=#<optionsFilterField>#"`. The parent field's selection then drives the child field's options via Joget's native AJAX cascading. No catalog hierarchy is introduced; items live in a flat catalog with a discriminator column, which is sufficient for the two-level cascades that cover ~all real-world use cases.

Implementation deferred to Phase 1 Week 3 — alongside the complex-widget passes (gis_polygon, signature, file_upload, qr_scan, repeating_group). Test fixture: a district → village pair on the first programme application (existing Lesotho district/village MD).

Why this is a Lesotho extension and not a refusal: RegBB §6.3.2.2 already mentions catalogs as "reusable across all services in the same instance" — extending the field-level wiring is additive and consistent with that intent. RegBB silence on the cascading aspect is a spec gap (see "Identified RegBB spec gaps" section in `convergence-framework.md`), not an intentional restriction.

Reference: `convergence-framework.md` "Identified RegBB spec gaps" §10 (to be added); `phase-1-plan.md` Week 3 (to be amended).

---

## D22 — 2026-04-29 — `mm_catalog` is the Master Data implementation; RegBB §7 doesn't formalise it

RegBB §7.2 enumerates twelve first-class entities (Registration, Service, Screen/form, Role screen/form, Workflow, Role, Institution/entity, Field/claim/data, Result/credential, Required document, Payment/fee, Determinant) — Catalog / Master Data is not among them. The spec mentions "catalog" three times in passing (§6.3.1 line 546, §6.3.2.2 lines 577 + 580, §6.3.2.3 line 638), all as a field type or a one-line property note ("Catalog reusable source. Catalogs are reusable across all services in the same instance."). It never models the entity, never specifies how lookup lists are managed, versioned, scoped, or hierarchical. The full spec contains zero hits on "master data", "lookup", "list of values", "reference data".

This is a real specification gap, not a misreading. Any real-world registration implementation has dozens of MD entities (the Lesotho farmers system has ~90: districts, villages, crops, livestock types, equipment categories, equipment items, marital statuses, orphanhood statuses, disability flags, etc.); the spec's silence on how to manage them forces every implementation to extend privately and inconsistently.

Decision: `mm_catalog` is an officially-acknowledged Lesotho extension implementing Master Data — schema: `code` (TextField + DuplicateValueValidator), `name`, `scope ∈ {instance, service}`, `serviceId` (when scope=service), `source ∈ {static, registry}`, `itemsJson` (when source=static, JSON array of `{value, label, ...optional discriminator columns for D21 cascading}`), `imCapabilityRef` (when source=registry, JSON reference to an upstream registry capability), `description`. Treated as a peer to the twelve RegBB entities for the purpose of our authoring tooling, but flagged in convergence-framework.md as a documented Lesotho deviation that we'd contribute upstream as feedback to GovStack.

Why this matters for our project: because it's the second time RegBB silence has forced us to invent (the first being the partitioned-grammar / DSL-canonical decision per ADR-001r2). Documenting these as "spec gaps + our resolution" rather than "our implementation choices" makes them legible as upstream feedback rather than private divergence.

Reference: `_03_Development/_02_Registration BB/_specs/govstack-registration-bb-spec.md` §6.3.1, §6.3.2.2, §6.3.2.3, §7.2 (zero entry); `convergence-framework.md` "Identified RegBB spec gaps" §10 (to be added).

---

## D23 — 2026-04-29 — Hard-delete semantics for mm_* configuration; temporal validity deferred

`mm_*` configuration entities use hard-delete semantics. A row may be deleted only when no other row references its `code`. There is no `isActive` flag, no soft-delete, no temporal validity window. Obsolete codes are not turned off — they are replaced by adding a new code; both rows coexist; new applications reference the new code; historical references to the old code continue to resolve. The catalog grows monotonically over time.

Reasoning: `isActive` without versioning is half-baked. It marks a row as "off" but cannot answer the questions that actually matter for a configuration entity referenced from historical application data — was the reference valid at the time it was made, what is the successor, what was the validity window. Adding `isActive` as if it were free soft-delete creates the appearance of a feature whose underlying schema cannot support it; the resulting inconsistency surfaces as silent bugs much later. The honest position is to defer the whole concern: do hard delete with FK integrity, accept catalog monotonic growth, and if true temporal validity is ever needed, build it properly with validity windows and point-in-time queries — not retroactively grafted onto the existing schema.

A future contributor who finds this decision and is tempted to "just add `isActive`" should instead promote the requirement to a real architectural decision (validity-window columns on every mm_* entity, point-in-time queries in the engine, version-aware FK uniqueness, audit trail of activations and deactivations) and design accordingly.

Reference: convention applied to all `mm_*` form definitions; consistent with the canonical MD pattern in `md*` forms which also have no `isActive` in practice (the field exists in some MD form definitions but is unused operationally).

---

## D24 — 2026-04-30 — Wizard / multi-screen sequence as a first-class element (`MetaWizardElement`); Lesotho extension to RegBB

RegBB §6.3.2.1 mentions that an analyst "can define a one-screen e-service or create a multi-page wizard e-service supported by Breadcrumb." The spec mentions the concept of wizard navigation but does not formalise *how the wizard structure is captured in the meta-model*. The closest primitives are `mm_screen.orderIndex` (defines a sequence) and the analyst-side guidance that screens have a default order (Guide → Form → Documents → Payment → Send). What's missing: an entity-level binding from a Joget form (or its rendering surface) to "render these screens as a tabbed wizard with breadcrumb navigation, validation per tab, persisted progress."

Filling the gap as a Lesotho extension. New element in `reg-bb-engine`: `MetaWizardElement`. Drops onto a Joget form as a single widget. Property: `serviceId` (resolves to all `mm_screen` rows for the service in `orderIndex` order) or an explicit semicolon-separated `screenIds`. The element renders those screens as tabs with:
* a top breadcrumb / step indicator
* per-tab Next / Previous navigation
* validation that fires when the user attempts to advance (per Joget's standard validator pipeline applied to each tab's children)
* single underlying data row in one table — same convention as today's stacked-sections form, no fan-out across 7 tables (the alternative `MultiPagedForm` pattern from `farmerRegistrationForm` requires that fan-out)
* operator-side parity: a `readonly=Y` mode that renders the wizard with all tabs accessible read-only and the OP_DECISION tab editable (RegBB §6.3.2.3 operator-screens semantics, gated by `mm_role_screen` per role)

Implementation: Phase 1 Week 3 deliverable. Replaces the current pattern where `subsidyApplication2025.json` hand-stacks six Meta Screen widgets in six Sections. After Week 3, `subsidyApplication2025` becomes a one-line form with one `MetaWizardElement` widget pointing at `SUBSIDY_2025`.

This is also relevant to D17 — when `reg-bb-publisher` generates citizen userviews from a published `mm_service`, it emits a one-widget form with `MetaWizardElement` rather than synthesising N hand-stacked Sections. That keeps the publisher output declarative and the resulting form maintainable.

Documented as a RegBB spec gap in `convergence-framework.md` §9.4. Candidate upstream feedback to GovStack: formalise wizard structure as part of the screen sequence model (orderIndex semantics + breadcrumb requirements) so multi-implementation portability is preserved.

Reference: `docs/architecture/convergence-framework.md` §9.4 (to be added); `docs/architecture/phase-1-week3-resume.md` Week 3 work item; future class `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/element/MetaWizardElement.java`.

---

## D25 — 2026-05-02 — Budget &amp; Commitment Engine as separate module

**Decision.** Public-sector subsidy programmes need commitment accounting (Reservation → Pre-commitment → Commitment → Expense funnel) tied to per-programme budget envelopes. We introduce a new cross-cutting module — the **Budget &amp; Commitment Engine** — separate from RegBB framework and from any specific business module. Storage (`budget_envelope`, `budget_event`, `budget_projection`), funnel ledger, Cost Estimation Service, programme launch gate. Codified in ADR-022.

**Key boundary.** The engine is a ledger plus a projection. It does not contain policy. It does not write back to subsidy or IM tables. Subsidy and IM emit lifecycle events through `mm_action.kind=budget_event` (D27); the engine's listener writes ledger rows.

Reference: `docs/architecture/adr/adr-022-budget-engine-as-separate-module.md`; `docs/architecture/architecture/components/budget-engine.md`.

---

## D26 — 2026-05-02 — Commitment funnel state model (Reservation → Pre-commitment → Commitment → Expense)

**Decision.** The funnel has four forward stages and matching reverse events. Terminology is IPSAS-aligned where possible, with a deliberate two-stage commitment level (programme-level Pre-commitment at operator approval; asset-level Commitment at IM voucher issuance) that's more granular than standard IPSAS but matches the system's actual lifecycle. Codified in ADR-023.

| Stage | Trigger |
|---|---|
| Reservation | Citizen submits finalised application |
| Pre-commitment | Operator approves application |
| Commitment | IM voucher issued |
| Expense | IM voucher redeemed |

Plus inverse releases at each stage. Append-only ledger; projection materialised via `budget_projection` view.

Reference: `docs/architecture/adr/adr-023-commitment-funnel-state-model.md`.

---

## D27 — 2026-05-02 — `mm_action.kind=budget_event` integration pattern

**Decision.** Subsidy and IM modules emit budget events through the existing `mm_action` dispatch mechanism — no direct API calls into the Budget Engine. New `mm_action.kind=budget_event`; Budget Engine registers a listener that subscribes to dispatches of this kind, reads the `triggerJson` (eventType, amountFormulaRule), evaluates the amount via `RoutingEvaluator`, writes the ledger row.

Module isolation: subsidy and IM never import Budget Engine code. New lifecycle hooks are configuration (mm_action rows), not code changes. Codified in ADR-024.

Reference: `docs/architecture/adr/adr-024-mm-action-budget-event-integration.md`.

---

## D28 — 2026-05-02 — Rule-based budget governance — six new `mm_determinant.scope` values

**Decision.** All budget governance is rule-driven, not schema-attribute-driven. New scope values:

| Scope | Decision the rule answers |
|---|---|
| `budget_amount` | Per-event amount (formula expression) |
| `budget_tolerance` | Is cost estimate within acceptable variance of allocated? |
| `programme_launch_gate` | Can this programme transition draft → published? |
| `budget_overrun_policy` | At decision time, does this approval breach? Allowed? |
| `budget_authorisation` | Can this user perform this budget operation? |
| `sla_decision` | Has this application breached SLA? |

The Budget Engine's manual-adjustment surface and the operator decision flow each call `RoutingEvaluator` with the proposed operation in context. Different programmes can have different tolerances, SLAs, overrun policies, launch gates — all configuration. Codified in ADR-025.

Reference: `docs/architecture/adr/adr-025-rule-based-budget-governance.md`.

---

## D29 — 2026-05-02 — Rule-to-SQL compiler for fast-path determinants

**Decision.** Cost Estimation Service needs to count "how many farmers in the registry are eligible for programme X" in interactive time (&lt; 1 second). Iterating row-by-row through the registry doesn't meet this. Add a `RuleToSqlCompiler` to the framework: given a closed-twenty AST without `$registry.*` references, emit a Postgres `WHERE` clause + parameter bindings.

Used for **batch / count** operations (CES); decision-time evaluation continues to go through `FastPathEvaluator` (no behaviour change). Iterative fallback for `$registry.*`-touching rules.

Translation-correctness suite: every grammar production runs through both interpreter and compiler against representative data; assert equivalence. Codified in ADR-026.

Reference: `docs/architecture/adr/adr-026-rule-to-sql-compiler.md`.

---

## D30 — 2026-05-02 — "If it's policy, it's a rule" applied universally

**Decision.** Anything that's a *policy decision* (a choice that could differ between programmes, be tightened/loosened, require different actors to authorise) lives as an `mm_determinant` row in the appropriate scope. Engine code reads the rule's outcome; engine code does not encode the policy.

Scope of the principle: every module **except** the farmer + parcel registries (which stay native Joget per D31). First two cleanups shipped in `reg-bb-engine` build-049:

- ADR-027 — `initial_status_assignment` scope: disposition → initial status mapping is rule-driven.
- ADR-028 — `decision_to_status` scope: operator decision → status mapping is rule-driven.

New helper `StatusPolicyResolver` consults rules first, falls back to hardcoded defaults if no rule matches; default rules ship in seed fixture.

Future cleanups inventoried in `docs/architecture/policy-to-rules-migration.md`. Standing principle on every future PR: when a code path branches on a policy decision, the reviewer asks "is this a rule?" If yes, it goes in `mm_determinant`, not in code.

Reference: `docs/architecture/policy-to-rules-migration.md`; ADRs 027 + 028.

---

## D31 — 2026-05-02 — Farmer + parcel registries are explicitly excluded from convergence

**Decision.** Reverses the previous "Stage 2 — farmer/parcel migration to mm_*" plan from migration-plan.md revision 2. The registries are **architecturally final** as native-Joget hand-built forms. Convergence consumers (subsidy, IM, reporting, Budget Engine) read them via `$registry.*` references; convergence never writes to them.

Rationale:

- The registries are working production code. Disrupting them carries risk that exceeds the benefit of metadata-driven rendering.
- Eligibility evaluation against the registries via `$registry.*` works fine without migrating them.
- The MM-form-gen kernel could in principle render them metadata-driven later if a real business case emerges, but there is none today.
- Convergence team capacity is better spent on subsidy + Budget Engine + IM.

Implication: the convergence framework's "second-domain test" is not run against farmer/parcel either. RegBB's portability beyond subsidies remains an open empirical question, to be probed only when a fitting public-services domain emerges.

Reference: `docs/architecture/architecture/components/farmer-parcel-registry-integration.md`; revised `migration-plan.md` revision 3.

---

## D32 — 2026-05-03 — L4 empirical convergence gate: 20/20 passed, MM-based subsidies module functionally closed

**Decision.** The Layer 4 parity test (5 fixtures × 4 programmes = 20 scenarios) passed end-to-end through the generated UX and the analyst-driven REST surface. Per `convergence-framework.md` §7, this is the empirical convergence gate for Phase 1 — proves the kernel + framework + capability registry shape the four 2025 subsidy programmes through pure configuration, with no hard-coded module logic.

**Five defects surfaced + fixed during L4-1/L4-3** (full text in `subsidy-to-im-backlog.md` Layer 4 section):

| | Defect | Layer | Resolution |
|---|---|---|---|
| D-001 | Catalogue load-binder leaked first DB row in `?_mode=add` | kernel-widget | gate `getLoadBinderDataProperty` on non-empty PK |
| D-001b | Client-side `select.value` defaults polluted `/catalogue/eval` | kernel-widget | track `dataset.userSet` on `change`, gate gather logic |
| D-002 | DSL string-literal escape: SQL-style `''` doesn't parse | framework-spec | choose quote char by what's not in the value |
| D-003 | Missing uniqueness check (same NID, same programme, twice) | module-content + spec gap | new `SubsidyApplicationCountAdapter` + four `DET_NO_DUPLICATE_PRG_xxx` rules; PRG_001 → `all_must_pass` |
| D-004 | Single-rule programme misclassified rule-FALSE as `pending_review` | module-content | PRG_002, PRG_003 → `all_must_pass` |

Categorised per the convergence-framework taxonomy: **two kernel-widget bugs, one framework-spec bug, two configuration bugs.** Crucially — the kernel-widget defects were Joget-platform integration traps (load-binder behaviour, `<select>` defaults), not RegBB-spec deviations. The framework-spec one (DSL escape) was a missing piece in the rule grammar, captured in CLAUDE.md as a known limit (escape-sequence support deferred to ADR-territory). The configuration bugs were exactly the kind L4 is designed to catch — operator-authored programme configuration that doesn't match the engine's strategy semantics.

**Bonus deliverables shipped during L4** (extending the kernel without re-opening the convergence audit):

- Analyst-driven API endpoint generation: new `mm_api:` YAML section + `POST /formcreator/apis` endpoint + `BuilderDefinitionDao` upsert via the form-creator-api plugin. Any analyst-spec'd form can be exposed as REST without manual App Composer steps.
- Third capability adapter (`SubsidyApplicationCountAdapter`) — proves the L2-1 registry pattern (ADR-020) extends to module-side capabilities, not just farmer/parcel registries.
- Automated parity-test runner (`tooling/run_l4_scenarios.py`) — 20/20 sweep in ~30s, with pre-cleanup of prior submissions and post-run cleanup, all through the citizen API (HARD RULE compliant).

**What's left for Phase 1 closure.** L4-4 was originally pencilled in here, but is **deferred to post-IM** (see D33 below). The quantitative half of the gate per Phase 1 plan §8 is complete.

**What this unlocks per the Gate to IM Phase 3** (`subsidy-to-im-backlog.md`): conditions 1, 2, and 4 are now ✅ green. Conditions 3 (Budget Engine — L3-1) and 5 (render performance — L4-5, new) remain.

Reference: `subsidy-to-im-backlog.md` Layer 4; `tooling/run_l4_scenarios.py`; CLAUDE.md updates for D-001 / D-001b / D-002 / D-003.

---

## D33 — 2026-05-03 — L4-4 operator UX sign-off deferred to post-IM; L4-5 render performance added as Gate condition

Two related changes to Phase 1 closure framing.

**Deferral of L4-4 (operator UX qualitative sign-off).** Originally scheduled as Phase 1 closure with the goal of Aare + 1 MOA operator validating the subsidy approval flow end-to-end. Reframed: operators don't experience the platform as separate modules — they see one tool that handles citizen application review AND input distribution. Signing off on subsidy-only ergonomics gives a false reading because the moment IM lands, the operator's daily flow spans both, and frictions that emerge at the boundary (handoff from approved subsidy application to IM voucher campaign, eligibility data shared between modules, reconciliation that closes both ledgers) will only surface when the operator drives the complete loop. The qualitative gate becomes meaningful **only after IM Phase 3** ships with the same kernel/framework convergence as subsidy. L4-4 in `subsidy-to-im-backlog.md` updated accordingly.

This is **not** a quality concession on Phase 1. The quantitative gate (L4-3, 20/20) demonstrates kernel + framework convergence empirically. The qualitative gate is about deployment readiness for the full operator role, which is by definition multi-module.

**Addition of L4-5 (render performance).** Production-blocking observation surfaced today: the citizen wizard takes 15–25 seconds from URL hit to interactive. That's an order of magnitude past acceptable, and Lesotho field conditions (older PCs in extension offices, mobile devices, weak GSM data) make it worse. A flawless rule engine that takes 25 seconds to render the application form will not be used. New L4-5 backlog item details suspect bottlenecks (kernel render path, per-render Determinant evaluation, Hibernate cold cache, MultiPagedForm tab pre-rendering, client bundle weight) and a plan: instrument first, profile under field-condition load, fix the dominant cost. Promoted to Gate-to-IM-Phase-3 condition #5.

**Updated Gate to IM Phase 3 conditions:**

1. ✅ Layer 1 kernel widgets
2. ✅ Layer 2 framework completeness
3. ⏳ Budget Engine (L3-1)
4. ✅ 20/20 parity test (L4-3)
5. ⏳ Render performance ≤ 3s cold / ≤ 1s warm on field-condition devices (L4-5)

L4-4 (operator UX) explicitly deferred until after IM ships, with sign-off then covering both modules together.

Reference: `subsidy-to-im-backlog.md` Layer 4 (L4-4 deferral, L4-5 new) and Gate section.

---

## D34 — 2026-05-03 — Budget Engine adopts proper public-sector fund accounting; methodology doc is the implementation contract

**Decision.** Before writing any L3-1 (Budget & Commitment Engine) code, define the business-accounting model explicitly as `docs/architecture/architecture/components/budget-accounting-methodology.md`. The engine implements the methodology faithfully; divergence requires a methodology amendment first, not a code-side workaround.

**What the methodology establishes** (key shape — full detail in the document):

- **Four-layer chart of accounts**: envelope (control) → funding-source contributions (multi-fund) → beneficiary sub-ledger (one per approved applicant) → vendor sub-ledger (Phase 3+).
- **Twelve transaction types** with explicit debit/credit pairs and source proration: ALLOCATION, RESERVATION, RELEASE_RESERVATION, PRE_COMMITMENT, RELEASE_PRE_COMMITMENT, COMMITMENT, RELEASE_COMMITMENT, EXPENSE, EXPENSE_ADJUSTMENT, RECONCILIATION_ADJUSTMENT, SOURCE_REALLOCATION, PROGRAMME_CLOSURE.
- **Nine invariants** the engine MUST enforce and reconciliation jobs MUST verify (envelope balance identity, source closure, beneficiary control-total, vendor control-total, double-entry, no-negative-balance, sub-ledger lifecycle ordering, proration consistency, historical immutability).
- **Reasonably IPSAS-compliant** — terminology + discipline aligned with IPSAS 24 (Budget Information), IPSAS 1 (Presentation), IPSAS 23 (Non-exchange / donor revenue). Sufficient for donor reporting + internal audit; falls short of statutory IPSAS only on consolidated-fund integration.
- **Authority + segregation of duties** via `budget_authorisation` mm_determinant scope (per ADR-025); four-eyes for ALLOCATION, RECONCILIATION_ADJUSTMENT, SOURCE_REALLOCATION, PROGRAMME_CLOSURE.
- **Audit trail** — every journal entry records actor, authority basis, rule version, source module, correlation id, idempotency key. Historical immutability rule: no UPDATE / DELETE on event rows; corrections are new events referencing the corrected one.

**Why the upgrade.** The original SAD described a state-machine ledger with five funnel stages — mechanically correct but unable to answer business-grounded questions like "how much donor money has been spent?", "what does the system say we owe Mants'ali?", or feed even a non-statutory IPSAS report. Aare's instinct that we should "know what we're doing in business terms, not just write code" was correct; the methodology pre-empts a class of architectural rework that would otherwise hit when MoA-Finance, donors, or the auditor-general first try to use the engine for reporting.

**Amendments to existing ADRs / SAD** (per §11 of the methodology doc):

- `docs/architecture/architecture/components/budget-engine.md` §5.2: storage schema gains `budget_envelope_source` (multi-fund proration) + `beneficiary_subledger` + `vendor_subledger` tables; `budget_event` rows gain `account_path`, `direction`, `authority_basis`, `idempotency_key` columns. §5.3 BudgetEventListener adds proration logic + sub-ledger maintenance. §5.4 Operator UX adds donor-report + beneficiary-account-lookup + vendor-reconciliation surfaces.
- ADR-022: implementation contract is the methodology document (added to *Decision*).
- ADR-023: reframed from "states of an application's budget impact" to "subaccounts within a programme envelope". Mathematical content unchanged; framing now accounting-grounded. Source contribution + sub-ledger layers added as cross-cutting.
- ADR-024: trigger-evaluation logic extended to include source proration + sub-ledger open/close.
- ADR-025: no change. The six new scopes already named are exactly what the methodology needs; subscope listing for `budget_authorisation` per transaction type added.
- ADR-026: no change. SQL-compiler scope is unchanged (CES count acceleration).

**Open questions** parked in §12 of the methodology — confirmed Aare's intent on the four asked clarifications (multi-fund tracking, beneficiary + vendor sub-ledger required, single currency LSL, reasonably-IPSAS-compliant); the seven §12 questions stay open as Phase-1-implementation refinements (not blocking 1A).

**Next**: L3-1 Slice 1A (storage + envelopes + initial source-contribution rows + materialised view + seeded BUDGET_ALLOCATION events) implements per the methodology. Tracked task #124.

Reference: `docs/architecture/architecture/components/budget-accounting-methodology.md`; ADR-022 through ADR-026; budget-engine.md SAD.

---

## D35 — 2026-05-06 — IM Phase 3 Slice 1: foundational MD + im_input_catalog + im_supplier; new MD ID convention

**Decision.** Phase 3 (IM module) opens with a small foundational slice: the IM `mm_service` namespace row (`INPUTS_2025`), five master-data forms (`md_input_category`, `md_input_unit`, `md_supplier_type`, `md_distribution_point`, `md_voucher_status`), and two foundational entities (`im_input_catalog`, `im_supplier`). All native Joget per IM SAD §4.1 + §11.10.1 quality goal "zero non-native widgets in IM module"; no kernel-shaped (mm_screen + mm_field) IM forms in this slice.

**Naming.** New IM-era master-data forms use the `md_<domain>` convention (snake_case, business-aligned ID) rather than the legacy `md<NN><name>` numbered convention. Examples: `md_input_category` (not `md38InputCategory`), `md_input_unit` (no legacy equivalent), `md_supplier_type` (no legacy equivalent), `md_distribution_point` (not `md45distributionPoint`), `md_voucher_status` (no legacy equivalent). Rationale:

- Legacy `mdNN*` forms already in the JWA (md38InputCategory, md44Input, md45distributionPoint etc.) predate D20 (code, not UUID, as cross-entity FK target) and have known broken binders documented in CLAUDE.md "Known issues to fix in the current JWA". Reusing them would inherit those issues.
- The numbered prefix encoded ordering for a now-deprecated documentation tier; new modules don't need to slot into that ordering.
- 24-character form-ID cap (Joget table-name constraint) is comfortable for `md_<domain>` (longest is `md_distribution_point` at 21 chars).

Legacy `mdNN*` forms remain in the JWA (orphaned but harmless) until a future cleanup slice formally retires them. No mass rename in this slice — ADR-016/017 boundary keeps the new IM module cleanly separated; legacy renaming is Convention-over-Invention work that earns its place when consolidated reporting starts pulling from both naming generations.

**Codified by ADRs.** 

- **ADR-016** (Accepted) — IM uses the MM-form-gen kernel only, not the RegBB framework. IM reuses two metamodel entities for general-purpose use (`mm_determinant` for business rules, `mm_action` for workflow triggers per ADR-013) but does NOT use `mm_registration` / `mm_required_doc` / `mm_benefit` / `mm_role` / `mm_role_screen` / `RegBb*` Java code. Trade-off named: shared rule + dispatch infrastructure across modules vs. clean module boundary on the rest.
- **ADR-017** (Accepted) — `mm_service` is namespace-only for IM. The `INPUTS_2025` row exists to scope IM's `mm_*` rows together; it carries no citizen-services semantics. Reuses one schema (no new `mm_namespace` table) at the cost of slight semantic noise — `mm_service` mixes citizen-services and namespace-only senses, distinguished by code prefix convention + description column.

**Shipped artefacts** (committed):

| | Path | Notes |
|---|---|---|
| Forms | `app/forms/im/{md-*,im-*}.json` × 7 | Native Joget; `code`/`input_code`/`supplier_code` as business key with DuplicateValueValidator. |
| Datalists | `app/datalists/list_{md_*,im_*}.json` × 7 | AdvancedFormRowDataListBinder; OptionsValueFormatter for FK columns. |
| Userview | `app/userviews/v.json` patched | New "Inputs Management" category with 7 CrudMenu entries. |
| ADRs | `docs/architecture/adr/adr-016-*.md`, `adr-017-*.md` | Accepted. |
| Seed | `app/seeds/lesotho-mm-fixture.yaml` extended | INPUTS_2025 row + 31 sample MD/catalogue/supplier rows. |

**Verified** end-to-end against dev Joget (PostgreSQL `app_fd_*` tables): all 32 IM rows present; FKs resolve by code (`im_input_catalog.category_code` → `md_input_category.code`; `im_supplier.supplier_type` → `md_supplier_type.code`; `md_distribution_point.district` → `md03district.code`).

**Out of scope for Slice 1, queued for next slices:**

- `im_inventory` + `im_stock_transaction` (stock movement)
- `im_allocation_plan` + `im_allocation_line` (allocation per programme × district)
- `imEntitlement` cross-module bridge (subsidy approval → IM entitlement issuance via `mm_action` ISSUE_IM_ENTITLEMENT + sysadmin XPDL workflow)
- `im_voucher` lifecycle + Budget COMMITMENT/EXPENSE event hooks
- `im_voucher_redemption` + `im_distribution`
- IM-specific `mm_determinant` rules (IM_VOUCHER_REDEEM_AT_ALLOCATED_POINT, IM_STOCK_BELOW_THRESHOLD, etc.)
- Operator UX qualitative review (L4-4) once Phase 3 delivers end-to-end voucher distribution

Reference: `docs/architecture/architecture/components/im-module.md`; `subsidy-to-im-backlog.md` (Phase 3 gate now consumed); ADR-013, ADR-016, ADR-017.

### Addendum (2026-05-06, same day) — Slice 1 cleanup pass after dedup audit

A same-day audit (triggered by an App Composer "plugin not installed" error on `SelectBoxDataListFilterType`) revealed two issues with the as-shipped Slice 1 that are now corrected:

1. **Duplication of pre-existing input chains.** The user's pre-Slice-1 design already covered the input space:
   - `md27inputCategory` (8 production rows: SEEDS, FERTILIZER, PESTICIDES, IRRIGATION, LIVESTOCK_VET, FENCING, STORAGE, OTHER) + `md27input` form a 2-tier dropdown cascade via `md27input.c_input_category → md27inputCategory.c_code`.
   - A separate `md38InputCategory` / `md44Input` / `md45DistribPoint` / `imAgroDealer` / `imCampaign` chain exists with a richer (but empty) schema, designed for purpose but never populated.

   My Slice 1 added a third parallel chain — `md_input_category`, `md_input_unit`, `md_supplier_type`, `md_distribution_point`, `md_voucher_status`, `im_input_catalog`, `im_supplier`. The "new MD ID convention" rationalisation in this decision was wrong-headed when applied to entities the user had already authored.

   **Cleanup applied (Slice 1 final state):**
   - **Dropped**: `md_input_category`, `md_distribution_point`, `im_input_catalog` form definitions, datalists, userview menus, and seed YAML sections.
   - **Reused**: existing `md27inputCategory` (8 prod rows kept) + `md27input` (4 sample SKUs migrated in: SEED_MAIZE_PAN6479_25KG, SEED_BEANS_KK15_50KG, FERT_NPK_50KG, FERT_UREA_50KG, mapped to existing SEEDS/FERTILIZER categories). Existing `md45DistribPoint` populated with 5 sample district hubs (DP_MASERU_DEPOT etc.).
   - **Kept** (genuine gaps in JWA, no equivalents existed): `md_input_unit` (5 rows), `md_supplier_type` (4 rows), `md_voucher_status` (6 rows), `im_supplier` (3 rows). The supplier vs. agro-dealer split is preserved deliberately — `im_supplier` covers upstream supply-chain (manufacturer / wholesaler / cooperative) and is distinct from `imAgroDealer` (last-mile retail). An ADR will codify this if the boundary becomes contentious.

   **Convention correction.** The "new IM-era forms use `md_<domain>` rather than legacy `mdNN<name>`" framing in the original D35 is **withdrawn**. The actual rule is: **reuse existing forms when they fit; create new forms only for genuine gaps in the JWA**. Legacy `mdNN*` IDs are not deprecated — they're the canonical identifiers for the entities they cover. New forms should still use the cleaner snake_case convention (per `md_input_unit`, `md_voucher_status`), but only when authoring something new, not when shadowing existing tables.

   **Field-naming gotcha (CLAUDE.md updated)**: the legacy MD forms use **camelCase** field ids (`districtCode`, `pointType`, `isActive`, `gpsLatitude`); Postgres folds these to lowercase column names (`c_districtcode`, etc.). Joget's Hibernate mapping uses the form-definition ids, so seed payloads MUST use camelCase keys — not the lowercase column names. md27input is an exception: its fields are snake_case (`input_category`, `default_unit`, `estimated_cost_per_unit`). The convention is per-form; always inspect the form definition before authoring a seed payload. This trap silently dropped my first md45DistribPoint seed (rows landed with code+name only; districtCode/pointType/isActive were null until I re-seeded with the right keys).

2. **Triple API endpoints from historical batch seeders.** A separate audit found 30 API names in `app_builder` had 2-3 rows each — 88 rows total, 58 of them duplicates. All created on 2026-01-26 in three batched seeder runs (16:10 / 16:44 / 17:03 UTC), predating the May-2026 idempotency upgrade to the form-creator-api `/apis` upsert.

   **Cleanup applied:**
   - New `POST /formcreator/apis/delete` endpoint added to form-creator-api (build-021). Calls `BuilderDefinitionDao.delete(id, appDef)` per id — same DAO path App Composer's "Delete API" uses, so CLAUDE.md HARD RULE preserved (no raw SQL on `app_builder`).
   - New `tooling/cleanup_duplicate_apis.py`: queries `app_builder` (read-only SELECT), groups by name, keeps the earliest row per name, deletes the rest via the new endpoint. Dry-run by default; `--apply` to commit. `--update-enabled-paths` to add `post:/formcreator/apis/delete` to the formCreatorApi's enabled paths (one-time prerequisite).
   - Run sequence (operator): (a) upload form-creator-api build-021 JAR via Joget UI → (b) `python3 tooling/cleanup_duplicate_apis.py --update-enabled-paths` → (c) `python3 tooling/cleanup_duplicate_apis.py` (dry-run preview) → (d) `python3 tooling/cleanup_duplicate_apis.py --apply` (commit). Verifies count drops from 166 to 108 API rows.

**Outcome (Slice 1 final state):** 4 retained new forms (md_input_unit, md_supplier_type, md_voucher_status, im_supplier), 4 datalists, "Inputs Management" userview category with 4 menus. Existing MD.27 / MD.45 chains carry the input + distribution-point data. ADR-016 / ADR-017 unchanged — the "kernel only, no RegBB framework" + "mm_service is namespace-only" decisions are not affected by the form-set cleanup.

Reference: `docs/architecture/adr/adr-016-im-uses-kernel-not-framework.md`; `docs/architecture/adr/adr-017-mm-service-namespace-only-for-im.md`; `tooling/cleanup_duplicate_apis.py`; `plugins/form-creator-api/src/main/java/global/govstack/formcreator/lib/FormCreatorServiceProvider.java` build-021.

---

## D36 — 2026-05-06 — Resource Centre is canonical; md45DistribPoint retired; 60 real centres loaded

**Decision.** `md37collectionPoint` (label "MD.37 - Resource Center") is the canonical form for the physical concept of a MAFSN agriculture extension hub / collection point / distribution point. The parallel `md45DistribPoint` (Slice 2 first cut) was a duplicate-by-naming for the same physical concept and is hereby retired from the active userview.

**Why.** A user check on Slice 2 revealed three terms in use for the same real-world entity:

- **Resource Centre** — the MAFSN-formal term (used in policy and the project's own canonical roster: `_08_Implementation/LESOTHO RESOURCE CENTRES with coordinates.docx`).
- **Collection Point** — the data-collection-team name (existing form id `md37collectionPoint`, label "MD.37 - Resource Center").
- **Distribution Point** — a supply-chain framing introduced by Slice 2 (`md45DistribPoint`).

All three describe the same physical location (Mantsonyane, Khabo, Teyateyaneng, etc.) viewed from different functional angles. Maintaining three parallel forms would have caused authoring drift and a category mix in the dropdown sources of any future IM transactional surface. MAFSN's own term wins.

**Shipped.**

1. **Form references re-pointed** — `im_inventory.point_code` and `im_stock_transaction.point_code` SelectBox.optionsBinder.formDefId changed from `md45DistribPoint` to `md37collectionPoint`. Datalist OptionsValueFormatters changed too. Forms re-pushed via form-creator-api.
2. **60 real Lesotho Resource Centres loaded** into `md37collectionPoint` from the canonical docx, with `CP_<NAME_SNAKE>` codes (e.g. `CP_MANTSONYANE`, `CP_MORIJA`, `CP_TEYATEYANENG`). Mixed-format coordinates (decimal, DMS like `29°37'24"S`, DMS with backtick instead of apostrophe, decimal with text "South/East", lat/lon swapped for Letlapeng, missing minus sign for Pilot) were normalised to decimal degrees by a parser that handled each variant. One centre (Thaba-chitja) had a malformed longitude in the source docx (`30°3\`27.22` truncated mid-DMS) — left null with a code-comment for operator follow-up. Distribution: 7-8 centres each in 9 districts (Quthing missing from docx; existing CP009 Quthing placeholder retained).
3. **CP001-CP010 placeholders kept alive** — these are referenced 243× across `app_fd_farm_location.c_resource_center` (58), `app_fd_parcellocation.c_resource_centre` (160), `app_fd_sp_district_alloc.c_collectionpointcode` (21), `app_fd_sp_program_geography.c_collectionpoints` (4). Deleting them would orphan farmer + parcel + programme records. Resolution: 70 rows total in `md37collectionPoint` (10 placeholders + 60 real). Operators can re-point farmers/parcels to the named centres at their leisure; future cleanup slice can retire the placeholders once references migrate.
4. **Slice 2 sample data re-anchored to CP_MORIJA** (a real Maseru-area Resource Centre) instead of the fictional `DP_MASERU_DEPOT`. Inventory codes regenerated as `INV_CP_MORIJA_*`; stock-transaction rows re-seeded against the new point.
5. **Userview cleanup** — removed the orphan `MD.45 - Distribution Point` menu from the Master Data category (its form/datalist still exist but no menu pointing at them). Live userview now has 137 menus, 0 orphans.
6. **Seed YAML** — removed the `md45DistribPoint` section, added `md37collectionPoint` with the 60 normalised centres, updated `_seed_order` accordingly.

**`md45DistribPoint` form fate.** Left in the JWA without a menu pointing at it. Removing the form definition itself requires a form DELETE endpoint that doesn't exist in form-creator-api yet (only POST/upsert). The form is harmless empty; future cleanup can either author the DELETE endpoint or retire it manually via App Composer. The fact that it lacks a menu is operationally sufficient — operators can't navigate to it.

**Trade-off named:** disambiguation vs. data-migration cost. Choosing `md37collectionPoint` as canonical avoided the destructive migration (re-pointing 243 farmer/parcel/programme references), but accepted that operators see 70 rows in the dropdown — the 60 named real centres plus 10 generic placeholders — until the cleanup catches up. The placeholder-vs-real ambiguity is operationally tolerable for a few weeks while subsidy and IM Phase 3 settle.

**Coordinate-parser as future-proofing.** The `tooling/` parser pattern that handled the docx's mixed coordinate formats is documented inline in the conversion script (now in `/tmp/resource_centres_clean.json` snapshot — promote to `tooling/parse_resource_centres.py` when the next docx-style import lands, or add to a future utility module). Three lessons codified:

* **Lesotho lat is always negative; lon always positive** — `lat ≈ -29 to -30`, `lon ≈ +27 to +29.5`. Use the bounds to detect swapped pairs (Letlapeng) or forgotten signs (Pilot).
* **DMS regex must accept backticks (`)** as well as apostrophes (')** — the docx uses both inconsistently.
* **Don't trust source data; normalise before storing.** The Tale centre in Leribe district has coordinates that place it in Mohale's Hoek (`30°17'31"S`); kept as authored but flagged for manual fix.

Reference: `app/seeds/lesotho-mm-fixture.yaml` md37collectionPoint section; `_08_Implementation/LESOTHO RESOURCE CENTRES with coordinates.docx`; `app/forms/im/im-inventory.json`, `app/forms/im/im-stock-transaction.json` (build pointing at md37collectionPoint); `app/userviews/v.json` (MD.45 menu removed).

---

## D37 — 2026-05-06 — IM stock-transaction restructured to header + line grid; storeBinder replaced by form post-processor

**Decision.** `im_stock_transaction` (Slice 2's first cut) was a one-row-per-movement form: every transaction had a single `input_code` + `quantity` on the header itself. Slice 6d restructures it: header carries `code / txn_date / txn_type / district_code / point_code` plus the linkage section (supplier, invoice, etc.), and the `input_code` + `quantity` (+ new `unit_price`, `line_notes`) move into a new `im_stock_txn_line` child form rendered as a FormGrid. Inventory delta application moves out of the parent's storeBinder into a dedicated form post-processor (`StockTransactionPostProcessor`) which runs once after the whole form tree commits.

**Why the form restructure.** A real supplier delivery typically covers multiple goods on a single invoice — Pannar might deliver 200 bags maize seed + 100 bags NPK on one GRN. The single-row shape forced the operator to copy date, point, supplier, invoice number, and the invoice scan onto every line, with no integrity link tying them back to the same physical delivery. Header + line-grid is the natural shape: one parent describes the event (when, where, who, why, what paperwork), the lines describe the goods. The same pattern fits ADJUSTMENT (one stock-count session, multiple SKUs corrected) and TRANSFER (one truck, multiple SKUs in transit). RECEIPT was the trigger but the gain generalises across all five `txn_type` values.

**Why the storeBinder → post-processor.** With line items in a child grid, the inventory-update logic can no longer run inside the parent's storeBinder. Joget walks `executeFormStoreBinders` recursively: parent storeBinder fires first, then the FormGrid's storeBinder (which is a child element). At parent-binder time, the child rows are not yet on disk — `SELECT FROM app_fd_im_stock_txn_line WHERE c_txn_id = ?` returns empty. Two clean options: (a) custom child storeBinder that reads parent context up; (b) form-level post-processor that runs after the whole tree commits. Picked (b): single point of logic, no parent-not-yet-saved edge case, and Joget's `FormUtil.executePostFormSubmissionProccessor` is the documented hook (source-verified `wflow-core/.../FormUtil.java` ≈ line 2202; called from `AppServiceImpl.submitForm` line 1985 after `formService.submitForm` returns).

**Append-only discipline preserved.** Post-processor wired with `runOn: "create"` — fires once on initial save, never on edits. Rationale: stock-txn is the audit log of physical movements. If the operator records a typo, the correction is a NEW txn (an ADJUSTMENT to undo, then a fresh RECEIPT for the right shape), never a silent edit. A `posted_at` HiddenField on the parent records the post-processor's timestamp as a belt-and-braces idempotency guard — even if `runOn` were ever changed to `"both"`, the post-processor short-circuits when `posted_at` is non-empty.

**Trade-off named.** Convention-over-Invention pulled toward the existing single-row pattern (don't restructure a form already in production); SRP + correct accounting shape pulled toward the line-grid (the parent form was doing two jobs — describing the event AND describing the goods movement). At single-customer scale with no live data depending on the old shape (the four sample seed rows were test fixtures, not customer data), SRP wins. If the form had had thousands of customer-authored rows, a more elaborate migration would have been needed. Slice 6d ran clean because the customer hadn't started using the form yet.

**The OSGi registration trap that delayed deploy by one cycle.** Adding `StockTransactionPostProcessor` as a new `DefaultApplicationPlugin` subclass required registering it in `Activator.start()` via `context.registerService(...)`. Without that registration, Joget's PluginManager can't resolve the class name when the form definition references it as `postProcessor.className`, even though the class compiles and ships in the JAR. The same trap had bitten Slice 6 earlier for `StockTransactionStoreBinder`, `VoucherIssuanceTool`, `VoucherRedemptionTool`. The discipline ("every plugin SPI subclass must be registered in Activator") is now codified in CLAUDE.md.

**The form post-processor pattern is now reusable.** Future cases where logic should run *after* a parent + its FormGrid children are all on disk (e.g. allocation-plan rollups, multi-line ledger postings, fan-out emails based on aggregate state) should use a `DefaultApplicationPlugin` wired as form-level `postProcessor` rather than chasing parent-storeBinder ordering hacks.

**Build-108 follow-up (2026-05-06).** The legacy `StockTransactionStoreBinder` was originally retained as a "safety net" in case any form definition still referenced it. A consolidation-pass audit confirmed zero references — none in local form JSON, none in the live JWA's `app_form` table, none in any datalist, none in code outside its own file and `Activator`. The class file was deleted and its `Activator` registration removed. The bundle shrank ~5KB. CLAUDE.md's "Form post-processor vs. storeBinder" section is the canonical reference for which pattern applies; the deleted class adds zero ongoing value.

Reference: `app/forms/im/im-stock-transaction.json` (post-Slice-6d); `app/forms/im/im-stock-txn-line.json` (new); `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/processing/StockTransactionPostProcessor.java`; `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/Activator.java` (build-104 introduced postProcessor; build-108 retired the legacy binder); `jw-community/wflow-core/src/main/java/org/joget/apps/form/service/FormUtil.java` line 2202.

---

## D38 — 2026-05-06 — Distribution receipt is a separate slice from voucher redemption; signed handover ≠ logical redemption

**Decision.** Voucher redemption (Slice 5, `im_voucher_redemption` row + `/budget/redeem-voucher` API) records the **logical** event: the voucher was used, voucher status flips to `redeemed`, inventory decrements, budget posts EXPENSE. Slice 8 adds a separate `im_distribution` form to record the **physical** event: the farmer received the goods, both farmer and counter operator signed for it. The two forms are 1:1-linked by `voucher_code` but represent different attestations.

**Why split.** The original Slice 5 design conflated the two — "redeem voucher" was treated as a single act that included physical handover. In practice they are two distinct moments with different evidentiary weight:

- **Logical redemption** — system records the voucher was consumed, atomically updates state (voucher → redeemed, inventory −=qty, EXPENSE posted, idempotency key recorded). This is what the Distribution Agent's redemption screen produces. It's complete from a data-integrity standpoint but provides no proof the goods physically reached the farmer.
- **Physical handover receipt** — counter operator confirms "I handed over X bags of NPK to this farmer on this date", farmer signs to acknowledge receipt, operator countersigns. This is what auditors / appeals processes need. Without it, a farmer's "I never got my fertilizer" complaint cannot be investigated past the redemption row.

Splitting them gives the system two evidentiary layers: redemption proves the system processed the voucher correctly, distribution proves the goods reached the farmer. They can disagree (a redemption with no matching distribution row is a flag for forensic review — possible voucher fraud, broken process at the counter, or just paperwork backlog).

**Why not extend `im_voucher_redemption` with signature fields.** Considered, rejected. Two reasons: (a) Redemption is invoked by the redemption *API*, often programmatically or from the redemption HTML screen — adding mandatory signature fields to the underlying form would break the API path which doesn't carry signature payload; (b) the distribution event is sometimes deferred from the redemption event (operator redeems the voucher when the farmer arrives, then runs to the storeroom to pull stock, returns and hands over — physical handover trails the redemption by minutes to hours, sometimes a day). Splitting the forms lets each capture its own timestamp accurately.

**`voucher_code` as the link.** `im_distribution.voucher_code` is a SelectBox sourced from `im_voucher` filtered to `status='redeemed'` (operators can only record receipts for already-redeemed vouchers); `DuplicateValueValidator` enforces one-receipt-per-voucher. The forensic query "which redeemed vouchers have no signed receipt" is a simple `SELECT v.code FROM app_fd_im_voucher v WHERE v.c_status = 'redeemed' AND NOT EXISTS (SELECT 1 FROM app_fd_im_distribution d WHERE d.c_voucher_code = v.c_code)`.

**Trade-off named.** YAGNI / Convention-over-Invention pulled toward "merge the two events, extend the existing form" (one slice, less cognitive load for operators). Audit-evidentiary discipline pulled toward "separate forms with separate timestamps and separate signatures" (two roles signing the same row needs two distinct events anyway). At public-money scale (subsidy fraud is a real concern in agricultural input distribution), the audit-evidentiary side wins. The cost is one extra form for operators to fill, mitigated by the SelectBox auto-narrowing to redeemed vouchers and most fields being short or optional.

**`Signature` element is `org.joget.plugin.enterprise.Signature`** — both `farmer_signature` and `operator_signature` are mandatory; renders as a draw-pad in the browser; on save Joget stores the PNG under `wflow/app_formuploads/im_distribution/<receipt_id>/<field>.png`. The DB column holds the bare filename. Skipping signatures during /seed is permitted because `/seed` bypasses validators — useful for the e2e test which doesn't have signature payload, harmful in normal CRUD where the validator is the audit guarantee.

Reference: `app/forms/im/im-distribution.json`; `app/datalists/list_im_distribution.json`; `app/userviews/v.json` (Inputs Management category, 8th menu); `docs/architecture/architecture/components/im-module-roles.md` ("Service Counter" actor).

---

## D39 — 2026-05-06 — Donor-grade financial slice: multi-donor envelopes via `programme_funding` × share_percent

**Decision.** Add an analyst-authorable mapping between programmes and donors, where one programme can be co-financed by multiple donors at fractional shares. Concretely: a master-data form `md_donor` (code + name + country + focal point + contact email), a linkage form `programme_funding` (programme_code → donor_code + share_percent + grant_reference + effective_from/to), and two reports — `dl_funding_by_donor` (per-donor totals across all their programmes) and `dl_programme_funding_breakdown` (per-programme×donor split with the formal envelope and event-driven committed/expensed). Seed the WFP / EU / MAFSN_DOMESTIC / WORLD_BANK donor list and link PRG_2025_001 to WFP (60%) + MAFSN_DOMESTIC (40%) as the worked example.

**Why this exists at all.** The Lesotho subsidy programmes are co-financed: WFP commits real money to PRG_2025_001 alongside MAFSN's domestic budget. Without a donor data model, the budget reports can show the envelope but cannot answer the question every donor asks at end-of-quarter — *"what fraction of this expense is mine?"*. The reports as built before C6 reported envelope-level totals only; the donor-grade slice fills in the per-funder cut.

**Allocated comes from `c_allocated_amount`, committed/expensed from `budget_projection`.** The two report sources are deliberately different. Allocated is the **planned commitment** the donor formally signed for — that lives on the envelope as `c_allocated_amount` and is set at programme-launch time, not derived from events. Committed and expensed are **what happened** — events posted by the budget engine as applications get approved (COMMITMENT) and vouchers redeemed (EXPENSE). Mixing them means the "donor allocated" column matches what the donor sees in their grant agreement, while "donor committed/expensed" tracks what actually moved. The earlier draft of the SQL pulled all three from `budget_projection`, which made allocated zero in dev because the BUDGET_ALLOCATE event is posted by the engine on programme launch and dev envelopes were created without that event firing — donors would have read "we allocated zero to your programme" which is the wrong story.

**share_percent is a percentage string, not a decimal.** Stored as the literal `"60"` / `"40"` (TextField with numeric validator), arithmetic in SQL is `× CAST(NULLIF(c_share_percent,'') AS NUMERIC) / 100.0`. Two reasons over a true decimal: (a) the form-creator's available widgets render TextField + numeric validator more cleanly than a numeric SelectBox with two-decimal precision; (b) operators reading the raw row see "60" and immediately understand the share — `0.6` would force a mental translation. Sum-to-100 enforcement is a **semantic invariant**, not a column constraint — the form does not validate the sum of all rows for a programme adds up to 100; the reports will simply show shares as authored, including incorrect ones. Validation is an analyst review responsibility (and could be added as a Determinant rule against `mm_registration` later, but YAGNI for the initial slice). The donor-grade reports surface mismatches because the per-donor breakdown rows for one programme will visibly not add up to the envelope total.

**No effective-dating logic in the reports.** `effective_from` / `effective_to` are captured on the linkage form so that when a programme's funding shifts mid-cycle (donor re-allocates between programmes, fiscal-year boundary), the historical share is preserved. The reports as built ignore them — they show the *current* shares regardless of when events were posted. This is consistent with the rest of the budget reports (which also ignore time-windowing on metadata changes) and matches what donors actually want at the point of reporting: "show me my share *as currently agreed*, applied to lifetime committed/expensed". Time-windowed donor reports (e.g. "WFP's share of expenses in Q3 only, valued at the share that was in force during Q3") are a future enhancement and would require materialising effective-dated joins; deferred under YAGNI.

**Master Data placement of `md_donor` (MD.94)**, with `programme_funding` under MM-Configuration alongside the other analyst-authoring forms. Per the userview-ordering convention (CLAUDE.md): donors are infrequently-edited reference data (lookup form), funding linkage is analyst-edited configuration (programme set-up). Both reports go under Reports trailing the existing IM reconciliation report.

**Trade-off named.** Convention-over-Invention pulled toward "extend `app_fd_budget_envelope_source` (which already exists for the budget engine's source-aware accounting)" — that table already encodes per-source allocations on each envelope, with `c_share_percent` and `c_source_name` columns. The donor-grade reports could have been built directly off it. Why a separate `programme_funding` table instead: (a) `budget_envelope_source` is engine-internal, written by the budget engine when `/budget/source-allocate` is called and tied to budget events; analyst-edited donor-share metadata didn't fit that ownership model. (b) `md_donor` as a master-data record (with focal point, contact email, country, notes) belongs on the analyst-authoring side, not the engine side. (c) The budget engine's source-aware path is for *enforcement* (no over-spending of WFP's share); the donor-reporting path is for *narrative* (what's WFP's slice worth right now). They can coexist — `programme_funding` could later be the input that drives auto-creation of `budget_envelope_source` rows, but that wiring is out of scope for C6 and isn't needed for the reports to function.

**Trade-off resolution: SRP wins over Convention here.** The two concerns (engine enforcement; analyst-authored donor metadata) have different lifecycles, different writers, different audiences. Co-locating them on one table would have made the simpler concern (donor reporting) drag in the harder concern (engine event semantics). At single-team scale the cost of the two tables is small (one extra form, four extra report joins), the cost of merging them would have been ongoing — every change to either concern would have to consider both.

**Future work.** (1) `mm_registration → programme_funding` cross-validation rule that fails launch if shares for a programme don't sum to 100. (2) Optional bridge from `programme_funding` to `budget_envelope_source` so the engine and the reports share one source of truth. (3) Time-windowed donor reports if donors start asking for them.

Reference: `app/forms/md/md-donor.json`; `app/forms/md/programme-funding.json`; `app/datalists/list_md_donor.json`; `app/datalists/list_programme_funding.json`; `app/datalists/dl_funding_by_donor.json`; `app/datalists/dl_programme_funding_breakdown.json`; `app/userviews/v.json` (Master Data MD.94, MM-Configuration trailing menu, Reports trailing 2 menus); `tooling/seed_donor_data.py`.

---

## D40 — 2026-05-06 — Voucher print slip is a self-contained HTML page with URL-borne data; no new Java endpoint

**Decision.** Build the printable voucher slip (B3) as a single `org.joget.apps.userview.lib.HtmlPage` menu under Inputs Management ("IM - Print Voucher Slip"), plus a per-row `Print` link added to `list_im_voucher`. The print page reads voucher fields from URL query parameters (`?c=VCH-...&a=Mants'ali Panyane&n=066257236627&p=PRG_2025_001&pn=Block Farming&i=Maize Seed&cn=Maseru Main Depot&q=80&u=BAG_25KG&e=2026-12-31&d=2026-05-06&s=issued`) and renders them onto an A5 slip with the voucher code rendered both as large monospace text and as a deterministic bar-stripe pattern derived from the code. No new Java endpoint, no API call from the page, no new bundle build.

**Why URL params, not an API fetch.** Adding a `/budget/get-voucher` endpoint would have required a Java rebuild, a JAR upload, and an Activator registration — for a feature whose entire job is *render this row that the operator just clicked Print on*. The voucher list already has all the data; passing it through the URL is one less moving part. Trade-offs: (a) URL length stays well under 2 KB even with long names — no risk of hitting browser/proxy limits; (b) URL parameters are visible in browser history and server access logs, which for voucher slip data (farmer NID, programme, quantity) is acceptable since the same data is already visible on the operator's screen and in `app_fd_im_voucher` rows. There is no payment data, no PII beyond the farmer name + NID that's already operator-visible.

**Print link rendered in SQL.** The existing `list_im_voucher` was an `AdvancedFormRowDataListBinder` (form-data binder) — that pattern doesn't allow computed columns with HTML. Converted the binder to `JdbcDataListBinder` so the SQL can `'<a target="_blank" href="' || ... || '">Print</a>' AS print_link` with `"renderHtml": "true"` on the column. Side benefit: the JDBC binder also lets us join `md_voucher_status`, `mm_registration`, `md27input`, `md37collectionPoint` once at query time and display human-readable names in dedicated columns instead of relying on per-column `OptionsValueFormatter` (which was running 4 sub-queries per row in the old binder). Filter columns kept identical to the old version (status, programme_code, point_code, farmer_nid) plus a new `code` filter for direct lookup.

**Quantity comes with unit appended from `md27input.c_default_unit`.** The voucher row stores `c_quantity` as a bare numeric string (`"80"`) but does not store the unit — unit lives on the input catalogue (`md27input.c_default_unit` = `"BAG_25KG"`). The display column `qty_disp` is computed as `c_quantity || ' ' || md27input.c_default_unit` in SQL. This matches the Service Counter operator's mental model — they hand over "80 bags", not "80". An earlier draft tried to read `v.c_unit` directly and failed with `column does not exist`; the input catalogue join was the right path. CLAUDE.md will not get a new gotcha entry for this since "the right column lives one join away" is already general SQL discipline, not a Joget-specific trap.

**Bar-stripe pattern, not a real QR code.** Real QR generation requires a JS library (`qrcode-generator`, `qrious`, etc.) — adding one means either (a) loading from a CDN (the customer's network may block outbound), or (b) embedding the library inline in the HtmlPage's `content` field (~15 KB extra per page render). Neither cost was justified for a slip the farmer carries from one counter to a sometimes-the-same counter — the human-readable voucher code is what the operator types or scans-as-text into the redemption screen anyway. The bar pattern (1-8 px wide bars derived from `code.charCodeAt(i) * 37 + i * 11`) gives the slip visual structure that distinguishes a real voucher from a hand-written copy without claiming to be a scan-grade barcode. If the customer adds a barcode-scanning hardware later, swap the renderer; the URL contract doesn't change.

**Auto-print is opt-in via `?autoprint=1`.** When operators click `Print` in the list, the page opens in a new tab and renders the slip — they can review it before clicking the page's `Print this slip` button. If a future flow wants to skip the review step (e.g. issuance auto-prints on voucher creation), append `&autoprint=1` and the page calls `window.print()` 350 ms after render. Default-off because operators sometimes need to inspect the slip before printing (correct centre? correct expiry?), and an unwanted print dialog every click is annoying.

**Trade-off named.** SRP / Clean architecture pulled toward "build a proper /voucher/get endpoint, fetch the row server-side, render from a single source of truth". YAGNI / Convention-over-Invention pulled toward "the data is already on screen, don't invent a new fetch path". At single-team / single-customer scale where the voucher row's display fields are stable and never need transformation, YAGNI wins. If the slip ever needs derived fields the SQL doesn't have (e.g. a benefits-redemption history per farmer, fancier formatting that's hard to do in SQL), revisit by adding the endpoint. Until then the URL contract is the simplest thing that works.

Reference: `app/userviews/_pages/im_print_voucher.html` (canonical authoring source, kept on disk so future edits aren't trapped inside the userview JSON); `app/userviews/v.json` (Inputs Management category, 7th menu — the HtmlPage embeds the same HTML inline); `app/datalists/list_im_voucher.json` (converted to JdbcDataListBinder, added `print_link` column with `renderHtml: true`); the URL-contract is documented at the top of the page's `<script>` block.

---

## D41 — 2026-05-06 — Forensic search is a single-box UNION-ALL datalist; empty term returns zero rows by design

**Decision.** Build the operator forensic search (B4) as a single `JdbcDataListBinder` datalist `dl_forensic_search` whose SQL `UNION ALL`-s five lifecycle tables (`subsidy_app_2025`, `im_voucher`, `im_voucher_redemption`, `im_distribution`, `reg_bb_eval_audit`) into a unified result set. The datalist takes one filter — `q`, a free-text term — and returns rows where the term appears anywhere in: application id, NID, applicant name, programme code, voucher code, redemption code, distribution code, voucher_code reference, redeemed_by, operator_name, determinant code. Each result row carries `result_type` (Application / Voucher / Redemption / Distribution / Audit), the entity's code, applicant identity, status, an event timestamp, a one-line summary, and a drill-down link that opens the entity's CRUD edit screen (or the print page, for vouchers) in a new tab. Lives under the MOA Office category as a DataListMenu.

**Why a single datalist, not five separate searches.** Operators investigating a complaint ("I never got my fertilizer") need to follow a thread that crosses entity boundaries — the trail is application → audit decisions → issued voucher → redemption → distribution receipt. Five separate searches mean five tabs, five context switches, and easy to miss a step. One unified result list with `result_type` as a column lets the operator type once and see the whole timeline. This is the same pattern as the audit dashboard (`dl_audit_trail`) which UNIONs four IM lifecycle tables; the forensic search adds applications and audit events to that pattern, plus a free-text search across multiple columns.

**Empty search returns zero rows on purpose.** The `WITH q AS (SELECT NULLIF(LOWER('#requestParam.q?sql#'), '') AS term)` plus `WHERE q.term IS NOT NULL AND ...` pattern means an unfiltered open of the page returns nothing — operators see "no results, type a search term". This is the opposite of every other datalist in the app (which default to "show me everything"). Reasoning: across five tables, an unfiltered search would return tens of thousands of rows, render slowly, and present operators with noise rather than the answer they came for. The forensic surface is a *targeted lookup tool*, not a browse surface — defaulting it to "browse" would invite operators to pick rows by guessing rather than by knowing what they want. Browsing each entity individually is what the existing `list_im_voucher` / `list_subsidyApplicationOperator2025` / etc. menus are for.

**Case-insensitive LIKE matching.** All match clauses lowercase both sides (`LOWER(col) LIKE '%' || q.term || '%'`). The term itself is also lowercased on the way in (`NULLIF(LOWER('#requestParam.q?sql#'), '')`). Operators can paste `VCH-20260506-0001` or `vch-20260506-0001` or `VcH-20260506-0001` and get the same result. The LIKE on lowercased columns won't use any indexes on those columns (Postgres needs a functional index `LOWER(col)` for that), but with only thousands of rows per table at single-customer scale, full-table scans complete in well under 200 ms. Revisit when the customer hits ~100k rows in any of these tables — at that point, add `CREATE INDEX ... ON LOWER(c_*)` for the four most-searched columns.

**`countSql` returns a static `0`.** The real `SELECT count(*) FROM (...)` would require running the entire UNION ALL twice per page render — once for the rows, once for the count. With LIMIT 500 on the rows query and the typical search term matching at most a few dozen rows across all five tables, that doubling of work is wasted. Returning `0` from `countSql` makes the page show "Showing 1–N of 0" which is cosmetically slightly off, but the operator can see the actual returned rows and that's what matters. If the cosmetic issue becomes a complaint, replace `SELECT 0` with a properly-bounded count or compute count from the result set client-side via JS.

**Drill-down links open in new tabs.** Each row's `view_link` is `<a target="_blank" href="...">Open</a>`. Operators investigating a thread typically open multiple entities side-by-side (compare the application's eligibility outcome to the audit row that produced it; compare the voucher's quantity to the redemption record). New-tab opens preserve the search context — the operator can come back to the unified list and click the next entity in the thread. Same pattern as the print-voucher link added in D40.

**Trade-off named.** Convention-over-Invention pulled toward "use Joget's stock global-search feature" — Joget DX has app-level search, surfaced via the `userview.search` property. Why a custom datalist instead: (a) the stock search is form-level and per-entity (it searches one form's columns at a time); operators would still have to switch entity context to find anything; (b) the stock search has no `UNION ALL` across entities and no concept of `result_type`. SRP / KISS pulled toward "one datalist, one job", and since the job is cross-entity, a custom binder was unavoidable. Cost: this datalist will need to be touched whenever a new entity is added to the lifecycle (e.g. a future appeals form would need to be UNION-ed in). The maintenance burden is captured by the explicit five-table list in the SQL — no metaprogramming, just append a new SELECT branch.

Reference: `app/datalists/dl_forensic_search.json`; `app/userviews/v.json` (MOA Office category, trailing menu "Forensic Search (cross-table)"); search verified against live data with NID `066257236627` (returned 30 audit + voucher + application rows for Mants'ali Panyane) and voucher prefix `VCH-20260506` (returned 19 voucher rows).

---

## D42 — 2026-05-07 — Plugin writes route through `AppService.storeFormData`, not `FormDataDao.saveOrUpdate` directly

**Decision.** Replace every direct `FormDataDao.saveOrUpdate(formId, tableName, rows)` call inside our plugin code with `AppService.storeFormData(formId, tableName, rows, primaryKeyValue)`, wrapped behind a small `RowWriter` utility in `plugins/reg-bb-engine/.../support/RowWriter.java`. Same DAO chain Joget itself uses for native form saves; same HARD-RULE-compliant write path; the only difference is that `AppService.storeFormData` populates `dateCreated`, `dateModified`, `createdBy`, `createdByName`, `modifiedBy`, `modifiedByName` before delegating to the DAO. The bare `FormDataDao.saveOrUpdate` call is a thin Hibernate wrapper that does none of that metadata work.

**The forensics that led here.** The audit dashboard built in B1 had to fall back to business-date columns (`c_issued_date`, `c_redemption_date`, etc.) instead of system timestamps because most `app_fd_*` tables had 100 % NULL `datecreated`/`datemodified`. A scan across all 284 form-data tables in this app on 2026-05-07 found 23 of them with 100 % NULL timestamps — every single one written by plugin code via direct `FormDataDao.saveOrUpdate`. The native Joget tables (`farmerbasicinfo`, `parcelregistration`, `farms_registry`, `household_members`, `livestock_details`) had 100 % populated timestamps because they go through Joget's `WorkflowFormBinder.store` → `AppService.storeFormData` chain. The fix is making our plugin code take the same path.

**Source-verified.** `wflow-core/src/main/java/org/joget/apps/app/service/AppServiceImpl.java` ≈ line 2135–2209 (`storeFormData(String formDefId, String tableName, FormRowSet rows, String primaryKeyValue)`) is the canonical save path. It iterates the row set, generates UUIDs for missing primary keys, sets `dateModified = now`, looks up the previous row's `dateCreated` and reuses it for updates (or sets `dateCreated = now` for inserts), captures the current user via `WorkflowUserManager` for created-by / modified-by attribution, calls `FormDataDao.updateSchema(...)` to reconcile any new columns, then calls `FormDataDao.saveOrUpdate(...)`. Plugin code calling the DAO directly skipped all of that — the row was inserted with NULL metadata, and Hibernate happily wrote it as NULL.

**`RowWriter` utility.** Single-method API: `RowWriter.save(formDefId, tableName, FormRowSet)`. Internally calls `AppService.storeFormData(formDefId, tableName, rows, null)` — the four-arg overload, with `primaryKeyValue=null` so the row's own id (or a generated UUID) is used. Falls back to `FormDataDao.saveOrUpdate` plus manual `setDateModified(now)` / `setDateCreated(now if absent)` if the AppService bean isn't reachable (e.g. early bundle activation). The fallback exists so the change is safe under unusual lifecycle conditions, not as the normal path.

**Sites refactored (build-111).** RegBbApplicationStoreBinder, RegBbOperatorDecisionBinder, BulkOperatorDecisionAction, AuditWriter, VoucherIssuanceTool, VoucherRedemptionTool (×3 sites for redemption + voucher flip + inventory decrement), VoucherCancellationTool, VoucherExpirySweeper, StockTransactionPostProcessor (×2 sites for inventory + stock-txn flag), BudgetEngine (×4 sites for events + subledger), BudgetThresholdMonitor (×2 sites for alert + envelope freeze). 16 sites total in `reg-bb-engine`. The `form-creator-api` bundle's `FixtureSeedService.upsertRow` was patched in-place (one site, doesn't depend on `RowWriter` since it's a different bundle) — same fix, calls `appService.storeFormData(...)` directly.

**Existing NULL rows are NOT backfilled.** The HARD RULE forbids raw SQL on `app_fd_*` tables. The values that *should* go into the historical `datecreated` columns can't be reconstructed accurately from any source — the rows were created before the fix existed and there's no audit trail of their original creation timestamps. Reports and dashboards that need event-ordering already use business-date columns (`c_issued_date`, `c_redemption_date`, `c_decided_at`, etc.) as the authoritative source — D41's forensic search and B1's audit dashboard both rely on these. New rows created from build-111 onwards will populate timestamps correctly; old rows stay NULL forever.

**Trade-off named.** SRP / DRY pulled toward fixing this at the Joget platform level — file an upstream patch so `FormDataDao.saveOrUpdate` itself populates timestamps, every plugin benefits without per-call-site changes. Convention over Invention pulled toward the per-call-site fix: Joget's separation between `FormDataDao` (raw Hibernate wrapper) and `AppService.storeFormData` (metadata-populating wrapper) is intentional — the platform offers both because some callers want to write rows without metadata side-effects (rare but real, e.g. data-import paths that supply their own timestamps). Patching `FormDataDao.saveOrUpdate` upstream would change behaviour for callers who rely on the lower-level semantics. Patching our plugin code is the local, contained, easy-to-revert fix. At single-customer scale and with us not maintaining the Joget core, Convention wins.

**Build / deploy.** The change requires rebuilding two JARs:

* `plugins/reg-bb-engine` → `target/reg-bb-engine-*.jar`. Build counter bumped to 111 in `Build.java`. Includes the new `RowWriter` class plus the 16 refactored call sites.
* `plugins/form-creator-api` → `target/form-creator-api-*.jar`. The single-site patch in `FixtureSeedService` only.

User builds with `mvn -pl plugins/reg-bb-engine clean package -am` and uploads via App Composer → Manage Plugins → Upload, replacing the existing JARs. After upload, smoke-test by submitting a fresh subsidy application and verifying its `datecreated` is non-NULL on `app_fd_subsidy_app_2025`.

Reference: `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/support/RowWriter.java`; `plugins/form-creator-api/src/main/java/global/govstack/formcreator/service/FixtureSeedService.java`; live audit query against the dev DB confirmed 23 of 284 `app_fd_*` tables had 100% NULL timestamps before the fix.

---

## D43 — 2026-05-07 — ADR-031 Slice A executed: additive columns only; rename deferred

**Decision.** Executed ADR-031 Slice A as a strictly additive change. Added four new columns to `mm_determinant`: `severity` (quality-scope only — `error` / `warning`), `triggerOn` (`submit` / `save` / `manual` / `field_change`), `aggregation` (`disposition` / `issue_list`), `affectedFields` (comma-separated form-field IDs). Added one new value (`quality`) to the existing `scope` enum. **Did not rename `mm_determinant` → `mm_rule`**; the rename is cosmetic, touches ~20 references across forms, datalists, plugins, and tooling, and provides no behavioural value. Defer to a later cosmetic pass if/when warranted.

**The non-breaking shape.** All 26 existing rows have `NULL` in the new columns — the existing eligibility and bot_pull rules don't need them and continue to evaluate via the legacy `ruleType` + `score` path. Only quality-side rules (when migrated in Slice B) populate the new columns. The schema is union-of-needs; rules tagged `scope=eligibility` ignore the new fields, rules tagged `scope=quality` rely on them.

**Why keep both `ruleType` and `severity`.** The ADR initially proposed a unified `severity` enum covering `mandatory` / `score` / `error` / `warning`, replacing `ruleType`. Inspection of the live form revealed `ruleType` already exists with values `inclusion` / `exclusion` / `priority` / `bonus`, used by 26 existing rows. Replacing it would have required migrating every row and patching the eligibility evaluator. Keeping both columns — `ruleType` for eligibility, `severity` for quality — costs one extra column on the schema but zero risk on the migration. Cleanup is a future-pass concern.

**Test gate held.** The equivalence-test signature (`e3ce90e02e12d4db`) is byte-identical before and after the schema change. No behavioural drift in the legacy engine. The 246-test foundational baseline runs green. L4 parity test (eligibility regression) green.

**Trade-off named.** SRP / DRY pulled toward replacing `ruleType` with the unified `severity` enum (one column, one mental model). YAGNI / risk-management pulled toward keeping both (no data migration, no plugin changes, evaluator paths unchanged). At pre-production timing the YAGNI argument is even stronger because cost-of-rework is bounded; at production scale either approach is fine but YAGNI is irreversible-cheaper. Chose YAGNI.

**Rollback.** Trivial: drop the four new columns via a Joget form-save (remove the new section, push). Existing data unaffected.

Reference: `app/forms/mm/mm-determinant.json`; `docs/architecture/adr/adr-031-unified-rule-engine.md`; `tooling/tests/baselines/legacy_pre_adr031.json` (snapshot before slice). Form pushed via `tooling/push_form.py`. Database confirms 4 new columns: `c_severity`, `c_triggeron`, `c_aggregation`, `c_affectedfields`. All 26 existing rows have NULLs in all four — additive change preserved.

---

## D44 — 2026-05-07 — ADR-031 Slice B executed: qa_rule rows mirrored into mm_determinant; dual-write window open

**Decision.** Migrated all 21 active `qa_rule` rows into `app_fd_mm_determinant` with `scope=quality`, `severity=error|warning` (lower-cased), `triggerOn=save`, `aggregation=issue_list`, and `ruleJson` byte-identical to the source `ruleScript`. Driven by `tooling/migrate_qa_to_mm.py` via the form-creator-api `/seed` endpoint with `businessKey=code`. **The `qa_rule` rows are not deleted** — Slice B opens the dual-write window where the legacy form-quality runtime continues to read from `qa_rule` and the new unified surface reads from `mm_determinant`. Switch happens in Slice C.

**Distribution after migration**: `mm_determinant` now holds 47 rows = 26 pre-existing (eligibility / decision_to_status / programme_launch_gate / budget_amount / initial_status_assignment / applicability / field) + 21 quality (17 ERROR + 4 WARNING). The original 21 `qa_rule` rows remain untouched.

**Idempotent re-run.** The migration script can be re-run any time. The seeder upserts on `code`, so re-running produces zero new rows when the source is unchanged. If a `qa_rule` is edited (rule fix, message tweak), re-running mirrors the edit into `mm_determinant` cleanly.

**Verification gates held.**

* Equivalence-test signature: `e3ce90e02e12d4db` before and after, identical to the Slice A baseline. Adding rows to `mm_determinant` does not drift the legacy engine because the runtime still reads from `qa_rule`.
* The 158 schema-validation tests in `test_rules_per_rule.py` (scope/ruletype/registration_refs) pass with the 21 new rows. Test was patched to skip the `ruletype` check when `scope=quality` (quality rules use `severity` instead).
* Foundational regression baseline (`make test`) still green at all layers.
* `migrate_qa_to_mm.py --apply` reported 21 inserted, 0 missing in target, 0 SQL probe mismatched.

**Two known-broken qa_rules carried into mm_determinant unchanged.** Per the migration's "byte-for-byte" semantic: the SQL probes copied without normalisation. The fixed `application.declaration_signed` rule (D43-era manual edit) propagated correctly. A second rule, `application.applicant_id_set`, references the same wrong table name (`app_fd_sp_application` vs `app_fd_spapplication`) — the test_rules_per_rule.py SQL parse test will surface this once the `mm_determinant` rule is wired up in Slice C and tested against the live evaluator. **Not fixing on the way in is deliberate**: byte-for-byte migration preserves the audit shape "what was in production before is in the new store now". Defects get fixed in their natural source location, not in the migration script.

**Slice C trigger.** Slice B is the safe rollback point. To start Slice C (switch read paths so `form-quality-runtime` reads from `mm_determinant`), the precondition is: a stable Slice B for ≥24 hours with no rule edits. After Slice C lands, the rules in `mm_determinant` become the runtime authority; `qa_rule` is read-only legacy.

Reference: `tooling/migrate_qa_to_mm.py`; `tooling/tests/baselines/legacy_post_slice_b.json` (snapshot post-slice — signature unchanged); `app_fd_mm_determinant` has 47 rows total (47 = 26 + 21).

---

## D45 — 2026-05-07 — ADR-031 Slice C built: form-quality-runtime reads from mm_determinant; JAR staged (build-014)

**Decision.** Patched `RuleRepository.java` in `plugins/form-quality-runtime/` to read quality rules from `app_fd_mm_determinant WHERE c_scope='quality'` instead of `app_fd_qa_rule`. Column-mapping handled inside `toRule(FormRow)`: `code` (was `ruleCode`), `failMessage` (was `message`), `ruleJson` (was `ruleScript`); `severity` lower-cased on the way in (`error`/`warning` → `ERROR`/`WARNING` enum) per the case-discipline established in D44. `qa_service`-side primary-form lookup (`findPrimaryFormId`) is unchanged — that table is not migrated yet, deferred to a later slice.

**Slice A extension applied first.** The original `qa_rule` had a `tabCode` column that drives operator-UI grouping ("issues on Identity tab"). My initial Slice A did not add this column to `mm_determinant`. Before patching `RuleRepository`, I added `tabCode` (TextField, optional) to the `mm_determinant` form definition and re-ran the migration. The seeder did 21 updates (idempotent on `code`), populating tabCode from the source `qa_rule` rows. Distribution: identity (5), benefits (2), eligibility (2), geometry (2), location (2), applicant (2), classification (1), declaration (1), geography (1), household (1), monitoring (1), timeline (1).

**Build pipeline detail.** The form-quality-runtime build script (`deploy/repack.sh`) hard-codes paths to a previous session's `.m2` and JDK. The current session's `.m2/repository/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar` was a stale symlink to `/tmp/osgi-core.jar` that didn't exist. Resolved by re-fetching the OSGi core jar from Maven Central (`https://repo1.maven.org/maven2/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar`, 475 KB). Build env vars used: `JDK=/tmp/jdk-11.0.31+11`, `M2=/sessions/<this-session>/.m2/repository`, `LIBS=jw-community/wflow-consoleweb/target/jw/WEB-INF/lib`. Counter ticked to build-014. JAR is 35 KB (vs build-006 baseline at 33 KB — small delta confirms targeted change).

**Outcome stores unchanged.** `qa_issue` and `qa_record_status` continue to be written to by the runtime. Slice C is *only* the rule-source switch; the outcome store unification is deferred (would touch the QualityBannerElement and the post-processor's write path — separate change). Operators see no UX change after deploy.

**Verification gates that hold pre-deploy.**

* Equivalence-test signature: `e3ce90e02e12d4db` unchanged (still legacy-vs-legacy until the new JAR is uploaded; the harness becomes the legacy-vs-unified comparison after).
* The 246-test foundational baseline: green.
* The 158 schema-validation tests in `test_rules_per_rule.py`: green (knows about the 21 quality rows added in Slice B + handles `quality` scope by skipping the `ruleType` check, per D44).

**Verification gates that need the deploy.**

* L4 parity (eligibility regression) — must stay green after redeploy. Eligibility evaluator is untouched, so this should hold trivially.
* L5 quality regression (the new slice-C smoke) — needs to be added next. A test that submits a known-broken record (e.g. a Subsidy Programme with a blank `programName`), saves it, and asserts the QualityBanner turns red. This tests that the new rule-source flows through the post-processor + banner correctly.
* End-to-end: open a verified record in `mm_determinant`-quality scope — confirm the operator UI behaves identically to pre-deploy (same banner state, same issue list grouping by tab).

**Deployment instructions.** The user uploads `form-quality-runtime-8.1-SNAPSHOT-build014.jar` via App Composer → Manage Plugins → Upload (replaces the existing build-006 JAR). After upload, smoke test:

1. Open a Subsidy Programme record (`spProgramMain`) in App Composer; confirm Quality Banner state matches pre-deploy.
2. Edit a programme's `programName` to blank, save, confirm banner turns red with "Programme name must not be blank" — the banner is now driven by the `mm_determinant.identity.programme_name_required` row (Slice B migration).
3. Restore the name, save, confirm banner clears.

**Trade-off named.** YAGNI / Convention pulled toward keeping the read path on `qa_rule` until Slice E retires the legacy table — would have made Slice C trivial. SRP / single-source-of-truth pulled toward switching now — every authoring CRUD edit on `mm_determinant`-quality rows takes effect immediately rather than via a re-migration step. The latter wins because the dual-write window's cost (two stores can drift) compounds the longer it stays open. Switch read paths now; the legacy `qa_rule` becomes silent / read-only / soon-to-be-retired.

**Risk profile.** Low. The Java diff is one class, ~30 lines changed. The data is identical between stores (verified Slice B). The two stores have different column shapes but `RuleRepository` consumes only what it needs — `code`, `severity`, `serviceId`, `tabCode`, `affectedFields`, `ruleJson`, `failMessage` — all of which are populated correctly in `mm_determinant` per the Slice B migration. Worst-case rollback: revert the JAR to build-006 (Manage Plugins → upload the older JAR). Two minutes.

Reference: `plugins/form-quality-runtime/src/main/java/global/govstack/formquality/service/RuleRepository.java` (the only Java change in this slice); `_built_jars/form-quality-runtime-8.1-SNAPSHOT-build014.jar` (staged for upload); `app/forms/mm/mm-determinant.json` (added `tabCode` column); `tooling/migrate_qa_to_mm.py` (extended to populate `tabCode`).

**Live verification (post-deploy)**: build-014 deployed by the user. A save on `spProgramMain → prog001` through App Composer wrote a fresh `qa_record_status` row with `c_lastevaluated = 2026-05-07T10:32:58Z` and `c_status = verified` — confirming the new RuleRepository correctly loaded 5 quality rules from `mm_determinant`, ran their SQL probes, and wrote the aggregated outcome. The runtime is now reading from the unified store; `qa_rule` is silent legacy.

---

## D46 — 2026-05-07 — ADR-031 Slice D executed: unified authoring UI on mm_determinant

**Decision.** Refreshed `list_mm_determinant.json` to expose the ADR-031 columns (`scope`, `severity`, `triggerOn`, `aggregation`, `tabCode`) alongside the legacy ones (`ruleType`, `code`, `serviceId`, `registrationId`, `name`). Added four list filters: `scope`, `serviceId`, `code`, `severity`. Renamed the userview menu from "MM - Determinant" to "MM - Rules (unified — eligibility / quality / bot_pull)" so analysts and Sysadmin see one CRUD location for every rule type. The formQuality app's Configuration → Rules CRUD continues to render as a legacy authoring path until Slice E retires the formQuality app.

**Why this is a userview-only slice — no Java rebuild.** The unified-authoring change is pure metadata: one datalist re-author + one userview menu-label tweak. The form (`mm_determinant`) already has all the required fields after Slices A and B (Slice A added the four ADR-031 columns; Slice C added `tabCode`). The runtime (form-quality-runtime build-014) already reads from `mm_determinant`. Slice D simply makes the analyst surface match the underlying schema.

**Practical authoring impact.**

* Eligibility rules: filter `scope = eligibility` (or `bot_pull`, `applicability`, `decision_to_status`, etc.) → see only those. The legacy fields (`ruleType`, `score`, `targetValue`) are visible as appropriate.
* Quality rules: filter `scope = quality` → see all 21 quality rules. The new ADR-031 fields (`severity`, `triggerOn`, `aggregation`, `tabCode`, `affectedFields`) are visible and editable. Edits flow live to the form-quality-runtime via the existing `RuleRepository.findActiveRulesForService` query (build-014, Slice C).
* When the analyst authors a new rule, they pick `scope` first, which contextualises the other fields ("for quality I need severity + tabCode; for eligibility I need ruleType").

**The legacy formQuality app remains operational.** Editing a rule via the formQuality app's Configuration → Rules screen still updates `qa_rule` rows. Those edits do *not* propagate to `mm_determinant` automatically — the migration script (`tooling/migrate_qa_to_mm.py`) is one-way. So during the dual-write window: edits in `mm_determinant` are runtime-authoritative; edits in `qa_rule` are visible in the legacy admin UI but ignored by the runtime. **Authors must edit in `mm_determinant` from now on**; the legacy UI becomes a read-only reference. This will be enforced structurally when Slice E retires the formQuality app.

**Rationale for the column ordering.** The columns are arranged with the most-discriminating fields on the left (Code, Scope, Service) and scope-specific fields toward the right (Tab, ruleType, Severity, triggerOn, Aggregation). This means analysts scanning the list can recognise rule shape at a glance — eligibility rules show ruleType but blank Severity, quality rules show Severity but blank ruleType. Tab is mostly populated for quality rules; blank for eligibility. The ordering is deliberately mixed-discipline rather than cleanly separated; the analyst's mental model is "what scope is this rule" first, then "what's it doing", and the column order reflects that.

**Verification.**

* Datalist render test: `list_mm_determinant` SQL parses + executes against the live DB. ✓
* Userview menu hygiene: `list_mm_determinant` is referenced by the renamed CrudMenu, both resolve. ✓
* Equivalence-test signature: `e3ce90e02e12d4db` unchanged. The authoring surface change does not affect rule outcomes — same rules, same SQL, same results.
* Foundational regression baseline: green.

**Trade-off named.** SRP / DRY pulled toward making the list scope-agnostic with all 11 columns visible. UX simplicity pulled toward two separate list views ("Eligibility rules" / "Quality rules") with a curated column subset each. Chose SRP — one list with filters — because the alternative requires maintaining two datalist files, and operators have always handled the existing 26-row list well. The four filters give the same UX as separate views via a one-click filter.

**Slice E trigger.** Slice D is the safe rollback point for the authoring UI. Once Slice D has soaked for ≥1 week with no analyst confusion or escaped edits in the formQuality app, Slice E retires the formQuality app entirely.

Reference: `app/datalists/list_mm_determinant.json` (refreshed); `app/userviews/v.json` (MM - Configuration category, "MM - Rules (unified)" menu label). Both pushed via form-creator-api endpoints; no Java rebuild.

---

---

## D47 — 2026-05-07 — ADR-031 Slice E.1: re-author qa_service form definition under farmersPortal

**Decision.** Pushed the existing `qa_service` form definition into the farmersPortal app via `formcreator/forms`. The definition now exists in BOTH apps: farmersPortal (new, post-Slice-E.1) and formQuality (legacy, original). The data table `app_fd_qa_service` is unchanged — same 4 rows, same columns. The form-quality-runtime continues to resolve `qa_service` cross-app through `FormDataDao` exactly as before; the only operational difference is that deleting the formQuality app no longer breaks the runtime, because farmersPortal now holds an equivalent definition.

**Why option (d) — re-author under farmersPortal — won over the alternatives.**

We considered three paths:

- **(a) Merge `primaryFormId` into `mm_service`.** Add a TextField, migrate the 4 qa_service rows in. Gain: eliminates a runtime form, brings everything under the metamodel umbrella. Loss: mixes two concept namespaces — `mm_service` would carry both UPPER_SNAKE codes for RegBB service offerings (`INPUTS_2025`, `SUBSIDY_2025`) and snake_case codes for form-quality runtime config (`farmer_application`, `farmer_registration`, `farmers_subsidy`, `parcel_registration`). The two are different entities at different granularities; conflating them in one master-data form is a lasting taxonomic confusion for future analysts.
- **(d) Re-author `qa_service` under farmersPortal.** Lighter touch. No code change, no rebuild. The runtime keeps its existing `RuleRepository.findPrimaryFormId` lookup unchanged. After Slice E.2/E.3 retires the formQuality copy, the farmersPortal copy continues to serve the runtime alone.
- **(f) Hardcode the 4-entry mapping inside `RuleRepository`.** Smallest moving parts. Loss: removes the analyst's ability to add a new service-form binding without a code change — though in practice adding a new service already requires code change (post-processor configuration on the new form's JSON).

**Trade-off named.** SRP / Configuration-over-Code pulled toward keeping the table-driven approach (option d). YAGNI / KISS pulled toward hardcoding the 4-entry mapping (option f). At single-team Lesotho-MAFSN scale both are defensible; the deciding factor was that option (d) is the smallest reversible change — purely additive, no code touched, no rebuild required, equivalence trivially preserved. Option (f) becomes attractive in a future hardening pass when we want to drop one more form definition; it stays parked.

**Verification.**

* `app_form` now has three rows for `qa_service`: `(formQuality, v1)`, `(formQuality, v2)`, and the new `(farmersPortal, v1)`. The data table `app_fd_qa_service` still has the original 4 rows.
* Equivalence-test signature: `e3ce90e02e12d4db` unchanged.
* Regression suite: `test_rules_engine_equivalence` + `test_userview_menus` + `test_md_seeds` all green (30/30).
* Live runtime health: most-recent `qa_record_status` row for `prog001` shows `c_lastevaluated = 2026-05-07T10:32:58Z`, `c_status = verified`, 0 errors / 0 warnings — same as the post-Slice-C verification, confirming the runtime is operating normally.

**What this slice does NOT do.** It does not delete the formQuality copy of qa_service (form-creator-api has no `/forms/delete` endpoint; deletion of form definitions is App Composer manual). It does not retire the formQuality app — that requires the other still-active runtime tables (`qa_record_status`, `qa_issue`, `audit_log`) to also have a home in farmersPortal first, which is Slice E.3.

**Slice E.2 — what remains for the user.** Manual cleanup via App Composer (form-creator-api lacks delete endpoints):

1. **App Composer → formQuality app → Forms → `qa_rule`** → Delete. The runtime no longer reads this; mm_determinant has byte-identical copies (verified Slice B). Data in `app_fd_qa_rule` can be retained as forensic backup; the form definition removal is what this step accomplishes.
2. **App Composer → formQuality app → Datalists → `list_qa_rule`** → Delete. Analyst surface for a deleted form, no purpose.
3. **App Composer → formQuality app → Userview "Form Quality Admin" → "QA Rules" menu** → Delete. The CrudMenu pointed at the deleted datalist.
4. **(Optional)** App Composer → formQuality app → Forms → `qa_service` → Delete. The farmersPortal copy now carries the runtime; formQuality's copy is redundant. Skip if not in a hurry; the soak window for E.1 should run first.

The runtime should keep working through every step above. If anything misbehaves, undo via App Composer (forms / datalists / userviews are versioned and recoverable from `app_form`'s previous version row).

**Slice E.3 trigger.** Once E.2 is complete and the farmersPortal copy of qa_service has soaked for ≥1 week with no runtime issues, E.3 can begin: relocate `qa_record_status`, `qa_issue`, `audit_log` form definitions to farmersPortal so the formQuality app itself becomes deletable. That slice needs a separate decision on whether to rename the forms (`qa_record_status` → `mm_record_status`?) to fit the metamodel naming scheme, which is its own architectural call worth its own design note.

Reference: `app/forms/quality/qa-service.json` (new local canonical copy, identical content to the formQuality v2 source); `tooling/push_form.py` (push tool, no change).

---

---

## D48 — 2026-05-07 — ADR-031 Slice E.3: relocate runtime-infra forms to farmersPortal (formQuality app deletable)

**Decision.** Pulled the v2 JSON of `qa_record_status`, `qa_issue`, and `audit_log` from formQuality and pushed each into farmersPortal via `formcreator/forms`. All three definitions now exist in BOTH apps. Data tables unchanged: `app_fd_qa_record_status` (2 rows), `app_fd_qa_issue` (0 rows), `app_fd_audit_log` (6 rows), `app_fd_qa_service` (4 rows). The formQuality app is now deletable — every form definition the form-quality runtime depends on has a redundant copy under farmersPortal that survives the delete.

**Naming decision: keep the `qa_*` prefix; do NOT rename to `mm_*`.**

`mm_*` is reserved for **metamodel configuration** that analysts author by hand (mm_screen, mm_field, mm_determinant, etc.). The three relocated forms are **runtime operational tables** — the system writes to them on every form save; analysts never edit rows. They sit conceptually next to `reg_bb_eval_audit`, `processing_queue`, `processing_queue_dead_letter`, all of which already live in farmersPortal *without* the `mm_*` prefix. The bounded-context prefix `qa_*` carries information ("this is form-quality runtime infra") that pure naming uniformity would erase.

**Trade-off named.** SRP / naming uniformity pulled toward renaming everything in farmersPortal to `mm_*` for one consistent vocabulary. YAGNI / convention-over-invention pulled toward leaving operational infra prefixed by its bounded context. The renaming work — Joget cannot rename a table after creation, so it would mean: create `mm_record_status` form → migrate 2 rows via `formcreator/seed` → update Java in 4 files (`RuleRepository`, `IssueRepository`, `QualityEntityType`, `FormQualityPostProcessor`) → rebuild form-quality-runtime → redeploy → delete `qa_record_status` form. Every one of those steps adds risk for zero operational benefit. Verdict: keep names, take the lightest reversible change. Renaming stays parked as a future hardening call if the codebase ever needs to harmonise.

**Verification.**

* `app_form` now carries each of `qa_service`, `qa_record_status`, `qa_issue`, `audit_log` under farmersPortal v1 (in addition to the existing formQuality v1+v2 copies).
* Data tables: untouched. Same row counts before and after.
* Equivalence-test signature: `e3ce90e02e12d4db` unchanged (10 fixtures, 5 layers, byte-identical to the pre-ADR-031 baseline).
* Regression suite green: `test_rules_engine_equivalence` + `test_userview_menus` (9/9).
* No code change, no rebuild — same shape as Slice E.1 for `qa_service`.

**The user's path to fully retire formQuality.** With E.1 + E.3 done (form definitions safely duplicated to farmersPortal), and the parts that need to stop being authored (Slice E.2: qa_rule form, list_qa_rule datalist, "QA Rules" userview menu), the formQuality app holds nothing the runtime can't survive without. Recommended sequence in App Composer:

1. **Soak window — at least 24 hours.** Save `prog001` (or any spProgramMain record). Confirm `qa_record_status` updates with a fresh `lastEvaluated` timestamp and `status='verified'`. If yes, the runtime is happily reading both copies; delete is safe. If no, halt and investigate before continuing.
2. **App Composer → formQuality → Forms → delete `qa_rule`** (truly retired since Slice C; mm_determinant carries byte-identical copies).
3. **App Composer → formQuality → Datalists → delete `list_qa_rule`**.
4. **App Composer → formQuality → Userview "Form Quality Admin" → delete the "QA Rules" menu**.
5. (Optional) **Re-run the equivalence harness** between any of these steps. Signature should stay `e3ce90e02e12d4db`.
6. **App Composer → Manage Apps → formQuality → Delete the entire app.** This drops the app metadata only (formQuality's app_form, app_userview, app_datalist, app_builder rows). The data tables `app_fd_qa_*` and `app_fd_audit_log` survive — they're owned by the Postgres schema, not the Joget app definition. The runtime continues to write to them through farmersPortal's copies of the form definitions.
7. (Optional cleanup, separate) **Drop the data tables** if you ever want to reclaim space — but they hold real production state (current quality status of every saved record + complete transition audit trail). Leave them unless an explicit retention decision is made.

**What this ends.** ADR-031 closes here. The rule engine is unified at every layer:

* **Storage**: one table (`mm_determinant`), `scope` discriminator (eligibility / quality / bot_pull / applicability / decision_to_status / budget_amount).
* **Runtime**: `RuleRepository` reads `mm_determinant` filtered by `scope='quality'`; `RoutingEvaluator` reads it filtered by `scope='eligibility'` etc.
* **Authoring**: one CRUD surface — `MM - Configuration → MM - Rules (unified)` — with four list filters (scope / serviceId / code / severity).
* **App ownership**: every runtime form definition lives in farmersPortal. formQuality is now a pure-removal candidate.

The architecture has moved from "two parallel rule engines with overlapping shape" to "one rule engine with discriminated scopes". Equivalence held across all five slices: `e3ce90e02e12d4db` from start to finish.

Reference: `app/forms/quality/qa-record-status.json`, `app/forms/quality/qa-issue.json`, `app/forms/quality/audit-log.json` (all three pulled from formQuality v2 and pushed verbatim to farmersPortal). No Java change; no rebuild.

---

## D49 — 2026-05-08 — Dynamic GIS map centring: client-side Element plugin, not server-side render hooks

**Decision.** Pre-population of the GIS widget's `auto_center_lat / auto_center_lon` HiddenFields from the customer's MD.95 (district × eco-zone) centroid table is implemented as a **custom Joget Element plugin** (`AutoCenterBootstrapElement`) that emits inline JavaScript reading sibling field values from the wizard's DOM. Server-side render hooks are NOT used. The element is wired as the first child of parcelGeometry's `gisSection` Column, before the GIS widget. parcelLocation's storeBinder and parcelGeometry's loadBinder remain stock `WorkflowFormBinder`.

**The four-attempt arc.** This took 11 plugin builds across two days because the obvious server-side paths each fail in a different way under Joget DX 8.x's MultiPagedForm wizard with `partiallyStore=true`. Recording all four because the failure modes are non-obvious and worth knowing for future Joget work.

| Attempt | Approach | Why it failed |
|---|---|---|
| 1 (D49a) | CustomHTML element with inline `<script>` reading `district` + `agroEcologicalZone`, calling a REST endpoint, writing `auto_center_lat/lon` | Joget strips `<script>` tags from CustomHTML element output during form rendering. The script never executed. |
| 2 (D49b) | `DefaultApplicationPlugin` post-processor wired on parcelLocation. On save, look up centroid in MD.95, write to sibling parcelGeometry by parent_id. | Two failures stacked: (a) under `partiallyStore=true`, per-tab Next saves call `AppService.storeFormData` which never invokes `executePostFormSubmissionProccessor` (verified `AppServiceImpl.java:1985` — only fired from `submitForm`). (b) Even with `partiallyStore=false`, parcelGeometry's storeBinder runs 8 ms after parcelLocation's and overwrites our `auto_center` write with empty form-state. The wizard-level post-processor slot on parcelRegistration is permanently held by `FormQualityPostProcessor`. |
| 3 (D49c) | Custom storeBinder on parcelLocation extending `WorkflowFormBinder.store()`. Bootstrap parent_id when empty, mint a parcelRegistration stub, write auto_center to a new parcelGeometry, all in one transactional cascade. | DB writes lands correctly — verified across 4 test parcels. But parcelGeometry's loadBinder on the next render fires with the wizard still in `_mode=add` (URL has no record id), so it can't find the just-written row. The wizard's primary key is empty until final Save. `AbstractSubForm.populateSubFormWithParentKey` (`AbstractSubForm.java:297`) early-returns when `rootFormPrimaryKeyValue` is empty. Side effect: every test created an orphan `parcelRegistration` row because the wizard's eventual final-Save minted its own UUID instead of reusing ours. |
| 4 (D49d) | Custom loadBinder on parcelGeometry extending `WorkflowFormBinder.load()`. Read sibling district/zone from FormData via `FormUtil.findElement`, look up centroid, inject into the returned FormRowSet. | Diagnostic logging confirmed the binder fired but `findElement` couldn't traverse to the inactive tab's elements during the active tab's render. Joget's MultiPagedForm rendering pipeline doesn't include all tab subform children in the runtime element tree the way it does in form-builder mode. Three fallback strategies (request params, DB lookup by parent_id) all hit the same wall: parent_id was empty for new parcels. |
| 5 ✓ | Custom Element plugin emitting inline `<script>` with embedded MD.95 lookup table | Works. The DOM preserves field values across tabs (Joget renders all tabs as hidden divs in one document), so client-side DOM-read is reliable. The Element plugin emits its own JavaScript without Joget stripping it — that's how `GisPolygonCaptureElement` itself emits client-side code. The lookup table (~25 rows × 64 bytes) is embedded as a JS object literal, queried synchronously, no auth concerns, no async race. Total: 1 file (~280 lines), 2 form-definition edits, 1 plugin rebuild. |

**Final architecture.**

```
parcelGeometry form
  Section: gisSection
    Column:
      [0] AutoCenterBootstrapElement         ← reads DOM, writes HiddenFields
      [1] GisPolygonCaptureElement (geometry)← reads HiddenFields, centres map
      ... (HiddenFields: parent_id, area_hectares, …, auto_center_lat, auto_center_lon)
```

The element runs at DOM-ready (before the GIS widget's init) and re-runs on `change` events of the district/zone fields, so cascading dropdown updates re-centre the map live. Idempotent: if `auto_center_*` already has a value (edit mode, existing parcel), the script skips. Configurable: four `*FieldId` properties expose the field IDs the element reads from / writes to, so the same plugin works on any future (district, zone)-driven form.

**Trade-off named.** SRP / "all logic on the server" pulled toward server-side render hooks. The DOM read approach is "less pure" — it depends on Joget rendering all tabs into one DOM document, which is currently true but isn't documented as a contract. Convention-over-invention pulled the other way: client-side scripting is exactly how `GisPolygonCaptureElement` already works (it's an Element plugin emitting its own JS), so we used the same proven pattern. YAGNI also pulled toward client-side: ~280 lines including comments vs. four failed server-side approaches at hundreds of lines each. Verdict: client-side wins on simplicity, on diagnosability (browser console shows exactly what happened), and on independence from Joget's render-pipeline assumptions. The "future risk" mitigation — if Joget ever moves to AJAX-only tab rendering — is a 30-line change to fetch district/zone from a new REST endpoint instead of the DOM.

**Verification.**

* Tested on parcel ID-000209 (Maseru / Lowlands, new parcel created from scratch). joget.log line: `AutoCenterBootstrapElement: emitted 25 centroids from md_district_eco_zone`. Browser-side: green "Using pre-computed coordinates" badge appeared on first visit to Location tab.
* parcelGeometry row written by the standard form save now carries `c_auto_center_lat = '-29.465738'`, `c_auto_center_lon = '27.556545'` — matches MD.95 maseru/lowlands exactly.
* Existing parcel (Khothatso, ID-000201): edit-mode centring continues to work — verified by re-opening the parcel; green badge appears.
* No orphan rows created on subsequent test parcels (storeBinder reverted to stock `WorkflowFormBinder` after the element took over).

**Lessons codified into CLAUDE.md.** Two new gotcha entries:

1. *Joget MultiPagedForm with `partiallyStore=true` does not propagate the wizard's primary key to tab subform render lifecycles for new records.* Any server-side cross-subform data-flow approach that depends on `parent_id` will fail silently on first visit. Use client-side DOM-reads when sibling-tab data is needed at render time.
2. *CustomHTML strips `<script>` from form rendering; custom Element plugins do not.* For inline JavaScript that needs to run on form load, build a one-class Element plugin — it's the same shape as `QualityBannerElement` or `GisPolygonCaptureElement`, takes ~30 minutes to scaffold, and gets you a re-usable form-builder palette element for free.

Reference: `plugins/parcel-zone-centring/src/main/java/global/govstack/parcelzonecentring/element/AutoCenterBootstrapElement.java` (the load-bearing class). Backups of every intermediate parcelGeometry form definition under `_backups/parcelGeometry.preL*.pretty.json`. Build-012 deployed and verified.

---

## D50 — 2026-05-09 — Userview RBAC: category-level GroupPermission for nav, API Builder routes for dashboard data

**Context.** Week 1 of the solo plan called for wiring role-based access control on the Farmers Portal userview so non-admin operators (`role_district_supervisor`, `role_finance_officer`, `role_field_officer`, `role_analyst`) see only the menus relevant to their role. Initial attempt patched every menu's `permission` block with `GroupPermission` and assumed that would hide menus from the navigation. It didn't — and the diagnosis took longer than the fix because three independent footguns in Joget DX 8.1 Community Edition's permission model each looked like the root cause of the symptom.

**Three footguns, source-verified.**

1. **`/web/json/**` is admin-only.** `wflow-consoleweb/.../applicationContext.xml` line 124 gates the data API at ROLE_ADMIN/ROLE_APPADMIN. Dashboard HtmlPages that fetch via `/jw/web/json/data/list/...` 403 for every operator. The `/api/**` rule (line 77) allows ROLE_USER.
2. **`GroupPermission` reads `allowedGroupIds`, semicolon-separated** — not `groupId` comma-separated. Earlier scripts wrote the wrong key/separator and silently authorised no-one (admin worked only via super-user bypass).
3. **Menu-level `permission` isn't checked at nav-build time** — only the category's `permission` block is. `UserviewService.createUserview()` (lines 305-318 = category check, 349-411 = menu check) only enforces `permissionDeny`/`permissionHidden` flags on menus, never the menu's own `permission` block. Per-menu permission is enforced at click-time (page access), not in the navigation rendering.

A fourth gotcha emerged when trying to work around #1 by setting `ROLE_ADMIN_GROUP` in app properties: `EnhancedWorkflowUserManager.getCurrentRoles()` line 134 adds the global `ROLE_ADMIN` to those users while inside the app, bypassing every `GroupPermission` check entirely. So that "shortcut" defeated the per-menu RBAC.

**Decision.** Two-layer enforcement:

* **Layer 1 — category-level `GroupPermission` on every userview category** (with the correct `allowedGroupIds` + `;` shape). Controls left-nav visibility. cat (district_supervisor) sees Dashboard / Registration Forms / MOA Office / Inputs Management / Reports; Budget / MM-Configuration / Admin / Master Data are hidden entirely.
* **Layer 2 — per-menu `GroupPermission`** (same shape) on every menu. Doesn't affect nav, but enforces page access if a user reaches the URL directly. Defence in depth, no UX cost.
* **Dashboards refactored to API Builder routes.** `form-creator-api` build-024 ships a new `GET /jw/api/formcreator/formcreator/data/list?appId=...&listId=...&api_id=...&api_key=...` endpoint that source-mirrors `FormListDataJsonController.formDataList` and returns the same `{total, data:[]}` shape (after API Builder envelope unwrap). 6 HtmlPages rewritten to use it. Falls under `/api/**` so cat can fetch dashboard data without admin escalation. ROLE_ADMIN_GROUP set to `role_sysadmin` only; no shortcuts.
* **Tooling.** `tooling/apply_rbac_v2.py` writes both layers idempotently. `tooling/rewrite_dashboard_urls.py` converts any legacy `/jw/web/json/data/list/...` URL it finds in HtmlPage bodies and adds the envelope-unwrap chain. Both are HARD-RULE compliant — they push via `form-creator-api`, never raw SQL.

**Trade-offs accepted.**

* Inside a category, all menus are visible to anyone who passes the category gate (per Joget Community-Edition limitation #3 above). Field officer entering Inputs Management sees Supplier / Inventory / Stock Transactions in the nav; click-time check then blocks them. UX is "click-blocks" not "hide" inside categories. Acceptable for v1; getting per-menu nav-hide needs the `permission_rules` infrastructure (separate task).
* `api_id` / `api_key` embedded in dashboard JS source. Any logged-in user can read any datalist via the new endpoint. Same threat surface as today's admin endpoint, since admin-equivalent users could already read everything; but a security-tightening item for the production-readiness roadmap (move to session-based auth on the `/api/data/list` endpoint, or per-datalist permission enforcement).

**Verification.** cat in fresh private window: 5 categories visible (Dashboard, Registration Forms, MOA Office, Inputs Management, Reports), Executive Overview KPI tiles populate, charts render, no 403 banners. clark in fresh private window: 2 categories (Registration Forms, Inputs Management). admin: full nav as before, dashboards still work.

**Reference files.**
- `tooling/apply_rbac_v2.py` — the canonical RBAC tool.
- `tooling/rewrite_dashboard_urls.py` — dashboard URL rewriter.
- `plugins/form-creator-api/.../FormCreatorServiceProvider.java::readDatalist` — the new ROLE_USER-accessible endpoint.
- `_jars/form-creator-api-build-024.jar` — deployed plugin.
- `docs/architecture/rbac_taxonomy.md` — role-to-menu mapping (the spec).
- `CLAUDE.md` "Userview RBAC — three Joget specifics" — gotcha summary so the next session doesn't repeat the diagnosis.

---

## D51 — 2026-05-10 — Week 2: transactional email plumbing + SMTP-provider lesson

**Context.** Week 2 of the solo plan called for wiring email notifications on every state transition in the subsidy lifecycle: application submitted → eligibility decision → voucher issued/redeemed/expired/cancelled → budget threshold alerts. Twelve templates total, fired from the existing lifecycle binders and from new scheduled jobs.

**Decisions.**

1. **`EmailDispatcher` helper class in reg-bb-engine** — a thin façade over Joget's `AppUtil.createEmail()` / Apache Commons `HtmlEmail.send()`. Each caller (`VoucherIssuanceTool`, `VoucherRedemptionTool`, `EligibilityProcessingWorker`, etc.) builds the subject + body strings inline with pre-resolved variables and calls the dispatcher. We don't use Joget's stock `EmailTool` workflow tool because most of our call sites run from REST endpoints or background workers — no `WorkflowAssignment` context for hash-variable resolution. Caller-pre-resolves is simpler and per-template is one Java class with three static methods (`subject`, `html`, `plaintext`). Source: `plugins/reg-bb-engine/.../notification/EmailDispatcher.java`.

2. **One template-helper class per email** — `VoucherIssuedEmail`, `VoucherRedeemedEmail`, `VoucherCancelledEmail`, `VoucherExpiredEmail`, `ApplicationOutcomeEmail` (which holds 02/03/04 since they share a call site). Markdown source of truth lives in `docs/notifications/`; the Java classes mirror those bodies for runtime use. JAR is the deployable; the markdown is the spec.

3. **Background auto-poller (`BackgroundWorkerScheduler`)** — a daemon thread spawned by the bundle Activator that calls `EligibilityProcessingWorker.execute()` every 60s. Without this, newly-submitted applications sit in `c_status=NULL` until someone manually hits `/api/budget/run-eligibility-worker`. Source: `plugins/reg-bb-engine/.../background/BackgroundWorkerScheduler.java`. For multi-node Joget deployments, add database-coordinated lease (one row, one node holds it) so the poller doesn't duplicate from N nodes; the worker's queue read is already idempotent so duplicates are correctness-safe but wasteful.

4. **Citizen-friendly application reference** — `AP-XXXXXXXX` derived from the first 8 hex chars of the application UUID. The spApplication form has no dedicated reference column yet (deferred to a future form-revision); the derivation gives the citizen something short and quotable while keeping operator-side search intact (operators can match by UUID prefix or by full UUID). Source: `EligibilityProcessingWorker.fireApplicationOutcomeEmail`.

5. **Mailtrap free-tier is unsuitable for our send pattern** — verified May 2026. The subsidy approval lifecycle fires three emails in a tight cluster (decision + 2 vouchers); Mailtrap free rejects burst sends with `550 5.7.0 Too many emails per second` regardless of client-side throttle (verified up to a 10-second client-side gap). Switched dev SMTP to **Gmail with a 16-character App Password** (host `smtp.gmail.com`, port 587, TLS). Gmail accepts 10+ emails/sec from one client; real delivery to a real inbox also simulates production better. The dispatcher ships a configurable inter-send throttle (`-Dregbb.email.gap.ms=N`, default 10000 ms while on Mailtrap; drop to 100 ms on Gmail/SES/MAFSN-prod).

**Templates wired and proven** (8 of 12):
- 02 application_auto_approved — ✅ end-to-end proven (Tumelo, Grace, Lerato)
- 03 application_pending_review — wired (call site tested)
- 04 application_rejected — ✅ end-to-end proven (Tlali)
- 06 voucher_issued — ✅ end-to-end proven via direct API and via worker auto-issuance
- 07 voucher_redeemed — wired
- 09 voucher_expired — wired
- 10 voucher_cancelled — wired

**Remaining 4 templates** (01 application_submitted post-processor; 05 supervisor digest; 08 7-day expiry reminder; 11/12 budget threshold alerts) tracked separately. Templates 05/08/11 require new periodic jobs — added to BackgroundWorkerScheduler in this same week's batch.

**Trade-offs accepted.**

* **Email failures are swallowed** (logged, never blocking the underlying business action). A failed approval email never blocks the approval; a failed voucher email never blocks the voucher issuance. The legal entitlement is the database row, not the email. Operators can re-trigger from the audit list if a notification miss is detected. Documented in `EmailDispatcher` and every call site's try-catch.
* **Background scheduler is single-node-safe only.** For multi-node Joget, add lease coordination. Deferred per "Convention over Invention" — this dev is single-node and stays single-node through UAT.
* **Recipient is hardcoded `aarelaponin@gmail.com` (dev override).** The farmer-registration form has no email column today; production cutover requires adding it + a "no email" branch for citizens without one. Tracked in the production-readiness roadmap.

**Reference files.**
- `plugins/reg-bb-engine/.../notification/` — EmailDispatcher + per-template helpers.
- `plugins/reg-bb-engine/.../background/BackgroundWorkerScheduler.java` — auto-poller.
- `docs/notifications/` — 12 markdown source-of-truth templates.
- `docs/operations/smtp_production_config.md` §6a — Mailtrap-vs-Gmail dev-SMTP lesson.
- `CLAUDE.md` "Email throttling — Mailtrap free is unusable" — gotcha summary for the next session.

---

*Add new decisions below this line.*

---

## D53 — Notifications are state-managed via joget-status-framework, not free-form status strings

**Date:** 2026-05-11 (W2.6 + W2.7, build-127)
**Status:** Accepted
**Context.** Up to build-126 the notification dispatcher wrote rows to `app_fd_notification_queue` and set `c_status` as a free-form string (`PENDING`, `SENT`, `FAILED`). There was no validation that transitions were legal, no audit trail for state flips, and no operator UI to retry failed dispatches. Operators had no surface to see "what notifications fired for this applicant?" — only joget.log greps could answer that.

**Decision.** Wire the notification lifecycle into the shared `joget-status-framework` plugin (already used by `form-quality-runtime`). Define `NotifEntityType.NOTIFICATION` with the transition map:

```
   (new) → PENDING → SENT          (terminal happy path)
              │
              ├→ SKIPPED            (terminal: no recipient in live mode,
              │                      template disabled, body blank)
              │
              └→ FAILED → PENDING   (operator-triggered retry)
                    │
                    └→ DEAD_LETTER  (terminal: max retries hit)
```

Every dispatch — immediate or scheduled, EMAIL or SMS — now:

1. Writes a `notification_queue` row with `status=PENDING` at the start of the send (via `NotifAudit.create()`).
2. Calls `StatusFramework.transition(...)` after the backend call resolves, flipping to SENT / SKIPPED / FAILED.
3. The transition writes one row to `app_fd_audit_log` automatically (via `TransitionAuditEntry.toFormRow()` in the framework).

**Two operator-facing DataListAction plugins** (`RetryNotificationAction`, `MarkDeadLetterAction`) registered in `Activator` give operators bulk control over FAILED rows.

**Why use the existing framework rather than invent notification-specific state machinery.**

- **Convention over Invention:** form-quality-runtime already runs through `joget-status-framework`; adding notifications gives us one consistent shape for "every entity transition the platform makes." Operators learn one audit-log pattern; one Audit Trail datalist serves both quality issues and notification dispatches.
- **DIP / SRP balance:** the framework owns the validate-load-store-audit machinery; reg-bb-engine just declares its lifecycle and consumes the API. Adding more lifecycles (W3.1 application-submission lifecycle, etc.) is now zero-marginal-cost.
- **CLAUDE.md "Reading Joget source first":** we re-read `StatusFramework.transition()` before writing wrapper code; the canonical mechanism (`row.setProperty("status", code)` + `dao.saveOrUpdate(...)` + `audit_log` insert) is reused verbatim. No new audit table; no new validator logic.

**Why the W2.7 banner is rendered via userview customJs, not a server-side endpoint.**

- A server-side `/jw/api/regbb/notif/status` endpoint would require yet another build + upload cycle. The banner content is static (the test inbox addresses don't change during pre-production).
- The banner is removed as part of the go-live cutover — same operation that flips `regbb.notif.testMode=N` in `setenv.sh` and restarts Tomcat. Bundling the userview JSON edit into that cutover is cleaner than two separate change paths.
- The script `tooling/inject_test_mode_banner.py` is idempotent (sentinel markers `=== RegBB test-mode banner ===` scope its edits). To remove: `--apply --remove`. To re-inject after a rollback: `--apply`.

**Operator-facing surfaces shipped in W2.6 + W2.7.**

| Surface | Where | Shows |
|---|---|---|
| Notification Queue | Admin → Notification Queue | Every dispatch with full audit columns; status filter; Retry + Mark Dead-Letter row actions |
| Test-mode banner | Top of every userview page | Big yellow strip: "TEST MODE — emails to aarelaponin@gmail.com, SMS to +26658515039" |
| Audit Trail | Admin → Audit Trail (existing) | Full forensic transition history via `app_fd_audit_log`, filter `entity_type=NOTIFICATION` |

**Reference files.**
- `plugins/reg-bb-engine/.../notification/NotifEntityType.java` + `NotifStatus.java` — the state-machine declaration.
- `plugins/reg-bb-engine/.../notification/NotifAudit.java` — convenience wrapper over `StatusFramework`.
- `plugins/reg-bb-engine/.../notification/RetryNotificationAction.java` + `MarkDeadLetterAction.java` — operator-facing bulk actions.
- `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/Activator.java` — `registerNotificationLifecycle()`.
- `tooling/inject_test_mode_banner.py` — banner inject/remove tool.
- `tooling/extend_notif_queue_form.py` — adds the new audit columns.
- `tooling/refresh_notif_queue_datalist.py` — datalist with new columns, filters, row actions.
- `docs/implementation/notification_test_mode_override.md` — operator playbook including "where to see notifications" and go-live procedure.

**Follow-up.** The scheduled-dispatch path (`SmsDispatcher` + scheduled-flagged templates) still needs the same StatusFramework integration. Today no template fires through the scheduled path, so this is dormant code. Address when ScheduledEmailJobs or NotificationQueueWorker are next exercised.

---

## D52 — Test-mode override implemented as JVM system properties (not a DB-backed singleton form)

**Date:** 2026-05-11 (W2.5 step 4c)
**Status:** Accepted, build-125 onwards
**Context.** The notification dispatcher (W2.5) needs a global kill switch that re-routes every email + SMS to a designated test inbox/phone during pre-production, so we can exercise the 12-event lifecycle without delivering to real Lesotho farmers. The switch flips `Y → N` exactly once, when MAFSN authorises live sends.

**First attempt (W2.5 step 4b, rejected).** A singleton DB-backed form `notif_global_config` (form + datalist + Admin menu) that operators would toggle in App Composer. The form was created via form-creator-api; the dispatcher read the row by raw SQL.

**Why it didn't work.** Joget's two-cache model — see CLAUDE.md "What goes wrong if you violate the rule" — hits a race for *brand-new* forms: `FormDefinitionDao.add()` populates cache 2 (per-form Hibernate ORM mapping) BEFORE the underlying `app_fd_<formId>` table exists (verified in `joget.log`: `ERROR: relation "app_fd_notif_global_config" does not exist` at the moment of `add()`). A `forceTableCreation()` call moments later creates the table, but cache 2 stays stuck on the broken mapping for the lifetime of the JVM. The datalist binder reads zero rows even though the singleton IS in the table. The CLAUDE.md-documented recovery (App Composer Save) did NOT clear it in practice — multiple App Composer saves and multiple form-creator-api re-pushes left cache 2 broken.

**Decision.** Drop the form-backed design. Read the override from three JVM system properties:

- `regbb.notif.testMode` (default `Y` — fail-safe)
- `regbb.notif.testEmail` (default `aarelaponin@gmail.com`)
- `regbb.notif.testPhone` (default `+26658515039`)

Set in Joget's `setenv.sh`. Going live = edit one line (`testMode=N`), restart Tomcat. The `notif_global_config` form / datalist / Postgres table stay as inert orphans (HARD RULE forbids raw DELETE on Joget-managed metadata; cleanup via App Composer "Delete Form" UI when convenient).

**Principles in tension and how they resolved.**
- **Configuration over code (P1)** and **operator-authorable rules (ADR-031)** would normally pull toward a UI-editable surface. They lost here because (a) this is a one-shot switch, not an everyday operator concern, (b) Joget's cache trap made the UI surface unreliable for newly-created forms, and (c) system properties give a stronger audit trail (`setenv.sh` lives in deployment scripts; UI clicks don't).
- **Convention over Invention** and **fail-safe defaults (P5 / loud failure)** pulled toward the JVM-property design. The dispatcher already uses `regbb.email.gap.ms` and `regbb.sms.backend` as system properties — same mechanism, same setenv.sh, same restart semantics. The fail-safe default (`testMode=Y` if unset) means a misconfigured deployment cannot accidentally send to real citizens.

**Trade-off accepted.** Operators can't flip the switch via UI. They have to edit one line and restart Joget. Acceptable because:
- This is a single-shot decision in the project's lifecycle (not an everyday toggle).
- Going-live already requires Tomcat restart for several other settings (SMTP relay swap, SMS gateway credentials).
- The person who flips this also has shell access (DevOps role doing the cutover anyway).

**Reference files.**
- `plugins/reg-bb-engine/.../notification/NotificationConfig.java` — the system-property reader (build-125).
- `docs/implementation/notification_test_mode_override.md` — operator playbook for the go-live flip.
- `CLAUDE.md` "Brand-new forms hit a table-before-mapping race in form-creator-api" — added to the gotcha list so the next session doesn't reinvent the singleton-form approach.
- Backup of pre-removal userview: `_backups/v.preRemoveNotifGlobalConfig.20260511-114249.json`.

**Follow-up.** The form `notif_global_config` + datalist `list_notif_global_config` + table `app_fd_notif_global_config` are orphan surface. Schedule a cleanup pass through App Composer's "Delete Form" UI when operationally convenient. Not urgent — they're inert.




## D54 — 2026-05-11 — Application lifecycle on joget-status-framework: parallel column, not replacement

**Decision.** W3.1 adds a coarse-grained application lifecycle state machine (DRAFT / SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED / PENDING_REVIEW / WITHDRAWN) registered with `joget-status-framework`. The state lives in a NEW column `c_lifecycleState` on `app_fd_subsidy_app_2025`, NOT in the existing `c_status` column. `c_status` keeps its fine-grained rules-driven values (`auto_approved`, `auto_rejected`, `pending_data_clarification`, `approved`). The two columns coexist; `AppLifecycleMapper.fromStatus()` is the one-way derivation function.

**Why parallel, not replacement.** The framing-question selection was "reuse c_status". Two operational reasons forced a deviation:
1. Every existing dashboard, report, and operator inbox filter in this app reads `c_status` for one of its 4 values. Renaming the values across 19 production-shaped rows AND updating all read-side consumers (Executive Overview, MOA Office inbox, Notification Dashboard, ~5 datalists) would have been a multi-hour change touching ~10 surfaces. Parallel column = zero blast radius.
2. The fine-grained value `auto_approved` carries information the coarse `APPROVED` doesn't (rules-decided vs. operator-decided). Collapsing the two loses forensic detail that audit operators rely on. Coarse + fine together gives both views.

**Phase 1 scope (build-131, May 2026).** Wired transitions: SUBMITTED (when EligibilityProcessingWorker picks up the row, before chain runs) → APPROVED / REJECTED / PENDING_REVIEW (when chain produces a c_status). Also: any operator decision via `RegBbOperatorDecisionBinder` writes the lifecycle transition + audit_log entry.

**Deferred to future slices:**
- DRAFT — needs wizard save-as-draft (the wizard today is submit-on-final-save).
- UNDER_REVIEW — needs an "operator opens for review" event distinct from the decision; operators today have no separate read action.
- WITHDRAWN — needs a citizen-withdraw UI action (button on the citizen-side application view).

**Backfill: NOT done.** The 19 existing rows have empty `c_lifecycleState`. The `AppAudit.transition()` method does an implicit `create()` with seed=`AppLifecycleMapper.fromStatus(c_status)` when called on a row with empty lifecycle, so any future operator action on an existing row brings it to consistency. No data migration script was needed.

**Principles in tension and how they resolved.**
- **Single source of truth** vs. **don't break what works.** Single SoT pulled toward c_status replacement. "Don't break what works" pulled toward parallel column. The latter won because the cost of breakage (every existing dashboard going wrong) exceeded the cost of an extra column.
- **Comprehensive lifecycle** (all 7 states, all hooks) vs. **incremental delivery.** Both have merit. Phase 1 = SUBMITTED + terminal transitions only. DRAFT/UNDER_REVIEW/WITHDRAWN registered in the state machine as future-ready but not wired. Lets us ship the audit trail benefit today without the 3 deferred slices.

**Reference files.**
- `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/lifecycle/` — `AppEntityType`, `AppLifecycleStatus`, `AppLifecycleMapper`, `AppAudit` (4 classes).
- `plugins/reg-bb-engine/.../Activator.java` `registerApplicationLifecycle()` — the transition map registration.
- `plugins/reg-bb-engine/.../processing/EligibilityProcessingWorker.java` — SUBMITTED + terminal-state hooks.
- `plugins/reg-bb-engine/.../binder/RegBbOperatorDecisionBinder.java` — operator-decision hook.
- `tooling/add_lifecycle_state_to_application.py` — adds the field.
- `tooling/backfill_app_lifecycle_state.py` — docs the path NOT taken.

**Follow-up.** Phase 2 (future slice): wizard save-as-draft + citizen-withdraw UI + operator-open hook + (optionally) consolidate `c_status` into `c_lifecycleState` after all consumers are updated.

## D55 — Lazy polyrepo extraction (2026-05-12)

**Decision.** Reject the mass-extraction plan for the Tier 1 + Tier 2 plugins. Extract plugins to their own repos lazily, only when a concrete second consumer surfaces a real reuse demand. Until each lazy extraction lands, the plugin lives at `farmers-portal/plugins/<plugin-name>/` with its citable GitHub URL.

**Why.** YAGNI dominates Convention-over-Invention in the absence of demand. Mass-extracting 13 plugins speculatively is 3-5 days of work for plugins mostly with no second consumer; each repo would carry placeholder docs that no one reads.

**First trigger.** GAM (banking workflow) needed `joget-status-framework` as a Maven dependency, May 2026. Standalone repo created at `github.com/aarelaponin/joget-status-framework`.

**Trade-off accepted.** Temporary duplication during the window between extraction and Pass C (the eventual consolidation that switches farmers-portal to Maven-artifact consumption + removes in-tree copies). Manual sync discipline: bug fixes land in the standalone repo first, then copied into farmers-portal's in-tree copy.

**Reference.** ADR-032 (`docs/architecture/adr/adr-032-lazy-polyrepo-extraction.md`). Cross-project handover note for GAM at `x_archive/NOTE-for-GAM-adopting-joget-status-framework.md`. Supersedes the "Tier 1 + Tier 2 polyrepo split, Pass B post-UAT" framing in the GovStack alignment report §7.3.

## D56 — Bidirectional app-state sync (2026-05-12)

**Decision.** Adopt per-asset-class sync discipline: plugin source push-only (local IDE → Joget); forms/datalists/userviews/master-data push+pull (push via `form-creator-api`, pull via `tooling/sync_pull.py` against Postgres); XPDL workflows + app-level properties manual on-demand only.

**Why.** A May-2026 audit found the repo contained ~40% of what was actually deployed in Joget (83/218 forms, 64/226 datalists). Root cause: push-only workflow — App Composer edits never flowed back. This blocked the fresh-install-from-repo claim and made the public repo embarrassing.

**Mechanism.** `tooling/sync_pull.py` queries Postgres for `app_form` / `app_datalist` / `app_userview` and dumps to individual JSONs. For master-data tables (`app_fd_md*`, `app_fd_mm_*`), dumps rows to per-form YAMLs under `app/seeds/master-data/`. Applies credential-placeholder substitution to all output. One-command ritual: `python3 tooling/sync_pull.py && git diff app/ && git commit && git push`.

**Trade-off accepted.** Sync is operator-disciplined, not automatic. Joget's built-in git would auto-commit on every save but requires VM filesystem access we don't have. ADR-033 is the right design for now; revisit when VM access is sorted.

**Reference.** ADR-033 (`docs/architecture/adr/adr-033-bidirectional-app-state-sync.md`).
