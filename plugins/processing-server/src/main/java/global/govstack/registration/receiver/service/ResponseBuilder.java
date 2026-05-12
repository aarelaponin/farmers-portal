package global.govstack.registration.receiver.service;

import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.config.Constants;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;

/**
 * Service for building API responses
 */
public class ResponseBuilder {
    private final WorkflowManager workflowManager;

    public ResponseBuilder(WorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    /**
     * Build the response JSON
     *
     * @param processId ID of the workflow process
     * @param submittedFormId ID of the submitted form
     * @return JSONObject containing the response data
     */
    public JSONObject buildResponse(String processId, String submittedFormId) {
        try {
            // Get the final state of the process for the response
            WorkflowProcess process = workflowManager.getRunningProcessById(processId);
            Collection<WorkflowActivity> currentActivities = workflowManager.getActivityList(
                    processId, 0, 10, null, false);
            JSONArray activitiesArray = new JSONArray();

            LogUtil.info(getClass().getName(), "Final activities count: " + currentActivities.size());
            for (WorkflowActivity activity : currentActivities) {
                JSONObject activityInfo = buildActivityInfo(activity);
                activitiesArray.put(activityInfo);
            }

            // Create the response
            JSONObject response = new JSONObject();
            response.put(Constants.APPLICATION_ID, submittedFormId);
            response.put(Constants.PROCESS_ID, processId);
            response.put(Constants.STATUS, process != null ? process.getState() : "unknown");
            response.put(Constants.ACTIVITIES, activitiesArray);

            return response;
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(), "Error building response: " + e.getMessage());
            return buildFallbackResponse(submittedFormId, processId, e.getMessage());
        }
    }

    /**
     * Builds detailed activity information
     */
    private JSONObject buildActivityInfo(WorkflowActivity activity) {
        JSONObject activityInfo = new JSONObject();
        activityInfo.put("id", activity.getId());
        activityInfo.put("name", activity.getName());
        activityInfo.put("activityDefId", activity.getActivityDefId());
        activityInfo.put("state", activity.getState());

        // Get assignment information
        try {
            WorkflowActivity runningInfo = workflowManager.getRunningActivityInfo(activity.getId());
            if (runningInfo != null) {
                String[] assignmentUsers = runningInfo.getAssignmentUsers();
                JSONArray assignees = new JSONArray();
                if (assignmentUsers != null) {
                    for (String user : assignmentUsers) {
                        assignees.put(user);
                    }
                }
                activityInfo.put("assignees", assignees);
            }
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(), "Could not get assignment info: " + e.getMessage());
        }

        return activityInfo;
    }

    /**
     * Build fallback response when errors occur
     */
    private JSONObject buildFallbackResponse(String submittedFormId, String processId, String errorMessage) {
        JSONObject fallbackResponse = new JSONObject();
        fallbackResponse.put(Constants.APPLICATION_ID, submittedFormId);
        fallbackResponse.put(Constants.PROCESS_ID, processId);
        fallbackResponse.put(Constants.STATUS, "unknown");
        fallbackResponse.put(Constants.ERROR, "Error fetching complete process details: " + errorMessage);
        return fallbackResponse;
    }
}