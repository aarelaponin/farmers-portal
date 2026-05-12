-- ============================================================================
-- SMART FARMER SEARCH - INDEX POPULATION SQL
-- 
-- This script populates the farmer_search_index table from source tables.
-- Run as a scheduled job (nightly recommended) or manually after data changes.
--
-- Prerequisites: DDL from smart-search-ddl.sql must be executed first.
-- ============================================================================

-- Use INSERT ... ON DUPLICATE KEY UPDATE for idempotent operation
INSERT INTO app_fd_farmer_search_index (
    id,
    c_search_name,
    c_search_text,
    c_name_soundex,
    c_national_id,
    c_phone_normalized,
    c_district_code,
    c_village,
    c_community_council,
    c_first_name,
    c_last_name,
    c_gender,
    c_date_of_birth,
    c_phone_display,
    c_district_name,
    c_cooperative_name,
    c_source_record_id
)
SELECT 
    -- Use farmerBasicInfo.id as primary key for the index
    bi.id,
    
    -- c_search_name: lowercase full name for text search
    LOWER(CONCAT_WS(' ', TRIM(COALESCE(bi.c_first_name, '')), TRIM(COALESCE(bi.c_last_name, '')))),
    
    -- c_search_text: all searchable fields combined for full-text search
    LOWER(CONCAT_WS(' ',
        COALESCE(bi.c_national_id, ''),
        TRIM(COALESCE(bi.c_first_name, '')),
        TRIM(COALESCE(bi.c_last_name, '')),
        REGEXP_REPLACE(COALESCE(bi.c_mobile_number, ''), '[^0-9]', ''),
        COALESCE(d.c_name, ''),
        COALESCE(loc.c_village, ''),
        COALESCE(loc.c_communityCouncil, ''),
        COALESCE(bi.c_cooperative_name, '')
    )),
    
    -- c_name_soundex: phonetic codes for fuzzy matching
    CONCAT_WS(' ', 
        SOUNDEX(COALESCE(bi.c_first_name, '')), 
        SOUNDEX(COALESCE(bi.c_last_name, ''))
    ),
    
    -- Exact match fields
    bi.c_national_id,
    REGEXP_REPLACE(COALESCE(bi.c_mobile_number, ''), '[^0-9]', ''),
    
    -- Filter fields
    COALESCE(loc.c_district, ''),
    COALESCE(loc.c_village, ''),
    COALESCE(loc.c_communityCouncil, ''),
    
    -- Display fields
    bi.c_first_name,
    bi.c_last_name,
    bi.c_gender,
    bi.c_date_of_birth,
    bi.c_mobile_number,
    d.c_name,
    bi.c_cooperative_name,
    
    -- Source reference (farms_registry.id)
    fr.id

FROM app_fd_farmerBasicInfo bi
INNER JOIN app_fd_farms_registry fr ON bi.c_parent_id = fr.id
LEFT JOIN app_fd_farm_location loc ON loc.c_parent_id = fr.id
LEFT JOIN app_fd_md03district d ON loc.c_district = d.c_code

ON DUPLICATE KEY UPDATE
    c_search_name = VALUES(c_search_name),
    c_search_text = VALUES(c_search_text),
    c_name_soundex = VALUES(c_name_soundex),
    c_phone_normalized = VALUES(c_phone_normalized),
    c_district_code = VALUES(c_district_code),
    c_village = VALUES(c_village),
    c_community_council = VALUES(c_community_council),
    c_first_name = VALUES(c_first_name),
    c_last_name = VALUES(c_last_name),
    c_gender = VALUES(c_gender),
    c_date_of_birth = VALUES(c_date_of_birth),
    c_phone_display = VALUES(c_phone_display),
    c_district_name = VALUES(c_district_name),
    c_cooperative_name = VALUES(c_cooperative_name),
    c_source_record_id = VALUES(c_source_record_id),
    c_last_updated = CURRENT_TIMESTAMP;


-- ============================================================================
-- OPTIONAL: Clean up orphaned index records
-- 
-- Remove index entries where source farmer no longer exists
-- Run periodically to keep index clean
-- ============================================================================

-- DELETE fsi FROM app_fd_farmer_search_index fsi
-- LEFT JOIN app_fd_farmerBasicInfo bi ON fsi.id = bi.id
-- WHERE bi.id IS NULL;


-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Count indexed farmers
-- SELECT COUNT(*) as indexed_count FROM app_fd_farmer_search_index;

-- Count source farmers (should match or be close)
-- SELECT COUNT(*) as source_count FROM app_fd_farmerBasicInfo;

-- Sample indexed data
-- SELECT id, c_first_name, c_last_name, c_district_code, c_village 
-- FROM app_fd_farmer_search_index 
-- LIMIT 10;

-- Verify soundex generation
-- SELECT c_first_name, c_last_name, c_name_soundex 
-- FROM app_fd_farmer_search_index 
-- LIMIT 10;
