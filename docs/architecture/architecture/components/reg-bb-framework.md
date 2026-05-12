# Component SAD — RegBB framework

| Field             | Value                                                                                                                                                                                                                                                                                                                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | RegBB framework (Lesotho instance)                                                                                                                                                                                                                                                                                                                                                                          |
| Document title    | Software Architecture Description (component level)                                                                                                                                                                                                                                                                                                                                                          |
| Version           | 1.0 — DRAFT                                                                                                                                                                                                                                                                                                                                                                                                  |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                                                                   |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                                                                           |
| Related           | Solution-level SAD (`docs/architecture/architecture/solution-architecture.md`); kernel SAD (`mm-form-gen-kernel.md`); subsidy-module SAD (`subsidy-module.md`); ADRs 001–010 in `docs/architecture/adr/`; `docs/architecture/determinant-architecture.md`; `docs/architecture/regbb-conformance-checklist.md`; the GovStack RegBB specification.                                                                                                  |
| What this is      | The component-level SAD for the **opinionated public-services framework**. Implements the parts of the GovStack Registration Building Block specification that go beyond form rendering: service shape, applicability + eligibility evaluation, audit, role-scoped operator review, single-window catalogue, REST endpoints (§6.5 submit, §8 eval), declarative workflow integration via `mm_action`. |
| What this is not  | A description of the form-rendering kernel it depends on (separate SAD). A description of the four 2025 subsidy programmes — those are configuration *content* that the framework runs, documented in the subsidy-module SAD. A how-to for authoring `mm_*` rows.                                                                                                                                            |

---

## 1. Introduction and goals

### 1.1 Purpose and scope

The RegBB framework is the citizen-services-shaped layer of the architecture. It encodes the GovStack Registration Building Block specification's view of public-sector citizen services: **a citizen registers for a service the state offers, eligibility is evaluated against state-held registries, an operator decides, every step is audited.** Its sole user today is the subsidy module, but its design is general — any future citizen service that fits the same shape (drought relief, agricultural extension, livestock disease compensation, …) can be configured on top of the framework without code changes.

In scope:

- **Service metamodel**: `mm_service`, `mm_registration`, `mm_determinant`, `mm_action`, `mm_required_doc`, `mm_benefit`, `mm_role`, `mm_role_screen` — the configuration entities that define a citizen service per RegBB §3 and §6.3 (extended per Lesotho-instance extensions D6, D9, D16, D21).
- **Eligibility / applicability evaluation** per RegBB §4.3 — closed-twenty rule grammar, fast-path AST evaluator, SQL-path evaluator for `$registry.*` references, routing decision per AST analysis (D14), L2 cache, audit emission.
- **Save-time evaluation hook** — `RegBbApplicationStoreBinder` runs eligibility on every application save, persists structured outcome JSON to `dataJson.eligibilityOutcome`, transitions `status` to one of the four dispositions per D15.
- **Operator decision lifecycle** — `RegBbOperatorDecisionBinder` runs on operator save, transitions status, audits, dispatches matching workflows.
- **Workflow dispatch** — declarative `mm_action` → Joget XPDL process bridge. `WorkflowDispatcher` resolves workflow by `(serviceId, registrationId, kind, onStatus)` and starts the process; the framework never authors XPDL itself.
- **Audit** per RegBB §7.4 — `reg_bb_eval_audit` table, one row per evaluation / decision / dispatch, with applicant identifiers (full name, NID, programme) snapshot for cross-list pivot.
- **REST endpoints** per RegBB §6.5 (`/regbb/submit`) and §8 (`/regbb/eval`).
- **Single-window catalogue** *(deferred to Phase 1 close-out)* — `kind=guide` screen renders the list of applicable registrations under a service, evaluating each registration's `applicabilityDeterminantId` against the current applicant.
- **Role-scoped review surface** *(deferred to Phase 2)* — `MetaReviewElement` consumes `mm_role_screen.sectionsJson` to render operator review screens with per-tab readonly mask + decision affordances per RegBB §7.2.

Out of scope:

- Form rendering primitives (kernel — separate SAD).
- XPDL authoring or generation. Workflow processes are authored by sysadmins in Joget's native process designer; the framework dispatches by id.
- IM business rules. The framework's `mm_determinant` rule-management infrastructure is reused by IM (per solution SAD §4.2), but the framework does not own IM-specific rules.
- Identity verification (out of scope until NID verification integration is designed).
- Notification dispatch (a stub `mm_action.kind=notification` slot exists; the SMS/SMTP adapter is not implemented).

### 1.2 Quality goals (component-level)

| Rank | Quality goal                          | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                                                                            |
| ---- | ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | **GovStack RegBB conformance**        | Implements §3 (service shape), §4.3 (rule grammar — closed twenty + AST routing), §6 (screens, conditional UI, submit), §7 (audit, role-scoped review), §8 (eval endpoint) faithfully. Lesotho-instance extensions are documented in `convergence-framework.md` §9 with multi-service evidence per the audit's drift-mode discipline. Target: every spec section either implemented, deferred-with-reason, or extended-with-evidence. |
| 2    | **Sustainability — configuration over code** | New programmes, eligibility rules, benefits, required documents, decision-triggered workflows are configured by writing `mm_*` rows. The framework does NOT need redeployment for content changes. Target: an operator-analyst can ship a new programme in ≤ 1 working day using only admin CRUDs (Phase 1) or a Programme Builder UI (Phase 2). Zero engineering involvement.                                                                            |
| 3    | **Auditability — forensic timeline**  | Every rule evaluation, operator decision, workflow dispatch is captured to `reg_bb_eval_audit` with sufficient context (applicant identifiers, rule source, AST path, outcome, error cause if any) to answer "why did this applicant get this disposition" months later. Operators see the audit through the standard datalist UI. Retention: minimum 12-24 months (subsidy cycle + appeal window). No silent garbage collection. |
| 4    | **UX — operator inbox feels like Joget** | The operator's daily working surface — applications inbox, applicant detail, audit list — is rendered through standard Joget userviews + datalists, with the framework's contributions (eligibility outcome pills, applicant identifier headers, sortable lists, programme-name FK formatters) feeling native. The operator never thinks "this is a metadata thing" — they think "this is the application I'm reviewing."           |
| 5    | **Joget-native integration**          | Workflow integration is via Joget's native XPDL process designer. Auth is via Joget's native user/role membership. Form persistence is via Joget's `FormDataDao`. The framework imports stable Joget extension points only — no internals that change between minor versions. Target: framework runs unchanged on Joget DX 8.1, 8.2, 8.3.                                                                                          |

### 1.3 Stakeholders

| Stakeholder                          | Concerns                                                                                                                                                              |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **GovStack reviewers**               | Spec conformance; that Lesotho extensions are documented as instance-level, not spec-extending without evidence; that the closed-twenty grammar isn't quietly widened. |
| **MAFSN policy / programme leads**   | That programmes can be added or modified without a Java release; that eligibility rules express what the policy intends.                                                |
| **MAFSN operators / case workers**   | That the inbox shows what they need to know; that the audit answers their forensic questions; that decisions stick.                                                    |
| **Engineering team**                 | That the framework is decoupled from any specific module; that new modules don't have to rebuild the framework; that the build → test → ship cycle stays short.        |
| **Future maintainers**               | That the design rationale (ADRs) is recoverable from the repo, not from tribal memory; that decision authority is recorded.                                            |

---

## 2. Architecture constraints

Inherits everything from solution-level SAD §2. Component-specific additions:

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                                              |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F-C1  | **Closed-twenty operators** (ADR-001r2). The fast-path rule grammar accepts twenty operator tokens: `==`, `!=`, `<`, `<=`, `>`, `>=`, `eq`, `neq`, `lt`, `lte`, `gt`, `gte`, `AND`, `OR`, `&&`, `||`, `in`, `notIn`, `not_in`, `notin`. Plus list literals, single/double-quoted strings, numerics, identifiers, and the four reference scopes (`$applicant.*`, `$constant.*`, `$registry.*`, `$service.*`). Adding an operator is an ADR-class decision. |
| F-C2  | **AST routing decides path, not the author** (D12, D14). The author writes one rule. The evaluator analyses the AST: any `$registry.*` reference → SQL path; otherwise → fast path. No operator override. Conservative: when in doubt, route to SQL.                                                                                                                                                  |
| F-C3  | **Audit before persist** (ADR-007). Every eligibility evaluation writes the audit row in the same transaction as the outcome JSON, before any side-effect that consumes the outcome. If audit fails, evaluation fails — the framework refuses to silently consume an unauditable result.                                                                                                                |
| F-C4  | **Never-null discipline** (ADR-007 §2). The two-transaction operator-decision pattern — tx 1 persists the operator's input, tx 2 transitions status + audits — wraps tx 2 in `try/catch`. If tx 2 fails, the operator's input still landed; the operator never sees their work disappear because of a downstream glitch.                                                                                |
| F-C5  | **No XPDL authoring or generation by the framework.** `WorkflowDispatcher` resolves workflows by id (`processDefId` from `mm_action.triggerJson`) and starts them via `WorkflowManager.processStart`. XPDL is authored by sysadmins in Joget's native process designer.                                                                                                                                |
| F-C6  | **Hard-delete semantics for mm_* configuration** (D23). No `isActive` flag, no temporal validity columns. Obsolete codes are replaced by new codes; both rows coexist while the in-flight applications using the old code are processed; the old code is deleted only when no row references it.                                                                                                       |
| F-C7  | **Public framework API isolated.** The packages `engine.api.*` and `engine.evaluator.*` are exported from the bundle's MANIFEST; everything else is bundle-private. Modules that consume the framework do so through `EvalContext`, `EvalResult`, `RoutingEvaluator`, the REST endpoints, and the `mm_*` admin CRUDs — never by reflecting into bundle-private classes.                                  |

---

## 3. Context and scope

### 3.1 Component context

The framework sits on top of the kernel and beneath the modules. The diagram below shows what flows in and out.

```
                          ┌────────────────────────────────────┐
                          │  Subsidy module (configuration)    │
                          │  IM module (rule reuse only)       │
                          │  Operator UI                        │
                          │  Citizen UI                         │
                          └─────────────────┬──────────────────┘
                                            │
                       ┌────────────────────┼────────────────────┐
                       │                    │                    │
                       ▼                    ▼                    ▼
   ┌───────────────────────────────────────────────────────────────────────┐
   │                         RegBB FRAMEWORK                               │
   │                                                                       │
   │  ┌──────────────┐  ┌────────────────┐  ┌─────────────────────────┐   │
   │  │  REST API    │  │ Save-time      │  │ Operator decision       │   │
   │  │ /regbb/eval  │  │ store binder   │  │ store binder + workflow │   │
   │  │ /regbb/submit│  │ (citizen save) │  │ dispatcher (operator)   │   │
   │  └──────┬───────┘  └────────┬───────┘  └────────────┬────────────┘   │
   │         │                   │                       │                 │
   │         └───────────────────┴───────────────────────┘                 │
   │                             │                                         │
   │                             ▼                                         │
   │  ┌─────────────────────────────────────────────────────────────────┐  │
   │  │            RoutingEvaluator                                     │  │
   │  │     ┌─────────────────────┐         ┌──────────────────────┐    │  │
   │  │     │ FastPathEvaluator   │         │ SqlPathEvaluator     │    │  │
   │  │     │ (closed-twenty AST) │         │ (resolves $registry  │    │  │
   │  │     │                     │         │  via JDBC, then      │    │  │
   │  │     └──────┬──────────────┘         │  delegates to fast)  │    │  │
   │  │            │                        └──────┬───────────────┘    │  │
   │  │            └──────────┬─────────────────────┘                   │  │
   │  │                       ▼                                         │  │
   │  │              ┌───────────────────┐                              │  │
   │  │              │ L2 cache (TTL)    │                              │  │
   │  │              │ AuditWriter       │                              │  │
   │  │              └───────────────────┘                              │  │
   │  └─────────────────────────────────────────────────────────────────┘  │
   └───────────────────────┬───────────────────────────────────────────────┘
                           │ uses
                           ▼
   ┌─────────────────────────────────────────────────────────────────────┐
   │              MM-FORM-GEN KERNEL (separate SAD)                       │
   │              MetaScreenElement + MetaWizardElement +                 │
   │              MetaModelDao + mm_screen/mm_field/mm_catalog            │
   └─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Upstream consumers

| Consumer                          | What it uses                                                                                                                                                                                                                          |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Subsidy module (configuration)    | The full framework: services, registrations, determinants, actions, required docs, benefits, roles, role-screens; the storeBinder; the workflow dispatcher; the eval/submit endpoints.                                                |
| IM module                         | Only `mm_determinant` (for IM business rules) and `mm_action` (for IM workflow triggers) — the rule-management infrastructure. **Does not** use `mm_service` for service semantics (uses it only as a namespace bundle id).            |
| Citizen UI                        | The `/regbb/submit` endpoint when the citizen submits via API; the storeBinder when the citizen saves through the wizard UI.                                                                                                          |
| Operator UI                       | The `RegBbOperatorDecisionBinder` on operator-form save; the audit datalist; the eligibility-outcome formatter.                                                                                                                       |
| Programme Builder *(Phase 2)*     | The `mm_*` admin CRUDs for programme authoring.                                                                                                                                                                                       |

### 3.3 Downstream dependencies

| Dependency                              | Used for                                                                                                                                       |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| MM-form-gen kernel                      | `MetaScreenElement`, `MetaWizardElement`, `MetaModelDao`. Strict downward dependency; the kernel never imports the framework.                 |
| Joget `wflow-core`                      | `FormDataDao`, `Form`, `FormRow`, `FormRowSet`, `WorkflowFormBinder`, `Element`, `FormUtil`.                                                  |
| Joget `wflow-wfengine`                  | `WorkflowManager`, `WorkflowAssignment`, `WorkflowProcessResult` — used by `WorkflowDispatcher` to start XPDL processes.                       |
| Joget `wflow-directory`                 | `workflowUserManager` — used to read `currentUsername` for audit rows.                                                                         |
| API Builder (`apibuilder_api`)          | `ApiPlugin`, `ApiPluginAbstract`, `@Operation`, `@Param`, `@Response` — used by `RegBbEvalApi` for the REST endpoints.                         |
| PostgreSQL                              | Reads via `FormDataDao`; writes via `FormDataDao.saveOrUpdate`. Per the hard rule, never raw SQL on Joget-managed tables.                       |

---

## 4. Solution strategy

The framework's design is shaped by ten signed-off ADRs (001–010) and several D-decisions. The integrated story:

### 4.1 Closed-twenty rule grammar (ADR-001r2)

The rule grammar is intentionally tiny. Twenty operator tokens, four reference scopes, three literal types. Authors writing rules learn the whole grammar in 20 minutes. The evaluator implements the whole grammar in ~400 lines of Java. The grammar is small enough to make the **AST routing** decision (fast path vs SQL path) reliable and small enough to make a future port to a different evaluator backend cheap.

Adding an operator to the grammar is an ADR-class decision because every operator widens the routing decision tree, the audit's serialisation surface, and the cognitive load on authors.

### 4.2 Two evaluator paths, one routing decision (D14, ADR-008)

`RoutingEvaluator` analyses the AST and routes:

- **Fast path** — pure `$applicant.*` / `$constant.*` rules. Evaluated in-memory, in microseconds. ~95% of rules.
- **SQL path** — any `$registry.*` reference. The evaluator resolves the registry value via JDBC against the appropriate `app_fd_*` table (joined by national_id), substitutes the literal back into the rule, then delegates to the fast path for the comparison.

The author writes ONE rule; the evaluator picks the path. The audit row records `evaluator='fast'|'sql'` so an analyst can see which path each evaluation took.

L2 cache (TTL-based, default 60s, `ConcurrentHashMap`-backed, key includes rule version) means repeated evaluations of the same rule against the same applicant are cheap. Cache invalidation on operator decision purges that applicant's entries.

### 4.3 Audit before persist (ADR-007)

Every evaluation writes an audit row before the outcome is consumed. If audit fails, evaluation fails — the framework refuses to silently produce an unauditable disposition. The audit table (`reg_bb_eval_audit`) is the forensic timeline operators rely on: applicant identifiers (full name, NID, applied_programme) are snapshot onto each row so cross-list pivots work without joins.

The audit table grows unbounded by design. Retention is a documented operational ritual (`CLAUDE.md` section "Audit retention"), not a silent garbage collector — silent GC would falsify the very evidence the audit exists to provide.

### 4.4 Two-transaction never-null on operator decision (ADR-007 §2)

Operator decision is split:

- **tx 1** — `super.store()` persists the operator's input (`decision`, `decision_score`, `decision_comment`, `decided_at`).
- **tx 2** — status transition + audit row + workflow dispatch.

tx 2 is wrapped in `try/catch`. If anything fails (status patch fails, audit write fails, workflow dispatch fails) the operator's input from tx 1 is already persistent. The operator's work is never lost because of a downstream side-effect.

### 4.5 Declarative workflow integration (D-pending — ADR-011)

The framework does NOT author or generate XPDL. It dispatches to existing Joget processes by id.

The contract: `mm_action` rows declare `(serviceId, registrationId, kind='status_change', triggerJson={"onStatus":"approved","workflowId":"subsidy_2025_approval_notification"})`. When `RegBbOperatorDecisionBinder` transitions an application to status `approved`, `WorkflowDispatcher` queries `mm_action` for matching rows and starts each one's referenced workflow via `WorkflowManager.processStart(processDefId, processInstanceId, variables, currentUser, null, false)`.

The sysadmin authors XPDL processes in Joget's native designer. The framework provides:

- The dispatch trigger.
- A diagnostic `RegBbWorkflowEchoTool` — drop into a workflow tool step to verify the engine→workflow handoff (writes `WORKFLOW_ECHO:<processDefId>` audit rows showing the workflow context the engine sent).

This division — framework dispatches, sysadmin authors processes — keeps RegBB out of the workflow-authoring business and lets workflows leverage Joget's full process designer (fan-out, deadlines, escalation, BeanShell tool steps, etc.) without RegBB having to re-implement any of it.

### 4.6 Lesotho-instance extensions, multi-service evidence (convergence framework §9)

Six `mm_*` extensions are pre-authorised by the audit's multi-service evidence (D6, D9, D16, D21, plus two more in the audit). No other extension lands without a fresh audit. If a phase surfaces an apparent need for a new extension, the team's response is: pause, document, look for a workaround, escalate to the project lead with evidence. "Just one more field" is the signature of drift mode 2.

This discipline is what keeps the framework's metamodel from accreting one-off Lesotho-specific cruft over time.

---

## 5. Building block view (component internals)

### 5.1 Whitebox — packages within the framework

```
global.govstack.regbb.engine.api/         ← public framework API (Export-Package)
   DeterminantEvaluator.java       (82 LoC)  — the evaluator interface
   EvalContext.java                (88 LoC)  — input to evaluation
   EvalResult.java                 (58 LoC)  — output: outcome + score + actionTarget + evaluator + errorCause
   ScreenEvalResult.java           (44 LoC)  — for screen-scope determinants (forthcoming)
   FeeResult.java / RequiredDocResult.java   — for fee-scope and document-scope determinants (forthcoming)
   RegBbEvalApi.java              (315 LoC)  — REST endpoints (/regbb/eval, /regbb/submit)

global.govstack.regbb.engine.evaluator/  ← public framework evaluator (Export-Package)
   RoutingEvaluator.java          (188 LoC)  — picks path per AST analysis
   FastPathEvaluator.java         (406 LoC)  — closed-twenty interpreter
   SqlPathEvaluator.java          (271 LoC)  — resolves $registry.*, delegates to fast

global.govstack.regbb.engine.audit/      ← bundle-private
   AuditWriter.java               (172 LoC)  — writes reg_bb_eval_audit rows

global.govstack.regbb.engine.cache/      ← bundle-private
   L2Cache.java                   (132 LoC)  — TTL ConcurrentHashMap

global.govstack.regbb.engine.binder/     ← bundle-private (registered as Joget plugins via Activator)
   RegBbApplicationStoreBinder.java (507 LoC) — citizen save → eligibility eval → outcome JSON + status
   RegBbOperatorDecisionBinder.java (251 LoC) — operator save → status transition + audit + dispatch

global.govstack.regbb.engine.workflow/   ← bundle-private
   WorkflowDispatcher.java        (234 LoC)  — mm_action → Joget process by id
   RegBbWorkflowEchoTool.java     (203 LoC)  — diagnostic tool step
```

The framework adds ~3300 LoC over the kernel. About half is the evaluator stack, the other half divides between binders, dispatcher, audit, and the REST layer.

### 5.2 Public framework API (Export-Package)

The two exported packages — `engine.api` and `engine.evaluator` — define the framework's surface area:

**`EvalContext`** — the input to any evaluation:

```java
public final class EvalContext {
    public final String                  applicationId;
    public final Map<String, Object>     data;             // applicant data (typically the form row)
    public final String                  currentUsername;
    public final String                  serviceId;
    public final String                  registrationId;
    // builder pattern: EvalContext.builder().applicationId(...).data(...).build()
}
```

**`EvalResult`** — the output:

```java
public final class EvalResult {
    public enum Outcome { TRUE, FALSE, NULL, ERROR }
    public final Outcome   outcome;
    public final Double    score;          // null for boolean-only rules
    public final String    actionTarget;   // for action-scope determinants
    public final String    evaluator;      // "fast" | "sql" | "operator" | "workflow_echo"
    public final String    errorCause;     // populated when outcome=ERROR
}
```

**`RoutingEvaluator`** — entry point for any consumer:

```java
public class RoutingEvaluator {
    public EvalResult evaluate(String determinantCode, EvalContext ctx);
    public void invalidate(String applicationId);  // cache flush on decision
}
```

**`RegBbEvalApi`** — REST endpoints registered via `ApiPluginAbstract`:

| Endpoint                                  | Method | Purpose                                                                                                                                                                                                  |
| ----------------------------------------- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/jw/api/regbb/eval`                      | POST   | RegBB §8. Pure evaluation: caller supplies determinant code + applicant data; framework returns outcome. Audited, cached, no persistence.                                                                  |
| `/jw/api/regbb/submit`                    | POST   | RegBB §6.5. Persist application row, evaluate eligibility on the applied programme's `applicabilityDeterminantId`, write outcome JSON + initial status, return application id + outcome.                  |

Both require Joget API Builder credentials (`api_id` + `api_key`) per the platform's auth model.

### 5.3 Evaluator stack — RoutingEvaluator → Fast / SQL → cache + audit

**`RoutingEvaluator.evaluate(String determinantCode, EvalContext ctx)`** is the single entry point. Flow:

1. **L2 cache lookup.** Key = `(determinantCode, applicationId, ruleVersion, dataHash)`. Hit → return cached EvalResult.
2. **Resolve determinant** via `MetaModelDao.findDeterminantByCode(code)`. If not found → return ERROR with cause `unknown_determinant:<code>`.
3. **Parse rule** via `FastPathEvaluator.parse(ruleSource)`. Cache the AST per determinant code + ruleVersion.
4. **AST analysis.** Walk the AST for `$registry.*` references.
   - None → fast path.
   - Any → SQL path.
5. **Dispatch to evaluator.** Fast or SQL. Each returns an `EvalResult`.
6. **Write audit row** via `AuditWriter.write(determinantCode, ctx, result, ruleSource, evalStartedAt, evalDurationMs)`.
7. **Cache and return.**

**`FastPathEvaluator`** is a hand-written recursive-descent parser + interpreter for the closed-twenty grammar. Roughly:

```
expr      ::= or_expr
or_expr   ::= and_expr ( ('OR' | '||') and_expr )*
and_expr  ::= cmp_expr ( ('AND' | '&&') cmp_expr )*
cmp_expr  ::= ref ( ('==' | '!=' | '<' | '<=' | '>' | '>=' |
                     'eq' | 'neq' | 'lt' | 'lte' | 'gt' | 'gte' |
                     'in' | 'notIn' | 'not_in' | 'notin') value )?
value     ::= literal | list_literal | ref
ref       ::= '$' identifier ('.' identifier)*  |  identifier         (bare = $applicant)
literal   ::= number | quoted_string
list_literal ::= '[' value (',' value)* ']'
```

The interpreter walks the AST against `EvalContext.data`. Three-valued logic: missing data → NULL (not FALSE); type mismatch → ERROR; otherwise TRUE or FALSE.

**`SqlPathEvaluator`** handles `$registry.*` references:

1. Walk the AST for each `$registry.<entity>.<field>` reference.
2. Look up the registry table mapping (e.g. `farmer` → `app_fd_farmerbasicinfo`).
3. Issue a JDBC `SELECT c_<field> FROM app_fd_<table> WHERE c_national_id = ?` for the applicant's NID.
4. Substitute the resolved literal back into the rule source.
5. Delegate to `FastPathEvaluator.evaluate(modifiedSource, ctx)`.

This is a deliberately narrow integration — SQL path doesn't try to push the comparison down to SQL (no `WHERE c_field == 'lowlands'`); it just resolves references and re-runs the fast path. Easier to audit, easier to test, no risk of SQL injection because rule source is never concatenated into the WHERE clause.

### 5.4 Save-time hook — RegBbApplicationStoreBinder

Wired as the `storeBinder` on the application form (`subsidyApplication2025`). Flow:

1. **tx 1** — `super.store()` persists the application row (citizen's input).
2. **Read applied_programme** off the saved row.
3. **Look up registration** via `MetaModelDao.findRegistrationByCode(programme)`. Read its `evaluationStrategy` and `passingThreshold`/`minimumScore`.
4. **Aggregate rules.**
   - `evaluationStrategy='all_must_pass'` (D6): walk all `mm_determinant` rows where `registrationId=programme AND scope='applicability'`, evaluate each, fail-on-first-FALSE, propagate NULL/ERROR per ADR-007.
   - `evaluationStrategy='score_based'` (D6): evaluate all rules, sum scores of TRUE rules, compare to threshold; below `minimumScore` → `eligibility_failed_mandatory`; between minimum and threshold → `eligibility_pending_review`; at-or-above → `eligibility_passed`.
5. **Persist outcome JSON** to `dataJson.eligibilityOutcome` with the full breakdown (rules evaluated, outcomes, scores, failed rule, fail message).
6. **Set initial status** per disposition (D15): `eligibility_passed` → `auto_approved`; `eligibility_failed_mandatory` → `auto_rejected`; `eligibility_pending_review` → `pending_operator_review`; `indeterminate` → `pending_data_clarification`.
7. **Cache invalidate** for this applicationId.

This is the moment the framework turns citizen-submitted data into a structured eligibility verdict the operator inbox can render and the audit can answer "why" about.

### 5.5 Operator decision hook — RegBbOperatorDecisionBinder

Wired as the `storeBinder` on the operator review form (`subsidyApplicationOperator2025`). Flow:

1. **tx 1** — `super.store()` persists the operator's input (`decision`, `decision_score`, `decision_comment`, `decided_at`).
2. **tx 2** (wrapped in try/catch — never-null discipline):
   - Map `decision` → status (`approve` → `approved`; `reject` → `rejected`; `send_back` → `sent_back`).
   - Patch the application row's status via `FormDataDao.saveOrUpdate`.
   - Write `OPERATOR_DECISION:<decision>` audit row.
   - Dispatch matching `mm_action` workflows via `WorkflowDispatcher`.

If tx 2 fails (any step), the operator's decision is still persistent from tx 1; an error is logged; no exception propagates to break the user's UI flow.

### 5.6 Workflow dispatcher — declarative bridge

`WorkflowDispatcher.dispatch(serviceId, registrationId, kind, applicationId, eventData, applicantData, currentUser)`:

1. Query `mm_action` for rows matching `(serviceId, registrationId, kind)`. (NULL `registrationId` means service-wide.)
2. For each matching row, parse `triggerJson` for `onStatus` filter; if `eventData.onStatus` doesn't match, skip.
3. Read `triggerJson.workflowId`. Resolve via `WorkflowManager.getConvertedLatestProcessDefId` to the latest published version.
4. Build workflow variables from `applicantData` + `eventData`.
5. Call `WorkflowManager.processStart(processDefId, applicationId, variables, currentUser, null, false)` — application id becomes the process instance id (so the workflow can find the application row by recordId).
6. Write `WORKFLOW_DISPATCH:<actionCode>` audit row with `workflow_started:<workflowId> pid=<processInstanceId>`.

Workflow start failures don't break the operator's decision — they're logged and audited as a `WORKFLOW_DISPATCH_FAILED` row.

### 5.7 Audit writer

`AuditWriter.write(rule, EvalContext, EvalResult, ruleSource, evalStartedAtMs, evalDurationMs)`:

- Builds a `FormRow` with all columns of `reg_bb_eval_audit`.
- Snapshots applicant identifiers (`full_name`, `national_id`, `applied_programme`) from `EvalContext.data` so the audit list shows them without joins.
- Sets `dateCreated` and `dateModified` explicitly (Joget's `FormDataDao` doesn't auto-populate metadata when caller pre-sets the row id).
- `FormDataDao.saveOrUpdate("reg_bb_eval_audit", "reg_bb_eval_audit", rowSet)`.

The schema:

```
id (UUID), dateCreated, dateModified, createdBy, modifiedBy,
c_application_id, c_determinant_code, c_outcome (TRUE|FALSE|NULL|ERROR),
c_evaluator (fast|sql|operator|workflow_echo),
c_eval_started_at (epoch ms), c_eval_duration_ms,
c_rule_version, c_service_id, c_correlation_id,
c_inputs_json, c_outputs_json, c_error_cause,
c_current_username,
c_applicant_name, c_national_id, c_applied_programme,
c_service_version
```

### 5.8 Framework metamodel forms

The framework adds ten `mm_*` form definitions on top of the kernel's three:

| Form               | Purpose                                                                                                                                                                                                                                  |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `mm_service`       | Top-level service. Code (e.g. `SUBSIDY_2025`), name, owning institution, audit config.                                                                                                                                                    |
| `mm_registration`  | A programme under a service. Code, name, applicabilityDeterminantId, evaluationStrategy (`all_must_pass`/`score_based`), passingThreshold, minimumScore, acceptanceWindowFrom/To.                                                       |
| `mm_determinant`   | A rule. Code, scope (`applicability`/`eligibility`/`field`/`screen`/`document`/`fee`), ruleType (`inclusion`/`exclusion`), ruleJson (the rule source), score, allowSlowPath, failMessage.                                                |
| `mm_action`        | A side effect. Code, kind (`status_change`/`bot_pull`/`notification`), triggerJson, configJson, fieldMappings (for bot_pull).                                                                                                            |
| `mm_required_doc`  | A document type. Code, requiredness, acceptedTypes, maxSizeBytes, registrationId (programme-specific) or NULL (service-wide).                                                                                                            |
| `mm_benefit`       | What a programme delivers. Code, benefitType (`cash`/`voucher`/`item`), value, unit, formula.                                                                                                                                            |
| `mm_role`          | An operator role. Code, name, permissions.                                                                                                                                                                                                |
| `mm_role_screen`   | The operator review screen layout for a (role, service) pair. sectionsJson (a JSON array of `{screenCode, readonly}` per RegBB §7.2).                                                                                                   |
| `mm_field` (kernel)| Used by the framework via `visibilityDeterminantId` / `requirednessDeterminantId` for §6.4 conditional UI.                                                                                                                                |

Every framework form is deployed via `form-creator-api`; admin CrudMenus expose them in the operator userview's MM-Configuration category.

### 5.9 Activator service registrations

```java
context.registerService(Element.class.getName(),     new MetaScreenElement(),         null);  // kernel
context.registerService(Element.class.getName(),     new MetaWizardElement(),         null);  // kernel
context.registerService(FormBinder.class.getName(),  new RegBbApplicationStoreBinder(), null);
context.registerService(FormBinder.class.getName(),  new RegBbOperatorDecisionBinder(), null);
context.registerService(ApplicationPlugin.class.getName(), new RegBbWorkflowEchoTool(), null);
context.registerService(ApiPlugin.class.getName(),   new RegBbEvalApi(),              null);
```

Six registrations: two kernel elements, two storeBinders, one workflow tool, one API plugin. Joget OSGi loads each one as a separate service consumable by configuration.

---

## 6. Runtime view

### 6.1 Application submission — citizen → eligibility outcome

```
Citizen UI: clicks Save on subsidyApplication2025 wizard
   │
   ▼
HTTP POST .../subsidyApplication2025_crud
   │
   ▼
Joget FormService.executeFormStoreBinders
   │
   ▼
RegBbApplicationStoreBinder.store(element, rowSet, formData)
   │
   ├─[tx 1]─ super.store(element, rowSet, formData) → rowSet persisted
   │
   ├──────── Read row from FormRowSet → applicationId, applied_programme, applicant data
   │
   ├──────── MetaModelDao.findRegistrationByCode(programme) → evaluationStrategy
   │
   ├──────── Build EvalContext { applicationId, data: row, currentUsername, ... }
   │
   ├──────── aggregate(programme, ctx):
   │           strategy = "all_must_pass" or "score_based"
   │           for each mm_determinant where registrationId=programme AND scope='applicability':
   │              RoutingEvaluator.evaluate(determinantCode, ctx)
   │                ├─ L2 cache check
   │                ├─ AST analysis → fast or SQL
   │                ├─ FastPathEvaluator.evaluate / SqlPathEvaluator.evaluate
   │                ├─ AuditWriter.write(determinantCode, ctx, result, source, started, duration)
   │                └─ return EvalResult
   │           aggregate per strategy → disposition
   │
   ├─[tx 2]─ FormDataDao.saveOrUpdate to patch eligibility_outcome (JSON) + status
   │           (failure logged but does not propagate — citizen's row already saved in tx 1)
   │
   └──────── L2Cache.invalidate(applicationId)
```

End state in the database:

| Table                            | Key columns updated                                                                                                                                                |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `app_fd_subsidy_app_2025`        | `c_<everything from the form>`, `c_eligibility_outcome` (JSON), `c_status` (e.g. `pending_operator_review`)                                                          |
| `app_fd_reg_bb_eval_audit`       | One row per evaluated determinant: `c_determinant_code`, `c_outcome`, `c_evaluator`, `c_eval_duration_ms`, `c_applicant_name`, `c_national_id`, `c_applied_programme` |

### 6.2 Operator decision — approve → workflow dispatch

```
Operator UI: opens application, picks "Approve", clicks Save
   │
   ▼
HTTP POST .../subsidyApplicationOperator2025_crud
   │
   ▼
RegBbOperatorDecisionBinder.store(element, rowSet, formData)
   │
   ├─[tx 1]─ super.store() → decision/decision_score/decision_comment/decided_at persisted
   │
   ├─[tx 2 — try/catch]
   │     │
   │     ├──── statusForDecision("approve") → "approved"
   │     ├──── FormDataDao.saveOrUpdate to patch c_status = "approved"
   │     ├──── AuditWriter.write("OPERATOR_DECISION:approve", ctx, fakeResult, ...)
   │     │
   │     └──── WorkflowDispatcher.dispatch(serviceId="SUBSIDY_2025", registrationId="PRG_2025_001",
   │                                       kind="status_change", applicationId, 
   │                                       eventData={onStatus:"approved"},
   │                                       applicantData=row, currentUser)
   │              │
   │              ├──── Query mm_action where serviceId="SUBSIDY_2025" AND kind="status_change"
   │              ├──── Match by triggerJson.onStatus="approved" → finds NOTIFY_APPROVAL row
   │              ├──── Resolve workflowId → "subsidy_2025_approval_notification"
   │              ├──── WorkflowManager.processStart(defId, applicationId, vars, user, null, false)
   │              └──── AuditWriter.write("WORKFLOW_DISPATCH:NOTIFY_APPROVAL", ...,
   │                                     errorCause="workflow_started:... pid=...")
   │
   └─ tx 2 outcome: status flipped, audit rows written, workflow running
      (any failure in tx 2 logged but does not propagate)
```

The XPDL workflow (`subsidy_2025_approval_notification`) is now running. What it does is up to the sysadmin who authored it — typically: send an SMS to the citizen, post a JSON event to a notification microservice, update an entitlement record in IM. The framework neither knows nor cares; it handed off cleanly.

### 6.3 Pure evaluation — REST /regbb/eval

```
POST /jw/api/regbb/eval
Headers: api_id, api_key
Body: { "determinantCode": "DET_LOWLANDS", "applicantData": { "agro_zone": "lowlands" } }
   │
   ▼
RegBbEvalApi.eval(requestBody)
   │
   ├──── Parse JSON, extract determinantCode + applicantData
   ├──── Build EvalContext from applicantData
   ├──── new RoutingEvaluator().evaluate(determinantCode, ctx)
   │       (audit row written as side effect)
   └──── Return JSON { outcome, evaluator, status:"success", determinantCode }
```

Used today for: smoke testing rules during development, dry-running a programme before publishing it, building external citizen UIs (mobile app, USSD service) that want eligibility verdicts without going through the full submit flow.

---

## 7. Deployment view

The framework is **packaged inside** the same `reg-bb-engine` OSGi bundle as the kernel. Build/deploy mechanics are the same:

```
plugins/reg-bb-engine/
├── src/main/java/global/govstack/regbb/engine/
│   ├── element/   ← KERNEL (separate SAD)
│   ├── dao/       ← KERNEL (with framework methods mixed in — refactor target)
│   ├── api/             ← framework: RegBbEvalApi, EvalContext, EvalResult, etc.
│   ├── audit/           ← framework: AuditWriter
│   ├── binder/          ← framework: storeBinders
│   ├── cache/           ← framework: L2Cache
│   ├── evaluator/       ← framework: RoutingEvaluator, FastPathEvaluator, SqlPathEvaluator
│   ├── workflow/        ← framework: WorkflowDispatcher, RegBbWorkflowEchoTool
│   ├── Activator.java
│   └── Build.java
└── reg-bb-engine-8.1-SNAPSHOT.jar
```

**Bundle MANIFEST.MF (framework-relevant):**

```
Bundle-SymbolicName: global.govstack.reg-bb-engine
Export-Package: global.govstack.regbb.engine.api;version="8.1.0",
                global.govstack.regbb.engine.evaluator;version="8.1.0"
Import-Package: org.joget.workflow.model,
                org.joget.workflow.model.service,
                org.joget.api.annotations, org.joget.api.model, org.joget.api.service,
                ...
DynamicImport-Package: *
```

**API Builder configuration** (one-time, sysadmin):

The `/regbb/eval` and `/regbb/submit` endpoints are exposed via API Builder. The sysadmin creates an API Builder API named `regbb` (id `API-168e3678-...`), points it at the `RegBbEvalApi` plugin class, and registers an api-key/api-id pair. URLs become `/jw/api/regbb/eval` and `/jw/api/regbb/submit`.

---

## 8. Cross-cutting concepts

### 8.1 Audit retention

Documented in `CLAUDE.md`. Short version:

- Live retention: 12–24 months (subsidy cycle + appeal window).
- Quarterly archival ritual: copy older rows to a cold archive, delete from live table, keep the live table small enough for the operator UI to render.
- Optional index when row count > 100k: `CREATE INDEX idx_reg_bb_eval_audit_app_id ON app_fd_reg_bb_eval_audit (c_application_id, datecreated DESC)`.
- One-shot prune SQL provided; no automated cron-style automation (Convention over Invention — operators get more value from a documented quarterly ritual).

### 8.2 Cache layering (ADR-008)

Three caches:

- **L1** — parser AST cache (per determinant code + ruleVersion). Lifetime: process. Lookup: ConcurrentHashMap.
- **L2** — evaluation result cache (per determinant + applicationId + ruleVersion + dataHash). TTL-based, default 60 seconds, configurable via `regbb.eval.l2.ttlSeconds` system property.
- **L3** *(deferred — not implemented Phase 1)* — capability adapter result cache (per `$registry.<entity>.<field>` lookup). Would benefit programmes whose eligibility hits the same registry row many times.

L1 and L2 invalidate on operator decision (clears that applicationId). No invalidation on metamodel change today — restart picks up rule edits. A future hot-reload signal would invalidate L1 on `mm_determinant` save.

### 8.3 Cross-bundle reflection (when framework calls the kernel)

The kernel and framework live in the same bundle today, so direct imports work. If/when the kernel splits out, the framework would call kernel APIs across an OSGi bundle boundary and the kernel's `Class<?>` identity would be per-classloader. CLAUDE.md documents the pattern:

> Pin the target's classloader explicitly. Every `Class<?>` lookup must go through B's classloader, not A's. `Class.forName(name)` resolves through the caller's classloader, returning the wrong `Class<?>` and causing `NoSuchMethodException` even though the method exists.

The pattern is in the repo already (used historically when the eval endpoint was in form-creator-api before being moved into reg-bb-engine).

### 8.4 Concurrency model (ADR-009)

- **Read-mostly metamodel.** `mm_*` rows change rarely; most evaluations are reads. No locking needed — Joget's `FormDataDao` reads are HQL session reads, eventually consistent.
- **Save-time evaluation is single-threaded per application.** Joget's storeBinder pipeline runs serially per submit request. No two threads ever concurrently evaluate eligibility for the same applicationId.
- **Audit writes are serialised through Joget's session.** No deduplication needed for the (rare) case of double-click duplicate submits — Joget's primary-key constraint catches it.
- **L2 cache is a thread-safe ConcurrentHashMap.** Concurrent evaluators of different applications proceed in parallel; concurrent evaluators of the same application share the cached result.

### 8.5 Forensic recovery — operator says "the system gave the wrong answer"

The audit table is the forensic mechanism. Procedure:

1. Find the application: `SELECT id FROM app_fd_subsidy_app_2025 WHERE c_national_id = '...'`.
2. List all audit rows: `SELECT * FROM app_fd_reg_bb_eval_audit WHERE c_application_id = '<id>' ORDER BY datecreated`.
3. Each row carries: rule code, rule source (in `c_inputs_json`), outcome, evaluator path, error cause if any, applicant identifiers, timestamp.
4. Re-run the evaluation: POST to `/regbb/eval` with the same applicantData → reproducible outcome.
5. If the re-run differs, the metamodel changed between the original evaluation and now (rule edited). The audit's `c_rule_version` distinguishes which version produced which outcome.

### 8.6 Telemetry / observability

The framework writes:

- Audit rows (forensic, persistent).
- Joget `LogUtil.info/warn/error` log lines per evaluation, decision, dispatch.
- L2 cache hit/miss counts (logged at DEBUG; not surfaced to a metrics endpoint).

A future observability story (Prometheus metrics endpoint, distributed tracing) is not in Phase 1 scope.

---

## 9. Architecture decisions

| ADR / D-entry           | Title                                                                                          | Status                                                   |
| ----------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| **ADR-001r2**           | Rule grammar canon — DSL canonical, closed twenty as fast-path subset                          | Accepted                                                 |
| **ADR-002r2**           | mm_dao placement — single-bundle layout                                                        | Accepted (revised after a single-bundle refactor)        |
| **ADR-003**             | Rule storage shape — DSL source verbatim in `mm_determinant.ruleJson`                          | Accepted                                                 |
| **ADR-004**             | EvalContext data shape                                                                         | Accepted                                                 |
| **ADR-005**             | Phase 1 save hook — storeBinder on application form                                            | Accepted                                                 |
| **ADR-006**             | Outcome persistence schema                                                                     | Accepted                                                 |
| **ADR-007**             | Save-evaluate atomicity — never-null discipline + audit before persist                         | Accepted                                                 |
| **ADR-008**             | Cache layering — L1/L2/L3                                                                      | Accepted (L1 + L2 implemented; L3 deferred)              |
| **ADR-009**             | Concurrency model                                                                              | Accepted                                                 |
| **ADR-010**             | Publisher validation depth                                                                     | Accepted                                                 |
| **ADR-011** *(forthcoming)* | No XPDL generation by RegBB                                                              | Pending. Codifies the boundary the framework already respects. |
| **D6** — 2026-04-28     | Scoring (`mm_registration.evaluationStrategy`) — adopt as 6th Lesotho-instance extension       | Accepted                                                 |
| **D9** — 2026-04-28     | `repeating_group` widget persistence — inline JSON in dataJson                                 | Accepted (kernel concern; framework consumes)            |
| **D14** — 2026-04-28    | AST routing — conservative; any `$registry.*` reference routes to SQL                          | Accepted                                                 |
| **D15** — 2026-04-28    | `pending_review` band — no new status enum; structured outcome on dataJson                     | Accepted                                                 |
| **D16** — 2026-04-28    | `attributesJson` and `acceptanceWindow` placement — `mm_registration`, not `mm_service`        | Accepted                                                 |
| **D21** — 2026-04-29    | Cascading catalog filter (Lesotho extension to mm_field)                                       | Accepted                                                 |
| **D22** — 2026-04-29    | `mm_catalog` is the Master Data implementation; RegBB §7 doesn't formalise it                  | Accepted                                                 |
| **D23** — 2026-04-29    | Hard-delete semantics for mm_* configuration; temporal validity deferred                        | Accepted                                                 |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                              | Source       | Stimulus                                                                | Artifact                  | Environment    | Response                                                                                                                | Measure                                          |
| --------------------------------- | ------------ | ----------------------------------------------------------------------- | ------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| **Spec conformance — §6.5 submit**| External API client | POSTs `applicationData` with applied_programme + applicant fields | `RegBbEvalApi.submit`     | Production     | Persists row, evaluates eligibility, returns outcome JSON                                                              | 100% conformance with RegBB §6.5 contract        |
| **Configuration over code**       | Programme designer | Adds a new programme by writing 1 mm_registration + 4 mm_determinant + 3 mm_required_doc + 2 mm_benefit rows | Framework                 | Phase 2 op     | Programme is fully functional (citizen can apply, operator can review, eligibility evaluates)                          | Time-to-add-programme ≤ 1 working day            |
| **Audit completeness**            | Auditor      | Asks "what was evaluated for application X"                             | reg_bb_eval_audit         | Production     | Every evaluation, decision, dispatch is in the table with applicant ids, rule, outcome, timestamp, evaluator path     | 100% — no operations bypass audit                |
| **Operator UX — eligibility outcome visible** | Operator | Opens the application list                                       | Operator userview         | Production     | Sees applicant name, NID, programme, disposition pill, score, failed rule per row                                       | Operators don't need to open each row to triage  |
| **Workflow integration — sysadmin authors process, framework dispatches** | Sysadmin | Authors a Joget XPDL process in the native designer; configures mm_action row | WorkflowDispatcher | Production | Decision triggers process; framework writes WORKFLOW_DISPATCH audit | Zero RegBB code change for new workflow integration |

### 10.2 ISO/IEC 25010 mapping

| Goal                          | 25010 characteristic                                                |
| ----------------------------- | ------------------------------------------------------------------- |
| Spec conformance              | Functional suitability — Functional correctness + completeness      |
| Configuration over code       | Maintainability — Modifiability                                     |
| Audit completeness            | Reliability — Availability of evidence; Security — Accountability   |
| Operator UX                   | Interaction capability — User error protection                      |
| Joget-native integration      | Compatibility — Interoperability                                    |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                                                                                                       | Severity | Mitigation                                                                                                                                                                                                                                                              |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F-R1  | **Single-domain validation only.** Spec conformance has been demonstrated against subsidies (one domain). The framework's portability to other public-sector citizen services is not yet proven. Convergence-framework's "second-domain test" was deferred (IM is not citizen-services-shaped).                  | Medium   | Honest documentation: solution-level SAD §4.1 names this explicitly. Future second-domain validation when a fitting domain emerges (drought relief, agricultural extension, livestock disease compensation).                                                            |
| F-R2  | **MetaReviewElement (RegBB §7.2) not yet wired.** Operator review today uses `MetaWizardElement` with a wizard-level `readonly` flag. Full per-role `sectionsJson` rendering (citizen tabs read-only by default; decision tab editable per role) is Phase 2.                                                  | Medium   | Phase 2 deliverable. Backwards-compatible — current operator review keeps working; MetaReviewElement is additive.                                                                                                                                                       |
| F-R3  | **IM-connector capability registry per spec §3.2 not yet generalised.** SqlPathEvaluator has hand-coded mapping for `$registry.farmer.*`. The spec calls for a registered capability adapter pattern. Adding `$registry.parcels.*` today requires editing SqlPathEvaluator.                                       | Medium   | Phase 1 close-out backlog. Generalise to a `CapabilityAdapter` interface registered via OSGi service; programmes' rules then reference any registered capability.                                                                                                       |
| F-R4  | **Single-window catalogue (kind=guide screen with applicability evaluation per registration) not yet implemented.** Today the citizen picks programme via a SelectBox on the Identity tab — a workaround. RegBB §6.1.6 describes the catalogue as the spec citizen entry point.                                | Medium   | Phase 1 close-out backlog. Implementation is straightforward (synthesise an HTML list with per-row applicability evaluation); the bigger work is the UX (showing why a programme is unavailable).                                                                       |
| F-R5  | **No publisher with full validation depth.** ADR-010 calls for a publisher that validates programme content (rules parse, references resolve, benefits are well-formed) before activation. Today the seeder does light validation only.                                                                          | Low      | Phase 2 deliverable. Programme Builder admin UI can host the publish gate.                                                                                                                                                                                              |
| F-R6  | **Rule version scheme is the row's `dateModified`.** This is fine for forensic audit (the timestamp is in `c_rule_version`) but doesn't support deliberate rule versioning ("publish v2 of DET_X without affecting v1 in-flight applications"). Coupled with the larger versioned-metamodel question.            | Medium   | Future ADR. Out of Phase 1 scope. The audit row carries enough information for reasoning today.                                                                                                                                                                         |
| F-R7  | **`MetaModelDao` mixes kernel and framework methods.** Kernel methods (`findScreenByCode`, `listFieldsForScreen`, `findCatalogByCode`) live alongside framework methods (`findServiceByCode`, `findRegistrationByCode`, `findDeterminantByCode`, `listDeterminantsForRegistration`).                                | Low      | When the kernel splits into its own bundle, framework methods move to `RegBbMetaDao` in the framework bundle. Bookmarked for the split.                                                                                                                                |
| F-R8  | **Notification dispatch not implemented.** `mm_action.kind=notification` is reserved in the metamodel; the SMS/SMTP adapter doesn't exist. Programmes that want a citizen-facing notification today route through a custom Joget process step.                                                                  | Low      | Phase 2+ deliverable. Could be implemented as a standalone OSGi bundle that consumes `mm_action.kind=notification` rows; framework's only contribution is dispatch.                                                                                                     |

---

## 12. Glossary (framework-specific)

| Term                          | Definition                                                                                                                                                                                                                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Applicability**             | Whether a programme is *open* to a given applicant. Evaluated by `mm_registration.applicabilityDeterminantId`. Failed applicability = the programme is hidden in the catalogue or marked unavailable.                                                                  |
| **Eligibility**               | Whether an applicant *meets the criteria* of a programme they're applying to. Evaluated by all `mm_determinant` rows where `registrationId=programme AND scope='applicability'` (Phase 1 collapses applicability and eligibility scopes). Failed eligibility = `eligibility_failed_mandatory` disposition. |
| **Disposition**               | The outcome of eligibility evaluation: `eligibility_passed`, `eligibility_failed_mandatory`, `eligibility_pending_review`, `indeterminate`.                                                                                                                            |
| **Status**                    | The application's lifecycle state: `auto_approved`, `auto_rejected`, `pending_operator_review`, `pending_data_clarification`, `approved`, `rejected`, `sent_back`. Transitions are governed by `RegBbApplicationStoreBinder` (initial) and `RegBbOperatorDecisionBinder` (operator). |
| **Determinant**               | A boolean predicate authored as an `mm_determinant` row with a rule expression in the closed-twenty grammar.                                                                                                                                                          |
| **Routing decision**          | The choice between fast path and SQL path, made by `RoutingEvaluator` per AST analysis (any `$registry.*` reference → SQL).                                                                                                                                            |
| **Audit row**                 | A row in `app_fd_reg_bb_eval_audit` capturing one evaluation, decision, or workflow dispatch event with full context.                                                                                                                                                  |
| **Action**                    | An `mm_action` row that links a service/registration/event to a side effect (workflow dispatch, NID auto-fill, notification). Declarative; no scripting.                                                                                                              |
| **Dispatch**                  | The act of `WorkflowDispatcher` resolving an `mm_action` to a Joget process and starting it via `WorkflowManager.processStart`.                                                                                                                                       |
| **Closed twenty**             | The fast-path operator subset of the rule grammar (ADR-001r2). Twenty operator tokens, fixed.                                                                                                                                                                            |
| **Lesotho-instance extension**| An `mm_*` extension authorised by the audit's multi-service evidence (D6, D9, D16, D21, etc.). Documented in `convergence-framework.md` §9 — not a spec change, an instance-level extension.                                                                                |
