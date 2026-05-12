# ADR-001 — Canonical rule grammar for the converged Farmers Portal / RegBB engine

| | |
|---|---|
| Status | **Proposed (revision 2)** — supersedes revision 1 of 2026-04-28 |
| Date | 2026-04-28 |
| Deciders | Farmers Portal architecture team |
| Consulted | RegBB spec authors; Lesotho operator analyst (rule-authoring UX feedback) |
| Informed | Engineering, GovStack reviewers |
| Supersedes | ADR-001 revision 1 (closed-grammar-only proposal) |
| Related | `_design/architecture-overview.md` §2.4, §3.1, §6.1 (Horizon-1 closeout); `_design/mm-completeness-audit.md` §4.C, §6.4 #3 |

> **Revision note.** Revision 1 of this ADR proposed adopting the closed twenty-operator grammar as the sole evaluator and retiring the DSL stack, with the closed-grammar property as the primary architectural commitment. On reconsideration, two principles stated more strongly than the revision 1 analysis weighted them must be honoured: **(P1) configuration over code on both citizen and registry sides — operators must author rules directly without engineering tickets**; and **(P2) excellent UX for citizens and back-office — standardisation is not pursued at the cost of authoring UX**. Revision 2 records the decision that flows from those principles. The reasoning sections of revision 1 remain available in git history; revision 2 supersedes it in full.

---

## 1. Context

The Farmers Portal carries two parallel rule grammars and has not yet decided how they relate. The decision must be made before any work begins on the metadata-driven engine, because the engine's implementation, the operator authoring UX, and the cross-cutting evaluator design all depend on which grammar wins, or how they coexist.

### 1.1 The closed twenty-operator grammar

Defined in `regbb-solution-architecture-spec.md` §4.3.3. Thirteen Boolean/comparison operators (`eq`, `neq`, `lt`, `lte`, `gt`, `gte`, `in`, `notIn`, `matches`, `exists`, `and`, `or`, `not`) plus seven arithmetic operators (`add`, `sub`, `mul`, `div`, `min`, `max`, `if`) scoped to fee formulas. Implemented as an in-memory Java tree-walker in `reg-bb-engine.DeterminantEvaluator`. Aggregation, set comprehension, filtered set lookups, string mutation, and statistical functions are deliberately rejected by the spec — rules answer "is this true?" and "how much?", and aggregation is treated as data-prep that belongs upstream in the registry layer.

### 1.2 The DSL eligibility engine stack

Built in `gs-plugins/` as four plugins: `rules-grammar` (ANTLR 4 parser, pure parser/AST), `joget-rules-api` (REST API plugin, compiles DSL → SQL via `RuleScriptCompiler`), `joget-rule-editor` (admin authoring UI with in-place validation, compile-on-save, test-against-sample-applicant), `subsidy-eligibility-runtime` (workflow Tool plugin `EligibilityRuntime` that loads a ruleset, compiles to SQL, runs the SQL filtered to one applicant, persists per-rule pass/fail).

Compiles DSL source to SQL and executes against the form-data tables. Therefore handles aggregation, filtered set lookups, joins, and temporal queries natively. The grammar is closed at the parser level — new operators require an ANTLR change and a `RuleScriptCompiler` release; there is no `eval()` because the source language has no `eval` construct; there are no plugin-loaded operators.

State today: schema in place, four plugins built and deployed, **zero rulesets bound to programmes**. The DSL is dormant on the application path.

### 1.3 The principle reweighting that drives the decision

Two principles, when applied strictly, change which grammar configuration is correct:

**P1 — Configuration over code on both citizen and registry sides.** Operators must be able to express new rules — including rules that aggregate, filter, or reach across the registry — directly in an authoring tool, without engineering tickets. This rules out any architecture that pushes aggregation off the rule-authoring surface and onto the engineering team, because every new programme that needs a new aggregate would block on engineering.

**P2 — Excellent UX for citizens and back-office.** Authoring UX is not sacrificed to satisfy a structural cleanliness constraint. The DSL editor's syntax-validating, compile-on-save, test-against-sample-applicant affordances are the working baseline; any approach that loses them, or asks operators to author rules in a regressed UI, is rejected.

### 1.4 Why a closed-grammar-only approach (revision 1) fails these principles

Revision 1 of this ADR proposed the closed grammar as sole canonical evaluator, with aggregation handled by a denormalisation discipline on the registry side via the future `derived-snapshot-runtime`. Two failures emerge under P1 and P2:

- **P1 failure: the denormalisation discipline pushes work to a layer that is not currently configurable by operators.** Today's `farmer-derived-plugin` requires Java to define new derivations. Generalising it into `derived-snapshot-runtime` could make the field-mapping configurable, but the source projection (the SQL or query that computes a value from N source forms) remains code. To fully satisfy P1, `derived-snapshot-runtime` itself would need to expose a no-code authoring surface — which is, structurally, a query DSL. The result is two grammars and two authoring tools (Determinants over a closed twenty-operator set, and derivations over a separate query language), with a fuzzy operator-facing boundary between "is this a Determinant or a derivation?".
- **P2 failure: the closed-grammar Determinant Builder UX is a regression from the existing DSL editor.** The DSL editor today validates syntax in-place, compiles on save, and supports test-against-sample-applicant. A tree-builder over JSON Determinants can be made acceptable for shallow rules but degrades fast at depth >3 (nested `and`/`or`/`not`/`if`). Asking analysts to give up source-language authoring for a tree builder in pursuit of operator-set simplicity is precisely the trade P2 forbids.

### 1.5 What follows from the principles

The principles imply: one authoring grammar; one editor; configuration over code on both sides — meaning aggregation is expressible at the rule-authoring layer, not pushed to a downstream pipeline. The DSL grammar already meets these conditions. The closed twenty-operator set is a strict syntactic subset of the DSL grammar (every closed-grammar operator is also an operator in `rules-grammar`'s ANTLR grammar). The closed grammar's runtime properties — fast in-memory tree-walking, suitable for per-keystroke Ajax — are therefore preservable as an *internal optimisation* of the engine, not as a separate authoring concern.

---

## 2. Decision

**Adopt the DSL grammar (defined by `rules-grammar`'s ANTLR grammar) as the canonical authoring grammar for all `mm_determinant.ruleJson` records. Use one editor (`joget-rule-editor`) for all Determinant scopes. Implement two evaluators in the engine, chosen automatically by static analysis of each rule:**

- **Fast-path evaluator (in-memory Java tree-walker)** — used when a rule's AST contains only operators in the closed twenty-operator subset *and* references only `$applicant.*` and `$constant.*` (no `$registry.*`, no aggregation, no filtered set lookup). Evaluation is microsecond-scale; suitable for screen render, Ajax conditional UI, and form-load actions.
- **Compile-to-SQL evaluator (existing DSL runtime)** — used when a rule's AST contains any operator outside the closed twenty subset *or* references `$registry.*` *or* uses aggregation/filtering. Evaluation is millisecond-scale; suitable for submit-time eligibility, server-authoritative routing, fee computation, document resolution, and role activation.

The selection is performed by the engine at first evaluation and cached with the AST. Operators do not see the partition — they author every rule in one editor, in one language. The publisher validates each rule against the DSL grammar at publish time; the engine determines the routing.

**The closed twenty-operator set is preserved in the spec as the fast-path operator subset, not as the canonical grammar.** It is the contract the engine relies on to route a rule to the in-memory evaluator. Adding to it follows the existing five-step additive-only protocol, because additions affect what runs on the fast path. Adding to the DSL grammar follows a separate process owned by the `rules-grammar` plugin's release cadence.

---

## 3. Reasoning

### 3.1 One language, one editor satisfies P1 and P2 directly

Operators author every rule in `joget-rule-editor`. The editor's existing UX — syntax validation, compile-on-save, test-against-sample-applicant — is preserved without rebuild. Any rule, simple or complex, is expressible in the same language; aggregation does not require an engineering ticket. The principles are honoured by construction.

### 3.2 The closed-set discipline transfers, it does not disappear

Spec §2.1 P2 ("Rules are data; the operator set is closed") is preserved at the parser level rather than at the operator-enum level. The DSL grammar is closed by the ANTLR grammar definition. New operators require the same kind of deliberate, additive-only release process — authored once in the grammar, implemented in the SQL compiler, validated by the publisher, accompanied by tests, released as a new `rules-grammar` version. The closure property is transferred from a 20-operator boundary to a parser-grammar boundary; what changes is the boundary's location, not its existence.

### 3.3 The fast path preserves the performance and security properties of the closed grammar

For the rules where the closed twenty-operator subset is sufficient (in-memory `$applicant.*` references, simple Boolean/comparison/arithmetic), the fast-path evaluator is identical to the closed-grammar evaluator described in spec §8. Same Java code, same audit shape, same security surface, same Ajax round-trip behaviour. The fast path is what the closed-grammar approach gave; this decision does not lose it.

For the rules where the DSL grammar is needed (aggregation, registry references), the SQL compile-and-execute path is used. It is slower (database round-trip rather than in-memory), it has a different audit shape (rule source + compiled SQL + result-set hash, rather than AST evaluation tree), and its security surface is what `joget-rules-api` already implements (parameterised SQL via the compiler — no operator-supplied raw SQL ever reaches the database).

### 3.4 The engine partitions by analysis, not by policy

The route a rule takes is determined by the rule's content, not by which scope it appears in or by an operator's preference. A `field` Determinant that happens to reference `$registry.civil.maritalStatus` is routed to the SQL evaluator (because it touches the registry); a `eligibility` Determinant whose entire content is `$applicant.age >= 18` is routed to the in-memory evaluator (because it touches only the application). This makes the partition implementation-private and stable against operator authoring choices.

### 3.5 The architecture-overview's strategic posture survives intact

Spec conformance is interpreted as RegBB-shaped — same component decomposition, same `mm_*` model, same workflow binding contract, same submission backbone. The rule grammar, however, is documented as a deliberate Lesotho-instance choice that extends the spec's twenty-operator set with an ANTLR DSL grammar. Other RegBB implementations adopting the published GovStack spec without the DSL extension run a strict subset of what this implementation runs; rules expressed in the closed twenty-operator subset are portable, rules using DSL extensions are not. This is documented honestly rather than concealed.

---

## 4. Alternatives considered and refused

### 4.1 Option A — Closed grammar as sole evaluator; DSL retired

**Refused under the reweighted principles.** Forces denormalisation at the registry layer for any aggregating rule, which (a) shifts authoring work to a layer that is not currently no-code (P1 failure), and (b) requires building a Determinant Builder UX that regresses on the DSL editor's affordances (P2 failure). See §1.4 for the detailed analysis.

This option remains defensible under different principles — specifically, under a stronger weight on cross-jurisdiction RegBB conformance and operator-set simplicity, with weaker weight on operator authoring UX and registry-side configurability. Those weights are not the project's principles. Revision 1 of this ADR recorded the reasoning at length; readers wanting that perspective should consult git history.

### 4.2 Option B — Expand the closed twenty-operator grammar to include aggregation

Add `sum`, `count`, `avg`, `forall`, `exists-with-filter`, `now`, `durationSince`, etc., directly to the closed grammar's operator enum.

**Refused.** This is the highest-cost, lowest-reward path:
- Reimplements aggregation in Java over a tree-walker, when the DSL already has a working compile-to-SQL pipeline that does it correctly.
- Forces scope-conditional grammar (operators that work in `eligibility` but not in `field`/`screen` because aggregation is too slow for per-keystroke evaluation), losing the closed grammar's elegance.
- Forces semantic disambiguation in the closed grammar's opaque `$registry.x.y` references (filter semantics, ownership scope, retired-row inclusion) that the DSL today resolves naturally with explicit JOIN/WHERE.
- Builds a query engine in Java, slower and less audited than the DSL's SQL.
- Erodes the closed-set property without delivering the DSL's UX or expressiveness gains.

The decision adopted in §2 achieves the same expressive power as Option B with materially lower implementation cost.

### 4.3 Option D — Run two grammars in parallel as separate authoring surfaces

Keep the closed grammar for some scopes and the DSL for others, with two editors and two authoring traditions.

**Refused.** This is roughly the situation today, and it is what the audit flagged as the strategic problem. Two parallel authoring surfaces produce two parallel mental models, two parallel audit shapes, drift in operator semantics over time. Spec §2.1 P6 ("citizen experience is server-authoritative" — single evaluator) is preserved by §2's decision because the engine's *entry-point* contract is single; the two evaluators are an implementation detail behind a single AST-based interface.

---

## 5. Consequences

### 5.1 Positive consequences

- **One authoring grammar; one editor.** Operators learn one language and one tool. The DSL editor's UX is preserved without rebuild.
- **Aggregation is operator-authorable.** New programmes can be configured with aggregating rules without engineering tickets.
- **Fast path preserved.** Simple in-memory Boolean rules evaluate at microsecond scale; Ajax conditional UI continues to work as spec §6.4 describes.
- **Closed-set discipline preserved at the parser level.** The grammar remains closed; new operators require deliberate release work.
- **Existing DSL infrastructure is leveraged, not retired.** Four plugins remain in production rather than scheduled for disposal.
- **The denormalisation pattern remains optional rather than mandatory.** Operators can still pre-compute aggregates on the registry side when performance demands it (the fast path is preferable for hot paths), but they are not forced to do so when the DSL evaluator is acceptable.

### 5.2 Negative consequences

- **Spec divergence from the published GovStack RegBB twenty-operator surface.** Documented as a deliberate Lesotho-instance choice; cross-jurisdiction rule portability becomes "rules expressed in the fast-path subset are portable; rules using DSL extensions are not." Whether this matters depends on whether peer-jurisdiction rule sharing materialises (today: aspirational).
- **Two evaluators in the engine.** Operationally one entry point but internally two code paths. Adds complexity to engine implementation and to evaluator testing. The boundary is well-defined (AST analysis), but it must be tested rigorously to ensure no rule routes to the wrong evaluator.
- **Two audit shapes.** Closed-grammar evaluations produce AST evaluation trees; DSL evaluations produce rule-source + compiled SQL + result-set descriptions. Operator review tooling (the audit panel on the back-office review form) must render both shapes coherently.
- **Performance discipline still recommended.** Although aggregation is no longer mandatory denormalisation, performance-critical rules that can be authored in the fast-path subset *should* be — the engine cannot prevent an operator from writing `count($registry.farmers WHERE districtCode = $applicant.districtCode) > 0` when an in-memory `$applicant.districtCode in $constant.targetDistricts` would do. Authoring guidance and the editor's hint surface ("this rule will run in the SQL evaluator; consider pre-computing on the registry") are productivity tooling worth investing in, but are not enforcement.

### 5.3 Neutral consequences

- The architecture-overview §2.4 ("DSL eligibility engine sub-system — built but dormant on application path") changes substantively: the DSL is no longer dormant or "complementary"; it is the canonical grammar. Section is rewritten to reflect this.
- The "two complementary layers" framing in architecture-overview §3.1 (citizen self-attestation by `form-quality-runtime` + server-side authoritative by DSL stack) is updated to "single rule grammar evaluated server-side, with the citizen's self-attestation answers captured as `$applicant.*` data alongside other application fields and re-evaluated authoritatively at submit."
- The Horizon-1 closeout in architecture-overview §6.1 still includes "DSL eligibility engine integration on application path", but the integration is now first-class, not a parallel to the closed grammar.
- `regbb-solution-architecture-spec.md` requires several substantive edits to reflect the partition; see §8 below.

---

## 6. Implementation outline

This is a *sketch*, not a plan.

### Phase 1 — Acceptance and spec revision (Week 0)
- This ADR is reviewed and accepted (or rejected, in which case revision 1's recommendation is reopened).
- `regbb-solution-architecture-spec.md` edits in §8 below are applied.
- `architecture-overview.md` §2.4, §3.1, §6.1 revised to reflect the decision.

### Phase 2 — Engine evaluator partition (Weeks 1–4)
- Specify the AST-analysis routing logic: a rule is fast-path-eligible if (a) every operator is in the closed twenty subset, (b) every reference is `$applicant.*` or `$constant.*`, (c) no operator's right-hand operand is a set comprehension. Otherwise SQL-path.
- Implement the routing in `reg-bb-engine.DeterminantEvaluator`. The fast path remains the existing `evaluate(...)` method; the SQL path delegates to `joget-rules-api`'s `RuleScriptCompiler` and `EligibilityRuntime`.
- Audit shapes: extend `reg_bb_eval_audit` to carry an `evaluator='fast'|'sql'` column plus, for SQL path, the compiled SQL and result-set hash.
- Test coverage: verify that representative rules route correctly; verify that performance-critical scopes (`field`, `screen`) reject SQL-path rules at publish time *unless* the operator has explicitly opted in via `mm_determinant.allowSlowPath=true`.

### Phase 3 — Editor convergence (Weeks 2–6, parallel to Phase 2)
- `joget-rule-editor` is the canonical editor for all Determinant authoring.
- The closed-grammar JSON shape (`{op, left, right}`) becomes a serialisation format produced by the editor when authoring fast-path-eligible rules. The editor compiles fast-path rules to JSON; it compiles SQL-path rules to the existing DSL → SQL pipeline. The published artefact stored in `mm_determinant.ruleJson` is the editor's choice — JSON for fast-path rules, the DSL source string for SQL-path rules.
- Editor surfaces a hint when the authored rule will route to the SQL evaluator ("This rule references the registry / uses aggregation; it will be evaluated at submit time. For per-keystroke evaluation, restrict the rule to applicant data and the closed-twenty operator set.").

### Phase 4 — Integration on the application path (Weeks 6–10)
- Wire the DSL evaluator into the `farmer_application_submission` workflow as a Tool activity (deferred from Horizon-1's "DSL integration in five layers"; layers 1–3 still apply).
- Bind a ruleset to `prog001` via the editor; verify end-to-end evaluation in the SQL path.
- Verify a `field`-scope visibility rule (`$applicant.member_of_cooperative == 'Y'` → show `cooperative_name`) routes to the fast path and works at Ajax debounce.

### Sequencing constraint
Phase 2 must complete before Phase 3 ships the editor change; the editor cannot promise routing behaviour the engine doesn't yet implement.

---

## 7. Open questions

1. **Where exactly does the fast path / SQL path boundary fall?** §2 names operators and references as the criteria. Edge cases need explicit treatment: an `if(...)` operator with an `$applicant.*` condition but a `$registry.*` branch — fast or slow? The conservative answer is "any reference to `$registry.*` anywhere in the AST → SQL path." Documented in Phase 2.
2. **What happens to legacy `subsidy-eligibility-runtime`'s persistence pattern (`spEligResult`, `spEligRuleResult`)?** Either aligned with `reg_bb_eval_audit`, or kept as an SQL-path-specific result table. Phase 2 design clarifies.
3. **Does the citizen self-attestation grid (Tab 2 of `spApplication`) become a single SQL-path eligibility evaluation, or remain a per-criterion form-quality-runtime evaluation?** Both work; the decision affects whether `form-quality-runtime` continues as a separate engine or is subsumed. Probably out of scope for this ADR; flagged for a follow-up.
4. **Should the editor surface the fast-path / SQL-path routing as a label on each authored rule?** Trades transparency against hiding implementation detail. Decided during Phase 3.

---

## 8. Spec amendments required

The following edits to `regbb-solution-architecture-spec.md` are required to accommodate this decision:

- **§1.2 Component count.** Add "DSL grammar (`rules-grammar`) as the canonical authoring grammar" to the moving-parts list. Note the closed twenty-operator set is the fast-path subset, not the canonical grammar.
- **§1.3 Headline architectural choices.** Reword point 5 ("All Determinant evaluation is server-side") to reflect the two evaluators behind one entry point.
- **§1.5 Spec conformance posture.** Add a paragraph naming the deliberate divergence from the GovStack twenty-operator surface and the rationale.
- **§2.1 P2.** Reword "Rules are data; the operator set is closed" to "Rules are data; the rule grammar is closed at the parser level. The closed twenty-operator set is the fast-path subset; the broader DSL grammar is the canonical authoring grammar."
- **§2.2 C3.** Reword the constraint to describe the two-evaluator partition.
- **§4.3 Determinant Grammar.** Add an introductory paragraph naming the partition. Subsection §4.3.3 "the closed operator set" → relabelled as "the fast-path operator subset". Add §4.3.5 "The DSL grammar — registry-touching and aggregating Determinants".
- **§4.3.4 Validation at publish.** Update the publisher's validation step to dispatch grammar checks by AST analysis: fast-path rules validated against the twenty-operator subset, SQL-path rules validated against the DSL ANTLR grammar via `joget-rules-api`.
- **§6.4 Conditional UI.** Clarify that `field` / `screen`-scope Determinants used for conditional UI are fast-path-eligible by default; rules requiring `$registry.*` references are routed to a deferred submit-time evaluation rather than blocking the Ajax round trip.
- **§8.2 Closed Operator Set.** Relabel as "Fast-Path Operator Subset". Update the additive-only release prose to describe the fast-path additions vs. DSL grammar additions as separate processes.
- **Add §8.6 "DSL evaluator path".** Describe the SQL-compile evaluator at the same level of detail as §8.1–§8.5 describe the in-memory evaluator.
- **§14 Decisions Log.** Add a row recording this ADR's decision.

These edits are applied in companion to this ADR's acceptance.

---

## 9. Decision record

| | |
|---|---|
| Decision | Adopt the DSL grammar as canonical authoring grammar; partition evaluation between fast-path (closed twenty subset) and SQL-path (DSL extensions) by AST analysis |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date Phase 2 starts)* |

---

*End of ADR-001 revision 2.*
