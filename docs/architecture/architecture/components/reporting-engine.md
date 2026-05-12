# Component SAD — Reporting engine

| Field             | Value                                                                                                                                                                                                                                                                                                                                                            |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | Reporting engine                                                                                                                                                                                                                                                                                                                                                  |
| Document title    | Software Architecture Description (component level)                                                                                                                                                                                                                                                                                                              |
| Version           | 0.1 — DRAFT (architecture intent; minimal implementation today)                                                                                                                                                                                                                                                                                                  |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                       |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                               |
| Related           | Solution-level SAD; subsidy-module SAD; IM-module SAD; framework SAD (audit retention).                                                                                                                                                                                                                                                                          |
| What this is      | The component-level SAD for the cross-cutting reporting engine. **Deliberately small.** Reporting in this architecture is native Joget datalists with `JdbcDataListBinder`, plus a small set of conventions about column ownership and a documented path to a future export pipeline. Not a separate "reporting application."                                  |
| What this is not  | A list of every report MAFSN will ever want — that's an operational backlog. A description of an OLAP cube or BI tool — those would be downstream consumers of this component. A description of any specific report's content (those are individual datalist JSON files).                                                                                       |

---

## 1. Introduction and goals

### 1.1 Purpose and scope

The reporting engine is the cross-cutting concern that lets users — operators, programme leads, auditors, MAFSN policy advisors — read the system's data in aggregated, filtered, sorted forms beyond what a single application's row-level UI provides. Examples:

- *"How many subsidy applications are pending operator review by district?"*
- *"What's the distribution by disposition for PRG_2025_001?"*
- *"How many vouchers were issued this quarter and what's the redemption rate?"*
- *"Show all DET_LOWLANDS evaluations that returned ERROR in the last 30 days."*
- *"List farmers in the district registry who have NOT applied for any 2025 subsidy."*

In scope:

- **Native-Joget datalists with `JdbcDataListBinder`** — the primary mechanism. SQL queries against `app_fd_*` tables, rendered as sortable/filterable/paginated tables in the Joget userview.
- **Pivot-style summary lists** for operational dashboards (counts by district, sums by programme, etc.).
- **Cross-module joins** — a report can join `app_fd_subsidy_app_2025` with `app_fd_im_voucher` and `app_fd_farmerbasicinfo` if needed; reporting reads ALL module tables.
- **Audit views** — datalists over `reg_bb_eval_audit` and `im_transaction_log` for forensic/compliance use.
- **A documented export path** — CSV download via Joget's standard datalist Export action; PDF for formal reports through Joget's report plugin (if available); Excel via xlsx export plugin if installed.

Out of scope:

- A standalone BI application (Tableau, PowerBI, Metabase) — those would consume this engine's exports as input, but are not part of the engine.
- Real-time push of metric events to an external observability platform (Prometheus, ELK) — there's no metric stream from RegBB today; reporting reads at-rest data.
- Custom Java reporting code — the entire engine is "datalist JSON files" (configuration), nothing more.

### 1.2 Quality goals (component-level)

| Rank | Quality goal                          | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                                       |
| ---- | ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | **Configuration over code**            | Adding a new report = writing a datalist JSON file + adding a userview menu. Zero Java. Target: time-to-add-a-report ≤ 1 working day for a competent operator-analyst.                                                                                                                                                                                                                            |
| 2    | **UX — feels native**                 | Reports render as standard Joget datalists with the same sort/filter/paginate UX users have everywhere else. No bespoke chart engine, no custom dashboard framework. Joget's stock features (column formatters, multi-row select, export-to-CSV, auto-refresh) work uniformly.                                                                                                                       |
| 3    | **Sustainability — low maintenance**   | A report's lifecycle is: SQL written → committed → datalist JSON pushed → rendered. No build, no JAR upload, no Joget restart. New columns added by editing the SQL. Schema changes in a source table propagate to the report on next run (no schema drift between report metadata and source data).                                                                                                |
| 4    | **Read-only and side-effect-free**     | Reporting NEVER writes to any table. SELECT only. The reporting engine does not register storeBinders, does not run scheduled actions that mutate data, does not have audit-write side effects of its own. Reports can be safely re-run, cached, exported without consequence.                                                                                                                  |
| 5    | **Performance — operator dashboards**  | An operator's daily-driver dashboard (open, see counts) renders in ≤ 1 second P95. Detailed reports (cross-module joins, ranges of months) render in ≤ 5 seconds P95. Fixes for slowness: indexes on the source tables (the audit table's `(c_application_id, datecreated DESC)` index documented in CLAUDE.md is an example).                                                                  |

### 1.3 Stakeholders

| Stakeholder                         | Concerns                                                                                                                                                              |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MAFSN policy lead**               | Programme uptake by district / farmer category / season; comparative outcomes across programmes; year-over-year trends.                                                |
| **MAFSN operations manager**        | Daily inbox health (pending count, throughput); decision turnaround time; bottleneck districts.                                                                        |
| **District coordinator**            | Their district's stock; their district's vouchers issued/redeemed; their district's pending applications.                                                              |
| **Auditor / inspector**             | Forensic queries against the audit tables; compliance reports; any anomaly investigation.                                                                                |
| **External consumers (future)**     | A future read-only API for data export to other ministries (anonymised) is named here for awareness; not built today.                                                  |

---

## 2. Architecture constraints

Inherits all solution-level constraints. Engine-specific:

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                              |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R-C1  | **No Java in reports.** Every report is a datalist JSON file with `JdbcDataListBinder`. No custom datalist binders, no custom column formatters beyond what Joget enterprise plugins provide.                                                                                                                                                                                              |
| R-C2  | **No write operations.** Reports are SELECT-only. The reporting engine never inserts, updates, deletes, or mutates any state. Even "increment a hit counter" is forbidden.                                                                                                                                                                                                                |
| R-C3  | **No cross-domain rule logic.** The framework's `mm_determinant` is for business rules (eligibility, voucher constraints). Reporting never embeds rule-grammar evaluation. If a report needs "applicants who passed eligibility" the SQL queries `c_eligibility_outcome::jsonb->>'disposition' = 'eligibility_passed'` directly — the eligibility evaluation has already happened upstream. |
| R-C4  | **`SELECT` is OK on Joget-managed tables, per the hard-rule's read carve-out.** The engine reads `app_fd_*` tables freely. No raw SELECT on Joget metadata tables either (`app_form`, `app_userview`, etc.) — those are Joget's internals; reading their JSON column shape risks coupling.                                                                                                |
| R-C5  | **PostgreSQL functions only.** The engine uses Postgres-specific JSON operators (`->`, `->>`, `::jsonb`) freely. A future cross-DBMS portability concern would replace these with portable patterns; for now the reporting layer is PG-coupled (the rest of the architecture is not).                                                                                                       |

---

## 3. Context and scope

### 3.1 Reporting in the system

```
   ┌──────────────────────────────────────────────────────────────┐
   │   Operator UI / Policy lead UI / Auditor UI                  │
   │   (Joget userview pages with datalists)                      │
   └────────────────────┬─────────────────────────────────────────┘
                        │ navigates to a report menu
                        ▼
   ┌──────────────────────────────────────────────────────────────┐
   │   Reporting engine — a directory of datalist JSON files      │
   │                                                              │
   │   app/datalists/                                                │
   │     list_subsidyApplicationOperator2025.json   (operator    │
   │                                                  inbox)      │
   │     list_reg_bb_eval_audit.json                (audit)      │
   │     report_subsidy_uptake_by_district.json     (pivot)      │
   │     report_disposition_distribution.json       (chart-like) │
   │     report_voucher_redemption_rate.json        (IM)         │
   │     report_unapplied_farmers.json              (cross-mod)  │
   │     ...                                                      │
   │                                                              │
   │   All using JdbcDataListBinder + standard formatters.        │
   └────────────────────┬─────────────────────────────────────────┘
                        │ SELECTs
                        ▼
   ┌──────────────────────────────────────────────────────────────┐
   │   PostgreSQL                                                 │
   │     app_fd_subsidy_app_2025  app_fd_reg_bb_eval_audit        │
   │     app_fd_farmerbasicinfo  app_fd_parcelregistration        │
   │     app_fd_im_voucher  app_fd_im_distribution  ...           │
   │     app_fd_mm_* (configuration tables — usually for FK label │
   │       resolution: c_code → c_name)                           │
   └──────────────────────────────────────────────────────────────┘
```

The reporting engine is **the directory `app/datalists/`**, plus the pattern of using `JdbcDataListBinder` for cross-table queries. There's no separate reporting bundle, no separate web frontend, no separate database.

### 3.2 What the engine reads

| Source                                               | Owner module        | Read pattern                                                                                                                  |
| ---------------------------------------------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `app_fd_subsidy_app_2025`                             | Subsidy             | Citizen application records: applicant identity, programme, eligibility outcome JSON, status, decision metadata.               |
| `app_fd_reg_bb_eval_audit`                            | Framework (subsidy emits) | Forensic timeline: every rule evaluation, decision, dispatch.                                                            |
| `app_fd_farmerbasicinfo`                              | Farmer registry      | Citizen demographics, household composition (for cohort analysis).                                                          |
| `app_fd_parcelregistration`                           | Parcel registry      | Parcel geometry, tenure, agro-zone (for geographic analysis).                                                                |
| `app_fd_im_*`                                          | IM (Phase 3)        | Vouchers, redemptions, inventory, distribution, transaction log.                                                            |
| `app_fd_mm_registration`                              | RegBB / shared       | Programme metadata for FK label resolution (programme code → programme name).                                              |
| `app_fd_mm_determinant`                               | RegBB / shared       | Determinant code → human-readable name.                                                                                      |
| `app_fd_md*`                                          | Master data          | Lookup labels (district code → district name, etc.).                                                                          |

### 3.3 No cross-bundle dependencies

The reporting engine has zero Java code. No OSGi bundle is associated with it. It runs entirely on Joget's stock `JdbcDataListBinder` plus stock `OptionsValueFormatter` and `DateFormatter`. **A consequence: deploying a new report does not require a JAR upload.**

---

## 4. Solution strategy

### 4.1 Datalists are the report engine

Joget's enterprise `JdbcDataListBinder` lets a datalist run an arbitrary SELECT query as its data source. The query produces rows; the datalist renders columns. Pagination, sorting, filtering all work on top of the SQL. With `OptionsValueFormatter` you can show `c_code` as `c_name`. With `DateFormatter` you can format timestamps. For pivot-style reports you write a `GROUP BY` query.

That covers ~90% of operational reporting. The remaining 10% (financial reconciliation, multi-quarter trend analysis, regulatory PDF reports) are out of v1 scope.

### 4.2 Standard report shapes

| Shape                  | Pattern                                                                                                   | Example                                                                                                |
| ---------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Inbox-style list**   | `SELECT detail-columns FROM source_table WHERE filter ORDER BY datemodified DESC NULLS LAST`              | Operator's pending applications list                                                                  |
| **Detail with FK label**| As above, joined to `mm_*` or `md*` tables to resolve codes to names                                       | Audit list with programme name (resolved from `mm_registration.code → name`)                          |
| **Pivot summary**      | `SELECT group_col, count(*), sum(numeric_col) FROM source GROUP BY group_col`                               | "Applications by district" or "Vouchers by status × month"                                            |
| **Cross-module join**   | Multi-table SELECT with FK joins                                                                            | "Farmers in registry who have NOT applied for any 2025 subsidy" (LEFT JOIN + NULL filter)            |
| **Forensic audit slice**| Date-bounded SELECT on the audit table with filters on outcome / determinant / applicant                  | "All ERROR outcomes in the last 30 days for PRG_2025_001"                                              |
| **Health metrics**      | Single-row pivot returning counts                                                                           | Dashboard tile showing "5 pending review, 12 approved this week, 3 send-back"                       |

### 4.3 Naming convention

| Prefix       | Use                                                                                                                                                                                |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `list_*`     | Operator inboxes (e.g. `list_subsidyApplicationOperator2025`). One row per source-table record. Used for daily-driver UIs.                                                            |
| `report_*`   | Aggregated reports (e.g. `report_subsidy_uptake_by_district`). One row per group, with counts/sums. Used for management dashboards.                                                   |
| `audit_*`    | Audit / forensic views (e.g. `audit_reg_bb_eval_failures`). Filtered slices over `reg_bb_eval_audit` and `im_transaction_log`.                                                       |
| `budget_*`   | Budget &amp; commitment funnel views (e.g. `budget_envelope_dashboard`, `budget_funnel_ledger`, `budget_reconciliation`). Reads `budget_envelope`, `budget_event`, `budget_projection`. |
| `sla_*`      | SLA monitoring slices (e.g. `sla_decision_pending`, `sla_decision_breached`). Reads operational tables joined to `mm_determinant` rules of `scope=sla_decision`.                 |

### 4.3-bis Budget &amp; SLA report families

Two new report families integrate with the Budget Engine and the rule-driven SLA discipline:

**Budget report family** (`budget_*`). Reads from `budget_envelope`, `budget_event`, and the materialised view `budget_projection`. Examples:

- `budget_envelope_dashboard` — per programme, per fiscal year: progress bar across allocated → reserved → pre-committed → committed → expensed, with available balance and utilisation %.
- `budget_funnel_ledger` — chronological feed of every `budget_event` for one envelope. Filters by event type, date range, source module. The forensic surface.
- `budget_event_per_application` — for one applicationId, the full chain (RESERVATION → PRE_COMMITMENT → COMMITMENT events when vouchers issue → EXPENSE events when redeemed). Used by the auditor "where did this M50,000 go" workflow.
- `budget_reconciliation_check` — quarterly reconciliation: compares `sum(committed events for active vouchers)` to `sum(active voucher values)`; surfaces mismatches.
- `budget_burn_rate_trend` — time-series chart-shaped table (date × cumulative_expensed) per programme. For policy lead's "are we on track" question.

**SLA report family** (`sla_*`). Driven by `mm_determinant` rules of `scope=sla_decision`. Each pending application is evaluated against the rule for its programme; the rule's outcome (TRUE = SLA breach) is the column.

- `sla_decision_pending` — pending applications grouped by `(district, days_since_submission)` with cells coloured by SLA proximity. Per programme.
- `sla_decision_breached` — applications past SLA, oldest first, with operator-of-record column.
- `sla_decision_turnaround` — average decision turnaround time per programme per month, trending.
- `sla_operator_throughput` — decisions per operator per week.

Optional: `mm_action.kind=sla_alert` for scheduled alerts when applications cross the rule's threshold; the action triggers a notification workflow. The reporting layer consumes the same `sla_decision` rules; the alert layer uses them as triggers.

### 4.4 Cross-module reporting boundary

The reporting engine reads cross-module freely — a report joining subsidy + IM + farmer-registry tables is fine. But it does NOT trigger cross-module side effects, run business rules, or invalidate caches. The reading is one-way and side-effect-free.

If a "report" needs to trigger an action (e.g. "list pending vouchers and send a reminder to each farmer"), that's NOT a report — it's a scheduled task with an `mm_action` dispatch. Reports *show* data; they don't *do* anything.

---

## 5. Building block view

### 5.1 What the engine actually consists of

```
app/datalists/                   ← every report lives here
  list_subsidyApplicationOperator2025.json
  list_reg_bb_eval_audit.json
  list_imVoucher.json                         (Phase 3)
  report_subsidy_uptake_by_district.json      (Phase 1 close-out)
  report_disposition_distribution.json        (Phase 1 close-out)
  report_voucher_redemption_rate.json         (Phase 3)
  report_unapplied_farmers.json               (Phase 1 close-out)
  audit_reg_bb_eval_failures.json             (Phase 1 close-out)
  audit_workflow_dispatch_failures.json       (Phase 1 close-out)
  ...

app/userviews/v.json             ← the userview menu refers to each datalist
                                via DataListMenu / EmbeddedDatalist menu elements

tooling/push_datalist.py     ← deploys datalist JSONs via form-creator-api
```

### 5.2 Anatomy of a JdbcDataListBinder report (sample)

`report_subsidy_uptake_by_district.json` (illustrative — not yet committed):

```json
{
  "id": "report_subsidy_uptake_by_district",
  "name": "Subsidy uptake by district",
  "binder": {
    "className": "org.joget.plugin.enterprise.JdbcDataListBinder",
    "properties": {
      "datasource": "default",
      "sql": "SELECT d.c_name AS district, p.c_name AS programme, count(*) AS apps_count, sum(CASE WHEN a.c_status = 'approved' THEN 1 ELSE 0 END) AS approved_count, sum(CASE WHEN a.c_status = 'rejected' THEN 1 ELSE 0 END) AS rejected_count FROM app_fd_subsidy_app_2025 a LEFT JOIN app_fd_md03district d ON d.c_code = a.c_district LEFT JOIN app_fd_mm_registration p ON p.c_code = a.c_applied_programme GROUP BY d.c_name, p.c_name ORDER BY d.c_name, p.c_name",
      "countSql": "SELECT count(*) FROM (SELECT 1 FROM app_fd_subsidy_app_2025 GROUP BY c_district, c_applied_programme) sub",
      "primaryKey": "district"
    }
  },
  "columns": [
    { "name": "district",        "label": "District" },
    { "name": "programme",       "label": "Programme" },
    { "name": "apps_count",      "label": "Applications" },
    { "name": "approved_count",  "label": "Approved" },
    { "name": "rejected_count",  "label": "Rejected" }
  ],
  "orderBy": "district",
  "order": "ASC",
  "pageSize": 0,
  "pageSizeSelectorOptions": "20,50,100"
}
```

The pattern: SQL produces the data, columns describe the rendering, formatters decorate (none needed in this example because the labels are already resolved by the JOIN). Sortable, filterable, paginated by Joget's stock datalist UI.

### 5.3 Use of formatters

Stock Joget enterprise formatters that the engine relies on:

| Formatter                       | Use                                                                                                                                                            |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OptionsValueFormatter`         | Resolve a code column to its label (e.g. `applied_programme` code → `mm_registration.name`). Removes the JOIN burden on the SQL.                              |
| `DateFormatter`                 | Display timestamps in a consistent format (e.g. `yyyy-MM-dd HH:mm:ss`).                                                                                       |
| `CSSClassFormatter` (custom)    | (If available) attach a CSS class to a cell based on its value — e.g. green for approved, red for rejected. Today this is done via post-render JS.            |
| `LinkFormatter`                 | Make a column a clickable link to another userview page (e.g. application-id column links to the operator review screen).                                     |

### 5.4 Deployment of a report

```bash
# Edit or create the JSON file:
vim app/datalists/report_disposition_distribution.json

# Push:
python tooling/push_datalist.py app/datalists/report_disposition_distribution.json

# Add a userview menu entry:
vim app/userviews/v.json   # add a DataListMenu / EmbeddedDatalist entry
python tooling/push_userview.py app/userviews/v.json

# Done. Refresh browser → new report visible.
```

---

## 6. Runtime view

### 6.1 Operator opens the inbox

1. Operator clicks **MOA Office → 2025 Subsidy Application — Operator Review**.
2. Joget userview routing → renders the page containing `list_subsidyApplicationOperator2025`.
3. Datalist loads: `JdbcDataListBinder` runs the configured SQL with current sort/filter/page params.
4. Postgres returns rows; Joget renders the table with formatters applied.
5. Operator sees the inbox.

### 6.2 Policy lead opens the uptake dashboard

1. Policy lead clicks **Reports → Subsidy uptake by district**.
2. Datalist `report_subsidy_uptake_by_district` runs.
3. Postgres returns the GROUP BY result; Joget renders.
4. Policy lead can sort by `apps_count` to see which districts have the most demand, filter to a single programme, export to CSV for further analysis.

### 6.3 Auditor investigates an anomaly

1. Auditor opens the audit list `list_reg_bb_eval_audit`.
2. Filters by determinant_code = `DET_DROUGHT_DECLARED` and outcome = `ERROR`.
3. Sees three rows from last week with error_cause = `parse_error:unknown ref scope: parcel`.
4. Cross-references with the audit list's applicant_name + national_id columns to identify which applicants were affected.
5. Files a remediation request: "rules using `parcel.*` were unmigrated; these three applications need re-evaluation."

(This is a real scenario from this project's history — task #76.)

### 6.4 Export to CSV

Joget's stock datalist Export action produces a CSV of the current view (with current filters/sorts applied). The auditor exports the filtered audit slice, attaches it to the remediation request, no engineering involvement.

---

## 7. Deployment view

The reporting engine doesn't have its own deployable artifact. It's the union of datalist JSON files committed in the repo and pushed via `form-creator-api`. The "deploy" step for a new report is `python tooling/push_datalist.py app/datalists/<file>.json` plus, if needed, a userview menu entry.

A future `_reports/` subdirectory parallel to `app/datalists/` could organise larger, more formal reports, but today they all live in `app/datalists/` with a `report_*` or `audit_*` prefix.

---

## 8. Cross-cutting concepts

### 8.1 Naming + ownership convention

Each datalist JSON's filename indicates its purpose:

- `list_*` — operator daily-driver inbox.
- `report_*` — aggregated management/policy report.
- `audit_*` — forensic / compliance slice over the audit tables.

Each datalist's `name` field carries a human label; its `id` is what userview menus reference.

The "owning module" of each report is implicit in its source tables. A report joining `app_fd_subsidy_app_2025` and `app_fd_im_voucher` is a cross-module report; it should be reviewed by the owners of both modules before merging.

### 8.2 Schema evolution and report breakage

If a source column is renamed (e.g. `c_decision` → `c_operator_decision`), every report's SQL referencing it breaks. Mitigation:

- Source columns are added freely (no breakage).
- Source columns are renamed via a migration: rename column, update all reports referencing it, deploy together.
- Source columns are deprecated (kept but marked deprecated) before removal.

The reporting layer doesn't have its own migration story; it follows whatever module owns the source table.

### 8.3 Performance and indexes

Datalist queries that scan large tables (audit at >100k rows) need indexes. Documented practice:

- The audit table grows; the documented index `(c_application_id, datecreated DESC)` supports the per-applicant audit-list filter.
- Operator inbox SQL (over `app_fd_subsidy_app_2025`) is small enough that no index is needed during Phase 1.
- IM transaction log (Phase 3) will likely need indexes on `(c_voucher_id)` and `(c_distribution_point, datecreated)`.

Indexes are owned by ops as a query-tuning concern, separate from the datalist JSON. Add them in a maintenance window, not via the reporting engine.

### 8.4 Export formats

| Format | Mechanism                                            | Notes                                                                                                              |
| ------ | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| CSV    | Joget's stock datalist Export action                  | Default; works for every datalist.                                                                                 |
| Excel  | Joget enterprise xlsx-export plugin (if installed)    | Same UX; more useful when there are formatted columns or formulas.                                                 |
| PDF    | (Future) Joget report plugin                          | Out of v1 scope. Today, formal PDF reports are produced externally from CSV.                                       |
| API    | (Future) Read-only API endpoint over reports          | Out of v1 scope. Could be done via a `RegBbReportApi` plugin similar to `RegBbEvalApi`, exposing parameterised SQL. |

### 8.5 Caching and freshness

Reports run their SQL on each render — there's no view materialisation today. For most datalists this is fine; for heavy aggregations on the audit table (>100k rows), consider PostgreSQL materialised views refreshed nightly:

```sql
CREATE MATERIALIZED VIEW mv_disposition_distribution AS
SELECT ... GROUP BY ...;
REFRESH MATERIALIZED VIEW mv_disposition_distribution;
```

Then the report's `JdbcDataListBinder` SELECTs from the materialised view. Out of Phase 1 scope; documented for future use.

### 8.6 Personal data + reporting

Reports can expose PII (national_id, full_name, contact_phone). Access control is via Joget user/role membership on the userview page — only authorised roles see the menu. Documented practice:

- Operator-role reports may show PII (operators are authorised to see applicant details).
- Policy-lead-role reports show district-level aggregates only; no individual-level PII.
- External consumer reports (future) anonymise national_id (hash with salt) and aggregate to district level.

---

## 9. Architecture decisions

| Decision                     | Title                                                                                       | Status                                                                                                          |
| ---------------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **(forthcoming)** ADR-019    | Reports as datalist JSON only — no Java reporting code, no separate reporting bundle         | Pending. Codifies the "configuration over code" choice and prevents drift to invented reporting infrastructure.  |
| **(implicit)**               | Reports never write — read-only, side-effect-free                                            | Implicit; would be elevated to ADR if anyone proposed a write-path report (e.g. "report and send notifications"). |
| **(implicit)**               | PostgreSQL-specific JSON operators are acceptable in report SQL                              | Implicit; portability concern flagged in §11.                                                                   |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                         | Source                | Stimulus                                                              | Artifact                       | Environment    | Response                                                                                                                                              | Measure                                                |
| ---------------------------- | --------------------- | --------------------------------------------------------------------- | ------------------------------ | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Configuration over code       | Operator-analyst      | Adds a new "applications by week" report                              | `app/datalists/report_*.json`     | Phase 2        | Edits a JSON file, runs `push_datalist.py`, adds userview menu — report is live                                                                       | ≤ 1 working day                                        |
| UX feels native               | Operator              | Sorts/filters/paginates a 10k-row report                              | Datalist UI                    | Production     | Joget stock UX with no surprises                                                                                                                      | Zero "where's the X feature" complaints                |
| Side-effect-free              | Auditor                | Verifies that running reports doesn't change any data                  | Reporting engine               | Production     | Reading reports produces no INSERT, UPDATE, DELETE, no audit row, no log line beyond a SELECT                                                       | Audit-log diff between "before run" and "after run" is empty |
| Performance — operator inbox  | Operator              | Opens the inbox dashboard at 9am with 500 rows in subsidy_app_2025      | `list_subsidyApplicationOperator2025` | Production    | First page renders in under 1 second                                                                                                                  | < 1s P95                                               |
| Performance — heavy aggregation| Policy lead          | Opens "subsidy uptake by district × programme" with 50k applications    | `report_subsidy_uptake_*`      | Production     | Renders in under 5 seconds                                                                                                                            | < 5s P95                                               |

### 10.2 ISO/IEC 25010 mapping

| Goal                       | 25010 characteristic                                |
| -------------------------- | --------------------------------------------------- |
| Configuration over code     | Maintainability — Modifiability                     |
| UX feels native             | Compatibility — Co-existence with platform UX       |
| Side-effect-free            | Reliability — Faultlessness                          |
| Performance                 | Performance efficiency — Time behaviour             |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                              | Severity | Mitigation                                                                                                                                                                                  |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RE-R1 | **PostgreSQL-specific SQL.** JSON operators (`->`, `->>`, `::jsonb`) are PG-specific. A future cross-DBMS portability ask would force rewrites of every JSON-touching report.                                                       | Medium   | Acceptable today (Joget on PG is the platform). Flagged for future review.                                                                                                                  |
| RE-R2 | **Sparse implementation today.** Phase 1 has only `list_subsidyApplicationOperator2025`, `list_reg_bb_eval_audit`, and the 13 admin CrudMenu lists. Real management/policy reports (uptake, disposition distribution, etc.) are Phase 1 close-out. | Medium   | Phase 1 close-out scope. ~6–8 reports to author for the four 2025 programmes.                                                                                                                |
| RE-R3 | **No materialised views or query caching.** Heavy aggregations re-run on every render. At current data sizes (low-thousands), fine. Will become a problem at 100k+ application rows.                                                  | Low      | Documented mitigation: PostgreSQL materialised views, refresh in maintenance window.                                                                                                       |
| RE-R4 | **No formal report PDF generation.** External regulatory reports (annual subsidy report to MAFSN minister, parliamentary reports) are produced from CSV exports today. Manual, error-prone.                                          | Low      | Out of Phase 1 scope. A future Joget report plugin or a separate Python-based PDF generator that consumes datalist JSON exports.                                                          |
| RE-R5 | **No dashboards / BI layer.** Joget datalists are tabular. Charts, KPI tiles, time-series graphs are not part of the engine.                                                                                                          | Low      | Operators currently get tabular reports. A future BI integration (Metabase, Grafana — both can connect to Postgres directly) is the natural next layer; not in Phase 1.                  |
| RE-R6 | **Cross-module report ownership is fuzzy.** A report joining subsidy + IM + farmer-registry has implicit dependencies on all three modules' schemas. Schema changes in any source can break it.                                      | Low      | Convention: cross-module reports are reviewed by owners of all touched modules before merge. Document the source tables in the JSON's `name` field as a hint.                           |

---

## 12. Glossary (engine-specific)

| Term                       | Definition                                                                                                                                                                                                       |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Reporting engine**       | The collection of datalist JSON files + `JdbcDataListBinder` pattern + naming conventions described in this SAD. Not a separate application.                                                                      |
| **Datalist**               | Joget's standard list-rendering construct. Configured by JSON: binder (data source), columns (rendering), filters (UI controls), pagination, sorting.                                                            |
| **JdbcDataListBinder**     | A Joget enterprise plugin that sources datalist data from a SQL query rather than a form table.                                                                                                                  |
| **Pivot summary**          | A report shape that returns one row per group with counts/sums. Built with SQL `GROUP BY`.                                                                                                                       |
| **Cross-module report**    | A report joining tables from more than one module (subsidy + IM + farmer-registry). Allowed; reviewed by all source-module owners.                                                                                |
| **Audit slice**            | A filtered view over `reg_bb_eval_audit` or `im_transaction_log` for forensic / compliance use. Always read-only.                                                                                                |
| **Materialised view**      | A precomputed, periodically-refreshed view in PostgreSQL. Documented as a future option for heavy reports; not used today.                                                                                       |
