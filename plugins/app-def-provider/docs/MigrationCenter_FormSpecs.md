# Migration Center - Form Specifications

## Overview

**Application ID:** `migrationCenter`
**Application Name:** Migration Center
**Purpose:** Command center for tracking application migrations across environments (dev → test → prod)

### Architecture

The Migration Center consists of 4 interconnected forms that track the complete lifecycle of application migrations:

```
deployment_jobs (parent)
    ↓
    ├── deployment_history (child - audit trail)
    ├── prerequisite_validation (child - validation results)
    └── component_registry (standalone - what's deployed where)
```

### Workflow

1. **Export** from source → Create `deployment_jobs` record
2. **Validate** prerequisites → Create `prerequisite_validation` records
3. **Import** to target → Create `deployment_history` records for each component
4. **Register** deployed components → Create/update `component_registry` records

---

## Form 1: Deployment Jobs

**Purpose:** Track each export/import operation as a job

### Form Details

- **Form ID:** `deployment_jobs`
- **Form Name:** Deployment Jobs
- **Table Name:** `app_fd_deployment_jobs`
- **Primary Key:** `job_id`

### Fields

| Field Name | Label | Type | Size | Required | Default | Purpose |
|------------|-------|------|------|----------|---------|---------|
| `job_id` | Job ID | Text Field | 100 | Yes | UUID | Unique identifier for this job |
| `job_type` | Job Type | Select Box | - | Yes | - | Type: export or import |
| `source_app` | Source App | Text Field | 100 | Yes | - | Application ID being exported |
| `target_app` | Target App | Text Field | 100 | No | - | Application ID receiving import (null for export jobs) |
| `source_environment` | Source Environment | Select Box | - | Yes | - | Environment: dev, test, prod |
| `target_environment` | Target Environment | Select Box | - | No | - | Target environment (for imports) |
| `status` | Status | Select Box | - | Yes | pending | Job status: pending, running, completed, failed |
| `started_by` | Started By | Text Field | 100 | Yes | {currentUser} | Username who initiated |
| `started_at` | Started At | Date Picker | - | Yes | {currentDateTime} | When job started |
| `completed_at` | Completed At | Date Picker | - | No | - | When job finished |
| `duration_seconds` | Duration (seconds) | Number | - | No | - | How long job took |
| `export_file` | Export File | File Upload | - | No | - | Uploaded ZIP file (for import jobs) |
| `components_count` | Components Count | Number | - | No | - | Total components processed |
| `warnings_count` | Warnings Count | Number | - | No | 0 | Number of warnings |
| `errors_count` | Errors Count | Number | - | No | 0 | Number of errors |
| `report` | Report | Text Area | - | No | - | JSON report with details |

### Select Box Options

**job_type:**
- export
- import

**source_environment / target_environment:**
- dev
- test
- prod

**status:**
- pending
- running
- completed
- failed

### Indexes
- Primary: `job_id`
- Index: `source_app`, `status`, `started_at`

---

## Form 2: Deployment History

**Purpose:** Audit trail of individual component deployments (child of deployment_jobs)

### Form Details

- **Form ID:** `deployment_history`
- **Form Name:** Deployment History
- **Table Name:** `app_fd_deployment_history`
- **Primary Key:** `history_id`
- **Foreign Key:** `job_id` → `deployment_jobs.job_id`

### Fields

| Field Name | Label | Type | Size | Required | Default | Purpose |
|------------|-------|------|------|----------|---------|---------|
| `history_id` | History ID | Text Field | 100 | Yes | UUID | Unique identifier |
| `job_id` | Job ID | Text Field | 100 | Yes | - | Link to parent deployment_jobs |
| `component_type` | Component Type | Select Box | - | Yes | - | Type: form, datalist, userview, api, process |
| `component_id` | Component ID | Text Field | 100 | Yes | - | Component identifier (e.g., formId) |
| `component_name` | Component Name | Text Field | 255 | No | - | Human-readable name |
| `checksum_before` | Checksum Before | Text Field | 64 | No | - | SHA-256 hash before import (if existed) |
| `checksum_after` | Checksum After | Text Field | 64 | Yes | - | SHA-256 hash after import |
| `action` | Action | Select Box | - | Yes | - | What happened: created, updated, skipped, failed |
| `error_message` | Error Message | Text Area | - | No | - | Error details if action=failed |
| `timestamp` | Timestamp | Date Picker | - | Yes | {currentDateTime} | When this component was processed |

### Select Box Options

**component_type:**
- form
- datalist
- userview
- api
- process

**action:**
- created
- updated
- skipped
- failed

### Indexes
- Primary: `history_id`
- Foreign Key: `job_id`
- Index: `component_type`, `component_id`, `action`

---

## Form 3: Prerequisite Validation

**Purpose:** Track validation results for prerequisites (child of deployment_jobs)

### Form Details

- **Form ID:** `prerequisite_validation`
- **Form Name:** Prerequisite Validation
- **Table Name:** `app_fd_prerequisite_validation`
- **Primary Key:** `validation_id`
- **Foreign Key:** `job_id` → `deployment_jobs.job_id`

### Fields

| Field Name | Label | Type | Size | Required | Default | Purpose |
|------------|-------|------|------|----------|---------|---------|
| `validation_id` | Validation ID | Text Field | 100 | Yes | UUID | Unique identifier |
| `job_id` | Job ID | Text Field | 100 | Yes | - | Link to parent deployment_jobs |
| `item_type` | Item Type | Select Box | - | Yes | - | Type: metadata_form, plugin, joget_version |
| `item_name` | Item Name | Text Field | 255 | Yes | - | Name of required item |
| `item_checksum` | Item Checksum | Text Field | 64 | No | - | Expected checksum (for plugins) |
| `status` | Status | Select Box | - | Yes | - | Result: found, missing, mismatch |
| `severity` | Severity | Select Box | - | Yes | warning | Impact: warning, error |
| `message` | Message | Text Area | - | No | - | Human-readable warning/error message |
| `validated_at` | Validated At | Date Picker | - | Yes | {currentDateTime} | When validation performed |

### Select Box Options

**item_type:**
- metadata_form
- plugin
- joget_version

**status:**
- found
- missing
- mismatch

**severity:**
- warning
- error

### Indexes
- Primary: `validation_id`
- Foreign Key: `job_id`
- Index: `item_type`, `status`, `severity`

---

## Form 4: Component Registry

**Purpose:** Registry of what components are deployed in which environment (standalone)

### Form Details

- **Form ID:** `component_registry`
- **Form Name:** Component Registry
- **Table Name:** `app_fd_component_registry`
- **Primary Key:** `registry_id`
- **Composite Unique Index:** `app_id + environment + component_type + component_id`

### Fields

| Field Name | Label | Type | Size | Required | Default | Purpose |
|------------|-------|------|------|----------|---------|---------|
| `registry_id` | Registry ID | Text Field | 100 | Yes | UUID | Unique identifier |
| `app_id` | App ID | Text Field | 100 | Yes | - | Application identifier |
| `environment` | Environment | Select Box | - | Yes | - | Where deployed: dev, test, prod |
| `component_type` | Component Type | Select Box | - | Yes | - | Type: form, datalist, userview, api, process |
| `component_id` | Component ID | Text Field | 100 | Yes | - | Component identifier |
| `component_name` | Component Name | Text Field | 255 | No | - | Human-readable name |
| `checksum` | Checksum | Text Field | 64 | Yes | - | SHA-256 hash of current version |
| `deployed_at` | Deployed At | Date Picker | - | Yes | {currentDateTime} | When last deployed/updated |
| `deployed_by` | Deployed By | Text Field | 100 | Yes | {currentUser} | Who deployed it |
| `job_id` | Job ID | Text Field | 100 | No | - | Reference to deployment_jobs (optional) |

### Select Box Options

**environment:**
- dev
- test
- prod

**component_type:**
- form
- datalist
- userview
- api
- process

### Indexes
- Primary: `registry_id`
- Unique Composite: `app_id + environment + component_type + component_id`
- Index: `app_id`, `environment`, `component_type`

---

## Form Creation Checklist

When creating these forms in Joget Form Builder:

### For Each Form:

1. ✅ Set correct Form ID and Table Name
2. ✅ Add all fields with correct types
3. ✅ Set field sizes/constraints
4. ✅ Mark required fields
5. ✅ Configure Select Box options
6. ✅ Set default values where specified
7. ✅ Add field descriptions/help text
8. ✅ Configure Primary Key field (read-only, default UUID)

### After Form Creation:

1. ✅ Generate CRUD for each form (Joget auto-generation)
2. ✅ Export form JSON files
3. ✅ Save exported JSONs to directory structure:

```
/Users/aarelaponin/IdeaProjects/plugins/app-def-provider/migration-center-forms/
├── deployment_jobs.json
├── deployment_history.json
├── prerequisite_validation.json
└── component_registry.json
```

### Userview Creation:

Create a userview with categories:
1. **Deploy** - Link to deployment_jobs CRUD (create new job)
2. **History** - Datalist showing deployment_history
3. **Components** - Datalist showing component_registry
4. **Prerequisites** - Datalist showing prerequisite_validation
5. **Reports** - Custom dashboard/reports (optional)

---

## Integration Notes

### How joget_utility.py Will Use These Forms

**During Import (--import-app command):**

1. **Create deployment_jobs record:**
   ```python
   job_id = generate_uuid()
   POST /jw/api/json/data/form/store/deployment_jobs
   {
     "job_id": job_id,
     "job_type": "import",
     "source_app": "sourceApp",
     "target_app": "targetApp",
     "status": "running",
     ...
   }
   ```

2. **For each component imported:**
   ```python
   POST /jw/api/json/data/form/store/deployment_history
   {
     "history_id": generate_uuid(),
     "job_id": job_id,
     "component_type": "form",
     "component_id": "formId",
     "action": "created",
     ...
   }
   ```

3. **Update component_registry:**
   ```python
   POST /jw/api/json/data/form/store/component_registry
   {
     "registry_id": generate_uuid(),
     "app_id": "targetApp",
     "environment": "test",
     "component_id": "formId",
     "checksum": "abc123...",
     ...
   }
   ```

4. **Complete job:**
   ```python
   PUT /jw/api/json/data/form/store/deployment_jobs/{job_id}
   {
     "status": "completed",
     "completed_at": current_time,
     "duration_seconds": 45
   }
   ```

### Prerequisites Validation Integration

Before import, validate and log:
```python
for missing_item in missing_prerequisites:
    POST /jw/api/json/data/form/store/prerequisite_validation
    {
      "validation_id": generate_uuid(),
      "job_id": job_id,
      "item_type": "plugin",
      "item_name": "custom-plugin.jar",
      "status": "missing",
      "severity": "warning",
      "message": "Plugin not found but import will continue"
    }
```

---

## Testing After Creation

### Test Data Insert

After creating forms, test by manually inserting a sample deployment job:

1. Go to deployment_jobs CRUD
2. Create new record:
   - Job Type: export
   - Source App: testApp
   - Source Environment: dev
   - Status: completed
   - Started By: admin
   - Components Count: 10

3. Create related deployment_history records
4. View in datalists to verify relationships work

---

## Next Steps

After you've created these forms:

1. Share the directory path where you saved the JSON files
2. I'll integrate them into joget_utility.py for automated tracking
3. We'll test the complete workflow (export → validate → import → track)

---

## Questions?

If you need clarification on any field purpose or relationship while creating the forms, let me know!
