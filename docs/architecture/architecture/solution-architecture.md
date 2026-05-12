# Lesotho Farmers Portal — Solution Architecture Description

| Field             | Value                                                                                                                                                                                                                                                                                                                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| System name       | Lesotho Farmers Portal (LST-FRP)                                                                                                                                                                                                                                                                                                                                                       |
| Document title    | Solution Architecture Description                                                                                                                                                                                                                                                                                                                                                      |
| Version           | 1.0 — DRAFT                                                                                                                                                                                                                                                                                                                                                                            |
| Status            | Draft                                                                                                                                                                                                                                                                                                                                                                                  |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                                             |
| Author(s)         | Aare Laponin (project lead) with engineering team                                                                                                                                                                                                                                                                                                                                      |
| Reviewers         | TBD                                                                                                                                                                                                                                                                                                                                                                                    |
| Approver          | Aare Laponin                                                                                                                                                                                                                                                                                                                                                                           |
| Distribution      | Internal — MAFSN Lesotho, GovStack reviewers                                                                                                                                                                                                                                                                                                                                           |
| Classification    | Internal                                                                                                                                                                                                                                                                                                                                                                               |
| Standards         | arc42 v8 · ISO/IEC/IEEE 42010:2022 · C4 model · ISO/IEC 25010:2023 · GovStack architecture spec v2.2.0                                                                                                                                                                                                                                                                                 |
| Companion docs    | `docs/architecture/convergence-framework.md` · `docs/architecture/migration-plan.md` · `docs/architecture/decision-log.md` · ADRs `docs/architecture/adr/adr-001 … adr-010` · component-level SADs under `docs/architecture/architecture/components/`                                                                                                                                                                                  |
| What this is      | The integration-focused architecture: how the five modules of the Farmers Portal fit together, what each one owns, what crosses module boundaries, and what crosses the system boundary to citizens, operators, and external services.                                                                                                                                                |
| What this is not  | A software-design document for any individual component (those are the per-component SADs); a configuration deliverable for the four 2025 subsidy programmes (those are operational rows in `mm_*` tables); a runbook (separate); a backlog (separate).                                                                                                                              |

---

## 1. Introduction and goals

### 1.1 Requirements overview

The Lesotho Farmers Portal is the Ministry of Agriculture, Food Security and Nutrition (MAFSN) digital service for managing the relationship between the state and the country's farmers. It currently delivers four families of capability:

1. **Farmer registry.** Self-registration and operator-mediated registration of farmers, capturing demographic, household, agricultural, and livelihood data; the canonical national list of producers.
2. **Parcel registry.** Registration of cultivated land parcels including geometry (polygon capture), tenure type, agro-ecological zone, primary crop, and link to the farmer who works the parcel.
3. **Subsidy management.** Programme design (eligibility rules, benefits, required documents, acceptance windows), citizen application against published programmes, operator review and decision, audit trail for every evaluation.
4. **Inputs management (planned).** Catalogue of agricultural inputs (seeds, fertilizers, pesticides, equipment), supplier and inventory management, allocation planning per programme/district/farmer category, voucher issuance and redemption, distribution tracking.

Cross-cutting reporting serves all four families.

The portal implements GovStack's Registration Building Block (RegBB) for the parts of the system that are unambiguously *public-sector citizen services* (subsidy management). The remaining parts use Joget DX 8.x as a delivery platform, with a metadata-driven form-rendering kernel shared across all modules. The portal must integrate with national identity (citizens identified by national ID), payment rails (subsidy disbursement, voucher redemption), and the broader MAFSN data ecosystem.

Authoritative requirements live in:

- `_03_Development/_07_Architecture/govstack-architecture-spec-v.2.2.0.md` — the GovStack architectural baseline this solution conforms to.
- `_03_Development/_07_Architecture/Land_Administration_Domain_Model.pdf` — domain reference for parcel modelling.
- `_03_Development/_07_Architecture/Data Taxonomies.docx` — taxonomy reference for cross-system data alignment.
- The RegBB specification (referenced via `convergence-framework.md`) — for the subsidy-management module specifically.
- `inputs-mgmt-workflow/` — the as-designed specification for the IM module (12 documents, ~7 forms, workflows, integration, reports).

### 1.2 Quality goals

| Rank | Quality goal                          | Concrete motivation and target                                                                                                                                                                                                                                                          |
| ---- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | **Modifiability — programme & rule**  | New subsidy programmes, eligibility rules, benefit shapes, and required documents must be configurable through the metamodel (`mm_*` rows + the rule grammar) without writing or deploying Java code. New IM allocation policies must be configurable through master data + UI. Target: time-to-add a new subsidy programme ≤ 1 working day for a trained operator-analyst, zero engineering involvement. |
| 2    | **UX parity with native Joget**       | Citizen and operator interactions through metadata-driven forms must be at least as usable as a hand-built Joget form on the same content. Specifically: cascading dropdowns, repeating-group widgets, GIS polygon capture, smart-search typeahead, signature, file upload, inline validation, save-as-draft, multi-column layout. If the metadata-driven path delivers strictly worse UX than a hand-built Joget form, the metadata layer is failing its purpose. |
| 3    | **Auditability**                      | Every eligibility evaluation, operator decision, workflow dispatch, and configuration change is captured to a forensic audit table with sufficient context to answer "why did this applicant receive this disposition" months later. Operators see the audit through a list view in the same Joget app, no server-log access required. Audit retention: minimum lifetime of the subsidy cycle the rows reference (12–24 months). |
| 4    | **GovStack conformance**              | The subsidy-management module conforms to RegBB §3, §4.3, §6, §7, §8 of the spec. Lesotho-specific extensions are documented as instance-level extensions per the convergence framework's drift-mode discipline; no spec-extending change happens without recorded multi-service evidence. The solution overall conforms to GovStack architecture v2.2.0 principles for development, deployment, architecture, quality, security, and data. |
| 5    | **Operational continuity in dev**     | Phase deliveries do not break working modules. Farmer registry remains continuously usable for citizen self-registration through every phase; parcel registry remains usable; existing subsidy applications under the legacy stack remain readable until cutover; nothing in convergence is destructive of unconverged components.                                       |
| 6    | **Budget control + commitment funnel** | Every subsidy programme runs against an allocated budget envelope. The full commitment funnel (Reservation → Pre-commitment → Commitment → Expense) is tracked. Programme launch is gated by Cost Estimation Service output evaluated against budget alignment rules; operator decisions surface budget impact inline; reconciliation at quarter-end matches the ledger. **All policy** (tolerance thresholds, overrun policy, authorisation, SLA targets) **is `mm_determinant` rules — no hardcoded policy in any code or schema attribute.** |

These five are the lens through which every architectural decision in §9 has been (or will be) evaluated.

### 1.3 Stakeholders

| Stakeholder                                | Concerns                                                                                                                                                              | Where addressed                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| **MAFSN policy and programme leadership**  | Policy fidelity (rules implemented match programme intent); ability to add/modify programmes mid-cycle; reporting on uptake and outcomes                              | §1.2 quality goal 1; §5 subsidy module; §6 runtime           |
| **MAFSN operators / case workers**         | Day-to-day usability of operator inbox; ability to act on applications quickly; access to context (applicant identity, eligibility outcome, audit trail)              | §1.2 quality goal 2; §5 RegBB framework — operator surface   |
| **Farmers (citizens)**                     | Ease of completing applications; clarity of required documents; predictable response times; transparency of eligibility outcome                                       | §1.2 quality goal 2; §6 application lifecycle                |
| **GovStack reviewers**                     | Conformance with the architecture spec and RegBB; clarity of where Lesotho extends the spec and why                                                                   | §1.2 quality goal 4; §9 ADRs; §11 risks                      |
| **Engineering team (current)**             | Modifiability of the codebase; low coupling between modules; ability to ship subsidy and IM independently                                                             | §4 solution strategy; §5 building blocks; §8 cross-cutting   |
| **Engineering team (future maintainers)**  | Documentation that lets a new engineer understand the system in a week; forensic recovery procedures; clear extension points                                          | This document; component-level SADs; CLAUDE.md repo guidance |
| **Auditors / regulators**                  | Decision traceability; data retention; access controls on personal data; compliance with the Land Administration Domain Model where applicable                        | §1.2 quality goal 3; §8.2 security; §8.3 data                |
| **Joget platform vendor**                  | The solution does not depend on Joget internals that may change; OSGi plugins follow the platform's contract                                                          | §2.1 technical constraints; CLAUDE.md "hard rule" section    |

ISO/IEC/IEEE 42010:2022 requires that concerns are addressed by viewpoints; the table above maps each concern to a section of this document.

---

## 2. Architecture constraints

Constraints differ from quality goals: a quality goal can be traded against another quality goal; a constraint cannot. Every architectural decision must respect every constraint listed here.

### 2.1 Technical constraints

| #   | Constraint                                                                                                                                                                                                                                                                                                                                                                                  | Source                                              |
| --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| C1  | **Joget DX 8.x is the delivery platform.** All UI, form persistence, datalist rendering, userview navigation, and process orchestration go through Joget. The solution does not bypass Joget for these concerns.                                                                                                                                                                            | Existing platform investment; team skill profile    |
| C2  | **OSGi plugin model.** Custom Java code is delivered as Joget OSGi bundles built with `maven-bundle-plugin`. Plugin classes follow Joget's `Plugin` / `FormElement` / `FormContainer` / `ApplicationPlugin` interfaces. No long-lived server processes outside the Joget JVM.                                                                                                                | Joget platform                                      |
| C3  | **PostgreSQL backing store.** Form data tables (`app_fd_*`) are PostgreSQL. Identifier folding to lowercase is platform behaviour and must be respected by all code reading FormRow properties (case-tolerant readers required for camelCase keys).                                                                                                                                          | Existing infrastructure                             |
| C4  | **No raw SQL on Joget metadata or form data tables for writes.** Writes to `app_form`, `app_userview`, `app_datalist`, `app_package`, any `app_fd_*` table, or any other Joget-managed table go through Joget's DAO (form-creator-api endpoints, App Composer UI, or `FormDataDao` from inside a plugin). Reads via `SELECT` are unrestricted. Violating this de-syncs Joget's two caches and silently loses data. | Hard rule documented in repo `CLAUDE.md`            |
| C5  | **Java 11 build target, OpenJDK runtime.** Joget DX 8.1 runs on JDK 11; bundles are compiled with `--release 11`.                                                                                                                                                                                                                                                                            | Platform                                            |
| C6  | **Form ID length cap of 24 characters.** Joget caps form IDs at 24 chars so the underlying `app_fd_<id>` table stays ≤ 32 chars. New form IDs respect this; existing truncated IDs (e.g. `md04agroEcologicalZo`) are used as-is.                                                                                                                                                              | Platform                                            |
| C7  | **DatePicker dialect.** Joget DatePicker uses its own format dialect (`yy`→`yyyy`, `mm`→`MM`, `MM`→`MMMMM`); never pass Java SimpleDateFormat patterns directly.                                                                                                                                                                                                                              | Platform                                            |
| C8  | **No XPDL workflow generation by RegBB.** Workflow processes are authored by sysadmins in Joget's native process designer. The RegBB `mm_action` mechanism dispatches to existing processes by id; it does not synthesise XPDL. (See ADR-011 — to be authored.)                                                                                                                              | D-decision (architectural)                          |

### 2.2 Organisational constraints

| #   | Constraint                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| O1  | **Two FTE engineering capacity.** Plan and decomposition reflect what two engineers can ship in parallel; tracks split where the work splits.                                                                                                                                                                                                                                                                                                                                                |
| O2  | **Aare Laponin holds decision authority.** Retirement gates, scope changes, audit-reopening events are decided by the project lead. Every such decision is recorded in `docs/architecture/decision-log.md`.                                                                                                                                                                                                                                                                                              |
| O3  | **Dev/test environment, no live citizen load yet.** Plans are aggressive; legacy and converged stacks may swap freely; cutover is a flag flip rather than a multi-stage migration. This relaxes when the system carries real citizen traffic.                                                                                                                                                                                                                                                  |
| O4  | **Documentation lives in the repository.** Architecture documents, ADRs, decision log, ops procedures all live under `docs/architecture/`, `_03_Development/_07_Architecture/`, and `CLAUDE.md`. No external documentation system; nothing about the system is "tribal knowledge."                                                                                                                                                                                                                       |

### 2.3 Conventions

| #   | Convention                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V1  | **FK convention split (D20).** Joget-internal FKs (FormGrid row → parent, wizard tab → wizard) store the parent's auto-generated UUID. Cross-entity references (Parcel → Farmer, mm_field.optionsCatalogId → mm_catalog) store the target's business `code`. SmartSearch and SelectBox configurations source by `code`, not by UUID.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| V2  | **Master data (`mm_*`) entities follow a uniform shape.** No `id` field on the form (Joget assigns UUID), `code` field with `DuplicateValueValidator` (UPPER_SNAKE_CASE), `name` field for display, hard-delete only (no `isActive` flag — D23). Exceptions: `mm_screen` uses `title` for the display name, `mm_field` has no global `code` (composite natural key by `(screenId, storageKey)`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| V3  | **Module prefix on form IDs.** `md` for master data lookup forms, `mm` for the metamodel/configuration entities, `01.xx` for farmer registration, `02.xx` for parcels, `sp`/`03.xx` for subsidy programmes, `im` for inputs management, `jre` for the (legacy) rule engine.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| V4  | **Build-numbered plugin JARs.** Every OSGi bundle carries a `Build.java` constant (`NUMBER`, `TIMESTAMP`, `STAMP`) bumped on every build by `deploy/repack.sh`. The build stamp is surfaced in the plugin description so the running build is visible from the Joget admin "Manage Plugins" page.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| V5  | **API-driven configuration deployment.** Form definitions, datalists, and userviews are versioned as JSON files under `app/forms/`, `app/datalists/`, `app/userviews/` and pushed to Joget through the `form-creator-api` endpoints. App Composer is used for ad-hoc inspection and one-off edits but never as the canonical authoring path.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| V6  | **Decisions captured as ADRs.** Significant architecture decisions are recorded as Architecture Decision Records under `docs/architecture/adr/adr-NNN-*.md` following the Nygard format. The decision log (`docs/architecture/decision-log.md`) carries shorter D-numbered entries for tactical decisions that don't warrant a full ADR.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |

### 2.4 Regulatory and compliance

| #    | Constraint                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R1   | **GovStack architecture spec v2.2.0** — solution conforms to the principles in `govstack-cfr-development`, `govstack-cfr-deployment`, `govstack-cfr-architecture`, `govstack-cfr-quality`, `govstack-cfr-security`, and `govstack-cfr-data` sections of the spec.                                                                                                                                                                                          |
| R2   | **GovStack RegBB specification** — the subsidy-management module specifically conforms to RegBB §3 (service shape), §4.3 (rule grammar), §6 (screens, conditional UI, submit), §7 (audit, role-scoped review), §8 (eval endpoint). Lesotho extensions to the metamodel are documented in `convergence-framework.md` §9.                                                                                                                                  |
| R3   | **Personal data protection.** National ID, contact phone, household composition, and document attachments are PII. Access is mediated by Joget user/role membership; audit log captures every access. Retention follows the data taxonomy at `_03_Development/_07_Architecture/Data Taxonomies.docx`.                                                                                                                                                     |
| R4   | **Land Administration Domain Model (LADM)** — parcel modelling aligns with LADM concepts where the existing parcel registry overlaps. Out-of-scope for this version of the SAD to claim full LADM conformance; alignment is on identifier structure (parcel code), tenure type vocabulary, and geometry representation.                                                                                                                                  |
| R5   | **Lesotho data protection regime.** The system processes personal data under MAFSN authority. Data residency: PostgreSQL instance in Azure Lesotho region; backups encrypted at rest.                                                                                                                                                                                                                                                                       |

---

## 3. Context and scope

### 3.1 Business context

The Farmers Portal sits at the centre of MAFSN's relationship with the country's agricultural sector. Five external actor classes interact with it:

```
                ┌─────────────────────────────────────────────┐
                │              Farmers Portal                 │
                │  (the system described in this document)    │
                └─────────────────────────────────────────────┘
                  ▲     ▲      ▲         ▲          ▲
        register  │     │ apply│         │ allocate │ disburse
        / update  │     │ for  │         │ inputs   │ subsidy /
        farmer    │     │ subs.│         │ /redeem  │ redeem
        & parcel  │     │      │         │ vouchers │ voucher
                  │     │      │         │          │
         ┌────────┴┐ ┌──┴─┐ ┌──┴──────┐ ┌┴────────┐ ┌┴────────────┐
         │Citizen  │ │Cit-│ │Operator │ │District │ │Payment      │
         │(self-   │ │izen│ │(MAFSN   │ │coord.   │ │provider     │
         │service) │ │app │ │case wkr)│ │/ depot  │ │(future)     │
         └─────────┘ └────┘ └─────────┘ └─────────┘ └─────────────┘
```

Each external actor's interaction with the system:

| Actor                          | What they do                                                                                                                                                                                                                                                                                                                                                  | Channel                                            |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| **Citizen — farmer**           | Self-registers as a farmer; registers their parcels (geometry, tenure, crop); applies for subsidy programmes through the catalogue; uploads required documents; redeems vouchers at distribution points; views their application status and entitlements.                                                                                                       | Web userview (citizen role) over HTTPS              |
| **Operator — MAFSN case worker** | Reviews submitted subsidy applications; decides approve / reject / send-back-for-correction; consults the audit trail; handles appeals (deferred); manages master data (programme catalogue, document types, eligibility determinants).                                                                                                                          | Web userview (operator role) over HTTPS             |
| **District coordinator / depot** | Plans input allocation per district / programme / farmer category; issues vouchers; records distribution events; reconciles inventory.                                                                                                                                                                                                                          | Web userview (IM-operator role) — Phase 3           |
| **Payment provider (future)**   | Receives disbursement instructions when subsidy applications are approved (cash benefits) or vouchers are redeemed (in-kind benefits). Out of scope for the current iteration; integration surface is named here so design accounts for it.                                                                                                                       | Outbound REST/webhook (TBD)                         |
| **Other ministries (future)**   | Read access to anonymised farmer registry data for cross-ministerial planning (food security, drought response, agricultural extension). Out of scope for the current iteration; named for completeness.                                                                                                                                                          | Read-only API (TBD)                                 |

### 3.2 Technical context

The system's external dependencies are smaller than the actor list might suggest. The current iteration depends on:

```
┌──────────────────────────────────────────────────────────────────┐
│                       Farmers Portal                             │
│                                                                  │
│   ┌──────────────────────────────────────────────────────────┐   │
│   │  Joget DX 8.1 (delivery platform, OSGi runtime, JDK 11)  │   │
│   └──────────────────────────────────────────────────────────┘   │
└──┬───────────────────────────────────────────────────────────┬───┘
   │                                                           │
   ▼                                                           ▼
┌────────────────────────┐                          ┌─────────────────────┐
│ PostgreSQL             │                          │ File system         │
│ (joget-pgsql-sa,       │                          │ (wflow/app_form     │
│  Azure Lesotho region) │                          │  uploads — local)   │
└────────────────────────┘                          └─────────────────────┘
```

External-system integrations that are **not yet wired** but are designed-for:

- **National ID verification.** Citizens enter NID; today the value is trusted as entered. A future ADR will define the verification call (Lesotho civil registry endpoint, response shape, caching, and what happens when the NID does not exist).
- **Payment provider.** Subsidy approval and voucher redemption are the trigger events. Integration is webhook-out: the system posts an event; the payment provider reconciles. No inbound payment-status push in v1.
- **GIS reference data.** Parcel geometry is captured in-portal today. A future integration with Lesotho Land Administration Authority spatial data (per LADM alignment) is sketched but not designed.
- **Notification channel (SMS / email).** Operator decisions and voucher issuance generate notifications. The current portal includes a `mm_action.kind=notification` slot in the metamodel; the dispatch adapter (SMS gateway, SMTP) is not yet implemented.

**Internal-only services run inside the Joget JVM:**

- The OSGi bundle `reg-bb-engine` (RegBB framework — eligibility evaluation, audit, MetaScreenElement, MetaWizardElement, operator decision binder, workflow dispatcher).
- The OSGi bundle `form-creator-api` (configuration deployment endpoints — forms, datalists, userviews, fixture seeding).
- Other Lesotho-instance plugins — see §5.

---

## 4. Solution strategy

This section names the few high-leverage decisions that shape the rest of the architecture. Each is recorded as an ADR; the prose here is the integrated story those ADRs tell together.

### 4.1 Layering: kernel, framework, modules

The solution is organised as three layers, each with a different scope of concern:

```
   ┌─────────────────────────────────────────────────────────────────────────┐
   │                              MODULE LAYER                               │
   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
   │  │ Subsidy  │ │   IM     │ │  Budget  │ │ Farmer   │ │  Reporting   │  │
   │  │ module   │ │ module   │ │ engine   │ │ + Parcel │ │  engine      │  │
   │  │ (RegBB-  │ │ (kernel- │ │ (cross-  │ │ registry │ │  (cross-     │  │
   │  │  shaped) │ │  shaped) │ │ cutting) │ │ (native) │ │   cutting)   │  │
   │  └─────┬────┘ └─────┬────┘ └─────┬────┘ └─────┬────┘ └──────┬───────┘  │
   └────────┼─────────────┼───────────┼──────────────┼──────────────┼───────┘
            │             │           │              │              │
   ┌────────┴─────────────┴───────────┴──────────────┴──────────────┴───────┐
   │                          FRAMEWORK LAYER                               │
   │  ┌────────────────────────────────────────────────────────────────┐    │
   │  │  RegBB framework (reg-bb-engine bundle)                        │    │
   │  │  — citizen-services pattern: services, registrations,          │    │
   │  │    determinants, audit, role-scoped review                     │    │
   │  │  — rule-management infrastructure reused by Budget + IM        │    │
   │  └────────────────────────────────────────────────────────────────┘    │
   └────────────────────────────────┬───────────────────────────────────────┘
                                    │
   ┌────────────────────────────────┴───────────────────────────────────────┐
   │                              KERNEL LAYER                              │
   │  ┌────────────────────────────────────────────────────────────────┐    │
   │  │  MM-form-gen kernel                                            │    │
   │  │  — metadata-driven form rendering                              │    │
   │  │    (MetaScreenElement + MetaWizardElement +                    │    │
   │  │     mm_screen + mm_field + mm_catalog)                         │    │
   │  │  — domain-agnostic                                             │    │
   │  └────────────────────────────────────────────────────────────────┘    │
   └────────────────────────────────────────────────────────────────────────┘
            │
   ┌────────┴────────────────────────────────────────────────────────────────┐
   │                            PLATFORM LAYER                               │
   │  Joget DX 8.1 · OSGi · PostgreSQL · JDK 11                              │
   └─────────────────────────────────────────────────────────────────────────┘
```

The Budget Engine sits in the module layer as a cross-cutting module — like Reporting, it is consumed by other modules (Subsidy emits funnel events, IM emits voucher events, both query the projection at decision time). It uses the framework's `mm_determinant` rule infrastructure for **all** policy decisions (tolerance, overrun, authorisation, SLA targets) — no policy attributes are hardcoded in the engine. See the Budget Engine component SAD for details.

The kernel layer (MM-form-gen) provides metadata-driven form rendering. It has no opinion about *why* a form exists — it just renders fields defined as `mm_screen` + `mm_field` rows into Joget Element instances at runtime. It is reused across modules: subsidy, IM, and any future module that wants config-driven form rendering. (Farmer and parcel registries today are hand-built Joget forms and are not on the kernel; they could be migrated later, but it is not on the roadmap.)

The framework layer (RegBB) sits on top of the kernel and adds the citizen-services-specific concerns: services, registrations, eligibility determinants, applicability vs. eligibility scopes, single-window catalogue, role-scoped operator review, audit. **It is opinionated about the public-sector citizen-service pattern.** Domains that are not citizen-service-shaped (e.g. inventory management, supply-chain logistics) do not use it.

The module layer is where domain-specific value lives. Each module chooses which lower layers to use:

- **Subsidy module** uses the kernel (forms) AND the framework (eligibility, catalogue, review). It is RegBB's primary use case.
- **IM module** uses the kernel only. Its domain is operational logistics, not citizen services. It integrates with the subsidy module by reading `mm_registration` rows (programmes drive allocation plans) and with the farmer registry by reading `app_fd_farms_registry` (vouchers are issued to known farmers).
- **Farmer + parcel registry** is native Joget — no kernel dependency, no framework dependency. Subsidy reads it through the SQL-path evaluator (`$registry.farmer.*`); IM reads it through the same pattern.
- **Reporting engine** is cross-cutting — reads from every module's tables, produces operational and policy reports. Discussed in §5.

This layering is the architecture's most consequential decision and is captured in (forthcoming) ADR-012 — *MM-form-gen as domain-agnostic kernel*.

### 4.2 Metamodel: rules, actions, master data

A small set of metamodel entities is shared across the framework and module layers:

| Entity                | Owned by   | Used by                | Purpose                                                                                                 |
| --------------------- | ---------- | ---------------------- | ------------------------------------------------------------------------------------------------------- |
| `mm_service`          | RegBB      | Subsidy; IM (namespace)| Top-level service definition. **Doubles as a namespace identifier for IM** (see below).                  |
| `mm_registration`     | RegBB      | Subsidy                | A registration (programme) under a service. Carries eligibility strategy, acceptance window, etc.        |
| `mm_screen`           | Kernel     | Subsidy, IM            | A screen in a form / wizard / review.                                                                    |
| `mm_field`            | Kernel     | Subsidy, IM            | A field within a screen. References `mm_catalog` for option lists, `mm_determinant` for visibility/required. |
| `mm_catalog`          | Kernel     | All                    | Master data list (e.g. district codes, crop types, input categories).                                    |
| `mm_determinant`      | RegBB + IM | Subsidy + IM           | A business rule. RegBB uses it for eligibility/applicability/visibility. **IM reuses the rule-management infrastructure for its own business rules** (e.g. voucher redemption constraints). |
| `mm_action`           | RegBB      | Subsidy + IM           | A side-effect: workflow dispatch, notification, bot_pull (NID auto-fill). IM reuses for its workflow triggers. |
| `mm_required_doc`     | RegBB      | Subsidy                | A required document type, scoped to service or registration.                                             |
| `mm_benefit`          | RegBB      | Subsidy                | A benefit a programme delivers (cash, voucher, item).                                                    |
| `mm_role`             | RegBB      | Subsidy                | A role in the service's operational workflow.                                                            |
| `mm_role_screen`      | RegBB      | Subsidy                | The operator review screen layout per role (sectionsJson per spec §7.2).                                 |
| `budget_envelope`     | Budget     | Subsidy + IM           | Per-programme budget allocation. Append-only; `BUDGET_ALLOCATION` events accumulate to `allocated_amount`. |
| `budget_event`        | Budget     | Budget (write); reporting (read) | Append-only ledger; one row per state transition. `correlation_id` traces back to the originating application or voucher. |
| `budget_projection`   | Budget     | Operator UX, reporting | Materialised view derived from `budget_event`. Refreshed on event write.                                  |

Two reuse patterns matter:

1. **`mm_service` as namespace.** Every `mm_*` row belongs to a service (`serviceId` FK by code). For RegBB this is semantically meaningful. For IM it is *namespace-only* — IM creates an `mm_service` row called e.g. `INPUTS_2025` not because IM is a citizen service, but because it bundles the IM module's `mm_screen`, `mm_field`, `mm_catalog`, `mm_determinant`, `mm_action` rows under a single owner. This avoids accidental cross-module coupling.
2. **`mm_determinant` as rule management.** The rule grammar (closed twenty operators, `$applicant.*` / `$constant.*` / `$registry.*` references, fast-path / SQL-path routing) was designed for RegBB eligibility but is reusable for any boolean predicate. IM uses it for rules like "voucher can only be redeemed at the allocated collection point", "stock alert when below threshold", "farmer category eligible for fertilizer subsidy". Budget Engine uses it for **every policy decision** — tolerance thresholds, overrun policy, authorisation, SLA targets, programme launch gating, amount formulas. Same engine, same audit trail, different scope label.

3. **"If it's policy, it's a rule" — applied universally.** Anything that's a *policy decision* (operator's authority to do X, threshold above which Y is blocked, default behaviour Z) is an `mm_determinant` row, not a hardcoded constant or schema attribute. Mechanical / technical decisions (fast-vs-SQL routing, cache TTL, parser AST shape) stay in code. The discipline applies to every module except the farmer + parcel registries (which stay native Joget). The plan for retroactively migrating remaining hardcoded policy is in `docs/architecture/policy-to-rules-migration.md`.

This positions the metamodel as a *general configuration layer* rather than a RegBB-specific construct. ADR-013 (forthcoming) — *Metamodel reuse beyond RegBB* — codifies the pattern; ADR-025 (forthcoming) extends with the new budget governance scopes.

### 4.3 Approach per top quality goal

| Quality goal                  | How the architecture achieves it                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1. Modifiability — programme  | Programmes, eligibility rules, benefits, required documents are all `mm_*` rows authored through admin CRUDs (Phase 1) or a Programme Builder UI (Phase 2). Adding a programme writes rows; no Java change. The rule grammar's closed-twenty operator subset (ADR-001) keeps the surface area small enough that authors do not need engineering help.                                                                                                            |
| 2. UX parity with native Joget | MetaScreenElement synthesises *real Joget Element instances* (TextField, SelectBox, FormGrid, GisPolygonCaptureElement, SmartSearchElement, Signature, …) at render time, not bespoke HTML (D18). Anything Joget supports natively, the metadata-driven path supports too — pass-through synthesis. Conditional UI runs server-side (authoritative on save) AND client-side (snappy for the simple-equality case). Identity header on every wizard step including guides. |
| 3. Auditability               | Single forensic table `reg_bb_eval_audit` captures every rule evaluation, operator decision, and workflow dispatch. Operators view the same rows through the standard datalist UI in the Joget app. ADR-006 (forthcoming) defines the schema; retention is documented in CLAUDE.md.                                                                                                                                                                              |
| 4. GovStack conformance       | Subsidy module implements the cited spec sections faithfully; deviations are documented as Lesotho-instance extensions with multi-service evidence per the convergence framework's drift discipline; no extension lands without recorded justification.                                                                                                                                                                                                            |
| 5. Operational continuity     | Convergence is staged: kernel → framework → subsidy module → IM module. Each stage delivers value standalone. Legacy stacks for unconverged modules continue running. Cutover for a converged module is a flag flip after 1 week of clean parallel operation.                                                                                                                                                                                                  |

### 4.4 Organisational alignment

Two FTE engineers work in parallel where the work splits. The split is along the layer axis:

- **Track E1 (engine / kernel / framework).** Owns reg-bb-engine, MetaScreenElement, MetaWizardElement, RoutingEvaluator, AuditWriter, OperatorDecisionBinder, WorkflowDispatcher.
- **Track E2 (configuration / content / publisher).** Owns mm_* form definitions, datalist + userview JSON files, seed fixtures, form-creator-api endpoints, programme content (for the four 2025 subsidy programmes), IM module configuration (Phase 3).

Joint sessions are Monday–Wednesday of each week's first day, plus weekly Friday review.

---

---

## 5. Building block view

This section describes the system at C4 Level 2 (containers). Each container has its own component-level SAD under `docs/architecture/architecture/components/` covering C4 Level 3 internals.

### 5.1 Containers — whitebox of the solution

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                        Lesotho Farmers Portal (Joget DX 8.1)                   │
│                                                                                │
│  ┌────────────────────────────────────────────────────────────────────────┐    │
│  │  Citizen userview                Operator userview                     │    │
│  │  (Farmers Portal)                (MOA Office)                          │    │
│  │   ▾ Farmer registration          ▾ Subsidy application — operator      │    │
│  │   ▾ Parcel registration          ▾ Mass-data admin (mm_*)              │    │
│  │   ▾ 2025 Subsidy Application     ▾ Reports                              │    │
│  │   ▾ My Vouchers (read-only)      ▾ Budget dashboard                    │    │
│  └────────────────────────────────────────────────────────────────────────┘    │
│                                                                                │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────────┐        │
│  │  Subsidy module  │ │   IM module      │ │  Reporting engine         │        │
│  │  (configuration  │ │   (configuration │ │  (datalist JSON files;    │        │
│  │   in mm_*; 4     │ │    + Phase 3     │ │   no Java code)           │        │
│  │   programmes)    │ │    OSGi bundle)  │ │                           │        │
│  └────────┬─────────┘ └────────┬─────────┘ └─────────────┬────────────┘        │
│           │                    │                         │                      │
│           ▼                    ▼                         ▼                      │
│  ┌────────────────────────────────────────────────────────────────────────┐    │
│  │  reg-bb-engine OSGi bundle                                             │    │
│  │  ┌─────────────────────────┐    ┌────────────────────────────────┐     │    │
│  │  │ RegBB framework         │    │ MM-form-gen kernel             │     │    │
│  │  │ (eligibility, audit,    │◀───│ (MetaScreenElement,            │     │    │
│  │  │  decision binder, REST, │    │  MetaWizardElement,            │     │    │
│  │  │  WorkflowDispatcher)    │    │  MetaModelDao,                 │     │    │
│  │  └─────────────────────────┘    │  mm_screen+mm_field+mm_catalog)│     │    │
│  │                                 └────────────────────────────────┘     │    │
│  └────────────────────────────────────────────────────────────────────────┘    │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │  regbb-budget-engine OSGi bundle (Phase 2.5)                         │      │
│  │  ┌──────────────────────┐    ┌────────────────────────────────┐     │      │
│  │  │ Funnel event listener│    │ Cost Estimation Service        │     │      │
│  │  │ Storage (envelope,   │    │ Rule-to-SQL compiler           │     │      │
│  │  │ event, projection)   │    │ Manual adjustment surface      │     │      │
│  │  └──────────────────────┘    └────────────────────────────────┘     │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │  Farmer + Parcel registries — native-Joget hand-built forms          │      │
│  │  (read-only contract; not migrated; LADM-aligned posture)            │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │  form-creator-api OSGi bundle (deploy-time configuration plumbing)    │      │
│  │  /jw/api/formcreator/{forms,datalists,userviews,seed,clear}           │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
└────────────────────────────────────────────────────────────────────────────────┘
            │                                    │
            ▼                                    ▼
┌────────────────────────────────┐    ┌─────────────────────────────────┐
│ PostgreSQL                     │    │ File system                     │
│ (joget-pgsql-sa,               │    │ (wflow/app_formuploads — local) │
│  Azure Lesotho region)         │    │                                  │
└────────────────────────────────┘    └─────────────────────────────────┘
```

### 5.2 Container responsibilities (one-line summaries)

| Container | Responsibility | Component SAD |
|---|---|---|
| **MM-form-gen kernel** | Domain-agnostic metadata-driven form rendering. Translates `mm_screen` + `mm_field` + `mm_catalog` rows into Joget Element instances. | `components/mm-form-gen-kernel.md` |
| **RegBB framework** | Citizen-services-shaped opinionated layer: eligibility evaluation, audit, decision binders, REST endpoints, declarative workflow dispatch via `mm_action`. | `components/reg-bb-framework.md` |
| **Subsidy module** | Configuration deliverable — the four 2025 subsidy programmes as `mm_*` rows + form/datalist/userview JSON. Runs on kernel + framework. | `components/subsidy-module.md` |
| **IM module** (Phase 3) | Inputs management — catalog, supplier, inventory, allocation, voucher, distribution. Uses kernel only; reuses `mm_determinant` + `mm_action`. Native Joget for stable transactional surfaces. | `components/im-module.md` |
| **Budget Engine** (Phase 2.5) | Storage + funnel ledger + Cost Estimation Service for programme budget commitment accounting. Policy via `mm_determinant` rules. | `components/budget-engine.md` |
| **Reporting engine** | Cross-cutting — native Joget datalists with `JdbcDataListBinder` over all module tables. No Java code. | `components/reporting-engine.md` |
| **Farmer + parcel registries** | Native-Joget forms; read-only contract for consumers via `$registry.*` reference scope. | `components/farmer-parcel-registry-integration.md` |
| **form-creator-api** | Deploy-time plumbing — forms, datalists, userviews, fixture seeding via REST. | (no separate SAD; documented in CLAUDE.md) |

### 5.3 Cross-container interfaces

The integration surfaces between containers:

| Producer | Surface | Consumer | Mechanism |
|---|---|---|---|
| Subsidy module | Lifecycle events on application save | RegBB framework | `RegBbApplicationStoreBinder` (storeBinder hook) |
| Subsidy module | Lifecycle events on operator decision | RegBB framework | `RegBbOperatorDecisionBinder` (storeBinder hook) |
| RegBB framework | Workflow / budget event dispatches | IM, Budget Engine, sysadmin XPDL | `mm_action` row → `WorkflowDispatcher` → listeners or `WorkflowManager.processStart` |
| RegBB framework | `$registry.*` reference resolution | Farmer + parcel registries | `SqlPathEvaluator` issues NID-keyed SELECTs |
| Subsidy + IM | Application + voucher state changes | Budget Engine | `mm_action.kind=budget_event` → `BudgetEventListener` → ledger |
| All modules | Database state | Reporting engine | Read-only SQL via `JdbcDataListBinder` in datalists |
| All modules | Form definitions, datalists, userviews | form-creator-api | Deploy-time POST to `/jw/api/formcreator/*` |

The integration discipline: **all cross-container communication is either through `mm_action` dispatch (event-driven, configuration), through Joget storeBinder hooks (lifecycle), or through read-only SQL (reporting + registry reads).** No direct Java method calls between modules; each module imports only the framework / kernel below it.

---

## 6. Runtime view

Three end-to-end scenarios that cross multiple containers; each illustrates how the integration discipline plays out at runtime.

### 6.1 Citizen submits a subsidy application

```
1. Citizen logs in to Citizen userview, opens "2025 Subsidy Application".
2. Joget loads `subsidyApplication2025` form → instantiates MetaWizardElement.
3. MetaWizardElement queries mm_screen for the wizard's tabs, synthesises
   one MetaScreenElement per tab.
4. Citizen fills tabs 1-5, uploads documents, reviews on tab 6.
5. Citizen clicks Submit.
6. RegBbApplicationStoreBinder runs:
   a. tx 1: persists application row.
   b. Aggregates eligibility per applied_programme:
      - For each mm_determinant in scope=applicability:
        RoutingEvaluator → fast or SQL path → audit row written
      - Aggregate per evaluationStrategy → disposition.
   c. Per ADR-027: StatusPolicyResolver evaluates initial_status_assignment
      rules → returns target status (e.g. pending_operator_review).
   d. tx 2: patches eligibility_outcome JSON + status.
   e. WorkflowDispatcher.dispatch(SUBSIDY_2025, programme, status_change, ...):
      - Matches BUDGET_RESERVE_ON_SUBMIT mm_action → BudgetEventListener
        writes RESERVATION event.
      - Matches any sysadmin-authored notification action → starts XPDL.
7. Operator inbox refreshes; new row visible with disposition pill.
8. Budget Engine projection refreshes; reserved += amount.
```

End state: row in `app_fd_subsidy_app_2025` (status, eligibility_outcome), N rows in `app_fd_reg_bb_eval_audit` (one per evaluated rule + one per initial-status-assignment rule + one per dispatch), 1 row in `budget_event` (RESERVATION).

### 6.2 Operator approves; budget pre-commitment + IM entitlement (Phase 3 view)

```
1. Operator opens application via operator inbox.
2. MetaWizardElement renders with roleScreenId=MOA_OPERATOR_REVIEW:
   - Citizen tabs read-only.
   - OP_DECISION tab editable.
   - Inline budget hint (Budget Engine column formatter):
     "Approving will pre-commit M2,000. Envelope after: M915,000 / M1,200,000."
3. Operator clicks Approve, adds comment, clicks Save.
4. RegBbOperatorDecisionBinder:
   a. tx 1: persists decision/score/comment/decided_at.
   b. tx 2 (try/catch — never-null discipline):
      - Per ADR-028: StatusPolicyResolver evaluates decision_to_status rules
        for "approve" → "approved".
      - budget_overrun_policy rule evaluated; allows the approval.
      - Patches application row's status = approved.
      - Writes OPERATOR_DECISION:approve audit row.
      - WorkflowDispatcher dispatches:
        * BUDGET_PRE_COMMIT_ON_APPROVE → BudgetEventListener writes
          PRE_COMMITMENT event; projection refresh.
        * ISSUE_IM_ENTITLEMENT (Phase 3) → starts sysadmin-authored XPDL
          process subsidy_approval_to_im_entitlement.
5. The XPDL workflow:
   a. Reads application row (recordId = applicationId).
   b. Looks up active im_allocation_plan for the programme.
   c. Validates farmer is in roster (mm_determinant: IM_FARMER_IN_ALLOCATION_ROSTER).
   d. Writes one im_voucher row per allocation-line × farmer.
   e. Voucher save dispatches BUDGET_COMMIT_ON_VOUCHER_ISSUE → COMMITMENT event.
6. Notification workflow fires: SMS to citizen with QR code.
```

End state: status flipped, two new audit families (decision + dispatch), Budget Engine ledger has PRE_COMMITMENT + COMMITMENT events, IM has voucher row(s).

### 6.3 Cross-module forensic query

```
Auditor asks: "Where did the M50,000 expense for envelope ENV_PRG_2025_001 come from?"

1. Auditor opens budget_funnel_ledger (reporting engine datalist) filtered
   by envelope_code = ENV_PRG_2025_001 + event_type = EXPENSE.
2. For each EXPENSE row, follows correlation_id (= voucher id) to
   im_voucher_redemption + im_voucher.
3. From voucher, follows voucher.application_id to the subsidy application.
4. From application, follows the audit chain in reg_bb_eval_audit:
   - All applicability rule evaluations on submit.
   - The OPERATOR_DECISION:approve audit row.
   - The WORKFLOW_DISPATCH audit rows (BUDGET_PRE_COMMIT, ISSUE_IM_ENTITLEMENT).
   - The BudgetEvent rows (RESERVATION, PRE_COMMITMENT, COMMITMENT, EXPENSE).
5. Full chain reconstructed without leaving the Joget admin UI.
```

This forensic capability is one of the architecture's load-bearing benefits — the audit + ledger chains close the loop on every cent of expense.

---

## 7. Deployment view

### 7.1 Environments

| Environment | Purpose | Status |
|---|---|---|
| **Dev** | Active development; the four 2025 programmes live for end-to-end testing. Hosts: Joget DX 8.1 on Azure Lesotho region, PostgreSQL Azure-managed. | Live (URL: `http://20.87.213.78:8080/jw`) |
| **Staging** | Pre-production validation; mirrors dev but with sanitised data. | Not yet provisioned |
| **Production** | Live citizen + operator traffic. | Not yet provisioned (target: Phase 1 close-out exit) |

The system is currently dev-only. Operational continuity quality goal #5 holds: phase deliveries do not break working modules in dev.

### 7.2 Infrastructure topology

```
                          Citizen / Operator
                                  │
                                  ▼ HTTPS (Phase: HTTP)
                  ┌─────────────────────────────────┐
                  │    Azure Lesotho region         │
                  │    Joget DX 8.1 on Tomcat       │
                  │    (single-node)                │
                  └────────┬────────────┬───────────┘
                           │            │
                           ▼            ▼
                  ┌─────────────┐  ┌──────────────────────────┐
                  │ Local FS    │  │ joget-pgsql-sa            │
                  │ (uploads,   │  │ (PostgreSQL,              │
                  │  felix      │  │  Azure-managed,           │
                  │  cache)     │  │  Lesotho region)          │
                  └─────────────┘  └──────────────────────────┘
```

Single-node, single-region. No load balancer, no replica DB, no CDN. Entirely appropriate for dev and pilot; Phase 4+ would split for scale.

### 7.3 Plugin deployment

OSGi bundles deploy via Joget admin **Settings → Manage Plugins → Upload**. Active bundles:

| Bundle | Purpose | Live build (May 2026) |
|---|---|---|
| `reg-bb-engine` | Kernel + framework | build-049 |
| `regbb-budget-engine` | Budget engine | not yet built |
| `form-creator-api` | Deploy-time plumbing | build-014 |
| `joget-status-framework` | Status transition + audit | (existing, unchanged) |
| `form-quality-runtime` | Form-level validation rules | (existing; application-side rules retiring per Phase 2) |
| `gisui` | GIS polygon capture | (existing, unchanged) |
| `concatfield`, `smart-search`, `embedded-datalist`, `signature` | Custom widgets | (existing, used by registries; will be reused by kernel pass-through) |

### 7.4 Configuration deployment

Form definitions, datalists, userviews, mm_* fixture rows are version-controlled in the repo and deployed via `form-creator-api` REST endpoints. Tooling under `tooling/`:

- `seed.py` — upserts `mm_*` fixture rows.
- `push_form.py` — pushes form definitions.
- `push_datalist.py` — pushes datalist JSONs.
- `push_userview.py` — pushes userview JSON.

No App Composer paste workflow for canonical changes — every authored artefact lives in the repo first, then pushes.

### 7.5 Runbook references

- Form-cache recovery procedure: `CLAUDE.md` "What goes wrong if you violate the rule" section.
- Audit retention quarterly archival: `CLAUDE.md` "Audit retention" section.
- Plugin upload + verification: `CLAUDE.md` "API Builder credentials" section.

---

## 8. Cross-cutting concepts

### 8.1 Domain model and ubiquitous language

- **Service / programme / registration** — RegBB-specific. A programme is one `mm_registration` row under a parent `mm_service`.
- **Citizen / applicant / farmer** — interchangeable in subsidy context.
- **Operator / case worker** — the MAFSN role that reviews applications.
- **Disposition / status** — disposition is the eligibility evaluation outcome; status is the application's lifecycle state. See framework SAD glossary.
- **Funnel** — the budget commitment pipeline: Reservation → Pre-commitment → Commitment → Expense. See Budget Engine SAD §4.
- **Determinant / rule** — interchangeable. A boolean (or arithmetic) predicate authored as an `mm_determinant` row.
- **Action** — a side-effect trigger authored as an `mm_action` row.

### 8.2 Security architecture

- **Authentication** — Joget user/role membership. Citizens have a generic citizen role; operators have role-specific membership (MOA_OPERATOR, MOA_SUPERVISOR, MOA_BUDGET_ADMIN, etc.).
- **Authorisation** — userview menu access by role membership for navigation; `mm_determinant.scope=budget_authorisation` rules for Budget Engine operations; framework's role-scoped review (planned `MetaReviewElement` per RegBB §7.2) for operator decision affordances.
- **PII handling** — National ID, contact phone, household composition, document attachments. Access is role-gated; audit log captures every access.
- **Network** — currently HTTP (dev). Production target: HTTPS with TLS termination at Azure Application Gateway.
- **Data residency** — PostgreSQL in Azure Lesotho region; backups encrypted at rest (Azure-managed encryption keys).

### 8.3 Data architecture

- **Form data** — per-form `app_fd_*` tables managed entirely by Joget's DAO. No raw writes (the hard rule).
- **Configuration data** — `mm_*` tables as `app_fd_mm_*`, deployed via fixture, accessed through MetaModelDao or directly via FormDataDao for cross-cutting concerns (e.g. RoutingEvaluator's rule lookup).
- **Audit data** — `app_fd_reg_bb_eval_audit` for rule evaluations; `budget_event` table for funnel events. Append-only ledger semantics for both.
- **Master data** — `md_*` forms (district, agro-zone, crops, supplier types, etc.) as native-Joget. Referenced by code from `mm_catalog` rows.

### 8.4 Integration / APIs

- **Internal** — modules talk to each other only through `mm_action` dispatch + framework hooks + read-only `$registry.*`. No direct cross-module imports.
- **External (in)** — Joget userview HTTP for citizens + operators; `form-creator-api` REST for deploy-time tooling; `RegBbEvalApi` REST (`/regbb/eval`, `/regbb/submit`) for programmatic eligibility/submission.
- **External (out)** — none wired today. Planned: SMS notifications via SMS gateway adapter; payment provider webhook on subsidy disbursement; GIS reference-data pull from Lesotho Land Administration Authority.

### 8.5 Observability

- **Audit tables** — primary forensic surface. `reg_bb_eval_audit` for rule evaluations + decisions + dispatches; `budget_event` for funnel events; future `im_transaction_log` for IM transactions.
- **Joget logs** — `LogUtil.info/warn/error` per evaluation, decision, dispatch. Tomcat catalina.out + a per-app log file.
- **Build stamps** — every OSGi bundle's `Build.java` carries `NUMBER + TIMESTAMP + STAMP`. Visible in plugin description on the Manage Plugins page; logged at Activator startup.
- **Not yet wired** — Prometheus / external metrics, distributed tracing, structured logging. Future work.

### 8.6 Resilience and error handling

- **Never-null discipline** (ADR-007): operator/citizen input is persisted in tx 1; downstream side effects (eligibility, audit, dispatch, budget event) run in tx 2 wrapped in try/catch; failure is logged but never breaks the user's flow.
- **Audit before persist**: every rule evaluation writes the audit row before the outcome is consumed; if audit fails, evaluation fails — the framework refuses to silently produce an unauditable result.
- **Cache invalidation on operator decision**: L2 evaluation cache flushes the applicationId so the next eligibility re-evaluation sees current data.

### 8.7 Configuration and feature flags

- **Configuration is rows, not flags.** Programmes, rules, actions, envelopes, dashboards are all `mm_*` or `_*` rows in PostgreSQL. To turn a feature on for a specific programme, write the relevant row.
- **System-property knobs** — a small number for technical tuning: `regbb.eval.l2.ttlSeconds` for cache TTL, the Postgres connection string, the API Builder credentials. Documented in CLAUDE.md.

### 8.8 Build, test, deployment

- **Build** — `bash deploy/repack.sh` per OSGi bundle. Bumps Build.java NUMBER + TIMESTAMP, compiles with `javac --release 11`, repacks JAR preserving OSGi MANIFEST.
- **Test** — unit tests for individual classes (e.g. `FastPathEvaluator` parser tests, `StatusPolicyResolver` resolution tests). Integration tests are operator-driven manual scenarios; Phase 1 close-out includes a 5×4 = 20-scenario parity test.
- **Deploy** — JAR upload via Joget admin; configuration push via `form-creator-api`; seed via `tooling/seed.py`. Idempotent at every step.

### 8.9 Privacy and data protection by design

- PII is read-side from registries; write-side from citizen self-registration through the registry forms (Joget's own validation + form-quality-runtime).
- The reporting engine's PII access is gated by Joget user/role membership on the report's userview page.
- Audit retention: documented quarterly archival ritual; live retention 12-24 months matching the subsidy cycle + appeal window.
- Anonymised external reads are a future workstream (not v1).

### 8.10 Internationalisation and accessibility

Phase 1 ships English-only UI on a Lesotho audience that primarily reads English. Sesotho support is a future workstream — Joget's i18n primitives + label translations would handle citizen-facing surfaces; operator-facing surfaces stay English (operator audience is ministerial staff fluent in English).

Accessibility (screen-reader, keyboard navigation) is delivered through Joget's stock theme + native form widgets. The kernel synthesises native widgets, so accessibility properties are inherited. No custom-rendered HTML for data widgets (per ADR-014's server-side path + ADR-015's pass-through dispatch).

### 8.11 AI/ML components

Not applicable to v1.

### 8.12 Sustainability

- **Configuration over code** is the headline sustainability mechanism. New programmes are configuration; new rules are configuration; new dashboards are configuration. Engineering team capacity goes to framework/kernel improvements, not per-programme code.
- **Build-numbered plugin JARs** make deployment regression-traceable.
- **Documentation in repo** — every architectural decision is an ADR; every operational ritual is in CLAUDE.md.
- **Standing principle** — when a new code path branches on policy, the reviewer asks "is this a rule?" Documented in `docs/architecture/policy-to-rules-migration.md` §7.

### 8.13 Documentation as code

All architecture documents are in the repository under `docs/architecture/` and `_03_Development/_07_Architecture/`. ADRs are individual files under `docs/architecture/adr/`. Decision log under `docs/architecture/decision-log.md`. Component SADs under `docs/architecture/architecture/components/`.

---

## 9. Architecture decisions

The decisions that shape the architecture are recorded as ADRs (Nygard format) under `docs/architecture/adr/`. The full list as of May 2026:

| ADR | Title | Status |
|---|---|---|
| ADR-001r2 | Rule grammar canon — DSL with closed-twenty fast-path subset | Accepted |
| ADR-002r2 | mm_dao placement — single-bundle layout | Accepted |
| ADR-003 | Rule storage shape — DSL source verbatim in mm_determinant.ruleJson | Accepted |
| ADR-004 | EvalContext data shape | Accepted |
| ADR-005 | Phase 1 save hook — storeBinder on application form | Accepted |
| ADR-006 | Outcome persistence schema | Accepted |
| ADR-007 | Save-evaluate atomicity — never-null discipline + audit before persist | Accepted |
| ADR-008 | Cache layering — L1 / L2 / L3 | Accepted |
| ADR-009 | Concurrency model | Accepted |
| ADR-010 | Publisher validation depth | Accepted |
| ADR-011 | No XPDL workflow generation by RegBB | Proposed (May 2026) |
| ADR-012 | MM-form-gen as domain-agnostic kernel | Proposed (May 2026) |
| ADR-013 | Metamodel reuse beyond RegBB | Proposed (May 2026) |
| ADR-014 | Conditional UI: server-side authoritative + client-side simple-equality toggle | Proposed (May 2026; implemented build-046+) |
| ADR-015 | Widget pass-through dispatch — switch statement, not registry | Proposed (May 2026) |
| ADR-022 | Budget Engine as separate module | Proposed (May 2026) |
| ADR-023 | Commitment funnel state model | Proposed (May 2026) |
| ADR-024 | mm_action.kind=budget_event integration pattern | Proposed (May 2026) |
| ADR-025 | Rule-based budget governance — new mm_determinant.scope values | Proposed (May 2026) |
| ADR-026 | Rule-to-SQL compiler for fast-path determinants | Proposed (May 2026) |
| ADR-027 | initial_status_assignment scope — initial status from disposition is rule-driven | Proposed (May 2026; implemented build-049) |
| ADR-028 | decision_to_status scope — operator decision → status is rule-driven | Proposed (May 2026; implemented build-049) |

Tactical decisions that don't warrant a full ADR are recorded as D-numbered entries in `docs/architecture/decision-log.md` (D1 through D31 as of May 2026).

---

## 10. Quality requirements

### 10.1 Quality scenarios at solution scope

| Goal | Source | Stimulus | Artifact | Environment | Response | Measure |
|---|---|---|---|---|---|---|
| Modifiability — programme | Operator-analyst | Adds a fifth subsidy programme with new eligibility, benefits, documents | Subsidy module mm_* rows | Phase 2 | Programme is live for citizens within 1 working day | ≤ 1 working day, zero engineering involvement |
| UX parity | Citizen | Completes a 6-tab application | Generated wizard | Phase 1 close-out | Wizard works as well as native Joget; cascading dropdowns, GIS, smart-search work | No UX defect ≥ Sev 2 in test panel |
| Auditability | Auditor | Asks "where did this M50,000 expense come from" | Cross-module audit chain | Production | Reconstructs full chain (application → eligibility → decision → dispatch → voucher → redemption → expense) within 5 minutes | 5 minutes from audit alone |
| GovStack conformance | GovStack reviewer | Audits the subsidy module against RegBB §3, §4.3, §6, §7, §8 | RegBB framework + subsidy config | Phase 1 close-out | Each section either implemented, deferred-with-reason, or extended-with-evidence | Zero unjustified gaps |
| Operational continuity | Citizen | Self-registers as a farmer during Phase 2 cutover week | Farmer registry (native Joget) | Production | Continues working unchanged through every convergence phase | Zero registry outage |
| Budget control | Operator | Approves an application that would breach the envelope | Budget overrun policy rule | Production | Approve button disabled with rule's failMessage shown | Zero unauthorised over-commitment |

### 10.2 ISO/IEC 25010 mapping

| Goal | 25010 characteristic |
|---|---|
| Modifiability — programme | Maintainability — Modifiability |
| UX parity | Interaction capability — Operability + Learnability |
| Auditability | Reliability — Recoverability of evidence + Security — Accountability |
| GovStack conformance | Functional suitability — Functional correctness + completeness |
| Operational continuity | Reliability — Availability |
| Budget control | Functional suitability — Functional appropriateness + Safety |

---

## 11. Risks and technical debt

| # | Item | Severity | Mitigation |
|---|---|---|---|
| S-R1 | **Single-domain validation only.** Spec conformance is demonstrated against subsidies (one domain). Convergence framework's "second-domain test" is withdrawn (IM is not citizen-services-shaped). RegBB's portability remains an open empirical question. | Medium | Honest documentation in solution-level SAD §4.1 + framework SAD §11. Future probe when a fitting public-service domain emerges. |
| S-R2 | **Phase 1 close-out backlog.** Repeating-group widget, GIS pass-through, signature, smart-search, cascading dropdowns are still placeholders. Single-window catalogue (RegBB §6.1.6) not yet built. Capability registry per spec §3.2 not generalised. | High | Documented per-component; sequenced in Phase 1 close-out (~4-6 weeks). |
| S-R3 | **Budget Engine not yet implemented.** Architecture documented (SAD + 5 ADRs); code is Phase 2.5 work. Until built, programmes have no budget visibility and operators decide blind. | High | First Phase 2.5 deliverable. ~3-4 person-weeks for the bundle + dashboards + initial seed. |
| S-R4 | **Operator concurrency over-commit.** Two operators approving simultaneously can over-commit a budget envelope. | Medium | Optimistic by default; reconciliation surfaces over-commit; pessimistic locking is a configurable rule per envelope. |
| S-R5 | **No external integrations live.** NID verification, payment provider, SMS gateway, GIS reference data are all designed-for but not wired. | Medium | Each is a separate workstream; not blocking subsidy convergence; named in solution-level §3.2. |
| S-R6 | **Configuration drift.** Operator edits via App Composer mid-cycle can drift from the YAML fixture in the repo. | Medium | Discipline: programme changes go through YAML + `seed.py`. Phase 2 Programme Builder writes to YAML on save. |
| S-R7 | **Single-region single-node deployment.** No HA, no DR, no CDN. Production-scale work is beyond Phase 1. | Low | Acceptable today; documented for future. |
| S-R8 | **Forensic recovery procedure depends on operator discipline.** Some failure modes (form-cache desync after raw SQL) require a documented recovery ritual. | Low | CLAUDE.md operational rules section; forensics are bounded. |

---

## 12. Glossary (solution scope)

Domain terms are inherited from component SADs; the following are solution-level cross-references:

| Term | Definition |
|---|---|
| **Lesotho Farmers Portal (LST-FRP)** | The system described in this document — MAFSN's digital service for farmer registration, parcel registration, subsidy management, and (planned) inputs management. |
| **MAFSN** | Ministry of Agriculture, Food Security and Nutrition (Lesotho). The system's primary stakeholder. |
| **GovStack** | The international architectural framework guiding this solution. RegBB is the Registration Building Block of GovStack. |
| **RegBB** | Registration Building Block — the GovStack specification for citizen-services-pattern systems. The subsidy module is RegBB-conformant. |
| **Programme** | A subsidy offering. Currently four programmes for the 2025 cycle: PRG_2025_001 through PRG_2025_004. Each is one `mm_registration` row. |
| **Funnel** | The budget commitment pipeline: Reservation → Pre-commitment → Commitment → Expense. |
| **Convergence** | The architectural project of bringing all subsidy programmes onto the metadata-driven RegBB stack (subsidy-only scope per D3). |
| **Configuration over code** | The headline architectural principle — programmes, rules, actions, envelopes, dashboards are all configuration rows, not Java code. |
| **"If it's policy, it's a rule"** | The corollary applied to policy decisions — they live as `mm_determinant` rows, not as schema attributes or code constants. See `docs/architecture/policy-to-rules-migration.md`. |
| **mm_*** | Metamodel tables: mm_service, mm_registration, mm_determinant, mm_action, mm_screen, mm_field, mm_catalog, mm_required_doc, mm_benefit, mm_role, mm_role_screen. |
| **Capability adapter** | The mechanism by which `$registry.*` references in rules resolve to actual data via JDBC. Today hand-coded; ADR-020 (forthcoming) generalises. |
| **Component SAD** | Software Architecture Description at component scope. Each major container has one under `docs/architecture/architecture/components/`. |
