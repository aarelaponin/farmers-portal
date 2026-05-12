package global.govstack.regbb.engine.processing;

import global.govstack.regbb.engine.binder.RegBbApplicationStoreBinder;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * ADR-030 Step 3 — drains {@code app_fd_processing_queue} and runs the
 * eligibility chain off the request thread.
 *
 * <p>Wireable as a tool step inside any Joget workflow process; schedule
 * the workflow on a 30-second cadence (or whatever interval matches your
 * load profile). For demo / test environments invoke on demand via the
 * {@code /budget/run-threshold-monitor} pattern (a sibling REST endpoint
 * lives in {@code BudgetApi.runEligibilityWorker} once you add it to
 * API Builder's enabledPaths).
 *
 * <p>Per-row state machine on the queue row (column {@code c_stage}):
 * <ul>
 *   <li><b>eligibility_pending</b> — fresh from submit. Worker runs the
 *       eligibility chain (which also fires budget lifecycle events
 *       inside {@code RegBbApplicationStoreBinder.runChain}). On success
 *       transitions to {@code budget_done} (single-stage worker, since
 *       the chain bundles eligibility + budget today). On failure: bumps
 *       {@code c_attempt_count}, sets {@code c_next_attempt_at} with
 *       exponential backoff, leaves stage as {@code eligibility_pending}
 *       so the next run picks it up again.
 *   <li><b>eligibility_done</b> — reserved for a future Step 4 split where
 *       budget dispatch is its own worker. Today the chain is bundled, so
 *       this state is unused.
 *   <li><b>budget_done</b> — terminal. Worker skips.
 * </ul>
 *
 * <p>Retry policy: {@code MAX_ATTEMPTS = 5}, backoff = 30s × 2^attempt
 * (capped at 30 min). On the 5th terminal failure the row is moved to
 * {@code app_fd_processing_dead_letter} and removed from the queue.
 *
 * <p>Locking: single-instance assumed today. The {@code c_locked_by} +
 * {@code c_locked_at} columns are present for future multi-instance
 * coordination; the worker reads them but doesn't yet enforce lock
 * exclusivity (would need {@code FOR UPDATE SKIP LOCKED}, which Joget's
 * FormDataDao doesn't expose — direct SQL would be needed). Adding
 * multi-instance is a follow-up if queue depth ever exceeds what one
 * worker can drain.
 */
public class EligibilityProcessingWorker extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = EligibilityProcessingWorker.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static final int MAX_ATTEMPTS = 5;
    private static final long BACKOFF_BASE_S = 30;
    private static final long BACKOFF_MAX_S  = 30 * 60;
    private static final int  BATCH_LIMIT    = 50;

    @Override public String getName()        { return "Eligibility Processing Worker"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "Eligibility Processing Worker"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "ADR-030 — drains processing_queue, runs eligibility chain off the request thread."; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Eligibility Worker\", \"properties\":[] } ]";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        long started = System.currentTimeMillis();
        int processed = 0, failed = 0, deadLettered = 0, skipped = 0;
        String workerId = "worker-" + Long.toHexString(System.nanoTime() & 0xFFFFFFL);

        List<QueueItem> pending = readPending(BATCH_LIMIT);
        if (pending.isEmpty()) {
            return "EligibilityProcessingWorker: queue empty, elapsed="
                    + (System.currentTimeMillis() - started) + "ms";
        }

        for (QueueItem item : pending) {
            try {
                Map<String, Object> data = readApplicationData(item.applicationId);
                if (data == null) {
                    skipped++;
                    LogUtil.warn(CLASS_NAME, "application not found for queue row "
                            + item.queueId + " (app=" + item.applicationId + ") — leaving in queue");
                    continue;
                }
                // W2 — fire application_submitted email (template 01) once,
                // before the eligibility chain runs. The worker only picks up
                // applications with empty c_status, so reaching this point
                // means it's the citizen's first encounter with the system —
                // the perfect moment for the "we received your application"
                // acknowledgement. Best-effort.
                try {
                    fireApplicationSubmittedEmail(item.applicationId, data);
                } catch (Throwable emailEx) {
                    LogUtil.error(CLASS_NAME, emailEx,
                        "application_submitted email failed for " + item.applicationId);
                }

                // W3.1 — stamp the lifecycle state as SUBMITTED. This is the
                // worker's first encounter with the row (c_status was empty
                // on pickup, so this is the citizen-just-clicked-Submit
                // moment). Best-effort; failure must not block the chain.
                try {
                    global.govstack.regbb.engine.lifecycle.AppAudit.create(
                            item.applicationId,
                            global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.SUBMITTED,
                            "system:async-worker",
                            "application picked up by EligibilityProcessingWorker");
                } catch (Throwable t) {
                    LogUtil.warn(CLASS_NAME, "AppAudit.create(SUBMITTED) failed for "
                            + item.applicationId + ": " + t.getMessage());
                }

                // Run the chain. RegBbApplicationStoreBinder.runChain is
                // never-null per ADR-007; the chain writes c_status to a
                // terminal value (auto_approved / auto_rejected /
                // pending_data_clarification) which excludes the row from
                // the next worker poll.
                new RegBbApplicationStoreBinder().runChain(
                        item.applicationId,
                        item.programmeCode,
                        "subsidyApplication2025",
                        "subsidy_app_2025",
                        data,
                        "system:async-worker");
                processed++;

                // Slice 6b — auto-issuance hook. Re-read the application's
                // status; if the chain transitioned it to auto_approved/approved
                // and there's an active allocation plan for the programme, fire
                // the voucher issuance tool. Failures here are logged but don't
                // mark the chain as failed — eligibility ran successfully; the
                // operator can re-issue manually via /budget/issue-vouchers.
                try {
                    String postStatus = readApplicationStatus(item.applicationId);

                    // W3.1 — flip the lifecycle to the terminal state matching
                    // the c_status the chain just produced. AppLifecycleMapper
                    // handles the fine-grained → coarse mapping (e.g.
                    // auto_approved → APPROVED).
                    try {
                        global.govstack.regbb.engine.lifecycle.AppLifecycleStatus target =
                            global.govstack.regbb.engine.lifecycle.AppLifecycleMapper.fromStatus(postStatus);
                        global.govstack.regbb.engine.lifecycle.AppAudit.transition(
                                item.applicationId,
                                target,
                                "system:async-worker",
                                "chain produced c_status=" + (postStatus == null ? "" : postStatus));
                    } catch (Throwable t) {
                        LogUtil.warn(CLASS_NAME, "AppAudit.transition failed for "
                                + item.applicationId + ": " + t.getMessage());
                    }

                    // W2 — fire application outcome email (templates 02/03/04)
                    // BEFORE auto-issuance, so the citizen sees the eligibility
                    // decision first and the voucher email arrives second.
                    try {
                        fireApplicationOutcomeEmail(item.applicationId, postStatus, data);
                    } catch (Throwable emailEx) {
                        LogUtil.error(CLASS_NAME, emailEx,
                            "Application outcome email failed for " + item.applicationId);
                    }

                    if ("approved".equalsIgnoreCase(postStatus)
                     || "auto_approved".equalsIgnoreCase(postStatus)) {
                        VoucherIssuanceTool tool = new VoucherIssuanceTool();
                        VoucherIssuanceTool.Result r = tool.issueFor(
                                item.applicationId, /*force*/ false, "system:async-worker");
                        LogUtil.info(CLASS_NAME, "auto-issuance for " + item.applicationId
                                + ": status=" + r.status
                                + " issued=" + r.vouchersIssued
                                + " skipped=" + r.vouchersSkippedExisting
                                + " (" + r.message + ")");
                    }
                } catch (Throwable t2) {
                    LogUtil.error(CLASS_NAME, t2,
                        "auto-issuance failed for application " + item.applicationId
                        + " — eligibility chain succeeded; vouchers can be issued "
                        + "later via /budget/issue-vouchers");
                }
            } catch (Throwable t) {
                failed++;
                LogUtil.error(CLASS_NAME, t, "chain run failed for application "
                        + item.applicationId + " — left pending for next poll");
                // Row stays in c_status='' state; next worker pass picks it up.
                // Real production would track attempt count; for now the
                // pending pipeline list shows the operator how long it has
                // been waiting and they can intervene.
            }
        }

        long elapsed = System.currentTimeMillis() - started;
        String summary = "EligibilityProcessingWorker[" + workerId + "]: "
                + "scanned=" + pending.size()
                + " processed=" + processed
                + " failed=" + failed
                + " deadLettered=" + deadLettered
                + " skipped=" + skipped
                + " elapsedMs=" + elapsed;
        LogUtil.info(CLASS_NAME, summary);
        return summary;
    }

    // -----------------------------------------------------------------
    //  Queue reads/writes
    // -----------------------------------------------------------------

    private static final class QueueItem {
        String queueId;
        String applicationId;
        String programmeCode;
        String applicantNid;
        String submittedAt;
        int    attemptCount;
        String stage;
    }

    /** Read up-to-{@code limit} pending applications directly from the
     *  subsidy application table — rows where c_status is empty/null
     *  (i.e. eligibility hasn't run yet). Ordered by datecreated ASC so
     *  the oldest unprocessed application gets attention first.
     *
     *  <p>This replaces the original queue-table approach. Scanning the
     *  application table avoids the cross-transaction issue that the
     *  binder's enqueue ran into (any FormDataDao call inside store()
     *  shares the form-save transaction, and an exception there marks
     *  it rollback-only). The application's own c_status field is the
     *  natural pending/done flag — no separate queue needed for the
     *  happy path. */
    private List<QueueItem> readPending(int limit) {
        List<QueueItem> out = new ArrayList<>();
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        // W3.4 — gate the worker on c_lifecycleState='submitted'. DRAFT rows
        // (c_lifecycleState='draft') sit in the table with c_status='' too,
        // but the citizen hasn't explicitly submitted them; the worker must
        // ignore them until the citizen ticks "I confirm submission" on the
        // final wizard tab. Backward-compat: NULL c_lifecycleState is treated
        // as legacy = effectively submitted (pre-W3.4 rows). See decision-log
        // entry D55 for the full reasoning.
        String sql = "SELECT id, c_applied_programme, c_national_id, datecreated "
                   + "  FROM app_fd_subsidy_app_2025 "
                   + " WHERE (c_status IS NULL OR c_status = '') "
                   + "   AND (c_lifecycleState IS NULL "
                   + "        OR c_lifecycleState = '' "
                   + "        OR c_lifecycleState = 'submitted') "
                   + " ORDER BY datecreated ASC NULLS LAST "
                   + " LIMIT ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    QueueItem q = new QueueItem();
                    q.queueId       = rs.getString(1);  // = applicationId here
                    q.applicationId = rs.getString(1);
                    q.programmeCode = rs.getString(2);
                    q.applicantNid  = rs.getString(3);
                    q.submittedAt   = rs.getString(4);
                    q.attemptCount  = 0;
                    q.stage         = "eligibility_pending";
                    out.add(q);
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readPending failed: " + e.getSQLState() + ":" + e.getMessage());
        }
        return out;
    }

    /** Read the application's row from app_fd_subsidy_app_2025 as a Map of
     *  field-id → value. Returns null if the row doesn't exist. */
    private Map<String, Object> readApplicationData(String applicationId) {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        String sql = "SELECT * FROM app_fd_subsidy_app_2025 WHERE id = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, applicationId);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> data = new HashMap<>();
                int n = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= n; i++) {
                    String col = rs.getMetaData().getColumnName(i);
                    Object v = rs.getObject(i);
                    // Strip Joget's c_ prefix so EvalContext.data keys match
                    // the form-field id convention the rules expect.
                    String key = col.startsWith("c_") ? col.substring(2) : col;
                    data.put(key, v);
                }
                return data;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readApplicationData failed for " + applicationId
                    + ": " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    /**
     * W2 — fire template 01 (application_submitted) right when the worker
     * first picks up the application. Lifecycle position: between citizen
     * submit and eligibility evaluation. Once-per-application because the
     * worker filter (c_status IS NULL) excludes already-processed rows.
     */
    private void fireApplicationSubmittedEmail(String applicationId,
                                               Map<String, Object> data) {
        final String firstName  = firstWord(stringFromData(data, "full_name"));
        final String fullName   = stringFromData(data, "full_name");
        final String programme  = stringFromData(data, "applied_programme");
        final String nid        = stringFromData(data, "national_id");
        final String appCode    = "AP-" + (applicationId == null || applicationId.length() < 8
                ? "UNKNOWN" : applicationId.substring(0, 8).toUpperCase());
        final String today      = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());

        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("national_id",      nid);   // required for APPLICANT resolver
        vars.put("first_name",       firstName);
        vars.put("farmer_name",      fullName);
        vars.put("application_code", appCode);
        vars.put("submitted_date",   today);
        vars.put("programme_name",   programme);
        vars.put("program_name",     programme);
        vars.put("district_name",    "your local");
        vars.put("district_phone",   "your district office");

        global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
            "APP_SUBMITTED", "EN", vars);
    }

    /**
     * W2 — fire one of templates 02 / 03 / 04 based on the post-chain status.
     * Status mapping:
     *   approved / auto_approved        -> template 02 application_auto_approved
     *   pending_review / pending_data_*  -> template 03 application_pending_review
     *   rejected / auto_rejected         -> template 04 application_rejected
     * Anything else (still empty, unknown) -> no email; the next worker pass will retry.
     */
    private void fireApplicationOutcomeEmail(String applicationId, String status,
                                             Map<String, Object> data) {
        if (status == null || status.isEmpty()) return;
        String s = status.toLowerCase();
        String firstName  = firstWord(stringFromData(data, "full_name"));
        String fullName   = stringFromData(data, "full_name");
        String programme  = stringFromData(data, "applied_programme");
        String nid        = stringFromData(data, "national_id");
        // Citizen-friendly reference: first 8 hex chars of UUID, e.g. "AP-1CF4704A".
        String appCode = "AP-" + (applicationId == null || applicationId.length() < 8
                ? "UNKNOWN"
                : applicationId.substring(0, 8).toUpperCase());
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());

        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("national_id",      nid);   // required for APPLICANT resolver
        vars.put("first_name",       firstName);
        vars.put("farmer_name",      fullName);
        vars.put("application_code", appCode);
        vars.put("decision_date",    today);
        vars.put("validity_days",    "30");
        vars.put("programme_name",   programme);
        vars.put("program_name",     programme);
        vars.put("district_name",    "your local");
        vars.put("district_phone",   "your district office");

        if (s.equals("approved") || s.equals("auto_approved")) {
            global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                "APP_APPROVED", "EN", vars);
        } else if (s.contains("pending")) {
            global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                "APP_UNDER_REVIEW", "EN", vars);
        } else if (s.contains("reject")) {
            String reason = stringFromData(data, "decision_comment");
            if (reason == null || reason.isEmpty())
                reason = "Eligibility rules did not match this programme.";
            vars.put("reason", reason);
            global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                "APP_REJECTED", "EN", vars);
        }
    }

    private static String stringFromData(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String firstWord(String s) {
        if (s == null) return "there";
        String t = s.trim();
        if (t.isEmpty()) return "there";
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }

    /** Re-reads c_status after the eligibility chain ran, for the auto-issuance hook. */
    private String readApplicationStatus(String applicationId) {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        String sql = "SELECT c_status FROM app_fd_subsidy_app_2025 WHERE id = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, applicationId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readApplicationStatus failed: "
                    + e.getSQLState() + ":" + e.getMessage());
        }
        return null;
    }

    // Note: queue-table tracking (markDone / bumpRetry / moveToDeadLetter)
    // removed in the post-2026-05-05 redesign. The application row's
    // c_status field is the natural done/pending flag. Production-grade
    // retry tracking and dead-letter ergonomics will come back when we
    // need them — likely as columns on app_fd_subsidy_app_2025
    // (c_processing_attempts, c_processing_last_error) rather than as
    // a separate queue table.

    // -----------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------

    private static int parseIntSafe(String s, int dflt) {
        if (s == null || s.isEmpty()) return dflt;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return dflt; }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String safe(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String stackHead(Throwable t, int frames) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(": ").append(safe(t.getMessage())).append('\n');
        StackTraceElement[] st = t.getStackTrace();
        int n = Math.min(frames, st == null ? 0 : st.length);
        for (int i = 0; i < n; i++) sb.append("    at ").append(st[i]).append('\n');
        return sb.toString();
    }
}
