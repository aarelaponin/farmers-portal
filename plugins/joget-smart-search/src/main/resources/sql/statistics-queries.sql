-- ============================================================================
-- STATISTICS QUERIES
-- SQL queries for computing confidence engine statistics
-- Used by StatisticsService.java
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. TOTAL FARMERS COUNT
-- Get the total number of farmers in the search index
-- ----------------------------------------------------------------------------

SELECT COUNT(*) AS total_farmers 
FROM v_farmer_search;


-- ----------------------------------------------------------------------------
-- 2. DISTRICT COUNTS
-- Get farmer counts per district for confidence calculation
-- ----------------------------------------------------------------------------

SELECT c_district_code, COUNT(*) AS count 
FROM v_farmer_search 
WHERE c_district_code IS NOT NULL AND c_district_code != ''
GROUP BY c_district_code
ORDER BY count DESC;


-- ----------------------------------------------------------------------------
-- 3. SURNAME FREQUENCY (Top 100)
-- Get most common surnames and their frequency (probability)
-- Used to estimate how unique a surname is
-- ----------------------------------------------------------------------------

SELECT 
    LOWER(c_last_name) AS surname, 
    COUNT(*) AS count,
    COUNT(*) / (SELECT COUNT(*) FROM v_farmer_search) AS frequency
FROM v_farmer_search
WHERE c_last_name IS NOT NULL AND c_last_name != ''
GROUP BY LOWER(c_last_name)
ORDER BY COUNT(*) DESC
LIMIT 100;


-- ----------------------------------------------------------------------------
-- 4. FIRST NAME FREQUENCY (Top 100)
-- Get most common first names and their frequency
-- ----------------------------------------------------------------------------

SELECT 
    LOWER(c_first_name) AS firstname,
    COUNT(*) AS count,
    COUNT(*) / (SELECT COUNT(*) FROM v_farmer_search) AS frequency
FROM v_farmer_search
WHERE c_first_name IS NOT NULL AND c_first_name != ''
GROUP BY LOWER(c_first_name)
ORDER BY COUNT(*) DESC
LIMIT 100;


-- ----------------------------------------------------------------------------
-- 5. AVERAGE VILLAGE SIZE
-- Used to estimate how much a village filter narrows down results
-- ----------------------------------------------------------------------------

SELECT AVG(village_count) AS avg_size FROM (
    SELECT c_village, COUNT(*) AS village_count
    FROM v_farmer_search
    WHERE c_village IS NOT NULL AND c_village != ''
    GROUP BY c_village
) AS village_sizes;


-- ----------------------------------------------------------------------------
-- 6. VILLAGE SIZE DISTRIBUTION
-- For more detailed analysis (optional, not used in basic confidence)
-- ----------------------------------------------------------------------------

SELECT 
    CASE 
        WHEN village_count <= 10 THEN '1-10'
        WHEN village_count <= 50 THEN '11-50'
        WHEN village_count <= 100 THEN '51-100'
        WHEN village_count <= 200 THEN '101-200'
        ELSE '200+'
    END AS size_bracket,
    COUNT(*) AS village_count_in_bracket
FROM (
    SELECT c_village, COUNT(*) AS village_count
    FROM v_farmer_search
    WHERE c_village IS NOT NULL AND c_village != ''
    GROUP BY c_village
) AS village_sizes
GROUP BY 
    CASE 
        WHEN village_count <= 10 THEN '1-10'
        WHEN village_count <= 50 THEN '11-50'
        WHEN village_count <= 100 THEN '51-100'
        WHEN village_count <= 200 THEN '101-200'
        ELSE '200+'
    END
ORDER BY 
    CASE size_bracket
        WHEN '1-10' THEN 1
        WHEN '11-50' THEN 2
        WHEN '51-100' THEN 3
        WHEN '101-200' THEN 4
        ELSE 5
    END;


-- ----------------------------------------------------------------------------
-- 7. COMMUNITY COUNCIL COUNTS
-- Used to estimate community council filter effectiveness
-- ----------------------------------------------------------------------------

SELECT c_community_council, COUNT(*) AS count
FROM v_farmer_search
WHERE c_community_council IS NOT NULL AND c_community_council != ''
GROUP BY c_community_council
ORDER BY count DESC
LIMIT 50;


-- ----------------------------------------------------------------------------
-- 8. PARTIAL ID EFFECTIVENESS CHECK
-- Test how many farmers match a 4-digit partial ID
-- This helps validate the partial_id_4 effectiveness factor
-- ----------------------------------------------------------------------------

-- Example: Check how many farmers have '1234' anywhere in their ID
SELECT COUNT(*) AS matching_farmers
FROM v_farmer_search
WHERE c_national_id LIKE '%1234%';

-- Average matches per 4-digit pattern (statistical estimate)
-- With 10000 possible 4-digit patterns and X total farmers,
-- average matches = X / 10000


-- ----------------------------------------------------------------------------
-- 9. COMBINED STATISTICS QUERY
-- Single query to get key statistics (for performance)
-- ----------------------------------------------------------------------------

SELECT 
    (SELECT COUNT(*) FROM v_farmer_search) AS total_farmers,
    (SELECT COUNT(DISTINCT c_district_code) FROM v_farmer_search WHERE c_district_code IS NOT NULL) AS district_count,
    (SELECT COUNT(DISTINCT c_village) FROM v_farmer_search WHERE c_village IS NOT NULL) AS village_count,
    (SELECT AVG(village_count) FROM (
        SELECT c_village, COUNT(*) AS village_count
        FROM v_farmer_search
        WHERE c_village IS NOT NULL AND c_village != ''
        GROUP BY c_village
    ) AS vs) AS avg_village_size;


-- ============================================================================
-- END OF STATISTICS QUERIES
-- ============================================================================
