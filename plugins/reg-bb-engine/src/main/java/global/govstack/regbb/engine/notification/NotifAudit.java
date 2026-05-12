package global.govstack.regbb.engine.notification;

import global.govstack.regbb.engine.support.RowWriter;
import global.govstack.statusframework.api.InvalidTransitionException;
import global.govstack.statusframework.core.StatusFramework;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Thin convenience layer over {@link StatusFramework} for the notification
 * lifecycle. Every dispatch (immediate or scheduled, email or SMS) goes
 * through this class so the audit trail is uniform.
 *
 * <p>Two-call pattern at every dispatch site:
 * <pre>{@code
 * String notifId = NotifAudit.create(
 *         eventCode, channel, backend, intendedRecipient, actualRecipient,
 *         correlationId, locale, subject, varsJson, testMode);
 * try {
 *     boolean ok = sendOne(...);
 *     NotifAudit.markSent(notifId, "smtp accepted");
 * } catch (Exception e) {
 *     NotifAudit.markFailed(notifId, e.getClass().getSimpleName() + ": " + e.getMessage());
 * }
 * }</pre>
 *
 * <p>The {@code create()} call writes a row in {@code notification_queue}
 * with {@code status=PENDING} and seeds the audit_log with one initial
 * transition (null → PENDING). The {@code markX()} calls flip status via
 * {@code StatusFramework.transition()}, which appends one audit_log row
 * per call.
 *
 * <p>Failures inside the audit layer are logged-and-swallowed: a failed
 * audit write must NOT crash the underlying business action (sending a
 * notification). Better to send the email and have a slightly imperfect
 * audit trail than to drop a citizen notification because the audit DAO
 * was momentarily unavailable.
 */
public final class NotifAudit {

    private static final String CLASS_NAME = NotifAudit.class.getName();
    private static final String TABLE       = "notification_queue";
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private NotifAudit() {}

    // ──────────────────────────────────────────────────────────────────
    // CREATE — inserts a PENDING row in notification_queue
    // ──────────────────────────────────────────────────────────────────

    /**
     * Create a new notification_queue row in {@code status=PENDING} and
     * return its generated id. Caller uses this id with {@link #markSent},
     * {@link #markSkipped}, {@link #markFailed}.
     *
     * <p>If anything goes wrong (DAO failure, table missing), this returns
     * {@code null} after logging — caller must handle null and continue
     * with the send anyway. See class-level javadoc for the rationale.
     */
    public static String create(String eventCode, String channel, String backend,
                                String intendedRecipient, String actualRecipient,
                                String correlationId, String locale, String subject,
                                String varsJson, boolean testMode) {
        // Backward-compat: callers that haven't switched to the 11-arg form
        // get the recipient-status auto-inferred from intendedRecipient being
        // empty (= no contact on registry) vs populated (= resolved).
        String inferredStatus = (intendedRecipient == null || intendedRecipient.isEmpty())
                ? "no_contact_on_registry" : "resolved";
        return create(eventCode, channel, backend, intendedRecipient, inferredStatus,
                actualRecipient, correlationId, locale, subject, varsJson, testMode);
    }

    /**
     * 11-arg form with explicit {@code intendedRecipientStatus}. Use this from
     * new dispatchers so the status is recorded as a forensic flag separate
     * from the recipient string. Allowed values today:
     * <ul>
     *   <li>{@code resolved} — resolver returned ≥1 recipient, address is in the column</li>
     *   <li>{@code no_contact_on_registry} — applicant has no email/phone on registry</li>
     *   <li>{@code operator_list_empty} — OPERATOR_LIST resolver, free-text list was blank</li>
     *   <li>{@code resolver_error} — resolver threw; intendedRecipient column will be empty</li>
     * </ul>
     */
    public static String create(String eventCode, String channel, String backend,
                                String intendedRecipient, String intendedRecipientStatus,
                                String actualRecipient,
                                String correlationId, String locale, String subject,
                                String varsJson, boolean testMode) {
        try {
            String id = UUID.randomUUID().toString();
            FormRow row = new FormRow();
            row.setId(id);
            row.setProperty("eventCode",                nz(eventCode));
            row.setProperty("channel",                  nz(channel));
            row.setProperty("backend",                  nz(backend));
            row.setProperty("intendedRecipient",        nz(intendedRecipient));
            row.setProperty("intendedRecipientStatus",  nz(intendedRecipientStatus));
            row.setProperty("actualRecipient",          nz(actualRecipient));
            row.setProperty("toAddress",                nz(actualRecipient));   // legacy column
            row.setProperty("correlationId",            nz(correlationId));
            row.setProperty("locale",                   nz(locale));
            row.setProperty("subject",                  truncate(nz(subject), 250));
            row.setProperty("varsJson",                 nz(varsJson));
            row.setProperty("priority",                 "NORMAL");
            row.setProperty("status",                   NotifStatus.PENDING.getCode());
            row.setProperty("attempts",                 "0");
            row.setProperty("testMode",                 testMode ? "Y" : "N");
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            RowWriter.save(TABLE, TABLE, rs);

            // Seed the audit trail with the initial null → PENDING transition.
            // StatusFramework only writes audit rows on transition(); we want
            // the creation itself recorded too, so emit one ourselves.
            writeInitialAudit(id, "dispatcher", nz(eventCode) + " created");
            return id;
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "create failed: " + t.getClass().getSimpleName()
                    + ":" + t.getMessage());
            return null;
        }
    }

    private static void writeInitialAudit(String notifId, String triggeredBy, String reason) {
        try {
            FormRow audit = new FormRow();
            audit.setId(UUID.randomUUID().toString());
            audit.setProperty("entity_type",     NotifEntityType.NOTIFICATION.name());
            audit.setProperty("entity_id",       notifId);
            audit.setProperty("from_status",     "");
            audit.setProperty("to_status",       NotifStatus.PENDING.getCode());
            audit.setProperty("triggered_by",    nz(triggeredBy));
            audit.setProperty("reason",          nz(reason));
            audit.setProperty("transitioned_at", ISO_UTC.format(new Date()));
            FormRowSet rs = new FormRowSet();
            rs.add(audit);
            RowWriter.save("audit_log", "audit_log", rs);
        } catch (Throwable t) {
            // Audit failures never block the underlying business action.
            LogUtil.warn(CLASS_NAME, "writeInitialAudit failed: "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // TRANSITIONS
    // ──────────────────────────────────────────────────────────────────

    public static void markSent(String notifId, String reason) {
        transition(notifId, NotifStatus.SENT, "dispatcher", reason, true);
    }

    public static void markSkipped(String notifId, String reason) {
        transition(notifId, NotifStatus.SKIPPED, "dispatcher", reason, false);
    }

    public static void markFailed(String notifId, String reason) {
        transition(notifId, NotifStatus.FAILED, "dispatcher", reason, false);
    }

    public static void markDeadLetter(String notifId, String reason) {
        transition(notifId, NotifStatus.DEAD_LETTER, "queue-worker", reason, false);
    }

    /** Operator retry — FAILED → PENDING. Increments attempts on the row. */
    public static void markPendingRetry(String notifId, String triggeredBy) {
        transition(notifId, NotifStatus.PENDING, triggeredBy, "operator retry", false);
    }

    private static void transition(String notifId, NotifStatus target,
                                   String triggeredBy, String reason,
                                   boolean updateSentAt) {
        if (notifId == null || notifId.isEmpty()) return;
        try {
            FormDataDao dao = StatusFramework.getFormDataDao();
            StatusFramework.transition(dao, NotifEntityType.NOTIFICATION, notifId,
                    target, nz(triggeredBy), truncate(nz(reason), 400));
            if (updateSentAt) {
                // Stamp the wall-clock send time on the row in addition to
                // the audit trail's transitioned_at.
                try {
                    FormRow row = dao.load(null, TABLE, notifId);
                    if (row != null) {
                        row.setProperty("sentAt", ISO_UTC.format(new Date()));
                        FormRowSet rs = new FormRowSet();
                        rs.add(row);
                        RowWriter.save(TABLE, TABLE, rs);
                    }
                } catch (Throwable t) {
                    LogUtil.warn(CLASS_NAME, "sentAt update failed: " + t.getMessage());
                }
            }
        } catch (InvalidTransitionException ite) {
            LogUtil.warn(CLASS_NAME, "invalid transition for " + notifId + ": " + ite.getMessage());
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "transition failed for " + notifId + ": "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    /** Look up the current status of a notification row (null-safe). */
    public static NotifStatus currentStatus(String notifId) {
        if (notifId == null || notifId.isEmpty()) return null;
        try {
            FormDataDao dao = StatusFramework.getFormDataDao();
            FormRow row = dao.load(null, TABLE, notifId);
            if (row == null) return null;
            String code = row.getProperty("status");
            return code == null ? null : valueOfCode(code);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Increment the row's attempts counter. */
    public static int incrementAttempts(String notifId) {
        try {
            FormDataDao dao = StatusFramework.getFormDataDao();
            FormRow row = dao.load(null, TABLE, notifId);
            if (row == null) return -1;
            String cur = row.getProperty("attempts");
            int n = (cur == null || cur.isEmpty()) ? 1 : Integer.parseInt(cur) + 1;
            row.setProperty("attempts", String.valueOf(n));
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            RowWriter.save(TABLE, TABLE, rs);
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static NotifStatus valueOfCode(String code) {
        for (NotifStatus s : NotifStatus.values()) {
            if (s.getCode().equalsIgnoreCase(code)) return s;
        }
        return null;
    }

    private static String nz(String s)  { return s == null ? "" : s; }
    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }
}
