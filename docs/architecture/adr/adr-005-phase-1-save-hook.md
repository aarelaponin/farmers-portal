# ADR-005 — Where the evaluator hooks into the application save lifecycle in Phase 1

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §5.6, §7.5, §12 Q4; ADR-006 (outcome persistence schema); `regbb-solution-architecture-spec.md` §6.5 step 943 (`MetaScreenElement.submit`), §6.5.1 (status enum); `jw-community/wflow-core/src/main/java/org/joget/apps/form/lib/WorkflowFormBinder.java`; `jw-community/wflow-core/src/main/java/org/joget/apps/form/service/FormServiceImpl.java`; `CLAUDE.md` "HARD RULE — Joget-native API only" |

---

## 1. Context

### 1.1 What "save-lifecycle hook" means

The Phase 1 citizen surface is a Joget wizard (per `decision-log.md`
D24). The citizen fills in tabs and clicks Save. The evaluator must run
the eligibility Determinants on the just-saved data and persist the
aggregated outcome (per ADR-006 — `dataJson.eligibilityOutcome`).

The question: at which point in Joget's save lifecycle does the
evaluation happen? Joget exposes several extension points; each has a
different lifecycle relationship to the row being saved.

### 1.2 The spec's eventual shape and Phase 1's gap

`regbb-solution-architecture-spec.md` §6.5 step 943 specifies the
spec-aligned submit:

> "The Submit button on the review screen invokes
> `MetaScreenElement.submit(applicationId)`. This: (1) flips
> `app_application.status` to `submitted`, (2) re-evaluates all
> `applyFee` and `requireDoc` determinants and persists the resolved
> values into `app_application.dataJson`, (3) looks up the active
> binding row, (4) calls `WorkflowManager.processStart(processDefId,
> vars)`, (5) writes the returned `processInstanceId` onto the
> `app_application` row, (6) calls `evaluator.invalidate(applicationId)`."

This is the eventual right shape. It assumes the full review-screen-as-
`mm_screen` model is in place — the operator-side review screens
(`MetaReviewElement`, per `decision-log.md` D17) and the spec-aligned
submit pipeline. Phase 3 deliverable per architecture doc §13.

Phase 1 ships before Phase 3. Slice 1A's exit criterion (architecture
doc §13) is "Lerato (Maseru, lowlands) → PASS; Tšepiso (Mokhotlong,
mountains) → FAIL with the rule's `failMessage`. Stop the evaluator
bundle → indeterminate disposition appears." This requires evaluation
to happen on the citizen's save click; it does not require the full
spec submit pipeline.

### 1.3 What Joget's save lifecycle exposes

Reading from the Joget DX 8.1 source:

* **`org.joget.apps.form.service.FormServiceImpl.executeFormStoreBinders(Form, FormData)`**
  (`jw-community/wflow-core/.../FormServiceImpl.java` line 534) — the
  orchestrator called when a form is saved. It does, in order: set
  workflow variables, call `FormUtil.executeElementFormatData(form,
  formData)` to populate the rowSet from element values, then
  `recursiveExecuteFormStoreBinders(form, form, formData)` (line 565)
  which walks elements and invokes each non-readonly element's
  `FormStoreBinder.store(...)`. Workflow variables are written at the
  end if a workflow context exists.
* **`org.joget.apps.form.lib.WorkflowFormBinder`** (`jw-community/wflow-core/.../WorkflowFormBinder.java`)
  extends `DefaultFormBinder`, implements `FormLoadElementBinder`,
  `FormStoreElementBinder`, `FormDataDeletableBinder`. Its
  `store(Element, FormRowSet, FormData)` (line 93) calls the parent's
  store (which persists rows via `FormDataDao.saveOrUpdate(...)`) then
  writes workflow variables. Subclassing is the standard extension
  pattern: a custom binder calls `super.store(...)` then runs
  post-save logic.
* **`org.joget.apps.app.service.AppServiceImpl.storeFormData(formDefId,
  tableName, FormRowSet, primaryKey)`** (`jw-community/wflow-core/.../AppServiceImpl.java`
  line 2131) — the actual persist path. Validates inputs, generates
  UUIDs, sets metadata (dateModified, modifiedBy, dateCreated,
  createdBy), loads the existing row to preserve creation metadata,
  calls `formDataDao.updateSchema(...)`, `formDataDao.saveOrUpdate(...)`,
  `FileUtil.storeFileFromFormRowSet(...)`. The method is not
  explicitly `@Transactional`; transaction boundaries are managed by
  the Hibernate session opened inside `FormDataDaoImpl.saveOrUpdate(...)`.
* **XPDL Tool activity** — Joget's workflow can run a tool task
  registered as an OSGi service implementing `PluginInterface`. Tool
  tasks run on the workflow thread; they have access to the workflow
  context (`processInstanceId`, `activityId`, `processVariables`).
  Used for "do something after the citizen submits" patterns.
* **A custom servlet** registered as `PluginWebSupport` — handles a
  POST to a custom URL. Bypasses the form-store lifecycle entirely;
  the engine reads the request body, does its own work, returns its
  own response.

### 1.4 What "Phase 1's submit" actually is

Phase 1 does not have the `MetaScreenElement.submit(applicationId)`
spec submit pipeline. The citizen interaction is: open the Joget
wizard, fill in tabs, click Save. The Save button is Joget's standard
form-Save, which triggers `executeFormStoreBinders`. There is no
separate "submit" step in slice 1A — Save *is* the submit.

(Slice 1F adds a richer publisher integration; slices 2 and 3 build
toward `MetaScreenElement.submit`. None of slice 1A through 1E has the
spec submit.)

### 1.5 What ADR-006 fixed about the outcome shape

ADR-006 fixes that the aggregated outcome lives in
`app_application.dataJson.eligibilityOutcome` (slice 1A) and the audit
table arrives at slice 1B. The hook must therefore be capable of:

* Setting `eligibilityOutcome` on the row that's being saved (or
  immediately after).
* Optionally writing audit rows (slice 1B+).
* Surviving evaluator failure (write
  `disposition=indeterminate` per architecture doc §8.1).

### 1.6 Principles in scope

* **CLAUDE.md HARD RULE** — Joget-native API only; no raw SQL on
  `app_*` or `app_fd_*`. Whatever hook is chosen must persist via
  `FormDataDao` or its callers.
* **SRP** — the hook's responsibility is "trigger eligibility
  evaluation and persist outcome." It is not the spec submit's
  responsibility (process start, status flip — those are Phase 3).
* **Reversibility** — Phase 1's hook retires when Phase 3's spec
  submit arrives. The hook should be small enough to remove cleanly.
* **Honest failure surface** — every saved row carries an outcome
  (architecture doc §8.1, ADR-006 §3.5).

---

## 2. Decision

The Phase 1 evaluator hooks into the application save lifecycle via a
**custom storeBinder, `RegBbApplicationStoreBinder`**, which extends
`org.joget.apps.form.lib.WorkflowFormBinder` and overrides
`store(Element, FormRowSet, FormData)`. The override:

1. Calls `super.store(element, rowSet, formData)` — the standard save
   runs first, persisting the row via `FormDataDao.saveOrUpdate`.
2. Reads the just-persisted row's primary key from `formData`.
3. Constructs an `EvalContext` populated from the rowSet plus the
   workflow user (`WorkflowUserManager.getCurrentUsername()`) plus
   the correlation id (NGINX `X-Request-Id` header propagated via
   request context).
4. For each `selectedRegistration`, gathers the eligibility
   Determinants and calls `evaluator.evaluate(determinantId, ctx)` on
   each.
5. Aggregates per `evaluationStrategy` (per `decision-log.md` D6) into
   the structured `eligibilityOutcome` JSON (per ADR-006 §2 + D15).
6. Writes the JSON to `dataJson.eligibilityOutcome` via a follow-up
   `FormDataDao.saveOrUpdate(formDefId, tableName, FormRowSet)` call
   carrying the same primary key.
7. Calls `evaluator.invalidate(applicationId)` to clear cache entries
   for this application.

If the evaluator service is unavailable or any evaluation throws, step
5 produces `disposition=indeterminate` with a `reason` cause; the row
is never left without an outcome value (architecture doc §8.1).

The storeBinder is bound to the application form's storeBinder slot
via the form definition's `storeBinder` property — the standard Joget
configuration pattern, no Java glue needed beyond the binder class.

The storeBinder **retires when Phase 3's `MetaScreenElement.submit`
arrives** (architecture doc §13 slice 3). At that point the spec submit
takes over the evaluation responsibility and the storeBinder shrinks
to a no-op subclass of `WorkflowFormBinder` (or is removed entirely
and replaced with `WorkflowFormBinder` directly in the form
definition).

---

## 3. Reasoning

### 3.1 SRP — the hook's responsibility is well-bounded

A storeBinder's documented purpose (per Joget DX 8.1 source —
`WorkflowFormBinder` line 93 store contract; `FormServiceImpl.executeFormStoreBinders`
line 534 calling pattern) is to extend the form-save lifecycle with
plugin-specific persistence behaviour. Eligibility evaluation as a
side-effect of save is exactly that pattern. The storeBinder owns one
responsibility: "save the row, then evaluate, then persist outcome."
Other concerns (status flip, process start, fee re-evaluation,
required-doc resolution) belong to the Phase 3 spec submit and stay
out of the storeBinder.

### 3.2 Joget-native by construction

The storeBinder pattern is the canonical Joget extension point for
post-save logic. Persistence flows through
`super.store(...)` → `FormDataDao.saveOrUpdate` (per
`WorkflowFormBinder` line 93 and `FormDataDaoImpl.saveOrUpdate` line
498). The follow-up `dataJson.eligibilityOutcome` write also flows
through `FormDataDao.saveOrUpdate`. No raw SQL on `app_*` or
`app_fd_*`. CLAUDE.md HARD RULE is honoured by construction.

### 3.3 Spec alignment posture — A is the documented Phase 1 stand-in for D

`regbb-solution-architecture-spec.md` §6.5 step 943 names
`MetaScreenElement.submit(applicationId)` as the eventual right
shape. The architecture doc §12 Q4 explicitly notes "A for Phase 1; D
for Phase 3" — A is Joget-native, gives end-to-end demo today, retires
cleanly when D arrives. This ADR ratifies that phasing. The
storeBinder is honest about being a Phase 1 stand-in; it does not
pretend to be the spec shape.

### 3.4 Cleanup at Phase 3 is mechanical

When `MetaScreenElement.submit(applicationId)` ships in Phase 3, the
spec submit takes over: it reads the citizen's row, runs the
evaluator, persists the outcome. The storeBinder is no longer needed
for evaluation. Two retirement paths:

* **Replace the binder in the form definition** — change the form's
  `storeBinder` property from `RegBbApplicationStoreBinder` to plain
  `WorkflowFormBinder`. One form definition edit, deployed via the
  form-creator-api per `CLAUDE.md` HARD RULE. No data migration, no
  schema change.
* **Make the binder a no-op** — `RegBbApplicationStoreBinder.store`
  just calls `super.store(...)` and returns. Slightly lazier; same
  effect.

The mechanical retirement is what justifies the storeBinder pattern
— it is the smallest hook that does the job, with the smallest
retirement footprint when its job is no longer needed.

### 3.5 Failure-mode honesty — disposition=indeterminate, never null

When the evaluator is unavailable (`PluginManager.list(...).orElse(null)`
returns null) or throws, the storeBinder catches and writes
`disposition=indeterminate` with `reason=evaluator_unavailable` or
`reason=internal:<class>:<msg>`. The row is saved (super.store
already ran); the outcome column is populated; the operator sees the
indeterminate banner and triages manually. Architecture doc §8.1's
"loud failure" discipline is upheld.

This is the discipline that makes the storeBinder pattern safe for
Phase 1: the hook never blocks the citizen's save on evaluator
failure. If the evaluator is down, the citizen still saves; the
operator does the manual review. Spec P5 honoured at the persistence
layer.

### 3.6 The two-transaction structure is acceptable; ADR-007 covers it

The storeBinder pattern produces two transactions: `super.store(...)`
opens a Hibernate session, persists the row, commits; the follow-up
`FormDataDao.saveOrUpdate(...)` for the `eligibilityOutcome` write
opens a fresh session and commits. Atomicity questions — what if the
first commits and the second fails? — are addressed in ADR-007. The
short version: the never-null outcome discipline plus retry-at-load
in the operator inbox is sufficient at Phase 1 scale.

---

## 4. Alternatives considered and refused

### 4.1 Option A — `RegBbApplicationStoreBinder` extending `WorkflowFormBinder` (the decision in §2)

The chosen option. Stated above.

### 4.2 Option B — XPDL Tool activity in a `farmer_application_submission` workflow

Spec §6.6 sequence diagram step "WF→Eng tool task" — an analyst-
authored XPDL package invokes `RegBbEvaluatorTool` after submit.
**Refused on Phase 1 deployment cost.** Requires: authoring an XPDL
package for the application submission workflow, registering
`RegBbEvaluatorTool` (which is itself a Phase 2 deliverable per
architecture doc §13 slice 2 — "Phase 2 deliverable"), running
`WorkflowManager.processStart(...)` on every save.

The XPDL approach also conflicts with the Phase 1 architecture: there
is no `farmer_application_submission` workflow today. Building one
purely to host the evaluator hook would be re-introducing the
workflow-driven architecture that the spec submit explicitly retires
(spec §6.5: "One code path; the only place a process is started" —
the spec is explicit that process start happens *inside* the submit
method, not in a workflow tool task).

This option is also not retirement-friendly: an XPDL package is harder
to remove than a storeBinder swap, and the tool task carries
operational weight (process instances, history rows, BSH script
config) that the storeBinder doesn't.

### 4.3 Option C — A custom servlet at `POST /regbb/submit`

A `PluginWebSupport` servlet handles the citizen's submit POST,
performs the save and evaluation in its own code path. **Refused on
Joget-native discipline.** Bypasses Joget's form-store lifecycle —
form validators, store-event listeners, file-upload handlers
(`FileUtil.storeFileFromFormRowSet`, per CLAUDE.md "Hard-won Joget
gotchas"), workflow-variable propagation. Replicating these in the
servlet means re-implementing a meaningful chunk of
`FormServiceImpl.executeFormStoreBinders` (line 534). SRP and
DRY violations both.

This option also breaks the Joget native admin surface: an operator
who clicks Save on the application from App Composer's data view
would not trigger the evaluator (App Composer uses the standard
form-store path, not the custom servlet). The storeBinder pattern is
called regardless of which save-triggering surface invokes it.

### 4.4 Option D — Spec-aligned `MetaScreenElement.submit(applicationId)` per spec §6.5

The eventual right shape. **Refused for Phase 1 only**, on Phase 1
deferral grounds: the full spec submit pipeline depends on the
review-screen-as-`mm_screen` model (`MetaReviewElement` per
`decision-log.md` D17), the operator-side review screens, and the
binding-row lookup machinery — none of which is in scope for Phase 1
slices 1A through 1F. Architecture doc §13 explicitly puts D at slice
3 / Phase 3.

This option is the right answer for Phase 3; it is the wrong answer
for Phase 1 because the dependencies aren't built yet. The ADR's
phasing discipline (architecture doc §13) places this option at
Phase 3.

### 4.5 Option E — A `FormPostProcessor` plugin or an `ExecutionListener`

Joget DX 8.1 has other extension points — form post-processors,
execution listeners on workflow events. **Refused on lifecycle
mismatch.** A post-processor runs after form storage but is invoked
from contexts the storeBinder doesn't see (e.g., admin imports,
batch updates); running the evaluator on every form save (including
admin-triggered batch saves) is wrong. The storeBinder's bind-to-
form-definition pattern is more precisely scoped: only saves that go
through the application form's bound storeBinder trigger evaluation.

Execution listeners run on workflow events, not form events. There
is no workflow in slice 1A. Misfit.

---

## 5. Consequences

### 5.1 Positive

* Joget-native by construction — every persistence call is through
  `FormDataDao.saveOrUpdate` (via `super.store(...)` and the
  follow-up write).
* Mechanical retirement at Phase 3 — a form-definition edit (binder
  class swap) replaces the storeBinder with the spec submit pipeline.
* End-to-end demo today — slice 1A's exit criterion (Lerato PASS,
  Tšepiso FAIL) is achievable without standing up the spec submit
  machinery.
* The hook's failure-mode discipline (write `indeterminate` on
  evaluator failure) ensures the citizen save never fails on
  evaluator unavailability — operator review handles the
  indeterminate cases.

### 5.2 Negative

* **Two transactions per save.** The standard save commits first;
  the outcome write commits second. ADR-007 addresses the atomicity
  question; the short version is "the never-null outcome
  discipline plus retry-at-load is sufficient at Phase 1 scale,"
  but the property is real and worth flagging.
* **The storeBinder runs on every save, not just on submit.** Phase
  1 has no separate submit; every Save triggers full evaluation. At
  Phase 1 scale (≤100 evaluations/day per architecture doc §9
  estimates), this is fine; at Phase 3 scale with per-tab autosave,
  it would be too aggressive. The retirement at Phase 3 (toward
  `MetaScreenElement.submit`) addresses this — the spec submit
  evaluates only on the citizen's submit click.
* **The storeBinder coupling is form-definition-bound.** Every
  application form that needs evaluation must be configured with
  this binder. If Phase 1 grows to multiple application forms, each
  needs its own form-definition edit. Slice 1A has one application
  form; slice 1E adds three more. Not load-bearing, but real.
* **Cache invalidation is caller-driven.** Step 7 of §2 calls
  `evaluator.invalidate(applicationId)` after the outcome write.
  If the storeBinder forgets to call invalidate (e.g., on an early
  return path), stale cache entries persist until L2 TTL rolls.
  Discipline: the invalidate call is in a `finally` block on the
  storeBinder's main method.

### 5.3 Neutral

* The `RegBbApplicationStoreBinder` class lives in the
  `reg-bb-engine` bundle (architecture doc §6.1). It is a rendering-
  layer concern (form-store binding) rather than an evaluator concern,
  consistent with the bundle decomposition.
* The storeBinder reads the evaluator via OSGi service lookup (per
  architecture doc §6.4 — `PluginManager.list(DeterminantEvaluator.class.getName())`),
  matching the discipline used for `MetaModelDao` (per ADR-002) and
  `RegistryReferenceResolver` (per architecture doc §6.4).

---

## 6. Implementation outline

Sketch.

1. Add `RegBbApplicationStoreBinder` class to
   `gs-plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/binder/`,
   extending `org.joget.apps.form.lib.WorkflowFormBinder`. Override
   `store(Element, FormRowSet, FormData)`.
2. The override body:
   - `FormRowSet stored = super.store(element, rowSet, formData);`
   - Look up evaluator via `PluginManager`; if null, log + continue
     with `evaluator==null` flag set.
   - Build `EvalContext` from `rowSet` (current row data) +
     `WorkflowUserManager.getCurrentUsername()` + correlation id from
     request thread-local.
   - For each `selectedRegistration` in
     `rowSet.get(0).getProperty("selectedRegistrations")`:
     - Fetch the Registration's eligibility Determinants via
       `MetaModelDao` (per ADR-002 — bundle `reg-bb-mm-dao`).
     - For each Determinant: `evaluator.evaluate(determinantId, ctx)`.
     - Aggregate per `evaluationStrategy` (per D6) into a
       per-Registration outcome.
   - Build the structured `eligibilityOutcome` JSON (per D15
     shape).
   - On any catch: produce `disposition=indeterminate` with
     `reason=<cause>`.
   - Persist: `formDataDao.saveOrUpdate(formDefId, tableName, rowSet
     with eligibilityOutcome set)`.
   - `evaluator.invalidate(applicationId)` in `finally` block (if
     evaluator non-null).
   - `return stored;`
3. Bind to the application form definition: set `storeBinder.className`
   to `global.govstack.regbb.engine.binder.RegBbApplicationStoreBinder`.
   The form definition is updated via `form-creator-api` (per
   CLAUDE.md HARD RULE), not raw SQL.
4. Activator registration: the storeBinder is a Joget plugin per
   Activator pattern; register in `Activator.java` of `reg-bb-engine`.

Lands in slice 1A.

---

## 7. Open questions

### 7.1 What if `selectedRegistrations` is empty on save?

A draft application that hasn't yet picked a Registration. Default
position: skip evaluation, write `disposition=not_yet_evaluated` to
`eligibilityOutcome` with no per-Registration details. The operator
inbox treats `not_yet_evaluated` as a non-actionable state. Forwarded
to implementation; documented here so the pattern is consistent.

### 7.2 How is correlation id propagated to the storeBinder?

NGINX injects `X-Request-Id` per architecture doc §10.2.4. Joget's
servlet container exposes the request via thread-locals
(`org.joget.apps.app.service.AppUtil.getRequestContextPath` and
similar). The storeBinder reads the header from the request thread-
local. If the storeBinder is invoked outside an HTTP request (admin
batch import), correlation id is null; the audit row records null.
Forwarded to implementation.

### 7.3 What about save events that don't change `dataJson` materially?

Joget's storeBinder is invoked on every Save click. If the citizen
re-saves without changes, evaluation runs anyway. Optimisation: skip
evaluation when `dataHash` (architecture doc §4.3) is unchanged from
the cache. Cache hit on the same `dataHash` is sub-millisecond, so
the optimisation is worth nanoseconds. Forwarded to slice 1D when L2
cache lands; not slice 1A.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` §6.5: no change. Step 943's
`MetaScreenElement.submit` remains the eventual right shape.

`determinant-architecture.md` §5.6 ("Adapter F — Application store
binder (Phase 1 internal use)") is consistent with this ADR; §7.5 is
consistent; §12 Q4 is replaced with a backlink to this ADR.

---

## 9. Decision record

| | |
|---|---|
| Decision | `RegBbApplicationStoreBinder` extending `WorkflowFormBinder`, ships in slice 1A; retires at Phase 3 in favour of `MetaScreenElement.submit` |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins)* |

---

*End of ADR-005.*
