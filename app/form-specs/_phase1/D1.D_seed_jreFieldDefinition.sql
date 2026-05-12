-- =============================================================================
-- D1.D — Seed jreFieldScope + jreFieldDefinition (replaces obsolete spEligField seed)
-- Phase 1 (revised after gs-plugins inventory)
--
-- Pre-state: app_fd_jrefielddefinition has 0 rows; app_fd_jrefieldscope 0 rows.
-- Post-state: 1 scope (FARMER_APPLICATION) + 18 evaluable fields.
-- Idempotent: re-running upserts on c_fieldid (within the FARMER_APPLICATION scope)
--             and on c_scopecode for jreFieldScope.
--
-- Schema (verified 2026-04-25 against live DB):
--
--   app_fd_jrefieldscope (12 cols):
--     c_scopecode, c_scopename, c_contexttype, c_description, c_isactive
--
--   app_fd_jrefielddefinition (21 cols):
--     c_fieldid, c_fieldlabel, c_category (FK md51), c_fieldtype,
--     c_scopecode (FK jreFieldScope), c_isgrid, c_gridparentfield,
--     c_lookupformid, c_lookupvalues, c_applicableoperators,
--     c_aggregationfunctions, c_helptext, c_displayorder, c_isactive
-- =============================================================================

-- ============================================================================
-- 1. Define the scope used by all eligibility rulesets in Phase 1
-- ============================================================================
DELETE FROM app_fd_jrefieldscope WHERE c_scopecode = 'FARMER_APPLICATION';

INSERT INTO app_fd_jrefieldscope
    (id, datecreated, datemodified, createdby, modifiedby,
     c_scopecode, c_scopename, c_contexttype, c_description, c_isactive)
VALUES
('jrescope-farmer-app', NOW(), NOW(), 'system', 'system',
 'FARMER_APPLICATION', 'Farmer in Application Context', 'PROGRAM',
 'Fields available when evaluating a farmer''s eligibility for a subsidy programme. Combines static farmer attributes with derived (spFarmerDerived) and program-history fields.', 'Y');

-- ============================================================================
-- 2. Field definitions
-- ============================================================================
DELETE FROM app_fd_jrefielddefinition
WHERE c_scopecode = 'FARMER_APPLICATION'
  AND c_fieldid IN (
    'farmer_age', 'farmer_gender', 'marital_status',
    'district_code', 'agro_zone', 'residency_type',
    'household_size', 'dependents_under_18', 'female_headed_household', 'disability_count',
    'total_land_ha', 'cultivated_land_ha', 'has_livestock',
    'annual_income_lsl', 'vulnerability_score',
    'food_security_status',
    'programs_last_2_seasons', 'total_benefits_lifetime'
  );

-- Convention used:
--   c_lookupvalues = static option list as JSON array (used for non-LOOKUP enums like gender)
--                    OR empty (when c_lookupformid is set, the rule editor reads from that form)
--   c_applicableoperators = comma-separated list of operators valid for the field type
--   c_aggregationfunctions = comma-separated list (only meaningful for grid fields)
--   c_isgrid = 'N' for all Phase 1 fields (none are grid-aggregated yet)

INSERT INTO app_fd_jrefielddefinition
    (id, datecreated, datemodified, createdby, modifiedby,
     c_fieldid, c_fieldlabel, c_category, c_fieldtype,
     c_scopecode, c_isgrid, c_gridparentfield,
     c_lookupformid, c_lookupvalues,
     c_applicableoperators, c_aggregationfunctions,
     c_helptext, c_displayorder, c_isactive)
VALUES
-- ============== DEMOGRAPHIC (3) ==============
('jre-farmer_age', NOW(), NOW(), 'system', 'system',
 'farmer_age', 'Farmer Age', 'DEMOGRAPHIC', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Age in years of the household head (sourced from spFarmerDerived.headAge)', 1, 'Y'),

('jre-farmer_gender', NOW(), NOW(), 'system', 'system',
 'farmer_gender', 'Farmer Gender', 'DEMOGRAPHIC', 'TEXT',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, '[{"value":"male","label":"Male"},{"value":"female","label":"Female"},{"value":"other","label":"Other"}]',
 '=,!=,IN,NOT_IN', NULL,
 'Static options: male, female, other', 2, 'Y'),

('jre-marital_status', NOW(), NOW(), 'system', 'system',
 'marital_status', 'Marital Status', 'DEMOGRAPHIC', 'LOOKUP',
 'FARMER_APPLICATION', 'N', NULL,
 'md01maritalStatus', NULL,
 '=,!=,IN,NOT_IN', NULL,
 'Picks from MD.01 Marital Status', 3, 'Y'),

-- ============== LOCATION (3) ==============
('jre-district_code', NOW(), NOW(), 'system', 'system',
 'district_code', 'District', 'LOCATION', 'LOOKUP',
 'FARMER_APPLICATION', 'N', NULL,
 'md03district', NULL,
 '=,!=,IN,NOT_IN', NULL,
 'Lesotho district from MD.03', 4, 'Y'),

('jre-agro_zone', NOW(), NOW(), 'system', 'system',
 'agro_zone', 'Agro-Ecological Zone', 'LOCATION', 'LOOKUP',
 'FARMER_APPLICATION', 'N', NULL,
 'md04agroEcologicalZo', NULL,
 '=,!=,IN,NOT_IN', NULL,
 'NB: form id is truncated to 24 chars (md04agroEcologicalZo)', 5, 'Y'),

('jre-residency_type', NOW(), NOW(), 'system', 'system',
 'residency_type', 'Residency Type', 'LOCATION', 'LOOKUP',
 'FARMER_APPLICATION', 'N', NULL,
 'md05residencyType', NULL,
 '=,!=,IN,NOT_IN', NULL,
 'Permanent vs seasonal vs transient', 6, 'Y'),

-- ============== HOUSEHOLD (4) ==============
('jre-household_size', NOW(), NOW(), 'system', 'system',
 'household_size', 'Household Size', 'HOUSEHOLD', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Total members (sourced from spFarmerDerived.totalHouseholdMembers)', 7, 'Y'),

('jre-dependents_under_18', NOW(), NOW(), 'system', 'system',
 'dependents_under_18', 'Dependents Under 18', 'HOUSEHOLD', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Sourced from spFarmerDerived.childrenUnder18', 8, 'Y'),

('jre-female_headed_household', NOW(), NOW(), 'system', 'system',
 'female_headed_household', 'Female-Headed Household', 'HOUSEHOLD', 'BOOLEAN',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, '[{"value":"true","label":"Yes"},{"value":"false","label":"No"}]',
 '=,!=', NULL,
 'true / false (from spFarmerDerived)', 9, 'Y'),

('jre-disability_count', NOW(), NOW(), 'system', 'system',
 'disability_count', 'Members with Disability', 'HOUSEHOLD', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Sourced from spFarmerDerived.householdDisabilityCount', 10, 'Y'),

-- ============== AGRICULTURAL (3) ==============
('jre-total_land_ha', NOW(), NOW(), 'system', 'system',
 'total_land_ha', 'Total Land Available (ha)', 'AGRICULTURAL', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Critical for smallholder definition (<2 ha per Section 4.2.1 policy). From spFarmerDerived.totalLandAvailable', 11, 'Y'),

('jre-cultivated_land_ha', NOW(), NOW(), 'system', 'system',
 'cultivated_land_ha', 'Total Cultivated Land (ha)', 'AGRICULTURAL', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'From spFarmerDerived.totalCultivatedLand', 12, 'Y'),

('jre-has_livestock', NOW(), NOW(), 'system', 'system',
 'has_livestock', 'Has Livestock', 'AGRICULTURAL', 'BOOLEAN',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, '[{"value":"true","label":"Yes"},{"value":"false","label":"No"}]',
 '=,!=', NULL,
 'From spFarmerDerived.hasLivestock', 13, 'Y'),

-- ============== ECONOMIC (2) ==============
('jre-annual_income_lsl', NOW(), NOW(), 'system', 'system',
 'annual_income_lsl', 'Annual Household Income (LSL)', 'ECONOMIC', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'In Lesotho Loti. From spFarmerDerived.averageAnnualIncome', 14, 'Y'),

('jre-vulnerability_score', NOW(), NOW(), 'system', 'system',
 'vulnerability_score', 'Vulnerability Score', 'ECONOMIC', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 '0 (least) to 100 (most). From spFarmerDerived.vulnerabilityScore', 15, 'Y'),

-- ============== FOOD_SECURITY (1) — closes Gap 11 ==============
('jre-food_security_status', NOW(), NOW(), 'system', 'system',
 'food_security_status', 'Food Security Status', 'FOOD_SECURITY', 'LOOKUP',
 'FARMER_APPLICATION', 'N', NULL,
 'md35foodSecurityStat', NULL,
 '=,!=,IN,NOT_IN', NULL,
 'Critical for poverty-targeting (Policy Section 2.2.2). Values: SECURE, MODERATE, SEVERE, CRISIS. From spFarmerDerived.currentFoodSecurityStatus', 16, 'Y'),

-- ============== PROGRAM_HISTORY (2) — closes Gap 4 ==============
('jre-programs_last_2_seasons', NOW(), NOW(), 'system', 'system',
 'programs_last_2_seasons', 'Active Programmes (Last 2 Seasons)', 'PROGRAM_HISTORY', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Anti-dependency check (Policy Section 1.2.iv). From spFarmerDerived.programsInLast2Seasons', 17, 'Y'),

('jre-total_benefits_lifetime', NOW(), NOW(), 'system', 'system',
 'total_benefits_lifetime', 'Total Benefits Received (Lifetime, LSL)', 'PROGRAM_HISTORY', 'NUMBER',
 'FARMER_APPLICATION', 'N', NULL,
 NULL, NULL,
 '=,!=,<,<=,>,>=,BETWEEN', NULL,
 'Cumulative subsidy value across all programmes ever. From spFarmerDerived.totalBenefitsReceivedLT', 18, 'Y');

-- =============================================================================
-- Verify
-- =============================================================================
SELECT c_scopecode, c_scopename, c_contexttype, c_isactive
FROM app_fd_jrefieldscope
WHERE c_scopecode = 'FARMER_APPLICATION';

SELECT c_category, c_fieldid, c_fieldlabel, c_fieldtype,
       CASE WHEN c_lookupformid IS NOT NULL THEN c_lookupformid
            WHEN c_lookupvalues IS NOT NULL THEN '(static)'
            ELSE '' END AS lookup_source
FROM app_fd_jrefielddefinition
WHERE c_scopecode = 'FARMER_APPLICATION' AND c_isactive = 'Y'
ORDER BY c_displayorder;
