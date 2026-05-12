# JSON Path Resolution Fix for agriculturalManagementSkills Field

## Problem
The field `agriculturalManagementSkills` with value `college_certificate` exists in the sender database (port 3307) but was arriving as NULL in the receiver database (port 3306).

## Root Cause
There was a path mismatch between DocSubmitter and ProcessingAPI:

1. **DocSubmitter** sends the field at: `extension.agriculturalActivities.managementSkillLevel` (using the `govstack` path from services.yml)
2. **ProcessingAPI** was looking for it at: `extension.agriculturalActivities.agriculturalManagementSkills` (using the `jsonPath` from services.yml)

This mismatch occurred because:
- Test data uses one path structure (`jsonPath`)
- Production data from DocSubmitter uses another path structure (`govstack`)
- The ProcessingAPI only checked one path, not both

## Solution Implemented

### 1. Enhanced Path Resolution in GovStackDataMapperV3
Modified the field extraction logic to try multiple paths:

```java
// Try multiple paths: first jsonPath (test data), then govstackPath (production data)
String extractPath = null;
JsonNode valueNode = null;

// Try jsonPath first (test data format)
if (jsonPath != null) {
    valueNode = JsonPathExtractor.extractNode(dataNode, jsonPath);
    if (valueNode != null && !valueNode.isNull()) {
        extractPath = jsonPath;
    }
}

// If not found, try govstackPath (DocSubmitter format)
if ((valueNode == null || valueNode.isNull()) && govstackPath != null) {
    valueNode = JsonPathExtractor.extractNode(dataNode, govstackPath);
    if (valueNode != null && !valueNode.isNull()) {
        extractPath = govstackPath;
    }
}
```

### 2. Fixed Column Mapping Issue
Removed incorrect column mapping in services.yml:
- Was: `column: "c_agricultural_mgmt_skills"` (this column doesn't exist)
- Now: Uses default `c_agriculturalManagementSkills` (correct column name)

### 3. Enhanced Logging
Added detailed logging in JsonPathExtractor to trace path resolution:
```java
if (path != null && path.contains("agriculturalManagementSkills")) {
    LogUtil.info(CLASS_NAME, "Extracting agriculturalManagementSkills from path: " + path);
}
```

## Files Modified

1. **GovStackDataMapperV3.java**
   - Added dual-path checking (jsonPath and govstackPath)
   - Enhanced logging for field extraction
   - Applied to both regular fields and array fields

2. **services.yml** (ProcessingAPI)
   - Removed incorrect `column: "c_agricultural_mgmt_skills"`
   - Now uses default column naming convention

3. **JsonPathExtractor.java**
   - Added debug logging for agriculturalManagementSkills field
   - Traces path resolution step-by-step

## How It Works Now

1. **DocSubmitter** sends data with field at `extension.agriculturalActivities.managementSkillLevel`
2. **ProcessingAPI** receives the JSON and:
   - First checks `extension.agriculturalActivities.agriculturalManagementSkills` (jsonPath)
   - If not found, checks `extension.agriculturalActivities.managementSkillLevel` (govstack)
   - Uses whichever path contains the value
3. **Value** is extracted and normalized (skipped for masterdata fields)
4. **Data** is saved to database with correct column name `c_agriculturalManagementSkills`

## Testing

To verify the fix:
1. Send a record through DocSubmitter with agriculturalManagementSkills = "college_certificate"
2. Check ProcessingAPI logs for path resolution
3. Verify value arrives in receiver database:

```sql
SELECT id, c_agriculturalManagementSkills
FROM app_fd_farmer_registry
WHERE id = 'your-record-id';
```

## Key Insights

1. **Path Flexibility**: The system now handles both test data format and production data format
2. **Backward Compatibility**: Works with existing test data while supporting DocSubmitter format
3. **Column Naming**: Joget automatically adds `c_` prefix, so we use field names not column names in the code
4. **Masterdata Fields**: These fields are not normalized and pass through unchanged

## Result
The field `agriculturalManagementSkills` with value `college_certificate` now successfully transfers from sender to receiver database.