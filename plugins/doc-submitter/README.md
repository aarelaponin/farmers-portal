# GovStack Document Submitter Plugin for Joget DX8

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.registration.sender`
**Architecture:** Multi-Service Support

A Joget DX8 plugin for GovStack Registration Building Block that extracts form data, transforms it to GovStack JSON format, and sends it to the Processing API for registration.

## Overview

This plugin is the **sender component** in a two-part architecture:
- **DocSubmitter** (this plugin) - Extracts Joget form data, transforms to GovStack JSON, sends to Processing API
- **ProcessingServer** (receiver) - Receives GovStack JSON, maps to Joget forms, saves to database
- **WorkflowActivator** (configuration) - Sets serviceId (single configuration point)

Together they enable data exchange between Joget instances using GovStack Registration Building Block standards.

**Key Feature**: Reads serviceId from workflow variables (set by WorkflowActivator), enabling **one plugin to serve multiple services**.

## Features

- **Multi-Service Architecture** - One plugin serves multiple services (farmers, students, subsidies, etc.)
- **ServiceId from Workflow** - Reads serviceId from workflow variables (set by WorkflowActivator)
- **Convention-Based YAML Loading** - Automatically loads `{serviceId}.yml` configuration
- **YAML-Driven Configuration** - All field mappings defined in YAML files (no code changes needed)
- **Automatic Field Transformation** - Handles dates, numbers, checkboxes, master data lookups
- **Grid/Subform Support** - Processes parent-child relationships (parent → children grids)
- **Field Normalization** - Converts yes/no ↔ 1/2 formats automatically
- **Configuration Generators** - Auto-generate configuration from minimal hints (92% time savings)
- **Hot-Deployable** - OSGi bundle architecture, no server restart required

## Requirements

- Joget DX8 Platform
- Java 8 or higher
- Maven 3.6+

## Quick Start

### 1. Build the Plugin

```bash
mvn clean package
```

The compiled plugin will be available at `target/doc-submitter-8.1-SNAPSHOT.jar`

### 2. Install to Joget

1. Upload the JAR file through Joget's Manage Plugins interface (Settings → Manage Plugins)
2. The plugin will be hot-deployed without server restart

### 3. Configure in Workflow

**IMPORTANT**: ServiceId is configured in **WorkflowActivator**, not in DocSubmitter!

1. Add "GovStack Document Submitter" as a Tool in your workflow process (e.g., `farmers_registry_submission`)
2. Configure the plugin properties:
   - **API Endpoint**: `http://receiver:8080/jw/api` (base URL only, serviceId comes from workflow)
   - **API ID**: (from receiver's API key)
   - **API Key**: (from receiver's API key)
   - **Extraction Mode**: Workflow Assignment (uses assignment context)
   - **Validate Before Sending**: ✓ checked (recommended)
   - **Update Workflow Status**: ✓ checked (recommended)

**The plugin will automatically**:
- Read `serviceId` from workflow variables (set by WorkflowActivator)
- Load configuration from `{serviceId}.yml` (e.g., `farmers_registry.yml`)
- Send to API endpoint: `/services/{serviceId}/applications`

**For complete configuration, see [CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)**

## Configuration

### Multi-Service Architecture

**Key Concept**: One plugin JAR contains multiple YAML configuration files:
- `farmers_registry.yml` - Configuration for farmers service
- `subsidy_application.yml` - Configuration for subsidy service
- `student_enrollment.yml` - Configuration for student service

The plugin automatically loads the correct YAML based on `serviceId` from workflow variables.

### Services Configuration (`{serviceId}.yml`)

**File Location**: `src/main/resources/docs-metadata/{serviceId}.yml`
**Naming Convention**: File name MUST match serviceId exactly

**Example**: `farmers_registry.yml` (embedded in JAR)

```yaml
service:
  id: farmers_registry  # MUST match file name
  name: Farmers Registry Service

metadata:
  masterDataFields: [district, cropType, livestockType]
  fieldNormalization:
    yesNo: [canReadWrite, cropProduction]
    oneTwo: [gender, chronicallyIll]

formMappings:
  farmerBasicInfo:
    formId: farmerBasicInfo
    tableName: app_fd_farmer_basic_data
    fields:
      - joget: national_id
        govstack: identifiers[0].value
        required: true
      - joget: first_name
        govstack: name.given[0]
        required: true
```

**Example**: `subsidy_application.yml`

```yaml
service:
  id: subsidy_application  # MUST match file name
  name: Subsidy Application Service

formMappings:
  subsidyRequest:
    formId: subsidyRequest
    tableName: app_fd_subsidy_request
    fields:
      - joget: applicant_id
        govstack: identifiers[0].value
        required: true
```

### Configuration Generators ⚡

**Generate configuration in 2 seconds instead of 3 hours:**

```bash
# Quick start for farmer registry
./generate-farmer-config.sh

# For new services
./generate-config.sh student-mapping-hints.yaml student-business-rules.yaml
```

**Benefits:**
- 92% time savings (15 minutes vs 3 hours)
- Auto-detects 21 master data fields + 18 normalization fields
- Zero typos, guaranteed consistency
- 84-100% field coverage

See [README-GENERATORS.md](README-GENERATORS.md) for details.

## Project Structure

```
doc-submitter/
├── src/main/java/global/govstack/registration/sender/
│   ├── lib/DocSubmitter.java                    # Main plugin - extracts & sends data
│   ├── model/
│   │   ├── MappingHints.java                    # Configuration model for generators
│   │   └── BusinessRules.java                   # Validation rules model
│   ├── util/
│   │   ├── ServicesYamlGenerator.java           # Auto-generates services.yml
│   │   └── ValidationRulesGenerator.java        # Auto-generates validation-rules.yaml
│   ├── service/                                  # Business logic services
│   │   └── metadata/
│   │       └── YamlMetadataService.java         # Loads {serviceId}.yml dynamically
│   └── exception/                                # Custom exceptions
├── src/main/resources/
│   ├── properties/DocSubmitter.json              # Plugin UI configuration
│   └── docs-metadata/
│       ├── farmers_registry.yml                  # Farmers service configuration
│       ├── subsidy_application.yml               # Subsidy service configuration (example)
│       └── form_structure.yaml                   # Form metadata (optional, for generators)
├── templates/
│   ├── mapping-hints-template.yaml               # Template for new services
│   └── business-rules-template.yaml              # Template for validation rules
├── generate-config.sh                            # Main configuration generator
├── generate-farmer-config.sh                     # Quick farmer config generator
├── farmer-mapping-hints.yaml                     # Example: farmer registry hints
└── farmer-business-rules.yaml                    # Example: farmer validation rules
```

## Documentation

### Quick References
- **[README-GENERATORS.md](README-GENERATORS.md)** - Configuration generators quick start
- **[SERVICES_YML_GUIDE.md](docs/SERVICES_YML_GUIDE.md)** - services.yml format reference

### Comprehensive Guides
- **[END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md)** - Complete 9-phase walkthrough
- **[GENERATOR_USAGE.md](GENERATOR_USAGE.md)** - Detailed generator usage
- **[CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)** - Overall architecture
- **[GENERATOR_SUMMARY.md](../GENERATOR_SUMMARY.md)** - Benefits & test results

### Joget Plugin Development
- **[docs/](docs/)** - Plugin basics, API reference, best practices

## Configuring a New Service

### Option 1: Quick Start with Generators (15 minutes)

```bash
# 1. Copy templates
cp templates/mapping-hints-template.yaml student-hints.yaml
cp templates/business-rules-template.yaml student-rules.yaml

# 2. Edit hints (10 minutes)
vim student-hints.yaml  # Set service ID, map key fields

# 3. Edit rules (5 minutes)
vim student-rules.yaml  # Define validation logic

# 4. Generate (2 seconds)
./generate-config.sh student-hints.yaml student-rules.yaml

# 5. Deploy
cp services.yml src/main/resources/docs-metadata/
mvn clean package
```

### Option 2: Manual Configuration (3 hours)

See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) for complete manual process.

## Examples

### Working Examples Included
- **Farmer Registry** - 11 forms, 104 fields, grid relationships, conditional validation
  - Configuration: `farmer-mapping-hints.yaml` (120 lines) + `farmer-business-rules.yaml` (17 lines)
  - Generated output: `services-farmer.yml` (378 lines) + `validation-rules-farmer.yaml` (24 lines)

### Additional Examples in Documentation
- **Student Enrollment** - Person entity with enrollment forms
- **Patient Registration** - Healthcare registration with medical history
- **Product Catalog** - Multi-category product management

## Architecture (Multi-Service)

```
Sender Joget Instance                Receiver Joget Instance
┌──────────────────────────┐        ┌──────────────────────────┐
│  Form Submission         │        │   ProcessingServer       │
│         ↓                │        │         ↓                │
│  WorkflowActivator       │        │   Extract serviceId      │
│  (Post-Processing)       │        │   from URL path          │
│  - Sets serviceId        │        │         ↓                │
│  - Starts {serviceId}    │        │   Load {serviceId}.yml   │
│    _submission process   │        │   (e.g., farmers_        │
│         ↓                │        │    registry.yml)         │
│  Process Started         │        │         ↓                │
│  (serviceId in workflow) │  HTTP  │   Map GovStack JSON      │
│         ↓                │  POST  │   to Joget forms         │
│  DocSubmitter (Tool)     │  ────→ │         ↓                │
│  - Reads serviceId from  │  JSON  │   Save to database       │
│    workflow variables    │        │                          │
│  - Loads {serviceId}.yml │        │                          │
│  - Extracts form data    │        │                          │
│  - Transforms to GovStack│        │                          │
│  - Sends to /services/   │        │                          │
│    {serviceId}/          │        │                          │
│    applications          │        │                          │
└──────────────────────────┘        └──────────────────────────┘

ServiceId flows through entire chain:
  WorkflowActivator config → Workflow variable → DocSubmitter → API URL → ProcessingServer

Multiple services use SAME plugins with DIFFERENT {serviceId}.yml files
```

## Multi-Service Deployment (Single JAR Approach)

**Recommended**: Deploy ONE plugin JAR containing MULTIPLE YAML files:

```
doc-submitter-8.1-SNAPSHOT.jar
├── (plugin code - generic)
└── docs-metadata/
    ├── farmers_registry.yml
    ├── subsidy_application.yml
    └── student_enrollment.yml
```

**Deployment Steps**:
1. Add all `{serviceId}.yml` files to `src/main/resources/docs-metadata/`
2. Build: `mvn clean package`
3. Deploy ONE JAR to Joget
4. Create separate workflows for each service:
   - `farmers_registry_submission`
   - `subsidy_application_submission`
   - `student_enrollment_submission`
5. Configure WorkflowActivator on each form with different serviceId

**Benefits**:
- Single plugin to maintain and deploy
- All services share the same codebase
- Easy to add new services (just add YAML + create process)
- No OSGi bundle conflicts

## Testing

```bash
# Compile generators
mvn compile

# Test farmer config generation
./generate-farmer-config.sh

# Compare with existing
diff services-farmer.yml src/main/resources/docs-metadata/services.yml
diff validation-rules-farmer.yaml src/main/resources/docs-metadata/validation-rules.yaml

# Run unit tests
mvn test
```

## Troubleshooting

### "ServiceId not found in workflow variables"
**Cause:** WorkflowActivator not configured or didn't execute
**Solution:**
- Verify form has WorkflowActivator in Post-Processing
- Check WorkflowActivator configuration has serviceId set
- Check WorkflowActivator logs to confirm it executed
- See [CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md#troubleshooting)

### "Metadata file not found: {serviceId}.yml"
**Cause:** YAML file missing or wrong naming
**Solution:**
- Verify file exists: `src/main/resources/docs-metadata/{serviceId}.yml`
- Check file name matches serviceId exactly (e.g., `farmers_registry.yml`)
- Rebuild JAR after adding YAML: `mvn clean package`
- Check JAR contents: `jar tf doc-submitter-8.1-SNAPSHOT.jar | grep yml`

### "ClassNotFoundException: ServicesYamlGenerator"
**Solution:** Compile first: `mvn compile`

### "Failed to load configuration"
**Solution:**
- Check YAML syntax is valid
- Verify `service.id` in YAML matches file name
- Check console logs for specific error messages

### "Field not mapping correctly"
**Solution:**
- Check field name in `{serviceId}.yml` matches Joget form field ID exactly (case-sensitive)
- Enable "Log JSON Payload" in DocSubmitter config to see what's being sent
- Compare field paths with GovStack JSON structure

### HTTP 401 Unauthorized
**Solution:**
- Regenerate API key on receiver
- Update DocSubmitter configuration with new API ID and API Key
- Test with curl to verify credentials

## Version History

- **8.1-SNAPSHOT**: Current development version
  - **Multi-Service Architecture** - Reads serviceId from workflow variables
  - **Convention-Based YAML Loading** - Automatically loads `{serviceId}.yml`
  - **Package Renaming** - `global.govstack.farmreg.registration` → `global.govstack.registration.sender`
  - **Generic Terminology** - Removed all "farmer" references from plugin code
  - **ServiceId Validation** - Validates serviceId from workflow variables
  - **Dynamic Metadata Loading** - YamlMetadataService supports multiple services

## Documentation

- **[CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)** - Complete deployment and configuration guide
- **[README-GENERATORS.md](README-GENERATORS.md)** - Configuration generators quick start
- **[END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md)** - Complete 9-phase walkthrough
- **[GENERATOR_USAGE.md](GENERATOR_USAGE.md)** - Detailed generator usage

## License

Part of the GovStack Registration Building Block initiative.
https://www.govstack.global

---

**Version**: 8.1-SNAPSHOT
**Package**: `global.govstack.registration.sender`
**Last Updated**: October 28, 2025
**Architecture**: Multi-Service Support (Transport-Layer Only)