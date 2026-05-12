package global.govstack.regbb.engine.lifecycle;

import global.govstack.regbb.engine.support.RowWriter;
import global.govstack.statusframework.core.StatusFramework;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Thin convenience layer over {@link StatusFramework} for the
 * subsidy-application lifecycle. Mirrors the shape of
 * {@code NotifAudit}: a {@code create()} that establishes the initial
 * lifecycle state on a freshly-submitted application, plus
 * {@code transition()} variants for each downstream state.
 *
 * <p>Failures inside this layer are logged-and-swallowed — a failed
 * audit write must NOT crash the underlying business action (saving the
 * application or recording the operator decision).
 *
 * <p>The {@code c_lifecycleState} column on
 * {@code app_fd_subsidy_app_2025} is the kept-current view; {@code audit_log}
 * carries the full transition history. Operators see both: the column
 * shows where things are now, the audit list shows the timeline.
 */
public final class AppAudit {

    private static final String CLASS_NAME = AppAudit.class.getName();
    private static final String FORM_ID   = "subsidyApplication2025";
    private static final String TABLE     = "subsidy_app_2025";   // bare name, no app_fd_ prefix
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private AppAudit() {}

    // ──────────────────────────────────────────────────────────────────
    // CREATE — establishes initial state + seeds audit_log
    // ──────────────────────────────────────────────────────────────────

    /**
     * Stamp the initial lifecycle state on an application row + write the
     * null → {@code state} transition into the audit_log. Idempotent —
     * calling twice on the same row will only seed the audit row the
     * first time (because the application row's c_lifecycleState will
     * already be set).
     *
     * @param applicationId  application UUID (FormRow id)
     * @param state          initial state — typically {@link AppLifecycleStatus#SUBMITTED}
     * @param triggeredBy    actor (e.g. "citizen", "system", "operator:<userId>")
     * @param reason         short free-text reason, recorded on the audit row
     */
    public static void create(String applicationId, AppLifecycleStatus state,
                              String triggeredBy, String reason) {
        if (applicationId == null || applicationId.isEmpty() || state == null) return;
        try {
            FormDataDao dao = StatusFramework.getFormDataDao();
            FormRow row = dao.load(null, TABLE, applicationId);
            if (row == null) {
                LogUtil.warn(CLASS_NAME, "create: row not found for id=" + applicationId);
                return;
            }
            String existing = row.getProperty("lifecycleState");
            if (existing != null && !existing.isEmpty()) {
                // Already initialised — nothing to do. transition() is the
                // right entry point for state changes after this point.
                return;
            }
            row.setProperty("lifecycleState", state.getCode());
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            // Critical contract: RowWriter.save(formId, tableName, rows)
            //   - formId must be the form definition id ("subsidyApplication2025")
            //   - tableName is the bare name WITHOUT the app_fd_ prefix
            // Passing wrong formId or app_fd_-prefixed table name causes
            // AppService.storeFormData to silently drop the row's
            // properties. Verified May 2026 — Tumelo Qacha test rows came
            // through with empty c_lifecyclestate despite audit_log
            // showing DRAFT was created.
            RowWriter.save(FORM_ID, TABLE, rs);

            writeInitialAudit(applicationId, state, triggeredBy, reason);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "create failed: "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    private static void writeInitialAudit(String applicationId, AppLifecycleStatus state,
                                          String triggeredBy, String reason) {
        try {
            FormRow audit = new FormRow();
            audit.setId(UUID.randomUUID().toString());
            audit.setProperty("entity_type",     AppEntityType.APPLICATION.name());
            audit.setProperty("entity_id",       applicationId);
            audit.setProperty("from_status",     "");
            audit.setProperty("to_status",       state.getCode());
            audit.setProperty("triggered_by",    nz(triggeredBy));
            audit.setProperty("reason",          nz(reason));
            audit.setProperty("transitioned_at", ISO_UTC.format(new Date()));
            FormRowSet rs = new FormRowSet();
            rs.add(audit);
            RowWriter.save("audit_log", "audit_log", rs);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "writeInitialAudit failed: "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // TRANSITIONS
    // ──────────────────────────────────────────────────────────────────

    /**
     * Move the application to a new lifecycle state. The transition must
     * be allowed by the registered map (see {@code Activator.start()}).
     * If c_lifecycleState is currently empty (i.e. this is an old row
     * never seeded), this method does an implicit create() first to bring
     * the audit trail to a consistent starting point.
     */
    public static void transition(String applicationId, AppLifecycleStatus target,
                                  String triggeredBy, String reason) {
        if (applicationId == null || applicationId.isEmpty() || target == null) return;
        try {
            FormDataDao dao = StatusFramework.getFormDataDao();
            FormRow row = dao.load(null, TABLE, applicationId);
            if (row == null) {
                LogUtil.warn(CLASS_NAME, "transition: row not found for id=" + applicationId);
                return;
            }
            // Read CURRENT lifecycle state from the dedicated column
            // (lifecycleState), NOT from the fine-grained c_status column.
            // StatusFramework.transition() hard-codes "status" as the
            // lifecycle column name and would parse a c_status value like
            // 'auto_approved' or 'pending_data_clarification' as an
            // AppLifecycleStatus code — those don't exist in our enum, so
            // it throws IllegalStateException which we'd catch silently.
            // To avoid that, we do the validation + write + audit
            // ourselves and skip StatusFramework.transition.
            String existingCode = row.getProperty("lifecycleState");
            // Case-tolerant read — Postgres folds c_lifecyclestate to lowercase
            // and Joget's FormRow can come back keyed lowercase on load.
            if (existingCode == null || existingCode.isEmpty()) {
                existingCode = row.getProperty("lifecyclestate");
            }
            boolean hadExplicitLifecycle = (existingCode != null && !existingCode.isEmpty());
            AppLifecycleStatus current = null;
            if (hadExplicitLifecycle) {
                for (AppLifecycleStatus s : AppLifecycleStatus.values()) {
                    if (s.getCode().equalsIgnoreCase(existingCode)) {
                        current = s; break;
                    }
                }
            }
            // When the lifecycle column is empty (fresh row OR legacy pre-W3.1
            // row), keep current=null so canTransition() falls through to its
            // isInitialStatus() branch. Do NOT seed from c_status here —
            // AppLifecycleMapper.fromStatus("") returns SUBMITTED, which then
            // makes canTransition(SUBMITTED, SUBMITTED) reject the write as a
            // self-loop and produce a silent "invalid transition" warn (this
            // was the bug behind builds 130-138 where citizen submit appeared
            // to do nothing — the audit_log entry null→submitted came from
            // the worker's later AppAudit.create() call, not the citizen path).
            //
            // canTransition(null, SUBMITTED) checks isInitialStatus which is
            // true (SUBMITTED is in the initial set registered by Activator),
            // so the row + audit_log writes proceed correctly.
            //
            // Idempotent return ONLY when the row already explicitly carries
            // the target state.
            if (hadExplicitLifecycle && current == target) {
                return;
            }
            // Use StatusFramework's registered transition map for validation.
            if (!StatusFramework.canTransition(AppEntityType.APPLICATION, current, target)) {
                LogUtil.warn(CLASS_NAME, "invalid transition for " + applicationId
                        + ": " + (current == null ? "null" : current.getCode())
                        + " → " + target.getCode());
                return;
            }
            // Write the new state to the row (lifecycleState column).
            row.setProperty("lifecycleState", target.getCode());
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            RowWriter.save(FORM_ID, TABLE, rs);
            // Write audit row.
            writeAudit(applicationId, current, target, triggeredBy, reason);
            LogUtil.info(CLASS_NAME, "Lifecycle: " + applicationId + " "
                    + (current == null ? "null" : current.getCode()) + " → " + target.getCode()
                    + " (" + nz(triggeredBy) + ")");
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "transition failed for " + applicationId + ": "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    /** Direct audit_log write (companion to writeInitialAudit but with a from_status). */
    private static void writeAudit(String applicationId, AppLifecycleStatus from,
                                   AppLifecycleStatus to, String triggeredBy, String reason) {
        try {
            FormRow audit = new FormRow();
            audit.setId(UUID.randomUUID().toString());
            audit.setProperty("entity_type",     AppEntityType.APPLICATION.name());
            audit.setProperty("entity_id",       applicationId);
            audit.setProperty("from_status",     from == null ? "" : from.getCode());
            audit.setProperty("to_status",       to.getCode());
            audit.setProperty("triggered_by",    nz(triggeredBy));
            audit.setProperty("reason",          truncate(nz(reason), 400));
            audit.setProperty("transitioned_at", ISO_UTC.format(new Date()));
            FormRowSet rs = new FormRowSet();
            rs.add(audit);
            RowWriter.save("audit_log", "audit_log", rs);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "writeAudit failed: "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    /** Lookup helper — the application's current lifecycle state, or null. */
    public static AppLifecycleStatus currentState(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) return null;
        try {
            FormDataDao dao = StatusFramework.getFormDataDao();
            FormRow row = dao.load(null, TABLE, applicationId);
            if (row == null) return null;
            String code = row.getProperty("lifecycleState");
            if (code == null || code.isEmpty()) return null;
            for (AppLifecycleStatus s : AppLifecycleStatus.values()) {
                if (s.getCode().equalsIgnoreCase(code)) return s;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private static String nz(String s) { return s == null ? "" : s; }
    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }
}
