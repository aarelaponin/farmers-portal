# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
mvn clean package
```

No tests exist in this project. The output is an OSGi bundle JAR (`packaging: bundle`) deployed to Joget's `wflow/app_plugins/` directory.

## Architecture

A Joget DX 8.1 form element plugin that watches a source SelectBox field and auto-populates with a value from a related form record. Java 17, base package: `global.govstack.lookupfield`.

### Key Files

- **Activator.java** — OSGi bundle activator. Registers `LookupFieldResources`, `LookupFieldElement`, and `LookupFieldWebService` as services. New plugin classes must be registered here.
- **LookupFieldElement.java** — Main form element (`extends Element implements FormBuilderPaletteElement`). Renders the FTL template with a config JSON object in `renderTemplate()`. Server-side fallback in `formatData()` uses `FormDataDao` to resolve values when JS didn't run.
- **LookupFieldElement.ftl** — FreeMarker template with embedded JS. Client-side AJAX calls `LookupFieldWebService` endpoint (session-authenticated). Shares a global cache (`window._lookupFieldCache`) across instances. Assumes jQuery (`$`) from Joget runtime.
- **LookupFieldWebService.java** — AJAX data endpoint (`extends ExtDefaultPlugin implements PluginWebSupport`). Accepts `action=lookup` with formId/keyCol/keyVal params. Uses `FormDataDao` server-side to query records, returns JSON. Accessed at `/jw/web/json/plugin/global.govstack.lookupfield.element.LookupFieldWebService/service`.
- **LookupFieldResources.java** — Static file server for CSS (`extends ExtDefaultPlugin implements PluginWebSupport`). Serves files from classpath `/static/` with directory traversal protection.
- **LookupFieldElement.json** — Plugin property definition (form builder UI configuration).

### Patterns

- CSS is duplicated: inline in the FTL `<style>` block and in `static/lookup-field.css`. Keep both in sync.
- Plugin properties are defined in `src/main/resources/properties/` as JSON and loaded via `AppUtil.readPluginResource()`.
- OSGi imports are explicitly listed in `pom.xml` under `maven-bundle-plugin` → `Import-Package`. New Joget package imports must be added there.

## Configuration Properties

| Property | Required | Description |
|---|---|---|
| `id` | Yes | Field ID (stored in DB) |
| `label` | No | Display label |
| `displayType` | Yes | `hidden` / `readonly` / `editable` |
| `sourceFieldId` | Yes | ID of the SelectBox to watch |
| `lookupFormId` | Yes | Form definition ID to fetch from |
| `lookupColumn` | Yes | Column name to extract from the fetched record |
| `lookupKeyColumn` | No | Column in lookup form matching SelectBox value (leave empty for PK lookup) |
| `lookupTableName` | No | Override DB table name (defaults to `lookupFormId`) |
| `updateOn` | No | `change` (default) or `blur` |

## Dependencies

All provided scope (by Joget runtime): `wflow-core` 8.1-SNAPSHOT, `javax.servlet-api` 3.1.0, `org.json` 20230227.
