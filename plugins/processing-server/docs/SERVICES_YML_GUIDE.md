# services.yml Configuration Guide

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [File Structure](#file-structure)
4. [Field Mapping Concepts](#field-mapping-concepts)
5. [Grid and Array Data](#grid-and-array-data)
6. [Transformations](#transformations)
7. [Data Flow Examples](#data-flow-examples)
8. [Developer Guide](#developer-guide)
9. [Reference](#reference)

---

## Overview

The `services.yml` file is the **single source of truth** for mapping data between GovStack API requests, Joget forms, and database tables. It defines how incoming JSON data from the GovStack Registration Building Block API is transformed and stored in multiple Joget forms and their associated database tables.

### Purpose
- **Central Configuration**: All field mappings in one place
- **Type Safety**: Defines data transformations and validations
- **Multi-Form Support**: Maps single API request to multiple forms
- **Grid Support**: Handles parent-child relationships for sub-forms

### Key Benefits
- No hardcoded mappings in Java code
- Easy to modify without recompiling
- Clear documentation of data flow
- Supports complex nested structures

---

## Architecture

### Data Flow Diagram
```
[GovStack API Request (JSON)]
            ↓
    [services.yml mappings]
            ↓
    [GovStackDataMapperV2.java]
            ↓
    [Multiple Joget Forms]
            ↓
    [Database Tables]
```

### Component Relationships
```
┌─────────────────────────────────────────────────────────────┐
│                     GovStack API Request                     │
│                         (JSON Data)                          │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                       services.yml                           │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐  │
│  │ Service Metadata│  │ Form Mappings  │  │Transformations│ │
│  └────────────────┘  └────────────────┘  └──────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   Processing Server Plugin                   │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │ Data Mapper  │→ │Form Manager  │→ │ Table Handler  │   │
│  └──────────────┘  └──────────────┘  └────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                      Joget Platform                          │
│  ┌───────────┐  ┌────────────┐  ┌─────────────────────┐   │
│  │  Form 01  │  │  Form 02   │  │   Grid Sub-forms    │   │
│  └─────┬─────┘  └──────┬─────┘  └──────────┬──────────┘   │
│        ↓                ↓                    ↓              │
│  ┌───────────┐  ┌────────────┐  ┌─────────────────────┐   │
│  │  Table 1  │  │  Table 2   │  │   Grid Tables       │   │
│  └───────────┘  └────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## File Structure

### Top-Level Structure
```yaml
service:            # Service metadata
  id: farmers_registry
  name: "Farmers Registry Service"
  version: "1.0"
  govstackVersion: "1.0"

entities:           # Entity types handled
  primary:
    type: "Person"
    identifierTypes:
      - NationalId
      - FarmerRegistrationNumber
      - BeneficiaryCode

formMappings:       # Form-specific field mappings
  farmerBasicInfo:  # Form section
    ...
  farmerLocation:   # Another form section
    ...
  householdMembers: # Grid/array section
    ...

transformations:    # Data transformation definitions
  date_ISO8601:
    ...
  yesNoBoolean:
    ...
```

### Form Mapping Structure
```yaml
formMappings:
  sectionName:                    # Unique section identifier
    formId: "jogetFormId"         # Joget form definition ID
    tableName: "app_fd_table"     # Database table name
    primaryKey: "c_id"            # Primary key column
    fields:                       # Field mappings array
      - joget: "field_name"       # Joget form field ID
        govstack: "api.path"      # GovStack API path
        jsonPath: "actual.path"   # Optional: override path for test data
        column: "c_column_name"   # Optional: database column name
        required: true            # Optional: field requirement
        transform: "type"         # Optional: transformation type
        valueMapping:             # Optional: value mappings
          "input": "output"
```

### Grid/Array Structure
```yaml
gridName:
  type: "array"                   # Marks this as array/grid data
  formId: "gridFormId"            # Grid's sub-form ID
  tableName: "app_fd_grid_table" # Grid's database table
  primaryKey: "c_id"              # Grid row primary key
  parentKey: "c_farmer_id"        # Parent table foreign key column
  parentField: "farmer_id"        # Actual field name for parent link
  govstack: "api.array.path"      # Path to array in API data
  jogetGrid: "gridFieldName"      # Joget grid field name
  controlField: "hasLivestock"    # Optional: conditional field
  controlValue: "yes"             # Optional: required value
  fields:                         # Grid column mappings
    - joget: "column1"
      govstack: "arrayItem.field1"
```

---

## Field Mapping Concepts

### Basic Field Mapping
Each field mapping connects three layers:
1. **API Layer** (`govstack` or `jsonPath`): Where data comes from
2. **Form Layer** (`joget`): Form field that displays data
3. **Database Layer** (`column` or derived from `joget`): Where data is stored

### Path Resolution

#### govstack Path
The expected path according to GovStack specification:
```yaml
govstack: "name.given[0]"  # Standard FHIR path
govstack: "extension.agriculturalActivities.cropProduction"
```

#### jsonPath Override
When actual data structure differs from specification:
```yaml
jsonPath: "extension.agriculturalActivities.agriculturalManagementSkills"
govstack: "extension.agriculturalActivities.managementSkillLevel"
# Uses jsonPath for extraction, but documents govstack as the standard
```

### Field Properties

| Property | Description | Example |
|----------|-------------|---------|
| `joget` | Form field ID | `"first_name"` |
| `govstack` | GovStack API path | `"name.given[0]"` |
| `jsonPath` | Override extraction path | `"actualPath"` |
| `column` | Database column name | `"c_first_name"` |
| `required` | Field is mandatory | `true` |
| `transform` | Transformation to apply | `"date_ISO8601"` |
| `valueMapping` | Value translations | `{"1": true, "2": false}` |

### Value Mappings
Transform input values to required format:
```yaml
valueMapping:
  # Input → Output
  "male": "male"
  "female": "female"
  "1": true
  "2": false
  "yes": "Y"
  "no": "N"
```

---

## Grid and Array Data

### Understanding Grids
Grids represent one-to-many relationships:
- **Parent Record**: Main form (e.g., farmer registration)
- **Child Records**: Grid rows (e.g., household members, crops)

### Grid Data Flow
```
API Array Data → FormRowSet (multi-row) → Grid Table
     ↓
Each array item → FormRow → Table Row
     ↓
Parent linking via parentField (e.g., farmer_id)
```

### Parent-Child Linking
```yaml
householdMembers:
  type: "array"
  parentField: "farmer_id"   # This field links to parent
  # When saved:
  # - Each row gets unique ID (UUID)
  # - farmer_id field contains parent's ID
  # - Rows are independent but linked
```

### Control Fields
Conditional grids based on parent form values:
```yaml
livestockDetails:
  controlField: "hasLivestock"  # Check this field
  controlValue: "yes"            # Process only if matches
  # Grid only populated when hasLivestock == "yes"
```

### Grid Storage Pattern
```java
// How grids are stored in Joget:
FormRowSet rowSet = new FormRowSet();
rowSet.setMultiRow(true);  // Mark as grid data

// Each row:
FormRow row = new FormRow();
row.setId(UUID);                    // Unique row ID
row.setProperty("farmer_id", parentId);  // Parent link
row.setProperty("memberName", "John");   // Grid data

// Store with null primaryKey (Joget convention)
appService.storeFormData(form, rowSet, null);
```

---

## Transformations

### Built-in Transformations

#### date_ISO8601
Converts date formats:
```yaml
transform: "date_ISO8601"
# Input: "2025-01-22"
# Output: "2025-01-22T00:00:00Z"
```

#### numeric
Ensures numeric values:
```yaml
transform: "numeric"
# Input: "42.5" or 42.5
# Output: 42.5
```

#### yesNoBoolean
Converts yes/no to boolean:
```yaml
transform: "yesNoBoolean"
# Input: "yes" → true
# Input: "no" → false
```

#### multiCheckbox
Handles multiple selections:
```yaml
transform: "multiCheckbox"
# Input: ["option1", "option2"]
# Output: "option1,option2"
```

#### base64
Encodes/decodes base64:
```yaml
transform: "base64"
# Used for signatures, images
```

### Transformation + Value Mapping
Transformations apply first, then value mappings:
```yaml
fields:
  - joget: "livestockProduction"
    govstack: "extension.hasLivestock"
    transform: "yesNoBoolean"  # First: "yes" → true
    valueMapping:
      true: "1"   # Then: true → "1"
      false: "2"  # Then: false → "2"
```

---

## Data Flow Examples

### Example 1: Simple Field
**API Input:**
```json
{
  "name": {
    "given": ["Mamosa"],
    "family": "Motlomelo"
  }
}
```

**services.yml Mapping:**
```yaml
fields:
  - joget: "first_name"
    govstack: "name.given[0]"
  - joget: "last_name"
    govstack: "name.family"
```

**Result in Database:**
```sql
-- Table: app_fd_farmer_basic
c_first_name: "Mamosa"
c_last_name: "Motlomelo"
```

### Example 2: Grid Data
**API Input:**
```json
{
  "extension": {
    "agriculturalData": {
      "crops": [
        {
          "cropType": "1",
          "areaCultivated": "2.5",
          "bagsHarvested": "45"
        }
      ]
    }
  }
}
```

**services.yml Mapping:**
```yaml
cropManagement:
  type: "array"
  parentField: "farmer_id"
  govstack: "extension.agriculturalData.crops"
  fields:
    - joget: "cropType"
      govstack: "cropType"
    - joget: "areaCultivated"
      govstack: "areaCultivated"
```

**Result in Database:**
```sql
-- Table: app_fd_crop_management
c_id: "uuid-123"          -- Unique row ID
c_farmer_id: "farmer-002"  -- Links to parent
c_cropType: "1"
c_areaCultivated: "2.5"
```

### Example 3: Complex Transformation
**API Input:**
```json
{
  "extension": {
    "agriculturalActivities": {
      "engagedInLivestockProduction": "1"
    }
  }
}
```

**services.yml Mapping:**
```yaml
fields:
  - joget: "livestockProduction"
    govstack: "extension.agriculturalActivities.engagedInLivestockProduction"
    valueMapping:
      "1": true
      "2": false
    required: true
```

**Processing Steps:**
1. Extract value: "1"
2. Apply value mapping: "1" → true
3. Store as: true

---

## Developer Guide

### Adding a New Form

1. **Define the form section:**
```yaml
formMappings:
  newFormSection:
    formId: "newFormId"
    tableName: "app_fd_new_table"
    primaryKey: "c_id"
    fields: []
```

2. **Add to SECTION_TO_FORM_MAP in GovStackDataMapperV2.java:**
```java
SECTION_TO_FORM_MAP.put("newFormSection", "newFormId");
```

3. **Add field mappings:**
```yaml
fields:
  - joget: "field1"
    govstack: "api.path.to.field1"
    required: true
```

### Adding a New Grid

1. **Define grid structure:**
```yaml
newGrid:
  type: "array"
  formId: "gridFormId"
  tableName: "app_fd_grid_table"
  parentField: "parent_id"
  govstack: "api.path.to.array"
  fields: []
```

2. **Update TableDataHandler.java:**
```java
case "newGrid":
    return "gridFormId";
```

3. **Add parent field mapping:**
```java
case "newGrid":
    return "parent_id";
```

### Testing Field Mappings

1. **Check extraction path:**
```java
// In test data, verify path exists:
"extension": {
  "agriculturalData": {
    "crops": []  // Path: extension.agriculturalData.crops
  }
}
```

2. **Verify field names:**
- Joget field ID must match form definition
- Database column typically prefixed with `c_`

3. **Test transformations:**
- Ensure input format matches expected
- Check value mappings are complete

### Common Patterns

#### Pattern 1: Optional Override Path
When test data structure differs from spec:
```yaml
- joget: "agriculturalManagementSkills"
  jsonPath: "extension.agriculturalActivities.agriculturalManagementSkills"  # Actual
  govstack: "extension.agriculturalActivities.managementSkillLevel"          # Spec
```

#### Pattern 2: Lookup Table References
For dropdown/select fields:
```yaml
- joget: "district"
  govstack: "address[0].district"
  # References: md03district.csv lookup table
```

#### Pattern 3: Combined Forms
Multiple sections to same form:
```yaml
farmerIncomePrograms: "farmerAgriculture"  # Combined in same form
farmerDeclaration: "farmerAgriculture"     # Combined in same form
```

### Debugging Tips

1. **Enable debug logging:**
```java
LogUtil.info(CLASS_NAME, "Processing field: " + jogetField);
LogUtil.info(CLASS_NAME, "Extracted value: " + value);
```

2. **Check extraction paths:**
```java
if ("problematicField".equals(jogetField)) {
    LogUtil.info(CLASS_NAME, "jsonPath: " + jsonPath);
    LogUtil.info(CLASS_NAME, "govstackPath: " + govstackPath);
    LogUtil.info(CLASS_NAME, "extractPath: " + extractPath);
    LogUtil.info(CLASS_NAME, "Extracted value: " + value);
}
```

3. **Verify grid parent fields:**
```sql
-- Check if parent_id is set correctly
SELECT c_id, c_farmer_id FROM app_fd_household_members;
```

---

## Reference

### File Locations
```
/src/main/resources/
├── docs-metadata/
│   ├── services.yml           # Main configuration
│   ├── validation-rules.yaml  # Validation rules
│   └── test-data.json        # Test data structure
```

### Database Tables
| Form | Table Name | Primary Key | Parent Field |
|------|-----------|-------------|--------------|
| Basic Info | app_fd_farmer_basic | c_farmer_id | - |
| Location | app_fd_farmer_location | c_farmer_id | - |
| Agriculture | app_fd_farmer_agric | c_farmer_id | - |
| Household Members | app_fd_household_members | c_id | c_farmer_id |
| Crops | app_fd_crop_management | c_id | c_farmer_id |
| Livestock | app_fd_livestock_details | c_id | c_farmer_id |

### Java Classes
| Class | Purpose |
|-------|---------|
| `GovStackDataMapperV2` | Maps API data to forms using services.yml |
| `YamlMetadataService` | Loads and parses services.yml |
| `MultiFormSubmissionManager` | Saves to multiple forms |
| `TableDataHandler` | Handles grid/array data |
| `DataTransformer` | Applies transformations |
| `JsonPathExtractor` | Extracts values from JSON |

### Transformation Types
| Type | Input | Output | Usage |
|------|-------|--------|-------|
| `date_ISO8601` | Date string | ISO8601 | Date fields |
| `numeric` | String/Number | Number | Numeric fields |
| `yesNoBoolean` | "yes"/"no" | true/false | Boolean fields |
| `multiCheckbox` | Array | CSV string | Multiple selections |
| `base64` | String | Base64 | Binary data |

### Value Mapping Examples
```yaml
# Boolean mappings
"yes": true
"no": false
"1": true
"2": false

# Gender mappings
"male": "M"
"female": "F"

# Status mappings
"active": "A"
"inactive": "I"
"pending": "P"
```

### Grid Configuration Checklist
- [ ] Define `type: "array"`
- [ ] Set `formId` to sub-form ID
- [ ] Set `tableName` to grid table
- [ ] Define `parentField` for linking
- [ ] Set `govstack` path to array location
- [ ] Map all grid columns in `fields`
- [ ] Add to `getGridFormId()` switch
- [ ] Add to `getParentFieldName()` switch
- [ ] Test with multi-row data

---

## Conclusion

The `services.yml` file serves as the central nervous system of the data mapping infrastructure, connecting GovStack API specifications with Joget's form and database architecture. By maintaining all mappings in a single, well-structured configuration file, the system achieves:

- **Maintainability**: Changes don't require code compilation
- **Clarity**: Data flow is explicitly documented
- **Flexibility**: Supports complex transformations and relationships
- **Scalability**: Easy to add new forms and fields

For questions or contributions, refer to the main project documentation or contact the development team.