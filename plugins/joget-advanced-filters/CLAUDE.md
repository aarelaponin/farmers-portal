# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Joget OSGi plugin bundle (Java 11, Maven) that delivers advanced DataList filter plugins for Joget Workflow Platform v8.1.0. These filters address specific limitations of Joget's built-in filter types that make them unsuitable for complex reporting dashboards.

The `docs/` folder contains reference material for the RPT-EXEC-001 report (the first consumer of these filters), including the DataList JSON definition and JDBC binder SQL.

## Why Not Standard Joget Filters?

### DateRangeFilterType vs built-in DateDataListFilterType

Joget's built-in date filter provides a **single date picker bound to one parameter**. A date range (From/To) on the same column requires two filter entries, each with its own label and cell, resulting in a clumsy UI. `DateRangeFilterType` renders **two date pickers in a single filter cell** with independent parameter suffixes, both targeting the same column via hash variables. It also optionally wraps the entire filter bar in a styled collapsible panel (replacing the need for a separate decorator plugin).

### MdmSelectFilterType vs built-in SelectBoxDataListFilterType

Joget's built-in select filter requires **options to be hardcoded in the DataList JSON**. When MDM lookup tables change (new districts, centres, zones are added), someone must manually update the DataList configuration. `MdmSelectFilterType` **queries MDM tables at render time** (`app_fd_{tableName}`), so dropdowns always reflect current reference data. It also supports **multi-select with semicolon-joined values** designed for PostgreSQL `string_to_array()` filtering — the built-in select filter submits only a single value.

### FilterPanelDecorator (embedded in DateRangeFilterType)

Joget's default filter bar renders filters in a **flat, unstyled horizontal row** with no visual grouping, labels, or collapsibility. For dashboards with 6+ filters, this creates a cluttered UI. The panel decorator transforms it into a **collapsible panel with CSS grid layout**, type badges, active-filter count, and styled Apply/Reset buttons. This is now built into `DateRangeFilterType` (configured via its "Filter Panel Styling" properties) rather than a separate plugin, because Joget's DataList Builder requires every filter to be bound to a SQL result column — there is no way to add a decoration-only filter without a dummy column hack.

## Build

```bash
mvn clean package
```

The output is an OSGi bundle JAR in `target/`. No tests or linter configured.

The `wflow-core:8.1-SNAPSHOT` dependency is `provided` scope (supplied by Joget at runtime). The `javax.servlet-api:3.1.0` is also `provided` — excluded from wflow-core's transitive deps and re-added explicitly. Follow the same pattern as sibling plugins in `plugins/` (e.g., `joget-gis-ui`).

## Architecture

All plugins extend `DataListFilterTypeDefault` and are registered via the standard OSGi `Activator.java`.

### Plugin Inventory

| Plugin | Class | Purpose |
|---|---|---|
| `DateRangeFilterType` | `org.joget.lst.DateRangeFilterType` | Two date pickers (From/To) in one filter cell + optional filter panel styling |
| `MdmSelectFilterType` | `org.joget.lst.MdmSelectFilterType` | Multi-select that loads options from any MDM lookup table |
| `CascadingMdmSelectFilterType` | `org.joget.lst.CascadingMdmSelectFilterType` | Parent-child cascading multi-selects with visual grouping and auto-filtering |
| `FilterPanelDecorator` | `org.joget.lst.FilterPanelDecorator` | Standalone panel decorator (legacy — prefer DateRangeFilterType's built-in panel) |

### Common Patterns

All filter plugins follow the same conventions:

- **Raw HTML rendering** — No FreeMarker templates (avoids OSGi classloader issues). HTML is built via `StringBuilder` in `getTemplate()`.
- **Hash-variable filtering** — `getQueryObject()` returns `null`. Filtering is handled by hash variables in the JDBC SQL (e.g., `#requestParam.d-5814095-fn_district_name?sql#`).
- **Encoded parameter names** — Use `datalist.getDataListEncodedParamName()` to generate request parameter names that Joget can resolve as hash variables. The DataList ID is hashed by Joget's ParamEncoder (e.g., `farmersOverview` → `5814095`).
- **Hidden input sync** — Visible controls (selects, date pickers) sync their values to hidden `<input>` fields with encoded names via JavaScript.
- **HTML/JS escaping** — `escHtml()` and `escJs()` utility methods for safe output.

### DateRangeFilterType

Generates two encoded parameter names from configurable suffixes (`fromParamSuffix`, `toParamSuffix`). Each maps to a separate hash variable in the SQL. Template renders two `<input type="date">` side by side with hidden inputs that sync on change and form submit. When `panelTitle` property is set (default: "FILTER CRITERIA"), also injects the collapsible panel CSS/JS that wraps the entire filter form.

### MdmSelectFilterType

Queries MDM lookup tables (`app_fd_*`) to populate a `<select multiple>` dropdown. Selected values are joined with semicolons and submitted as a single parameter for use with PostgreSQL `string_to_array()` in the SQL. Detects macOS at render time and shows "⌘+click" hint instead of "Ctrl+click". Includes a **clear button** (×) to reset individual filter selections.

### CascadingMdmSelectFilterType

Extends MdmSelectFilterType with parent-child hierarchy support. Key features:
- **Parent code attribute** on child options (`data-parent-code`) for client-side filtering
- **Filter registry** (`window.roFilterRegistry`) for cross-filter communication
- **Bi-directional sync**: parent selection filters child options; child selection auto-selects parent
- **Visual grouping**: filters with matching `filterGroupName` are grouped in a styled wrapper with header and hierarchy arrows
- **Clear button** (×) that also triggers cascade update to reset child filters

### FilterPanelDecorator

Legacy standalone decorator. Injects CSS/JS to wrap the filter form in a collapsible panel with grid layout, type badges, and styled Apply/Reset buttons. Requires binding to a SQL result column in the DataList Builder, which is awkward. Prefer using DateRangeFilterType's built-in panel instead.

## Plugin Properties

Each plugin's configurable properties are defined in `src/main/resources/properties/`:
- `dateRangeFilterType.json` — from/to parameter suffixes, default date values, panel title/columns/hierarchy hint
- `mdmSelectFilterType.json` — table name, code/name columns, size, default value
- `cascadingMdmSelectFilterType.json` — same as mdmSelectFilterType plus: parentCodeColumn, parentFilterName, filterGroupName/Order/Hint
- `filterPanelDecorator.json` — panel title, grid columns, hierarchy hint

## Reference: RPT-EXEC-001

`docs/RPT-EXEC-001/RPT-EXEC-001-spec.md` contains the full technical specification for the Registry Overview Dashboard including filter options data, column definitions, and Joget hash variable patterns.

`docs/RPT-EXEC-001/r02-farmers.json` contains the DataList JSON definition including JDBC SQL with CTE joining 5 tables plus TOTAL summary row.

`docs/RPT-EXEC-001/binder-sql.sql` contains the standalone JDBC binder SQL (full version with sub-centres and villages).

`docs/RPT-EXEC-001/binder-sql-simple.sql` contains a simplified SQL version without sub-centres and villages columns.
