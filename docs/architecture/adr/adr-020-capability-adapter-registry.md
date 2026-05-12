# ADR-020 — Capability adapter registry per RegBB §3.2

| | |
|---|---|
| Status | Proposed (implemented in build-055) |
| Date | 2026-05-03 |
| Decider | Aare Laponin |
| Related | ADR-001r2 (rule grammar canon); ADR-008 (cache layering); registry-integration component SAD; subsidy-to-IM backlog L2-1; D7 (households.vulnerability future capability). |

## Context

`SqlPathEvaluator` resolves `$registry.<entity>.<field>` references — the rule grammar's mechanism for reading data from outside the application row (typically the farmer registry, eventually parcels, household composition, IM allocation rosters, etc.). The Slice 1B-b implementation hand-coded this with a switch:

```java
private static String entityToTable(String entity) {
    switch (entity.toLowerCase()) {
        case "farmer": return "farmerbasicinfo";
        case "parcel": return "parcelregistration";
        default: throw new IllegalArgumentException("unknown_registry_entity:" + entity);
    }
}
```

Plus a single shape of query: `SELECT c_<field> FROM app_fd_<table> WHERE c_national_id = ? LIMIT 1`. This worked for one capability ("farmer") but didn't scale to:

- **1:N aggregates.** A farmer has many parcels; rules need `cultivated_total` or `parcel_count`, not a single column.
- **Registries with non-NID join keys** (future).
- **Module-authored capabilities.** IM module needs `$registry.allocation.is_member_of_roster`, but adding it shouldn't require touching the engine bundle.
- **Households vulnerability per D7.** That capability aggregates household_members rows by farmer; not a simple column lookup.

RegBB §3.2 calls for a *capability registry* pattern — adapters registered by name, the engine looks them up at evaluation time. This ADR codifies that pattern at Phase 1 scope.

## Decision

A `CapabilityAdapter` interface in `engine.api` (exported package; future modules import it). Concrete adapters live in `engine.registry/`. `SqlPathEvaluator` keeps a `Map<String, CapabilityAdapter>` keyed by capability name; resolution dispatches to the adapter whose name matches the reference's entity segment.

```java
public interface CapabilityAdapter {
    String getCapabilityName();
    String resolve(String field, String nationalId, EvalContext ctx) throws Exception;
}
```

Phase 1 ships two adapters:

- **`FarmerByNidAdapter`** (capability=`farmer`) — preserves the slice 1B-b behaviour. White-listed allowed fields (national_id, first_name, last_name, full_name, date_of_birth, gender, contact_phone, email, district, village). One SELECT per evaluation.
- **`ParcelsSummaryAdapter`** (capability=`parcels_summary`) — virtual fields `parcel_count`, `cultivated_total`, `largest_parcel`. One aggregating SELECT per evaluation.

Adapters are looked up case-insensitively. Unknown capability → `EvalResult.error("unknown_registry_capability:<name>", "sql")`.

Backward compatibility: existing rules using `$registry.farmer.<field>` keep working because `farmer` is a registered capability. Rules previously using `$registry.parcel.<field>` (the slice 1B-b name) now require updating to a registered capability — there's no rule today that uses this since the parcel.* rules were migrated to bare-identifier `$applicant.*` form per task #76.

## Consequences

**Positive:**

- **Adding a new capability is one new class plus one line in `defaultRegistry()`.** No SqlPathEvaluator change. No grammar change. The framework's pluggability is real.
- **1:N aggregates are first-class.** ParcelsSummaryAdapter shows the pattern; future adapters (households.vulnerability, farmers.subsidies.history) follow it.
- **Per-capability validation lives with the capability.** FarmerByNidAdapter has the white-listed fields list inline; ParcelsSummaryAdapter has its `parcel_count`/`cultivated_total`/`largest_parcel` field check. The evaluator stays generic.
- **Spec §3.2 conformance.** The earlier hand-coded mapping was a Lesotho-specific workaround that didn't match the spec's pluggable-adapter intent. This is the spec-conformant shape.
- **DET_SMALLHOLDER and similar parcel-touching rules can now be authored honestly:** `$registry.parcels_summary.cultivated_total <= 5` evaluates against the real parcel registry. The current stubbed rule (`area_hectares <= 5` against the application row) is replaceable.

**Negative:**

- **Adapters live in the engine bundle today.** A future IM-module bundle that wants to register its own adapters (e.g. `allocation.is_member_of_roster`) needs either: (a) a hand-off API on SqlPathEvaluator that the IM bundle calls at startup, or (b) OSGi service discovery where the engine scans `CapabilityAdapter` services at evaluation time. Phase 1 deliberately doesn't take either path — the in-bundle list is enough until IM ships.
- **Capability names use snake_case to match identifier syntax.** RegBB §3.2 prose-describes capabilities as `farmers.byNid`, `parcels.summary` — dotted. Our grammar's regex captures two segments only, so we use `parcels_summary` as one segment. The semantic shape is the same; the dot/underscore difference is documentation-level only.
- **Per-evaluation cost is one DB roundtrip per capability reference.** Same as before; the L2 cache (ADR-008) absorbs repeats. A multi-reference rule (`$registry.farmer.gender == 'F' AND $registry.parcels_summary.cultivated_total > 0`) issues two queries per evaluation. Acceptable today; if profiling shows it's a bottleneck, batch-resolve in one query (future optimisation).
- **Validation surface grows per adapter.** Each adapter is responsible for its own field white-listing and parameter binding. That's the right place for it (the adapter knows what's safe), but it does mean six adapters means six places to maintain validation discipline. Mitigated by interface-level guidance + the `engine.registry/` directory being small enough to scan as a whole.

**Trade-off named:** Convention over Invention vs. Open-Closed Principle.

Convention over Invention argued for keeping the existing switch — it works for two capabilities. Open-Closed argued for the registry — modules can add adapters without touching SqlPathEvaluator. We chose the registry because (a) RegBB §3.2 specifies this shape, so we'd have to converge eventually anyway; (b) D7 (`households.vulnerability`) is a confirmed future capability that doesn't fit the single-column-by-NID shape; (c) Phase 3 IM module brings more capabilities; (d) the cost of the interface is small (~30 LoC per adapter is the floor anyway). Doing it now while the switch had two cases is cheaper than doing it later when it has six.

The principle Convention over Invention pulled the other way; rejected because RegBB-spec conformance is a non-trade-off goal here.

**Documents updated:** `_design/architecture/components/farmer-parcel-registry-integration.md` §5.2 (registry of capabilities table); registry-integration SAD §11 (R-R1 mitigation). Code: `engine.api.CapabilityAdapter`, `engine.registry.FarmerByNidAdapter`, `engine.registry.ParcelsSummaryAdapter`, refactored `SqlPathEvaluator`.
