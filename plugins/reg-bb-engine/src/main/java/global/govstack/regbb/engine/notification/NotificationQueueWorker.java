package global.govstack.regbb.engine.notification;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Background worker that drains {@code app_fd_notification_queue}.
 *
 * <p>Same shape as {@code EligibilityProcessingWorker}: a daemon thread
 * polls every 60 s, picks rows whose {@code status='PENDING'} and
 * {@code scheduledFor <= now()}, ordered by priority (HIGH first) then
 * scheduledFor (oldest first). For each row it calls the right dispatcher
 * (email or SMS), then updates the row's {@code status}, {@code attempts},
 * {@code lastError}, {@code sentAt} via {@link global.govstack.regbb.engine.support.RowWriter}.
 *
 * <p>Retry policy: on failure, {@code attempts} is incremented and {@code lastError}
 * stored. After {@link #MAX_ATTEMPTS} failures the row moves to {@code status='DEAD'}
 * and the worker stops touching it — operator must inspect manually from
 * the "Notification Queue" admin menu.
 */
public final class NotificationQueueWorker {

    private static final String CLASS_NAME = NotificationQueueWorker.class.getName();
    private static final int    BATCH_SIZE    = 25;
    private static final int    MAX_ATTEMPTS  = 5;
    private static final long   POLL_GAP_MS   = 60_000L;
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static volatile Thread daemon = null;
    private static volatile boolean stop  = false;

    private NotificationQueueWorker() {}

    /** Start the daemon. Idempotent — safe to call from Activator.start(). */
    public static synchronized void start() {
        if (daemon != null && daemon.isAlive()) return;
        stop = false;
        daemon = new Thread(NotificationQueueWorker::run, "regbb-notif-queue-worker");
        daemon.setDaemon(true);
        daemon.start();
        LogUtil.info(CLASS_NAME, "started (poll every " + POLL_GAP_MS + "ms, batch=" + BATCH_SIZE + ")");
    }

    /** Stop the daemon. Called from Activator.stop(). */
    public static synchronized void shutdown() {
        stop = true;
        if (daemon != null) daemon.interrupt();
        LogUtil.info(CLASS_NAME, "stopped");
    }

    private static void run() {
        while (!stop) {
            try {
                int n = drainOne();
                if (n == 0) {
                    Thread.sleep(POLL_GAP_MS);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "worker loop error — sleeping " + POLL_GAP_MS + "ms");
                try { Thread.sleep(POLL_GAP_MS); } catch (InterruptedException ie) { return; }
            }
        }
    }

    /** Pop a batch of due rows and dispatch each. Returns number dispatched (success+fail). */
    private static int drainOne() {
        List<QueueRow> due = fetchDue();
        if (due.isEmpty()) return 0;
        int handled = 0;
        for (QueueRow row : due) {
            try {
                dispatch(row);
                handled++;
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "dispatch of " + row.id + " failed unexpectedly");
                recordFailure(row, t.getClass().getSimpleName() + ":" + safeTrunc(t.getMessage(), 200));
            }
        }
        return handled;
    }

    private static List<QueueRow> fetchDue() {
        List<QueueRow> out = new ArrayList<>();
        // W2.6 — only pick up SCHEDULED rows. Immediate-dispatch rows are
        // also written with status=PENDING but with empty c_scheduledfor;
        // they're already handled inline by EmailDispatcher / SmsDispatcher
        // and must not be re-dispatched here.
        // c_status filter includes 'pending' (lower) for joget-status-framework
        // compatibility and 'PENDING' (upper) for legacy rows.
        String sql =
            "SELECT id, c_eventcode, c_toaddress, c_toname, c_channel, c_locale, "
          + "       c_varsjson, c_scheduledfor, c_priority, c_attempts "
          + "  FROM app_fd_notification_queue "
          + " WHERE LOWER(COALESCE(c_status,'pending')) = 'pending' "
          + "   AND COALESCE(c_scheduledfor, '') <> '' "
          + "   AND c_scheduledfor::timestamp <= now() AT TIME ZONE 'UTC' "
          + " ORDER BY CASE c_priority WHEN 'HIGH' THEN 1 ELSE 2 END, c_scheduledfor "
          + " LIMIT " + BATCH_SIZE;
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            try (Connection c = ds.getConnection();
                 PreparedStatement p = c.prepareStatement(sql);
                 ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    QueueRow q = new QueueRow();
                    q.id           = rs.getString(1);
                    q.eventCode    = rs.getString(2);
                    q.toAddress    = rs.getString(3);
                    q.toName       = rs.getString(4);
                    q.channel      = rs.getString(5);
                    q.locale       = rs.getString(6);
                    q.varsJson     = rs.getString(7);
                    q.scheduledFor = rs.getString(8);
                    q.priority     = rs.getString(9);
                    String a = rs.getString(10);
                    q.attempts = (a == null || a.isEmpty()) ? 0 : Integer.parseInt(a);
                    out.add(q);
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "fetchDue SQL failed: " + safeTrunc(e.getMessage(), 200));
        }
        return out;
    }

    private static void dispatch(QueueRow row) {
        Map<String, String> vars = parseVarsJson(row.varsJson);
        String ev = row.eventCode == null ? "" : row.eventCode;
        boolean ok;
        if ("SMS".equalsIgnoreCase(row.channel)) {
            ok = SmsDispatcher.sendByEvent(ev, vars);
        } else {
            // Email: re-dispatch via sendByEvent (which will look up resolver
            // again). The queue row's toAddress is informational here — the
            // dispatcher resolves recipients afresh.
            ok = EmailDispatcher.sendByEvent(ev, row.locale == null ? "EN" : row.locale, vars);
        }
        if (ok) recordSuccess(row);
        else    recordFailure(row, "Dispatcher returned false (template missing/disabled or all recipients failed)");
    }

    private static Map<String, String> parseVarsJson(String json) {
        Map<String, String> m = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return m;
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = o.get(k);
                m.put(k, v == null ? "" : v.toString());
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "parseVarsJson failed: " + e.getMessage());
        }
        return m;
    }

    private static void recordSuccess(QueueRow row) {
        update(row, "SENT", row.attempts + 1, "", ISO_UTC.format(new Date()));
    }

    private static void recordFailure(QueueRow row, String err) {
        int next = row.attempts + 1;
        String status = (next >= MAX_ATTEMPTS) ? "DEAD" : "PENDING";
        update(row, status, next, err, null);
    }

    private static void update(QueueRow row, String status, int attempts, String lastError, String sentAt) {
        try {
            FormRow r = new FormRow();
            r.setId(row.id);
            r.setProperty("eventCode",    row.eventCode == null ? "" : row.eventCode);
            r.setProperty("toAddress",    row.toAddress == null ? "" : row.toAddress);
            r.setProperty("toName",       row.toName == null ? "" : row.toName);
            r.setProperty("channel",      row.channel == null ? "EMAIL" : row.channel);
            r.setProperty("locale",       row.locale == null ? "EN" : row.locale);
            r.setProperty("varsJson",     row.varsJson == null ? "" : row.varsJson);
            r.setProperty("scheduledFor", row.scheduledFor == null ? "" : row.scheduledFor);
            r.setProperty("priority",     row.priority == null ? "NORMAL" : row.priority);
            r.setProperty("status",       status);
            r.setProperty("attempts",     String.valueOf(attempts));
            r.setProperty("lastError",    safeTrunc(lastError, 400));
            if (sentAt != null) r.setProperty("sentAt", sentAt);
            FormRowSet rs = new FormRowSet();
            rs.add(r);
            global.govstack.regbb.engine.support.RowWriter.save(
                    "notification_queue", "notification_queue", rs);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "update of " + row.id + " failed");
        }
    }

    private static String safeTrunc(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }

    private static class QueueRow {
        String id, eventCode, toAddress, toName, channel, locale,
               varsJson, scheduledFor, priority;
        int attempts;
    }
}
