-- ============================================================================
-- SMART FARMER SEARCH - DATABASE SETUP (VIEW-BASED APPROACH)
-- 
-- This approach uses a VIEW over existing Joget form tables instead of a 
-- separate index table. Benefits:
-- - Zero latency (always current data)
-- - No sync job needed
-- - Single source of truth
-- - Simpler architecture
--
-- Run this script against your Joget database.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- STEP 1: ADD INDEXES TO EXISTING JOGET FORM TABLES
-- 
-- These indexes optimize the search queries. Add them to the source tables
-- that Joget created for your forms.
-- ----------------------------------------------------------------------------

-- Indexes on farmerBasicInfo (f01_01)
-- Check if index exists before creating to avoid errors
CREATE INDEX IF NOT EXISTS idx_fbi_national_id ON app_fd_farmerBasicInfo(c_national_id);
CREATE INDEX IF NOT EXISTS idx_fbi_mobile ON app_fd_farmerBasicInfo(c_mobile_number);
CREATE INDEX IF NOT EXISTS idx_fbi_first_name ON app_fd_farmerBasicInfo(c_first_name(50));
CREATE INDEX IF NOT EXISTS idx_fbi_last_name ON app_fd_farmerBasicInfo(c_last_name(50));
CREATE INDEX IF NOT EXISTS idx_fbi_parent ON app_fd_farmerBasicInfo(c_parent_id);

-- Indexes on farm_location (f01_02)
CREATE INDEX IF NOT EXISTS idx_fl_district ON app_fd_farm_location(c_district);
CREATE INDEX IF NOT EXISTS idx_fl_village ON app_fd_farm_location(c_village(50));
CREATE INDEX IF NOT EXISTS idx_fl_community ON app_fd_farm_location(c_communityCouncil(50));
CREATE INDEX IF NOT EXISTS idx_fl_parent ON app_fd_farm_location(c_parent_id);

-- Note: If "CREATE INDEX IF NOT EXISTS" is not supported (MySQL < 8.0.29),
-- use this pattern instead:
-- 
-- DROP PROCEDURE IF EXISTS add_index_if_not_exists;
-- DELIMITER //
-- CREATE PROCEDURE add_index_if_not_exists()
-- BEGIN
--     IF NOT EXISTS (SELECT 1 FROM information_schema.statistics 
--                    WHERE table_name = 'app_fd_farmerBasicInfo' 
--                    AND index_name = 'idx_fbi_national_id') THEN
--         CREATE INDEX idx_fbi_national_id ON app_fd_farmerBasicInfo(c_national_id);
--     END IF;
-- END //
-- DELIMITER ;
-- CALL add_index_if_not_exists();
-- DROP PROCEDURE add_index_if_not_exists;


-- ----------------------------------------------------------------------------
-- STEP 2: CREATE THE SEARCH VIEW
-- 
-- This view joins the source tables and computes search fields on-the-fly.
-- Soundex is computed at query time (MySQL SOUNDEX function).
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_farmer_search AS
SELECT 
    -- Primary key (from farmerBasicInfo)
    bi.id,
    
    -- === COMPUTED SEARCH FIELDS ===
    
    -- Full name for text search (lowercase, space-separated)
    LOWER(CONCAT_WS(' ', 
        TRIM(COALESCE(bi.c_first_name, '')), 
        TRIM(COALESCE(bi.c_last_name, ''))
    )) AS c_search_name,
    
    -- Phonetic codes for fuzzy matching (computed on-the-fly)
    CONCAT_WS(' ', 
        SOUNDEX(COALESCE(bi.c_first_name, '')), 
        SOUNDEX(COALESCE(bi.c_last_name, ''))
    ) AS c_name_soundex,
    
    -- === EXACT MATCH FIELDS ===
    
    bi.c_national_id,
    REGEXP_REPLACE(COALESCE(bi.c_mobile_number, ''), '[^0-9]', '') AS c_phone_normalized,
    
    -- === FILTER FIELDS ===
    
    COALESCE(loc.c_district, '') AS c_district_code,
    COALESCE(loc.c_village, '') AS c_village,
    COALESCE(loc.c_communityCouncil, '') AS c_community_council,
    
    -- === DISPLAY FIELDS ===
    
    bi.c_first_name,
    bi.c_last_name,
    bi.c_gender,
    bi.c_date_of_birth,
    bi.c_mobile_number AS c_phone_display,
    d.c_name AS c_district_name,
    bi.c_cooperative_name,
    
    -- === METADATA ===
    
    fr.id AS c_source_record_id

FROM app_fd_farmerBasicInfo bi
INNER JOIN app_fd_farms_registry fr ON bi.c_parent_id = fr.id
LEFT JOIN app_fd_farm_location loc ON loc.c_parent_id = fr.id
LEFT JOIN app_fd_md03district d ON loc.c_district = d.c_code;


-- ----------------------------------------------------------------------------
-- STEP 3: VERIFICATION QUERIES
-- ----------------------------------------------------------------------------

-- Test the view works
-- SELECT COUNT(*) AS total_farmers FROM v_farmer_search;

-- Test exact ID search
-- SELECT * FROM v_farmer_search WHERE c_national_id = '4444';

-- Test name search with soundex
-- SELECT id, c_first_name, c_last_name, c_name_soundex, c_district_code
-- FROM v_farmer_search 
-- WHERE c_search_name LIKE '%mamosa%' OR c_name_soundex LIKE '%M520%'
-- LIMIT 20;

-- Test district filter
-- SELECT id, c_first_name, c_last_name, c_village
-- FROM v_farmer_search 
-- WHERE c_district_code = 'BER'
-- LIMIT 20;

-- Check index usage (should show index being used)
-- EXPLAIN SELECT * FROM v_farmer_search WHERE c_national_id = '123456789';


-- ============================================================================
-- NOTES ON PERFORMANCE
-- ============================================================================
-- 
-- With proper indexes on the underlying tables, the view approach performs well
-- for databases up to several hundred thousand records.
-- 
-- Key indexes that must exist:
-- 1. app_fd_farmerBasicInfo.c_national_id (for exact ID lookup)
-- 2. app_fd_farmerBasicInfo.c_mobile_number (for phone lookup)
-- 3. app_fd_farmerBasicInfo.c_parent_id (for JOIN)
-- 4. app_fd_farm_location.c_district (for district filter)
-- 5. app_fd_farm_location.c_parent_id (for JOIN)
-- 
-- If performance degrades with very large datasets, consider:
-- 1. Adding composite indexes for common query patterns
-- 2. Using MySQL's query cache
-- 3. Falling back to the materialized index table approach with scheduled sync
--
-- ============================================================================
