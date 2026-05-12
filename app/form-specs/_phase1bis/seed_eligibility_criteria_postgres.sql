-- =============================================================================
-- Seed Eligibility Criteria (Tab 6) on the DEV Postgres
-- =============================================================================
-- The other four child grids (districts, target groups, benefit items, KPIs)
-- were seeded in v1; sp_elig_criterion was never seeded — that's why the
-- Eligibility tab grid renders empty.
--
-- This script:
--   1. Removes any partial sp_elig_criterion rows we may have left behind.
--   2. Inserts criteria for prog001..prog004 with c_program_id resolved at
--      INSERT time to the matching ELIGIBILITY tab record's UUID
--      (sp_program_eligiblt.id where c_parent_id='progNNN').
--
-- The MD codes used here (FARMER / PARCEL / HOUSEHOLD field categories and
-- EQ / GE / LE / IN operators) are the conventional codes — adjust in the
-- UI later if your md51FieldCategory / md50EligOperator tables use
-- different ones.
--
-- Apply with:
--   psql -h joget-pgsql-sa.postgres.database.azure.com -U <user> -d <db> \
--        -f seed_eligibility_criteria_postgres.sql
-- Then App Composer → open any form → Save (no edits) to evict cache.
-- =============================================================================

BEGIN;

-- 0) Cleanup any prior junk rows that would clash on PK ------------------------
DELETE FROM app_fd_sp_elig_criterion WHERE id LIKE 'ec-%';

-- =============================================================================
-- prog001 — Input Subsidy (ALL_MUST_PASS, no scoring)
-- =============================================================================
INSERT INTO app_fd_sp_elig_criterion
  (id, "dateCreated", "dateModified", "createdBy", "modifiedBy", c_program_id,
   c_criterionOrder, c_fieldCategory, c_fieldName, c_operatorCode,
   c_criterionValue, c_criterionValueTo, c_isMandatory, c_ruleType, c_score, c_failMessage, c_notes)
SELECT 'ec-001-01', NOW(), NOW(), 'admin', 'admin', e.id,
   1, 'FARMER', 'isActive', 'EQ',
   'Y', NULL, 'Y', 'INCLUSION', 0, 'Farmer profile must be active', 'Active farmer registry record'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog001'
UNION ALL SELECT 'ec-001-02', NOW(), NOW(), 'admin', 'admin', e.id,
   2, 'PARCEL', 'totalArea', 'LE',
   '2', NULL, 'Y', 'INCLUSION', 0, 'Parcel must be ≤ 2 ha', 'Smallholder threshold'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog001'
UNION ALL SELECT 'ec-001-03', NOW(), NOW(), 'admin', 'admin', e.id,
   3, 'PARCEL', 'priorSeasonCereal', 'EQ',
   'Y', NULL, 'Y', 'INCLUSION', 0, 'Must have grown cereal in the previous season', 'Maize/sorghum/wheat in last harvest'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog001'
UNION ALL SELECT 'ec-001-04', NOW(), NOW(), 'admin', 'admin', e.id,
   4, 'HOUSEHOLD', 'concurrentSubsidy', 'EQ',
   'N', NULL, 'N', 'EXCLUSION', 0, 'Cannot be enrolled in another input subsidy this season', 'Cross-program block'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog001';

-- =============================================================================
-- prog002 — Mechanisation (ALL_MUST_PASS)
-- =============================================================================
INSERT INTO app_fd_sp_elig_criterion
  (id, "dateCreated", "dateModified", "createdBy", "modifiedBy", c_program_id,
   c_criterionOrder, c_fieldCategory, c_fieldName, c_operatorCode,
   c_criterionValue, c_criterionValueTo, c_isMandatory, c_ruleType, c_score, c_failMessage, c_notes)
SELECT 'ec-002-01', NOW(), NOW(), 'admin', 'admin', e.id,
   1, 'FARMER', 'isActive', 'EQ', 'Y', NULL, 'Y', 'INCLUSION', 0, 'Farmer profile must be active', NULL
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog002'
UNION ALL SELECT 'ec-002-02', NOW(), NOW(), 'admin', 'admin', e.id,
   2, 'PARCEL', 'totalArea', 'GE', '0.5', NULL, 'Y', 'INCLUSION', 0, 'Parcel must be ≥ 0.5 ha', 'Mechanisable lower bound'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog002'
UNION ALL SELECT 'ec-002-03', NOW(), NOW(), 'admin', 'admin', e.id,
   3, 'PARCEL', 'slopePercent', 'LE', '8', NULL, 'Y', 'INCLUSION', 0, 'Slope must be ≤ 8% (mechanisable terrain)', 'Tractor safety'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog002'
UNION ALL SELECT 'ec-002-04', NOW(), NOW(), 'admin', 'admin', e.id,
   4, 'PARCEL', 'gisVerified', 'EQ', 'Y', NULL, 'Y', 'INCLUSION', 0, 'Parcel boundary must be GIS-verified', NULL
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog002';

-- =============================================================================
-- prog003 — Block Farming (SCORE_BASED, threshold 60)
-- =============================================================================
INSERT INTO app_fd_sp_elig_criterion
  (id, "dateCreated", "dateModified", "createdBy", "modifiedBy", c_program_id,
   c_criterionOrder, c_fieldCategory, c_fieldName, c_operatorCode,
   c_criterionValue, c_criterionValueTo, c_isMandatory, c_ruleType, c_score, c_failMessage, c_notes)
SELECT 'ec-003-01', NOW(), NOW(), 'admin', 'admin', e.id,
   1, 'FARMER', 'cooperativeMember', 'EQ', 'Y', NULL, 'N', 'PRIORITY', 40, 'Cooperative member', '40 points'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog003'
UNION ALL SELECT 'ec-003-02', NOW(), NOW(), 'admin', 'admin', e.id,
   2, 'PARCEL', 'totalAreaContributed', 'GE', '0.5', NULL, 'N', 'PRIORITY', 30, 'Contributing ≥0.5 ha to a block', '30 points'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog003'
UNION ALL SELECT 'ec-003-03', NOW(), NOW(), 'admin', 'admin', e.id,
   3, 'PROGRAMME', 'blockPlanSubmitted', 'EQ', 'Y', NULL, 'N', 'PRIORITY', 30, 'Block plan signed by chief & cooperative', '30 points'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog003'
UNION ALL SELECT 'ec-003-04', NOW(), NOW(), 'admin', 'admin', e.id,
   4, 'FARMER', 'isActive', 'EQ', 'Y', NULL, 'Y', 'INCLUSION', 0, 'Farmer profile must be active', NULL
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog003'
UNION ALL SELECT 'ec-003-05', NOW(), NOW(), 'admin', 'admin', e.id,
   5, 'HOUSEHOLD', 'priorBlockMember', 'EQ', 'Y', NULL, 'N', 'BONUS', 10, 'Returning block member from prior season', '+10 bonus'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog003';

-- =============================================================================
-- prog004 — Vulnerable HH (ALL_MUST_PASS, exclusion of duplicates)
-- =============================================================================
INSERT INTO app_fd_sp_elig_criterion
  (id, "dateCreated", "dateModified", "createdBy", "modifiedBy", c_program_id,
   c_criterionOrder, c_fieldCategory, c_fieldName, c_operatorCode,
   c_criterionValue, c_criterionValueTo, c_isMandatory, c_ruleType, c_score, c_failMessage, c_notes)
SELECT 'ec-004-01', NOW(), NOW(), 'admin', 'admin', e.id,
   1, 'HOUSEHOLD', 'foodSecurityScore', 'GE', '3', NULL, 'Y', 'INCLUSION', 0, 'Food security score must be MODERATE or higher (≥3)', 'On 1–5 scale'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog004'
UNION ALL SELECT 'ec-004-02', NOW(), NOW(), 'admin', 'admin', e.id,
   2, 'HOUSEHOLD', 'vulnerabilityFlag', 'IN', 'ELDERLY,DISABILITY,HIV,ORPHAN_HEAD', NULL, 'Y', 'INCLUSION', 0, 'Household must carry at least one vulnerability flag', NULL
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog004'
UNION ALL SELECT 'ec-004-03', NOW(), NOW(), 'admin', 'admin', e.id,
   3, 'HOUSEHOLD', 'vulnerabilityScore', 'GE', '50', NULL, 'Y', 'INCLUSION', 0, 'Vulnerability score ≥ 50/100', 'Composite vulnerability index'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog004'
UNION ALL SELECT 'ec-004-04', NOW(), NOW(), 'admin', 'admin', e.id,
   4, 'HOUSEHOLD', 'concurrentSubsidy', 'EQ', 'N', NULL, 'Y', 'EXCLUSION', 0, 'Cannot be enrolled in concurrent subsidy programmes', 'Anti-duplication block'
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog004'
UNION ALL SELECT 'ec-004-05', NOW(), NOW(), 'admin', 'admin', e.id,
   5, 'FARMER', 'nidVerified', 'EQ', 'Y', NULL, 'Y', 'INCLUSION', 0, 'NID verified against the registry', NULL
  FROM app_fd_sp_program_eligiblt e WHERE e.c_parent_id='prog004';

COMMIT;

-- =============================================================================
-- VERIFICATION — expect 18 rows (4 + 4 + 5 + 5)
-- =============================================================================
-- SELECT c.c_program_id, e.c_parent_id AS prog_id, COUNT(*) AS criteria
--   FROM app_fd_sp_elig_criterion c
--   JOIN app_fd_sp_program_eligiblt e ON e.id = c.c_program_id
--  WHERE e.c_parent_id LIKE 'prog%'
--  GROUP BY c.c_program_id, e.c_parent_id
--  ORDER BY e.c_parent_id;
