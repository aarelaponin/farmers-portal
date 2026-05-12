# Joget DX8 Guide: Sending Saved Form Records to Async Processes

## Common Scenario
After a user submits a form in Joget, you often need to:
1. Save the form data to the database
2. Get the saved record's ID
3. Start an asynchronous workflow process
4. Pass the record ID to that process for further processing

This is a critical pattern for enterprise applications where you need to process form submissions asynchronously (e.g., sending to external APIs, generating documents, approval workflows).

## The Complete Solution

### Step 1: Form Configuration

In your form's **Post Processing** section, add the **Workflow Activator** plugin (or similar Post Processing Tool).

### Step 2: Post Processing Tool Plugin

Create a Post Processing Tool that captures the saved record ID and starts the workflow:

```java
package com.company.plugin;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import java.util.Map;
import java.util.HashMap;

public class AsyncWorkflowStarter extends DefaultApplicationPlugin {

    @Override
    public Object execute(Map properties) {
        LogUtil.info(getClassName(), "===== AsyncWorkflowStarter Starting =====");
        
        try {
            // STEP 1: Extract the saved record ID
            String recordId = extractRecordId(properties);
            
            if (recordId == null || recordId.trim().isEmpty()) {
                LogUtil.error(getClassName(), null, "Failed to extract record ID");
                return "ERROR: No record ID found";
            }
            
            LogUtil.info(getClassName(), "Successfully extracted record ID: " + recordId);
            
            // STEP 2: Start the workflow with the record ID
            String processDefId = getPropertyString("processDefId"); // from plugin config
            boolean success = startWorkflowWithRecord(processDefId, recordId, properties);
            
            if (success) {
                LogUtil.info(getClassName(), "Successfully started workflow for record: " + recordId);
                return "SUCCESS: Workflow started for record " + recordId;
            } else {
                return "ERROR: Failed to start workflow";
            }
            
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error in AsyncWorkflowStarter");
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Extract record ID from various possible sources in Post Processing Tool context
     */
    private String extractRecordId(Map properties) {
        String recordId = null;
        
        // Priority 1: Direct property "recordId" (Joget sometimes provides this)
        if (properties.containsKey("recordId")) {
            recordId = properties.get("recordId").toString();
            LogUtil.info(getClassName(), "Found recordId in direct properties: " + recordId);
            return recordId;
        }
        
        // Priority 2: Direct property "id"
        if (properties.containsKey("id")) {
            recordId = properties.get("id").toString();
            LogUtil.info(getClassName(), "Found id in direct properties: " + recordId);
            return recordId;
        }
        
        // Priority 3: From FormRowSet (most common in Post Processing)
        FormRowSet rows = extractFormRowSet(properties);
        if (rows != null && !rows.isEmpty()) {
            FormRow row = rows.get(0);
            recordId = row.getId();
            LogUtil.info(getClassName(), "Found record ID from FormRowSet: " + recordId);
            return recordId;
        }
        
        // Priority 4: From primary key property
        if (properties.containsKey("primaryKey")) {
            recordId = properties.get("primaryKey").toString();
            LogUtil.info(getClassName(), "Found primaryKey in properties: " + recordId);
            return recordId;
        }
        
        return recordId;
    }
    
    /**
     * Extract FormRowSet from various possible property names
     */
    private FormRowSet extractFormRowSet(Map properties) {
        // Try different property names that Joget uses
        String[] possibleNames = {"rows", "formRowSet", "formData", "rowSet"};
        
        for (String name : possibleNames) {
            Object obj = properties.get(name);
            if (obj instanceof FormRowSet) {
                LogUtil.info(getClassName(), "Found FormRowSet in property: " + name);
                return (FormRowSet) obj;
            }
        }
        
        return null;
    }
    
    /**
     * Start workflow and properly set the record ID as workflow variable
     */
    private boolean startWorkflowWithRecord(String processDefId, String recordId, Map properties) {
        try {
            WorkflowManager workflowManager = (WorkflowManager) AppUtil
                .getApplicationContext().getBean("workflowManager");
            
            // Prepare workflow variables - CRITICAL: Include the record ID
            Map<String, String> workflowVariables = new HashMap<>();
            
            // Set multiple variable names to ensure compatibility with different process tools
            workflowVariables.put("recordId", recordId);
            workflowVariables.put("formRecordId", recordId);
            workflowVariables.put("primaryKey", recordId);
            workflowVariables.put("id", recordId);
            
            // Add any form-specific ID (e.g., if it's a customer form)
            String formDefId = getPropertyString("formDefId");
            if (formDefId != null && formDefId.contains("_")) {
                String entityName = formDefId.split("_")[0];
                workflowVariables.put(entityName + "Id", recordId);
                LogUtil.info(getClassName(), "Added entity-specific variable: " + entityName + "Id = " + recordId);
            }
            
            // Add any additional data from form if needed
            boolean passFormData = "true".equals(getPropertyString("passFormData"));
            if (passFormData) {
                addFormDataToVariables(properties, workflowVariables);
            }
            
            LogUtil.info(getClassName(), "Starting workflow with variables: " + workflowVariables.keySet());
            
            // Start the workflow
            WorkflowProcessResult result = workflowManager.processStart(
                processDefId,
                null,  // let Joget generate process ID
                workflowVariables,
                null,  // use current user
                null,  // use default start activity
                false  // don't abort if already running
            );
            
            if (result != null && result.getProcess() != null) {
                String processInstanceId = result.getProcess().getInstanceId();
                
                // CRITICAL: Also SET the variables after process start to ensure they're available
                workflowManager.setProcessVariable(processInstanceId, "recordId", recordId);
                workflowManager.setProcessVariable(processInstanceId, "formRecordId", recordId);
                
                LogUtil.info(getClassName(), "Workflow started successfully. Process ID: " + processInstanceId);
                
                // For async execution, start in separate thread
                if ("true".equals(getPropertyString("asyncMode"))) {
                    executeAsync(processInstanceId, recordId);
                }
                
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error starting workflow");
            return false;
        }
    }
    
    /**
     * Execute in separate thread for true async processing
     */
    private void executeAsync(String processInstanceId, String recordId) {
        Thread asyncThread = new Thread(() -> {
            LogUtil.info(getClassName(), "Async thread started for process: " + processInstanceId + ", record: " + recordId);
            // Any additional async processing can go here
        });
        asyncThread.setDaemon(true);
        asyncThread.start();
    }
    
    /**
     * Add form data to workflow variables if needed
     */
    private void addFormDataToVariables(Map properties, Map<String, String> variables) {
        FormRowSet rows = extractFormRowSet(properties);
        if (rows != null && !rows.isEmpty()) {
            FormRow row = rows.get(0);
            for (Object key : row.keySet()) {
                String fieldName = key.toString();
                String fieldValue = row.getProperty(fieldName);
                if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                    variables.put(fieldName, fieldValue);
                }
            }
            LogUtil.info(getClassName(), "Added " + row.size() + " form fields to workflow variables");
        }
    }
    
    // Standard plugin methods
    @Override
    public String getName() { return "Async Workflow Starter"; }
    
    @Override
    public String getVersion() { return "1.0.0"; }
    
    @Override
    public String getDescription() { return "Starts async workflow with saved form record ID"; }
    
    @Override
    public String getLabel() { return "Async Workflow Starter"; }
    
    @Override
    public String getClassName() { return getClass().getName(); }
    
    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(),
            "/properties/asyncWorkflowStarter.json", null, true, null);
    }
}
```

### Step 3: Process Tool Plugin (Receiving Side)

Create a Process Tool that receives and uses the record ID:

```java
package com.company.plugin;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import java.util.Map;

public class RecordProcessor extends DefaultApplicationPlugin {

    @Override
    public Object execute(Map properties) {
        LogUtil.info(getClassName(), "===== RecordProcessor Starting =====");
        
        try {
            // Extract the record ID from workflow context
            String recordId = extractRecordIdFromWorkflow(properties);
            
            if (recordId == null || recordId.trim().isEmpty()) {
                LogUtil.error(getClassName(), null, "No record ID found in workflow context");
                return "ERROR: No record ID";
            }
            
            LogUtil.info(getClassName(), "Processing record ID: " + recordId);
            
            // Now you can use the record ID to:
            // 1. Load the form data
            FormRow formData = loadFormData(recordId);
            
            // 2. Process the data (send to API, generate documents, etc.)
            boolean success = processRecord(recordId, formData);
            
            // 3. Update workflow status
            updateWorkflowStatus(properties, success);
            
            return success ? "SUCCESS" : "FAILED";
            
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error in RecordProcessor");
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Extract record ID from workflow context - try multiple sources
     */
    private String extractRecordIdFromWorkflow(Map properties) {
        String recordId = null;
        
        // Get workflow assignment
        WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");
        
        if (assignment == null) {
            LogUtil.warn(getClassName(), "No workflow assignment found");
            return null;
        }
        
        String processId = assignment.getProcessId();
        LogUtil.info(getClassName(), "Process ID: " + processId);
        
        // Get workflow manager
        WorkflowManager workflowManager = (WorkflowManager) AppUtil
            .getApplicationContext().getBean("workflowManager");
        
        // Try different variable names (in order of preference)
        String[] variableNames = {"recordId", "formRecordId", "primaryKey", "id"};
        
        for (String varName : variableNames) {
            recordId = workflowManager.getProcessVariable(processId, varName);
            if (recordId != null && !recordId.trim().isEmpty()) {
                LogUtil.info(getClassName(), "Found record ID in workflow variable '" + varName + "': " + recordId);
                break;
            }
        }
        
        // If still not found, check activity-level variables
        if (recordId == null || recordId.trim().isEmpty()) {
            String activityId = assignment.getActivityId();
            for (String varName : variableNames) {
                recordId = workflowManager.getActivityVariable(activityId, varName);
                if (recordId != null && !recordId.trim().isEmpty()) {
                    LogUtil.info(getClassName(), "Found record ID in activity variable '" + varName + "': " + recordId);
                    break;
                }
            }
        }
        
        return recordId;
    }
    
    /**
     * Load form data using the record ID
     */
    private FormRow loadFormData(String recordId) {
        try {
            String formDefId = getPropertyString("formDefId");
            String tableName = getPropertyString("tableName");
            
            if (tableName == null || tableName.isEmpty()) {
                tableName = "app_fd_" + formDefId.replace("_", "");
            }
            
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");
            
            FormRow row = formDataDao.load(formDefId, tableName, recordId);
            
            if (row != null) {
                LogUtil.info(getClassName(), "Successfully loaded form data for record: " + recordId);
                
                // Log some sample fields for debugging
                for (Object key : row.keySet()) {
                    String fieldName = key.toString();
                    String value = row.getProperty(fieldName);
                    if (value != null && value.length() > 0) {
                        String displayValue = value.length() > 50 ? value.substring(0, 50) + "..." : value;
                        LogUtil.info(getClassName(), "Field [" + fieldName + "] = " + displayValue);
                    }
                }
            } else {
                LogUtil.warn(getClassName(), "No form data found for record: " + recordId);
            }
            
            return row;
            
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error loading form data");
            return null;
        }
    }
    
    /**
     * Process the record (your business logic here)
     */
    private boolean processRecord(String recordId, FormRow formData) {
        try {
            // Your processing logic here
            // Examples:
            // - Send to external API
            // - Generate PDF documents
            // - Send notifications
            // - Update other systems
            
            LogUtil.info(getClassName(), "Processing record: " + recordId);
            
            // Simulate processing
            Thread.sleep(1000);
            
            return true;
            
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error processing record");
            return false;
        }
    }
    
    /**
     * Update workflow variables with processing status
     */
    private void updateWorkflowStatus(Map properties, boolean success) {
        try {
            WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");
            if (assignment == null) return;
            
            WorkflowManager workflowManager = (WorkflowManager) AppUtil
                .getApplicationContext().getBean("workflowManager");
            
            String processId = assignment.getProcessId();
            
            workflowManager.setProcessVariable(processId, "processingStatus", success ? "SUCCESS" : "FAILED");
            workflowManager.setProcessVariable(processId, "processingDate", new java.util.Date().toString());
            
            LogUtil.info(getClassName(), "Updated workflow status: " + (success ? "SUCCESS" : "FAILED"));
            
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error updating workflow status");
        }
    }
    
    // Standard plugin methods
    @Override
    public String getName() { return "Record Processor"; }
    
    @Override
    public String getVersion() { return "1.0.0"; }
    
    @Override
    public String getDescription() { return "Processes form records from workflow"; }
    
    @Override
    public String getLabel() { return "Record Processor"; }
    
    @Override
    public String getClassName() { return getClass().getName(); }
    
    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(),
            "/properties/recordProcessor.json", null, true, null);
    }
}
```

## Common Pitfalls and Solutions

### Pitfall 1: Using Wrong Method to Set Variables

❌ **Wrong:**
```java
workflowManager.processVariable(processId, "recordId", value); // This GETS, not SETS!
```

✅ **Correct:**
```java
workflowManager.setProcessVariable(processId, "recordId", value); // This SETS the variable
```

### Pitfall 2: Extracting Process Instance ID Instead of Record ID

❌ **Wrong:**
```java
// This extracts "8201" from "8201_appId_processName" - but it's NOT the form record ID!
String recordId = processId.substring(0, processId.indexOf("_"));
```

✅ **Correct:**
```java
// Get the actual record ID from workflow variables or form data
String recordId = workflowManager.getProcessVariable(processId, "recordId");
```

### Pitfall 3: Not Setting Multiple Variable Names

❌ **Wrong:**
```java
// Only setting one variable name
variables.put("id", recordId);
```

✅ **Correct:**
```java
// Set multiple names for compatibility
variables.put("recordId", recordId);
variables.put("formRecordId", recordId);
variables.put("primaryKey", recordId);
variables.put("id", recordId);
```

### Pitfall 4: Not Checking Multiple Sources for Record ID

❌ **Wrong:**
```java
// Only checking one source
String recordId = properties.get("recordId");
```

✅ **Correct:**
```java
// Check multiple sources in priority order
String recordId = extractRecordId(properties); // Method that checks multiple sources
```

## Testing Checklist

When implementing this pattern, test these scenarios:

- ✅ **Form Submission**: Submit a form and verify the record is saved
- ✅ **Record ID Extraction**: Check logs to confirm record ID is extracted correctly
- ✅ **Workflow Start**: Verify the workflow process starts
- ✅ **Variable Transfer**: Confirm record ID is available in the process tool
- ✅ **Data Loading**: Verify the process tool can load the form data using the record ID
- ✅ **Error Handling**: Test with missing or invalid record IDs

## Quick Reference

### In Post Processing Tool:
```java
// Extract record ID
String recordId = extractRecordId(properties);

// Set in workflow
workflowManager.setProcessVariable(processId, "recordId", recordId);
```

### In Process Tool:
```java
// Get record ID
String recordId = workflowManager.getProcessVariable(processId, "recordId");

// Load form data
FormRow row = formDataDao.load(formDefId, tableName, recordId);
```

## Summary

This pattern ensures reliable transfer of form record IDs from form submission to asynchronous workflow processing. Always remember:

1. **Extract** the record ID from multiple possible sources
2. **Set** (not get) workflow variables using `setProcessVariable`
3. **Use** multiple variable names for compatibility
4. **Check** workflow variables in the receiving process tool
5. **Log** extensively for debugging

This approach works reliably across different Joget versions and configurations.