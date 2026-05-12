# GovStack Registration Building Block - Configuration Guide

**Version:** 8.1-SNAPSHOT  
**Date:** October 28, 2025  
**Architecture:** Multi-Service Support (Transport-Layer Only)

This guide provides step-by-step instructions for deploying and configuring the GovStack Registration Building Block plugins from scratch. Use this as your testing guide.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Clean Slate Preparation](#clean-slate-preparation)
3. [Plugin Deployment](#plugin-deployment)
4. [Sender Configuration](#sender-configuration)
5. [Receiver Configuration](#receiver-configuration)
6. [Testing & Verification](#testing--verification)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Components

**Sender Joget Instance** (e.g., Farmers Portal):
- Joget DX 8.1 Enterprise Edition
- Forms already created
- Database: `jwdb` (sender)

**Receiver Joget Instance** (e.g., Ministry):
- Joget DX 8.1 Enterprise Edition
- Forms already created (matching structure)
- Database: `jwdb` (receiver)

### Built Plugin JARs

```
wf-activator-8.1-SNAPSHOT.jar        (2.2 MB)
doc-submitter-8.1-SNAPSHOT.jar       (6.4 MB)
processing-server-8.1-SNAPSHOT.jar   (2.7 MB)
```

**Location:**
- `/path/to/wf-activator/target/wf-activator-8.1-SNAPSHOT.jar`
- `/path/to/doc-submitter/target/doc-submitter-8.1-SNAPSHOT.jar`
- `/path/to/processing-server/target/processing-server-8.1-SNAPSHOT.jar`

### Configuration Files

**IMPORTANT:** YAML files (`farmers_registry.yml`) are embedded in the JARs. If you modify them, you must rebuild the JARs.

---

## Clean Slate Preparation

### On Sender Joget

```bash
# 1. Stop Joget
cd /path/to/sender-joget
./joget.sh stop

# 2. Remove old plugins
cd wflow/app_plugins
rm -f wf-activator-*.jar doc-submitter-*.jar *farmer*.jar
ls -lh *.jar  # Verify cleanup

# 3. Start Joget
cd ../..
./joget.sh start
```

### On Receiver Joget

```bash
# 1. Stop Joget
cd /path/to/receiver-joget
./joget.sh stop

# 2. Remove old plugins
cd wflow/app_plugins
rm -f processing-server-*.jar *farmer*.jar
ls -lh *.jar

# 3. Start Joget
cd ../..
./joget.sh start
```

---

## Plugin Deployment

### 1. Deploy WorkflowActivator (Sender Only)

1. **Login to Sender Joget:** `http://sender:8080/jw`

2. **Navigate to Plugin Manager:**  
   **Settings** → **Manage Plugins**

3. **Upload Plugin:**
   - Click **Upload Plugin**
   - Select: `wf-activator-8.1-SNAPSHOT.jar`
   - Click **Upload**

4. **Verify:**
   - Plugin Name: **Workflow Activator**
   - Version: **8.0.6**
   - Status: **Active**

5. **Restart Joget:**
   ```bash
   ./joget.sh restart
   ```

### 2. Deploy DocSubmitter (Sender Only)

1. **Upload Plugin:**
   - **Settings** → **Manage Plugins** → **Upload Plugin**
   - Select: `doc-submitter-8.1-SNAPSHOT.jar`

2. **Verify:**
   - Plugin Name: **GovStack Document Submitter**
   - Version: **8.1-SNAPSHOT**

3. **Restart Joget:**
   ```bash
   ./joget.sh restart
   ```

### 3. Deploy ProcessingServer (Receiver Only)

1. **Login to Receiver Joget:** `http://receiver:8080/jw`

2. **Upload Plugin:**
   - **Settings** → **Manage Plugins** → **Upload Plugin**
   - Select: `processing-server-8.1-SNAPSHOT.jar`

3. **Verify:**
   - Plugin Name: **GovStack Registration Receiver**
   - Version: **8.1-SNAPSHOT**

4. **Restart Joget:**
   ```bash
   ./joget.sh restart
   ```

5. **Verify API Endpoint:**
   ```bash
   tail -f logs/joget.log | grep "Registered API endpoint"
   # Look for: /services/{serviceId}/applications
   ```

---

## Sender Configuration

### Step 1: Configure Receiver API (Get Credentials)

**On Receiver Joget:**

1. **Configure Plugin:**
   - **Settings** → **Manage Plugins**
   - Find **GovStack Registration Receiver**
   - Click **Configure**
   - Check: **Use GovStack Mode** = ✓ Enabled
   - Click **Submit**

2. **Generate API Key:**
   - **Settings** → **API Management** → **API Keys**
   - Click **New API Key**
   - Name: `Farmers Portal Sender`
   - **Submit** → **Copy API ID and API Key**

3. **Note Endpoint URL:**
   ```
   http://receiver:8080/jw/api/services/farmers_registry/applications
   ```

### Step 2: Create Workflow Process (Sender)

**CRITICAL:** Process name MUST be: `{serviceId}_submission`

1. **Create Process:**
   - **Apps** → **Farmer App** → **Processes**
   - Click **Create New Process**

2. **Process Details:**
   ```
   Process ID: farmers_registry_submission
   Process Name: Farmers Registry Submission
   Version: 1
   ```

3. **Process Design:**
   - Add: **Start** → **Activity** → **End**
   - Activity Name: `Send to Ministry`
   - Activity Type: **Tool**

4. **Configure DocSubmitter Tool:**
   - Double-click activity → **Tools** tab
   - Click **Add Tool**
   - Plugin: **GovStack Document Submitter**

5. **DocSubmitter Configuration:**
   ```
   API Endpoint: http://receiver:8080/jw/api
   API ID: [paste from Step 1.2]
   API Key: [paste from Step 1.2]
   
   Extraction Mode: Workflow Assignment
   
   ✓ Validate Before Sending
   ✓ Update Workflow Status
   ✓ Log JSON Payload (for testing)
   
   Connection Timeout: 30 seconds
   ```

6. **Save Process** → **Map to Application**

### Step 3: Configure WorkflowActivator (THE CRITICAL STEP)

This is the **single configuration point** for serviceId.

1. **Open Form:**
   - **Apps** → **Farmer App** → **Forms**
   - Open main registration form (e.g., `farmerBasicInfo`)

2. **Add Post-Processing:**
   - **Advanced** → **Post Form Submission Processing**
   - Click **Add Post Processing Tool**
   - Select: **Workflow Activator**

3. **WorkflowActivator Configuration:**

   **Workflow Configuration:**
   ```
   Service ID: farmers_registry
      ↑
      CRITICAL: This MUST match:
      - Process name: farmers_registry_submission
      - YAML file: farmers_registry.yml
      - API URL: /services/farmers_registry/applications
   
   ✓ Use Naming Convention: Yes (recommended)
      When checked, auto-starts: {serviceId}_submission
   
   Leave these EMPTY:
   - Process Definition ID: (empty)
   - Process Name: (empty)
   
   Execution Mode: Synchronous (Wait for start)
   □ Wait for Process Completion: No
   ```

   **Data Mapping:**
   ```
   ✓ Pass Form Data to Workflow: Yes
   
   Participant Assignment: #currentUser.username#
   
   Custom Workflow Variables: (leave empty)
   ```

   **Advanced:**
   ```
   ✓ Enable Detailed Logging: Yes
   
   Error Handling: Log error and continue
   
   Activation Condition: (leave empty)
   ```

4. **Submit** → **Save Form**

### Step 4: Verify Configuration

✅ **Checklist:**
```
Process Name:     farmers_registry_submission  ✓
ServiceId Config: farmers_registry              ✓
Pattern Match:    {serviceId}_submission       ✓
YAML File:        farmers_registry.yml          ✓
API URL:          /services/farmers_registry/... ✓
```

---

## Receiver Configuration

### Step 1: Verify Plugin

1. **Check Status:**
   - **Settings** → **Manage Plugins**
   - **GovStack Registration Receiver** = **Active**

2. **Check Logs:**
   ```bash
   tail -100 logs/joget.log | grep "RegistrationServiceProvider"
   ```

### Step 2: Test API Endpoint

```bash
curl -X POST \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}' \
  http://receiver:8080/jw/api/services/farmers_registry/applications
```

Expected: HTTP 400 (endpoint exists but data invalid)  
If HTTP 404: Endpoint not registered - check plugin

### Step 3: Verify YAML Embedded

```bash
cd wflow/app_plugins
unzip -p processing-server-8.1-SNAPSHOT.jar \
  docs-metadata/farmers_registry.yml | head -20
```

Should show:
```yaml
service:
  id: farmers_registry
  name: "Farmers Registry Service"
  ...
```

---

## Testing & Verification

### Test Flow

```
1. User submits form on Sender
   ↓
2. WorkflowActivator (Post-Processing):
   - Sets serviceId = farmers_registry
   - Starts: farmers_registry_submission
   ↓
3. DocSubmitter (Process Tool):
   - Reads serviceId from workflow variables
   - Loads farmers_registry.yml
   - Sends to: /services/farmers_registry/applications
   ↓
4. ProcessingServer receives:
   - Extracts serviceId from URL
   - Loads farmers_registry.yml
   - Saves to receiver database
```

### Step-by-Step Test

#### 1. Monitor Logs

**Sender:**
```bash
tail -f logs/joget.log | grep -E "(WorkflowActivator|DocSubmitter|serviceId)"
```

**Receiver:**
```bash
tail -f logs/joget.log | grep -E "(RegistrationServiceProvider|farmers_registry)"
```

#### 2. Submit Test Form

Fill in test data and click **Submit**

#### 3. Expected Sender Logs

```
INFO - ===== WorkflowActivator v2.0 STARTING =====
INFO - ServiceId: farmers_registry
INFO - Using convention-based process: farmers_registry_submission
INFO - Set serviceId workflow variable: farmers_registry
INFO - Successfully started workflow process: <UUID>

[After a moment...]

INFO - Executing GovStack Document Submitter Plugin
INFO - Found serviceId in workflow variable 'serviceId': farmers_registry
INFO - Loading metadata from classpath: docs-metadata/farmers_registry.yml
INFO - Successfully loaded metadata for service: farmers_registry
INFO - Processing registration data for record ID: <UUID>
INFO - GovStack JSON payload: {...}
INFO - Successfully sent data to GovStack API
```

#### 4. Expected Receiver Logs

```
INFO - Processing request for serviceId: farmers_registry
INFO - Using GovStackRegistrationService for serviceId from URL: farmers_registry
INFO - Loading metadata from classpath: docs-metadata/farmers_registry.yml
INFO - Successfully loaded metadata for service: farmers_registry
INFO - Processing GovStack JSON request
INFO - Saving data to multiple forms...
INFO - Successfully processed request
```

#### 5. Verify Database

```sql
-- Receiver database
SELECT * FROM app_fd_farmer_basic_data
ORDER BY dateCreated DESC LIMIT 1;
```

Should see new farmer record with your test data.

---

## Troubleshooting

### Issue 1: "ServiceId not found in workflow variables"

**Symptom:** DocSubmitter error

**Cause:** WorkflowActivator not configured or didn't run

**Solution:**
1. Check form has WorkflowActivator in Post-Processing
2. Check serviceId is set in WorkflowActivator config
3. Resubmit form, check for WorkflowActivator logs

### Issue 2: "Process not found using convention"

**Symptom:** WorkflowActivator error

**Cause:** Process name doesn't match

**Solution:**
1. Verify process name: `farmers_registry_submission`
2. Check process is mapped to application
3. Check process is published (not draft)

### Issue 3: "Metadata file not found"

**Symptom:** Plugin fails to load configuration

**Solution:**
```bash
# Verify YAML in JAR
unzip -l doc-submitter-8.1-SNAPSHOT.jar | grep farmers_registry.yml

# Check spelling matches EXACTLY everywhere:
# - WorkflowActivator: farmers_registry
# - Process: farmers_registry_submission
# - YAML: farmers_registry.yml
# - URL: /services/farmers_registry/applications
```

### Issue 4: HTTP 401 Unauthorized

**Symptom:** DocSubmitter fails with 401

**Solution:**
1. Regenerate API key on receiver
2. Update DocSubmitter configuration
3. Test with curl

### Issue 5: Data Not Saving

**Symptom:** Success but NULL fields

**Solution:**
1. Enable "Log JSON Payload" in DocSubmitter
2. Check JSON structure in sender logs
3. Compare with receiver's `farmers_registry.yml`
4. Check field mappings match database schema

---

## Service ID Consistency Checklist

For service: `farmers_registry`

| Component | Value | Check |
|-----------|-------|-------|
| WorkflowActivator | `farmers_registry` | ☐ |
| Process Name | `farmers_registry_submission` | ☐ |
| Sender YAML | `farmers_registry.yml` | ☐ |
| Receiver YAML | `farmers_registry.yml` | ☐ |
| API URL | `/services/farmers_registry/applications` | ☐ |
| YAML service.id | `farmers_registry` | ☐ |

**ALL must match exactly (lowercase + underscore only)**

---

## Adding a Second Service

To add `subsidy_application` service:

1. **Create new YAMLs:**
   - `doc-submitter/src/main/resources/docs-metadata/subsidy_application.yml`
   - `processing-server/src/main/resources/docs-metadata/subsidy_application.yml`

2. **Rebuild JARs:**
   ```bash
   mvn clean package -Dmaven.test.skip=true
   ```

3. **Redeploy plugins via Joget UI**

4. **Create process:** `subsidy_application_submission`

5. **Configure WorkflowActivator on subsidy form:**
   - Service ID: `subsidy_application`

**No code changes needed!**

---

## Log Files

**Sender:** `/path/to/sender-joget/logs/joget.log`  
**Receiver:** `/path/to/receiver-joget/logs/joget.log`

**Useful grep:**
```bash
# Full flow
tail -f joget.log | grep -E "(WorkflowActivator|DocSubmitter|serviceId)"

# Errors only
tail -f joget.log | grep ERROR
```

---

**Document Version:** 1.0  
**Last Updated:** October 28, 2025  
**Architecture:** Multi-Service, Transport-Layer Only
