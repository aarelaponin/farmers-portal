# GovStack Registration BB Implementation

## Overview
This plugin implements the GovStack Registration Building Block API specification for processing farmer registration data. It maps generic GovStack JSON format to Joget form fields based on YAML metadata configuration.

## Features
- **Service Validation**: Validates incoming service ID against configured service
- **Complete Data Mapping**: Maps all fields from GovStack JSON to Joget forms (all 7 tabs)
- **Array/Grid Support**: Handles household members and other sub-table data
- **Field Transformations**: Date formatting, boolean conversions, value mappings
- **YAML-based Configuration**: Flexible metadata-driven mapping

## Configuration

### Plugin Parameters
1. **Use GovStack Mode**: Enable the new GovStack-compliant processing
2. **Service ID**: Must match the service ID in `docs-metadata/services.yml` (e.g., `farmers_registry`)
3. **Main Form ID**: The Joget form ID for the main registration form

### Metadata Files
- `docs-metadata/services.yml` - Service configuration and field mappings
- `docs-metadata/test-data.json` - Sample GovStack JSON for testing

## API Endpoint
```
POST /services/{serviceId}/applications
```

### Request Format (GovStack JSON)
```json
{
  "resourceType": "Person",
  "identifiers": [...],
  "name": {
    "given": ["FirstName"],
    "family": "LastName"
  },
  "gender": "male",
  "birthDate": "1994-05-15",
  "address": [...],
  "telecom": [...],
  "relatedPerson": [...],
  "extension": {
    "farmLocation": {...},
    "farmDetails": {...},
    "agriculturalActivities": {...}
  }
}
```

### Response Format
```json
{
  "success": true,
  "applicationId": "uuid-here",
  "status": "submitted",
  "timestamp": 1234567890,
  "service": {
    "serviceId": "farmers_registry",
    "serviceName": "Farmers Registry Service"
  }
}
```

## Components

### Core Services
- **YamlMetadataService**: Loads and parses YAML metadata
- **ServiceValidator**: Validates service ID
- **DataTransformer**: Handles field transformations
- **GovStackDataMapper**: Maps JSON to form fields
- **TableDataHandler**: Saves sub-table/grid data
- **GovStackRegistrationService**: Main orchestrator

### Data Flow
1. Request arrives at `/services/farmers_registry/applications`
2. Service ID validated against configuration
3. YAML metadata loaded for field mappings
4. GovStack JSON parsed and mapped to Joget fields
5. Main form data saved first
6. Sub-table data (household members) saved with parent reference
7. Success response returned with application ID

## Testing
Run the test utility to verify mapping:
```bash
mvn test -Dtest=TestGovStackMapping
```

Or run the main method in `TestGovStackMapping.java` directly.

## Field Mappings
The YAML configuration maps GovStack paths to Joget fields:
- Simple fields: `name.given[0]` → `first_name`
- Nested paths: `extension.farmLocation.latitude` → `gpsLatitude`
- Arrays: `relatedPerson[]` → household members grid
- Value mappings: `"male"` → `"male"`, `"1"` → `"rural"`
- Transformations: ISO dates → Joget format, booleans → yes/no

## Limitations (Initial Implementation)
- No workflow triggering (data storage only)
- No document handling
- No GET/PUT/DELETE operations
- No duplicate checking
- Synchronous processing only

## Future Enhancements
- Phase 2: Workflow integration
- Phase 3: Document handling
- Phase 4: Full CRUD operations
- Phase 5: Multiple service support