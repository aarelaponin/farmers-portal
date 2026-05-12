-- =============================================================================
-- Fix child-grid FK on the DEV Postgres (Azure) — surgical UPDATE only
-- =============================================================================
-- Existing state on dev DB:
--   * 21 rows in app_fd_sp_district_alloc with c_program_id = 'prog001'..'prog004'
--     (i.e. the WIZARD id) — and similarly for sp_target_group / sp_benefit_item / sp_kpi.
--   * The wizard's tab subforms (geography / beneficiary / benefits / monitor)
--     each already have rows with c_parent_id = 'prog001'..'prog004'.
--     Their primary key `id` is a random UUID (not 'prog001-geog' etc.).
--
-- Joget's MultirowFormBinder filters child rows by the IMMEDIATE tab record's
-- id, NOT the wizard's id. So we re-point each child row's c_program_id at the
-- matching tab record's UUID.
--
-- This script is destructive ONLY to the c_program_id column on the 4 child
-- grid tables. Nothing else is touched. Wizard rows, tab rows, and other
-- columns on the child grid tables remain untouched.
--
-- Apply with psql:
--   psql -h joget-pgsql-sa.postgres.database.azure.com -U <user> -d <db> \
--        -f fix_child_grid_fk_postgres.sql
-- Then App Composer → open any form → Save (no edits) to evict cache.
-- =============================================================================

BEGIN;

-- 1) District Allocations — point at the GEOGRAPHY tab record id ---------------
UPDATE app_fd_sp_district_alloc d
   SET c_program_id = g.id
  FROM app_fd_sp_program_geography g
 WHERE g.c_parent_id = d.c_program_id            -- d.c_program_id is currently 'prog001'..
   AND d.c_program_id LIKE 'prog%';              -- safety: only touch the broken rows

-- 2) Target Groups — point at the BENEFICIARY tab record id --------------------
UPDATE app_fd_sp_target_group t
   SET c_program_id = b.id
  FROM app_fd_sp_program_beneficr b
 WHERE b.c_parent_id = t.c_program_id
   AND t.c_program_id LIKE 'prog%';

-- 3) Benefit Items — point at the BENEFITS tab record id ----------------------
UPDATE app_fd_sp_benefit_item bi
   SET c_program_id = bn.id
  FROM app_fd_sp_program_benefits bn
 WHERE bn.c_parent_id = bi.c_program_id
   AND bi.c_program_id LIKE 'prog%';

-- 4) KPIs — point at the MONITORING tab record id -----------------------------
UPDATE app_fd_sp_kpi k
   SET c_program_id = m.id
  FROM app_fd_sp_program_monitor m
 WHERE m.c_parent_id = k.c_program_id
   AND k.c_program_id LIKE 'prog%';

-- =============================================================================
-- VERIFICATION (run after committing)
-- Each query should return 0 rows. If it returns >0, those rows still point at
-- a non-existent tab record id (likely because that programme's tab was never
-- created — re-open the wizard and visit the tab to create it, then re-run).
-- =============================================================================
-- SELECT 'orphan district_alloc' AS issue, d.id, d.c_program_id
--   FROM app_fd_sp_district_alloc d
--   LEFT JOIN app_fd_sp_program_geography g ON g.id = d.c_program_id
--  WHERE g.id IS NULL AND d.c_program_id IS NOT NULL;
--
-- SELECT 'orphan target_group', t.id, t.c_program_id
--   FROM app_fd_sp_target_group t
--   LEFT JOIN app_fd_sp_program_beneficr b ON b.id = t.c_program_id
--  WHERE b.id IS NULL AND t.c_program_id IS NOT NULL;
--
-- SELECT 'orphan benefit_item', bi.id, bi.c_program_id
--   FROM app_fd_sp_benefit_item bi
--   LEFT JOIN app_fd_sp_program_benefits bn ON bn.id = bi.c_program_id
--  WHERE bn.id IS NULL AND bi.c_program_id IS NOT NULL;
--
-- SELECT 'orphan kpi', k.id, k.c_program_id
--   FROM app_fd_sp_kpi k
--   LEFT JOIN app_fd_sp_program_monitor m ON m.id = k.c_program_id
--  WHERE m.id IS NULL AND k.c_program_id IS NOT NULL;

COMMIT;
