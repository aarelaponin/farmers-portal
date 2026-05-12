# OBJECTIVE
Add a feature to the existing joget_utility package for fully automated Joget metadata management that can:
1. **Auto-generate** Joget form definition JSONs for new CSV files with intelligent nested LOV detection (including subcategory source pattern)
2. **Discover** existing forms and API endpoints in Joget instance and compare with definitions
3. **Create** missing forms using the form-creator plugin
4. **Populate/Update** form data from CSV files automatically (with data augmentation for subcategory patterns)

# CONTEXT

## Business Context
- **Use Case**: Farmers Registry based on BB Registration specification (https://registration.govstack.global/)
- **Platform**: Joget DX8 Enterprise Edition (no-code/low-code platform)
- **Architecture**: Federated systems requiring common metadata across multiple Joget instances
- **Development Environment**: PyCharm IDE with Python 3.13 utility

## Infrastructure
**Three Joget Instances (local development environment):**
- Port 8080 (DB: 3306) - Farmers Portal
- Port 9999 (DB: 3307) - Ministry of Agriculture back-office
- Port 8888 (DB: 3308) - Ministry of Finance **Current Target** (masterData app exists here)

**Current Scope:** Working only with port 8888 instance

## Target Application
- **Master Data Manager App**: `masterData` (contains form-creator plugin)
- **Target App**: `subsidyApplication` (where metadata forms will be created)
- **Target Version**: `1` (Published version)

# CURRENT ASSETS

## 1. Form Creator Plugin
**Location:** `/Users/aarelaponin/IdeaProjects/gam-plugins/form-creator`

**API Interface:** `joget_utility/config/form-creator-api.yaml`

**Capabilities:**
- Creates Joget form from JSON definition
- Creates CRUD interface automatically
- Creates REST API endpoint for data operations
- Handles multipart/form-data file uploads

**API Parameters (from YAML):**
- `target_app_id` - Target application ID (subsidyApplication)
- `target_app_version` - Application version (1)
- `form_id` - Unique form identifier
- `form_name` - Display name for the form
- `table_name` - Database table name
- `api_name` - REST API endpoint name (use same as table_name with prefix 'api_')
- `create_crud` - Flag to create CRUD (use "yes")
- `create_api_endpoint` - Flag to create API endpoint (use "yes")
- `form_definition_json` - JSON file upload (multipart/form-data)

## 2. Data Sources

### A. Master Data CSVs
**Location:** `data/metadata/` 

**This is the single point of truth for master data.** Always start by adding the master data CSV file from here.

**Content:** CSV files containing master/reference data for the application

**Examples:**
- `md03district.csv` - District master data
- `md19crops.csv` - Agricultural crop classifications
- `md25equipmentCategory.csv` - Equipment category parent LOV
- `md25tillageEquipment.csv` - Tillage equipment child LOV
- `md27inputCategory.csv` - Input category parent LOV
- `md27fertilizer.csv` - Fertilizer child LOV
- etc.

**Structure:** Each CSV has headers matching form field IDs

### B. Form Definition JSONs
**Location:** `data/metadataapp/forms/`

**Purpose:** Joget form definitions corresponding to each CSV

**Characteristics:**
- One JSON per CSV file (matching filename without .csv extension)
- Contains complete Joget form structure
- **May be missing** for new metadata items (trigger auto-generation)
- Defines form fields, validators, data bindings

**CRITICAL:** Some metadata has parent-child relationships requiring **nested LOVs** (see JOGET_NESTED_LOV_GUIDE.md and metadata-overview.docx)

### C. Application Forms (Future Consumption)
**Location:** `docapp/forms/`

**Forms:**
- `farmers-registration-form.json` (7 tabs, 2 with embedded tables)
- `civil-registry-01.json` (simplified civil registry data)
- `budget-program-01.json` (subsidy program definitions)
- `application-01.json` (farmers' subsidy applications)

**Purpose:** These forms will consume the metadata via lookup fields once metadata is populated

## 3. Documentation
**Location:** `docs/`

**Files:**
- `metadata-overview.docx` - **CRITICAL**: Complete metadata catalog with relationship mappings
- `02-ucm-design.docx` - High-level use case model
- `BASIC_FORMS_GUIDE.md` - How to create Joget forms
- `JOGET_NESTED_LOV_GUIDE.md` - **CRITICAL**: Nested/Cascading LOV implementation guide

# CRITICAL: Understanding Nested LOVs in Joget

## Key Concept
When dealing with related metadata (e.g., countries → regions → districts, or equipment categories → specific equipment), Joget requires a specific structure for cascading dropdowns to work.

## The Golden Rule
**The child LOV form MUST have a SelectBox field (NOT TextField) that loads data from the parent LOV form.**

## Two Relationship Patterns in Your Metadata

### Pattern 1: Traditional Foreign Key (Standard Pattern)
CSV explicitly contains the foreign key column.

**Example:**
```
countries.csv:
  code | name
  -----+-------------
  US   | United States
  UK   | United Kingdom

regions.csv:
  region_code | country_code | region_name
  -----------+-------------+-------------
  CA          | US          | California
  TX          | US          | Texas
  LON         | UK          | London
```

**regions.csv HAS a `country_code` column** that stores the parent reference.

### Pattern 2: Subcategory Source (YOUR SPECIFIC PATTERN) ⭐
CSV does NOT contain the foreign key column. Parent metadata indicates which child CSVs belong to it.

**Example from your metadata:**
```
md25equipmentCategory.csv (Parent):
  code    | name            | description | ...
  --------+-----------------+-------------+----
  TILLAGE | Tillage Equip   | ...         | ...
  PLANTING| Planting Equip  | ...         | ...

Note: This CSV may have a column indicating child form:
  - Could be in metadata-overview.docx mapping
  - Could be implicit (naming convention)
  
md25tillageEquipment.csv (Child):
  code           | name                | power_source | ...
  ---------------+---------------------+--------------+----
  PLOUGH_ANIMAL  | Animal-Drawn Plough | animal       | ...
  PLOUGH_TRACTOR | Tractor Plough      | tractor      | ...
  
⚠️ CRITICAL: This CSV has NO equipment_category_code column!
BUT all records implicitly belong to TILLAGE category.
```

**From metadata-overview.docx:**
- MD25A: Equipment Categories (Parent) → cascades to 9 child forms
- MD27A: Input Categories (Parent) → cascades to 5 child forms
  - SEEDS → md19crops
  - FERTILIZER → md27fertilizer
  - PESTICIDES → md27pesticide
  - LIVESTOCK_VET → md27livestockSupply
  - IRRIGATION → md27irrigation

## Critical Requirements for Both Patterns

Regardless of pattern, the **generated Joget form** MUST have:

```json
{
  "elements": [
    {
      "className": "org.joget.apps.form.lib.TextField",
      "properties": {"id": "code", ...}
    },
    {
      "className": "org.joget.apps.form.lib.SelectBox",  // ← MUST be SelectBox!
      "properties": {
        "id": "equipment_category_code",  // or "input_category_code"
        "optionsBinder": {
          "className": "org.joget.apps.form.lib.FormOptionsBinder",
          "properties": {
            "formDefId": "md25equipmentCategory",  // References parent form
            "idColumn": "code",
            "labelColumn": "name",
            "addEmptyOption": "true"
          }
        }
      }
    },
    // ... other fields
  ]
}
```

## Why This Matters
- **TextField for parent reference** → Cascading will NOT work
- **SelectBox for parent reference** → Cascading works perfectly
- **Pattern 2**: SelectBox field must be **INJECTED** during form generation (it's not in CSV!)
- Later, main forms can use `groupingColumn` and `controlField` to enable cascading

## Reference
See complete implementation details in:
- `JOGET_NESTED_LOV_GUIDE.md` (technical implementation)
- `metadata-overview.docx` (your specific metadata relationships)

---

# PHASE 0: Integration Design & Setup

## Objective
Analyze the existing joget_utility package structure and design how to integrate the new metadata management features without breaking existing functionality.

## Phase 0A: Existing Structure Exploration

### Task 0A.1: Discover Current Architecture
```
Please analyze the existing joget_utility package:

1. Show me the current directory structure
2. Identify existing modules and their purposes:
   - HTTP clients (if any)
   - Configuration management (if any)
   - Data handling (if any)
   - CLI commands (if any)
   - Utility functions (if any)
3. Check for existing patterns:
   - How is Joget API accessed?
   - How is configuration managed?
   - How is logging set up?
   - Are there existing form/data operations?
4. Note any potential conflicts or overlaps
```

**Expected Output:**
```
Current joget_utility Structure:
joget_utility/
├── src/
│   ├── joget/
│   │   ├── client.py ♻️ (REUSABLE - HTTP client exists)
│   │   └── api.py ♻️ (REUSABLE - basic API calls)
│   ├── config/
│   │   └── loader.py ♻️ (REUSABLE - config management)
│   ├── utils/
│   │   └── logger.py ♻️ (REUSABLE - logging setup)
│   └── cli/
│       └── commands.py 🔄 (EXTEND - add new commands)
├── config/
│   └── config.yaml ♻️ (EXTEND - add new sections)
└── ...

Findings:
✓ HTTP client available for reuse
✓ Config system in place
✓ Logging infrastructure ready
⚠️ No existing CSV/metadata handling
⚠️ No form generation capabilities
```

### Task 0A.2: Identify Integration Points
```
Based on the existing structure, identify:

1. What can be REUSED (♻️):
   - Existing HTTP clients
   - Configuration loaders
   - Logging utilities
   - Error handling patterns

2. What needs to be ADDED (🆕):
   - discovery/ package (CSV analysis)
   - generators/ package (form JSON generation)
   - validators/ package (data validation)
   - models/ package (data structures)

3. What needs to be EXTENDED (🔄):
   - Configuration (add metadata settings)
   - CLI (add metadata commands)
   - Existing API wrappers (if needed)

4. What might need REFACTORING (⚠️):
   - Any tight coupling to existing features
   - Configuration structure changes
   - API client enhancements
```

## Phase 0B: Integration Design Proposal

### Task 0B.1: Propose Enhanced Structure
```
Based on exploration, propose the integrated structure:

Proposed Integration:
joget_utility/
├── src/
│   ├── joget/
│   │   ├── client.py ♻️ (Keep existing)
│   │   ├── form_api.py 🆕 (NEW - form operations)
│   │   ├── data_api.py 🆕 (NEW - data operations)
│   │   └── plugin_api.py 🆕 (NEW - form creator plugin)
│   │
│   ├── metadata/  🆕 (NEW PACKAGE)
│   │   ├── __init__.py
│   │   ├── discovery/
│   │   │   ├── __init__.py
│   │   │   ├── csv_analyzer.py
│   │   │   ├── relationship_detector.py
│   │   │   ├── form_generator.py
│   │   │   └── metadata_parser.py
│   │   ├── generators/
│   │   │   ├── __init__.py
│   │   │   ├── simple_form.py
│   │   │   ├── parent_lov.py
│   │   │   ├── child_lov.py
│   │   │   └── field_factory.py
│   │   ├── validators/
│   │   │   ├── __init__.py
│   │   │   ├── csv_validator.py
│   │   │   └── json_validator.py
│   │   └── models/
│   │       ├── __init__.py
│   │       ├── form_definition.py
│   │       ├── relationship.py
│   │       └── csv_metadata.py
│   │
│   ├── config/
│   │   └── loader.py ♻️ (Extend for metadata settings)
│   │
│   ├── utils/
│   │   ├── logger.py ♻️ (Reuse as-is)
│   │   └── console_formatter.py 🆕 (NEW - pretty output)
│   │
│   └── cli/
│       ├── commands.py 🔄 (Extend with metadata subcommands)
│       └── metadata_commands.py 🆕 (NEW - metadata CLI)
│
├── config/
│   ├── config.yaml 🔄 (Extend with metadata section)
│   └── form-creator-api.yaml ♻️ (Already exists)
│
├── data/
│   ├── metadata/ ♻️ (CSV files)
│   ├── metadataapp/forms/ 🆕 (JSON definitions)
│   └── relationships.json 🆕 (Generated metadata)
│
└── docs/
    ├── CREATE-MDM-SPEC.md 🆕 (This specification)
    ├── metadata-overview.docx ♻️
    ├── JOGET_NESTED_LOV_GUIDE.md ♻️
    └── BASIC_FORMS_GUIDE.md ♻️

Legend:
♻️ - Reuse existing
🆕 - Add new
🔄 - Extend/modify existing
```

### Task 0B.2: Define Integration Strategy
```
1. Module Organization:
   - Group all new metadata features under src/metadata/
   - Keep existing joget/ package intact
   - Add new API wrappers in joget/ alongside existing ones

2. Configuration Extension:
   ```yaml
   # config/config.yaml (EXTENDED)
   
   joget:  # Existing section
     base_url: ...
     username: ...
     # ... existing settings
   
   metadata:  # NEW section
     csv_path: "data/metadata"
     forms_path: "data/metadata_forms"
     relationships_file: "data/relationships.json"
     form_creator:
       source_app: "masterData"
       plugin_endpoint: "/web/json/app/masterData/1/plugin/..."
     subcategory_mappings:
       md25equipmentCategory: {...}
       md27inputCategory: {...}
   ```

3. CLI Integration:
   ```bash
   # Existing commands (keep working)
   joget-util deploy ...
   joget-util fetch ...
   
   # New metadata commands (add)
   joget-util metadata discover
   joget-util metadata generate
   joget-util metadata compare
   joget-util metadata create
   joget-util metadata populate
   joget-util metadata all  # Run all phases
   ```

4. Dependency Injection:
   - New modules depend on existing HTTP client
   - New modules use existing config loader
   - New modules use existing logger
   - No modifications to existing modules unless necessary

5. Testing Strategy:
   - New tests for metadata features
   - Existing tests remain unchanged
   - Integration tests for metadata + existing features
```

### Task 0B.3: Backward Compatibility Plan
```
Ensure existing functionality continues to work:

1. ✓ No changes to existing CLI commands
2. ✓ No changes to existing config structure (only additions)
3. ✓ No modifications to existing modules
4. ✓ New features are opt-in (must explicitly call metadata commands)
5. ✓ Existing imports remain valid
6. ✓ Existing tests pass without modification

Breaking Changes: NONE
Migration Required: NONE
```

## Phase 0C: Implementation Setup

### Task 0C.1: Create New Package Structure
```python
# Create src/metadata/ package with sub-packages
mkdir -p src/metadata/discovery
mkdir -p src/metadata/generators
mkdir -p src/metadata/validators
mkdir -p src/metadata/models

# Create __init__.py files
touch src/metadata/__init__.py
touch src/metadata/discovery/__init__.py
touch src/metadata/generators/__init__.py
touch src/metadata/validators/__init__.py
touch src/metadata/models/__init__.py
```

### Task 0C.2: Extend Configuration
```python
# In config/config.yaml, add new section:

metadata:
  paths:
    csv: "data/metadata"
    forms: "data/metadata_forms"
    overview: "docs/metadata-overview.docx"
    relationships: "data/relationships.json"
  
  behavior:
    auto_generate_forms: true
    detect_nested_lovs: true
    use_metadata_overview: true
    validate_generated_json: true
    augment_pattern2_data: true
  
  form_creator:
    source_app: "masterData"
    source_app_version: "1"
    plugin_endpoint: "/web/json/app/masterData/1/plugin/org.joget.gam.FormCreatorDatalist/service"
    create_crud: "yes"
    create_api_endpoint: "yes"
    wait_timeout: 60
    check_interval: 3
  
  subcategory_mappings:
    md25equipmentCategory:
      TILLAGE: md25tillageEquipment
      PLANTING: md25plantingEquipment
      PEST_CONTROL: md25pestControlEquipment
      IRRIGATION: md25irrigationEquipment
      STORAGE: md25storageEquipment
      PROCESSING: md25processingEquipment
      TRANSPORT: md25transportEquipment
      GENERAL_TOOLS: md25generalTools
      LIVESTOCK_EQUIP: md25livestockEquipment
    
    md27inputCategory:
      SEEDS: md19crops
      FERTILIZER: md27fertilizer
      PESTICIDES: md27pesticide
      LIVESTOCK_VET: md27livestockSupply
      IRRIGATION: md27irrigation
```

### Task 0C.3: Add New CLI Commands
```python
# src/cli/metadata_commands.py (NEW FILE)

import click
from src.metadata.discovery import csv_analyzer
from src.metadata.generators import form_generator
# ... imports

@click.group()
def metadata():
    """Metadata management commands."""
    pass

@metadata.command()
def discover():
    """Discover CSV files and generate form definitions."""
    click.echo("Phase 1: CSV Discovery & Form Generation")
    # Implementation...

@metadata.command()
def compare():
    """Compare local forms with Joget instance."""
    click.echo("Phase 2: Form Discovery & Comparison")
    # Implementation...

@metadata.command()
def create():
    """Create missing forms in Joget."""
    click.echo("Phase 3: Form Creation")
    # Implementation...

@metadata.command()
def populate():
    """Populate forms with CSV data."""
    click.echo("Phase 4: Data Population")
    # Implementation...

@metadata.command()
def all():
    """Run all phases sequentially."""
    click.echo("Running all phases...")
    # Call all phases in order...

# Register with main CLI
# In src/cli/commands.py:
# from src.cli.metadata_commands import metadata
# cli.add_command(metadata)
```

### Task 0C.4: Create Integration Points
```python
# src/joget/form_api.py (NEW - uses existing client)

from src.joget.client import JogetClient

class FormAPI:
    """Form management operations using existing Joget client."""
    
    def __init__(self, client: JogetClient):
        self.client = client  # Reuse existing HTTP client
    
    def list_forms(self, app_id, app_version):
        """List all forms in application."""
        return self.client.get(f'/app/{app_id}/{app_version}/forms')
    
    def get_form_definition(self, app_id, app_version, form_id):
        """Get form JSON definition."""
        return self.client.get(f'/app/{app_id}/{app_version}/form/{form_id}')
    
    # ... more methods
```

### Task 0C.5: Update Dependencies
```txt
# requirements.txt (ADD new dependencies)

# Existing dependencies (keep all)
# ... existing ...

# New dependencies for metadata management
python-docx==1.1.0  # For parsing metadata-overview.docx
pandas==2.1.4       # For CSV processing (if not already present)
```

## Phase 0D: Validation & Testing

### Task 0D.1: Verify Integration
```
Checklist:
- [ ] New package structure created
- [ ] Configuration extended (not replaced)
- [ ] CLI commands added (existing ones work)
- [ ] Integration points established
- [ ] Dependencies updated
- [ ] No breaking changes introduced
```

### Task 0D.2: Run Existing Tests
```bash
# Ensure all existing tests still pass
pytest tests/

# Expected: All existing tests pass (green)
```

### Task 0D.3: Test New CLI Structure
```bash
# Verify new commands are available
joget-util --help
# Should show new 'metadata' command group

joget-util metadata --help
# Should show: discover, compare, create, populate, all
```

## Console Output Example

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                   PHASE 0: INTEGRATION DESIGN & SETUP                        ║
╚══════════════════════════════════════════════════════════════════════════════╝

Phase 0A: Exploring Existing Structure
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 Current joget_utility structure:
joget_utility/
├── src/
│   ├── joget/
│   │   ├── client.py ♻️ (HTTP client - 250 lines)
│   │   └── api.py ♻️ (Basic API wrapper - 180 lines)
│   ├── config/
│   │   └── loader.py ♻️ (YAML config loader - 120 lines)
│   └── utils/
│       └── logger.py ♻️ (Logging setup - 80 lines)

Analysis:
✓ HTTP client available for reuse (requests-based)
✓ Configuration management in place (YAML + env)
✓ Logging infrastructure ready (loguru)
✓ No existing metadata/form handling
✓ No CLI structure (needs to be added)

Reusable Components Found: 4
New Components Needed: ~15
Integration Complexity: MODERATE

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Phase 0B: Integration Design
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Proposed Structure:
joget_utility/
├── src/
│   ├── joget/ ♻️
│   │   ├── client.py ♻️ (Keep)
│   │   ├── api.py ♻️ (Keep)
│   │   ├── form_api.py 🆕 (Add - extends api.py)
│   │   ├── data_api.py 🆕 (Add - extends api.py)
│   │   └── plugin_api.py 🆕 (Add - form creator)
│   │
│   ├── metadata/ 🆕 (NEW PACKAGE)
│   │   ├── discovery/ 🆕 (4 modules)
│   │   ├── generators/ 🆕 (4 modules)
│   │   ├── validators/ 🆕 (2 modules)
│   │   └── models/ 🆕 (3 modules)
│   │
│   ├── config/ 🔄
│   │   └── loader.py 🔄 (Extend)
│   │
│   ├── utils/ 🔄
│   │   ├── logger.py ♻️ (Keep)
│   │   └── console_formatter.py 🆕 (Add)
│   │
│   └── cli/ 🆕 (NEW PACKAGE)
│       ├── commands.py 🆕
│       └── metadata_commands.py 🆕

Integration Strategy:
✓ Isolated package (src/metadata/)
✓ Extends existing components (config, utils)
✓ Adds new API wrappers (form_api, data_api)
✓ No modifications to existing modules
✓ Backward compatible (100%)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Phase 0C: Implementation Setup
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Creating package structure...
✓ Created src/metadata/
✓ Created src/metadata/discovery/
✓ Created src/metadata/generators/
✓ Created src/metadata/validators/
✓ Created src/metadata/models/
✓ Created src/cli/

Extending configuration...
✓ Added metadata section to config.yaml
✓ Added subcategory_mappings
✓ Added form_creator settings

Setting up CLI...
✓ Created src/cli/commands.py
✓ Created src/cli/metadata_commands.py
✓ Registered metadata command group

Updating dependencies...
✓ Added python-docx==1.1.0
✓ Added pandas==2.1.4 (if needed)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Phase 0D: Validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Running existing tests...
✓ All 47 existing tests passed (100%)
✓ No breaking changes detected
✓ Code coverage maintained at 85%

Verifying new structure...
✓ Import paths work correctly
✓ Configuration loads successfully
✓ CLI commands registered properly

Testing CLI:
$ joget-util --help
  Commands:
    metadata  Metadata management commands

$ joget-util metadata --help
  Commands:
    discover  Discover CSV files and generate form definitions
    compare   Compare local forms with Joget instance
    create    Create missing forms in Joget
    populate  Populate forms with CSV data
    all       Run all phases sequentially

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Phase 0 Complete!

Summary:
  ♻️ Reused: 4 existing modules
  🆕 Added: 15 new modules
  🔄 Extended: 2 existing modules
  ⚠️ Breaking changes: 0
  ✓ Backward compatibility: 100%

Ready for Phase 1 implementation!

Next Steps:
  → Begin Phase 1: CSV Discovery & Form Generation
  → All existing functionality preserved
  → New metadata features available via 'metadata' CLI group

Press Enter to continue to Phase 1...
```

## Success Criteria for Phase 0

✅ **Structure:**
- [ ] New src/metadata/ package created
- [ ] All sub-packages in place (discovery, generators, validators, models)
- [ ] CLI infrastructure added

✅ **Integration:**
- [ ] Existing modules work without modification
- [ ] New modules can import and use existing components
- [ ] Configuration extended (not replaced)
- [ ] No import conflicts

✅ **Testing:**
- [ ] All existing tests pass
- [ ] No breaking changes
- [ ] New CLI commands registered
- [ ] Can run `joget-util metadata --help`

✅ **Documentation:**
- [ ] Integration design documented
- [ ] New structure matches proposal
- [ ] Backward compatibility confirmed

---

# PHASE 1: CSV Discovery & Form Definition Auto-Generation

[Rest of the specification continues as in the original document...]

# PHASE 2: Form Discovery & Comparison

[Content as in original...]

# PHASE 3: Form Creation

[Content as in original...]

# PHASE 4: Data Population (ENHANCED FOR PATTERN 2)

[Content as in original...]

---

# IMPLEMENTATION NOTES

## Integration Best Practices

1. **Always import from existing modules first**
   ```python
   # Good - reuses existing client
   from src.joget.client import JogetClient
   
   # Bad - reimplements HTTP client
   import requests  # Don't do this if client exists
   ```

2. **Extend configuration, don't replace**
   ```python
   # Good - reads metadata section
   config['metadata']['paths']['csv']
   
   # Bad - assumes structure
   config['csv_path']
   ```

3. **Use existing utilities**
   ```python
   # Good - reuses existing logger
   from src.utils.logger import get_logger
   logger = get_logger(__name__)
   
   # Bad - creates new logger
   import logging
   logging.basicConfig(...)
   ```

4. **Maintain isolation**
   ```python
   # Good - self-contained metadata package
   from src.metadata.discovery import csv_analyzer
   
   # Bad - mixing concerns
   from src.joget.forms import analyze_csv  # Don't do this
   ```

## CLI Command Naming Convention

```bash
# Pattern: joget-util <group> <action>

joget-util metadata discover    # Group: metadata, Action: discover
joget-util metadata generate    # Alternative name for discover
joget-util metadata compare     # Compare forms
joget-util metadata create      # Create forms in Joget
joget-util metadata populate    # Populate with data
joget-util metadata all         # Run all phases
```

## Configuration Access Pattern

```python
# Always access config through loader
from src.config.loader import load_config

config = load_config()
metadata_config = config.get('metadata', {})
csv_path = metadata_config.get('paths', {}).get('csv', 'data/metadata')
```


---

# PHASE 1: CSV Discovery & Form Definition Auto-Generation

## Objective
Scan `data/metadata/` for CSV files and automatically generate Joget form definition JSONs when they don't exist in `data/metadataapp/forms/`. Intelligently detect and create nested (cascading) LOV relationships, including the subcategory source pattern.

## Input
- CSV files in `data/metadata/`
- Existing JSON files in `data/metadataapp/forms/` (if any)
- `docs/metadata-overview.docx` (optional, for relationship validation)

## Output
- New JSON form definitions in `data/metadataapp/forms/`
- `data/relationships.json` - Relationship metadata file
- Console report of generated forms

## Algorithm

### Step 1.1: Scan and Inventory
```python
def discover_csv_files():
    """
    Scan data/metadata/ and create inventory.
    
    Returns:
        dict: {
            'csv_filename': {
                'path': 'data/metadata/md25tillageEquipment.csv',
                'form_id': 'md25tillageEquipment',
                'has_json': True/False,
                'json_path': 'data/metadataapp/forms/md25tillageEquipment.json'
            }
        }
    """
```

**Logic:**
1. List all `.csv` files in `data/metadata/`
2. For each CSV, derive form_id (filename without .csv extension)
3. Check if corresponding `.json` exists in `data/metadataapp/forms/`
4. Mark CSVs needing JSON generation

### Step 1.2: CSV Analysis & Column Detection

For each CSV requiring JSON generation:

```python
def analyze_csv_structure(csv_path):
    """
    Analyze CSV structure and detect metadata.
    
    Returns:
        {
            'filename': 'md25tillageEquipment.csv',
            'form_id': 'md25tillageEquipment',
            'columns': [
                {
                    'name': 'code',
                    'type': 'string',
                    'is_primary_key': True,
                    'is_foreign_key': False,
                    'sample_values': ['PLOUGH_ANIMAL', 'PLOUGH_TRACTOR']
                },
                {
                    'name': 'name',
                    'type': 'string',
                    'is_primary_key': False,
                    'sample_values': ['Animal-Drawn Plough', 'Tractor Plough']
                }
            ],
            'row_count': 10
        }
    """
```

**Primary Key Detection:**
```python
def detect_primary_key(columns):
    """
    Identify primary key column(s).
    
    Rules (in priority order):
    1. Column named exactly 'id' or 'code'
    2. Column ending with '_id' or '_code' (first one found)
    3. Column with all unique values (first one found)
    4. Default to first column if nothing else matches
    
    Returns: column_name (string)
    """
```

### Step 1.3: Relationship Detection (CRITICAL - Two Patterns)

#### Pattern 1: Traditional Foreign Key Detection
```python
def detect_traditional_relationships(csv_files_metadata):
    """
    Detect parent-child relationships via foreign key columns in CSV.
    
    Detection Rules:
    1. Column ends with '_code' or '_id'
    2. Remove suffix and check for matching parent CSV
       Example: 'country_code' → check for 'country.csv' or 'countries.csv'
    3. Column name matches another CSV filename
       Example: column 'district' → check for 'district.csv'
    
    Returns:
        [
            {
                'pattern_type': 'traditional_fk',
                'parent_csv': 'md03district.csv',
                'parent_form': 'md03district',
                'parent_primary_key': 'code',
                'child_csv': 'md37collectionPoint.csv',
                'child_form': 'md37collectionPoint',
                'child_foreign_key': 'district_code',
                'relationship_type': 'nested_lov',
                'needs_fk_injection': False  # FK already exists in CSV
            }
        ]
    """
```

#### Pattern 2: Subcategory Source Detection ⭐ NEW
```python
def detect_subcategory_source_relationships(csv_files_metadata, metadata_overview_path=None):
    """
    Detect parent-child relationships via subcategory source pattern.
    
    This pattern applies to your MD25 (Equipment) and MD27 (Input) hierarchies.
    
    Detection Methods:
    
    Method 1: Parse metadata-overview.docx
    Look for relationship mappings like:
    - "MD25A: Equipment Categories (Parent)"
    - "  ├─ MD25B: Tillage Equipment (TILLAGE)"
    - "TILLAGE → md25tillageEquipment"
    
    Method 2: Naming Convention Analysis
    If parent is md25equipmentCategory.csv or md27inputCategory.csv:
    - Find all CSVs starting with same prefix (md25*, md27*)
    - Exclude the category file itself
    - Map based on category codes
    
    Method 3: Parent CSV Metadata Column (if exists)
    Some parent CSVs may have columns like:
    - 'subcategory_source'
    - 'child_metadata'
    - 'child_form'
    
    Example Detection:
    
    Parent: md27inputCategory.csv
    Has rows:
      code='SEEDS', name='Seeds', ...
      code='FERTILIZER', name='Fertilizer', ...
      
    Child CSVs found:
      md19crops.csv (all records belong to SEEDS)
      md27fertilizer.csv (all records belong to FERTILIZER)
      md27pesticide.csv (all records belong to PESTICIDES)
    
    Returns:
        [
            {
                'pattern_type': 'subcategory_source',
                'parent_csv': 'md27inputCategory.csv',
                'parent_form': 'md27inputCategory',
                'parent_primary_key': 'code',
                'parent_code_value': 'SEEDS',  # Specific category
                'child_csv': 'md19crops.csv',
                'child_form': 'md19crops',
                'child_foreign_key': 'input_category_code',  # GENERATED NAME
                'relationship_type': 'nested_lov',
                'needs_fk_injection': True,  # ⚠️ CRITICAL: FK not in CSV!
                'fk_value_to_inject': 'SEEDS'  # Value to add to all records
            },
            {
                'pattern_type': 'subcategory_source',
                'parent_csv': 'md27inputCategory.csv',
                'parent_form': 'md27inputCategory',
                'parent_primary_key': 'code',
                'parent_code_value': 'FERTILIZER',
                'child_csv': 'md27fertilizer.csv',
                'child_form': 'md27fertilizer',
                'child_foreign_key': 'input_category_code',
                'relationship_type': 'nested_lov',
                'needs_fk_injection': True,
                'fk_value_to_inject': 'FERTILIZER'
            }
        ]
    """
```

**Your Specific Metadata Mappings:**
```python
# Based on metadata-overview.docx:
KNOWN_SUBCATEGORY_MAPPINGS = {
    'md25equipmentCategory': {
        'TILLAGE': 'md25tillageEquipment',
        'PLANTING': 'md25plantingEquipment',
        'PEST_CONTROL': 'md25pestControlEquipment',
        'IRRIGATION': 'md25irrigationEquipment',
        'STORAGE': 'md25storageEquipment',
        'PROCESSING': 'md25processingEquipment',
        'TRANSPORT': 'md25transportEquipment',
        'GENERAL_TOOLS': 'md25generalTools',
        'LIVESTOCK_EQUIP': 'md25livestockEquipment'
    },
    'md27inputCategory': {
        'SEEDS': 'md19crops',
        'FERTILIZER': 'md27fertilizer',
        'PESTICIDES': 'md27pesticide',
        'LIVESTOCK_VET': 'md27livestockSupply',
        'IRRIGATION': 'md27irrigation'
    }
}
```

### Step 1.4: Form Type Classification

```python
def classify_form_type(csv_metadata, relationships):
    """
    Classify each CSV into a form type.
    
    Types:
    - 'simple': No relationships, basic data
    - 'parent_lov': Referenced by other forms (Pattern 1 or Pattern 2)
    - 'child_lov': Has foreign key to parent (Pattern 1 - FK in CSV)
    - 'child_lov_injected': Belongs to parent but no FK in CSV (Pattern 2)
    
    Returns: form_type (string)
    """
```

### Step 1.5: Form Definition JSON Generation

#### For PARENT LOV Forms:
```python
def generate_parent_lov_form(csv_metadata):
    """
    Generate parent LOV form (master data).
    
    Standard Structure:
    - 'code' field: TextField with DuplicateValueValidator
    - 'name' field: TextField (display name)
    - Other fields from CSV
    
    Template: See JOGET_NESTED_LOV_GUIDE.md → Parent LOV Form
    """
```

#### For CHILD LOV Forms - Pattern 1 (Traditional FK):
```python
def generate_child_lov_form_traditional(csv_metadata, relationship):
    """
    Generate child LOV form where FK exists in CSV.
    
    Example: md37collectionPoint.csv has district_code column
    
    Field Order:
    1. Primary key (TextField with DuplicateValueValidator)
    2. Foreign key (SelectBox with FormOptionsBinder) ← ALREADY IN CSV
    3. Other fields (TextField or appropriate type)
    """
```

#### For CHILD LOV Forms - Pattern 2 (Subcategory Source) ⭐ NEW
```python
def generate_child_lov_form_subcategory(csv_metadata, relationship):
    """
    Generate child LOV form where FK must be INJECTED.
    
    ⚠️ CRITICAL DIFFERENCE: The child CSV does NOT have a foreign key column,
    but the child FORM must have a SelectBox for cascading to work!
    
    Example: md25tillageEquipment.csv
    
    CSV columns:
    - code, name, power_source, estimated_cost_lsl, ...
    - NO equipment_category_code column!
    
    Generated form JSON must include:
    - code (TextField with DuplicateValueValidator)
    - equipment_category_code (SelectBox → md25equipmentCategory) ⭐ INJECTED!
    - name (TextField)
    - power_source (TextField)
    - ... other fields from CSV
    
    Field Generation Logic:
    1. Determine FK field name from parent form name
       Example: parent="md25equipmentCategory" → fk="equipment_category_code"
    2. Insert SelectBox AFTER primary key field (position 1)
    3. Generate all other fields from CSV columns
    """
```

**Example Output (md25tillageEquipment.json):**
```json
{
    "className": "org.joget.apps.form.model.Form",
    "properties": {
        "id": "md25tillageEquipment",
        "name": "Tillage Equipment",
        "tableName": "md25tillageEquipment"
    },
    "elements": [
        {
            "className": "org.joget.apps.form.model.Section",
            "properties": {
                "label": "Section",
                "id": "section1"
            },
            "elements": [
                {
                    "className": "org.joget.apps.form.model.Column",
                    "properties": {"width": "100%"},
                    "elements": [
                        {
                            "className": "org.joget.apps.form.lib.TextField",
                            "properties": {
                                "id": "code",
                                "label": "Code",
                                "validator": {
                                    "className": "org.joget.apps.form.lib.DuplicateValueValidator",
                                    "properties": {
                                        "formDefId": "md25tillageEquipment",
                                        "fieldId": "code",
                                        "mandatory": "true"
                                    }
                                }
                            }
                        },
                        {
                            "className": "org.joget.apps.form.lib.SelectBox",
                            "properties": {
                                "id": "equipment_category_code",
                                "label": "Equipment Category",
                                "optionsBinder": {
                                    "className": "org.joget.apps.form.lib.FormOptionsBinder",
                                    "properties": {
                                        "formDefId": "md25equipmentCategory",
                                        "idColumn": "code",
                                        "labelColumn": "name",
                                        "groupingColumn": "",
                                        "addEmptyOption": "true",
                                        "useAjax": ""
                                    }
                                },
                                "validator": {
                                    "className": "org.joget.apps.form.lib.DefaultValidator",
                                    "properties": {
                                        "mandatory": "true"
                                    }
                                }
                            }
                        },
                        {
                            "className": "org.joget.apps.form.lib.TextField",
                            "properties": {
                                "id": "name",
                                "label": "Name"
                            }
                        },
                        {
                            "className": "org.joget.apps.form.lib.TextField",
                            "properties": {
                                "id": "power_source",
                                "label": "Power Source"
                            }
                        }
                        // ... other fields from CSV
                    ]
                }
            ]
        }
    ]
}
```

### Step 1.6: Generate Relationship Metadata File

```python
def save_relationships_metadata(relationships, hierarchies):
    """
    Save relationship metadata to data/relationships.json
    
    Purpose: Used in Phase 2, 3, and 4 for:
    - Determining form creation order (parents before children)
    - Data population order (parents before children)
    - Validation of referential integrity
    - Data augmentation (Pattern 2 relationships)
    """
```

**Enhanced Output File: data/relationships.json**
```json
{
    "generated_at": "2025-01-15T10:30:00Z",
    "relationships": [
        {
            "pattern_type": "traditional_fk",
            "parent_form": "md03district",
            "parent_primary_key": "code",
            "child_form": "md37collectionPoint",
            "child_foreign_key": "district_code",
            "relationship_type": "nested_lov",
            "needs_fk_injection": false
        },
        {
            "pattern_type": "subcategory_source",
            "parent_form": "md27inputCategory",
            "parent_primary_key": "code",
            "parent_code_value": "SEEDS",
            "child_form": "md19crops",
            "child_foreign_key": "input_category_code",
            "relationship_type": "nested_lov",
            "needs_fk_injection": true,
            "fk_value_to_inject": "SEEDS",
            "notes": "Child CSV does not have FK column; will be injected during form creation and data population"
        },
        {
            "pattern_type": "subcategory_source",
            "parent_form": "md27inputCategory",
            "parent_primary_key": "code",
            "parent_code_value": "FERTILIZER",
            "child_form": "md27fertilizer",
            "child_foreign_key": "input_category_code",
            "relationship_type": "nested_lov",
            "needs_fk_injection": true,
            "fk_value_to_inject": "FERTILIZER"
        },
        {
            "pattern_type": "subcategory_source",
            "parent_form": "md25equipmentCategory",
            "parent_primary_key": "code",
            "parent_code_value": "TILLAGE",
            "child_form": "md25tillageEquipment",
            "child_foreign_key": "equipment_category_code",
            "relationship_type": "nested_lov",
            "needs_fk_injection": true,
            "fk_value_to_inject": "TILLAGE"
        },
        {
            "pattern_type": "subcategory_source",
            "parent_form": "md25equipmentCategory",
            "parent_primary_key": "code",
            "parent_code_value": "PLANTING",
            "child_form": "md25plantingEquipment",
            "child_foreign_key": "equipment_category_code",
            "relationship_type": "nested_lov",
            "needs_fk_injection": true,
            "fk_value_to_inject": "PLANTING"
        }
        // ... more relationships
    ],
    "hierarchies": [
        {
            "name": "equipment_hierarchy",
            "pattern": "subcategory_source",
            "levels": [
                {
                    "form": "md25equipmentCategory",
                    "level": 0,
                    "parent": null
                },
                {
                    "forms": [
                        "md25tillageEquipment",
                        "md25plantingEquipment",
                        "md25pestControlEquipment",
                        "md25irrigationEquipment",
                        "md25storageEquipment",
                        "md25processingEquipment",
                        "md25transportEquipment",
                        "md25generalTools",
                        "md25livestockEquipment"
                    ],
                    "level": 1,
                    "parent": "md25equipmentCategory"
                }
            ]
        },
        {
            "name": "input_hierarchy",
            "pattern": "subcategory_source",
            "levels": [
                {
                    "form": "md27inputCategory",
                    "level": 0,
                    "parent": null
                },
                {
                    "forms": [
                        "md19crops",
                        "md27fertilizer",
                        "md27pesticide",
                        "md27livestockSupply",
                        "md27irrigation"
                    ],
                    "level": 1,
                    "parent": "md27inputCategory"
                }
            ]
        }
    ]
}
```

### Step 1.7: Validation

```python
def validate_generated_forms():
    """
    Validate all generated JSON form definitions.
    
    Checks:
    1. JSON is valid and parseable
    2. Has required Joget structure (className, properties, elements)
    3. Primary key field has DuplicateValueValidator
    4. Foreign key fields are SelectBox (NOT TextField)
    5. SelectBox fields have FormOptionsBinder with correct parent form
    6. No circular dependencies
    7. All parent forms exist before child forms reference them
    8. For Pattern 2: FK field is injected correctly
    
    Returns: validation_report (dict)
    """
```

## Console Output Example

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                     PHASE 1: CSV DISCOVERY & FORM GENERATION                 ║
╚══════════════════════════════════════════════════════════════════════════════╝

📋 Scanning data/metadata/ for CSV files...

Found 39 CSV files:
  ✓ 10 have existing JSON definitions (MD01-MD10)
  ⚠ 29 need auto-generation (MD21-MD37 + nested children)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 Analyzing CSV structures and detecting relationships...

📖 Parsing metadata-overview.docx for documented relationships...
  ✓ Found Equipment Category hierarchy (1 parent → 9 children)
  ✓ Found Input Category hierarchy (1 parent → 5 children)

Relationship Detection:

Pattern 1 (Traditional FK):
  ✓ md03district.csv → md37collectionPoint.csv (via district_code)

Pattern 2 (Subcategory Source):
  ✓ md27inputCategory → md19crops (SEEDS)
  ✓ md27inputCategory → md27fertilizer (FERTILIZER)
  ✓ md27inputCategory → md27pesticide (PESTICIDES)
  ✓ md27inputCategory → md27livestockSupply (LIVESTOCK_VET)
  ✓ md27inputCategory → md27irrigation (IRRIGATION)
  ✓ md25equipmentCategory → md25tillageEquipment (TILLAGE)
  ✓ md25equipmentCategory → md25plantingEquipment (PLANTING)
  ✓ md25equipmentCategory → md25pestControlEquipment (PEST_CONTROL)
  ✓ md25equipmentCategory → md25irrigationEquipment (IRRIGATION)
  ✓ md25equipmentCategory → md25storageEquipment (STORAGE)
  ✓ md25equipmentCategory → md25processingEquipment (PROCESSING)
  ✓ md25equipmentCategory → md25transportEquipment (TRANSPORT)
  ✓ md25equipmentCategory → md25generalTools (GENERAL_TOOLS)
  ✓ md25equipmentCategory → md25livestockEquipment (LIVESTOCK_EQUIP)

Hierarchy Detection:
  📊 Equipment: md25equipmentCategory (L0) → 9 children (L1)
  📊 Input: md27inputCategory (L0) → 5 children (L1)
  📊 Geographic: md03district (L0) → md37collectionPoint (L1)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🛠 Generating Form Definitions...

[1/29] md21programType.json
      └─ Type: Simple
      └─ Fields: code (PK), name, description
      └─ Status: ✓ Generated

[2/29] md25equipmentCategory.json
      └─ Type: Parent LOV (Pattern 2 - has 9 children)
      └─ Fields: code (PK), name, description, typical_subsidy_percent, requires_training
      └─ Status: ✓ Generated

[3/29] md25tillageEquipment.json
      └─ Type: Child LOV - Subcategory Source (Pattern 2)
      └─ Parent: md25equipmentCategory (category: TILLAGE)
      └─ CSV Fields: code, name, power_source, estimated_cost_lsl, ...
      └─ Injected Field: equipment_category_code (SelectBox → md25equipmentCategory) ⭐
      └─ All records will get: equipment_category_code = 'TILLAGE'
      └─ Status: ✓ Generated with FK injection

[4/29] md25plantingEquipment.json
      └─ Type: Child LOV - Subcategory Source (Pattern 2)
      └─ Parent: md25equipmentCategory (category: PLANTING)
      └─ Injected Field: equipment_category_code (SelectBox) ⭐
      └─ Status: ✓ Generated with FK injection

[...similar for other equipment children...]

[12/29] md27inputCategory.json
       └─ Type: Parent LOV (Pattern 2 - has 5 children)
       └─ Fields: code (PK), name, has_subcategory, default_unit, ...
       └─ Status: ✓ Generated

[13/29] md19crops.json
       └─ Type: Child LOV - Subcategory Source (Pattern 2)
       └─ Parent: md27inputCategory (category: SEEDS)
       └─ CSV Fields: code, name, crop_category
       └─ Injected Field: input_category_code (SelectBox → md27inputCategory) ⭐
       └─ All records will get: input_category_code = 'SEEDS'
       └─ Status: ✓ Generated with FK injection

[...continues for all forms...]

[28/29] md37collectionPoint.json
       └─ Type: Child LOV - Traditional FK (Pattern 1)
       └─ Parent: md03district
       └─ Fields: code (PK), district_code (SelectBox → md03district), name, address, ...
       └─ Note: district_code already exists in CSV
       └─ Status: ✓ Generated

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Validation Results:

✓ All JSON files are valid
✓ All primary keys have DuplicateValueValidator
✓ All foreign keys use SelectBox (not TextField)
✓ All FormOptionsBinders reference correct parent forms
✓ Pattern 2 forms have injected FK SelectBox fields
✓ No circular dependencies detected
✓ Hierarchical order is valid

⚠️ Important Notes:
  • 14 forms use Pattern 2 (Subcategory Source)
  • These forms have FK fields NOT in original CSV
  • Data population will augment CSV data with FK values

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📊 Summary:

Generated Files:        29
Traditional FK (P1):    1
Subcategory Source (P2): 14
Simple Forms:           14
Relationships Found:    15
Hierarchies Detected:   3
Validation Status:      ✓ PASSED

Output Locations:
  └─ Form Definitions: data/metadataapp/forms/
  └─ Relationships:    data/relationships.json

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Next Steps:
  → Review generated JSON files in data/metadataapp/forms/
  → Verify relationship metadata in data/relationships.json
  → Pay special attention to Pattern 2 forms with injected FK fields
  → Run Phase 2 to create forms in Joget

Press Enter to continue to Phase 2...
```

---

# PHASE 2: Form Discovery & Comparison

[Content remains the same as original prompt]

---

# PHASE 3: Form Creation

[Content remains the same as original prompt through Step 3.3]

### Step 3.4: Special Handling for Pattern 2 Forms

```python
def verify_pattern2_form_creation(form_id, relationship, joget_client):
    """
    Additional verification for Pattern 2 (subcategory source) forms.
    
    After form is created, verify:
    1. Injected FK field exists in form definition
    2. FK field is SelectBox (not TextField)
    3. SelectBox references correct parent form
    4. Database table has the FK column
    
    This is critical because the FK field was NOT in the original CSV.
    """
```

---

# PHASE 4: Data Population (ENHANCED FOR PATTERN 2)

## Objective
Populate forms with data from CSV files using generated API endpoints. **CRITICAL:** Augment CSV data with FK values for Pattern 2 relationships.

## Algorithm Updates

### Step 4.1b: Data Augmentation for Pattern 2 (NEW) ⭐

```python
def augment_csv_with_parent_value(csv_data, relationship):
    """
    Add parent category value to ALL rows in child CSV.
    
    This is ONLY for Pattern 2 (subcategory_source) relationships.
    
    Example: md25tillageEquipment.csv
    
    Original CSV:
    code              | name                    | power_source | ...
    ------------------------------------------------------------------
    PLOUGH_ANIMAL     | Animal-Drawn Plough     | animal       | ...
    PLOUGH_TRACTOR    | Tractor Plough          | tractor      | ...
    
    After Augmentation (before Joget upload):
    code              | equipment_category_code | name                    | ...
    -------------------------------------------------------------------------------
    PLOUGH_ANIMAL     | TILLAGE                 | Animal-Drawn Plough     | ...
    PLOUGH_TRACTOR    | TILLAGE                 | Tractor Plough          | ...
    
    The value "TILLAGE" comes from relationship['fk_value_to_inject'].
    
    Process:
    1. Check if relationship needs_fk_injection == True
    2. Get FK field name and value from relationship metadata
    3. Add new column to DataFrame with constant value
    4. Reorder columns: [primary_key, foreign_key, ...other_fields]
    5. Return augmented DataFrame
    """
```

### Step 4.2: Read and Validate CSV (Enhanced)

```python
def read_and_validate_csv(csv_path, form_json_path, relationships):
    """
    Read CSV and validate against form definition.
    
    NEW: Check if this CSV needs data augmentation (Pattern 2)
    
    Validations:
    1. All form fields have corresponding CSV columns OR will be injected
    2. Required fields are not empty
    3. Data types match expected types
    4. Foreign key values exist in parent tables
    5. No duplicate primary key values
    
    Returns:
        {
            'data': pandas.DataFrame,  # May be augmented with FK column
            'validation_errors': [],
            'warnings': [],
            'augmented': True/False  # True if FK was injected
        }
    """
```

### Step 4.4: Populate Data (Enhanced)

```python
def populate_form_data(csv_data, api_name, existing_records, relationship, joget_client):
    """
    Populate form with data from CSV.
    
    NEW: Handle augmented data for Pattern 2 relationships
    
    For each row in CSV:
    1. If Pattern 2: Verify augmented FK column exists
    2. Extract primary key value
    3. Check if record exists
    4. If exists:
       - Compare data (including injected FK for Pattern 2)
       - If different: UPDATE
       - If same: SKIP
    5. If not exists:
       - CREATE new record (with FK value for Pattern 2)
    
    Returns:
        {
            'created': 150,
            'updated': 25,
            'skipped': 30,
            'errors': 5,
            'total': 210,
            'augmented': True/False  # Indicates if data was augmented
        }
    """
```

## Console Output Example (Enhanced)

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                          PHASE 4: DATA POPULATION                            ║
╚══════════════════════════════════════════════════════════════════════════════╝

📋 CSV files to process: 39
⏱ Estimated time: ~10-15 minutes

Population Order (by dependency):
  Level 0: md01-md24 (simple & parent LOVs)
  Level 1: md25* children, md27* children, md37

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[1/39] Processing: md21programType.csv → API: md21programType
       
       📖 Reading CSV...
       ✓ Found 8 records
       ✓ Simple form (no relationships)
       
       📤 Populating data...
       ████████████████████████████████████████ 100% (8/8)
       
       Results:
         • Created:  8 records
         • Updated:  0 records
       
       Status: ✅ SUCCESS (2s)

[...continues through parent LOVs...]

[15/39] Processing: md25equipmentCategory.csv → API: md25equipmentCategory
        
        📖 Reading CSV...
        ✓ Found 9 records (Parent LOV for Pattern 2)
        ✓ This form has 9 child forms that will reference it
        
        📤 Populating data...
        ████████████████████████████████████████ 100% (9/9)
        
        Results:
          • Created:  9 records
          • Updated:  0 records
        
        Status: ✅ SUCCESS (3s)

[16/39] Processing: md25tillageEquipment.csv → API: md25tillageEquipment
        
        📖 Reading CSV...
        ✓ Found 10 records
        
        ⚙️ Data Augmentation (Pattern 2):
        ✓ Injecting FK column: equipment_category_code
        ✓ FK value: TILLAGE
        ✓ All 10 records augmented
        
        Before:
          Columns: code, name, power_source, estimated_cost_lsl, ...
        
        After:
          Columns: code, equipment_category_code, name, power_source, ...
          All rows have: equipment_category_code = 'TILLAGE'
        
        ✓ Validating augmented data...
        ✓ All required fields present (including injected FK)
        
        🔗 Verifying parent category exists...
        ✓ Category 'TILLAGE' found in md25equipmentCategory
        
        📤 Populating data...
        ████████████████████████████████████████ 100% (10/10)
        
        Results:
          • Created:  10 records (with FK = 'TILLAGE')
          • Updated:  0 records
          • Augmented: Yes
        
        Status: ✅ SUCCESS (4s)

[17/39] Processing: md25plantingEquipment.csv → API: md25plantingEquipment
        
        📖 Reading CSV...
        ✓ Found 8 records
        
        ⚙️ Data Augmentation (Pattern 2):
        ✓ Injecting FK column: equipment_category_code
        ✓ FK value: PLANTING
        ✓ All 8 records augmented
        
        📤 Populating data...
        ████████████████████████████████████████ 100% (8/8)
        
        Results:
          • Created:  8 records (with FK = 'PLANTING')
          • Augmented: Yes
        
        Status: ✅ SUCCESS (3s)

[...continues for all equipment children...]

[25/39] Processing: md27inputCategory.csv → API: md27inputCategory
        
        📖 Reading CSV...
        ✓ Found 8 records (Parent LOV for Pattern 2)
        ✓ This form has 5 child forms that will reference it
        
        📤 Populating data...
        ████████████████████████████████████████ 100% (8/8)
        
        Status: ✅ SUCCESS (2s)

[26/39] Processing: md19crops.csv → API: md19crops
        
        📖 Reading CSV...
        ✓ Found 21 records
        
        ⚙️ Data Augmentation (Pattern 2):
        ✓ Injecting FK column: input_category_code
        ✓ FK value: SEEDS
        ✓ All 21 records augmented
        
        🔗 Verifying parent category exists...
        ✓ Category 'SEEDS' found in md27inputCategory
        
        📤 Populating data...
        ████████████████████████████████████████ 100% (21/21)
        
        Results:
          • Created:  21 records (with FK = 'SEEDS')
          • Augmented: Yes
        
        Status: ✅ SUCCESS (5s)

[...continues...]

[38/39] Processing: md37collectionPoint.csv → API: md37collectionPoint
        
        📖 Reading CSV...
        ✓ Found 10 records
        ✓ Traditional FK pattern (district_code already in CSV)
        
        🔗 Verifying foreign key integrity...
        ✓ Checking district_code references... (10 unique values, all found)
        
        📤 Populating data...
        ████████████████████████████████████████ 100% (10/10)
        
        Results:
          • Created:  10 records
          • Augmented: No (FK already in CSV)
        
        Status: ✅ SUCCESS (3s)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📊 Population Summary:

┌────────────────────────┬─────────┐
│ Metric                 │ Value   │
├────────────────────────┼─────────┤
│ CSV Files Processed    │ 39/39   │
│ Total Records          │ ~450+   │
│ Records Created        │ ~450    │
│ Records Updated        │ 0       │
│ Records Skipped        │ 0       │
│ Records with Errors    │ 0       │
│ Success Rate           │ 100%    │
│ Total Time             │ 8m 15s  │
├────────────────────────┼─────────┤
│ Pattern 2 Augmentations│ 14      │
│ Records Augmented      │ ~150    │
└────────────────────────┴─────────┘

✅ All CSV files processed!

✅ Pattern 2 (Subcategory Source) Summary:
  • 14 child forms had FK values injected
  • All augmented records validated successfully
  • All parent category references verified

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 Detailed Reports Generated:
  → logs/population_summary_20250115_110000.json
  → logs/augmentation_report_20250115_110000.csv

Next Steps:
  → Review augmentation report for Pattern 2 forms
  → Test forms in Joget UI
  → Verify nested LOV cascading works for both patterns
  → Test application forms that consume this metadata
```

---

# TECHNICAL REQUIREMENTS

## Python Environment
- **Version**: Python 3.13
- **IDE**: IntelliJ IDEA
- **Package Manager**: pip or poetry

## Required Libraries
```txt
# requirements.txt

# HTTP & API
requests==2.31.0

# Data Processing
pandas==2.1.4
numpy==1.26.2

# Document Parsing (for metadata-overview.docx)
python-docx==1.1.0

# Configuration
pyyaml==6.0.1
python-dotenv==1.0.0

# JSON/Schema Validation
jsonschema==4.20.0

# CLI
click==8.1.7
rich==13.7.0

# Progress & Logging
tqdm==4.66.1
loguru==0.7.2

# Testing
pytest==7.4.3
responses==0.24.1
```

---

# CONFIGURATION

```yaml
# config/config.yaml

joget:
  instances:
    dev:
      name: "Development Instance"
      base_url: "http://localhost:8888/jw"
      username: "admin"
      password: "${JOGET_PASSWORD}"
      app_id: "subsidyApplication"
      app_version: "1"
      timeout: 30

  active_instance: "dev"
  
  form_creator:
    source_app: "masterData"
    source_app_version: "1"
    plugin_endpoint: "/web/json/app/masterData/1/plugin/org.joget.gam.FormCreatorDatalist/service"
    create_crud: "yes"
    create_api_endpoint: "yes"
    wait_timeout: 60
    check_interval: 3

paths:
  metadata_csv: "data/metadata"
  metadata_forms: "data/metadata_forms"
  metadata_overview: "docs/metadata-overview.docx"
  relationships: "data/relationships.json"
  logs: "logs"

logging:
  level: "INFO"
  file: "logs/joget_utility.log"
  console: true

behavior:
  # Phase 1
  auto_generate_forms: true
  detect_nested_lovs: true
  use_metadata_overview: true  # NEW: Parse documented relationships
  validate_generated_json: true
  
  # Phase 2
  compare_remote_forms: true
  report_differences: true
  
  # Phase 3
  verify_form_creation: true
  retry_on_failure: true
  max_retries: 3
  
  # Phase 4
  validate_csv_data: true
  check_foreign_keys: true
  augment_pattern2_data: true  # NEW: Enable data augmentation
  batch_size: 100
  skip_on_error: true

# NEW: Known subcategory mappings (from metadata-overview.docx)
subcategory_mappings:
  md25equipmentCategory:
    TILLAGE: md25tillageEquipment
    PLANTING: md25plantingEquipment
    PEST_CONTROL: md25pestControlEquipment
    IRRIGATION: md25irrigationEquipment
    STORAGE: md25storageEquipment
    PROCESSING: md25processingEquipment
    TRANSPORT: md25transportEquipment
    GENERAL_TOOLS: md25generalTools
    LIVESTOCK_EQUIP: md25livestockEquipment
  
  md27inputCategory:
    SEEDS: md19crops
    FERTILIZER: md27fertilizer
    PESTICIDES: md27pesticide
    LIVESTOCK_VET: md27livestockSupply
    IRRIGATION: md27irrigation
```

---

# SUCCESS CRITERIA

✅ **Phase 1:**
- [ ] All CSVs without JSON definitions have them generated
- [ ] Both Pattern 1 and Pattern 2 relationships were correctly identified
- [ ] Pattern 2 child LOV forms have INJECTED SelectBox fields
- [ ] All SelectBox fields properly configured with FormOptionsBinder
- [ ] Hierarchies documented in relationships.json
- [ ] All generated JSONs pass validation

✅ **Phase 2:**
- [ ] Can connect to Joget instance
- [ ] Can compare local vs remote definitions
- [ ] Identifies critical issues (TextField vs SelectBox)

✅ **Phase 3:**
- [ ] Can create forms in the correct dependency order
- [ ] Pattern 2 forms verified to have injected FK fields
- [ ] CRUDs and API endpoints generated
- [ ] 100% success rate

✅ **Phase 4:**
- [ ] Can populate all forms with CSV data
- [ ] Pattern 2 data augmentation works correctly
- [ ] All injected FK values validated against parent
- [ ] > 99% success rate for data population
- [ ] Augmentation report generated

✅ **Overall:**
- [ ] Can provision new Joget instance with all metadata
- [ ] Pattern 2 cascading works (equipment & input hierarchies)
- [ ] Comprehensive logging
- [ ] Reusable across metadata sets

---

# CRITICAL NOTES

## Pattern 2 (Subcategory Source) Requirements
1. **Form Generation**: FK SelectBox MUST be injected (not in CSV)
2. **Data Population**: CSV data MUST be augmented with FK values
3. **Validation**: All FK values must exist in parent table
4. **Database**: Table will have FK column even though CSV doesn't

## Foreign Key Field Naming Convention
For Pattern 2 relationships, FK field name derived from parent form:
- Parent: `md25equipmentCategory` → FK: `equipment_category_code`
- Parent: `md27inputCategory` → FK: `input_category_code`
- Pattern: `{parent_name_minus_prefix}_code`

## Data Augmentation Process
1. Read original CSV
2. Identify relationship from relationships.json
3. Get FK field name and value to inject
4. Add new column with constant value
5. Reorder columns (PK, FK, others)
6. Validate against parent table
7. Upload augmented data to Joget

