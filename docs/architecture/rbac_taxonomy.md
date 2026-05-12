# RBAC role taxonomy

**Status:** Implementing as Tier A.4 → W1.1-W1.3 of the solo plan.
**Implementation:** Joget built-in user groups + `GroupPermission` on every userview menu. Maps cleanly to Keycloak groups when Keycloak is added later (Keycloak `groups` claim → matched to Joget group ids).

---

## The five roles

| Role id | Persona | Mandate |
|---|---|---|
| `role_field_officer` | Field officer at a district office or resource centre | Registers citizens and parcels on the citizens' behalf; submits applications; sees their own submissions in the inbox. **Cannot approve / reject.** Cannot see other districts' work. |
| `role_district_supervisor` | District supervisor at a district HQ | Everything a field officer does, plus: reviews applications in the inbox, approves / rejects, sees the audit list scoped to their district, runs operational reports. |
| `role_finance_officer` | Finance officer at MAFSN HQ | Sees the entire budget surface (envelopes, ledger, alerts, GL export, donor disbursement, manual adjustments with maker-checker). Sees finance-relevant reports. **Read-only** on operational menus. |
| `role_analyst` | MM-configuration analyst | Authors and edits the metamodel — screens, fields, rules, catalogues, capabilities, role definitions. Maintains all 96 master-data lookups. **No access** to citizen data or budget. |
| `role_sysadmin` | MAFSN ICT system administrator | Sees everything. Manages users, runs scheduled-task surfaces, plugin-management, audit retention. Operational support root. |

## Menu-to-role mapping

Permission is **disjunctive** — a menu listing two roles means a user with EITHER role sees it.

| Category | Menu | Roles allowed |
|---|---|---|
| Dashboard | Executive Overview | sysadmin, district_supervisor, finance_officer |
| Dashboard | District Map (compact) | sysadmin, district_supervisor, finance_officer |
| Dashboard | District Map (geographic) | sysadmin, district_supervisor, finance_officer |
| Registration Forms | 01 - Farmer Registration Form | sysadmin, district_supervisor, field_officer |
| Registration Forms | 02 - Parcel Registration | sysadmin, district_supervisor, field_officer |
| Registration Forms | 2025 Subsidy Application | sysadmin, district_supervisor, field_officer |
| MOA Office | Manage Support Program | sysadmin, district_supervisor, finance_officer |
| MOA Office | 2025 Subsidy Application — Operator Review | sysadmin, district_supervisor |
| MOA Office | RegBB Evaluation Audit | sysadmin, district_supervisor |
| Budget | Envelope state | sysadmin, finance_officer |
| Budget | Per-source breakdown | sysadmin, finance_officer |
| Budget | Recent ledger entries | sysadmin, finance_officer |
| Budget | Pending pipeline | sysadmin, finance_officer |
| Budget | Variance report | sysadmin, finance_officer |
| Budget | Alerts | sysadmin, finance_officer |
| Budget | Roll-forward | sysadmin, finance_officer |
| Budget | General Ledger | sysadmin, finance_officer |
| Budget | Donor disbursement | sysadmin, finance_officer |
| Budget | Manual adjustments | sysadmin, finance_officer |
| Inputs Mgmt | IM - Supplier | sysadmin, district_supervisor |
| Inputs Mgmt | IM - Inventory | sysadmin, district_supervisor |
| Inputs Mgmt | IM - Stock Transactions | sysadmin, district_supervisor |
| Inputs Mgmt | IM - Allocation Plan | sysadmin, district_supervisor, finance_officer |
| Inputs Mgmt | IM - Vouchers | sysadmin, district_supervisor |
| Inputs Mgmt | IM - Redeem Voucher | sysadmin, district_supervisor, field_officer |
| Inputs Mgmt | IM - Print Voucher Slip | sysadmin, district_supervisor, field_officer |
| Inputs Mgmt | IM - Redemption Audit Log (admin) | sysadmin |
| Inputs Mgmt | IM - Distribution Receipts | sysadmin, district_supervisor, field_officer |
| Reports | Overview | sysadmin, district_supervisor, finance_officer |
| Reports | List of Farmers | sysadmin, district_supervisor |
| Reports | IM — Dashboard KPIs | sysadmin, district_supervisor, finance_officer |
| Reports | IM — Voucher utilisation by programme | sysadmin, district_supervisor, finance_officer |
| Reports | IM — Consumption by centre (30d) | sysadmin, district_supervisor |
| Reports | IM — Supplier performance | sysadmin, district_supervisor, finance_officer |
| Reports | IM — End-of-campaign reconciliation | sysadmin, district_supervisor, finance_officer |
| Reports | IM — Funding by donor | sysadmin, finance_officer |
| Reports | IM — Programme funding breakdown (per donor) | sysadmin, finance_officer |
| Reports | Approval rates | sysadmin, district_supervisor, finance_officer |
| Reports | Voucher velocity | sysadmin, district_supervisor, finance_officer |
| Reports | Budget envelope | sysadmin, finance_officer |
| MM-Config | MM - Institution | sysadmin, analyst |
| MM-Config | MM - Service | sysadmin, analyst |
| MM-Config | MM - Registration | sysadmin, analyst |
| MM-Config | MM - Screen | sysadmin, analyst |
| MM-Config | MM - Catalog | sysadmin, analyst |
| MM-Config | MM - Rules | sysadmin, analyst |
| MM-Config | MM - Action | sysadmin, analyst |
| MM-Config | MM - Required Document | sysadmin, analyst |
| MM-Config | MM - Fee | sysadmin, analyst |
| MM-Config | MM - Benefit | sysadmin, analyst |
| MM-Config | MM - Role | sysadmin |
| MM-Config | MM - Role Screen | sysadmin |
| MM-Config | MM - Field | sysadmin, analyst |
| MM-Config | Programme Funding (donor share) | sysadmin, finance_officer |
| Admin | Manage API Key | sysadmin |
| Admin | Form Creator | sysadmin, analyst |
| Admin | Identity Resolvers | sysadmin |
| Admin | Resolver Field Maps | sysadmin |
| Admin | Audit Trail | sysadmin |
| Master Data | MD.01 — MD.95 (all 96 menus) | sysadmin, analyst |

## Test users (one per role)

To smoke-test the wiring without affecting demo data:

| Username | Password (initial, change on first login) | Group | Display name |
|---|---|---|---|
| `test_field` | `Tier1Field!` | role_field_officer | Test Field Officer |
| `test_supervisor` | `Tier1Sup!` | role_district_supervisor | Test District Supervisor |
| `test_finance` | `Tier1Fin!` | role_finance_officer | Test Finance Officer |
| `test_analyst` | `Tier1Ana!` | role_analyst | Test Analyst |
| `test_sysadmin` | `Tier1Sys!` | role_sysadmin | Test System Administrator |

The existing `admin` user (Admin Admin) is also added to `role_sysadmin` so daily admin access is unaffected.

## Out of scope for this taxonomy

- **District-level data scoping.** A field officer in Mohale's Hoek should ideally see only Mohale's Hoek farmers. Today every operator sees every district. This is a row-level security concern, separate from menu-level RBAC, and is deferred to a later task.
- **Maker-checker on individual menus.** Manual budget adjustments need maker-checker — already implemented at the form-binder level, not the menu level.
- **Citizen access.** Citizens use the kiosk page (no auth) or the operator-mediated registration flow. They never hold a Joget user account in this iteration.

## Migration path to Keycloak

Once Keycloak is provisioned (Decision 1 in the customer briefing), each Joget group `role_*` corresponds to a Keycloak group with the same id. Joget's OAuth2/OIDC Directory Manager pulls the user's `groups` claim from the Keycloak ID token and maps each entry to the Joget group of the same name. No menu-level config changes required.
