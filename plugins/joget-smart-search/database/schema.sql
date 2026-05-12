-- ============================================================================
-- SMART FARMER SEARCH - DATABASE SCHEMA
-- Version: 8.1-SNAPSHOT
--
-- This script provides database setup options for the Smart Farmer Search plugin.
--
-- OPTION 1: Use a VIEW (Recommended for PostgreSQL/Azure)
--   - Creates a view over existing Joget form tables
--   - No data duplication, always up-to-date
--   - See "POSTGRESQL VIEW" section below
--
-- OPTION 2: Use a TABLE (For MySQL or when view is too slow)
--   - Creates a denormalized index table
--   - Requires periodic refresh via populate-index.sql
--   - See "INDEX TABLE" section below
-- ============================================================================

-- ============================================================================
-- POSTGRESQL VIEW (Recommended for Azure PostgreSQL)
-- Adjust table names to match your Joget form definitions
-- ============================================================================

-- Basic view without Soundex (Azure PostgreSQL compatible):
/*
CREATE OR REPLACE VIEW v_farmer_search AS
SELECT
    bi.id,
    bi.c_national_id,
    REGEXP_REPLACE(bi.c_mobile_number, '[^0-9]', '', 'g') AS c_phone_normalized,
    bi.c_mobile_number AS c_phone_display,
    bi.c_first_name,
    bi.c_last_name,
    bi.c_gender,
    bi.c_date_of_birth,
    loc.c_district AS c_district_code,
    d.c_name AS c_district_name,
    loc.c_village,
    loc.c_communityCouncil AS c_community_council,
    bi.c_cooperative_name,
    LOWER(CONCAT_WS(' ', TRIM(bi.c_first_name), TRIM(bi.c_last_name))) AS c_search_name,
    NULL AS c_name_soundex,
    fr.id AS c_source_record_id
FROM app_fd_farmerBasicInfo bi
INNER JOIN app_fd_farms_registry fr ON bi.c_parent_id = fr.id
LEFT JOIN app_fd_farm_location loc ON loc.c_parent_id = fr.id
LEFT JOIN app_fd_md03district d ON loc.c_district = d.c_code;
*/

-- With Soundex (requires fuzzystrmatch extension):
/*
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

CREATE OR REPLACE VIEW v_farmer_search AS
SELECT
    bi.id,
    bi.c_national_id,
    REGEXP_REPLACE(bi.c_mobile_number, '[^0-9]', '', 'g') AS c_phone_normalized,
    bi.c_mobile_number AS c_phone_display,
    bi.c_first_name,
    bi.c_last_name,
    bi.c_gender,
    bi.c_date_of_birth,
    loc.c_district AS c_district_code,
    d.c_name AS c_district_name,
    loc.c_village,
    loc.c_communityCouncil AS c_community_council,
    bi.c_cooperative_name,
    LOWER(CONCAT_WS(' ', TRIM(bi.c_first_name), TRIM(bi.c_last_name))) AS c_search_name,
    CONCAT_WS(' ', soundex(bi.c_first_name), soundex(bi.c_last_name)) AS c_name_soundex,
    fr.id AS c_source_record_id
FROM app_fd_farmerBasicInfo bi
INNER JOIN app_fd_farms_registry fr ON bi.c_parent_id = fr.id
LEFT JOIN app_fd_farm_location loc ON loc.c_parent_id = fr.id
LEFT JOIN app_fd_md03district d ON loc.c_district = d.c_code;
*/

-- ============================================================================
-- FUZZY MATCHING WITH pg_trgm (Recommended)
-- Enables finding "Thabo" when searching "Tabo"
-- May require Azure portal allow-listing before CREATE EXTENSION
-- ============================================================================

/*
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create trigram index for fast fuzzy name searches
CREATE INDEX idx_farmer_search_name_trgm
ON app_fd_farmerBasicInfo
USING gin((LOWER(c_first_name || ' ' || c_last_name)) gin_trgm_ops);
*/

-- ============================================================================
-- INDEX TABLE (Alternative for MySQL or performance optimization)
-- ============================================================================

-- ============================================================================
-- FARMER SEARCH INDEX TABLE
-- Application-managed denormalized index for fast farmer lookups
-- ============================================================================

CREATE TABLE IF NOT EXISTS app_fd_farmer_search_index (
    -- Primary Key (same as farmer registry ID)
    id VARCHAR(50) PRIMARY KEY,
    
    -- === CONCATENATED SEARCH COLUMNS (Indexed) ===
    
    -- Full name for text search (lowercase, space-separated)
    -- Source: LOWER(first_name || ' ' || last_name)
    c_search_name VARCHAR(200) NOT NULL,
    
    -- Full text search blob (all searchable text combined)
    -- Source: national_id + first_name + last_name + phone + district + village
    c_search_text TEXT NOT NULL,
    
    -- Phonetic codes for fuzzy matching
    -- Source: SOUNDEX(first_name) + ' ' + SOUNDEX(last_name)
    c_name_soundex VARCHAR(20),
    
    -- === EXACT MATCH COLUMNS (Indexed) ===
    
    c_national_id VARCHAR(20) NOT NULL,
    c_phone_normalized VARCHAR(20),      -- Digits only: 26651234567
    
    -- === FILTER COLUMNS (Indexed) ===
    
    c_district_code VARCHAR(20) NOT NULL,
    c_village VARCHAR(100) NOT NULL,
    c_community_council VARCHAR(100),
    
    -- === DISPLAY COLUMNS (Not indexed, for result rendering) ===
    
    c_first_name VARCHAR(100) NOT NULL,
    c_last_name VARCHAR(100) NOT NULL,
    c_gender VARCHAR(10),
    c_date_of_birth DATE,
    c_phone_display VARCHAR(20),         -- Formatted: +266 5123 4567
    c_district_name VARCHAR(100),
    c_cooperative_name VARCHAR(100),
    
    -- === METADATA ===
    
    c_source_record_id VARCHAR(50),      -- FK to farms_registry.id
    c_last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- === INDEXES ===
    
    -- Exact match indexes
    UNIQUE INDEX idx_fss_national_id (c_national_id),
    INDEX idx_fss_phone (c_phone_normalized),
    
    -- Filter indexes
    INDEX idx_fss_district (c_district_code),
    INDEX idx_fss_village (c_village(50)),
    INDEX idx_fss_district_village (c_district_code, c_village(50)),
    INDEX idx_fss_community_council (c_community_council(50)),
    
    -- Text search indexes
    INDEX idx_fss_search_name (c_search_name(100)),
    FULLTEXT INDEX ft_fss_search_text (c_search_text),
    
    -- Phonetic index for fuzzy matching
    INDEX idx_fss_soundex (c_name_soundex)
    
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================================
-- STATISTICS CACHE TABLE
-- Pre-computed statistics for client-side confidence estimation
-- ============================================================================

CREATE TABLE IF NOT EXISTS app_fd_farmer_search_stats (
    -- Primary Key (e.g., 'GLOBAL', 'DISTRICT_BEREA', etc.)
    id VARCHAR(50) PRIMARY KEY,
    
    -- Statistics data (JSON format)
    c_stats_json LONGTEXT NOT NULL,
    
    -- Metadata
    c_version VARCHAR(20) NOT NULL,
    c_generated_at TIMESTAMP NOT NULL,
    c_total_farmers INT NOT NULL,
    c_district_code VARCHAR(20),         -- NULL for global stats
    
    -- Indexes
    INDEX idx_fss_stats_district (c_district_code),
    INDEX idx_fss_stats_generated (c_generated_at)
    
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================================
-- INDEX POPULATION SQL (Sample)
-- This would be executed as a scheduled job to refresh the search index
-- ============================================================================

-- REPLACE INTO app_fd_farmer_search_index (
--     id,
--     c_search_name,
--     c_search_text,
--     c_name_soundex,
--     c_national_id,
--     c_phone_normalized,
--     c_district_code,
--     c_village,
--     c_community_council,
--     c_first_name,
--     c_last_name,
--     c_gender,
--     c_date_of_birth,
--     c_phone_display,
--     c_district_name,
--     c_cooperative_name,
--     c_source_record_id
-- )
-- SELECT 
--     bi.id,
--     LOWER(CONCAT_WS(' ', TRIM(bi.c_first_name), TRIM(bi.c_last_name))),
--     LOWER(CONCAT_WS(' ',
--         bi.c_national_id,
--         TRIM(bi.c_first_name),
--         TRIM(bi.c_last_name),
--         REGEXP_REPLACE(bi.c_mobile_number, '[^0-9]', ''),
--         d.c_name,
--         loc.c_village,
--         loc.c_communityCouncil,
--         bi.c_cooperative_name
--     )),
--     CONCAT_WS(' ', SOUNDEX(bi.c_first_name), SOUNDEX(bi.c_last_name)),
--     bi.c_national_id,
--     REGEXP_REPLACE(bi.c_mobile_number, '[^0-9]', ''),
--     loc.c_district,
--     loc.c_village,
--     loc.c_communityCouncil,
--     bi.c_first_name,
--     bi.c_last_name,
--     bi.c_gender,
--     bi.c_date_of_birth,
--     bi.c_mobile_number,
--     d.c_name,
--     bi.c_cooperative_name,
--     fr.id
-- FROM app_fd_farmerBasicInfo bi
-- INNER JOIN app_fd_farms_registry fr ON bi.c_parent_id = fr.id
-- LEFT JOIN app_fd_farm_location loc ON loc.c_parent_id = fr.id
-- LEFT JOIN app_fd_md03district d ON loc.c_district = d.c_code;


-- ============================================================================
-- SAMPLE STATISTICS GENERATION (Global)
-- ============================================================================

-- INSERT INTO app_fd_farmer_search_stats (
--     id, 
--     c_stats_json, 
--     c_version, 
--     c_generated_at, 
--     c_total_farmers, 
--     c_district_code
-- )
-- SELECT 
--     'GLOBAL',
--     JSON_OBJECT(
--         'version', '1.0',
--         'generated_at', NOW(),
--         'total_farmers', COUNT(*)
--     ),
--     '1.0',
--     NOW(),
--     COUNT(*),
--     NULL
-- FROM app_fd_farmer_search_index
-- ON DUPLICATE KEY UPDATE
--     c_stats_json = VALUES(c_stats_json),
--     c_generated_at = NOW(),
--     c_total_farmers = VALUES(c_total_farmers);
