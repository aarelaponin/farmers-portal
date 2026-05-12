-- =============================================================================
-- Child-grid only seed (Option B) — keeps existing prog001..prog004 + tab rows
-- =============================================================================
-- Use this if you have ALREADY entered/seeded prog001..prog004 wizard rows
-- and tab subforms (identity/timeline/geography/etc.) on the dev DB.
--
-- This script ONLY:
--   1. Deletes any leftover child-grid rows that point at LIKE 'prog%'
--      (covers both the broken v1 attempt and any partial v2 attempt).
--   2. Re-INSERTs child-grid rows (district allocations, target groups,
--      benefit items, KPIs) using the actual tab record id from each
--      tab subform table — NOT the wizard parent id.
--
-- Why subqueries? Joget UUIDs for the tab records aren't predictable;
-- the only reliable link is c_parent_id = wizard.id. We resolve the
-- correct tab id at INSERT time.
--
-- Apply with:
--   mysql -h <dev-host> -u <user> -p jwdb < seed_child_grids_only_v2.sql
-- Then App Composer → open any form → Save (no edits) to evict cache.
-- =============================================================================

START TRANSACTION;

-- 1) Clean any wrongly-linked child-grid rows ----------------------------------
DELETE FROM app_fd_sp_district_alloc WHERE c_program_id LIKE 'prog%';
DELETE FROM app_fd_sp_target_group   WHERE c_program_id LIKE 'prog%';
DELETE FROM app_fd_sp_benefit_item   WHERE c_program_id LIKE 'prog%';
DELETE FROM app_fd_sp_kpi            WHERE c_program_id LIKE 'prog%';

-- 2) Verify each programme has exactly one of each tab row (read-only check) ---
-- SELECT c_parent_id, COUNT(*) FROM app_fd_sp_program_geography WHERE c_parent_id LIKE 'prog%' GROUP BY c_parent_id;
-- SELECT c_parent_id, COUNT(*) FROM app_fd_sp_program_beneficr  WHERE c_parent_id LIKE 'prog%' GROUP BY c_parent_id;
-- SELECT c_parent_id, COUNT(*) FROM app_fd_sp_program_benefits  WHERE c_parent_id LIKE 'prog%' GROUP BY c_parent_id;
-- SELECT c_parent_id, COUNT(*) FROM app_fd_sp_program_monitor   WHERE c_parent_id LIKE 'prog%' GROUP BY c_parent_id;

-- =============================================================================
-- DISTRICT ALLOCATIONS — c_program_id = sp_program_geography.id of each prog
-- =============================================================================
-- prog001 (Input Subsidy) — 7 districts
INSERT INTO app_fd_sp_district_alloc (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id, c_districtCode, c_zoneCode, c_collectionPointCode, c_allocatedBudget, c_estimatedBeneficiaries, c_priorityRank, c_notes)
SELECT 'da-001-01', NOW(), NOW(), 'admin', 'admin', g.id, 'd01','z01','cp01', 9000000, 3600, 1, 'Maseru — largest allocation' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
UNION ALL SELECT 'da-001-02', NOW(), NOW(), 'admin', 'admin', g.id, 'd02','z01','cp02', 7500000, 3000, 2, 'Berea' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
UNION ALL SELECT 'da-001-03', NOW(), NOW(), 'admin', 'admin', g.id, 'd03','z01','cp02', 6500000, 2600, 3, 'Leribe' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
UNION ALL SELECT 'da-001-04', NOW(), NOW(), 'admin', 'admin', g.id, 'd04','z01','cp03', 5500000, 2200, 4, 'Mafeteng' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
UNION ALL SELECT 'da-001-05', NOW(), NOW(), 'admin', 'admin', g.id, 'd05','z01','cp03', 5000000, 2000, 5, 'Mohale''s Hoek' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
UNION ALL SELECT 'da-001-06', NOW(), NOW(), 'admin', 'admin', g.id, 'd06','z02','cp01', 5000000, 2000, 6, 'Quthing' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
UNION ALL SELECT 'da-001-07', NOW(), NOW(), 'admin', 'admin', g.id, 'd07','z02','cp01', 4000000, 1600, 7, 'Butha-Buthe' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog001'
-- prog002 (Mechanisation) — 4 districts
UNION ALL SELECT 'da-002-01', NOW(), NOW(), 'admin', 'admin', g.id, 'd01','z01','cp01', 6000000, 2000, 1, 'Maseru — largest cropping area' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog002'
UNION ALL SELECT 'da-002-02', NOW(), NOW(), 'admin', 'admin', g.id, 'd02','z01','cp02', 5000000, 1700, 2, 'Berea' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog002'
UNION ALL SELECT 'da-002-03', NOW(), NOW(), 'admin', 'admin', g.id, 'd03','z01','cp02', 4000000, 1400, 3, 'Leribe' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog002'
UNION ALL SELECT 'da-002-04', NOW(), NOW(), 'admin', 'admin', g.id, 'd05','z01','cp02', 3000000,  900, 4, 'Mohale''s Hoek' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog002'
-- prog003 (Block Farming) — 3 districts
UNION ALL SELECT 'da-003-01', NOW(), NOW(), 'admin', 'admin', g.id, 'd02','z01','cp02',10000000, 1000, 1, 'Berea pilot block' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog003'
UNION ALL SELECT 'da-003-02', NOW(), NOW(), 'admin', 'admin', g.id, 'd03','z01','cp02', 9000000,  900, 2, 'Leribe pilot block' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog003'
UNION ALL SELECT 'da-003-03', NOW(), NOW(), 'admin', 'admin', g.id, 'd04','z02','cp04', 6000000,  600, 3, 'Mafeteng pilot block' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog003'
-- prog004 (Vulnerable HH) — national, 10 districts
UNION ALL SELECT 'da-004-01', NOW(), NOW(), 'admin', 'admin', g.id, 'd01','z01','cp01', 1500000, 450, 1, 'Maseru' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-02', NOW(), NOW(), 'admin', 'admin', g.id, 'd02','z01','cp02', 1300000, 380, 2, 'Berea' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-03', NOW(), NOW(), 'admin', 'admin', g.id, 'd03','z01','cp02', 1200000, 360, 3, 'Leribe' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-04', NOW(), NOW(), 'admin', 'admin', g.id, 'd04','z02','cp04', 1100000, 330, 4, 'Mafeteng' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-05', NOW(), NOW(), 'admin', 'admin', g.id, 'd05','z02','cp04', 1100000, 330, 5, 'Mohale''s Hoek' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-06', NOW(), NOW(), 'admin', 'admin', g.id, 'd06','z03','cp05',  900000, 270, 6, 'Quthing' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-07', NOW(), NOW(), 'admin', 'admin', g.id, 'd07','z03','cp05',  900000, 270, 7, 'Butha-Buthe' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-08', NOW(), NOW(), 'admin', 'admin', g.id, 'd08','z04','cp03',  900000, 270, 8, 'Qacha''s Nek' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-09', NOW(), NOW(), 'admin', 'admin', g.id, 'd09','z04','cp03',  800000, 240, 9, 'Mokhotlong' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004'
UNION ALL SELECT 'da-004-10', NOW(), NOW(), 'admin', 'admin', g.id, 'd10','z04','cp03',  800000, 240,10, 'Thaba-Tseka' FROM app_fd_sp_program_geography g WHERE g.c_parent_id='prog004';

-- =============================================================================
-- TARGET GROUPS — c_program_id = sp_program_beneficr.id of each prog
-- =============================================================================
INSERT INTO app_fd_sp_target_group (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id, c_targetGroupCode, c_targetCategoryCode, c_estimatedCount, c_priorityWeight, c_allocationPercent, c_notes)
SELECT 'tg-001-01', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_SMALLHOLDER','SH', 12000, 1.0, 60, 'Smallholders ≤2 ha (general)' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog001'
UNION ALL SELECT 'tg-001-02', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_WOMEN_FARMER','WF', 4000, 1.2, 25, 'Women-headed households' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog001'
UNION ALL SELECT 'tg-001-03', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_YOUTH_FARMER','YF', 2000, 1.2, 15, 'Youth (18–35)' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog001'
UNION ALL SELECT 'tg-002-01', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_MEDIUM_FARMER','MF', 4500, 1.0, 75, 'Farmers with mechanisable land 0.5–5 ha' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog002'
UNION ALL SELECT 'tg-002-02', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_COOP','COOP', 900, 1.1, 15, 'Registered cooperatives' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog002'
UNION ALL SELECT 'tg-002-03', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_LARGE_FARMER','LF', 600, 0.9, 10, 'Larger farmers (≥5 ha) — capped' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog002'
UNION ALL SELECT 'tg-003-01', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_BLOCK_MEMBER','BM', 2000, 1.0, 80, 'Members of registered farming blocks' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog003'
UNION ALL SELECT 'tg-003-02', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_COOP','COOP', 500, 1.0, 20, 'Cooperatives operating blocks' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog003'
UNION ALL SELECT 'tg-004-01', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_ELDERLY','EL', 1500, 1.0, 35, 'Heads ≥65 yrs' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog004'
UNION ALL SELECT 'tg-004-02', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_DISABLED','DIS', 800, 1.2, 25, 'Disability-affected households' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog004'
UNION ALL SELECT 'tg-004-03', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_HIV','HIV', 1000, 1.1, 30, 'HIV-affected households' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog004'
UNION ALL SELECT 'tg-004-04', NOW(), NOW(), 'admin', 'admin', b.id, 'TG_ORPHAN_HEAD','OH', 200, 1.3, 10, 'Child/orphan-headed households' FROM app_fd_sp_program_beneficr b WHERE b.c_parent_id='prog004';

-- =============================================================================
-- BENEFIT ITEMS — c_program_id = sp_program_benefits.id of each prog
-- =============================================================================
INSERT INTO app_fd_sp_benefit_item (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id, c_itemType, c_itemCode, c_itemName, c_categoryCode, c_quantity, c_unit, c_unitCost, c_totalCost, c_subsidyPercent, c_subsidyAmount, c_farmerContribution, c_notes)
SELECT 'bi-001-01', NOW(), NOW(), 'admin', 'admin', x.id, 'INPUT','FERT_NPK','NPK Fertiliser 50kg','FERT', 1,'BAG', 1200, 1200, 50, 600, 600, 'Compound D 50kg' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog001'
UNION ALL SELECT 'bi-001-02', NOW(), NOW(), 'admin', 'admin', x.id, 'INPUT','FERT_LAN','LAN Fertiliser 50kg','FERT', 1,'BAG', 1100, 1100, 50, 550, 550, 'Top-dress' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog001'
UNION ALL SELECT 'bi-001-03', NOW(), NOW(), 'admin', 'admin', x.id, 'INPUT','SEED_MAIZE','Certified maize seed 25kg','SEED', 1,'BAG', 700, 700, 50, 350, 350, 'Hybrid recommended for lowlands' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog001'
UNION ALL SELECT 'bi-002-01', NOW(), NOW(), 'admin', 'admin', x.id, 'EQUIPMENT','TRAC_HIRE','Tractor hire / ha','MECH', 1,'HA', 1000, 1000, 60, 600, 400, 'Voucher per ha cultivated' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog002'
UNION ALL SELECT 'bi-002-02', NOW(), NOW(), 'admin', 'admin', x.id, 'EQUIPMENT','DRAUGHT','Draught animal day','MECH', 1,'DAY', 250, 250, 60, 150, 100, 'Where tractor unavailable' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog002'
UNION ALL SELECT 'bi-003-01', NOW(), NOW(), 'admin', 'admin', x.id, 'INPUT','BLOCK_FERT','Block fertiliser package','FERT', 50,'BAG', 1200, 60000, 50, 30000, 30000, 'Per 25-ha block' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog003'
UNION ALL SELECT 'bi-003-02', NOW(), NOW(), 'admin', 'admin', x.id, 'INPUT','BLOCK_SEED','Block seed package','SEED', 25,'BAG', 700, 17500, 50, 8750, 8750, 'Per 25-ha block' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog003'
UNION ALL SELECT 'bi-003-03', NOW(), NOW(), 'admin', 'admin', x.id, 'EQUIPMENT','BLOCK_TRAC','Block tractor hire','MECH', 25,'HA', 1000, 25000, 50, 12500, 12500, 'Per 25-ha block' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog003'
UNION ALL SELECT 'bi-004-01', NOW(), NOW(), 'admin', 'admin', x.id, 'INPUT','VULN_PKG','Full input package','PKG', 1,'PKG', 2500, 2500, 100, 2500, 0, 'Fertiliser + seed + tools' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog004'
UNION ALL SELECT 'bi-004-02', NOW(), NOW(), 'admin', 'admin', x.id, 'CASH','CASH_TRF','Cash transfer','CASH', 1,'TRANSFER', 3000, 3000, 100, 3000, 0, 'M-Pesa or bank — once per HH' FROM app_fd_sp_program_benefits x WHERE x.c_parent_id='prog004';

-- =============================================================================
-- KPIs — c_program_id = sp_program_monitor.id of each prog
-- =============================================================================
INSERT INTO app_fd_sp_kpi (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id, c_kpiName, c_kpiDescription, c_unit, c_baselineValue, c_currentValue, c_targetValue, c_formula, c_dataSource, c_frequency)
SELECT 'kpi-001-01', NOW(), NOW(), 'admin', 'admin', m.id, 'Beneficiaries enrolled', 'Count of distinct farmers with redeemed voucher', 'count', 0, 0, 18000, 'COUNT(distinct farmer_id) WHERE voucher_redeemed', 'app_fd_spProgParticip', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog001'
UNION ALL SELECT 'kpi-001-02', NOW(), NOW(), 'admin', 'admin', m.id, 'Voucher redemption rate', '% of issued vouchers redeemed', 'pct', 0, 0, 90, 'redeemed/issued*100', 'app_fd_spApplication', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog001'
UNION ALL SELECT 'kpi-001-03', NOW(), NOW(), 'admin', 'admin', m.id, 'Maize yield uplift', 'Mean yield delta vs baseline', 'kg_per_ha', 1100, 0, 1500, 'AVG(yield) on harvest forms', 'app_fd_spCropHarvestItem', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog001'
UNION ALL SELECT 'kpi-001-04', NOW(), NOW(), 'admin', 'admin', m.id, 'Women % of beneficiaries', 'Female-headed HH share', 'pct', 28, 0, 35, 'female_count/total*100', 'app_fd_spProgParticip', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog001'
UNION ALL SELECT 'kpi-002-01', NOW(), NOW(), 'admin', 'admin', m.id, 'Hectares mechanised', 'Total ha cultivated with subsidised tractor', 'ha', 0, 0, 30000, 'SUM(ha_voucher)', 'app_fd_spApplication', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog002'
UNION ALL SELECT 'kpi-002-02', NOW(), NOW(), 'admin', 'admin', m.id, 'Avg cost reduction per ha', 'Subsidised cost vs market cost', 'pct', 0, 0, 60, '1-paid/market_rate', 'app_fd_spApplication', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog002'
UNION ALL SELECT 'kpi-002-03', NOW(), NOW(), 'admin', 'admin', m.id, 'Tractor providers active', 'Distinct registered providers fulfilling vouchers', 'count', 8, 0, 25, 'COUNT(distinct provider)', 'app_fd_spApplication', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog002'
UNION ALL SELECT 'kpi-003-01', NOW(), NOW(), 'admin', 'admin', m.id, 'Blocks established', 'Number of formally registered blocks', 'count', 0, 0, 100, 'COUNT(blocks)', 'app_fd_spProgParticip', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog003'
UNION ALL SELECT 'kpi-003-02', NOW(), NOW(), 'admin', 'admin', m.id, 'Hectares under block farming', 'Total ha pooled across blocks', 'ha', 0, 0, 2500, 'SUM(ha)', 'app_fd_spProgParticip', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog003'
UNION ALL SELECT 'kpi-003-03', NOW(), NOW(), 'admin', 'admin', m.id, 'Yield uplift vs individual plot', 'Block yield delta', 'pct', 0, 0, 25, 'block-yield/plot-yield-1', 'app_fd_spCropHarvestItem', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog003'
UNION ALL SELECT 'kpi-003-04', NOW(), NOW(), 'admin', 'admin', m.id, 'Members per block (avg)', 'Avg farmer count per block', 'count', 0, 0, 30, 'AVG(member_count)', 'app_fd_spProgParticip', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog003'
UNION ALL SELECT 'kpi-004-01', NOW(), NOW(), 'admin', 'admin', m.id, 'Vulnerable HH reached', 'Distinct HHs receiving full package + cash', 'count', 0, 0, 3500, 'COUNT(distinct HH)', 'app_fd_spProgParticip', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog004'
UNION ALL SELECT 'kpi-004-02', NOW(), NOW(), 'admin', 'admin', m.id, 'Cash transfer success rate', '% of transfers settled within 7 days', 'pct', 0, 0, 95, 'settled_within_7d/total*100', 'app_fd_spApplication', 'MONTHLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog004'
UNION ALL SELECT 'kpi-004-03', NOW(), NOW(), 'admin', 'admin', m.id, 'Food-security score uplift', 'Mean HH food-security score delta', 'score', 2.1, 0, 3.5, 'AVG(post)-AVG(pre)', 'external_survey', 'ANNUAL' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog004'
UNION ALL SELECT 'kpi-004-04', NOW(), NOW(), 'admin', 'admin', m.id, 'Disability-HH reached', 'Subset of beneficiaries with disability flag', 'count', 0, 0, 800, 'COUNT WHERE disability=Y', 'app_fd_spProgParticip', 'QUARTERLY' FROM app_fd_sp_program_monitor m WHERE m.c_parent_id='prog004';

COMMIT;

-- =============================================================================
-- VERIFICATION
-- Expected: 24 district allocs, 12 target groups, 10 benefit items, 15 KPIs
-- =============================================================================
-- SELECT 'sp_district_alloc' tbl, COUNT(*) FROM app_fd_sp_district_alloc WHERE c_program_id IN (SELECT id FROM app_fd_sp_program_geography WHERE c_parent_id LIKE 'prog%')
-- UNION ALL SELECT 'sp_target_group', COUNT(*) FROM app_fd_sp_target_group WHERE c_program_id IN (SELECT id FROM app_fd_sp_program_beneficr WHERE c_parent_id LIKE 'prog%')
-- UNION ALL SELECT 'sp_benefit_item', COUNT(*) FROM app_fd_sp_benefit_item WHERE c_program_id IN (SELECT id FROM app_fd_sp_program_benefits WHERE c_parent_id LIKE 'prog%')
-- UNION ALL SELECT 'sp_kpi', COUNT(*) FROM app_fd_sp_kpi WHERE c_program_id IN (SELECT id FROM app_fd_sp_program_monitor WHERE c_parent_id LIKE 'prog%');
