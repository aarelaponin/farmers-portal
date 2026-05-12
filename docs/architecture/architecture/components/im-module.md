# Component SAD — Inputs Management (IM) module

| Field             | Value                                                                                                                                                                                                                                                                                                                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | Inputs Management module                                                                                                                                                                                                                                                                                                                                                                                    |
| Document title    | Software Architecture Description (component level — forward-looking)                                                                                                                                                                                                                                                                                                                                       |
| Version           | 0.1 — DRAFT (architecture intent; implementation not started)                                                                                                                                                                                                                                                                                                                                                |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                                                                   |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                                                                           |
| Related           | Solution-level SAD; kernel SAD; subsidy-module SAD; registry-integration SAD; `inputs-mgmt-workflow/` (the IM business specification — 12 task documents covering catalog, supplier, inventory, allocation, voucher, distribution, reports).                                                                                                                                                                |
| What this is      | The component-level SAD for the Inputs Management module. **Not yet implemented; this document describes architecture intent before code is written.** IM uses the MM-form-gen kernel only (NOT the RegBB framework). Reuses `mm_determinant` for IM-specific business rules and `mm_action` for IM workflow triggers. Its domain — supply-chain logistics, allocation planning, voucher redemption, distribution tracking — is operational ERP territory, not citizen-services territory. |
| What this is not  | A re-description of the IM business specification (those are the 12 documents under `inputs-mgmt-workflow/`). A re-description of the kernel that runs it. A configuration of any specific 2025 IM cycle.                                                                                                                                                                                                  |

---

## 1. Introduction and goals

### 1.1 Purpose and scope

Inputs Management is MAFSN's operational capability to **distribute agricultural inputs** (seeds, fertilizers, pesticides, equipment, veterinary supplies, irrigation materials) **to registered farmers** under subsidy programmes. The IM module supports:

- Catalogue management — defining what inputs are distributed, with specifications, units, pricing.
- Supplier management — tracking the procurement chain.
- Inventory management — stock levels at distribution centres.
- Allocation planning — *who gets what* per programme, district, farmer category.
- Voucher issuance — authorising a specific farmer to claim a specific allocation.
- Voucher redemption — recording the actual collection at a distribution centre.
- Distribution tracking — physical hand-over events with audit trail.

This is a **separate module from subsidies** with its own forms, datalists, business rules, and operator surface. It integrates with subsidies (an allocation plan is bound to a `mm_registration`) and with the farmer registry (vouchers are issued to identified farmers), but it does not run on the RegBB framework.

In scope:

- The IM forms and child grids (`im_input_catalog`, `im_supplier`, `im_inventory`, `im_allocation_plan`, `im_voucher`, `im_voucher_redemption`, `im_distribution`).
- The IM master-data forms (`md_input_category`, `md_input_unit`, `md_supplier_type`, `md_distribution_point`, etc. — see `inputs-mgmt-workflow/03_mdm_tables/`).
- IM business rules expressed as `mm_determinant` rows (e.g. "voucher redemption only at allocated collection point", "stock alert when < threshold", "farmer is in the allocation roster").
- IM workflow triggers expressed as `mm_action` rows (e.g. "on voucher.status=issued, send SMS to farmer with QR code").
- A namespace `mm_service` row (`INPUTS_2025`) that scopes all of the above.
- Cross-module integration: read of `mm_registration` (programme drives allocation), read of `app_fd_farmerbasicinfo` (voucher holder identity), write of `imEntitlement` rows triggered by subsidy approval.

Out of scope:

- Authorship of XPDL workflows for IM lifecycle events (sysadmin's job in Joget's process designer; IM module references them by id via `mm_action.triggerJson.workflowId`).
- A citizen-facing IM wizard. IM is operator-and-supplier-facing, not citizen-facing. (A *farmer* sees vouchers in their portal view, but only as read-only entitlements; the data-entry surfaces are operator-only.)
- Real-time financial reconciliation with the payment provider (out of scope until that integration is designed).

### 1.2 Quality goals (component-level)

| Rank | Quality goal                                | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                       |
| ---- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | **UX — operator/supplier daily-driver feel**| Native Joget UX for every operator/supplier surface: inventory grids, allocation planning tables with sortable/filterable columns, voucher redemption that completes in 2-3 clicks, distribution events captured at a counter without keyboard-fighting. Goal: an extension officer at a distribution point should be able to redeem 50 vouchers/day with zero training reread. |
| 2    | **Joget-native everywhere**                 | IM uses Joget's enterprise FormGrid for inventory rows, EmbeddedDatalist for allocation tables, native CrudMenu for catalogue authoring, native datalists with JdbcDataListBinder for stock reports. The IM module adds **zero** custom widgets to the kernel. If a UX need arises that's not native-Joget, the answer is to evolve the kernel (via solution-level discussion), not the module. |
| 3    | **Sustainability — content over code**      | Adding a new input category, a new distribution point, a new supplier — all are master-data row writes. New allocation policies (e.g. "winter wheat farmers in mountains get priority") are `mm_determinant` rules. Target: IM cycle setup for a new season ≤ 1 working week from a trained operator-analyst, no engineering involvement.                                              |
| 4    | **Integration boundaries are explicit**     | IM reads `mm_registration` for programme metadata, reads `app_fd_farmerbasicinfo` for farmer identity, writes `imEntitlement` rows when subsidy approval triggers an allocation. Each integration is documented as a specific column reference; no opaque cross-module coupling.                                                                                                       |
| 5    | **Auditability — every transaction is logged** | Every voucher issuance, redemption, distribution event lands an audit row. The audit table can be `reg_bb_eval_audit` (reused) for IM business rule evaluations (`mm_determinant`), and a dedicated `im_transaction_log` for transactional events (issuance, redemption, distribution) with movement quantities and counterparty IDs.                                          |

### 1.3 Stakeholders

| Stakeholder                            | Concerns                                                                                                                                                              |
| -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MAFSN procurement / planning**       | Allocation planning is configurable; reports show stock and distribution by programme/district; supplier performance is tracked.                                       |
| **District coordinator / extension officer** | Day-to-day distribution UX is fast; voucher redemption is intuitive; offline degradation (when network is patchy) doesn't lose data.                              |
| **Supplier**                           | Inventory updates, delivery confirmations, payment status are visible.                                                                                                  |
| **Farmer (read-only)**                 | Sees their vouchers + entitlements + redemption history through the citizen portal.                                                                                     |
| **Auditor / ministry inspector**        | Distribution events are auditable; voucher chain (issuance → redemption) is intact; no orphan vouchers, no double-redemption.                                           |

---

## 2. Architecture constraints

Inherits all solution-level + kernel constraints. Module-specific:

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                                       |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| I-C1  | **IM does not use the RegBB framework.** No `mm_registration` rows for IM (registrations are RegBB's "citizen registration" concept, doesn't apply). No `mm_required_doc` or `mm_benefit` for IM (these are RegBB's "what the citizen submits and receives in return" concept). IM uses `mm_service` (namespace), `mm_determinant` (rules), `mm_action` (workflow triggers), and the kernel for forms. |
| I-C2  | **IM forms are native Joget (with kernel for some).** Master-data forms (`md_input_category`, `md_supplier_type`, etc.) are native Joget — they don't need metadata-driven rendering. Transactional forms (`im_voucher`, `im_distribution`) MAY use the kernel where the dynamic-field shape matters, but default is native Joget where the form structure is stable.                                |
| I-C3  | **No XPDL authoring or generation by the IM module.** Same boundary as RegBB. XPDL is sysadmin-authored in Joget's process designer; IM dispatches via `mm_action`.                                                                                                                                                                                                                              |
| I-C4  | **Voucher chain integrity.** A voucher row's lifecycle (issued → redeemed → reconciled) is enforced by Joget's status-framework pattern (transition map per entity type) + database-level integrity (foreign keys to issuance lot, redemption events). Bypassing the chain (e.g. a "redeemed" voucher with no issuance) is a defect.                                                                  |
| I-C5  | **No real-time inventory updates from external systems in v1.** Stock arrives via supplier-side data entry or batch import. A future ERP integration is out of scope until designed.                                                                                                                                                                                                              |

---

## 3. Context and scope

### 3.1 IM module's place in the system

```
                        ┌────────────────────────────────────────┐
                        │    Operator UI (Farmers Portal)        │
                        │    Supplier UI (limited surface)       │
                        │    Citizen UI (vouchers read-only)     │
                        └────────────┬───────────────────────────┘
                                     │
                                     ▼
       ┌────────────────────────────────────────────────────────────┐
       │  IM module  (configuration + forms + datalists)            │
       │                                                            │
       │  mm_service: INPUTS_2025 (namespace bundle)                │
       │     │                                                      │
       │     ├── mm_determinant (IM business rules, scope='im_*')   │
       │     ├── mm_action (workflow triggers for issuance, etc.)   │
       │     └── mm_screen / mm_field / mm_catalog (kernel-shaped   │
       │           forms where dynamic-field shape matters)         │
       │                                                            │
       │  Native-Joget forms (most forms):                          │
       │     im_input_catalog                                       │
       │     im_supplier                                            │
       │     im_inventory + im_stock_transaction                    │
       │     im_allocation_plan + im_allocation_line                │
       │     im_voucher + im_voucher_redemption                     │
       │     im_distribution + im_distribution_item                 │
       │     im_transaction_log (audit)                             │
       │                                                            │
       │  Master-data forms:                                        │
       │     md_input_category, md_input_unit, md_supplier_type,    │
       │     md_distribution_point, md_voucher_status, ...          │
       └────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
        ┌─────────────────────────────────────────────────────────────┐
        │ Reads (cross-module)                                        │
        │   mm_registration (programme metadata for allocation)       │
        │   app_fd_farmerbasicinfo (voucher holder identity)          │
        │   app_fd_subsidy_app_2025 (subsidy approval triggers        │
        │     entitlement issuance via mm_action)                     │
        └─────────────────────────────────────────────────────────────┘
```

### 3.2 Upstream consumers

| Consumer                          | Interaction                                                                                                                                                                                                |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| MAFSN procurement officer         | Authors the input catalogue; maintains supplier records; reviews quarterly allocation plans.                                                                                                                |
| District coordinator              | Reviews stock at their distribution point; issues vouchers when applicable; records distribution events; reconciles end-of-day.                                                                              |
| Extension officer                 | Looks up a farmer's entitlements when they arrive; redeems voucher (transaction logging).                                                                                                                    |
| Supplier (limited operator role)   | Updates their delivery records; confirms inventory drops.                                                                                                                                                    |
| Farmer (read-only)                | Views their voucher inventory + redemption history through the citizen portal.                                                                                                                              |
| Reporting engine                  | Reads IM tables for stock reports, distribution-by-district, programme-utilisation, voucher unredeemed-tail, supplier-performance.                                                                          |

### 3.3 Cross-module integrations

| Integration                                                | Direction                                | Mechanism                                                                                                                                                                                                                              |
| ---------------------------------------------------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Allocation plan → programme                                | IM reads from RegBB's `mm_registration`  | `im_allocation_plan.programme_code` (FK by code per D20) references `mm_registration.code`. IM's allocation-line filters use the programme's eligibility/applicability metadata.                                                       |
| Voucher → farmer                                           | IM reads from farmer registry            | `im_voucher.farmer_nid` references `app_fd_farmerbasicinfo.c_national_id`. Read-only — IM never writes to the farmer registry.                                                                                                          |
| Subsidy approval → entitlement issuance                    | RegBB triggers IM via `mm_action`        | `mm_action` row in subsidy module: `(SUBSIDY_2025, status_change, onStatus=approved, workflowId=subsidy_approval_to_im_entitlement)`. The XPDL process — sysadmin-authored — writes the `imEntitlement` row. IM's role is to consume that row downstream. |
| Voucher redemption → distribution event                    | IM internal                              | A redemption row links to one or more distribution-item rows (the actual physical hand-over).                                                                                                                                          |
| Stock alert → operator notification                        | IM internal                              | `mm_determinant` rule: `inventory_level < reorder_threshold`. On match (evaluated daily by a scheduled tool), `mm_action` fires a Joget process that sends the alert.                                                                  |
| Reporting engine → IM tables                               | Reporting reads IM                       | Standard JdbcDataListBinder against `app_fd_im_*` tables. No special interface required.                                                                                                                                                |

---

## 4. Solution strategy

### 4.1 Use the kernel for the parts that benefit from it

The kernel's metadata-driven rendering shines when:

- **The set of fields varies by context.** E.g. an allocation-line row's "applicable input categories" depend on the programme's input mix. An `mm_screen` for the allocation-line edit form, with `mm_field.visibilityDeterminantId` toggling visibility based on the programme code, is the right shape.
- **A form is shared across multiple workflows with role-specific masks.** E.g. the voucher edit form: an extension officer sees redemption fields; a procurement officer sees only the issuance metadata. `mm_role_screen.sectionsJson` per role encodes the masks.

Most IM forms do NOT vary by context. They are stable native-Joget forms — `im_supplier` has supplier_id, name, contact_phone, address, supplier_type, certification_number; nothing about that varies. Author it as a hand-built Joget form with a fixed FormBuilder JSON.

**Rule of thumb:** native Joget for stable forms; kernel for forms whose shape varies by configuration.

### 4.2 Reuse `mm_determinant` for IM business rules

IM has business rules. Examples:

- `IM_VOUCHER_REDEEM_AT_ALLOCATED_POINT` — `voucher.allocated_point == redemption_point`.
- `IM_STOCK_BELOW_THRESHOLD` — `inventory.current_qty < inventory.reorder_threshold`.
- `IM_FARMER_IN_ALLOCATION_ROSTER` — `$registry.allocation.is_member_of(plan_id, farmer_nid) == true` (custom capability adapter).
- `IM_VOUCHER_NOT_EXPIRED` — `voucher.expiry_date >= today`.

These are boolean predicates evaluated against `EvalContext.data`. The framework's `RoutingEvaluator` is general — it doesn't know or care that the rule is an IM rule rather than a subsidy eligibility rule. **Reusing the rule infrastructure for IM saves us from inventing a separate rule engine.**

The convention: `mm_determinant.scope` values for IM use a distinct namespace prefix:

- `scope='im_voucher_redemption'` — fires at voucher redemption time.
- `scope='im_stock_alert'` — fires on inventory updates.
- `scope='im_allocation_filter'` — fires when filtering the farmer roster for an allocation plan.

### 4.3 Reuse `mm_action` for IM workflow triggers

Same pattern. Examples:

- `IM_VOUCHER_ISSUED` — on `voucher.status=issued`, dispatch a Joget process that sends an SMS with the voucher's QR code.
- `IM_REDEMPTION_RECONCILED` — on daily batch run, dispatch reconciliation workflow.
- `IM_STOCK_ALERT` — on stock-below-threshold rule TRUE, dispatch alert workflow.

The XPDL workflows themselves are sysadmin-authored. IM dispatches them via the existing `WorkflowDispatcher`.

### 4.4 `mm_service` as namespace

`mm_service` rows for IM exist purely as a namespace bundle:

```yaml
mm_service:
  - code: INPUTS_2025
    name: Inputs Management — 2025/26 Cycle
    institutionId: MAFSN
    institutionDept: Inputs Distribution
```

All IM `mm_*` rows carry `serviceId='INPUTS_2025'` so they group together. **No RegBB framework code interprets `INPUTS_2025` as a registration-based service** — it's just a string that scopes IM's metamodel rows. Cleanly avoids the question "is this a citizen service?" — answer for IM: no, but it shares the metamodel scaffolding.

### 4.4-bis Budget integration via the Budget &amp; Commitment Engine

The IM module emits two budget funnel events: **Commitment** when a voucher is issued, and **Expense** when a voucher is redeemed. Both flow through `mm_action.kind=budget_event` rows, identical to the subsidy module's pattern. IM does not import Budget Engine code; it just emits events the engine listens for.

```yaml
mm_action:
  - code: BUDGET_COMMIT_ON_VOUCHER_ISSUE
    serviceId: INPUTS_2025
    kind: budget_event
    triggerJson: |
      { "onVoucherStatus": "issued",
        "eventType": "COMMITMENT",
        "amountFormulaRule": "VOUCHER_COMMITMENT_AMOUNT" }

  - code: BUDGET_EXPENSE_ON_REDEMPTION
    serviceId: INPUTS_2025
    kind: budget_event
    triggerJson: |
      { "onVoucherStatus": "redeemed",
        "eventType": "EXPENSE",
        "amountFormulaRule": "VOUCHER_REALISED_AMOUNT" }

  - code: BUDGET_RELEASE_COMMIT_ON_VOUCHER_EXPIRE
    serviceId: INPUTS_2025
    kind: budget_event
    triggerJson: |
      { "onVoucherStatus": "expired_unredeemed",
        "eventType": "RELEASE_COMMITMENT" }
```

The amount formula rules (also `mm_determinant` rows) compute the per-event amount:

```yaml
mm_determinant:
  - code: VOUCHER_COMMITMENT_AMOUNT
    scope: budget_amount
    ruleJson: voucher_quantity * input_unit_price_at_issuance

  - code: VOUCHER_REALISED_AMOUNT
    scope: budget_amount
    ruleJson: redeemed_quantity * input_unit_price_at_redemption
```

When `voucher_quantity == redeemed_quantity` and prices are identical, COMMITMENT and EXPENSE amounts match. Price drift between issuance and redemption produces a difference; the Budget Engine writes an additional `EXPENSE_ADJUSTMENT` event for the delta.

**Voucher → application traceability.** A voucher's `correlation_id` chain — voucher → entitlement → approved application → submission → reservation — is preserved through the Budget Engine's ledger. Forensic queries follow the chain via the `correlation_id` and `correlation_type` columns on `budget_event`.

**Pricing policy is a rule, not a column.** Whether a voucher's commitment uses *current* price, *issuance-time* price, or *issuance-day-mid-month-average* price is determined by the `VOUCHER_COMMITMENT_AMOUNT` rule's expression. Different programmes can use different pricing policies via different rules.

### 4.5 Native Joget for transactional surfaces, kernel for configurable surfaces

The voucher redemption screen at a distribution counter:

- **Transactional core** (NID lookup, quantity dropdown, redeem button) — native Joget. Stable shape, no benefit from metadata-driven rendering.
- **Programme-specific decorations** (e.g. for PRG_2025_001 vouchers, show the cooperative membership cell; for PRG_2025_002, show the mountain-zone cell) — kernel-driven via `mm_field.visibilityDeterminantId` or programme-specific extra screens.

Distribution officers should not see "the system feels different per programme" — the kernel's job here is to make programme-specific cells appear/disappear without restructuring the form.

---

## 5. Building block view (component internals)

### 5.1 IM forms inventory (per `inputs-mgmt-workflow/` spec)

Drawing from the existing 12-task IM specification:

| Form                          | Purpose                                                                                                                                       | Native Joget or Kernel-shaped |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------- |
| `im_input_catalog`            | Input catalogue — a stock-keeping unit per row. Code, name, category, unit, specifications.                                                  | Native                        |
| `im_supplier`                 | Supplier registry — supplier id, name, contact, type, certification.                                                                          | Native                        |
| `im_inventory`                | Stock balance per (input × distribution_point). Current quantity, reorder threshold.                                                          | Native                        |
| `im_stock_transaction`        | Stock movements — receipts from suppliers, issues to vouchers, adjustments, transfers.                                                         | Native                        |
| `im_allocation_plan`          | Allocation plan per programme + cycle. Total quantity per input, target farmer category, district scope.                                       | Native (with kernel for config-varying lines) |
| `im_allocation_line`          | One line per (plan × input × district × farmer-category). Quantity, priority weight.                                                           | Kernel-shaped (programme drives field visibility) |
| `im_voucher`                  | Voucher record — voucher_code, farmer_nid, allocation_line_id, allocated_point, status, expiry_date.                                            | Native                        |
| `im_voucher_redemption`       | Redemption event — voucher_id, redemption_point, redemption_date, redeemed_by_officer, signature.                                              | Native                        |
| `im_distribution`             | Distribution event header — date, point, officer, summary.                                                                                     | Native                        |
| `im_distribution_item`        | Line items per distribution — voucher_id (or null for non-voucher distribution), input, quantity.                                              | Native                        |
| `im_transaction_log`          | Audit table for IM transactional events (separate from `reg_bb_eval_audit` which holds rule evaluations).                                       | Native                        |

The 7 master-data forms (`md_input_category`, `md_input_unit`, `md_supplier_type`, `md_distribution_point`, `md_voucher_status`, etc.) are all native Joget MD forms following the same pattern as the existing `md03district` etc.

### 5.2 IM mm_* rows (configuration content)

| Entity            | IM rows                                                                                                                                                                                                                                                                                                          |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `mm_service`      | 1 row: `INPUTS_2025`.                                                                                                                                                                                                                                                                                            |
| `mm_determinant`  | ~10–15 rows for IM business rules (voucher constraints, stock alerts, allocation filters).                                                                                                                                                                                                                       |
| `mm_action`       | ~5–10 rows for IM workflow triggers.                                                                                                                                                                                                                                                                              |
| `mm_screen`       | ~4–6 rows for kernel-shaped IM screens (allocation-line editor, voucher redemption with programme-specific cells, etc.). Most IM forms are native, so this count is small.                                                                                                                                       |
| `mm_field`        | ~10–20 rows accompanying the kernel-shaped screens.                                                                                                                                                                                                                                                              |
| `mm_catalog`      | Reuses subsidy module's catalogues where applicable; adds IM-specific catalogues (input-category list, voucher-status list) by reference to the IM master-data forms.                                                                                                                                            |

Total IM `mm_*` row count: ~30–50, much smaller than subsidy module because most IM forms are native Joget.

### 5.3 IM-specific business rules (sample)

```yaml
mm_determinant:
  - code: IM_VOUCHER_REDEEM_AT_ALLOCATED_POINT
    name: Voucher must be redeemed at the allocated distribution point
    serviceId: INPUTS_2025
    scope: im_voucher_redemption
    ruleType: inclusion
    ruleJson: voucher_allocated_point == redemption_point
    failMessage: This voucher is allocated to a different distribution point.

  - code: IM_VOUCHER_NOT_EXPIRED
    name: Voucher is within its validity window
    serviceId: INPUTS_2025
    scope: im_voucher_redemption
    ruleType: inclusion
    ruleJson: voucher_expiry_date >= today
    failMessage: This voucher has expired.

  - code: IM_FARMER_IN_ALLOCATION_ROSTER
    name: Farmer is on the allocation roster for this plan
    serviceId: INPUTS_2025
    scope: im_voucher_issuance
    ruleType: inclusion
    ruleJson: $registry.allocation.is_member_of_roster == true
    failMessage: This farmer is not on the allocation roster for the chosen plan.
```

(The rule grammar's reference scope `$registry.*` is generalised in the framework's capability registry — IM adds capabilities without changing the framework code.)

### 5.4 Cross-module workflow trigger — subsidy approval → IM entitlement

When `RegBbOperatorDecisionBinder` transitions a subsidy application to `approved`, the subsidy module's `mm_action` (currently just `NOTIFY_APPROVAL`) is dispatched. To wire entitlement issuance:

```yaml
# Added to the subsidy module's mm_action seed:
mm_action:
  - code: ISSUE_IM_ENTITLEMENT
    name: Issue IM entitlement on approval
    serviceId: SUBSIDY_2025
    registrationId: ""              # service-wide
    kind: status_change
    triggerJson: |
      { "onStatus": "approved",
        "workflowId": "subsidy_approval_to_im_entitlement" }
```

The XPDL workflow `subsidy_approval_to_im_entitlement` (sysadmin-authored) does:

1. Read the application row (provided as workflow context by `WorkflowDispatcher`).
2. Determine the applicable IM allocation plan from `applied_programme`.
3. Determine the input mix from the plan.
4. Write one `imEntitlement` row per input (using a Joget tool step that calls `FormDataDao.saveOrUpdate`).
5. Optionally fire a downstream `IM_ENTITLEMENT_ISSUED` action that sends the citizen an SMS.

The IM module **consumes** these entitlement rows downstream (vouchers can be issued against entitlements). It does not author the cross-module workflow itself.

---

## 6. Runtime view

### 6.1 Allocation planning — operator authors a plan

1. Operator opens **MOA Office → Inputs Management → Allocation Plans → New**.
2. Selects programme code from a dropdown (sourced from `mm_registration` rows of `SUBSIDY_2025`).
3. The form (kernel-shaped) reads the programme's metadata, populates the input mix dropdown with relevant inputs, and shows district-level allocation lines.
4. Operator fills allocation quantities per district; saves.
5. Standard Joget save lifecycle persists the plan + lines.

### 6.2 Voucher issuance — auto-triggered by subsidy approval

1. Subsidy operator approves an application.
2. `RegBbOperatorDecisionBinder` transitions status to `approved`.
3. `WorkflowDispatcher` fires `ISSUE_IM_ENTITLEMENT` action → starts XPDL workflow.
4. Workflow:
   - Reads `applied_programme` from the application row.
   - Looks up the active `im_allocation_plan` for that programme.
   - Validates farmer is on the roster (`mm_determinant: IM_FARMER_IN_ALLOCATION_ROSTER`).
   - Writes one `im_voucher` row per allocation-line × farmer combination, with `status=issued`, `expiry_date=plan.expiry`.
   - Fires downstream `IM_VOUCHER_ISSUED` action → SMS to citizen.

The framework, IM module, and sysadmin XPDL each play their part; no module reaches into another's code.

### 6.3 Voucher redemption — at distribution counter

1. Extension officer at distribution point opens **MOA Office → Inputs Management → Redeem Voucher**.
2. Officer scans QR code (or types voucher_code).
3. Form looks up the voucher row, displays farmer name, allocated input, quantity.
4. Officer confirms quantity and clicks **Redeem**.
5. `RegBbVoucherRedemptionBinder` (an IM-specific store binder, sibling to `RegBbApplicationStoreBinder`):
   - tx 1: persists the `im_voucher_redemption` row (input, quantity, officer, signature, timestamp).
   - tx 2 (try/catch):
     - Evaluates IM determinants (`IM_VOUCHER_REDEEM_AT_ALLOCATED_POINT`, `IM_VOUCHER_NOT_EXPIRED`). If any fail, rollback redemption + flag for supervisor review.
     - Updates voucher status `issued → redeemed`.
     - Decrements `im_inventory.current_qty`.
     - Writes `im_transaction_log` row.
     - Fires `IM_REDEMPTION_DONE` action → confirmation SMS to citizen + reconciliation log.

### 6.4 Stock alert — daily scheduled check

1. Scheduled tool (Joget tool plugin) runs daily.
2. For each `im_inventory` row, evaluates `mm_determinant: IM_STOCK_BELOW_THRESHOLD`.
3. On TRUE outcome: writes audit, fires `IM_STOCK_ALERT` action → SMS/email to procurement officer.

---

## 7. Deployment view

IM is delivered as:

1. **Form definitions** for all native + kernel-shaped IM forms — JSON files under `app/forms/im/`. Pushed via `form-creator-api`.
2. **Master-data forms** for IM-specific MD entities — JSON files under `app/forms/md/`. Pushed via `form-creator-api`.
3. **Datalists** for IM list views — JSON files under `app/datalists/`. Pushed via `form-creator-api` `/datalists` endpoint.
4. **Userview menus** added to the existing Farmers Portal userview (under MOA Office category for operators, citizen category for read-only voucher view). Pushed via `form-creator-api` `/userviews`.
5. **`mm_*` seed rows** for IM business rules + actions. Added to `app/seeds/lesotho-mm-fixture.yaml` (or a separate `app/seeds/lesotho-im-fixture.yaml`).
6. **A new OSGi bundle `im-runtime`** (forthcoming) — only if Joget extension points are needed beyond what the framework already provides. Examples: a custom `RegBbVoucherRedemptionBinder`, a `StockAlertScheduledTool`, a custom widget for QR scan + voucher lookup.

The current expectation is that IM is mostly configuration + sysadmin XPDL, with a small `im-runtime` bundle for the binders and the QR-scan widget. Estimated bundle size: ~1500–2500 LoC, much smaller than `reg-bb-engine`.

---

## 8. Cross-cutting concepts

### 8.1 Audit — two tables, two scopes

- `reg_bb_eval_audit` holds **rule evaluations** — `mm_determinant` evaluations, regardless of which module's scope. IM rules land here automatically when the framework's `RoutingEvaluator` evaluates them.
- `im_transaction_log` holds **transactional events** — issuance, redemption, distribution, inventory adjustment. Specific to IM; doesn't fit the rule-evaluation shape.

### 8.2 Voucher chain integrity

A voucher's lifecycle is enforced via:

- **Status transitions** documented in a transition map (issuable → issued → redeemed → reconciled). Joget's `joget-status-framework` plugin provides the validate-and-audit primitive.
- **Database FKs** between `im_voucher_redemption.voucher_id` and `im_voucher.id`. A redemption row without a matching voucher is impossible.
- **`mm_determinant` rules** at redemption time prevent invalid redemptions (expired vouchers, wrong distribution point).

### 8.3 Offline degradation (UX consideration)

Distribution points may have patchy connectivity. The current architecture is pure online — Joget is server-rendered, no offline mode. This is a known UX gap.

A future option: a thin offline-capable redemption frontend that queues transactions locally and syncs when online. Not in v1 scope; flagged as a Phase 3+ concern.

### 8.4 IM master-data integration with subsidies

Some master-data is shared across subsidies and IM:
- District (md03district) — both modules read it.
- Crops (md19crops) — subsidies use it for primary_crop selection; IM uses it for input applicability.
- Farmer category (md_target_farmer_category) — both modules use it for filtering.

These MD forms remain native-Joget and are referenced by code from `mm_catalog` rows of either module.

### 8.5 Reporting

IM reports are built as native-Joget datalists with `JdbcDataListBinder`, same pattern as the operator inbox. Examples:

- **Stock-by-input-by-point** — pivot table grouped by (input, distribution_point), summing current_qty.
- **Distribution-by-district-by-month** — time-series of distribution events.
- **Voucher unredeemed-tail** — list of vouchers issued > N days ago that are still status='issued'.
- **Programme-utilisation** — % of allocated quantity actually distributed, per programme.

These live in the reporting-engine component (separate SAD).

---

## 9. Architecture decisions (module-relevant)

| Decision               | Title                                                                                                                                                                                | Status            |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------- |
| **(forthcoming)** ADR-016 | IM does not use the RegBB framework — only the kernel + selective metamodel reuse                                                                                              | Pending. Codifies the boundary so it doesn't drift back. |
| **(forthcoming)** ADR-017 | `mm_service` as namespace-only for IM                                                                                                                                          | Pending           |
| **(forthcoming)** ADR-018 | Voucher chain integrity via status framework + FKs + determinants (no scripting in voucher transitions)                                                                       | Pending           |
| **(implicit)** mm_determinant reuse for non-eligibility business rules (rule-management infrastructure is general)                                                                     |                                                                                                                                                                                       | Implicit in solution-level §4.2; will be ADR-013 (forthcoming) |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                       | Source                  | Stimulus                                                       | Artifact                    | Environment    | Response                                                                                              | Measure                                         |
| -------------------------- | ----------------------- | -------------------------------------------------------------- | --------------------------- | -------------- | ----------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| UX — voucher redemption    | Distribution officer    | Scans 50 vouchers in a 2-hour distribution session             | Voucher redemption form     | Production     | Each redemption completes in ≤ 30 seconds; 50 redemptions in under 90 minutes including queue time   | 95% of redemptions ≤ 30s server-time            |
| Joget-native everywhere    | Engineering reviewer    | Audits the IM module for non-native-Joget patterns              | All IM forms + datalists    | Phase 3 review | All forms render via Joget element tree; all datalists use stock binders + formatters; no bespoke HTML | Zero non-native widgets in IM module             |
| Sustainability             | Operator-analyst        | Adds a new input category, supplier, distribution point          | Master-data forms            | Production     | Edits MD form rows via admin CRUD; no engineering involved                                            | Time-to-add ≤ 1 hour per item                    |
| Cross-module integration   | Subsidy approval        | Operator approves a PRG_2025_001 application                    | Subsidy mm_action + IM XPDL | Production     | imEntitlement row created within 60s; SMS sent to farmer with voucher QR                              | End-to-end ≤ 60s; failure rate < 1%             |
| Auditability                | Auditor                 | Asks "which farmers redeemed at point X on date Y"               | im_transaction_log + audit  | Production     | List returned with full farmer NID + voucher_code + officer + signature                               | Query returns within 5s; no missing rows         |

### 10.2 ISO/IEC 25010 mapping

| Goal                       | 25010 characteristic                                |
| -------------------------- | --------------------------------------------------- |
| UX voucher redemption      | Performance efficiency — Time behaviour             |
| Joget-native everywhere    | Maintainability — Reusability                       |
| Sustainability             | Maintainability — Modifiability                     |
| Cross-module integration   | Compatibility — Interoperability                    |
| Auditability                | Reliability — Recoverability + Security — Accountability |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                                                                                          | Severity | Mitigation                                                                                                                                                                                                  |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| I-R1  | **Implementation not started.** Architecture is documented; the 7 forms + datalists + mm_* rows + sysadmin workflows + small Joget bundle for binders all need building. Estimated effort: 4–8 person-weeks.                                                                                  | High     | Formal Phase 3 scope. Sequence: master-data forms first → core forms (catalog, supplier, inventory) → allocation plan → voucher → distribution → reports.                                                  |
| I-R2  | **Allocation-roster capability adapter is non-trivial.** The rule `IM_FARMER_IN_ALLOCATION_ROSTER` needs `$registry.allocation.is_member_of_roster` resolved by the SqlPathEvaluator. That requires extending the SqlPathEvaluator's table-mapping and adding a roster-lookup capability.       | Medium   | Will couple with the framework's Phase 1 close-out work to generalise the capability registry (per RegBB §3.2). Not blocking — voucher issuance can be done without this rule until the registry lands.   |
| I-R3  | **Offline degradation is not designed.** Distribution points with patchy connectivity may lose redemption events.                                                                                                                                                                              | Medium   | Out of v1 scope. Documented for Phase 4+ work.                                                                                                                                                              |
| I-R4  | **Voucher chain integrity is enforced at multiple layers (status framework + FKs + determinants).** Each layer can fail independently. Defensive multiple checks, but operationally complex.                                                                                                  | Low      | Tests covering each transition; formal status-transition map authored in `joget-status-framework` config.                                                                                                  |
| I-R5  | **No real-time financial reconciliation.** Voucher redemption is logged but financial impact (subsidy disbursement to supplier) is reconciled in batch.                                                                                                                                        | Low      | Out of v1 scope. Reconciliation lives outside the IM module — in the reporting engine and (eventually) in a payment integration.                                                                            |
| I-R6  | **Stakeholder split across MAFSN units.** Procurement, distribution, district coordinators, suppliers each have different views. Coordinating UX across roles is a real risk.                                                                                                                  | Medium   | Phase 3 design week with each stakeholder; iterate the operator UX before declaring Phase 3 done.                                                                                                          |

---

## 12. Glossary (module-specific)

| Term                       | Definition                                                                                                                                              |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **IM**                     | Inputs Management — the operational capability described in this SAD.                                                                                   |
| **Input**                  | A specific stock-keeping unit (e.g. "Maize Seed Variety XYZ, 25kg bag") in the catalogue.                                                                |
| **Allocation plan**        | Per programme + cycle, a plan defining which farmer categories in which districts get which inputs in what quantities.                                  |
| **Allocation line**        | One row of an allocation plan — quantity per (input × district × farmer-category).                                                                       |
| **Voucher**                | An authorisation token issued to a specific farmer for a specific allocation, redeemable at a specific distribution point until a specific expiry date.  |
| **Redemption**             | The act of a farmer presenting a voucher at a distribution point and the extension officer recording the physical hand-over.                             |
| **Distribution event**     | The session-level wrapper for redemptions at a point on a date. Multiple redemptions may share one distribution event.                                  |
| **Entitlement**            | A pre-voucher record (`imEntitlement`) created when a subsidy is approved; entitlements are the input to voucher issuance.                              |
| **Distribution point**     | A physical location where inputs are stocked and distributed (district depot, sub-district hub, mobile distribution event).                              |
| **Roster**                 | The list of farmers eligible for a specific allocation plan. Built from the farmer registry filtered by programme + district + farmer category.         |
| **Reconciliation**         | The end-of-cycle process of matching issued vouchers to redemptions and identifying the unredeemed tail.                                                |
