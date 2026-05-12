# ADR-026 — Rule-to-SQL compiler for fast-path determinants

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-001r2 (closed-twenty grammar), ADR-008 (cache layering), ADR-022 (budget engine), `_design/architecture/components/budget-engine.md` §5.4 (CES). |

## Context

The Cost Estimation Service needs to count "how many farmers in the registry are eligible for programme X". Today, with `RoutingEvaluator` operating row-by-row, this means iterating every farmer in the registry and evaluating each one — for a 200,000-farmer registry, ~200k evaluations per estimate. At ~3ms per fast-path evaluation, that's roughly 10 minutes wall time. Too slow for an interactive Programme Builder UI.

PostgreSQL can do the same count in milliseconds via a `WHERE` clause. The closed-twenty grammar is small enough that translating fast-path-eligible rules to PostgreSQL `WHERE` predicates is feasible. The grammar's operator and reference set:

- Operators: `==`, `!=`, `<`, `<=`, `>`, `>=`, `eq`, `neq`, `lt`, `lte`, `gt`, `gte`, `in`, `notIn`, `not_in`, `notin`, `AND`, `OR`, `&&`, `||`.
- References: `$applicant.<field>` (or bare identifiers) — translates to `c_<field>` columns; `$constant.<X>`, `$service.<X>` — translate to literals at compile time.
- Literals: numbers, single-quoted strings, double-quoted strings, list literals.

Rules with `$registry.*` references are NOT fast-path-eligible (per ADR-001r2 / D14); they continue to iterate.

## Decision

Add a `RuleToSqlCompiler` to the framework's evaluator package. Given a rule AST (already parsed by `FastPathEvaluator`), it emits a SQL `WHERE` clause string + parameter bindings.

Translation table:

| Rule grammar | SQL |
|---|---|
| `$applicant.field == 'lowlands'` (or `field == 'lowlands'`) | `c_field = 'lowlands'` (parameter-bound) |
| `field <= 5` | `c_field <= 5` |
| `field in ['mountains','foothills']` | `c_field IN ('mountains','foothills')` (parameter-bound) |
| `A AND B` | `(<A>) AND (<B>)` |
| `A OR B` | `(<A>) OR (<B>)` |
| `eq` / `neq` / `lt` / etc. | mapped to `=` / `<>` / `<` / etc. |

The compiler operates on the same AST classes the interpreter uses. Fail-safe: if the AST contains any node the compiler doesn't handle (custom function, $registry reference, future operator extension), the compiler returns null; CES falls back to iterative evaluation.

The Cost Estimation Service uses the compiler:

```java
String whereClause = ruleToSqlCompiler.compile(determinant.getAst());
if (whereClause != null) {
    String sql = "SELECT COUNT(*) FROM app_fd_farmerbasicinfo WHERE " + whereClause;
    long eligibleCount = jdbcTemplate.queryForLong(sql, params);
    return eligibleCount;
} else {
    // Iterative fallback for $registry.* rules
    return iterativeCount(determinant, ctx);
}
```

The compiler does NOT replace the interpreter at decision time. Decision-time evaluation always goes through `FastPathEvaluator.evaluate(...)` — it's already fast (~3ms) and behaviour-preserving with the audit trail. The compiler is for **batch / count operations** where row-by-row iteration is too slow.

## Consequences

**Positive:**

- CES becomes interactive: a programme estimate against a 200k-row registry runs in ~10–50ms instead of 10 minutes.
- Programme designers can iterate quickly: edit a rule, re-estimate, see the impact, refine.
- The compiler is bounded — twenty operators + four reference scopes. Maintainable.
- Behaviour parity is testable: a translation-correctness suite runs each grammar production through both interpreter and compiler against representative data; assert equivalence.

**Negative:**

- New code surface to maintain. Bugs in translation produce silent errors (CES returns wrong count). Mitigation: mandatory translation-correctness tests for every grammar production; the suite must pass before a PR merges.
- PostgreSQL-specific. Other DBMSes would need their own compiler. Acceptable: the platform is PG; a port is a future, scoped exercise.
- Compiler must stay in sync with grammar evolution. Adding an operator to the closed twenty (an ADR-class decision per ADR-001r2) requires updating the compiler too. Mitigation: add a checklist item to ADR-001r2's evolution procedure.
- `$registry.*` rules don't benefit. CES iteration for those rules remains slow. Acceptable today (most rules don't reference `$registry.*`); future ADR may extend with a registry-aware translation (e.g. JOIN to the relevant `app_fd_*` table).

**Trade-off named:** Speed vs. simplicity. The simple alternative — "iterative evaluation, accept the wall time, run CES as a background job" — was considered. Rejected because programme design is interactive; analyst-on-the-tool round-trip time matters. The cost of the compiler is bounded (~500 LoC + tests); the benefit is interactive programme estimation.

**Documents updated:** Budget Engine SAD §5.4 (CES uses the compiler), §11 (BE-R1 risk on translation correctness); RegBB framework SAD will add `RuleToSqlCompiler` to the evaluator package internals when implemented.
