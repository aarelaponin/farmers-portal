# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Joget DX8 form element plugin** that embeds a read-only datalist inside a form with filtering capabilities. It solves the limitation where Joget's ListGrid + JDBC Datalist Binder don't work together.

## Build Commands

```bash
# Build the plugin JAR
mvn clean package

# Output: target/embedded-datalist-8.1-SNAPSHOT.jar
```

## Architecture

This is an OSGi bundle plugin for Joget DX8:

- **Activator.java** - OSGi bundle activator that registers `EmbeddedDatalist` as a service
- **EmbeddedDatalist.java** - Main form element class extending Joget's `Element` and implementing `FormBuilderPaletteElement`
- **embeddedDatalist.ftl** - FreeMarker template with HTML, CSS, and JavaScript for rendering the datalist via AJAX
- **embeddedDatalist.json** - Plugin property definitions (configuration UI in Form Builder)
- **EmbeddedDatalist.properties** - i18n message bundle

### Key Integration Points

The plugin uses Joget's JSON API endpoint for data retrieval:
```
/jw/web/json/data/list/{appId}/{listId}?paramName=value
```

Filter parameters are passed as URL query parameters, built from form field values mapped in the configuration.

### JDBC Datalist Binder Filtering

For JDBC Datalist Binder, use `#requestParam.paramName#` hash variable in SQL with a conditional WHERE:

```sql
WHERE ('#requestParam.farm_id#' = '' OR column = '#requestParam.farm_id#')
```

This handles both design-time (empty param → show all) and runtime (with param → filter).

**Do NOT use** `{filter_name}` syntax as it requires defining filters in Datalist UI.

See `docs/f01.08.json` and `docs/f01.08-list.json` for working examples.

## Plugin Configuration Structure

The plugin properties (`embeddedDatalist.json`) define:
1. **Basic Settings** - Element ID, label, datalist selection
2. **Filter Parameters** - Grid mapping paramName → fieldId → defaultValue
3. **Display Options** - Height, pagination, page size, empty message
4. **Advanced** - Refresh triggers, custom CSS

## Requirements

- Joget DX8 Enterprise Edition
- Java 17+
- Maven for building
