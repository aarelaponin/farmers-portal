# Complete Database Schema Documentation

## Overview

This document provides the **exact database schema** for the Farmer Registration System based on form definition analysis. All table names and column names are verified from the actual form JSON definitions.

## Key Findings

### Table Names (Actual vs Assumed)

| Form Section | Actual Table Name | Previously Assumed |
|--------------|------------------|-------------------|
| Basic Info | `farmer_basic_data` | `app_fd_farmer_basic` |
| Location | `farm_location` | `app_fd_farmer_location` |
| Agriculture | `farmer_registry` | `app_fd_farmer_agric` |
| Crops/Livestock | `farmer_crop_livestck` | `app_fd_farmer_crop_livestck` |
| Household | `farmer_registry` | `app_fd_farmer_household` |
| Income | `farmer_income` | `app_fd_farmer_income_prog` |
| Declaration | `farmer_declaration` | `app_fd_farmer_declaration` |

### Grid Tables (Sub-forms)

| Grid | Actual Table Name | Parent Link Field |
|------|------------------|------------------|
| Household Members | `household_members` | `c_farmer_id` |
| Crop Management | `crop_management` | `c_farmer_id` |
| Livestock Details | `livestock_details` | `c_farmer_id` |

## Complete Table Schemas

### 1. farmer_basic_data
**Form:** 01.01 - Farmer Basic Information

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated |
| `c_national_id` | national_id | VARCHAR | National ID |
| `c_first_name` | first_name | VARCHAR | First Name |
| `c_last_name` | last_name | VARCHAR | Last Name |
| `c_gender` | gender | VARCHAR | Gender (male/female) |
| `c_date_of_birth` | date_of_birth | DATE | Date of Birth |
| `c_marital_status` | marital_status | VARCHAR | Marital Status |
| `c_preferred_language` | preferred_language | VARCHAR | Preferred Language |
| `c_mobile_number` | mobile_number | VARCHAR | Mobile Number |
| `c_email_address` | email_address | VARCHAR | Email Address |
| `c_extension_officer_name` | extension_officer_name | VARCHAR | Extension Officer Name |
| `c_member_of_cooperative` | member_of_cooperative | VARCHAR | Cooperative member? (yes/no) |
| `c_cooperative_name` | cooperative_name | VARCHAR | Cooperative Name |
| `c_parent_id` | parent_id | VARCHAR | Link to main record |

### 2. farm_location
**Form:** 01.02 - Farmer Location and Farm

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated |
| `c_district` | district | VARCHAR | District |
| `c_village` | village | VARCHAR | Village Name |
| `c_communityCouncil` | communityCouncil | VARCHAR | Community Council |
| `c_agroEcologicalZone` | agroEcologicalZone | VARCHAR | Agro-Ecological Zone |
| `c_residency_type` | residency_type | VARCHAR | Residency Type |
| `c_yearsInArea` | yearsInArea | VARCHAR | Years lived in area |
| `c_gpsLatitude` | gpsLatitude | VARCHAR | GPS Latitude |
| `c_gpsLongitude` | gpsLongitude | VARCHAR | GPS Longitude |
| `c_ownedRentedLand` | ownedRentedLand | VARCHAR | Owned/Rented Land (Hectares) |
| `c_totalAvailableLand` | totalAvailableLand | VARCHAR | Total Available Land |
| `c_cultivatedLand` | cultivatedLand | VARCHAR | Cultivated Land |
| `c_conservationAgricultureLand` | conservationAgricultureLand | VARCHAR | Conservation Agriculture Land |
| `c_access_to_services` | access_to_services | VARCHAR | Closest Service |
| `c_distanceWaterSource` | distanceWaterSource | VARCHAR | Distance to Water Source |
| `c_distancePrimarySchool` | distancePrimarySchool | VARCHAR | Distance to Primary School |
| `c_distancePublicHospital` | distancePublicHospital | VARCHAR | Distance to Public Hospital |
| `c_distanceLivestockMarket` | distanceLivestockMarket | VARCHAR | Distance to Livestock Market |
| `c_distanceAgriculturalMarket` | distanceAgriculturalMarket | VARCHAR | Distance to Agricultural Market |
| `c_distancePublicTransport` | distancePublicTransport | VARCHAR | Distance to Public Transport |
| `c_parent_id` | parent_id | VARCHAR | Link to main record |

### 3. farmer_registry
**Form:** 01.03 - Agricultural Activities (also used by 01.04 Household)

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated |
| `c_agriculture_key` | agriculture_key | VARCHAR | Section key |
| `c_cropProduction` | cropProduction | VARCHAR | Engaged in crop production? |
| `c_livestockProduction` | livestockProduction | VARCHAR | Engaged in livestock production? |
| `c_canReadWrite` | canReadWrite | VARCHAR | Can read and write? |
| `c_mainSourceFarmLabour` | mainSourceFarmLabour | VARCHAR | Main source of farm labour |
| `c_mainSourceLivelihood` | mainSourceLivelihood | VARCHAR | Main source of livelihood |
| `c_agriculturalManagementSkills` | agriculturalManagementSkills | VARCHAR | Agricultural management skills level |
| `c_mainSourceAgriculturalInfo` | mainSourceAgriculturalInfo | VARCHAR | Main source of agricultural info |
| `c_conservationPractices` | conservationPractices | VARCHAR | Conservation practices |
| `c_conservationPracticesOther` | conservationPracticesOther | VARCHAR | Other conservation practices |
| `c_shocks_hazards` | shocks_hazards | VARCHAR | Shocks/hazards experienced |
| `c_otherHazards` | otherHazards | VARCHAR | Other hazards |
| `c_copingMechanisms` | copingMechanisms | TEXT | Coping mechanisms |
| `c_household_key` | household_key | VARCHAR | Household section key |
| `c_parent_id` | parent_id | VARCHAR | Link to main record |

### 4. farmer_crop_livestck
**Form:** 01.05 - Crops and Livestock Container

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated |
| `c_crops_livestock_key` | crops_livestock_key | VARCHAR | Section key |
| `c_hasLivestock` | hasLivestock | VARCHAR | Has livestock? (yes/no) |
| `c_parent_id` | parent_id | VARCHAR | Link to main record |

### 5. farmer_income
**Form:** 01.06 - Income and Programs

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated |
| `c_income_programs_key` | income_programs_key | VARCHAR | Section key |
| `c_mainSourceIncome` | mainSourceIncome | VARCHAR | Main source of income |
| `c_income_sources` | income_sources | VARCHAR | Income sources (comma-separated) |
| `c_gainfulEmployment` | gainfulEmployment | VARCHAR | In gainful employment? |
| `c_governmentEmployed` | governmentEmployed | VARCHAR | Government employed? |
| `c_averageAnnualIncome` | averageAnnualIncome | VARCHAR | Average annual income |
| `c_monthlyExpenditure` | monthlyExpenditure | VARCHAR | Monthly expenditure |
| `c_relativeSupport` | relativeSupport | VARCHAR | Receives relative support? |
| `c_supportFrequency` | supportFrequency | VARCHAR | Support frequency |
| `c_supportType` | supportType | VARCHAR | Type of support |
| `c_supportProgram` | supportProgram | VARCHAR | Support programs |
| `c_creditDefault` | creditDefault | VARCHAR | In credit default? |
| `c_everOnISP` | everOnISP | VARCHAR | Ever on ISP? |
| `c_otherInputSupport` | otherInputSupport | VARCHAR | Other input support? |
| `c_totalLoans12Months` | totalLoans12Months | VARCHAR | Total loans 12 months |
| `c_informalTransfers` | informalTransfers | VARCHAR | Informal transfers |
| `c_formalTransfers` | formalTransfers | VARCHAR | Formal transfers |
| `c_networkAssociations` | networkAssociations | VARCHAR | Network associations |
| `c_networkRelatives` | networkRelatives | VARCHAR | Network relatives |
| `c_networksSupport` | networksSupport | VARCHAR | Networks support |
| `c_parent_id` | parent_id | VARCHAR | Link to main record |

### 6. farmer_declaration
**Form:** 01.07 - Declaration

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated |
| `c_declaration_key` | declaration_key | VARCHAR | Section key |
| `c_declarationConsent` | declarationConsent | VARCHAR | Consent checkbox |
| `c_declarationFullName` | declarationFullName | VARCHAR | Full name |
| `c_field13` | field13 | DATE | Declaration date |
| `c_field12` | field12 | TEXT | Signature (base64) |
| `c_registrationStation` | registrationStation | VARCHAR | Registration station |
| `c_beneficiaryCode` | beneficiaryCode | VARCHAR | Generated beneficiary code |
| `c_registrationChannel` | registrationChannel | VARCHAR | Registration channel |
| `c_registrationStatus` | registrationStatus | VARCHAR | Registration status |
| `c_parent_id` | parent_id | VARCHAR | Link to main record |

### 7. household_members (Grid/Sub-table)
**Form:** 01.04-1 - Household Member Sub-form

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated row ID |
| `c_farmer_id` | farmer_id | VARCHAR | **Parent farmer ID** |
| `c_memberName` | memberName | VARCHAR | Member name |
| `c_sex` | sex | VARCHAR | Sex |
| `c_date_of_birth` | date_of_birth | DATE | Date of birth |
| `c_relationship` | relationship | VARCHAR | Relationship to head |
| `c_orphanhoodStatus` | orphanhoodStatus | VARCHAR | Orphanhood status |
| `c_participatesInAgriculture` | participatesInAgriculture | VARCHAR | Participates in agriculture |
| `c_disability` | disability | VARCHAR | Disability status |
| `c_chronicallyIll` | chronicallyIll | VARCHAR | Chronically ill |

### 8. crop_management (Grid/Sub-table)
**Form:** 01.05-1 - Crop Management Sub-form

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated row ID |
| `c_farmer_id` | farmer_id | VARCHAR | **Parent farmer ID** |
| `c_cropType` | cropType | VARCHAR | Crop type |
| `c_areaCultivated` | areaCultivated | VARCHAR | Area cultivated |
| `c_areaUnit` | areaUnit | VARCHAR | Area unit |
| `c_bagsHarvested` | bagsHarvested | VARCHAR | 50kg bags harvested |
| `c_fertilizerApplied` | fertilizerApplied | VARCHAR | Fertilizer applied? |
| `c_pesticidesApplied` | pesticidesApplied | VARCHAR | Pesticides applied? |

### 9. livestock_details (Grid/Sub-table)
**Form:** 01.05-2 - Livestock Details Sub-form

| Column Name | Field ID | Type | Description |
|------------|----------|------|-------------|
| `id` | - | PRIMARY KEY | Auto-generated row ID |
| `c_farmer_id` | farmer_id | VARCHAR | **Parent farmer ID** |
| `c_livestockType` | livestockType | VARCHAR | Livestock type |
| `c_numberOfMale` | numberOfMale | VARCHAR | Number of males |
| `c_numberOfFemale` | numberOfFemale | VARCHAR | Number of females |

## Critical Parent-Child Relationships

### Grid Data Linking
All grid tables link to the parent farmer record via `c_farmer_id` field:

```sql
-- Example: Get household members for a farmer
SELECT * FROM household_members
WHERE c_farmer_id = 'farmer-002';

-- Example: Get crops for a farmer
SELECT * FROM crop_management
WHERE c_farmer_id = 'farmer-002';

-- Example: Get livestock for a farmer
SELECT * FROM livestock_details
WHERE c_farmer_id = 'farmer-002';
```

### Main Form Linking
Main forms link via `c_parent_id` field to a common farmer ID:

```sql
-- Example: Get all data for a farmer
SELECT * FROM farmer_basic_data WHERE id = 'farmer-002' OR c_parent_id = 'farmer-002';
SELECT * FROM farm_location WHERE c_parent_id = 'farmer-002';
SELECT * FROM farmer_registry WHERE c_parent_id = 'farmer-002';
SELECT * FROM farmer_income WHERE c_parent_id = 'farmer-002';
SELECT * FROM farmer_declaration WHERE c_parent_id = 'farmer-002';
```

## SQL Verification Queries

### Verify Table Structure
```sql
-- Show all columns for a table
SHOW COLUMNS FROM farmer_basic_data;
SHOW COLUMNS FROM household_members;
SHOW COLUMNS FROM crop_management;
SHOW COLUMNS FROM livestock_details;
```

### Check Grid Data
```sql
-- Verify grid rows have correct parent field
SELECT c_farmer_id, COUNT(*) as member_count
FROM household_members
GROUP BY c_farmer_id;

-- Check for orphaned grid rows
SELECT * FROM household_members
WHERE c_farmer_id IS NULL OR c_farmer_id = '';
```

### Data Integrity Checks
```sql
-- Check for duplicate national IDs
SELECT c_national_id, COUNT(*) as count
FROM farmer_basic_data
GROUP BY c_national_id
HAVING count > 1;

-- Verify all main forms have parent_id set
SELECT COUNT(*) FROM farm_location WHERE c_parent_id IS NULL;
SELECT COUNT(*) FROM farmer_registry WHERE c_parent_id IS NULL;
```

## Important Notes

1. **NO app_fd_ prefix**: Unlike assumed, tables do NOT use the `app_fd_` prefix
2. **Column naming**: All columns use `c_` prefix followed by field ID
3. **Parent linking**:
   - Grid tables use `c_farmer_id` to link to parent
   - Main forms use `c_parent_id` to link to farmer record
4. **Multiple tables**: Some forms share tables (e.g., `farmer_registry` used by multiple forms)
5. **Primary keys**: All tables use `id` as primary key column

## Updates Required

Based on this analysis, the following need updates:

1. **services.yml**: Update all `tableName` references
2. **TableDataHandler.java**: Correct table name mappings
3. **GovStackDataMapperV2.java**: Update SECTION_TO_FORM_MAP
4. **test-validation.yml**: Use actual table and column names

This documentation represents the **actual database schema** as defined in the form JSONs.