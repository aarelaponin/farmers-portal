# ADR-006 — Schema for evaluation outcomes

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §4.2, §10.2, §12 Q5; `regbb-solution-architecture-spec.md` §6.5 (submit lifecycle), §6.5.1 (status enum), §7.4 (audit table); `_design/decision-log.md` D6 (scoring extension), D9 (`dataJson` repeating-group precedent), D15 (`pending_review` band — structured outcome on `dataJson`); `CLAUDE.md` "HARD RULE — Joget-native API only" |

> **Drafting order note.** This ADR is drafted before ADR-005 because the
> outcome schema constrains what the save-lifecycle hook writes. ADR-005
> picks a hook *after* this ADR fixes the persistence shape.

---

## 1. Context

### 1.1 What "outcome persistence" means

When the evaluator finishes evaluating the eligibility Determinants on an
application, the result must be persisted somewhere the back-office
operator can read at review time and somewhere downstream consumers
(workflow gateways, status-update logic) can branch on. Two persistence
shapes are natural; both have spec backing for different reasons:

* **A column on the application form's row** carrying the aggregated
  per-Registration outcome — one row, one outcome (D15 names the
  structured shape: `{mandatoryPassed, totalScore, passingThreshold,
  minimumScore, disposition}` on `app_application.dataJson.eligibilityOutcome`).
* **An audit table** (`reg_bb_eval_audit`, spec §7.4) carrying one row
  per Determinant evaluation that produced a material outcome — the
  per-rule trail.

The two shapes answer different questions. The column answers "what is
this application's aggregated eligibility right now?". The audit table
answers "which rules fired, with what inputs, when, who?".

### 1.2 What the spec mandates

`regbb-solution-architecture-spec.md` §7.4 specifies the
`app_fd_reg_bb_eval_audit` table with columns `id`, `c_application_id`,
`c_determinant_id`, `c_evaluated_at`, `c_evaluated_by`, `c_inputs_json`,
`c_outcome` (true|false|error), `c_cause`, `c_action_taken_json`,
`c_correlation_id`. Rows are written **only when outcome causes an
action** — submit, fee-calc, doc-resolver, role assign, process tool —
not on per-keystroke Ajax re-evaluation. Retention: 1 year via a
scheduled `RegBbAuditPurger`.

§6.5 (submit lifecycle, step 943) specifies that submit "re-evaluates
all `applyFee` and `requireDoc` determinants and persists the resolved
values into `app_application.dataJson`." The spec mandates `dataJson`
persistence for a specific subset of evaluations (fees, required docs).
Eligibility outcomes are not explicitly named in §6.5 step 943; the
spec refers to them in §6.4 and §8.4 generally without specifying the
exact column.

`decision-log.md` D15 settles the `dataJson` placement for eligibility
specifically: "When `mandatoryPassed=true && minimumScore <= totalScore
< passingThreshold` (the pending-review band introduced by D6's scoring
extension), [...] the structured outcome
`{mandatoryPassed, totalScore, passingThreshold, minimumScore,
disposition}` lives on `app_application.dataJson.eligibilityOutcome`."

### 1.3 What the Joget native audit covers and doesn't cover

Spec §7.4 enumerates five audit sources and is explicit that
`reg_bb_eval_audit` "fills a specific gap: native `dir_audit` records
that a value changed but not why a routing or fee or doc decision was
reached." Joget's built-in form audit (`app_form_audit`) records CRUD on
`app_*` and `mm_*` tables but does not record the chain of reasoning
that produced a particular column value — i.e., it tells you the
column changed, not which rules fired to make it change.

### 1.4 The slice-by-slice consumption pattern

The architecture doc §13 sequences delivery in slices:

* **Slice 1A** — first rule, fast path, post-store hook. Demo target:
  Lerato (lowlands) → PASS, Tšepiso (mountains) → FAIL with the rule's
  failMessage. The operator inbox needs to render the disposition.
  Audit-trail consumption is not yet required.
* **Slice 1B** — SQL path, registry-touching rule. Audit rows
  introduced. Operator review form reads audit for "explain this
  decision" affordance.
* **Slice 1C** — multi-rule scoring per D6. Operator inbox sortable by
  `totalScore`. The aggregated outcome must carry score components.
* **Slice 1D / 1E / 1F** — field-scope, fee, document-resolver, role
  Determinants. Audit rows fire on each material outcome.

The two persistence shapes earn their keep at different slices. The
column earns its keep from slice 1A (operator inbox renders disposition).
The audit table earns its keep from slice 1B (operator's "explain"
affordance and registry-refresh re-evaluation history).

### 1.5 What "indeterminate" means

Architecture doc §8.1 specifies that the engine never leaves the
outcome NULL on a saved row. Failure modes (`evaluator_unavailable`,
`registry_unavailable`, `breaker_open`, `db_unavailable`, `internal:*`)
all produce a structured outcome with `disposition=indeterminate` and a
`reason` cause. The operator triages indeterminate applications
manually. This is spec P5 (loud failure) at the outcome level.

### 1.6 Principles in scope

* **SRP** — the column and the audit table answer different questions;
  one entity per question.
* **Spec alignment** — §7.4 mandates the audit table; D15 mandates the
  `dataJson.eligibilityOutcome` column; both must be honoured.
* **Honest failure surface** — every saved row carries an outcome,
  including failure cases, per architecture doc §8.1.
* **Phasing discipline** — earn each new entity when a real consumer
  exists, not before (architecture doc §10.2 is explicit about this for
  the audit table: "Phase 1 may defer the audit table to slice 1B/1C,
  with eligibility outcomes captured in
  `app_application.dataJson.eligibilityOutcome` instead").

---

## 2. Decision

The outcome persistence schema is **two-tier**:

* **Tier 1 — `app_application.dataJson.eligibilityOutcome`** (a JSON
  field stored as a column value on the application form's row, per
  Joget's standard `dataJson` pattern, populated by the
  `RegBbApplicationStoreBinder` per architecture doc §5.6) — carries
  the aggregated, current outcome per Registration. Shape per D15:
  `{mandatoryPassed, totalScore, passingThreshold, minimumScore,
  disposition, perRegistration: [{registrationCode, disposition,
  failMessage, ...}]}`. Always populated on a saved row, including
  failure cases (`disposition=indeterminate`, `reason=<cause>`).
  Updated on every save and on every operator-triggered re-evaluation.
  This tier ships in **slice 1A**.
* **Tier 2 — `app_fd_reg_bb_eval_audit`** (a Joget form-data table
  defined per spec §7.4) — carries one row per Determinant evaluation
  that produced a material outcome, per spec §7.4 row schema. Material
  outcomes: visibility change took effect, fee was applied, document
  was demanded, role was assigned, eligibility decision was reached at
  submit, process tool fired. Per-keystroke Ajax re-evaluations do not
  write rows. This tier ships in **slice 1B** (per architecture doc
  §13 — the slice that introduces SQL-path and registry-touching
  rules, where the per-rule trail materially helps debugging).

The tiers are complementary, not redundant. Tier 1 tells the operator
review form what the current outcome is; Tier 2 tells the operator
*why* that outcome was reached.

---

## 3. Reasoning

### 3.1 SRP — current state and historical trail are different responsibilities

The aggregated outcome is mutable: each save recomputes it. The audit
trail is append-only: each evaluation adds a row, never edits one.
These are different write disciplines, different read patterns
(operator inbox reads the column to filter/sort; operator review reads
the audit table to "explain"), different retention concerns (the
column lives as long as the application row; the audit rolls off at
1 year via the spec's purger). One responsibility per entity.

Putting both in the column (the aggregated outcome plus a serialised
trail of evaluations) collapses two responsibilities and produces a
column that mutates on every save while also accumulating history —
the worst of both worlds. SRP rejects.

### 3.2 Spec §7.4 mandates the audit table; this ADR honours it without making it Phase 0

§7.4's audit table is the spec's normative requirement. Diverging from
it (e.g., "we'll never have an audit table; everything goes in the
column") would be a documented divergence on the scale of ADR-001 r2's
DSL-grammar choice — a real architectural commitment, with a
spec-amendment tail. There is no architectural reason to make that
commitment; the audit table answers a real question and earns its
keep at slice 1B.

§10.2 of the architecture doc explicitly invites the deferral pattern:
"Phase 1 may defer the audit table to slice 1B/1C, with eligibility
outcomes captured in `app_application.dataJson.eligibilityOutcome`
instead." This ADR adopts that invitation as the slicing schedule.

### 3.3 D15 mandates the column; this ADR honours D15

`decision-log.md` D15 is binding: the structured outcome lives on
`dataJson.eligibilityOutcome`. The disposition values (`eligibility_passed`,
`eligibility_failed_mandatory`, `eligibility_failed_score`,
`eligibility_pending_review`, `indeterminate`) are annotations on
`dataJson.eligibilityOutcome.disposition`, not values of
`app_application.status`. The closed status enum (spec §6.5.1) is
preserved.

### 3.4 The column persists via Joget-native API; no raw SQL

Per `CLAUDE.md` HARD RULE, every write to `app_fd_*` goes through
Joget's `FormDataDao.saveOrUpdate(...)`. The
`RegBbApplicationStoreBinder` (per ADR-005, drafted next) sets the
storeBinder rowSet's `eligibilityOutcome` key to the JSON-serialised
outcome value before the save runs; Joget's standard form-store path
persists it. No bypass.

For the audit table: the `app_fd_reg_bb_eval_audit` table is a Joget
form-data table — defined by an `mm_*`-managed form definition (or a
fixed form created at engine bundle deploy time). Writes go through
`FormDataDao.saveOrUpdate(...)` exactly as for any form-data table. The
audit-writing code path lives in the evaluator itself (not in the
storeBinder), called immediately after producing each material outcome
(architecture doc §7.3 last bullet).

### 3.5 Indeterminate is structured, not absent

When the evaluator returns `outcome=error`, the storeBinder writes
`disposition=indeterminate` with a `reason` cause to
`dataJson.eligibilityOutcome`. The row is never NULL on this column.
Operator inbox queries (sort by disposition, filter by indeterminate)
can rely on the column always being populated. Spec P5 (loud failure)
holds at the persistence layer.

### 3.6 Slice 1B's audit-table introduction is naturally aligned with consumer arrival

The audit table earns its keep when the first consumer needs the
trail. Slice 1B introduces SQL-path rules (`$registry.*` references
included), which are the rules with the highest debugging cost: a
registry response changed, the rule's outcome flipped, the operator
needs to know why. That is exactly the audit table's value
proposition. Earning the entity at slice 1B aligns the schema's
introduction with its first real consumer.

---

## 4. Alternatives considered and refused

### 4.1 Option A — column only, no audit table ever

**Refused on spec divergence and debugging surface.** Spec §7.4 is
normative; eliminating the audit table is a documented divergence
without architectural justification. The only reason to do it is to
save the schema-creation work, which is one form definition. The cost
is real: every "why did this rule fire?" question becomes archaeology
through Joget's native `app_form_audit` (which records column values
changing but not which rules ran). Refused on cost-benefit grounded in
real debugging burden.

### 4.2 Option B — audit table only, no column

**Refused on read-shape mismatch.** The operator inbox needs to
filter and sort by current outcome (per architecture doc §13 slice 1A
and 1C — operator sortable by `decision_score`, filter by disposition).
Computing the current aggregated outcome from the audit table on every
inbox query means a SQL aggregation per-application per-render — slow
and complex.

The audit table's row pattern (one row per Determinant evaluation)
also doesn't naturally answer "what is this application's current
aggregated outcome?" without a "latest non-error row per
(application, scope=eligibility)" query that has to join across
evaluations and disambiguate by `evaluated_at`. The column is the
materialised view that makes the inbox query cheap. SRP says: keep it.

### 4.3 Option C — both column and audit table from slice 1A (Phase 0 of both)

**Refused on phasing discipline.** Slice 1A's only consumer is the
operator inbox rendering disposition. There is no consumer of the
audit trail until slice 1B. Adding the audit table in slice 1A
introduces a deployment artefact (form definition + purger job) that
no caller writes to or reads from for the duration of slice 1A. The
architecture doc §10.2 invites the deferral; the ADR brief explicitly
permits "earn the audit table when there's a second consumer." This
ADR earns it at slice 1B.

This is not "we don't need it yet" feature-need framing — the audit
table genuinely has no consumer in slice 1A. Slice 1B introduces both
the consumer (operator's "explain" affordance) and the schema
(audit table) together. Coupling artefact creation to consumer arrival
is a discipline.

### 4.4 Option D — audit serialised into the column as a trail

A hypothetical: `dataJson.eligibilityOutcome.history = [{evaluatedAt,
outcome, ...}, ...]`. **Refused on column growth and append discipline.**
A column is mutable; serialising an append-only trail into a mutable
column means every save rewrites the trail (read-modify-write the JSON
blob). At any reasonable cardinality (tens of evaluations per
application across its lifecycle), the rewrite cost grows. The audit
table's append-only discipline is structurally simpler and correct.

### 4.5 Option E — Joget's native `app_form_audit` only, no `reg_bb_eval_audit`

**Refused on semantic gap.** `app_form_audit` records that the
`eligibilityOutcome` column changed; it does not record which
Determinants fired with which inputs. The "which rules fired" question
is exactly the gap §7.4 names. Refused per spec §7.4's explicit
purpose.

---

## 5. Consequences

### 5.1 Positive

* Two clear entities, two clear responsibilities. Operator inbox
  reads the column; operator review's "explain" reads the audit table.
* Slice 1A ships without the audit table. Smaller deployment artefact;
  the form definition for the audit table is created in slice 1B's
  scope.
* Indeterminate is a first-class outcome value, structurally
  consistent with all other dispositions.
* Joget-native: column persisted via storeBinder rowSet; audit table
  written via `FormDataDao.saveOrUpdate`. No raw SQL, no bypass.
* The aggregated outcome's shape (D15) is JSON; adding new fields to
  the outcome (Phase 2's `evaluatedAt`, `serviceVersion` recapture,
  etc.) is an additive `dataJson` change with no schema migration.

### 5.2 Negative

* **The audit table grows.** Architecture doc §10.2 specifies a
  1-year retention via `RegBbAuditPurger`. At scale, this is the
  largest table in the system: every material outcome on every
  application produces ~5–10 audit rows (one per Determinant in the
  service); 100k applications/year × 8 rows/application = 800k rows.
  The retention purger is critical, not optional. Forecast Phase 2:
  partition the table by month if volume warrants.
* **The column and the audit table can disagree** under failure: a
  save persists the column update but the audit-row write fails for
  some rows. The discipline is "column write is authoritative for
  current state; audit table is best-effort with a follow-up retry
  queue." The disagreement is documented as a known property; the
  audit table is not the source of truth for current state.
* **The `eligibilityOutcome` column is JSON-as-text.** SQL-native
  queries against it require Postgres JSONB operators (per
  `decision-log.md` D9). This is acceptable but means operator inbox
  filters use JSONB syntax (`dataJson->>'disposition' = 'eligibility_passed'`),
  not column-equals-value. Slightly more complex to author.
* **Slice 1B's audit table introduction has a small migration cost** —
  the engine in slice 1A wrote no audit rows; slice 1B begins writing.
  No data migration (the table is empty before slice 1B), but the
  evaluator code that writes audit rows is new code with its own test
  surface.

### 5.3 Neutral

* The audit table's schema (per spec §7.4) is fixed by the spec; this
  ADR does not modify it.
* The aggregated outcome's shape (per D15) is fixed by the decision
  log; this ADR ratifies it.
* The retention discipline (1 year via `RegBbAuditPurger`) is per spec
  §7.4 and architecture doc §10.2; this ADR does not modify it.

---

## 6. Implementation outline

Sketch.

### 6.1 Slice 1A

1. Add an `eligibilityOutcome` HiddenField (or TextArea, JSON-typed) to
   the application form's definition, persisting to
   `c_eligibility_outcome`. Per `CLAUDE.md` HARD RULE, the field is
   added through `form-creator-api` or App Composer's Save — never via
   raw SQL `ALTER TABLE`.
2. The `RegBbApplicationStoreBinder` (per ADR-005) writes the
   aggregated outcome JSON to this field via the storeBinder rowSet
   (the value lands in `app_fd_<application_table>.c_eligibility_outcome`
   alongside the rest of the row).
3. The aggregation logic (per `decision-log.md` D6 + D15) lives in
   the storeBinder; produces the structured outcome described in §2.
4. The operator inbox datalist column reads the column via Postgres
   JSONB operators (`dataJson->>'disposition'`). The datalist
   definition is in scope for slice 1A.

### 6.2 Slice 1B

1. Define the `app_fd_reg_bb_eval_audit` form (per spec §7.4 schema).
   Fields per spec column list. Form definition lives in
   `gs-plugins/reg-bb-evaluator/src/main/resources/forms/`.
2. Implement the audit-writer in the evaluator: every material
   outcome triggers a `FormDataDao.saveOrUpdate(formDefId, tableName,
   FormRowSet)` to insert one row. The writer is a side-effect of
   evaluation, not a separate code path the caller must invoke.
3. Implement `RegBbAuditPurger` as a Joget scheduled task per
   architecture doc §10.2: nightly, deletes audit rows older than the
   retention window.
4. Operator review form gains an "explain this decision" subform that
   lists the audit rows for the current application's Determinants,
   ordered by `evaluatedAt`.

### 6.3 Failure-path discipline

If the column write fails during save (Joget's `saveOrUpdate` throws),
the storeBinder lets the throw propagate (per ADR-007's atomicity
discipline). Joget rolls back the form-store transaction; the row is
not saved. The operator sees a save failure.

If the audit-row write fails, the evaluator logs ERROR and continues
— the column is the source of truth for current state; the audit
table is best-effort. A follow-up retry queue (Phase 2) is on the
table if audit-loss becomes operationally significant.

---

## 7. Open questions

### 7.1 Audit-row retention duration

Spec §7.4 says 1 year. Some regulatory jurisdictions may require
longer (financial-record-tier retention is sometimes 5–7 years). Is
1 year the right Phase 1 default?

This is the kind of question that warrants user input rather than
default-take. Forwarded to a clarification at acceptance time:

> *Question for sign-off:* What retention window does the Lesotho
> regulator require for `reg_bb_eval_audit`? Default 1 year per spec;
> if longer, `RegBbAuditPurger` is reconfigured.

### 7.2 Whether the column carries `correlationId`

The audit table carries `correlation_id` (the NGINX-injected
`X-Request-Id`). The column does not currently carry it. The
correlation between an inbox-displayed disposition and its audit-row
trail relies on `(applicationId, evaluated_at)` joining. Adding
`correlationId` to the column makes the join trivial but inflates the
column shape. Default position: don't add it; the audit table's
`(applicationId, evaluated_at)` query is sufficient. Forwarded to
implementation.

### 7.3 Whether `inputs_json` in the audit table truncates large `data`

Spec §7.4 specifies `inputs_json` carries "values that fed the rule
(refs resolved)". Large `$registry.*` payloads (e.g., a registry
record with many fields) bloat audit rows. Default position: `inputs_json`
carries only the values the rule's reference set actually consumes
(the same projection used for `dataHash`, per architecture doc §4.3).
Forwarded to implementation.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` §6.5 step 943 should explicitly
reference `dataJson.eligibilityOutcome` (currently it names "fee" and
"required-doc" persistence; eligibility persistence is implicit). Add
one sentence: "Aggregated eligibility outcomes are persisted to
`app_application.dataJson.eligibilityOutcome` per the structured shape
recorded in `decision-log.md` D15."

§7.4: no change. The audit table schema is normative.

`determinant-architecture.md` §10.2 ("Phase 1 may defer the audit
table to slice 1B/1C") is consistent with this ADR; §12 Q5 is
replaced with a backlink to this ADR.

`decision-log.md` D15 is consistent with this ADR; cross-reference
added at architecture-doc closeout.

---

## 9. Decision record

| | |
|---|---|
| Decision | Two-tier: `dataJson.eligibilityOutcome` column from slice 1A; `app_fd_reg_bb_eval_audit` table from slice 1B |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins)* |

---

*End of ADR-006.*
