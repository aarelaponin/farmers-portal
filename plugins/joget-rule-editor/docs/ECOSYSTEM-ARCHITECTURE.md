# Farmers Registry System - Development Ecosystem Architecture

**Version:** 3.0 (7-Project Architecture)
**Date:** 2025-12-30
**Status:** APPROVED

---

## Overview

The Farmers Registry System (FRS) development ecosystem consists of **7 specialized projects**, each with clearly defined responsibilities. This document establishes the authoritative architecture for how these projects interact.

**Recent Changes (v3.0):**
- Split `joget-rule-editor` into two plugins for separation of concerns
- Added `joget-rules-api` - Generic Rules Management API plugin
- `joget-rule-editor` is now UI-only (CodeMirror editor component)

---

## Project Inventory

| # | Project | Type | Location | Primary Responsibility |
|---|---------|------|----------|------------------------|
| 1 | **joget-form-generator** | Python | `PycharmProjects/dev/joget-form-generator` | Form specification & generation |
| 2 | **joget-deployment-toolkit** | Python | `PycharmProjects/dev/joget-deployment-toolkit` | Deployment, data integration, field dictionary |
| 3 | **joget-instance-manager** | Python | `PycharmProjects/dev/joget-instance-manager` | Instance lifecycle, infrastructure |
| 4 | **form-creator-api** | Java/Joget | `IdeaProjects/plugins/form-creator-api` | REST API for form creation |
| 5 | **joget-rule-editor** | Java/Joget | `IdeaProjects/plugins/joget-rule-editor` | UI plugin (CodeMirror editor element) |
| 6 | **joget-rules-api** | Java/Joget | `IdeaProjects/plugins/joget-rules-api` | Rules API (parser, compiler, field dictionary) |
| 7 | **rules-grammar** | Java | `IdeaProjects/plugins/rules-grammar` | Rule language grammar, parser (ANTLR4) |

---

## Project Responsibilities

### 1. joget-form-generator (Python)

**Purpose:** Single source of truth for Joget form specifications and master data definitions.

**Responsibilities:**
- ✅ Define form structures in YAML specifications
- ✅ Store master data CSV files (single source of truth)
- ✅ Generate Joget form JSON from YAML specs
- ✅ Validate form specifications
- ✅ Produce deployment-ready artifacts

**Does NOT do:**
- ❌ Deploy to Joget instances
- ❌ Connect to databases
- ❌ Business logic or runtime operations

**Directory Structure:**
```
joget-form-generator/
├── specs/
│   ├── jre/                          # JRE component specs
│   │   ├── input/                    # YAML form definitions (SOURCE)
│   │   │   ├── jre-field-scope.yaml
│   │   │   ├── jre-field-definition.yaml
│   │   │   └── jre-ruleset.yaml
│   │   ├── data/                     # Master data CSVs (SOURCE OF TRUTH)
│   │   │   ├── jreFieldScope.csv
│   │   │   └── jreFieldDefinition.csv
│   │   └── output/                   # Generated form JSONs
│   │       ├── jreFieldScope.json
│   │       ├── jreFieldDefinition.json
│   │       └── jreRuleset.json
│   ├── spm/                          # SPM component specs
│   ├── farmer/                       # Farmer registry specs
│   └── archive/                      # Archived/deprecated specs
├── src/                              # Generator source code
└── README.md
```

---

### 2. joget-deployment-toolkit (Python)

**Purpose:** Integration layer between development artifacts and Joget instances. Handles deployment, data synchronization, and cross-system data transformations.

**Responsibilities:**
- ✅ Deploy forms to Joget instances (via form-creator-api)
- ✅ Deploy/synchronize master data to Joget
- ✅ Read from Joget instances (export, backup)
- ✅ Generate field dictionary from farmer form metadata
- ✅ Data transformation and integration tasks
- ✅ Read instance configuration from `~/.joget/instances.yaml`

**Does NOT do:**
- ❌ Define or modify form specifications (uses output from joget-form-generator)
- ❌ Own master data definitions (copies from joget-form-generator)
- ❌ Grammar or parsing logic
- ❌ Manage instance infrastructure (that's joget-instance-manager)

**Directory Structure:**
```
joget-deployment-toolkit/
├── components/
│   ├── jre/                          # JRE deployment artifacts
│   │   ├── README.md
│   │   ├── forms/                    # COPIED from joget-form-generator output
│   │   ├── data/                     # COPIED from joget-form-generator data
│   │   ├── scripts/                  # Deployment & integration scripts
│   │   │   ├── deploy_jre_forms.py
│   │   │   ├── populate_field_dictionary.py
│   │   │   └── generate_field_dictionary.py
│   │   └── archive/erel/             # Old EREL reference
│   ├── spm/
│   └── farmer/
├── src/                              # Toolkit source code
├── config/                           # Local configurations
└── README.md
```

---

### 3. joget-instance-manager (Python)

**Purpose:** Low-level Joget instance lifecycle management and infrastructure configuration.

**Responsibilities:**
- ✅ Tomcat port configuration
- ✅ Datasource file management
- ✅ Glowroot APM configuration
- ✅ MySQL database creation, user management, schema import
- ✅ Instance lifecycle: setup, reset, verification, health checks
- ✅ Multi-instance coordination
- ✅ **Manages `~/.joget/instances.yaml`** - Single source of truth for instance configuration
- ✅ Bootstrap formCreator plugin for new instances

**Does NOT do:**
- ❌ Deploy application forms or data (that's joget-deployment-toolkit)
- ❌ Business logic
- ❌ Form generation

**Instance Architecture:**
```
Instance   Port    Purpose
────────   ────    ───────
jdx1       8081    Production
jdx2       8082    Staging
jdx3       8083    Development
jdx4       8084    Client Alpha (SPM PoC target)
jdx5       8085    Client Beta
jdx6       8086    Sandbox
```

**Directory Structure:**
```
joget-instance-manager/
├── joget_instance_manager.py         # Primary CLI tool
├── bootstrap_joget_instance.py       # Bootstrap formCreator
├── health_check.py                   # Health monitoring
├── config/
│   └── defaults.yaml                 # Default configurations
└── README.md
```

**Key Configuration File:**
```yaml
# ~/.joget/instances.yaml
instances:
  jdx4:
    name: "Client Alpha"
    port: 8084
    tomcat_home: "/opt/joget/jdx4"
    database:
      host: localhost
      port: 3306
      name: jwdb_jdx4
      user: jwdb_jdx4_user
    api:
      api_id: "API-xxx"
      api_key: "xxx"
```

---

### 4. form-creator-api (Java/Joget Plugin)

**Purpose:** REST API for programmatic form creation in Joget, enabling automated deployments.

**Responsibilities:**
- ✅ REST API endpoint for form creation
- ✅ Create forms programmatically
- ✅ Create API endpoints for forms
- ✅ Create CRUD interfaces (datalist + userview)
- ✅ Solve bootstrap paradox (formCreator form needed for API deployments)

**Does NOT do:**
- ❌ Deploy data to forms
- ❌ Instance management
- ❌ Form specification

**Package:** `global.govstack.formcreator`

**Key API:**
```
POST /jw/api/formcreator/formcreator/forms
{
  "formId": "myForm",
  "formName": "My Form",
  "tableName": "myTable",
  "formDefinition": "{...json...}",
  "createApiEndpoint": true,
  "createCrud": false
}
```

**Directory Structure:**
```
form-creator-api/
├── src/main/java/global/govstack/formcreator/
│   ├── Activator.java                # OSGi activator
│   ├── api/
│   │   └── FormCreatorApi.java       # REST endpoint
│   └── service/
│       ├── FormCreationService.java  # Main orchestrator
│       ├── FormDatabaseService.java  # Form registration
│       ├── ApiBuilderService.java    # API endpoint creation
│       └── CrudService.java          # CRUD generation
├── src/main/resources/
│   └── properties/                   # Plugin configurations
├── pom.xml
└── README.md
```

---

### 5. joget-rule-editor (Java/Joget Plugin) - UI Only

**Purpose:** Joget plugin providing the Rule Editor UI element. Pure frontend component with configurable API endpoint.

**Responsibilities:**
- ✅ Joget Form Element (RuleEditorElement)
- ✅ Static resource serving (RuleEditorResources)
- ✅ UI components (CodeMirror editor, field dictionary panel)
- ✅ Configurable API endpoint property
- ✅ JavaScript editor with syntax highlighting

**Does NOT do:**
- ❌ REST API endpoints (moved to joget-rules-api)
- ❌ Parser/Compiler logic (moved to joget-rules-api)
- ❌ Database access (moved to joget-rules-api)
- ❌ Grammar definition (in rules-grammar)

**Dependencies:**
- Calls `joget-rules-api` REST endpoints at runtime

**Package:** `global.govstack.ruleeditor`

**JAR Size:** ~88KB

**Directory Structure:**
```
joget-rule-editor/
├── src/main/java/global/govstack/ruleeditor/
│   ├── Activator.java                # OSGi activator
│   └── element/                      # Joget form elements
│       ├── RuleEditorElement.java    # Form element
│       └── RuleEditorResources.java  # Static file serving
├── src/main/resources/
│   ├── static/                       # JS/CSS assets
│   │   ├── jre-editor.js
│   │   ├── jre-editor.css
│   │   ├── jre-mode.js
│   │   └── codemirror.min.*
│   ├── templates/                    # FTL templates
│   │   └── RuleEditorElement.ftl
│   └── properties/                   # Plugin configurations
│       └── RuleEditorElement.json
├── docs/                             # Documentation
├── pom.xml
└── README.md
```

---

### 6. joget-rules-api (Java/Joget Plugin) - NEW

**Purpose:** Generic Rules Management API. Provides REST endpoints for validation, compilation, field dictionary, and ruleset management.

**Responsibilities:**
- ✅ REST API endpoints (RulesApiProvider)
- ✅ Parser/Validator (uses rules-grammar library)
- ✅ SQL Compiler (AST → SQL WHERE clauses)
- ✅ Field dictionary service (loads from configurable Joget form)
- ✅ Ruleset CRUD service (saves to configurable Joget form)

**Does NOT do:**
- ❌ UI components (that's joget-rule-editor)
- ❌ Grammar definition (that's rules-grammar)
- ❌ Form generation

**Dependencies:**
- `rules-grammar` - For parsing rule scripts

**Package:** `global.govstack.rulesapi`

**JAR Size:** ~496KB (includes rules-grammar and ANTLR runtime)

**API Endpoints:**
```
Base: /jw/api/jre/jre/

GET  /jre/fields?scopeCode=X       - Field dictionary
POST /jre/fields/refresh           - Clear field cache
POST /jre/validate                 - Validate script
POST /jre/compile                  - Compile to SQL
POST /jre/saveRuleset              - Save ruleset
GET  /jre/loadRuleset?rulesetCode=X - Load ruleset
POST /jre/publishRuleset           - Publish ruleset
```

**Directory Structure:**
```
joget-rules-api/
├── src/main/java/global/govstack/rulesapi/
│   ├── Activator.java                # OSGi activator
│   ├── lib/                          # REST API
│   │   └── RulesApiProvider.java
│   ├── service/                      # Business services
│   │   ├── FieldRegistryService.java
│   │   └── RulesetService.java
│   ├── parser/                       # Parser facade
│   │   ├── RuleScriptParser.java
│   │   └── RuleScriptValidator.java
│   ├── compiler/                     # SQL generation
│   │   ├── RuleScriptCompiler.java
│   │   ├── CompiledRuleset.java
│   │   └── FieldMapping.java
│   ├── adapter/                      # Bridge to rules-grammar
│   │   └── ...
│   └── model/                        # Domain models
│       └── ...
├── src/main/resources/
│   └── properties/
│       └── RulesApiProvider.json
├── pom.xml
└── README.md
```

---

### 7. rules-grammar (Java Library) ✅ COMPLETE

**Status:** ✅ IMPLEMENTED - Uses ANTLR4 for grammar definition, comprehensive test coverage

**Purpose:** Standalone library defining the Rules Script language grammar, parser, and AST. Independent of Joget and business logic.

**Technology:** ANTLR4 4.13.1 (generates lexer/parser from .g4 grammar files)

**Package:** `global.govstack.rules.grammar`

**Responsibilities:**
- ✅ Define Rules Script grammar (ANTLR4 .g4 files)
- ✅ Lexer and parser implementation (generated by ANTLR4)
- ✅ Abstract Syntax Tree (AST) model
- ✅ AST Builder from parse tree
- ✅ Parse result wrapper with error handling
- ✅ Language documentation

**Does NOT do:**
- ❌ Joget-specific code
- ❌ UI components
- ❌ Database access
- ❌ SQL compilation (that's in joget-rule-editor)
- ❌ Business-specific rules

**Directory Structure:**
```
rules-grammar/
├── src/main/
│   ├── antlr4/global/govstack/rules/grammar/
│   │   ├── RulesScriptLexer.g4       # Lexer grammar
│   │   └── RulesScriptParser.g4      # Parser grammar
│   └── java/global/govstack/rules/grammar/
│       ├── RulesScript.java          # Main API entry point
│       ├── RulesScriptAstBuilder.java # AST construction
│       ├── ParseResult.java          # Parse result wrapper
│       ├── ParseError.java           # Error representation
│       └── model/                    # AST model classes
│           ├── Script.java           # Root AST node
│           ├── Rule.java             # Rule definition
│           ├── Condition.java        # Sealed interface with variants
│           ├── Value.java            # Sealed interface with variants
│           ├── FieldRef.java         # Field reference
│           ├── RuleType.java         # INCLUSION/EXCLUSION/PRIORITY/BONUS
│           └── ComparisonOperator.java
├── src/test/java/                    # Comprehensive test suite
├── target/generated-sources/antlr4/  # Generated lexer/parser
├── pom.xml
├── README.md
├── DEVELOPER.md
└── CHANGELOG.md
```

**Key API:**
```java
// Main parsing API
ParseResult<Script> result = RulesScript.parse(scriptText);

if (result.isSuccess()) {
    Script script = result.getValue();
    // Process AST
} else {
    List<ParseError> errors = result.getErrors();
    // Handle errors
}
```

**AST Model:**
- `Condition` - Sealed interface with variants: SimpleComparison, Between, In, NotIn, IsEmpty, And, Or, Not, GridCheck, Aggregation
- `Value` - Sealed interface with variants: StringValue, NumberValue, BooleanValue, IdentifierValue

---

## Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DATA FLOW                                       │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────┐
                    │   joget-form-generator  │
                    │        (Python)         │
                    │                         │
                    │  specs/jre/input/*.yaml │ ◄── Developer edits
                    │  specs/jre/data/*.csv   │ ◄── Master data (SOURCE OF TRUTH)
                    │           │             │
                    │           ▼             │
                    │  specs/jre/output/*.json│ ◄── Generated forms
                    └───────────┬─────────────┘
                                │
                                │ sync/copy
                                ▼
                    ┌─────────────────────────┐
                    │ joget-deployment-toolkit│
                    │        (Python)         │
                    │                         │
                    │  components/jre/forms/  │ ◄── Copied JSONs
                    │  components/jre/data/   │ ◄── Copied CSVs
                    │           │             │
                    │  scripts/generate_      │
                    │  field_dictionary.py    │ ◄── Reads farmer metadata
                    │           │             │     Writes to specs/jre/data/
                    └───────────┬─────────────┘
                                │
                                │ reads config from
                                ▼
                    ┌─────────────────────────┐
                    │  joget-instance-manager │
                    │        (Python)         │
                    │                         │
                    │  ~/.joget/instances.yaml│ ◄── Instance configuration
                    │                         │
                    │  setup, reset, verify   │
                    │  bootstrap formCreator  │
                    └───────────┬─────────────┘
                                │
                                │ manages
                                ▼
                    ┌─────────────────────────┐
                    │    Joget Instance       │
                    │        (jdx4)           │
                    │                         │
                    │  Plugins:               │
                    │    form-creator-api ────┼──► REST API for deployment
                    │    joget-rule-editor ───┼──► UI Form Element
                    │    joget-rules-api ─────┼──► Rules REST API
                    │                         │
                    │  Database Tables:       │
                    │    app_fd_jreFieldScope │
                    │    app_fd_jreFieldDef.. │
                    │    app_fd_jreRuleset    │
                    └───────────┬─────────────┘
                                │
                                │ runtime
                                ▼
┌───────────────────┐     ┌─────────────────────────┐     ┌─────────────────────────┐
│   rules-grammar   │     │   joget-rules-api       │     │   joget-rule-editor     │
│  (Java Library)   │     │   (Joget Plugin)        │     │   (Joget Plugin)        │
│     ✅ COMPLETE   │     │                         │     │                         │
│                   │     │  RulesApiProvider       │     │  RuleEditorElement      │
│  Parser           │◄────│  FieldRegistryService   │◄────│  RuleEditorResources    │
│  AST Models       │     │  RulesetService         │     │  (UI only, calls API)   │
│  ParseResult API  │     │  RuleScriptCompiler     │     │                         │
│                   │     │  (496KB JAR)            │     │  (88KB JAR)             │
└───────────────────┘     └─────────────────────────┘     └─────────────────────────┘
        ▲                           │                               │
        │      Maven dependency     │         HTTP REST API         │
        └───────────────────────────┘◄──────────────────────────────┘
```

---

## Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DEPENDENCY GRAPH                                   │
└─────────────────────────────────────────────────────────────────────────────┘

    PYTHON PROJECTS                          JAVA PROJECTS
    ───────────────                          ─────────────

    ┌──────────────────┐                    ┌──────────────────┐
    │joget-form-       │                    │  rules-grammar   │ ✅ COMPLETE
    │  generator       │                    │  (Java Library)  │
    │                  │                    │  No dependencies │
    │ SOURCE OF TRUTH  │                    └────────┬─────────┘
    │ for specs/data   │                             │
    └────────┬─────────┘                             │ Maven dep
             │                                       ▼
             │ file artifacts              ┌──────────────────┐
             ▼                             │ joget-rules-api  │ NEW
    ┌──────────────────┐                   │  (Joget Plugin)  │
    │joget-deployment- │                   │                  │
    │    toolkit       │                   │ Uses: rules-     │
    │                  │                   │       grammar    │
    │ Deploys to Joget │                   │       Joget DX8  │
    └────────┬─────────┘                   └────────┬─────────┘
             │                                      │
             │ reads config                         │ HTTP API
             │ uses REST API                        ▼
             ▼                             ┌──────────────────┐
    ┌──────────────────┐                   │joget-rule-editor │
    │joget-instance-   │                   │  (Joget Plugin)  │
    │    manager       │                   │                  │
    │                  │                   │ UI Only, calls   │
    │ INFRASTRUCTURE   │                   │ joget-rules-api  │
    │                  │                   └──────────────────┘
    │ ~/.joget/        │
    │ instances.yaml   │                   ┌──────────────────┐
    └────────┬─────────┘                   │ form-creator-api │
             │                             │  (Joget Plugin)  │
             │◄────────────────────────────│                  │
             │   bootstraps                │ REST API for     │
             │                             │ form deployment  │
                                           └──────────────────┘
```

---

## Key Principles

### 1. Single Source of Truth

| What | Where | Project |
|------|-------|---------|
| Form specs (YAML) | `specs/*/input/` | joget-form-generator |
| Master data (CSV) | `specs/*/data/` | joget-form-generator |
| Grammar definition | `.g4` files | rules-grammar |
| Instance configuration | `~/.joget/instances.yaml` | joget-instance-manager |

### 2. Separation of Concerns

- **Specification** (joget-form-generator) is separate from **Deployment** (joget-deployment-toolkit)
- **Infrastructure** (joget-instance-manager) is separate from **Application** (deployment-toolkit)
- **Grammar** (rules-grammar) is separate from **Plugin** (joget-rule-editor)
- **Form Creation API** (form-creator-api) is separate from **Instance Management** (instance-manager)
- **No circular dependencies**

### 3. Configuration Over Coding

- Forms defined in YAML, not coded
- Field dictionary generated from metadata, not hardcoded
- Rules written in DSL, not Java
- Instance configuration in YAML, not scripts

### 4. Artifact Flow Direction

```
specs/ → generated/ → deployed/
```

Artifacts flow one direction: from source (specs) through generation to deployment.

### 5. Infrastructure Independence

- `joget-instance-manager` handles low-level infrastructure
- `joget-deployment-toolkit` handles application-level deployment
- They communicate via `~/.joget/instances.yaml`

---

## Cross-Project Communication

### Python Projects

| From | To | Method |
|------|-----|--------|
| joget-form-generator | joget-deployment-toolkit | File artifacts (JSON, CSV) |
| joget-deployment-toolkit | joget-instance-manager | Reads `~/.joget/instances.yaml` |
| joget-deployment-toolkit | form-creator-api | HTTP REST API |

### Java Projects

| From | To | Method |
|------|-----|--------|
| joget-rules-api | rules-grammar | Maven dependency |
| joget-rule-editor | joget-rules-api | HTTP REST API |
| joget-deployment-toolkit | form-creator-api | HTTP REST API |

---

## File Synchronization

### From joget-form-generator to joget-deployment-toolkit

**Script:** `sync_artifacts.py` (in deployment toolkit)

```bash
# Sync JRE component artifacts
python -m src.sync_artifacts \
    --source ~/PycharmProjects/dev/joget-form-generator/specs/jre/output \
    --dest ~/PycharmProjects/dev/joget-deployment-toolkit/components/jre/forms

python -m src.sync_artifacts \
    --source ~/PycharmProjects/dev/joget-form-generator/specs/jre/data \
    --dest ~/PycharmProjects/dev/joget-deployment-toolkit/components/jre/data
```

**Rule:** Never edit files in `components/*/forms/` or `components/*/data/` directly. Always edit in `specs/` and sync.

---

## Current Status

### rules-grammar Project

**Status:** ✅ **COMPLETE** with comprehensive test coverage

**Integration:** joget-rule-editor needs to add Maven dependency and use `RulesScript.parse()` API

### Field Dictionary

**Current:** Sample data in jreFieldScope.csv  
**Target:** ~80 fields generated from farmer form metadata

**Generation:** Script in joget-deployment-toolkit writes to joget-form-generator/specs/jre/data/

### Infrastructure

**Available:** 
- joget-instance-manager ready for instance setup/reset
- form-creator-api ready for programmatic deployments
- `~/.joget/instances.yaml` configuration established

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-27 | Initial 4-project architecture |
| 2.0 | 2025-01-28 | Expanded to 6-project architecture with instance-manager and form-creator-api |
| 3.0 | 2025-12-30 | Split joget-rule-editor into UI (joget-rule-editor) and API (joget-rules-api) - now 7 projects |
