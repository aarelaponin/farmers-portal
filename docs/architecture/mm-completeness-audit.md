# Meta-model completeness audit — farmer registration and subsidy application against `mm_*`

| | |
|---|---|
| Status | Draft for review |
| Date | 2026-04-28 |
| Owner | Farmers Portal architecture team |
| Audience | Engineering, GovStack reviewers |
| Scope artefacts | `APP_farmersPortal-1-20260425161508.jwa`, `docs/architecture/architecture-overview.md`, `_03_Development/_02_Registration BB/_metamodel/regbb-solution-architecture-spec.md`, `_03_Development/_02_Registration BB/_metamodel/regbb-software-architecture-spec.md` |

---

## 1. Scope and method

This document audits whether the Lesotho Farmers Portal's two operative services — **farmer registration** and **subsidy application** — can be expressed in the closed RegBB meta-model, namely the twelve `mm_*` entities (`mm_institution`, `mm_service`, `mm_registration`, `mm_role`, `mm_screen`, `mm_field`, `mm_action`, `mm_catalog`, `mm_required_doc`, `mm_fee`, `mm_determinant`, `mm_role_screen`) plus the three runtime forms (`app_application`, `app_credential`, `app_application_message`), interpreted by a single Java engine that evaluates the closed twenty-operator grammar (`eq`, `neq`, `lt`, `lte`, `gt`, `gte`, `in`, `notIn`, `matches`, `exists`, `and`, `or`, `not`, `add`, `sub`, `mul`, `div`, `min`, `max`, `if`).

The question the audit answers is the precondition for the convergence bet articulated in `architecture-overview.md` §6 and the RegBB solution architecture spec §1: can both Lesotho services be delivered by the metadata-driven engine without domain-specific Java branches, using only the twelve entity types and the twenty-operator vocabulary, with no expansion of either, and with everything that does not fit explicitly classified as out-of-scope (workflow, GIS widgets, credential rendering) and handled outside the meta-model?

### 1.1 The four verdicts

Every aspect under audit receives one of four verdicts, applied with the following discipline:

- **Fits cleanly.** The aspect maps to one or more existing `mm_*` entities, or to an existing Determinant scope/operator/action, with no extension. The mapping is structurally faithful (it preserves what the aspect does at runtime; it does not collapse meaning).
- **Fits with meta-model extension.** The aspect maps to `mm_*` if a specific, named extension is added — a new field on an existing entity, a new entity that fits the existing grammar's shape, or a new value in a closed enumeration. Each such row carries the extension named, and is later classified in §6 as drift-mode-1 (single-service evidence, refused) or legitimately-additive (multi-service evidence, queued).
- **Fits as out-of-scope concern.** The aspect is real and necessary, but it is not a meta-model concern in the RegBB sense. Examples include workflow orchestration (Joget Process Builder + binding registry), audit and lifecycle (`joget-status-framework`), GIS polygon capture (custom widget), credential rendering (`reg-bb-credential-issuer`), monitoring & evaluation, and operational accounting. Out-of-scope rows do not block convergence; they are handled by the surrounding architecture.
- **Does not fit.** The aspect cannot be expressed in the meta-model without either a domain-specific engine branch or a structural change that breaks the closed grammar's properties. Each such row is the most consequential output of the audit.

### 1.2 The convergence test

A green verdict requires that the meta-model express both Lesotho services using the same twelve entities and the same twenty operators. An amber verdict allows a small number of legitimately-additive extensions, supported by multi-service evidence. A red verdict means the meta-model needs structural work before convergence at Lesotho scale is taken on.

### 1.3 Non-goals of this audit

The audit deliberately does *not* produce: a migration plan from the current Lesotho codebase to the metadata-driven engine; a Determinant rule library for either service; a list of which Lesotho plugins retire in which order; a proposal for `mm_*` schema changes beyond identifying where they would be needed; a workflow design in XPDL. Those are downstream artefacts that a non-red verdict authorises.

### 1.4 Source of truth

Where the audit refers to a form, field, or datalist by ID, that ID is taken from `APP_farmersPortal-1-20260425161508.jwa` as parsed against `appDefinition.xml` on 2026-04-28 (119 form definitions). Where the audit refers to a `mm_*` entity, scope, or operator, the source is the RegBB solution architecture spec §4. Where the audit refers to an existing platform service, the source is `architecture-overview.md` (last revised 2026-04-28).

---

## 2. The service-shape question — farmer registration

This section resolves a question that governs how §3 is structured. The answer must come before any column-by-column mapping, because the mapping of household-members, parcels, and crops/livestock is shape-dependent.

### 2.1 The question

Lesotho's `farmerRegistrationForm` is an eight-tab `MultiPagedForm` wizard: basic info, residency, agriculture, household members (FormGrid), crops & livestock (FormGrid), income & programmes, and declaration. Adjacent to it sits `parcelRegistration`, a separate wizard producing parcel records that hold a foreign key to the farmer. Household members and crop/livestock rows are repeating sub-records *within* the farmer wizard; parcels are independent records linked to but separately maintained from the farmer.

The meta-model question: how does this map onto `mm_service` / `mm_registration` / `mm_screen` / `mm_field`? Three candidate shapes deserve evaluation.

### 2.2 Shape A — single Service, many Screens, parcels as repeating section

One `mm_service` (`farmer_registration`) with six to eight `mm_screen` rows — basic info, residency, agriculture, household members, crops/livestock, income, declaration, **plus** a "parcels" screen whose `mm_field` is a repeating-group widget.

- Implications: one `app_application` row per registered farmer. Parcels are stored as nested arrays inside `app_application.dataJson`. Editing one parcel requires opening the farmer's application and editing that parcel inline.
- Issues:
  - `mm_field.widget` enumeration in spec §4.1.6 is closed: `text | number | date | select | radio | checkbox | textarea | file_upload | gis_polygon | signature | qr_scan`. There is no `repeating_group` widget. Adding one is a meta-model extension.
  - Parcel data is operationally first-class — a parcel is added, retired, partitioned, replanted across seasons independently of the farmer record. Storing it inside the farmer's `dataJson` makes those operations awkward and breaks the "once-only" property: a subsidy programme that needs `parcels[*].sizeHa` cannot fetch it via `$registry.parcels.<id>` because parcels are not registry-addressable on their own.

### 2.3 Shape B — two Services, registry-to-registry reference

One `mm_service` (`farmer_registration`) producing a farmer record, and a second `mm_service` (`parcel_registration`) producing parcel records that hold a `$registry.farmers.<id>` reference back to a farmer.

- Implications: two `app_application` row types, two distinct flows. A subsidy programme that needs parcel data references `$registry.parcels[ownerId=$applicant.farmerId].sizeHa` (or a denormalised `$registry.farmers.totalParcelHectares` if aggregation is unavoidable — see §5.3).
- Fit:
  - Two services, each with one Registration each, fits `mm_service ↔ mm_registration` cleanly.
  - Each service has its own `mm_screen` flow with no repeating-group requirement at the parcel boundary.
  - The architecture-overview already runs them as two separate `qa_service` registrations (`farmer_registration` and `parcel_registration`, §2.2) with their own form-quality rules and lifecycle gates. The cross-service reference pattern is already in place via the `farmerByNid` identity resolver.
- Cost: household members and crops/livestock remain inside the farmer service as repeating sections (Shape A's problem, scoped to those two cases instead of three). The audit's §3 records this as a single named extension rather than a service-shape problem.

### 2.4 Shape C — single Service with non-linear sub-record screens

One `mm_service` whose flow includes a parcel-management screen that loops back into a sub-form for each parcel row, returning to the parent on save.

- Implications: one application instance but with a non-linear screen flow that `mm_screen.orderIndex` does not natively model. Joget's `MultiPagedForm` can render this; the meta-model's `mm_screen` cannot.
- Verdict: requires a meta-model extension to `mm_screen` (sub-flow loops). Inferior to Shape B because it imports the operational coupling problem of Shape A while also adding meta-model complexity.

### 2.5 Evaluation against three criteria

| Criterion | Shape A | Shape B | Shape C |
|---|---|---|---|
| Fits the twelve `mm_*` entities without extension | No (`repeating_group` widget needed) | **Yes for the service boundary; one residual extension for household / crops-livestock repeating sections within the farmer service** | No (sub-flow loops in `mm_screen`) |
| Preserves operational model (parcels editable independently of farmer) | No | **Yes** | No |
| Supports `$registry.farmers.<id>` cross-service references for the subsidy service | Partial (parcels not registry-addressable) | **Yes** | Partial |

### 2.6 Recommended shape

**Shape B is the working assumption for the rest of this audit.** The decision rests on the operational evidence — parcels are already registry-addressable in the current architecture and the architecture-overview already names them as a separate service. The single residual extension (repeating sub-records inside the farmer service for household members, crop/livestock, and the eligibility / benefit / document grids on the application side) is named once in §3 and §4 and consolidated in §6.

This assumption is flagged as a decision worth re-opening if §3, §4, or §5 surfaces evidence against it. The most plausible such evidence would be a Determinant pattern that needs to traverse the farmer↔parcel boundary in a way the closed grammar does not support — see §5.3 for the parcel-aggregation case.

---

## 3. Column-by-column audit — farmer registration

Working under the Shape B assumption from §2: `farmer_registration` is one `mm_service` with one `mm_registration` (the simple case from spec §4.1.3). The `parcel_registration` service is audited in §3.B at the end of this section.

The "Aspect" column is exhaustive: every wizard tab plus every structurally-significant field on each tab. Routine text/number/date inputs are grouped one row per section and noted as "fit cleanly" unless individual fields stress the meta-model. Fields that stress the meta-model — identifiers, lookups, repeating sections, conditional fields, GIS, derived fields, signatures, identity-resolution, lifecycle — get their own row.

### 3.A `farmerRegistrationForm` (wrapper)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Wizard wrapper (`farmerRegistrationForm` / `farms_registry`, MultiPagedForm) | One Joget form whose only field is the `MultiPagedForm` element | `mm_service` (one) + N `mm_screen` rows (one per tab) | Fits cleanly | The wizard mechanism itself is replaced by `MetaScreenElement` walking `mm_screen.orderIndex`. |
| Wrapper-level lifecycle (`status`, audit columns from `joget-status-framework`) | `farms_registry` table columns + `audit_log` | `app_application.status` enum (spec §6.5.1) + native Joget audit | Fits cleanly | The closed status enum (`draft`, `submitted`, `in_review`, `approved`, `rejected`, etc.) covers the registration lifecycle. The Lesotho-specific `ACTIVE` / `VERIFIED` are project-time renames of the spec enum, not new states. |

### 3.A.1 Tab — `farmerBasicInfo` / `farmerBasicInfo` (Personal info, Contact info, Cooperative)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Tab as a screen | Subform with three Sections | `mm_screen` (kind=`form`, orderIndex=1) | Fits cleanly | |
| `national_id` (TextField) | Citizen-entered foundational identifier | `mm_field` widget=text, `claimType=applicant.nationalId`; consumed by an `applicabilityDeterminant` and by `bot_pull` actions | Fits cleanly | This is the load-bearing identity field. |
| Identity resolution from National ID (auto-fill of `first_name`, `last_name`, `gender`, `date_of_birth` etc. from the registry) | `identity-resolver-runtime` plugin + `farmerByNid` config + `app_resolver_field_map` | `mm_action` kind=`bot_pull` triggered on `national_id` `field_change`, `configJson.imCapabilityRef='civil.byNid'`, `configJson.fieldMappings` covers the auto-fill set | Fits cleanly | Spec §6.7 explicitly names this as the canonical `bot_pull` use case. The Lesotho-specific resolver becomes the IM-connector capability registration. |
| `first_name`, `last_name`, `gender` (Radio), `date_of_birth` (DatePicker), `marital_status` (SelectBox→md01) | Personal info Section | `mm_field` widget=text/radio/date/select; `marital_status.optionsCatalogId → mm_catalog(code='maritalStatus', source='static')` | Fits cleanly | The MD.01 form becomes one row in `mm_catalog` with `itemsJson`. |
| `personal_information` (ConcatFieldElement, derived "FirstName LastName") | Custom plugin element that concatenates other fields client-side | None — display-only computed | Fits as out-of-scope concern | A computed display label is a rendering decision, not captured data. The meta-model never asked to model it. The `MetaScreenElement` would render the same label inline as a templated header. Alternative classification: extension to `mm_field` (`computedFromJson`) — refused because there is only one Lesotho occurrence (drift-mode-1). |
| `preferred_language` (SelectBox→md02), `mobile_number` (TextField), `email_address` (TextField) | Contact Section | `mm_field` widget=select/text + `mm_catalog` for language | Fits cleanly | |
| `member_of_cooperative` (Radio Y/N) | Cooperative Section | `mm_field` widget=radio | Fits cleanly | |
| `cooperative_name` (TextField, conditionally required) | Visible only when `member_of_cooperative=Y` | `mm_field` + `mm_determinant` scope=`field` op=`eq` action=`require`/`hide` | Fits cleanly | Canonical Determinant pattern from spec §4.3.2. |
| `extension_officer_name` (TextField) | Cooperative Section | `mm_field` widget=text | Fits cleanly | |
| `parent_id` (HiddenField, wizard plumbing) | Carries the wrapper's record id into the subform table | None — wizard implementation detail | Fits as out-of-scope concern | `mm_screen` ordering and `app_application.dataJson` together replace the wrapper/subform hidden-field linkage. |

### 3.A.2 Tab — `farmerResidency` / `farm_location` (Location, Farm size, Access to services)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `district` (SelectBox→md03district) | Location Section | `mm_field` widget=select + `mm_catalog(code='district')` | Fits cleanly | |
| `agroEcologicalZone` (SelectBox→md04agroEcologicalZo) | Location Section | `mm_field` widget=select + `mm_catalog(code='agroEcologicalZone')` | Fits cleanly | |
| `resource_center` (SelectBox→md37collectionPoint, **cascade=district**) | Cascading dropdown filtered by district | `mm_field` widget=select + `mm_catalog(code='resourceCentre')` + a per-render filter on the parent value | **Fits with meta-model extension** | The closed action set in spec §4.3.2 (`require`, `hide`, `show`, `applyFee`, `requireDoc`, `assignRole`, `branch`, `applicable`, `fireAction`) does not include `applyOptions` or `filterOptions`. Cascading selects are common across many services; the extension would be either (a) `mm_field.optionsFilterJson` referencing the parent field, or (b) a new action `filterOptions` on the `field` scope. Multi-service evidence: parcel location (`resource_centre`), district allocation (`collectionPointCode`), crop detail (`cropType` cascade=`crop_category`), livestock (`livestockType` cascade=`livestockCategory`). Likely-additive on multi-service grounds — see §6. |
| `village` (TextField) | Location Section | `mm_field` widget=text | Fits cleanly | Free-text, not catalogued. |
| `residency_type` (SelectBox→md05) | Location Section | `mm_field` + `mm_catalog` | Fits cleanly | |
| `yearsInArea` (TextField numeric) | Location Section | `mm_field` widget=number | Fits cleanly | |
| `gpsLatitude`, `gpsLongitude` (TextField numeric) | Manual entry of farmstead coordinates | `mm_field` widget=number ×2 | Fits cleanly | These are not the GIS polygon — that lives on parcels. |
| Farm Size Details (`ownedRentedLand`, `totalAvailableLand`, `cultivatedLand`, `conservationAgricultureLand` — TextField numeric ×4) | Farm Size Section | `mm_field` widget=number ×4 | Fits cleanly | |
| Access to Services (`mode_of_transport` SelectBox + 8 distance TextField) | Access Section | `mm_field` widget=select + 8× widget=number | Fits cleanly | Mass-mappable. |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

### 3.A.3 Tab — `farmerAgriculture` / `farmerAgriculture` (Activities, Skills, Conservation, Hazards)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `cropProduction`, `livestockProduction`, `canReadWrite` (Radio Y/N ×3) | Activities Section | `mm_field` widget=radio ×3 | Fits cleanly | |
| `mainSourceFarmLabour` (SelectBox→md06), `mainSourceLivelihood` (→md07), `agriculturalManagementSkills` (→md08), `mainSourceAgriculturalInfo` (→md09) | Skills Section | `mm_field` widget=select ×4 + four `mm_catalog` rows | Fits cleanly | |
| `conservationPractices` (SelectBox→md10) + `conservationPracticesOther` (TextField, conditional on "Other") | Conservation Section | `mm_field` widget=select + `mm_field` widget=text gated by `mm_determinant` scope=`field` op=`eq` action=`show` | Fits cleanly | Same conditional pattern as `cooperative_name`. |
| `shocks_hazards` (SelectBox→md11) + `otherHazards` (TextField conditional) | Hazards Section | Same pattern as above | Fits cleanly | |
| `copingMechanisms` (TextArea) | Hazards Section | `mm_field` widget=textarea | Fits cleanly | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

### 3.A.4 Tab — `farmerHousehold` / `farmer_household` (FormGrid → `householdMemberForm`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Household members repeating section (FormGrid pointing at `householdMemberForm` / `household_members`) | A grid of N rows per farmer; each row carries `memberName`, `sex`, `date_of_birth`, `relationship` (→md12), `orphanhoodStatus` (→md13), `participatesInAgriculture`, `disability` (→md14), `chronicallyIll` | None — `mm_field.widget` enumeration does not include a repeating group | **Does not fit** the closed widget enumeration | This is the load-bearing meta-model gap on the farmer side. Three resolutions are possible: (a) extend `mm_field.widget` to include `repeating_group` referencing a child `mm_screen` (additive, multi-service evidence — see crops/livestock and the application-side seeded grids in §4); (b) model household members as their own `mm_service` with a `$registry.farmers.<id>` reference (Shape B applied recursively — pure but operationally heavy for what is genuinely intra-record data); (c) accept it as out-of-scope and continue rendering household members through a Joget-native FormGrid backed by hand-built code. The audit's recommended disposition is (a), classified in §6 as legitimately-additive on multi-service evidence. |
| `householdInstruction` (CustomHTML) | Static instruction text | None — display-only | Fits as out-of-scope concern | The same `mm_screen.description` field carries instructional copy. |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

### 3.A.5 Tab — `farmerCropsLivestock` / `farmer_crop_livestck` (FormGrid → `livestockDetailsForm`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `hasLivestock` (Radio) | Section | `mm_field` widget=radio | Fits cleanly | |
| `livestockDetails` (FormGrid → `livestockDetailsForm`, conditional on `hasLivestock=Y`) | Repeating grid of livestock holdings; each row carries `livestockCategory` (→md161), `livestockType` (→md16, **cascade=livestockCategory**), `numberOfMale`, `numberOfFemale` | Same widget gap as household members; *plus* the cascading select inside the grid | **Does not fit** without the same `repeating_group` extension as household members; the cascade adds the extension already named in §3.A.2 | Multi-service evidence reinforces the household-members case. The cascading select inside a grid row is not a new gap — it is the same `filterOptions` extension already identified. |
| Display-only computed totals (e.g. inferred herd size) | Not present in current form, but cited in `farmer-derived-plugin` snapshot logic | Out-of-scope (snapshot is a data-prep step, not a meta-model concern) | Fits as out-of-scope concern | This is the kind of computation that pushes data-prep up to the registry layer so the closed grammar can read a single value (`$registry.farmers.totalLivestock`). |

### 3.A.6 Tab — `farmerIncomePrograms` / `farmer_income` (Income, Programmes, Safety nets)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `mainSourceIncome` (SelectBox→md17) | Income Section | `mm_field` widget=select + `mm_catalog` | Fits cleanly | |
| `income_sources` (SelectBox multi-select?→md17) | Income Section | If genuinely multi-select: `mm_field` widget=checkbox + `mm_catalog`; if single-select duplicate of `mainSourceIncome`: clean up first | Fits cleanly | Worth verifying which it is during implementation; widget-level distinction. |
| `gainfulEmployment`, `governmentEmployed`, `relativeSupport`, `supportFrequency`, `creditDefault`, `everOnISP`, `otherInputSupport` (Radio Y/N ×7) | Income/Programs Sections | `mm_field` widget=radio ×7 | Fits cleanly | |
| `supportType` (CheckBox multi-select) | Income Section | `mm_field` widget=checkbox + `mm_catalog` | Fits cleanly | |
| `averageAnnualIncome`, `monthlyExpenditure`, `totalLoans12Months`, `informalTransfers`, `networksSupport`, `formalTransfers`, `networkAssociations`, `networkRelatives` (TextField numeric ×8) | Income / Safety Nets Sections | `mm_field` widget=number ×8 | Fits cleanly | |
| `supportProgram` (SelectBox→md20) | Programs Section | `mm_field` + `mm_catalog` | Fits cleanly | |
| `parent_id`, `income_programs_key` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

### 3.A.7 Tab — `farmerDeclaration` / `farmer_declaration` (Declaration, Signature, Official Use)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `declarationHeader`, `declarationText` (CustomHTML) | Display-only legal copy | `mm_screen.description` (per-screen copy) or out-of-scope | Fits as out-of-scope concern | |
| `declarationConsent` (CheckBox, must be ticked) | Declaration Section | `mm_field` widget=checkbox + `mm_determinant` scope=`field` requiredness=true | Fits cleanly | |
| `declarationFullName` (TextField), `field13` (DatePicker), `field12` (Signature) | Signature Section | `mm_field` widget=text/date/signature ×3 | Fits cleanly | `signature` is in the spec's widget enumeration. |
| `beneficiaryCode` (IdGeneratorField, format `FR-??????`) | Server-generated PK | `app_application.id` (engine-generated `APP-<6char>` per spec) — and a registry-public form `FR-<6char>` if needed | Fits as out-of-scope concern | The spec's runtime PK is generated by the engine, not declared in `mm_field`. The Lesotho-specific format becomes a configuration on the engine's PK generator, not a meta-record. |
| `registrationStation`, `registrationChannel` (SelectBox→md18), `registrationStatus` (HiddenField) | "Official Use Only" — operator-side fields, not citizen-captured | These belong on a `mm_role_screen` (operator section), not on a citizen `mm_screen` | Fits cleanly *under operator-screen reclassification* | A small content move at implementation time, not a meta-model extension. |
| `parent_id`, `declaration_key` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

### 3.B Audit — `parcelRegistration` (`parcel_registration` service under Shape B)

`parcelRegistration` is a separate `mm_service` (per Shape B in §2). It has its own four-tab wizard.

#### 3.B.1 `parcelLocation` / `parcelLocation`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `farmer_id` (SmartSearchElement, typeahead lookup against farmer registry) | The cross-service link | `mm_field` widget=select + `optionsBinderJson.imCapabilityRef='registry.farmers.search'` + `claimType=parcel.ownerId` | **Fits with meta-model extension** | `mm_field.widget` does not include a `typeahead` value. Three resolutions: (a) add `widget=typeahead` (additive, multi-service evidence — `spApplication.programCode` uses the same pattern); (b) reuse `widget=select` and let `MetaScreenElement` choose typeahead UX based on `optionsBinderJson` source kind; (c) reclassify SmartSearch as a custom widget like GisPolygon, registered through a small `mm_field.widget` opt-in. Recommended (b): the IM-connector binder for paged search can drive a typeahead UI in `MetaScreenElement` without needing a new widget value. |
| `parcel_number` (IdGeneratorField, `PC-??????`) | Server-generated PK | `app_application.id` per the spec; the human-readable prefix is engine config | Fits as out-of-scope concern | Same pattern as `beneficiaryCode`. |
| `district` / `agroEcologicalZone` / `resource_centre` (cascade) / `sub_centre` / `village` | Administrative Location Section | Same as farmer residency: `mm_field` + `mm_catalog`; cascade tracked in §3.A.2 | Fits cleanly *plus the cascade extension already named* | |
| `parcelName` (TextField, optional) | Free-text label | `mm_field` widget=text, `requirednessDeterminant` returns false unconditionally | Fits cleanly | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 3.B.2 `parcelGeometry` / `parcelGeometry`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `geometry` (GisPolygonCaptureElement, custom plugin `joget-gis-ui` + Leaflet) | The actual polygon capture widget | `mm_field` widget=`gis_polygon` (in the spec's widget enumeration) | Fits cleanly *as widget kind*, **fits as out-of-scope concern for the implementation** | The spec lists `gis_polygon` as a valid widget but the actual polygon-capture UI, the GeoJSON storage shape, and the overlap-check service are not meta-model concerns; they live in the `joget-gis-server` plugin invoked by the engine when it encounters this widget. The architecture-overview already classifies GIS this way. |
| Companion derived fields (`area_hectares`, `perimeter_meters`, `vertex_count`, `centroid_lat`, `district_name`, `village_name`, `auto_center_lat`, `auto_center_lon` — all HiddenField) | Server-computed at polygon save | None — these are derived from `geometry`, not captured | Fits as out-of-scope concern | Computed at the GIS plugin's storeBinder. Same out-of-scope reasoning as `personal_information` ConcatField — derived data is data-prep, not meta-model. The closed grammar reads them as `$applicant.parcel.areaHectares` if needed. |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 3.B.3 `parcelClassification` / `parcelClassification`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `cultivatedLand`, `conservationAgricultureLand` (TextField numeric) | Land Size Section | `mm_field` widget=number ×2 | Fits cleanly | Note these duplicate fields with the same names exist on `farmerResidency` — at implementation time, decide whether they live on the farmer service (aggregated) or per-parcel (granular). |
| `conservationPractices` (SelectBox), `conservationNote` (TextArea) | Conservation Practices Section | `mm_field` widget=select + textarea | Fits cleanly | |
| `parcelNotes` (TextArea) | Additional Information Section | `mm_field` widget=textarea | Fits cleanly | |
| `landSizeNote` (CustomHTML) | Display-only | Out-of-scope | Fits as out-of-scope concern | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 3.B.4 `parcelCrops` / `parcelCrops` (FormGrid → `cropDetailForm`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `cropManagement` (FormGrid → `cropDetailForm`) | Repeating grid of crop rows; each row carries `crop_category` (→md191), `cropType` (→md19, **cascade=crop_category**), `areaCultivated`, `areaUnit` (→md15), `bagsHarvested`, `fertilizerApplied`, `pesticidesApplied` | Same widget gap as household members and livestock | **Does not fit** without the same `repeating_group` extension (multi-service evidence: now four occurrences) | This is the third reinforcement of the legitimately-additive case in §6. |
| `cropInstruction` (CustomHTML) | Display-only | Out-of-scope | Fits as out-of-scope concern | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

### 3.C Cross-cutting concerns on the farmer registration service

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `joget-status-framework` lifecycle (`DRAFT`, `ACTIVE`, `VERIFIED`) and `audit_log` | `plugins/joget-status-framework` consumed by `form-quality-runtime` | Partially `app_application.status` (the spec's closed enum); partially out-of-scope (audit) | Fits cleanly *as far as the spec's enum is concerned*; the Lesotho-specific names map onto `submitted` / `approved` / a domain-specific equivalent of `completed` | The spec's enum is broader; the Lesotho project's `ACTIVE` is semantically a `submitted`/`approved` post-state. No new states needed. |
| `form-quality-runtime` rules over the farmer service (5 rules, gate `ACTIVE, VERIFIED`) | `qa_service('farmer_registration')` configuration in `qa_rule` | These map onto **`mm_determinant scope=routing`** (transition gate) and onto **`mm_field.validationRulesJson` + screen-load Determinants** (data-quality checks) | Fits cleanly *if rules are expressible in the closed twenty-operator grammar* | This is the meaningful test. The current rules are SQL — `EXISTS (SELECT ... WHERE c_field IS NULL OR c_field='')` for completeness checks, plus cross-field consistency rules. Most reduce to `exists`/`eq` Determinants. The rules that do *not* reduce are usually those that aggregate (e.g. "total parcel area > 0"), which face the §5.3 aggregation gap. |
| `farmer-derived-plugin` computed snapshot (`spFarmerDerived`) | Domain-specific Java tool that projects fields from N source forms into a snapshot | Out-of-scope (data-prep, not meta-model) | Fits as out-of-scope concern | The architecture-overview already names this as the future `derived-snapshot-runtime` platform engine. The `mm_*` model never asked to express it; it exists exactly to keep the closed grammar honest by pre-computing values that the grammar cannot derive at evaluation time (per §5.3). |
| Form-quality `QualityBannerElement` on the farmer form | Custom Joget element | Out-of-scope (rendering decoration) | Fits as out-of-scope concern | Banners are UX. The `MetaScreenElement` would render an equivalent inline status pill from `app_application.status`. |

---

## 4. Column-by-column audit — subsidy application

The subsidy domain has two halves: the **programme designer** (`spProgramMain` + 9 tab subforms — the operator-side service-design surface), and the **application wizard** (the citizen-facing `spApplication` + its child grids — the runtime surface). Under Shape B and the meta-model's posture, these map to two very different parts of `mm_*`: the programme designer becomes meta-records (one programme = one `mm_service` + child meta-records); the application wizard becomes runtime `app_application` rows.

Where the JWA still carries the legacy single-form `spApplication` (50 fields), the live architecture (per `architecture-overview.md` §2.3, build-012 of `application-engine-runtime`) has refactored it into a 6-tab wizard with `spApplicationEligibility`, `spApplicationBenefitReq`, `spApplicationDocV2` seeded children. The audit treats the live architecture as canonical and notes the legacy form where it differs.

### 4.A Programme designer — `spProgramMain` and tabs

#### 4.A.0 `spProgramMain` wrapper / `sp_program`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Wizard wrapper (MultiPagedForm with 9 tabs) | One wrapper form with status fields and the wizard element | One `mm_service` row with status lifecycle | Fits cleanly | |
| `programCode` (IdGeneratorField, `PRG-YYYY-NNN`) | Stable handle for the programme | `mm_service.code` | Fits cleanly | |
| `status`, `requestedStatus`, `submittedBy`, `submittedDate`, `approvedBy`, `approvedDate` (HiddenField + audit) | Lifecycle gate for publishing a programme | `mm_service.status` ∈ {`draft`, `published`, `retired`} + `mm_service.publishedAt` + `joget-status-framework` audit | Fits cleanly | The Lesotho-specific `requestedStatus` two-step (operator submits, supervisor approves) is a workflow concern around the publish action, not a meta-model state — out-of-scope by the same logic as workflow generally. |

#### 4.A.1 Tab — `spProgramIdentity` / `sp_program_identity`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `programName` (TextField) | Identification Section | `mm_service.name` | Fits cleanly | |
| `programType` (SelectBox→md21programType) | Identification Section | None directly on `mm_service` (no `serviceType` field) | **Fits with meta-model extension** | `mm_service` carries `code`, `name`, `version`, `status`, `institutionId`, `description`, `publishedAt` — and nothing else. A `serviceType` (or generally a typed-attributes JSON column) would carry programme-typology data. Multi-service evidence: vehicle registration would also typify (vehicle category, fuel type) in similar ways. Likely-additive — see §6. Alternative: model programme-type as a `mm_determinant scope=eligibility` constant rather than as service metadata. Refused because `programType` is a service-level *fact*, not a rule. |
| `seasonCode` (SelectBox→md48Season) | Identification Section | `mm_service` extension (same column as `programType`); or `mm_service.version` repurposed | Fits with meta-model extension | The season is a defining attribute of the service version, not a citizen rule. The `(code, version)` identity in the spec implicitly covers seasonality if every season's programme is a new version — clean but operationally heavy (operators would recreate the programme for every season). The recommended disposition is a `mm_service.attributesJson.season` that participates in catalog filters but does not affect immutability. |
| `campaignType` (SelectBox→md39CampaignType, **cond=`c_isActive='Y'`**) | Identification Section | Same `mm_service.attributesJson` slot; the `isActive` filter on the catalog applies via `mm_catalog.itemsJson` curation | Fits with meta-model extension | Same disposition as `programType`. |
| `legalBasis`, `fundingSource` (TextField) | Identification Section | `mm_service.description` (free-form) or `mm_service.attributesJson` | Fits cleanly | |
| `description`, `objectives` (TextArea ×2) | Description Section | `mm_service.description` is a single field — two free-form fields require structuring | Fits with meta-model extension | The spec's `mm_service.description` is one column; the Lesotho programme distinguishes "what the programme is" from "what it aims to achieve". Either model both as one structured-Markdown blob (no extension), or split into `description` + `objectives` (small additive extension). Preferred resolution: keep one `description` and treat objectives as section headings within it. |
| `sectionAudit` (CustomHTML) | Display-only | Out-of-scope | Fits as out-of-scope concern | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 4.A.2 Tab — `spProgramTimeline` / `sp_program_timeline`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `applicationStartDate`, `applicationEndDate`, `distributionStartDate`, `distributionEndDate`, `programStartDate`, `programEndDate` (DatePicker ×6) | Timeline Section | None on `mm_service` — service has only `publishedAt` | **Fits with meta-model extension** | The window-based application acceptance is a structural feature of subsidy services. Evidence beyond Lesotho: any call-for-applications service (research grants, bursaries, election registration) needs the same. Likely-additive: extend `mm_service` with `acceptanceWindow{from,to}` or model as a service-level `mm_determinant scope='applicable'` rule using `$now()` — but `$now` is not a defined ref in the closed grammar, which would itself be an extension. Multi-service evidence makes this a queued additive item — see §6. |
| `totalBudget`, `reservePercentage`, `availableBudget` (CalculationField), `adminBudget` (TextField numeric ×4) | Budget Section | None — programme budget is operational accounting | Fits as out-of-scope concern | Programme budget is *not* a registration-BB concern. It is a financial management concern that the engine should not touch. Out-of-scope. |
| `allocBasisCode` (SelectBox→md41), `benefitModelCode` (SelectBox→md28), `estimatedBeneficiaries`, `maxBenefitPerFarmer`, `fixedBenefitAmount` (cascade=benefitModelCode), `subsidyPercentage` (cascade=benefitModelCode) | Budget Section | Programme delivery configuration — partially `mm_fee`-shaped, mostly **out of `mm_*` for benefits** | **Does not fit** under the current meta-model | `mm_fee` models charges to citizens (per spec §4.1.10: "A fee on a registration… `flatAmount`, `formulaJson`, `currency`"). Subsidies — money paid *to* citizens — are not modelled. This is the load-bearing finding for the subsidy domain. Three resolutions: (a) introduce `mm_benefit` as a sibling of `mm_fee` (additive, same shape, different sign — `kind=charge|subsidy|in_kind`); (b) reinterpret `mm_fee` as bidirectional with sign in `flatAmount` (compact but semantically muddled); (c) accept benefits as out-of-scope and handle them in a separate "entitlement engine" (the current `decision-engine-runtime` does this). The audit's recommended disposition is (a) on multi-service evidence — any subsidy/cash-transfer/in-kind-distribution programme has this need; the registration BB is the natural place to model it. Classified in §6. |
| `dateValidationScript` (CustomHTML, JavaScript validating start≤end) | Cross-field validation | `mm_action` kind=`validate` wired to the screen, gated by `mm_determinant` rule `{op:'lte', left:'$applicant.applicationStartDate', right:'$applicant.applicationEndDate'}` | Fits cleanly | Spec §4.1.7 lists `validate` as an `mm_action` kind; the Determinant gates whether the action fires. The closed grammar handles cross-field comparison via `lt`/`lte`/`gt`. |
| `parent_id`, `sectionTimeline`, `sectionBudget` (HiddenField + CustomHTML) | Wizard plumbing + display | None | Fits as out-of-scope concern | |

#### 4.A.3 Tab — `spProgramGeography` / `sp_program_geography` (with FormGrid → `spDistrictAllocation`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `geographicScope` (Radio: national / districts / zones / centres) | Geography Section | `mm_field` on the programme designer (=`mm_service.attributesJson.geoScope`) — but the *use* of it is by Determinants on the citizen application | Fits cleanly | |
| `targetDistricts`, `targetZones`, `collectionPoints` (CheckBox multi-select, conditional on geographicScope) | Geography Section | These are *applicability constraints*, modelled as `mm_determinant scope=eligibility` op=`in` rule like `{op:'in', left:'$applicant.districtCode', right:[<list>]}` with the list as a `$constant` literal pinned at publish | Fits cleanly | Canonical use of `in` from spec §4.3.3. |
| `districtAllocations` FormGrid → `spDistrictAllocation` (8 fields: districtCode, zoneCode, allocatedBudget, estimatedBeneficiaries, collectionPointCode (cascade=districtCode), priorityRank, notes) | Geography Section | None — district-level budget allocation is operational accounting | Fits as out-of-scope concern | Same logic as overall programme budget. Allocation is not a registration BB concern; it lives in a budget-management plugin downstream. |
| `geographyNotes` (TextArea) | Geography Section | `mm_service.attributesJson.geoNotes` or out-of-scope | Fits as out-of-scope concern | |
| `parent_id`, `sectionGeoInstruction` (HiddenField + CustomHTML) | Wizard plumbing + display | None | Fits as out-of-scope concern | |

#### 4.A.4 Tab — `spProgramBeneficiary` / `sp_program_beneficr` (with FormGrid → `spTargetGroupRow`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `targetingStrategy` (Radio), `priorityModel` (SelectBox) | Beneficiary Section | `mm_service.attributesJson` — programme-shape configuration | Fits cleanly | Doesn't drive Determinants; drives operator-side prioritisation. |
| `requiresApplication` (Radio Y/N) | Beneficiary Section | `mm_registration.applicabilityKind` — when N, the registration is auto-applied (`mandatory_for_all` with no application screen, just an enrolment hook) | Fits cleanly | Spec §6.3.1.4 maps directly. |
| `autoEnrollEligible` (Radio, cascade=requiresApplication) | Beneficiary Section | If `requiresApplication=N`, this controls whether enrolment runs automatically — modelled as a workflow tool, not a meta-record | Fits as out-of-scope concern | |
| `allowMultipleBenefits` (Radio Y/N), `maxBenefitsPerFarmer` (TextField numeric, cascade) | Beneficiary Section | These constrain runtime behaviour and link to the `mm_benefit` extension proposed in §4.A.2 | Fits with meta-model extension | Lives on the same `mm_benefit` row family. |
| `targetGroups` FormGrid → `spTargetGroupRow` (7 fields: targetGroupCode (→md30), targetCategoryCode (→md42), priorityWeight, estimatedCount, allocationPercent, notes) | Beneficiary Section | This is the *target-group eligibility predicate* — modelled as `mm_determinant scope=eligibility` rule referencing applicant attributes | Fits cleanly *as Determinants*; the `priorityWeight` / `allocationPercent` numerics are out-of-scope (programme administration) per **decision-log D5 (2026-04-28)** | The eligibility logic ("female farmers under 35 with disability flag") is a clean closed-grammar Determinant. The weight/quota numerics live outside `mm_*` — programme-administration metadata, not registration-BB concerns. No `mm_target_group` extension. |
| `beneficiaryNotes` (TextArea) | Beneficiary Section | `mm_service.attributesJson` or out-of-scope | Fits as out-of-scope concern | |
| `parent_id`, `sectionBenefInstruction` (HiddenField + CustomHTML) | Wizard plumbing + display | None | Fits as out-of-scope concern | |

#### 4.A.5 Tab — `spProgramBenefits` / `sp_program_benefits` (with FormGrid → `spBenefitItemRow`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `distribModelCode` (SelectBox→md40), `allowedPaymentMethods` (CheckBox→md24), `inputPackageCode` (SelectBox→md46) | Benefit Configuration Section | These are operational delivery configuration — partially `mm_fee.paymentMethods[]` (which already exists in the spec at §4.1.10) | Fits cleanly | `paymentMethods` is in spec; `distribModel` and `inputPackage` are subsidy-specific extensions of the same `mm_benefit` entity proposed in §4.A.2. |
| `allowPartialBenefit` (Radio), `requiresVerification` (Radio) | Distribution Settings Section | Operational behaviour; `requiresVerification` maps to `mm_role` (a verification role) on the registration | Fits cleanly | |
| `benefitDescription`, `benefitNotes` (TextArea) | Distribution Settings Section | `mm_benefit.description` (extension) | Fits with meta-model extension | |
| `benefitItems` FormGrid → `spBenefitItemRow` (13 fields: itemType, categoryCode, itemCode, itemName, quantity, unit, unitCost, subsidyPercent, farmerContribution, totalCost, subsidyAmount, notes) | Benefit Items Section | None in current `mm_*`. This is the substance of `mm_benefit` — the item-level decomposition of what the programme delivers | **Does not fit** under the current meta-model | This is the same finding as the `mm_fee` / `mm_benefit` direction in §4.A.2 and is the architecturally heaviest single gap in the audit. The closed grammar's `if` and arithmetic operators (`add`, `mul`) cover the formula side (`subsidyAmount = quantity × unitCost × (subsidyPercent / 100)`) — the math is fine. What is missing is the *entity* on which to attach those formulas: a benefit-bearing analogue of `mm_fee`. |
| `sectionDistribution`, `sectionBenefitItems`, `sectionBenefitSummary`, `benefitModelCode_display` (CustomHTML ×4) | Display only | Out-of-scope | Fits as out-of-scope concern | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 4.A.6 Tab — `spProgramEligibility` / `sp_program_eligiblt` (with FormGrid → `spEligCriterionRow`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `evaluationStrategy` (SelectBox) | Eligibility Configuration Section | `mm_registration.evaluationStrategy ∈ {all_must_pass, score_based}` per **decision-log D6 (2026-04-28)** | Fits with meta-model extension (6th Lesotho-instance extension) | Audit-time recommendation was to refuse scoring on single-service-evidence grounds. Programme screening on 2026-04-28 surfaced three-of-four programmes with `SCORE_BASED` configured — multi-service evidence reverses the recommendation. Scoring adopted; engine evaluator's score-sum mode wires into the SQL-path evaluator. |
| `minimumScore`, `passingThreshold` (TextField numeric, cascade=evaluationStrategy) | Eligibility Configuration Section | `mm_registration.minimumScore` + `mm_registration.passingThreshold` (numbers, default 0) | Fits with meta-model extension (part of D6) | |
| `eligibilityCriteria` FormGrid → `spEligCriterionRow` (12 fields: criterionOrder, fieldCategory, fieldName, operatorCode, criterionValue, criterionValueTo, isMandatory, ruleType, score, failMessage, notes) | Eligibility Section | This is **the in-house determinant model**. Each row maps directly onto `mm_determinant scope=eligibility ruleJson={op: <operatorCode>, left: $applicant.<fieldName>, right: <criterionValue>}` | **Fits cleanly *if* `operatorCode` is constrained to the closed twenty** | Today, `operatorCode` references `md36eligibilityOpera` or `md50EligOperator` — free-form, populated by analysts. The closed-set discipline requires the catalogue to be pinned at the twenty. This is a meta-model-authoring discipline, not a structural change. |
| `criterionValueTo` (upper bound for between-style rules) | Eligibility Section | The closed grammar has no `between`; expressed as `and(gte(left, lower), lte(left, upper))` | Fits cleanly | One Determinant becomes one `and` of two comparisons. The audit's stress test in §5 verifies this on a real Lesotho rule. |
| `score` (per-criterion weight) | Eligibility Section | `mm_determinant.score` (number, default 0) + `mm_determinant.ruleType ∈ {inclusion, exclusion, priority, bonus}` per **decision-log D6 (2026-04-28)** | Fits with meta-model extension (part of D6) | Score is consulted only when the parent registration's strategy is `score_based`; sums across priority/bonus Determinants whose rule evaluates true. |
| `failMessage`, `notes` (TextField + TextField) | Eligibility Section | `mm_determinant.description` carries the message | Fits cleanly | |
| `eligibilityNotes` (TextArea) | Eligibility Configuration | Out-of-scope (operator-only annotation) | Fits as out-of-scope concern | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 4.A.7 Tab — `spProgramApproval` / `sp_program_approval`

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `requiredDocuments` (CheckBox→md23documentType, multi-select) | Documents Section | One `mm_required_doc` row per ticked code, each linking to `md23` (which becomes a `mm_catalog` row) | Fits cleanly | Spec §4.1.8 is a tight match. |
| `notificationTypes` (CheckBox→md34notificationType, multi-select) | Notifications Section | `mm_action` kind=`message` rows, one per ticked notification template, gated by lifecycle Determinants | Fits cleanly | The `notification-dispatcher-runtime` plugin (proposed in architecture-overview Horizon 2) reads `mm_action` config. |
| `programOfficer`, `contactEmail`, `contactPhone`, `supervisorName`, `supervisorEmail` (TextField ×5) | Administration Section | `mm_institution.contactInfoJson` (the institution that owns the service) | Fits cleanly | These are properties of the institution running the programme, not of the programme itself. |
| `approvalWorkflow` (SelectBox) | Administration Section | `app_fd_reg_bb_binding.c_process_def_id` (binding registry from spec §5.2.2) | Fits cleanly | The Lesotho operator-picks-a-workflow becomes the analyst-adds-a-binding-row pattern. |
| `isActive` (Radio Y/N) | Approval Section | `mm_service.status='published'` | Fits cleanly | |
| `publishDate` (DatePicker) | Approval Section | `mm_service.publishedAt` | Fits cleanly | |
| `approverComments`, `rejectionReason`, `approvalNotes` (TextArea ×3) | Approval Section | These are operator-side workflow data, not service definition | Fits as out-of-scope concern | They live on the workflow process variables, not on the service meta-record. |
| Status display + audit info (CustomHTML ×3) | Display only | Out-of-scope | Fits as out-of-scope concern | |
| `parent_id` (HiddenField) | Wizard plumbing | None | Fits as out-of-scope concern | |

#### 4.A.8 Tab — `spProgramMonitoring` / `sp_program_monitor` (with FormGrid → `spKpiRow`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| `monitoringApproach`, `reportingFrequency`, `dataCollectionMethod` (SelectBox ×3); `evaluationStartDate`, `evaluationEndDate` (DatePicker ×2); `baselineRequired`, `midtermReview`, `finalEvaluation` (Radio ×3); `monitoringNotes` (TextArea); `kpiGrid` (FormGrid → `spKpiRow`, 11 fields per row) | Monitoring Configuration Section | None — programme M&E is reporting/observability, not registration | Fits as out-of-scope concern | The entire tab is M&E. Reporting is a downstream observability concern, not a meta-model concern. The architecture-overview already classifies reporting in Horizon 2 as a separate REST surface (`/api/audit`) over the audit spine. Out-of-scope. |

### 4.B Application wizard — `spApplication` and seeded children

The legacy `spApplication` (50 fields, single form) is superseded by the build-012 wizard. The audit covers the live architecture; legacy fields whose meaning survives are noted at the bottom of each tab.

#### 4.B.0 Wrapper — `spApplication` (6-tab wizard)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Wizard wrapper with 6 tabs (applicant, eligibility, benefits, documents, declaration, decision) | One Joget MultiPagedForm | `mm_service.id=farmer_application` is **NOT** a separate service — it is the citizen-facing flow of the `farmers_subsidy` family of services. Each programme is one `mm_service` (per §4.A.0). The "application" is one `app_application` row | Fits cleanly | This corrects a naming asymmetry in the current architecture — `farmer_application` is registered in `qa_service` but is not a service in the RegBB sense. It's a *runtime artefact*, not a registration-BB service. |
| `applicationCode` (IdGeneratorField, `AP-??????`) | Server-generated PK | `app_application.id` | Fits cleanly | |
| `applicant_*` resolved fields (farmerCode, nationalId, farmerName, gender, mobileNumber, districtCode, village) | Auto-filled by `identity-resolver-runtime` from National ID | `mm_action` kind=`bot_pull` on the Tab 1 screen, identical pattern to §3.A.1 | Fits cleanly | |
| `programCode` (SelectBox, **cond=`c_isActive='Y' AND c_status='APPROVED'`**) | Programme picker | `mm_field` widget=select with `optionsBinderJson.imCapabilityRef='catalogue.publishedServices'` (or, more directly, hard-coded to "the citizen picks one of the catalogue's `mm_service WHERE status=published`") | Fits cleanly | The programme picker is the *Service Catalogue page* per spec §3.1 — the citizen lands on the catalogue, picks a programme, and the chosen programme's `mm_service.id` is what drives all subsequent screen / Determinant resolution. |

#### 4.B.1 Tab 1 — Applicant & Programme

Already covered by `bot_pull` action in §4.B.0. All fields fit cleanly under the `mm_action`/`mm_field` pattern. No further audit rows needed.

#### 4.B.2 Tab 2 — Eligibility (seeded grid → `spApplicationEligibility`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Citizen self-attestation grid, one row per criterion seeded from `sp_elig_criterion` | `application-engine-runtime.SeedingService.seed()` populates rows | The runtime artefact is `app_application.dataJson.eligibilityResponses[]`. The *seeding* is the engine's act of materialising one citizen-side checkbox per `mm_determinant scope=eligibility` row of the chosen service. The Determinant evaluator then re-evaluates each rule server-side at submit | Fits cleanly | The two-layer pattern (citizen self-attestation + server-side authoritative evaluation) named in `architecture-overview.md` §3.1 maps directly onto the spec's "evaluate the same Determinant twice — once at form load for visibility/required, once at submit for authoritative outcome". |
| `eligibilityScore`, `eligibilityDetails`, `eligibilityWarnings` (resolved at submit) | Engine-computed | `app_application.dataJson.eligibilityOutcome = {mandatoryPassed, totalScore, passingThreshold, minimumScore}` per the structured outcome introduced by **decision-log D6**; surfaced via the operator-side `mm_role_screen` | Fits cleanly under the scoring extension | Engine emits the structured outcome on every authoritative evaluation; the operator review screen renders it for human review when `totalScore` falls between `minimumScore` and `passingThreshold` (the `pending_review` band). |

#### 4.B.3 Tab 3 — Benefits requested (seeded grid → `spApplicationBenefitReq`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Citizen-side benefit request grid, one row per programme benefit item | `application-engine-runtime.SeedingService.seed()` reads programme's `sp_benefit_item` rows and seeds one application row per | If `mm_benefit` is added (per §4.A.5), this tab is the citizen-side counterpart — one `app_application.dataJson.benefitRequests[]` row per programme `mm_benefit` row, each with `requestedQty` and the per-row formula evaluation. **Without `mm_benefit`, this tab does not fit** | **Does not fit** under the current meta-model; fits with the `mm_benefit` extension | Same finding as §4.A.5 — the application side reinforces the design side. |
| `requested_qty`, `approved_qty` (per row) | Citizen-entered + operator-overridden | `mm_field` widget=number on the seeded child screen; `approved_qty` lives on the `mm_role_screen` operator section | Fits cleanly under `mm_benefit` | |

#### 4.B.4 Tab 4 — Documents (seeded grid → `spApplicationDocV2`)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Document upload grid, one row per programme document requirement | Seeded from `sp_doc_requirement_row` | `app_application.dataJson.documents[]`, one per `mm_required_doc` row of the service | Fits cleanly | Spec §4.1.8 + §6.5 covers this directly. |
| `accepted_formats`, `max_size_kb` (snapshotted at seed time per design decision D15) | Per-doc constraint baked at seed | Spec snapshots `acceptedTypes` / `maxSizeBytes` at seed time too (per spec §4.1.8 + §6.5 audit-stable wording) | Fits cleanly | Pattern match. |
| `verification_status` (SelectBox→md64documentStatus) | Verification lifecycle | Operator-side, lives on the `mm_role_screen` review section | Fits cleanly | |
| `rejection_reason` (SelectBox→md65docRejReason) | When verification rejected | Same — operator-side | Fits cleanly | |
| `documentNumber`, `issuingAuthority`, `documentDate`, `expiryDate` (per-document metadata fields, captured per upload) | Citizen-entered details about each document | None on `mm_required_doc` — the spec defines the *requirement*, not the *captured metadata* | **Fits with meta-model extension** | Add `mm_required_doc.captureFieldsJson` listing per-document fields the citizen must fill. Multi-service evidence: any document-bearing service (passport submission, driver's licence, marriage certificate) has issue-date/expiry-date concerns. Likely-additive — see §6. |

#### 4.B.5 Tab 5 — Declaration

Mirrors §3.A.7 (citizen consent + signature). Fits cleanly under the same Determinant + signature-widget pattern. No new audit rows.

#### 4.B.6 Tab 6 — Decision (operator-only)

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| Decision capture (approve / reject / needs-info), reduction, reason | Operator captures via `decision-engine-runtime`'s `DecisionStoreBinder` | This is the canonical `mm_role_screen.kind='review'` with `decisionAffordance ∈ {approve, reject, return_for_correction}` per spec §4.1.12 | Fits cleanly | |
| Grant issuance (`imEntitlement` row + N `imEntitlementItem` rows on approve) | `EntitlementGenerator` creates the entitlement and items | Under `mm_benefit`: the resolved benefit set becomes `app_application.dataJson.approvedBenefits[]`; *issuance* (creating the entitlement record) is then the analyst's workflow tool, not a meta-record | Fits cleanly *as runtime artefact*; the entitlement row itself is workflow output | Same shape as `app_credential` from spec §4.1.14 — credentials are the spec's term for issuance output. |
| `DECISION` audit row | `joget-status-framework`.`audit_log` | Out-of-scope (audit) | Fits as out-of-scope concern | |
| Snapshot-at-decision (architecture §5.5 #5, not yet built) | Pending Horizon-1 closeout | Spec design principle "Snapshot at decision time" (§5.5 #5) maps directly | Fits cleanly *when implemented* | |

### 4.C Cross-cutting concerns on the subsidy services

| Aspect | Where it lives today | `mm_*` mapping | Verdict | Notes |
|---|---|---|---|---|
| DSL eligibility engine (`rules-grammar` + `joget-rules-api` + `joget-rule-editor` + `subsidy-eligibility-runtime`) | Build-* set, dormant on application path | The DSL is a *parallel* rule grammar to the closed twenty-operator set. **Either the closed grammar is the canonical evaluator and the DSL is retired, or the DSL is canonical and the closed grammar's twenty operators are insufficient.** | **Does not fit** the closed-grammar property under the current architecture | The architecture-overview makes the DSL part of the strategic stack; the meta-model spec makes the closed twenty-operator grammar the only evaluator. These positions are inconsistent. The audit's recommended disposition is to enumerate the rules currently authored (or *expected* to be authored) in the DSL, project each onto the closed grammar, and accept the loss of expressivity for the rules that don't translate. The §5 stress test does this for representative cases. This is the most consequential strategic finding of the audit and is classified red until reconciled. |
| `farmer_application.yml` Service Catalogue metamodel (Horizon-1 closeout) | Pending | The `{serviceId}.yml` is the spec's machine-readable Service Catalogue (spec §3.1, §5.1, §5.4). Fits the spec's two-view model directly | Fits cleanly | |
| `application-engine-runtime` (build-012) — SeedingService, SeedingTabLoadBinder, ApplicationSeedingTool | Built and live | This *is* the engine — the spec's `reg-bb-engine`'s `MetaScreenElement` plus the seeding behaviour is exactly `application-engine-runtime`'s responsibility | Fits cleanly | The Lesotho plugin is a precursor to `reg-bb-engine`. |
| `decision-engine-runtime` + entitlement issuance | Built and live | Maps onto the analyst-built XPDL workflow's tool tasks per spec §5.2 | Fits cleanly | |
| `form-quality-runtime` rules over the application service | Built but dormant | Same as farmer-side: rules expressible in the closed grammar fit; rules requiring aggregation do not | Fits cleanly *for translatable rules* | Verified in §5. |
| `WorkflowActivator → DocSubmitter → ProcessingServer` triad with `farmer_application.yml` | Built (proven for `farmers_registry`); pending wiring on application path | Spec §3.1 + §5.1 — RegBB-conformant submission backbone | Fits cleanly | |
| Appeal flow (`spAppeal` 48 fields) | Separate citizen flow | Could be a separate `mm_service` (kind=`appeal`) referencing the original application; or out-of-scope (post-decision workflow handled by analyst-built XPDL with no new service) | Fits cleanly *under either disposition* | |

---

## 5. The cross-service test

This section tests the once-only principle (spec §3.1) against representative references the subsidy service makes into the farmer-registration record. For each, we name the Determinant rule shape and the `$registry.*` reference, and we flag any reference that requires aggregation, joins, or computation that the IM connector wouldn't naturally do.

The test's purpose is the closed-grammar honesty check. The closed twenty-operator set deliberately rejects `AVG`, `COUNT`, `SUM`, and `MEDIAN` (spec §4.3.3). Anything that *requires* those operators must either (a) be pre-computed by a derived-snapshot pipeline so the engine reads a single value, or (b) be rejected as a registration-BB concern. The honesty check is whether the rules a real subsidy service needs translate cleanly under (a), or whether the closed grammar is forcing too much computation up to data-prep.

### 5.1 Rule 1 — "Applicant is registered as an active farmer"

**Determinant:**
```json
{
  "scope": "eligibility",
  "rule": { "op": "eq", "left": "$registry.farmers.<by-NID>.status", "right": "ACTIVE" },
  "action": { "action": "applicable", "target": "registration:<this>" }
}
```
**Reference:** `$registry.farmers.<NID>.status`. Single-value lookup against the farmer registry. Fits cleanly. Requires the IM-connector capability `farmers.byNid` to be registered in `app_fd_reg_bb_im_capability` and to expose `status`.

### 5.2 Rule 2 — "Applicant resides in a district covered by the programme"

**Determinant:**
```json
{
  "scope": "eligibility",
  "rule": { "op": "in", "left": "$registry.farmers.<by-NID>.districtCode", "right": "$constant.targetDistricts" },
  "action": { "action": "applicable" }
}
```
where `$constant.targetDistricts` is compiled from the programme's `targetDistricts` checkbox (per §4.A.3) at publish.

**Reference:** `$registry.farmers.<NID>.districtCode`. Single-value lookup. Fits cleanly. The right-hand side is a compiled constant — closed against runtime injection per spec §4.3.4.

### 5.3 Rule 3 — "Applicant's total registered parcel size ≤ 2 ha"

**Determinant attempt 1 — direct aggregation:**
```json
{ "op": "lte",
  "left":  { "op": "add", "args": [ "$registry.parcels[ownerId=<NID>].sizeHa" ] },
  "right": 2 }
```

This requires the `add` operator to operate over the unbounded set `$registry.parcels[ownerId=<NID>]`. The closed grammar's `add` is defined as `n` numeric arguments — *known at expression construction time*, not a set comprehension. The audit's reading of spec §4.3.3 is that **set aggregation is not in the closed grammar**.

**Determinant attempt 2 — denormalised access:**
```json
{ "op": "lte", "left": "$registry.farmers.<by-NID>.totalParcelHectares", "right": 2 }
```

This works *only if* a derived-snapshot pipeline (the future `derived-snapshot-runtime` per architecture-overview §2.7) maintains `totalParcelHectares` on the farmer record. The current `farmer-derived-plugin` already computes this kind of snapshot for `spFarmerDerived`. Fits cleanly under denormalisation; **does not fit** under the closed grammar without it.

**Honesty verdict:** the closed grammar is *honest* — it correctly refuses to be a SQL substitute. The cost is a hard architectural commitment: every cross-service reference that requires aggregation must be materialised on the source-side registry, before the engine runs. The Lesotho project's existing `farmer-derived-plugin` is exactly this pattern. The audit endorses keeping the discipline.

### 5.4 Rule 4 — "Applicant has not received subsidy from this programme in the current season"

**Determinant attempt — filtered exists:**
```json
{ "op": "not",
  "arg": { "op": "exists",
           "arg": "$registry.applications[serviceCode=<thisCode>, season=$service.attributesJson.season, applicantId=<NID>, status='approved']" } }
```

The closed grammar's `exists` is unary on a `ref`. Filter syntax inside the ref (`[serviceCode=..., season=...]`) is not specified by the spec; spec §4.3.1 lists `$registry.civil.maritalStatus` as the canonical form, with no filter. **Does not fit** unless the IM connector exposes a pre-built capability `applications.byApplicantAndProgrammeAndSeason` returning a single Boolean.

**Honesty verdict:** same as Rule 3 — the registry must publish the answer-shape the engine needs. If it does, the rule is one `exists`. If not, the engine cannot answer.

### 5.5 Rule 5 — "Applicant is in a target group for this programme"

**Determinant — disjunction of equality / range / membership rules:**
```json
{ "op": "and", "args": [
  { "op": "eq", "left": "$registry.farmers.<NID>.gender", "right": "F" },
  { "op": "lt", "left": "$registry.farmers.<NID>.age", "right": 35 },
  { "op": "eq", "left": "$registry.farmers.<NID>.disability", "right": "Y" }
] }
```

Fits cleanly. Single-value references, closed-grammar operators. The age field requires the registry to expose `age` as a derived field (date-of-birth math is not in the closed grammar — `sub` between dates is not defined as date arithmetic). Same denormalisation discipline as Rule 3.

### 5.6 Rule 6 — "Applicant has at least one parcel under crop production"

**Determinant attempt — same denormalisation:**
```json
{ "op": "eq", "left": "$registry.farmers.<NID>.hasActiveCropParcel", "right": true }
```

Fits cleanly under denormalisation, does not fit otherwise. Same pattern as Rule 3.

### 5.7 Rule 7 — "Applicant has no outstanding loan default"

**Determinant:**
```json
{ "op": "eq", "left": "$registry.farmers.<NID>.creditDefault", "right": "N" }
```

Fits cleanly. The `creditDefault` field already exists on `farmerIncomePrograms` (Tab 6 of the farmer wizard). Single-value lookup.

### 5.8 Rule 8 — "Applicant has not appealed a decision in the last 12 months"

**Determinant attempt — temporal filter:**
```json
{ "op": "not", "arg": { "op": "exists",
  "arg": "$registry.appeals[applicantId=<NID>, submissionDate > $now() - P12M]" } }
```

`$now()` is not in the closed grammar's scopes. Date arithmetic is not in the operators. **Does not fit** without IM-connector pre-computation: the registry would expose `appeals.recentlySubmitted.byApplicant` returning a Boolean.

### 5.9 Rule 9 — "Applicant's parcel is in a programme-targeted agro-ecological zone"

**Determinant:**
```json
{ "op": "in",
  "left":  "$registry.parcels[ownerId=<NID>].first().agroEcologicalZone",
  "right": "$constant.targetZones" }
```

The `.first()` accessor is not in the spec. With the denormalisation discipline (`$registry.farmers.<NID>.primaryParcelZone`), fits cleanly. Without it, does not fit.

### 5.10 Rule 10 — "Applicant has provided declaration consent"

**Determinant:**
```json
{ "op": "eq", "left": "$applicant.declarationConsent", "right": true }
```

Fits cleanly. `$applicant.*` references resolve in-memory against the current application, no registry call.

### 5.11 Pattern summary

| Rule | Fits closed grammar? | Fits with denormalised registry shape? | Fits with no help? |
|---|---|---|---|
| 1. Active farmer | Yes | Yes | Yes |
| 2. District-covered | Yes | Yes | Yes |
| 3. Total parcel area ≤ 2 ha | No (set aggregation) | Yes | No |
| 4. No prior subsidy this season | No (filtered set lookup) | Yes (single-value capability) | No |
| 5. Target group | Yes | Yes (with `age` derived) | No (age requires DOB math) |
| 6. Active crop parcel | No | Yes | No |
| 7. No credit default | Yes | Yes | Yes |
| 8. No recent appeal | No (temporal filter) | Yes | No |
| 9. Parcel in target zone | No | Yes | No |
| 10. Declaration consent | Yes | Yes | Yes |

**Pattern:** of ten representative rules, four fit the closed grammar directly with no registry help, four fit only if the registry maintains a denormalised value, and two require pre-computed Boolean capabilities exposed by the IM connector. **None require an extension of the operator set.** The closed grammar is sufficient; the cost is a registry-side discipline of materialising aggregate, filtered, and temporal answers as single-value fields or single-value capabilities.

This is consistent with the spec's own honesty test at §4.3.3 — operators that require aggregation are deliberately excluded; the registry layer is where that work belongs. The Lesotho project's existing `farmer-derived-plugin` (the future `derived-snapshot-runtime`) already implements this discipline for the local case. Convergence does not require new operators; it requires committing to the denormalisation pattern as an architectural rule.

---

## 6. Consolidated verdict

### 6.1 Verdict counts

| Verdict | Farmer registration (§3) | Subsidy application (§4) | Combined |
|---|---|---|---|
| Fits cleanly | 41 | 42 | 83 |
| Fits with meta-model extension | 2 | 10 | 12 |
| Fits as out-of-scope concern | 21 | 19 | 40 |
| Does not fit | 3 | 4 | 7 |
| **Audit rows total** | **67** | **75** | **142** |

(Counts taken directly from the §3 and §4 tables; rows with compound topics — e.g. "Radio Y/N ×7" — count once.) The "Does not fit" rows are the consequential ones. They split into two classes:

### 6.2 Multi-service "fits with meta-model extension" — legitimately additive

These are extensions whose evidence appears across both services (or across Lesotho services and at least one other plausible registration domain). They are queued for additive treatment per spec §4.3.3 / §C3 — the closed-set extension protocol:

1. **Repeating sub-records inside a service** (`mm_field.widget=repeating_group` referencing a child `mm_screen` set). Evidence: household members (farmer service), livestock (farmer service), parcel crops (parcel service), seeded eligibility / benefit / document grids (subsidy application). Four occurrences across three services. Strongest legitimately-additive case in the audit.
2. **`mm_benefit` as a sibling of `mm_fee`** (kind=`charge|subsidy|in_kind` on a `mm_fee_or_benefit` parent, or two parallel entities). Evidence: programme benefits (`spProgramBenefits`, `spBenefitItemRow`), application benefit requests (`spApplicationBenefitReq`), decision-time grant issuance (`imEntitlement`), and any subsidy / cash-transfer / in-kind-distribution programme outside Lesotho. Architecturally heaviest single addition in the audit. Without it, the meta-model cannot honestly express subsidy services.
3. **Cascading select** (`mm_field.optionsFilterJson` or `mm_determinant action=filterOptions`). Evidence: farmer residency (resource_centre cascade=district), parcel location (resource_centre cascade=district), programme geography (collectionPointCode cascade=districtCode), crops (cropType cascade=crop_category), livestock (livestockType cascade=livestockCategory). Five occurrences; the most frequent UI pattern in the JWA.
4. **`mm_registration.attributesJson`** for registration-level typology and configuration that drive the catalogue rather than Determinants (programmeType, seasonCode, campaignType). Evidence: subsidy service has 4 registrations differing in these facts; any service whose catalogue entries need faceted filtering would too. Multi-registration evidence within one service. *(Placement clarified by decision-log D16: under D4's Single-Window framing, programme-level facts vary per registration, not per service.)*
5. **`mm_registration.acceptanceWindowFrom`/`acceptanceWindowTo`** for time-windowed application registrations. Evidence: all 4 subsidy programmes carry `applicationStartDate`/`applicationEndDate`; any call-for-applications service does similarly. *(Placement per D16: registrations have windows, not the umbrella service.)*
6. **`mm_required_doc.captureFieldsJson`** for per-document metadata fields the citizen fills (issue date, expiry, document number, issuing authority). Evidence: subsidy application document tab, and any document-bearing service. Two-domain evidence.
7. **Typeahead binder** (`mm_field.optionsBinderJson` with paged-search semantics, no widget extension needed if `MetaScreenElement` interprets the binder kind). Evidence: programme picker (subsidy), parcel→farmer link, citizen→service catalogue. Three-domain evidence; the recommendation is to handle this without a new widget value, treating it as a binder-side concern.
8. **Scored eligibility** (`mm_registration.evaluationStrategy ∈ {all_must_pass, score_based}`, `mm_registration.minimumScore`, `mm_registration.passingThreshold`, `mm_determinant.score`, `mm_determinant.ruleType ∈ {inclusion, exclusion, priority, bonus}`). Evidence reframed by the 2026-04-28 programme screening: three of four programmes (prog001, prog002, prog003) have `SCORE_BASED` configured — multi-service evidence. **Adopted by decision-log D6 (was refused under audit-time single-service evidence).**

### 6.3 Single-service "fits with meta-model extension" — drift-mode-1 candidates, refused

These extensions appear only on one service and would expand the meta-model to accommodate a single domain. The audit refuses them; each gets a downstream disposition:

- **`mm_field.computedFromJson`** for ConcatField-style display labels (only `personal_information` on `farmerBasicInfo`). Refused — render the label inline in `MetaScreenElement` from a templated header, no new field.
- **Scored eligibility** — refusal **reversed 2026-04-28** by decision-log D6 on multi-service evidence surfaced by the programme screening; now classified as legitimately additive at §6.2 #8.
- **Per-criterion `score`, weight, and quota numerics** on target groups — only on the subsidy service. Refused at the meta-model level; treat as out-of-scope (programme administration).

### 6.4 "Does not fit" — the consequential findings

Seven rows in the §3+§4 tables are classified as does-not-fit; they cluster into three substantive findings, plus two stress-test findings that surface only in §5. Each is named here with its disposition:

1. **Repeating sub-records** (§3.A.4 household, §3.A.5 livestock, §3.B.4 parcel crops, §4.B.3 application benefits — 4 rows). Disposition: accepted as legitimately-additive (extension #1 above).
2. **`mm_benefit` for subsidy benefits** (§4.A.2 benefit configuration, §4.A.5 benefit items, §4.B.3 application benefit requests — 2 rows where the meta-model gap is named, plus shared evidence with #1). Disposition: accepted as legitimately-additive (extension #2 above).
3. **DSL eligibility engine vs closed twenty-operator grammar** (§4.C — 1 row). Disposition: red flag — the project carries two parallel rule grammars and must reconcile. The audit's recommendation is to pin the DSL's expressible-rule set against the closed grammar and either (a) retire the rules that don't translate (accept the loss of expressivity), (b) retire the DSL stack entirely in favour of the closed grammar, or (c) accept that the convergence target is *the DSL grammar*, not the closed twenty-operator set, and re-author the spec accordingly. This decision must precede any engine implementation work. Single most consequential finding of the audit.
4. **Set aggregation in cross-service references** (§5.3, §5.4, §5.6, §5.8, §5.9). Disposition: accepted as a registry-side discipline; the closed grammar is honest. Requires a hard architectural rule that aggregate, filtered, and temporal answers are pre-computed on the source registry (the future `derived-snapshot-runtime`).
5. **Date arithmetic / `$now()`** (§5.5, §5.8). Disposition: accepted as a registry-side discipline; the registry exposes `age`, `monthsSinceLastAppeal`, etc., as denormalised fields. The closed grammar does not get date math.

### 6.5 Plain-language verdict

**Amber, leaning to green pending one decision.**

Both services fit the meta-model with at most six legitimately-additive extensions, all carrying multi-service evidence, all conformant to the closed-set extension protocol. The closed twenty-operator grammar is sufficient for representative subsidy rules under the denormalisation discipline already practised in `farmer-derived-plugin`. The shape decision in §2 (Shape B — two services, registry-to-registry reference) carries the rest of the audit without strain.

**The amber qualifier is the DSL/closed-grammar reconciliation in §6.4 #3.** Until it is decided whether the closed twenty operators or the DSL grammar is the canonical evaluator, the convergence direction is ambiguous in a way that affects engine design, plugin retirement, and operator authoring UX. The audit cannot turn green on this question — the question is strategic, not structural, and must be decided on grounds the audit cannot address from the data alone.

The recommended next step is therefore **not** to begin engine implementation work, but to author a one-page decision memo answering the DSL vs closed-grammar question with explicit reasoning. After that decision lands, the verdict can be re-pronounced as green and Phase 1 of the convergence (a thin engine standing up against `mm_*` records) can begin.

---

## 7. Decisions taken in this audit

| # | Decision | Evidence | Alternative considered | Cost accepted |
|---|---|---|---|---|
| A1 | Shape B (two services, registry-to-registry reference) is the working assumption for farmer ↔ parcel separation | `architecture-overview.md` §2.2 already runs them as two `qa_service` registrations; cross-service reference pattern in place via `farmerByNid` resolver; parcel operations are operationally first-class | Shape A (parcels as repeating section inside one farmer service) would require a meta-model widget extension *and* would lose registry-addressability for parcels in subsidy rules | Single residual extension (`repeating_group`) for household members and crops/livestock inside the farmer service — accepted as legitimately-additive in §6.2 |
| A2 | Render-only computed labels (ConcatField, CustomHTML headers) classified as out-of-scope, not as `mm_field` extensions | Only one occurrence in the JWA (`personal_information`); strictly UX | Add `mm_field.computedFromJson` | Refused as drift-mode-1 (single-service evidence) |
| A3 | Server-generated PKs (`FR-??????`, `PC-??????`, `AP-??????`, `EN-??????`) classified as out-of-scope, treated as engine PK-generator config | Spec already generates `APP-<6char>` runtime IDs; the Lesotho prefixes are domain-presentation choices | Add `mm_service.idPattern` | Refused — operationally fine to keep as engine config; not a meta-record |
| A4 | Cascading dropdowns named as a legitimately-additive extension on multi-service evidence (5 occurrences) | residency, parcel location, geography, crops, livestock — all use cascade=parent | Reject and flatten the catalogues at authoring time | Refused — cascading is a structural UX property of master-data hierarchies, not a one-off |
| A5 | Subsidy benefits (`mm_benefit`) named as a legitimately-additive extension because the closed `mm_fee` cannot honestly express benefits-paid | `spProgramBenefits` + `spBenefitItemRow` 13 fields per row; application-side seeded benefit requests; decision-time `imEntitlement` issuance — three runtime touchpoints | Reinterpret `mm_fee` with signed amounts | Refused — collapsing charge/subsidy semantics into one entity with a sign field is a category error: a charge has a payer, a subsidy has a beneficiary, payment-method mechanics differ |
| A6 | Programme M&E and budget allocation classified as out-of-scope (not registration BB concerns) | Multiple tabs (Monitoring, parts of Timeline, district allocations) covered | Stretch `mm_*` to model budget — requires extensive entity additions | Refused — pushes the meta-model beyond registration into financial administration; better handled by separate downstream plugins |
| A7 | DSL eligibility engine flagged as a red item requiring a strategic decision before convergence proceeds | Two parallel rule grammars (closed twenty-operator and the DSL) cannot both be canonical; spec §2.1 P2 says "operator set is closed" | Either retire the DSL stack, or expand the closed grammar, or re-author the spec to make the DSL canonical | Cost: the convergence Phase 1 cannot start until this is decided |
| A8 | Aggregation, filtered lookup, and temporal queries are accepted as a registry-side denormalisation discipline (the `derived-snapshot-runtime` pattern) | 6 of 10 stress-test rules in §5 require pre-computation; `farmer-derived-plugin` already implements this | Add aggregation operators (`SUM`, `COUNT`, `AVG`) — explicitly rejected by the spec at §4.3.3 | Cost: every cross-service rule requiring aggregation imposes a corresponding data-prep commitment on the source registry; this becomes a hard architectural rule |
| A9 | `farmer_application` reclassified — it is a runtime artefact (the citizen flow over `farmers_subsidy` services), not a `mm_service` | Naming asymmetry in current `qa_service` registrations does not reflect the meta-model's intent | Keep `farmer_application` as a `mm_service` | Refused — would require the meta-model to model "the act of applying" as a service, which collides with how `mm_service` is defined in spec §4.1.2 |
| A10 | Operator-side fields on citizen forms (`registrationStation`, `registrationChannel` on declaration; `verifiedBy`, `verificationDate` on documents; reviewer/approver fields on application) are reclassified to `mm_role_screen` operator sections | Spec §4.1.12 explicitly carries operator screens with role-scoped sections | Leave them on the citizen `mm_screen` with `visibilityDeterminant` | Refused — produces a screen the citizen cannot meaningfully interact with; cleaner to reclassify |

---

## 8. What this audit does not commit to

This audit produces a verdict on whether the twelve `mm_*` entities and the closed twenty-operator grammar can express farmer registration and subsidy application. It does not produce, and authorises no claim about:

- a migration plan from the current Lesotho codebase to the metadata-driven engine (no sequence of plugin retirements, no cutover criteria, no risk-and-rollback plan);
- a Determinant rule library for either service (the §5 stress-test rules are illustrative, not authoring-ready);
- specific `mm_*` schema changes (the extensions named in §6.2 are evidence-classified, not specified);
- a workflow design in XPDL for either service (the binding contract from spec §5.2 is acknowledged, not authored);
- a decision on the DSL eligibility engine vs closed-grammar reconciliation flagged in §6.4 #3 — the audit names it as the consequential strategic decision but does not resolve it.

A non-red verdict authorises the next layer of work (engine prototyping, schema specification, rule authoring, workflow design, plugin retirement planning). None of those layers belong in this document. They are downstream artefacts of a settled audit.

---

*End of document. The verdict in §6.5 is the conclusion this document was built to reach.*
