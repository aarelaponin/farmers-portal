# Selectbox Field Mapping Issue

## Problem
District field (and potentially other selectboxes) are not being populated even though the data is mapped correctly.

## Root Cause
- The district field is a **selectbox** that loads options from a master data form
- Configuration in farmers-01.02.json shows:
  ```json
  "idColumn": "code",
  "labelColumn": "name"
  ```
- This means the selectbox expects the district **code** (not the name)
- Our test data has: `"district": "Maseru"` (the name, not the code)

## Solutions

### Option 1: Update Test Data (Quick Fix)
Change test-data.json to use district codes:
```json
"district": "MSU"  // or whatever the actual code for Maseru is
```

### Option 2: Add Value Lookup (Proper Solution)
Create a lookup mechanism in the mapper that:
1. Detects fields that are selectboxes
2. Queries the master data to find the code for a given name
3. Maps the name to code automatically

### Option 3: Add Value Mapping in YAML
Add a valueMapping section in services.yml:
```yaml
valueMappings:
  districtCodes:
    "Maseru": "MSU"
    "Leribe": "LRB"
    # etc...
```

### Option 4: Store Both Code and Name
Modify the form to accept the name directly or have a text field fallback.

## Affected Fields
Based on the form structure, these fields might have the same issue:
- district (selectbox)
- community_council (selectbox)
- agro_ecological_zone (selectbox)
- Any other fields using FormOptionsBinder

## Temporary Workaround
For testing, you can:
1. Find the actual district codes from the master data table
2. Update test-data.json to use codes instead of names
3. Or manually select the correct values in the form after data import