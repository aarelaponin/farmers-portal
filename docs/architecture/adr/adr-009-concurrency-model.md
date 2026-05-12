# ADR-009 — Concurrency model and invariants

| | |
|---|---|
| Status | **Proposed** |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | none |
| Related | `_design/determinant-architecture.md` §8, §11, §12 Q8; ADR-007 (atomicity), ADR-008 (cache layering); `regbb-solution-architecture-spec.md` §4.4 (versioning), §8.4 (cache invalidation); `jw-community/wflow-core/src/main/java/org/joget/apps/form/dao/FormDataDaoImpl.java` (Hibernate session-per-call lifecycle) |

---

## 1. Context

### 1.1 What "concurrency model" means here

The Determinant evaluator is a singleton OSGi service consumed by
multiple callers, possibly simultaneously. The concurrency model
specifies the invariants the engine maintains under concurrent
requests, the shape of any locks (or their deliberate absence), and
the visible properties of cache/DB interaction under contention.

### 1.2 Concrete concurrency events the engine must handle

Architecture doc §12 Q8 enumerates five:

* Two operators reviewing the same application simultaneously.
* A citizen submitting while the service is being republished.
* The evaluator service stopped mid-evaluation.
* Two citizens hitting `evaluateScreen` for the same rule
  simultaneously.
* A cache invalidation race — data write and a concurrent eval on
  the same application.

To these we add two more from the architecture doc §11 (versioning)
and ADR-007 (atomicity):

* In-flight applications during a publish (their `serviceVersion`
  pinning, per architecture doc §11).
* Two saves on the same application from different sessions
  (admin batch import while a citizen is editing) — the
  `RegBbApplicationStoreBinder` runs in both contexts.

Each event must have a documented behaviour.

### 1.3 What the spec mandates

Spec §8.4 enumerates the three-tier cache and its invalidation
triggers but does not specify a concurrency model beyond "cache
writes are atomic per key." Spec §4.4 specifies that
`mm_service.version` is immutable once published; in-flight
applications keep their `serviceVersion` pinned via
`app_application.c_service_version`.

Joget DX 8.1 itself runs on a Servlet 3.1+ container (per
`jw-community/`), so concurrent request handling is the platform
default. Joget's `FormDataDaoImpl.saveOrUpdate` (line 498) opens a
session per call (per ADR-007 §1.2), which means concurrent saves on
the same form do not share a session; Hibernate's row-level locking
applies on conflicting writes to the same row primary key.

### 1.4 Invariants the engine must hold

Three invariants matter:

* **Determinism** — same `(applicationId, determinantId, dataHash,
  registryFetchEpoch)` produces the same `EvalResult`. Concurrent
  evaluations may execute redundantly but must agree on the outcome.
* **Versioned isolation** — an application pinned to `serviceVersion=4`
  evaluates against v4's rules; a publish to v5 must not affect v4
  evaluations.
* **Bounded staleness** — cache hits after an invalidation are
  bounded in time. The next evaluation after the invalidate sees
  the new state; in-flight evaluations may complete on stale data
  but their *next* evaluation is fresh.

### 1.5 What the engine must not pretend to provide

* **Cross-call ACID transactions** spanning the cache and the DB.
  Cache and DB are separate stores; updates to them are not
  atomic.
* **Strict consistency on cache reads.** The cache may serve a
  stale entry briefly after invalidation; readers tolerate this.
* **Locks that would serialise concurrent applicants.** Two citizens
  evaluating in parallel do not block each other.

### 1.6 Principles in scope

* **Deterministic by construction** — the evaluator is a pure
  function on `(rule, ctx)`; concurrency cannot produce inconsistent
  results because there is no shared mutable state in the
  computation itself.
* **Bounded staleness over locks** — the spec's eventual-consistency
  posture (§8.4 "Cache writes are atomic per key" — i.e., each key
  is single-writer; no global lock) is the deliberate design choice.
* **Joget-native** — concurrent persistence behaviour follows
  Joget's `FormDataDao` discipline; no custom locking on top.
* **Honest property statement** — the concurrency model is
  documented, including failure modes, not glossed.

---

## 2. Decision

The Determinant evaluator's concurrency model is **lockless,
eventually-consistent on cache, strictly-consistent on database
writes**. The specific properties:

1. **No locks anywhere in the evaluator.** Concurrent calls to
   `evaluate(determinantId, ctx)` execute in parallel; they may
   redundantly compute on a cache miss but produce identical
   outcomes by determinism (per §1.4 invariant 1).
2. **Cache writes are atomic per key, per tier.** Caffeine's L2
   provides this natively (`putIfAbsent`); Hazelcast's L3 does the
   same. L1 is per-thread, no contention. Concurrent writers on
   the same key produce one final cached value (last-writer-wins
   semantically; both compute the same value by determinism, so
   the write order is invisible).
3. **DB writes are serialised per row by Hibernate.** Joget's
   `FormDataDaoImpl.saveOrUpdate` opens a session per call (line
   498) and Hibernate enforces row-level locking on conflicting
   writes to the same primary key. Two concurrent storeBinder
   invocations on the same application are serialised at the row
   write; the second one sees the first one's commit before
   reading.
4. **Versioned isolation is enforced via cache key** — the cache
   key includes `serviceVersion` (per architecture doc §4.3,
   architecture doc §11). A publish to v5 does not invalidate v4
   entries; v4-pinned applications continue evaluating against v4
   rules.
5. **Invalidation is fire-and-forget.**
   `evaluator.invalidate(applicationId)` removes cache entries for
   the application across all tiers; if a concurrent evaluation
   was in flight, it completes against the pre-invalidation cache
   (or computes from scratch if cache miss); the next evaluation
   after the invalidate sees the post-invalidation state. Bounded
   staleness window: max one Ajax round-trip on the conditional UI
   path; max one save→outcome cycle on the storeBinder path.
6. **Service shutdown is graceful** — when the OSGi service
   `DeterminantEvaluator` is stopped, in-flight evaluations
   complete; new lookups via `PluginManager.list(...)` return
   empty; callers degrade per the established
   `evaluator_unavailable` discipline (architecture doc §8.1, ADR-005
   §3.5).

---

## 3. Reasoning

### 3.1 Determinism makes locking unnecessary

The evaluator is a pure function: same rule, same `EvalContext`
produces the same outcome. Two threads computing the same key in
parallel produce the same result; the cache stores either result and
both readers see consistent state. There is no race condition that
produces an *incorrect* outcome — only redundant work.

The cost of redundant computation is bounded: cache miss →
microsecond fast path or millisecond SQL path; the rare case of two
threads racing on the same key wastes one microsecond / millisecond.
A lock to prevent this race would itself cost microseconds; the lock
is not free. Eventual-consistency-by-determinism is faster than
locking-to-deduplicate.

### 3.2 Cache writes are single-key-per-writer; no need for stronger ordering

Caffeine and Hazelcast both implement per-key concurrent maps with
single-writer semantics: `putIfAbsent`, `compute`, etc., are atomic
per key. Two threads writing the same key produce one final state;
the last writer's value is what's stored. Under determinism (§3.1),
both writers compute the same value, so "last writer wins" is
invisible.

L1 is per-thread (`ThreadLocal`); no contention; no atomicity
question. Inheriting the per-tier guarantee from Caffeine and
Hazelcast costs zero — they are designed for it.

### 3.3 Hibernate's row-level locking is the right grain for DB writes

Two concurrent saves to the same `app_application` row occur in two
distinct Hibernate sessions (`FormDataDaoImpl.saveOrUpdate` line 498
opens a fresh session per call). Hibernate's row-level locking
serialises them at the SQL `UPDATE` boundary; the second session
sees the first session's committed row before its own UPDATE.

This is the correct grain. Locking at the application level
(e.g., a per-applicationId mutex) would serialise unrelated work
(read of fields not touched by the save). Hibernate's row-level
discipline serialises only the actual write contention.

For the storeBinder's two-write pattern (per ADR-007 §1.1), the
two transactions on the same row in the same thread are sequential
within one save call. Two parallel saves on the same row by two
different threads serialise on the row-level lock — the second
storeBinder.store() blocks at its `super.store(...)` UPDATE until
the first commits. The result: both saves complete; both run their
own evaluations; both produce outcome writes; the last write of
`eligibilityOutcome` wins in the column. Determinism (§3.1) makes
this invisible if the data didn't change between the two saves; if
the data did change, the second save's `dataHash` differs and
produces a different outcome — which is correct, because the second
save's data is what should be reflected.

### 3.4 Versioned isolation via cache key is structurally simple

The cache key includes `serviceVersion` (architecture doc §4.3, §11).
Different versions have different keys; v4 and v5 are different
namespaces. A publish to v5 invokes
`evaluator.invalidateService(serviceId)` (architecture doc §7.2 last
bullet); the implementation walks the cache and removes entries
where `key.serviceId == serviceId AND key.serviceVersion == latest_active_version`.
The previous-version entries remain.

This decouples versioning from invalidation: v5's publish does not
invalidate v4's cache entries; v4-pinned applications keep their
evaluation behaviour. Spec §4.4's immutability invariant ("Meta-
records are immutable once a version is published") is upheld at
the cache layer too.

### 3.5 Invalidation is fire-and-forget; bounded staleness is acceptable

`evaluator.invalidate(applicationId)` is called from the storeBinder
after the outcome write (per ADR-005 §6.1) and from the publisher
after publish completes (per architecture doc §7.2 last bullet). The
call walks each cache tier and removes matching entries. If a
concurrent evaluation is in flight when invalidate runs:

* If the in-flight eval has already returned its result, it has
  written to cache; the invalidate then removes that just-written
  entry. Net: stale write was made and immediately removed.
* If the in-flight eval has not yet returned, it computes against
  the pre-invalidate state of the underlying data; its write may
  produce a stale entry (if the data changed during the
  invalidate). Bounded staleness: the *next* evaluation
  (`dataHash` may have changed) will re-compute.

The window is bounded to the duration of one in-flight evaluation,
which is at most milliseconds. No information is lost; bounded
staleness is the explicit trade for lockless invalidation.

### 3.6 Service shutdown without partial state

Stopping the OSGi `DeterminantEvaluator` bundle is permitted at any
time. Per architecture doc §8.2:
* In-flight evaluations complete (they hold a reference to the
  evaluator instance; the bundle shutdown doesn't yank the object
  from under them).
* New `PluginManager.list(DeterminantEvaluator.class.getName())`
  lookups return empty; callers see null and degrade
  (`disposition=indeterminate`, per ADR-005 §3.5 and ADR-006 §3.5).
* The storeBinder writes the outcome with `disposition=indeterminate`,
  `reason=evaluator_unavailable` to `dataJson.eligibilityOutcome`.
  No partial outcome state.
* Operator inbox shows the indeterminate banner; manual triage.

This is the same property exercised by slice 1A's exit criterion
("Stop the evaluator bundle → indeterminate disposition appears,
demonstrating the OSGi split paid off." — architecture doc §13).

---

## 4. Alternatives considered and refused

### 4.1 Option A — lockless, eventually-consistent on cache, strict on DB (the decision in §2)

The chosen option. Stated above.

### 4.2 Option B — Per-key cache locking to deduplicate concurrent computes

Use Caffeine's `Cache.get(key, mappingFunction)` semantics — the
mapping function runs once even under concurrent access for the same
key. **Refused on cost-benefit.** The deduplication has a real cost
(thread waits on the in-progress compute); the savings (one fewer
microsecond-scale fast-path eval, or one fewer millisecond-scale SQL
path eval) don't justify that cost in typical concurrency patterns.
Different applicants don't share keys (different `applicationId`); the
same applicant typically doesn't have parallel evaluations.

`Cache.get(key, mappingFunction)` becomes worth using only on the
SQL path for `$registry.*`-touching rules where two applicants might
race on the same `(serviceVersion, determinantId, dataHash,
registryFetchEpoch)` shared registry-fetch entry. This is a Phase 2
optimisation if profiling motivates; not Phase 1.

### 4.3 Option C — Per-application mutex on the storeBinder save path

Serialise concurrent saves on the same `applicationId`. **Refused
on lock-grain mismatch.** Joget's row-level locking (via Hibernate)
is the correct grain for DB writes; an application-level mutex on
top is redundant for the DB but would also serialise the evaluator
work, including the parts that don't touch the row.

The hypothetical concern this option addresses — "what if two
saves race and the second's `eligibilityOutcome` write happens before
the first's?" — is closed by Hibernate's row-level lock: the second
storeBinder's `super.store(...)` blocks at the row UPDATE until the
first commits, so the orderings are causally consistent (second
sees first's row before its own write).

### 4.4 Option D — Optimistic locking via row version columns

Add a `version` column to `app_application`; storeBinder reads-
modifies-writes with a CAS check. **Refused on
unnecessary-machinery.** Hibernate already provides row-level locking
for conflicting writes. Optimistic locking with `version` columns is
useful when long-lived transactions might commit stale state; the
storeBinder's transactions are short (one save call). Adding a
`version` column is additional schema, additional code, no
additional safety in the typical pattern.

This option becomes interesting if Phase 2 introduces long-running
transactions or async outcome rewrites; current shape doesn't need
it.

### 4.5 Option E — Single-threaded execution of all evaluations through a queue

Pin the evaluator to a single executor thread; all calls submit
work and await. **Refused on throughput.** Phase 1's ≤100 saves/day
makes this thinkable, but Phase 2's per-keystroke Ajax pressure makes
it impossible — debounced 250 ms keystrokes from N concurrent
citizens times M field-scope rules per screen produce a queue depth
that single-threading cannot serve. Refused at the principles-and-
phasing level: lockless determinism is the design that scales.

### 4.6 Option F — Cluster-wide invalidation broadcast guaranteeing zero stale reads

When `invalidate(applicationId)` runs, broadcast to all cluster
nodes and wait for ack before returning. **Refused on synchronous-
broadcast cost and on Phase 1 single-node deployment.** Phase 1 is
single-node (per ADR-008 §3.3); there is no cluster broadcast. Phase
2 is clustered, and Hazelcast's invalidation is asynchronous (per
spec §8.4 — eviction propagates across nodes within a bounded
window, not synchronously). Synchronous broadcast at every save
adds latency proportional to the slowest node; the bounded-staleness
trade is the spec's deliberate choice.

---

## 5. Consequences

### 5.1 Positive

* Lockless. Throughput scales with thread count; no contention
  bottleneck.
* Determinism makes concurrent evaluations safely redundant; the
  rare wasted compute is bounded.
* Versioned isolation is "free" — cache key includes version; no
  invalidation logic per version transition.
* Hibernate's row-level locking is the grain we get from Joget; no
  custom lock infrastructure.
* Service shutdown is graceful; the OSGi-split-pays-off property
  (architecture doc §13 slice 1A exit criterion) is exercised here.

### 5.2 Negative

* **Bounded staleness on cache reads is a real property.** A cache
  hit after an invalidate-in-flight may return the pre-invalidate
  value for one round-trip. Bounded to milliseconds in the typical
  case; documented; not zero.
* **Concurrent saves on the same row can produce two
  evaluations in close succession.** The first save evaluates →
  writes outcome A; the second save evaluates → writes outcome B
  (with different `dataHash`). Outcomes are correct individually,
  but an audit trail at slice 1B shows two rows close in time; an
  observer might wonder which was the "real" final state. The
  answer is "the latest, as always." Discipline in the operator-
  review form: order by `evaluatedAt desc`.
* **Determinism's correctness depends on the evaluator's purity.**
  Any future code that introduces side-effecting state (e.g., a
  metrics counter that affects evaluation logic — bad pattern, not
  a real plan) breaks the lockless-by-determinism property. Code
  review enforces purity; documented as a non-negotiable invariant.
* **Versioned isolation adds a cache-key dimension.** The cache key
  has four components; the dataHash component subsumes some of the
  variability but the (serviceId, serviceVersion) pair is structural.
  Cache size estimates (architecture doc §11 R3) must account for
  multiple versions co-existing in cache during in-flight
  applications spanning publish events.

### 5.3 Neutral

* Phase 1's single-node deployment means cluster-related concurrency
  events (Hazelcast invalidation propagation, node failure) don't
  apply until Phase 2. ADR-008's L3 introduction is when cluster
  concurrency becomes live.
* The "loud failure" discipline (architecture doc §8.1) applies
  across concurrent failure modes: an evaluator throw on one
  thread does not affect other threads' evaluations; the failing
  thread's storeBinder writes `disposition=indeterminate`; other
  threads continue.
* Joget's `FormDataDaoImpl.saveOrUpdate` opens a session per call
  (per ADR-007 §1.2); concurrent calls do not share sessions. This
  property is inherited; this ADR doesn't modify it.

---

## 6. Implementation outline

Sketch.

1. The core evaluator (`reg-bb-evaluator` bundle) is a single
   instance, no instance fields holding mutable state except the
   parsed-AST cache (which is also a thread-safe Caffeine).
2. The evaluation method is reentrant: it reads from the AST cache,
   reads from the result cache via the decorator chain, computes on
   miss, writes to caches.
3. The cache decorators (per ADR-008) use thread-safe primitives:
   ThreadLocal at L1, Caffeine at L2, Hazelcast at L3.
4. The storeBinder's `store(...)` method does not take a per-
   application mutex; it relies on Hibernate's row-level locking
   for concurrent saves.
5. The OSGi service registration is a simple
   `bundleContext.registerService(DeterminantEvaluator.class.getName(),
   evaluator, props)`. Bundle stop unregisters; in-flight callers
   keep their reference.
6. Documentation in the codebase: a top-of-file comment in the core
   evaluator class noting "this class must remain a pure function
   on `(rule, ctx)`; introducing side-effecting evaluation logic
   breaks lockless-by-determinism."

---

## 7. Open questions

### 7.1 Whether to add `Cache.get(key, mappingFunction)` for `$registry.*` rules in slice 1B

Two applicants concurrently evaluating the same registry-touching
rule with the same `dataHash` and `registryFetchEpoch` produce two
IM round-trips in the worst case. `Cache.get(key, mappingFunction)`
deduplicates to one. Profile slice 1B; if IM round-trip
amplification is observed, add the deduplication; otherwise defer.
Forwarded to slice 1B implementation review.

### 7.2 Whether the OSGi service shutdown should support drain

Currently, stopping the bundle returns the service immediately;
in-flight callers hold their references and complete. An alternative
is to wait for in-flight calls to complete before unregistering.
Joget's `PluginManager.list(...)` is fire-and-forget, so a drain
mechanism would require the bundle's stop method to track in-flight
counts. Default position: no drain; in-flight calls finish on their
own threads. Forwarded to operations review if undeploys cause
visible problems.

### 7.3 Whether per-thread `EvalContext` immutability is enforced or convention

`EvalContext` is described as immutable (architecture doc §4.1 — "the
immutable input bundle"). Convention or enforced? Default position:
final fields, no setters, defensive copies of the `data` map at
construction. Forwarded to api bundle implementation.

---

## 8. Spec amendments required

`regbb-solution-architecture-spec.md` §8.4 already specifies "cache
writes are atomic per key"; no change needed. No explicit concurrency
section in the spec; the eventual-consistency posture is implicit.

`determinant-architecture.md` §12 Q8 is replaced with a backlink to
this ADR. §11 (versioning) is consistent with this ADR's versioned-
isolation property.

---

## 9. Decision record

| | |
|---|---|
| Decision | Lockless evaluator; eventually-consistent on cache; strictly-consistent (Hibernate row-level) on DB writes; versioned isolation via cache key |
| Decided by | *(name of decider on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | *(date slice 1A begins)* |

---

*End of ADR-009.*
