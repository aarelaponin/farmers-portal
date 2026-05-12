package global.govstack.regbb.engine.api;

import global.govstack.regbb.engine.evaluator.RoutingEvaluator;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * REST endpoints for the RegBB engine. Lives in the same bundle as
 * {@link RoutingEvaluator} so cross-bundle reflection is unnecessary —
 * the previous implementation in form-creator-api hit OSGi classloader
 * identity issues that vanish here. Same canonical pattern as
 * {@code apibuilder_sample_plugin/SampleAPI.java}.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /eval}    — §8: evaluate one determinant against the
 *       supplied applicant data. No persistence.</li>
 *   <li>{@code POST /submit}  — §6.5: persist an application row + run
 *       eligibility on its applied registration's applicabilityDeterminantId
 *       + write outcome JSON to the {@code eligibility_outcome} column.</li>
 * </ul>
 *
 * <p>Both endpoints flow through {@link RoutingEvaluator} so they get the
 * full audit trail (one row per evaluate() in {@code reg_bb_eval_audit})
 * and L2 cache treatment for free.
 */
public class RegBbEvalApi extends ApiPluginAbstract {

    private static final String CLASS_NAME = RegBbEvalApi.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Override public String getName()        { return "RegBB Eval API"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT"; }
    @Override public String getLabel()       { return "RegBB Eval API"; }
    @Override public String getDescription() { return "RegBB engine REST endpoints: /eval (§8), /submit (§6.5)"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getPropertyOptions() { return ""; }
    @Override public String getIcon()        { return "<i class=\"fas fa-bolt\"></i>"; }
    @Override public String getTag()         { return "regbb"; }

    /**
     * §8 — evaluate one determinant against caller-supplied data. No persistence.
     *
     * <pre>{
     *   "determinantCode": "DET_LOWLANDS",
     *   "applicationId":   "(optional, used for audit + cache key)",
     *   "applicantData":   { "agro_zone": "lowlands", "area_hectares": "3.5" }
     * }</pre>
     */
    @Operation(
        path = "/eval",
        type = Operation.MethodType.POST,
        summary = "Evaluate one determinant; return outcome (TRUE/FALSE/NULL/ERROR)",
        description = "Per spec §8. No persistence — pure evaluation. Audited + cached."
    )
    @Responses({
        @Response(responseCode = 200, description = "Evaluation completed"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during evaluation")
    })
    public ApiResponse eval(
        @Param(value = "body", required = false) String requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return badRequest("Request body is empty");
            }
            JSONObject req = new JSONObject(requestBody);
            String determinantCode = req.optString("determinantCode", "");
            String applicationId   = req.optString("applicationId", null);
            JSONObject applicantData = req.optJSONObject("applicantData");
            if (determinantCode.isEmpty()) {
                return badRequest("Missing 'determinantCode'");
            }

            Map<String, Object> data = jsonToMap(applicantData);
            EvalContext ctx = EvalContext.builder()
                    .applicationId(applicationId)
                    .data(data)
                    .build();

            RoutingEvaluator evaluator = new RoutingEvaluator();
            EvalResult result = evaluator.evaluate(determinantCode, ctx);

            JSONObject body = resultToJson(result);
            body.put("status", "success");
            body.put("determinantCode", determinantCode);
            return new ApiResponse(200, body.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "RegBbEvalApi.eval failed");
            return serverError(e);
        }
    }

    /**
     * §6.5 — persist an application row, evaluate eligibility on the applied
     * registration's {@code applicabilityDeterminantId}, write outcome JSON
     * to {@code eligibility_outcome}, return outcome.
     *
     * <pre>{
     *   "formId":          "subsidyApplication2025",
     *   "tableName":       "subsidy_app_2025",
     *   "applicationData": { "national_id": "...", "applied_programme": "PRG_2025_001", ... }
     * }</pre>
     */
    @Operation(
        path = "/submit",
        type = Operation.MethodType.POST,
        summary = "Submit an application; persist; evaluate; persist outcome",
        description = "Per spec §6.5. Returns the application id + outcome JSON."
    )
    @Responses({
        @Response(responseCode = 200, description = "Submission persisted; outcome computed"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during submission")
    })
    public ApiResponse submit(
        @Param(value = "body", required = false) String requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return badRequest("Request body is empty");
            }
            JSONObject req = new JSONObject(requestBody);
            String formDefId = req.optString("formId", "subsidyApplication2025");
            String tableName = req.optString("tableName", "subsidy_app_2025");
            JSONObject applicationData = req.optJSONObject("applicationData");
            if (applicationData == null) {
                return badRequest("Missing 'applicationData' object");
            }

            // Persist the application row.
            FormRow row = new FormRow();
            String applicationId = UuidGenerator.getInstance().getUuid();
            row.setId(applicationId);
            Date now = new Date();
            row.setDateCreated(now);
            row.setDateModified(now);
            for (Iterator<String> it = applicationData.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = applicationData.get(k);
                if (v == null || JSONObject.NULL.equals(v)) continue;
                row.setProperty(k, v.toString());
            }
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            dao.saveOrUpdate(formDefId, tableName, rs);

            // Evaluate the applicability determinant for the applied programme.
            String appliedProgramme = applicationData.optString("applied_programme", "");
            JSONObject outcome = new JSONObject();
            outcome.put("appliedProgramme", appliedProgramme);
            synchronized (ISO_UTC) {
                outcome.put("evaluatedAt", ISO_UTC.format(new Date()));
            }

            if (appliedProgramme.isEmpty()) {
                outcome.put("disposition", "indeterminate");
                outcome.put("reason", "no_programme_selected");
            } else {
                outcome = evaluateApplicability(appliedProgramme, applicationId, applicationData, outcome);
            }

            // Persist the outcome JSON + initial status (Phase 2-a state machine).
            // Per ADR-027, the disposition→status mapping is rule-driven.
            FormRow patch = new FormRow();
            patch.setId(applicationId);
            patch.setProperty("eligibility_outcome", outcome.toString());
            String status = statusForDisposition(
                    outcome.optString("disposition", ""), "SUBSIDY_2025", appliedProgramme);
            if (status != null) patch.setProperty("status", status);
            FormRowSet pset = new FormRowSet();
            pset.add(patch);
            dao.saveOrUpdate(formDefId, tableName, pset);

            JSONObject resp = new JSONObject();
            resp.put("status", "success");
            resp.put("applicationId", applicationId);
            resp.put("outcome", outcome);
            return new ApiResponse(200, resp.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "RegBbEvalApi.submit failed");
            return serverError(e);
        }
    }

    /**
     * L2-2-bis — live in-flight catalogue evaluation. The kernel's server-
     * side {@code renderCatalogue} can't see MultiPagedForm's intermediate
     * tab values in {@code ?_mode=add} (framework caches them in its own
     * state, not in the load binder). This endpoint accepts the wizard's
     * currently-typed form payload from JS, walks every
     * {@code mm_registration} for the service, evaluates each programme's
     * {@code applicabilityDeterminantId} against that payload, and returns
     * outcomes JSON. Client-side JS in the catalogue HTML calls this on
     * page load and replaces card states in place.
     *
     * <pre>{
     *   "serviceCode":   "SUBSIDY_2025",
     *   "applicantData": { "district": "maseru", "agro_zone": "foothills",
     *                      "area_hectares": "10", ... }
     * }</pre>
     *
     * Response:
     * <pre>{
     *   "status": "success",
     *   "results": [
     *     { "code": "PRG_2025_001", "outcome": "FALSE",
     *       "failMessage": "This programme is restricted to Lowlands applicants." },
     *     { "code": "PRG_2025_003", "outcome": "FALSE",
     *       "failMessage": "The E-Voucher Pilot is restricted to smallholders (≤ 5 ha)." },
     *     ...
     *   ]
     * }</pre>
     */
    @Operation(
        path = "/catalogue/eval",
        type = Operation.MethodType.POST,
        summary = "Live in-flight evaluation of every programme in a service",
        description = "L2-2-bis. JS in the catalogue tab posts the wizard's currently-typed form payload; kernel returns per-programme outcomes for client-side card refresh."
    )
    @Responses({
        @Response(responseCode = 200, description = "Outcomes returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse catalogueEval(
        @Param(value = "body", required = false) String requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return badRequest("Request body is empty");
            }
            JSONObject req = new JSONObject(requestBody);
            String serviceCode = req.optString("serviceCode", "");
            JSONObject applicantDataJson = req.optJSONObject("applicantData");
            if (serviceCode.isEmpty()) {
                return badRequest("Missing 'serviceCode'");
            }

            Map<String, Object> applicantData = jsonToMap(applicantDataJson);
            EvalContext ctx = EvalContext.builder().data(applicantData).build();

            global.govstack.regbb.engine.dao.MetaModelDao dao =
                    new global.govstack.regbb.engine.dao.MetaModelDao();
            java.util.List<org.joget.apps.form.model.FormRow> regs =
                    dao.listRegistrationsForService(serviceCode);

            org.json.JSONArray results = new org.json.JSONArray();
            RoutingEvaluator evaluator = new RoutingEvaluator();
            for (org.joget.apps.form.model.FormRow reg : regs) {
                String code    = nullSafe(reg.getProperty("code"));
                String detCode = nullSafe(reg.getProperty("applicabilityDeterminantId"));
                JSONObject row = new JSONObject();
                row.put("code", code);
                if (detCode.isEmpty()) {
                    row.put("outcome", "OPEN");
                } else {
                    EvalResult r;
                    try {
                        r = evaluator.evaluate(detCode, ctx);
                    } catch (Throwable t) {
                        r = EvalResult.error("eval_threw:" + t.getClass().getSimpleName(), "");
                    }
                    String outcome = (r != null && r.outcome != null) ? r.outcome.name() : "ERROR";
                    row.put("outcome", outcome);
                    if ("FALSE".equals(outcome)) {
                        org.joget.apps.form.model.FormRow det = dao.findDeterminantByCode(detCode);
                        if (det != null) {
                            row.put("failMessage", nullSafe(det.getProperty("failMessage")));
                        }
                    } else if ("ERROR".equals(outcome) && r != null && r.errorCause != null) {
                        row.put("errorCause", r.errorCause);
                    }
                }
                results.put(row);
            }

            JSONObject body = new JSONObject();
            body.put("status",  "success");
            body.put("results", results);
            return new ApiResponse(200, body.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "RegBbEvalApi.catalogueEval failed");
            return serverError(e);
        }
    }

    private static String nullSafe(Object o) { return o == null ? "" : o.toString(); }

    /**
     * L2-3 — bot_pull mm_action dispatcher. Citizen types a trigger field
     * (typically {@code national_id}); JS posts the trigger value here;
     * kernel walks the action's {@code configJson.fieldMappings} and
     * resolves each via the L2-1 capability registry (no determinant
     * evaluation — direct value reads).
     *
     * <pre>{
     *   "actionCode": "AUTOFILL_FROM_FARMER_REGISTRY",
     *   "trigger":    { "national_id": "1995022700567" }
     * }</pre>
     *
     * Action row shape (mm_action):
     * <pre>
     * kind: bot_pull
     * triggerJson: { "fieldId": "national_id" }
     * configJson:  {
     *   "fieldMappings": [
     *     { "target": "full_name",     "sourceCapability": "farmer", "sourceField": "full_name" },
     *     { "target": "date_of_birth", "sourceCapability": "farmer", "sourceField": "date_of_birth" },
     *     { "target": "contact_phone", "sourceCapability": "farmer", "sourceField": "contact_phone" }
     *   ]
     * }
     * </pre>
     *
     * Response:
     * <pre>{
     *   "status": "success",
     *   "actionCode": "AUTOFILL_FROM_FARMER_REGISTRY",
     *   "resolved":   { "full_name": "Tšepiso Khabo",
     *                   "date_of_birth": "1995-02-27",
     *                   "contact_phone": "+266 5723 4567" }
     * }</pre>
     *
     * If the trigger value resolves to no row in the source capability,
     * the response carries an empty {@code resolved} object — JS then
     * leaves the UI unchanged. No error.
     */
    @Operation(
        path = "/bot_pull/eval",
        type = Operation.MethodType.POST,
        summary = "Resolve a bot_pull mm_action against a trigger value",
        description = "L2-3 / D11. Walks the action's fieldMappings via the L2-1 capability registry; returns target→value JSON for client-side form auto-fill."
    )
    @Responses({
        @Response(responseCode = 200, description = "Resolved values returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse botPullEval(
        @Param(value = "body", required = false) String requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return badRequest("Request body is empty");
            }
            JSONObject req = new JSONObject(requestBody);
            String actionCode = req.optString("actionCode", "");
            JSONObject triggerData = req.optJSONObject("trigger");
            if (actionCode.isEmpty()) {
                return badRequest("Missing 'actionCode'");
            }

            // Look up mm_action row.
            global.govstack.regbb.engine.dao.MetaModelDao dao =
                    new global.govstack.regbb.engine.dao.MetaModelDao();
            FormDataDao fdd = (FormDataDao) AppUtil.getApplicationContext()
                            .getBean("formDataDao");
            FormRowSet rs = fdd.find("mm_action", "mm_action",
                    "WHERE e.customProperties.code = ?",
                    new Object[]{ actionCode }, null, false, null, null);
            if (rs == null || rs.isEmpty()) {
                return badRequest("Unknown action code: " + actionCode);
            }
            FormRow action = rs.get(0);
            String kind = nullSafe(action.getProperty("kind"));
            if (!"bot_pull".equalsIgnoreCase(kind)) {
                return badRequest("Action " + actionCode + " is kind=" + kind + ", not bot_pull");
            }

            // Parse triggerJson { fieldId } and configJson { fieldMappings }.
            String triggerJsonRaw = nullSafe(action.getProperty("triggerJson"));
            String configJsonRaw  = nullSafe(action.getProperty("configJson"));
            String triggerFieldId = "";
            if (!triggerJsonRaw.isEmpty()) {
                try {
                    triggerFieldId = new JSONObject(triggerJsonRaw).optString("fieldId", "");
                } catch (Exception ignore) {}
            }
            org.json.JSONArray mappings = null;
            if (!configJsonRaw.isEmpty()) {
                try {
                    mappings = new JSONObject(configJsonRaw).optJSONArray("fieldMappings");
                } catch (Exception ignore) {}
            }
            if (mappings == null || mappings.length() == 0) {
                JSONObject empty = new JSONObject();
                empty.put("status",     "success");
                empty.put("actionCode", actionCode);
                empty.put("resolved",   new JSONObject());
                return new ApiResponse(200, empty.toString());
            }

            // The trigger value (e.g. national_id) — adapter.resolve takes it
            // as the second arg ("nationalId" in the interface signature; in
            // practice it's whatever the capability uses as its lookup key).
            String triggerValue = "";
            if (triggerData != null && !triggerFieldId.isEmpty()) {
                triggerValue = nullSafe(triggerData.opt(triggerFieldId));
            }

            // Resolve every mapping via the capability registry.
            global.govstack.regbb.engine.evaluator.SqlPathEvaluator sqlEval =
                    new global.govstack.regbb.engine.evaluator.SqlPathEvaluator();
            EvalContext ctx = EvalContext.builder()
                    .data(jsonToMap(triggerData))
                    .build();
            JSONObject resolved = new JSONObject();
            for (int i = 0; i < mappings.length(); i++) {
                JSONObject m = mappings.optJSONObject(i);
                if (m == null) continue;
                String target           = m.optString("target", "");
                String sourceCapability = m.optString("sourceCapability", "");
                String sourceField      = m.optString("sourceField", "");
                if (target.isEmpty() || sourceCapability.isEmpty() || sourceField.isEmpty()) continue;
                global.govstack.regbb.engine.api.CapabilityAdapter adapter =
                        sqlEval.getCapability(sourceCapability);
                if (adapter == null) {
                    LogUtil.warn(CLASS_NAME,
                        "bot_pull " + actionCode + ": unknown capability '"
                        + sourceCapability + "' (mapping target=" + target + " skipped)");
                    continue;
                }
                try {
                    String value = adapter.resolve(sourceField, triggerValue, ctx);
                    if (value != null && !value.isEmpty()) {
                        resolved.put(target, value);
                    }
                } catch (Throwable t) {
                    LogUtil.warn(CLASS_NAME,
                        "bot_pull " + actionCode + ": adapter '" + sourceCapability
                        + "' failed for field '" + sourceField + "': " + t.getMessage());
                }
            }

            JSONObject body = new JSONObject();
            body.put("status",     "success");
            body.put("actionCode", actionCode);
            body.put("resolved",   resolved);
            return new ApiResponse(200, body.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "RegBbEvalApi.botPullEval failed");
            return serverError(e);
        }
    }

    @Operation(
        path = "/mdm/list",
        type = Operation.MethodType.GET,
        summary = "List all rows of a master-data form",
        description = "Returns all rows of a master-data form (formId must start with 'md'). "
                    + "For external integrators (e.g. pilot-data migration teams) who need "
                    + "to validate their data against the portal's MDM. Read-only; whitelisted "
                    + "to master-data forms only. Strips the 'c_' column prefix in output for "
                    + "cleanliness. Returns JSON: {\"formId\":\"...\",\"rowCount\":N,"
                    + "\"rows\":[{\"id\":\"...\",\"code\":\"...\",\"name\":\"...\"}, ...]}."
    )
    @Responses({
        @Response(responseCode = 200, description = "Rows returned"),
        @Response(responseCode = 400, description = "Invalid formId (must start with 'md', alphanumeric)"),
        @Response(responseCode = 404, description = "Master-data table does not exist"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse mdmList(
        @Param(value = "formId", required = true) String formId,
        @Param(value = "limit",  required = false) String limitStr
    ) {
        if (formId == null || formId.isEmpty()) {
            return badRequest("formId is required");
        }
        // Whitelist: only master-data forms. Allow alphanumeric + underscore.
        if (!formId.matches("^md[a-zA-Z0-9_]+$")) {
            return badRequest("formId must start with 'md' and contain only alphanumeric/underscore");
        }
        int limit = 10000;  // generous default; master-data tables are small
        if (limitStr != null && !limitStr.isEmpty()) {
            try { limit = Math.min(Integer.parseInt(limitStr), 50000); }
            catch (NumberFormatException nfe) {
                return badRequest("limit must be numeric");
            }
        }

        // Postgres folds unquoted identifiers to lowercase; our app_fd_<formId>
        // tables are stored lowercase regardless of camelCase formId.
        String tableName = ("app_fd_" + formId).toLowerCase();

        javax.sql.DataSource ds = (javax.sql.DataSource)
                AppUtil.getApplicationContext().getBean("setupDataSource");

        try (java.sql.Connection c = ds.getConnection()) {
            // Verify table exists. information_schema is safe to query.
            try (java.sql.PreparedStatement p = c.prepareStatement(
                    "SELECT count(*) FROM information_schema.tables "
                  + "WHERE table_name = ? AND table_schema = current_schema()")) {
                p.setString(1, tableName);
                try (java.sql.ResultSet rs = p.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        JSONObject err = new JSONObject();
                        err.put("status", "error");
                        err.put("message", "Master-data table not found: " + tableName
                              + " (formId=" + formId + ")");
                        return new ApiResponse(404, err.toString());
                    }
                }
            }

            // Discover columns (everything c_*).
            java.util.List<String> columns = new java.util.ArrayList<>();
            try (java.sql.PreparedStatement p = c.prepareStatement(
                    "SELECT column_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND table_schema = current_schema() "
                  + "  AND (column_name = 'id' OR column_name LIKE 'c\\_%' ESCAPE '\\') "
                  + "ORDER BY ordinal_position")) {
                p.setString(1, tableName);
                try (java.sql.ResultSet rs = p.executeQuery()) {
                    while (rs.next()) columns.add(rs.getString(1));
                }
            }
            if (columns.isEmpty()) {
                JSONObject err = new JSONObject();
                err.put("status", "error");
                err.put("message", "Master-data table has no readable columns: " + tableName);
                return new ApiResponse(500, err.toString());
            }

            // Read rows. Use SELECT * with explicit limit, ORDER BY c_code if present.
            String selectCols = String.join(", ", columns);
            boolean hasCode = columns.contains("c_code");
            String sql = "SELECT " + selectCols + " FROM " + tableName
                       + (hasCode ? " ORDER BY c_code" : " ORDER BY id")
                       + " LIMIT " + limit;
            org.json.JSONArray rows = new org.json.JSONArray();
            try (java.sql.PreparedStatement p = c.prepareStatement(sql);
                 java.sql.ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < columns.size(); i++) {
                        String col = columns.get(i);
                        String key = col.startsWith("c_") ? col.substring(2) : col;
                        String val = rs.getString(i + 1);
                        row.put(key, val == null ? "" : val);
                    }
                    rows.put(row);
                }
            }

            JSONObject body = new JSONObject();
            body.put("status",   "ok");
            body.put("formId",   formId);
            body.put("tableName", tableName);
            body.put("rowCount", rows.length());
            body.put("rows",     rows);
            return new ApiResponse(200, body.toString());

        } catch (java.sql.SQLException e) {
            LogUtil.warn(CLASS_NAME, "mdmList SQL error: " + e.getSQLState() + ":" + e.getMessage());
            JSONObject err = new JSONObject();
            err.put("status",    "error");
            err.put("errorType", "SQLException");
            err.put("sqlState",  e.getSQLState());
            err.put("message",   e.getMessage());
            return new ApiResponse(500, err.toString());
        } catch (Throwable t) {
            return serverError(t);
        }
    }

    // ---------------------------------------------------------------------
    //  Internal helpers
    // ---------------------------------------------------------------------

    private JSONObject evaluateApplicability(String registrationCode, String applicationId,
                                             JSONObject applicationData, JSONObject outcome) throws Exception {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        FormRowSet regs = dao.find("mm_registration", "mm_registration",
                "WHERE e.customProperties.code = ?", new Object[]{registrationCode}, null, false, null, null);
        if (regs == null || regs.isEmpty()) {
            outcome.put("disposition", "indeterminate");
            outcome.put("reason", "registration_not_found:" + registrationCode);
            return outcome;
        }
        FormRow reg = regs.get(0);
        String applicabilityCode = reg.getProperty("applicabilityDeterminantId");
        if (applicabilityCode == null || applicabilityCode.isEmpty()) {
            outcome.put("disposition", "eligibility_passed");
            outcome.put("reason", "no_applicability_rule_configured");
            return outcome;
        }

        Map<String, Object> data = jsonToMap(applicationData);
        EvalContext ctx = EvalContext.builder()
                .applicationId(applicationId)
                .data(data)
                .build();

        RoutingEvaluator evaluator = new RoutingEvaluator();
        EvalResult result = evaluator.evaluate(applicabilityCode, ctx);

        outcome.put("ruleCode", applicabilityCode);
        outcome.put("evaluator", result.evaluator == null ? "" : result.evaluator);
        switch (result.outcome) {
            case TRUE:
                outcome.put("disposition", "eligibility_passed");
                break;
            case FALSE:
                outcome.put("disposition", "eligibility_failed_mandatory");
                break;
            case NULL:
                outcome.put("disposition", "indeterminate");
                outcome.put("failMessage", "rule_returned_null:applicant_data_incomplete");
                break;
            case ERROR:
            default:
                outcome.put("disposition", "indeterminate");
                outcome.put("failMessage", "evaluator_error:" + result.errorCause);
                break;
        }
        return outcome;
    }

    /** Map an eligibility disposition string to the initial application
     *  status (Phase 2-a state machine). Per ADR-027 the mapping is
     *  rule-driven via {@link global.govstack.regbb.engine.binder.StatusPolicyResolver};
     *  hardcoded fallback below preserves backwards compatibility when seed
     *  rules are missing.
     */
    private static String statusForDisposition(String disposition,
                                               String serviceCode,
                                               String registrationCode) {
        if (disposition == null || disposition.isEmpty()) return null;
        // Try rule-driven resolution first (ADR-027)
        String fromRules = global.govstack.regbb.engine.binder.StatusPolicyResolver
                .resolveInitialStatus(disposition, serviceCode, registrationCode);
        if (fromRules != null) return fromRules;
        // Hardcoded fallback (defensive)
        switch (disposition) {
            case "eligibility_passed":           return "auto_approved";
            case "eligibility_failed_mandatory": return "auto_rejected";
            case "eligibility_pending_review":   return "pending_operator_review";
            case "indeterminate":                return "pending_data_clarification";
            default: return null;
        }
    }

    private static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> out = new HashMap<>();
        if (json == null) return out;
        for (Iterator<String> it = json.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = json.opt(k);
            if (v == null || JSONObject.NULL.equals(v)) continue;
            out.put(k, v.toString());
        }
        return out;
    }

    private static JSONObject resultToJson(EvalResult r) {
        JSONObject j = new JSONObject();
        if (r == null) { j.put("outcome", "UNKNOWN"); return j; }
        j.put("outcome",   r.outcome == null ? "UNKNOWN" : r.outcome.name());
        j.put("evaluator", r.evaluator == null ? "" : r.evaluator);
        if (r.numericValue != null) j.put("numericValue", r.numericValue.doubleValue());
        if (r.actionTarget != null) j.put("actionTarget", r.actionTarget);
        if (r.errorCause   != null) j.put("errorCause",   r.errorCause);
        return j;
    }

    private static ApiResponse badRequest(String message) {
        JSONObject j = new JSONObject();
        j.put("status",  "error");
        j.put("message", message);
        return new ApiResponse(400, j.toString());
    }

    /** Honest error response — exposes the actual exception type + message
     *  rather than the generic "An unexpected error occurred during form creation"
     *  that form-creator-api's handler emitted. Slice 1 ergonomics; production
     *  may want to scrub stack traces. */
    private static ApiResponse serverError(Throwable t) {
        JSONObject j = new JSONObject();
        j.put("status",   "error");
        j.put("errorType", t.getClass().getSimpleName());
        j.put("message",  t.getMessage() == null ? t.toString() : t.getMessage());
        return new ApiResponse(500, j.toString());
    }
}
