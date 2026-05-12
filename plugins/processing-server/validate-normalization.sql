-- ============================================
-- VALIDATION SCRIPT FOR LOV NORMALIZATION
-- Purpose: Verify that all LOV values are correctly normalized
-- ============================================

USE jwdb;

-- ============================================
-- SECTION 1: Check LOV Values Distribution
-- ============================================

SELECT '=== LOV VALUES DISTRIBUTION ===' as Status;

-- Check livestockProduction values (should be "1" or "2")
SELECT
    'livestockProduction' as field_name,
    c_livestockProduction as value,
    COUNT(*) as count,
    CASE
        WHEN c_livestockProduction IN ('1', '2') THEN '✓ Valid LOV'
        WHEN c_livestockProduction IN ('true', 'false') THEN '✗ UNNORMALIZED BOOLEAN'
        WHEN c_livestockProduction IN ('yes', 'no') THEN '✗ WRONG LOV TYPE'
        ELSE '✗ UNEXPECTED VALUE'
    END as status
FROM app_fd_farmer_crop_livestck
WHERE c_livestockProduction IS NOT NULL
GROUP BY c_livestockProduction;

-- Check cropProduction values (should be "yes" or "no")
SELECT
    'cropProduction' as field_name,
    c_cropProduction as value,
    COUNT(*) as count,
    CASE
        WHEN c_cropProduction IN ('yes', 'no') THEN '✓ Valid LOV'
        WHEN c_cropProduction IN ('true', 'false') THEN '✗ UNNORMALIZED BOOLEAN'
        WHEN c_cropProduction IN ('1', '2') THEN '✗ WRONG LOV TYPE'
        ELSE '✗ UNEXPECTED VALUE'
    END as status
FROM app_fd_farmer_crop_livestck
WHERE c_cropProduction IS NOT NULL
GROUP BY c_cropProduction;

-- Check participatesInAgriculture values (should be "1" or "2")
SELECT
    'participatesInAgriculture' as field_name,
    c_participatesInAgriculture as value,
    COUNT(*) as count,
    CASE
        WHEN c_participatesInAgriculture IN ('1', '2') THEN '✓ Valid LOV'
        WHEN c_participatesInAgriculture IN ('true', 'false') THEN '✗ UNNORMALIZED BOOLEAN'
        WHEN c_participatesInAgriculture IN ('yes', 'no') THEN '✗ WRONG LOV TYPE'
        ELSE '✗ UNEXPECTED VALUE'
    END as status
FROM app_fd_household_members
WHERE c_participatesInAgriculture IS NOT NULL
GROUP BY c_participatesInAgriculture;

-- Check chronicallyIll values (should be "1" or "2")
SELECT
    'chronicallyIll' as field_name,
    c_chronicallyIll as value,
    COUNT(*) as count,
    CASE
        WHEN c_chronicallyIll IN ('1', '2') THEN '✓ Valid LOV'
        WHEN c_chronicallyIll IN ('true', 'false') THEN '✗ UNNORMALIZED BOOLEAN'
        WHEN c_chronicallyIll IN ('yes', 'no') THEN '✗ WRONG LOV TYPE'
        ELSE '✗ UNEXPECTED VALUE'
    END as status
FROM app_fd_household_members
WHERE c_chronicallyIll IS NOT NULL
GROUP BY c_chronicallyIll;

-- ============================================
-- SECTION 2: Test Specific Records
-- ============================================

SELECT '=== TEST RECORD VALIDATION ===' as Status;

-- Check original test-data.json record (farmer-002)
SELECT
    'farmer-002 (original)' as test_case,
    fc.c_livestockProduction,
    fc.c_cropProduction,
    fc.c_canReadWrite,
    fc.c_fertilizerApplied,
    fc.c_pesticidesApplied,
    CASE
        WHEN fc.c_livestockProduction = '1'
         AND fc.c_cropProduction = 'yes'
        THEN '✓ PASS - Original format preserved'
        ELSE '✗ FAIL - Values changed'
    END as result
FROM app_fd_farms_registry f
LEFT JOIN app_fd_farmer_crop_livestck fc ON f.c_crops_livestock = fc.id
WHERE f.id = 'farmer-002';

-- Check boolean test record (test-bool-001)
SELECT
    'test-bool-001 (boolean)' as test_case,
    fc.c_livestockProduction,
    fc.c_cropProduction,
    fc.c_canReadWrite,
    fc.c_fertilizerApplied,
    fc.c_pesticidesApplied,
    CASE
        WHEN fc.c_livestockProduction = '2'  -- false -> "2"
         AND fc.c_cropProduction = 'yes'      -- true -> "yes"
         AND fc.c_canReadWrite = 'yes'        -- true -> "yes"
         AND fc.c_fertilizerApplied = 'no'    -- false -> "no"
         AND fc.c_pesticidesApplied = 'yes'   -- true -> "yes"
        THEN '✓ PASS - Booleans normalized correctly'
        ELSE '✗ FAIL - Boolean normalization failed'
    END as result
FROM app_fd_farms_registry f
LEFT JOIN app_fd_farmer_crop_livestck fc ON f.c_crops_livestock = fc.id
WHERE f.id = 'test-bool-001';

-- Check mixed format record (test-mixed-001)
SELECT
    'test-mixed-001 (mixed)' as test_case,
    fc.c_livestockProduction,
    fc.c_cropProduction,
    fc.c_canReadWrite,
    fc.c_mainSourceFarmLabour,
    fc.c_fertilizerApplied,
    fc.c_pesticidesApplied,
    CASE
        WHEN fc.c_livestockProduction = '1'   -- "1" -> "1" (preserved)
         AND fc.c_cropProduction = 'yes'      -- true -> "yes"
         AND fc.c_canReadWrite = 'yes'        -- "yes" -> "yes" (preserved)
         AND fc.c_mainSourceFarmLabour = '2'  -- "Seasonally Hired" -> "2"
         AND fc.c_fertilizerApplied = 'no'    -- false -> "no"
         AND fc.c_pesticidesApplied = 'no'    -- "no" -> "no" (preserved)
        THEN '✓ PASS - Mixed formats handled correctly'
        ELSE '✗ FAIL - Mixed format handling failed'
    END as result
FROM app_fd_farms_registry f
LEFT JOIN app_fd_farmer_crop_livestck fc ON f.c_crops_livestock = fc.id
WHERE f.id = 'test-mixed-001';

-- ============================================
-- SECTION 3: Check for Unnormalized Values
-- ============================================

SELECT '=== UNNORMALIZED VALUES CHECK ===' as Status;

-- Find any boolean values that weren't normalized
SELECT
    'Unnormalized booleans' as issue,
    COUNT(*) as count
FROM (
    SELECT id FROM app_fd_farmer_crop_livestck
    WHERE c_livestockProduction IN ('true', 'false')
       OR c_cropProduction IN ('true', 'false')
       OR c_canReadWrite IN ('true', 'false')
       OR c_fertilizerApplied IN ('true', 'false')
       OR c_pesticidesApplied IN ('true', 'false')
    UNION
    SELECT id FROM app_fd_household_members
    WHERE c_participatesInAgriculture IN ('true', 'false')
       OR c_chronicallyIll IN ('true', 'false')
) as unnormalized;

-- Find any wrong LOV type usage
SELECT
    'Wrong LOV types' as issue,
    COUNT(*) as count
FROM (
    SELECT id FROM app_fd_farmer_crop_livestck
    WHERE (c_livestockProduction IN ('yes', 'no'))  -- Should be 1/2
       OR (c_cropProduction IN ('1', '2'))           -- Should be yes/no
    UNION
    SELECT id FROM app_fd_household_members
    WHERE c_participatesInAgriculture IN ('yes', 'no')  -- Should be 1/2
       OR c_chronicallyIll IN ('yes', 'no')             -- Should be 1/2
) as wrong_lov;

-- ============================================
-- SECTION 4: Summary
-- ============================================

SELECT '=== NORMALIZATION SUMMARY ===' as Status;

SELECT
    'Total Farmers' as metric,
    COUNT(*) as value
FROM app_fd_farms_registry;

SELECT
    'Records with correct LOV values' as metric,
    COUNT(*) as value
FROM app_fd_farmer_crop_livestck
WHERE c_livestockProduction IN ('1', '2')
  AND c_cropProduction IN ('yes', 'no');

SELECT
    'Records with unnormalized values' as metric,
    COUNT(*) as value
FROM app_fd_farmer_crop_livestck
WHERE c_livestockProduction IN ('true', 'false')
   OR c_cropProduction IN ('true', 'false');

-- ============================================
-- SUCCESS CRITERIA:
-- ✅ No unnormalized boolean values (true/false)
-- ✅ livestockProduction only has "1" or "2"
-- ✅ cropProduction only has "yes" or "no"
-- ✅ participatesInAgriculture only has "1" or "2"
-- ✅ chronicallyIll only has "1" or "2"
-- ✅ Test records show correct transformations
-- ============================================