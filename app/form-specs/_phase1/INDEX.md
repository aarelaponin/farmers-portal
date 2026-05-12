# Phase 1 — Executable Spec Files (REVISED 2026-04-25 after gs-plugins inventory)

This folder contains the executable artefacts that implement Phase 1 of
the Subsidy Adjustment Plan. Each item maps to one deliverable in
`_02_Analysis/Phase1_Delivery_Plan_2026-04-25.md` (and its addenda).

## Pivot summary (this revision)

After inventorying `/Users/aarelaponin/IdeaProjects/rsr/gs-plugins`, the plan
was simplified considerably:

- **`joget-rules-api` + `joget-rule-editor` + `rules-grammar`** already implement
  a full DSL-based rules engine with SQL compilation. The original D1.G
  EligibilityEvaluator is **redundant** — replaced with a **thin runtime**
  that wraps the existing compiler.
- **The legacy `spEligField` / `spEligRule` / `spEligCriterionRow` /
  `spProgramEligibility` forms are obsolete.** Phase 1 migrates to
  `jreFieldDefinition` / `jreFieldScope` / `jreRuleset` (the JRE schema).
- **Reference rule library** is now three Rules Script `.rs` files
  (version-controlled in this folder), loaded via the JRE REST API rather
  than as criterion-row INSERTs.

**Effort impact:** Phase 1 drops from ~13 days to **~9 days** (saved ~4 days
on the EligibilityRuntime).

## Confirmed scope decisions (reaffirmed)

- Java plugins only (no BeanShell)
- Java package root: `global.govstack.*`
- Default rule strictness: scoring (the JRE supports SCORE on BONUS/PRIORITY rules + INCLUSION/EXCLUSION for hard requirements)
- Food security stored in two places (`farmerIncomePrograms.foodSecurityCode` + `spFarmerDerived.currentFoodSecurityStatus`)
- Cross-program checks: derive in `FarmerDerivedRefresh` → reference as JRE fields (`programs_last_2_seasons`, `total_benefits_lifetime`)
- Eligibility runtime: thin tool plugin layered over `joget-rules-api`

## Files in this folder

| ID | File | Type | What it does | Run with | Status |
|---|---|---|---|---|---|
| D1.A | `D1.A_seed_md51_categories.sql` | DDL/DML | Adds `FOOD_SECURITY` and `PROGRAM_HISTORY` to md51 | `psql` | unchanged |
| D1.B | `D1.B_farmerIncomePrograms.patch.yaml` | Form patch | Adds `foodSecurityCode` SelectBox | `joget-form-gen` | unchanged |
| D1.C | `D1.C_spFarmerDerived.patch.yaml` | Form patch | Adds 5 fields | `joget-form-gen` | unchanged |
| **D1.D** | `D1.D_seed_jreFieldDefinition.sql` | DDL/DML | Seeds `jreFieldScope.FARMER_APPLICATION` + 18 `jreFieldDefinition` rows | `psql` | **REPOINTED** (was spEligField) |
| D1.E | `D1.E_FarmerDerivedRefresh.plugin.spec.yaml` | Plugin spec | Java plugin to refresh `spFarmerDerived` | `joget-plugin-dev` | unchanged |
| D1.F | `../_workflows/refreshOneFarmer.spec.yaml` | Workflow spec | 1-activity workflow + scheduled task | `joget-workflow-gen` | unchanged |
| **D1.G** | `D1.G_EligibilityRuntime.plugin.spec.yaml` | Plugin spec | Thin Java runtime over joget-rules-api | `joget-plugin-dev` | **SHRUNK** from 5d to 1.5d |
| D1.H | `D1.H_seed_uat_progparticip.sql` | DML | UAT seed: 8 rows for 3 test farmers | `psql` | unchanged |
| **D1.I** | `D1.I_load_reference_rulesets.sh` + `rules/*.rs` | DSL files + REST loader | 3 reference rulesets in Rules Script DSL | `bash` (curl) | **REWRITTEN** as DSL files |

## Obsolete files (parked under `_legacy/`)

These were generated before the gs-plugins inventory and are kept for
reference only. Do not run.

- `_legacy/D1.D_seed_spEligField.sql.OBSOLETE` — used spEligField; replaced by jreFieldDefinition
- `_legacy/D1.G_EligibilityEvaluator.plugin.spec.yaml.OBSOLETE` — designed a duplicate evaluator; replaced by EligibilityRuntime
- `_legacy/D1.I_starter_rule_library.sql.OBSOLETE` — used criterion-row INSERTs; replaced by Rules Script DSL files

## Execution sequence

```
1.  D1.A    md51 categories                (psql)         ─┐
2.  D1.D    jreFieldScope + jreFieldDefn   (psql)          │ schemas first
3.  D1.B    farmerIncomePrograms patch     (form-gen)      │
4.  D1.C    spFarmerDerived patch          (form-gen)     ─┘
                                                ↓
5.  Deploy  joget-rules-api JAR             (Joget UI)     ← from gs-plugins (already built)
6.  Deploy  joget-rule-editor JAR           (Joget UI)     ← optional: only for rule authoring UI
                                                ↓
7.  D1.E    FarmerDerivedRefresh build+deploy
                                                ↓
8.  D1.G    EligibilityRuntime build+deploy
                                                ↓
9.  D1.F    refreshOneFarmer + scheduler    (workflow-gen + Joget Settings)
                                                ↓
10. D1.H    UAT seed                         (psql)
11. D1.I    Load 3 reference rulesets        (bash + curl, JRE REST)
                                                ↓
12. UAT — submit a test application against PROG-TEST-001 bound to REF-FOOD-TARGET
    Verify: spEligResult, spEligRuleResult, workflow variable, audit log
```

## Total effort: ~9 days

| Item | Effort |
|---|---|
| D1.A md51 seed | 0.25d |
| D1.B income field | 0.5d |
| D1.C derived fields | 0.5d |
| D1.D jreFieldDefinition seed | 0.5d |
| Deploy gs-plugins (rules-api + rule-editor) | 0.25d |
| D1.E refresh plugin | 3-4d |
| D1.G eligibility runtime | 1.5d |
| D1.F scheduler + workflow | 0.5d |
| D1.H UAT seed | 0.25d |
| D1.I load reference rulesets | 0.25d |
| UAT + bug fix | 1d |
| **Total** | **~9 days** |

The two Java plugins (D1.E, D1.G) are still the bulk. D1.G dropped from 5d to
1.5d because it's now a thin runtime. Independent of D1.E in data flow — can
be parallelised across two devs (~6 elapsed days).

## Migration note: legacy spElig* forms

After Phase 1, the following forms become **legacy** (kept in the JWA, used
only as result-storage targets):

- **Used as result targets only:**
  - `spEligResult` — written by EligibilityRuntime
  - `spEligRuleResult` — written by EligibilityRuntime (one row per rule)

- **Not used:**
  - `spEligField` — replaced by `jreFieldDefinition`
  - `spEligRule` — replaced by `jreRuleset`
  - `spEligCriterionRow` — replaced by Rules Script DSL inside `jreRuleset.script`
  - `spProgramEligibility` Tab 6 — needs UI update to embed the rule editor instead of the criterion grid (Phase 1 doesn't change Tab 6 yet — programmes set their ruleset by inserting a `jreRuleset` row with `contextType=PROGRAM, contextCode=<programCode>`)

**Tab 6 UI update** is recommended for Phase 2: replace the criterion-row
FormGrid with the rule-editor element from `joget-rule-editor`. Phase 1 leaves
the legacy UI intact but unused.

## Smoke test

After all artefacts are deployed:

1. Run D1.A, D1.D, D1.H in psql; D1.B, D1.C as form patches
2. Deploy `joget-rules-api`, `farmer-derived-plugin`, `subsidy-eligibility-runtime` JARs
3. Configure scheduled task per D1.F
4. Run `D1.I_load_reference_rulesets.sh` to register the 3 rulesets
5. SQL-create a test programme:
   ```sql
   -- Bind REF-FOOD-TARGET to a test programme:
   UPDATE app_fd_jreruleset
     SET c_contexttype='PROGRAM', c_contextcode='PROG-TEST-001'
     WHERE c_rulesetcode='REF-FOOD-TARGET';
   ```
6. Manually trigger the scheduler → verify `spFarmerDerived` rows for the 3 fixture farmers
7. Submit a test `spApplication` for FR-T00003 to PROG-TEST-001
8. Trigger the application_review workflow (or call EligibilityRuntime directly)
9. Verify:
   - `spEligResult` has one row with `decision`, `totalScore`, `isEligible`
   - `spEligRuleResult` has 3 rows (one per rule in REF-FOOD-TARGET)
   - Workflow variable `eligibilityDecision` is set
   - Audit log shows ELIGIBILITY_EVALUATION + FARMER_DERIVED_REFRESH entries

## Cross-references

- Plan: `_02_Analysis/Phase1_Delivery_Plan_2026-04-25.md`
- Addendum (DB-verified): `_02_Analysis/Phase1_Delivery_Plan_Addendum_2026-04-25.md`
- This pivot doc: `_02_Analysis/Phase1_Pivot_gsPlugins_2026-04-25.md` (see next file)
- Master plan: `_02_Analysis/Subsidy_Adjustment_Plan_2026-04-25.md`
- Skills: `_skills/joget-form-gen`, `_skills/joget-plugin-dev`,
  `_skills/joget-workflow-gen`, `_skills/joget-userview-gen`,
  `_skills/joget-datalist-gen` (all installed/built this session)
- gs-plugins consumed: `joget-rules-api`, `rules-grammar`, optionally `joget-rule-editor`
