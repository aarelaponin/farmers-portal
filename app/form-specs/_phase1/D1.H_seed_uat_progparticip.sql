-- =============================================================================
-- D1.H — Seed UAT spProgParticip rows so cross-program scoring rules can fire
-- Phase 1, Subsidy Adjustment Plan
-- Pre-state: app_fd_spprogparticip is empty (verified live).
-- Post-state: 8 fixture rows for 3 test farmers covering:
--   - recent participation (within 24 months) — pumps programsInLast2Seasons
--   - old participation (>24 months ago) — affects lifetime total but not season count
--   - mix of program types
-- After seeding, run FarmerDerivedRefresh scope=all to populate spFarmerDerived.
-- =============================================================================

-- Pre-requisite: 3 test farmers must exist in app_fd_farmerbasicinfo with
-- farmerCodes FR-T00001, FR-T00002, FR-T00003. Adjust if your test fixture uses
-- different codes.

DELETE FROM app_fd_spprogparticip
WHERE c_farmercode IN ('FR-T00001', 'FR-T00002', 'FR-T00003');

INSERT INTO app_fd_spprogparticip
    (id, datecreated, datemodified, createdby, modifiedby,
     c_participationid, c_farmercode, c_nationalid, c_farmername,
     c_programcode, c_programname, c_programtype,
     c_enrollmentdate, c_firstbenefitdate, c_lastbenefitdate,
     c_status, c_completiondate, c_statuschangedate, c_statuschangereason,
     c_totalbenefitentitled, c_totalbenefitreceived, c_totalfarmercontrib,
     c_benefitbalance, c_redemptioncount,
     c_enrolledby, c_enrollmentchannel,
     c_satisfactionrating, c_feedbackcomments,
     c_exitreason, c_exitnotes,
     c_seasoncode, c_benefitmodelcode)
VALUES

-- ============================================================================
-- FARMER FR-T00001 — heavy historical user (3 programmes, 1 recent)
-- Expected derived: programsInLast2Seasons=1, totalBenefitsReceivedLT=4500
-- ============================================================================
('pp-uat-001', NOW(), NOW(), 'system', 'system',
 'PP-T0001-A', 'FR-T00001', '1199001011001', 'Mpho Test One',
 'BLOCK_FARM_2024', 'Block Farming 2024 Maize', 'INPUT_SUBSIDY',
 '2024-09-15', '2024-10-01', '2024-12-15',
 'COMPLETED', '2025-01-30', '2025-01-30', 'season ended',
 1500, 1500, 0, 0, 3,
 'officer.maseru', 'IN_PERSON', 4, 'Inputs received on time',
 'COMPLETED', 'Successful season', '2024_25', 'IN_KIND'),

('pp-uat-002', NOW(), NOW(), 'system', 'system',
 'PP-T0001-B', 'FR-T00001', '1199001011001', 'Mpho Test One',
 'INPUT_2023', 'Input Subsidy Programme 2023', 'INPUT_SUBSIDY',
 '2023-09-10', '2023-10-05', '2024-01-20',
 'COMPLETED', '2024-02-15', '2024-02-15', 'season ended',
 1500, 1500, 0, 0, 2,
 'officer.maseru', 'IN_PERSON', 4, '',
 'COMPLETED', '', '2023_24', 'IN_KIND'),

('pp-uat-003', NOW(), NOW(), 'system', 'system',
 'PP-T0001-C', 'FR-T00001', '1199001011001', 'Mpho Test One',
 'EQUIPMENT_2022', 'Equipment Support 2022', 'EQUIPMENT',
 '2022-04-01', '2022-05-15', '2022-06-30',
 'COMPLETED', '2022-07-30', '2022-07-30', 'equipment delivered',
 1500, 1500, 200, 0, 1,
 'officer.maseru', 'IN_PERSON', 5, 'Tractor service excellent',
 'COMPLETED', '', '2022_23', 'PER_UNIT'),

-- ============================================================================
-- FARMER FR-T00002 — moderate user (2 programmes, both recent)
-- Expected derived: programsInLast2Seasons=2, totalBenefitsReceivedLT=2200
-- ============================================================================
('pp-uat-004', NOW(), NOW(), 'system', 'system',
 'PP-T0002-A', 'FR-T00002', '1198202022002', 'Lerato Test Two',
 'BLOCK_FARM_2024', 'Block Farming 2024 Maize', 'INPUT_SUBSIDY',
 '2024-09-20', '2024-10-05', '2024-12-20',
 'COMPLETED', '2025-01-30', '2025-01-30', '',
 1200, 1200, 0, 0, 2,
 'officer.berea', 'IN_PERSON', 4, '',
 'COMPLETED', '', '2024_25', 'IN_KIND'),

('pp-uat-005', NOW(), NOW(), 'system', 'system',
 'PP-T0002-B', 'FR-T00002', '1198202022002', 'Lerato Test Two',
 'CASH_2025', 'Drought Cash Transfer 2025', 'CASH_TRANSFER',
 '2025-03-15', '2025-04-01', '2025-04-01',
 'ACTIVE', NULL, '2025-04-01', '',
 1000, 1000, 0, 0, 1,
 'officer.berea', 'SMS', 5, 'Quick disbursement',
 '', '', '2024_25', 'FIXED'),

-- ============================================================================
-- FARMER FR-T00003 — first-time / minimal user (1 programme, recent, partial)
-- Expected derived: programsInLast2Seasons=1, totalBenefitsReceivedLT=400
-- ============================================================================
('pp-uat-006', NOW(), NOW(), 'system', 'system',
 'PP-T0003-A', 'FR-T00003', '1199503033003', 'Thabo Test Three',
 'INPUT_2025', 'Input Subsidy Programme 2025', 'INPUT_SUBSIDY',
 '2025-02-15', '2025-03-01', '2025-03-15',
 'ACTIVE', NULL, '2025-03-15', 'partial redemption',
 1500, 400, 0, 1100, 1,
 'officer.leribe', 'IN_PERSON', 3, 'Some inputs missing',
 '', '', '2024_25', 'IN_KIND'),

-- Two more programmes to test programsInLast2Seasons cap behavior:
-- This farmer is in 3 programmes in last 24 months (anti-dependency rule should fail)
('pp-uat-007', NOW(), NOW(), 'system', 'system',
 'PP-T0003-B', 'FR-T00003', '1199503033003', 'Thabo Test Three',
 'TRAINING_2024', 'Smallholder Training 2024', 'TRAINING',
 '2024-06-01', NULL, NULL,
 'COMPLETED', '2024-08-15', '2024-08-15', 'training completed',
 0, 0, 0, 0, 0,
 'officer.leribe', 'IN_PERSON', 5, '',
 'COMPLETED', '', '2024_25', 'TRAINING'),

('pp-uat-008', NOW(), NOW(), 'system', 'system',
 'PP-T0003-C', 'FR-T00003', '1199503033003', 'Thabo Test Three',
 'EMERGENCY_2024', 'Emergency Drought Relief 2024', 'EMERGENCY',
 '2024-03-01', '2024-03-10', '2024-03-25',
 'COMPLETED', '2024-04-15', '2024-04-15', 'crisis support',
 800, 800, 0, 0, 2,
 'officer.leribe', 'SMS', 4, 'Helped through drought',
 'COMPLETED', '', '2023_24', 'CASH');

-- ============================================================================
-- Verification queries
-- ============================================================================
-- Per-farmer rollup that FarmerDerivedRefresh should produce
SELECT
    c_farmercode,
    COUNT(*) AS total_participations,
    SUM(CAST(c_totalbenefitreceived AS NUMERIC)) AS lifetime_received_lsl,
    MAX(c_lastbenefitdate)::date AS last_benefit_date,
    COUNT(DISTINCT c_programcode) FILTER (WHERE c_enrollmentdate::date >= CURRENT_DATE - INTERVAL '24 months') AS programs_last_2_seasons
FROM app_fd_spprogparticip
WHERE c_farmercode IN ('FR-T00001', 'FR-T00002', 'FR-T00003')
GROUP BY c_farmercode
ORDER BY c_farmercode;

-- Expected output:
--  FR-T00001 | 3 | 4500 | 2024-12-15 | 1   (one programme in last 24 months)
--  FR-T00002 | 2 | 2200 | 2025-04-01 | 2
--  FR-T00003 | 3 |  800 | 2025-03-15 | 3   (anti-dependency rule should fail this one)
