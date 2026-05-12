package global.govstack.workflow.activator.lib;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import java.util.Map;
import java.util.HashMap;

/**
 * WorkflowActivator Plugin
 *
 * A Post Processing Tool plugin for Joget DX8 Enterprise Edition
 * that allows automatic workflow process invocation after form submission.
 *
 * This plugin follows Joget's configuration-over-coding philosophy
 * and integrates seamlessly with the Form Builder's Post Processing capabilities.
 */
public class WorkflowActivator extends DefaultApplicationPlugin {

    private static final String PLUGIN_NAME = "Workflow Activator";
    private static final String PLUGIN_DESCRIPTION = "Automatically invoke workflow processes after form submission";
    private static final String PLUGIN_VERSION = "8.0.6";

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public String getDescription() {
        return PLUGIN_DESCRIPTION;
    }

    @Override
    public String getLabel() {
        return PLUGIN_NAME;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(),
                "/properties/workflowActivator.json", null, true,
                "messages/workflowActivator");
    }

    /**
     * Main execution method for the Post Processing Tool
     * Called automatically after form submission and data storage
     */
    @Override
    public Object execute(Map properties) {
        System.out.println("===== WorkflowActivator v2.0 STARTING =====");
        System.out.println("Properties count: " + properties.size());
        System.out.println("Properties keys: " + properties.keySet());

        LogUtil.info(getClassName(), "===== WorkflowActivator v2.0 STARTING =====");
        LogUtil.info(getClassName(), "Properties count: " + properties.size());
        LogUtil.info(getClassName(), "Properties keys: " + properties.keySet());
        LogUtil.error(getClassName(), null, "DEBUG: WorkflowActivator v2.0 - Properties: " + properties.keySet());

        // When used as Form Post Processing Tool, the primary key is in "recordId"
        String recordId = null;

        // Try to get the record ID from various sources
        if (properties.containsKey("recordId")) {
            recordId = properties.get("recordId").toString();
            LogUtil.info(getClassName(), "Found recordId directly: " + recordId);
        }

        // Also check for id in properties
        if (recordId == null && properties.containsKey("id")) {
            recordId = properties.get("id").toString();
            LogUtil.info(getClassName(), "Found id directly: " + recordId);
        }

        // Log all properties for debugging
        for (Object key : properties.keySet()) {
            Object value = properties.get(key);
            if (value != null) {
                String valueStr = value.toString();
                if (valueStr.length() > 200) {
                    valueStr = valueStr.substring(0, 200) + "...";
                }
                LogUtil.info(getClassName(), "Property [" + key + "] = " + valueStr);
            }
        }

        try {
            // Get configuration properties
            String processDefId = getPropertyString("processDefId");
            String processName = getPropertyString("processName");
            String runMode = getPropertyString("runMode");
            boolean passFormData = "true".equals(getPropertyString("passFormData"));
            boolean waitForCompletion = "true".equals(getPropertyString("waitForCompletion"));
            String participantMapping = getPropertyString("participantMapping");

            // Get serviceId configuration (REQUIRED for multi-service support)
            String serviceId = getPropertyString("serviceId");
            if (serviceId == null || serviceId.trim().isEmpty()) {
                LogUtil.error(getClassName(), null, "ServiceId is required but not configured");
                return null;
            }

            // Validate serviceId format (alphanumeric + underscore only)
            if (!serviceId.matches("^[a-z0-9_]+$")) {
                LogUtil.error(getClassName(), null, "Invalid serviceId format: " + serviceId + ". Use lowercase, numbers, underscore only.");
                return null;
            }

            LogUtil.info(getClassName(), "ServiceId: " + serviceId);

            // Determine process definition ID
            boolean useConvention = "true".equals(getPropertyString("useConvention"));

            if (useConvention && (processDefId == null || processDefId.trim().isEmpty())) {
                // Use convention: {serviceId}_submission
                String conventionProcessName = serviceId + "_submission";
                processDefId = resolveProcessDefId(conventionProcessName);

                if (processDefId == null) {
                    LogUtil.error(getClassName(), null,
                        "Process not found using convention: " + conventionProcessName +
                        ". Please create process or manually specify processDefId.");
                    return null;
                }

                LogUtil.info(getClassName(), "Using convention-based process: " + conventionProcessName);
            } else if (processDefId == null || processDefId.trim().isEmpty()) {
                if (processName == null || processName.trim().isEmpty()) {
                    LogUtil.warn(getClassName(), "Process Definition ID or Process Name must be specified");
                    return null;
                }
                // Resolve process definition ID from process name
                processDefId = resolveProcessDefId(processName);
            }

            if (processDefId == null) {
                LogUtil.warn(getClassName(), "Could not resolve Process Definition ID");
                return null;
            }

            // Get workflow manager
            WorkflowManager workflowManager = (WorkflowManager) AppUtil
                    .getApplicationContext().getBean("workflowManager");

            // Prepare workflow variables from form data
            Map<String, String> workflowVariables = prepareWorkflowVariables(properties, passFormData);

            // Add serviceId to workflow variables (CRITICAL for multi-service support)
            workflowVariables.put("serviceId", serviceId);
            LogUtil.info(getClassName(), "Set serviceId workflow variable: " + serviceId);

            // Ensure we have the record ID
            if (recordId != null && !recordId.trim().isEmpty()) {
                workflowVariables.put("recordId", recordId);
                workflowVariables.put("id", recordId);
                LogUtil.info(getClassName(), "Set workflow variables with recordId: " + recordId);
            }

            // Add participant mapping if specified
            if (participantMapping != null && !participantMapping.trim().isEmpty()) {
                workflowVariables.put("participant", participantMapping);
            }

            // Log the critical workflow variables before starting
            LogUtil.info(getClassName(), "About to start workflow with variables:");
            LogUtil.info(getClassName(), "  - serviceId: " + workflowVariables.get("serviceId"));
            LogUtil.info(getClassName(), "  - recordId: " + workflowVariables.get("recordId"));
            LogUtil.info(getClassName(), "  - id: " + workflowVariables.get("id"));

            // Execute workflow based on run mode
            WorkflowProcessResult result = null;

            if ("async".equals(runMode)) {
                // Asynchronous execution - don't wait for completion
                result = executeWorkflowAsync(workflowManager, processDefId, workflowVariables);
            } else {
                // Synchronous execution - default behavior
                result = executeWorkflowSync(workflowManager, processDefId, workflowVariables, waitForCompletion);
            }

            // Log results
            if (result != null && result.getProcess() != null) {
                String processId = result.getProcess().getInstanceId();
                LogUtil.info(getClassName(), "Successfully started workflow process: " + processId);

                // Store process ID in properties for potential use by subsequent tools
                properties.put("startedProcessId", processId);
                properties.put("workflowResult", result);

                return result;
            } else {
                LogUtil.warn(getClassName(), "Failed to start workflow process");
                return null;
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error executing WorkflowActivator: " + e.getMessage());
            return null;
        }
    }

    /**
     * Execute workflow synchronously
     */
    private WorkflowProcessResult executeWorkflowSync(WorkflowManager workflowManager,
                                                      String processDefId, Map<String, String> variables, boolean waitForCompletion) {

        try {
            LogUtil.info(getClassName(), "Starting synchronous workflow: " + processDefId);
            LogUtil.info(getClassName(), "Workflow variables passed: " + variables);

            // Start the workflow process
            WorkflowProcessResult result = workflowManager.processStart(
                    processDefId,
                    null, // processId - let Joget generate it
                    variables,
                    null, // username - use current user
                    null, // startActivityId - use default start
                    false // abortIfRunning
            );


            if (waitForCompletion && result != null && result.getProcess() != null) {
                // Wait for process completion (useful for simple workflows)
                String processId = result.getProcess().getInstanceId();
                LogUtil.info(getClassName(), "Waiting for process completion: " + processId);

                // Timeout mechanism to prevent infinite loops
                int maxWaitTime = 60; // Maximum 60 seconds
                int waited = 0;
                while (workflowManager.getRunningProcessById(processId) != null && waited < maxWaitTime) {
                    Thread.sleep(1000); // Wait 1 second
                    waited++;
                }

                if (waited >= maxWaitTime) {
                    LogUtil.warn(getClassName(), "Process completion timeout after " + maxWaitTime + " seconds: " + processId);
                } else {
                    LogUtil.info(getClassName(), "Process completed: " + processId);
                }
            }

            return result;

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error in synchronous workflow execution");
            return null;
        }
    }

    /**
     * Execute workflow asynchronously using thread
     */
    private WorkflowProcessResult executeWorkflowAsync(WorkflowManager workflowManager,
                                                       String processDefId, Map<String, String> variables) {

        try {
            LogUtil.info(getClassName(), "Starting asynchronous workflow: " + processDefId);
            LogUtil.info(getClassName(), "Async workflow variables passed: " + variables);

            // Create a new thread for workflow execution
            Thread workflowThread = new Thread(() -> {
                try {
                    WorkflowProcessResult asyncResult = workflowManager.processStart(
                            processDefId,
                            null, // processId - let Joget generate it
                            variables,
                            null,
                            null,
                            false
                    );

                    if (asyncResult != null && asyncResult.getProcess() != null) {
                        String processInstanceId = asyncResult.getProcess().getInstanceId();
                        LogUtil.info(getClassName(), "Async workflow started: " + processInstanceId);
                    }
                } catch (Exception e) {
                    LogUtil.error(getClassName(), e, "Error in async workflow execution");
                }
            });

            workflowThread.setDaemon(true);
            workflowThread.start();

            // Return a placeholder result for immediate response
            // Note: Actual process ID won't be available immediately
            LogUtil.info(getClassName(), "Async workflow thread started");
            return null; // or create a custom result object

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error starting async workflow");
            return null;
        }
    }

    /**
     * Prepare workflow variables from form data and plugin properties
     */
    private Map<String, String> prepareWorkflowVariables(Map properties, boolean passFormData) {
        Map<String, String> variables = new HashMap<>();

        try {
            // In Joget Post Processing Tool, the primary key is available after form save
            // Get it from the form row that was just saved
            FormRowSet rows = null;

            // Try different property names that Joget uses
            Object rowsObj = properties.get("rows");
            if (rowsObj instanceof FormRowSet) {
                rows = (FormRowSet) rowsObj;
                LogUtil.info(getClassName(), "Found FormRowSet in 'rows' property");
            }

            if (rows == null) {
                rowsObj = properties.get("formRowSet");
                if (rowsObj instanceof FormRowSet) {
                    rows = (FormRowSet) rowsObj;
                    LogUtil.info(getClassName(), "Found FormRowSet in 'formRowSet' property");
                }
            }

            if (rows == null) {
                rowsObj = properties.get("formData");
                if (rowsObj instanceof FormRowSet) {
                    rows = (FormRowSet) rowsObj;
                    LogUtil.info(getClassName(), "Found FormRowSet in 'formData' property");
                }
            }

            if (rows != null && !rows.isEmpty()) {
                FormRow row = rows.get(0);

                // Get the primary key (ID) of the saved record
                String recordId = row.getId();

                if (recordId != null && !recordId.trim().isEmpty()) {
                    // Set multiple variable names to ensure compatibility
                    variables.put("recordId", recordId);
                    variables.put("id", recordId);
                    LogUtil.info(getClassName(), "Successfully set recordId workflow variable: " + recordId);
                } else {
                    LogUtil.warn(getClassName(), "FormRow.getId() returned null or empty");
                }

                // Also add all form fields if configured
                if (passFormData) {
                    for (Object key : row.keySet()) {
                        String fieldName = key.toString();
                        String fieldValue = row.getProperty(fieldName);

                        if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                            variables.put(fieldName, fieldValue);
                        }
                    }
                    LogUtil.info(getClassName(), "Added " + variables.size() + " total workflow variables");
                }
            } else {
                LogUtil.error(getClassName(), null, "No FormRowSet found in properties - cannot extract record ID");
                LogUtil.info(getClassName(), "Available properties: " + properties.keySet());
            }

            // Add custom workflow variables from plugin configuration
            String customVariables = getPropertyString("customVariables");
            if (customVariables != null && !customVariables.trim().isEmpty()) {
                parseCustomVariables(customVariables, variables);
            }

            // Add system variables
            variables.put("formSubmissionTime", String.valueOf(System.currentTimeMillis()));
            variables.put("activatorPlugin", getClassName());

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error preparing workflow variables");
        }

        return variables;
    }

    /**
     * Parse custom variables from configuration
     * Expected format: "var1=value1;var2=value2"
     */
    private void parseCustomVariables(String customVariables, Map<String, String> variables) {
        try {
            String[] pairs = customVariables.split(";");
            for (String pair : pairs) {
                if (pair.contains("=")) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();
                        variables.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error parsing custom variables");
        }
    }

    /**
     * Resolve process definition ID from process name
     * Constructs the proper Joget process definition ID format: packageId:latest:processDefId
     *
     * Note: Joget uses packageId:packageVersion:processDefId format (with colons)
     * We use "latest" to always pick up the most recent package version
     */
    private String resolveProcessDefId(String processName) {
        try {
            // Get app definition from properties
            AppDefinition appDef = (AppDefinition) getProperty("appDef");

            if (appDef == null) {
                LogUtil.error(getClassName(), null, "AppDefinition not found in properties");
                return null;
            }

            String packageId = appDef.getId();

            // Construct process definition ID in Joget format: packageId:latest:processDefId
            // Using "latest" ensures we always get the most recent package version
            String processDefId = packageId + ":latest:" + processName;

            LogUtil.info(getClassName(), "Resolved process definition ID: " + processDefId);
            return processDefId;

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error resolving process definition ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to get property string value
     */
    public String getPropertyString(String key) {
        Object value = getProperty(key);
        return value != null ? value.toString() : null;
    }
}