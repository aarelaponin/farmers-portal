# ADR-031 — Unified rule engine: fold formQuality into mm_determinant

| | |
|---|---|
| Status | **Proposed** (2026-05-07) |
| Date | 2026-05-07 |
| Deciders | Farmers Portal architecture team |
| Consulted | MAFSN ICT (advisory) |
| Informed | Engineering, QA Lead |
| Supersedes | none — extends ADR-001 (rule grammar canon), ADR-003 (rule storage shape), ADR-006 (outcome persistence) |
| Related | ADR-001 (rule grammar canon), ADR-003 (rule storage shape), ADR-005 (Phase 1 save hook), ADR-006 (outcome persistence schema), ADR-027 (initial-status assignment), `_design/architecture/components/rules-determinants-architecture.md`, `gs-plugins/form-quality-runtime/`, `_design/test_strategy.md` (companion), the form-quality JWA (`APP_formQuality-2-20260507083841.jwa`), the live farmersPortal JWA (`APP_farmersPortal-1-20260507084455.jwa`) |

---

## 1. Context

### 1.1 What we have today — two parallel rule engines

The system currently runs **two rule engines** that are conceptually almost identical:

**Engine A — `mm_determinant` (eligibility).** Stored in farmersPortal's `app_fd_mm_determinant`. Evaluated by the `RoutingEvaluator` / `SqlPathEvaluator` / `FastPathEvaluator` chain in the `reg-bb-engine` plugin. Fires on subsidy application **submission**. Produces a per-application *disposition* (`eligibility_passed` / `eligibility_failed_mandatory` / `eligibility_pending_review` / `indeterminate`) plus a per-rule audit row in `app_fd_reg_bb_eval_audit`. Drives the application's `c_status` via the initial-status rule (ADR-027).

**Engine B — `qa_rule` (form quality).** Stored in formQuality's `app_fd_qa_rule`. Evaluated by the `form-quality-runtime` plugin's `FormQualityPostProcessor`. Fires on **every save** of any wired form. Produces per-rule *issue* rows in `app_fd_qa_issue`, aggregated into `app_fd_qa_record_status`. Drives gate behaviour via `app_fd_qa_gate` — blocks status transitions when ERROR-severity rules fail.

Both engines:

- Evaluate SQL-shaped probes against form-data tables.
- Substitute a record-id token into the SQL at runtime.
- Persist outcomes to a structured store.
- Surface results to operators via UI components on the relevant form (eligibility pills next to each rule on the application form; QualityBanner at the top of the registration / parcel / programme / application forms).

The differences — *when* they fire, *what aggregation shape* they produce, *who configures them* — are properties of individual rules, not architectural properties that justify separate engines.

### 1.2 What surfaced this question

The Form Quality Admin manual review (May 2026, prompted by integration verification against `APP_farmersPortal-1-20260507084455.jwa`) made the architectural cost of the split visible. Concretely:

* The `form-quality-runtime-8.1-SNAPSHOT.jar` plugin is **bundled inside the farmersPortal JWA**. It is not independently deployable.
* The 21 rules in `qa_rule` reference farmersPortal's tables (`app_fd_sp_program_identity`, etc.) directly. There is no abstraction layer between the rules and the target schema.
* Four farmersPortal forms render `QualityBannerElement`; eleven forms wire `FormQualityPostProcessor`. The integration is *tight*.
* The "separate app" boundary consists of two things: a different `appId` and a separate userview. Nothing else.

In other words, every architectural surface — code, deployment, data, UX — is already coupled to farmersPortal. The "separation" is structural overhead that buys nothing in 2026.

### 1.3 Why the original split was made (and why those reasons no longer hold)

Three justifications can be reconstructed from the codebase and decision history:

1. **"Audience separation — QA Lead is a different role from operators."** True, but role-based menu visibility in a single userview achieves this without a second app. Splitting on audience usually means splitting users; here it splits machinery.

2. **"Reusability — formQuality could apply to other Joget apps."** Theoretically true. In practice it has exactly one consumer (farmersPortal). The reusability hasn't paid for the architectural cost, and the rules-pointing-directly-at-farmersPortal-tables design defeats the abstraction anyway.

3. **"Independent versioning — rules can evolve without rebuilding farmersPortal."** Half-true. Rule rows in `qa_rule` evolve independently. But adding a *new service* requires wiring a `FormQualityPostProcessor` on a farmersPortal form, which is a farmersPortal release. So the "independence" is only on the *content* layer, not the *plumbing* layer.

The asymmetric history is telling: the **eligibility evaluator** (`mm_determinant`), built later with the formQuality split available as a precedent, was *deliberately* kept inside farmersPortal as part of the meta-model. Same conceptual shape, opposite architectural decision. The asymmetry is chronological, not principled.

### 1.4 Why now

The system is **pre-production**. No live MAFSN data flows through either engine yet; the only data is the dev fixture and the customer migration package (which has not yet been imported). The migration cost compounds the moment production traffic starts. Pre-production is therefore the cheapest moment to consolidate. Once MAFSN goes live (planned post-UAT, summer 2026), the cost rises substantially.

---

## 2. Decision

### 2.1 Unify into a single rule engine, scoped per rule

Replace the two engines with **one unified rule engine** that dispatches by per-rule attributes. The rule storage table becomes `mm_rule` (renamed from `mm_determinant`, to reflect the broader scope). Each rule row carries:

| Column | Values | Meaning |
|---|---|---|
| `code` | `<scope>.<tab>.<short_description>` lower-snake-case | Rule identity (unique) |
| `scope` | `eligibility` / `quality` / `bot_pull` | What kind of rule this is |
| `severity` | `mandatory` / `score` / `error` / `warning` | Decisiveness |
| `triggerOn` | `submit` / `save` / `manual` | When the engine evaluates this rule |
| `aggregation` | `disposition` / `issue_list` | How the engine reports the outcome |
| `expression` | DSL or SQL | The probe |
| `targetFormId` | Form id | Which form the rule applies to |
| `targetTable` | Underlying table (derived; cached) | What the SQL queries |
| `affectedFields` | Comma-separated form-field IDs | UI flagging |
| `failMessage` | Plain text | What operators see |
| `serviceId` | Optional | Grouping (replaces `qa_service`) |
| `tabCode` | Optional | Grouping (replaces `qa_tab`) |
| `registrationCode` | Optional | Programme link (eligibility only) |

Existing eligibility rules migrate as `scope=eligibility, severity=mandatory|score, triggerOn=submit, aggregation=disposition`. Existing quality rules migrate as `scope=quality, severity=error|warning, triggerOn=save, aggregation=issue_list`. The 21 `qa_rule` rows and the ~14+ `mm_determinant` rows all keep their existing SQL probes — that is the asset, the rule logic itself.

### 2.2 Unified evaluator

The existing eligibility evaluator chain (`RoutingEvaluator` / `SqlPathEvaluator` / `FastPathEvaluator` in `reg-bb-engine`) absorbs the form-quality runtime's evaluation logic. The chain becomes:

1. **Trigger filter.** Given a `triggerEvent ∈ {submit, save, manual}`, select rules where `rule.triggerOn = triggerEvent`.
2. **Scope filter.** If the trigger came from a specific form (e.g. save on `spProgramMain`), narrow to rules where `targetFormId` matches the form's chain (parent or sub-form).
3. **Per-rule evaluation.** Same SQL/DSL probe execution as today.
4. **Outcome write.** Write to a unified `rule_outcome` store (extends today's `reg_bb_eval_audit`), with columns for severity, aggregation, ruleCode, recordId, formId, actor, timestamp.
5. **Aggregation.** Per-record:
   - `aggregation=disposition` rules → compute disposition (existing eligibility logic).
   - `aggregation=issue_list` rules → maintain open-issue list (existing quality logic).
6. **Gate enforcement.** If the trigger was a status change, consult gates (now stored as `mm_rule_gate` rows referencing rules by scope/severity); block save if any blocking rules fail.

### 2.3 Unified outcome store

`reg_bb_eval_audit` extends to become `rule_outcome` (rename for clarity). Columns added: `severity`, `aggregation`, `is_open` (for quality issues that haven't yet been resolved). Eligibility audit rows are append-only and `is_open=null`; quality issue rows are mutable (`is_open=true|false`). The QualityBanner reads `rule_outcome WHERE aggregation='issue_list' AND is_open=true GROUP BY recordId`. The eligibility-pills surface reads `rule_outcome WHERE aggregation='disposition' AND applicationId=? ORDER BY datecreated DESC LIMIT 1 per ruleCode`. Both surfaces are now thin views over the same underlying data.

### 2.4 Unified authoring UI

A single **Rules** category in the farmersPortal userview, gated to QA Lead / Sysadmin / programme designer roles. CRUD list with `scope` filter ("Eligibility / Quality / Bot pull"). Existing eligibility-rule and quality-rule CRUDs become saved-filter views over the same underlying table.

### 2.5 What goes away

- The `formQuality` Joget app entirely. Its userview, its `qa_*` form definitions, its `app_app` row.
- The `qa_service`, `qa_tab`, `qa_rule`, `qa_gate`, `qa_issue`, `qa_record_status` tables (data migrated to `mm_*` tables).
- The `form-quality-runtime` plugin as a separate JAR. Its hooks (PostProcessor, BannerElement) move into `reg-bb-engine` as additional widgets in the unified evaluator.
- Two authoring UIs become one. Two outcome stores become one. Two engines become one.

### 2.6 What stays

- Every existing rule's SQL probe — the rule logic itself, which is the actual asset.
- The eligibility-pills rendering on the application form — operators see no UX change.
- The QualityBanner on the four primary forms — operators see no UX change.
- The L4 20-scenario parity test — becomes the regression test for the eligibility scope of the unified engine.
- The 4 gates — migrate to `mm_rule_gate` rows; gate behaviour is unchanged.

---

## 3. Migration plan

Five slices, each shippable independently. Each slice has acceptance criteria; the next slice does not start until the prior slice's criteria are met. The L4 parity test runs at the end of every slice — if it regresses, the slice is rolled back.

### Slice A — Schema unification (1 day)

Add the new columns to `mm_determinant`:

* `severity` (extend the current `mandatory`/`score` set with `error`/`warning`).
* `triggerOn` (default `submit` for existing rows).
* `aggregation` (default `disposition` for existing rows).
* `affectedFields` (default null for existing rows).

Rename the table to `mm_rule` via a Joget form re-author (HARD-RULE compliant: edit the form definition, let Joget DDL the schema). The existing data is unchanged in semantics.

**Acceptance**: L4 passes; existing eligibility CRUD UI renders unchanged; existing tests green.

### Slice B — Data migration: qa_rule → mm_rule (1 day)

Script-driven row insert: each `app_fd_qa_rule` row inserts as an `app_fd_mm_rule` row with `scope=quality`, `severity=error|warning`, `triggerOn=save`, `aggregation=issue_list`. The SQL probe column copies byte-for-byte. Same for `qa_service` → optional `mm_service`-style rows, `qa_tab` → `mm_tab`-style rows, `qa_gate` → `mm_rule_gate`.

The `qa_rule` rows are NOT deleted yet. The system runs in a **dual-write window** — the form-quality-runtime continues to read from `qa_rule`, while the unified evaluator can also see the new `mm_rule` rows. This is the safe rollback point.

**Acceptance**: dual content equivalence — for every test fixture, both stores produce identical outcome sets.

### Slice C — Switch read paths (3 days, with soak)

The form-quality-runtime plugin's `FormQualityPostProcessor` is patched to read rules from `mm_rule WHERE scope='quality'` instead of `qa_rule`. The QualityBannerElement is patched to read from `rule_outcome` instead of `qa_record_status`. Eligibility evaluator continues to read its rules; nothing in the eligibility path changes.

For one week: monitor outcome equivalence between the two engines on every save, log any divergence. Run L4, run the new L5 quality regression (see test strategy doc), run end-to-end smoke. If outcomes match for 7 days, declare success and move on.

**Acceptance**: zero divergence over 7 days of normal operation; L4 + L5 pass.

### Slice D — Unified authoring UI (2 days)

Author a single CRUD list under farmersPortal's userview: `Rules` menu with a `scope` filter dropdown. The existing eligibility-rule CRUD and quality-rule CRUD become saved-filter views (`?scope=eligibility` / `?scope=quality`). The formQuality app's authoring UI continues to work for legacy rule edits but is no longer the canonical location.

**Acceptance**: every authoring action available in the formQuality app is also available in the new unified UI; all 21 quality rules + N eligibility rules visible and editable.

### Slice E — Retire formQuality app (1 day)

Delete the formQuality app's userview. Drop the `qa_*` form definitions. Archive the JWA for the historical record. Remove any residual references in tooling, documentation, and the user manual.

This is the no-going-back step. Done last, after Slices A–D have been stable for at least one week.

**Acceptance**: zero references to `qa_rule`, `qa_service`, `qa_tab`, `qa_gate`, `qa_issue`, `qa_record_status`, or the `formQuality` appId in the codebase, the documentation set, or the running app metadata.

**Total scope**: ~8–10 working days for a single engineer, executed across two sprints.

### Rollback strategy

* Slice A: rollback by removing the added columns. No data loss; no behavioural impact.
* Slice B: rollback by deleting the new `mm_rule` rows where `scope='quality'`. The original `qa_rule` rows are still there and still authoritative.
* Slice C: rollback by reverting the form-quality-runtime patch to read `qa_rule` again. The dual-store window means both stores still have the data.
* Slice D: rollback by removing the new `Rules` menu from the userview.
* Slice E: this slice is intentionally NOT rollback-safe. Do it only after every prior slice has been stable for ≥1 week.

---

## 4. Test strategy (summary)

The full test architecture for this project lives in `_design/test_strategy.md` (companion document). For this ADR specifically, three test categories anchor the migration:

1. **Per-rule equivalence tests.** For each of the ~21 quality rules + N eligibility rules: feed a fixture record where the rule should fire, assert it fires; feed a record where it shouldn't, assert it doesn't. Run against both the old and new engine; assert byte-for-byte outcome equivalence. Authored as a new `_tooling/test_rules_equivalence.py` script.

2. **L4 parity (existing).** The 20-scenario eligibility regression test continues to be the gate. After each slice, L4 must pass.

3. **L5 quality regression (new).** A new pytest harness covers the 21 quality rules end-to-end: trigger a save, assert the QualityBanner state, assert the gate behaviour on status transitions. Authored alongside Slice C.

Performance regression: the per-save evaluation budget remains <500 ms across all rules in the heaviest service (`farmer_application`). Profile on every slice.

---

## 5. Risks and open questions

### 5.1 Risk — fast-path vs SQL-path coverage for quality probes

The eligibility evaluator's fast path was built for simpler probe shapes. Some `qa_rule` SQL is more complex (multi-table joins). If the fast path can't compile a quality rule, it falls back to the SQL path. Worst case, all quality rules run via SQL-path; eligibility rules retain the fast path. Architecturally clean either way; the choice is per-rule. Verify via the equivalence tests.

### 5.2 Risk — `mm_rule_gate` is new shape

Eligibility doesn't have gates today (an eligibility failure produces a disposition; it doesn't block transitions). Gates are quality-specific. The new `mm_rule_gate` table accommodates quality's pattern; eligibility ignores it. The asymmetry is documented in the table schema.

### 5.3 Risk — `qa_record_status` aggregation cost

`qa_record_status` materialises per-record aggregate counts. Today this is recomputed by the post-processor on every save. After unification, the same recompute happens but against the larger `rule_outcome` store. Verify aggregation is still O(rules-per-record), not O(all-rule-outcomes-globally).

### 5.4 Risk — bot_pull rules and the unified trigger model

`bot_pull` rules don't fit the "fail/pass" mental model — they populate fields. They're already in `mm_determinant` as `scope=bot_pull`. They run on a *different* trigger than eligibility (typically on field-change rather than on submit). The unified `triggerOn` enumeration must include enough values to cover all three scopes' lifecycles. Open question: does `triggerOn` need to be more granular than `submit / save / manual`? Likely a fourth value (`field_change`) for bot_pull. Defer; today's bot_pull continues to use its existing trigger plumbing.

### 5.5 Open question — naming

Should `mm_determinant` rename to `mm_rule`? Pro: clearer scope. Con: backward-compat — any external system or tooling that references `mm_determinant` breaks. The system is pre-production, so the cost is small. **Decision: rename.** Document in the ADR consequences (below).

### 5.6 Open question — gate on eligibility?

Today eligibility produces a disposition that auto-rejects on `failed_mandatory`. In the unified model, this is functionally equivalent to a gate on the `submitted → auto_approved` transition. Worth standardising? Probably yes — collapses two control mechanisms into one. Defer to a follow-up ADR if scope grows.

---

## 6. Trade-offs named

**Convention over Invention** pulled toward keeping the two engines: each was built for its purpose, both work, the failure modes are well-understood. Refactor cost vs. status-quo cost.

**SRP + DIP** pulled toward unification: two engines doing functionally identical work is the textbook case for consolidation. Different scopes are an *attribute* of a rule, not a *property* of an engine.

**YAGNI** pulled toward keeping things separate: don't refactor what works.

**Pre-production timing** is the resolving factor. Refactor cost is bounded (no live data); status-quo cost compounds (every new module pays the two-engine tax). At pre-production, SRP wins.

The earlier formQuality split was itself a Convention-over-Invention decision (write a generic Joget plugin, follow the marketplace pattern). It accumulated debt because the actual integration ended up tight despite the structural separation. This ADR corrects that.

---

## 7. Consequences

**Positive**

* One rule engine, one storage table, one outcome store, one authoring UI. Conceptual simplicity for analysts and engineers.
* New modules add rules in one place. The next MAFSN module (livestock voucher, fishery subsidy, …) configures eligibility + quality together rather than in two app contexts.
* The QA Lead and the programme designer use the same tools. Cross-pollination of conventions.
* One set of tests guards the rule engine, not two divergent test sets.

**Negative**

* Breaking change: any tooling that references `mm_determinant`, `qa_rule`, `qa_issue`, etc. by name needs updating. Migration script handles the canonical references; one-off scripts may need touch-up.
* The unified table is wider than either of its predecessors. New columns (`severity`, `triggerOn`, `aggregation`, `affectedFields`) carry meaningful nulls for some rule types, which can confuse first-time readers. Mitigated by documentation.
* The formQuality app being retired means the JWA snapshot (`APP_formQuality-2-20260507083841.jwa`) is the historical reference. Don't lose it.

**Neutral**

* The plugin JAR consolidation (form-quality-runtime → reg-bb-engine) means one fewer JAR upload at deploy time. Slight operational simplification.
* The renaming of `mm_determinant` → `mm_rule` requires touching documentation in several places (CLAUDE.md, the SAD, the user manual, this ADR, the architecture overview). Mechanical edit; one PR.

---

## 8. References

* `gs-plugins/form-quality-runtime/` — the form-quality plugin source.
* `gs-plugins/reg-bb-engine/` — the eligibility engine source; will absorb the form-quality logic.
* `_design/architecture/components/rules-determinants-architecture.md` — current architecture; supersedes this ADR for ongoing detail once accepted.
* `APP_formQuality-2-20260507083841.jwa` — the formQuality app archive; the historical reference once retired.
* `APP_farmersPortal-1-20260507084455.jwa` — the live farmersPortal JWA showing the integration shape.
* `_design/test_strategy.md` — companion test architecture document.
* L4 parity test: `_tooling/run_l4_scenarios.py`.
* CLAUDE.md "Form post-processor vs. storeBinder" gotcha — relevant to the post-processor migration.

---

## 9. Sign-off

This ADR is **Proposed**. Acceptance requires:

- [ ] Architecture review by the customer's technical lead (one architect approval).
- [ ] Engineering review for migration plan feasibility.
- [ ] Test strategy review (`_design/test_strategy.md`).

Once accepted, the ADR moves to **Accepted** and the slices begin in order. Any deviation from the plan above produces a new ADR superseding this one.
