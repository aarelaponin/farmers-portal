# ADR-015 — Widget pass-through dispatch — switch statement, not registry

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | Kernel SAD §5.2 (synthesiseField switch table); ADR-012 (kernel as domain-agnostic). |

## Context

`MetaScreenElement.synthesiseField` translates a `mm_field` row into a Joget Element instance — `TextField` for `widget=text`, `SelectBox` for `widget=select`, etc. The current implementation is a switch statement on `mm_field.widget`, hardcoded inside the kernel. Adding a new widget kind (e.g. `repeating_group`, `gis_polygon`, `signature`) requires editing `synthesiseField`, recompiling, and redeploying the kernel JAR.

Two natural designs:

- **Hardcoded switch** (current). Fast to read, easy to follow, fast to extend by kernel maintainers. Requires kernel-team involvement for any new widget. Can't be extended by modules.
- **Widget registry.** Modules register widget factories at OSGi service-startup time (`registerWidgetFactory("gis_polygon", new GisPolygonFactory())`). Pluggable; modules add widgets without touching kernel. Adds complexity (registry data structure, factory interface, lookup at synthesis time, error handling on unregistered widgets).

For Phase 1, the four 2025 subsidy programmes use only the seven simple widgets the kernel already supports. The widgets we know we'll need next (repeating_group, gis_polygon, signature, smart_search, cascading_select) are all installed Joget enterprise plugins; the synthesis is one-line-per-widget pass-through. Eight more switch cases is a small change.

## Decision

Keep the hardcoded switch in `synthesiseField`. Add new widget kinds by extending the switch.

The switch table covers seven widgets today (`text`, `number`, `date`, `textarea`, `select`, `radio`, `checkbox`). Phase 1 close-out will add five more (`gis_polygon`, `signature`, `smart_search`, `repeating_group`, `cascading_select`, `file_upload` for form-kind screens). Each is a new case in the switch that pass-through-instantiates the relevant Joget element class.

A widget registry (OSGi service for factory registration) is **deferred** until evidence emerges that it's needed: either a module wants to add its own custom widget without going through the kernel team, or the switch grows beyond ~20 cases and becomes hard to read. Neither is true today.

## Consequences

**Positive:**

- The kernel stays simple. A new contributor reads `synthesiseField` and sees the whole widget map at a glance.
- No registry data structure to maintain, no factory interface to evolve, no lookup error-handling on unregistered widgets.
- Adding a widget is a code change in one place (`synthesiseField`'s switch) plus a possible test.

**Negative:**

- Modules cannot add their own widgets without modifying the kernel. If the IM module wants a custom QR-scan widget, that widget either lives in the kernel (out of place), or IM ships its own non-pass-through path (parallel to the kernel — undesirable).
- Adding a widget requires a kernel rebuild + JAR redeploy. Faster than nothing, slower than registry-based plug-and-play.
- The switch will grow. At ~20 cases it becomes harder to read; that's the trigger for revisiting this decision.

**Trade-off named:** Convention over Invention won against OCP (open for extension, closed for modification). The registry pattern is more "open for extension" but adds invented complexity that current scale doesn't earn. At single-team scale + ~12 widgets total, the switch is the cheaper structure. Revisit when scale or modularity needs change.

**Documents updated:** Kernel SAD §5.2 (synthesiseField table); kernel SAD §11 (K-R3 risk acknowledged that this is a deliberate simplicity choice).
