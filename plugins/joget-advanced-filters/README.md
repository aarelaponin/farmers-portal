# Joget Advanced Filters

OSGi plugin bundle that delivers advanced DataList filter plugins for **Joget DX 8.1+**. These filters address limitations of Joget's built-in filter types that make them unsuitable for complex reporting dashboards.

![Filter panel with styled grid layout, collapsible header, type badges, and Apply/Reset buttons](docs/images/filter-panel-result.png)

## Why These Plugins?

- **Date range in one cell** -- Joget's built-in date filter provides a single date picker per filter entry. A From/To range on the same column requires two filter entries with two labels, resulting in a clumsy UI. `DateRangeFilterType` renders two date pickers in a single filter cell with independent hash-variable parameters.

- **Live MDM lookups** -- Joget's built-in select filter requires options to be hardcoded in the DataList JSON. When MDM tables change (new districts, centres, zones), someone must manually update the configuration. `MdmSelectFilterType` queries MDM tables at render time, so dropdowns always reflect current reference data. It also supports multi-select with semicolon-joined values for PostgreSQL `string_to_array()` filtering.

- **Collapsible filter panel** -- Joget's default filter bar renders filters in a flat, unstyled horizontal row. For dashboards with 6+ filters, this creates a cluttered UI. The built-in panel decorator (part of `DateRangeFilterType`) transforms the filter bar into a collapsible panel with CSS grid layout, type badges, active-filter count, and styled Apply/Reset buttons.

## Prerequisites

| Requirement | Version |
|---|---|
| Joget DX | 8.1+ |
| PostgreSQL | 10+ (for `string_to_array()` in JDBC SQL) |
| Java | 11+ |
| Maven | 3.6+ |

## Build & Install

```bash
mvn clean package
```

The output is `target/joget-advanced-filters-8.1.0.jar`.

To install: upload the JAR via **Joget Admin > Settings > Plugin Manager > Upload Plugin**.

## Plugin Summary

| Plugin | Class | Purpose | Status |
|---|---|---|---|
| Date Range Filter | `org.joget.lst.DateRangeFilterType` | Two date pickers (From/To) in one filter cell + optional collapsible filter panel | Active |
| MDM Multi-Select Filter | `org.joget.lst.MdmSelectFilterType` | Multi-select dropdown that loads options from any MDM lookup table | Active |
| Cascading MDM Filter | `org.joget.lst.CascadingMdmSelectFilterType` | Parent-child cascading multi-selects with visual grouping (e.g. District → Centre) | Active |
| Filter Panel Decorator | `org.joget.lst.FilterPanelDecorator` | Standalone collapsible panel decorator | **Legacy** -- use DateRangeFilterType's built-in panel instead |

## Features

- **Clear button** on all multi-select filters -- click × to clear individual filter selections without resetting all filters
- **Cascading filter groups** -- visually grouped filters with parent→child relationships and automatic filtering of child options based on parent selection

## Documentation

- **[Admin Guide](docs/admin-guide.md)** -- Installation, configuration, properties reference, SQL patterns, troubleshooting
- **[Developer Guide](docs/developer-guide.md)** -- Project structure, architecture patterns, plugin deep-dives, extending the bundle
- **[RPT-EXEC-001 Spec](docs/RPT-EXEC-001/RPT-EXEC-001-spec.md)** -- Reference report specification (the first consumer of these filters)

## Compatibility

| Joget Version | Plugin Version | Status |
|---|---|---|
| 8.1.x | 8.1.0 | Tested |
| 8.0.x | -- | Not tested |
| 7.x | -- | Not supported |

## AI Assistant Context

This project includes a [`CLAUDE.md`](CLAUDE.md) file with detailed AI-facing context about the codebase architecture, patterns, and conventions. It is designed for use with Claude Code and similar AI coding assistants.
