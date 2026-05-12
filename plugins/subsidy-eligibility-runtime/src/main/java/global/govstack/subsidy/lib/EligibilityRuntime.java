package global.govstack.subsidy.lib;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EligibilityRuntime — Phase 1 D1.G
 *
 * Thin runtime over joget-rules-api. For one application:
 *   1. Loads the published ruleset via JRE REST API (loadRuleset)
 *   2. Compiles it via JRE REST API (compile) — gets back fullEligibilityQuery + scoringQuery
 *   3. Wraps each query with WHERE id = ? to filter to the applicant's farmer code
 *   4. Persists per-rule pass/fail to spEligRuleResult, overall to spEligResult
 *   5. Sets workflow variable eligibilityDecision = PASS|FAIL|ERROR
 *
 * Requires:
 *   - joget-rules-api plugin deployed
 *   - jreFieldScope FARMER_APPLICATION + jreFieldDefinition rows seeded (D1.D)
 *   - At least one published ruleset bound to the target programme via
 *     contextType=PROGRAM, contextCode=programCode
 *
 * Spec: _form-specs/_phase1/D1.G_EligibilityRuntime.plugin.spec.yaml
 */
public class EligibilityRuntime extends DefaultApplicationPlugin {

    private static final String PLUGIN_NAME = "Subsidy Eligibility Runtime";
    private static final String PLUGIN_DESCRIPTION =
            "Per-applicant eligibility evaluation using the joget-rules-api compiled SQL — closes Phase 1 D1.G";
    private static final String PLUGIN_VERSION = "1.0.0";

    // Form IDs
    private static final String FORM_ELIG_RESULT = "spEligResult";
    private static final String FORM_ELIG_RULE_RESULT = "spEligRuleResult";
    private static final String FORM_PROGRAM_ELIG = "spProgramEligibility";

    private static final Gson GSON = new Gson();

    @Override public String getName()        { return PLUGIN_NAME; }
    @Override public String getVersion()     { return PLUGIN_VERSION; }
    @Override public String getDescription() { return PLUGIN_DESCRIPTION; }
    @Override public String getLabel()       { return PLUGIN_NAME; }
    @Override public String getClassName()   { return getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
                getClass().getName(),
                "/properties/eligibilityRuntime.json",
                null,
                true,
                "messages/eligibilityRuntime");
    }

    @Override
    public Object execute(Map properties) {
        long start = System.currentTimeMillis();

        String applicationId = str(properties, "applicationId");
        String farmerCode = str(properties, "applicantFarmerCode");
        String programCode = str(properties, "programCode");
        String rulesetCode = str(properties, "rulesetCode");
        String jreBaseUrl = strDefault(properties, "jreBaseUrl", "http://localhost:8080/jw");
        String jreApiKey = str(properties, "jreApiKey");
        boolean writeAudit = "true".equalsIgnoreCase(strDefault(properties, "writeAuditTrail", "true"));

        LogUtil.info(getClassName(),
                "EligibilityRuntime start: applicationId=" + applicationId
                        + ", farmerCode=" + farmerCode
                        + ", programCode=" + programCode
                        + ", rulesetCode=" + rulesetCode);

        Map<String, Object> result = new HashMap<>();
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");

        try {
            // 1. Resolve the ruleset
            JsonObject ruleset = loadRuleset(jreBaseUrl, jreApiKey, rulesetCode, programCode);
            if (ruleset == null) {
                return finishError(dao, applicationId, farmerCode, programCode,
                        "No ruleset bound to programme " + programCode, assignment, result);
            }
            String resolvedRulesetCode = ruleset.get("rulesetCode").getAsString();
            String script = ruleset.get("script").getAsString();
            String fieldScopeCode = ruleset.has("fieldScopeCode") && !ruleset.get("fieldScopeCode").isJsonNull()
                    ? ruleset.get("fieldScopeCode").getAsString() : "FARMER_APPLICATION";

            // 2. Compile via JRE REST
            JsonObject compiled = compileScript(jreBaseUrl, jreApiKey, script, fieldScopeCode, resolvedRulesetCode);
            if (compiled == null) {
                return finishError(dao, applicationId, farmerCode, programCode,
                        "JRE compile failed for ruleset " + resolvedRulesetCode, assignment, result);
            }
            String fullEligibilityQuery = compiled.has("fullEligibilityQuery") && !compiled.get("fullEligibilityQuery").isJsonNull()
                    ? compiled.get("fullEligibilityQuery").getAsString() : null;
            String scoringQuery = compiled.has("scoringQuery") && !compiled.get("scoringQuery").isJsonNull()
                    ? compiled.get("scoringQuery").getAsString() : null;
            if (fullEligibilityQuery == null) {
                return finishError(dao, applicationId, farmerCode, programCode,
                        "Compiled ruleset missing fullEligibilityQuery", assignment, result);
            }

            // 3. Run per-rule SQL filtered to one applicant
            EvaluationResult evalResult = runEligibilityForOne(fullEligibilityQuery, scoringQuery, farmerCode);
            if (evalResult == null) {
                return finishError(dao, applicationId, farmerCode, programCode,
                        "Farmer " + farmerCode + " not in JRE main table — derivation may be stale", assignment, result);
            }

            // 4. Apply programme threshold (optional layer over JRE's is_eligible)
            applyProgrammeThreshold(dao, programCode, evalResult);

            // 5. Persist results
            persistResults(dao, applicationId, farmerCode, programCode, resolvedRulesetCode, evalResult);

            // 6. Set workflow variable
            if (assignment != null) {
                WorkflowManager wm = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
                wm.activityVariable(assignment.getActivityId(), "eligibilityDecision", evalResult.decision);
                wm.activityVariable(assignment.getActivityId(), "eligibilityScore",
                        String.valueOf(evalResult.totalScore));
            }

            // 7. Audit
            long durationMs = System.currentTimeMillis() - start;
            if (writeAudit) {
                writeAuditLog(applicationId, farmerCode, programCode, resolvedRulesetCode,
                        evalResult.decision, evalResult.totalScore, durationMs);
            }

            result.put("status", "OK");
            result.put("decision", evalResult.decision);
            result.put("totalScore", evalResult.totalScore);
            result.put("isEligible", evalResult.isEligible);
            result.put("ruleCount", evalResult.perRuleResults.size());
            result.put("durationMs", durationMs);
            return result;

        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "EligibilityRuntime failed: " + ex.getMessage());
            return finishError(dao, applicationId, farmerCode, programCode,
                    "Runtime error: " + ex.getMessage(), assignment, result);
        }
    }

    // ------------------------------------------------------------------
    // JRE REST integration
    // ------------------------------------------------------------------

    /**
     * Load a ruleset from the JRE — either by code, or by programme binding.
     * Endpoint: GET /jw/api/jre/jre/loadRuleset?rulesetCode=... OR ?contextType=PROGRAM&contextCode=...
     */
    protected JsonObject loadRuleset(String baseUrl, String apiKey,
                                     String rulesetCode, String programCode) throws Exception {
        String url;
        if (rulesetCode != null && !rulesetCode.isEmpty()) {
            url = baseUrl + "/api/jre/jre/loadRuleset?rulesetCode=" + urlEncode(rulesetCode);
        } else {
            url = baseUrl + "/api/jre/jre/loadRuleset?contextType=PROGRAM&contextCode=" + urlEncode(programCode);
        }
        String body = httpGet(url, apiKey);
        if (body == null || body.isEmpty()) return null;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (root.has("ruleset") && !root.get("ruleset").isJsonNull()) {
            return root.getAsJsonObject("ruleset");
        }
        // Some implementations return the ruleset directly
        if (root.has("script")) return root;
        return null;
    }

    /**
     * Compile a script to SQL via the JRE.
     * Endpoint: POST /jw/api/jre/jre/compile  body: {script, fieldScopeCode, rulesetCode}
     */
    protected JsonObject compileScript(String baseUrl, String apiKey,
                                       String script, String fieldScopeCode, String rulesetCode) throws Exception {
        String url = baseUrl + "/api/jre/jre/compile";
        Map<String, String> reqBody = new HashMap<>();
        reqBody.put("script", script);
        reqBody.put("fieldScopeCode", fieldScopeCode);
        reqBody.put("rulesetCode", rulesetCode);
        String body = httpPost(url, apiKey, GSON.toJson(reqBody));
        if (body == null || body.isEmpty()) return null;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (root.has("compiled") && !root.get("compiled").isJsonNull()) {
            return root.getAsJsonObject("compiled");
        }
        // Fall back: return the root if it directly contains the query fields
        if (root.has("fullEligibilityQuery")) return root;
        return null;
    }

    // ------------------------------------------------------------------
    // SQL execution against Joget's main datasource
    // ------------------------------------------------------------------

    protected EvaluationResult runEligibilityForOne(String fullSql, String scoringSql, String farmerId) throws Exception {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        EvaluationResult out = new EvaluationResult();

        // Wrap the query to filter to one record
        // Note: rules-api emits SELECT alias.id AS its first column. We wrap in a subquery.
        String wrappedFull = "SELECT * FROM (\n" + fullSql + "\n) inner_q WHERE inner_q.id = ?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(wrappedFull)) {
            ps.setString(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // farmer not found in JRE main table
                }
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    String col = meta.getColumnName(i).toLowerCase();
                    String val = rs.getString(i);
                    if ("id".equals(col)) continue;
                    if ("is_eligible".equals(col)) {
                        out.isEligible = "1".equals(val) || "true".equalsIgnoreCase(val);
                        continue;
                    }
                    // Per-rule columns: rules-api typically emits one BOOLEAN/INT per rule
                    // (rule_xxxx_pass = 1/0). Capture them as RuleResult entries.
                    boolean passed = "1".equals(val) || "true".equalsIgnoreCase(val);
                    out.perRuleResults.add(new RuleResult(col, passed));
                }
            }
        }

        // Run scoring query (if present) to get total_score
        if (scoringSql != null && !scoringSql.isEmpty()) {
            String wrappedScore = "SELECT total_score FROM (\n" + scoringSql + "\n) score_q WHERE score_q.id = ?";
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(wrappedScore)) {
                ps.setString(1, farmerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out.totalScore = rs.getDouble("total_score");
                    }
                }
            } catch (Exception e) {
                LogUtil.warn(getClassName(),
                        "Scoring query failed (continuing with isEligible only): " + e.getMessage());
            }
        }

        out.decision = out.isEligible ? "PASS" : "FAIL";
        if (!out.isEligible) {
            out.decisionReason = "JRE evaluation: applicant not eligible (mandatory rule failed or excluded)";
        } else {
            out.decisionReason = "JRE evaluation: passed all mandatory inclusion rules; not excluded";
        }
        return out;
    }

    /**
     * Optional programme-level threshold layered on top of JRE's is_eligible.
     * If the programme has minimumScore / passingThreshold set on
     * spProgramEligibility, apply them.
     */
    protected void applyProgrammeThreshold(FormDataDao dao, String programCode, EvaluationResult eval) {
        FormRowSet rs = dao.find(FORM_PROGRAM_ELIG, FORM_PROGRAM_ELIG,
                "WHERE e.customProperties.parent_id = (SELECT id FROM app_fd_spProgram WHERE c_code = ? LIMIT 1)",
                new Object[]{programCode}, null, null, null, null);
        if (rs == null || rs.isEmpty()) return;
        FormRow row = rs.get(0);
        Double minScore = parseDouble(row.getProperty("minimumScore"));
        if (minScore != null && eval.isEligible && eval.totalScore < minScore) {
            eval.decision = "FAIL";
            eval.decisionReason = "Score " + eval.totalScore + " below programme minimum " + minScore;
        }
    }

    // ------------------------------------------------------------------
    // Persistence — writes to spEligResult + spEligRuleResult
    // ------------------------------------------------------------------

    protected void persistResults(FormDataDao dao, String applicationId, String farmerCode,
                                  String programCode, String rulesetCode, EvaluationResult eval) {
        // 1. spEligResult — upsert by applicationId
        FormRow result = new FormRow();
        result.setId(applicationId);
        result.setProperty("applicationId", applicationId);
        result.setProperty("farmerCode", farmerCode);
        result.setProperty("programCode", programCode);
        result.setProperty("rulesetCode", rulesetCode);
        result.setProperty("totalScore", String.valueOf(eval.totalScore));
        result.setProperty("isEligible", eval.isEligible ? "true" : "false");
        result.setProperty("decision", eval.decision);
        result.setProperty("decisionReason", eval.decisionReason);
        result.setProperty("evaluatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        FormRowSet results = new FormRowSet();
        results.add(result);
        dao.saveOrUpdate(FORM_ELIG_RESULT, FORM_ELIG_RESULT, results);

        // 2. spEligRuleResult — replace all rows for this applicationId
        // TODO: implement DELETE WHERE c_application_id = ? then save new rows.
        //       FormDataDao doesn't expose a direct delete-by-where; either:
        //       (a) use raw JDBC against app_fd_speligruleresult
        //       (b) load existing rows and call dao.delete one-by-one
        //       For now, save new rows with deterministic IDs derived from
        //       (applicationId + ruleName) so re-runs upsert.
        FormRowSet ruleRows = new FormRowSet();
        for (RuleResult rr : eval.perRuleResults) {
            FormRow r = new FormRow();
            r.setId(applicationId + "::" + rr.ruleName);
            r.setProperty("applicationId", applicationId);
            r.setProperty("ruleName", rr.ruleName);
            r.setProperty("passed", rr.passed ? "true" : "false");
            ruleRows.add(r);
        }
        if (!ruleRows.isEmpty()) {
            dao.saveOrUpdate(FORM_ELIG_RULE_RESULT, FORM_ELIG_RULE_RESULT, ruleRows);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected Map<String, Object> finishError(FormDataDao dao, String applicationId, String farmerCode,
                                              String programCode, String reason,
                                              WorkflowAssignment assignment, Map<String, Object> result) {
        LogUtil.warn(getClassName(), "EligibilityRuntime ERROR: " + reason);
        EvaluationResult err = new EvaluationResult();
        err.decision = "ERROR";
        err.decisionReason = reason;
        try {
            persistResults(dao, applicationId, farmerCode, programCode, "", err);
        } catch (Exception ignored) { /* swallow — already in error path */ }
        if (assignment != null) {
            WorkflowManager wm = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
            wm.activityVariable(assignment.getActivityId(), "eligibilityDecision", "ERROR");
        }
        result.put("status", "ERROR");
        result.put("decision", "ERROR");
        result.put("decisionReason", reason);
        return result;
    }

    protected void writeAuditLog(String applicationId, String farmerCode, String programCode,
                                 String rulesetCode, String decision, double totalScore, long durationMs) {
        // TODO: write to audit_log form (existing per CLAUDE.md). For now, log only.
        LogUtil.info(getClassName(),
                "audit: applicationId=" + applicationId
                        + " farmerCode=" + farmerCode
                        + " programCode=" + programCode
                        + " rulesetCode=" + rulesetCode
                        + " decision=" + decision
                        + " totalScore=" + totalScore
                        + " durationMs=" + durationMs);
    }

    protected String httpGet(String url, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("api_key", apiKey);
        }
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        if (code >= 400) {
            LogUtil.warn(getClassName(), "JRE GET " + url + " returned HTTP " + code);
            return null;
        }
        return readBody(conn);
    }

    protected String httpPost(String url, String apiKey, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("api_key", apiKey);
        }
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        int code = conn.getResponseCode();
        if (code >= 400) {
            LogUtil.warn(getClassName(), "JRE POST " + url + " returned HTTP " + code);
            return null;
        }
        return readBody(conn);
    }

    protected String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    protected String urlEncode(String s) throws Exception {
        return java.net.URLEncoder.encode(s, "UTF-8");
    }

    protected String str(Map p, String key) {
        Object v = p.get(key);
        return v != null ? v.toString() : "";
    }

    protected String strDefault(Map p, String key, String def) {
        Object v = p.get(key);
        return v != null && !v.toString().isEmpty() ? v.toString() : def;
    }

    protected Double parseDouble(Object v) {
        if (v == null) return null;
        try {
            String s = v.toString().trim();
            return s.isEmpty() ? null : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Internal data structures
    // ------------------------------------------------------------------

    protected static class EvaluationResult {
        boolean isEligible;
        double totalScore;
        String decision = "ERROR";
        String decisionReason = "";
        List<RuleResult> perRuleResults = new ArrayList<>();
    }

    protected static class RuleResult {
        final String ruleName;
        final boolean passed;
        RuleResult(String ruleName, boolean passed) {
            this.ruleName = ruleName;
            this.passed = passed;
        }
    }
}
