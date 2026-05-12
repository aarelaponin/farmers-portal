# ADR-029 — Widget configuration overrides via `mm_field.widgetConfig`

| | |
|---|---|
| Status | Proposed (implemented in build-062) |
| Date | 2026-05-03 |
| Decider | Aare Laponin |
| Related | ADR-012 (mm-form-gen as domain-agnostic kernel); ADR-014 (conditional UI dual-path); ADR-015 (widget pass-through dispatch); subsidy-to-IM backlog L1-3, L1-4, L1-5. |

## Context

Three widget pass-throughs landed in May 2026 — `signature` (L1-3), `file_upload` (L1-4), and `gis_polygon` (L1-5). Each instantiates a Joget element from inside `MetaScreenElement.synthesiseField` and configures it with hardcoded defaults baked into kernel source:

```java
case "gis_polygon":
    elementClass = "global.govstack.gisui.element.GisPolygonCaptureElement";
    props.put("defaultLatitude",  "-29.6");
    props.put("defaultLongitude", "28.2");
    props.put("defaultZoom",      "13");
    props.put("mapHeight",        "400");
    // ...etc
```

That's "configuration as code" — the analyst can't change defaults without a kernel rebuild. The principle violation is consistent across all three widgets:

* `signature` — width, height, encryption, readonly hardcoded.
* `file_upload` — fileType, maxSize, multiple hardcoded.
* `gis_polygon` — 30+ properties hardcoded; the GIS plugin's Form Builder surface exposes 12 configuration groups, our hardcoded slice covers ~15% of them.

The user identified this during L1-5 verification: "we are using it with arbitrary defaults — I am not sure whether it influenced something, but I am sure it is not a best practice."

Per RegBB §6 and the spec's principle P1 (configuration over code), screen widgets should be analyst-authorable. Per ADR-012 (kernel is domain-agnostic), kernel source must not encode policy choices.

## Decision

Add a single `widgetConfig` TEXT column to `mm_field` carrying analyst-authored JSON overrides. The kernel parses this JSON at synthesise time, filters keys through a per-widget allowlist, and overlays the surviving keys onto the per-case default `props` map before instantiating the Element.

```json
{
  "defaultZoom": 15,
  "mapHeight": 500,
  "tileProvider": "ESRI_SATELLITE",
  "minVertices": 3,
  "maxVertices": 50
}
```

**Per-widget allowlists** are constants in `MetaScreenElement` — `SIGNATURE_OVERRIDABLE_KEYS`, `FILE_UPLOAD_OVERRIDABLE_KEYS`, `GIS_POLYGON_OVERRIDABLE_KEYS`. Anything outside the allowlist is dropped with a log warning.

**Locked keys** (NOT in any allowlist):

* `id`, `label`, `validator` — kernel sets these from `mm_field.storageKey`, `mm_field.label`, `mm_field.defaultBehaviorOnError`. Overriding them via widgetConfig would silently divorce the rendered field from its mm_field row.
* For `gis_polygon`: all field-binding props (`areaFieldId`, `perimeterFieldId`, `centroidFieldId`, `vertexCountFieldId`, plus the four `autoCenter*FieldId` keys). The kernel's `attachGisCompanionHiddenFields` synthesises HiddenField siblings whose ids match these references; letting analysts retarget would break the wiring contract.

**Failure behaviour:** malformed JSON → log warning + use kernel defaults (fail-open). Unknown keys → log warning + drop. Bad values fall through to whatever the underlying Joget element's defaults are. The form always renders.

**Implementation point:** `mergeWidgetConfig(props, f, widget, allowedKeys)` runs at the end of `synthesiseField`, after the per-case switch sets defaults but before `newElement(elementClass)` is called. ~30 lines in the helper, ~6 lines at the call site, ~50 lines of allowlist constants. Total ≈ 90 lines.

The per-widget property catalogues are documented in `_design/architecture/components/mm-form-gen-kernel.md` §8.7. The analyst reads that catalogue to know what keys are valid; bad keys produce log warnings (visible in audit log over time).

## Consequences

**Positive:**

* **One column, not twenty.** A single TEXT column on `mm_field` covers configuration for all current and future widgets. Adding a 4th, 5th, 10th widget never bloats the schema.
* **Configuration over code.** The kernel keeps shipping defaults that are fine for most cases. When a programme needs different settings (closer-in zoom for densely-parcelled districts, larger maxSize for lab reports, etc.), the analyst edits one mm_field row. No kernel rebuild, no redeploy.
* **Authoring affordance is uniform.** Every mm_field has the same `widgetConfig` TextArea. Analysts learn the pattern once, reuse it for every widget.
* **Forward-compatible.** Future widgets — `repeating_group` (L1-6), `bot_pull` (L2-3), Phase 3 IM-specific widgets — opt into the override mechanism by adding one allowlist constant. No further infrastructure work.
* **Locked-key contract is explicit.** The "what the analyst can't override" boundary is captured in code (the allowlist) AND prose (the kernel SAD). Future maintainers see immediately why the field-binding props are kernel-managed.
* **Bounded blast radius.** Bad widgetConfig affects exactly one rendered field — log warning, defaults applied, form still renders. Nothing else breaks.

**Negative:**

* **Free-form JSON has no schema validation at authoring time.** The analyst types JSON into a TextArea; mistakes surface only when the form is rendered (log warning + default applied). For Phase 1 simplicity this is acceptable; Phase 2 could add a richer per-widget property panel that drives the JSON behind the scenes.
* **The property catalogue lives in two places.** The allowlist constant in `MetaScreenElement` (the source of truth for "what's allowed") and the SAD §8.7 prose (the source of truth for "what each key means"). Drift risk if one is updated and not the other. Mitigated by both being small and co-located in our codebase.
* **String coercion.** All values are coerced to strings via `v.toString()` before `props.put`. Joget element setters expect strings throughout — this matches the existing per-case defaults — but it does mean booleans like `"true"`/`"false"` are stringly-typed. The few cases where Joget enterprise plugins use `"Y"`/`"N"` (per kernel SAD §8.6) require analyst discipline to use the right convention. Documented in the property catalogue.
* **Locked-key list grows with each new widget.** Every new widget that synthesises companion fields (FormGrid for repeating_group, etc.) adds more locked keys. Risk of analysts trying to override them and being silently blocked. Mitigated by warning logs every time a locked key is dropped — operators auditing the log will see the pattern.

**Trade-off named:** Configuration over Code (analyst-authoring) vs. Strong Typing (one column per property).

Strong Typing argued for: each property gets a column, analyst gets a typed Joget form to author it, mistakes are caught at authoring time, IDE-level autocomplete via the property panel. The case is real — it's the path the legacy parcel form (`02.02 - Parcel Geometry`) takes today: Form Builder shows the GIS element's full property panel because the field IS a real GIS element on a real form, not synthesised.

Strong Typing was rejected because:

* The property surface for any one widget is large (gis_polygon alone has 30+ keys). Strong-typing it means 30 columns on mm_field per widget kind, or 30+ rows on a sub-table per field. Either way schema bloat.
* The property surface is per-widget. A mm_field column for `gis_polygon.tileProvider` is meaningless for a `signature` row. Strong-typing forces nullable-everywhere or per-widget tables — both worse than JSON.
* The strong-typed alternative — one Joget form per widget kind, referenced by mm_field — drags in an authoring-UX maintenance burden disproportionate to single-team scale. Convention-over-Invention also pulled this way.
* The cost of unstrong-typed JSON is bounded: fail-open + log warning + kernel default applied. The benefit (analyst authoring without kernel rebuild, scaling to any future widget) is large.

The principle that pulled the other way (Strong Typing) loses because the cost is bounded and the benefit is large; we'd rather catch authoring mistakes via a documented property catalogue + log warnings than via column-by-column type checks that quintuple the schema.

**Documents updated:** `_forms/mm/mm-field.json` (added `widgetConfig` TextArea); `_design/architecture/components/mm-form-gen-kernel.md` §8.7 (full property catalogues + mechanism description); `CLAUDE.md` (one-line gotcha entry pointing at SAD §8.7); `_seeds/lesotho-mm-fixture.yaml` (test override on the parcel_geometry field). Code: `MetaScreenElement.mergeWidgetConfig`, three allowlist constants (`SIGNATURE_OVERRIDABLE_KEYS`, `FILE_UPLOAD_OVERRIDABLE_KEYS`, `GIS_POLYGON_OVERRIDABLE_KEYS`), call site at end of `synthesiseField`. Build: reg-bb-engine build-062.
