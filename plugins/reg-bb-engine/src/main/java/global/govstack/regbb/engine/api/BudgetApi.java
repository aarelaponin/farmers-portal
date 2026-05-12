package global.govstack.regbb.engine.api;

import global.govstack.regbb.engine.budget.BudgetEngine;
import global.govstack.regbb.engine.budget.CostEstimationService;
import java.math.BigDecimal;

import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * L3-1 1B-i — REST endpoint for Budget Engine dispatch. Lets us drive the
 * engine through HTTP without first wiring it into the subsidy storeBinder.
 * Used by {@code _tooling/budget_dispatch.py} for end-to-end tests.
 *
 * <p>Single endpoint: {@code POST /budget/dispatch}. Body shape:
 * <pre>{
 *   "actionCode":     "BUDGET_RESERVE_ON_SUBMIT",
 *   "applicationId":  "uuid-of-the-application",
 *   "envelopeCode":   "ENV_PRG_2025_002_FY2526",
 *   "actor":          "system" (optional — defaults to system),
 *   "correlationType":"subsidy_application" (optional),
 *   "sourceModule":   "subsidy" (optional),
 *   "applicantData":  { ... } (passed to amount-formula and condition rules)
 * }</pre>
 *
 * <p>1B-ii will replace this with a direct in-process call from the
 * subsidy storeBinder; the REST endpoint stays as a manual / tooling
 * surface.
 */
public class BudgetApi extends ApiPluginAbstract {

    private static final String CLASS_NAME = BudgetApi.class.getName();

    @Override public String getName()        { return "RegBB Budget API"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT"; }
    @Override public String getLabel()       { return "RegBB Budget API"; }
    @Override public String getDescription() { return "Budget Engine dispatch endpoint (L3-1 1B-i)"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getPropertyOptions() { return ""; }
    @Override public String getIcon()        { return "<i class=\"fas fa-coins\"></i>"; }
    @Override public String getTag()         { return "budget"; }

    @Operation(
        path = "/dispatch",
        type = Operation.MethodType.POST,
        summary = "Dispatch a budget event (L3-1 1B-i)",
        description = "Resolves an mm_action.kind=budget_event row, evaluates "
                    + "amount, posts balanced journal entries, refreshes projection. "
                    + "Idempotent on (actionCode + applicationId + eventType)."
    )
    @Responses({
        @Response(responseCode = 200, description = "Dispatch completed (status in body)"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during dispatch")
    })
    public ApiResponse dispatch(
        @Param(value = "body", required = false) String requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return badRequest("Request body is empty");
            }
            JSONObject req = new JSONObject(requestBody);
            String actionCode      = req.optString("actionCode", "");
            String applicationId   = req.optString("applicationId", "");
            String envelopeCode    = req.optString("envelopeCode", null);
            String actor           = req.optString("actor", "system");
            String correlationType = req.optString("correlationType", "subsidy_application");
            String sourceModule    = req.optString("sourceModule", "subsidy");
            JSONObject applicantDataJson = req.optJSONObject("applicantData");

            if (actionCode.isEmpty() || applicationId.isEmpty()) {
                return badRequest("actionCode and applicationId are required");
            }

            Map<String, Object> applicantData = jsonToMap(applicantDataJson);

            BudgetEngine.DispatchRequest dr = new BudgetEngine.DispatchRequest(
                    actionCode, applicationId, envelopeCode,
                    actor, correlationType, sourceModule, applicantData);

            BudgetEngine engine = new BudgetEngine();
            BudgetEngine.DispatchResult result = engine.dispatch(dr);

            JSONObject body = new JSONObject();
            body.put("status",        result.status);
            body.put("transactionId", result.transactionId == null ? "" : result.transactionId);
            body.put("envelopeCode",  result.envelopeCode == null ? "" : result.envelopeCode);
            body.put("eventType",     result.eventType == null ? "" : result.eventType);
            body.put("amount",        result.amount == null ? "" : result.amount.toPlainString());
            body.put("beneficiaryAccountCode",
                    result.beneficiaryAccountCode == null ? "" : result.beneficiaryAccountCode);
            body.put("errorCause",    result.errorCause == null ? "" : result.errorCause);
            body.put("elapsedMs",     result.elapsedMs);
            JSONArray lines = new JSONArray();
            for (BudgetEngine.JournalLine ln : result.journalLines) {
                JSONObject lo = new JSONObject();
                lo.put("accountPath", ln.accountPath);
                lo.put("direction",   ln.direction);
                lo.put("amount",      ln.amount.toPlainString());
                lines.put(lo);
            }
            body.put("journalLines", lines);
            return new ApiResponse(200, body.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/dispatch failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                   + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/ces/estimate",
        type = Operation.MethodType.POST,
        summary = "Cost Estimation Service — programme cost projection + launch gate (L3-1 1C)",
        description = "Computes estimated_cost = expectedApplicantCount × per-applicant amount. "
                    + "Returns coverage_ratio_pct, source_breakdown (proration), and the outcome of "
                    + "every programme_launch_gate rule. Read-only; safe to call repeatedly."
    )
    @Responses({
        @Response(responseCode = 200, description = "Estimate returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse cesEstimate(
        @Param(value = "body", required = false) String requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                return badRequest("Request body is empty");
            }
            JSONObject req = new JSONObject(requestBody);
            String programmeCode    = req.optString("programmeCode", "");
            int expectedApplicants  = req.optInt("expectedApplicantCount", 0);
            if (programmeCode.isEmpty()) {
                return badRequest("programmeCode is required");
            }

            CostEstimationService ces = new CostEstimationService();
            CostEstimationService.EstimateResult r = ces.estimate(programmeCode, expectedApplicants);

            JSONObject body = new JSONObject();
            body.put("status",                 r.errorCause == null ? "ok" : "error");
            body.put("programmeCode",          str(r.programmeCode));
            body.put("envelopeCode",           str(r.envelopeCode));
            body.put("envelopeAllocated",      bd(r.envelopeAllocated));
            body.put("currency",               str(r.currency));
            body.put("expectedApplicantCount", r.expectedApplicantCount);
            body.put("perApplicantAmount",     bd(r.perApplicantAmount));
            body.put("estimatedCost",          bd(r.estimatedCost));
            body.put("coverageRatioPct",       bd(r.coverageRatioPct));
            JSONObject breakdown = new JSONObject();
            for (Map.Entry<String, BigDecimal> e : r.sourceBreakdown.entrySet()) {
                breakdown.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toPlainString());
            }
            body.put("sourceBreakdown", breakdown);
            JSONArray gateLines = new JSONArray();
            for (CostEstimationService.LaunchGateRuleResult g : r.launchGateRules) {
                JSONObject go = new JSONObject();
                go.put("ruleCode",    str(g.ruleCode));
                go.put("outcome",     str(g.outcome));
                go.put("failMessage", str(g.failMessage));
                go.put("errorCause",  str(g.errorCause));
                gateLines.put(go);
            }
            body.put("launchGateRules",       gateLines);
            body.put("launchGateGreen",       r.launchGateGreen);
            body.put("launchGateFailMessage", str(r.launchGateFailMessage));
            body.put("errorCause",            str(r.errorCause));
            body.put("elapsedMs",             r.elapsedMs);
            return new ApiResponse(200, body.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/ces/estimate failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/timeseries",
        type = Operation.MethodType.GET,
        summary = "Per-day event counts + commitment sums for the dashboard sparkline",
        description = "Returns a time-series of budget activity for the last N days. "
                    + "If envelopeCode is supplied, scoped to that envelope; otherwise system-wide. "
                    + "Output: { days:[YYYY-MM-DD], events:[int], committed:[number] } — chart it as bars+line."
    )
    @Responses({
        @Response(responseCode = 200, description = "Series returned"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse timeseries(
        @Param(value = "envelopeCode", required = false) String envelopeCode,
        @Param(value = "days",         required = false) String daysStr
    ) {
        int days;
        try { days = Math.max(1, Math.min(365, Integer.parseInt(daysStr == null ? "30" : daysStr))); }
        catch (NumberFormatException e) { days = 30; }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT to_char(date_trunc('day', datecreated), 'YYYY-MM-DD') AS day, ")
           .append("count(*) AS events, ")
           .append("sum(CASE WHEN c_event_type IN ('PRE_COMMIT','BUDGET_PRE_COMMIT_ON_APPROVE') ")
           .append("            AND c_direction='debit' AND c_account_path LIKE 'ENV_%.PRE_COMMITTED' ")
           .append("         THEN CAST(c_amount AS NUMERIC(15,2)) ELSE 0 END) AS pre_committed ")
           .append("FROM app_fd_budget_event ")
           .append("WHERE datecreated > now() - INTERVAL '").append(days).append(" days' ");
        if (envelopeCode != null && !envelopeCode.isEmpty()) {
            sql.append("AND c_envelope_code = ? ");
        }
        sql.append("GROUP BY date_trunc('day', datecreated) ")
           .append("ORDER BY date_trunc('day', datecreated)");
        try {
            javax.sql.DataSource ds = (javax.sql.DataSource) org.joget.apps.app.service.AppUtil
                    .getApplicationContext().getBean("setupDataSource");
            JSONArray dayList    = new JSONArray();
            JSONArray eventsList = new JSONArray();
            JSONArray preList    = new JSONArray();
            try (java.sql.Connection c = ds.getConnection();
                 java.sql.PreparedStatement p = c.prepareStatement(sql.toString())) {
                if (envelopeCode != null && !envelopeCode.isEmpty()) p.setString(1, envelopeCode);
                try (java.sql.ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        dayList.put(rs.getString(1));
                        eventsList.put(rs.getInt(2));
                        java.math.BigDecimal v = rs.getBigDecimal(3);
                        preList.put(v == null ? 0 : v.doubleValue());
                    }
                }
            }
            JSONObject body = new JSONObject();
            body.put("envelopeCode", envelopeCode == null ? "" : envelopeCode);
            body.put("days",         days);
            body.put("series",       new JSONObject()
                    .put("day",          dayList)
                    .put("events",       eventsList)
                    .put("preCommitted", preList));
            return new ApiResponse(200, body.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/timeseries failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/run-projection-refresh",
        type = Operation.MethodType.POST,
        summary = "Run BudgetProjectionRefreshJob on demand (ADR-030 Step 5)",
        description = "Refreshes budget_projection + budget_projection_by_source materialised "
                    + "views CONCURRENTLY. Same logic as the scheduled job. Useful when "
                    + "operator needs an up-to-date dashboard without waiting for the next "
                    + "30-second tick. Idempotent."
    )
    @Responses({
        @Response(responseCode = 200, description = "Refresh completed"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse runProjectionRefresh() {
        try {
            global.govstack.regbb.engine.budget.BudgetProjectionRefreshJob job =
                    new global.govstack.regbb.engine.budget.BudgetProjectionRefreshJob();
            Object result = job.execute(new HashMap<>());
            JSONObject body = new JSONObject();
            body.put("status", "ok");
            body.put("summary", result == null ? "" : result.toString());
            return new ApiResponse(200, body.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/run-projection-refresh failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/run-eligibility-worker",
        type = Operation.MethodType.POST,
        summary = "Run EligibilityProcessingWorker on demand (ADR-030)",
        description = "Drains app_fd_processing_queue, runs the eligibility chain "
                    + "(eligibility evaluation + budget dispatch) for each pending row. "
                    + "Up to 50 rows per invocation; failures retry with exponential backoff; "
                    + "5 attempts → dead-letter. Same logic as the scheduled worker. "
                    + "Returns a summary string. Idempotent — safe to call repeatedly."
    )
    @Responses({
        @Response(responseCode = 200, description = "Worker ran"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse runEligibilityWorker() {
        try {
            global.govstack.regbb.engine.processing.EligibilityProcessingWorker worker =
                    new global.govstack.regbb.engine.processing.EligibilityProcessingWorker();
            Object result = worker.execute(new HashMap<>());
            JSONObject body = new JSONObject();
            body.put("status", "ok");
            body.put("summary", result == null ? "" : result.toString());
            return new ApiResponse(200, body.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/run-eligibility-worker failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/run-threshold-monitor",
        type = Operation.MethodType.POST,
        summary = "Run BudgetThresholdMonitor on demand (test + ops)",
        description = "Scans every envelope's utilisation, posts WATCH/OVER/AUTO_FREEZE alerts, "
                    + "auto-freezes envelopes at 110%. Same logic as the scheduled job. "
                    + "Returns a summary string. Idempotent — safe to call repeatedly."
    )
    @Responses({
        @Response(responseCode = 200, description = "Monitor ran"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse runThresholdMonitor() {
        try {
            global.govstack.regbb.engine.budget.BudgetThresholdMonitor monitor =
                    new global.govstack.regbb.engine.budget.BudgetThresholdMonitor();
            Object result = monitor.execute(new HashMap<>());
            JSONObject body = new JSONObject();
            body.put("status", "ok");
            body.put("summary", result == null ? "" : result.toString());
            return new ApiResponse(200, body.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/run-threshold-monitor failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/issue-vouchers",
        type = Operation.MethodType.POST,
        summary = "Issue IM vouchers for an approved subsidy application (Slice 4 — Phase E)",
        description = "Reads the application by id, looks up the active im_allocation_plan for "
                    + "its applied programme, and writes one im_voucher row per matching "
                    + "allocation line. Idempotent — re-running for the same applicationId "
                    + "skips lines that already have a voucher. Pass force=true to bypass the "
                    + "approved/auto_approved status check (admin override; useful for testing)."
    )
    @Responses({
        @Response(responseCode = 200, description = "Issuance result returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during issuance")
    })
    public ApiResponse issueVouchers(
        @Param(value = "applicationId", required = true) String applicationId,
        @Param(value = "force",         required = false) String forceStr,
        @Param(value = "actor",         required = false) String actorStr
    ) {
        if (applicationId == null || applicationId.isEmpty()) {
            return badRequest("applicationId is required");
        }
        boolean force = "true".equalsIgnoreCase(forceStr);
        String actor = (actorStr == null || actorStr.isEmpty())
                ? "rest:budget/issue-vouchers" : actorStr;
        try {
            global.govstack.regbb.engine.processing.VoucherIssuanceTool tool =
                    new global.govstack.regbb.engine.processing.VoucherIssuanceTool();
            global.govstack.regbb.engine.processing.VoucherIssuanceTool.Result r =
                    tool.issueFor(applicationId, force, actor);
            // Tool builds its own JSON (with the voucherCodes[]); pass through.
            return new ApiResponse(200, r.toJson());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/issue-vouchers failed for app=" + applicationId);
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("applicationId", applicationId);
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/redeem-voucher",
        type = Operation.MethodType.POST,
        summary = "Redeem an issued voucher at a Resource Centre (Slice 5 — Phase G)",
        description = "Validates the voucher (status=issued, not expired, allocated point matches "
                    + "the redemptionPoint), then atomically writes a im_voucher_redemption row, "
                    + "flips voucher.status issued→redeemed, and decrements the matching "
                    + "im_inventory row's quantity_on_hand by the redeemed quantity. Returns "
                    + "JSON with status (ok / voucher_not_found / already_redeemed / wrong_status / "
                    + "expired / wrong_point / error) and the redemption code."
    )
    @Responses({
        @Response(responseCode = 200, description = "Redemption result returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during redemption")
    })
    public ApiResponse redeemVoucher(
        @Param(value = "voucherCode",     required = true)  String voucherCode,
        @Param(value = "redemptionPoint", required = true)  String redemptionPoint,
        @Param(value = "redeemedBy",      required = true)  String redeemedBy,
        @Param(value = "quantity",        required = false) String quantity
    ) {
        if (voucherCode == null || voucherCode.isEmpty())
            return badRequest("voucherCode is required");
        if (redemptionPoint == null || redemptionPoint.isEmpty())
            return badRequest("redemptionPoint is required");
        if (redeemedBy == null || redeemedBy.isEmpty())
            return badRequest("redeemedBy is required");
        try {
            global.govstack.regbb.engine.processing.VoucherRedemptionTool tool =
                    new global.govstack.regbb.engine.processing.VoucherRedemptionTool();
            global.govstack.regbb.engine.processing.VoucherRedemptionTool.Result r =
                    tool.redeem(voucherCode, redemptionPoint, redeemedBy, quantity);
            return new ApiResponse(200, r.toJson());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/redeem-voucher failed for voucher=" + voucherCode);
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("voucherCode", voucherCode);
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/cancel-voucher",
        type = Operation.MethodType.POST,
        summary = "Cancel an issued voucher and release its budget COMMITMENT (Slice 10 — operator action)",
        description = "Operator-triggered void of a voucher in 'issued' state. Reads the original "
                    + "COMMITMENT amount from app_fd_budget_event by idempotency_key='voucher_issued:CODE', "
                    + "dispatches RELEASE_COMMITMENT with idempotency_key='voucher_cancelled:CODE', then "
                    + "flips voucher status to 'cancelled' with the operator's reason annotated in notes. "
                    + "Returns JSON with status (ok / not_found / already_redeemed / already_expired / "
                    + "already_cancelled / wrong_status / error) and the released amount. Idempotent — "
                    + "calling twice returns 'already_cancelled' on the second call."
    )
    @Responses({
        @Response(responseCode = 200, description = "Cancellation result returned"),
        @Response(responseCode = 400, description = "Invalid request (missing voucherCode or reason)"),
        @Response(responseCode = 500, description = "Server error during cancellation")
    })
    public ApiResponse cancelVoucher(
        @Param(value = "voucherCode", required = true)  String voucherCode,
        @Param(value = "reason",      required = true)  String reason,
        @Param(value = "actor",       required = false) String actor
    ) {
        if (voucherCode == null || voucherCode.isEmpty())
            return badRequest("voucherCode is required");
        if (reason == null || reason.isEmpty())
            return badRequest("reason is required (operator must state why)");
        try {
            global.govstack.regbb.engine.processing.VoucherCancellationTool tool =
                    new global.govstack.regbb.engine.processing.VoucherCancellationTool();
            global.govstack.regbb.engine.processing.VoucherCancellationTool.Result r =
                    tool.cancel(voucherCode, reason, actor);
            return new ApiResponse(200, r.toJson());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/cancel-voucher failed for voucher=" + voucherCode);
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("voucherCode", voucherCode);
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/expire-vouchers",
        type = Operation.MethodType.POST,
        summary = "Sweep expired vouchers and release their budget COMMITMENT (Slice 9 — production hardening)",
        description = "Walks app_fd_im_voucher for rows where status='issued' and expiry_date is in the past. "
                    + "For each, looks up the original COMMITMENT amount from app_fd_budget_event by "
                    + "idempotency_key='voucher_issued:CODE', then dispatches RELEASE_COMMITMENT (envelope "
                    + ".AVAILABLE += amount; .COMMITTED -= amount) with idempotency_key='voucher_expired:CODE'. "
                    + "Voucher status flips to 'expired'. Idempotent — running the sweep repeatedly produces "
                    + "no duplicate budget events. Designed to run on a daily schedule via a Joget workflow "
                    + "tool step; this endpoint is the ad-hoc operator trigger. Returns JSON with scanned/"
                    + "flipped/released/releaseSkipped counts."
    )
    @Responses({
        @Response(responseCode = 200, description = "Sweep result returned"),
        @Response(responseCode = 500, description = "Server error during sweep")
    })
    public ApiResponse expireVouchers(
        @Param(value = "actor", required = false) String actor
    ) {
        try {
            global.govstack.regbb.engine.processing.VoucherExpirySweeper sweeper =
                    new global.govstack.regbb.engine.processing.VoucherExpirySweeper();
            global.govstack.regbb.engine.processing.VoucherExpirySweeper.Result r =
                    sweeper.sweep(actor != null && !actor.isEmpty() ? actor : "operator:expire-vouchers");
            return new ApiResponse(200, r.toJson());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/expire-vouchers failed");
            JSONObject err = new JSONObject();
            err.put("status",     "error");
            err.put("errorCause", "internal:" + t.getClass().getSimpleName() + ":"
                                 + (t.getMessage() == null ? "" : t.getMessage()));
            return new ApiResponse(500, err.toString());
        }
    }

    // ---- W2 — scheduled email triggers (manually pokeable for testing) ----

    @Operation(
        path = "/send-pending-digest",
        type = Operation.MethodType.POST,
        summary = "Fire the supervisor pending-decisions digest email (template 05)",
        description = "Counts applications stuck in pending_review/pending_data_clarification for >24h, "
                    + "fires one digest email to the dev recipient (or in production, to all district "
                    + "supervisors). Idempotent — safe to run repeatedly; the email reflects current state."
    )
    @Responses({@Response(responseCode = 200, description = "Digest result")})
    public ApiResponse sendPendingDigest() {
        try {
            String summary = global.govstack.regbb.engine.notification.ScheduledEmailJobs.sendPendingDigest();
            JSONObject r = new JSONObject();
            r.put("status", "ok");
            r.put("summary", summary);
            return new ApiResponse(200, r.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/send-pending-digest failed");
            JSONObject err = new JSONObject();
            err.put("status", "error");
            err.put("errorCause", t.getClass().getSimpleName() + ":" + t.getMessage());
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/send-expiring-reminders",
        type = Operation.MethodType.POST,
        summary = "Fire 7-day voucher expiry reminders (template 08)",
        description = "Finds vouchers where expiry_date is exactly 7 days away and status is "
                    + "issued/partially_redeemed, fires one reminder email per voucher. Schedule "
                    + "daily; idempotent within the day."
    )
    @Responses({@Response(responseCode = 200, description = "Reminder sweep result")})
    public ApiResponse sendExpiringReminders() {
        try {
            String summary = global.govstack.regbb.engine.notification.ScheduledEmailJobs.sendExpiringRemindersFor7Days();
            JSONObject r = new JSONObject();
            r.put("status", "ok");
            r.put("summary", summary);
            return new ApiResponse(200, r.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/send-expiring-reminders failed");
            JSONObject err = new JSONObject();
            err.put("status", "error");
            err.put("errorCause", t.getClass().getSimpleName() + ":" + t.getMessage());
            return new ApiResponse(500, err.toString());
        }
    }

    @Operation(
        path = "/send-budget-alerts",
        type = Operation.MethodType.POST,
        summary = "Fire budget-envelope 75% threshold alerts (template 11)",
        description = "Computes (committed+expensed)/allocated per envelope; for any envelope in the "
                    + "75-90% band (and not yet alerted), fires one alert email to the finance officer. "
                    + "Schedule hourly during active subsidy cycles."
    )
    @Responses({@Response(responseCode = 200, description = "Alert sweep result")})
    public ApiResponse sendBudgetAlerts() {
        try {
            String summary = global.govstack.regbb.engine.notification.ScheduledEmailJobs.sendBudgetThresholdAlerts();
            JSONObject r = new JSONObject();
            r.put("status", "ok");
            r.put("summary", summary);
            return new ApiResponse(200, r.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "/budget/send-budget-alerts failed");
            JSONObject err = new JSONObject();
            err.put("status", "error");
            err.put("errorCause", t.getClass().getSimpleName() + ":" + t.getMessage());
            return new ApiResponse(500, err.toString());
        }
    }

    private static String str(String s) { return s == null ? "" : s; }
    private static String bd(BigDecimal v) { return v == null ? "" : v.toPlainString(); }

    private static Map<String, Object> jsonToMap(JSONObject o) {
        Map<String, Object> m = new HashMap<>();
        if (o == null) return m;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            m.put(k, v == JSONObject.NULL ? null : (v == null ? null : v.toString()));
        }
        return m;
    }

    private ApiResponse badRequest(String msg) {
        JSONObject body = new JSONObject();
        body.put("status",     "error");
        body.put("errorCause", msg);
        return new ApiResponse(400, body.toString());
    }
}
