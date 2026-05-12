-- =============================================================================
-- Subsidy Programmes Seed — v2 (corrected FK convention)
-- =============================================================================
-- Re-seeds 4 policy-aligned subsidy programmes after the v1 seed wiped.
--
-- KEY FIX from v1:
--   Child-grid rows (sp_district_alloc, sp_target_group, sp_benefit_item, sp_kpi)
--   now point c_program_id at the **TAB-form record id** (e.g. prog001-geog),
--   not the wizard parent id (prog001).
--
-- Why: Joget's MultirowFormBinder filters child rows by the IMMEDIATE parent
-- form's record id — and the FormGrid lives inside spProgramGeography (the tab),
-- not spProgramMain (the wizard). Verified against the working
-- household_members → farmer_household pattern (member.c_farmer_id =
-- farmer_household.id, NOT farms_registry.id).
--
-- Also wires the wizard's c_tab_* pointers (one per tab) to the matching
-- subform record id, mirroring how MultiPagedForm tracks tab linkage.
--
-- Apply with:
--   mysql -h <host> -u <user> -p jwdb < seed_subsidy_programmes_v2.sql
--
-- Then in Joget App Composer, open any form → Save (no edits) to evict cache.
-- =============================================================================

START TRANSACTION;

-- Clear any leftover prog* rows from prior attempts -----------------------------
DELETE FROM app_fd_sp_district_alloc WHERE c_program_id LIKE 'prog%';
DELETE FROM app_fd_sp_target_group   WHERE c_program_id LIKE 'prog%';
DELETE FROM app_fd_sp_benefit_item   WHERE c_program_id LIKE 'prog%';
DELETE FROM app_fd_sp_kpi            WHERE c_program_id LIKE 'prog%';

DELETE FROM app_fd_sp_program_identity  WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_timeline  WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_geography WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_eligiblt  WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_benefits  WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_beneficr  WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_approval  WHERE c_parent_id LIKE 'prog%';
DELETE FROM app_fd_sp_program_monitor   WHERE c_parent_id LIKE 'prog%';

DELETE FROM app_fd_sp_program WHERE id LIKE 'prog%';

-- =============================================================================
-- 4 WIZARD PARENT ROWS — id = prog001..prog004
-- The c_tab_* columns will be UPDATEd later once the tab records are inserted
-- =============================================================================
INSERT INTO app_fd_sp_program
  (id, dateCreated, dateModified, createdBy, createdByName, modifiedBy, modifiedByName,
   c_programCode, c_status, c_requestedStatus, c_submittedBy, c_submittedDate, c_approvedBy, c_approvedDate)
VALUES
  ('prog001', NOW(), NOW(), 'admin', 'Admin', 'admin', 'Admin',
   'INPUT-2026', 'APPROVED', 'APPROVED', 'admin', '2026-03-01 10:00:00', 'admin', '2026-03-15 14:30:00'),
  ('prog002', NOW(), NOW(), 'admin', 'Admin', 'admin', 'Admin',
   'MECH-2026',  'APPROVED', 'APPROVED', 'admin', '2026-03-05 11:00:00', 'admin', '2026-03-20 09:15:00'),
  ('prog003', NOW(), NOW(), 'admin', 'Admin', 'admin', 'Admin',
   'BLOCK-2026', 'PENDING_APPROVAL', 'APPROVED', 'admin', '2026-04-02 08:30:00', NULL, NULL),
  ('prog004', NOW(), NOW(), 'admin', 'Admin', 'admin', 'Admin',
   'VFS-2026',   'DRAFT', 'DRAFT', NULL, NULL, NULL, NULL);

-- =============================================================================
-- TAB 1 — IDENTITY  (id pattern: prog00X-id)
-- =============================================================================
INSERT INTO app_fd_sp_program_identity
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_programName, c_description, c_programType, c_campaignType, c_seasonCode, c_legalBasis, c_objectives, c_fundingSource)
VALUES
  ('prog001-id', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'Input Subsidy Programme 2025/26',
   'Subsidised fertiliser and certified seed for smallholder maize and sorghum farmers, aligned with the Lesotho Subsidy Policy (2003).',
   'INPUT_SUBSIDY', 'SEASONAL', '2025_26', 'Subsidy Policy 2003 §4.1',
   'Improve household and national cereal yield by reducing input cost barrier for smallholders.',
   'GOL'),
  ('prog002-id', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'Mechanisation Subsidy 2025/26',
   'Subsidised tractor hire and draught power for farmers with parcels >0.5 ha. Voucher-based.',
   'MECHANISATION', 'SEASONAL', '2025_26', 'Subsidy Policy 2003 §4.3',
   'Reduce labour cost of land preparation; raise cultivated hectarage.',
   'GOL'),
  ('prog003-id', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'Block Farming Initiative 2025/26',
   'Co-operative block-farming arrangement: pooled inputs, mechanisation and extension. 50% subsidy on inputs.',
   'BLOCK_FARMING', 'SEASONAL', '2025_26', 'Subsidy Policy 2003 §4.5',
   'Aggregate smallholders into productive blocks of ≥25 ha for economy of scale.',
   'GOL'),
  ('prog004-id', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'Vulnerable Household Food Security 2025/26',
   'Targeted 100% subsidy for elderly, disability and HIV-affected households. Cash-and-in-kind hybrid.',
   'VULNERABLE_HH', 'ANNUAL', '2025_26', 'Subsidy Policy 2003 §4.4 + Disability Act',
   'Protect food-insecure households via fully-subsidised input package + cash transfer.',
   'GOL+DEV_PARTNERS');

-- =============================================================================
-- TAB 2 — TIMELINE & BUDGET  (id pattern: prog00X-time)
-- =============================================================================
INSERT INTO app_fd_sp_program_timeline
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_programStartDate, c_programEndDate, c_applicationStartDate, c_applicationEndDate,
   c_distributionStartDate, c_distributionEndDate,
   c_totalBudget, c_adminBudget, c_availableBudget, c_reservePercentage,
   c_estimatedBeneficiaries, c_maxBenefitPerFarmer, c_fixedBenefitAmount, c_subsidyPercentage,
   c_benefitModelCode, c_allocBasisCode, c_budgetNotes)
VALUES
  ('prog001-time', NOW(), NOW(), 'admin', 'admin', 'prog001',
   '2026-03-15','2026-12-31','2026-04-01','2026-06-30','2026-07-15','2026-09-30',
   45000000, 2500000, 42500000, 10,
   18000, 2500, 0, 50,
   'INPUT_VOUCHER','PER_HA','Budget LSL 45M, 50% subsidy on fertiliser + seed up to LSL 2,500/farmer'),
  ('prog002-time', NOW(), NOW(), 'admin', 'admin', 'prog002',
   '2026-03-20','2026-11-30','2026-04-15','2026-06-15','2026-08-01','2026-10-15',
   18000000, 1200000, 16800000, 8,
   6000, 3000, 0, 60,
   'TRACTOR_VOUCHER','PER_HA','Voucher per ha cultivated, max 5 ha/farmer'),
  ('prog003-time', NOW(), NOW(), 'admin', 'admin', 'prog003',
   '2026-04-15','2027-06-30','2026-04-20','2026-07-31','2026-08-15','2026-10-31',
   25000000, 2000000, 23000000, 10,
   2500, 10000, 0, 50,
   'BLOCK_BENEFIT','BLOCK_QUOTA','Per-block disbursement; minimum block size 25 ha and 30 farmers'),
  ('prog004-time', NOW(), NOW(), 'admin', 'admin', 'prog004',
   '2026-05-01','2026-12-31','2026-05-01','2026-08-31','2026-09-15','2026-11-30',
   12000000, 800000, 11200000, 15,
   3500, 3500, 3000, 100,
   'CASH_IN_KIND','TARGETED','100% subsidy + LSL 3,000 cash transfer per qualifying household');

-- =============================================================================
-- TAB 3 — GEOGRAPHY  (id pattern: prog00X-geog)
-- =============================================================================
INSERT INTO app_fd_sp_program_geography
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_geographicScope, c_targetDistricts, c_targetZones, c_collectionPoints, c_geographyNotes)
VALUES
  ('prog001-geog', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'DISTRICT', 'd01;d02;d03;d04;d05;d06;d07', 'z01;z02', 'cp01;cp02;cp03',
   'Targeting all 7 lowland & foothill districts; mountain districts excluded for logistics.'),
  ('prog002-geog', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'DISTRICT', 'd01;d02;d03;d05', 'z01', 'cp01;cp02',
   'Lowland districts only — mechanisable terrain.'),
  ('prog003-geog', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'DISTRICT', 'd02;d03;d04', 'z01;z02', 'cp02;cp04',
   'Pilot in 3 high-yield districts; expansion based on Year-1 results.'),
  ('prog004-geog', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'NATIONAL', 'd01;d02;d03;d04;d05;d06;d07;d08;d09;d10', 'z01;z02;z03;z04', 'cp01;cp02;cp03;cp04;cp05',
   'National coverage — vulnerable households exist everywhere.');

-- =============================================================================
-- TAB 4 — BENEFICIARY TARGETING  (id pattern: prog00X-bene)
-- =============================================================================
INSERT INTO app_fd_sp_program_beneficr
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_targetingStrategy, c_priorityModel, c_maxBenefitsPerFarmer,
   c_requiresApplication, c_autoEnrollEligible, c_allowMultipleBenefits, c_beneficiaryNotes)
VALUES
  ('prog001-bene', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'CATEGORICAL','PRIORITY_SCORE', 1, 'true','false','false',
   'Smallholders ≤2 ha; women & youth get +20% priority weight.'),
  ('prog002-bene', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'COMBINED','FCFS', 1, 'true','false','false',
   'Mechanisable parcels ≥0.5 ha. First-come-first-served per district until quota fills.'),
  ('prog003-bene', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'GEOGRAPHIC','COMMUNITY', 1, 'true','false','true',
   'Block-level targeting; community committee selects member farmers.'),
  ('prog004-bene', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'CATEGORICAL','PRIORITY_SCORE', 1, 'true','true','false',
   'Auto-enrol if registered as elderly OR disabled OR HIV-affected on farmer profile.');

-- =============================================================================
-- TAB 5 — BENEFITS  (id pattern: prog00X-bnft)
-- =============================================================================
INSERT INTO app_fd_sp_program_benefits
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_distribModelCode, c_inputPackageCode, c_allowedPaymentMethods,
   c_allowPartialBenefit, c_requiresVerification, c_benefitDescription, c_benefitNotes)
VALUES
  ('prog001-bnft', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'VOUCHER','PKG_MAIZE_STD','VOUCHER;BANK', 'true','true',
   '50% subsidy: 50kg fertiliser bag + 25kg certified maize seed per farmer (cap LSL 2,500).',
   'Redeemable at gazetted agro-dealers only. Voucher invalid past 2026-09-30.'),
  ('prog002-bnft', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'VOUCHER','PKG_MECH_STD','VOUCHER', 'true','true',
   'Tractor-hire voucher LSL 600/ha × max 5 ha (60% of average market rate).',
   'Voucher redeemed at registered tractor service providers.'),
  ('prog003-bnft', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'BULK_DELIVERY','PKG_BLOCK','VOUCHER;BANK', 'false','true',
   'Bulk inputs delivered to block + 50% subsidy on tractor hire + free extension visits.',
   'Inputs released only after block plan + member list signed off.'),
  ('prog004-bnft', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'CASH_IN_KIND','PKG_VULN_FULL','CASH;BANK;MOBILE', 'false','true',
   'Full input package (fertiliser+seed+tools) + LSL 3,000 cash transfer per HH.',
   'Cash transferred via M-Pesa or bank to verified vulnerable household.');

-- =============================================================================
-- TAB 6 — ELIGIBILITY  (id pattern: prog00X-elig)
-- =============================================================================
INSERT INTO app_fd_sp_program_eligiblt
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_evaluationStrategy, c_passingThreshold, c_minimumScore, c_eligibilityNotes)
VALUES
  ('prog001-elig', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'ALL_MUST_PASS', 100, 0,
   'Active farmer profile + at least 1 registered parcel ≤2 ha + previous-season cereal cultivation.'),
  ('prog002-elig', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'ALL_MUST_PASS', 100, 0,
   'Active farmer + parcel ≥0.5 ha + parcel slope ≤8% (mechanisable).'),
  ('prog003-elig', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'SCORE_BASED', 60, 60,
   'Score >=60: cooperative member (40), >=0.5 ha contributed (30), block plan submitted (30).'),
  ('prog004-elig', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'ALL_MUST_PASS', 100, 0,
   'Vulnerability flag set (elderly OR disabled OR HIV-affected) on registered farmer profile.');

-- =============================================================================
-- TAB 7 — APPROVAL  (id pattern: prog00X-appr)
-- =============================================================================
INSERT INTO app_fd_sp_program_approval
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_approvalWorkflow, c_supervisorName, c_supervisorEmail, c_programOfficer, c_contactEmail, c_contactPhone,
   c_publishDate, c_isActive, c_requiredDocuments, c_notificationTypes, c_approverComments, c_approvalNotes, c_rejectionReason)
VALUES
  ('prog001-appr', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'STANDARD','Mr. M. Letsie','m.letsie@maf.gov.ls','Ms. T. Khoaeli','t.khoaeli@maf.gov.ls','+266 2231 1234',
   '2026-04-01', 'true', 'Annual budget memo;District allocation matrix','EMAIL;SMS','Approved as per Cabinet decision 2026/14.',
   'Standard approval flow followed.', NULL),
  ('prog002-appr', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'STANDARD','Mr. M. Letsie','m.letsie@maf.gov.ls','Mr. P. Ramoholi','p.ramoholi@maf.gov.ls','+266 2231 1245',
   '2026-04-15', 'true', 'Tractor service provider register;Voucher denomination schedule','EMAIL','Approved.',
   'Subject to mid-season review.', NULL),
  ('prog003-appr', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'EXPRESS','Dr. N. Maliehe','n.maliehe@maf.gov.ls','Ms. L. Mokoena','l.mokoena@maf.gov.ls','+266 2231 1267',
   NULL, 'false', 'Block charter template;Cooperative registration certificate','EMAIL;SMS', NULL,
   'Pending review by Department of Agricultural Research.', NULL),
  ('prog004-appr', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'EMERGENCY',NULL,NULL,'Ms. K. Sechele','k.sechele@maf.gov.ls','+266 2231 1289',
   NULL, 'false', 'Vulnerability registry extract;NID verification batch','SMS;EMAIL', NULL,
   'Draft pending budget confirmation.', NULL);

-- =============================================================================
-- TAB 8 — MONITORING  (id pattern: prog00X-mon)
-- =============================================================================
INSERT INTO app_fd_sp_program_monitor
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_parent_id,
   c_monitoringApproach, c_dataCollectionMethod, c_reportingFrequency,
   c_baselineRequired, c_midtermReview, c_finalEvaluation,
   c_evaluationStartDate, c_evaluationEndDate, c_monitoringNotes)
VALUES
  ('prog001-mon', NOW(), NOW(), 'admin', 'admin', 'prog001',
   'CONTINUOUS','SYSTEM_AUTO','MONTHLY', 'true','true','true',
   '2026-03-15','2026-12-31','Monthly redemption + harvest reports; midterm at 2026-08-15; final at 2026-12-15.'),
  ('prog002-mon', NOW(), NOW(), 'admin', 'admin', 'prog002',
   'PERIODIC','MIXED','QUARTERLY', 'false','true','true',
   '2026-04-01','2026-11-30','Quarterly voucher-redemption + ha-cultivated reports from providers.'),
  ('prog003-mon', NOW(), NOW(), 'admin', 'admin', 'prog003',
   'CONTINUOUS','FIELD_REPORTS','MONTHLY', 'true','true','true',
   '2026-05-01','2027-06-30','Block-level monthly visits; baseline yield benchmark prior to inputs.'),
  ('prog004-mon', NOW(), NOW(), 'admin', 'admin', 'prog004',
   'POST_HOC','SURVEY','ANNUAL', 'true','false','true',
   '2026-09-01','2026-12-31','Post-distribution household survey on food security indicators.');

-- =============================================================================
-- BACK-FILL the wizard's c_tab_* pointers so list_spProgramMain JOINs work
-- =============================================================================
UPDATE app_fd_sp_program SET
  c_tab_identity     = CONCAT(id,'-id'),
  c_tab_timeline     = CONCAT(id,'-time'),
  c_tab_geography    = CONCAT(id,'-geog'),
  c_tab_beneficiary  = CONCAT(id,'-bene'),
  c_tab_benefits     = CONCAT(id,'-bnft'),
  c_tab_eligibility  = CONCAT(id,'-elig'),
  c_tab_approval     = CONCAT(id,'-appr'),
  c_tab_monitoring   = CONCAT(id,'-mon')
WHERE id LIKE 'prog%';

-- =============================================================================
-- CHILD GRID: District Allocations  (under TAB 3 Geography)
-- c_program_id = the GEOGRAPHY-TAB record id (prog00X-geog) — NOT the wizard id!
-- =============================================================================
INSERT INTO app_fd_sp_district_alloc
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id,
   c_districtCode, c_zoneCode, c_collectionPointCode, c_allocatedBudget, c_estimatedBeneficiaries, c_priorityRank, c_notes)
VALUES
  -- prog001 (Input Subsidy) — 7 lowland/foothill districts
  ('da-001-01', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd01','z01','cp01', 9000000, 3600, 1, 'Maseru — largest allocation'),
  ('da-001-02', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd02','z01','cp02', 7500000, 3000, 2, 'Berea'),
  ('da-001-03', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd03','z01','cp02', 6500000, 2600, 3, 'Leribe'),
  ('da-001-04', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd04','z01','cp03', 5500000, 2200, 4, 'Mafeteng'),
  ('da-001-05', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd05','z01','cp03', 5000000, 2000, 5, 'Mohales Hoek'),
  ('da-001-06', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd06','z02','cp01', 5000000, 2000, 6, 'Quthing'),
  ('da-001-07', NOW(), NOW(), 'admin', 'admin', 'prog001-geog', 'd07','z02','cp01', 4000000, 1600, 7, 'Butha-Buthe'),
  -- prog002 (Mechanisation) — 4 districts
  ('da-002-01', NOW(), NOW(), 'admin', 'admin', 'prog002-geog', 'd01','z01','cp01', 6000000, 2000, 1, 'Maseru — largest cropping area'),
  ('da-002-02', NOW(), NOW(), 'admin', 'admin', 'prog002-geog', 'd02','z01','cp02', 5000000, 1700, 2, 'Berea'),
  ('da-002-03', NOW(), NOW(), 'admin', 'admin', 'prog002-geog', 'd03','z01','cp02', 4000000, 1400, 3, 'Leribe'),
  ('da-002-04', NOW(), NOW(), 'admin', 'admin', 'prog002-geog', 'd05','z01','cp02', 3000000, 900,  4, 'Mohales Hoek'),
  -- prog003 (Block Farming) — 3 pilot districts
  ('da-003-01', NOW(), NOW(), 'admin', 'admin', 'prog003-geog', 'd02','z01','cp02', 10000000, 1000, 1, 'Berea pilot block'),
  ('da-003-02', NOW(), NOW(), 'admin', 'admin', 'prog003-geog', 'd03','z01','cp02',  9000000,  900, 2, 'Leribe pilot block'),
  ('da-003-03', NOW(), NOW(), 'admin', 'admin', 'prog003-geog', 'd04','z02','cp04',  6000000,  600, 3, 'Mafeteng pilot block'),
  -- prog004 (Vulnerable HH) — national, all 10 districts
  ('da-004-01', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd01','z01','cp01', 1500000, 450, 1, 'Maseru'),
  ('da-004-02', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd02','z01','cp02', 1300000, 380, 2, 'Berea'),
  ('da-004-03', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd03','z01','cp02', 1200000, 360, 3, 'Leribe'),
  ('da-004-04', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd04','z02','cp04', 1100000, 330, 4, 'Mafeteng'),
  ('da-004-05', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd05','z02','cp04', 1100000, 330, 5, 'Mohales Hoek'),
  ('da-004-06', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd06','z03','cp05',  900000, 270, 6, 'Quthing'),
  ('da-004-07', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd07','z03','cp05',  900000, 270, 7, 'Butha-Buthe'),
  ('da-004-08', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd08','z04','cp03',  900000, 270, 8, 'Qachas Nek'),
  ('da-004-09', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd09','z04','cp03',  800000, 240, 9, 'Mokhotlong'),
  ('da-004-10', NOW(), NOW(), 'admin', 'admin', 'prog004-geog', 'd10','z04','cp03',  800000, 240, 10,'Thaba-Tseka');

-- =============================================================================
-- CHILD GRID: Target Groups  (under TAB 4 Beneficiary)
-- c_program_id = BENEFICIARY-TAB record id (prog00X-bene)
-- =============================================================================
INSERT INTO app_fd_sp_target_group
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id,
   c_targetGroupCode, c_targetCategoryCode, c_estimatedCount, c_priorityWeight, c_allocationPercent, c_notes)
VALUES
  -- prog001 (Input)
  ('tg-001-01', NOW(), NOW(), 'admin', 'admin', 'prog001-bene', 'TG_SMALLHOLDER','SH', 12000, 1.0, 60, 'Smallholders ≤2 ha (general)'),
  ('tg-001-02', NOW(), NOW(), 'admin', 'admin', 'prog001-bene', 'TG_WOMEN_FARMER','WF',  4000, 1.2, 25, 'Women-headed households'),
  ('tg-001-03', NOW(), NOW(), 'admin', 'admin', 'prog001-bene', 'TG_YOUTH_FARMER','YF',  2000, 1.2, 15, 'Youth (18–35)'),
  -- prog002 (Mechanisation)
  ('tg-002-01', NOW(), NOW(), 'admin', 'admin', 'prog002-bene', 'TG_MEDIUM_FARMER','MF', 4500, 1.0, 75, 'Farmers with mechanisable land 0.5–5 ha'),
  ('tg-002-02', NOW(), NOW(), 'admin', 'admin', 'prog002-bene', 'TG_COOP','COOP',         900, 1.1, 15, 'Registered cooperatives'),
  ('tg-002-03', NOW(), NOW(), 'admin', 'admin', 'prog002-bene', 'TG_LARGE_FARMER','LF',   600, 0.9, 10, 'Larger farmers (≥5 ha) — capped'),
  -- prog003 (Block Farming)
  ('tg-003-01', NOW(), NOW(), 'admin', 'admin', 'prog003-bene', 'TG_BLOCK_MEMBER','BM',  2000, 1.0, 80, 'Members of registered farming blocks'),
  ('tg-003-02', NOW(), NOW(), 'admin', 'admin', 'prog003-bene', 'TG_COOP','COOP',          500, 1.0, 20, 'Cooperatives operating blocks'),
  -- prog004 (Vulnerable HH)
  ('tg-004-01', NOW(), NOW(), 'admin', 'admin', 'prog004-bene', 'TG_ELDERLY','EL', 1500, 1.0, 35, 'Heads ≥65 yrs'),
  ('tg-004-02', NOW(), NOW(), 'admin', 'admin', 'prog004-bene', 'TG_DISABLED','DIS', 800, 1.2, 25, 'Disability-affected households'),
  ('tg-004-03', NOW(), NOW(), 'admin', 'admin', 'prog004-bene', 'TG_HIV','HIV',     1000, 1.1, 30, 'HIV-affected households'),
  ('tg-004-04', NOW(), NOW(), 'admin', 'admin', 'prog004-bene', 'TG_ORPHAN_HEAD','OH', 200, 1.3, 10, 'Child/orphan-headed households');

-- =============================================================================
-- CHILD GRID: Benefit Items  (under TAB 5 Benefits)
-- c_program_id = BENEFITS-TAB record id (prog00X-bnft)
-- =============================================================================
INSERT INTO app_fd_sp_benefit_item
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id,
   c_itemType, c_itemCode, c_itemName, c_categoryCode, c_quantity, c_unit, c_unitCost, c_totalCost, c_subsidyPercent, c_subsidyAmount, c_farmerContribution, c_notes)
VALUES
  -- prog001 (Input) — 3 items per farmer
  ('bi-001-01', NOW(), NOW(), 'admin', 'admin', 'prog001-bnft', 'INPUT','FERT_NPK','NPK Fertiliser 50kg','FERT', 1,'BAG', 1200, 1200, 50, 600, 600, 'Compound D 50kg'),
  ('bi-001-02', NOW(), NOW(), 'admin', 'admin', 'prog001-bnft', 'INPUT','FERT_LAN','LAN Fertiliser 50kg','FERT', 1,'BAG', 1100, 1100, 50, 550, 550, 'Top-dress'),
  ('bi-001-03', NOW(), NOW(), 'admin', 'admin', 'prog001-bnft', 'INPUT','SEED_MAIZE','Certified maize seed 25kg','SEED',1,'BAG', 700, 700, 50, 350, 350, 'Hybrid recommended for lowlands'),
  -- prog002 (Mech) — 2 items
  ('bi-002-01', NOW(), NOW(), 'admin', 'admin', 'prog002-bnft', 'EQUIPMENT','TRAC_HIRE','Tractor hire / ha','MECH', 1,'HA', 1000, 1000, 60, 600, 400, 'Voucher per ha cultivated'),
  ('bi-002-02', NOW(), NOW(), 'admin', 'admin', 'prog002-bnft', 'EQUIPMENT','DRAUGHT','Draught animal day','MECH', 1,'DAY', 250, 250, 60, 150, 100, 'Where tractor unavailable'),
  -- prog003 (Block) — 3 items
  ('bi-003-01', NOW(), NOW(), 'admin', 'admin', 'prog003-bnft', 'INPUT','BLOCK_FERT','Block fertiliser package','FERT', 50,'BAG', 1200, 60000, 50, 30000, 30000, 'Per 25-ha block'),
  ('bi-003-02', NOW(), NOW(), 'admin', 'admin', 'prog003-bnft', 'INPUT','BLOCK_SEED','Block seed package','SEED', 25,'BAG', 700, 17500, 50, 8750, 8750, 'Per 25-ha block'),
  ('bi-003-03', NOW(), NOW(), 'admin', 'admin', 'prog003-bnft', 'EQUIPMENT','BLOCK_TRAC','Block tractor hire','MECH', 25,'HA', 1000, 25000, 50, 12500, 12500, 'Per 25-ha block'),
  -- prog004 (Vulnerable) — 2 items
  ('bi-004-01', NOW(), NOW(), 'admin', 'admin', 'prog004-bnft', 'INPUT','VULN_PKG','Full input package','PKG', 1,'PKG', 2500, 2500, 100, 2500, 0, 'Fertiliser + seed + tools'),
  ('bi-004-02', NOW(), NOW(), 'admin', 'admin', 'prog004-bnft', 'CASH','CASH_TRF','Cash transfer','CASH', 1,'TRANSFER', 3000, 3000, 100, 3000, 0, 'M-Pesa or bank — once per HH');

-- =============================================================================
-- CHILD GRID: KPIs  (under TAB 8 Monitoring)
-- c_program_id = MONITORING-TAB record id (prog00X-mon)
-- =============================================================================
INSERT INTO app_fd_sp_kpi
  (id, dateCreated, dateModified, createdBy, modifiedBy, c_program_id,
   c_kpiName, c_kpiDescription, c_unit, c_baselineValue, c_currentValue, c_targetValue, c_formula, c_dataSource, c_frequency)
VALUES
  -- prog001 (Input) — 4 KPIs
  ('kpi-001-01', NOW(), NOW(), 'admin', 'admin', 'prog001-mon', 'Beneficiaries enrolled',           'Count of distinct farmers with redeemed voucher',                'count',     0,    0, 18000, 'COUNT(distinct farmer_id) WHERE voucher_redeemed','app_fd_spProgParticip','MONTHLY'),
  ('kpi-001-02', NOW(), NOW(), 'admin', 'admin', 'prog001-mon', 'Voucher redemption rate',          '% of issued vouchers redeemed',                                  'pct',       0,    0, 90,    'redeemed/issued*100','app_fd_spApplication','MONTHLY'),
  ('kpi-001-03', NOW(), NOW(), 'admin', 'admin', 'prog001-mon', 'Maize yield uplift',               'Mean yield delta vs baseline',                                   'kg_per_ha', 1100, 0, 1500,  'AVG(yield) on harvest forms','app_fd_spCropHarvestItem','QUARTERLY'),
  ('kpi-001-04', NOW(), NOW(), 'admin', 'admin', 'prog001-mon', 'Women % of beneficiaries',         'Female-headed HH share',                                         'pct',       28,   0, 35,    'female_count/total*100','app_fd_spProgParticip','MONTHLY'),
  -- prog002 (Mech) — 3 KPIs
  ('kpi-002-01', NOW(), NOW(), 'admin', 'admin', 'prog002-mon', 'Hectares mechanised',              'Total ha cultivated with subsidised tractor',                    'ha',        0,    0, 30000, 'SUM(ha_voucher)','app_fd_spApplication','QUARTERLY'),
  ('kpi-002-02', NOW(), NOW(), 'admin', 'admin', 'prog002-mon', 'Avg cost reduction per ha',        'Subsidised cost vs market cost',                                 'pct',       0,    0, 60,    '1-paid/market_rate','app_fd_spApplication','QUARTERLY'),
  ('kpi-002-03', NOW(), NOW(), 'admin', 'admin', 'prog002-mon', 'Tractor providers active',         'Distinct registered providers fulfilling vouchers',              'count',     8,    0, 25,    'COUNT(distinct provider)','app_fd_spApplication','QUARTERLY'),
  -- prog003 (Block) — 4 KPIs
  ('kpi-003-01', NOW(), NOW(), 'admin', 'admin', 'prog003-mon', 'Blocks established',               'Number of formally registered blocks',                           'count',     0,    0, 100,   'COUNT(blocks)','app_fd_spProgParticip','MONTHLY'),
  ('kpi-003-02', NOW(), NOW(), 'admin', 'admin', 'prog003-mon', 'Hectares under block farming',     'Total ha pooled across blocks',                                  'ha',        0,    0, 2500,  'SUM(ha)','app_fd_spProgParticip','MONTHLY'),
  ('kpi-003-03', NOW(), NOW(), 'admin', 'admin', 'prog003-mon', 'Yield uplift vs individual plot',  'Block yield delta',                                              'pct',       0,    0, 25,    'block-yield/plot-yield-1','app_fd_spCropHarvestItem','QUARTERLY'),
  ('kpi-003-04', NOW(), NOW(), 'admin', 'admin', 'prog003-mon', 'Members per block (avg)',          'Avg farmer count per block',                                     'count',     0,    0, 30,    'AVG(member_count)','app_fd_spProgParticip','QUARTERLY'),
  -- prog004 (Vulnerable HH) — 4 KPIs
  ('kpi-004-01', NOW(), NOW(), 'admin', 'admin', 'prog004-mon', 'Vulnerable HH reached',            'Distinct HHs receiving full package + cash',                     'count',     0,    0, 3500,  'COUNT(distinct HH)','app_fd_spProgParticip','MONTHLY'),
  ('kpi-004-02', NOW(), NOW(), 'admin', 'admin', 'prog004-mon', 'Cash transfer success rate',       '% of transfers settled within 7 days',                           'pct',       0,    0, 95,    'settled_within_7d/total*100','app_fd_spApplication','MONTHLY'),
  ('kpi-004-03', NOW(), NOW(), 'admin', 'admin', 'prog004-mon', 'Food-security score uplift',       'Mean HH food-security score delta',                              'score',     2.1,  0, 3.5,   'AVG(post)-AVG(pre)','external_survey','ANNUAL'),
  ('kpi-004-04', NOW(), NOW(), 'admin', 'admin', 'prog004-mon', 'Disability-HH reached',            'Subset of beneficiaries with disability flag',                   'count',     0,    0, 800,   'COUNT WHERE disability=Y','app_fd_spProgParticip','QUARTERLY');

COMMIT;

-- =============================================================================
-- VERIFICATION (run after the seed; expected counts in comment)
-- =============================================================================
-- SELECT 'sp_program' AS tbl, COUNT(*) FROM app_fd_sp_program          -- 4
-- UNION ALL SELECT 'sp_program_identity', COUNT(*) FROM app_fd_sp_program_identity  -- 4
-- UNION ALL SELECT 'sp_program_timeline', COUNT(*) FROM app_fd_sp_program_timeline  -- 4
-- UNION ALL SELECT 'sp_program_geography', COUNT(*) FROM app_fd_sp_program_geography -- 4
-- UNION ALL SELECT 'sp_program_eligiblt', COUNT(*) FROM app_fd_sp_program_eligiblt   -- 4
-- UNION ALL SELECT 'sp_program_benefits', COUNT(*) FROM app_fd_sp_program_benefits   -- 4
-- UNION ALL SELECT 'sp_program_beneficr', COUNT(*) FROM app_fd_sp_program_beneficr   -- 4
-- UNION ALL SELECT 'sp_program_approval', COUNT(*) FROM app_fd_sp_program_approval   -- 4
-- UNION ALL SELECT 'sp_program_monitor', COUNT(*) FROM app_fd_sp_program_monitor     -- 4
-- UNION ALL SELECT 'sp_district_alloc', COUNT(*) FROM app_fd_sp_district_alloc       -- 24
-- UNION ALL SELECT 'sp_target_group', COUNT(*) FROM app_fd_sp_target_group           -- 12
-- UNION ALL SELECT 'sp_benefit_item', COUNT(*) FROM app_fd_sp_benefit_item           -- 10
-- UNION ALL SELECT 'sp_kpi', COUNT(*) FROM app_fd_sp_kpi;                            -- 15
