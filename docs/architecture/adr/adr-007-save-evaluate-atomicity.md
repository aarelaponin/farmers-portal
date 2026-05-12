# ADR-007 — Atomicity of save + evaluate

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §7.5, §8, §12 Q6; ADR-005 (save hook), ADR-006 (outcome persistence schema); `jw-community/wflow-core/src/main/java/org/joget/apps/form/dao/FormDataDaoImpl.java` (lines 498, 511, 839); `jw-community/wflow-core/src/main/java/org/joget/apps/app/service/AppServiceImpl.java` (lines 2131–2215, `storeFormData`); `regbb-solution-architecture-spec.md` §8.5 (loud failure discipline) |

---

## 1. Context

### 1.1 The two writes the storeBinder produces

ADR-005 fixed that the Phase 1 hook is a `RegBbApplicationStoreBinder`
extending `WorkflowFormBinder`. Its overridden `store` method does:

1. `super.store(element, rowSet, formData)` — Joget's standard form
   save; persists the row to `app_fd_<application_table>`.
2. Computes the aggregated `eligibilityOutcome` JSON.
3. `FormDataDao.saveOrUpdate(formDefId, tableName, rowSet)` — a
   second persistence call to write the JSON to
   `c_eligibility_outcome` on the same row.

The architecture doc §7.5 names this as "two transactions (the standard
save, then the outcome write)." This ADR resolves whether that two-
transaction shape stays, becomes one transaction, or moves to a
fundamentally different model.

### 1.2 What Joget's session lifecycle actually does

Reading from `jw-community/wflow-core/.../FormDataDaoImpl.java`:

* `saveOrUpdate(Form form, FormRowSet rowSet)` (line 498) and the
  overload `saveOrUpdate(String formDefId, String tableName,
  FormRowSet rowSet)` (line 511) each call `findSessionFactory(...)`
  (line 839), which returns a Hibernate `SessionFactory` for the
  form's table (cached per the `formSessionFactoryCache` field, line
  91 — Joget's "Cache 2" per `CLAUDE.md`).
* The `SessionFactory` produces a session per call. The session opens,
  the row(s) save, the session commits and closes — all inside the
  one method.
* The session is *not* reused across calls; each `saveOrUpdate`
  invocation produces its own session, its own transaction, its own
  commit.

`AppServiceImpl.storeFormData(formDefId, tableName, FormRowSet,
primaryKey)` (line 2131) — the higher-level wrapper used by App
Composer's standard save path — is the same shape: validate, prepare
metadata, `formDataDao.updateSchema(...)`, `formDataDao.saveOrUpdate(...)`,
`FileUtil.storeFileFromFormRowSet(...)`. Not `@Transactional` at the
method level; transaction demarcation lives inside `saveOrUpdate`.

### 1.3 What "one transaction" would require

To wrap both writes in a single transaction, the storeBinder would
have to:

* Open a Hibernate session before invoking `super.store(...)`.
* Configure both Joget's storeBinder `super.store(...)` and the
  follow-up `saveOrUpdate(...)` to participate in that session, not
  open their own.
* Commit explicitly after both writes succeed; rollback both on
  failure of either.

Joget's storeBinder pattern does not expose hooks for caller-supplied
sessions. `FormDataDaoImpl.saveOrUpdate` opens its own session (line
839 `findSessionFactory` returns the cached factory; the factory's
`openSession()` is called inside the persistence path). To inject a
caller-supplied session, the storeBinder would need to bypass the
public API and call lower-level Hibernate primitives directly — a
violation of `CLAUDE.md` HARD RULE ("Joget-native API only").

`AppServiceImpl.storeFormData(...)` could in principle be wrapped in
`@Transactional`, but it is invoked by Joget's framework code (not by
the storeBinder directly); making the framework's call path
transactional is invasive and brittle.

### 1.4 What can go wrong with two transactions

The failure modes:

* **First commits, second fails.** The row is saved with the citizen's
  data; the `eligibilityOutcome` column is NULL or stale. The operator
  inbox renders no disposition for this row.
* **First commits, second never runs** (e.g., evaluator throws,
  storeBinder catches but bug means follow-up write doesn't fire).
  Same as above.
* **First fails, second never runs.** The standard save throws; the
  storeBinder's overridden `store` propagates the throw; Joget rolls
  back its own session; the row is not saved. Citizen sees a save
  failure. No outcome to persist anyway. **Self-consistent.**
* **First fails, second runs anyway** (a bug in the storeBinder's
  control flow). The follow-up tries to update a non-existent row.
  `saveOrUpdate` either upserts (creating an orphan row with only
  `c_eligibility_outcome` set — bad) or no-ops (silent loss — also
  bad).

The first failure mode (commits, second fails) is the realistic risk.
The fourth is a code-review defect.

### 1.5 What ADR-006 fixed about the outcome's never-NULL property

ADR-006 §3.5 specifies: "Indeterminate is structured, not absent. When
the evaluator returns `outcome=error`, the storeBinder writes
`disposition=indeterminate` with a `reason` cause to
`dataJson.eligibilityOutcome`. The row is never NULL on this column."

The discipline is at the application-code level, not the database
level. The discipline holds *if the storeBinder reaches the follow-up
write* — i.e., if both transactions complete. It fails if the
follow-up write is skipped or fails.

### 1.6 The retry-at-load possibility

A hypothetical: when the operator opens an application whose
`eligibilityOutcome` is NULL, the operator review page detects the
NULL and triggers a re-evaluation. The re-evaluation produces an
outcome; the page re-renders with a value. This converts the failure
mode from "permanent NULL" to "lazy populate on next read."

The retry-at-load is structurally available because the evaluator is
deterministic on `(applicationId, dataHash)`: the same input produces
the same output. There is no information lost when the first follow-up
write fails — the data needed to recompute is on the row.

### 1.7 Principles in scope

* **CLAUDE.md HARD RULE** — Joget-native API only; no bypass to
  Hibernate primitives.
* **SRP** — each save commits one logical write; mixing two logical
  writes in one transaction couples them.
* **Honest failure surface** — the failure mode must be visible and
  recoverable, not silent.
* **Phase 1 scale** — slice 1A is a demo, not a production workload;
  Phase 1 production is ≤100 evaluations/day per architecture doc §9.

---

## 2. Decision

The save and the outcome write are **two separate transactions**. The
storeBinder does not attempt to coordinate them via a shared Hibernate
session; each `FormDataDao.saveOrUpdate(...)` call is left to manage
its own session.

The atomicity gap is closed at the application level by **two
disciplines**:

1. **Never-null-on-saved-row** (per ADR-006 §3.5) — the storeBinder's
   second write always runs, including in the evaluator-failure path
   (`disposition=indeterminate`, `reason=<cause>`). The control flow
   is wrapped in `try { ... } catch (Throwable t) { ... }` so the
   second write cannot be skipped by an unexpected throw in the
   evaluation step.
2. **Retry-at-load** — when the operator review form opens an
   application, it detects a NULL or missing `eligibilityOutcome` and
   triggers a synchronous re-evaluation. The retry produces the same
   outcome the original evaluation would have produced (deterministic
   on `(applicationId, dataHash)`); the missing column is populated on
   first read. The retry path is the same evaluator + same aggregator
   the storeBinder uses.

The combination — discipline 1 covers the realistic case (evaluator
throws but storeBinder reaches its catch block); discipline 2 covers
the unrealistic case (storeBinder's process dies between the first
commit and the second) — gives never-null-on-display-time without a
single-transaction mechanism that would violate the Joget-native rule.

---

## 3. Reasoning

### 3.1 Joget's persistence API does not expose the seam needed for one transaction

Reading `FormDataDaoImpl.saveOrUpdate` (line 498/511): each call obtains
a `SessionFactory` via `findSessionFactory(...)` (line 839), opens a
session, persists, commits, closes. The session is local to the call.
There is no public surface for "use this session I've already opened."

Wrapping two calls in one transaction would require either:

* Bypassing `FormDataDao` and calling Hibernate primitives directly —
  violates CLAUDE.md HARD RULE ("Joget-native API only. Never raw SQL
  on Joget metadata or form data... bypass... causes silent data loss
  (Hibernate mapping desync)" — the same desync mechanism applies if
  we bypass the DAO layer).
* Patching Joget core (`FormDataDaoImpl`) to accept a caller-supplied
  session — a fork of upstream code. Out of scope; `CLAUDE.md` explicitly
  privileges native Joget API.

The architectural consequence: Joget's API shape is itself a constraint
in the decision. The constraint says: two transactions is the default;
single-transaction is achievable only by leaving the Joget native path.

### 3.2 SRP — two writes are two responsibilities, not one

The first write persists citizen-authored data (the row). The second
write persists engine-derived data (the outcome). They are different
in source, different in failure semantics (citizen save failure means
"try again"; outcome write failure means "evaluator misbehaved"), and
different in invariants (the citizen save must reflect what the
citizen typed; the outcome write must reflect the rules' verdict on
that data).

Conflating them in one transaction couples their failure modes: a
rule-evaluation bug becomes a save bug from the citizen's perspective
("I clicked Save and got an error, but my data is gone" — actually
it's not gone, but the citizen doesn't know that). The two-
transaction shape preserves the citizen's "I saved" affordance even
when downstream evaluation has problems.

### 3.3 The realistic failure mode is the catch path, which discipline 1 closes

The realistic failure scenario: the evaluator throws during the
aggregation step. The storeBinder's overridden `store` catches the
throw, builds `disposition=indeterminate`, and runs the second write.
Both transactions commit; the row has `disposition=indeterminate`
with `reason=evaluator_internal_error`. The operator sees the
indeterminate banner and triages.

This case requires only that the catch block runs — i.e., the
storeBinder's control flow reaches the second write under all
exception paths from the evaluator. Code review enforces this: the
evaluation block is wrapped in `try { ... } catch (Throwable t) { ...
mark indeterminate ... }`, no early return between the evaluation
and the second write, the second write is in a `finally` block or
inside the same method body after the try/catch.

This discipline is testable: a unit test stubs the evaluator to
throw and asserts that the resulting row has `disposition=indeterminate`.

### 3.4 The unrealistic failure mode is process death, which discipline 2 closes

The remaining failure mode: the JVM dies between the first commit
and the second. The row is saved with NULL `eligibilityOutcome`.

This mode has a concrete recovery path: when the operator review
form (or operator inbox) loads the row, it detects the missing
column and triggers re-evaluation. The re-evaluation is deterministic
(same `(applicationId, dataHash)` produces same result), so the
missing outcome is reconstructed lazily.

The retry-at-load is a generic property of the system, not specific
to atomicity: it also covers the case where the evaluator's logic
changes between save time and review time (a Phase 2 deployment that
adds a fast-path operator), the case where the registry data has
changed since save, and the case where an operator clicks "re-
evaluate this application" manually (architecture doc §4.3 last row
of the invalidation table).

### 3.5 Phase 1 scale makes the two-transaction shape acceptably safe

Per architecture doc §9 estimates, Phase 1 production is ≤100 saves/
day. The window for process death between two writes is sub-second.
The probability of a JVM dying inside that window, on Phase 1 traffic,
across a deployment lifetime, is low. The retry-at-load discipline
(§3.4) makes it self-healing when it does occur. The cost of building
a single-transaction mechanism (Joget API patch or DAO bypass) for
this risk is not justified at this scale.

If Phase 2 scales push this to ≥10k saves/day, the calculation is
re-examined. The architecture doc §13 phasing already plans Phase 3
to retire the storeBinder in favour of `MetaScreenElement.submit`,
where the spec submit's transaction shape is whatever the spec
specifies (§6.5 step 943 doesn't explicitly mandate one transaction;
the same two-transaction analysis applies).

### 3.6 The retire-at-Phase-3 path is unaffected

When Phase 3 ships `MetaScreenElement.submit(applicationId)` (per
ADR-005 §3.4), the spec submit's transaction shape replaces the
storeBinder's. This ADR's two-transaction discipline applies only to
Phase 1's storeBinder hook; the Phase 3 transition is orthogonal.
Spec §6.5 step 943 lists six steps the submit performs; whether they
are one or six transactions is a Phase 3 design question, not this
ADR's.

---

## 4. Alternatives considered and refused

### 4.1 Option A — two transactions; never-null discipline + retry-at-load (the decision in §2)

The chosen option. Stated above.

### 4.2 Option B — one transaction wrapping both writes via a shared Hibernate session

**Refused on Joget-native discipline.** Requires bypassing
`FormDataDao`'s session-per-call shape. Per `CLAUDE.md` HARD RULE,
bypassing the DAO causes Hibernate mapping desync (the two-cache
invalidation trap documented in CLAUDE.md "What goes wrong if you
violate the rule" — the per-form ORM mapping cache rebuild is
triggered only by `AppService.saveX` paths; a DAO-bypassed write
doesn't rebuild the mapping, and silent data loss results).

This option is also fragile under upstream Joget upgrades: any
change to `FormDataDaoImpl`'s session lifecycle (Joget DX 8.2's
cache layer changes, etc.) would break the bypass code. The native
API is the contract; bypassing it is taking on an upgrade burden.

### 4.3 Option C — one transaction by inserting the outcome write inside `super.store`'s session via a Hibernate listener

A `PostInsertEventListener` or similar registered on the form's
`SessionFactory` would fire after the row insert, inside the same
session, allowing the outcome write to participate in the save's
transaction. **Refused on lifecycle invasiveness.** Hibernate event
listeners are global to the `SessionFactory`; registering one to
fire on the application form table affects every save on that table,
not just citizen wizards. Edge cases (admin batch import, App
Composer manual edit) trigger evaluation when they shouldn't (the
storeBinder's bind-to-form-definition pattern was chosen exactly to
avoid this). Refused on lifecycle precision.

### 4.4 Option D — defer the outcome write to a separate event-loop / message queue

Publish a "row saved" event; an asynchronous consumer reads it,
runs evaluation, persists the outcome. **Refused on architecture
weight.** Phase 1 doesn't have a message queue; introducing one for
this case is gross over-engineering. The architectural property
(eventual consistency between row save and outcome) is also worse
than the synchronous two-transaction shape: the operator may load
an application before the consumer has run.

This option becomes interesting at Phase 3+ scale where evaluation
work could be heavy; the architecture doc §9.2 already names the
"async resolution with deferred-toggle pattern" (spec §6.4) for
the per-keystroke conditional UI case. Generalising it to submit-
time is a Phase 3+ concern.

### 4.5 Option E — write the outcome to a transactional DB ledger, reconcile back to the column in a sweeper

Use an outbox pattern: the second write goes to an "outbox" row in
the same transaction as the first, and a sweeper reconciles outbox
to `eligibilityOutcome` column. **Refused on complexity.** This is
the canonical at-least-once-with-eventual-consistency pattern, and
it has its place — but its place is high-throughput, low-latency
distributed systems. Phase 1's scale doesn't justify the
infrastructure.

---

## 5. Consequences

### 5.1 Positive

* Joget-native — every persistence call goes through `FormDataDao`,
  no bypass.
* The storeBinder's failure handling is local: try/catch around the
  evaluation step, follow-up write always runs.
* Retry-at-load is a generic recovery for several failure modes,
  not just the atomicity one. Reusable property.
* Phase 1 scale (≤100 saves/day) makes the realistic cost of the
  two-transaction shape negligible.
* The retire-at-Phase-3 path is unaffected; whatever transaction
  shape `MetaScreenElement.submit` has, this ADR's Phase 1 discipline
  doesn't constrain it.

### 5.2 Negative

* **Atomicity is application-level, not database-level.** A reviewer
  reading the codebase has to understand the discipline (try/catch
  + retry-at-load) to verify correctness. Database transactions are
  more obvious. Documented; reviewed in code review.
* **The retry-at-load path runs the evaluator on operator inbox
  load.** If a citizen's row landed with NULL `eligibilityOutcome`,
  the operator's first inbox view of it triggers an evaluation. At
  scale, this could mean the inbox is sluggish on the rare load.
  Bounded — only happens when the storeBinder's second write was
  skipped, which is the unrealistic failure mode.
* **Two transactions cost slightly more than one.** Two session
  open/close cycles, two commit roundtrips. At Phase 1 scale,
  measurable in milliseconds, not perceptible. At Phase 2+ scale,
  re-examine.
* **The `dataJson.eligibilityOutcome` column can be briefly NULL on
  a row that has just been saved.** A racy reader (a datalist
  refresh between the first commit and the second) could see the
  NULL. The operator inbox renders such rows as "evaluating..." or
  similar; the next inbox refresh shows the actual outcome. Bounded
  to sub-second windows.

### 5.3 Neutral

* The audit table (per ADR-006 slice 1B+) is written by the
  evaluator itself, not by the storeBinder, on every material outcome.
  The audit-write atomicity is a separate question (it is
  best-effort by ADR-006 §6.3); this ADR does not constrain it.
* The retry-at-load discipline is consistent with the operator's
  manual "re-evaluate this application" affordance (architecture
  doc §4.3) — both invoke the same evaluator path. Code reuse.

---

## 6. Implementation outline

Sketch.

1. The `RegBbApplicationStoreBinder.store` method body:
   ```java
   FormRowSet stored = super.store(element, rowSet, formData);  // tx 1
   try {
       // build EvalContext, evaluate Determinants, aggregate
       String outcomeJson = ...
       row.setProperty("eligibilityOutcome", outcomeJson);
       formDataDao.saveOrUpdate(formDefId, tableName, rowSet);   // tx 2
   } catch (Throwable t) {
       LogUtil.error(CLASS_NAME, t, "evaluator failure");
       row.setProperty("eligibilityOutcome", indeterminateJson(t));
       formDataDao.saveOrUpdate(formDefId, tableName, rowSet);   // tx 2 always runs
   } finally {
       if (evaluator != null) evaluator.invalidate(applicationId);
   }
   return stored;
   ```
   The discipline: tx 2 runs in both success and failure paths.
2. Operator review form's load path: when reading the application
   row, check `dataJson.eligibilityOutcome` for absence/NULL/empty;
   if absent, trigger a synchronous re-evaluation via the same
   evaluator interface, persist the result, and continue with the
   freshly-populated outcome.
3. Operator inbox datalist: render a placeholder ("evaluating...")
   for rows with NULL `eligibilityOutcome`. The placeholder is rare
   in practice; on an inbox refresh, the rows have populated values.

Lands in slice 1A (storeBinder) and slice 1A or 1C (operator review
retry-at-load — depending on whether slice 1A's operator surface is
the inbox or the review form).

---

## 7. Open questions

### 7.1 Whether `evaluator.invalidate(applicationId)` should be transactional with tx 2

The cache invalidation call sits in the `finally` block, after both
transactions. If the invalidate call itself fails (e.g., L3
Hazelcast unreachable), the invalidate is a no-op and stale cache
entries persist for L2 TTL (30 min). The next save's
`evaluator.invalidate` retry will succeed; in the meantime, stale
cache reads can be wrong. Bounded to the L2 TTL window. Forwarded
to ADR-008 (cache layering) for confirmation; this ADR notes the
property.

### 7.2 Whether retry-at-load happens on every read or only on operator open

Default position: retry on operator open of the application. A
datalist render of 100 inbox rows does not trigger 100 retries (it
renders the placeholder for NULL rows). Otherwise an operator
loading a 100-row inbox triggers a flood of evaluations. Forwarded
to operator-review implementation guidance.

### 7.3 Whether the audit table records the retry-at-load evaluation

ADR-006's audit-write discipline (slice 1B+) writes a row per
material outcome. A retry-at-load that produces an outcome is a
material outcome and should write a row. The cause field
distinguishes (`reason=retry_at_load_after_storebinder_skip`).
Forwarded to slice 1B implementation.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` — none. The spec doesn't speak
to Phase 1 storeBinder atomicity; the spec submit (§6.5) is Phase 3.

`determinant-architecture.md` §7.5 already names the two-transaction
property; consistent with this ADR. §12 Q6 is replaced with a
backlink to this ADR.

---

## 9. Decision record

| | |
|---|---|
| Decision | Two transactions; never-null-on-saved-row discipline (try/catch wrapping evaluation, second write always runs) plus retry-at-load on operator open |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins)* |

---

*End of ADR-007.*
