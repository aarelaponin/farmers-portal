# MM Authoring — A Conceptual Guide

Audience: analysts and developers who'll work with the `mm_*` meta-model.
Read this once before opening MM-Configuration. Decisions and conventions are
cross-referenced to the formal decision log entries (D…) in `decision-log.md`.

This guide is a working document — it captures the conceptual model that
emerged through Phase 1 development. Polish and examples to be added as more
real programmes land.

---

## 1. What is the meta-model

The `mm_*` entities together describe a *registration service* —
configuration that the engine reads at runtime to produce citizen-facing
forms, evaluate eligibility, manage applications, and orchestrate
operators. The principle is **configuration over code on both citizen and
registry sides**: an analyst can stand up a new programme by editing
`mm_*` rows, without writing or redeploying Java.

This is RegBB's data model (per spec §7.2) plus the Lesotho extensions
documented in the convergence framework §9 (Master Data, cascading
filters, scoring, repeating groups, score-based evaluation, acceptance
windows).

---

## 2. The conceptual hierarchy

The twelve entities organise into four mental groups. Every entity in
groups B–D ultimately points back to something in group A.

### Group A — The service definition (top-down structural)

These four define *what is being offered* and *how the citizen experiences
it*. The order is also the dependency order: each one references the
previous.

| # | Entity            | Role                                                            |
|---|-------------------|-----------------------------------------------------------------|
| 1 | `mm_institution`  | Who owns the service (Ministry, Authority, Department).         |
| 2 | `mm_service`      | The single-window container — one citizen-facing entry point.   |
| 3 | `mm_registration` | Each distinct outcome the citizen can apply for in this service. |
| 4 | `mm_screen`       | A page in the citizen application flow.                          |

The non-obvious one is **registration vs service**. RegBB calls this the
*Single-Window pattern* (§4.1.3 / §6.3.1.9): one service can offer many
registrations, with shared screens but differentiated rules. Why this is
the right shape:

* **Single citizen entry point.** The applicant doesn't navigate "which
  programme am I eligible for?" across four portals — they fill one
  application and the engine determines which registration(s) they qualify
  for.
* **Shared application data, differentiated outcomes.** Identity,
  household, parcel data is collected once. Each registration carries its
  own eligibility rules, required documents, benefits, fees, decision
  logic, and acceptance window.
* **Donor / governance separation preserved.** Each registration can have
  its own funding source, legal basis, and audit trail without forking the
  citizen experience.

Concrete Lesotho example: `SUBSIDY_2025` is the umbrella service. Inside it
sit four registrations — `PRG_2025_001` (Block Farming, government-funded),
`PRG_2025_002` (Mountain Pulses, mixed FAO/IFAD), `PRG_2025_003` (E-Voucher
Pilot, World Bank), `PRG_2025_004` (Drought Emergency, WFP/UNICEF).
Different acceptance windows, different evaluation strategies, different
benefits — same application form.

### Group B — Reusable building blocks

These are referenced from many other entities. The analyst usually creates
them on demand when authoring a screen or a determinant.

| # | Entity          | Role                                                            |
|---|-----------------|-----------------------------------------------------------------|
| 5 | `mm_catalog`    | Master Data lookup list referenced by select/radio/checkbox fields. |
| 6 | `mm_determinant`| A rule. Returns true/false; gates visibility, applicability, eligibility, fees, benefits, documents, screens. |
| 7 | `mm_action`     | A named procedural callback (save, submit, validate, calculate, send-email). Attachable to fields/buttons. |

`mm_catalog` is Lesotho's Master Data implementation — RegBB §7 doesn't
formalise it (see D22 + convergence-framework §9.1). Catalog rows can be
*static* (`itemsJson` holds the options inline) or *registry-sourced*
(`imCapabilityRef` points at an upstream data source, including any Joget
form like the existing `md*` lookups).

### Group C — Per-registration outcomes

What the applicant gets, has to provide, or has to pay. Every row carries
a `registrationId` because each programme has its own.

| # | Entity              | Role                                                  |
|---|---------------------|-------------------------------------------------------|
| 8 | `mm_required_doc`   | A document the applicant must upload.                 |
| 9 | `mm_fee`            | An amount the applicant must pay.                     |
| 10 | `mm_benefit`        | An outcome the applicant receives (input pack, cash, voucher). |

All three are typically *gated by determinants* — "this document is
required only if the applicant is in the lowlands", "this fee waived for
applicants under Drought Emergency", "this benefit applies if score ≥ 80".

### Group D — The operator side

How an application is processed after submission.

| # | Entity              | Role                                                  |
|---|---------------------|-------------------------------------------------------|
| 11 | `mm_role`           | A processing role (reviewer, approver, signer).       |
| 12 | `mm_role_screen`    | The operator-facing screen for a role.                |

### Group E — A child of Group A (kept as escape hatch during development)

| #   | Entity     | Role                                                              |
|-----|------------|-------------------------------------------------------------------|
| 4.1 | `mm_field` | A single input on a screen. Authored *inside* a `mm_screen` via the FormGrid (D19); a parallel CRUD menu remains during development for QA inspection but is dropped when the system stabilises. |

---

## 3. The MM-Configuration menu order

Following the conceptual groups, in dependency order:

1. Institution
2. Service
3. Registration
4. Screen
5. Field *(during development; remove once stable)*
6. Catalog
7. Rules (unified — eligibility / quality / bot_pull) — see §6
8. Action
9. Required Document
10. Fee
11. Benefit
12. Role
13. Role Screen

---

## 4. Conventions you must internalise (D20, D22, D23)

### 4.1 Two kinds of foreign keys

There are two distinct FK conventions in this app, and confusing them is
the most common source of bugs.

* **Joget-internal FK.** Relationships managed automatically by Joget
  machinery — FormGrid row → parent, wizard tab → wizard, AbstractSubForm
  → host. Stores the parent's auto-generated `id` (UUID). Hidden from
  authors, never shown on forms. Example: `mm_field.screenId` (FormGrid
  on `mm_screen` writes the parent screen's UUID into this column).
* **Cross-entity reference.** Relationships established explicitly by an
  analyst picking from a dropdown or SmartSearch. Stores the target's
  business `code`, not its UUID. Example:
  `mm_registration.serviceId = "SUBSIDY_2025"` (literally the code, not a
  UUID). FormOptionsBinder is configured with `idColumn: "code"` so the
  dropdown both displays and stores codes.

The empirical anchor for the convention is `farmerBasicInfo`:
`national_id` is the business key (with DuplicateValueValidator);
`parcel.farmer_id` stores `national_id`, not the farmer's UUID.

### 4.2 Every `mm_*` entity has the same shape

* `id` — Joget auto-UUID. Internal, never exposed on the form.
* `code` — TextField with DuplicateValueValidator self-referencing. Both
  mandatory and unique. UPPER_SNAKE_CASE values: `MIN_AGRO`,
  `INPUT_SUBSIDY_001`, `FIRST_SUBSIDY_INTRO`.
* `name` (or `title` for `mm_screen`) — TextField with mandatory
  DefaultValidator. Display label, doesn't have to be globally unique.

`mm_field` is the one exception: no global `code`, because fields are
scope-local — the natural key is `(screenId, storageKey)` and Joget can't
enforce composite uniqueness via DuplicateValueValidator. Disciplined
storageKey naming within a screen is sufficient.

### 4.3 No `isActive` (D23)

The meta-model uses **hard-delete semantics**. A code can be removed only
if nothing references it. Obsolete codes are not deactivated — they are
replaced by adding a new code, both rows coexist, new applications use
the new code, historical references continue to resolve.

Don't add `isActive` retroactively. If true temporal validity is ever
needed, design it properly (validity windows, point-in-time queries,
version-aware FK uniqueness), not as an afterthought.

---

## 5. Authoring flow — a worked example

To stand up a new programme:

1. **Confirm the institution exists** (`mm_institution`). Most programmes
   reuse `MIN_AGRO`. If your service spans ministries, you can add more.
2. **Decide service vs. new registration.** Is this a new outcome under
   an existing umbrella service (then it's a new `mm_registration`), or
   genuinely separate (a new `mm_service`)? Test: would the citizen apply
   through the same form? Same → registration. Different form entirely →
   service.
3. **Create the `mm_registration`** with code (UPPER_SNAKE_CASE), name,
   service link, evaluation strategy, passing thresholds, acceptance
   window dates.
4. **Define screens.** Most can be reused from the parent service.
   Screen-level customisation per registration uses determinants
   (`mm_screen.visibilityDeterminantId`).
5. **Add fields to the screens** — directly inside the Screen edit page
   via the FormGrid. Each field gets a storageKey, label, widget type, and
   optional gating determinants. If a field needs a lookup list, point its
   `optionsCatalogId` at a `mm_catalog` row.
6. **Define rules.** Visibility / applicability / requiredness /
   eligibility / form-quality / bot-pull / decision-mapping / budget-
   estimation rules. Every rule lives in `mm_determinant`, discriminated
   by the `scope` column (see §6 for the full scope catalogue and per-
   scope authoring instructions). Use the partitioned-grammar DSL
   (canonical) — the engine routes to fast-path or SQL-path automatically
   (per ADR-001r2).
7. **Required documents, fees, benefits.** Add per-registration rows; gate
   each with a determinant if applicability varies.
8. **Operator side.** Define `mm_role` rows for the processing chain;
   `mm_role_screen` rows for what each role sees.
9. **Publish.** The `reg-bb-publisher` plugin (Phase 1 D17) generates the
   citizen userview menu and operator inspection menu for the service.

Test data while developing: use the seeder (`tooling/seed.py` with
`app/seeds/lesotho-mm-fixture.yaml`). Re-runnable, idempotent,
configuration-as-code.

---

## 6. Authoring rules — six scopes, one table (ADR-031)

Every rule the platform evaluates lives in **`mm_determinant`**. There
are no other rule tables. What changes between rule types is the value
in the `scope` column — that tells each consumer in the runtime which
rows belong to it. Six scopes ship today:

| `scope` | Read by | What it does |
|---|---|---|
| `eligibility` | `RoutingEvaluator` (reg-bb-engine) | Pass / fail / warning rules for whether a citizen can apply to a programme. Returns a disposition (`approved`, `rejected`, `pending_review`) per rule, then aggregates per programme. |
| `quality` | `RuleRepository` (form-quality-runtime) | Form-quality probes that fire on every save of a governed form (registration, parcel, programme, application). Each rule produces a severity = `error` or `warning`; results land as rows in `qa_issue` and a rolled-up status in `qa_record_status`. |
| `bot_pull` | `BotPullEvaluator` (reg-bb-engine) | Auto-fill rules invoked by `MetaScreenElement` capability adapters — e.g. "given an NID, populate name / DOB / village from the registry". |
| `applicability` | `RoutingEvaluator` | Catalogue display: "which programmes can this farmer see on their portal landing page". A pre-eligibility filter; cheaper than full eligibility. |
| `decision_to_status` | `DecisionMapper` (reg-bb-engine) | Operator-decision-to-application-status mapping. E.g. "if operator clicks 'Approve' on a stage 2 application, transition status to `approved_for_voucher`". |
| `budget_amount` | `CostEstimationService` | Programme-launch cost estimation: "what's the projected envelope draw for PRG_2025_001 given the eligible-applicant count". |

The analyst lands at **MM-Configuration → MM-Rules (unified)** in the
operator userview. Four list filters sit above the grid: `scope`,
`serviceId`, `code`, `severity`. Filter `scope = quality` to see only
form-quality rules; filter `scope = eligibility` to see only routing
rules. The column shape is unified, the discipline is per-row.

### 6.1 What fields matter for which scope

Not every column is meaningful for every scope. The form exposes them
all but only the scope-relevant ones need to be filled.

* **`code`** (always required) — UPPER_SNAKE_CASE business key.
  Examples: `farmer.has_national_id`, `application.declaration_signed`,
  `prg_001.eligibility.lowlands_only`, `mdm.farmer_lookup_by_nid`.
* **`name`** (always required) — short human label shown in the operator
  audit list.
* **`scope`** (always required) — one of the six values above.
* **`serviceId`** (always required) — the form-quality serviceId
  (`farmer_registration`, `parcel_registration`, `farmers_subsidy`,
  `farmer_application`) for `scope=quality`; the programme code for
  `scope=eligibility/applicability/budget_amount`.
* **`registrationId`** — for `scope=eligibility/applicability` only.
  Pins the rule to a specific programme registration; left blank means
  "applies to all programmes under this service".
* **`tabCode`** — for `scope=quality` only. Groups rules per wizard tab
  in the citizen form (so the issue panel can render "3 issues on Tab 2:
  Household").
* **`severity`** — for `scope=quality` only. `error` blocks workflow
  progression; `warning` shows in the panel but doesn't block.
* **`triggerOn`** — for `scope=quality` only. `save` (default) /
  `field_change` / `manual`. Controls when the probe fires during the
  form lifecycle.
* **`aggregation`** — for `scope=quality` only. `issue_list` (default —
  one issue row per failure) / `disposition` (used by `scope=eligibility`).
* **`affectedFields`** — for `scope=quality` only. Comma-separated list
  of field IDs to highlight when the rule fails. The runtime decorates
  those fields with a red border + tooltip.
* **`ruleType`** — for `scope=eligibility` only. `assignment` (per-rule
  contributes to the disposition) / `inclusion` (includes another rule
  by code, used to compose larger rules).
* **`score`** — for `scope=eligibility` only. Numeric weight contributed
  on pass.
* **`allowSlowPath`** — for `scope=eligibility` only. If `Y`, the engine
  is permitted to fall through to SQL-path evaluation when the
  fast-path can't resolve. Default `N`; set to `Y` only when the rule
  references registry data the citizen-facing form doesn't have yet.
* **`ruleJson`** — the rule body itself. The partitioned-grammar DSL
  (per ADR-001r2): `'$path.expression' op 'value'` for fast-path; raw
  SQL for slow-path (gated by `allowSlowPath=Y`).
* **`actionJson`** — for `scope=decision_to_status` only. The target
  status to transition to.
* **`failMessage`** — citizen-facing error message shown when the rule
  fails. Translatable; keep short (one sentence).
* **`targetValue`** — for `scope=eligibility` rules with numeric
  comparison. The value the rule body compares against.

### 6.2 Worked example — adding a new quality rule

You discovered that operators are submitting parcel records without a
GPS centroid, which breaks the map view. You want a quality probe that
flags any saved parcel where `centroid_lat` is empty.

1. Navigate to **MM-Configuration → MM-Rules (unified)** in the operator
   userview.
2. Click **+ Add**.
3. Fill in:
   - `code`: `parcel.centroid_required`
   - `name`: `Parcel must have GPS centroid`
   - `scope`: `quality`
   - `serviceId`: `parcel_registration`
   - `tabCode`: `geometry` (the wizard tab where centroid_lat lives)
   - `severity`: `warning` (operator can save and fix later) — or `error`
     if you want to block save until fixed
   - `triggerOn`: `save`
   - `aggregation`: `issue_list`
   - `affectedFields`: `centroid_lat,centroid_lon`
   - `ruleJson`: `'$parcel.centroid_lat' != ''` (DSL fast-path)
   - `failMessage`: `GPS centroid is required for the map view to
     render. Capture the parcel polygon to populate it automatically.`
4. Save. Effective immediately on the next save — no redeploy.

### 6.3 Worked example — adding a new eligibility rule

Same form, different scope. You want PRG_001 to require lowlands
agro-zone.

1. Same path: **MM-Configuration → MM-Rules (unified)**, **+ Add**.
2. Fill in:
   - `code`: `prg_001.lowlands_only`
   - `name`: `PRG_001 — lowlands agro-zone only`
   - `scope`: `eligibility`
   - `serviceId`: `farmers_subsidy`
   - `registrationId`: `PRG_001`
   - `ruleType`: `assignment`
   - `ruleJson`: `'$registry.farmer.agro_zone' == 'lowlands'`
   - `failMessage`: `This programme is restricted to lowlands farmers.`
3. Save. The next eligibility evaluation pass will pick it up.

### 6.4 What if a rule uses fields the citizen form doesn't expose

For `scope=eligibility` only: if the rule references registry data not
collected during the application (e.g. a parcel-level fact in the
farmer registration), set `allowSlowPath=Y`. The engine will fall back
to SQL evaluation against the relevant `app_fd_*` tables.

For `scope=quality`: this case shouldn't arise — quality probes run
against the form being saved, so all referenced fields are by
definition present. If a quality probe needs cross-form data, model
it as an eligibility rule and let the engine resolve at submit time.

### 6.5 Where the runtime infra writes — `qa_record_status`, `qa_issue`, `audit_log`

When a quality rule fires on save, the form-quality-runtime writes:

* one row per failing rule into `qa_issue` (table `app_fd_qa_issue`).
* one summary row per saved record into `qa_record_status`
  (table `app_fd_qa_record_status`) — the rolled-up state
  (`verified` / `issues_detected` / `not_validated`).
* one transition row into `audit_log` (table `app_fd_audit_log`)
  whenever the record's quality status changes.

These are **runtime infrastructure**, NOT analyst-authored
configuration. Don't try to edit `qa_issue` or `qa_record_status` rows
by hand — they're rebuilt on every save. The form definitions live
under `farmersPortal` (relocated from the legacy formQuality app in
ADR-031 Slice E.3) and follow bounded-context naming; they're parallel
to `reg_bb_eval_audit` and `processing_queue`, not to `mm_*` master-data
forms.

### 6.6 The retired path

Before May 2026 (ADR-031), quality rules lived in a separate `qa_rule`
form in a separate `formQuality` Joget app, with their own admin UI.
That path is retired. The legacy `qa_rule` Postgres table is kept as a
forensic backup of the migration but is no longer read by any code.
Any documentation or training material that points at "Configuration →
Rules" inside formQuality is obsolete; redirect to MM-Configuration →
MM-Rules (unified).

For the architectural reasoning, see ADR-031 in
`docs/architecture/adr/adr-031-unified-rule-engine.md`. Decision-log entries D43
through D48 walk the slice-by-slice migration.

---

## 7. Common confusions

### "Why isn't my field showing on the citizen form?"

Three usual causes, in order of likelihood:

1. **Wrong screenId on the Meta Screen widget.** It must be a `code`, not
   a UUID. Use the dropdown in the form-builder property panel (build-009
   onward); typing UUIDs by hand is the historical footgun.
2. **The field row's parent isn't this screen.** Check the FormGrid on
   the screen edit page. If the row isn't there, its `screenId` (Joget
   UUID) doesn't match the parent.
3. **A determinant is hiding it.** If `visibilityDeterminantId` is set
   and the rule evaluates false, the field is hidden by design.

### "I changed a code and now everything broke."

Codes are *immutable after first save*. Renaming = creating a new code
and leaving the old one in place. To genuinely rename, clear all
references first, delete the old row, create a new one — or just live
with the historical name. (This is the trade-off for hard-delete /
no-isActive — see D23.)

### "The DatePicker shows `YYYYYYYY-MMMMM-DD` in the placeholder."

You used the Java SimpleDateFormat pattern (`yyyy-MM-dd`) in the
DatePicker `format` property. Joget's DatePicker uses its own dialect:
write `yy-mm-dd` and Joget translates it to Java `yyyy-MM-dd`
internally. See `CLAUDE.md` for the full dialect table.

### "The datalist column shows raw IDs, not labels."

The FormOptionsBinder for that target form has `idColumn: "id"`. Per D20
it should be `idColumn: "code"`. Update the binder and reload.

### "Meta Screen error: ArrayList cannot be cast to FormRowSet."

You hit this if a SelectBox / Radio / CheckBox is being synthesised
programmatically and its `options` property was set as a plain
`List<Map>`. Joget's `FormUtil.getElementPropertyOptionsMap` hard-casts
the `options` property to `FormRowSet`, so it must be a `FormRowSet` of
`FormRow` rows (each carrying `value`, `label`, `grouping` properties).
Inside `MetaScreenElement.synthesiseField` the catalog options are now
built as a real `FormRowSet`; this idiom is required for any future
hand-rolled element that supplies `options` programmatically. Build-012
of `reg-bb-engine` carries the fix.

---

## 8. RegBB compliance and gaps

This implementation is faithful to RegBB §7 with documented Lesotho
extensions (D6 scoring, D9 repeating_group, D16 acceptance window
placement, D22 Catalog-as-MD, D21 cascading filter, D43–D48 unified rule
engine). The extensions fill identified gaps in the spec; see
`convergence-framework.md` §9 for the candidate-feedback list to
GovStack.

The gaps we've identified so far:

1. **Master Data** — RegBB doesn't formalise it. Filled by `mm_catalog`.
2. **Cascading lookup** — RegBB has no field-driven option filtering.
   Filled by `mm_field.optionsFilterField` / `optionsFilterColumn`.
3. **Rule grammar canonicity** — resolved by ADR-001r2 (DSL canonical,
   closed twenty subset as fast-path optimisation).
4. **Form-quality probes as first-class rules** — RegBB §7 doesn't
   mention save-time form-quality probes. We initially modelled them
   as a separate engine (formQuality app, `qa_rule` table). ADR-031
   unified them with eligibility / bot_pull / decision-mapping rules
   under `mm_determinant` discriminated by `scope`. One table, six
   consumers. See §6 above and ADR-031.

---

## 9. Where to look for the formal record

* **`docs/architecture/decision-log.md`** — every non-trivial architectural decision,
  with reasoning and references. ADR-031 entries are D43–D48.
* **`docs/architecture/convergence-framework.md`** — the framework that distinguishes
  RegBB-converged code from transitional bridges and the legacy-to-retire,
  plus the spec gaps identified.
* **`docs/architecture/adr-001-rule-grammar-canon.md`** — DSL vs closed-grammar
  resolution.
* **`docs/architecture/adr/adr-031-unified-rule-engine.md`** — full reasoning for
  the unified `mm_determinant` story (covered in §6 above). The five
  slices A → B → C → D → E.1+E.3 are documented end-to-end with the
  equivalence harness baseline (`e3ce90e02e12d4db`).
* **`docs/architecture/phase-1-plan.md`** — what Phase 1 ships and in what order.
* **`CLAUDE.md`** — the tactical "rules of the road" for agents and
  developers working in this repo: HARD RULE on no raw SQL, FK convention,
  DatePicker dialect, FormGrid load+store binder requirement, the
  unified rule-engine story, etc.
