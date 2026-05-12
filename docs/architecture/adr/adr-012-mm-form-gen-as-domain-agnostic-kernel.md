# ADR-012 — MM-form-gen as domain-agnostic kernel

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | Kernel SAD; framework SAD; IM-module SAD; solution-level SAD §4.1. |

## Context

The `MetaScreenElement` + `MetaWizardElement` + `MetaModelDao` + `mm_screen` / `mm_field` / `mm_catalog` set was originally implemented inside the `reg-bb-engine` OSGi bundle and conceived as part of RegBB. As the architecture evolved, two observations made the layering explicit:

1. The form-rendering primitives have **zero dependency** on RegBB-specific concepts. They don't know about services, eligibility, applications, or the citizen-services pattern. They render forms from configuration; the configuration's *meaning* is interpreted elsewhere.
2. The IM module **needs metadata-driven form rendering** but does NOT need RegBB framework concerns (eligibility, applicability, single-window catalogue, role-scoped review). IM is operational logistics, not citizen services.

Without an explicit boundary, IM would either drag the whole framework along or duplicate the form-rendering work. Neither is acceptable.

## Decision

The form-rendering primitives are a **domain-agnostic kernel**, conceptually a separate layer below the RegBB framework. The kernel:

- Owns: `MetaScreenElement`, `MetaWizardElement`, `MetaModelDao` (kernel-relevant methods only), `mm_screen`, `mm_field`, `mm_catalog`.
- Imports nothing from RegBB framework.
- Is consumable by any module that wants metadata-driven form rendering — RegBB framework, IM, future modules.

The kernel today **physically** lives inside the same `reg-bb-engine` OSGi bundle as the framework. The split is logical, not physical. A future split into a separate bundle (`mm-form-gen`) is anticipated but not urgent — it becomes useful when (a) IM module ships and would benefit from importing only the kernel, or (b) the framework needs versioning independent from the kernel.

## Consequences

**Positive:**

- The architecture's layering is explicit: kernel → framework → modules. Any module can opt in to metadata-driven rendering without taking on RegBB-specific concerns.
- Refactoring the kernel later (e.g. evolving the widget pass-through to a registry per ADR-015) doesn't risk RegBB regression.
- The kernel SAD documents what's kernel and what's not; reviewers can hold us to the boundary.

**Negative:**

- The same OSGi bundle contains both layers today, so the boundary depends on discipline rather than enforcement. A careless PR can import `engine.api.*` from inside `engine.element.*` without anything stopping it.
- A separate bundle eventually requires `Export-Package` declarations on `engine.element.*` and `engine.dao.*`, plus an `Import-Package` on the framework side. Cheap when needed, unnecessary today.

**Trade-off named:** SRP (separate kernel for separate domain) won against YAGNI (don't split until forced). The principle that pulled the other way was operational simplicity (one bundle to deploy, one build to ship). At single-team scale, the discipline-only boundary is cheap; the split happens when scale or version-skew makes it worthwhile. ADR-002r2 explicitly anticipated this: keep the single-bundle layout for now, document the layering, plan to split when the IM module lands.

**Documents updated:** Solution-level SAD §4.1; kernel SAD §1.1, §5.1, §7; framework SAD §1.1; IM-module SAD §1.1.
