# IM module — canonical role model

| | |
|---|---|
| Status | Accepted (May 2026) — the role contract for IM Phase 3 implementation |
| Date | 2026-05-06 |
| Owner | Aare Laponin |
| Supersedes | The implicit/divergent actor naming in `docs/architecture/architecture/components/im-module.md` §3.2 and `inputs-mgmt-workflow/01_module_overview/module_overview.md` §"System Actors". Both source docs remain valid for their domains; this note picks one canonical name set for implementation. |
| Companion docs | `docs/architecture/architecture/components/im-module.md` (architecture-tier); `inputs-mgmt-workflow/` (operational-tier 12-task spec); decision-log D36 (Resource Centre as canonical site). |
| What this document is | The canonical role list + capability matrix for the IM module. Becomes the single contract for `mm_role` rows, `mm_role_screen` permissions, Joget user-group naming, and userview menu visibility for IM Phase 3+. |
| What this document is not | A detailed UX spec (per-screen flows live in `inputs-mgmt-workflow/`). A Joget user-management runbook (sysadmin authors users + group memberships in App Composer). |

## 1. Why this document exists

The IM module has been specified in two complementary tiers:

- **Architecture tier** — `docs/architecture/architecture/components/im-module.md` §3.2 names six upstream consumers in MAFSN-organisational terms ("MAFSN procurement officer", "District coordinator", "Extension officer", etc.).
- **Operational tier** — `inputs-mgmt-workflow/` (12-task specification) names six actors in operational-process terms ("Program Officer", "Warehouse Manager", "Distribution Agent", etc.) plus a seventh ("Program Manager") that appears only in `10_workflows/workflows.md`.

The names don't line up. The same MAFSN employee who appears as "MAFSN procurement officer" in the SAD is the "Program Officer" in the workflow spec. Same person, two labels. As Phase 3 ships role-based UX (`mm_role` + `mm_role_screen` + userview menu permissions per RegBB §7.2 / ADR-013-style metamodel reuse), we need ONE name per role — otherwise the metamodel rows accumulate inconsistencies, the userview permissions drift, and operator UX testing surfaces "who does this menu belong to?" confusion.

This document picks the canonical set of seven roles, maps both source-doc names onto each, defines a `mm_role.code` per role, and tabulates capabilities per IM functional area. It is the contract: when authoring `mm_role` rows, configuring user groups, or wiring userview menu visibility for IM, use the names + codes here.

## 2. Canonical roles

Seven user-facing roles plus one system actor. The codes follow the `mm_role.code` UPPER_SNAKE_CASE convention (per ADR-013 / D20).

| Canonical role | mm_role code | Source-doc names | Where they sit | Headline capability |
|---|---|---|---|---|
| **System Administrator** | `IM_ADMIN` | "System Administrator" (workflow); implicit in SAD | MAFSN IT / sysadmin team | System config, MDM curation, user/role provisioning |
| **Procurement Officer** | `IM_PROCUREMENT` | "MAFSN procurement officer" (SAD); "Program Officer" (workflow §01) | MAFSN HQ | Catalogue authoring, supplier registry, allocation planning |
| **Program Manager** | `IM_PROGRAM_MANAGER` | "Program Manager" (workflow §10 actor reference) — not in SAD | MAFSN HQ (senior) | Approves plans, authorises reversals, closes programmes |
| **Warehouse Manager** | `IM_WAREHOUSE` | "Warehouse Manager" (workflow); partial overlap with "District coordinator" (SAD) | At a Resource Centre | Stock receipt, adjustments, transfers, inventory accuracy |
| **Distribution Agent** | `IM_DISTRIBUTION` | "Distribution Agent" (workflow); "Extension officer" (SAD) | At a Resource Centre, farmer-facing | Voucher redemption, distribution recording, farmer verification |
| **Supplier** | `IM_SUPPLIER` | "Supplier (limited operator role)" (SAD); "Supplier" (workflow swimlanes) | External (companies / cooperatives) | Confirms deliveries, updates own delivery records |
| **Farmer** | `IM_FARMER` | "Farmer (read-only)" (SAD); "Farmer" (workflow) | Citizen portal | Views own entitlements + voucher inventory + redemption history |
| *(System actor — not a role)* | `(reporting_engine)` | "Reporting engine" (SAD) | Service inside the platform | Read-only datalist queries powering reports + dashboards. Not assigned to humans; documented for completeness. |

**Naming choices, briefly:**

- **Procurement Officer** wins over "Program Officer" because "Program Officer" overloads with subsidy-side terminology that already exists in the RegBB framework (e.g. an mm_action.kind=`status_change` is dispatched by a "program officer" in subsidy too). Procurement is the unambiguous IM-side name. The SAD's "MAFSN procurement officer" is the canonical phrasing.
- **Warehouse Manager** wins over "District coordinator" because the workflow swimlanes (`Procurement & Stock-In`, `Stock Transfer`) are unambiguously warehouse operations; the SAD's "District coordinator" is a more political title that doesn't match the operational shape. A district may have multiple Resource Centres each with its own Warehouse Manager.
- **Distribution Agent** wins over "Extension officer" because the workflow's `Voucher Distribution` swimlane is operationally a distribution action; "Extension officer" is the broader Lesotho field title that includes farmer training, advisory work, etc. — the IM module touches only the distribution slice.
- **Program Manager** is added explicitly — it appears in workflow §10's actor reference table but not in the SAD's §3.2 consumer list. The role is essential for two-step approval (per workflow 2 step 5: "Program Officer creates plan, Program Manager approves"). Without it, every plan would self-approve.

## 3. Capability matrix

Capabilities listed by IM functional domain × role. `✓` = primary actor; `view` = read-only access; `approve` = authorises but doesn't author; `–` = no access. Adapted from `inputs-mgmt-workflow/01_module_overview/module_overview.md` §"Actor-System Interaction Matrix" with Procurement Officer and Program Manager separated.

| Function | Admin | Procurement | Program Mgr | Warehouse | Distribution | Supplier | Farmer |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Master Data (input categories, units, voucher statuses) | ✓ | view | view | – | – | – | – |
| Resource Centre registry (`md37collectionPoint`) | ✓ | view | view | view | – | – | – |
| Input Catalogue (`md27input`) | view | ✓ | view | view | view | – | – |
| Supplier registry (`im_supplier`) | view | ✓ | view | view | – | own row | – |
| Inventory (`im_inventory`) | – | view | view | ✓ | view | – | – |
| Stock transactions (`im_stock_transaction`) — RECEIPT / TRANSFER / ADJUSTMENT | – | view | view | ✓ | – | view (own deliveries) | – |
| Stock transactions — ISSUE (against voucher) | – | view | view | view | ✓ | – | – |
| Allocation Plan (`im_allocation_plan` / `im_allocation_line`) | – | ✓ author | approve | view | view | – | – |
| Voucher generation (auto on subsidy approval; manual gap-fill) | – | ✓ manual | approve | – | view | – | view (own) |
| Voucher redemption (`im_voucher_redemption`) | – | view | view | – | ✓ | – | acknowledge |
| Distribution event recording (`im_distribution`, `im_distribution_item`) | – | view | view | view | ✓ | – | acknowledge |
| Reports (stock, distribution, programme utilisation, voucher tail) | view | ✓ | ✓ | view | view (own point) | view (own deliveries) | view (own only) |
| Reversals / programme closure | – | view | ✓ | – | – | – | – |

Cross-module capabilities (subsidy → IM bridge) live in the subsidy module's `mm_action.ISSUE_IM_ENTITLEMENT` pattern — the role on the subsidy side that triggers it is the subsidy-module operator, not an IM role. IM's role is to consume the resulting `imEntitlement` row (downstream in Phase 3 Slice 4+).

## 4. Per-role detail

### IM_ADMIN — System Administrator

Sysadmin-scope role. Configures the IM module's plumbing, not its day-to-day data.

- Authors all MD lookup forms (`md_input_unit`, `md_supplier_type`, `md_voucher_status`, `md37collectionPoint`).
- Provisions Joget users + assigns them to the role groups defined here.
- Edits `mm_role` / `mm_role_screen` for IM (this document is the source spec).
- Maintains workflow templates (XPDL processes for voucher issuance, stock alerts) — sysadmin-authored per ADR-011.
- Cross-module: same person also administers subsidy module + Budget Engine; Joget user-group memberships span all three.

**Userview surfaces:** Admin category (already in v.json) + MM - Configuration category + Master Data category.

### IM_PROCUREMENT — Procurement Officer

The MAFSN HQ employee responsible for the supply-chain side of IM.

- Authors and maintains the input catalogue (`md27input` rows + their categories in `md27inputCategory`).
- Onboards suppliers (`im_supplier`); maintains certification status, contact info, performance notes.
- Drafts allocation plans per programme × district × farmer category (Phase 3 Slice 3).
- Generates manual vouchers when the auto-issuance bridge can't cover an edge case.
- Reads stock-level + programme-utilisation reports.

**Userview surfaces:** Inputs Management category (full CRUD on catalog/supplier/allocation plan); Reports category. No access to Admin category or low-level MD lookups.

### IM_PROGRAM_MANAGER — Program Manager

Senior MAFSN role with approval authority. One step above Procurement Officer in the segregation-of-duties chain.

- Approves allocation plans drafted by Procurement Officers (workflow 2 step 5).
- Authorises reversals of completed transactions (returns, error corrections).
- Closes programmes (final reconciliation, budget release).
- Reads all reports including the variance / unredeemed-tail dashboards.

**Userview surfaces:** Inputs Management + Reports categories; the same surfaces as Procurement Officer but with additional "Approve / Reverse / Close" buttons unlocked. Implementation: `mm_role_screen.sectionsJson` overlays the approval-action buttons on the allocation-plan, voucher, and distribution forms when the user is in this role.

### IM_WAREHOUSE — Warehouse Manager

Per-Resource-Centre operational role. Each centre may have one or more Warehouse Managers.

- Records stock receipts from suppliers (workflow 1: Procurement & Stock-In).
- Adjusts stock for damage, expiry, count corrections.
- Initiates and accepts inter-centre transfers (workflow 6: Stock Transfer).
- Monitors per-centre reorder thresholds; raises requests when stock dips.

**Userview surfaces:** Inputs Management → IM - Inventory + IM - Stock Transactions (filtered to their assigned point — the filter is enforced via `mm_determinant.scope=im_point_filter` checking `current_user.point_code == row.point_code`, lit up in Phase 3 Slice 5+). No access to allocation planning, voucher generation, or programme closure.

### IM_DISTRIBUTION — Distribution Agent

Per-Resource-Centre, farmer-facing role. Multiple agents per centre during distribution sessions.

- Verifies farmer identity at the redemption counter.
- Scans / types voucher codes; the system validates eligibility (workflow 4: Voucher Distribution).
- Records redemption events; the matching `im_inventory` row is decremented (Phase 3 Slice 5+ binder).
- For non-voucher distribution (workflow 5: Direct Distribution), records the event without a voucher reference (auditable but rarer).

**Userview surfaces:** Inputs Management → IM - Voucher Redemption (Phase 3 Slice 6+). Filtered to their assigned point. No access to inventory adjustment, allocation planning, or financial reconciliation.

### IM_SUPPLIER — Supplier

External role; suppliers log in to confirm their own deliveries.

- Confirms inventory drops at MAFSN Resource Centres (paired with the Warehouse Manager's RECEIPT entry).
- Updates their own contact info / certification renewals on `im_supplier` (limited to their own row).
- Reads their own delivery history + outstanding stock-purchase orders.

**Userview surfaces:** A small Supplier-portal category (forthcoming Phase 3 Slice 7+) showing only their own row in `im_supplier` + `im_stock_transaction` filtered to their `supplier_code`. Implemented via row-level filter on the load binder, not a separate app — same Joget app, different user group.

### IM_FARMER — Farmer

Read-only role. Farmers see only their own data.

- Views their own voucher inventory (issued vouchers, expiry dates).
- Views their own redemption history (which inputs were collected, when, by which agent at which centre).
- Acknowledges receipt at the distribution counter (workflow 4 step 7) — captured digitally if the centre has signature capture, or implicitly by the agent's distribution-event record.

**Userview surfaces:** Citizen portal — reuses the existing citizen userview category; adds a "My Vouchers" + "My Redemption History" menu. Filter via `farmer_nid == current_user.nid`.

## 5. mm_role / mm_role_screen authoring (forward-looking)

When Phase 3 lands the role-based UX layer, author one `mm_role` per canonical role above:

```yaml
mm_role:
  - code: IM_ADMIN
    name: System Administrator (IM)
    serviceId: INPUTS_2025
    description: Sysadmin scope — system config, MDM curation, user provisioning.
  - code: IM_PROCUREMENT
    name: Procurement Officer (IM)
    serviceId: INPUTS_2025
    description: MAFSN HQ — catalogue, supplier registry, allocation drafting.
  - code: IM_PROGRAM_MANAGER
    name: Program Manager (IM)
    serviceId: INPUTS_2025
    description: MAFSN HQ senior — approves plans, authorises reversals, closes programmes.
  # ...etc
```

Then per role × screen, author `mm_role_screen.sectionsJson` to mask which sections / actions are visible. The capability matrix in §3 above is the authoring source: rows are screens, columns are roles, cells are the visibility/edit/approve rights.

For Joget user-group naming (the platform-level construct that ties a logged-in user to roles):

- Create eight Joget user groups: `im_admin`, `im_procurement`, `im_program_manager`, `im_warehouse`, `im_distribution`, `im_supplier`, `im_farmer`, plus the existing `farmersPortalAdmin` for cross-module sysadmins.
- The `im_warehouse` and `im_distribution` groups are per-centre — name as `im_warehouse_morija`, `im_distribution_morija`, etc. — OR add a `point_code` user attribute and resolve in `mm_determinant`. The latter is cleaner; aim for that.

## 6. Cross-references

- **Architecture-tier**: `docs/architecture/architecture/components/im-module.md` §3.2 (Upstream consumers), §6 (Runtime view scenarios per actor), §10.1 (Quality scenarios with actor stimulus-response).
- **Operational-tier**: `inputs-mgmt-workflow/01_module_overview/module_overview.md` §"System Actors" + §"Actor-System Interaction Matrix"; `inputs-mgmt-workflow/10_workflows/workflows.md` §"Actor Reference" + 7 swimlane diagrams; per-form specs in `inputs-mgmt-workflow/04_form_input_catalog/`, `05_form_supplier/`, `06_form_inventory/`, `07_form_allocation/`, `08_form_voucher/`, `09_form_distribution/`.
- **Decision log**: D20 (FK-by-code convention applies to mm_role too); D35–D36 (Slice 1 + 2 consolidation context); ADR-013 (mm_role is general-purpose, reusable beyond RegBB); ADR-016 (IM uses MM-form-gen kernel only); ADR-017 (mm_service is namespace-only for IM).

## 7. Open questions / parked decisions

- **Per-centre filtering** of inventory + transactions for Warehouse Manager / Distribution Agent — do we use group naming (`im_warehouse_morija`) or a `current_user.point_code` attribute resolved in `mm_determinant`? Recommendation: attribute-based (cleaner, scales to dozens of centres). Decide in Phase 3 Slice 5 when the per-centre filter actually ships.
- **Supplier authentication** — suppliers logging in is a real use case but Lesotho-specific operator UX (one supplier representative per company, manually onboarded by Procurement Officer). No SSO; password auth via Joget's stock user-management. Confirm scope with MAFSN before Phase 3 Slice 7.
- **Auditor role** — Reports access for an external auditor is currently rolled into `IM_PROCUREMENT` + `IM_PROGRAM_MANAGER`. If MAFSN engages an external auditor with read-only access across modules, an `IM_AUDITOR` role can be added later (additive, no model change required).
- **Cross-module role overlap** — a user who is `IM_PROCUREMENT` is likely also a subsidy-module operator. Joget user groups support multiple memberships natively; no platform change needed. Document in the Phase 3 Slice 5+ go-live runbook.

---

*This document is the role contract for IM Phase 3. Future slices reference role names from §2 only. New roles require an update here first.*
