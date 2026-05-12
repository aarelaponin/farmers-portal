package global.govstack.regbb.engine.workflow;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.audit.AuditWriter;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 2-b dispatcher. Given an event (kind, serviceId, registrationId,
 * applicationId, data), looks up matching {@code mm_action} rows and fires
 * the configured Joget workflow processes via {@link WorkflowManager}.
 *
 * <p><b>mm_action.triggerJson convention</b> for {@code kind=status_change}:
 * <pre>{
 *   "onStatus":   "approved",
 *   "workflowId": "subsidy_2025_approval",
 *   "packageId":  "farmersPortal"          // optional; defaults to current app
 * }</pre>
 *
 * <p>The dispatcher does NOT design or generate workflows. Sysadmins build
 * processes in Joget Process Builder; mm_action declares which workflow
 * runs for which event. This matches Joget's "convention over invention"
 * pattern and keeps RegBB out of the orchestration business.
 *
 * <p>Failures are swallowed and logged — never block the calling binder
 * (per ADR-007 never-null discipline). A WORKFLOW_DISPATCH audit row is
 * always written, with success/failure recorded in the outcome field.
 */
public final class WorkflowDispatcher {

    private static final String CLASS_NAME = WorkflowDispatcher.class.getName();
    private static final String FORM_MM_ACTION = "mm_action";

    private WorkflowDispatcher() { /* static helpers only */ }

    /**
     * Dispatch all mm_action rows matching the event.
     *
     * @param serviceId       e.g. "SUBSIDY_2025"
     * @param registrationId  e.g. "PRG_2025_001" (or null for service-wide)
     * @param eventKind       e.g. "status_change", "form_save"
     * @param applicationId   the application row id, used as workflow correlation key
     * @param eventData       event-specific data (e.g. {"onStatus":"approved"} for status_change)
     * @param applicationData full applicant row data (passed as workflow variables)
     * @param currentUsername Joget user triggering the dispatch (for audit attribution)
     * @return number of workflows successfully started
     */
    public static int dispatch(String serviceId,
                               String registrationId,
                               String eventKind,
                               String applicationId,
                               Map<String, Object> eventData,
                               Map<String, Object> applicationData,
                               String currentUsername) {
        if (serviceId == null || serviceId.isEmpty()) return 0;
        if (eventKind == null || eventKind.isEmpty()) return 0;

        Collection<FormRow> matches = findMatchingActions(serviceId, registrationId, eventKind);
        if (matches.isEmpty()) {
            LogUtil.debug(CLASS_NAME, "No mm_action rows match (service=" + serviceId
                    + ", registration=" + registrationId + ", kind=" + eventKind + ")");
            return 0;
        }

        int started = 0;
        for (FormRow action : matches) {
            String code         = prop(action, "code");
            String triggerJsonS = prop(action, "triggerJson");
            JSONObject trigger;
            try {
                trigger = new JSONObject(triggerJsonS == null || triggerJsonS.isEmpty() ? "{}" : triggerJsonS);
            } catch (Throwable t) {
                writeDispatchAudit(code, applicationId, eventData, applicationData, currentUsername,
                        EvalResult.Outcome.ERROR, "trigger_json_parse_error:" + t.getMessage());
                continue;
            }

            // Filter by event-specific predicates declared in triggerJson.
            // Today we honour onStatus for status_change events; extensible.
            if ("status_change".equals(eventKind)) {
                String onStatus     = trigger.optString("onStatus", "");
                String currentStatus = String.valueOf(eventData == null ? "" : eventData.getOrDefault("onStatus", ""));
                if (!onStatus.isEmpty() && !onStatus.equalsIgnoreCase(currentStatus)) {
                    continue;  // this action is for a different status transition
                }
            }

            String workflowId = trigger.optString("workflowId", "");
            String packageId  = trigger.optString("packageId", null);  // optional
            if (workflowId.isEmpty()) {
                writeDispatchAudit(code, applicationId, eventData, applicationData, currentUsername,
                        EvalResult.Outcome.ERROR, "missing_workflow_id");
                continue;
            }

            // Build workflow variables from applicationData (Joget expects String values).
            Map<String, String> wfVars = new HashMap<>();
            if (applicationData != null) {
                for (Map.Entry<String, Object> e : applicationData.entrySet()) {
                    if (e.getValue() == null) continue;
                    wfVars.put(e.getKey(), e.getValue().toString());
                }
            }
            // Convenience variables — these are what most workflow tools expect.
            if (applicationId != null) wfVars.put("applicationId", applicationId);
            if (serviceId != null)     wfVars.put("serviceId",     serviceId);
            if (registrationId != null) wfVars.put("registrationId", registrationId);
            if (eventData != null) {
                for (Map.Entry<String, Object> e : eventData.entrySet()) {
                    Object v = e.getValue();
                    if (v != null) wfVars.put("event_" + e.getKey(), v.toString());
                }
            }

            try {
                WorkflowManager wfm = (WorkflowManager) AppUtil.getApplicationContext()
                        .getBean("workflowManager");
                // getConvertedLatestProcessDefId() resolves a short or partial
                // process def id to its full "packageId#version#processId" form
                // for the latest published version. mm_action.triggerJson.workflowId
                // can be either the short id or the full id; this normalises.
                String resolvedDefId = wfm.getConvertedLatestProcessDefId(workflowId);
                if (resolvedDefId == null || resolvedDefId.isEmpty()) resolvedDefId = workflowId;
                // 6-arg signature: (processDefId, processId, variables, startUsername, parentProcessId, startManually)
                // processId = applicationId so the workflow correlates to the application row.
                WorkflowProcessResult result = wfm.processStart(
                        resolvedDefId, applicationId, wfVars,
                        currentUsername == null ? "admin" : currentUsername,
                        null, false);
                String resultProcessId = (result != null && result.getProcess() != null)
                        ? result.getProcess().getInstanceId() : null;
                writeDispatchAudit(code, applicationId, eventData, applicationData, currentUsername,
                        EvalResult.Outcome.TRUE,
                        "workflow_started:" + resolvedDefId + (resultProcessId == null ? "" : " pid=" + resultProcessId));
                started++;
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "WorkflowDispatcher.processStart failed for action " + code);
                writeDispatchAudit(code, applicationId, eventData, applicationData, currentUsername,
                        EvalResult.Outcome.ERROR,
                        "workflow_start_error:" + t.getClass().getSimpleName() + ":" + safeMsg(t));
            }
        }
        return started;
    }

    @SuppressWarnings("unchecked")
    private static Collection<FormRow> findMatchingActions(String serviceId,
                                                           String registrationId,
                                                           String eventKind) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        // The mm_action.kind column maps to our eventKind; serviceId + (optional)
        // registrationId scope it. We accept rows where registrationId is null
        // (service-wide actions) or matches.
        FormRowSet rows = dao.find(FORM_MM_ACTION, FORM_MM_ACTION,
                "WHERE e.customProperties.serviceId = ? AND e.customProperties.kind = ?",
                new Object[]{serviceId, eventKind}, null, false, null, null);
        if (rows == null || rows.isEmpty()) return java.util.Collections.emptyList();
        if (registrationId == null || registrationId.isEmpty()) return rows;
        // Filter by registrationId match-or-empty in-app rather than via HQL
        // (the OR-with-empty pattern doesn't translate cleanly to Joget's HQL).
        java.util.List<FormRow> out = new java.util.ArrayList<>(rows.size());
        for (FormRow r : rows) {
            String regId = prop(r, "registrationId");
            if (regId == null || regId.isEmpty() || regId.equals(registrationId)) {
                out.add(r);
            }
        }
        return out;
    }

    private static void writeDispatchAudit(String actionCode,
                                           String applicationId,
                                           Map<String, Object> eventData,
                                           Map<String, Object> applicationData,
                                           String currentUsername,
                                           EvalResult.Outcome outcome,
                                           String errorCauseOrSuccessNote) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("actionCode", actionCode);
            if (eventData != null) {
                for (Map.Entry<String, Object> e : eventData.entrySet()) {
                    snap.put("event." + e.getKey(), e.getValue());
                }
            }
            // Don't dump full applicationData — it can be large and is
            // already on the application row. Just key fields.
            if (applicationData != null) {
                for (String k : new String[]{"national_id", "applied_programme", "agro_zone", "district"}) {
                    Object v = applicationData.get(k);
                    if (v != null) snap.put("appl." + k, v);
                }
            }

            EvalResult r = new EvalResult(outcome, null, null, "dispatcher", errorCauseOrSuccessNote);
            EvalContext ctx = EvalContext.builder()
                    .applicationId(applicationId)
                    .data(snap)
                    .currentUsername(currentUsername)
                    .build();
            long now = System.currentTimeMillis();
            AuditWriter.write("WORKFLOW_DISPATCH:" + (actionCode == null ? "?" : actionCode),
                    ctx, r, "n/a — workflow dispatch", now, now);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "WorkflowDispatcher audit write failed: " + t.getMessage());
        }
    }

    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null) return "";
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
