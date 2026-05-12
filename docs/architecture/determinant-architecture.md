# Determinant — architecture design

| | |
|---|---|
| Status | **Draft for review (v0.1)** |
| Author | Claude / pairs with Aare |
| Date | 2026-04-30 |
| Supersedes | `determinant-evaluator-design.md` (which is now an early draft) |
| Related | RegBB spec §8 (`_03_Development/_02_Registration BB/_metamodel/regbb-solution-architecture-spec.md`); ADR-001 r2; decision-log D6, D12, D14, D15; phase-1-plan §D1, §D2 |
| Audience | Engineering review; can be handed to a fresh implementer |

> **Reading guide.** §1–4 are foundations: what the Determinant is, where it sits, what it does, what it doesn't. §5–7 are the operational surface: how callers talk to it, what data flows, what bundles ship it. §8 is the lifecycle: design → publish → runtime → cache invalidation. §9–11 are the runtime properties: failures, performance, audit. §12 is the feasibility analysis of every architectural choice not already settled by ADR-001 r2 / decision log — every option, every reversibility marker. §13 sequences delivery; §14 names the architectural risks. §15 glossary.

---

## 1. Executive summary

The Determinant is the rule-evaluation component of the RegBB Registration Building Block. It answers four questions on demand:

1. *Is this applicant eligible for this Registration?* (eligibility scope)
2. *Should this field/screen be visible/required for this applicant?* (field/screen scope)
3. *What's the fee — and what documents/roles apply — for this applicant's situation?* (fee, document, role scopes)
4. *Which workflow branch should this application take?* (routing scope)

It exposes one Java interface (`DeterminantEvaluator`) with five methods (`evaluate`, `evaluateScreen`, `computeFees`, `resolveRequiredDocs`, plus invalidation hooks). Five thin adapter surfaces (form element, hash variable, process tool, eval servlet, REST endpoint) call into the same instance. Internally, two evaluator implementations cooperate behind the interface: an in-memory tree-walker for rules over a closed twenty-operator subset against `$applicant.*`/`$constant.*`/`$service.*` data (the **fast path**, microsecond-scale, suitable for per-keystroke Ajax), and a compile-to-SQL evaluator for the rest of the DSL grammar including `$registry.*` references and aggregation (the **SQL path**, 5–50 ms, suitable for submit-time evaluation). Routing is by AST analysis at publish time, cached on the AST, implementation-private.

Key architectural commitments, all already settled by ADR-001 r2 and the decision log:

- **One canonical authoring grammar** (the DSL grammar, by ANTLR, defined in `plugins/rules-grammar/`). The closed twenty-operator subset is a runtime optimisation, not an authoring constraint.
- **Server-authoritative evaluation only.** No client-side mirror.
- **Loud failures.** A registry timeout becomes `outcome=error` with a cause; the engine never substitutes `false`. Spec P5.
- **Two-cache invalidation discipline.** Per-application invalidation on data writes; per-service invalidation on republish.

This document does not relitigate those decisions. It specifies the architecture that flows from them: scope, decomposition, contracts, lifecycle, operational properties, and feasibility analysis of the choices not yet fixed.

---

## 2. Context — where the Determinant sits in the system

### 2.1 The four-layer system map

The RegBB-aligned subsidy system has four layers (per `docs/architecture/architecture-overview.md` §3):

```
┌────────────────────────────────────────────────────────────────┐
│ AUTHORING                                                       │
│   joget-rule-editor + 13 mm_* admin CrudMenus + form-creator-api│
│   reg-bb-publisher (validates + binds at publish)               │
└────────────────────────────────────────────────────────────────┘
                             ▼ publishes mm_* meta-records
┌────────────────────────────────────────────────────────────────┐
│ META-MODEL                                                      │
│   12 mm_* entities + 6 Lesotho-instance extensions              │
│   Stored in app_fd_mm_*, owned by Joget's FormDataDao           │
└────────────────────────────────────────────────────────────────┘
                             ▼ reads at runtime
┌────────────────────────────────────────────────────────────────┐
│ RUNTIME ENGINE                                                  │
│   reg-bb-engine — renders forms, evaluates rules, drives flow   │
│   reg-bb-im-connector — registry data via Information Mediator  │
│   reg-bb-payment-connector — payment BB integration             │
│   reg-bb-credential-issuer — credential BB integration          │
└────────────────────────────────────────────────────────────────┘
                             ▼ presents to
┌────────────────────────────────────────────────────────────────┐
│ USER SURFACES                                                   │
│   Citizen wizard (MetaScreenElement, MetaWizardElement)         │
│   Operator review (MetaReviewElement — Phase 3)                 │
│   §8 REST APIs (external systems)                               │
│   XPDL workflows (analyst-built process tasks)                  │
└────────────────────────────────────────────────────────────────┘
```

The **Determinant** is the rule-evaluation half of the runtime engine layer. It is a sibling of the form-rendering half (MetaScreenElement, MetaWizardElement), and a peer of the connector plugins (IM, Payment, Credential) — not a layer above them, not a layer below.

### 2.2 What calls the Determinant

Five adapter surfaces. Detailed contracts in §6; the table here is for orientation:

| Adapter | Caller | Intent |
|---|---|---|
| Form element (built into MetaScreenElement) | Citizen runtime, on render and Ajax | "Should this field be visible? Required?" |
| Hash variable `#regbb.eval[detId]#` | Joget forms / datalists / userviews / notification templates | "What's the value of this rule for the current applicant?" |
| Process tool `RegBbEvaluatorTool` | Analyst-authored XPDL | "Branch this workflow on this rule's outcome." |
| Eval servlet `/regbb/eval/screen` | Citizen Ajax (debounced 250ms) | "Re-evaluate all visible-field rules on this screen given current data." |
| §8 REST `/api/registration/v1/eval` | External BBs / portals | "Programmatically: would this applicant qualify?" |

Plus two internal callers that don't go through an adapter:

- **`RegBbApplicationStoreBinder`** (Phase 1): on application save, evaluate eligibility Determinants and write the structured outcome to `app_application.dataJson.eligibilityOutcome`.
- **Submit-time engine hooks** (Phase 1+): re-evaluate `applyFee` and `requireDoc` Determinants and persist resolved values per spec §6.5 step 943.

### 2.3 What the Determinant calls

Three downstream collaborators, each behind a Java interface:

```
                  ┌────────────────────────────┐
                  │   DeterminantEvaluator     │
                  └─────────────┬──────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐   ┌──────────────────────┐   ┌────────────────┐
│ MetaModelDao  │   │ RegistryReference    │   │ RuleScript     │
│ (read mm_*)   │   │ Resolver             │   │ Compiler       │
│               │   │ (reg-bb-im-connector)│   │ (joget-rules-api)│
│ in-process    │   │ external service     │   │ in-process     │
└───────────────┘   └──────────────────────┘   └────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
   FormDataDao              IM gateway              Postgres
   (Joget core)             (mTLS, breaker)         (form-data tables)
```

- **`MetaModelDao`** — reads `mm_determinant`, `mm_field`, `mm_screen`, `mm_required_doc`, `mm_fee`, `mm_catalog`, `mm_role_screen` rows. In-process; thin wrapper over Joget's `FormDataDao`.
- **`RegistryReferenceResolver`** — resolves `$registry.<source>.<path>` references for SQL-path rules. Implemented by `reg-bb-im-connector`. The Determinant depends on the interface, not the connector — tests stub it; production wires the OSGi service.
- **`RuleScriptCompiler`** — compiles a parsed AST (or DSL source) to parameterised SQL. Lives in `plugins/joget-rules-api/`. The fast-path evaluator does not call this; only the SQL-path evaluator does.

### 2.4 What the Determinant does NOT do

Boundaries. Each is a sibling concern, named here so the Determinant's scope is unambiguous:

| NOT the Determinant's job | Whose job | Boundary point |
|---|---|---|
| Authentication / who is the applicant | `reg-bb-iam-bridge` + Joget's identity manager | `EvalContext.currentUsername` is already populated when the Determinant is called |
| Looking up "which farmer is this NID" | `reg-bb-im-connector` + `farmers.byNid` capability | Determinant calls `RegistryReferenceResolver.resolve("farmers", "byNid", nid, ctx)`; resolver returns the record |
| Self-attestation rules ("did the citizen tick the right boxes on Tab 2") | `form-quality-runtime` (existing legacy plugin) | These are validation rules at the form layer, not eligibility rules at the engine layer; the Determinant evaluates against the *result* of form-quality validation, not its rules |
| Validating mm_determinant.ruleJson at design time | `joget-rule-editor` (in-place validation) and `reg-bb-publisher` (publish-time validation) | The Determinant assumes the rule it's handed is parseable and well-formed; if not, it logs and returns `outcome=error` |
| Persisting application data | `MetaScreenElement` / Joget's standard form-store binding chain | The Determinant reads `EvalContext.data`; it never writes to `app_fd_*` |
| Generating form/userview/datalist JSON | `form-creator-api` + `reg-bb-publisher` | The Determinant never emits Joget definition JSON |
| XPDL workflow orchestration | Joget's `WorkflowManager` + analyst-authored XPDL | The Determinant produces an outcome; the workflow's gateway routes on it |
| Payment, credential issuance, document upload mechanics | Their respective connector plugins | The Determinant *decides* what's required (`resolveRequiredDocs`, `computeFees`); the connectors *execute* the upload/payment/issuance |
| Emitting notifications, sending emails, posting to messaging | `mm_action` kind=`message` + the action runtime | The Determinant *gates* whether an action fires (`scope=action`); it does not fire actions itself |

The Determinant is a pure function (in the side-effect sense) of `(mm_determinant.ruleJson, EvalContext) → EvalResult`. It reads meta-records and registry data; it produces outcomes. Everything *upstream* (authoring, publishing, citizen data entry) and everything *downstream* (workflow orchestration, action execution, document/fee/credential mechanics) is somebody else's job.

---

## 3. Scope — what the Determinant is

### 3.1 Definition

A **Determinant** is one row in the `mm_determinant` table. Each row carries:

- A **rule** (`ruleJson`) — either a JSON-AST (fast-path-eligible rules) or a DSL source string (SQL-path rules). The rule, when evaluated, produces a Boolean (most scopes) or a number (fee scope) or `null` (missing data, per spec P5) or an error.
- A **scope** (`scope`) — one of `field`, `screen`, `document`, `fee`, `eligibility` (was `applicability`), `routing`, `action`, `role`. The scope tells the engine *which* of the four questions in §1 this Determinant participates in answering.
- An **action** (`actionJson`) — what happens when the rule evaluates true. The action's `target` references another `mm_*` entity (`mm_field`, `mm_screen`, `mm_fee`, `mm_registration`, etc.); its `kind` is one of `require`, `hide`, `show`, `applyFee`, `requireDoc`, `assignRole`, `branch`, `applicable`, `fireAction`.
- **Lesotho-instance scoring extension** (per D6) — `score` (number, default 0), `ruleType ∈ {inclusion, exclusion, priority, bonus}`, plus the parent `mm_registration.evaluationStrategy ∈ {all_must_pass, score_based}` driving how multiple Determinants combine into a single outcome per Registration.
- **Performance hygiene flag** — `allowSlowPath` (Y/N). Lets an analyst opt in to SQL-path rules in performance-sensitive scopes (`field`, `screen`) — see §4.3.4 of the spec, §10 of this document.

The **Determinant evaluator** is the singleton Java component that, given an `mm_determinant.id` and a runtime context, fetches the rule, parses it (or pulls from cache), routes it to fast or SQL path, evaluates, returns the outcome, writes an audit row when material, and updates caches.

### 3.2 Scopes — what each one means at runtime

| Scope | What "true" means | What action fires | Used at | Per-keystroke? |
|---|---|---|---|---|
| `eligibility` (was `applicability`) | "This Registration is applicable to this applicant given current data" | Registration listed on the catalogue page as selectable | Catalogue render; submit | No |
| `field` | "This field's visibility / requiredness should change" | `hide`/`show`/`require`/`optional` on a `mm_field` | Screen render; Ajax (per-keystroke) | **Yes** — fast path is hard requirement |
| `screen` | "This screen step should be skipped or shown" | `hide`/`show` on a `mm_screen` | Wizard navigation | **Yes** — fast path is hard requirement |
| `document` | "This required document applies to this applicant" | `requireDoc` produces a resolved `RequiredDocResult` | Submit; document-upload tab render | No |
| `fee` | "This fee applies and equals N" | `applyFee` with the numeric result; arithmetic operators only | Fee-calc tool task; submit | No |
| `routing` | "This routing rule selected this branch" | XPDL gateway condition | Submit; workflow gateway | No |
| `role` | "This role is active for this application" | `assignRole`; if false, role is bypassed in the workflow | Workflow role-screen activation | No |
| `action` | "This action should fire" | `fireAction` enables an `mm_action` row | Action runtime (Phase 2+) | No |

Two scopes (`field`, `screen`) participate in the per-keystroke Ajax conditional-UI path and **must** be evaluated on the fast path for production performance — the spec mandates this, the publisher warns at publish time when an SQL-path rule is authored in these scopes (§4.3.4). All other scopes are submit-time-or-later and may use either path freely.

### 3.3 Determinant cardinality and aggregation

A Registration typically has 1–10 Determinants attached via `applicabilityDeterminantId` plus zero or more per-scope rules. With four programmes in Phase 1, the spec estimates 18 Determinants total (4 + 4 + 5 + 5 from the screening; phase-1-plan §D5).

When multiple Determinants apply to the same Registration:

- For `evaluationStrategy=all_must_pass` (default): every `inclusion` Determinant must evaluate true; no `exclusion` Determinant may evaluate true; outcome is `pass` if both conditions hold.
- For `evaluationStrategy=score_based` (per D6): mandatory `inclusion`/`exclusion` rules behave as above, then `priority` and `bonus` rules' `score` values sum into `totalScore`. Outcome:
  - `pass` if `mandatoryPassed && totalScore >= passingThreshold`
  - `fail` if `!mandatoryPassed || totalScore < minimumScore`
  - `pending_review` if `mandatoryPassed && minimumScore <= totalScore < passingThreshold` (per D15 — does not introduce a new status enum value; the disposition is structured data on `dataJson.eligibilityOutcome`)

The aggregation is a property of the *Registration*, not of any single Determinant. The evaluator exposes `evaluate(determinantId, ctx)` for one rule; aggregation is the caller's job (typically `RegBbApplicationStoreBinder` for eligibility, or `RegBbFeeCalcTool` for fees). Document and field/screen scopes don't aggregate — each Determinant fires or doesn't independently.

---

## 4. Information model

### 4.1 What the Determinant reads

Five categories of input data, each from a stable source:

```
EvalContext
  ├── applicationId           ← identifies the row in app_application
  ├── serviceId, serviceVersion ← identifies the mm_service version
  ├── selectedRegistrationIds ← from app_application.selectedRegistrationIds
  ├── data: Map<String,Object>← from app_application.dataJson + form fields
  ├── currentUsername         ← Joget WorkflowUserManager
  └── correlationId           ← X-Request-Id header (NGINX-injected)

External lookups (computed during eval, not in EvalContext):
  ├── mm_determinant row      ← MetaModelDao.findDeterminantByCode
  ├── mm_field metadata       ← MetaModelDao for ref resolution + scope rules
  ├── mm_catalog values       ← MetaModelDao for $constant.* refs
  ├── mm_role_screen.sectionsJson ← MetaModelDao when role-scope rules fire
  └── $registry.<source>.<path> ← RegistryReferenceResolver (IM connector)
```

Reference resolution table (per spec §4.3.1):

| Ref form | Where it resolves | Resolver |
|---|---|---|
| `$applicant.<storageKey>` | `EvalContext.data` (matches a `mm_field.storageKey`) | Engine, in-memory |
| `$registry.<source>.<path>` | External registry via IM | `RegistryReferenceResolver.resolve(...)` |
| `$service.<path>` | `mm_service` row, looked up at evaluation | MetaModelDao |
| `$registration.<regCode>.<path>` | `mm_registration` row | MetaModelDao |
| `$selected_registrations` | `EvalContext.selectedRegistrationIds` | Direct |
| `$constant.<key>` | Inlined at publish time as a literal in the AST | n/a |

Null propagation: any operator with a null operand returns null, except `exists` which returns false. Spec §4.3.0.1.

### 4.2 What the Determinant writes

Three categories of output:

```
EvalResult                        ← returned to the caller, in-memory
  ├── outcome: TRUE|FALSE|NULL|ERROR
  ├── numericValue                ← non-null for fee scope
  ├── actionTarget                ← non-null for routing/applicable scopes
  ├── evaluator: "fast"|"sql"
  └── errorCause                  ← non-null when outcome=ERROR

Cache writes                      ← side effect of every eval
  ├── L1 (per-thread)
  ├── L2 (per-JVM Caffeine)
  └── L3 (cluster Hazelcast)

Audit row (when outcome is "material")     ← written to app_fd_reg_bb_eval_audit
  ├── id, application_id, determinant_id
  ├── evaluated_at, evaluated_by
  ├── inputs_json (resolved refs)
  ├── outcome, cause
  ├── action_taken_json
  └── correlation_id
```

Material outcomes that warrant an audit row (per spec §7.4): visibility change took effect, fee was applied, document was demanded, role was assigned, eligibility decision was reached at submit, process tool fired. Per-keystroke Ajax re-evaluations do **not** write audit rows — that would flood the table.

The Determinant **does not** write to `app_application.dataJson`. The structured outcome from eligibility evaluation is written by `RegBbApplicationStoreBinder` (in `reg-bb-engine`), which calls the Determinant, then persists the outcome via `FormDataDao.saveOrUpdate`. This separation matters for SOLID — the Determinant is a pure rule evaluator; persistence side-effects are the caller's concern. See §7.5.

### 4.3 Cache key schema

```
Key = (applicationId, determinantId, dataHash, registryFetchEpoch)

dataHash:           hash of the SUBSET of EvalContext.data referenced
                    by this Determinant (computed at first eval, cached)
registryFetchEpoch: incremented when IM cache rolls (default TTL 5min)
```

Two consequences worth flagging:

- The cache key includes only the data that the rule actually references, not the whole `EvalContext.data`. Computing the field-reference set is a side effect of parsing — done once per Determinant per JVM lifetime.
- `registryFetchEpoch` is exposed by the IM connector; the Determinant reads it as part of building the cache key. When IM's cache for `(source, path)` rolls, the Determinant's cache entry's key becomes unreachable — natural eviction.

Cache invalidation (per spec §8.4):

| Trigger | Scope | Mechanism |
|---|---|---|
| Application data updated | All entries for `applicationId` | `evaluator.invalidate(applicationId)` from the binder after persist |
| Service republished | All entries for `serviceId` | `evaluator.invalidateService(serviceId)` from publisher as last publish step |
| IM TTL rolls | Entries with stale `registryFetchEpoch` | Implicit (key change) |
| Cluster node restart | L1+L2 lost; L3 survives | Hazelcast |
| Manual operator override ("re-evaluate this application") | One application | Button on operator review form; `force=true` parameter |

---

## 5. Where it's used — the five adapter contracts

Each adapter is ~50 LOC of glue, per spec §8.1. Detailed contracts here so an engineer can implement each adapter without re-deriving the data flow.

### 5.1 Adapter A — Form element (built into `MetaScreenElement`)

| | |
|---|---|
| Caller | Citizen runtime, automatically by `MetaScreenElement.renderTemplate` |
| Trigger | Initial screen render (one shot); Ajax `regbbWatch` (per-keystroke debounced 250 ms) |
| Method | `evaluator.evaluateScreen(screenId, ctx)` |
| Input | `screenId`, current applicant data subset for the screen |
| Output | `ScreenEvalResult` — per-field visibility/requiredness toggles + degraded-mode marker |
| Latency budget | <50 ms for Ajax round-trip; render path tolerates 200 ms |
| Cache | All three layers; key on `applicationId + screenId + dataHash` |
| Failure | If any rule on the screen errors, that field's `defaultBehaviorOnError` from `mm_field` governs (visible/hidden/required/optional) |

Already partially scaffolded: `MetaScreenElement` exists and renders. The evaluator call inside it is not yet wired. Phase 1B addition.

### 5.2 Adapter B — Hash variable `#regbb.eval[determinantId]#`

| | |
|---|---|
| Caller | Anywhere Joget resolves hash variables — form labels, datalist columns, notification templates, userview content, email subjects |
| Trigger | Each hash-variable resolution call |
| Method | `evaluator.evaluate(determinantId, ctx)` |
| Input | `determinantId` from the hash variable; `applicationId` resolved from current workflow context |
| Output | String form of the outcome — `"true"`, `"false"`, `"error"`, or numeric for fee determinants |
| Latency budget | Tens of ms acceptable (hash variables are not on the hot path) |
| Cache | All three layers |
| Failure | Returns `"error"` and writes audit row; the hash-variable resolver does not throw — caller's template renders `error` literally |

Implemented as a Joget `HashVariablePlugin` registered in the OSGi Activator. Lives in the **engine's caller-adapters bundle** (see §7.2 — separate from the evaluator itself for SRP reasons).

### 5.3 Adapter C — Process tool `RegBbEvaluatorTool`

| | |
|---|---|
| Caller | Analyst-authored XPDL workflow tool tasks |
| Trigger | XPDL workflow execution reaches a tool task |
| Method | `evaluator.evaluate(determinantId, ctx)` |
| Input | `determinantId` from tool config; `outputVariable` name from tool config (must be `svc_*`-prefixed by convention) |
| Output | Writes outcome to the named workflow variable (boolean → `"true"`/`"false"` string; numeric → number string) |
| Latency budget | Workflow tool tasks are allowed seconds |
| Cache | All three layers |
| Failure | Variable set to `"error"`; analyst's gateway must handle |

A sibling process tool, `RegBbFeeCalcTool`, takes `serviceId` as config and writes total to `feeAmount` workflow variable. Both implemented as `org.joget.plugin.base.PluginInterface` extensions registered in the Activator.

### 5.4 Adapter D — Eval servlet `POST /regbb/eval/screen`

| | |
|---|---|
| Caller | Citizen browser, via `regbbWatch` JS (debounced 250 ms) |
| Trigger | Field change/blur on a watched field |
| Method | `evaluator.evaluateScreen(screenId, ctx)` |
| Input | JSON body: `{applicationId, screenId, partialData}` |
| Output | JSON body: `{toggles: [{fieldId, visible, required}], degraded?: bool, reason?: str}` |
| Latency budget | <100 ms wire-to-wire (typical); 250 ms tolerable on mobile |
| Cache | All three layers; L1 is the per-request cache |
| Failure | HTTP 200 with `degraded: true` + cause; the JS does not surface errors to the citizen — fields show their `defaultBehaviorOnError` state |

Internal endpoint, not part of the §8 REST surface. Implemented as a Joget `PluginWebSupport` servlet registered in the Activator.

### 5.5 Adapter E — §8 REST `POST /api/registration/v1/eval`

| | |
|---|---|
| Caller | External BBs / portals |
| Trigger | External HTTP POST |
| Method | `evaluator.evaluate(determinantId, ctx)` |
| Input | JSON body: `{serviceCode, serviceVersion, determinantId, data}` — no applicationId (anonymous "would this hypothetical applicant qualify?") |
| Output | JSON body: `{outcome, action, evaluator, evaluatedAt}` |
| Latency budget | 200 ms |
| Cache | L2/L3 only (no applicationId means no L1 keying); cache key uses `(serviceCode, determinantId, dataHash)` with no `applicationId` |
| Authentication | API Builder credentials (api-id + api-key). The §8 surface authenticates differently than the Joget admin surface — see CLAUDE.md §"Calling form-creator-api endpoints" |
| Failure | HTTP 200 with `outcome: "error"` body; HTTP 4xx only for malformed requests / missing auth |

Implemented as an API Builder plugin in `plugins/reg-bb-api-public/` (separate from the engine — SRP, plus the §8 surface evolves on its own cadence per spec §9). Phase 2 deliverable.

### 5.6 Adapter F — Application store binder (Phase 1 internal use)

Not on the spec's "five adapters" list because the spec assumes the evaluator is called *from* the storeBinder via the form element adapter. In Phase 1 we use a more direct hook because the Phase 1 form layout is simpler than the spec's full citizen-runtime model. Documented here for completeness:

| | |
|---|---|
| Caller | `RegBbApplicationStoreBinder` (subclass of Joget's `WorkflowFormBinder`) |
| Trigger | Citizen clicks Save on the application wizard |
| Method | `evaluator.evaluate(determinantId, ctx)` for each Determinant on each selected Registration |
| Input | `applicationId`, the just-saved row's data |
| Output | Aggregated `eligibilityOutcome` JSON (per §3.3) written to `app_fd_subsidy_app_2025.c_eligibility_outcome` via a follow-up `FormDataDao.saveOrUpdate` |
| Latency budget | Acceptable up to several seconds (not in the hot path) |
| Cache | All three layers |
| Failure | Outcome `disposition=indeterminate` with `reason=engine_error`; never null on a saved row |

This adapter is replaced in Phase 3 by the spec's submit flow (`MetaScreenElement.submit`), at which point the storeBinder retires.

---

## 6. Architecture decomposition

### 6.0 Principle vocabulary (corrected per ADR-002 r2 lesson)

Architectural decisions in this document and in the ADR set are evaluated against a vocabulary of named principles. The complete list:

* **SOLID** — SRP (one reason to change), OCP (open to extension, closed to modification), LSP (substitutable subtypes), ISP (focused interfaces), DIP (depend on abstractions).
* **YAGNI** — don't carry architectural complexity until a current user benefits from it. Distinguished from feature-need framing: feature-need speculates "users might want X later"; YAGNI is "this complexity in our own design isn't earned today." The first is invalid as an architecture argument, the second is valid.
* **KISS** — prefer the simplest shape that meets the requirements. When a principle conflict pulls toward complexity, KISS pulls back unless the complexity is earned.
* **Convention over Invention** — when an established platform pattern exists (Joget's plain-JAR contract sharing, RegBB spec's adapter shapes, etc.), divergence requires scale-proportionate justification.
* **Spec principles** — P1 (configuration over code), P2 (excellent UX over standardisation purity), P5 (loud failure over silent default) — from the RegBB spec's named principles.

**No principle is universal.** Principles point in different directions and conflict regularly: SRP says split, YAGNI says combine; OCP says open structure, KISS says simple structure; DIP says abstractions, Convention says don't invent. An honest decision names the principle that won, the principle that pulled the other way, and why the latter lost. A decision citing one principle and no contrary principle is suspect — it's selective application masquerading as principled reasoning.

ADR-002 revision 1 was a worked example of this failure mode: SRP was cited in isolation, YAGNI / KISS / Convention over Invention weren't given a hearing, and the decision over-applied SRP to a single-customer single-team context where simpler shapes were correct. Revision 2 superseded it after the missing principles were named. The methodological lesson — name the counter-principle — is now part of the ADR template (`determinant-adr-drafting-session-prompt.md` §4).

### 6.1 SOLID-grounded bundle layout

The Determinant ships across multiple OSGi bundles. Each bundle exists because it has one responsibility (SRP), and the dependency direction between bundles inverts toward abstractions (DIP). The split is **not** justified by future feature needs (e.g. "what if we swap evaluators?") — it's justified by the principles holding by default.

```
                    ┌──────────────────────────┐
                    │      reg-bb-api          │  (interfaces + value types)
                    │   exports:               │
                    │    DeterminantEvaluator  │
                    │    EvalContext, EvalResult│
                    │    ScreenEvalResult      │
                    │    FeeResult, RequiredDocResult│
                    │    RegistryReferenceResolver│
                    │    MetaModelDao          │
                    └────────────┬─────────────┘
                                 │ depended on by ↓
              ┌──────────────────┼─────────────────────┐
              │                  │                     │
              ▼                  ▼                     ▼
    ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
    │ reg-bb-engine    │ │ reg-bb-evaluator │ │ reg-bb-im-       │
    │ (rendering)      │ │ (rule eval)      │ │ connector        │
    │                  │ │                  │ │ (registry data)  │
    │ - MetaScreenEl   │ │ - FastPathEval   │ │ - Implements     │
    │ - MetaWizardEl   │ │ - SqlPathEval    │ │   RegistryRef-   │
    │ - StoreBinder    │ │ - RoutingEval    │ │   erenceResolver │
    │                  │ │ - RuleAstCache   │ │ - mTLS, breaker, │
    │ depends on:      │ │ - 3-tier cache   │ │   IM gateway      │
    │  reg-bb-api      │ │                  │ │                  │
    │  Joget Form API  │ │ depends on:      │ │ depends on:      │
    │                  │ │  reg-bb-api      │ │  reg-bb-api      │
    │                  │ │  rules-grammar   │ │                  │
    │                  │ │  joget-rules-api │ │                  │
    └──────────────────┘ └──────────────────┘ └──────────────────┘
              ▲                  ▲                     ▲
              └──────────────────┴─────────────────────┘
                                 │
                    ┌────────────┴─────────────┐
                    │  reg-bb-adapters         │  (caller adapters)
                    │  - HashVariablePlugin    │
                    │  - RegBbEvaluatorTool    │
                    │  - RegBbFeeCalcTool      │
                    │  - EvalServlet           │
                    │                          │
                    │  depends on: reg-bb-api  │
                    └──────────────────────────┘

Plus:
    ┌──────────────────────────┐    ┌──────────────────────────┐
    │ reg-bb-publisher         │    │ reg-bb-api-public        │ (Phase 2)
    │ - Publish action         │    │ - §8 REST endpoint       │
    │ - Validation (incl. AST  │    │ - API Builder plugin     │
    │   classification)        │    │                          │
    │ - Cache invalidation     │    │ depends on: reg-bb-api   │
    │ depends on: reg-bb-api,  │    │              api-builder │
    │             rules-grammar│    └──────────────────────────┘
    └──────────────────────────┘
```

### 6.2 Bundle responsibilities

| Bundle | Responsibility | Reason for being separate |
|---|---|---|
| `reg-bb-api` | Define the Determinant contract — interfaces, value types, the `MetaModelDao` shared DAO | SRP: API definition is its own responsibility; bundling it with implementations would force every consumer to depend on every implementor |
| `reg-bb-engine` | Render forms; bind application save (`StoreBinder`) | SRP: rendering and storage are form-lifecycle concerns, distinct from rule evaluation |
| `reg-bb-evaluator` | Evaluate rules — both fast path and SQL path; manage cache; route by AST analysis | SRP: rule evaluation has its own change cadence (operator additions, grammar evolutions) and is independently testable as POJOs without a Joget runtime. DIP: depends on the API bundle, not on `reg-bb-engine` |
| `reg-bb-adapters` | Hash variable, process tool, eval servlet | SRP: adapters are glue — short, stable, with their own Joget-plugin deployment shape (PluginInterface, PluginWebSupport) |
| `reg-bb-im-connector` | Implement `RegistryReferenceResolver` for the Information Mediator | SRP: integration with an external system is independent of evaluation |
| `reg-bb-publisher` | Validate and publish services; classify rules by AST analysis at publish time | SRP: design-time validation and runtime evaluation evolve at different rates |
| `reg-bb-api-public` | §8 REST endpoint via API Builder | SRP: external API surface; deploys via API Builder's lifecycle, not the engine's |

Open question on `MetaModelDao` placement: §12 Q1.

### 6.3 Why not fewer bundles?

Two collapses suggested at various points; both refused:

- *"Combine `reg-bb-api` and `reg-bb-evaluator`."* Refused. The api bundle is consumed by `reg-bb-engine`, `reg-bb-im-connector`, `reg-bb-adapters`, `reg-bb-publisher`, `reg-bb-api-public` — five consumers. Bundling api into the evaluator would force them all to depend on the evaluator's whole transitive classpath (rules-grammar, joget-rules-api, etc.) just to see the interfaces. DIP violation.
- *"Combine `reg-bb-engine` and `reg-bb-evaluator`."* Refused, and this was Aare's catch. Rendering and evaluation are two responsibilities; one reason-to-change per bundle. Earlier draft made the wrong call here; this document corrects it.

### 6.4 OSGi service wiring

At runtime, the evaluator is a single instance registered as an OSGi service. Engine, adapters, and publisher look it up via Joget's `PluginManager`:

```java
// in RegBbApplicationStoreBinder (reg-bb-engine):
DeterminantEvaluator evaluator = (DeterminantEvaluator)
    PluginManager.list(DeterminantEvaluator.class.getName())
        .stream().findFirst().orElse(null);
if (evaluator == null) {
    // graceful degradation per §9
    return indeterminateOutcome("evaluator_unavailable");
}
```

The `RegistryReferenceResolver` is registered the same way by `reg-bb-im-connector`; the evaluator looks it up on first use. If absent, all `$registry.*` rules return `outcome=error` cause `registry_resolver_unavailable` — never silent false. Per §8.5 of the spec.

No Spring beans, no static singletons. Bundles are independently start/stoppable; loss of the evaluator bundle degrades but does not crash the engine. Loss of the IM connector degrades only `$registry.*`-touching rules.

---

## 7. Lifecycle

### 7.1 Design time (analyst authoring)

```
analyst opens joget-rule-editor
  ↓ types DSL source for the rule
  ↓ editor validates in-place (ANTLR parse)
  ↓ editor analyses the parsed AST
  ↓ editor decides serialisation format:
        - JSON-AST shape if rule is fast-path-eligible
        - DSL source string otherwise
  ↓ editor displays informational hint if SQL-path
  ↓ analyst saves
  ↓ mm_determinant.ruleJson updated via FormDataDao
```

Routing classification is editor-side at design time **for hint display only**. The authoritative routing decision is publisher-side at publish time and engine-side at first evaluation; editor classification is advisory.

### 7.2 Publish time (validation + binding)

```
operator clicks "Publish" on mm_service admin record
  ↓ reg-bb-publisher walks every Determinant on the service
  ↓ for each: parse via rules-grammar
  ↓ for each: walk refs — verify they resolve
  ↓ for each: walk action targets — verify they exist
  ↓ for each: classify (fast-path-eligible vs SQL-path)
  ↓ for each: if scope ∈ {field,screen} && SQL-path && !allowSlowPath:
              issue publish-time warning (not a hard reject)
  ↓ store classification on the cached AST (per service version)
  ↓ flip mm_service.status to "published"
  ↓ call evaluator.invalidateService(serviceId) as final step
```

Publish failures abort with a clear message naming the offending Determinant (per spec §4.3.4). Re-running publish after fix is idempotent.

### 7.3 Runtime — fast path

```
caller calls evaluator.evaluate(determinantId, ctx)
  ↓ check L1 cache → hit returns immediately
  ↓ check L2 → hit promotes to L1, returns
  ↓ check L3 → hit promotes to L2+L1, returns
  ↓ MISS:
  ↓   load mm_determinant via MetaModelDao
  ↓   load cached AST or parse if not cached
  ↓   read classification: FAST
  ↓   resolve refs ($applicant.* from ctx.data,
                     $constant.* from inlined literals)
  ↓   walk AST, evaluate operators
  ↓   produce EvalResult with evaluator="fast"
  ↓   write to L1, L2, L3
  ↓   if material outcome: write reg_bb_eval_audit row
  ↓ return
```

Total time on cache miss: <1 ms typical, dominated by AST walk depth (rules in this codebase rarely exceed depth 5).

### 7.4 Runtime — SQL path

```
caller calls evaluator.evaluate(determinantId, ctx)
  ↓ cache check (same as fast path)
  ↓ MISS:
  ↓   load mm_determinant, load cached AST
  ↓   read classification: SQL
  ↓   look up cached compiled SQL for (determinantId, serviceVersion)
  ↓   if not cached: compile via RuleScriptCompiler
  ↓   resolve $registry.* refs:
  ↓     for each: RegistryReferenceResolver.resolve(source, path, subjectId, ctx)
  ↓     IM connector hits cache or fetches; updates registryFetchEpoch
  ↓     on RegistryUnavailableException: outcome=error, return
  ↓   bind parameters from EvalContext.data + resolved $registry values
  ↓   execute SQL against form-data DB
  ↓   read result row → outcome
  ↓   produce EvalResult with evaluator="sql"
  ↓   write to all cache layers
  ↓   write reg_bb_eval_audit row with compiled-SQL hash
  ↓ return
```

Total time on cache miss: 5–50 ms typical, dominated by IM round-trip when `$registry.*` refs are present.

### 7.5 The application save cycle (Phase 1 storeBinder)

```
citizen clicks Save on the wizard
  ↓ Joget form lifecycle: executeElementFormatData walks the tree
  ↓   each MetaScreenElement.formatData contributes its values
  ↓ rowSet built; passed to RegBbApplicationStoreBinder.store(...)
  ↓ super.store(form, rowSet, formData) — standard save runs first
  ↓ row persisted; Joget redirects (citizen perception: "saved")
  ↓ POST-SAVE PROCESSING — runs on the same thread, before redirect:
  ↓   fetch mm_registration for selected_registrations
  ↓   for each Registration:
  ↓     gather its eligibility Determinants
  ↓     for each: evaluator.evaluate(detId, ctx) [cache layered]
  ↓     aggregate per evaluationStrategy → per-Registration outcome
  ↓   build aggregated eligibilityOutcome JSON
  ↓   FormDataDao.saveOrUpdate write outcome to c_eligibility_outcome
  ↓   evaluator.invalidate(applicationId)  — caller-driven invalidation
  ↓ return; Joget completes the redirect
```

Two transactions (the standard save, then the outcome write). On evaluator failure, the second write inserts `disposition=indeterminate` so the row never lacks an outcome value. Spec-aligned alternative for Phase 3: `MetaScreenElement.submit` fuses the steps into one transaction; deferred per phasing in §13.

### 7.6 Cache invalidation events

Already covered in §4.3 table. The discipline is: **caller-driven invalidation on data writes; publisher-driven invalidation on republish.** The Determinant evaluator does not invalidate itself reactively — invalidation always has a clear cause and a clear caller.

---

## 8. Failure modes & resilience

### 8.1 Six explicit failure cases

| Case | Engine response | Citizen UX | Operator UX | Audit |
|---|---|---|---|---|
| Rule evaluates true | `outcome=TRUE` | Outcome reflects true (e.g., field shown) | Decision shown | Audit row written if material |
| Rule evaluates false | `outcome=FALSE` | Outcome reflects false (e.g., disposition=fail with `failMessage`) | Disposition + cause shown | Audit row written if material |
| Rule's reference is null (P5) | `outcome=NULL` per spec — no false-substitution | Treated as "validation incomplete" — submit blocks at form layer, not at engine layer | NULL surfaced with cause: which ref was missing | Audit row marks NULL outcome |
| Rule references unsupported | `outcome=ERROR` cause `routed_to_sql_path_not_implemented` (slice 1A only) | Save succeeds; disposition=indeterminate; cause shown to operator | Indeterminate banner + manual triage | Audit row + WARN log |
| Parser error (corrupt rule) | `outcome=ERROR` cause `parse_error: <antlr_message>` | Same as above | Same as above | Audit row + ERROR log; publisher should have caught this |
| Internal evaluator bug (NPE etc.) | `outcome=ERROR` cause `internal: <classname>:<msg>` | Same | Same | Audit row + ERROR log + stack trace |

P5 is a hard rule from the spec: missing data does **not** become false. The disposition `indeterminate` (or per-rule `NULL`) is the only honest answer. Operators triage manually — better than auto-rejecting on incomplete data.

### 8.2 Resilience properties

- **Evaluator bundle stopped.** All five adapters return `outcome=error` cause `evaluator_unavailable`. StoreBinder writes `disposition=indeterminate`. Citizen save still succeeds. Operator review shows the indeterminate banner.
- **IM connector unreachable.** All `$registry.*`-touching rules return `outcome=error` cause `registry_unavailable` (or `breaker_open` if circuit is tripped). Application enters `pending_external` per spec §10.2.5. Sweeper job promotes long-stuck applications to `clerk_review_pending_external`.
- **Database unreachable (SQL path).** Same as IM unreachable — `outcome=error` cause `db_unavailable`.
- **Ajax endpoint unreachable.** Citizen UI shows fields in their `defaultBehaviorOnError` state; the JS does not poll. User can save anyway; submit-time evaluation runs once.
- **Cluster node failure.** L1+L2 lost on the failed node; L3 survives; applications continue. New requests route to surviving nodes.

### 8.3 What "loud failure" means in code

```java
// NOT THIS:
try { return evaluate(rule, ctx); }
catch (Exception e) { return EvalResult.bool(false, "fast"); }   // ← silent false; spec P5 violation

// THIS:
try { return evaluate(rule, ctx); }
catch (RegistryUnavailableException e) {
    LogUtil.warn(...);
    auditWriter.write(applicationId, determinantId, "error", "registry_unavailable", e.getMessage());
    return EvalResult.error("registry_unavailable", "sql");      // ← surfaced
}
catch (Exception e) {
    LogUtil.error(CLASS_NAME, e, "internal evaluator failure");
    auditWriter.write(applicationId, determinantId, "error", "internal:" + e.getClass().getSimpleName(), e.getMessage());
    return EvalResult.error("internal", "fast");                  // ← surfaced
}
```

Code review for the evaluator must reject silent-false on any failure path. Spec P5 is non-negotiable.

---

## 9. Performance and scaling

### 9.1 Latency budgets (per spec §8.6.4 and §6.4)

| Path | Typical latency | Used for |
|---|---|---|
| Fast path | <1 ms cache miss, ~0.1 ms cache hit | Per-keystroke Ajax conditional UI; screen render; routing on `$applicant.*`-only rules |
| SQL path | 5–50 ms cache miss, ~0.1 ms cache hit | Submit-time eligibility, fee computation, registry-touching field rules (with `allowSlowPath=true`) |
| Ajax round-trip | <100 ms wire-to-wire | Per-field re-evaluation; mobile tolerable to 250 ms |

### 9.2 Cache hit ratio assumptions

- L1 (per-thread): nearly 100% within one screen render (overlapping rules on same data).
- L2 (per-JVM): high (~90%) for one applicant's session — same applicant evaluates the same rules multiple times during a wizard.
- L3 (cluster): meaningful when a citizen switches device mid-session (e.g., started on mobile, finishing on desktop).

### 9.3 What scales linearly, what doesn't

- **Linear in determinants per evaluation.** Evaluating `evaluateScreen` for a screen with 8 Determinants does 8 evaluations. No batching.
- **Sub-linear in applicants.** L2/L3 cache means concurrent applicants on the same service version share registry-fetch results.
- **Linear in `$registry.*` cardinality.** A rule with 3 `$registry.*` refs incurs 3 IM round-trips on cache miss.
- **Constant in rule depth.** AST walk is O(node count); deepest rules in this codebase are ~10 nodes.

### 9.4 Performance hygiene at publish

The publisher warns when a `field`-scope or `screen`-scope rule classifies as SQL-path. Override via `mm_determinant.allowSlowPath=true` for the rare legitimate case (e.g., "show this field only if applicant's civil-registry marital status = married"). The override is opt-in and visible in the audit; analysts cannot accidentally ship a per-keystroke registry rule.

### 9.5 What we measure

Phase 1 doesn't ship metrics infrastructure. Phase 2+: latency histograms per evaluator path, cache hit ratios per layer, IM call counts per rule, audit-row count per service per day. The instrumentation hooks belong on the evaluator's interface boundaries (one wrapper class per cache layer, one wrapper around `RegistryReferenceResolver`); no business-logic changes needed when metrics land.

---

## 10. Security and audit

### 10.1 Threats

| Threat | Mitigation |
|---|---|
| Operator-supplied SQL injection | DSL compiles to parameterised SQL; no raw-SQL admin path; operator-supplied values become bound parameters at execution |
| Operator-supplied regex-DoS | `matches` operator's pattern is a literal in the rule, never user-supplied at runtime; pattern compiled once and cached |
| Information disclosure via $registry.* | IM connector enforces consent per `EvalContext.currentUsername`; access denials surface as `outcome=error` cause `access_denied`; publisher fails fast on unreachable refs |
| Audit tampering | `reg_bb_eval_audit` rows are append-only; Joget's audit-trail discipline applies; cluster-wide retention is 1 year via `RegBbAuditPurger` job |
| Replay across applications | Cache key includes `applicationId`; results from one applicant cannot leak to another |

### 10.2 Audit trail

`reg_bb_eval_audit` columns per spec §7.4:

| Column | Purpose |
|---|---|
| `id` (PK) | `EVT-<6char>` |
| `c_application_id` | `app_application.id` |
| `c_determinant_id` | `mm_determinant.id` |
| `c_evaluated_at` | server time |
| `c_evaluated_by` | username from `WorkflowUserManager` |
| `c_inputs_json` | values that fed the rule (refs resolved) |
| `c_outcome` | `true` \| `false` \| `error` (NULL omitted from this column for brevity; `c_cause='null_ref'` distinguishes) |
| `c_cause` | non-empty when outcome=error or null |
| `c_action_taken_json` | the resolved action; null if false/error |
| `c_correlation_id` | propagated from NGINX `X-Request-Id` |

Written when material — at submit, at fee-calc tool, at document-resolver, at any process tool invocation. **Not** written on per-keystroke Ajax re-evaluations (would flood).

Phase 1 may defer the audit table to slice 1B/1C, with eligibility outcomes captured in `app_application.dataJson.eligibilityOutcome` instead. This defers decisions that rightly belong in §12 Q5 but lets the first slice ship without a new table.

### 10.3 Spec conformance posture

The rule grammar is documented as a deliberate Lesotho-instance choice that extends the published GovStack RegBB twenty-operator surface with the ANTLR DSL. Cross-jurisdiction rule portability:

- Rules expressible in the closed twenty-operator subset over `$applicant.*`/`$constant.*` are portable.
- Rules using DSL extensions (`$registry.*`, aggregation, set quantifiers) are not portable to a pure-spec implementation.

This is documented honestly per ADR-001 r2 §5.2 — not concealed.

---

## 11. Versioning and in-flight applications

A `mm_service` carries a `version`. Publish creates a new version; in-flight applications keep their `serviceVersion` pinned. Rules edited on the new version don't affect applications on the old version's cache.

```
publisher publishes service version 5
  ↓ new (serviceId, v5) AST cache populated
  ↓ evaluator.invalidateService(serviceId) — but only entries with v=5 had been computed previously
  ↓ in-flight applications continue evaluating against v4 (their pinned serviceVersion)
  ↓ a citizen who started on v4 finishes on v4
  ↓ next-new-applicant-after-publish gets v5
```

Implication: cache key includes `serviceVersion`. Rule changes in a service edit on `draft` are visible only after the draft is published as a new version.

Spec §4.4 (Service Lifecycle and Versioning) is the canonical reference.

---

## 12. Feasibility analysis — open architectural questions

Each question below is a choice not already settled by ADR-001 r2 / decision log. For each: options enumerated (not just two), feasibility evidence cited (file/method), recommendation with the principle named, reversibility flagged.

### Q1. Where does `MetaModelDao` live?

Used by both rendering (`reg-bb-engine`) and evaluation (`reg-bb-evaluator`).

| Option | Detail | Evidence |
|---|---|---|
| A | Keep in `reg-bb-engine`; evaluator depends on engine | Cycle: storeBinder in engine calls evaluator; evaluator would call engine. OSGi accepts this but architecturally rotten. **Refused.** |
| B | Move to `reg-bb-api`; both depend on api | DAOs aren't really "API" semantically (interfaces are; concrete persistence is implementation), but pragmatic. The DAO methods (`findScreenByCode`, `listFieldsForScreen`, etc.) wrap `FormDataDao` (Joget core), so the dependency footprint is small. |
| C | Each bundle has its own DAO | ~7 lookup methods duplicated; risk of drift; but each bundle self-contained |
| D | Fourth bundle `reg-bb-mm-dao` | Architecturally cleanest; DAO is its own responsibility; over-decomposition for Phase 1 scale |

**Recommendation: B (move to `reg-bb-api`).** Pragmatic. The DAO surface is stable (driven by the 12 fixed `mm_*` entities); duplication risk is low; the API bundle's classpath remains thin (Joget core + value types). If Phase 2 expands the DAO surface (audit table reads, capability-registry reads), revisit and move to D.

**Justifying principle:** SRP-leaning compromise. Strictly speaking D is more SRP-aligned, but the cost (extra bundle, extra Activator, extra deploy) outweighs the benefit at Phase 1 scale.

**Reversibility: cheap.** Moving the DAO from `reg-bb-api` to a new `reg-bb-mm-dao` is a refactor, not a contract break. Consumers update their `Import-Package` and rebuild.

### Q2. AST shape on disk in `mm_determinant.ruleJson`

Two states the rule can be in: DSL source string, or pre-parsed AST JSON.

| Option | Detail | Evidence |
|---|---|---|
| A | DSL source string in `ruleJson`; engine parses on first eval, caches AST in JVM | Today's shape: `{"dsl":"parcel.agroZone == 'lowlands'"}`. Parser is fast (sub-ms). No on-disk migration risk. |
| B | Pre-parsed AST JSON; publisher parses once, persists | Spec §4.3.0 calls this out for fast-path-eligible rules. Eliminates per-JVM-restart parsing. |
| C | DSL source for SQL-path; pre-parsed AST for fast-path; editor decides at save | Spec position (§4.3.0 last paragraph). The editor produces JSON-AST shape for fast-path and DSL source for SQL-path. |

**Recommendation: C.** This is what the spec specifies. The editor is in control; the engine handles both shapes uniformly (parse the source if it's a string; use directly if it's an AST). The AST cache in the evaluator is keyed by `(determinantId, serviceVersion)`; the on-disk shape is the source of truth.

**Reversibility: cheap to medium.** Switching shapes mid-flight is a one-shot migration over `mm_determinant` rows. Editor and publisher need coordinated updates.

### Q3. `EvalContext.data` shape

Resolves `$applicant.<storageKey>` references at runtime.

| Option | Detail |
|---|---|
| A | Flat `Map<String, Object>` keyed by `storageKey` | `data.get("district") = "Maseru"`. Visitor walks AST, calls `data.get(storageKey)` per `$applicant` ref. Simple, cheap, matches the storeBinder rowSet shape we already have (every `c_<storageKey>` is in the saved row). |
| B | Nested `Map<String, Map<...>>` keyed by hierarchical paths | `data.get("applicant").get("district")`. Aligns with potential future multi-subject (`$applicant.spouse.dateOfBirth`). |
| C | Typed POJO record per service | Strong types; codegen burden; doesn't fit a metadata-driven engine |

**Recommendation: A.** Today's storeBinder produces flat keys; the evaluator's natural fit is reading from that shape. Multi-subject (B) becomes relevant when `$applicant.spouse.*` rules appear — Phase 3+. When they do, the visitor can be extended to recognise dotted paths and synthesise nested lookups. **DIP**: depend on the simplest shape that satisfies today; add levels when justified.

**Reversibility: medium.** Visitor logic changes; cache key dataHash logic changes; storeBinder data extraction changes. Manageable but touches several places.

### Q4. Where does the evaluator hook into the application save lifecycle in Phase 1?

| Option | Detail | Evidence |
|---|---|---|
| A | `RegBbApplicationStoreBinder` (subclass of `WorkflowFormBinder`) | Joget standard contract; spec §6.5 step 943. No new infra. Two-transaction issue per §7.5. |
| B | XPDL Tool activity in a `farmer_application_submission` workflow | Spec §6.6 sequence diagram step "WF→Eng tool task". Requires authoring an XPDL package, registering `RegBbEvaluatorTool` (Phase 2 anyway), running the WorkflowManager — heavy lift for Phase 1. |
| C | A new servlet at `POST /regbb/submit` invoked by a custom Submit button | Bypasses Joget save lifecycle; loses the form-binding chain; unidiomatic. Refused. |
| D | Spec-aligned `MetaScreenElement.submit(applicationId)` per spec §6.5 | The eventual right shape. Requires the full review-screen-as-`mm_screen` model in place. Phase 3. |

**Recommendation: A for Phase 1; D for Phase 3.** A is Joget-native (per CLAUDE.md HARD RULE), gives us end-to-end demo today, and retires cleanly when D arrives. The two-transaction concern (§7.5) is mitigated by the never-leave-outcome-null discipline.

**Reversibility: cheap.** The storeBinder is a small class; replacing it with the spec submit path is a swap, not a rewrite.

### Q5. Schema for evaluation outcomes — one column or audit table

| Option | Detail | Evidence |
|---|---|---|
| A | One `eligibility_outcome` JSON-as-text column on the application form | Self-contained; one row, one outcome; Phase 1 simplest. Can't represent multiple evaluations of same application (e.g., re-eval after registry refresh). |
| B | `app_fd_reg_bb_eval_audit` table from day one | Spec §7.4. Multiple rows per evaluation; trail. But: one row per *Determinant evaluation*, not per *application*. Aggregated outcome would still need to live somewhere — likely the column anyway. |
| C | Both (A + B) | Column carries the latest aggregate; table carries per-rule trail. Spec-aligned. Simplest path for Phase 2. |

**Recommendation: A in slice 1A; A+C in slice 1B-1C.** Earn the audit table when there's a second consumer (operator's "explain this decision" affordance, or registry-refresh re-eval). Slice 1A's demo doesn't yet need it.

**Reversibility: cheap.** Adding the audit table later is additive — no schema change to the column.

### Q6. Atomicity of save + evaluate

Already covered §7.5. Two transactions; never leave outcome null on a saved row; evaluator never throws to caller.

**Recommendation:** as written. Joget's session lifecycle (per `jw-community/wflow-core/src/main/java/org/joget/apps/form/dao/FormDataDaoImpl.java`) opens a session per `saveOrUpdate`; wrapping two writes in one Hibernate transaction would mean managing session lifecycle outside Joget's pattern. Two transactions is the SRP-respecting choice — each save has one reason to commit.

**Reversibility: medium.** Switching to one transaction would require refactoring the storeBinder to manage Hibernate sessions; possible but invasive.

### Q7. Caching layers in Phase 1

Per spec §8.4: L1 (per-thread), L2 (Caffeine per-JVM), L3 (Hazelcast per-cluster). Phase 1 doesn't need full three-tier.

**Recommendation:**
- **Slice 1A: L1 only.** Single submit, single evaluation per Determinant, no per-keystroke pressure. Keep it simple; add L2 when slice 1D introduces per-keystroke field-scope evaluation (where overlapping rules on the same field actually share work).
- **Slice 1D: add L2 (Caffeine).** Wire it cleanly behind the same evaluator interface; L1 remains the per-request cache.
- **Slice 1F or Phase 2: add L3 (Hazelcast).** Earns its keep when cluster sizes >1.

**Reversibility: trivial.** Each cache layer is a wrapper around the evaluator; adding a layer is composing one more decorator.

### Q8. Concurrency model

| Concurrency event | Behaviour |
|---|---|
| Two operators reviewing same application | Each gets their own `EvalContext`; both hit cache. No write conflict — neither writes to `app_application` from the eval path. |
| Citizen submitting while service is being republished | Citizen's `serviceVersion` is pinned; new publish creates new version; old version's cache entries still valid. No conflict. |
| Evaluator service stopped mid-evaluation | StoreBinder's lookup returns null; `disposition=indeterminate` written. No partial state. |
| Two citizens hitting `evaluateScreen` for same rule | Cache hit on second call; first does the work, second reads. Cache writes are atomic (Caffeine + Hazelcast both single-writer-per-key). |
| Cache invalidation race (data write + concurrent eval) | Worst case: a stale read; cache key includes `dataHash`, so the next eval after data write hits a different key. Bounded staleness, max one Ajax round-trip. |

No locks. No transactions across cache + DB. Eventual consistency on cache; strict consistency on DB writes. Spec §8.4 is the authority.

### Q9. The five adapter contracts — covered in §5

Each adapter's contract is fully specified in §5. Open work: ApiServiceProvider class for §8 REST surface (§5.5) requires API Builder source-side implementation; Phase 2.

### Q10. Publisher-side validation depth

| Option | Detail |
|---|---|
| A | Parse rules, check refs resolve, check targets exist | Spec §4.3.4 minimum. |
| B | A + classify route (fast vs SQL) and store on AST | Spec §4.3.4 + §8.6.1. |
| C | A + B + dry-run evaluate against a stub `EvalContext` to catch runtime issues | Aggressive. Helps catch resolver-disagreement bugs but doubles publish time. |

**Recommendation: B.** Spec position. Dry-run is a Phase 2 nice-to-have once IM stub-mocking infrastructure is built.

### Q11. Rule grammar evolution — additive only

Spec §4.3.3 last paragraph: adding a fast-path operator follows a deliberate five-step process (operator table, evaluator code, publisher validation, tests, release). DSL grammar evolution is a separate process owned by `rules-grammar`.

**No design choice here** — already settled by spec. Documented for completeness.

---

## 13. Phasing — slicing delivery

Each slice independently demonstrable end-to-end. Per slice: scope, exit criterion.

### Slice 1A — first rule, fast path, post-store hook

- One rule on PRG_2025_001 (recommended: `$applicant.district in $constant.lowlands_districts`)
- `FastPathEvaluator` with the 8 operators slice 1A needs (`eq`, `neq`, `in`, `notIn`, `and`, `or`, `not`, simple comparisons), L1 cache
- `RegBbApplicationStoreBinder` + storeBinder wired in form definition
- Outcome written to `c_eligibility_outcome` JSON column
- Operator identity header extended to render disposition (extends today's build-31)
- Three new bundles scaffolded: `reg-bb-api`, `reg-bb-evaluator`; trim of `reg-bb-engine`

**Exit criterion:** Lerato (Maseru, lowlands) → PASS; Tšepiso (Mokhotlong, mountains) → FAIL with the rule's `failMessage`. Stop the evaluator bundle → indeterminate disposition appears, demonstrating the OSGi split paid off. ~1 day of work.

### Slice 1B — SQL path, registry-touching rule

- `SqlPathEvaluator` (delegates to `joget-rules-api`'s `RuleScriptCompiler`)
- `RoutingEvaluator` (AST-analysis dispatcher)
- One `$registry.*`-touching rule on PRG_2025_002 (mountain-pulses programme)
- `app_fd_reg_bb_eval_audit` table created (slice 1B is the right time per Q5)
- `RegistryReferenceResolver` interface implemented by a slice-1B IM-connector stub (real connector lands in 1E)

**Exit criterion:** Both fast and SQL paths produce correct outcomes for representative rules. Audit rows visible.

### Slice 1C — multi-rule scoring per D6

- Per-Registration aggregation logic
- PRG_2025_003 score-based programme with 3+ Determinants
- Operator inbox sortable by `decision_score` (already wired in today's issue #3)

**Exit criterion:** A score-based application produces `pending_review` disposition correctly; operator can sort and triage.

### Slice 1D — field-scope and screen-scope conditional UI

- `evaluateScreen` adapter + `/regbb/eval/screen` servlet
- `regbbWatch` JS in `MetaScreenElement`
- L2 cache (Caffeine)
- Field-scope and screen-scope Determinants in PRG_2025_001

**Exit criterion:** A field shows/hides based on a watched field's value, server-authoritatively, debounced.

### Slice 1E — fee + required-doc + role Determinants; real IM connector

- `computeFees`, `resolveRequiredDocs`, role activation paths
- `reg-bb-im-connector` plugin (real implementation, replacing slice-1B stub)
- `farmers.byNid` and `farmers.parcels.summary` capabilities

**Exit criterion:** All four programmes in Phase 1 plan §D5 evaluate end-to-end against test fixtures.

### Slice 1F — publisher integration; AST persistence; editor integration

- `reg-bb-publisher` runs validation + classification + persists ASTs
- `joget-rule-editor` integrated with `mm_determinant.ruleJson`
- Publish triggers cache invalidation

**Exit criterion:** Operator authors a new rule end-to-end through the editor, hits Publish, sees it evaluate live.

### Slice 2 — adapters: hash variable, process tool, REST

Out of Phase 1 scope. Phase 2 deliverable.

### Slice 3 — operator review screens + spec-aligned submit (`MetaScreenElement.submit`)

Phase 3. Retires `RegBbApplicationStoreBinder`.

---

## 14. Architectural risks

Genuinely worrying things; not bugs. Each with mitigation today, plus what we can do later if today's mitigation is insufficient.

| # | Risk | Today's mitigation | If insufficient |
|---|---|---|---|
| R1 | Routing decision cached at publish; rule-references-capability change makes cached routing wrong | Republish triggers `invalidateService`; classification re-runs | Add a capability-change hook in IM that triggers `invalidateService` of all services referencing it |
| R2 | SQL path's compiled-SQL cache evicts but registry data has not (different TTLs) | Cache key includes `registryFetchEpoch`; SQL recompile on cache miss is cheap | Acceptable; revisit if recompile shows up on profile |
| R3 | `dataHash` collision (two distinct data values hash to same key) | Use a strong hash (SHA-256 truncated); collision probability negligible at expected scale | Switch to full SHA-256; ~50% size cost on cache key |
| R4 | `MetaModelDao` placement chosen B (in api bundle); evolves into a heavier DAO surface | Phase 2 review point; movable to D (separate bundle) without contract break | Q1's "reversibility: cheap" claim holds |
| R5 | `RegistryReferenceResolver.resolve` blocks the eval path | IM connector's circuit breaker + IM-side cache mean blocking is bounded | Async resolution with deferred-toggle pattern (spec §6.4 already specifies this for per-keystroke field rules); generalise |
| R6 | Audit table grows unboundedly | `RegBbAuditPurger` job, 1-year retention | Tune retention per regulator's requirement; partition table if volume warrants |
| R7 | OSGi service lookup race during bundle restart | `PluginManager.list` is thread-safe; null result → graceful degradation | OSGi service tracker pattern if degradation is too noisy |
| R8 | Spec divergence (Lesotho-instance DSL vs published RegBB twenty-operator surface) | Documented honestly per ADR-001 r2 §5.2 | Restrict to fast-path subset for cross-jurisdiction rule sharing if/when it matters |

---

## 15. Glossary

| Term | Definition |
|---|---|
| **Determinant** | A rule (`mm_determinant` row) plus its scope, action, and scoring metadata |
| **Evaluator** | The Java component that takes `(determinantId, EvalContext)` and produces `EvalResult` |
| **Fast path** | In-memory tree-walker over the closed twenty operator subset; for `$applicant.*`-only rules; <1 ms |
| **SQL path** | Compile-to-SQL evaluator delegating to `joget-rules-api`; for `$registry.*`-touching rules and aggregation; 5–50 ms |
| **Routing** | The decision of which evaluator (fast vs SQL) handles a rule; computed at publish, cached on AST |
| **Scope** | Which of the four eligibility/visibility/fee/routing questions a Determinant participates in answering |
| **Action** | What happens when the rule evaluates true (`hide`, `requireDoc`, `applyFee`, etc.) |
| **`EvalContext`** | Immutable input bundle carrying applicant data, service version, selected registrations, current user, correlation id |
| **`EvalResult`** | Outcome of one evaluation — TRUE/FALSE/NULL/ERROR plus optional numeric value, action target, evaluator discriminator |
| **L1/L2/L3 cache** | Per-thread / per-JVM Caffeine / cluster-shared Hazelcast |
| **Material outcome** | An outcome that drives a visible change — fee applied, doc demanded, role assigned, eligibility decided. Material outcomes write audit rows; per-keystroke re-evaluations don't |
| **`registryFetchEpoch`** | A counter exposed by the IM connector that increments when its cache for `(source, path)` rolls; included in eval cache key |
| **Loud failure** | The evaluator never substitutes false for unknown — `outcome=ERROR` with cause is the response to any non-Boolean evaluation result. Spec P5 |
| **`allowSlowPath`** | Per-Determinant flag opting in to SQL-path classification in `field`/`screen` scopes; suppresses publisher warning |

---

*End of document v0.1. Open for review.*
