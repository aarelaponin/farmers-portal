# Testing Guide for Multi-Format LOV Normalization

## Overview
This guide provides step-by-step instructions to test the new multi-format normalization system that handles both legacy string formats (from test-data.json) and new boolean formats (from DocSubmitter).

## 1. Unit Tests (Already Passing)

### Run all normalization unit tests
```bash
cd /Users/aarelaponin/IdeaProjects/plugins/processing-server
mvn test -Dtest=ValueFormatDetectorTest,ValueNormalizerTest
```

Expected: 52 tests should pass (31 + 21)

### Run backward compatibility tests
```bash
mvn test -Dtest=BackwardCompatibilityTest
```

Expected: 3 tests pass, 1 skipped

### Run DocSubmitter format tests
```bash
mvn test -Dtest=DocSubmitterFormatTest
```

Expected: 5 tests pass

## 2. Integration Testing with ProcessingAPI

### Step 1: Build and Deploy ProcessingAPI
```bash
cd /Users/aarelaponin/IdeaProjects/plugins/processing-server
mvn clean package
# Deploy the JAR to your Joget installation
```

### Step 2: Test with Original test-data.json
```bash
# Start ProcessingAPI if not running
# Send the original test-data.json
curl -X POST http://localhost:8080/jw/web/json/plugin/global.govstack.processing.ProcessingAPI/service \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json
```

Check the database to verify fields are stored correctly:
```sql
-- Check if LOV values are preserved
SELECT
  c_livestockProduction,  -- Should be "1" or "2"
  c_cropProduction,       -- Should be "yes" or "no"
  c_canReadWrite,        -- Should be "yes" or "no"
  c_participatesInAgriculture  -- Should be "1" or "2"
FROM app_fd_farmer_crop_livestck
WHERE id = 'farmer-002';
```

### Step 3: Create Test Data with Boolean Values
Create a file `test-boolean-format.json`:

```json
{
  "testData": [{
    "id": "test-bool-001",
    "extension": {
      "agriculturalActivities": {
        "engagedInLivestockProduction": false,
        "engagedInCropProduction": true,
        "canReadWrite": true,
        "fertilizerApplied": false,
        "pesticidesApplied": true
      },
      "cooperativeMember": false
    },
    "relatedPerson": [{
      "extension": {
        "participatesInAgriculture": true,
        "chronicallyIll": false
      }
    }]
  }]
}
```

Send it to ProcessingAPI:
```bash
curl -X POST http://localhost:8080/jw/web/json/plugin/global.govstack.processing.ProcessingAPI/service \
  -H "Content-Type: application/json" \
  -d @test-boolean-format.json
```

Verify in database:
```sql
-- Boolean false should become "2" for livestockProduction
SELECT
  c_livestockProduction  -- Should be "2" (from false)
FROM app_fd_farmer_crop_livestck
WHERE id = 'test-bool-001';
```

## 3. End-to-End Testing with DocSubmitter

### Step 1: Build and Deploy DocSubmitter
```bash
cd /Users/aarelaponin/IdeaProjects/plugins/doc-submitter
mvn clean package
# Deploy to Joget
```

### Step 2: Test Complete Flow
1. Create a new farmer registration in Joget
2. Fill form with Yes/No selections
3. Submit the form
4. Check DocSubmitter logs for the JSON being sent
5. Check ProcessingAPI logs for received and normalized values
6. Verify database has correct LOV values

### Step 3: Verify Field Mappings

Test these specific fields and their expected transformations:

| Field | Input (DocSubmitter) | Expected in DB | Notes |
|-------|---------------------|----------------|-------|
| livestockProduction | true | "1" | Uses 1/2 LOV |
| livestockProduction | false | "2" | Uses 1/2 LOV |
| cropProduction | true | "yes" | Uses yes/no LOV |
| cropProduction | false | "no" | Uses yes/no LOV |
| participatesInAgriculture | true | "1" | Uses 1/2 LOV |
| chronicallyIll | false | "2" | Uses 1/2 LOV |
| canReadWrite | true | "yes" | Uses yes/no LOV |
| cooperativeMember | false | "no" | Uses yes/no LOV |

## 4. Database Validation Queries

### Check all farmers with their LOV values
```sql
-- Main agriculture fields
SELECT
  f.id as farmer_id,
  fc.c_livestockProduction,
  fc.c_cropProduction,
  fc.c_canReadWrite,
  fc.c_fertilizerApplied,
  fc.c_pesticidesApplied
FROM app_fd_farms_registry f
LEFT JOIN app_fd_farmer_crop_livestck fc ON f.c_crops_livestock = fc.id
WHERE f.id IN ('farmer-002', 'test-bool-001');

-- Household members
SELECT
  hm.c_farmer_id,
  hm.c_participatesInAgriculture,
  hm.c_chronicallyIll
FROM app_fd_household_members hm
WHERE hm.c_farmer_id IN ('farmer-002', 'test-bool-001');
```

### Validate No Data Loss
```sql
-- Count distinct values for each LOV field
SELECT
  'livestockProduction' as field,
  c_livestockProduction as value,
  COUNT(*) as count
FROM app_fd_farmer_crop_livestck
GROUP BY c_livestockProduction

UNION ALL

SELECT
  'cropProduction' as field,
  c_cropProduction as value,
  COUNT(*) as count
FROM app_fd_farmer_crop_livestck
GROUP BY c_cropProduction;
```

## 5. Mixed Format Testing

Create `test-mixed-format.json` with both formats:

```json
{
  "testData": [{
    "id": "test-mixed-001",
    "extension": {
      "agriculturalActivities": {
        "engagedInLivestockProduction": "1",     // String format
        "engagedInCropProduction": true,          // Boolean format
        "canReadWrite": "yes",                    // String format
        "fertilizerApplied": false,               // Boolean format
        "mainSourceFarmLabour": "Seasonally Hired" // Custom mapping
      }
    }
  }]
}
```

Expected results in database:
- livestockProduction: "1" (preserved)
- cropProduction: "yes" (from true)
- canReadWrite: "yes" (preserved)
- fertilizerApplied: "no" (from false)
- mainSourceFarmLabour: "2" (mapped from "Seasonally Hired")

## 6. Regression Testing

### Ensure existing functionality still works:
1. Run all existing ProcessingAPI tests:
   ```bash
   cd /Users/aarelaponin/IdeaProjects/plugins/processing-server
   mvn test
   ```

2. Test with production data (if available)
3. Verify all form sections are still extracted correctly
4. Check that array/grid data still works

## 7. Performance Testing

Monitor for any performance impact:

```bash
# Time the processing of test-data.json
time curl -X POST http://localhost:8080/jw/web/json/plugin/global.govstack.processing.ProcessingAPI/service \
  -H "Content-Type: application/json" \
  -d @src/main/resources/docs-metadata/test-data.json
```

Compare timing before and after the change.

## 8. Error Scenarios

Test edge cases:

### Null values
```json
{
  "testData": [{
    "extension": {
      "agriculturalActivities": {
        "engagedInLivestockProduction": null,
        "engagedInCropProduction": null
      }
    }
  }]
}
```

### Invalid values
```json
{
  "testData": [{
    "extension": {
      "agriculturalActivities": {
        "engagedInLivestockProduction": "maybe",
        "engagedInCropProduction": 123
      }
    }
  }]
}
```

## 9. Logging and Monitoring

Enable debug logging to trace normalization:

```java
// In logback.xml or log4j.properties
<logger name="global.govstack.processing.service.normalization" level="DEBUG"/>
```

Look for log entries like:
```
Normalized field 'livestockProduction' from BOOLEAN to LOV value: 2
Field livestockProduction converted from BOOLEAN false to '2'
```

## 10. Rollback Plan

If issues are found:

1. Keep the original ProcessingAPI JAR as backup
2. Can disable normalization by removing ValueNormalizer usage in GovStackDataMapperV3
3. Database values are preserved - no schema changes required

## Success Criteria

✅ All unit tests pass (52+ tests)
✅ Original test-data.json processes without errors
✅ Boolean values from DocSubmitter are correctly normalized
✅ Mixed format documents are handled correctly
✅ No performance degradation
✅ All existing functionality continues to work
✅ Database contains correct LOV values for all formats

## Troubleshooting

### If values are not normalized correctly:

1. Check field configuration in ValueNormalizer.initializeDefaultConfigs()
2. Verify field name matches exactly in services.yml
3. Enable debug logging to see normalization decisions
4. Check if custom mappings are needed for the field

### If tests fail:

1. Run individual test classes to isolate issues
2. Check test data files are in correct locations
3. Verify database schema matches expectations
4. Review stack traces for specific normalization errors

## Contact for Issues

If you encounter issues during testing:
1. Check the logs in both DocSubmitter and ProcessingAPI
2. Verify the field names match between systems
3. Ensure services.yml is properly configured
4. Run the validation SQL script to check database state