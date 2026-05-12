# GovStack Registration Receiver Plugin (ProcessingServer)

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.registration.receiver`
**Architecture:** Multi-Service Support

A Joget DX8 plugin for GovStack Registration Building Block that receives GovStack JSON data via HTTP API, maps it to Joget forms, and saves it to the database. This plugin is a **transport layer** that handles data transformation between GovStack and Joget formats.

## Overview

This plugin is the **receiver component** in a three-part architecture:
- **WorkflowActivator** (sender config) - Sets serviceId (single configuration point)
- **DocSubmitter** (sender) - Extracts Joget form data, transforms to GovStack JSON, sends to Processing API
- **ProcessingServer** (this plugin) - Receives GovStack JSON, maps to Joget forms, saves to database

Together they enable data exchange between Joget instances using GovStack Registration Building Block standards.

**Key Feature**: Extracts serviceId from URL path (`/services/{serviceId}/applications`), enabling **one plugin to serve multiple services**.

**Design Philosophy**: These plugins are **transport-layer only**. All business validation is done in Joget forms using native validation tools. The plugins validate only structure/metadata compliance, not business data.

## Features

- **Multi-Service Architecture** - One plugin serves multiple services (farmers, students, subsidies, etc.)
- **ServiceId from URL Path** - Extracts serviceId from `/services/{serviceId}/applications` endpoint
- **Convention-Based YAML Loading** - Automatically loads `{serviceId}.yml` configuration
- **HTTP API Endpoint** - `/services/{serviceId}/applications` for receiving data
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
mvn clean package -Dmaven.test.skip=true
```

The compiled plugin will be available at `target/processing-server-8.1-SNAPSHOT.jar`

### 2. Deploy to Joget

**Option A: Hot Deploy (Recommended)**
1. Upload the JAR file through Joget's Manage Plugins interface (Settings → Manage Plugins)
2. The plugin will be hot-deployed without server restart

**Option B: Manual Deploy**
1. Copy `target/processing-server-8.1-SNAPSHOT.jar` to Joget's `wflow/app_plugins/`
2. Restart Joget server

### 3. Test the API

Submit test data via API:
```bash
curl -X POST http://localhost:8080/jw/api/services/farmers_registry/applications \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json
```

Expected response:
```json
{
  "status": "success",
  "recordId": "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78",
  "message": "Application processed successfully"
}
```

**For complete configuration, see [CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)**

## API Endpoints

### POST `/jw/api/services/{serviceId}/applications`

Receives GovStack JSON data and saves to Joget forms.

**ServiceId is extracted from URL path** - enables multi-service support with one plugin.

**Path Parameters:**
- `serviceId` - Service identifier from services.yml (e.g., `farmers_registry`, `student_enrollment`)

**Request Headers:**
- `Content-Type: application/json`

**Request Body:**
```json
{
  "resourceType": "Person",
  "identifiers": [{"value": "123456789"}],
  "name": {"given": ["John"], "family": "Doe"},
  "birthDate": "1990-01-15",
  "extension": {
    "agriculture": {
      "cropProduction": "yes"
    }
  }
}
```

**Response:**
- `200 OK` - Data processed successfully
- `400 Bad Request` - Validation failed
- `500 Internal Server Error` - Processing error

## Configuration

### Multi-Service Architecture

**Key Concept**: One plugin JAR contains multiple YAML configuration files:
- `farmers_registry.yml` - Configuration for farmers service
- `subsidy_application.yml` - Configuration for subsidy service
- `student_enrollment.yml` - Configuration for student service

The plugin automatically loads the correct YAML based on `serviceId` extracted from the URL path.

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
    primaryKey: c_farmer_id
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

From the doc-submitter directory:
```bash
cd ../doc-submitter

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

See [doc-submitter/README-GENERATORS.md](../doc-submitter/README-GENERATORS.md) for details

## Project Structure

```
processing-server/
├── src/main/java/global/govstack/registration/receiver/
│   ├── lib/RegistrationServiceProvider.java      # HTTP API endpoint handler
│   ├── service/
│   │   ├── GovStackRegistrationService.java      # Maps GovStack JSON to Joget forms
│   │   └── metadata/
│   │       ├── YamlMetadataService.java          # Loads {serviceId}.yml dynamically
│   │       └── TableDataHandler.java             # Handles table operations
│   └── exception/                                # Custom exceptions
├── src/main/resources/docs-metadata/
│   ├── farmers_registry.yml                       # Farmers service configuration
│   ├── subsidy_application.yml                    # Subsidy service configuration (example)
│   └── test-data.json                             # Test data
├── doc-forms/                                     # Joget form definitions (JSON)
├── docs/                                          # Documentation
│   ├── INDEX.md                                   # Documentation index
│   ├── GENERIC_CONFIGURATION.md                   # Multi-service configuration
│   └── SERVICES_YML_GUIDE.md                      # services.yml reference
├── ARCHITECTURE.md                                # Transport-layer architecture explanation
├── TODO.md                                        # Future improvements
├── CONFIGURATION_SYNC.md                          # Configuration synchronization guide
└── target/processing-server-8.1-SNAPSHOT.jar      # Built plugin
```

## Documentation

### Quick References
- **[Services YAML Guide](docs/SERVICES_YML_GUIDE.md)** - services.yml format reference
- **[Generic Configuration](docs/GENERIC_CONFIGURATION.md)** - Configure for ANY service type

### Comprehensive Guides (in doc-submitter)
- **[END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md)** - Complete 9-phase walkthrough
- **[CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)** - Overall architecture
- **[GENERATOR_USAGE.md](../doc-submitter/GENERATOR_USAGE.md)** - Configuration generators detailed guide

### Technical Documentation
- **[Documentation Index](docs/INDEX.md)** - Complete documentation index
- **[Field Mapping Fixes](docs/FIELD_MAPPING_FIXES.md)** - Summary of fixes applied
- **[Complete Fix Documentation](docs/FIXES_DOCUMENTATION.md)** - Detailed technical changes
- **[Validation Tool](docs/VALIDATION_TOOL.md)** - Data validation framework

## Architecture (Multi-Service)

```
Sender Joget Instance                Receiver Joget Instance
┌──────────────────────────┐        ┌──────────────────────────┐
│  Form Submission         │        │   ProcessingServer       │
│         ↓                │        │   (this plugin)          │
│  WorkflowActivator       │        │         ↓                │
│  - Sets serviceId        │  HTTP  │   Extract serviceId      │
│         ↓                │  POST  │   from URL path:         │
│  Process Started         │  ────→ │   /services/{serviceId}  │
│  (serviceId in workflow) │  JSON  │   /applications          │
│         ↓                │        │         ↓                │
│  DocSubmitter            │        │   Load {serviceId}.yml   │
│  - Reads serviceId from  │        │   (e.g., farmers_        │
│    workflow variables    │        │    registry.yml)         │
│  - Loads {serviceId}.yml │        │         ↓                │
│  - Sends to /services/   │        │   Map GovStack JSON      │
│    {serviceId}/          │        │   to Joget forms         │
│    applications          │        │         ↓                │
│                          │        │   Save to database       │
│                          │        │   (app_fd_* tables)      │
└──────────────────────────┘        └──────────────────────────┘

ServiceId flows: WorkflowActivator → Workflow var → DocSubmitter → URL path → ProcessingServer

Multiple services use SAME plugins with DIFFERENT {serviceId}.yml files
```

## Form Mappings

All field mappings are configured in `src/main/resources/docs-metadata/{serviceId}.yml`

### Example: Farmer Registry Form Sections (farmers_registry.yml)
- `farmerBasicInfo` - Basic farmer information (national_id, name, gender, etc.)
- `farmerLocation` - Location and farm details (district, village, GPS coordinates)
- `farmerAgriculture` - Agricultural activities (crop production, literacy, skills)
- `farmerCropsLivestock` - Crops and livestock data (hasLivestock flag)
- `farmerHousehold` - Household members (grid/subform)
- `farmerIncomePrograms` - Income and programs (income sources, support programs)
- `farmerDeclaration` - Declaration and consent (signature, consent checkboxes)
- `cropManagement` - Crop details grid (cropType, area, harvest)
- `livestockDetails` - Livestock details grid (type, male/female counts)

### Database Tables

The plugin creates/updates these tables (for farmer registry example):
- `app_fd_farmer_basic_data` - Basic information
- `app_fd_farm_location` - Location and farm details
- `app_fd_farmer_registry` - Registry metadata
- `app_fd_farmer_activity` - Agricultural activities
- `app_fd_farmer_crop_livestck` - Crops/livestock flags
- `app_fd_farmer_income` - Income and programs
- `app_fd_farmer_declaration` - Declaration and consent
- `app_fd_household_members` - Household members (grid)
- `app_fd_crop_management` - Crop details (grid)
- `app_fd_livestock_details` - Livestock details (grid)

## Configuring a New Service

### Option 1: Quick Start with Generators (10 minutes)

From the doc-submitter directory:
```bash
cd ../doc-submitter

# 1. Copy templates
cp templates/mapping-hints-template.yaml patient-hints.yaml

# 2. Edit hints (10 minutes)
vim patient-hints.yaml  # Set service ID, map key fields

# 3. Generate (2 seconds)
./generate-config.sh patient-hints.yaml

# 4. Deploy to processing-server
cp services.yml ../processing-server/src/main/resources/docs-metadata/
```

**Note**: Business validation rules should be configured in Joget forms using native validation tools, not in configuration files.

### Option 2: Manual Configuration (3 hours)

See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) for complete manual process.

## Multi-Service Deployment (Single JAR Approach)

**Recommended**: Deploy ONE plugin JAR containing MULTIPLE YAML files:

```
processing-server-8.1-SNAPSHOT.jar
├── (plugin code - generic)
└── docs-metadata/
    ├── farmers_registry.yml
    ├── subsidy_application.yml
    └── student_enrollment.yml
```

**Deployment Steps**:
1. Add all `{serviceId}.yml` files to `src/main/resources/docs-metadata/`
2. Build: `mvn clean package -Dmaven.test.skip=true`
3. Deploy ONE JAR to Joget
4. Plugin automatically creates API endpoints for each service:
   - `/api/services/farmers_registry/applications`
   - `/api/services/subsidy_application/applications`
   - `/api/services/student_enrollment/applications`

**Benefits**:
- Single plugin to maintain and deploy
- All services share the same codebase
- Easy to add new services (just add YAML file and rebuild)
- No OSGi bundle conflicts

**For complete configuration, see [CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)**

## Validation Approach

**This plugin does NOT perform business validation**. It only validates:
- ✅ JSON structure is parseable
- ✅ Metadata version compatibility (sender/receiver configuration sync)
- ✅ Configuration matches database schema (at startup)

**All functional/business validation is done in Joget forms** using native validation tools:
- Required fields → Configure in form designer
- Conditional requirements → Use Joget visibility/validation rules
- Data format → Use Joget field validators
- Business logic → Use Joget form validators

This separation ensures the plugin remains generic and service-agnostic.

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Testing
```bash
# 1. Build and deploy plugin
mvn clean package -Dmaven.test.skip=true

# 2. Upload to Joget via UI or copy to app_plugins/

# 3. Configure plugin with GovStack Mode enabled

# 4. Generate API key in Joget (Settings → API Management → API Keys)

# 5. Submit test data
curl -X POST http://localhost:8080/jw/api/services/farmers_registry/applications \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json

# 6. Check database
mysql -h localhost -P 3306 -u root -p jwdb \
  -e "SELECT * FROM app_fd_farmer_basic_data ORDER BY dateModified DESC LIMIT 1;"
```

### End-to-End Testing with DocSubmitter

1. **Deploy both plugins**: DocSubmitter (sender) and ProcessingAPI (receiver)
2. **Configure sender**: Point DocSubmitter to receiver's API endpoint
3. **Submit form in sender Joget instance**
4. **Verify data arrives in receiver database**

See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) Phase 8 for complete testing procedures.

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| **"Service not found"** | Check `service.id` in `{serviceId}.yml` matches URL path parameter exactly |
| **"Metadata file not found"** | Verify `{serviceId}.yml` exists in JAR: `jar tf processing-server-8.1-SNAPSHOT.jar \| grep yml` |
| **API returns 404** | Verify URL format: `/jw/api/services/{serviceId}/applications` (not `/govstack/v2/`) |
| **Mandatory fields empty** | Check field paths in `{serviceId}.yml` match GovStack JSON structure exactly |
| **Grid data not showing** | Verify `parentField`/`parentColumn` in `{serviceId}.yml` (usually matches primary key) |
| **Field not mapping** | Check field name is exact match (case-sensitive) with Joget form field ID |
| **Boolean field wrong** | Add field to `fieldNormalization.yesNo` or `oneTwo` in `{serviceId}.yml` |
| **Date format error** | Verify GovStack sends ISO8601 format: `2025-01-15T00:00:00Z` |
| **Validation errors** | Check Joget form validators - all business validation is in forms, not plugin |
| **HTTP 401 Unauthorized** | Verify API key is configured correctly in sender's DocSubmitter |

**For detailed troubleshooting, see [CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md#troubleshooting)**

### Debug Logging

Check Joget logs for detailed error messages:
```bash
# Joget console log
tail -f logs/joget.log | grep -E "(RegistrationServiceProvider|serviceId)"
```

Expected logs:
```
INFO - Processing request for serviceId: farmers_registry
INFO - Using GovStackRegistrationService for serviceId from URL: farmers_registry
INFO - Loading metadata from classpath: docs-metadata/farmers_registry.yml
INFO - Successfully loaded metadata for service: farmers_registry
INFO - Processing GovStack JSON request
INFO - Successfully processed request
```

### Enable Detailed Logging

Add to Joget's log4j configuration for more details:
```properties
log4j.logger.global.govstack.registration.receiver=DEBUG
```

## Development

### Requirements
- Java 8 or higher
- Maven 3.6+
- Joget DX8 Platform

### Build & Deploy Cycle

```bash
# 1. Make code changes
vim src/main/java/global/govstack/processing/...

# 2. Build
mvn clean package -Dmaven.test.skip=true

# 3. Hot deploy via Joget UI (no restart needed)
# Upload target/processing-server-8.1-SNAPSHOT.jar

# 4. Test
curl -X POST http://localhost:8080/jw/api/govstack/v2/farmers_registry/applications \
  -H "Content-Type: application/json" \
  -d @test-data.json
```

### Making Configuration Changes

```bash
# 1. Edit configuration
vim src/main/resources/docs-metadata/services.yml

# 2. Rebuild (configuration is embedded in JAR)
mvn clean package -Dmaven.test.skip=true

# 3. Redeploy
# Upload new JAR via Joget UI
```

## Examples

### Working Example: Farmer Registry
- **11 forms**, 104 fields, grid relationships
- Configuration: `farmers_registry.yml` (embedded in JAR)
- Test data: `test-data.json` (complete farmer registration)
- Validation: Configured in Joget forms using native validators

### Additional Examples in Documentation
See [END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md) for:
- **Student Enrollment** - Person entity with enrollment forms
- **Patient Registration** - Healthcare registration with medical history
- **Product Catalog** - Multi-category product management

## Version History

- **8.1-SNAPSHOT**: Current development version
  - **Multi-Service Architecture** - Extracts serviceId from URL path parameter
  - **Convention-Based YAML Loading** - Automatically loads `{serviceId}.yml`
  - **API Endpoint Change** - `/govstack/v2/{serviceId}/` → `/services/{serviceId}/`
  - **Package Renaming** - `global.govstack.processing` → `global.govstack.registration.receiver`
  - **Generic Service Support** - Works for ANY service type (farmers, students, subsidies, etc.)
  - **Dynamic Metadata Loading** - YamlMetadataService supports multiple services
  - **Transport Layer Only** - Removed business validation (now in Joget forms)
  - Configuration generators added (92% time savings)
  - Major field mapping fixes (200+ corrections)

## Documentation

- **[CONFIGURATION_GUIDE.md](../CONFIGURATION_GUIDE.md)** - Complete deployment and configuration guide
- **[GENERIC_CONFIGURATION.md](docs/GENERIC_CONFIGURATION.md)** - Multi-service configuration
- **[END_TO_END_SERVICE_CONFIGURATION.md](../END_TO_END_SERVICE_CONFIGURATION.md)** - Complete walkthrough
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Transport-layer design philosophy
- **[Documentation Index](docs/INDEX.md)** - Complete documentation index

## License

Part of the GovStack Registration Building Block initiative.
https://www.govstack.global

---

**Version**: 8.1-SNAPSHOT
**Package**: `global.govstack.registration.receiver`
**Last Updated**: October 28, 2025
**Architecture**: Multi-Service Support (Transport-Layer Only)