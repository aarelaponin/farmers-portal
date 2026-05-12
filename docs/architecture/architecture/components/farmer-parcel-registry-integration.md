# Component SAD — Farmer + Parcel registry (integration surface)

| Field             | Value                                                                                                                                                                                                                                                                                                                                                            |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | Farmer registry + Parcel registry — integration surface                                                                                                                                                                                                                                                                                                          |
| Document title    | Software Architecture Description (component level — integration contract)                                                                                                                                                                                                                                                                                       |
| Version           | 1.0 — DRAFT                                                                                                                                                                                                                                                                                                                                                       |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                        |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                                |
| Related           | Solution-level SAD; subsidy-module SAD; IM-module SAD; framework SAD (`$registry.*` reference scope); existing JWA `APP_farmersPortal-*.jwa` (the live form definitions); `_03_Development/_07_Architecture/Land_Administration_Domain_Model.pdf` (LADM domain reference for parcels).                                                                          |
| What this is      | The component-level SAD describing the **integration contract** between the existing native-Joget farmer + parcel registries and the rest of the Lesotho Farmers Portal. **The registries are explicitly NOT being migrated to RegBB or to the MM-form-gen kernel.** This document records what consumers (subsidy module, IM module, reporting engine) read from them and what changes to those forms would break those consumers. |
| What this is not  | A re-design of the registries. A migration plan to MM-form-gen. A specification of the registries' internals — those live in the JWA. A LADM conformance audit (a future exercise; this SAD names the alignment surface).                                                                                                                                       |

---

## 1. Introduction and goals

### 1.1 Purpose and scope

The farmer registry and parcel registry are **the working core** of the Lesotho Farmers Portal. They were built before the convergence work began and they continue running, in production, as native Joget forms. Per the convergence framework's scope decision (D3) and the revised long-term roadmap (this session, May 2026), they remain **architecturally final** as native Joget — they are not on a migration path to RegBB or to the kernel.

What other modules need from them is read access to specific columns, addressed by NID. This SAD documents:

- The tables and columns RegBB and IM read.
- The integration mechanism (`$registry.*` reference scope in the rule grammar).
- The stability contract — what changes in the registries would break consumers.
- The LADM (Land Administration Domain Model) alignment for parcel data.
- What the registries do NOT promise (and what consumers must therefore not assume).

In scope:

- The integration surface presented by `farmerbasicinfo`, `farms_registry`, `farm_location`, `farmerHousehold`, `farmerAgriculture`, `parcelRegistration`, `parcelGeometry`, `parcelLocation` and friends.
- The capability adapters (`farmers.byNid`, `farmers.parcels.summary`, `households.vulnerability` per D7) that the framework's SqlPathEvaluator uses to resolve `$registry.*` references.
- The stability contract for these tables' column sets.
- The LADM alignment posture for parcels.

Out of scope:

- The registries' UX (it's hand-built Joget; documented in the existing form definitions and the legacy `architecture-overview.md`).
- The registries' validation rules (handled by `form-quality-runtime` and the form definitions themselves; orthogonal to convergence).
- A re-design of the farmer registration wizard (no plans).
- A re-design of the parcel registry's GIS polygon capture (works fine; remains hand-built).

### 1.2 Quality goals (component-level)

| Rank | Quality goal                          | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                                       |
| ---- | ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | **Stability of the integration surface** | The columns and data shapes that consumers depend on do not change without a coordinated cross-module update. Renaming `c_national_id` to `c_nid` would break every `$registry.farmer.*` rule across the system; such changes are de-facto disallowed without a migration plan. Target: zero unannounced column renames, deletions, or type changes on the integration surface.                  |
| 2    | **Performance — registry read by NID**  | A subsidy application's eligibility evaluation may issue 1–5 SQL reads against the farmer/parcel registries (one per `$registry.*` reference). Each read is by `c_national_id` — which is the form's natural key. Target: P95 < 50 ms per registry read on the live database.                                                                                                                          |
| 3    | **Read-only consumers**               | The framework, IM, and reporting engine NEVER write to the registries. This is enforced architecturally — the `$registry.*` reference scope is strictly read-side. The registries write themselves; nothing else does.                                                                                                                                                                                |
| 4    | **LADM alignment posture (parcels)**   | The parcel registry's identifier structure, tenure type vocabulary, and geometry representation are aligned with LADM concepts where they overlap. Full LADM conformance is a future exercise; today the alignment is documented but not certified. Target: when LADM conformance becomes an explicit ask, the gap is documentable in days, not weeks.                                              |
| 5    | **Sustainability — registries do not require convergence-team attention** | The registries are owned by their original team (or by ops). The convergence team consumes them, doesn't maintain them. A clean integration boundary means the convergence team can ship subsidy + IM features without scheduling time on the registry team's calendar.                                                                                                                              |

### 1.3 Stakeholders

| Stakeholder                          | Concerns                                                                                                                                                              |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Registry owners (existing team)** | That the registries continue working unchanged; that consumers don't reach into them in unsupported ways; that any consumer-driven change request is explicit.         |
| **Subsidy module**                  | That `$registry.farmer.*` and `$registry.parcels.*` work for the columns the rules reference.                                                                          |
| **IM module**                       | That `app_fd_farmerbasicinfo` is readable for voucher holder identity verification; that the column shape doesn't change.                                              |
| **Reporting engine**                | That cross-module reports can join `app_fd_subsidy_app_2025` to `app_fd_farmerbasicinfo` reliably.                                                                     |
| **GovStack reviewers**               | That LADM alignment for parcel modelling is on a credible path, even if not certified today.                                                                            |
| **Auditor**                         | That registry data is the source of truth for "is X a registered farmer" claims; that the audit trail of registry edits exists in the registry's own audit mechanisms. |

---

## 2. Architecture constraints

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                                                                |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| RG-C1 | **Read-only from convergence consumers.** The framework's SqlPathEvaluator, IM workflow steps, and reporting engine all use `SELECT` only. No `UPDATE` / `INSERT` / `DELETE` via the integration surface.                                                                                                                                                                                                                |
| RG-C2 | **Reads by national_id.** The integration is keyed by `c_national_id`. Other lookups (by name, by location) are reporting-engine concerns and don't constitute integration.                                                                                                                                                                                                                                              |
| RG-C3 | **The registries do not migrate to MM-form-gen or RegBB.** This is a permanent choice (as of the May 2026 roadmap update). Any reversal requires re-opening the convergence framework's scope decision (D3) and authoring a fresh migration plan.                                                                                                                                                                          |
| RG-C4 | **Hard rule — no raw write SQL.** Same as everywhere: the registries' `app_fd_*` tables are Joget-managed; no consumer ever writes to them via raw SQL. (The registries themselves, of course, write to them — through Joget's storeBinder pipeline.)                                                                                                                                                                       |
| RG-C5 | **National_id is treated as PII.** Reads of `c_national_id` from the registries are subject to the same access-control model as any PII-bearing column: gated by Joget user/role membership, audited at the application layer (the framework's `reg_bb_eval_audit` records every NID-keyed read in the context of an evaluation).                                                                                              |
| RG-C6 | **LADM alignment is a guiding posture, not a present claim.** Parcel identifiers, tenure type values, and geometry representation are designed to be compatible with LADM but the registries are not LADM-certified. Statements about LADM conformance in external materials must be careful to call this out.                                                                                                              |

---

## 3. Context and scope

### 3.1 The integration surface

```
   ┌──────────────────────────────────────────────────────────────────┐
   │  Subsidy module     IM module     Reporting engine               │
   │  (RegBB rules,      (voucher      (cross-module                  │
   │   eval, audit)       redemption)   reports)                      │
   └────────────────┬────────────────┬──────────────────┬─────────────┘
                    │                │                  │
                    └────────────────┼──────────────────┘
                                     │
                          $registry.* reference   |  joined SELECT
                          via SqlPathEvaluator    |  in datalist SQL
                                     │                  
                                     ▼                  
   ┌──────────────────────────────────────────────────────────────────┐
   │           INTEGRATION SURFACE — read-only contract               │
   │                                                                  │
   │  Tables (Postgres):                                              │
   │    app_fd_farmerbasicinfo                                        │
   │    app_fd_farms_registry         (composite — wizard parent)     │
   │    app_fd_farm_location          (parent farmer's farm location) │
   │    app_fd_farmerHousehold                                        │
   │    app_fd_farmerAgriculture                                      │
   │    app_fd_household_members                                      │
   │    app_fd_livestockDetailsForm                                   │
   │    app_fd_parcelRegistration                                     │
   │    app_fd_parcelGeometry                                         │
   │    app_fd_parcelLocation                                         │
   │    app_fd_parcelClassification                                   │
   │                                                                  │
   │  Capability adapters (in framework's SqlPathEvaluator):          │
   │    farmers.byNid                                                 │
   │    farmers.parcels.summary  (Phase 1 close-out)                  │
   │    households.vulnerability  (D7 — capability adapter)           │
   └──────────────────────────────────────────────────────────────────┘
                                     ▲
                          writes by Joget through                   
                          the registries' OWN forms                  
                                     │                  
   ┌──────────────────────────────────────────────────────────────────┐
   │  Citizen self-registration wizard (farmerRegistrationForm)       │
   │  Operator-driven parcel registration (parcelRegistration)        │
   │   — both are native-Joget hand-built forms, NOT touched by       │
   │     convergence work                                             │
   └──────────────────────────────────────────────────────────────────┘
```

### 3.2 Consumers and what they read

| Consumer            | Reads                                                                                                                                                                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Subsidy module — applicability rules | `app_fd_farmerbasicinfo` columns (`c_first_name`, `c_last_name`, `c_date_of_birth`, `c_gender`, `c_national_id`, `c_contact_phone`, `c_district`, `c_village`). Used by `$registry.farmer.*` references (e.g. `DET_FARMER_REGISTERED`).                                                       |
| Subsidy module — area-based rules | `app_fd_parcelRegistration` aggregated to `farmers.parcels.summary` capability (Phase 1 close-out). Used by area-based rules (e.g. `$registry.parcels.summary.cultivated_total <= 5`).                                                                                                                                                                |
| Subsidy module — vulnerability | `app_fd_household_members` aggregated to `households.vulnerability` capability (D7, deferred to Phase 2). Used by social-protection programmes (vulnerable households get higher score).                                                                                                                                                                                |
| IM module           | `app_fd_farmerbasicinfo` for voucher-holder identity verification; voucher row holds `farmer_nid`, IM forms display the farmer's name from this lookup.                                                                                            |
| Reporting engine    | All registry tables, joined freely. Cross-module reports like "farmers in registry who haven't applied for any 2025 subsidy" rely on this.                                                                                                          |

### 3.3 Non-consumers

The kernel does NOT consume the registries. The framework's REST endpoints (`/regbb/eval`, `/regbb/submit`) accept applicant data from the caller and don't go to the registry by themselves — it's the framework's storeBinder + SqlPathEvaluator inside the engine that reads the registry, and only when a rule references `$registry.*`.

---

## 4. Solution strategy

### 4.1 Read-only via `$registry.*` reference scope

The framework's rule grammar supports four reference scopes:

- `$applicant.*` (or bare identifiers) — values from the in-flight application row.
- `$constant.*` — system constants (currently unused).
- `$service.*` — service-level metadata (currently unused).
- **`$registry.<entity>.<field>`** — reads from the named registry's tables via a registered capability adapter.

When a rule contains `$registry.farmer.first_name`, the `RoutingEvaluator`'s AST analysis routes the rule to the `SqlPathEvaluator`. SqlPathEvaluator looks up the capability adapter for `farmer`, executes its SQL (`SELECT c_first_name FROM app_fd_farmerbasicinfo WHERE c_national_id = ?`), substitutes the result into the rule, then delegates to the fast path for the comparison.

Today the table mapping is hand-coded in `SqlPathEvaluator.java`. Phase 1 close-out generalises this to a `CapabilityAdapter` interface registered via OSGi service. The interface contract:

```java
interface CapabilityAdapter {
    String getCapabilityName();   // e.g. "farmer", "parcels.summary", "households.vulnerability"
    Map<String, Object> resolve(String nationalId, EvalContext ctx);
}
```

A capability adapter for the parcel summary aggregates parcel rows. **Important caveat verified against the live registry (May 2026):** `c_area_hectares` lives on `app_fd_parcelgeometry`, not on `app_fd_parcelregistration`. The two tables are linked through a wizard-parent chain: `farmerbasicinfo (NID) → parcelregistration.c_parent_id → parcelgeometry.c_parent_id`. The actual SQL the `ParcelsSummaryAdapter` runs is therefore a three-table join through the geometry table, not the single-table aggregate the original sketch above implied. Today the linkage column `parcelregistration.c_parent_id` is empty in 184/184 live rows — a data-integrity gap that means every farmer resolves to `parcel_count = 0` until the registration→farmer FK is populated. The grammar handles that correctly (rules return FALSE for "has parcels"), and the L2-1 test seed exercises this path end-to-end.

### 4.2 No write integration

There is no write path from convergence consumers to the registries. If a subsidy application's review reveals data missing from the registry (e.g. the citizen's contact phone is empty), the operator's options are:

- Note it in the operator decision comment, send the applicant back via `send_back` decision with a request to update their farmer registration.
- Escalate to the registry team for a manual correction.

The framework deliberately does NOT offer "fix the registry from inside the subsidy app" because that would couple the modules and violate the read-only contract.

### 4.3 LADM alignment posture for parcels

LADM (ISO 19152) defines a domain model for land administration. The Lesotho parcel registry's design — predating the LADM-alignment formalisation — happens to be compatible with LADM concepts on the integration surface:

| LADM concept                  | Parcel registry mapping                                                                                                |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `LA_SpatialUnit`              | `parcelRegistration` row                                                                                               |
| `LA_Party`                    | The farmer (`farmerbasicinfo` row), referenced from `parcelRegistration.farmer_id`                                     |
| `LA_RRR` (Right/Restriction/Responsibility) | `parcelRegistration.tenure_type` — values align with LADM tenure-type vocabulary                                |
| `LA_LandUse`                  | `parcelRegistration.primary_crop` (cropland use)                                                                        |
| Geometry representation       | `parcelGeometry.polygon_wkt` (Well-Known Text), centroid lat/lon, area_hectares                                         |

Full LADM conformance (spatial unit hierarchies, multi-party rights, baseline-survey traceability) is not claimed. The current alignment posture is "the integration surface is LADM-shaped enough that future conformance work is bounded."

This SAD does NOT migrate the parcel registry to a LADM-conformant schema. That would be a separate, scoped project.

### 4.4 Consumer responsibility for staleness

The registries are read whenever a rule references them. There's no caching layer in the framework's SqlPathEvaluator beyond L2 (which keys on `applicationId + ruleVersion + dataHash`, so a registry change after evaluation doesn't invalidate the cache).

If a citizen registers a farmer profile, then immediately submits a subsidy application, the application's eligibility evaluation reads the just-written farmer profile — no staleness. If a registry change happens AFTER an application's eligibility evaluation, the audit row records the registry value at evaluation time; future re-evaluations would see the new value.

This is the right semantics: the audit is the historical truth ("this is what the rule saw on this date"); the registry is the present truth.

---

## 5. Building block view

### 5.1 Tables on the integration surface

The full inventory of registry tables (per the JWA), with which are integration-surface (read by consumers):

| Table                                | Owned by         | Read by consumers? | Notes                                                                        |
| ------------------------------------ | ---------------- | ------------------- | ---------------------------------------------------------------------------- |
| `app_fd_farmerbasicinfo`             | Farmer registry  | **YES** (heavy)     | Identity columns; the most-read registry table.                               |
| `app_fd_farms_registry`              | Farmer registry  | YES (light)         | Composite wizard parent; the canonical farmer registration record.            |
| `app_fd_farm_location`               | Farmer registry  | YES (light)         | Farm location associated with a farmer (district, village).                   |
| `app_fd_farmerHousehold`             | Farmer registry  | NO (today)          | Household composition; will be read by `households.vulnerability` (D7).       |
| `app_fd_household_members`           | Farmer registry  | NO (today)          | Per-member rows; aggregated into the vulnerability capability.                |
| `app_fd_farmerAgriculture`           | Farmer registry  | YES (light)         | Crops grown, livestock owned; potentially used by `farmers.crops` rules.      |
| `app_fd_livestockDetailsForm`        | Farmer registry  | NO (today)          | Detailed livestock; not yet referenced by any rule.                           |
| `app_fd_parcelRegistration`          | Parcel registry  | YES (medium)        | Parcel-level data; aggregated by `farmers.parcels.summary`.                  |
| `app_fd_parcelGeometry`              | Parcel registry  | NO (today)          | Polygon WKT + centroid; not yet referenced.                                  |
| `app_fd_parcelLocation`              | Parcel registry  | YES (light)         | Parcel location (district, village) — sometimes used by geographic rules.    |
| `app_fd_parcelClassification`        | Parcel registry  | NO (today)          | Soil class, slope; not yet referenced.                                       |
| `app_fd_cropDetailForm`              | Parcel registry  | NO (today)          | Per-parcel crops; potentially used in future expansion.                      |

### 5.2 Capability adapters

| Capability                       | What it returns                                                                                                                | Implemented?                                          |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------- |
| `farmer.byNid` (= `$registry.farmer.*`) | Single farmer row keyed by national_id. Returns the requested column (e.g. `first_name`, `gender`, `date_of_birth`).         | Phase 1 — yes, hand-coded mapping in SqlPathEvaluator |
| `farmers.parcels.summary`        | Aggregate over a farmer's parcels: `cultivated_total` (sum of area_hectares), `parcel_count`, `gisVerified` (bool).            | Phase 1 close-out                                     |
| `households.vulnerability`       | Aggregate over a farmer's household: `dependants_under_18`, `chronically_ill_count`, `disability_count`, `orphans_count`.      | Phase 2 (per D7)                                       |
| `farmers.subsidies.history`      | List of past subsidy applications for the farmer; useful for "no double benefit" rules.                                         | Future, not scoped                                     |

When the capability registry is generalised (Phase 1 close-out), each capability lives as a `CapabilityAdapter` registered via OSGi service. New capabilities are added without touching `SqlPathEvaluator`.

### 5.3 Stable-column contract per table

Phase 1 close-out should produce a formal stability spec listing every column on the integration surface and its stability promise. The current state is:

`app_fd_farmerbasicinfo` (the most consequential):

| Column              | Stability  | Used by rule grammar as       | Notes                                                                |
| ------------------- | ---------- | ----------------------------- | -------------------------------------------------------------------- |
| `c_national_id`     | LOCKED     | The lookup key; unchangeable. | Foundational; renaming is a multi-system refactor.                   |
| `c_first_name`      | STABLE     | `$registry.farmer.first_name` | Used by `DET_FARMER_REGISTERED` today.                                |
| `c_last_name`       | STABLE     | `$registry.farmer.last_name`  |                                                                      |
| `c_date_of_birth`   | STABLE     | `$registry.farmer.dob`        | (Mapped to `dob` in capability for brevity)                          |
| `c_gender`          | STABLE     | `$registry.farmer.gender`     |                                                                      |
| `c_district`        | STABLE     | `$registry.farmer.district`   | Lookup of district code per `md03district`.                          |
| `c_village`         | STABLE     | `$registry.farmer.village`    | Lookup of village code per village MD form.                          |
| `c_contact_phone`   | STABLE     | (not used in rules today)     | Used for IM SMS notifications.                                       |
| `c_email`           | OPTIONAL   | (not used in rules today)     |                                                                      |

`app_fd_parcelRegistration`:

| Column                | Stability  | Used in capability   | Notes                                                                |
| --------------------- | ---------- | -------------------- | -------------------------------------------------------------------- |
| `c_parcel_code`       | LOCKED     | (parcel lookup key)  |                                                                      |
| `c_farmer_id`         | LOCKED     | Joins to farmer NID  |                                                                      |
| `c_district`          | STABLE     | `parcels.summary`    |                                                                      |
| `c_agro_zone`         | STABLE     | `parcels.summary`    |                                                                      |
| `c_area_hectares`     | STABLE     | `parcels.summary`    | Aggregated as `cultivated_total`. **Lives on `app_fd_parcelgeometry`, not `app_fd_parcelregistration`** — verified May 2026. The summary adapter joins geometry → registration → farmerbasicinfo. |
| `c_tenure_type`       | STABLE     | (read-only)          | LADM-aligned values.                                                 |
| `c_primary_crop`      | STABLE     | (read-only)          |                                                                      |

**Linkage gap in live data (May 2026):** `app_fd_parcelregistration.c_parent_id` (the wizard-parent FK to `app_fd_farmerbasicinfo.id`) is empty in 184/184 live rows. Until that linkage is populated, every farmer resolves to `parcels.summary.parcel_count = 0` and `cultivated_total = 0`. Rules that depend on these aggregates (DET_SMALLHOLDER's "≤ 5 ha cultivated total", DET_AREA_NONZERO's "> 0 ha cultivated") evaluate against zero, returning the appropriate FALSE/NULL per grammar semantics. Capability adapter dispatch is verified end-to-end (TEST_FARMER_HAS_PARCELS rule, build-056); the missing data is a registry-data-integrity issue, not a framework issue.

Stability levels:

- **LOCKED**: changing this column requires a coordinated cross-system refactor with explicit project sign-off.
- **STABLE**: changes (renames, type changes) are possible but require updating every consumer in the same release cycle. Owners notify consumers in advance.
- **OPTIONAL**: column may be absent in older rows; consumers handle null.

Adding new columns to a registry table is unrestricted (no consumer breaks).

---

## 6. Runtime view

### 6.1 Subsidy eligibility evaluation hits the farmer registry

```
1. Citizen submits a subsidy application with national_id="1995022700567"
2. RegBbApplicationStoreBinder runs; aggregates rules for the programme
3. One of the rules: DET_FARMER_REGISTERED — rule source: $registry.farmer.first_name != ""
4. RoutingEvaluator → AST contains $registry.* → SqlPathEvaluator
5. SqlPathEvaluator:
   a. Reference: $registry.farmer.first_name
   b. Lookup: capability="farmer", field="first_name", key=NID
   c. SQL: SELECT c_first_name FROM app_fd_farmerbasicinfo WHERE c_national_id = '1995022700567'
   d. Result: "Tšepiso"
   e. Substitute back into rule: "Tšepiso" != "" 
   f. Delegate to FastPathEvaluator → TRUE
6. AuditWriter writes a row recording: rule source, $registry-resolved values, outcome=TRUE
7. Aggregator continues with next rule
```

The audit row's `c_outputs_json` carries the resolved registry value, so re-running the rule against the audit data alone (without re-querying the registry) reproduces the outcome.

### 6.2 IM voucher lookup hits the farmer registry

```
1. Extension officer redeems voucher; Joget loads the voucher row
2. Voucher's farmer_nid = "1995022700567"
3. Voucher edit form has a "Farmer name" cell rendered with OptionsValueFormatter
   pointing at app_fd_farmerbasicinfo with idColumn=national_id, labelColumn=full_name
4. Joget runs SELECT c_full_name FROM app_fd_farmerbasicinfo WHERE c_national_id = ?
5. Officer sees "Tšepiso Khabo"; proceeds with redemption
```

### 6.3 Reporting cross-module join

```sql
SELECT
  f.c_first_name || ' ' || f.c_last_name AS farmer_name,
  f.c_district,
  count(distinct s.id) AS subsidy_apps,
  sum(CASE WHEN s.c_status = 'approved' THEN 1 ELSE 0 END) AS approved
FROM app_fd_farmerbasicinfo f
LEFT JOIN app_fd_subsidy_app_2025 s ON s.c_national_id = f.c_national_id
GROUP BY f.c_national_id, f.c_first_name, f.c_last_name, f.c_district
ORDER BY approved DESC, subsidy_apps DESC
```

A reporting datalist runs this SQL as its `JdbcDataListBinder` source; renders the result.

---

## 7. Deployment view

The registries are deployed as part of the existing Joget application — they're hand-built forms in the JWA. Their lifecycle is independent of the convergence work:

- The JWA `APP_farmersPortal-*.jwa` contains all the registry forms, datalists, userviews.
- Updates to a registry form happen through Joget's normal authoring (App Composer) or via `form-creator-api` if the registry team adopts the same versioning pattern (currently they don't — the registries remain in the JWA and edits are App Composer ad-hoc).

The integration surface this SAD describes is **table columns** in the database, not deployable artefacts. The contract holds as long as the column shape holds.

---

## 8. Cross-cutting concepts

### 8.1 What if the registry team changes a column?

Procedure:

1. Registry team announces planned change (rename, type change, deletion) ≥ 1 sprint in advance.
2. Convergence team identifies all consumers (search for the column name across rules, datalist SQL, capability adapters).
3. Joint decision: (a) rename in registries + update all consumers in the same release, OR (b) keep both columns during a transition period, deprecate the old one.
4. Release.

The "no unannounced changes" rule is enforced by social/process discipline, not by code. The mitigation is documentation (this SAD), reviews, and a culture that takes the registry-consumer relationship seriously.

### 8.2 What if a consumer wants a new column?

Procedure:

1. Consumer specifies the column they need (name, type, semantics, who fills it in).
2. Registry team evaluates whether the column belongs in the registry (most do — registry is the canonical citizen profile).
3. Registry team adds the column to the relevant form, deploys, populates for existing rows if needed.
4. Consumer references the new column.

Most additions are absorbed cleanly. The registry team retains ownership of *how* the column is filled in (which form captures it, which validations apply); the consumer just reads.

### 8.3 LADM alignment work (when prioritised)

When LADM conformance becomes an explicit ask:

1. Author a parcel-LADM mapping document (parcel registry's columns → LADM `LA_*` concepts).
2. Identify the gaps (e.g. parcel hierarchies, multi-party rights, baseline survey traceability are not modeled).
3. Decide for each gap: extend the parcel registry to support the LADM concept, OR document the gap as out-of-scope for the Lesotho instance.
4. If extensions are chosen, the parcel registry team owns implementation; convergence team absorbs the impact via updated capability adapters.

This SAD is a placeholder for that future work; today the alignment is informal.

### 8.4 Registry data quality

Out of scope of convergence. The `form-quality-runtime` plugin (existing) is registered against `farmer_registration` and `parcel_registration` services. It runs validation rules at form-save time; data-quality flags surface in the registry's own UI. The convergence framework reads the resulting data with no opinion about whether it passed which quality rules.

### 8.5 Anonymised reads (future external consumers)

Per the solution-level SAD §3.1, future external consumers (other ministries, statistical office) may want anonymised reads from the registries. The pattern: a future API endpoint that hashes national_id with a salt and aggregates to district level. **Not** implemented today; named as a future workstream.

---

## 9. Architecture decisions

| Decision                  | Title                                                                                                            | Status                                                                                                                |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| **D3** — 2026-04-28       | Convergence scope: subsidy-only as Stage 1                                                                       | Accepted. Implies farmer/parcel stay native Joget for Stage 1.                                                       |
| **(May 2026 update)**     | Farmer + parcel registries are architecturally final as native Joget — no migration to MM-form-gen or RegBB     | Accepted. Reverses the previous "Stage 2 — farmer/parcel migration" plan from migration-plan.md (now archived).       |
| **D7** — 2026-04-28       | HOUSEHOLD vulnerability data: design `households.vulnerability` capability                                       | Accepted. Capability adapter pattern; concrete implementation in Phase 2.                                              |
| **D14** — 2026-04-28      | AST routing: any `$registry.*` reference routes to SQL                                                           | Accepted. Framework concern, but anchors this SAD's read-side contract.                                                |
| **(forthcoming) ADR-020** | Capability adapter registry — pluggable `$registry.<entity>` resolution                                          | Pending. Phase 1 close-out item; today the mapping is hand-coded in SqlPathEvaluator.                                 |
| **(forthcoming) ADR-021** | LADM alignment posture for parcel registry                                                                       | Pending. Documents the current state (informal alignment, no certification) and the path to formal conformance work.  |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                              | Source                | Stimulus                                                                | Artifact                  | Environment    | Response                                                                                                                | Measure                                  |
| --------------------------------- | --------------------- | ----------------------------------------------------------------------- | ------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| Stability of the integration      | Registry team         | Renames a column without coordination                                   | `app_fd_farmerbasicinfo`  | Production     | Convergence build breaks (CI catches the dangling reference); release blocked until coordinated update                   | 0 unannounced renames reach production   |
| Performance — registry read       | Subsidy eligibility   | Eligibility eval issues 5 `$registry.*` reads on save                    | SqlPathEvaluator + DB     | Production     | Total registry-read time stays under 250ms                                                                              | < 250ms total per save P95              |
| Read-only consumers               | Auditor               | Audits the codebase for any non-SELECT path against registry tables     | Framework + IM            | Annual review  | Zero INSERT/UPDATE/DELETE statements found targeting `app_fd_farmerbasicinfo` or `app_fd_parcel*`                       | 0 violations                              |
| Sustainability                    | Registry team         | Ships a change to the farmer registration wizard (new field added)       | farmerRegistrationForm    | Production     | Convergence team is unaffected; no convergence-side work needed                                                          | Time-spent-on-convergence-coordination = 0 |
| LADM alignment readiness          | GovStack reviewer     | Asks "is parcel data LADM-conformant"                                    | Parcel registry contract  | Documentation review | Architect points at this SAD §4.3 + §11; documents alignment surface and gaps in days, not weeks                  | Documentable in ≤ 5 working days          |

### 10.2 ISO/IEC 25010 mapping

| Goal                              | 25010 characteristic                                |
| --------------------------------- | --------------------------------------------------- |
| Stability of the integration      | Maintainability — Testability + Modifiability      |
| Performance — registry read       | Performance efficiency — Time behaviour             |
| Read-only consumers               | Reliability — Faultlessness                          |
| Sustainability (decoupling)       | Maintainability — Modularity                       |
| LADM alignment                    | Compatibility — Interoperability                    |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                                                                                                       | Severity | Mitigation                                                                                                                                                                                                                                                                                |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RG-R1 | **Capability registry not yet generalised.** Today's table mapping is hand-coded in `SqlPathEvaluator.java`. Adding `$registry.parcels.summary` requires editing the SqlPathEvaluator, not registering a new capability adapter.                                                                                | Medium   | Phase 1 close-out item. ADR-020 codifies the registry pattern; refactor to OSGi-registered `CapabilityAdapter` services.                                                                                                                                                              |
| RG-R2 | **No formal stability spec per column.** Stability levels (LOCKED / STABLE / OPTIONAL) are documented in §5.3 of this SAD but not enforced in CI. A registry-team developer can rename `c_first_name` without anything failing the build.                                                                       | Medium   | Phase 2: introduce a CI check that scans capability adapters' SQL + every datalist's SQL for column references; fail the build if any reference dangles.                                                                                                                                  |
| RG-R3 | **LADM alignment is informal.** Stated as a posture, not certified. External consumers asking for LADM conformance get a "we're aligned but not certified" answer — could be a soft point in international reviews.                                                                                              | Low      | Documented; future work (ADR-021) sets a path to formal alignment when prioritised.                                                                                                                                                                                                       |
| RG-R4 | **Cross-table joins in capability adapters.** `farmers.parcels.summary` aggregates parcels by farmer; `households.vulnerability` aggregates household members. Both involve joins. Performance becomes a concern at scale (>10k farmers, >50k parcels).                                                            | Low      | Standard SQL tuning if it materialises (indexes on `c_farmer_id` and `c_national_id`); not yet a measured problem.                                                                                                                                                                        |
| RG-R5 | **The registries do not own a stability contract document.** This SAD documents the contract from the consumer side; the registry team has not signed off on it.                                                                                                                                                | Medium   | Schedule a review: registry team reads this SAD, confirms the column-stability table, signs off. Until signed, the contract is consumer-asserted, not bilateral.                                                                                                                          |
| RG-R6 | **Registry edit audit is the registry's own concern.** When an operator edits a farmer profile mid-cycle, the convergence framework's audit (`reg_bb_eval_audit`) doesn't capture that — only the rule evaluation. Forensic question "when did the registry value change" needs the registry's own audit log. | Medium   | Convention: the registry team maintains `audit_log` (which exists per the legacy `joget-status-framework` plugin). Cross-link in forensic procedures: audit.registry-edit + audit.eligibility-evaluation = full picture.                                                                  |

---

## 12. Glossary

| Term                          | Definition                                                                                                                                                                                                                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Farmer registry**           | The set of native-Joget forms (`farmerRegistrationForm` and friends) and `app_fd_*` tables that capture the canonical Lesotho farmer profile, keyed by national_id.                                                                                                    |
| **Parcel registry**           | The set of native-Joget forms (`parcelRegistration` and friends) and `app_fd_*` tables that capture cultivated land parcels, including geometry, tenure, agro-zone, primary crop.                                                                                       |
| **Integration surface**       | The specific tables and columns of the registries that other modules read. Documented in §5 of this SAD.                                                                                                                                                              |
| **Capability adapter**         | Code that resolves `$registry.<entity>.<field>` references at evaluation time. Today hand-coded in `SqlPathEvaluator`; future ADR-020 generalises to an OSGi-registered service interface.                                                                            |
| **LADM**                      | ISO 19152 — Land Administration Domain Model. International standard for land administration data shapes. The parcel registry is informally aligned; not certified.                                                                                                  |
| **Stability level**           | LOCKED / STABLE / OPTIONAL — the contract on a column's stability for consumers.                                                                                                                                                                                       |
| **`$registry.*` reference**   | A reference scope in the rule grammar that triggers a registry lookup at evaluation time. Routed by AST analysis to `SqlPathEvaluator`.                                                                                                                                  |
