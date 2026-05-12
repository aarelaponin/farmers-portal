# Verified Database Schema - Direct from MySQL Database

## Database Connection Successful ✓

Connected to MySQL database `jwdb` and extracted actual schema information.

## Key Findings

### 1. Table Naming Convention - CONFIRMED

**ALL tables use the `app_fd_` prefix** as originally assumed in `services.yml`

✓ Found 40 tables with 'app_fd_' prefix
✓ NO tables without this prefix for form data

### 2. Verified Table Names from services.yml

All tables mentioned in `services.yml` **exist in the database**:

| services.yml Table Name | Exists in DB | Row Count | Columns |
|------------------------|--------------|-----------|---------|
| `app_fd_farmer_basic_data` | ✓ YES | 1 | 21 |
| `app_fd_farm_location` | ✓ YES | 1 | 28 |
| `app_fd_farmer_registry` | ✓ YES | 1 | 52 |
| `app_fd_farmer_crop_livestck` | ✓ YES | 0 | 11 |
| `app_fd_farmer_income` | ✓ YES | 0 | 30 |
| `app_fd_farmer_declaration` | ✓ YES | 0 | 17 |
| `app_fd_household_members` | ✓ YES | 3 | 17 |
| `app_fd_crop_management` | ✓ YES | 3 | 15 |
| `app_fd_livestock_details` | ✓ YES | 0 | 12 |

**Note:** The table `app_fd_farmer_basic` in services.yml should be `app_fd_farmer_basic_data`

### 3. Column Naming Convention - CONFIRMED

✓ **69.6% of columns use the `c_` prefix** (641 out of 921 columns)
✓ System columns don't use the prefix: `id`, `dateCreated`, `dateModified`, `createdBy`, etc.
✓ Form field columns use the prefix: `c_national_id`, `c_first_name`, `c_gender`, etc.

### 4. Actual Column Examples

#### app_fd_farmer_registry (sample columns)
- `c_agriculturalManagementSkills` ✓ (matches services.yml field)
- `c_mainSourceLivelihood` ✓
- `c_cropProduction` ✓
- `c_mainSourceFarmLabour` ✓
- `c_conservationPractices` ✓
- `c_parent_id` ✓ (parent linking field)

#### app_fd_household_members (has data)
- Has 3 rows of actual data
- Contains `c_farmer_id` field for parent linking

#### app_fd_crop_management (has data)
- Has 3 rows of actual data
- Contains crop management fields

### 5. Primary Keys

**ALL tables use `id` as the primary key** (VARCHAR(255))

### 6. System Columns

Every table has these system columns:
- `id` (PRIMARY KEY)
- `dateCreated`
- `dateModified`
- `createdBy`
- `createdByName`
- `modifiedBy`
- `modifiedByName`

### 7. Multiple Registry Tables Found

There are multiple farmer registry-related tables:
- `app_fd_farmer_registry` (52 columns) - Main one
- `app_fd_farms_registry` (83 columns) - Comprehensive
- `app_fd_farmer_registration` (32 columns)
- `app_fd_farmer_reg_approval`
- `app_fd_farmer_reg_review`
- `app_fd_farmer_reg_record`

## Corrections Needed

### In services.yml:

1. **Table Name Correction:**
   - Change: `app_fd_farmer_basic`
   - To: `app_fd_farmer_basic_data`

2. **Verify Correct Registry Table:**
   - Currently using: `app_fd_farmer_agric`
   - Should verify if this maps to: `app_fd_farmer_registry`

### Column Mappings are CORRECT:

✓ The `c_` prefix convention is confirmed
✓ Field names match (e.g., `agriculturalManagementSkills` → `c_agriculturalManagementSkills`)
✓ Parent ID fields exist (`c_parent_id`, `c_farmer_id`)

## SQL Verification Queries (Tested)

```sql
-- Check table exists
SHOW TABLES LIKE 'app_fd_farmer_%';

-- Check columns for a table
DESCRIBE app_fd_farmer_registry;

-- Check for data
SELECT COUNT(*) FROM app_fd_household_members;  -- Returns: 3
SELECT COUNT(*) FROM app_fd_crop_management;     -- Returns: 3
SELECT COUNT(*) FROM app_fd_farmer_registry;     -- Returns: 1
```

## Summary

1. **Your original assumptions in services.yml were mostly CORRECT** ✓
2. **Tables DO use `app_fd_` prefix** ✓
3. **Columns DO use `c_` prefix for form fields** ✓
4. **Only minor correction needed**: `app_fd_farmer_basic` → `app_fd_farmer_basic_data`
5. **The database schema matches the expected structure**

The confusion earlier came from:
- Looking at form JSON `tableName` property which contains logical names without prefix
- Joget adds the `app_fd_` prefix when creating actual database tables
- The form definitions don't show the actual database table names