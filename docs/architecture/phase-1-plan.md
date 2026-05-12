# Phase 1 plan — Engine + 4-programme empirical convergence test

| | |
|---|---|
| Status | **Revision 2 — sketch for review** (supersedes revision 1 of 2026-04-28) |
| Date | 2026-04-28 |
| Phase | Stage 1 / Phase 1 of `migration-plan.md` (Path B — all 4 programmes in Phase 1) |
| Owner | Farmers Portal architecture team |
| Authority | Aare decides scope, retirement gates, prerequisite resolutions |
| What this is | Phase-level planning: deliverables, workstreams, sequencing, milestones, prerequisites, exit criterion. **Not design** — schema shapes, class structures, AST serialisation specifics happen in Week 1's design pass. |

> **Revision note.** Revision 1 sized Phase 1 as one fixture programme with the four-programme convergence test deferred to Phase 2. After screening all 4 dev-environment programmes (`PRG-2025-001` through `PRG-2025-004`), the decision was to fold the convergence test into Phase 1 (Path B). Phase 1 expands accordingly: SQL-path evaluator wired in Phase 1, all 4 programmes authored in Phase 1, the four-programme test becomes Phase 1's exit criterion. Phase 2 shrinks correspondingly to cutover prep only. The three open questions surfaced by the screening (target-group priority weights, prog003's scoring, prog004's HOUSEHOLD-level data sourcing) become **blocking Day-1 inputs** rather than Phase 2 problems.

---

## 1. Goal restatement

`reg-bb-engine` renders the application wizard for **all 4 subsidy programmes** dynamically from `mm_*` rows. **Citizens reach the wizard via a generated catalogue userview** (per D17); they navigate Discovery → Authentication → Guide → Form → Documents → Review → Submit → Confirmation. Identity resolution from National-ID auto-fills applicant fields. Eligibility evaluation is authoritative — the SQL-path evaluator resolves `$registry.farmers.*` and `$registry.parcels.*` references against legacy tables. **Operators reach submitted applications via a generated inbox userview** and inspect them through `MetaScreenElement` in read-only mode. **Five test farmer profiles × 4 programmes = 20 application scenarios all produce expected outcomes through the generated UX.** No farmer/parcel UI is touched. No legacy plugin is retired.

Effort budget per migration plan rev2 with D17's scope expansion: **~30 person-weeks ≈ 12 calendar weeks at 2 FTE** (was 26).

---

## 2. Deliverables — what concretely ships

Eight deliverables (D8 added per D17 for end-to-end UX validation at the phase gate):

### D1. `reg-bb-engine` plugin

A new OSGi bundle at `plugins/reg-bb-engine/`:
- `DeterminantEvaluator` interface per spec §8.1.
- **Both** evaluators wired in Phase 1 (was: fast-path only):
  - Fast-path tree-walker (closed twenty subset, in-memory, over `$applicant.*` / `$constant.*` / `$service.*` / `$registration.*`).
  - **SQL-path evaluator** delegating to `joget-rules-api`'s `RuleScriptCompiler` (was: stub returning `error`).
- AST analysis routing per spec §4.3.0 — fast-path-eligible if AST contains only closed-twenty operators *and* references only `$applicant.*` / `$constant.*` (no `$registry.*`, no aggregation). SQL-path otherwise. Routing decision computed at publish, cached on the AST.
- L1/L2/L3 cache integration with `evaluator='fast'|'sql'` discriminator on `reg_bb_eval_audit` rows.
- `EvalContext` carrying `applicationId`, `serviceId`, `serviceVersion`, `selectedRegistrationIds`, `data`, `currentUsername`, `correlationId`.
- `MetaScreenElement` form element class — walks `mm_screen` + `mm_field` rows, supports widgets `text|number|date|select|radio|checkbox|textarea|file_upload|signature` plus `repeating_group` extension.
- `MetaModelDao` for reading `mm_*` rows via Joget `FormDataDao`.
- `Activator.java`, build counter, `repack.sh`, OSGi descriptor.

**Concrete output:** deployable `.jar` named `reg-bb-engine-1.0-SNAPSHOT.jar`, deployed in farmersPortal Joget instance.

### D2. `reg-bb-publisher` plugin

A new OSGi bundle at `plugins/reg-bb-publisher/`:
- "Publish" custom userview menu action on `mm_service` admin record.
- Validation: parse Determinants against DSL grammar (delegates to `joget-rules-api`'s parser); walk refs to confirm they resolve; walk action targets; classify each Determinant fast-path or SQL-path per spec §4.3.4.
- Performance hygiene check: SQL-path Determinants in `field`/`screen` scopes trigger a publish-time warning unless `mm_determinant.allowSlowPath = true`.
- Status flip from `draft` to `published` via `FormDataDao.saveOrUpdate`.
- Engine cache invalidation hook (`evaluator.invalidateService(serviceId)`).
- Userview generation deferred to Phase 3.

### D3. `mm_*` schema deployment

Twelve `mm_*` form definitions plus Lesotho-instance extensions, deployed via `form-creator-api`:
- 12 base entities: `mm_institution`, `mm_service`, `mm_registration`, `mm_role`, `mm_screen`, `mm_field`, `mm_action`, `mm_catalog`, `mm_required_doc`, `mm_fee`, `mm_determinant`, `mm_role_screen`.
- **Six Lesotho-instance extensions** (was: five; `mm_registration.evaluationStrategy` + scoring fields added per Decision D6):
  1. `mm_field.widget=repeating_group` enum value + child-screen reference.
  2. `mm_benefit` form definition (sibling of `mm_fee`, design Q from §9).
  3. `mm_registration.attributesJson` — programme-level metadata (programmeType, seasonCode, campaignType, legalBasis, fundingSource). Per D16 — varies per registration, not per service.
  4. `mm_required_doc.captureFieldsJson` — per-document capture fields.
  5. `mm_registration.acceptanceWindowFrom`/`mm_registration.acceptanceWindowTo` — date-range visibility gate per D16. The catalogue exposes only registrations whose window covers `now()`.
  6. **Scoring** (per decision-log D6): `mm_registration.evaluationStrategy ∈ {all_must_pass, score_based}` (default `all_must_pass`); `mm_registration.minimumScore` and `passingThreshold` (numbers, default 0); `mm_determinant.score` (number, default 0); `mm_determinant.ruleType ∈ {inclusion, exclusion, priority, bonus}`. Engine evaluator returns structured outcome `{mandatoryPassed, totalScore, passingThreshold, minimumScore}` for `score_based` registrations. Used by prog003 actively; configured (but unused) on prog001 and prog002.
- 13 admin CrudMenus (one per `mm_*` entity including `mm_benefit`) in a "Meta-model admin" userview.

### D4. IM-connector capability configuration

The connector reads from legacy farmer/parcel tables via JDBC (no postgres-postgres transit; same database instance). Read-only.

Capabilities for Phase 1's 4-programme coverage:
- **`farmers.byNid`** — given NID, returns farmer record fields. Joins `app_fd_farms_registry` ↔ `app_fd_farm_location` ↔ `app_fd_farmerAgriculture` ↔ `app_fd_farmerIncome` ↔ `app_fd_farmerHousehold`. Returns: `farmerCode`, `firstName`, `lastName`, `gender`, `mobileNumber`, `districtCode`, `villageName`, `nationalId`, `isActive`, `gainfulEmployment`, `governmentEmployed`, `creditDefault`, `cooperativeMember`.
- **`farmers.parcels.summary`** — given NID, returns aggregated parcel data: `totalParcelHectares`, `parcelCount`, `hasGisPolygon`, `gisVerified` (T if all parcels have polygons), `priorSeasonCereal` (T if any parcel grew cereal previously), `slopePercentMax` (max slope across parcels). Joins `app_fd_farms_registry` ↔ `app_fd_parcelRegistration` ↔ `app_fd_parcelGeometry` ↔ `app_fd_parcelClassification` ↔ `app_fd_parcelCrops`/`cropDetailForm`.
- **`households.vulnerability`** — given NID, returns household vulnerability indicators. Source: derived from `farmerHousehold` + `farmerIncomePrograms` data per a documented computation rule. Returns: `foodSecurityScore`, `vulnerabilityFlag` (CSV from {ELDERLY, DISABILITY, HIV, ORPHAN_HEAD}), `vulnerabilityScore` (0-100), `concurrentSubsidy` (T if active subsidy enrollment exists).
- **`programmes.applicationsByApplicant`** — given NID, returns list of `(serviceCode, registrationCode, season, status, submittedAt)` for that applicant's prior subsidy applications. Used to evaluate `concurrentSubsidy` and similar exclusion rules. Read from `app_fd_spApplication` (legacy table; survives Phase 3 cutover until cleanup).

**Concrete output:** capability rows in `app_fd_reg_bb_im_capability` plus documented adapter classes wiring the JDBC queries.

### D5. Four-programme content in `mm_*`

All 4 programmes (`PRG-2025-001` through `PRG-2025-004`) authored in `mm_*` rows via the admin CrudMenus:

- **1 `mm_service`** ("Farmers Subsidy") with `attributesJson` carrying nothing programme-specific (programme-specific facts go on `mm_registration` rows).
- **4 `mm_registration` rows** — one per programme. Each carries:
  - `attributesJson` for programme-specific metadata: programmeType, seasonCode, campaignType, legalBasis, fundingSource, distribModelCode, allocBasisCode, benefitModelCode, allowedPaymentMethods.
  - `acceptanceWindow{from,to}` from the legacy `applicationStartDate`/`applicationEndDate`.
  - `applicabilityDeterminantId` referencing a Determinant that gates "this Registration is shown to citizens whose `$registry.farmers.<NID>.districtCode in <programme's targetDistricts>`".
- **6 `mm_screen` rows** for the application wizard (Guide, Applicant, Eligibility, Benefits, Documents, Confirmation per spec §4.1.5). Defined once on the service; merged per-Registration content at runtime.
- **~30–40 `mm_field` rows** for wizard structure plus 3 `repeating_group` widgets (eligibility checks, benefit requests, documents).
- **~18 `mm_determinant` rows** total across the 4 programmes (4 + 4 + 5 + 5 from the screening). Mix of fast-path (self-attestation: `$applicant.X eq Y`) and SQL-path (registry-touching: `$registry.farmers.<NID>.totalParcelHectares lte 2`).
- **~10 `mm_benefit` rows** total across the 4 programmes (3 + 4 + 1 + 2 from the screening).
- **~11 `mm_required_doc` rows** total (3 + 4 + 3 + 1 from the screening). Documents shared across programmes (e.g. `NATIONAL_ID`) deduplicate at runtime per spec §6.1.6 — same `code`, the strictest requirement wins.
- **1 `mm_action` row** kind=`bot_pull`, trigger=`field_change` on `national_id` at the Applicant tab, calls `farmers.byNid` capability.

Authoring proceeds programme-by-programme in the order: prog001 (vanilla), prog004 (NATIONAL + ALL_MUST_PASS), prog002 (GIS-touching), prog003 (scoring discipline test).

### D6. `joget-rule-editor` integration with `mm_determinant`

The DSL editor's UI configured to author `mm_determinant.ruleJson`. Editor produces JSON-AST for fast-path-eligible rules and DSL source for SQL-path rules; routing decision automatic per ADR-001r2. Editor displays the chosen routing in operator UI (informational hint).

### D7. Four-programme test fixture pack — Phase 1 exit criterion

Five representative test farmer profiles drawn from `app_fd_farms_registry` (or authored if not enough exist), each with NID + farmer record + parcel records + household composition. The matrix of 5 farmers × 4 programmes = 20 application scenarios.

| Farmer | Profile shape | Used to test |
|---|---|---|
| F1 | Lowlands smallholder, parcel 1.5 ha, cooperative member, no prior subsidy | prog001 PASS + prog003 partial (cooperative bonus) + prog002 FAIL (district mismatch) |
| F2 | Mountain smallholder, parcel 0.8 ha, GIS-verified, slope 6% | prog002 PASS + prog001 FAIL (district mismatch) |
| F3 | Vulnerable elderly household, food-insecure, NID verified | prog004 PASS + prog001 FAIL (district may match) |
| F4 | Multi-parcel farmer, total area 2.5 ha | prog001 FAIL (boundary), prog002 PASS, prog003 partial |
| F5 | Maseru smallholder with concurrent subsidy enrollment | prog001 FAIL (concurrent) + prog003 (concurrent exclusion if applies) |

Per scenario, ground-truth outcome is documented before authoring (Aare reviews and confirms). The four-programme test passes if all 20 scenarios match expectation.

A divergence whose root cause is "the meta-model cannot express what the programme needs" reopens the audit per framework §7. The plan explicitly does not paper over.

### D8. Generated userviews + operator read-only inspection (per D17)

End-to-end UX so the Phase 1 gate is real. Two surfaces, both generated by `reg-bb-publisher`, both readable from the same Joget instance:

**Citizen surface:**
- One citizen userview menu per published `mm_service` (Phase 1: one menu, "Farmers Subsidy"). Generated when the service publishes.
- Catalogue page: rendered by `MetaScreenElement` from a synthesized `mm_screen` of `kind='guide'` that walks the service's `mm_registration` rows. For each registration, evaluates `applicabilityDeterminant` against the current applicant — applicable registrations show as selectable; non-applicable ones show with the disqualification reason.
- Click-through to the wizard. The 6 `mm_screen` rows (Applicant, Eligibility, Benefits, Documents, Declaration, Confirmation) render in sequence per spec §6.1.
- The citizen's path is governed by `app_application.selectedRegistrationIds`; per-registration content (Determinants, Benefits, RequiredDocs) is merged at runtime per spec §6.1.6.

**Operator surface (inspection-only):**
- One operator userview category per published `mm_service`. Generated.
- Inbox menu (native Joget `Inbox`-style listing of submitted applications, scoped to the service).
- Applications-list datalist scoped to the service. Generated using the `joget-datalist-gen` skill pattern (`AdvancedFormRowDataListBinder` over `app_application` with `c_serviceCode` filter); columns include `applicationId`, `applicantNid`, `selectedRegistrations`, `eligibilityOutcome.disposition`, `eligibilityOutcome.totalScore`, `submittedAt`.
- Open-application surface uses `MetaScreenElement` with `readonly=true` on every element — operators see the same wizard the citizen filled in, all fields readable, all uploaded documents previewable, the `eligibilityOutcome` rendered alongside.
- **No decision affordance in Phase 1.** Operators inspect; approval flows through admin direct-write on `app_application.dataJson` for now. Phase 3 brings `MetaReviewElement` with full decision affordances + XPDL workflows.

**Concrete output:** new code in `reg-bb-publisher` for userview JSON generation (citizen menu + catalogue page + operator inbox + applications-list datalist); read-only mode flag wired through `MetaScreenElement`'s element-emission path; Joget userview definitions deployed via `form-creator-api` (or the publisher writes them directly through `UserviewDefinitionDao`).

**Engine cache:** the catalogue page evaluates `applicabilityDeterminant` for each registration on each render; cached per applicant in L2 (`(applicantUserId, serviceVersion, registryFetchEpoch)`).

---

## 3. Workstream allocation across 2 FTE

Same E1 (engine) / E2 (schema + content + connector) split. E2's load grows under Path B; D8 lands on E1's track so the engine engineer owns the publisher + userview-generation chain end-to-end:

| Workstream | Owner | Deliverables |
|---|---|---|
| Engine + Publisher + Userviews | E1 | D1 (engine + SQL-path integration), D2 (publisher), D6 (editor), **D8 (generated userviews + read-only mode)** |
| Schema + Content + Connector | E2 | D3 (schema), D4 (4 IM capabilities), D5 (4 programmes' content) |
| Joint | Both | D7 (5 farmers × 4 programmes through generated UX), Weeks 10–11 parity testing |

Cross-track coordination points: Week 1 design pass, Week 4 mid-point check-in, Week 7 first end-to-end against prog001 via generated catalogue, Week 8 D8 generated UX in operator hands, Week 9 four-programme test starts, Weeks 10–11 joint full-time parity.

---

## 4. Dependencies and sequencing

```
Week 1: Joint design pass (resolve 3 blocking decisions + 4 design questions)
                            │
        ┌───────────────────┴───────────────────┐
        ▼                                       ▼
   E1: D1 engine                          E2: D3 mm_* schema
        │                                       │
        ▼                                       ▼
   E1: SQL-path                           E2: D4 IM capabilities
        evaluator                                (4 of them)
        │                                       │
        ▼                                       ▼
   E1: D2 publisher                       E2: D5 prog001 content
   (validation+routing)                         │
        │                                       ▼
        ▼                                  E2: D5 prog004 content
   E1: D6 editor                                 │
        │                                       ▼
        ▼                                  E2: D5 prog002 content
   E1: repeating_group                           │
        widget rendering                        ▼
        │                                  E2: D5 prog003 content
        ▼                                       │
   E1: D8 userview gen +                        │
        read-only mode                          │
        │                                       │
        └───────────────────┬───────────────────┘
                            ▼
            Joint: D7 four-programme test (Weeks 10–11, via generated UX)
```

**Key dependencies:**
- D1 engine SQL-path → D5 programme content authoring of SQL-path Determinants. Until SQL-path works, SQL-path Determinants cannot be tested.
- D4 IM capabilities → D5 content authoring. Each programme's `$registry.*` references depend on the corresponding capabilities being live.
- D5 ordering matters: prog001 first (cleanest), then prog004 (different shape), then prog002 (GIS), then prog003 (scoring discipline).
- **D2 publisher + D5 prog001 content → D8 userview generation.** First userview generation runs against prog001 once it publishes; iterative on the remaining 3 programmes.
- D8 → D7 four-programme test. Test runs through the generated UX (citizen catalogue → wizard; operator inbox → read-only review), not through admin URLs.

**Critical path:** Week 1 design → D3 schema deploy + D1 engine skeleton → D4 IM capabilities + D1 SQL-path → D5 prog001 content (Week 5) → D5 remaining programmes + D8 userview gen (Weeks 6-9) → D7 four-programme test (Weeks 10-11).

---

## 5. Week-by-week milestones (12 calendar weeks)

### Week 1 — Design pass + scaffolding

**Joint (full Monday-Wednesday):** Decisions D5–D17 already resolved (see §9). Remaining: confirm the 5 fixture NIDs, schedule Week-1 schema review with Aare. Document the userview-generation acceptance criteria (read-only mode behaviour, catalogue page rendering, inbox columns).

**E1 (rest of week):** `plugins/reg-bb-engine/` scaffolding: `pom.xml`, `Activator.java`, `Build.java`, `repack.sh`. `DeterminantEvaluator` interface + `EvalContext`. Stub for `MetaScreenElement.readonly` mode flag (implementation in Week 8).

**E2 (rest of week):** 13 `mm_*` form definitions + scoring + acceptanceWindow + attributesJson placement designed (per `phase-1-schemas.md`); first 2 forms deployed to verify pipeline.

### Week 2 — Foundations

**E1:** `MetaScreenElement` renders simple text fields. `MetaModelDao` reads from `app_fd_*`.
**E2:** All 13 `mm_*` forms deployed. 13 admin CrudMenus deployed.

### Week 3 — Fast-path evaluator + first IM capability

**E1:** Fast-path `DeterminantEvaluator` over the closed twenty subset. L1 cache. Visibility/required Determinants on simple cases.
**E2:** `farmers.byNid` capability adapter + tests against 5 fixture NIDs.

### Week 4 — SQL-path evaluator + second + third capability

**E1 (the big one):** Wire SQL-path evaluator. AST analysis routing. Delegation to `joget-rules-api`'s `RuleScriptCompiler`. L2/L3 cache integration with `evaluator='fast'|'sql'` audit discriminator.
**E2:** `farmers.parcels.summary` + `programmes.applicationsByApplicant` capability adapters.

**Mid-point check-in (joint, ~1.5 hours):** Engine track integration ready? Schema track ready for content authoring? Performance acceptable on small load? Design decisions still holding?

### Week 5 — Publisher + prog001 content

**E1:** D2 publisher with validation + routing classification + status flip + cache invalidation.
**E2:** prog001 fully authored in `mm_*` (4 criteria, 3 benefits, 3 docs). Includes the bot_pull mm_action. First end-to-end render via admin URL — citizen NID → applicant fields auto-fill → eligibility evaluation runs.

### Week 6 — `households.vulnerability` + prog004 content + start D8

**E1:** `repeating_group` widget rendering for seeded grids. **Start D8 (citizen userview generation):** publisher emits citizen userview menu + catalogue page on `mm_service` publish. First catalogue page render against prog001's published `mm_service`.
**E2:** `households.vulnerability` capability adapter. prog004 authored — the NATIONAL/multi-channel/HOUSEHOLD shape.

### Week 7 — prog002 content + editor integration + D8 citizen flow

**E1:** D6 editor integration with `mm_determinant.ruleJson`. **D8 citizen flow finalised:** catalogue → wizard navigation works end-to-end via the generated userview; prog001 + prog004 reachable.
**E2:** prog002 authored — the GIS-touching shape. Validates `farmers.parcels.summary.gisVerified` resolves correctly.

### Week 8 — prog003 content + D8 operator surface + read-only mode

**E1:** **D8 operator surface:** publisher generates operator userview category + inbox + applications-list datalist. `MetaScreenElement.readonly=true` flag wired through every emitted element. Operator can open a submitted application and see all data + `eligibilityOutcome` rendered.
**E2:** prog003 authored — exercises scoring per D6.

### Week 9 — Test setup + first parity dry-run

**E1:** Submit-time eligibility evaluation pipeline polished. Audit shape stabilised.
**E2:** Five fixture farmers identified or authored. Ground-truth matrix (5 × 4 = 20 expected outcomes) confirmed with Aare. **First parity dry-run:** at least 3 fixtures × 4 programmes via the generated UX — surfacing UX defects + Determinant defects ahead of full Week 10–11 testing.

### Week 10 — Four-programme test execution

**Joint, full-time:**
- Submit 20 applications (5 farmers × 4 programmes) **via the generated citizen catalogue + wizard** (not admin URL). Operators inspect each via the generated inbox. Compare outcomes against ground-truth.
- Defects logged across three categories: capability data shape mismatches, Determinant translation errors, **UX defects (catalogue rendering, wizard navigation, read-only mode glitches)**.
- Goal: by Friday, 16+ of 20 scenarios pass end-to-end; remaining 4 have known-cause-and-fix.

### Week 11 — Defects + UX polish

**Joint:**
- Fix Week-10 defects across all three categories.
- UX polish on D8 surfaces: catalogue page typography, wizard step labels, inbox column ordering, read-only field rendering.
- Re-run all 20 scenarios end-to-end; goal 20/20 pass through generated UX.

### Week 12 — Retrospective + go/no-go

**Joint:**
- Final 20/20 confirmation.
- Phase 1 retrospective: what went per plan, what slipped, drift-mode-1 / drift-mode-2 incidents.
- **Go/no-go review with Aare:** does the generated UX feel like the converged stack you want? Does the four-programme test give confidence the meta-model holds? Decision logged.
- Decision log updated; Phase 2 design week scheduled.

---

## 6. Inputs / prerequisites (now with 3 blocking decisions)

| Prerequisite | Owner | Resolved by |
|---|---|---|
| Phase 0 alignment confirmed (framework rev2, ADR rev2, spec edits, plan rev2 accepted) | Aare | Phase 0 close |
| `docs/architecture/decision-log.md` created with Phase 0 decisions | Either engineer | Day 0 |
| 5 fixture farmer NIDs identified (or new records authored) covering the matrix from §2 D7 | E2 + Aare | End of Week 1 |
| **Decision 1: Target-group priority weights** | ✅ **Resolved 2026-04-28** — drop. Target groups become Determinants; weights are programme-administration metadata outside `mm_*`. See decision-log D5. | — |
| **Decision 2: Scoring (prog003)** | ✅ **Resolved 2026-04-28** — adopt as 6th Lesotho-instance extension. Three-of-four programmes have SCORE_BASED authored (multi-service evidence holds). Shape: `mm_registration.evaluationStrategy` + scoring fields. See decision-log D6. | — |
| **Decision 3: HOUSEHOLD vulnerability data (prog004)** | ✅ **Resolved 2026-04-28** — design `households.vulnerability` capability computing scores from farmer registry data. See decision-log D7. | — |
| Decisions on the 7 design questions (§9) — Q-mm_benefit shape, Q-repeating_group persistence, etc. | Aare + engineers | End of Week 1 |
| Maven local repo cache primed for `joget-rules-api`, `rules-grammar` references | E1 | Day 0 |
| Test database with at least 50 farmer records and 100 parcel records | E2 | Day 0 |

The three blocking decisions surfaced by the programme screening were resolved on 2026-04-28 — see `docs/architecture/decision-log.md` entries D5–D7. They appear in this table for traceability.

---

## 7. Risk-front-loading

### 7.1 The three Day-1 decisions (resolved 2026-04-28; tracking only)

The three decisions that were Day-1 blockers are resolved per decision-log D5 (target-group weights dropped), D6 (scoring adopted as 6th extension), D7 (`households.vulnerability` capability designed). Risk eliminated; tracking retained for traceability.

### 7.2 `mm_benefit` design (Week 1 design pass)

Heaviest single addition. Affects all 4 programmes (3 + 4 + 1 + 2 = 10 benefit rows). A reshape after content authoring is expensive. Design carefully against vehicle-registration mental model and against prog003's voucher / prog004's tiered-cash shapes; document rejected alternatives.

### 7.3 IM-connector capability stability (Weeks 3–6)

Capability shapes registered in Phase 1 must survive Stage 2's eventual farmer/parcel migration without breaking Determinants. Capabilities exposed against the *logical model* of farmer/parcel registration, not against legacy table structure. Reviewed at Week 4 mid-point check-in.

### 7.4 SQL-path evaluator performance under realistic load (Week 4)

First end-to-end test of compile-to-SQL evaluation on `$registry.farmers.parcels.summary` aggregation. May reveal cache + connector + compiler interaction issues. Mitigation: small load test with 50 representative applicants in Week 4; if performance is unacceptable, decide whether to push aggregation into denormalised registry columns (the `derived-snapshot-runtime` pattern).

### 7.5 The four-programme test as audit-validation (Weeks 10–11)

This is the empirical convergence test. A divergence's root cause may be a content bug (Determinant mistranslated), a connector gap (capability missing a field), an extension gap (`mm_*` shape can't express something), or genuine evidence the audit's verdict was wrong. The framework's posture (§7) requires re-opening the audit if the third or fourth reading fits — the discipline is to surface that reading rather than papering over.

### 7.6 Generated UX usability (Weeks 6–11) — added per D17

The qualitative half of the Phase 1 gate. Two risks: (a) the catalogue page renders correctly but feels unusable to non-engineer users — column layout, filter affordances, language; (b) the read-only operator inspection surface is technically correct but the operator can't find the eligibility outcome or document uploads quickly. Mitigation: Week 9's first parity dry-run is half a UX dry-run — surface UX defects early, fix during Weeks 9–11 rather than discovering at the Week-12 gate. Aare's qualitative review at Week 12 has a real chance to push back; the gate respects that.

---

## 8. Exit criterion — Phase 1 done

**Two conditions, both required:**

1. **Four-programme parity test passes through generated UX.** All 20 scenarios (5 farmer fixtures × 4 programmes) produce outcomes matching the pre-specified ground truth. The test is run end-to-end via the generated citizen catalogue and wizard (D8 citizen surface) — not via admin URL. Operators inspect each submitted application via the generated inbox (D8 operator surface) and confirm the structured `eligibilityOutcome` matches expectation.

2. **Go/no-go review confirms convergent UX.** Aare reviews the generated UX and confirms two things: (a) "yes, citizens can use this" — the catalogue and wizard feel acceptable for end users, not just engineers; (b) "yes, operators can review this" — the inbox and read-only inspection surfaces give operators what they need to handle applications. This is the qualitative half of the gate; the four-programme test is the quantitative half.

Plus:
- D1–D8 delivered and reviewed.
- Decision log carries entries for the Phase 1 retrospective and any drift incidents.
- No outstanding "drift mode 1" findings. (Drift mode 2 candidates recorded for future evidence-watch.)
- Aare has reviewed and authorised Phase 2 to start.

If a divergence in the four-programme test is rooted in a meta-model gap, **Phase 1 does not exit** — divergence investigated, audit consulted, extension added (with multi-service evidence) or divergence accepted as scope choice.

If the qualitative review fails — generated UX too rough, operator workflow not actually usable — Phase 1 also does not exit. UX defects are in scope for Week 11 polish; if Week 12's review still fails, the phase extends rather than the gate softens.

---

## 9. Design questions — all resolved 2026-04-28

All 8 design questions (Q-A through Q-H) have been answered prior to Phase 1 start. See `docs/architecture/phase-1-design-answers.md` for the proposed answers and `docs/architecture/decision-log.md` entries D8 through D15 for the recorded decisions.

| # | Question | Resolution | Decision-log entry |
|---|---|---|---|
| Q-A | `mm_benefit` shape | Separate entity (sibling of `mm_fee`) | D8 |
| Q-B | `repeating_group` widget persistence | Inline JSON in `dataJson` | D9 |
| Q-C | IM-connector capability registry | `app_fd_reg_bb_im_capability` table per spec §3.2 | D10 |
| Q-D | `bot_pull` field-mapping shape | Reuse existing shape, nested in `mm_action.configJson.fieldMappings` | D11 |
| Q-E | Editor's serialisation choice | Automatic per AST analysis; no operator override in Phase 1 | D12 |
| Q-F | Admin CrudMenu UX | 13 separate CrudMenus in Phase 1; Service Builder is Phase 3 | D13 |
| Q-G | AST routing edge cases | Conservative — any `$registry.*` reference routes the rule to SQL path | D14 |
| Q-H | `pending_review` state under scoring | No new status enum value; structured outcome on `dataJson.eligibilityOutcome` | D15 |

Phase 1 Week 1 design pass therefore narrows to: producing the schema docs (mm_benefit DDL, repeating_group widget JSON shape, capability row contracts, mm_action.configJson schema for bot_pull) and confirming any details the answers leave implicit. No new design decisions block Phase 1 start.

---

## 10. What Phase 1 explicitly does NOT do

- **Does not retire any plugin.** All seven existing plugins continue running. Legacy `spApplication` continues accepting applications.
- **Does not author XPDL workflows.** Phase 1 applications submit to a draft `app_application` row; no workflow runs. Operator approval is direct-write on `app_application.dataJson`.
- **Does not touch farmer registration UI or parcel registration UI.**
- **Does not deploy `MetaReviewElement`.** Operator review uses `MetaScreenElement` in read-only mode (per D8); the full role-scoped section rendering with decision affordances is Phase 3.
- **Does not deploy `mm_role_screen.sectionsJson`** authoring. Phase 3.
- **Does not wire the submission backbone** (`wf-activator → DocSubmitter → ProcessingServer`). Phase 3.
- **Does not deploy decision affordances** (approve / reject / return-for-correction). Phase 3.
- **Does not deploy `mm_field.optionsFilterJson`** (cascading select) — none of the 4 programmes need it within their own Determinants. Lands when needed.

What previously was on this list and **now IS in Phase 1**:
- SQL-path evaluator (was Phase 2; now Phase 1 because all 4 programmes need it).
- All 4 programmes' content authoring (was: 1 programme).
- The four-programme test (was Phase 2's exit criterion; now Phase 1's).
- `mm_registration.acceptanceWindow` extension (was Phase 2; now Phase 1 because all programmes use date windows). Placement per D16.
- Three IM capabilities beyond `farmers.byNid`: `farmers.parcels.summary`, `households.vulnerability`, `programmes.applicationsByApplicant`.
- **Citizen userview generation** (catalogue + wizard menu) — was Phase 3, now Phase 1 per D17.
- **Operator userview generation** (inbox + applications-list datalist) — was Phase 3, now Phase 1 per D17.
- **`MetaScreenElement` read-only mode** for operator inspection — was Phase 3, now Phase 1 per D17.

---

*End of Phase 1 plan revision 2. Open for review and revision.*
