# Form Quality Runtime

Generic, form-id-driven quality validation engine for Joget DX 8.x. Designed to be the third citizen of the GovStack RegBB triad alongside `doc-submitter` and `processing-server`.

> **Status: Day 1 scaffold.** Lifecycle + admin form definitions in place; the runtime hooks (FormPostProcessor, FormValidator, REST API) land in subsequent days.

## What's done (Day 1)

| Component | Path | Notes |
|---|---|---|
| Maven OSGi scaffold | `pom.xml` | Mirrors `subsidy-eligibility-runtime` style |
| Bundle Activator | `src/main/java/global/govstack/formquality/Activator.java` | Registers quality lifecycle on bundle start |
| Quality lifecycle enums | `status/QualityEntityType.java`, `status/QualityStatus.java` | Implement `joget-status-framework` interfaces |
| Admin form: QA Service | `resources/forms/qa_service.json` | One row per RegBB service |
| Admin form: QA Tab | `resources/forms/qa_tab.json` | UI-grouping per service |
| Admin form: QA Rule | `resources/forms/qa_rule.json` | The rule library — JRE DSL in `ruleScript` |
| Admin form: QA Gate | `resources/forms/qa_gate.json` | Status-transition gates |
| Runtime form: QA Record Status | `resources/forms/qa_record_status.json` | Per-record current quality state |
| Runtime form: QA Issue | `resources/forms/qa_issue.json` | Per-rule issue ledger |
| Lifecycle tests | `test/.../QualityLifecycleTest.java` | 10 tests, all green |

## Quality lifecycle (registered with `joget-status-framework`)

```
                  ┌───────────────┐
                  │ NOT_VALIDATED │  initial — record exists but rules
                  └───────┬───────┘             have not yet run
                          │
                          ▼ on save (post-processor runs all rules)
                ┌─────────┴──────────┐
                ▼                    ▼
       ┌──────────────┐      ┌──────────────────┐
       │   VERIFIED   │      │ ISSUES_DETECTED  │
       └──────┬───────┘      └────────┬─────────┘
              │                       │
              │                       │ on next save
              │                       │ (re-run rules)
              │       ┌───────────────┘
              │       │
              ▼       ▼
       ┌──────────────────────────┐
       │   BLOCKED_FROM_PUBLISH   │  any gated transition (e.g. status →
       │      (not terminal)     │  ACTIVE) attempted while ERROR-severity
       └──────────────────────────┘  issues still active

       Any state can return to NOT_VALIDATED via "force re-evaluate"
       (admin-only) — used after rule library changes.
```

## Storage shape

| Form | Table | Purpose |
|---|---|---|
| `qa_service` | `app_fd_qa_service` | RegBB services governed by quality |
| `qa_tab` | `app_fd_qa_tab` | UI-grouping per service |
| `qa_rule` | `app_fd_qa_rule` | The rule library |
| `qa_gate` | `app_fd_qa_gate` | Status-transition gates |
| `qa_record_status` | `app_fd_qa_record_status` | Runtime quality state per (formId, recordId) |
| `qa_issue` | `app_fd_qa_issue` | Issue ledger (active + resolved) |
| `audit_log` | `app_fd_audit_log` | Shared with all status-framework consumers (gam-plugins etc.) |

## Day 2 — coming next

- `service.RuleRepository` — reads `qa_*` tables via `FormDataDao`
- `service.RuleEvaluator` — calls `joget-rules-api` REST to compile + run rule SQL
- `service.IssueRepository` — writes `qa_issue` rows + updates `qa_record_status` via `StatusFramework.transition()`
- End-to-end smoke test on one rule against a fake form record

## Day 3 — coming after

- `hook.FormQualityPostProcessor` (`DefaultApplicationPlugin` wired as form `postProcessor`) — runs rules on every save
- `hook.QualityValidationProcessTool` — workflow-side trigger for re-validation

## Day 4 — closing

- `api.FormQualityApi` (`@Operation`-annotated `ApiPluginAbstract`)
- Validation panel wiring (CustomHTML + EmbeddedDatalist) onto `spProgramMain`, `farmerRegistrationForm`, `parcelRegistration`
- Seed 30 starter rules across the 3 forms

## Why this design

- **Joget-native.** Hooks via `DefaultApplicationPlugin` wired as form post-processor (proven in `doc-submitter`), REST via `@Operation` (proven in `apibuilder_sample_plugin`), no custom interfaces invented.
- **No new schema written by code.** All tables come from form definitions imported via App Composer — respects gam-plugins's "form-first, code-second" rule.
- **Rules engine reused.** `joget-rules-api` already has the ANTLR DSL, AST, and SQL compiler; we wrap it for quality validation rather than duplicate.
- **Status engine reused.** Same `joget-status-framework` that gam-plugins uses for the bank pipeline. No coupling of bank and government enums.
- **Panel UI reused.** `embedded-datalist` element + JDBC datalist over `qa_issue` filtered by `recordId` — no new Element plugin needed.
