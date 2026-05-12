# Convergence Framework — Lesotho Farmers Portal toward the Registration Building Block

**A framework for thinking about the convergence of the Lesotho Agricultural Subsidy Management System onto the GovStack Registration Building Block as specified in the vision paper.**

| | |
|---|---|
| Status | **Revision 2** — supersedes revision 1 of 2026-04-28 |
| Audience | Farmers Portal engineering team |
| Companion documents | `regbb-as-metadata-driven-generation.md` (the vision; baseline), `govstack-registration-bb-spec.md` (the spec; normative), `regbb-solution-architecture-spec.md` and `regbb-software-architecture-spec.md` (current architectural drafts; provisional), `mm-completeness-audit.md` (verdict: green pending §3.2 alignment), `adr-001-rule-grammar-canon.md` (rule grammar decision) |
| What this document is | The lens through which we make convergence decisions |
| What this document is not | An audit of fit, a migration plan, a phase sequencing, or a plugin retirement timeline — all of which are downstream artefacts |

> **Revision note.** Revision 1 of this framework committed the convergence to the closed twenty-operator grammar as the rule language for the converged system, with the DSL stack on the retire list. ADR-001 revision 2 (2026-04-28) revisits that commitment under two project principles: *(P1) configuration over code on both citizen and registry sides — operators must author rules including aggregation directly without engineering tickets*; and *(P2) excellent UX for citizens and back-office — standardisation is not pursued at the cost of authoring UX*. The decision recorded by ADR-001r2 is to adopt the DSL grammar as the canonical authoring grammar, with the closed twenty operators preserved as the engine's *fast-path operator subset* — an internal optimisation, not a separate authoring grammar. This revision of the framework records that change in §3.2 and reclassifies the four DSL plugins (`rules-grammar`, `joget-rules-api`, `joget-rule-editor`, `subsidy-eligibility-runtime`) in §5. Revision 1 of the framework is recoverable from git history.

---

## 1. What this document is and is not

This is the framework — the lens, the discipline, the working test — by which the team decides whether any Lesotho component, any proposed change, and any new feature request is moving the system toward the Registration BB or away from it. It exists because the convergence is not a one-time decision that can be discharged by writing a migration plan; it is a posture the team has to hold across every architectural choice for as long as the convergence is live.

The framework does not tell you what to build next. It tells you how to recognise whether what you are about to build belongs in the converged architecture, belongs as a transitional bridge until something in the converged architecture reaches parity, or belongs in the past tense — work that should not happen because the converged equivalent is already on its way. It does not name horizons, does not assign ownership, and does not estimate effort. Those are the next document's job.

The framework is also not a restatement of the vision paper or the GovStack spec. It assumes both as read. Where it cites them, it does so to make a working point operational, not to re-explain the source.

The intended use is concrete. When a code review surfaces a question — "should we wire `FormQualityPostProcessor` onto `spApplication` to close the dormant rules?" — the framework gives the team a shared way to answer it. When a backlog grooming surfaces a feature — "the agronomist wants a derived 'eligibility-likelihood' score on the farmer record" — the framework gives the team a shared way to classify it. The point is not that the framework produces the right answer mechanically; it is that the framework produces the right *question* consistently, so that whichever team member is in the chair on a given Tuesday is reasoning about the architecture along the same axes as the team member who sat there last Tuesday.

## 2. The trio that defines the architecture

The Registration BB's architectural core, per vision paper §3, is the trio of properties that together make a generator a generator. Closed and complete metadata. An engine that interprets rather than templates. A small, closed, additive-only grammar. None of the three is independently sufficient. A closed grammar without complete metadata is a parser without a program. Complete metadata without an interpreting engine is a database without behaviour. An interpreting engine without a closed grammar is a scripting host with extra steps. The convergence's success criterion is that the Lesotho system has all three at the end of it, not two of three with the third deferred.

The team's working test on any architectural decision, large or small, is to classify it against the trio. Three classifications, no fourth.

A decision **serves the trio** if it makes the metadata more complete, the engine more uniformly interpretive, or the grammar's closed boundary more honest. Standing up `mm_service` and `mm_screen` rows for `prog001` serves the trio because it relocates a concrete service into the closed metadata. Implementing `MetaScreenElement` to read `mm_field` rows at render time serves the trio because it is the engine acting interpretively. Translating an existing rule from the DSL into a Determinant serves the trio because it is the closed grammar absorbing what the open grammar previously handled.

A decision is **neutral to the trio** if it neither helps nor hurts. Improving the GIS polygon widget's rendering performance is neutral. Fixing a bug in `form-creator-api`'s cache eviction is neutral. Adding a new master data lookup that nothing in the meta-model references yet is neutral. Neutral work is fine, often useful, and sometimes urgent — it is just not convergence work, and should not be claimed as such.

A decision **competes with the trio** if it adds capability outside the meta-model that the meta-model is supposed to cover, or extends the grammar in ways that are not earned by evidence, or builds engine code that branches on domain-specific concerns the meta-model could express. Wiring a new SQL rule into `form-quality-runtime` competes with the trio, because rules belong in Determinants and the SQL rule is a parallel grammar. Adding a hardcoded list of programme types into `application-engine-runtime` competes with the trio, because programme structure belongs in `mm_service` rows. Authoring a new `{serviceId}.yml` file by hand competes with the trio, because the YAML is a stand-in for `mm_*` content that is about to be the source of truth.

The disposition rules follow from the classification. Decisions that serve the trio go in the queue. Decisions that compete with the trio do not, even if they would close a useful gap in the short term, because closing the gap with competing work makes the convergence harder later, not easier. Decisions that are neutral are evaluated on their own merits — operational urgency, user value, technical debt — and live in their own backlog stream.

The classification is not always immediately obvious. A decision can serve the trio in one respect and compete with it in another. The framework's discipline is to surface both readings, decide which dominates, and record the decision. The classification is a working tool, not a tribunal.

## 3. The three settled questions and what they commit us to

Three questions about the convergence direction have been answered. Each answer commits us to a posture; each posture has costs and unlocks.

### 3.1 The meta-model is the source of truth

The eleven `mm_*` entities — institution, service, registration, role, screen, field, action, catalog, required document, fee, determinant, plus the back-office processing-screen entity — are the canonical representation of any registration service. `prog001` and the farmer-registration shape are both expressed in `mm_*` rows. `spProgramMain` and its nine tabs cease to be the source of truth; they become, at most, an authoring UX over `mm_*` and are most likely retired in favour of the meta-model's own admin CrudMenus. The same is true of `farmerRegistrationForm` and `parcelRegistration` insofar as their *structure* (which fields, in which order, on which tab, with which validators) is meta-model content; their domain *content* — the actual farmer records — remains where it is, as registry data, accessed through the IM connector.

What this commits us to. Every authoring tool, every form definition, every rule, every fee schedule, every document requirement that is currently expressed in domain-specific Joget forms migrates into `mm_*`. There is no parallel source of truth. There is no "we'll keep `spProgramMain` for the friendly UX and `mm_service` for the engine" — that is two sources of truth, and per vision paper P3 it is exactly what the framing rules out.

What this unlocks. New services are added by inserting `mm_*` rows. The two-service test from vision paper §8 is taken internally between farmer registration and subsidy application; passing it is the empirical evidence that the meta-model is doing its job. The §8 REST API can be implemented thinly over `mm_*` queries because there is no other place service definitions live.

What this costs. The migration of `prog001` and the farmer registration shape into `mm_*` is unavoidable work that does not exist on any current backlog. The custom Joget forms that today serve as the programme designer become obsolete in their current form; the team has to choose between retiring them outright and refactoring them into thin authoring shells over `mm_*`.

### 3.2 The Determinant is the rule grammar (one grammar, one editor, two evaluators)

The DSL grammar defined by the `rules-grammar` ANTLR parser is the rule language for the converged system. Within that grammar, the closed twenty-operator subset (comparison and Boolean operators plus arithmetic for fee formulas) is the *fast-path* operator subset — the operators the engine's in-memory tree-walker can evaluate in microseconds, suitable for per-keystroke conditional UI. Aggregation, filtered set lookups, `$registry.*` references, and temporal references live inside the same grammar and are evaluated by the engine's compile-to-SQL evaluator at submit-time. **Operators author every rule in one editor (`joget-rule-editor`) using one language; the engine routes evaluation to the fast-path tree-walker or the SQL-compile evaluator by static AST analysis. The routing is implementation-private.**

The closed-set discipline transfers from the operator enum to the parser grammar. The DSL grammar is closed at the ANTLR-grammar boundary — new constructs (operators, set quantifiers, temporal functions) require a deliberate `rules-grammar` plugin release; nothing outside the grammar is parseable; there is no `eval()`, no scripting, no plugin-loaded operators. The closed-twenty fast-path subset is closed by a parallel five-step protocol because additions affect what runs on the fast path. Both paths are additive-only. The "no second rule engine" property in vision paper P2 is preserved: there is one grammar at the authoring level, one entry point at the engine level (`DeterminantEvaluator`), and the two evaluators behind it are an implementation detail callers do not see.

Every rule that today lives in `form-quality-runtime`'s SQL grammar or in `application-engine-runtime`'s conditional seeding logic is re-expressed as a Determinant authored in the DSL — fast-path-eligible if the rule references only `$applicant.*` and uses only the closed twenty operators, SQL-path otherwise. The team's response when a rule does not translate to the *fast-path* subset is *not* to add an engine branch; it is to author the rule in the DSL grammar's broader surface, where aggregation and `$registry.*` references are first-class. ADR-001r2 records this in detail.

What this commits us to. The SQL-rule layer of `form-quality-runtime` is on the retire list as soon as Determinants with `field` and `screen` scope cover the corresponding cases. The four DSL plugins (`rules-grammar`, `joget-rules-api`, `joget-rule-editor`, `subsidy-eligibility-runtime`) are *not* on the retire list — they are first-class infrastructure of the converged engine. `joget-rule-editor` is the canonical Determinant authoring tool; `joget-rules-api` is the SQL-path compiler; `rules-grammar` is the parser. `subsidy-eligibility-runtime` keeps its function as the SQL-path runner regardless of its historical "subsidy" name (the same evaluator handles eligibility for any service whose Determinants use DSL extensions). What this *does* commit us to is integrating the DSL stack into the converged `reg-bb-engine` interface — `DeterminantEvaluator` becomes the single entry point that routes to either the in-memory tree-walker or the DSL stack's SQL evaluator, and analysts see one editor and one language regardless.

What this unlocks. One auditable rule grammar interprets every rule in the system. Operators express aggregation, registry references, and temporal queries directly in their authoring tool — *configuration over code on both citizen and registry sides*. Server-authoritative behaviour (vision paper P6) follows automatically: the browser cannot make a business decision because the same server-side evaluator gates the UI, the workflow, and the §8 REST endpoint.

What this costs. Cross-jurisdiction rule portability is partial: rules expressed in the fast-path operator subset are portable across any RegBB-conformant peer; rules using DSL extensions (aggregation, `$registry.*`, temporal) are this implementation's superset and not portable. The closed-set property is preserved at the grammar level but the spec divergence is real and recorded in `regbb-solution-architecture-spec.md` §1.5. The team's discipline against drift mode 2 (§4.2) now applies at two levels: extending the fast-path subset, and extending the DSL grammar itself. Both require multi-service evidence; neither is a routine engineering decision.

### 3.3 The citizen-portal / back-office decoupling is real

In the deployed system, the citizen portal is one Joget instance and each back-office is a separate Joget instance. There is, in any realistic operational picture, one citizen-facing portal and many back-offices — agriculture, health, education, social welfare — each running the back-office side of one or more services. The submission backbone (`wf-activator` → `doc-submitter` → `processing-server`) survives the convergence because it is the layer that bridges the two.

What this commits us to. Two architectural facts the SAD as written does not address. First, the seven-plugin set runs in *different subsets* on the citizen-portal and back-office instances, and we have to be deliberate about which plugin goes where. Second, the submission backbone is part of the architecture, not an auxiliary; it is the eighth concern in the SAD's seven-plugin partitioning and needs an explicit owner in our architectural model.

What this unlocks. The once-only principle works across instances rather than within one. A reference like `$registry.farmers.parcels[*].size_ha` from a subsidy-application Determinant on the citizen portal resolves through the IM connector against the farmer registry on the AgriMin back-office instance; this is exactly what the IM connector is for. The architecture extends to many back-offices without architectural change — onboarding the Ministry of Health is operationally a deployment of an instance and a few `mm_*` rows, not an engineering project.

What this costs. The submission backbone has to be re-pointed at `mm_*` rather than at hand-authored YAML. The right shape of this — does the publisher generate the metadata payload at publish time, does the backbone read `mm_*` directly across instances, or is there a third option — is a design question that has not been answered. Nothing in the convergence requires it to be answered yet, but it has to be answered before the backbone is rebuilt against the converged architecture.

### 3.4 What follows from the three answers together

The three answers compose. Meta-model as source of truth plus Determinant as grammar gives us the inside-Joget converged engine. Adding the multi-instance topology gives us the inter-Joget submission layer. Together they describe a system whose service definitions live in one place, whose rules live in one grammar, and whose runtime spans as many Joget instances as the deployment topology requires, with the IM connector and the submission backbone as the two cross-instance bridges. This is a coherent architecture. The convergence is the work of getting from the present state to it.

## 4. The two drift modes, restated operationally

Vision paper §7 names two drift modes. They are easy to recognise on paper and surprisingly hard to recognise in the moment. The framework's job is to make them recognisable in the moment, when the team is making decisions under time pressure.

### 4.1 Drift mode 1 — the domain-specific code path

This is the failure mode where a service has a quirk the meta-model cannot quite express, and the pragmatic response is to add a domain-specific code path to the engine to handle it. It is the failure mode that ends the system's life as a generator, because the moment the engine has a branch that says "if this is a farmer-subsidy registration, do X; if this is a vehicle registration, do Y", the engine has stopped interpreting the meta-model and started encoding domain knowledge. From that point forward, every new service either fits into one of the existing branches or requires a new branch, and the system has become a framework with hardcoded variants.

In day-to-day work, drift mode 1 looks like:

- "The DSL has aggregation operators we use in this rule, and the closed grammar doesn't. Let's add a special engine path that handles aggregations for eligibility rules."
- "Subsidy benefit items don't fit `mm_fee` because they're grants not fees. Let's add a `BenefitCalculator` to the engine that handles grants."
- "The farmer registration's parcel sub-records don't fit `mm_screen` cleanly. Let's add a `ParcelSubRecordRenderer` to the engine."
- "GIS polygon capture doesn't fit `mm_field`. Let's add a `GisFieldType` to the engine that knows how to render and persist polygons."

The signature of drift mode 1 is the word "let's add ... to the engine". When that phrase appears, the framework's response is to refuse and ask three questions instead. First, does the meta-model have a way to express this that we have overlooked? Often the answer is yes; `mm_field` already supports custom widget references, and the GIS polygon belongs there as a widget rather than as engine code. Second, is this concern actually inside the meta-model's remit, or is it something that belongs in the workflow, in a custom widget, or as out-of-scope deliberately? Third, if it genuinely doesn't fit and is genuinely in scope, is there evidence from more than one service that the meta-model needs to be extended to cover it? If the evidence is there, the meta-model is extended — a new field on an entity, a new entity, a refinement of semantics — and the engine continues to interpret the (now richer) meta-model uniformly. The engine never gains a domain-specific branch.

Refusing drift mode 1 in the moment is harder than describing it on paper. The pragmatic case is always strong: there is a concrete user request, a concrete deadline, a concrete way to deliver value this week. The discipline is that delivering value this week by adding an engine branch costs the architecture's properties. The framework's posture is that this is not a trade-off the team is authorised to make on its own initiative; it is escalated, documented, and decided deliberately, with the cost of acceptance recorded.

### 4.2 Drift mode 2 — expressiveness without evidence

This is the symmetric failure mode. The temptation to extend the meta-model not because a domain-specific quirk demanded it, but because expressiveness feels like progress. A new operator because it would be useful. A new field on an entity because someone might want it. A new entity because it would be more elegant. Each addition individually is small and harmless; the cumulative effect is a meta-model that is no longer closed, a grammar that is no longer small, and an engine that is increasingly hard to reason about because its surface keeps growing.

In day-to-day work, drift mode 2 looks like:

- "We should add a `regex_replace` operator to the grammar. It's only one operator, and it would let us normalise input."
- "Let's add a `priority` field to `mm_required_doc` so we can render documents in priority order."
- "We should add a `mm_notification_template` entity to the meta-model so notifications are configurable."
- "The grammar should have a `case` operator. It's just sugar over nested `if`s, but it's cleaner."

The signature of drift mode 2 is the absence of multi-service evidence. The proposal is justified by hypothetical use, by elegance, by symmetry with something else in the meta-model, by a single service's request. It is never justified by "two services need this and they cannot both be expressed without it". When that signature appears, the framework's response is to refuse and to record the proposal in a queue of *would-be additions awaiting evidence*. If a second service surfaces the same need, the proposal moves out of the queue and into a deliberate evidence-based addition. If it does not, it stays in the queue forever, which is the right outcome.

The rule grammar is closed at two levels (per §3.2): the fast-path operator subset (closed twenty), and the DSL grammar itself (closed at the ANTLR-grammar boundary). Neither is a starting point; both are deliberate ceilings, designed to be adequate for the surveyed services and inadequate for inventive expansion. Each level has its own additive-only release protocol — five-step for fast-path, `rules-grammar` plugin release for DSL constructs — and the discipline applies symmetrically. The framework's posture is that the team's instinct to extend (at either level) should be overridden by the framework's discipline to wait for evidence.

### 4.3 The structural similarity of the two drift modes

The two modes look opposite — one adds engine code, the other adds metadata — but they fail in the same way. Both make the architecture's properties weaker. Drift mode 1 weakens completeness (the metadata stops being the single source of variation). Drift mode 2 weakens closure (the grammar stops being closed). Both feel like progress in the moment. The framework's discipline is to recognise that *feeling* of progress as the warning sign rather than the validation.

The team's working stance is asymmetric vigilance. Drift mode 1 is more dangerous because it is harder to undo (engine code with semantics tends to acquire dependents); drift mode 2 is more frequent because additive metadata changes feel inherently safe. The team should be especially alert to drift mode 1 in code reviews and especially alert to drift mode 2 in design reviews and backlog grooming.

## 5. Classifying every existing component against the trio

The Lesotho system's current plugin set was built before the convergence was settled. Each plugin has a relationship to the trio that the framework can read off. The classification is not a retirement schedule — it is the input to a retirement schedule, which is a separate document.

### 5.1 Components that serve the trio

These are the components that, in their current form, are doing work the converged architecture also requires. They stay.

`form-creator-api` solves the two-cache problem in Joget DX 8 — every form deployment goes through its REST surface to evict the AppDefinition cache and the per-form Hibernate ORM mapping atomically. This is operational infrastructure the meta-model migration depends on; without it, deploying `mm_*` form definitions cannot be done safely. It is orthogonal to the trio in the sense that it is platform-tier rather than meta-model-tier, but it serves the trio by making meta-model deployment operationally sound.

The custom widget plugins — `joget-gis-server` plus `joget-gis-ui`, `joget-concat-field`, `joget-smart-search`, `embedded-datalist`, `joget-advanced-filters` — provide field rendering capabilities the converged engine references via `mm_field.widget`. They are widget-tier, called by `MetaScreenElement` rather than competing with it. They stay.

The submission backbone (`wf-activator`, `doc-submitter`, `processing-server`) serves the trio by carrying meta-model-driven payloads across instance boundaries, given that Q-C committed us to a multi-instance deployment. Its current implementation reads YAML rather than `mm_*`; the implementation needs to be re-pointed at the converged source of truth, but the architectural slot it occupies survives.

`joget-status-framework` provides cross-cutting audit infrastructure that the converged architecture also needs (the SAD has per-plugin audit tables; whether the team prefers one shared audit log or per-plugin tables is a separable decision). It stays at least until that decision is taken.

The four DSL plugins — `rules-grammar` (ANTLR parser), `joget-rules-api` (REST + DSL→SQL compiler), `joget-rule-editor` (operator authoring UI), `subsidy-eligibility-runtime` (per-applicant SQL-path runner) — serve the trio under the framing established by ADR-001r2 and §3.2 of this document. `rules-grammar` is the parser for the canonical rule grammar. `joget-rules-api` is the SQL-path compiler that the converged engine's `DeterminantEvaluator` delegates to for SQL-path rules. `joget-rule-editor` is the canonical Determinant authoring tool — the editor analysts use for every Determinant regardless of which evaluator the engine routes it to. `subsidy-eligibility-runtime` keeps its runtime function (per-applicant SQL execution + result persistence) as the SQL path of `DeterminantEvaluator`; despite the historical "subsidy" name, the evaluator is generic to any service whose Determinants use DSL extensions. What changes for these four during convergence is integration shape, not retirement: they are wired into the converged engine's single entry point rather than running as a parallel rule layer.

### 5.2 Components that are neutral to the trio

These are doing useful work that does not directly bear on the convergence. They neither help nor hurt.

The Joget core configuration — userviews, themes, master data forms (the 100+ MD lookups), the workflow engine, the foundation layer — is neutral. It is the substrate on which the convergence runs, not a participant in it. The vision paper §4 is explicit that the no-code platform's native capabilities are a load-bearing reason for choosing Joget; the convergence does not redo them.

`farmer-derived-plugin` (and its proposed generalisation to `derived-snapshot-runtime`) is neutral. Derived snapshot computation — projecting registry data into a flattened table for reporting or query convenience — is a data engineering concern, not a meta-model concern. It can keep running in its current form; whether the generalisation should happen is unrelated to the convergence and can be evaluated on its own merits.

The various utility plugins flagged as undocumented in the overview (`app-def-provider`, `registry-overview-plugin`, `diagnostic-tool`) are neutral until documented. The framework's posture is that neutral-but-undocumented is not a stable state — they should be either documented as serving a specific purpose or retired, on a separate cadence from the convergence.

### 5.3 Components that compete with the trio

These are the components whose function the converged architecture covers differently. They retire as the converged equivalent reaches parity. They are not retired pre-emptively; they keep running until the converged equivalent can take over.

`form-quality-runtime` competes with the trio in two ways. Its SQL-rule grammar in `qa_rule` is a second rule grammar parallel to the canonical DSL. Its post-processor wiring is a second engine path parallel to `MetaScreenElement`'s render-time evaluation. Both are meta-model-tier work the converged architecture does through Determinants and the engine. The plugin retires once Determinants with `field` and `screen` scope cover what the SQL rules cover today; the SQL rules themselves translate to fast-path Determinants where they reference only `$applicant.*` data, and to SQL-path Determinants where they reach across forms or aggregate.

(The DSL stack — `rules-grammar`, `joget-rules-api`, `joget-rule-editor`, `subsidy-eligibility-runtime` — was on the retire list in revision 1 of this framework. Per ADR-001r2 it now serves the trio (§5.1 above) and is *not* retired. The earlier classification is recorded for historical context only; do not act on it.)

`application-engine-runtime` competes with the trio in its current implementation, although the function it performs (seeding application child rows from a service definition) is something the converged engine also has to do. The path forward is most likely a rewrite as part of `reg-bb-engine` rather than a retirement. The current plugin retires once the converged engine reads from `mm_required_doc`, `mm_fee`, `mm_benefit` (the Lesotho-instance extension established by the audit), and `mm_determinant` rather than from `sp_doc_requirement_row`, `sp_benefit_item_row`, and `sp_elig_criterion`.

`identity-resolver-runtime` is a borderline case. Its function — pre-fill applicant fields from a registry record keyed by foundational ID — is what the IM connector does for `$registry.*` references inside Determinants. In the converged architecture, identity resolution is a Determinant pattern, not a separate plugin. The current resolver retires once the IM connector reaches parity. Until then it is doing real work and is left in place; classifying it as competing with the trio is a statement about its eventual disposition, not about whether it should be touched today.

`decision-engine-runtime` competes with the trio in a different way. Its function — applying an operator's decision and issuing a row in `imEntitlement` — straddles two architectural concerns the converged design separates. The decision itself is a workflow concern, handled by the analyst's hand-built XPDL; the entitlement issuance, in the converged design, is a credential issued by `reg-bb-credential-issuer`. The current plugin retires when those two concerns are taken over by their converged equivalents, which is a later motion than the meta-model migration.

### 5.4 Disposition is not a schedule

The classifications above tell us what each component's eventual disposition is. They do not tell us when. Retirement timing is a downstream artefact, scheduled by the migration plan, gated by the converged equivalent's reaching parity, and constrained by the operational continuity of the running Lesotho system. The framework's commitment is only that the disposition is settled — every component knows what its end state is — so that no work is invested in a competing component's growth, and no urgency to retire is allowed to outpace parity.

## 6. Multi-instance deployment topology

The SAD as written assumes one Joget runtime per RegBB instance. Q-C committed us to a different topology. The framework needs to make explicit which plugins live where, and what crosses instance boundaries, so that team conversations about deployment have a shared reference.

### 6.1 The instance partitioning

The deployed system has, at minimum, two Joget instances and in the realistic operational picture more than two. The citizen portal is one Joget instance. Each back-office is a separate Joget instance — AgriMin for farmer registration and subsidy application, MoH for any health-related registration, MoE for education-related registration, and so on. There may also be a third "admin" instance hosting meta-model authoring; whether this is a separate instance or part of the citizen portal or part of one of the back-offices is a deployment decision, not an architectural one.

This is a topology, not an architecture. The architecture is still seven plugins, the meta-model, the Determinant grammar, the engine. The topology is which plugins are deployed in which instance.

### 6.2 Plugin-to-instance map

A working assignment, subject to revision as the deployment is built out:

| Plugin | Citizen portal | Back-office | Admin | Notes |
|---|---|---|---|---|
| `reg-bb-engine` | Yes | Yes | Yes | Renders citizen screens on the portal, operator review screens on the back-office, admin meta-model screens on the admin instance. The engine is the same code in all three; what differs is which `mm_*` rows it reads. |
| `reg-bb-publisher` | No | No | Yes | Validation, binding-row creation, and userview generation are admin-side concerns. |
| `reg-bb-api` | Yes | Yes | Optional | The §8 REST surface. Citizen-side exposes service catalogue, application submission. Back-office-side exposes processing endpoints. |
| `reg-bb-iam-bridge` | Yes | Yes | Yes | Both sides need OIDC validation, citizen-side primarily for citizens, back-office side primarily for operators. |
| `reg-bb-im-connector` | Yes | Yes | No | Both sides resolve `$registry.*` references during Determinant evaluation. |
| `reg-bb-payment-connector` | Yes | Optional | No | Payment is normally citizen-initiated at submission or post-decision. |
| `reg-bb-credential-issuer` | No | Yes | No | Credential issuance is post-decision and lives back-office-side. |
| Submission backbone | Yes (sender) | Yes (receiver) | No | The portal sends, the back-office receives. The same plugin code; the configuration differs. |

The map is not rigid. A small deployment may collapse the admin instance into one of the others. A larger deployment may split the back-office further. The framework's commitment is that the plugin set is the same; the deployment is configuration of where each plugin runs and what it reads.

### 6.3 What crosses instance boundaries

Three things, and only three things.

The submission payload, from citizen portal to back-office, when an application is submitted. This is the submission backbone's job. The payload is meta-model-derived — `app_application` content shaped by the service's `mm_*` definition — and travels over a stable HTTP contract.

The IM-connector's `$registry.*` resolution, from any instance that runs Determinants to any instance that hosts a registry the Determinant references. This is the IM connector's job. Inbound and outbound.

The meta-model itself, conceptually, in the sense that the citizen-portal's engine and the back-office's engine both need to read the same `mm_*` content for a given service. Whether this is achieved by replicating `mm_*` rows across instances, by having the engine fetch them from the admin instance at runtime, or by some hybrid is a deployment design question. The architectural commitment is only that there is one source of truth and every engine reads from it.

Nothing else crosses. Application data does not flow back from back-office to citizen portal except through the submission backbone's response and through user-facing status endpoints exposed by the §8 API. Workflow state lives in the back-office's Joget; the portal does not see Joget Shark internals across instances. Audit trails are local to each instance and aggregated, if needed, by reporting tools that read from each instance's audit tables.

This is what makes the multi-instance topology architecturally tractable. The cross-instance contracts are few and stable. Adding another back-office is operationally a deployment exercise, not an architectural change.

## 7. The audit and what each verdict authorises

The framework calls for an audit before the convergence's first code change: a meta-model completeness audit of farmer registration and subsidy application against `mm_*`. The audit is a separate document. The framework's role here is only to say what each of the audit's possible verdicts authorises.

### 7.1 Green verdict

Both services fit `mm_*` cleanly, with at most a small number of additive extensions each supported by multi-service evidence. The convergence proceeds. The next artefact is the migration plan — a phase sequencing that takes us from the present state to a state where `prog001` and the farmer registration shape are running on `mm_*` rows interpreted by `reg-bb-engine`. The first phase of that plan stands up `reg-bb-engine`, `reg-bb-publisher`, and the meta-model schema, and migrates the simpler of the two services into it as a fixture. No Lesotho plugin is retired in this phase; the converged stack runs alongside the existing one until parity is reached.

### 7.2 Amber verdict

Both services fit but only by stretching the meta-model in ways the audit cannot fully justify on multi-service evidence. The convergence still proceeds, but with a deliberate scope-narrowing. The team commits to a *minimum convergence target* — the parts of `prog001` and the farmer registration shape that fit cleanly today — and queues the stretching cases for evidence-based addition later. This is the framework's posture against drift mode 2 made concrete: the additions wait for a second service that needs them. The migration plan is still authored; its phase 1 is smaller; its phase 2 takes longer because some non-fitting cases require deferred work to land first.

### 7.3 Red verdict

The meta-model does not fit one or both services in ways that would require domain-specific engine branches to resolve. Convergence is paused. The next artefact is not a migration plan but a meta-model revision proposal — a deliberate, evidence-based extension to the meta-model itself, taken back to the GovStack spec authors if appropriate, and re-tested against the audit's failing cases. The Lesotho system continues running in its current form during this period; no Lesotho plugin retirement happens; the system's near-term roadmap reverts to non-convergence concerns until the meta-model is in a state to support the convergence.

A red verdict is not a failure. It is a signal that the bet vision paper §8 makes — that government registration is uniform enough across domains to be implemented once and configured many times — does not hold for the specific shape Lesotho needs without further work. That is information the team and the GovStack programme want, regardless of how disappointing it is in the moment.

### 7.4 What each verdict has in common

All three verdicts produce a deliberate next step that is *not* "carry on with the current Lesotho roadmap as written". The Horizon-1 closeout list in the existing overview document — form-quality wiring on `farmer_application`, submission-backbone wiring for `farmer_application`, DSL eligibility integration, snapshot-at-decision tool — is superseded under all three verdicts, because each item on it competes with the trio in some way. The framework's commitment is that the audit gates the next move, and no version of the next move continues investing in plugins and wiring that retire.

## 8. The discipline this framing requires

The framework only works if the team works it. Three operational habits make the difference.

### 8.1 Classification as a meeting habit

Every backlog grooming, every design review, every code review of non-trivial change asks the question: does this serve the trio, is it neutral, does it compete? The answer is recorded — one sentence, in the ticket, in the design doc, in the code review thread. The recording matters more than the answer. A team that records its classifications builds, over time, a corpus of decisions that future contributors can read; a team that classifies in their head builds nothing.

### 8.2 Refusal as a stance

When a decision is classified as competing with the trio, the response is to refuse the work in its proposed form, not to do it anyway with caveats. The refusal is constructive: it surfaces the alternative — extend the meta-model with evidence, push the concern out of scope, defer until the converged equivalent reaches parity — and makes the alternative concrete enough to act on. A refusal that does not propose an alternative is unhelpful; a refusal that compromises by half-doing the competing work is worse than doing it cleanly. The framework's posture is that "no, and here is what to do instead" is the only acceptable form.

### 8.3 Decisions logged, not implicit

The convergence will involve dozens of small decisions over months. The audit produces some. The migration plan produces more. Day-to-day classification produces a steady stream. Each one is recorded — a one-paragraph entry in a decision log, in the style of the SAD's §10 or the existing Lesotho overview's §7. The log is read at every milestone. A decision that has not been written down is, for practical purposes, not a decision; it is a working assumption that the next contributor will quietly reverse.

The discipline is not heavy. The cost of a one-paragraph log entry is minutes; the cost of a quietly reversed decision is months. The framework's posture is that the team treats the log as load-bearing infrastructure and not as ceremonial paperwork.

## 9. Identified RegBB specification gaps

Implementing RegBB against a real-world ministry workload surfaces concepts the spec does not formalise. We record these here so they are legible as upstream feedback to GovStack rather than as private divergence. Each gap is paired with how the Lesotho instance fills it and the corresponding decision-log entry. When two or more concrete RegBB implementations agree on a fill, it is candidate language for the next revision of the spec.

### 9.1 Master Data / Catalog as a first-class entity

**Where the spec is silent.** §7.2 enumerates twelve first-class entities — Catalog is not among them. The word "catalog" / "catalogue" appears three times in §6 (line 546, line 577, line 580, line 638), as a field type and a one-line property note ("Catalog reusable source. Catalogs are reusable across all services in the same instance."). The full spec returns zero hits on `master data`, `lookup`, `list of values`, `reference data`. The closest the spec comes to acknowledging managed lookup lists is one passing sentence; it does not specify their entity shape, scope, lifecycle, source, or relationship to fields.

**Why this matters in practice.** Any real implementation has dozens of MD entities. The Lesotho farmers system has approximately ninety: districts, villages, crops, livestock types, equipment categories, equipment items, marital statuses, orphanhood statuses, disability flags, hazard types, agro-ecological zones, and so on. The silence forces every implementation to invent its own MD model privately, and those private inventions are very unlikely to align across implementations — so a spec-conforming portal in country A and a spec-conforming portal in country B cannot exchange catalog content through any shared schema.

**Lesotho fill (per D22).** `mm_catalog` is a peer of the twelve RegBB entities, with `code`, `name`, `scope ∈ {instance, service}`, `source ∈ {static, registry}`, `itemsJson` (for static), `imCapabilityRef` (for registry-sourced via IM capability). Catalog is referenced from `mm_field.optionsCatalogId` exactly as a field-level lookup binding. Treated explicitly as Master Data — the spec's "catalog" mention is interpreted to be the same concept.

**Candidate spec language.** Add §7.2.13 Catalog (Master Data) with the shape above, marked REQUIRED.

### 9.2 Cascading / hierarchical lookup between fields

**Where the spec is silent.** Once Master Data exists in any form, real screens always need cascading dependencies — pick a district, then villages refilter; pick an equipment category, then equipment items refilter. RegBB has no concept for this. Determinants control field visibility and required-ness but not field options. mm_catalog is a flat list. There is no field-to-field option-filter expression in §6.3.2.2 or §7.2.8.

**Why this matters in practice.** Without a cascading mechanism, fields backed by catalogs of any non-trivial size are unusable for citizens. A flat 85-item dropdown of equipment items is not a viable form control. Implementations work around by building screen-specific subcatalogs (multiplies catalog count by category count — does not scale) or by hand-rolling JavaScript per screen (ad-hoc, not config-over-code).

**Lesotho fill (per D21).** Two optional columns on `mm_field`: `optionsFilterField` (storageKey of another field on the same screen whose value filters this one) and `optionsFilterColumn` (the column name in catalog items to match against). The synthesiser wires `controlField + extraCondition` on the resulting Joget SelectBox, exposing native cascading via standard form mechanics. Catalog items remain flat; filtering happens at the field-binding layer.

**Candidate spec language.** Add to §7.2.8 Field/claim/data: optional `optionsFilter` property describing field-driven filtering of the field's catalog, with subfields `field` (the source field's id) and `column` (the discriminator column in catalog items).

### 9.3 Rule grammar canonicity (recorded already in ADR-001)

The spec leaves rule expression open between a closed-set operator vocabulary (§7.2.12 "rules: object math rules") and a free-form DSL. This was the first identified gap and is resolved by ADR-001 revision 2: DSL canonical, closed twenty-operator subset preserved as the engine's fast-path. Restated here for completeness so all spec gaps live in one section.

### 9.4 Wizard / multi-screen sequence as a first-class element

**Where the spec is silent.** §6.3.2.1 says an analyst "can define a one-screen e-service or create a multi-page wizard e-service supported by Breadcrumb." The concept of wizard navigation is mentioned but not modelled. `mm_screen.orderIndex` carries an implicit sequence; nothing in §7 binds a Joget form (or its rendering surface) to that sequence with the navigation, validation-per-tab, and persisted-progress semantics a real wizard needs.

**Why this matters in practice.** Real citizen-facing applications in any non-trivial implementation will be six-to-fifteen-screen flows. Stacking that many sections on a single page is poor UX (long scroll, no progress indicator, no per-tab validation gate). Without a spec-level wizard primitive, every implementation invents its own — Joget's `MultiPagedForm` requires N+1 tables (one per tab), other portals roll bespoke React state machines. None portable.

**Lesotho fill (per D24).** New element `MetaWizardElement` (Phase 1 Week 3 deliverable). Single widget; configured with a service code (or explicit screen-code list). Renders all matching `mm_screen` rows in `orderIndex` order as tabs with breadcrumb navigation, per-tab validation, and a single underlying data row in one table — preserving the simple data model from today's stacked-sections form. Operator parity: `readonly=Y` mode renders citizen tabs read-only and the operator-decision tab editable, gated by the appropriate `mm_role_screen` row.

**Candidate spec language.** Add to §6.3.2.1 a wizard-binding entity (or extend `mm_service` / `mm_registration` with a `flowKind ∈ {single_page, wizard}` property) so a spec-conforming runtime knows to render the screen sequence as a wizard rather than a stacked page. Specify breadcrumb semantics, the validation gate at each tab boundary, and the requirement that data persists per-tab (so an applicant can resume across sessions).

### 9.5 What this list authorises

These gaps are intentionally narrow: each is a missing piece of the spec without which an implementation cannot be both spec-conforming and usable. None of them ask RegBB to expand its scope (none of them are "add a CRM" or "add a payments engine" — RegBB explicitly defers payments to the Payment BB and identity to the Identity BB, those are not gaps). The convergence target remains the published RegBB; this section names where we have to extend to close usability holes the spec does not address, and proposes minimal additions to the spec itself.

When the Lesotho project reaches Phase 1 acceptance, this section is the basis of a structured feedback memo to the GovStack RegBB working group. The memo is part of the convergence work, not separate from it.

## 10. Closing

The convergence is a posture as much as a project. The framework does not tell the team what to build; it tells the team how to recognise whether what they are about to build belongs in the converged architecture, the transitional bridge, or the retired past. The trio from vision paper §3 is the test. The three settled questions from §3 of this document are the commitments. The two drift modes from §7 of the vision paper are the failure modes. The component classification in §5 of this document is the present state. The audit and its verdicts are what gate the next move.

A team that holds this framework consistently over the months of the convergence ends with a Lesotho system that is a working instance of the Registration Building Block as the vision paper describes it. A team that holds it intermittently ends with a system that is partially converged in irregular ways, requiring more discipline to maintain than to have done correctly the first time. The framework exists so that the team's choices, individually small and made under pressure, accumulate into the architecture rather than away from it.

---

*End of document. Companion artefacts: meta-model completeness audit (next), migration plan (after the audit's verdict), per-plugin retirement schedule (downstream of the migration plan).*
