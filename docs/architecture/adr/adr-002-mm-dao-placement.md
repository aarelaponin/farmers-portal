# ADR-002 — `MetaModelDao` placement (revision 2)

| | |
|---|---|
| Status | **Proposed (revision 2)** — supersedes revision 1 of 2026-04-30 |
| Date | 2026-04-30 |
| Deciders | Farmers Portal architecture team |
| Consulted | — |
| Informed | Engineering |
| Supersedes | ADR-002 revision 1 (option D — own bundle) |
| Related | `_design/determinant-architecture.md` §6, §12 Q1; `_design/adr-001-rule-grammar-canon.md` r2; `_design/decision-log.md` D20; `CLAUDE.md` |

> **Revision note.** Revision 1 picked option D — a dedicated `reg-bb-mm-dao` OSGi bundle — on SRP grounds, dismissing option B (api as plain JAR) and option A (no split — keep DAO in `reg-bb-engine`) as feature-need framing. On reconsideration, **two principles were missing from the original ADR's vocabulary**: YAGNI (don't add complexity until it's earned) and Convention over Invention (deviate from established platform patterns only with scale-proportionate justification). Once those principles are named, option A wins decisively at Lesotho-MAFSN scale. Revision 2 records the decision that flows from the corrected vocabulary. The reasoning of revision 1 (SRP + DIP) remains correct on its own terms; it was over-applied because the counter-principles weren't given a hearing.

> **Methodological lesson.** ADR-001 r2's discipline rejected feature-need framing as a refusal pattern ("no 'we don't need it yet'"). That wording was meant to prevent decisions made on speculation about user need. It accidentally also banned YAGNI applied to architectural complexity, which is a different and valid principle. The ADR template (`determinant-adr-drafting-session-prompt.md` §4) is amended in companion to this revision to distinguish the two cases. CLAUDE.md gains a paragraph naming the requirement to surface counter-principles.

---

## 1. Context

### 1.1 What the question is

`MetaModelDao` is the typed read accessor for the twelve `mm_*` configuration entities — six methods today, ~170 LOC of thin delegation to Joget's `FormDataDao`. Today it lives at `gs-plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/dao/MetaModelDao.java`.

When the Determinant evaluator and the application storeBinder are added (slice 1A per architecture doc §13), they both need to read `mm_*` rows. The question is the bundle decomposition that supports this.

### 1.2 Four reasonable options

- **A.** No split. Keep `MetaModelDao` and the new evaluator + storeBinder all inside `reg-bb-engine`. One bundle.
- **B.** Two-bundle split. Create `reg-bb-api` (interfaces + value types as a plain JAR) and put `MetaModelDao` + impl + new evaluator + storeBinder inside `reg-bb-engine`. Joget convention.
- **C.** Per-consumer DAO. Each consumer bundle has its own DAO impl. Refused on DRY.
- **D.** Four-bundle split. `reg-bb-api`, `reg-bb-mm-dao`, `reg-bb-evaluator`, `reg-bb-engine` all separate OSGi bundles. Strict SRP.

### 1.3 The customer in front of us

Lesotho Ministry of Agriculture and Food Security. One deployment. One team owning the codebase. One release cadence. One Joget instance. The "RegBB is meant for whole public sector at scale" framing names a possible future, not a present user.

### 1.4 Principles in scope (corrected vocabulary)

* **YAGNI** — don't add architectural complexity until a customer realises its benefit. Splits add deploy steps, manifest contracts, classloader complexity, and mental-model overhead. Each split must justify itself by a benefit a current user receives.
* **KISS** — prefer the simplest shape that meets the requirements. Single bundle with internal package structure is simpler than four bundles with OSGi service-lookup ceremony.
* **Convention over Invention** — Joget's own architecture (`wflow-plugin-base` as plain JAR) and api-builder's pattern (`apibuilder_api` as plain JAR) both treat shared contracts as compile-time JARs, not OSGi bundles. Diverging from that convention requires scale-proportionate justification.
* **SRP** — one bundle, one reason to change. Strong principle but applies at the bundle's *responsibility* level, not the *class* level. A single bundle whose responsibility is "the runtime engine for the Determinant component" is SRP-compatible.
* **DIP** — depend on abstractions. Achievable inside one bundle via Java interfaces; doesn't require OSGi bundle boundaries.

### 1.5 What revision 1's reasoning got wrong

Revision 1 invoked SRP and DIP, dismissed option B as "category mismatch in the api bundle" and option A as cycle-creation, and refused the YAGNI / KISS / Convention objections by characterising them as feature-need framing forbidden by the ADR template.

The dismissal was wrong on three counts:

1. **The "cycle" argument against option A was overstated.** `MetaModelDao` is a leaf utility; storeBinder calling evaluator calling MetaModelDao is a tree, not a cycle. The cycle revision 1 named would only exist if MetaModelDao called the evaluator, which it doesn't.
2. **The "category mismatch" argument against option B was abstract.** Joget's own `wflow-plugin-base` puts interfaces, DAOs, and services together. The "DAOs aren't really API" position is one architect's preference, not a structural principle.
3. **YAGNI is not feature-need framing.** Feature-need framing speculates about user need ("users will want X later"). YAGNI applies to architectural complexity ("don't carry complexity that buys nothing today"). The two are categorically different. The ADR template's §4 wording conflated them and revision 2 of this ADR is correcting that mistake.

---

## 2. Decision

`MetaModelDao` stays where it is — in `gs-plugins/reg-bb-engine`. No new bundles are created. The Determinant component for Phase 1 ships as **a single OSGi bundle** (`reg-bb-engine`) containing:

* `engine.element.*` — MetaScreenElement, MetaWizardElement (existing).
* `engine.dao.MetaModelDao` — existing concrete class (no interface extraction; the class is small and stable).
* `engine.api.*` — DeterminantEvaluator interface + value types (existing scaffolding).
* `engine.evaluator.FastPathEvaluator` — slice 1A's real implementation (replaces the existing stub).
* `engine.binder.RegBbApplicationStoreBinder` — new for slice 1A.

Internal collaboration uses Java interfaces and direct instantiation; no OSGi service-lookup machinery between components inside the same bundle.

If federated multi-tenant RegBB scale ever materialises (multiple customers, multiple teams, independent release cadences), the migration to a multi-bundle layout is a future ADR. Until then, the single-bundle shape is the architecture.

---

## 3. Reasoning

### 3.1 YAGNI applied to architectural complexity

A multi-bundle split delivers benefits at scale: Import-Package version ranges, independent api lifecycles, multi-version coexistence, federated team independence. None of those benefits has a current user. Lesotho MAFSN has one team, one cadence, one deployment. Carrying multi-bundle complexity for hypothetical federated future scale is paying complexity tax for value no one realises.

This is not feature-need framing about a feature ("users might want X later") — it's the YAGNI principle applied to architecture: don't carry the complexity of N bundles when one suffices.

### 3.2 KISS — fewer moving parts is simpler

One bundle has one `pom.xml`, one Activator, one manifest, one Build.java, one repack.sh, one deploy step, one set of Import-Package declarations, one set of build-stamp updates per release. Four bundles have four of each. The simpler shape is testable, deployable, and maintainable with less cognitive load. KISS says prefer it unless complexity earns its place.

### 3.3 Convention over Invention — Joget itself does this

`jw-community/wflow-core/pom.xml` is a single bundle containing form interfaces, form DAOs, form services, workflow types, and more — all together. Joget's own architecture treats "the form runtime" as one responsibility despite containing many sub-concerns. RegBB's runtime engine is a smaller object than Joget's; insisting it splits where Joget itself doesn't is inventing a discipline Joget hasn't found necessary.

`api-builder/apibuilder_api/pom.xml` (`<packaging>jar</packaging>`) shows a community-developed Joget plugin family also using plain JARs for shared contracts. Both Joget and api-builder converge on the same convention. Diverging from it for a Lesotho-only Phase 1 deployment lacks scale justification.

### 3.4 SRP at the responsibility level, not the class level

The bundle's responsibility is *"the runtime engine for the Determinant component"*. Within that responsibility, rendering, evaluation, persistence-binding, and DAO access are sub-concerns of one cohesive whole. SRP says one reason to change per bundle; "the Determinant runtime evolves" is one reason. The fact that internally the runtime has structure (rendering vs. evaluation vs. DAO) doesn't fragment the responsibility — it organises code within one responsibility.

The Java package boundary (`engine.element`, `engine.evaluator`, `engine.dao`, `engine.binder`) gives us SRP at the package level — which is sufficient for Java's encapsulation guarantees and developer-mental-model needs. We don't need bundle boundaries to enforce package boundaries.

### 3.5 DIP without OSGi bundles

The interfaces (`DeterminantEvaluator`, `MetaModelDao`) live in `engine.api`; implementations (`FastPathEvaluator`, `MetaModelDao` impl methods) live in their own packages. Consumers (storeBinder, MetaScreenElement) depend on the interfaces, not the impls. DIP is honoured. OSGi service-lookup is one way to wire DIP at runtime; direct instantiation is another way; both satisfy the principle. The ADR template doesn't require OSGi service registration to claim DIP compliance.

### 3.6 What revision 1's SRP argument actually was

Revision 1 said `MetaModelDao` is a different responsibility from `DeterminantEvaluator`. That's true at the class level. SRP at the bundle level asks a different question: what's this bundle's reason to change? Answer: "the Determinant runtime evolves." That's one reason, even though the runtime has multiple internal classes. SRP at the bundle level is satisfied by one bundle.

The overcorrection in revision 1 was reading SRP as "every distinct responsibility requires its own bundle." That's not what SRP says — it says "one bundle, one reason to change," which is a much looser constraint.

---

## 4. Alternatives considered and refused

### 4.1 Option D — four-bundle split (revision 1's choice)

**Refused on YAGNI, KISS, and Convention over Invention.** Each principle independently argues against the four-bundle shape at Lesotho scale; together they're decisive. The benefits the split delivers (versioned api, independent lifecycle, multi-version, federation) all materialise only at scales we don't operate. The costs (deploy choreography, manifest complexity, classloader unity concerns, mental-model overhead, divergence from Joget convention) are paid today by a single team that derives no benefit from carrying them.

This refusal is not feature-need framing — there's no speculation about future user features. It's principle-based: YAGNI says don't carry architectural complexity that no current user benefits from. KISS says prefer simpler shapes when they suffice. Convention over Invention says don't deviate from Joget's pattern without scale-proportionate justification.

If federated multi-tenant scale ever arrives, the migration to option D is mechanical and bounded (~30 minutes; the api package extraction is a `mv` and a manifest edit). The reversibility is symmetric — A→D and D→A are equally cheap. Choosing A first costs nothing extra because we're refining existing code rather than scaffolding fresh bundles.

### 4.2 Option B — two-bundle split (api as plain JAR)

**Refused on YAGNI applied recursively.** Option B is one step toward D and avoids D's biggest mistake (api as OSGi bundle), but still pays for splits whose benefits don't materialise at our scale. A separate api JAR makes sense when multiple consumer plugins from different teams need the api contracts. We have one consumer team (us) and our consumers all live in the same bundle. Splitting the api into a separate compile-time JAR with no inter-team boundary buys nothing.

This is an honest YAGNI refusal: one bundle suffices, two bundles don't add value, four bundles add cost. The simpler shape wins.

### 4.3 Option C — per-consumer DAO duplication

**Refused on DRY.** ~7 lookup methods duplicated across bundles is real drift risk, especially for `mm_*`-touching SQL where one consumer fixes a Postgres folding bug another doesn't. DRY is operational, not aesthetic. Refused.

### 4.4 Why revision 1's option D refusal was wrong

For completeness and as a methodological lesson: revision 1 refused option A on a "cycle" argument that wasn't actually a cycle (storeBinder → evaluator → MetaModelDao is a tree). It refused option B on a "category mismatch" argument that Joget itself doesn't honour. It dismissed YAGNI by characterising it as feature-need framing forbidden by the ADR template. All three dismissals were wrong; the ADR template's §4 wording made the third dismissal feel principled when it wasn't.

---

## 5. Consequences

### 5.1 Positive

- **One bundle to deploy.** Replace existing `reg-bb-engine-8.1-SNAPSHOT.jar` with the new build. Same operator workflow as builds 25–31.
- **Visible in Manage Plugins.** The bundle appears with all its registered plugins (MetaScreenElement, MetaWizardElement, RegBbApplicationStoreBinder).
- **Aligned with Joget convention.** Future maintainers reading the codebase find a familiar pattern — interfaces, DAOs, services in one bundle, organised by Java package.
- **Smaller cognitive surface.** No OSGi service-lookup ceremony between the storeBinder and the evaluator; direct Java method calls.
- **Faster iteration.** Slice 1B (SQL path), slice 1C (multi-rule scoring) all extend the same bundle without manifest negotiations between bundles.

### 5.2 Negative

- **Less prepared for federated scale.** If GovStack RegBB grows beyond Lesotho into multi-team federation, we'll need to refactor to a multi-bundle layout. The cost: ~30 minutes of refactor work; not free, not catastrophic. We're betting that scale arrives later, or doesn't.
- **Bundle gets bigger over time.** Every Determinant slice (1B, 1C, 1D, 1E, 1F) adds code to this bundle. By Phase 1 closeout the engine bundle could be ~5000 LOC. Manageable; comparable to other Joget plugins; testable.
- **Internal package discipline matters more.** Without bundle boundaries to enforce separation, the team has to discipline itself: rendering code in `engine.element`, evaluation code in `engine.evaluator`, DAO code in `engine.dao`. Code review enforces; static analysis tools could too if needed.

### 5.3 Neutral

- The interfaces in `engine.api` remain in the engine bundle's package namespace. If revision 3 ever splits to `reg-bb-api`, the rename is mechanical (`engine.api` → `regbb.api`) and contract-preserving.
- The existing repack.sh, Build.java, Activator pattern is preserved verbatim.
- Slice 1B+ work continues against the single-bundle shape until scale justifies otherwise.

---

## 6. Implementation outline

Sketch.

1. Replace `reg-bb-engine/.../engine/evaluator/FastPathEvaluator.java` (current Week-1 stub returning ERROR) with the slice 1A implementation: parser-evaluator for the operator subset, `$applicant.*` and `$constant.*` ref resolution, L1 cache.
2. Add `reg-bb-engine/.../engine/binder/RegBbApplicationStoreBinder.java` per ADR-005: extends `WorkflowFormBinder`; super.store + try/catch + outcome write per ADR-006/007.
3. Update `reg-bb-engine/Activator.java` to register `RegBbApplicationStoreBinder` alongside the existing element registrations.
4. Discard the abandoned bundles from this session's earlier (revision 1) work: delete `gs-plugins/reg-bb-api/`, `gs-plugins/reg-bb-mm-dao/`, `gs-plugins/reg-bb-evaluator/`. Their built JARs are stale.
5. The form definition update (`subsidyApplication2025.json` → uses `RegBbApplicationStoreBinder`, adds `eligibility_outcome` field) and the seeder fixture update (`DET_LOWLANDS` rule moved from JSON envelope to DSL source) stand — both are bundle-independent.

Lands in slice 1A. Build and deploy a single JAR (build-034 of `reg-bb-engine`).

---

## 7. Open questions

### 7.1 When does multi-bundle become justified?

Trigger conditions: (a) a second customer with a separate release cadence appears; (b) a third-party plugin needs to consume `DeterminantEvaluator` types from outside our codebase; (c) the bundle grows beyond a maintainability threshold (~10k LOC, subjective). Any of these reopens this ADR.

### 7.2 Should `engine.api`'s interfaces be promoted to a `regbb.api` package?

Today they're under `global.govstack.regbb.engine.api`. The `engine` qualifier reflects today's architecture (one bundle). If Phase N introduces a second consumer plugin family, the package rename to `global.govstack.regbb.api` is the API-stabilisation step. Not today.

---

## 8. Spec amendments required

* **`_design/determinant-architecture.md` §6** — replace the four-bundle decomposition diagram with the one-bundle shape; note that multi-bundle is a deferred decision pending scale evidence.
* **`_design/determinant-architecture.md` §12 Q1** — replace with a backlink to this ADR (revision 2).
* **`_design/adr/adr-002-mm-dao-placement.md`** — this file (the revision 1 content is preserved in git history; revision 2 supersedes it in the working copy).
* **`_design/determinant-adr-drafting-session-prompt.md` §4** — distinguish feature-need framing (invalid) from YAGNI applied to complexity (valid). Add YAGNI, KISS, and Convention over Invention to the named principle vocabulary in §3 Reasoning.
* **`CLAUDE.md`** — add a paragraph: when an ADR cites a principle, it should also name the principle that pulled the other way and why it lost. A decision with one principle named and no contrary principle named is suspect.

---

## 9. Decision record

| | |
|---|---|
| Decision | Single OSGi bundle (`reg-bb-engine`). No api / mm-dao / evaluator separation in Phase 1. Multi-bundle deferred until scale justifies. |
| Decided by | *(name on acceptance)* |
| Decided on | *(date on acceptance)* |
| Effective from | Slice 1A — build-034 of `reg-bb-engine`. |

---

*End of ADR-002 revision 2.*
