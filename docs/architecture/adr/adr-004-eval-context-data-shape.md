# ADR-004 — `EvalContext.data` shape

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §4.1, §12 Q3; `regbb-solution-architecture-spec.md` §4.3.1 (refs and resolution), §8.1 (`EvalContext` definition); `_design/decision-log.md` D9 (`repeating_group` widget persistence — JSON arrays inside `app_application.dataJson`) |

---

## 1. Context

### 1.1 What `EvalContext.data` is for

`EvalContext` is the immutable input bundle the engine passes into
`DeterminantEvaluator.evaluate(determinantId, ctx)` and friends. Its
`data` field is the concrete representation of `$applicant.<storageKey>`
references: when a rule names `$applicant.district`, the evaluator reads
from `data` to get the citizen's value of `district`.

Spec §4.3.1 defines the resolution rule: `$applicant.<storageKey>`
resolves "against `app_application.dataJson` for the current application
(engine, in-memory)." The shape question is how `data` materialises that
JSON in Java memory.

### 1.2 Where the data comes from upstream

Three upstream sources contribute to `data`:

* **Citizen form input** at submit-time: rendered by `MetaScreenElement`
  / `MetaWizardElement` and persisted via Joget's standard form-store
  binding chain. Joget's `FormDataDao.saveOrUpdate(...)` (per
  `jw-community/wflow-core/.../FormDataDaoImpl.java` line 511) writes a
  flat row into `app_fd_<table>`: each `mm_field.storageKey` becomes a
  column `c_<storageKey>`.
* **Pre-existing applicant fields** loaded at session start: the
  `RegBbApplicationStoreBinder` (architecture doc §5.6) loads the row
  into a `FormRowSet` whose `FormRow` is essentially a flat
  `Map<String,Object>` keyed by storageKey.
* **Repeating-group widget content** per `decision-log.md` D9: rendered
  as JSON arrays inside `app_application.dataJson`. A field like
  `dataJson.eligibilityResponses = [{criterionId, response}, ...]`
  represents grouped data that does not flatten into one column.

The first two contribute flat key-value pairs. The third contributes
nested structure via JSON arrays.

### 1.3 What rules actually reference today

Empirical inventory across the four Phase 1 programmes (per
`_design/phase-1-plan.md` §D5): 18 Determinants total. Their references:

* `$applicant.district`, `$applicant.age`, `$applicant.maritalStatus`,
  `$applicant.parcel.agroZone`, `$applicant.member_of_cooperative`,
  `$applicant.gainfulEmployment`, etc. — all map to a single `mm_field`
  storageKey, persisted as a single column in the application form's
  underlying table.
* No rule today references `$applicant.eligibilityResponses[?].response`
  or any element of a repeating-group array. The repeating-group widget
  is a citizen self-attestation surface (D9), not a rule input surface.
* No rule today references `$applicant.spouse.dateOfBirth` or any
  multi-subject path. Multi-subject (`$applicant.spouse.*`,
  `$applicant.dependents[i].*`) is a Phase 3+ concern.

### 1.4 Three candidate Java shapes

* **Shape A — flat `Map<String, Object>`** keyed by `storageKey`. A
  rule's `$applicant.district` resolves via `data.get("district")`. For
  dotted-path references like `$applicant.parcel.agroZone`, the storage
  key is the field's literal `mm_field.storageKey` (which today is
  `parcel.agroZone` or `parcel_agroZone` — the storageKey itself
  encodes the path semantics, not Java structure).
* **Shape B — nested `Map<String, Map<String, ...>>`** keyed by
  hierarchical paths. `$applicant.parcel.agroZone` resolves via
  `data.get("parcel").get("agroZone")`. The visitor decomposes the
  reference path on each lookup.
* **Shape C — typed POJO record** generated per `mm_service`.
  `$applicant.district` resolves via reflection on a generated
  `Applicant` class, with field types statically known at codegen time.
  Codegen burden, doesn't fit a metadata-driven engine.

### 1.5 The Joget storeBinder rowSet shape

The architecture doc §4.1 and §5.6 specify that `EvalContext.data` is
populated from the row the storeBinder just produced. Joget's
storeBinder rowSet (`org.joget.apps.form.model.FormRowSet`, used in
`jw-community/wflow-core/.../FormUtil.java executeElementFormatData`
line 534) is, mechanically, a list of `FormRow` instances; each
`FormRow` is a `Properties` extension (a flat string-keyed map). The
keys are field IDs (post-Postgres-folding lowercase per the CLAUDE.md
"Hard-won Joget gotchas" section). The values are strings (Joget's
form layer is string-typed; type conversion happens at evaluator
operator level, e.g., `lt`/`gte` parse to numbers).

The flat shape is therefore the upstream representation Joget hands us.
Any other shape we choose imposes a transformation step.

### 1.6 Principles in scope

* **DIP** — depend on the simplest shape that satisfies today's
  requirements; expand the contract when a real consumer needs more
  structure.
* **OCP** — the shape decision should not preclude multi-subject
  references becoming addressable later.
* **Joget-native discipline** (CLAUDE.md HARD RULE) — `data` is a thin
  view over what `FormDataDao` produces; we do not synthesise alternate
  representations.

---

## 2. Decision

`EvalContext.data` is a **flat `Map<String, Object>`** keyed by
`mm_field.storageKey`. Values are the unparsed strings as they emerge
from Joget's `FormRow` (typed coercion is per-operator, in the
evaluator). Repeating-group field values, where present, are stored as
their JSON-string serialised form (the value of `c_<storageKey>` in the
`app_fd_*` table — a JSON array as text, per `decision-log.md` D9). The
evaluator does not parse them at `data.get(key)` time; only an operator
that explicitly needs to look inside a repeating-group value (none in
slice 1A; none defined in the closed twenty-operator subset) performs the
JSON parse on demand.

Multi-subject references (`$applicant.spouse.*`, `$applicant.dependents[i].*`)
are **deferred**: when they arrive (Phase 3+), the visitor for `$applicant`
references is extended to recognise `spouse` and `dependents` as scoped
sub-keys, with sub-key resolution either via a flat composite key
(`spouse.dateOfBirth`) or via a nested-lookup extension to the visitor.
The decision between flat composite key and nested lookup is forwarded to
the ADR that introduces multi-subject support.

---

## 3. Reasoning

### 3.1 The upstream is flat; matching shape avoids transformation overhead

Joget's `FormRow` (used throughout the storeBinder lifecycle —
`FormUtil.executeElementFormatData`, `FormDataDaoImpl.find` line 281,
`FormDataDaoImpl.saveOrUpdate` line 498) is a flat map. The data the
evaluator reads emerges from Joget in this shape. Choosing a flat
`Map<String, Object>` for `EvalContext.data` is the identity transform;
the engine's adapter code is one line:

```java
EvalContext ctx = new EvalContext.Builder()
    .data(new HashMap<>(formRow))
    .build();
```

Choosing nested `Map<String, Map<...>>` (shape B) requires a
storageKey-decomposition pass that splits each key on `.`, walks
deeper, and inserts. The pass runs on every `EvalContext` construction.
Per architecture doc §9.2, an applicant's session can construct
hundreds of `EvalContext` instances during a wizard; the decomposition
is cheap individually but real in aggregate, and it adds a correctness
surface (what does the decomposer do when storageKey contains a literal
`.`? what about empty path components?) that the flat shape avoids.

### 3.2 Today's rule corpus does not motivate nesting

§1.3 inventoried the 18 Determinants. None reference a path that does
not flatten cleanly into a single storageKey. The hypothetical case for
nesting (multi-subject references) is Phase 3+ (architecture doc §13
slice 2 and 3). DIP says depend on the simplest shape that satisfies
today's requirements; the nested shape's added complexity buys nothing
real today.

### 3.3 OCP — the flat shape does not preclude multi-subject

When `$applicant.spouse.dateOfBirth` becomes a real reference, two
extension paths are open:

* **Flat composite key** — the `mm_field` for spouse's date-of-birth is
  given a storageKey of `spouse.dateOfBirth`. The visitor for
  `$applicant.<path>` continues to do `data.get(path)`. Joget's
  `app_fd_*` column would be `c_spouse_dateofbirth` (after Postgres
  folding). Trivial extension.
* **Nested-lookup visitor extension** — the visitor learns that
  `$applicant.spouse.<path>` decomposes into a sub-lookup. Requires
  `data.get("spouse")` to return a nested map. The flat shape blocks
  this *unless* we change the upstream to populate it, which means
  changing the storeBinder to emit nested rows (a wholesale Joget-
  integration change).

The flat-composite-key path is the OCP-respecting extension: zero
storeBinder changes, an additive `mm_field` storageKey convention. The
nested path is the OCP-violating one. Choosing flat today preserves the
flat-composite-key extension; it forecloses the nested-lookup extension
only by being incompatible with Joget's row-shape, not by ADR-004 making
a constraint.

### 3.4 Joget-native discipline preserved

CLAUDE.md HARD RULE: "Never raw SQL on Joget metadata or form data...
The Determinant reads `EvalContext.data`; it never writes to `app_fd_*`."
The flat shape is a one-line view over a `FormRow`. The data path stays
inside Joget's contract: `FormDataDao.find` → `FormRow` → `Map`. No
synthesised alternate shape sneaks data out of Joget's typing or
lifecycle.

### 3.5 Repeating-group nesting is honoured by deferring the parse

`decision-log.md` D9 stores repeating-group content as a JSON array
inside `dataJson`. The flat shape stores that array as a JSON-string
value at the key `eligibilityResponses` (or whatever the storageKey
is). No operator in the closed twenty subset (spec §4.3.3) consumes a
repeating-group value as input — they all consume scalars. If a future
operator needs to look inside a repeating-group value (e.g., a
`forall`/`exists-with-filter` operator), it parses the JSON-string at
the operator level. The flat shape does not preclude this; it simply
defers the parse to the operator that needs it.

This is structurally aligned with the spec §4.3.3 closed-set
discipline: aggregation lives on the SQL path (per ADR-001 r2 §2,
because aggregation is too slow for per-keystroke evaluation). The SQL
path's compiled SQL runs against the form-data tables directly, where
the JSON column can be queried with Postgres JSONB operators if needed.
The fast path doesn't need to look inside repeating-groups.

---

## 4. Alternatives considered and refused

### 4.1 Option A — flat `Map<String, Object>` keyed by storageKey (the decision in §2)

The chosen option. Stated above.

### 4.2 Option B — nested `Map<String, Map<...>>` keyed by hierarchical paths

**Refused on DIP and upstream-shape mismatch.** The data Joget hands us
is flat (`FormRow` extends `Properties`). Choosing a nested shape
imposes a decomposition transform on every `EvalContext` construction
— a transform whose only justification is hypothetical multi-subject
references that are Phase 3+. DIP says depend on the simplest shape
that satisfies today.

The nested shape also makes the visitor's reference resolution path
heavier: instead of one map lookup per `$applicant.<path>`, the visitor
does N lookups for an N-component path, with null-handling logic at
each level. Spec §4.3.0.1's null-propagation rule already handles null
values; doing it at every level of nesting is more failure surface, not
less.

### 4.3 Option C — typed POJO record per service

**Refused on metadata-driven discipline.** Every `mm_service` would
require a generated `Applicant` class; the evaluator would reflect over
it. Codegen burden, build-time per-service classes, fragility under
service version changes (a v1.1 service that adds a field requires a
new POJO class — at runtime, the engine must hold both v1.0 and v1.1
POJO classes simultaneously for in-flight applications per spec §4.4).

This option also conflicts with the architecture's metadata-driven
posture: the engine reads `mm_field` rows to know what fields exist;
generating Java types from `mm_field` rows is a static-dynamic
mismatch.

### 4.4 Option D — `JsonNode` (Jackson/Gson tree) as the data shape

**Refused on type discipline.** A `JsonNode` is structurally similar to
a nested map but typed as a JSON-DOM. The visitor's operators would do
`data.get("district").asText()` instead of `(String) data.get("district")`.
The marginal benefit (one library's tree-walking conventions) is offset
by a cost: every operator's type-coercion logic depends on
JsonNode-specific methods, which means the rule grammar is implicitly
coupled to Jackson/Gson's API surface. The flat `Map<String, Object>`
keeps type coercion in the evaluator's per-operator code, where it
belongs (per spec §8.2 — operator semantics include type-coercion
rules).

### 4.5 Option E — the actual `FormRow` instance, passed through

**Refused on lifecycle coupling.** `FormRow` is a Joget runtime type;
its lifecycle is tied to Joget's persistence session. Holding a
`FormRow` reference inside an `EvalContext` and passing it across cache
layers (architecture doc §11) entangles the cache lifecycle with
Joget's session lifecycle — a `FormRow` whose underlying session has
closed becomes problematic to read. The flat `Map<String, Object>` is
detached from Joget's lifecycle: once constructed, it survives any
session boundary.

---

## 5. Consequences

### 5.1 Positive

* `EvalContext.data` matches the upstream `FormRow` shape; construction
  is the identity transform.
* The visitor's `$applicant.<path>` resolution is one map lookup —
  simple, fast, easy to test.
* Repeating-group values are stored verbatim; no eager-parse cost.
* Multi-subject references can be added via the flat-composite-key
  extension (§3.3) without touching the data shape.
* The `EvalContext` is detached from Joget's session lifecycle —
  cache-friendly.

### 5.2 Negative

* **Multi-subject via flat-composite-key has a UX cost at the editor.**
  An analyst authoring a rule against a spouse's date of birth writes
  `$applicant.spouse.dateOfBirth` in the DSL but the underlying
  `mm_field.storageKey` is `spouse.dateOfBirth` (a single column
  `c_spouse_dateofbirth`). The visual nesting in the rule does not
  match real schema nesting. This is a small ergonomic loss; the editor
  can mask it (autocomplete from the catalogue of `mm_field` rows). It
  is real.
* **Repeating-group values inside `data` are JSON-string-typed.** Any
  operator that needs to look inside (none in slice 1A) parses on
  demand. Slow path, allocates per call. Acceptable today; revisit if
  rule shapes change.
* **The flat shape does not document its keys.** A consumer reading
  `EvalContext.data` must consult `mm_field` to know what storageKeys
  exist. The `mm_field` row is the source of truth, so this is correct,
  but it means the type signature `Map<String, Object>` is
  uninformative on its own.

### 5.3 Neutral

* The visitor's null-propagation discipline (spec §4.3.0.1: "any operator
  with a null operand returns null, except `exists` which returns false")
  applies identically under any shape. The flat shape simplifies it: one
  lookup, one null check.
* The `dataHash` cache-key component (architecture doc §4.3) hashes the
  *subset* of `data` that the rule references, computed at parse time.
  Computing the field-reference subset over a flat map is one
  `keys ∩ referenceSet` operation; over a nested map it is a tree walk.
  Faster under the flat shape.

---

## 6. Implementation outline

Sketch.

1. `EvalContext.data` declared as `Map<String, Object>` in the
   `reg-bb-api` interface (architecture doc §6.1 api bundle).
2. The storeBinder (`RegBbApplicationStoreBinder`, architecture doc
   §5.6) builds `data` by copying its `FormRow` into a new
   `LinkedHashMap<String, Object>` (preserves insertion order for
   debug-log readability; not required for correctness).
3. The visitor for `$applicant.<path>` references calls
   `data.get(path)`; null is the absence-of-value (per spec
   §4.3.0.1 null-propagation).
4. The `dataHash` cache key component (architecture doc §4.3) hashes
   the projection `{k → v : k ∈ referenceSet}` where `referenceSet` is
   the rule's `$applicant.*` reference set, computed once per parse and
   cached on the AST.
5. Repeating-group values are read as `String` and stored verbatim;
   slice 1A's operator set does not unpack them.

---

## 7. Open questions

### 7.1 Multi-subject reference shape

Forwarded to a future ADR or spec amendment when the first multi-subject
rule appears. Default position: flat-composite-key (`storageKey =
"spouse.dateOfBirth"`). Nested-lookup extension is on the table if a
real use case demands rich `$applicant.spouse.*` decomposition.

### 7.2 Whether `data` carries type metadata

`Map<String, Object>` loses `mm_field.dataType` (text vs number vs date).
The evaluator's per-operator coercion (per spec §8.2) uses
`mm_field.dataType` from the `MetaModelDao` (per ADR-002 — bundle
`reg-bb-mm-dao`). An alternative is to carry a sibling
`Map<String, FieldType>` in `EvalContext` to avoid the lookup per
operator. Performance-driven; defer until profiling shows it matters.

### 7.3 Postgres column-name folding

CLAUDE.md "Hard-won Joget gotchas" notes that Postgres folds unquoted
identifiers to lowercase. The `FormRow` keys may emerge lowercased.
Whether the engine's `storageKey` matching is case-sensitive or
case-tolerant is a small but real correctness question. Default
position: case-tolerant on read (the engine's `data.get(key)` falls
back to a lowercased lookup if exact match fails). Forwarded to the
implementation phase; documented here so it isn't forgotten.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` §8.1: the `EvalContext.data`
field is described as carrying "current application data, `$applicant.*`"
without specifying shape. Add: "shape: flat `Map<String, Object>` keyed
by `mm_field.storageKey`; values are unparsed (per-operator coercion is
in §8.2)." No structural spec change.

The Determinant architecture document (`determinant-architecture.md`)
§4.1 already implies the flat shape; §12 Q3 is replaced with a backlink
to this ADR.

---

## 9. Decision record

| | |
|---|---|
| Decision | `EvalContext.data` is a flat `Map<String, Object>` keyed by `mm_field.storageKey`; multi-subject deferred |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins)* |

---

*End of ADR-004.*
