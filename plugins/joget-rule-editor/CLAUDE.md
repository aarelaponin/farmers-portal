# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Joget Rule Editor is a **UI-only** OSGi plugin for Joget DX 8.1 that provides a CodeMirror-based code editor for "Rules Script" - a DSL for defining eligibility rules in social protection programs.

**Important:** This plugin is UI-only. All API, parsing, compilation, and data access are in the separate `joget-rules-api` plugin.

## Build Commands

```bash
# Download CodeMirror (required before first build)
./download-codemirror.sh

# Build the plugin
mvn clean package

# Output JAR (88KB)
target/joget-rule-editor-8.1-SNAPSHOT.jar
```

## Architecture (7 Projects)

```
┌─────────────────────────────────────────────────────────────┐
│  joget-rule-editor (THIS PROJECT - UI ONLY)                 │
│  • RuleEditorElement - Joget form element                   │
│  • RuleEditorResources - Static file serving                │
│  • CodeMirror editor with jre-mode.js highlighting          │
│  • Configurable API endpoint (default: /jw/api/jre/jre)     │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP REST API calls
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  joget-rules-api (SEPARATE PLUGIN)                          │
│  • RulesApiProvider - REST endpoints                        │
│  • FieldRegistryService - Field dictionary from DB          │
│  • RulesetService - Ruleset CRUD                            │
│  • RuleScriptParser - Facade to ANTLR parser                │
│  • RuleScriptCompiler - AST → SQL WHERE clauses             │
└──────────────────────────┬──────────────────────────────────┘
                           │ Maven dependency
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  rules-grammar (SEPARATE LIBRARY)                           │
│  • ANTLR4 grammar for Rules Script DSL                      │
│  • Parser, Lexer, AST models                                │
└─────────────────────────────────────────────────────────────┘
```

**Key Components in this project:**
- `Activator` - OSGi lifecycle, registers plugins
- `RuleEditorElement` - Joget form element with configurable properties
- `RuleEditorResources` - Serves static files (JS, CSS)

**Related Projects:**
- `joget-rules-api` - REST API, parser, compiler (sibling project in gs-plugins)
- `rules-grammar` - ANTLR grammar and parser (sibling project in gs-plugins)

## Configurable Properties

The `RuleEditorElement` form element has these configurable properties:

### Basic Settings
| Property | Description | Default |
|----------|-------------|---------|
| `apiEndpoint` | Base URL for Rules API | `/jw/api/jre/jre` |
| `scopeCode` | Field scope for autocomplete | `FARMER_ELIGIBILITY` |
| `height` | Editor height (e.g., "400px" or "auto") | `auto` |
| `showDictionary` | Show field dictionary panel | `true` |
| `showSaveButton` | Show save button | `false` |

### Field Dictionary Filters
These filters restrict which fields appear in the dictionary panel (form-level configuration):

| Property | Description | Default |
|----------|-------------|---------|
| `filterCategories` | Comma-separated category codes (e.g., "DEMOGRAPHIC,ECONOMIC") | (all) |
| `filterFieldTypes` | Comma-separated field types (e.g., "NUMBER,TEXT,LOOKUP") | (all) |
| `filterIsGrid` | Filter by grid fields: "Y" (grid only), "N" (non-grid), "" (all) | (all) |
| `filterLookupFormId` | Only show fields with this lookup form ID | (all) |

### Runtime Filters
The dictionary panel also includes runtime filter dropdowns that allow users to further filter fields:
- **Category dropdown** - Populated from `/categories` API (md51FieldCategory MDM)
- **Type dropdown** - Populated from loaded field types
- **Grid dropdown** - Filter by grid/non-grid fields
- **Search box** - Text search across field IDs and labels

## Rules Script DSL

```
RULE "Adult Farmer"
  TYPE: INCLUSION
  CATEGORY: demographic
  MANDATORY: true
  ORDER: 1
  WHEN age >= 18 AND occupation = "farmer"
  PASS_MESSAGE: "Eligible as adult farmer"
  FAIL_MESSAGE: "Must be 18+ and a farmer"
```

Rule types: `INCLUSION`, `EXCLUSION`, `BONUS`, `PRIORITY`

## REST API Endpoints (in joget-rules-api)

Base: `/jw/api/jre/jre`

### Field Dictionary
```
GET /fields?scopeCode=X[&categories=A,B][&fieldTypes=C,D][&isGrid=Y|N][&lookupFormId=Z]
```
Returns field definitions for autocomplete, optionally filtered.

```
GET /categories
```
Returns available field categories from md51FieldCategory MDM:
```json
{"categories": [{"code": "DEMOGRAPHIC", "name": "Demographic"}, ...], "count": 8}
```

### Validation & Compilation
- `POST /validate` - Validate script syntax
- `POST /compile` - Compile to SQL WHERE clauses

### Ruleset Management
- `POST /saveRuleset` - Persist ruleset
- `GET /loadRuleset?rulesetCode=X` - Load by code
- `POST /publishRuleset` - Publish ruleset

### Cache
- `POST /fields/refresh` - Clear field cache

## File Locations

- Form Element: `src/main/java/global/govstack/ruleeditor/element/`
- Editor JS: `src/main/resources/static/jre-editor.js`, `jre-mode.js`
- Templates: `src/main/resources/templates/RuleEditorElement.ftl`
- Properties: `src/main/resources/properties/RuleEditorElement.json`

## Cache Busting

Static resources use version parameter for cache busting:
- Update `jreCacheVersion` in `RuleEditorElement.ftl` when changing JS/CSS
- Server sets `Cache-Control: no-cache` headers

## Coding Standards

- Google Java Style Guide
- 4 spaces indentation
- 120 char line limit
- Conventional commits: `feat(scope): description`

## Deployment

Both plugins must be deployed:
1. `joget-rules-api-8.1-SNAPSHOT.jar` (496KB) - API plugin
2. `joget-rule-editor-8.1-SNAPSHOT.jar` (88KB) - UI plugin

Create API Builder app "jre" to expose the API endpoints.
