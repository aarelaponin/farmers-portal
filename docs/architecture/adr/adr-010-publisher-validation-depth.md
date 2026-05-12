# ADR-010 — Publisher-side validation depth

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §7.2, §12 Q10; `regbb-solution-architecture-spec.md` §4.3.4 (publisher validation), §8.5 (RegistryReferenceResolver `isResolvable`), §8.6.1 (routing classification at publish); ADR-001 r2 §3.4 (engine partitions by analysis, not by policy); ADR-003 (rule storage shape); `gs-plugins/reg-bb-publisher/src/main/java/global/govstack/regbb/publisher/service/ServiceValidator.java` (existing publisher entry point) |

---

## 1. Context

### 1.1 What "publisher validation depth" means

When an operator clicks "Publish" on a `mm_service` row, the
`reg-bb-publisher` plugin runs a series of checks on every Determinant
the service references. Failures abort publish with a clear message
naming the offending Determinant. Successes proceed: the publisher
flips status, calls `evaluator.invalidateService(serviceId)`, and the
new version is live.

The question: how much checking does the publisher do? Three points
on the spectrum:

* **Light** — parse rules, check refs resolve, check action targets
  exist. Spec §4.3.4 minimum.
* **Standard** — light + classify each rule (fast-path-eligible vs
  SQL-path) and store the classification. Spec §4.3.4 + §8.6.1.
* **Aggressive** — standard + dry-run evaluate each rule against a
  stub `EvalContext` to catch resolver-disagreement and runtime
  bugs.

### 1.2 The spec's position

Spec §4.3.4 specifies the standard-depth set:

* Parse the rule against the DSL grammar (parse failure aborts).
* Walk refs — every `$applicant.<storageKey>` resolves to a real
  `mm_field`; every `$registry.<source>.<path>` is reachable per
  `RegistryReferenceResolver.isResolvable(source, path)`; every
  `$constant.<key>` resolves to a `mm_catalog` value;
  every `$service.*` and `$registration.*` resolves.
* Walk action targets — `mm_field`, `mm_screen`, `mm_fee`, etc., as
  named by the action's `target`.
* Arithmetic operators only in `mm_fee.formulaJson`, never in
  Boolean rules.
* Classify each rule: fast-path-eligible vs SQL-path by AST
  analysis.
* Performance hygiene: in `field` / `screen` scope, an SQL-path
  classification triggers a publish-time warning unless
  `mm_determinant.allowSlowPath = true`.
* Failures abort with a clear message naming the offending
  Determinant.

Spec §8.5 specifies that `RegistryReferenceResolver.isResolvable`
is called once per `$registry.*` reference at publish time —
"publish fails with clear message if reference unknown."

Spec §8.6.1 specifies that the routing classification is computed
once at publish and stored on the cached AST; runtime evaluation
doesn't re-classify.

The spec stops at the standard depth. Dry-run (aggressive depth) is
not specified.

### 1.3 What dry-run would catch and at what cost

A dry-run evaluates each rule against a stub `EvalContext` —
typically a fully-populated synthetic applicant produced from
`mm_field` defaults — and asserts that the rule produces an outcome
without internal evaluator errors.

What it catches:

* **Resolver disagreement** — the publisher's `isResolvable` returns
  true (the resolver knows the ref name), but the actual resolution
  at runtime returns null or throws. A dry-run finds the
  disagreement before publish.
* **Runtime evaluator bugs** — a rule the publisher accepted because
  it parses and classifies cleanly produces a `RuntimeException` at
  evaluation. Rare, but rare bugs in the evaluator surface here.
* **Operator-side data shape mismatches** — the rule references
  `$applicant.parcel.agroZone` but the storageKey is actually
  `parcel_agroZone`. The publisher's ref-walk accepts either if a
  matching `mm_field` exists; the dry-run tries to read from the
  stub and fails.

The cost:

* **Doubled publish time.** Every rule runs a synthetic evaluation —
  fast-path microseconds for fast-path rules, milliseconds for
  SQL-path rules including IM round-trips (or stubs of them). For a
  service with 18 Determinants (per architecture doc §3.3
  `phase-1-plan §D5`), publish time roughly doubles compared to
  standard-depth.
* **Stub `EvalContext` construction is non-trivial.** A complete
  stub applicant requires defaults for every `mm_field`. Wrong
  stubs produce false negatives (publisher rejects a rule that
  works in production with real data).
* **Stub registry resolutions need careful design.** `isResolvable`
  doesn't return data; a dry-run needs the resolver to return
  *some* synthetic value. Either the resolver gains a "stub mode"
  or the publisher carries its own stub library.

### 1.4 The current state of the publisher

`gs-plugins/reg-bb-publisher/src/main/java/global/govstack/regbb/publisher/service/ServiceValidator.java`
exists; the bundle is scaffolded with four classes (per repo
inventory). Slice 1F (architecture doc §13) is when the publisher's
full integration ships — including AST persistence and editor
integration. The publisher's validation depth is therefore an
implementation choice for slice 1F; this ADR sets the target depth.

### 1.5 What `RegistryReferenceResolver.isResolvable` provides

Per spec §8.5, `RegistryReferenceResolver.isResolvable(String source,
String path)` returns true if the resolver recognises the source/
path pair. It does not check whether *any specific subjectId*
resolves; it answers "is this ref name part of my contract?". The
runtime call `resolve(source, path, subjectId, ctx)` is what fetches
the actual data.

This is the right granularity for publish-time validation: the
publish doesn't have a real subjectId to test against, so it can
only check that the refs are recognised. Going further (a dry-run
with a stub subjectId) adds the resolver-disagreement coverage but
requires the stubs §1.3 names.

### 1.6 The performance-hygiene warning's scope

Per spec §4.3.4 and architecture doc §7.2, the publisher warns
(does not reject) when a `field` or `screen` scope rule classifies
as SQL-path without `allowSlowPath=true`. This is a performance
warning, not a correctness one. Operators can choose to override or
to refactor the rule. The warning has a real value: it surfaces
performance risk before deployment.

### 1.7 Principles in scope

* **Spec alignment** — diverging from the spec's documented validation
  depth carries the same cost ADR-001 r2 §5.2 names: cross-jurisdiction
  rule portability is reduced. Standard depth is the spec's depth.
* **OCP / additive** — light → standard → aggressive is an additive
  sequence; aggressive can ship later without breaking standard's
  contract.
* **Honest negative surface** — publish-time errors that catch what
  would otherwise be runtime errors are net wins; publish-time
  errors that produce false rejects are net losses.
* **Phasing** — slice 1F is where the publisher's full validation
  lands; later slices can extend.

---

## 2. Decision

The publisher implements **standard-depth validation** at slice 1F,
matching spec §4.3.4 + §8.6.1:

1. Parse each `mm_determinant.ruleJson` source via `rules-grammar`.
   Parse failure aborts publish with the offending Determinant
   named.
2. Walk every reference in the parsed AST:
   - `$applicant.<storageKey>` — assert a `mm_field` with that
     storageKey exists in scope (the screens reachable from the
     service via `mm_screen` rows).
   - `$registry.<source>.<path>` — call
     `RegistryReferenceResolver.isResolvable(source, path)`; assert
     true.
   - `$constant.<key>` — assert a `mm_catalog` row with that code
     exists.
   - `$service.*`, `$registration.*` — assert the path is valid for
     the corresponding `mm_service` / `mm_registration` row.
3. Walk every action target (`mm_field`, `mm_screen`, `mm_fee`,
   `mm_required_doc`, `mm_role`) — assert the target exists.
4. Assert arithmetic operators (`add`, `sub`, `mul`, `div`, `min`,
   `max`, `if`) appear only in `mm_fee.formulaJson` rules.
5. Classify each rule via AST analysis: fast-path-eligible if every
   operator is in the closed twenty-operator subset (per spec
   §4.3.3) AND no `$registry.*` reference appears anywhere in the
   AST (per `decision-log.md` D14 conservative routing). Otherwise
   SQL-path.
6. Store the classification on the cached AST per service version
   (per ADR-003 §6.2).
7. For Determinants in `field` / `screen` scope classified as
   SQL-path, emit a publish-time *warning* (not abort) unless
   `mm_determinant.allowSlowPath=true`. Warnings appear in the
   publish UI; operators choose to proceed.
8. On all-checks-pass, flip `mm_service.status` to `published` and
   call `evaluator.invalidateService(serviceId)`.

Failures abort publish. Warnings surface in the publish UI but do
not abort.

**Dry-run validation (aggressive depth) is deferred to Phase 2.**
The infrastructure dependency — a stub-mocking framework for
`RegistryReferenceResolver` and a stub-applicant construction
library — does not exist in Phase 1. Building it for Phase 1 is
work disproportionate to the marginal coverage it adds.

---

## 3. Reasoning

### 3.1 Standard depth catches the realistic failure modes

The realistic publish-time errors (those an operator actually makes)
are: a typo in `$applicant.<storageKey>` that no `mm_field` matches;
an `mm_action.target` pointing at a deleted `mm_field`; an
`$registry.civil.maritalStatus` ref where the IM connector has no
`civil` source. Standard depth catches all three.

The unrealistic publish-time errors (those that aggressive depth
would catch) are: resolver-disagreement (publisher accepts a ref the
resolver later rejects); runtime evaluator bugs (parse-clean rule
that throws on evaluation); subtle data-shape mismatches that pass
the publisher's structural check but fail at runtime resolution.
These are rare; their realisation is also recoverable (the rule's
runtime failure produces `outcome=error`, audit row, indeterminate
disposition, operator triage). The cost of the bug is bounded.

The spec's standard depth is calibrated around this trade. Aggressive
depth doubles publish time and adds infrastructure for marginal
additional coverage on rare failure modes. Net cost-benefit favours
standard.

### 3.2 Spec alignment posture

ADR-001 r2 §5.2 establishes that divergences from the spec are
documented as deliberate Lesotho-instance choices. This ADR adopts
the spec's standard depth without divergence; cross-jurisdiction
rule portability is preserved at the publisher level (a peer
RegBB instance running the spec's standard validation accepts the
same rules our publisher accepts).

The diverge-or-conform calculation: for grammar (ADR-001), divergence
was justified by P1 (configuration over code) and P2 (excellent
authoring UX). For storage shape (ADR-003), divergence was justified
by OCP and reader DIP. Neither principle motivates diverging on
publisher validation depth — the spec's depth is right.

### 3.3 The performance-hygiene warning is the right shape

A hard reject for SQL-path rules in `field`/`screen` scope would be
too aggressive: there are legitimate cases (e.g., a field-scope rule
that needs `$registry.civil.maritalStatus` for a one-time conditional
visibility decision; the operator opts in via `allowSlowPath=true`).
A silent acceptance would be too permissive — the operator might not
realise the rule will run on per-keystroke Ajax with a registry
round-trip per call.

The warning surfaces the property at publish time without forcing the
analyst's hand; `allowSlowPath=true` is the explicit override.
Architecture doc §9.4 names this as performance hygiene; standard
depth implements it correctly.

### 3.4 Dry-run's stub infrastructure is real work; deferral is principled

§1.3 listed three infrastructure dependencies dry-run requires: stub
applicant construction (defaults from every `mm_field`), stub
registry resolution (`RegistryReferenceResolver.resolveStub` or
similar), stub fee/document/role resolution. None exists. Building
them for Phase 1 means:

* A stub-applicant builder that walks `mm_field` rows and emits
  defaults — non-trivial logic, especially for typed fields (date
  defaults? select-list defaults?).
* A stub `RegistryReferenceResolver` mode that returns synthetic
  data shapes — design questions about which shapes (a registry
  may return arrays, nested objects, etc.; stubs need to mirror).
* Plumbing in the publisher to invoke the evaluator in stub-mode
  without writing to caches or audit.

Phase 1's slice 1F has ~1 person-week of publisher work scoped
(architecture doc §13). Adding dry-run infrastructure could
double that. The trade isn't worth it for Phase 1.

Phase 2 has more headroom and a stronger case for dry-run (production
deployments have published at least one version; resolver-disagreement
bugs become more probable; a stub framework gets reused for testing
infrastructure). Earning dry-run at Phase 2 is the right phase
boundary.

### 3.5 Standard depth is the largest single-step improvement over light depth

Light depth (parse + ref/target check, no classification) leaves the
runtime to do its own classification on first evaluation. Standard
depth amortises the classification to publish time, where it belongs:
the publisher's job is to lock in everything that doesn't depend on
runtime data. Per spec §8.6.1: "classification is computed once at
publish and stored on cached AST; runtime evaluation doesn't
re-classify."

This has performance and correctness implications: a runtime
classification that disagrees with what the publisher would have
done introduces drift (a rule classified differently at different
JVM lifetimes). Publish-time classification eliminates the drift.
Standard depth is the right minimum.

### 3.6 The performance-hygiene warning leverages the classification

Because standard depth classifies at publish time (item 5 of §2),
the warning condition (item 7 of §2) can fire — the publisher knows
the rule's path. Light depth does not classify, so the warning
cannot be issued at publish time; it would have to fire on first
runtime evaluation, which is too late for the operator to react.
The warning is structurally tied to standard-depth classification.

---

## 4. Alternatives considered and refused

### 4.1 Option A — light depth (parse + ref/target check)

**Refused on classification timing and warning surface.** Without
publish-time classification, the routing decision is deferred to
runtime; the performance-hygiene warning has no place to fire (the
operator would discover the slow rule only on first per-keystroke
Ajax).

This option is also one step away from spec §8.6.1; adopting it
would be a documented divergence with no benefit beyond saving the
classification logic at publish time, which is a one-pass AST walk
at sub-millisecond cost per rule. Net: no benefit, real cost.

### 4.2 Option B — standard depth (the decision in §2)

The chosen option. Stated above.

### 4.3 Option C — aggressive depth (standard + dry-run)

**Refused on infrastructure cost vs marginal coverage.** §1.3
detailed the cost: stub applicant construction, stub resolver mode,
publisher plumbing. The coverage gain is rare failure modes that
have recoverable runtime behaviour (`outcome=error` + audit +
indeterminate disposition + operator triage). The cost-coverage
ratio is poor at Phase 1 scale.

This option is also constrained by stub fidelity: a stub applicant
that produces synthetic defaults may pass a rule that real data
would fail, or vice versa. False rejects (publish blocked on a
working rule) and false acceptances (publish allowed on a broken
rule) both undermine the publisher's promise. Dry-run is sound
when the stub framework is high-fidelity; building that framework
is work proportionate to a Phase 2 effort, not Phase 1.

This option earns reconsideration in Phase 2 if production
publishes reveal patterns of resolver-disagreement that would have
been caught.

### 4.4 Option D — light depth at slice 1F; aggressive at Phase 2

**Refused on the lost middle.** Skipping standard depth at slice 1F
means the runtime does classification on first evaluation, and the
performance-hygiene warning never fires. Both properties are spec-
required (§4.3.4 + §8.6.1); skipping them is a documented
divergence. The alternative of "standard at slice 1F, aggressive at
Phase 2" is the right phasing.

### 4.5 Option E — standard at slice 1F, with classification stored in `mm_determinant.classificationCache` on the row

A variant of standard: instead of caching classification on the
AST in the evaluator's per-JVM AST cache, persist it to a column on
`mm_determinant`. **Refused on cache-vs-source-of-truth confusion.**
The classification is derived data; persisting it means every
publisher run rewrites the column. The persisted column then
becomes a thing readers can read directly, bypassing the AST cache;
two sources of truth emerge. SRP says: classification is a
publisher-derived attribute of the AST; it lives on the AST, not on
the row. Spec §8.6.1 is consistent with cache-only.

---

## 5. Consequences

### 5.1 Positive

* Spec-conformant publisher; cross-jurisdiction rule portability
  preserved at the publisher level.
* Performance-hygiene warning fires at publish time, where it can
  influence design.
* Classification is locked in at publish; runtime doesn't re-derive.
* Dry-run is on the table for Phase 2 without committing to
  Phase 1 infrastructure.

### 5.2 Negative

* **Resolver-disagreement bugs surface only at runtime.** A rule
  that the publisher accepts based on
  `RegistryReferenceResolver.isResolvable` may fail on real
  resolution. The runtime path produces `outcome=error` with
  `cause=registry_unavailable` or similar; the operator sees
  indeterminate. Bounded; not silent.
* **Runtime evaluator bugs surface only at runtime.** A rule that
  parses and classifies cleanly may throw on evaluation. Bounded
  by the loud-failure discipline (audit + indeterminate); not
  silent.
* **The performance-hygiene warning's UX surface is publisher-
  dependent.** Slice 1F's publisher must render warnings visibly
  enough that operators see them; a buried warning is an unread
  warning. Documented as an implementation requirement.
* **Stub-mocking framework deferred to Phase 2.** When dry-run
  becomes desirable, the stub work surfaces. Phase 2's planning
  must include it.

### 5.3 Neutral

* `decision-log.md` D14 (conservative routing — any `$registry.*`
  → SQL path) is implemented as part of standard-depth
  classification. Consistent.
* The `mm_determinant.allowSlowPath` flag (per spec §4.3.4 and
  architecture doc §3.1) is a real `mm_*` field; the publisher
  reads it during the warning step.
* Failure messages naming the offending Determinant are a UX
  requirement of the publisher; the spec mandates this. Slice 1F's
  publisher must produce informative messages.

---

## 6. Implementation outline

Sketch.

### 6.1 Slice 1F

1. `ServiceValidator.validate(serviceId)` (in
   `gs-plugins/reg-bb-publisher/.../service/ServiceValidator.java`)
   becomes the standard-depth orchestrator. Methods:
   - `validateRules(determinants)` — parse each, walk refs, walk
     targets. Returns a list of validation errors and warnings.
   - `classifyRules(determinants)` — AST analysis per spec §4.3.3
     and D14. Stores classification on the publisher's
     publish-time AST cache.
   - `validatePerformanceHygiene(determinants)` — emit warnings
     for `field`/`screen` SQL-path rules without `allowSlowPath`.
2. Reference-walk implementation: visitor pattern over the parsed
   AST. For each reference type:
   - `$applicant`: read `mm_field` rows for screens reachable from
     the service's `mm_screen` set.
   - `$registry`: call
     `RegistryReferenceResolver.isResolvable(source, path)`. If no
     resolver is registered, treat as resolvable=false and emit a
     publish-time warning that `$registry.*` resolution will fail
     at runtime.
   - `$constant`: read `mm_catalog` rows by code.
   - `$service`, `$registration`: read the relevant `mm_*` row.
3. Action-target walk: for each `mm_determinant.actionJson.target`,
   read the target entity by code. Missing targets abort.
4. On success: write the parsed AST + classification to the
   publisher's cache; flip `mm_service.status`; call
   `evaluator.invalidateService(serviceId)`.
5. On failure: surface errors to the publisher UI (the
   `PublishUserviewMenu` already exists per repo inventory),
   abort, no status change.

### 6.2 Phase 2 (deferred)

1. Build a stub-applicant constructor that walks `mm_field` rows
   and emits synthetic defaults.
2. Add a `RegistryReferenceResolver.resolveStub(source, path,
   ctx)` mode that returns canonical synthetic shapes per source.
3. Add a publisher dry-run mode (`?dryRun=true` query param or
   admin-only toggle) that invokes the evaluator with the stub
   context and asserts no exceptions.
4. Document the stub library so other test infrastructure can
   reuse it.

---

## 7. Open questions

### 7.1 What level of failure-message detail does the publisher UI render

The spec mandates "clear message naming the offending Determinant";
in practice, operators benefit from seeing the rule's source line,
the failing reference's path, and (where applicable) the
similar-but-misspelled options the publisher checked. Default
position: include rule source, failing reference, similar refs (if
edit distance ≤2). Forwarded to slice 1F UI implementation.

### 7.2 Whether warnings persist across publishes

A warning emitted at v1.0's publish should re-emit at v1.1's
publish if the condition still holds. Default position: warnings are
re-derived at every publish from the rule's current state; no
stored warning history. Operators see the same warning until the
rule changes. Forwarded to implementation.

### 7.3 How aggressive depth is invoked when it ships

Operator opt-in (`?dryRun=true`)? Always-on at Phase 2? Tied to a
deployment environment (always-on in staging, opt-in in
production)? Forwarded to Phase 2 design.

### 7.4 The `allowSlowPath` default — Y or N

Currently per spec §4.3.4, default is N (warning fires). Some
deployments may prefer the warning never to fire by setting
`allowSlowPath=Y` as the default. Default position: keep N (the
warning is information; defaulting to suppression hides it).
Confirmed by this ADR; mentioned for completeness.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` §4.3.4: no change. Standard
depth is what the spec specifies.

`determinant-architecture.md` §7.2 is consistent with this ADR. §12
Q10 is replaced with a backlink to this ADR.

---

## 9. Decision record

| | |
|---|---|
| Decision | Standard-depth validation at slice 1F (parse + ref/target check + classification + performance-hygiene warning); aggressive (dry-run) deferred to Phase 2 |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1F ships)* |

---

*End of ADR-010.*
