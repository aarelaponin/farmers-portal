# Subsidy → IM backlog

| | |
|---|---|
| Status | Active backlog (May 2026) |
| Owner | Aare Laponin |
| What this is | The single ordered list of deliverables between current state and "subsidy fully operational + IM module ready to start." |
| Companion documents | `docs/architecture/architecture/solution-architecture.md`; `docs/architecture/architecture/components/*.md`; `docs/architecture/migration-plan.md` revision 3; `docs/architecture/policy-to-rules-migration.md`; ADRs 001–028. |
| Gate | IM module (Phase 3) does **not** start until: (a) all Layer 1+2+3 items below are done, (b) 20/20 parity test passes through generated UX, (c) Aare's qualitative operator-UX sign-off, (d) Budget Engine is live with the four 2025 envelopes seeded. |

This document supersedes the per-component "Phase 1 close-out" backlog notes scattered across kernel, framework, subsidy, and IM SADs. It is the canonical sequencing reference.

The principle: **fix the kernel before exercising it twice.** IM uses the same MM-form-gen kernel that subsidy uses. Any kernel weakness compounds across both modules. Better to harden it once on subsidy, then IM lands on already-proven infrastructure.

---

## Estimated total

**~7–10 weeks at 2 FTE**, depending on parallelism. Layer 1 + Layer 2 are mostly parallelisable (widget work in track E1, framework completeness in track E2). Layer 3 and Layer 4 are partially serial.

---

## Layer 1 — kernel UX parity (highest priority)

The placeholder widgets in `MetaScreenElement.synthesiseField` (kernel SAD §11 K-R2). Every item is a switch case in `synthesiseField` that pass-through-instantiates a Joget enterprise plugin. Without these, real programmes can't be authored cleanly.

### L1-1 — `cascading_select` widget pass-through  
**Why.** District → village is the canonical Lesotho cascading dropdown (D21). Every programme that asks for village needs this.  
**Ships.** New case in `synthesiseField`: instantiates `org.joget.lst.CascadingMdmSelectFilterType` (or the equivalent installed plugin); wires `mm_field.optionsFilterField` for the parent-field linkage.  
**Effort.** 1 day.  
**Depends on.** Nothing — pure kernel change.  
**Acceptance.** A `mm_field` with `widget=cascading_select`, `optionsCatalogId=MD_VILLAGE_LIST`, `optionsFilterField=district` renders a select that filters by the parent `district` selection at runtime. Verified in test wizard.

### L1-2 — `farmer_search` widget pass-through *(deferred to Phase 3 / IM)*  
**Reframe (May 2026).** Source-read of `SmartSearchElement.java` showed it's domain-specific farmer-search — hardcoded "Smart Farmer Search" name, fixed display columns, NID/phone pattern detection, calls `/jw/api/fss`. Not a generic typeahead. Used by `parcelLocation` to look up the farmer for a parcel; that's the canonical use case.  
**Why deferred.** Subsidies don't need farmer search — citizen is the applicant, NID is captured directly on the Identity tab. The widget is useful in IM (voucher→farmer, allocation roster) and in farmer/parcel registry (out of convergence scope per D31). Lands in Phase 3 alongside IM module work, not as Phase 1 close-out.  
**When picked up.** Phase 3 IM start. Widget kind would likely be `farmer_search` (not generic `smart_search`) to match what the plugin actually does. Pass-through similar to L1-1 cascading_select pattern.

### L1-2-bis — `select_from_form` (generic typeahead-shaped SelectBox against an MD form) *(included free with L1-1)*  
**Why.** What L1-2 was originally trying to enable — a SelectBox sourced from a master-data form (e.g. crop from `md19crops`, input from `md44Input`) — is *already* covered by L1-1's `addCascadingOptionsBinder` if `optionsFilterField` is empty. The cascading-select case is a superset of "select against a form." Cost: zero — already implemented.  
**Action.** Document this in the kernel SAD §5.2 widget table: `cascading_select` with empty `optionsFilterField` is the canonical "select against an MD form" pattern. No new widget kind needed.  
**Status.** Effectively done as a side effect of L1-1.

### L1-3 — `signature` widget pass-through  
**Why.** Distribution-event captures (IM future); citizen declaration on subsidy submission (optional).  
**Ships.** New case: instantiates `org.joget.plugin.enterprise.Signature` with default size + label.  
**Effort.** 0.5 day.  
**Depends on.** Nothing.  
**Acceptance.** Signature pad renders, captures, persists base64 signature image to the column.

### L1-4 — `file_upload` for form-kind screens  
**Why.** Today only `kind=documents` screens synthesise FileUpload (via `synthesiseDocumentChildren`). A regular form-kind screen with `mm_field.widget=file_upload` should work too — useful for one-off attachments not bound to `mm_required_doc`.  
**Ships.** Extends the form-kind `synthesiseField` switch with a `file_upload` case. Reuses the file-move temp-path lifecycle from `synthesiseDocumentChildren` (see kernel SAD §8.4).  
**Effort.** 1 day.  
**Depends on.** Nothing.  
**Acceptance.** A `mm_field` with `widget=file_upload` on a form-kind screen renders FileUpload, accepts upload, the file moves from temp to `wflow/app_formuploads/<table>/<id>/<filename>` on save, and downloads work.

### L1-5 — `gis_polygon` widget pass-through  
**Why.** Parcel tab on the subsidy wizard currently has plain text fields for `district` / `agro_zone` / `village_name` / `area_hectares`. The native parcel registry has full polygon capture via `GisPolygonCaptureElement`. The metadata-driven path needs the same.  
**Ships.** New case: instantiates `global.govstack.gisui.element.GisPolygonCaptureElement` with default Lesotho coordinates (lat -29.6, lon 28.2 per CLAUDE.md), auto-populates companion HiddenFields (`area_hectares`, `perimeter_meters`, `centroid_lat`, `vertex_count`, etc. — see CLAUDE.md GIS conventions).  
**Effort.** 1.5 days (the companion HiddenField synthesis is the wrinkle).  
**Depends on.** Nothing.  
**Acceptance.** Parcel tab renders the polygon capture; citizen draws a polygon; `c_area_hectares` column is auto-computed and saved alongside the polygon WKT.

### L1-6 — `repeating_group` widget pass-through  
**Why.** `mm_benefit` rows render as a per-programme grid (citizen sees what they'd receive); household member tables need the same; IM allocation lines will too. The hardest of the six because `FormGrid` requires both `loadBinder` AND `storeBinder` configured (kernel SAD §8.5 — D9 + the FormGrid gotcha).  
**Ships.** New case: instantiates `org.joget.plugin.enterprise.FormGrid` with both binders configured to `MultirowFormBinder` pointing at the child `mm_field.referencedFormId`. Needs a new `mm_field.referencedFormId` column on the form definition (one new TextField property).  
**Effort.** 2–3 days (binder dual-wiring + verification with both load and save paths).  
**Depends on.** Form-definition update for `mm_field.referencedFormId`. Same one-line addition pattern as `targetValue` (ADR-027).  
**Acceptance.** Render Lerato Mokoena's household tab via metadata-driven path. Add a member, save, reopen — member is there. Edit a member, save, reopen — change persists. Delete a member, save, reopen — gone. End-to-end parity with the legacy `farmerHousehold` form.

### Layer 1 total effort
~7 days serial; ~1 week at 2 FTE in parallel.

---

## Layer 2 — RegBB framework completeness (subsidy-specific spec coverage)

### L2-1 — Capability adapter registry (per RegBB §3.2; ADR-020 forthcoming)  
**Why.** `SqlPathEvaluator` today has hand-coded mapping for `$registry.farmer.first_name`. Real eligibility rules touching parcel data (DET_SMALLHOLDER's "≤ 5 ha cultivated total"), household composition (D7's `households.vulnerability`), or other entity registries can't be authored without going back to the kernel team. Generalise to a `CapabilityAdapter` interface registered via OSGi service.  
**Ships.**  
- `CapabilityAdapter` interface: `String getCapabilityName(); Map<String,Object> resolve(String nationalId, EvalContext ctx)`.  
- Refactor `SqlPathEvaluator` to look up adapters via `BundleContext` instead of hardcoded mapping.  
- Two adapters lit up: `farmers.byNid` (full applicant identity row) + `parcels.summary` (cultivated area total, parcel count, gisVerified).  
- Author ADR-020.  
**Effort.** 3–5 days (interface design + refactor + 2 adapters + tests).  
**Depends on.** Nothing.  
**Acceptance.** A determinant `$registry.parcels.summary.cultivated_total <= 5` evaluates against a real applicant whose parcels sum to 7 ha → returns FALSE. Audit row records `evaluator='sql'` with the resolved registry value in `inputs_json`. New adapter can be added without touching `SqlPathEvaluator`.

### L2-2 — Single-window catalogue page (RegBB §6.1.6) ✅ DONE (May 2026, build-066)  
**Shipped.** `kind=guide` + `guideKind=catalogue` renders one card per `mm_registration` row scoped to the screen's serviceId; each programme's applicability rule evaluates through `RoutingEvaluator`; outcomes drive coloured indicators (green TRUE / red FALSE + failMessage / grey NULL / yellow ERROR); click-to-select highlights and updates a hidden `applied_programme` field that persists on save. New `mm_screen.guideKind` column added; new `MetaModelDao.listRegistrationsForService(serviceCode)` method added; new `renderCatalogue` method in `MetaScreenElement`. Detailed mechanism + property catalogue documented in kernel SAD §8.8.

### L2-2-bis — Live in-flight catalogue re-evaluation (Phase 2)  
**Why.** Phase 1 known limitation per kernel SAD §8.8: in `?_mode=add` mode, MultiPagedForm doesn't surface previous-tab values to the load binder of an earlier tab, so the catalogue's `applicantData` is empty for the entire wizard until first save. Every applicability rule depending on captured data evaluates to NULL; every card shows grey "Need more information." After first save (or in `?_mode=edit`), it works correctly. This is a framework constraint, not a kernel gap.  
**Ships.** Ajax round-trip on tab focus: JS posts the wizard's current form-element values to `POST /jw/api/regbb/catalogue/eval`, kernel builds an `EvalContext` from the posted payload, re-evaluates each programme, returns outcomes; cards swap indicator + reason in place.  
**Effort.** ~3 hours.  
**Depends on.** Nothing.  
**Status.** Deferred to Phase 2 — structural catalogue is already RegBB §6.1.6 conformant; live re-evaluation is UX polish.

### L2-3 — `bot_pull` mm_action — NID auto-fill from farmer registry (D11) ✅ DONE (May 2026)  
**Why.** Citizen types their NID; the system should auto-fill full_name, date_of_birth, contact_phone, gender from the farmer registry — eliminating re-entry. Architectural slot existed in the metamodel (`mm_action.kind=bot_pull`); the dispatcher and field-mapping execution were missing.  
**Shipped.**  
- New `POST /jw/api/regbb/bot_pull/eval` endpoint in `RegBbEvalApi`. Looks up `mm_action` by code, validates `kind=bot_pull`, reads `triggerJson.fieldId` + `configJson.fieldMappings`, resolves each target via `SqlPathEvaluator.getCapability(sourceCapability).resolve(sourceField, triggerValue, ctx)` (i.e. through the L2-1 capability adapter registry), returns `{status, actionCode, resolved: {target: value, ...}}`. Audit row written per `reg_bb_eval_audit` per action invocation.  
- `MetaScreenElement.attachBotPullScript` synthesises a CustomHTML element carrying inline JS that wires `blur` listeners on every field whose `mm_field.actionIds` references a `bot_pull` action. On blur the JS POSTs to the endpoint, applies the `resolved` map to each target field — handling text/date/select via `.value` and radio/checkbox groups via `.checked` on the matching node (radio gotcha: `querySelector('[name=X]')` returns the FIRST radio, not the matching one — see CLAUDE.md). Never overwrites values the user already entered.  
- One demo `mm_action` row: `AUTOFILL_FROM_FARMER_REGISTRY` triggered by `national_id`, mapping `full_name`, `date_of_birth`, `contact_phone`, `gender` from `farmer.*`. Targets limited to fields that exist on the APP_APPLICANT screen AND resolve via `FarmerByNidAdapter`. `district` deliberately not mapped — `farmerbasicinfo` doesn't carry it and `farmerresidency` is empty.  
- `FarmerByNidAdapter` refactored from inferred `c_<field>` SQL to an explicit `FIELD_TO_COLUMN_SQL` map: `full_name` → `TRIM(COALESCE(c_first_name,'') || ' ' || COALESCE(c_last_name,''))`, `contact_phone` → `c_mobile_number`, `email` → `c_email_address`, others bare columns. Aliases hide schema idiosyncrasies from rule authors.  
**Verified.** Citizen enters NID `066257236627` on the Identity tab, blurs the field, sees full_name="Mants'ali Panyane", gender=Female, date_of_birth="1985-10-01", contact_phone="+26659518328" populated automatically. Audit row records each `farmer.<field>` resolution. NID with no registry row → fields stay blank with no error.

### Layer 2 total effort
~7–10 days serial; ~1.5–2 weeks at 2 FTE.

---

## Layer 3 — Phase 2 + Phase 2.5 deliverables

### L3-1 — Budget &amp; Commitment Engine (Phase 2.5)  
**Why.** Without budget control, operator approvals can blow programme budgets and the system has no visibility. Per ADR-022 / ADR-023 / ADR-024 / ADR-025 / ADR-026 (already drafted).  
**Ships.** Per `docs/architecture/architecture/components/budget-engine.md`:  
- New OSGi bundle `regbb-budget-engine` (~1500–2500 LoC).  
- Storage tables: `budget_envelope`, `budget_event`. Materialised view `budget_projection`.  
- `BudgetEventListener` subscribed to `mm_action.kind=budget_event` dispatches.  
- `CostEstimationService` with `RuleToSqlCompiler` (ADR-026) for fast registry counting.  
- Operator UX surfaces: inline budget hint on operator decision tab, programme launch wizard, budget dashboard, manual adjustment surface.  
- Initial envelope seed for the four 2025 programmes.  
- mm_determinant rules: 4 amount formulas + tolerance + overrun policy + authorisation defaults.  
- Sysadmin XPDL processes for budget-driven workflows (Joget's process designer; not RegBB-authored).  
**Effort.** 3–4 person-weeks. ~2 weeks calendar at 1 FTE focused; less if parallelised across the bundle build + operator UX.  
**Depends on.** L1 widgets (budget dashboard reuses repeating_group; manual adjustment surface uses the kernel).  
**Acceptance.** Operator opens an application; sees inline budget hint ("Approving will pre-commit M2,000. Envelope after: M915,000 / M1,200,000"). Approves; ledger writes RESERVATION → PRE_COMMITMENT events. Dashboard reflects the change within seconds. Programme designer authors a new programme; CES estimates cost; launch gate evaluates the rule; programme either publishes or shows the rule's failMessage. Reconciliation report at week-end matches.

### L3-2 — `MetaReviewElement` per RegBB §7.2  
**Why.** Today's wizard-level `readonly=Y` flag is a workaround. RegBB §7.2 specifies role-scoped review screens via `mm_role_screen.sectionsJson` — citizen tabs read-only by default, decision tab editable per role, decision affordances per role's permissions. Spec conformance.  
**Ships.**  
- New element `MetaReviewElement` (sibling to `MetaWizardElement`; could share base class).  
- Reads `mm_role_screen.sectionsJson` for the (role × service) pair.  
- Renders citizen tabs with the per-tab readonly mask from sectionsJson.  
- Renders decision tab with operator-role-specific options (filtered via `decision_authorisation` rule per ADR-029 forthcoming).  
- Form definitions for the operator review surface migrate from `MetaWizardElement(readonly=Y)` to `MetaReviewElement`.  
**Effort.** 1.5–2 weeks.  
**Depends on.** Decision authorisation rule (ADR-029, future cleanup from policy-to-rules migration).  
**Acceptance.** A `MOA_OPERATOR` role sees Approve / Reject / Send Back. A `MOA_DIRECTOR` role additionally sees Override-Eligibility option. Both share read-only access to citizen tabs. Section-by-section role configuration via `mm_role_screen.sectionsJson` works without code changes.

### L3-3 — Programme Builder admin UX (Phase 2 — DEFERRED)  
**Why.** Operator-analysts authoring programmes via the 13 separate CrudMenus is workable but rough. Composite "Service → Registration → Eligibility → Benefits → Documents" guided wizard would be cleaner.  
**Ships.** A new admin-side userview category that orchestrates writes to the 13 mm_* tables in a guided sequence.  
**Effort.** 2–3 weeks.  
**Status.** **Deferred to post-IM.** Operators can author through the existing CrudMenus (D13 Phase 1 decision); Programme Builder is sustainability/UX polish, not a functional gap. IM doesn't block on it.

### Layer 3 total effort (excluding deferred)
~5–6 weeks serial; ~3–4 weeks at 2 FTE with the Budget Engine on its own track.

---

## Layer 4 — proof, not feature work

### L4-1 — First parity dry-run (3 fixtures × 4 programmes) ✅ DONE (May 2026)
**Why.** Surfaces UX defects you can't see when testing one programme at a time. First time someone-not-the-author drives the whole flow.
**Shipped.** 3 fixtures (Mants'ali / Mosito / Lineo) submitted via the citizen UI manually for the first cycle, then via the automated runner for the regression cycle. Five defects surfaced and fixed inline (see L4-2 below).
**Acceptance.** 12/12 scenarios end-to-end. Five defects logged + fixed during L4-1.

### L4-2 — Defect cleanup + iterate ✅ DONE (May 2026)
**Five defects fixed during L4-1 / L4-3 cycles:**
- **D-001** — `MetaScreenElement.renderCatalogue` leaked the first row of `app_fd_subsidy_app_2025` into a fresh `?_mode=add` evaluation (load binder returns arbitrary rows when no PK matches). Fix: gate load-binder reads on `formData.getPrimaryKeyValue() != null`. Kernel-widget. CLAUDE.md updated.
- **D-001b** — Catalogue's client-side Ajax refresh read `select.value` which defaults to the first option for untouched selects, sending phantom `agro_zone="lowlands"` etc. to `/catalogue/eval`. Fix: gate `<select>` and radio reads on a tracked `dataset.userSet` flag set on `change`. Kernel-widget. CLAUDE.md updated.
- **D-002** — `SqlPathEvaluator.substituteRefs` used SQL-style `''` escape for apostrophes, but the DSL tokeniser doesn't support escapes. `Mants'ali Panyane` broke `$registry.farmer.first_name != ""` with `parse_error:unsupported operator: 'ali'`. Fix: choose the quote char based on which doesn't appear in the value. Framework-spec. CLAUDE.md updated.
- **D-003** — Missing uniqueness check: same NID could submit the same programme twice and both auto-approved. Fixed via the spec-conformant capability adapter pattern (RegBB §3.2 / ADR-020): new `SubsidyApplicationCountAdapter` registered as `"subsidy"`, exposes virtual field `prior_applications_this_programme`. Four `DET_NO_DUPLICATE_PRG_xxx` mm_determinant rows (one per programme) added to the YAML. PRG_001 strategy switched from `score_based` to `all_must_pass` so duplicate-fail is mandatory-fail (also closes the score-based loophole where Mosito-on-PRG_001 could squeak through). Module-content + spec gap.
- **D-004** — Single-rule programmes (PRG_002, PRG_003) misclassified rule-FALSE as `pending_review` because `score_based(min=0, ...)` lets a complete-failure score (0) fall in the `[0, threshold)` band. Fix: switched both programmes to `evaluationStrategy: all_must_pass`. With one rule, all-or-nothing is the right semantic. Module-content.

### L4-3 — Full parity test (5 farmers × 4 programmes = 20 scenarios) ✅ DONE (May 2026) — **EMPIRICAL CONVERGENCE GATE PASSED**
**Why.** Pre-specified ground-truth outcomes per scenario. All 20 must pass through the generated UX. Per `convergence-framework.md` §7 — proves kernel + framework converge cleanly on a second concrete service shape.
**Shipped.** Automated test harness `tooling/run_l4_scenarios.py` posts each scenario through `/jw/api/form/subsidyApplication2025`, polls the eligibility outcome, asserts disposition + status against the ground-truth matrix, cleans up. Run-time ~30s for the full 20-scenario sweep. Profiles A/B/C plus two extension fixtures D (foothills/berea/6.5ha — all-rejected boundary) and E (senqu_river_valley/quthing/drought=Y — exercises the OR branch of `DET_MOUNTAINS_OR_SENQU` and the quthing arm of `DET_DROUGHT_DECLARED`).
**Acceptance.** 20/20 passed. No Java engine changes required after L4-2; only configuration (YAML) + analyst-driven artefacts (mm_api endpoints, scenario fixtures) + one rule-grammar bug fix (D-002 — quoted-string escape). Empirical convergence proven.
**Bonus L4 deliverables:**
- Analyst-driven API generation pattern: new `mm_api:` YAML section + `POST /formcreator/apis` endpoint + `BuilderDefinitionDao` upsert. Any form spec'd by the analyst can now be exposed as REST without manual App Composer steps.
- `SubsidyApplicationCountAdapter` (third capability adapter after `farmer` and `parcels_summary`) — proves the registry pattern's extensibility.

### L4-4 — Operator UX qualitative sign-off ⏳ DEFERRED until IM Phase 3 lands
**Why.** The other half of the gate per Phase 1 plan §8 — quantitative test (20/20 ✅) plus qualitative review. **Originally scheduled for Phase 1 closure; moved to post-IM** because operators don't experience the platform as separate modules. They see one tool that handles application review AND input distribution. Signing off on "operators can review this" mid-stream — with subsidy approval working but IM still XPDL-and-paper-based — creates a false certainty. The MOA operator might bless what's there, then come back when IM lands and discover ergonomic issues that span both modules (e.g., handing off an approved subsidy application to an IM voucher campaign, where the friction surfaces at the boundary).
**Ships.** Aare's review session with at least one MOA operator using the system end-to-end across BOTH subsidy approval and IM voucher / distribution flows. Yes/no on "citizens can use this; operators can review and dispatch this."
**Effort.** 1 day.
**Depends on.** IM Phase 3 launches with the same kernel/framework convergence (no new mm_* extensions; uses the established capability registry pattern; passes its own L4-style parity test).
**Acceptance.** Recorded sign-off in the decision log covering both modules end-to-end.

### L4-5 — Render-time performance review ⏳ PARTIAL (May 2026), full pass deferred until post-IM
**Status.** First pass shipped in reg-bb-engine build-077: per-render ThreadLocal caches on `MetaModelDao` (five hot-path lookups: `findCatalogByCode`, `listFieldsForScreen`, `findDeterminantByCode`, `listRegistrationsForService`, `listRequiredDocsForService`) and on `RoutingEvaluator` (L1 dedup keyed on determinantCode + ctx-data-hash). Ref-counted scopes opened by `MetaWizardElement` / `MetaScreenElement.renderTemplate`. **Result: 15–25s → ~8s** (roughly 2-3× improvement, captured by Aare on first re-test). The cache pass eliminated the redundant DAO + eval cost; the remaining ~8s is structural — eager all-tabs rendering plus driver-side latency compounding across 6 tab synthesises. **Full pass deferred** to a focused round after IM lands, so the work covers both modules' render paths and we have a richer baseline to profile against.
**Original observation.** Citizen-facing: opening a fresh subsidy application takes **15–25 seconds** before the wizard becomes interactive. That's far outside any reasonable UX target (~2s max) and will be especially bad in Lesotho field conditions — older computers in extension offices, mobile devices in villages, weak GSM data. Citizens will abandon mid-load.
**Suspects to investigate (in priority order):**
1. **Kernel render path** — `MetaScreenElement.synthesiseChildren` walks every `mm_field` row for the screen, hits `mm_catalog` for each SelectBox to load options, hits `mm_determinant` for each visibility/required toggle (§6.4). For a wizard with 6 tabs × ~8 fields/tab × catalog lookups, that's a lot of small DAO calls. Potential N+1; need to profile.
2. **Per-render Determinant evaluation** — every visibility rule fires on every render. The L2 Caffeine cache (slice 1B-d) helps for repeat evaluations within TTL, but cold-start is uncached.
3. **Hibernate cold cache** — first-time-after-deploy renders are dramatically slower than subsequent ones because the per-form ORM mapping rebuilds. The "warm cache" steady-state is what citizens see most of the time, but operators see the cold path on every redeploy.
4. **MultiPagedForm rendering all tabs upfront** — the wizard renders all 6 tabs in the initial response (display:hidden on inactive ones), so the citizen pays render cost for every tab even on tab 1.
5. **Client-side bundle weight** — Joget DX's stock CSS/JS payload is heavy by 2026 standards. Mostly out of our control, but worth measuring against the field-condition baseline.
**Ships.**
- Server-side timing instrumentation: log per-phase timings (DAO read of mm_screen, mm_field walk, catalog resolution, determinant eval, render assembly) so we can locate the actual bottleneck instead of guessing.
- Field-condition probe: run the catalogue + wizard render through Lighthouse / WebPageTest under throttled 3G + low-CPU profile. Establish the baseline.
- Targeted fix: typically one of (a) batch DAO reads per screen render, (b) per-screen materialised-view cache for `mm_screen → fields → catalog options` joined view, (c) defer non-active-tab rendering until tab transition.
**Effort.** 2–3 days investigation + 3–5 days targeted fix.
**Depends on.** Nothing — can run in parallel with Budget Engine or IM work.
**Acceptance.** Cold-render TTI ≤ 3s on a low-end Android device on simulated 1 Mbps connection. Warm-render TTI ≤ 1s. Documented profile-and-fix in `docs/architecture/notes/render-performance.md`.

### Layer 4 total effort
~1–1.5 weeks.

---

## Gate to IM Phase 3

IM module work begins **when these conditions are met**:

1. ✅ Layer 1 (all six kernel widgets) shipped + verified.
2. ✅ Layer 2 (capability registry + single-window catalogue + bot_pull) shipped + verified.
3. ⏳ Budget Engine (L3-1) live with the four 2025 envelopes seeded; operator decision-time hint visible; reconciliation report green.
4. ✅ 20/20 parity test passes through generated UX (L4-3).
5. ⏳ Render performance ≤ 3s cold / ≤ 1s warm on field-condition devices (L4-5). Slow citizen-facing renders will block real adoption regardless of how clean the architecture is.

Conditions 1+2 prove **kernel and framework are mature enough** for a second domain to land on. Condition 3 closes the budget gap. Condition 4 is the empirical convergence proof. Condition 5 is the deployment-reality check — without it, the cleanest architecture is unusable in Lesotho field conditions.

**Note on L4-4 (operator UX sign-off).** Originally a Phase 1 closure item, now moved to post-IM. Operators experience the platform as one tool spanning subsidy + IM; signing off on subsidy-only ergonomics produces a false reading that will be revisited the moment IM ships. The qualitative gate becomes meaningful only after IM Phase 3 lands and the operator can drive end-to-end (citizen application → eligibility → IM voucher / distribution → reconciliation) in one sitting.

---

## Standing principles during the backlog

These apply throughout — adopted from the convergence framework's discipline:

- **No new mm_* extensions** without multi-service evidence per the audit. Six are pre-authorised; no others land without re-opening the audit.
- **No raw SQL on Joget metadata or form data.** Every write goes through the DAO. (CLAUDE.md hard rule.)
- **No XPDL generation.** Sysadmin authors workflows in Joget's native designer; RegBB dispatches by id (ADR-011).
- **Configuration over code.** New programmes = mm_* rows. New rules = mm_determinant rows. New actions = mm_action rows.
- **"If it's policy, it's a rule."** When a code path branches on a policy decision, reviewer asks "is this a rule?" If yes, it's mm_determinant. (Per `docs/architecture/policy-to-rules-migration.md` §7.)
- **Wrapping a stock Joget element — read the source first.** Every L1 widget item below (smart_search, signature, gis_polygon, repeating_group, file_upload form-kind, plus any future widget) follows the discipline in CLAUDE.md "Wrapping a stock Joget element — read the source FIRST." Three reads per case: the element's class, its binder if any, and `FormUtil.parseBinderFromJsonObject`. The L1-1 cascading_select reference (`attachCascadingOptionsBinder` in `MetaScreenElement`) is the canonical shape — copy it when wiring any binder-backed widget. Don't trust prose documentation about element configuration; the source is the contract.
- **Build-numbered plugin JARs.** Every code change bumps the build number; description visible in Manage Plugins.
- **Decisions as ADRs.** Architectural decisions get an ADR; tactical ones get a D-numbered entry in `docs/architecture/decision-log.md`.

---

## How to use this document

When picking the next thing to work on, walk top-to-bottom. Items in the same layer can be parallelised across E1 / E2 tracks. Items across layers are mostly serial because of the dependencies named.

When an item ships, mark it ✅ in this file (delete it as a future cleanup). When new items emerge, slot them into the right layer with severity rationale.

When the gate's four conditions are all ✅, IM Phase 3 starts.
