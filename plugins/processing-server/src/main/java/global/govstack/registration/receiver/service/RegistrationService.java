package global.govstack.registration.receiver.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.config.Constants;
import global.govstack.registration.receiver.exception.ConfigurationException;
import global.govstack.registration.receiver.exception.FormSubmissionException;
import global.govstack.registration.receiver.exception.InvalidRequestException;
import global.govstack.registration.receiver.exception.WorkflowProcessingException;
import global.govstack.registration.receiver.exception.ApiProcessingException;
import global.govstack.registration.receiver.exception.ValidationException;
import global.govstack.registration.receiver.util.UserContextUtil;
import org.json.JSONObject;
import org.joget.workflow.model.service.WorkflowUserManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Main service orchestrating the registration process flow
 */
public class RegistrationService implements ApiRequestProcessor {
    private final ConfigurationService configService;
    private final WorkflowService workflowService;
    private final ResponseBuilder responseBuilder;
    private final WorkflowUserManager workflowUserManager;
    private static final String CLASS_NAME = RegistrationService.class.getName();

    /**
     * Constructor with dependency injection
     */
    public RegistrationService(
            ConfigurationService configService,
            WorkflowService workflowService,
            ResponseBuilder responseBuilder,
            WorkflowUserManager workflowUserManager) {
        this.configService = configService;
        this.workflowService = workflowService;
        this.responseBuilder = responseBuilder;
        this.workflowUserManager = workflowUserManager;
    }

    /**
     * Process an API request - implementation of ApiRequestProcessor interface
     */
    @Override
    public JSONObject processRequest(String requestBody) throws ApiProcessingException {
        try {
            return processApplication(requestBody);
        } catch (InvalidRequestException e) {
            throw ApiProcessingException.invalidRequest(e.getMessage());
        } catch (ValidationException e) {
            throw ApiProcessingException.validationError(e.getMessage());
        } catch (FormSubmissionException e) {
            throw ApiProcessingException.formSubmissionError(e.getMessage());
        } catch (WorkflowProcessingException e) {
            throw ApiProcessingException.workflowError(e.getMessage());
        } catch (ConfigurationException e) {
            throw ApiProcessingException.configError(e.getMessage());
        } catch (Exception e) {
            throw ApiProcessingException.serverError("Error processing application: " + e.getMessage());
        }
    }

    /**
     * Main method to process a registration application - streamlined with clear steps
     *
     * @param requestBody JSON string with application data
     * @return JSONObject containing the response data
     */
    public JSONObject processApplication(String requestBody)
            throws InvalidRequestException, ValidationException, FormSubmissionException,
            WorkflowProcessingException, ConfigurationException {

        // Step 1: Validate request and load configuration
        Map<String, String> configValues = validateAndLoadConfig(requestBody);
        JsonObject configJson = getConfigJson();

        // Step 2: Prepare parameters for processing
        String formId = configValues.get(Constants.FORM_ID);
        String processDefId = configValues.get(Constants.PROCESS_DEF_ID);
        String adminUsername = configValues.get(Constants.ADMIN_USERNAME);
        String registrantUsername = configValues.get(Constants.REGISTRANT_USERNAME);
        String chiefReviewerUsername = configValues.get(Constants.CHIEF_REVIEWER_USERNAME);
        String submitActivityId = configValues.get(Constants.SUBMIT_ACTIVITY_ID);
        String reviewActivityId = configValues.get(Constants.REVIEW_ACTIVITY_ID);
        String configJsonStr = new Gson().toJson(configJson);

        // Step 3: Submit form data
        String submittedFormId = submitFormData(requestBody, formId, configJsonStr, registrantUsername, adminUsername);

        // Step 4: Start workflow process
        String processId = startWorkflowProcess(processDefId, submittedFormId, registrantUsername, adminUsername);

        // Step 5: Process submit activity
        processSubmitActivity(processId, processDefId, submitActivityId, registrantUsername, adminUsername, submittedFormId);

        // Step 6: Process review activity
        processReviewActivity(processId, processDefId, reviewActivityId, adminUsername, chiefReviewerUsername);

        // Step 7: Build and return response
        return responseBuilder.buildResponse(processId, submittedFormId);
    }

    /**
     * Step 1: Validate request and load configuration
     */
    private Map<String, String> validateAndLoadConfig(String requestBody)
            throws InvalidRequestException, ConfigurationException {
        return configService.validateAndLoadConfig(requestBody);
    }

    /**
     * Get the loaded configuration JSON object
     */
    private JsonObject getConfigJson() throws ConfigurationException {
        return configService.getConfigJson();
    }

    /**
     * Step 3: Submit form data with proper user context
     */
    private String submitFormData(String requestBody, String formId, String config,
                                  String registrantUsername, String adminUsername)
            throws FormSubmissionException, ValidationException {

        try {
            // Execute as farmer with proper user context management
            return UserContextUtil.executeAsUser(workflowUserManager, registrantUsername, adminUsername, () -> {
                try {
                    return submitForm(requestBody, formId, config);
                } catch (Exception e) {
                    if (e instanceof FormSubmissionException) {
                        throw new RuntimeException(e);
                    }
                    if (e instanceof ValidationException) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(new FormSubmissionException(
                            "Error submitting form: " + e.getMessage(), e));
                }
            });
        } catch (RuntimeException e) {
            // Unwrap the original exception if possible
            Throwable cause = e.getCause();
            if (cause instanceof ValidationException) {
                throw (ValidationException) cause;
            }
            if (cause instanceof FormSubmissionException) {
                throw (FormSubmissionException) cause;
            }
            throw new FormSubmissionException("Error in form submission: " + e.getMessage(), e);
        }
    }

    /**
     * Submit the form data and get the form ID
     */
    private String submitForm(String requestBody, String formId, String config)
            throws FormSubmissionException, ValidationException {
        try {
            // Parse the request into JSON
            JSONObject payloadJson = new JSONObject(requestBody);

            // Process the registration data
            Map<String, String> formData = FormDataProcessor.processRegistrationData(config, payloadJson.toString());

            // Validate form data
            if (formData == null || formData.isEmpty()) {
                throw new FormSubmissionException("No valid form data was processed from request");
            }

            // Submit the data
            FormSubmissionManager formSubmissionManager = new FormSubmissionManager(formId);
            String submittedId = formSubmissionManager.saveData(formData);

            // Validate submission result
            if (submittedId == null || submittedId.trim().isEmpty()) {
                throw new FormSubmissionException("Form submission failed: No ID returned");
            }

            return submittedId;
        } catch (ValidationException e) {
            // Log the validation error
            LogUtil.warn(CLASS_NAME, "Validation error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            if (e instanceof ValidationException) {
                throw (ValidationException) e;
            }
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error processing form data: " + e.getMessage(), e);
        }
    }

    /**
     * Step 4: Start workflow process with proper user context
     */
    private String startWorkflowProcess(String processDefId, String submittedFormId,
                                        String registrantUsername, String adminUsername)
            throws WorkflowProcessingException {

        try {
            // Execute as farmer with proper user context management
            return UserContextUtil.executeAsUser(workflowUserManager, registrantUsername, adminUsername, () -> {
                try {
                    Map<String, String> variables = createWorkflowVariables(submittedFormId);
                    return workflowService.startWorkflowProcess(
                            processDefId,
                            variables,
                            registrantUsername,
                            submittedFormId
                    );
                } catch (Exception e) {
                    if (e instanceof WorkflowProcessingException) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(new WorkflowProcessingException(
                            "Error starting workflow: " + e.getMessage(), e));
                }
            });
        } catch (RuntimeException e) {
            // Unwrap the original exception if possible
            Throwable cause = e.getCause();
            if (cause instanceof WorkflowProcessingException) {
                throw (WorkflowProcessingException) cause;
            }
            throw new WorkflowProcessingException("Error in workflow process: " + e.getMessage(), e);
        }
    }

    /**
     * Step 5: Process submit activity with proper user context
     */
    private void processSubmitActivity(String processId, String processDefId, String submitActivityId,
                                       String registrantUsername, String adminUsername, String submittedFormId) {

        UserContextUtil.executeAsUser(workflowUserManager, registrantUsername, adminUsername, () -> {
            try {
                Map<String, String> variables = createWorkflowVariables(submittedFormId);
                boolean success = workflowService.processSubmitActivity(
                        processId,
                        processDefId,
                        submitActivityId,
                        registrantUsername,
                        variables
                );
                LogUtil.info(CLASS_NAME, "Submit activity processed: " + success);
            } catch (Exception e) {
                LogUtil.warn(CLASS_NAME, "Error processing submit activity: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Step 6: Process review activity with proper user context
     */
    private void processReviewActivity(String processId, String processDefId, String reviewActivityId,
                                       String adminUsername, String chiefReviewerUsername) {

        UserContextUtil.executeAsUser(workflowUserManager, adminUsername, adminUsername, () -> {
            try {
                boolean success = workflowService.processReviewActivity(
                        processId,
                        processDefId,
                        reviewActivityId,
                        adminUsername,
                        chiefReviewerUsername
                );
                LogUtil.info(CLASS_NAME, "Review activity processed: " + success);
            } catch (Exception e) {
                LogUtil.warn(CLASS_NAME, "Error processing review activity: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Create workflow variables for the process
     */
    private Map<String, String> createWorkflowVariables(String formId) {
        Map<String, String> variables = new HashMap<>();
        variables.put(Constants.STATUS, Constants.STATUS_PENDING);
        variables.put(Constants.FORM_ID, formId);
        return variables;
    }
}