# Implementation Prompt

Copy the prompt below and paste it into a new Claude Code chat to start implementation.

---

## Prompt

```
I need you to implement 4 missing features for my Joget DX8 Embedded Datalist plugin.

## Project Location
/Users/aarelaponin/IdeaProjects/plugins/embedded-datalist

## Background
This is an OSGi bundle plugin that embeds a read-only datalist inside a Joget form. The design document specified 4 features that were not implemented. Please read the implementation plan for full details:

docs/IMPLEMENTATION_PLAN_MISSING_FEATURES.md

## Features to Implement

1. **showExport** - Add CSV/Excel export links in the datalist footer
2. **showFilter** - Add datalist filter controls above the table (use Joget's `fn_` parameter prefix)
3. **rowClickAction** - Add row click behavior: none / open form in popup / redirect to form
4. **rowClickFormId** - Add form selector (only visible when rowClickAction is popup or redirect)

## Files to Modify

1. `src/main/resources/properties/embeddedDatalist.json` - Add property definitions
2. `src/main/resources/messages/EmbeddedDatalist.properties` - Add i18n messages
3. `src/main/java/org/joget/marketplace/EmbeddedDatalist.java` - Read properties in renderTemplate()
4. `src/main/resources/templates/embeddedDatalist.ftl` - Add UI elements and JavaScript

## Implementation Order

1. Start with showExport (simplest)
2. Then rowClickAction + rowClickFormId together
3. Finally showFilter (most complex - needs filter metadata fetching)

## Build Command
mvn clean package

## Key Technical Details

- Export URLs: `/web/json/data/app/{appId}/{version}/datalist/{listId}?_export=csv` or `?_export=xls`
- Filter parameter format: `fn_filterName=value`
- Popup: Use `JPopup.show()` from Joget's JavaScript library
- Form selector visibility: Use `control_field` and `control_value` in JSON property definition

Please implement all 4 features following the plan document.
```
