package global.govstack.regbb.engine.workflow;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.audit.AuditWriter;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic process-tool plugin. Drop into any Joget workflow tool step
 * to verify that data flows through correctly. Captures everything the
 * tool receives — workflow assignment fields, all process variables,
 * configured properties, current user — and writes one row to the
 * {@code reg_bb_eval_audit} table with the determinant code prefixed
 * {@code WORKFLOW_ECHO:}. Operators see the row in the existing audit
 * list (Farmers Registration → MOA Office → RegBB Evaluation Audit) so
 * verification doesn't require server-log access.
 *
 * <p>Use case: sysadmin builds a dummy workflow with this tool as the
 * only step, configures one mm_action row pointing at that workflow, and
 * triggers the action by saving an operator decision. The audit list
 * shows the WORKFLOW_ECHO row with all the workflow context inline —
 * "yes, the engine handed control to the workflow with the right
 * applicationId / variables / activity context".
 *
 * <p>This plugin owns no business logic. It's purely a probe.
 */
public class RegBbWorkflowEchoTool extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = RegBbWorkflowEchoTool.class.getName();

    @Override public String getName()        { return "RegBB Workflow Echo Tool"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "Diagnostic tool — logs workflow context to reg_bb_eval_audit. Drop into any tool step to verify the engine→workflow handoff."; }
    @Override public String getLabel()       { return "RegBB Workflow Echo Tool"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getPropertyOptions() { return ""; }

    @Override
    public Object execute(Map properties) {
        LogUtil.info(CLASS_NAME, "===== RegBbWorkflowEchoTool fired =====");

        // Workflow context — present when invoked from inside a process.
        WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");
        String processInstanceId = assignment != null ? assignment.getProcessId()        : null;
        String activityId        = assignment != null ? assignment.getActivityId()       : null;
        String processDefId      = assignment != null ? assignment.getProcessDefId()     : null;
        String activityName      = assignment != null ? assignment.getActivityName()     : null;
        String assigneeId        = assignment != null ? assignment.getAssigneeId()       : null;
        String assigneeName      = assignment != null ? assignment.getAssigneeName()     : null;
        // packageId is the first segment of processDefId ("packageId#version#processId").
        String packageId = null;
        if (processDefId != null) {
            int hash = processDefId.indexOf('#');
            if (hash > 0) packageId = processDefId.substring(0, hash);
        }

        // Form-context — present when invoked as form post-processor.
        String recordId = stringOrNull(properties.get("recordId"));
        if (recordId == null) recordId = stringOrNull(properties.get("id"));

        // Variables. Joget supplies these via "workflowVariables" or directly
        // on the properties map; capture both shapes for portability.
        Map<String, Object> snapshot = captureContextSnapshot(
                properties, assignment, recordId, processInstanceId, activityId,
                processDefId, packageId, activityName, assigneeId, assigneeName);

        // Look up applicant identifiers from the application row so the
        // audit row carries them too (for cross-list pivoting). recordId
        // == applicationId since RegBb dispatches workflows with the app
        // id as the process instance id.
        if (recordId != null && !recordId.isEmpty()) {
            try {
                FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                FormRowSet rs = dao.find("subsidyApplication2025", "subsidy_app_2025",
                        "WHERE e.id = ?", new Object[]{recordId}, null, false, null, null);
                if (rs != null && !rs.isEmpty()) {
                    FormRow appRow = rs.get(0);
                    snapshot.put("full_name",         appRow.getProperty("full_name"));
                    snapshot.put("national_id",       appRow.getProperty("national_id"));
                    snapshot.put("applied_programme", appRow.getProperty("applied_programme"));
                }
            } catch (Throwable t) {
                LogUtil.warn(CLASS_NAME, "could not lookup application row " + recordId + ": " + t.getMessage());
            }
        }

        // Build EvalContext + a "result" so we can reuse AuditWriter.write()
        // — same row shape as eligibility evaluations and operator decisions.
        EvalResult fakeResult = new EvalResult(
                EvalResult.Outcome.TRUE, null,
                /*actionTarget*/ processInstanceId,
                /*evaluator*/    "workflow_echo",
                /*errorCause*/   null);
        EvalContext ctx = EvalContext.builder()
                .applicationId(recordId)
                .data(snapshot)
                .currentUsername(assigneeName)
                .build();

        long now = System.currentTimeMillis();
        String tag = "WORKFLOW_ECHO:" + (processDefId == null ? "?" : processDefId);
        try {
            AuditWriter.write(tag, ctx, fakeResult,
                    /*ruleSource*/ "n/a — workflow echo tool",
                    now, now);
            LogUtil.info(CLASS_NAME, "RegBbWorkflowEchoTool wrote audit row tag=" + tag
                    + " applicationId=" + recordId);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "RegBbWorkflowEchoTool audit write failed");
        }

        // Also log to console for ops who want to grep.
        for (Map.Entry<String, Object> e : snapshot.entrySet()) {
            String v = e.getValue() == null ? "null" : e.getValue().toString();
            if (v.length() > 250) v = v.substring(0, 250) + "…";
            LogUtil.info(CLASS_NAME, "  echo[" + e.getKey() + "] = " + v);
        }
        LogUtil.info(CLASS_NAME, "===== RegBbWorkflowEchoTool done =====");
        return null;
    }

    /** Pull every observable field into one ordered map for the audit
     *  inputs_json snapshot. */
    @SuppressWarnings("rawtypes")
    private Map<String, Object> captureContextSnapshot(Map properties,
                                                       WorkflowAssignment assignment,
                                                       String recordId,
                                                       String processInstanceId,
                                                       String activityId,
                                                       String processDefId,
                                                       String packageId,
                                                       String activityName,
                                                       String assigneeId,
                                                       String assigneeName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recordId",          recordId);
        out.put("processInstanceId", processInstanceId);
        out.put("activityId",        activityId);
        out.put("processDefId",      processDefId);
        out.put("packageId",         packageId);
        out.put("activityName",      activityName);
        out.put("assigneeId",        assigneeId);
        out.put("assigneeName",      assigneeName);

        // Configured tool properties (everything the sysadmin set on this
        // step in process designer, minus the workflow plumbing keys).
        for (Object k : properties.keySet()) {
            if (k == null) continue;
            String key = k.toString();
            if (isPlumbingKey(key)) continue;
            Object v = properties.get(k);
            out.put("prop." + key, v);
        }

        // Workflow variables. Joget represents these in different ways —
        // try a few likely keys.
        Object wfVars = properties.get("workflowVariables");
        if (wfVars instanceof Map) {
            for (Object k : ((Map) wfVars).keySet()) {
                out.put("var." + k, ((Map) wfVars).get(k));
            }
        }
        if (assignment != null) {
            try {
                Collection<?> varList = assignment.getProcessVariableList();
                if (varList != null) {
                    int i = 0;
                    for (Object var : varList) {
                        out.put("varList." + (i++), var == null ? null : var.toString());
                    }
                }
            } catch (Throwable ignore) {}
        }

        return out;
    }

    /** Keys that are plumbing rather than user-configured tool properties. */
    private static boolean isPlumbingKey(String key) {
        return key.equals("workflowAssignment")
            || key.equals("workflowVariables")
            || key.equals("appDef")
            || key.equals("appDefinition")
            || key.equals("recordId")
            || key.equals("id")
            || key.equals("pluginManager");
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}
