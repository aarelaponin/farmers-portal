-- =============================================================================
-- D1.A — Seed FOOD_SECURITY and PROGRAM_HISTORY categories on md51FieldCategory
-- Phase 1, Subsidy Adjustment Plan
-- Target DB: jogetdb (Postgres) on joget-pgsql-sa.postgres.database.azure.com
-- Pre-state: md51 has 8 categories (DEMOGRAPHIC, LOCATION, HOUSEHOLD, AGRICULTURAL,
--            ECONOMIC, DERIVED, PROGRAM, OTHER) at displayOrder 1-8.
-- Post-state: 10 categories. Idempotent (safe to re-run).
-- =============================================================================

INSERT INTO app_fd_md51fieldcategory
    (id, datecreated, datemodified, createdby, modifiedby,
     c_code, c_name, c_description, c_displayorder, c_isactive)
SELECT
    'cat-foodsecurity-001',  -- stable ID for re-runs and external references
    NOW(), NOW(), 'system', 'system',
    'FOOD_SECURITY',
    'Food Security',
    'Household food security status & vulnerability indicators',
    9,
    'Y'
WHERE NOT EXISTS (
    SELECT 1 FROM app_fd_md51fieldcategory WHERE c_code = 'FOOD_SECURITY'
);

INSERT INTO app_fd_md51fieldcategory
    (id, datecreated, datemodified, createdby, modifiedby,
     c_code, c_name, c_description, c_displayorder, c_isactive)
SELECT
    'cat-programhistory-001',
    NOW(), NOW(), 'system', 'system',
    'PROGRAM_HISTORY',
    'Program History',
    'Cross-programme participation & dependency indicators',
    10,
    'Y'
WHERE NOT EXISTS (
    SELECT 1 FROM app_fd_md51fieldcategory WHERE c_code = 'PROGRAM_HISTORY'
);

-- Verify
SELECT c_code, c_name, c_displayorder, c_isactive
FROM app_fd_md51fieldcategory
ORDER BY c_displayorder;
