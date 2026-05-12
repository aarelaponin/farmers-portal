# Workflow Activator Plugin for Joget DX8

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.workflow.activator`
**Architecture:** Multi-Service Support

## Overview
The Workflow Activator is a Post Processing Tool plugin for Joget DX8 that automatically triggers workflow processes after form submission. It serves as the **single configuration point** for serviceId in GovStack Registration Building Block's multi-service architecture.

## Purpose
This plugin was developed to:
- **Configure serviceId once** - Single configuration point for service identification
- **Convention-based process naming** - Automatically starts `{serviceId}_submission` processes
- Automatically start workflow processes when forms are submitted
- Pass form data and serviceId to workflow processes as workflow variables
- Support both synchronous and asynchronous workflow execution
- Enable integration with GovStack plugins (DocSubmitter, ProcessingServer)

## How It Works

### Integration Flow (Multi-Service Architecture)
```
[Form Submission]
    ↓
[WorkflowActivator (Post Processing Tool)] ← SINGLE CONFIG POINT
    │
    ├─ Sets serviceId (e.g., "farmers_registry")
    ├─ Validates serviceId format
    ├─ Uses convention: {serviceId}_submission
    │  (e.g., "farmers_registry_submission")
    ↓
[Workflow Process Started]
    │
    ├─ Process ID = Form Record ID (Joget standard)
    ├─ Workflow variable "serviceId" = "farmers_registry"
    ↓
[Process Tools (e.g., DocSubmitter)]
    │
    ├─ Reads serviceId from workflow variables
    ├─ Loads {serviceId}.yml (e.g., farmers_registry.yml)
    ├─ Sends to API: /services/{serviceId}/applications
    ↓
[ProcessingServer Receiver]
    │
    ├─ Extracts serviceId from URL path
    ├─ Loads {serviceId}.yml
    ├─ Saves to receiver database
```

### Key Mechanism
- **Single Configuration Point**: WorkflowActivator is where serviceId is configured (follows Joget philosophy)
- **Convention-Based Process Naming**: Process name = `{serviceId}_submission` (e.g., `farmers_registry_submission`)
- **ServiceId Validation**: Enforces lowercase + underscore only format (`[a-z0-9_]+`)
- **Workflow Variable Passing**: Sets `serviceId` as workflow variable for downstream plugins
- **Form Record ID**: Becomes process instance ID (Joget's standard behavior)
- **No Hardcoding**: Same plugins work for ANY service type (farmers, students, subsidies, etc.)

## Features

### 1. Service ID Configuration (NEW - Multi-Service Support)
- **Single Configuration Point**: Configure serviceId once in WorkflowActivator
- **Convention-Based Process Naming**: Automatically resolves `{serviceId}_submission` processes
- **ServiceId Validation**: Enforces `[a-z0-9_]+` format (lowercase + underscore only)
- **Workflow Variable Passing**: Passes serviceId to downstream process tools

### 2. Process Invocation
- Start workflows by Process Definition ID or Process Name
- **Convention-based**: Use `{serviceId}_submission` naming pattern (recommended)
- Support for specific version or "latest" version
- Automatic resolution of process definition

### 3. Execution Modes
- **Synchronous**: Waits for process to start before continuing
- **Asynchronous**: Starts process in background thread
- **Wait for Completion**: Optional waiting for entire process to complete (with timeout protection)

### 4. Data Mapping
- **Form Data Pass-through**: Automatically passes all form fields as workflow variables
- **ServiceId Passing**: Automatically passes serviceId as workflow variable (critical for multi-service)
- **Custom Variables**: Add additional workflow variables with support for hash variables
- **Participant Assignment**: Configure who should handle the first activity

### 5. Advanced Options
- **Detailed Logging**: Enable/disable detailed execution logs
- **Error Handling**: Configure how to handle errors (log, abort, or retry)
- **Conditional Activation**: BeanShell expressions to control when workflow triggers

## Configuration

### Quick Start Configuration (Recommended)

**For GovStack Multi-Service Architecture:**

1. **Service ID** (REQUIRED - Single Configuration Point)
   ```
   Service ID: farmers_registry

   Format: [a-z0-9_]+ (lowercase + underscore only)
   Examples:
     - farmers_registry
     - subsidy_application
     - student_enrollment
   ```

2. **Use Naming Convention** (RECOMMENDED)
   ```
   ✓ Use Naming Convention: checked

   - Automatically resolves process: {serviceId}_submission
   - Example: farmers_registry → farmers_registry_submission
   - No need to specify Process Definition ID or Process Name
   ```

3. **Leave Process Fields Empty** (when using convention)
   ```
   Process Definition ID: (empty)
   Process Name: (empty)
   ```

### Alternative Configuration (Non-Convention)

1. **Process Definition ID**
   ```
   Format: appId#version#processId
   Example: myapp#1#documentSubmission
   Example: myapp#latest#documentSubmission
   ```

2. **Process Name** (Alternative to Process Def ID)
   ```
   Example: documentSubmission
   (Will resolve to: documentSubmission:latest)
   ```

### Configuration in Joget Form Builder

1. Open your form in Form Builder
2. Go to Properties/Settings → **Post Processing Tool**
3. Click **Add Post Processing Tool**
4. Select **Workflow Activator**
5. Configure (GovStack Multi-Service):
   - **Service ID**: `farmers_registry` (or your service ID)
   - **Use Naming Convention**: ✓ (checked - recommended)
   - Process Definition ID: (leave empty when using convention)
   - Process Name: (leave empty when using convention)
   - **Execution Mode**: Synchronous (Wait for start)
   - **Pass Form Data**: ✓ (checked)
   - **Participant Assignment**: `#currentUser.username#`
   - **Enable Detailed Logging**: ✓ (recommended for testing)

**For complete step-by-step setup, see [CONFIGURATION_GUIDE.md](../../CONFIGURATION_GUIDE.md)**

### Workflow Variables Available

When "Pass Form Data" is enabled, all form fields become workflow variables:
- **`serviceId`**: Service identifier configured in WorkflowActivator (CRITICAL for multi-service)
- All form field values (fieldName → fieldValue)
- System variables:
  - `formSubmissionTime`: Timestamp of submission
  - `activatorPlugin`: Plugin class name
  - `formRecordId`: The form's primary key (via Joget's standard mechanism)

## Integration with GovStack Plugins (DocSubmitter + ProcessingServer)

This plugin serves as the **single configuration point** in the GovStack Registration Building Block's multi-service architecture:

### Complete Flow:

1. **Form Submission**: User submits a registration form (farmers, students, subsidies, etc.)
2. **WorkflowActivator** (Post-Processing - **SINGLE CONFIG POINT**):
   - Configured with `serviceId = "farmers_registry"`
   - Sets workflow variable: `serviceId = "farmers_registry"`
   - Starts process: `farmers_registry_submission` (convention-based)
3. **Process Started**: Process ID = Form Record ID (Joget standard)
4. **DocSubmitter Tool** (Process Tool):
   - Reads `serviceId` from workflow variables
   - Loads configuration: `farmers_registry.yml`
   - Sends to API: `/services/farmers_registry/applications`
5. **ProcessingServer** (Receiver):
   - Extracts `serviceId` from URL path
   - Loads configuration: `farmers_registry.yml`
   - Saves to receiver database

### Example Workflow Design (Convention-Based)
```
Process Name: farmers_registry_submission
(Automatically resolved by WorkflowActivator using convention)

[Start] → [DocSubmitter Tool] → [End]
              ↑
      Reads serviceId from workflow variable
      Loads {serviceId}.yml
      Process ID = Form Record ID
```

### Service ID Consistency (CRITICAL)
All components must use the SAME serviceId:
- WorkflowActivator config: `farmers_registry`
- Process name: `farmers_registry_submission`
- YAML files: `farmers_registry.yml` (sender and receiver)
- API endpoint: `/services/farmers_registry/applications`

## Technical Details

### Package Structure
- **Package**: `global.govstack.workflow.activator`
- **Main Class**: `global.govstack.workflow.activator.lib.WorkflowActivator`
- **OSGi Activator**: `global.govstack.workflow.Activator`

### Dependencies
- Joget DX8 Platform (wflow-core 8.1-SNAPSHOT)
- OSGi Bundle Framework
- Jackson/Gson for JSON processing

### Key Classes
- `WorkflowActivator`: Main plugin implementation (extends `DefaultApplicationPlugin`)
- `Activator`: OSGi bundle activator
- Uses Joget's `WorkflowManager` for process management

### API Methods Used
- `workflowManager.processStart()`: Start new process
- `workflowManager.getRunningProcessById()`: Check process status
- Form data access via `FormRowSet` and `FormRow`

## Error Handling

The plugin includes robust error handling:
- Timeout protection (60 seconds) for waiting on process completion
- Null checks for workflow assignments and form data
- Comprehensive logging for debugging
- Configurable error strategies (log, abort, retry)

## Build and Installation

### Building
```bash
mvn clean package
```

### Installation
1. Build produces: `target/wf-activator-8.1-SNAPSHOT.jar`
2. In Joget: Settings → Manage Plugins → Upload Plugin
3. Select the JAR file and upload
4. Plugin available in Form Builder → Post Processing Tool

## Usage Example

### Scenario: Farmers Registry (Multi-Service Architecture)

1. **Form Setup**: Farmer registration form with fields:
   - National ID
   - Name (first, last)
   - Farm Location
   - Crop Types

2. **WorkflowActivator Configuration** (Post-Processing Tool):
   - **Service ID**: `farmers_registry` ← **SINGLE CONFIG POINT**
   - **Use Naming Convention**: ✓ checked
   - Process Def ID: (empty)
   - Process Name: (empty)
   - Pass Form Data: Yes
   - Execution Mode: Synchronous

3. **Workflow Process** (Convention-Based):
   - **Process Name**: `farmers_registry_submission` (auto-resolved)
   - Start Event
   - DocSubmitter Tool (configured with API endpoint)
   - End Event

4. **Result**:
   - Form submission → WorkflowActivator sets serviceId
   - Process `farmers_registry_submission` starts automatically
   - DocSubmitter reads serviceId from workflow variables
   - DocSubmitter loads `farmers_registry.yml`
   - Data sent to `/services/farmers_registry/applications`
   - ProcessingServer receives and saves to database
   - **No hardcoding, works for ANY service type**

### Adding a Second Service (e.g., Subsidies)

1. **Create new process**: `subsidy_application_submission`
2. **Create new YAML**: `subsidy_application.yml` (sender + receiver)
3. **Configure WorkflowActivator** on subsidy form:
   - Service ID: `subsidy_application`
   - Use Convention: ✓ checked
4. **Deploy** - Same plugins, different configuration!

## Troubleshooting

### Common Issues

1. **"ServiceId not found in workflow variables" (DocSubmitter error)**
   - **Cause**: WorkflowActivator not configured or didn't run
   - **Solution**:
     - Check form has WorkflowActivator in Post-Processing
     - Verify Service ID is set in WorkflowActivator config
     - Check logs for WorkflowActivator execution
     - See [CONFIGURATION_GUIDE.md](../../CONFIGURATION_GUIDE.md#issue-1-serviceid-not-found-in-workflow-variables)

2. **"Process not found using convention"**
   - **Cause**: Process name doesn't match `{serviceId}_submission` pattern
   - **Solution**:
     - Verify process name exactly matches: `{serviceId}_submission`
     - Example: serviceId=`farmers_registry` → process=`farmers_registry_submission`
     - Check process is mapped to application
     - Check process is published (not draft)

3. **"Invalid serviceId format"**
   - **Cause**: ServiceId contains invalid characters
   - **Solution**: Use only lowercase letters, numbers, and underscores
     - ✓ Good: `farmers_registry`, `subsidy_app`, `student_enrollment2`
     - ✗ Bad: `FarmersRegistry`, `subsidy-app`, `student enrollment`

4. **Process Not Starting**
   - Check Process Definition ID format (if not using convention)
   - Verify process exists and is deployed
   - Check logs for authentication issues

5. **Form Data Not Passing**
   - Ensure "Pass Form Data" is checked
   - Verify form fields have proper IDs
   - Check workflow variable names match form field names

6. **Timeout Issues**
   - Default timeout is 60 seconds
   - For long-running processes, use Asynchronous mode
   - Don't enable "Wait for Completion" for complex workflows

### Enable Debug Logging

Check Joget logs:
```bash
tail -f logs/joget.log | grep -E "(WorkflowActivator|serviceId)"
```

Expected logs:
```
INFO - ===== WorkflowActivator v2.0 STARTING =====
INFO - ServiceId: farmers_registry
INFO - Using convention-based process: farmers_registry_submission
INFO - Set serviceId workflow variable: farmers_registry
INFO - Successfully started workflow process: <UUID>
```

## Version History

- **8.1-SNAPSHOT**: Current development version
  - **Multi-Service Architecture** - Added serviceId configuration (single config point)
  - **Convention-Based Process Naming** - Automatic `{serviceId}_submission` resolution
  - **Package Renaming** - `global.govstack.farmreg` → `global.govstack.workflow.activator`
  - **ServiceId Validation** - Enforces `[a-z0-9_]+` format
  - Fixed API compatibility (replaced non-existent `isProcessEnded()` with `getRunningProcessById()`)
  - Added timeout protection
  - Made `getPropertyString()` public for interface compliance

## Documentation

- **[CONFIGURATION_GUIDE.md](../../CONFIGURATION_GUIDE.md)** - Complete deployment and configuration guide
- **[Joget DX8 Documentation](https://dev.joget.org/community/display/DX8/Main)** - Joget platform documentation
- **[GovStack Registration Building Block](https://govstack.gitbook.io/)** - GovStack specifications

## License
Part of GovStack Registration Building Block initiative
https://www.govstack.global

---

**Version**: 8.1-SNAPSHOT
**Package**: `global.govstack.workflow.activator`
**Last Updated**: October 28, 2025
**Architecture**: Multi-Service Support (Transport-Layer Only)