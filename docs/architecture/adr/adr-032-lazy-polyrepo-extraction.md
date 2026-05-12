# ADR-032: Lazy polyrepo extraction, triggered by concrete reuse demand

**Status:** Accepted
**Date:** 2026-05-12
**Supersedes:** the "Tier 1 + Tier 2 polyrepo split, post-UAT" implied roadmap in earlier session notes and the original `production_readiness_roadmap.md`.

## Context

The Farmers Portal codebase contains ~29 OSGi plugins under `plugins/`, which logically fall into three tiers:

- **Tier 1 — generic platform plugins** (joget-status-framework, form-creator-api, joget-gis-server, joget-gis-ui, joget-concat-field, joget-smart-search, embedded-datalist, joget-advanced-filters, joget-lookup-field, app-def-provider). These are domain-agnostic and reusable across any Joget project.
- **Tier 2 — RegBB-implementation plugins** (the 6-plugin RegBB suite, the 4-plugin DSL rule engine, the 3-plugin submission backbone). These together IMPLEMENT the GovStack Registration Building Block on Joget.
- **Tier 3 — Lesotho-specific plugins** (parcel-zone-centring, farmer-derived-plugin). Only meaningful inside Farmers Portal.

An earlier plan (drafted during the GovStack alignment review, May 2026) proposed extracting Tier 1 and Tier 2 plugins into 12-13 separate GitHub repos in a single "Pass B" project, post-UAT, with the rationale of:
- GovStack publication (cite-able per-plugin or per-suite repos)
- Cross-project reuse (other Joget implementations forking generic plugins)
- Handover (Lesotho IT inheriting a clean polyrepo structure)

## Decision

**Reject the mass-extraction plan. Adopt lazy polyrepo extraction instead: a plugin is extracted to its own repo only when a concrete second consumer needs to depend on it.**

The trigger is *demand*, not *speculation*. When a real second project (the GAM banking workflow being the first concrete trigger, May 2026) needs to consume a plugin as a Maven dependency, we extract that plugin at that time. Until then, the plugin lives in `farmers-portal/plugins/<plugin-name>/` with a citable GitHub URL of the form `github.com/aarelaponin/farmers-portal/tree/main/plugins/<plugin-name>` — which is already sufficient for both GovStack publication referencing and casual code review.

The extraction procedure for each lazy trigger is:

1. Polish the plugin's `README.md` + `LICENSE` + `.gitignore` for standalone presentation, committed to farmers-portal.
2. Create the new GitHub repo (`gh repo create aarelaponin/<plugin-name> --public ...`).
3. Copy the plugin folder to a sibling working location (no git history is preserved from farmers-portal — the new repo starts fresh at `v0.1`).
4. `git init` + initial commit + push.
5. Consumer projects clone the new repo + `mvn install` for local consumption. GitHub Packages publishing is set up later only if/when multiple consumer machines need it.
6. **The original copy in `farmers-portal/plugins/` stays** until Pass C (a future, also-deferred consolidation pass) switches farmers-portal to consume the published Maven artifact and removes the in-tree source.

## Consequences

### Positive

- **No speculative mass-extraction project.** The ~3-5 days of focused effort that the original plan would have required is saved until it pays for itself.
- **Per-extraction granularity.** Each new repo gets care: a thoughtful README, an integration note for the specific consumer project, license + topics + description. Mass-extraction would have produced 13 thin shells with placeholder docs.
- **GovStack publication is unaffected.** Farmers Portal's public repo already provides citable URLs for every plugin under `tree/main/plugins/<name>`. The Tier 2A "RegBB on Joget" framing has its own `docs/architecture/govstack-alignment-may2026.md` report; reviewers can read that plus the plugin sources in one place.
- **Lower cognitive load.** Twelve new repos to maintain in lockstep with farmers-portal would have created sync discipline overhead at exactly the moment we're trying to keep the architecture stable for UAT.

### Negative

- **Temporary duplication during the transition period for any lazily-extracted plugin.** Between the extraction and the eventual Pass C consolidation, the plugin's source exists in two repos: the standalone one (canonical for new consumers) and `farmers-portal/plugins/` (for farmers-portal's own build). Manual sync discipline is required for any fix that lands during this window.
- **Future Pass C work remains.** Switching farmers-portal to consume Maven artifacts (and removing the in-tree plugin sources) is still owed; the work is just deferred, not eliminated.
- **The earlier roadmap docs are now misleading.** `production_readiness_roadmap.md`, `solo_implementation_plan.md`, and the README each contain language like "Tier 1 polyrepo split is Pass B work, post-UAT". Those references are now anachronistic and need a touch-up to point at this ADR instead.

### Trade-offs explicitly accepted

- **The "single source of truth" principle is violated during the duplication window.** Acknowledged. The discipline is: bug fixes flow first into the canonical standalone repo, then are copied into farmers-portal's in-tree copy. Pass C removes the duplicate when it's the lowest-cost moment.
- **Cross-jurisdiction reuse is gated on demand, not enabled in advance.** Another government wanting to adopt `joget-status-framework` would have to wait for someone to do the extraction. Acceptable: extraction is a half-day exercise per plugin once triggered, and prematurely doing 13 of them on a guess is more expensive than doing 1-2 on demand.

## Alternatives considered

**Option A — Mass extraction in Pass B, post-UAT (the rejected original plan).**
- Pro: All plugins are standalone-shaped immediately. Clean polyrepo from a known date.
- Con: 3-5 days of focused work for plugins that mostly have no second consumer. 12-13 repos to maintain. README + LICENSE polish for plugins no one is consuming. Optimism about speculative future use cases.

**Option B — Leave everything in farmers-portal indefinitely (the do-nothing option).**
- Pro: Single source of truth. No sync discipline overhead.
- Con: Real reuse demand (GAM) blocks on missing standalone artifact. Forces ad-hoc subdirectory-copying instead of proper dependency management.

**Option C — Mass extraction now (worst option).**
- Pro: Same as Option A, immediate.
- Con: Same as Option A, but more disruptive at a worse time (pre-UAT).

**Option D — Lazy extraction (this ADR).**
- Pro: Effort matches actual demand. Each new repo is properly cared for. Farmers Portal's public repo already provides citable URLs.
- Con: Temporary duplication for any extracted plugin until Pass C. Future Pass C work is owed but deferred.

Option D dominates A and B on cost; C is dominated by A on timing.

## Principles invoked

This decision invokes two principles in tension:

- **Convention over Invention** (one of the project's recurring framing principles). The convention for "generic Joget plugins" in the Joget Marketplace ecosystem is per-plugin polyrepo. Sticking to that convention IS the right thing — eventually. The "eventually" is the bit ADR-032 disciplines.
- **YAGNI (You Aren't Gonna Need It)**. Speculative mass extraction of 13 plugins, most of which currently have only one consumer (farmers-portal), is the canonical anti-pattern YAGNI exists to refuse.

The Convention-over-Invention pull is real but conditional on demand; YAGNI is unconditional in the absence of demand. YAGNI wins here, with Convention-over-Invention taking over the moment each demand surfaces.

## Related

- ADR-002 r2 (bundle topology) — the earlier "single bundle for reg-bb-engine" decision used the same Convention-over-Invention vs YAGNI framing at smaller scale.
- `x_archive/NOTE-for-GAM-adopting-joget-status-framework.md` — the first concrete lazy-extraction trigger, captured as a cross-project handover note.
- The standalone repo: `github.com/aarelaponin/joget-status-framework` — first lazy extraction, May 2026.

## Trigger log

Track each lazy extraction here as it happens.

| Date | Plugin | Triggered by | Standalone repo |
|---|---|---|---|
| 2026-05-12 | `joget-status-framework` | GAM (banking workflow) consumption | `github.com/aarelaponin/joget-status-framework` |
| _future_ | _next plugin to land_ | _name the consumer_ | _link_ |

Pass C (the consolidation pass that switches farmers-portal to Maven-artifact consumption + removes the in-tree duplicates) is **not currently scheduled**. Revisit when (a) ≥3 plugins have been lazily extracted, OR (b) farmers-portal handover to Lesotho IT is imminent and the team wants the cleaner shape.
