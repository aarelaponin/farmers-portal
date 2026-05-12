# ADR-003 — Rule storage shape in `mm_determinant.ruleJson`

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §7.1, §7.3, §7.4, §12 Q2; ADR-001 r2 §2 (two-evaluator partition); `regbb-solution-architecture-spec.md` §4.3.0 (rule storage), §4.3.0.1 (fast-path JSON shape), §4.3.4 (publisher validation); `_design/decision-log.md` D12 (editor routing — automatic per AST analysis), D14 (any `$registry.*` → SQL path) |

---

## 1. Context

### 1.1 What "rule storage shape" means

A `mm_determinant` row carries a `ruleJson` column. When the Determinant
evaluator processes that row at runtime, it must turn the column's content
into a structure the fast-path tree-walker or the SQL-compile evaluator can
consume. The question is: in what shape does `ruleJson` *enter* the engine?

Two natural shapes exist, and the spec mentions both:

* A **DSL source string** — the analyst's text, exactly as authored in
  `joget-rule-editor` (e.g., `parcel.agroZone == 'lowlands' AND
  applicant.age >= 18`). Parsing happens at engine load.
* A **pre-parsed JSON-AST** — the editor walks the parse tree once and
  emits a structured JSON object (e.g., `{"op":"and","args":[{"op":"eq",
  "left":"$applicant.parcel.agroZone","right":"lowlands"},{"op":"gte",
  "left":"$applicant.age","right":18}]}`). The runtime consumes the AST
  directly; no parsing on the hot path.

### 1.2 The spec's position

`regbb-solution-architecture-spec.md` §4.3.0 specifies:

> "The persistence shape follows the routing: `mm_determinant.ruleJson`
> holds a JSON-serialised AST for fast-path-eligible rules, and the DSL
> source string for SQL-path rules."

§4.3.0.1 specifies the JSON-AST grammar: binary operators as
`{op, left, right}`, n-ary Boolean operators as `{op: "and"|"or",
args: [expr, ...]}`, unary operators as `{op: "not"|"exists", arg: expr}`,
arithmetic operators with the same shapes, refs as `"$<scope>.<path>"`
strings.

§4.3.4 specifies that the publisher classifies each rule as fast-path-
eligible or SQL-path by AST analysis at publish time, and stores the
classification.

`decision-log.md` D12 settles that the editor performs the routing
classification automatically per the AST — no operator override on the
storage shape.

`decision-log.md` D14 settles the conservative routing rule: any
`$registry.*` reference anywhere in the AST routes the entire rule to
SQL path.

### 1.3 Why the question is non-trivial despite the spec's apparent settlement

Three pressures push back on the spec's "follow the routing" position:

* **Storage uniformity is appealing.** Code that does not have to handle
  two shapes is simpler. A reader (publisher, editor, evaluator) that
  treats all `ruleJson` values as DSL source strings deals with one parse
  pipeline; a reader that treats all as JSON-AST deals with one walker.
  The spec's hybrid forces every reader to dispatch on shape.
* **Round-tripping is harder under the hybrid.** An analyst opens an
  existing rule in the editor; if it's stored as JSON-AST, the editor
  must reverse-render the DSL source for editing. Reverse-rendering is a
  separate code path from the forward parser.
* **The fast-path / SQL-path boundary moves over time.** Adding a fast-
  path operator (per spec §4.3.3 five-step process) flips some rules from
  SQL-path to fast-path-eligible. Under the hybrid, those rules' on-disk
  shape changes — a migration. Under uniform-source storage, the routing
  is recomputed at publish time but the on-disk shape doesn't change.

These pressures argue for re-examining the spec's hybrid before adopting
it.

### 1.4 Where the parse cost actually lives

The `rules-grammar` plugin (per repo inventory) is an ANTLR4 parser at
`gs-plugins/rules-grammar/`. ANTLR4 parsers are fast: parsing a typical
Determinant (10–30 nodes, depth ≤5) is sub-millisecond on modern JVMs once
the grammar's static state is initialised (first parse on JVM startup pays
~100 ms of grammar setup; subsequent parses are sub-ms). The parse cost is
dominated by the first parse per JVM lifetime, not by per-call overhead.

The architecture doc §11 specifies that parsed ASTs are cached in the
evaluator, keyed by `(determinantId, serviceVersion)`, reused across
evaluations. The cache is invalidated when `mm_service` republishes
(architecture doc §4.3 invalidation table). Parse cost is therefore amortised
across all evaluations of a Determinant version, regardless of on-disk
shape.

### 1.5 The editor's role and round-trip burden

`joget-rule-editor` (per `gs-plugins/joget-rule-editor/`) is the canonical
authoring surface (per ADR-001 r2 §2). It validates DSL source in-place,
compiles on save, and supports test-against-sample-applicant. Any decision
on storage shape constrains the editor: if storage is DSL source, the
editor saves the source verbatim and the publisher classifies; if storage
is JSON-AST, the editor parses, classifies, and emits the AST. Either way
the editor is in control; the constraint is on what it emits.

### 1.6 Principles in scope

* **OCP (open-closed)** — adding a fast-path operator should not require
  rewriting persisted rules. Whatever shape we choose must survive
  operator-set changes.
* **DIP** — readers depend on a stable rule contract; whether the
  contract is "DSL source string" or "JSON-AST" or "either" is an
  architectural choice with implementation consequences.
* **Spec alignment** — diverging from spec §4.3.0 carries the same cost
  ADR-001 r2 §5.2 names: cross-jurisdiction rule portability is reduced
  by every divergence.

---

## 2. Decision

`mm_determinant.ruleJson` carries **the DSL source string in all cases**.
The editor produces a single shape (DSL source); the publisher parses the
source at publish time, classifies the rule (fast-path-eligible vs
SQL-path), and stores the classification (plus the parsed AST) in the
evaluator's per-service-version AST cache. The runtime evaluator never
parses on the hot path — it consumes the cached AST.

This is a **deliberate divergence from spec §4.3.0**, which prescribes the
hybrid (JSON-AST for fast-path, DSL source for SQL-path). The divergence
is justified in §3 below; spec amendments to record it are in §8.

**JSON-AST is preserved as an internal serialisation format** — the
publisher emits a JSON-serialised AST as part of its publish artefact,
stored alongside the rule in an evaluator-private location (the AST cache,
not the `mm_determinant` row). The on-disk DSL source remains the source
of truth; the JSON-AST is a derived, regenerable artefact.

---

## 3. Reasoning

### 3.1 OCP — the on-disk shape survives fast-path-operator additions

Spec §4.3.3 documents that adding a fast-path operator follows a five-step
process (operator table, evaluator code, publisher validation, tests,
release). Under the hybrid (spec §4.3.0), some Determinants previously
classified SQL-path may become fast-path-eligible after a release that
adds (e.g.) a new comparison operator. Their on-disk shape changes from
DSL source string to JSON-AST. That change is a migration: existing rows
must be re-parsed and re-emitted. Multiply this across operator additions
over a year of releases, and `mm_determinant.ruleJson` accumulates a
mixed-shape population — old rules in their old shape, new rules in their
new shape, post-migration rules in their post-migration shape.

Under uniform DSL-source storage, the same fast-path-operator addition
re-classifies the rule at publish time without changing the row. The
on-disk shape is invariant under operator-set evolution.

### 3.2 DIP — readers depend on one shape

Every reader of `mm_determinant.ruleJson` becomes simpler. The publisher
calls `RulesScript.parse(source)` (via `gs-plugins/rules-grammar/...`).
The editor opens the rule by displaying the source verbatim. The evaluator
caches the AST; the cache key is `(determinantId, serviceVersion)`, the
cache value is the parsed AST. Under the hybrid, every reader dispatches:
"if string, parse; if object, decode." Two code paths, two failure modes,
two test surfaces.

### 3.3 The parse cost is structurally cached, not eliminated by storage shape

The architecture doc §7.3 specifies that ASTs are cached in the evaluator
on first evaluation per JVM lifetime, keyed by
`(determinantId, serviceVersion)`. The cache is shared across all
evaluations of a given Determinant version. The parse cost paid at first
evaluation is amortised across (typically) tens of thousands of subsequent
evaluations. Pre-parsing at publish-time and storing JSON-AST on disk
shaves the first-evaluation parse cost only, on a code path that runs once
per JVM-lifetime per Determinant. The argument that storage shape
materially affects evaluation latency is not supported by the cache
design.

### 3.4 The publisher already does the work

Spec §4.3.4 specifies that the publisher walks every Determinant on the
service at publish time and classifies it. That walk requires parsing.
The parse already happens at publish time regardless of storage shape.
Under DSL-source storage, the publisher's parse output is what populates
the evaluator's AST cache (via the `invalidateService(serviceId)` call
named in architecture doc §7.2 last bullet — the cache is repopulated on
the next evaluation, but the publisher's parse-and-classify ensures all
rules are already known to be parseable at the moment publish completes).
The work is not duplicated; it is funnelled into one parser invocation
per Determinant per publish, exactly as it would be under the hybrid.

### 3.5 The editor's UX is preserved

`joget-rule-editor` (per ADR-001 r2 §2) provides syntax validation,
compile-on-save, and test-against-sample-applicant. All three operate on
the DSL source. Storing the DSL source verbatim means the editor's open-
edit-save cycle round-trips trivially: open reads the column, save writes
the column. No reverse-rendering pass to convert JSON-AST back to DSL
source for editing. The editor's UX continues to work as it does today,
without a new code path.

### 3.6 The divergence from spec §4.3.0 is honest

Spec §4.3.0 prescribes the hybrid. This ADR diverges. The divergence is
small (one architectural property: storage uniformity) and its rationale
(OCP under operator-set evolution + reader DIP) is grounded in principles
the spec itself accepts. The divergence is documented (§8 spec amendments
section), not concealed. ADR-001 r2 §5.2 already establishes the
discipline of recording deliberate Lesotho-instance divergences from the
spec; this ADR follows the same discipline.

---

## 4. Alternatives considered and refused

### 4.1 Option A — DSL source string only (the decision in §2)

This is the chosen option. Stated in §2 above.

### 4.2 Option B — Pre-parsed JSON-AST in `ruleJson` for all Determinants

Spec §4.3.0.1's JSON-AST grammar is well-defined; using it uniformly is
defensible. The cost is the inverse of DSL-source uniform: the editor
must reverse-render JSON-AST to DSL source for editing (a round-trip path
that does not exist today), and the rule's on-disk shape is no longer the
analyst's authored text. **Refused on round-tripping cost** — the editor
has working forward-parsing; reverse-rendering is a new code path, with a
separate test surface, that adds no architectural property the cached AST
in the evaluator doesn't already provide.

This option also has a subtle ergonomic regression: hand-inspecting an
`mm_determinant.ruleJson` value becomes harder. An on-call engineer
investigating "why is this Determinant returning false?" benefits from a
human-readable column. JSON-AST is human-readable but loses the analyst's
formatting and comment intent.

### 4.3 Option C — The spec-prescribed hybrid (DSL source for SQL-path, JSON-AST for fast-path)

Spec §4.3.0's recommendation. The architecture doc §12 Q2 endorses this.
**Refused on OCP and reader DIP** (per §3.1, §3.2 above). The hybrid
optimises for a marginal first-evaluation parse-cost saving on the fast
path while creating a migration burden whenever the fast-path operator
set or routing classification changes. It also forces every reader to
dispatch on shape, which is a permanent SRP weakening at every reader.

The spec's stated rationale for the hybrid is performance (fast path
benefits from skipping parse). That rationale collapses under the cache
design (§3.3): the parse runs once per JVM-lifetime per Determinant
version regardless of storage shape, because the AST is cached.

### 4.4 Option D — Both shapes in `ruleJson` (a JSON envelope `{"source": "...", "ast": {...}}`)

Belt-and-braces. **Refused on data integrity.** Two representations of the
same rule, on the same row, must stay in sync. They will drift —
operator edits the source via the editor; some other process re-derives
the AST and writes back; one of the two paths fails partway. The
architecture acquires a new failure mode (source/AST disagreement) that
serves no architectural purpose the cached AST does not already serve.
SRP rejects holding two representations of the same fact.

### 4.5 Option E — Compiled SQL inlined into `ruleJson` for SQL-path rules

Pre-compile the SQL-path rule at publish time and store the compiled SQL
on disk alongside the source. Pitched occasionally as an "extreme
performance" choice. **Refused on portability.** SQL syntax and parameter-
binding shape depend on the database driver; a rule on disk in MySQL
syntax is wrong on a Postgres-backed deployment, and cross-environment
deployment becomes brittle. Spec §8.6.2 specifies that the compiled SQL
is per-service-version cached in the evaluator, exactly because
compilation is environment-bound. Storing it on disk would make the
storage layer environment-bound — a category mistake.

---

## 5. Consequences

### 5.1 Positive

* `mm_determinant.ruleJson` carries one shape across all Determinants —
  DSL source string. Readers do not dispatch on shape.
* Adding a fast-path operator (or moving an existing operator across the
  fast-path boundary) does not migrate any `mm_determinant` row.
* The editor's open-edit-save round-trip is trivial: open reads source,
  save writes source. No reverse-rendering pass.
* On-call hand-inspection of a Determinant's rule is the analyst's
  authored text, not a derived AST.
* The publisher's parse-and-classify work is the same as under the
  hybrid — funnelled into one parser invocation per Determinant per
  publish.

### 5.2 Negative

* **Divergence from spec §4.3.0 / §4.3.0.1.** Cross-jurisdiction rule
  portability decreases by one more axis. Rules authored in the Lesotho
  instance and exported to a spec-conformant peer would need their AST
  re-derived from the source at the receiving end. Today no such peer
  exists; the cost is theoretical. ADR-001 r2 §5.2 documents the
  divergence-honesty discipline this ADR follows.
* **First-evaluation parse cost is paid at the runtime evaluator, not at
  publish.** Spec §4.3.0's hybrid moves the cost to publish-time for
  fast-path rules. This ADR pays it on first runtime evaluation. The
  cost is sub-millisecond per Determinant; the per-evaluation amortised
  cost is negligible. But for very large services with many fast-path
  Determinants and a cold JVM cache, the first-citizen-after-publish
  pays a tiny synchronous cost the spec design avoids. Bounded; not
  zero.
* **Spec §4.3.0.1 JSON-AST grammar is not unused** — the publisher emits
  it as the AST cache value, and the §8 REST endpoint may surface it for
  debug introspection. The grammar carries no on-disk weight, but it
  remains a contract the publisher and evaluator must agree on.

### 5.3 Neutral

* Adding the JSON-AST grammar to the AST cache value does not change
  the cache design (architecture doc §11). The cache key
  `(determinantId, serviceVersion)` and the cache value (parsed AST) are
  unchanged in shape; the value's *concrete representation* is
  publisher-emitted JSON-AST per spec §4.3.0.1 grammar, used identically
  whether storage is source or hybrid.
* The DSL grammar's evolution discipline (per ADR-001 r2 §3.2) is
  unchanged. Adding a DSL operator is the same release process under
  any storage shape.

---

## 6. Implementation outline

Sketch, not a plan.

1. The editor (`joget-rule-editor`) saves the analyst's DSL source
   verbatim to `mm_determinant.ruleJson`. No client-side AST emission.
   The `ruleJson` column type stays as TEXT/CLOB in the form definition.
2. The publisher (`reg-bb-publisher`) on publish: reads each rule's
   source, parses via `rules-grammar`, classifies (per architecture doc
   §7.2, spec §4.3.4), stores the parsed AST plus classification in the
   evaluator's AST cache (architecture doc §11), and on success calls
   `evaluator.invalidateService(serviceId)` (architecture doc §7.2 last
   bullet) to ensure the AST cache is repopulated on next evaluation.
3. The runtime evaluator (`reg-bb-evaluator`) on first evaluation per
   JVM lifetime per `(determinantId, serviceVersion)`: looks up the
   cached AST. On AST-cache miss (e.g., publisher hasn't run yet on this
   JVM), the evaluator parses the source itself — the same parse the
   publisher does — and caches. This makes the runtime evaluator's
   correctness independent of publisher state, at the cost of the parse
   call being implemented in two places. Both call sites delegate to the
   `rules-grammar` plugin's `RulesScript.parse(source)` entry point;
   neither contains a re-implementation.
4. Hand-edit / migration tooling: any tooling that constructs
   `mm_determinant` rows programmatically (test fixtures, seed data)
   emits DSL source strings, never JSON-AST.

Lands in slice 1A (architecture doc §13). The slice's
`FastPathEvaluator` already needs to consume an AST, so the cached-AST
machinery slots in at the right time.

---

## 7. Open questions

### 7.1 What "DSL source string" means at the byte level

Whitespace, Unicode normalisation, line endings — minor questions. The
canonical position: the editor emits UTF-8, normalised to NFC, line
endings normalised to LF. A round-trip through the editor is idempotent.
Forwarded to the editor's implementation guidance.

### 7.2 Whether the cached AST is per-JVM Caffeine or distributed Hazelcast

The architecture doc §8 cache layering applies to *evaluation results*,
not to the AST cache. The AST cache is a separate concern. Phase 1
position: AST cache is per-JVM only (Caffeine), small (≤1k entries per
JVM). Distributed AST cache is overkill — re-parsing on a JVM-cache miss
is sub-ms. Forwarded to ADR-008 (cache layering) for confirmation; this
ADR does not constrain it.

### 7.3 What happens when a previously-published rule's source becomes unparseable after a grammar evolution

The DSL grammar's evolution discipline (ADR-001 r2 §3.2) is additive-only;
removing or changing operators is forbidden. Therefore a previously-
published rule's source is always parseable under any later grammar
version. Forwarded to spec §4.3.3's evolution protocol.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` requires the following edits:

* **§4.3.0** — change "The persistence shape follows the routing:
  `mm_determinant.ruleJson` holds a JSON-serialised AST for fast-path-
  eligible rules, and the DSL source string for SQL-path rules." to
  "The persistence shape is uniform: `mm_determinant.ruleJson` holds the
  DSL source string for every rule. The publisher parses the source at
  publish time, classifies the rule (fast-path-eligible vs SQL-path),
  and stores the parsed AST plus classification in the evaluator's
  per-service-version cache. Routing classification is recomputed on
  every publish; on-disk shape is invariant under operator-set
  evolution."
* **§4.3.0.1** — retain the JSON-AST grammar; relabel as "the
  publisher's emitted AST representation" rather than "the persistence
  shape for fast-path rules." Note that the same grammar is used for
  the §8 REST endpoint's debug introspection surface.
* **§4.3.4** — no change required; the publisher already classifies
  every rule.
* **§14 Decisions Log** — add a row recording this ADR's decision.

The Determinant architecture document (`determinant-architecture.md`)
§7.1 ("editor decides serialisation format") and §7.3 ("load cached AST
or parse if not cached") need updating to reflect uniform DSL-source
storage. §12 Q2 is replaced with a backlink to this ADR.

---

## 9. Decision record

| | |
|---|---|
| Decision | `mm_determinant.ruleJson` stores DSL source string in all cases; publisher parses + classifies at publish time; AST is cached per `(determinantId, serviceVersion)` in the evaluator |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins)* |

---

*End of ADR-003.*
