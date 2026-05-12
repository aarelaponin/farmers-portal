package global.govstack.registration.receiver.service;

import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.exception.WorkflowProcessingException;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;

import java.util.Collection;
import java.util.Map;

/**
 * Service for handling workflow operations
 */
public class WorkflowService {
    private final WorkflowManager workflowManager;
    private final WorkflowUserManager workflowUserManager;

    public WorkflowService(WorkflowManager workflowManager, WorkflowUserManager workflowUserManager) {
        this.workflowManager = workflowManager;
        this.workflowUserManager = workflowUserManager;
    }

    /**
     * Start the workflow process
     */
    public String startWorkflowProcess(String processDefId, Map<String, String> variables,
                                       String username, String formId)
            throws WorkflowProcessingException {
        try {
            String exactProcessDefId = workflowManager.getConvertedLatestProcessDefId(processDefId);

            if (exactProcessDefId == null || exactProcessDefId.trim().isEmpty()) {
                throw new WorkflowProcessingException("Process definition ID cannot be empty");
            }

            WorkflowProcessResult result = workflowManager.processStart(
                    exactProcessDefId, null, variables, username, formId, false);

            if (result == null || result.getProcess() == null) {
                throw new WorkflowProcessingException("Process not started properly: Result or process is null");
            }

            String processId = result.getProcess().getInstanceId();
            if (processId == null || processId.trim().isEmpty()) {
                throw new WorkflowProcessingException("Process started but no instance ID was returned");
            }

            LogUtil.info(getClass().getName(), "Process started with ID: " + processId);
            return processId;
        } catch (Exception e) {
            if (e instanceof WorkflowProcessingException) {
                throw (WorkflowProcessingException) e;
            }
            throw new WorkflowProcessingException("Failed to start workflow process: " + e.getMessage(), e);
        }
    }

    /**
     * Process the submit activity
     */
    public boolean processSubmitActivity(String processId, String processDefId,
                                         String submitActivityId, String username,
                                         Map<String, String> variables) {
        try {
            // Get the first activity (should be submitFarmerApplication)
            Collection<WorkflowActivity> activities = workflowManager.getActivityList(
                    processId, 0, 10, null, false);

            if (activities == null || activities.isEmpty()) {
                LogUtil.warn(getClass().getName(), "No activities found for process");
                return false;
            }

            LogUtil.info(getClass().getName(), "Number of activities found: " + activities.size());

            // Find the submit activity
            WorkflowActivity submitActivity = null;
            for (WorkflowActivity activity : activities) {
                if (submitActivityId.equals(activity.getActivityDefId())) {
                    submitActivity = activity;
                    break;
                }
            }

            if (submitActivity == null) {
                LogUtil.warn(getClass().getName(), "Submit activity (" + submitActivityId + ") not found");
                return false;
            }

            // Log the initial state
            String initialState = submitActivity.getState();
            LogUtil.info(getClass().getName(), "Submit activity initial state: " + initialState);

            // Explicitly start the activity to ensure it's in 'running' state
            boolean started = workflowManager.activityStart(processId, submitActivityId, false);
            LogUtil.info(getClass().getName(), "Activity start result: " + started);

            // Re-fetch the activity to get updated state
            submitActivity = workflowManager.getActivityById(submitActivity.getId());
            LogUtil.info(getClass().getName(), "Activity state after start: " + submitActivity.getState());

            // Make sure process variables are set (can affect transitions)
            workflowManager.processVariables(processId, variables);

            // Complete the activity using force complete
            String convertedProcessDefId = workflowManager.getConvertedLatestProcessDefId(processDefId);
            workflowManager.assignmentForceComplete(convertedProcessDefId, processId,
                    submitActivity.getId(), username);
            LogUtil.info(getClass().getName(), "Submit activity force completed with user: " + username);

            // Reevaluate assignments to ensure proper next steps
            workflowManager.reevaluateAssignmentsForProcess(processId);

            return true;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error processing submit activity: " + e.getMessage());
            return false;
        }
    }

    /**
     * Process the review activity
     */
    public boolean processReviewActivity(String processId, String processDefId,
                                         String reviewActivityId, String adminUsername,
                                         String reviewerUsername) {
        try {
            // Get the current user
            String originalUser = workflowUserManager.getCurrentUsername();

            try {
                // Switch to admin to reassign
                workflowUserManager.setCurrentThreadUser(adminUsername);

                // Check for the review activity
                Collection<WorkflowActivity> activities = workflowManager.getActivityList(
                        processId, 0, 10, null, false);
                LogUtil.info(getClass().getName(), "Activities after completion: " + activities.size());

                boolean foundReviewActivity = false;
                for (WorkflowActivity activity : activities) {
                    LogUtil.info(getClass().getName(), "Activity: " + activity.getId() +
                            " [" + activity.getActivityDefId() + "] in state: " + activity.getState());

                    if (reviewActivityId.equals(activity.getActivityDefId())) {
                        foundReviewActivity = true;
                        LogUtil.info(getClass().getName(), "Found review activity: " + activity.getId());

                        // Reassign the review activity to the reviewer user
                        String convertedProcessDefId = workflowManager.getConvertedLatestProcessDefId(processDefId);
                        workflowManager.assignmentReassign(convertedProcessDefId, processId,
                                activity.getId(), reviewerUsername, null);
                        LogUtil.info(getClass().getName(), "Review activity reassigned to: " + reviewerUsername);

                        // Reevaluate assignments for this activity
                        workflowManager.reevaluateAssignmentsForActivity(activity.getId());
                    }
                }

                if (!foundReviewActivity) {
                    LogUtil.warn(getClass().getName(), "Review activity (" + reviewActivityId + ") not found");
                    return false;
                }

                return true;
            } finally {
                // Restore the original user
                workflowUserManager.setCurrentThreadUser(originalUser);
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error processing review activity: " + e.getMessage());
            return false;
        }
    }
}