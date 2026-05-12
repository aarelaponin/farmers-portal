# Phase 1 schema artefacts — concrete shapes

| | |
|---|---|
| Status | **Sketch for review** |
| Date | 2026-04-28 |
| Companion | `phase-1-plan.md` rev2 §3 D3, `phase-1-design-answers.md`, `decision-log.md` D6, D8–D15 |
| What this is | Concrete field-level schemas for the 6 Lesotho-instance extensions, the `bot_pull` action shape, the 4 IM capabilities, and the `eligibilityOutcome` shape. Sized for review before Phase 1 Week 1 starts. |
| What this is not | DDL ready for `CREATE TABLE`. Joget form definitions deploy through `form-creator-api` per the no-raw-SQL rule (`CLAUDE.md`); these specifications produce the form JSON, which produces the table. |
| Open questions | None — §3 placement question resolved by D16 (2026-04-28). |

---

## 1. `mm_benefit` — sibling of `mm_fee` (per D8)

### 1.1 Form definition fields

| Field | Type | Notes |
|---|---|---|
| `id` | varchar(64) PK | Generated; `MM-BNF-<6char>` |
| `registrationId` | varchar(64) FK | → `mm_registration.id` |
| `serviceId` | varchar(64) FK denormalised | → `mm_service.id`; matches the parent registration's serviceId |
| `code` | varchar(64) | Immutable handle within registration; e.g. `SEED-MAIZE`, `VOUCHER-1500`, `CASH-T1` |
| `label` | varchar(255) | Display name; e.g. "Improved Maize Seed (DEKALB)" |
| `kind` | enum | `input` \| `voucher` \| `cash` \| `in_kind` |
| `category` | varchar(64) FK → mm_catalog | Category code (e.g. SEED, FERTILIZER, VOUCHER, LIVESTOCK, CASH); resolved against a `mm_catalog` row with `code='benefitCategory'` |
| `itemCode` | varchar(64) FK → mm_catalog | Specific item code within category; e.g. SEED-MAIZE, FERT-NPK, VOUCHER, FODDER-T2; resolved against catalog rows |
| `quantity` | numeric(18,4) | Base quantity per beneficiary; e.g. 25 (kg of seed), 1 (voucher), 12000 (LSL cash) |
| `unit` | varchar(32) FK → mm_catalog | Unit code; e.g. `kg`, `voucher`, `LSL`; resolved against `mm_catalog` `code='unit'` |
| `unitCost` | numeric(18,4) | Cost per unit in LSL |
| `subsidyPercent` | numeric(5,2) | 0–100; e.g. 80 (input subsidy), 100 (voucher/cash) |
| `farmerContribution` | numeric(18,4) | **Derived** at render: `quantity × unitCost × (1 − subsidyPercent/100)`. Stored for audit-stable display. |
| `subsidyAmount` | numeric(18,4) | **Derived** at render: `quantity × unitCost × (subsidyPercent/100)`. Stored. |
| `formulaJson` | jsonb | Optional. When present, supersedes flat-amount derivation. Shape: closed-twenty arithmetic operators (`add`, `mul`, `if`, etc.). |
| `triggerDeterminantId` | varchar(64) FK | Optional. → `mm_determinant.id`. When present, benefit applies only when Determinant evaluates true. |
| `paymentTiming` | enum | `post_decision` (default) \| `post_completion`. Distinct from `mm_fee.paymentTiming`. |
| `paymentMethods` | text (semicolon-separated) | Subset of `in_kind`, `e_voucher`, `mobile_money`, `cash_agent` |
| `displayOrder` | integer | Sort order in benefit-request grid |
| `description` | text | Optional |

### 1.2 Mappings to the 4 programmes

prog001 — 3 rows:
```
{code: SEED-MAIZE,  kind: input, quantity: 25,  unit: kg, unitCost: 40, subsidyPercent: 80, paymentMethods: [in_kind]}
{code: FERT-NPK,    kind: input, quantity: 100, unit: kg, unitCost: 15, subsidyPercent: 80, paymentMethods: [in_kind]}
{code: FERT-UREA,   kind: input, quantity: 50,  unit: kg, unitCost: 18, subsidyPercent: 80, paymentMethods: [in_kind]}
```

prog002 — 4 rows: SEED-WHEAT, SEED-PEAS, SEED-BEANS, FERT-NPK-MOUNTAIN — all kind=input, 85% subsidy.

prog003 — 1 row: `{code: VOUCHER, kind: voucher, quantity: 1, unit: voucher, unitCost: 1500, subsidyPercent: 100, paymentMethods: [e_voucher, mobile_money]}`.

prog004 — 2 rows: `{code: FODDER-T2, kind: in_kind, ...}` and `{code: CASH-T1, kind: cash, quantity: 12000, unit: LSL, ...}`.

### 1.3 What this replaces

Legacy `app_fd_sp_benefit_item` rows. Phase 3 cutover migrates the data and retires the legacy table.

---

## 2. Scoring extension on `mm_registration` and `mm_determinant` (per D6)

### 2.1 New fields on `mm_registration`

| Field | Type | Notes |
|---|---|---|
| `evaluationStrategy` | enum | `all_must_pass` (default) \| `score_based` |
| `minimumScore` | numeric(8,2) | Default 0. Below this → hard fail regardless of mandatory criteria. |
| `passingThreshold` | numeric(8,2) | Default 0. At or above → eligible if mandatory criteria pass. |
| `scoringNotes` | text | Optional operator-facing description of the scoring scheme |

### 2.2 New fields on `mm_determinant`

| Field | Type | Notes |
|---|---|---|
| `score` | numeric(8,2) | Default 0. Points contributed when rule evaluates true. Consulted only when parent registration's `evaluationStrategy='score_based'`. |
| `ruleType` | enum | `inclusion` (default) \| `exclusion` \| `priority` \| `bonus`. Inclusion/exclusion rules evaluate unconditionally and gate `mandatoryPassed`; priority/bonus rules contribute to `totalScore` only when their rule evaluates true. |
| `failMessage` | text | Optional message to surface when the rule fails |

### 2.3 Engine semantics

For a registration with `evaluationStrategy='score_based'`, the SQL-path evaluator:

1. Collects all `mm_determinant` rows scoped to the registration.
2. Evaluates each rule independently. Records pass/fail per rule.
3. Computes `mandatoryPassed = AND(all rules with ruleType ∈ {inclusion, exclusion})` — applying the rule's `ruleType` semantics: inclusion-rules must be true; exclusion-rules must be false.
4. Computes `totalScore = SUM(score)` over rules with `ruleType ∈ {priority, bonus}` whose rule evaluates true.
5. Emits structured outcome `{mandatoryPassed, totalScore, passingThreshold, minimumScore, disposition}`.
6. `disposition` is derived:
   - `eligibility_failed_mandatory` if `!mandatoryPassed`
   - `eligibility_failed_score` if `mandatoryPassed && totalScore < minimumScore`
   - `eligibility_pending_review` if `mandatoryPassed && minimumScore ≤ totalScore < passingThreshold`
   - `eligibility_passed` if `mandatoryPassed && totalScore ≥ passingThreshold`

For `evaluationStrategy='all_must_pass'`, the evaluator collects only `inclusion` and `exclusion` rules; `mandatoryPassed` is the AND; `totalScore` and `passingThreshold` are 0; disposition is `eligibility_passed` if `mandatoryPassed`, `eligibility_failed_mandatory` otherwise.

### 2.4 Mappings to the 4 programmes

- prog001 (4 criteria, all score=0): `evaluationStrategy=score_based`, but operator-authored as effectively `all_must_pass` because no priority/bonus rules. Engine treats it identically to `all_must_pass`.
- prog002 (4 criteria, all score=0): same as prog001.
- prog003 (5 criteria, scores 40/30/30/0/10): `evaluationStrategy=score_based`, `minimumScore=55`, `passingThreshold=60`. Three priority rules (cooperativeMember, totalAreaContributed, blockPlanSubmitted) and one bonus rule (priorBlockMember).
- prog004 (5 criteria, all mandatory inclusion/exclusion): `evaluationStrategy=all_must_pass`.

---

## 3. `attributesJson` and `acceptanceWindow` placement (per D16)

**Resolved 2026-04-28 by decision-log D16.** Programme-level metadata and the application acceptance window live on `mm_registration`, not `mm_service`. Under D4's Single-Window framing (1 service + 4 registrations), the umbrella service has no programmeType, no seasonCode, no campaignType; those facts distinguish the 4 registrations.

| Field | New placement | Type | Notes |
|---|---|---|---|
| `attributesJson` | `mm_registration` | jsonb | Programme-level metadata: `{programmeType, seasonCode, campaignType, legalBasis, fundingSource, distribModelCode, allocBasisCode, benefitModelCode, allowedPaymentMethods, prioritisationStrategy, ...}` |
| `acceptanceWindowFrom` | `mm_registration` | date | Earliest date applications accepted |
| `acceptanceWindowTo` | `mm_registration` | date | Latest date applications accepted |

`mm_service.attributesJson` (empty in Lesotho's case but available for future service-level facts) and `mm_service.acceptanceWindow` are **not** added in Phase 1. They can be added later if a real use case appears.

### 3.1 Mappings to the 4 programmes

```
prog001:  attributesJson = {programmeType: INPUT_SUBSIDY, seasonCode: 2024-25-summer, distribModelCode: DIRECT_GOV, ...}
          acceptanceWindow = (2025-08-15, 2025-09-30)
prog002:  attributesJson = {programmeType: INPUT_SUBSIDY, seasonCode: 2025-winter, ...}
          acceptanceWindow = (2025-04-15, 2025-05-31)
prog003:  attributesJson = {programmeType: VOUCHER, distribModelCode: E_VOUCHER, ...}
          acceptanceWindow = (2025-09-15, 2025-10-30)
prog004:  attributesJson = {programmeType: EMERGENCY, distribModelCode: DIRECT_GOV, ...}
          acceptanceWindow = (2025-01-20, 2025-03-31)
```

---

## 4. `mm_field.widget=repeating_group` (per D9)

### 4.1 Widget enum extension

`mm_field.widget` enum gains the value `repeating_group`. Existing enum: `text | number | date | select | radio | checkbox | textarea | file_upload | gis_polygon | signature | qr_scan`. Becomes: `... | repeating_group`.

### 4.2 Additional `mm_field` fields (only when widget=`repeating_group`)

| Field | Type | Notes |
|---|---|---|
| `childScreenId` | varchar(64) FK | → `mm_screen.id`. The screen that defines the repeating row's structure. The child screen's `mm_field` rows ARE the columns of the repeating grid. |
| `minRows` | integer | Default 0. Minimum number of rows the citizen must enter. |
| `maxRows` | integer | Default null = unlimited. |
| `addLabel` | text | Default "Add row" |
| `removeLabel` | text | Default "Remove" |

### 4.3 Persistence shape (per D9)

`app_application.dataJson.<storageKey>` is a JSON array of objects, each object's keys matching the child screen's `mm_field.storageKey` values:

```json
{
  "applicationId": "APP-X7Y8Z9",
  "data": {
    "national_id": "1234567890",
    "first_name": "Mpho",
    "...": "...",
    "eligibilityResponses": [
      {"criterionId": "MM-DET-A1B2C3", "response": "Y"},
      {"criterionId": "MM-DET-D4E5F6", "response": "N"}
    ],
    "benefitRequests": [
      {"benefitId": "MM-BNF-AAAAAA", "requestedQty": 25},
      {"benefitId": "MM-BNF-BBBBBB", "requestedQty": 100}
    ]
  }
}
```

### 4.4 Engine rendering

`MetaScreenElement` reading a `mm_field` with `widget=repeating_group`:
1. Loads the child screen's `mm_field` rows in `orderIndex` order.
2. Reads the existing array from `dataJson.<storageKey>` (or seeds an empty array if first render and `minRows=0`).
3. For seeded screens (eligibility/benefits/documents), the engine seeds rows at first navigation per the parent registration's `mm_determinant`/`mm_benefit`/`mm_required_doc` content (this is the seeding behaviour `application-engine-runtime` currently does on the legacy stack).
4. Renders an inline grid; "Add" and "Remove" buttons obey min/max bounds.

---

## 5. `mm_required_doc.captureFieldsJson` (per audit §6.2 #6)

Single new field on `mm_required_doc`:

| Field | Type | Notes |
|---|---|---|
| `captureFieldsJson` | jsonb | Array of per-document fields the citizen fills. Default `[]`. |

### 5.1 Shape

```json
[
  {"name": "documentNumber", "label": "Document Number", "type": "text", "required": true},
  {"name": "documentDate", "label": "Issue Date", "type": "date", "required": true},
  {"name": "expiryDate", "label": "Expiry Date", "type": "date", "required": false},
  {"name": "issuingAuthority", "label": "Issuing Authority", "type": "text", "required": false}
]
```

### 5.2 Persistence

`app_application.dataJson.documents[].captureFields` is an object keyed by `name`. Storage is per uploaded document, alongside the file path:

```json
{
  "documents": [
    {
      "docId": "MM-DOC-XXXXXX",
      "fileRef": "wflow/app_formuploads/.../national_id_scan.pdf",
      "captureFields": {
        "documentNumber": "ID-12345",
        "documentDate": "2020-03-15",
        "expiryDate": "2030-03-15"
      }
    }
  ]
}
```

---

## 6. `mm_action.configJson` shape for `bot_pull` (per D11)

```json
{
  "imCapabilityRef": {"source": "farmers", "path": "byNid"},
  "subjectIdField": "national_id",
  "fieldMappings": [
    {"target": "first_name",   "source": "farmer.firstName"},
    {"target": "last_name",    "source": "farmer.lastName"},
    {"target": "gender",       "source": "farmer.gender"},
    {"target": "mobileNumber", "source": "farmer.mobileNumber"},
    {"target": "districtCode", "source": "farmer.districtCode"},
    {"target": "village",      "source": "farmer.villageName"}
  ]
}
```

### 6.1 Field semantics

- `imCapabilityRef.source` + `imCapabilityRef.path` → identifies the row in `app_fd_reg_bb_im_capability` to invoke.
- `subjectIdField` → the `mm_field.storageKey` whose current value (from `app_application.dataJson`) is the lookup key passed to the capability.
- `fieldMappings[].target` → a target `mm_field.storageKey` in the current screen's data.
- `fieldMappings[].source` → a JSONPath expression into the capability's response object.
- `fieldMappings[].transform` (optional) → a transformation hint (e.g. `uppercase`, `trim`); engine applies before writing target.

### 6.2 Trigger discovery

The `mm_action.triggerJson` carries `{event, target}` where `event ∈ {field_change, form_load, click, row_add}` and `target` references the field whose change fires the action. For the National-ID auto-fill use case: `triggerJson = {"event": "field_change", "target": "national_id"}`.

---

## 7. IM-connector capability rows for Phase 1 (per D10)

Four rows in `app_fd_reg_bb_im_capability`. Each row has fields per spec §3.2: `(source, path, response shape, cache TTL, consent flag, X-Road service code, ...)`. For Phase 1's single-instance topology, X-Road service code is null and the adapter is a JDBC query.

### 7.1 Capability rows

| source | path | description | response shape | cache TTL |
|---|---|---|---|---|
| `farmers` | `byNid` | Single farmer record by National ID | `{farmerCode, firstName, lastName, gender, mobileNumber, districtCode, villageName, isActive, gainfulEmployment, governmentEmployed, creditDefault, cooperativeMember}` | 5 min |
| `farmers` | `parcels.summary` | Aggregated parcel data for one farmer | `{totalParcelHectares, parcelCount, hasGisPolygon, gisVerified, priorSeasonCereal, slopePercentMax}` | 5 min |
| `households` | `vulnerability` | Household vulnerability indicators (computed) | `{foodSecurityScore, vulnerabilityFlag, vulnerabilityScore, concurrentSubsidy}` | 5 min |
| `programmes` | `applicationsByApplicant` | Prior applications by this applicant | `[{serviceCode, registrationCode, season, status, submittedAt}, ...]` | 1 min |

### 7.2 Adapter query sketches (JDBC)

**`farmers.byNid`:**
```sql
SELECT 
  fr.id AS farmerId, fr.c_beneficiaryCode AS farmerCode,
  fr.c_first_name AS firstName, fr.c_last_name AS lastName, fr.c_gender AS gender,
  fr.c_mobile_number AS mobileNumber, fr.c_member_of_cooperative AS cooperativeMember,
  fl.c_district AS districtCode, fl.c_village AS villageName,
  fp.c_creditDefault AS creditDefault, fp.c_gainfulEmployment AS gainfulEmployment, 
  fp.c_governmentEmployed AS governmentEmployed,
  fr.c_status AS isActive  -- normalised from joget-status-framework status
FROM app_fd_farms_registry fr
LEFT JOIN app_fd_farm_location fl ON fl.c_parent_id = fr.id
LEFT JOIN app_fd_farmer_income fp ON fp.c_parent_id = fr.id
WHERE fr.c_national_id = ?
LIMIT 1
```

**`farmers.parcels.summary`:**
```sql
SELECT 
  COUNT(p.id) AS parcelCount,
  COALESCE(SUM(pg.c_area_hectares::numeric), 0) AS totalParcelHectares,
  CASE WHEN COUNT(pg.id) = COUNT(p.id) THEN true ELSE false END AS hasGisPolygon,
  CASE WHEN BOOL_AND(pg.c_gisVerified = 'Y') THEN true ELSE false END AS gisVerified,
  CASE WHEN MAX(pc.c_slopePercent::numeric) IS NULL THEN 0 ELSE MAX(pc.c_slopePercent::numeric) END AS slopePercentMax,
  -- priorSeasonCereal: any parcel had a cereal crop last season
  CASE WHEN EXISTS (
    SELECT 1 FROM app_fd_cropDetailForm c
    JOIN app_fd_parcelRegistration p2 ON c.c_parcel_id = p2.id
    JOIN app_fd_farms_registry fr2 ON p2.c_farmer_id = fr2.id
    WHERE fr2.c_national_id = ? AND c.c_crop_category IN ('cereals')
  ) THEN 'Y' ELSE 'N' END AS priorSeasonCereal
FROM app_fd_farms_registry fr
LEFT JOIN app_fd_parcelRegistration p ON p.c_farmer_id = fr.id
LEFT JOIN app_fd_parcelGeometry pg ON pg.c_parent_id = p.id
LEFT JOIN app_fd_parcelClassification pc ON pc.c_parent_id = p.id
WHERE fr.c_national_id = ?
```

**`households.vulnerability`** (computed from existing fields per D7):
```sql
WITH base AS (
  SELECT 
    fr.id, fr.c_national_id,
    fp.c_creditDefault, fp.c_everOnISP, fp.c_otherInputSupport,
    fp.c_supportType, -- semicolon-separated
    fh.c_hh_size  -- via household members count, computed elsewhere
  FROM app_fd_farms_registry fr
  LEFT JOIN app_fd_farmer_income fp ON fp.c_parent_id = fr.id
  LEFT JOIN app_fd_farmer_household fh ON fh.c_parent_id = fr.id
  WHERE fr.c_national_id = ?
)
SELECT 
  -- foodSecurityScore: 0-5 derived from food-security indicators
  -- (1 if creditDefault=Y, 1 if everOnISP=Y, 1 if no monthly income, ...)
  (CASE WHEN c_creditDefault = 'Y' THEN 1 ELSE 0 END
   + CASE WHEN c_everOnISP = 'Y' THEN 1 ELSE 0 END
   + ...) AS foodSecurityScore,
  -- vulnerabilityFlag: CSV from the household members table
  (SELECT string_agg(DISTINCT
    CASE WHEN c_disability = 'Y' THEN 'DISABILITY'
         WHEN c_chronicallyIll = 'Y' THEN 'HIV'
         WHEN c_orphanhoodStatus IN ('OS','OO') THEN 'ORPHAN_HEAD'
         ELSE NULL END, ',')
   FROM app_fd_household_members WHERE c_farmer_id = base.id) AS vulnerabilityFlag,
  -- vulnerabilityScore: 0-100 weighted aggregate
  (foodSecurityScore * 20 + (CASE WHEN vulnerabilityFlag IS NOT NULL THEN 30 ELSE 0 END)) AS vulnerabilityScore,
  -- concurrentSubsidy: any active subsidy enrolment
  (CASE WHEN EXISTS (
    SELECT 1 FROM app_fd_spApplication sa
    WHERE sa.c_nationalId = base.c_national_id AND sa.c_status IN ('APPROVED','UNDER_REVIEW')
  ) THEN 'Y' ELSE 'N' END) AS concurrentSubsidy
FROM base
```

**Note:** the exact computation rule for `foodSecurityScore` and `vulnerabilityScore` weights is operator-specifiable in `app_fd_reg_bb_im_capability.computationRuleJson` (a Phase 1 design extension on the spec's capability shape); the SQL above is illustrative — Aare confirms the rule before deployment.

**`programmes.applicationsByApplicant`:**
```sql
SELECT 
  c_serviceCode AS serviceCode, c_registrationCode AS registrationCode, 
  c_season AS season, c_status AS status, c_submittedAt AS submittedAt
FROM app_fd_spApplication
WHERE c_nationalId = ?
ORDER BY c_submittedAt DESC
```

(In Phase 3 cutover this points at `app_fd_app_application` instead of legacy `app_fd_spApplication`; the capability shape is unchanged.)

---

## 8. `app_application.dataJson.eligibilityOutcome` shape (per D15)

Stored at the JSON path `dataJson.eligibilityOutcome` after every authoritative evaluation:

```json
{
  "evaluatedAt": "2026-04-30T14:32:11Z",
  "evaluator": "sql",
  "registrationId": "MM-REG-XXXXXX",
  "mandatoryPassed": true,
  "totalScore": 70,
  "minimumScore": 55,
  "passingThreshold": 60,
  "disposition": "eligibility_passed",
  "perRule": [
    {"determinantId": "MM-DET-AAAAAA", "ruleType": "inclusion", "passed": true,  "score": 0,  "message": null},
    {"determinantId": "MM-DET-BBBBBB", "ruleType": "priority",  "passed": true,  "score": 40, "message": "Cooperative member"},
    {"determinantId": "MM-DET-CCCCCC", "ruleType": "priority",  "passed": true,  "score": 30, "message": "Block plan signed"}
  ]
}
```

### 8.1 Disposition values

- `eligibility_passed` — `mandatoryPassed && totalScore ≥ passingThreshold`
- `eligibility_pending_review` — `mandatoryPassed && minimumScore ≤ totalScore < passingThreshold`
- `eligibility_failed_mandatory` — `!mandatoryPassed`
- `eligibility_failed_score` — `mandatoryPassed && totalScore < minimumScore`

### 8.2 Operator review screen rendering

`MetaReviewElement` (Phase 3) reads `dataJson.eligibilityOutcome` and renders:
- A status pill colour-coded by disposition.
- A score breakdown showing each priority/bonus rule's contribution.
- A list of failed inclusion/exclusion rules with their `failMessage`.
- An "approve / reject / return-for-correction" decision affordance per spec §6.2.3.

Operators sort their inbox by `eligibilityOutcome.totalScore` descending to prioritise borderline cases.

---

## 9. The fast-path AST shape (reference)

The fast-path JSON-AST shape is already specified in `regbb-solution-architecture-spec.md` §4.3.0.1. Phase 1 Week 1 design pass does not reproduce it; the editor's serialisation per D12 lands rules in this shape.

---

## 10. Naming & deployment conventions

All forms deploy via `form-creator-api` per `CLAUDE.md`. No raw SQL on Joget metadata.

| Form ID | Table name | Notes |
|---|---|---|
| `mm_institution` | `app_fd_mm_institution` | per spec §4.1.1 |
| `mm_service` | `app_fd_mm_service` | per spec §4.1.2 |
| `mm_registration` | `app_fd_mm_registration` | per spec §4.1.3 + §3 placement decision (attributesJson, acceptanceWindow, evaluationStrategy, minimumScore, passingThreshold) |
| `mm_role` | `app_fd_mm_role` | per spec §4.1.4 |
| `mm_screen` | `app_fd_mm_screen` | per spec §4.1.5 |
| `mm_field` | `app_fd_mm_field` | per spec §4.1.6 + §4 widget=repeating_group + childScreenId/minRows/maxRows |
| `mm_action` | `app_fd_mm_action` | per spec §4.1.7 + §6 configJson shape for bot_pull |
| `mm_required_doc` | `app_fd_mm_required_doc` | per spec §4.1.8 + §5 captureFieldsJson |
| `mm_catalog` | `app_fd_mm_catalog` | per spec §4.1.9 |
| `mm_fee` | `app_fd_mm_fee` | per spec §4.1.10 |
| `mm_determinant` | `app_fd_mm_determinant` | per spec §4.1.11 + §2 score, ruleType |
| `mm_role_screen` | `app_fd_mm_role_screen` | per spec §4.1.12 |
| **`mm_benefit`** | `app_fd_mm_benefit` | **NEW per §1** |

Form IDs are ≤24 characters per Joget DX 8 constraint (`CLAUDE.md`); the longest here is `mm_required_doc` at 15 chars. All clear.

---

## 11. Status

All schema artefacts in this doc are accepted as of 2026-04-28 (decisions D8–D16). No open questions remain. Phase 1 Week 1 design pass narrows to producing the form-definition JSON for each entity per the field lists above; deployments via `form-creator-api` follow standard pattern.

---

*End of Phase 1 schema artefacts. Open for review.*
