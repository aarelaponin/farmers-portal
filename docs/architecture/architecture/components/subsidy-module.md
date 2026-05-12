# Component SAD — Subsidy module

| Field             | Value                                                                                                                                                                                                                                                                                                                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | Subsidy module                                                                                                                                                                                                                                                                                                                                                                                              |
| Document title    | Software Architecture Description (component level)                                                                                                                                                                                                                                                                                                                                                          |
| Version           | 1.0 — DRAFT                                                                                                                                                                                                                                                                                                                                                                                                  |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                                                                   |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                                                                           |
| Related           | Solution-level SAD; kernel SAD (`mm-form-gen-kernel.md`); framework SAD (`reg-bb-framework.md`); registry-integration SAD (`farmer-parcel-registry-integration.md`); `docs/architecture/migration-plan.md`; `docs/architecture/phase-1-plan.md`; `docs/architecture/subsidy-application-scope.md`; `app/seeds/lesotho-mm-fixture.yaml`.                                                                                                  |
| What this is      | The component-level SAD for the subsidy-management module. **The module is a configuration deliverable, not a code module.** It is a curated collection of `mm_*` rows that, when interpreted by the kernel + framework, produce the citizen wizard, operator review surface, and audit timeline for Lesotho's agricultural subsidy programmes.                                                          |
| What this is not  | A re-description of the framework or kernel that runs it. A specification of any specific 2025 programme (those are the `mm_registration` rows). A how-to for operators (`mm-authoring-guide.md`).                                                                                                                                                                                                          |

---

## 1. Introduction and goals

### 1.1 Purpose and scope

The subsidy module is the **first and so-far only domain content** that runs on the RegBB framework + MM-form-gen kernel. Its job is to express MAFSN's agricultural subsidy programmes — eligibility rules, benefits, document requirements, operator workflow — as `mm_*` rows interpretable by the framework. The framework and kernel are the *engine*; the subsidy module is the *content*.

In scope (configuration deliverables):

- **Subsidy service** — one `mm_service` row (`SUBSIDY_2025`) bundling all 2025 subsidy programmes under one operator-administered offering.
- **Four programmes** — `mm_registration` rows for `PRG_2025_001` through `PRG_2025_004`:
  - `PRG_2025_001` — Block Farming, Maize Lowlands 2025/26
  - `PRG_2025_002` — Mountain Pulses & Wheat, Winter 2025
  - `PRG_2025_003` — Smallholder E-Voucher Pilot 2025/26
  - `PRG_2025_004` — Drought Emergency Relief 2025
- **Eligibility determinants** — `mm_determinant` rows expressing each programme's eligibility criteria in the closed-twenty grammar.
- **Required documents** — `mm_required_doc` rows (service-level: NID front/back; programme-specific: cooperative membership proof, land tenure proof, etc.).
- **Benefits** — `mm_benefit` rows describing what each programme delivers (cash subsidy, voucher, in-kind input).
- **Screens and fields** — `mm_screen` and `mm_field` rows defining the citizen wizard (Welcome guide, Applicant Identity, Parcel Information, Crops & Activities, Required Documents, Review & Submit) and the operator review surface (same 5 citizen tabs read-only + Decision tab editable).
- **Operator role and review layout** — `mm_role` and `mm_role_screen` rows for the MOA operator role.
- **Decision actions** — `mm_action` rows linking decision events to Joget XPDL workflows authored separately by sysadmins (e.g. `NOTIFY_APPROVAL` linking `(SUBSIDY_2025, status=approved)` to `subsidy_2025_approval_notification`).
- **Forms used by the citizen and operator** — `subsidyApplication2025`, `subsidyApplicationOperator2025`, `reg_bb_eval_audit`. These are thin shells that host MetaWizardElement; the bulk of the content is in the metamodel rows.
- **Datalists and userviews** — `list_subsidyApplicationOperator2025`, `list_reg_bb_eval_audit`, the citizen and operator userviews for the Farmers Portal Joget app.

Out of scope:

- The framework itself (separate SAD).
- The kernel itself (separate SAD).
- Authoring future programmes — that's an operational concern using the same patterns.
- Integration with the IM module (covered in IM module SAD).

### 1.2 Quality goals (component-level)

| Rank | Quality goal                                | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                       |
| ---- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | **Policy fidelity**                         | Each `mm_determinant` rule expresses what the programme's policy intends. A policy advisor who reads the rule should recognise their programme. A divergence between policy and rule is a Phase 1 close-out blocker, not a deferred-fix.                                                                                                                                              |
| 2    | **UX — citizen application feels native**   | The citizen completes the application without ever knowing it's metadata-driven. Cascading dropdowns work, GIS polygon capture works on the parcel tab, file upload with previews works on the documents tab, the wizard remembers their progress. "Like every other Joget form they've used."                                                                                          |
| 3    | **UX — operator triage is fast**            | The operator opens the inbox, sees applicant name + NID + programme + disposition pill + score + failed rule per row, sorts/filters by what matters today (newest first, by district, by disposition), opens an applicant, sees the eligibility outcome inline, makes a decision. Triage of a typical applicant: under 60 seconds.                                                  |
| 4    | **Configurability over re-deployment**      | Adding a fifth subsidy programme to the 2025 cycle: an operator-analyst writes the YAML fixture lines, runs the seeder, the new programme is live in the catalogue. Zero engineering involvement, no JAR upload.                                                                                                                                                                       |
| 5    | **Auditability — every decision is reproducible** | Every operator decision and every eligibility evaluation lands in `reg_bb_eval_audit` with applicant identifiers, rule source, outcome, evaluator path. Six months later, a question like "why did Tšepiso Khabo's PRG_2025_001 application fail?" is answered by reading audit rows, not by debugging.                                                                              |

### 1.3 Stakeholders

| Stakeholder                          | Concerns                                                                                                                                                              |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MAFSN policy advisor**             | Rule expresses programme intent; benefit shape matches; required documents match the policy document.                                                                  |
| **MAFSN operator**                   | Inbox is usable; review surface gives them context; decision flow is intuitive.                                                                                        |
| **Citizen — applicant**              | Form is quick to fill; fields make sense; required documents are stated upfront; eligibility outcome is explained when they're rejected.                                |
| **Operator-analyst (configures programmes)** | The mm_* rows make sense as a configuration layer; new programmes can be added by editing rows; rules are debuggable.                                            |
| **Auditor / inspector**              | The audit table answers "why" questions; timestamps are correct; applicant identity is preserved per evaluation.                                                       |

---

## 2. Architecture constraints

The module inherits all constraints from the solution-level SAD and from the kernel + framework SADs. Module-specific:

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                                       |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S-C1  | **Programme-specific Java is forbidden.** The four 2025 programmes must run from `mm_*` rows alone. Any code change made "for programme X" is a defect against the module's reason to exist.                                                                                                                                                                                                       |
| S-C2  | **Lesotho-specific master data (district, agro-zone, crops) is owned outside the subsidy module.** The MD lookup forms (`md03district`, `md04agroEcologicalZo`, `md19crops`, etc.) are pre-existing native-Joget master-data forms. The module references their codes, never re-models them.                                                                                                       |
| S-C3  | **Citizen UX must not regress vs. the legacy `spApplication` form.** The legacy hand-built form is the floor. If the metadata-driven path is harder to use than the legacy form on any flow, that flow is a Phase 1 close-out blocker.                                                                                                                                                              |
| S-C4  | **The `subsidyApplication2025` and `subsidyApplicationOperator2025` forms have a single MetaWizardElement and minimal hand-built fields** (just the wizard host plus hidden fields for `eligibility_outcome`, `status`). Adding more fields to these form definitions is a violation of the configuration-over-code principle — fields belong in `mm_field`.                                              |
| S-C5  | **Programme codes are stable identifiers.** A registration code (`PRG_2025_001`) is referenced by audit rows, in-flight applications, citizen URLs (deep links), and external systems. Codes don't get renamed; obsolete codes are replaced by new codes (e.g. `PRG_2026_001`), both rows coexist while in-flight applications complete.                                                              |

---

## 3. Context and scope

### 3.1 The subsidy module's place in the system

```
                      ┌─────────────────────────────────┐
                      │  Citizen UI / Operator UI       │
                      │  (Farmers Portal userview)      │
                      └────────────────┬────────────────┘
                                       │
                                       ▼
                      ┌─────────────────────────────────┐
                      │  subsidyApplication2025         │
                      │  subsidyApplicationOperator2025 │
                      │  (form shells with MetaWizard)  │
                      └────────────────┬────────────────┘
                                       │ rendered by
                                       ▼
                ┌──────────────────────────────────────────┐
                │ RegBB framework (reg-bb-engine bundle)   │
                │ + MM-form-gen kernel                     │
                └──────────────────┬───────────────────────┘
                                   │ reads
                                   ▼
              ┌────────────────────────────────────────────────┐
              │  THE SUBSIDY MODULE — configuration content    │
              │                                                │
              │  mm_service: SUBSIDY_2025                      │
              │      │                                         │
              │      ├── 4× mm_registration (the programmes)   │
              │      │     │                                   │
              │      │     ├── mm_determinant (eligibility)    │
              │      │     ├── mm_required_doc (programme)     │
              │      │     ├── mm_benefit                      │
              │      │     └── mm_action (decision triggers)   │
              │      │                                         │
              │      ├── 6× mm_screen (citizen wizard)         │
              │      │     │                                   │
              │      │     └── 20× mm_field                    │
              │      │                                         │
              │      ├── 1× mm_role (MOA operator)             │
              │      ├── 1× mm_role_screen (operator view)     │
              │      └── 7× mm_required_doc (service-level)    │
              │                                                │
              │  ─── reads from ───                            │
              │  app_fd_farmerbasicinfo (NID lookup)           │
              │  app_fd_parcelregistration (parcel info)       │
              └────────────────────────────────────────────────┘
```

The module's database footprint is split:

- **Owned by the module** (subsidy-specific tables): `app_fd_subsidy_app_2025` (the application records), `app_fd_reg_bb_eval_audit` (audit; technically owned by the framework but populated by subsidy events).
- **Read-only consumed** (registry tables): `app_fd_farmerbasicinfo`, `app_fd_parcelregistration`, `app_fd_district`, `app_fd_agro_zone`, `app_fd_crops`, etc.

### 3.2 Upstream consumers

| Consumer          | Interaction                                                                                                                                                                                |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Citizen           | Browses Farmers Portal → "Apply for Subsidy" → fills the wizard → uploads documents → submits.                                                                                              |
| Operator          | Logs in → "2025 Subsidy Application — Operator Review" → opens an applicant → reads eligibility outcome → makes a decision → moves on.                                                        |
| Programme designer (Phase 2) | Logs in → "Programme Builder" → creates a new programme by stepping through Service → Registration → Eligibility → Benefits → Documents → publishes.                          |
| Reporting engine  | Reads `app_fd_subsidy_app_2025` and `app_fd_reg_bb_eval_audit` for operational and policy reports (cohort uptake, disposition mix, decision turnaround time, audit trail completeness). |

### 3.3 Downstream dependencies

| Dependency                                                  | What for                                                                                                                            |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| RegBB framework                                             | All eligibility evaluation, save-time hooks, operator decision lifecycle, audit, REST endpoints, workflow dispatch.                  |
| MM-form-gen kernel                                          | All form rendering (citizen wizard, operator wizard).                                                                                 |
| Farmer registry (`app_fd_farmerbasicinfo`)                  | Read-only via `$registry.farmer.*` — applicant identity verification, registry membership check.                                     |
| Parcel registry (`app_fd_parcelregistration`)               | Read-only via `$registry.parcels.*` (Phase 1 close-out) — parcel summary for area-based determinants.                                |
| Master data (`md03district`, `md04agroEcologicalZo`, `md19crops`, etc.) | Option lists for citizen form fields.                                                                                       |
| `form-creator-api`                                          | Deploy-time: pushes form/datalist/userview JSON; pushes seed fixture rows.                                                            |
| Joget user/role directory                                   | Operator authentication and authorisation.                                                                                            |

---

## 4. Solution strategy

### 4.1 Single service, multiple registrations (Single-Window pattern)

Per RegBB §4.1.3 and §6.3.1.9: the four 2025 programmes become **one `mm_service` row plus four `mm_registration` rows**. The application wizard's screens are defined once on the service; per-programme variation (eligibility, benefits, required documents) is scoped by `registrationId`.

This shape supports a citizen who could in theory apply for multiple programmes in one sitting (Single-Window). For 2025 the operational practice is one programme per application; the architecture supports the multi-select shape if and when policy adopts it.

The citizen picks the programme via a SelectBox on the Identity tab (today's UX) or via the catalogue page on the Welcome tab (Phase 1 close-out — RegBB §6.1.6).

### 4.2 Eligibility per programme — applicability scope (Phase 1)

Phase 1 collapses RegBB's distinct *applicability* and *eligibility* scopes into a single `scope='applicability'` evaluation step. Every `mm_determinant` for a programme runs together on save; the registration's `evaluationStrategy` determines aggregation:

- **`all_must_pass`** (D6) — every rule must return TRUE. First FALSE → `eligibility_failed_mandatory`. NULL/ERROR → `indeterminate`.
- **`score_based`** (D6) — sum scores of TRUE rules; compare to `passingThreshold`. Below `minimumScore` → fail; between minimum and threshold → `eligibility_pending_review`; at-or-above → pass.

The four 2025 programmes are configured as follows (current state):

| Programme           | Strategy        | passing | minimum | Determinants                                                                                                          |
| ------------------- | --------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------- |
| `PRG_2025_001`      | `score_based`   | 100     | 50      | DET_LOWLANDS (100), DET_AREA_NONZERO (100), DET_FARMER_REGISTERED (50, SQL path)                                      |
| `PRG_2025_002`      | `score_based`   | 50      | 0       | DET_MOUNTAINS_OR_SENQU (100)                                                                                          |
| `PRG_2025_003`      | `score_based`   | 70      | 0       | DET_SMALLHOLDER (100)                                                                                                 |
| `PRG_2025_004`      | `all_must_pass` | n/a     | n/a     | DET_DROUGHT_DECLARED                                                                                                  |

Per the migration plan, eligibility determinants are expanded across Phase 1 close-out as parcel + farmer registry integrations come online (currently only `$registry.farmer.first_name` is wired; programme rules touching `$registry.parcels.*` are stubbed).

### 4.3 Operator review surface

Today: the operator opens `subsidyApplicationOperator2025`, which is a `MetaWizardElement` with `readonly=Y` on the citizen tabs and an editable Decision tab. The Decision tab has hand-built fields (`decision`, `decision_score`, `decision_comment`, `decided_at`); save runs through `RegBbOperatorDecisionBinder`.

Phase 2 (deliverable): replace the readonly-flag pattern with `MetaReviewElement` consuming `mm_role_screen.sectionsJson`. The operator's role + service determines which tabs are shown and which are editable per role. Out of scope of this Phase 1 SAD.

### 4.4 Decision → workflow

Operator decision triggers `WorkflowDispatcher` per `mm_action` rules. Today's wired action: `NOTIFY_APPROVAL` — when a `SUBSIDY_2025` application transitions to `approved`, dispatch `subsidy_2025_approval_notification` (a Joget XPDL process the sysadmin authors). The diagnostic `RegBbWorkflowEchoTool` is wired into that process to verify the engine→workflow handoff produces the expected context.

Future actions (per programme):
- `BENEFIT_ISSUANCE` — on `approved`, fire a process that creates an `imEntitlement` row for the applicant (linkage to IM module).
- `REJECTION_NOTIFICATION` — on `rejected`, fire a process that sends an SMS with the rejection reason.
- `OPERATOR_REVIEW_DUE` — on `pending_operator_review`, fire a process that pages the operator on duty (deadline + escalation).

All of these are sysadmin-authored XPDL; the subsidy module configures the `mm_action` row, nothing more.

### 4.4-bis Budget integration via the Budget &amp; Commitment Engine

The subsidy module emits budget funnel events at four lifecycle hooks — application submission (Reservation), approval (Pre-commitment), rejection or withdrawal (Release), and cancellation of an already-pre-committed application (Release Pre-commitment). Each is wired through an `mm_action.kind=budget_event` row; the Budget Engine's listener subscribes to these dispatches and writes ledger rows. **The subsidy module does not import Budget Engine code** — the integration is the same `mm_action` mechanism that handles workflow dispatch and notifications.

Sample wiring:

```yaml
mm_action:
  - code: BUDGET_RESERVE_ON_SUBMIT
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": ["pending_operator_review", "pending_data_clarification", "auto_approved", "auto_rejected"],
        "eventType": "RESERVATION",
        "amountFormulaRule": "BENEFIT_AMOUNT_BY_PROGRAMME" }

  - code: BUDGET_RELEASE_ON_REJECT
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": "rejected",
        "eventType": "RELEASE_RESERVATION" }

  - code: BUDGET_PRE_COMMIT_ON_APPROVE
    serviceId: SUBSIDY_2025
    kind: budget_event
    triggerJson: |
      { "onStatus": "approved",
        "eventType": "PRE_COMMITMENT",
        "amountFormulaRule": "BENEFIT_AMOUNT_BY_PROGRAMME" }
```

The `amountFormulaRule` resolves per programme — typically a `mm_determinant` rule like:

```yaml
mm_determinant:
  - code: BENEFIT_AMOUNT_BY_PROGRAMME
    scope: budget_amount
    ruleJson: |
      applied_programme == 'PRG_2025_001' ? 2000 :
      applied_programme == 'PRG_2025_002' ? 3500 :
      applied_programme == 'PRG_2025_003' ? 1500 :
      applied_programme == 'PRG_2025_004' ? (5000 + household_dependents_under_18 * 1500) : 0
```

(The closed-twenty grammar handles ternary-like patterns through composition; for clarity, the example shows logical pseudo-code — actual rule expressions might use one rule per programme rather than a switch.)

**Operator decision-time visibility.** The operator review form now includes an inline budget hint, rendered by a custom column formatter from the Budget Engine:

> *"Approving will pre-commit M2,000. PRG_2025_001 envelope after: M915,000 / M1,200,000 (76% utilised, 24% remaining)."*

If the `budget_overrun_policy` rule (a `mm_determinant`) evaluates FALSE for the proposed approval, the Approve button is disabled with the rule's failMessage shown. If a warning rule fires (configurable), the operator sees the warning + can proceed. **Neither block nor warning is hardcoded** — both are rule outcomes.

**Programme launch gate.** Before a programme transitions `draft → published`, the Cost Estimation Service runs and the `programme_launch_gate` rule is evaluated against its output. The subsidy module does not re-implement this; it relies on the Budget Engine's launch wizard. New programmes go live only when their cost estimate aligns with their envelope per the gate rule.

**SLA monitoring.** `mm_determinant` rules in `scope=sla_decision` (one per programme, optionally with district overrides) feed the reporting engine. Subsidy module does not contain SLA logic — `slaDecisionDays` is not a `mm_registration` attribute. Each programme's SLA is encoded as a rule like `days_since_submitted <= 5` (PRG_2025_001) or `days_since_submitted <= 2` (PRG_2025_004 — drought emergency relief, tighter SLA).

### 4.5 Citizen wizard structure (current — 6 screens)

| #   | Screen code         | kind         | Purpose                                                                                                                                       |
| --- | ------------------- | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `WELCOME`           | `guide`      | Welcome — overview of the four programmes, eligibility summary, what the applicant will need (NID, parcel proof, etc.). No data capture.        |
| 2   | `APP_IDENTITY`      | `form`       | Applicant Identity — applied_programme (SelectBox), national_id, full_name, gender, date_of_birth, contact_phone.                              |
| 3   | `APP_PARCEL`        | `form`       | Parcel Information — district, agro_zone, village_name, area_hectares, tenure_type, primary_crop. (Future: GIS polygon capture pass-through.) |
| 4   | `APP_CROPS`         | `form`       | Crops & Activities — primary_crop, block_farming_member (radio), cooperative_name (conditional on block_farming_member=yes), drought_affected_decl. |
| 5   | `APP_DOCUMENTS`     | `documents`  | Required Documents — service-level (NID front/back) + programme-specific (cooperative membership, land tenure, drought assessment, etc.).      |
| 6   | `APP_REVIEW_SUBMIT` | `review`     | Review & Submit — read-only summary of all entered data; explicit submit button.                                                              |

Each `mm_screen` carries `audience='citizen'` so the operator wizard skips it (or shows it read-only via `mm_role_screen.sectionsJson` in Phase 2).

### 4.6 Operator wizard structure (current — 6 screens, OP_DECISION added)

The same 6 screens above (in `audience='both'` mode for review) plus one more:

| #   | Screen code     | kind         | Purpose                                                                                                                                            |
| --- | --------------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 7   | `OP_DECISION`   | `form`       | Operator Decision — decision (radio: approve / reject / send_back), decision_score, decision_comment, decided_at. `audience='operator'`.            |

The operator's role-screen mapping today is wizard-level (`readonly=Y` on the wizard makes all 6 citizen tabs read-only; OP_DECISION inherits from `mm_screen.audience='operator'` which is editable). Phase 2 generalises this via `mm_role_screen.sectionsJson`.

---

## 5. Building block view (component internals)

The subsidy module is **content**, not code. The "internals" are the rows in the seed fixture and their structure.

### 5.1 Configuration deliverable structure

```
app/seeds/lesotho-mm-fixture.yaml      ← canonical source for ALL subsidy module configuration
app/forms/subsidyApplication2025.json  ← citizen application form shell
app/forms/subsidyApplicationOperator2025.json ← operator review form shell
app/forms/reg-bb-eval-audit.json       ← audit table form
app/datalists/list_subsidyApplicationOperator2025.json ← operator inbox
app/datalists/list_reg_bb_eval_audit.json ← audit list
app/userviews/v.json                   ← citizen + operator userview combined
```

Deployment via `tooling/seed.py` (which calls `form-creator-api` and seeds the YAML fixture).

### 5.2 The `mm_*` row inventory

Approximate row counts (current, end of Phase 1 close-out):

| Entity            | Row count | Notes                                                                                                                                  |
| ----------------- | --------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `mm_institution`  | 1         | MAFSN — service owner                                                                                                                  |
| `mm_service`      | 1         | SUBSIDY_2025                                                                                                                            |
| `mm_registration` | 4         | One per programme                                                                                                                      |
| `mm_screen`       | 7         | 6 citizen + OP_DECISION                                                                                                                |
| `mm_field`        | ~25       | Across all screens                                                                                                                     |
| `mm_catalog`      | 8         | YES_NO, GENDER, TENURE_TYPE, DECISION (approve/reject/send_back), and 4 cross-references to native-MD lookup forms                    |
| `mm_determinant`  | 7         | DET_LOWLANDS, DET_AREA_NONZERO, DET_FARMER_REGISTERED, DET_MOUNTAINS_OR_SENQU, DET_SMALLHOLDER, DET_DROUGHT_DECLARED, DET_BLOCK_FARMING_MEMBER (UI-conditional) |
| `mm_required_doc` | 7         | NID_FRONT, NID_BACK (service-level), COOP_MEMBERSHIP, LAND_TENURE, DROUGHT_ASSESSMENT, IRRIGATION_PROOF, MOUNTAIN_LOCATION_PROOF (programme-specific) |
| `mm_benefit`      | 7         | Per-programme benefit definitions                                                                                                      |
| `mm_action`       | 1         | NOTIFY_APPROVAL (status_change → workflowId)                                                                                            |
| `mm_role`         | 3         | citizen, moa_operator, moa_supervisor                                                                                                  |
| `mm_role_screen`  | 6         | One per (role, service) pair plus a default                                                                                            |

Total: ~80 rows constitute "the subsidy module."

### 5.3 Key form definitions (form shells)

**`subsidyApplication2025`** (citizen application form):

```json
{
  "id": "subsidyApplication2025",
  "tableName": "subsidy_app_2025",
  "elements": [
    {
      "className": "global.govstack.regbb.engine.element.MetaWizardElement",
      "properties": { "serviceId": "SUBSIDY_2025" }
    },
    { "className": "...HiddenField", "properties": { "id": "eligibility_outcome" } },
    { "className": "...HiddenField", "properties": { "id": "status" } }
  ],
  "loadBinder":  { "className": "...WorkflowFormBinder" },
  "storeBinder": { "className": "global.govstack.regbb.engine.binder.RegBbApplicationStoreBinder" }
}
```

This is **the entire form definition for the citizen wizard**. Everything else — six tabs, twenty-five fields, conditional UI, file uploads, validation — comes from the `mm_*` rows.

**`subsidyApplicationOperator2025`** (operator review form):

```json
{
  "id": "subsidyApplicationOperator2025",
  "tableName": "subsidy_app_2025",                          // SAME table — different lens
  "elements": [
    {
      "className": "global.govstack.regbb.engine.element.MetaWizardElement",
      "properties": {
        "roleScreenId": "MOA_OPERATOR_REVIEW",                // resolves mm_role_screen.sectionsJson
        "readonly": "false"                                   // each tab decides via sectionsJson
      }
    }
  ],
  "loadBinder":  { "className": "...WorkflowFormBinder" },
  "storeBinder": { "className": "global.govstack.regbb.engine.binder.RegBbOperatorDecisionBinder" }
}
```

### 5.4 Sample mm_determinant row (DET_LOWLANDS)

```yaml
mm_determinant:
  - code: DET_LOWLANDS
    name: Applicant farms in the Lowlands
    serviceId: SUBSIDY_2025
    registrationId: PRG_2025_001
    scope: applicability
    ruleType: inclusion
    score: "100"
    allowSlowPath: "N"
    ruleJson: agro_zone == 'lowlands'
    failMessage: This programme is restricted to Lowlands applicants.
    description: |
      Block Farming — Maize Lowlands targets the lowlands intensive crop
      strategy. Mountain / Senqu applicants are not eligible.
```

The rule is the closed-twenty grammar with bare identifiers (resolves against `$applicant.*` per the spec). The fast-path evaluator handles this in microseconds. The audit row written on each evaluation captures rule source verbatim for forensic reproducibility.

### 5.5 Sample mm_action row (NOTIFY_APPROVAL)

```yaml
mm_action:
  - code: NOTIFY_APPROVAL
    name: Notify approval — fire downstream workflow
    serviceId: SUBSIDY_2025
    registrationId: ""              # service-wide
    kind: status_change
    triggerJson: |
      { "onStatus": "approved",
        "workflowId": "subsidy_2025_approval_notification" }
    configJson: ""
```

When `RegBbOperatorDecisionBinder` transitions any `SUBSIDY_2025` application to `approved`, this row is matched and the workflow `subsidy_2025_approval_notification` is started by `WorkflowDispatcher`. The XPDL of that workflow is authored by the sysadmin in Joget's process designer — the subsidy module references it by id only.

---

## 6. Runtime view

### 6.1 Citizen submits an application

1. Citizen logs in, opens the Farmers Portal userview.
2. Navigates: **Registration Forms → 2025 Subsidy Application → New**.
3. Joget loads `subsidyApplication2025` form, instantiates `MetaWizardElement(serviceId=SUBSIDY_2025)`.
4. MetaWizardElement queries `mm_screen` for screens in this service with `audience IN (citizen, both)`, sorts by `orderIndex`, synthesises one `MetaScreenElement` per screen as a tab panel.
5. Citizen fills tabs 1–4, uploads documents on tab 5, reviews on tab 6.
6. Citizen clicks **Save**.
7. `RegBbApplicationStoreBinder.store` runs:
   - Persists the row.
   - Reads `applied_programme`, looks up the registration's strategy.
   - Evaluates every `mm_determinant` for that programme via `RoutingEvaluator` (each evaluation writes an audit row).
   - Aggregates per strategy → disposition → status.
   - Patches `eligibility_outcome` JSON + `status`.
8. Joget redirects to the application list.
9. Operator's inbox now shows the new row with eligibility pill rendered.

### 6.2 Operator reviews and approves

1. Operator opens **MOA Office → 2025 Subsidy Application — Operator Review**.
2. Datalist `list_subsidyApplicationOperator2025` shows applicant name, NID, programme name (resolved via OptionsValueFormatter on registration code), district, eligibility disposition pill, score, failed rule, current status.
3. Operator sorts newest-first by `datemodified`, opens the top-of-list row.
4. Joget loads `subsidyApplicationOperator2025`, instantiates `MetaWizardElement(roleScreenId=MOA_OPERATOR_REVIEW)`.
5. MetaWizardElement renders the wizard with tabs 1-6 read-only and tab 7 (OP_DECISION) editable. The applicant identity header (build-047+) at the top shows "Tšepiso Khabo · NID 1995022700567 · Programme PRG_2025_001".
6. Operator reviews the tabs; opens the audit list in another tab to see the eligibility evaluation history.
7. Operator picks "Approve", scores it 100, comments "Verified parcel polygon matches the village mapping; cooperative membership confirmed via supervisor", clicks **Save**.
8. `RegBbOperatorDecisionBinder.store` runs:
   - tx 1: persists decision/score/comment/decided_at.
   - tx 2:
     - Maps `approve` → status `approved`, patches the row.
     - Writes `OPERATOR_DECISION:approve` audit row.
     - `WorkflowDispatcher.dispatch` queries `mm_action` for `(SUBSIDY_2025, status_change, onStatus=approved)`, finds NOTIFY_APPROVAL, starts the `subsidy_2025_approval_notification` workflow.
     - Writes `WORKFLOW_DISPATCH:NOTIFY_APPROVAL` audit row with the started process instance id.
9. Joget redirects to the operator inbox.
10. The XPDL workflow (sysadmin-authored) does whatever it does — sends an SMS, posts to a downstream microservice, opens a follow-up task. The framework neither knows nor cares.

---

## 7. Deployment view

The subsidy module is deployed as a set of configuration artefacts pushed via `form-creator-api`:

```
1. Push form definitions (citizen + operator + audit forms):
   curl POST /jw/api/formcreator/formcreator/forms with app/forms/subsidyApplication2025.json
   curl POST /jw/api/formcreator/formcreator/forms with app/forms/subsidyApplicationOperator2025.json
   curl POST /jw/api/formcreator/formcreator/forms with app/forms/reg-bb-eval-audit.json

2. Push datalists:
   python tooling/push_datalist.py app/datalists/list_subsidyApplicationOperator2025.json
   python tooling/push_datalist.py app/datalists/list_reg_bb_eval_audit.json

3. Push userview:
   python tooling/push_userview.py app/userviews/v.json

4. Seed mm_* fixture:
   python tooling/seed.py
```

Each step is idempotent (forms upsert by id; datalists upsert by id; userviews upsert by id; seed rows upsert by business key per `_seed_business_keys`).

**No JAR deployment for subsidy-module changes.** Changing a programme's eligibility, adding a new programme, modifying a benefit, adjusting a required document — all edits to the YAML fixture + a `seed.py` run.

---

## 8. Cross-cutting concepts

### 8.1 Master data references

The module references native-Joget master-data forms (the `md*` series) for option lists. The integration is via `mm_catalog` rows pointing at the MD form's `code` column:

```yaml
mm_catalog:
  - code: MD_DISTRICT_LIST
    name: District (Lesotho administrative)
    source: registry
    registrySpec: |
      { "form": "md03district", "labelColumn": "name" }
```

The kernel's `addCatalogOptions` reads the catalog row and, for `source=registry`, walks the referenced form to populate options. (Today most catalogs use `source=static` with hand-curated lists; the `registry` path is wired but minimally exercised — a Phase 1 close-out item.)

### 8.2 Citizen-side draft persistence

A citizen's incomplete wizard submission is persisted at any "Next" tab transition (Joget's standard MultiPagedForm-style behavior baked into the wizard's client JS — actually NOT today; today Next/Previous are pure client-side). When the citizen comes back, the load binder rehydrates from the `app_fd_subsidy_app_2025` row. The eligibility outcome is recomputed on every save.

Phase 1 close-out: explicit "Save as Draft" affordance in the wizard, distinct from "Submit" — required for UX parity.

### 8.3 Document upload lifecycle

Per the kernel SAD §8.4 — the `documents` screen synthesises FileUpload elements per `mm_required_doc` row. Files land in `wflow/app_formuploads/subsidy_app_2025/<applicationId>/<filename>`. The column `c_doc_<code>` carries the bare filename; downloads reconstruct the path.

The required-doc filter respects `mm_required_doc.registrationId` — programme-specific docs only appear when the applicant's `applied_programme` matches.

### 8.4 GIS polygon capture (Phase 1 close-out)

Today the `APP_PARCEL` tab has plain text fields for `district`, `agro_zone`, `village_name`, `area_hectares`. Phase 1 close-out adds a `gis_polygon` field that synthesises Joget's `GisPolygonCaptureElement` and auto-populates `area_hectares` from the polygon geometry, matching the existing parcel-registry UX exactly.

### 8.5 Eligibility outcome decoration in the operator inbox

The operator inbox has dual-style decoration on the disposition column:

- **Server-side**: `JdbcDataListBinder` SQL extracts disposition from `eligibility_outcome::jsonb->>'disposition'` so the column is sortable and filterable.
- **Client-side** (build-027+ userview JS): pill-style decoration converts the bare disposition string into a colored pill — green for `eligibility_passed`, red for `eligibility_failed_mandatory`, amber for `eligibility_pending_review`, grey for `indeterminate`. Same treatment on `status`.

The same JS also adds a sort indicator to the audit list's `dateCreated` column.

### 8.6 Determinants reference applicant data + registry data

Determinants in the closed-twenty grammar can reference:

- **Bare identifiers** = `$applicant.*` = field values from the row being evaluated. Examples: `agro_zone == 'lowlands'`, `area_hectares > 0`, `block_farming_member == 'yes'`.
- **`$registry.<entity>.<field>`** = SQL-path resolution against legacy tables. Today only `$registry.farmer.first_name` is wired (one capability — `farmers.byNid`). Phase 1 close-out adds `$registry.parcels.summary` (cultivated area total per applicant).

### 8.7 Programme catalogue page (Phase 1 close-out — RegBB §6.1.6)

Today: citizen picks programme via SelectBox on the Identity tab. UX workaround.
Target: WELCOME (`kind=guide`) screen renders the list of `mm_registration` rows under `SUBSIDY_2025`, evaluates each programme's `applicabilityDeterminantId` against the applicant's known data (NID lookup → farmer registry → applicant fields), shows applicable / ineligible-with-reason. Citizen clicks the applicable programme, the rest of the wizard adapts to that programme's required documents and benefit shape.

---

## 9. Architecture decisions (module-relevant)

| ADR / D-entry        | Title                                                                                       | Status                                              |
| -------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| **D3** — 2026-04-28  | Convergence scope — subsidy-only as Stage 1                                                 | Accepted                                            |
| **D4** — 2026-04-28  | Phase 1 path — Path B (all 4 programmes in Phase 1)                                         | Accepted                                            |
| **D5** — 2026-04-28  | Target-group priority weights — drop                                                        | Accepted (no per-target weighting in eligibility)   |
| **D6** — 2026-04-28  | Scoring (`mm_registration.evaluationStrategy`) — adopt as 6th Lesotho-instance extension    | Accepted; framework concern                         |
| **D7** — 2026-04-28  | HOUSEHOLD vulnerability data — design `households.vulnerability` capability                  | Accepted; capability adapter scoped for Phase 1 close-out |
| **D8** — 2026-04-28  | `mm_benefit` shape — separate entity                                                        | Accepted                                            |
| **D11** — 2026-04-28 | `bot_pull` field-mapping shape — reuse existing nested in `mm_action.configJson`           | Accepted                                            |
| **D13** — 2026-04-28 | Admin UX — 13 separate CrudMenus in Phase 1; Service Builder is Phase 2                     | Accepted                                            |
| **D16** — 2026-04-28 | `attributesJson` and `acceptanceWindow` placement — `mm_registration`, not `mm_service`     | Accepted                                            |
| **D17** — 2026-04-28 | Phase 1 includes citizen userview generation + operator read-only inspection                | Accepted; deliverable                              |
| **D24** — 2026-04-30 | Wizard / multi-screen sequence as a first-class element (`MetaWizardElement`)               | Accepted; framework concern                         |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                       | Source              | Stimulus                                                                                  | Artifact                       | Environment       | Response                                                                                                                  | Measure                                                  |
| -------------------------- | ------------------- | ----------------------------------------------------------------------------------------- | ------------------------------ | ----------------- | ------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| Policy fidelity            | Policy advisor      | Reads DET_DROUGHT_DECLARED's rule source                                                  | mm_determinant row             | Phase 1 review    | Sees the rule expressed in domain language, recognises programme intent                                                   | "Yes, that's what we said the policy should be" — qualitative |
| UX — citizen application   | Citizen test panel  | Completes a full application end-to-end                                                   | Generated wizard               | Phase 1 close-out | Completes without help, doesn't hit any "this is broken" defects                                                          | Test panel completes in ≤ 15 minutes; no UX defects ≥ Sev 2 |
| UX — operator triage speed | Operator test       | Reviews 10 applications back-to-back                                                      | Operator inbox + review wizard | Phase 1 close-out | Triages each in under 60 seconds (excluding genuine investigation cases)                                                  | 10/10 under 60s for clear cases                           |
| Configurability             | Operator-analyst    | Adds a new programme by writing fixture lines + running seed.py                           | Subsidy module config           | Phase 2           | Programme is live; citizen can apply; operator can review; eligibility evaluates                                         | Time-to-add-programme ≤ 1 working day                    |
| Auditability                | Auditor             | Asks "why did Tšepiso get pending_operator_review on PRG_2025_001"                         | reg_bb_eval_audit              | Production        | Reviews audit rows; sees DET_LOWLANDS=FALSE (mountains applicant); sees DET_AREA_NONZERO=TRUE; sees DET_FARMER_REGISTERED=NULL; understands score=100 below threshold=50 + threshold=100 → pending_review | Question answered in ≤ 5 minutes from audit alone        |

### 10.2 ISO/IEC 25010 mapping

| Goal                       | 25010 characteristic                                |
| -------------------------- | --------------------------------------------------- |
| Policy fidelity            | Functional suitability — Functional appropriateness |
| UX — citizen application   | Interaction capability — Operability + Learnability |
| UX — operator triage speed | Performance efficiency — Time behaviour (operator) |
| Configurability             | Maintainability — Modifiability                    |
| Auditability                | Reliability — Recoverability of evidence           |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                                                                         | Severity | Mitigation                                                                                                                                                                                                                            |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S-R1  | **Single-window catalogue not yet built.** Citizen picks programme via SelectBox today; the spec calls for a guide-screen catalogue with applicability evaluation per programme. UX workaround that obscures eligibility upfront.                                              | Medium   | Phase 1 close-out backlog item.                                                                                                                                                                                                       |
| S-R2  | **`$registry.parcels.*` capability not lit up.** Programme rules touching parcel data are stubbed (placeholder rules that pass for everyone). DET_SMALLHOLDER's "≤5 ha" and DET_AREA_NONZERO need real parcel data to evaluate against.                                          | High     | Phase 1 close-out backlog. Wire the parcel summary capability adapter; expand SqlPathEvaluator to handle `$registry.parcels.summary.*` references.                                                                                  |
| S-R3  | **No save-as-draft affordance.** Wizard saves the row at every Next-tab transition (server-side via the page render's load binder), but there's no UX-level "draft" state distinct from "submitted." Citizen who closes the browser mid-wizard loses no data, but the row's status reads as if submitted. | Low      | Phase 1 close-out: introduce `status='draft'` and an explicit "Submit" button on the Review tab that transitions draft → submitted (which then triggers eligibility evaluation).                                                      |
| S-R4  | **No GIS polygon capture on parcel tab.** The `APP_PARCEL` screen has plain text fields. Native parcel registry has full polygon capture via `GisPolygonCaptureElement`. UX gap.                                                                                              | Medium   | Phase 1 close-out: kernel pass-through for `gis_polygon` widget kind.                                                                                                                                                                |
| S-R5  | **`MetaReviewElement` per RegBB §7.2 not built.** Operator review uses wizard-level readonly flag. Per-role per-tab readonly mask via `mm_role_screen.sectionsJson` is Phase 2.                                                                                                | Medium   | Phase 2 deliverable. Today's pattern works; the spec-conformant implementation is additive.                                                                                                                                          |
| S-R6  | **Programme content is not version-controlled by the metamodel.** Editing `DET_LOWLANDS` mid-cycle changes how *all* in-flight applications evaluate the rule. The audit row's `c_rule_version` records when it last changed, but there's no "publish v2 vs v1" mechanism.       | Medium   | Future ADR. For now, operational discipline: don't edit determinants mid-cycle; supersede with a new code (`DET_LOWLANDS_V2`) and update the registration to point at it.                                                            |
| S-R7  | **Workflow integration depth is one demo action.** `NOTIFY_APPROVAL` exists; richer workflows (entitlement issuance to IM, deadline-based escalation, appeals path) are not yet wired. Sysadmin must author each XPDL.                                                            | Low      | Operational concern. The framework boundary is correct; the work is sysadmin XPDL authoring per programme.                                                                                                                          |
| S-R8  | **Fixture seed and live database can drift.** The YAML fixture is the source of truth; `seed.py` upserts it. But operator edits via App Composer or the admin CRUDs land in the database directly, not in the YAML. Drift accumulates.                                          | Medium   | Operational discipline: programme changes go through YAML + seed, not direct CRUD edits. Phase 2 Programme Builder writes to YAML on save.                                                                                          |

---

## 12. Glossary (module-specific)

| Term                          | Definition                                                                                                                                                                                                                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Subsidy module**            | The collection of `mm_*` rows + form/datalist/userview JSON + seed fixture that, when interpreted by the kernel and framework, produces the Lesotho 2025 subsidy application capability.                                                                              |
| **Programme**                 | One `mm_registration` row representing a specific subsidy offering (e.g. PRG_2025_001 — Block Farming Maize Lowlands).                                                                                                                                                  |
| **Service**                   | The bundle of programmes administered together. Today: SUBSIDY_2025.                                                                                                                                                                                                  |
| **Applicant**                 | A citizen filling in or who has filled in an application. Identified by national_id.                                                                                                                                                                                   |
| **Determinant**               | A boolean predicate authored as an `mm_determinant` row. Subsidy module determinants currently express applicability/eligibility scope only.                                                                                                                            |
| **Disposition**               | The outcome of eligibility evaluation. See framework SAD glossary.                                                                                                                                                                                                     |
| **Status**                    | The application's lifecycle state. See framework SAD glossary.                                                                                                                                                                                                         |
| **Required doc**              | An `mm_required_doc` row identifying a document type the applicant must upload. Service-level (always required) or programme-specific (required only when applicant's applied_programme matches the doc's registrationId).                                              |
| **Operator review surface**   | The operator-facing wizard rendered by `subsidyApplicationOperator2025` form + `MetaWizardElement(roleScreenId=...)`. Citizen tabs read-only; OP_DECISION editable.                                                                                                  |
| **Decision**                  | An operator's verdict on an application: approve, reject, send_back. Persisted to `decision`, `decision_score`, `decision_comment`, `decided_at` columns; triggers status transition + workflow dispatch.                                                                |
