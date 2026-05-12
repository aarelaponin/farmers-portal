# ADR-008 — Cache layering schedule across Phase 1 slices

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §4.3, §9, §12 Q7; ADR-005 (save hook), ADR-006 (outcome persistence schema), ADR-007 (atomicity); `regbb-solution-architecture-spec.md` §8.4 (three-tier cache), §6.4 (per-keystroke Ajax conditional UI); `jw-community/wflow-commons/src/main/resources/ehcache.xml` (Joget's existing cache infrastructure); ADR-001 r2 §3.3 (fast path performance is the closed-grammar guarantee preserved) |

---

## 1. Context

### 1.1 What the spec mandates and why

`regbb-solution-architecture-spec.md` §8.4 specifies a three-tier
cache for evaluator results:

* **L1** — per-request `ThreadLocal`, cleared at servlet end. Coalesces
  repeated evaluations of the same Determinant during one screen
  render or one Ajax round-trip.
* **L2** — per-JVM Caffeine, max 10k entries, expires 30 min after
  last access. Coalesces evaluations across requests within one JVM
  — same applicant evaluating the same rule multiple times during a
  wizard, multiple applicants on the same service version sharing
  registry-fetch results.
* **L3** — cluster-shared Hazelcast, max 100k entries, expires 30
  min, reuses Joget DX 8.1's existing cluster (configured in
  `jw-community/wflow-commons/src/main/resources/ehcache.xml`).
  Coalesces across nodes — a citizen who switches device mid-session
  reuses cached values; load balancers don't pin sessions.

The cache key is `(applicationId, determinantId, dataHash,
registryFetchEpoch)` per spec §8.4. Each tier shares the key shape;
they differ only in scope and TTL.

### 1.2 What the architecture doc recommends for phasing

`determinant-architecture.md` §12 Q7 names a phased rollout:

* Slice 1A — L1 only.
* Slice 1D — add L2.
* Slice 1F or Phase 2 — add L3.

The reasoning the architecture doc gives is "earn each layer when it
matters." Slice 1A has only the post-store hook (per ADR-005), one
evaluation per save, no per-keystroke pressure. Slice 1D introduces
the conditional-UI Ajax (`evaluateScreen`) where overlapping rules on
the same field actually share work. Slice 1F integrates the publisher
and tightens the cluster-restart story; L3 starts to matter when more
than one citizen uses the system concurrently and load balancing across
nodes is real.

### 1.3 Where the per-keystroke pressure actually starts

ADR-001 r2 §3.3 establishes that the fast path's performance property
is the closed-grammar guarantee preserved as an internal optimisation.
The per-keystroke pressure begins at slice 1D (architecture doc §13)
when `MetaScreenElement` ships the `regbbWatch` JS that posts to
`/regbb/eval/screen` debounced 250 ms. Before slice 1D, the only
evaluation paths are:

* Slice 1A — storeBinder, one evaluation per save click.
* Slice 1B — same (SQL path joins; same cardinality).
* Slice 1C — same plus aggregation logic across multiple Determinants
  per save.

None of slices 1A–1C have per-keystroke evaluation pressure.

### 1.4 What L1 buys at slice 1A

Slice 1A's evaluations are: one save → one storeBinder.store() → one
EvalContext → N evaluations (one per eligibility Determinant on each
selected Registration). Per architecture doc §3.3 ("phase-1-plan §D5"),
N is roughly 4 Determinants per Registration; with ≤4 Registrations
per application, ≤16 evaluations per save.

Within those 16 evaluations, the same Determinant is never evaluated
twice on the same `(applicationId, dataHash)` — each Determinant on
each Registration is unique. L1 therefore buys nothing at slice 1A:
there are no overlapping evaluations within one storeBinder call to
coalesce.

L1 starts to pay off at slice 1D when the Ajax round-trip evaluates
multiple Determinants per screen render against the same partial
data (the `regbbWatch` JS posts the screen state once; the servlet
runs `evaluateScreen` which internally evaluates every visible-field
Determinant on the screen). In that case, L1 caches sub-results
across the per-screen evaluation pass.

### 1.5 What L2 buys at slice 1D

L2 (per-JVM Caffeine, 30 min after last access) caches across
requests. Same citizen, same wizard, returns to a previously-visited
tab — the Ajax for that tab re-evaluates the same Determinants over
the same data; L2 hits return microsecond-scale results. Multiple
citizens on the same service version evaluating registry-touching
rules reuse the same `registryFetchEpoch`-keyed entries — L2 becomes
the principal hit path for `$registry.*` rules.

### 1.6 What L3 buys at slice 1F or Phase 2

L3 (cluster-shared Hazelcast, 30 min) matters when:

* Sessions span node boundaries (load balancer doesn't pin sessions
  to nodes; a citizen's request lands on whichever node is least
  loaded).
* A citizen switches device mid-session — phone in the morning,
  laptop in the afternoon. Joget cluster-shared L3 cache makes the
  laptop's first evaluation hit L3 instead of recomputing from
  scratch.

Phase 1 production is single-node per the architecture doc §6 (no
cluster work in Phase 1's deploy plan); Phase 2 introduces clustering.
L3 earns its keep at Phase 2.

### 1.7 The cache-write side-effect property of evaluation

Per architecture doc §4.2, every cache write happens as a side-effect
of evaluation. The evaluator interface is unchanged across cache
tiers; tiers are added as decorators around the core evaluator. A
JVM that has L1 only sees a tiered evaluator with one decorator; a
JVM that has L1+L2 sees two decorators; a JVM that has all three
sees three. The decorator pattern is canonical and additive — adding
a tier is a wrapping operation, not an evaluator-internal change.

### 1.8 Principles in scope

* **YAGNI applied with discipline** — adding cache tiers before they
  pay off is over-engineering; deferring them past the point of
  payoff is under-engineering. The schedule is a deliberate
  alignment, not a default.
* **OCP** — the evaluator's interface stays stable; cache tiers
  are added by composition, not by modification.
* **Spec alignment** — §8.4's three-tier shape is the eventual
  endgame; this ADR sequences delivery, not design.
* **Joget-native** — L2 (Caffeine) is a library dependency; L3
  reuses Joget's existing Hazelcast (per `ehcache.xml`). No new
  cluster-management infrastructure.

---

## 2. Decision

The cache layering rolls out in three steps, aligned with architecture
doc §13's slice schedule:

* **Slice 1A — L1 only.** A `ThreadLocal<Map<CacheKey, EvalResult>>`
  cleared at servlet end. Coalesces sub-result reuse within one
  evaluation pass; no cross-request reuse.
* **Slice 1D — add L2 (Caffeine).** A per-JVM Caffeine cache, 10k
  entries, 30 min after last access (per spec §8.4). Wraps L1 as a
  decorator: L1 lookup first, on miss L2 lookup, on miss the
  underlying evaluator runs and writes both layers.
* **Slice 1F (defaults) / Phase 2 (firm) — add L3 (Hazelcast).**
  Reuses Joget's existing cluster-shared cache infrastructure (per
  `ehcache.xml` lines 67–78 — `FLU_CACHE` and `SF_CACHE` patterns;
  add a regbb-specific Hazelcast region). Wraps L2 as a third
  decorator. Earns its keep when clustering is live.

The same evaluator interface is consumed by every caller across all
three tiers; tiers are decorators on the core evaluator instance,
configured at OSGi service registration time.

The cache key is fixed across tiers per architecture doc §4.3 and
spec §8.4: `(applicationId, determinantId, dataHash,
registryFetchEpoch)`. The same key shape works for every tier.

Invalidation events (per architecture doc §4.3 invalidation table)
fire identically across tiers: `evaluator.invalidate(applicationId)`
clears all entries for that application from L1 and L2 (and L3 when
present); `evaluator.invalidateService(serviceId)` clears all entries
for that service version.

---

## 3. Reasoning

### 3.1 L1 alone matches slice 1A's actual pressure

The ADR's principle test: do the cache tiers earn their keep at the
slice where they are introduced?

Slice 1A produces one storeBinder.store() per save → ≤16 evaluations
per save (per §1.4). Each evaluation has a distinct `determinantId`
(rules don't repeat within a save); cache keys are mutually
distinct; cache hits inside one storeBinder.store are zero. L1's
gain at slice 1A is structural housekeeping (the `ThreadLocal` exists,
gets populated, gets cleared) without performance benefit.

But L1 has near-zero cost — a `ThreadLocal<HashMap>` allocation,
~10 lookups per save, ~50 µs of overhead. Including it at slice 1A
is cheap and aligns with the eventual design where L1 is the
innermost layer; slice 1D inherits L1 already in place.

L2 at slice 1A would pay off in only one scenario: two saves on the
same application within 30 min by the same citizen, with no data
change between them (so `dataHash` matches). This case is real but
rare at Phase 1 scale; the marginal benefit doesn't justify the
implementation work in slice 1A's scope.

### 3.2 L2 at slice 1D matches the per-keystroke pressure

Slice 1D introduces the `regbbWatch` Ajax path. Each keystroke (after
debounce) posts the screen state and runs `evaluateScreen`. The
screen has multiple field-scope Determinants; many of them reference
overlapping `$applicant.*` data. Rapid retyping of the same value
produces repeated evaluations of the same Determinants on the same
data — L2 hit rates approach 100% across retypes.

L2 also makes the cross-citizen `$registry.*` reuse pay off: two
citizens on the same service version, both pre-checking against
`$registry.civil.maritalStatus`, share the registry-fetch result via
the `registryFetchEpoch`-keyed L2 entry. The hit rate depends on
citizen overlap; even modest overlap saves IM round-trips.

### 3.3 L3 at slice 1F/Phase 2 matches clustering

Phase 1 is single-node. The architecture doc §6 doesn't mention
cluster deployment in Phase 1; the deploy artefact is one Joget
instance. L3's only gain in single-node deployment is durability
across JVM restarts (L2 is in-memory; L3 survives). At Phase 1's
≤100 saves/day, JVM-restart cost is sub-second; the durability
gain is operationally insignificant.

Phase 2 introduces clustering (per `architecture-overview.md` §6.2
horizon-2). At that point, L3 is mandatory: a citizen's session
crossing nodes without L3 means every node-cross is a cold cache.
Clustering and L3 ship together.

### 3.4 The decorator pattern means each addition is non-invasive

Per §1.7, cache tiers are decorators around the core evaluator.
Adding L2 in slice 1D means: write a `CaffeineCachedEvaluator
implements DeterminantEvaluator` that delegates to a wrapped
evaluator on miss; configure the OSGi service registration to wrap
the core in L2 + L1 instead of just L1. No change to consumers
(MetaScreenElement, storeBinder, hash-variable plugin, REST
endpoint) — all of them call `DeterminantEvaluator.evaluate(...)`,
unchanged.

This OCP property is what makes the phased rollout cheap. Each
slice's addition is a decorator class plus a service-registration
update.

### 3.5 The cache key shape is invariant across tiers

The key `(applicationId, determinantId, dataHash, registryFetchEpoch)`
is computed by the core evaluator and propagated identically through
L1, L2, L3. Each tier is a `Map<CacheKey, EvalResult>` with a TTL
strategy; the key is the same. This invariance is what makes adding a
tier mechanical: a new tier is a new cache region, same key, same
value.

Invalidation symmetry: `invalidate(applicationId)` walks each tier in
turn and removes entries whose key contains the matching
`applicationId`. The implementation is one method per tier, called by
the outermost decorator.

### 3.6 Joget's existing Hazelcast saves L3 infrastructure work

`jw-community/wflow-commons/src/main/resources/ehcache.xml` lines
67–78 already configure `FLU_CACHE` (form / userview / datalist
cache) and `SF_CACHE` (per-form Hibernate ORM mapping cache), both
backed by Joget's cluster infrastructure. Adding a regbb-specific
cache region to the same `ehcache.xml` reuses Joget's cluster wiring.
Phase 2's L3 is one ehcache.xml addition plus a Hazelcast-aware
decorator class — not a fresh cluster-cache integration.

---

## 4. Alternatives considered and refused

### 4.1 Option A — phased rollout (the decision in §2)

The chosen option. Stated above.

### 4.2 Option B — All three tiers from slice 1A

**Refused on YAGNI applied with discipline.** L2 and L3 don't earn
their keep until slices 1D and 1F/Phase 2 respectively (per §3.2 and
§3.3). Adding them earlier carries real cost: implementation,
testing, deployment-config tuning, OSGi service registration
complexity, and ongoing operational concerns (cache size, eviction,
debugging). The decorator pattern makes deferral cheap; deferral is
the right call.

This is not "we don't need it yet" feature-need framing —
specifically, L2 and L3 do not have a working consumer in slice 1A.
Slice 1A's only evaluation pressure is one storeBinder.store() per
save with no overlapping work. Adding L2 to a code path with no
overlap is adding capacity for hits that won't occur.

### 4.3 Option C — L1 only across all of Phase 1; defer L2 to Phase 2

**Refused on slice 1D requirements.** The per-keystroke Ajax
conditional UI of slice 1D requires <50 ms Ajax round-trips per spec
§6.4 and architecture doc §9.1. Without L2, every Ajax call resolves
afresh via the underlying evaluator — fine for `$applicant.*`-only
fast-path rules (sub-millisecond) but problematic for any registry-
touching field-scope rule (with `allowSlowPath=true` per spec
§4.3.4). L2 hits make the registry round-trip happen once per
`registryFetchEpoch` per JVM, not per keystroke. The latency
budget collapses without L2.

### 4.4 Option D — skip L1, go straight to L2

L2 covers within-request reuse, so why have L1 at all? **Refused on
ThreadLocal vs Caffeine cost asymmetry.** ThreadLocal lookup is
~5 ns; Caffeine lookup is ~50 ns (still very fast, but 10x). For
hot paths where the same `(applicationId, determinantId, dataHash)`
is evaluated multiple times within one screen render — slice 1D's
`evaluateScreen` is exactly this case — L1's per-thread coalescing
saves a meaningful slice of the Ajax latency budget.

L1 also has a correctness property L2 doesn't: a per-request cache
guarantees consistency within one round-trip. Two evaluations of
the same Determinant on the same data within one request return
the same result (memory-fenced via the same `ThreadLocal`). With L2
alone, two evaluations within one request could in principle race
against an L2 invalidation between them — a remote scenario, but
visible. L1 short-circuits the race window for within-request
consistency.

### 4.5 Option E — A single distributed cache across the cluster, no per-JVM layer

**Refused on per-JVM hit cost.** Distributed cache lookups are
~1–5 ms (network + serialisation); per-JVM Caffeine is ~50 ns.
For per-keystroke evaluation with ≤50 ms latency budget, distributed-
only cache eats the budget on every lookup. L2 is the architecturally
necessary layer for slice 1D's latency.

This option also conflates clustering (a Phase 2 concern) with
single-node performance (a slice 1D concern). The phased approach
keeps them separate.

---

## 5. Consequences

### 5.1 Positive

* Each cache tier ships at the slice where it earns its keep; no
  speculative complexity.
* The decorator pattern means each tier is a non-invasive addition;
  consumers are unchanged.
* L3 reuses Joget's existing Hazelcast infrastructure; Phase 2's
  cluster work is one ehcache.xml region plus a decorator class.
* The cache key shape is invariant across tiers; one mental model
  applies across all three.
* Invalidation is uniform: `invalidate(applicationId)` walks each
  tier; same surface across phases.

### 5.2 Negative

* **The cache configuration grows over time.** Slice 1D adds a
  Caffeine config (size, TTL); Phase 2 adds an ehcache.xml region
  with Hazelcast peer-to-peer config. Operations need to track all
  three layers as the cache surface evolves.
* **Decorator stacking has a debugging cost.** A cache miss at
  slice 1F traverses L1 → L2 → L3 → core evaluator; logs need to
  show which layer hit/missed. Without proper logging, "why is this
  slow?" requires walking through the decorator chain manually.
  Discipline: per-tier metrics (architecture doc §9.5 names them)
  ship in Phase 2.
* **L1 adds slight overhead at slice 1A even though it doesn't
  pay off.** A ThreadLocal lookup, a hash compute, a put. Tens of
  microseconds per save. Negligible at Phase 1 scale, but it is
  paid early.
* **Cache invalidation race windows widen as tiers stack.**
  Architecture doc §11 R3 names `dataHash` collision and the
  cache-invalidation race; ADR-009 (concurrency) addresses the
  semantics. Adding L3 widens the race window from "between L2
  invalidate and next eval" to "between L3 invalidate-replicated-
  to-all-nodes and next eval." Bounded staleness, sub-second.

### 5.3 Neutral

* The `registryFetchEpoch` cache key component (architecture doc
  §4.3) is computed by the IM connector and passes through the cache
  layers unchanged. When the IM cache rolls, the epoch increments,
  the cache key changes, the previous-epoch entries are unreachable
  (natural eviction). Same property at every tier.
* Spec §8.4's three-tier shape is the eventual endgame; this ADR is
  about *when* to ship each, not whether to.

---

## 6. Implementation outline

Sketch.

### 6.1 Slice 1A

1. Implement `ThreadLocalCachedEvaluator` as a `DeterminantEvaluator`
   decorator. Wraps the core evaluator. On `evaluate(...)`:
   - Compute cache key.
   - Look up in `ThreadLocal<HashMap<CacheKey, EvalResult>>`.
   - Hit: return.
   - Miss: delegate to wrapped evaluator, store result, return.
2. Implement a request lifecycle hook (a Joget `PluginWebSupport`
   filter or a request listener) that clears the ThreadLocal at the
   end of each request.
3. Activator registers the core evaluator wrapped in L1 as the OSGi
   `DeterminantEvaluator` service.

### 6.2 Slice 1D

1. Add Caffeine to `reg-bb-evaluator` pom.xml.
2. Implement `CaffeineCachedEvaluator` as a `DeterminantEvaluator`
   decorator. Configuration: max 10k entries, expireAfterAccess(30,
   TimeUnit.MINUTES) per spec §8.4.
3. Update Activator registration: core wrapped in L2 wrapped in L1.
4. `invalidate(applicationId)` walks both layers.

### 6.3 Slice 1F or Phase 2

1. Add an ehcache.xml region for `org.joget.cache.REGBB_EVAL_CACHE`,
   modelled on the existing `FLU_CACHE` / `SF_CACHE` patterns;
   configure 100k entries, 30 min TTL.
2. Implement `HazelcastCachedEvaluator` as a `DeterminantEvaluator`
   decorator backed by the Joget cluster cache.
3. Update Activator registration: core wrapped in L3 wrapped in L2
   wrapped in L1.
4. `invalidate(applicationId)` walks all three layers.

---

## 7. Open questions

### 7.1 The AST cache (separate from result cache) — same phasing?

The AST cache (per ADR-003 §6.3) is a separate concern from the
result cache. The AST cache stores parsed-once ASTs per
`(determinantId, serviceVersion)`; result cache stores evaluation
results per `(applicationId, determinantId, dataHash,
registryFetchEpoch)`. AST cache is per-JVM Caffeine from slice 1A
(per ADR-003 §7.2), small (≤1k entries), no L3 needed (re-parsing
on miss is sub-ms). This ADR does not constrain the AST cache
schedule; it's already settled by ADR-003.

### 7.2 What about in-memory parsed-rule cache during the decorator stack

The core evaluator's parsed-rule cache is mentioned implicitly in
slice 1A. It is local to the core evaluator, not a decorator layer.
The decorators (L1, L2, L3) cache *evaluation results*, not parsed
rules. Documented for clarity.

### 7.3 Whether L2 should be configurable per Determinant scope

A Determinant in `eligibility` scope has different cache hit
patterns than a Determinant in `field` scope. Per-scope tuning is
plausible but YAGNI for slice 1D. Default position: one L2 config
covers all scopes; per-scope tuning is a Phase 2+ optimisation if
profiling motivates.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` §8.4: no change. The three-
tier shape is normative; this ADR sequences delivery without changing
the design.

`determinant-architecture.md` §12 Q7 is replaced with a backlink to
this ADR.

---

## 9. Decision record

| | |
|---|---|
| Decision | Phased rollout: L1 only at slice 1A; +L2 (Caffeine) at slice 1D; +L3 (Hazelcast) at slice 1F or Phase 2 |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins; per-tier dates per slice schedule)* |

---

*End of ADR-008.*
