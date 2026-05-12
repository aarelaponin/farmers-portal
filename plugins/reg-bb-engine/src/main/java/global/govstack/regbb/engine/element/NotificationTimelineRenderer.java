package global.govstack.regbb.engine.element;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Render every notification dispatched for one subsidy application as a
 * read-only HTML table.
 *
 * <p>Drives the {@code notification_timeline} widget in
 * {@link MetaScreenElement#synthesiseField}. Same shape as
 * {@link EligibilitySummaryRenderer} and
 * {@link global.govstack.regbb.engine.budget.BudgetHintRenderer}: a single
 * SELECT against {@code app_fd_notification_queue}, formatted into a
 * card that visually matches the existing operator-decision panels.
 *
 * <p>Filters by {@code c_correlationId}, which the dispatcher writes as
 * {@code "AP-" + first 8 chars of application UUID uppercased}
 * (see {@code EligibilityProcessingWorker.fireApplicationSubmittedEmail}
 * and friends). The renderer derives the same code from the application
 * id passed in and queries on equality.
 *
 * <p>Graceful fallbacks: empty rows render an explicit "no notifications
 * dispatched yet" panel; SQL errors render an inline diagnostic note
 * rather than aborting form rendering.
 */
public final class NotificationTimelineRenderer {

    private static final String CLASS_NAME = NotificationTimelineRenderer.class.getName();
    private static final SimpleDateFormat DISPLAY_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private NotificationTimelineRenderer() { /* helpers only */ }

    /** Operator-form panel: same border / heading shape as the Eligibility
     *  outcome and Budget impact cards on the Operator Decision tab. */
    public static String renderForForm(String applicationId) {
        String inner = render(applicationId);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"")
          .append("border:1px solid #d0d0e8;background:#f6f6fc;border-radius:6px;")
          .append("padding:12px 16px;margin:8px 0;\">");
        sb.append("<div style=\"font-weight:600;color:#1a1a4a;margin-bottom:6px;font-size:0.9em;\">")
          .append("Notifications sent for this application")
          .append("</div>");
        sb.append(inner);
        sb.append("</div>");
        return sb.toString();
    }

    /** Build just the inner HTML (no card chrome). Useful if the panel ever
     *  gets embedded in something other than the standard card. */
    public static String render(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            return placeholder("Application not yet saved — notifications will appear once it has been submitted.");
        }
        String correlationId = deriveCorrelationId(applicationId);
        List<Row> rows;
        try {
            rows = loadRows(correlationId);
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "loadRows failed for " + applicationId + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return placeholder("Could not load notifications: "
                    + e.getClass().getSimpleName());
        }
        if (rows.isEmpty()) {
            return placeholder("No notifications dispatched for this application yet.");
        }
        return renderTable(rows);
    }

    /** Mirror of EligibilityProcessingWorker's appCode derivation. */
    static String deriveCorrelationId(String applicationId) {
        if (applicationId == null || applicationId.length() < 8) return "AP-UNKNOWN";
        return "AP-" + applicationId.substring(0, 8).toUpperCase();
    }

    private static String placeholder(String msg) {
        return "<div style=\"color:#666;font-style:italic;font-size:0.9em;padding:4px 0;\">"
                + escape(msg) + "</div>";
    }

    private static String renderTable(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:0.85em;\">");
        sb.append("<thead><tr style=\"background:#eef0fa;\">");
        for (String h : new String[]{"When","Event","Ch","Status","Backend","Sent To","Reason","Subject"}) {
            sb.append("<th style=\"text-align:left;padding:4px 8px;border-bottom:1px solid #c0c8e0;\">")
              .append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (Row r : rows) {
            sb.append("<tr>");
            cell(sb, r.when_at);
            cell(sb, r.event_code);
            cell(sb, r.channel);
            cellStatus(sb, r.status);
            cell(sb, r.backend);
            cell(sb, r.actual);
            cell(sb, r.intended_status);
            cell(sb, r.subject);
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static void cell(StringBuilder sb, String v) {
        sb.append("<td style=\"padding:4px 8px;border-bottom:1px solid #eef0fa;vertical-align:top;\">")
          .append(escape(v == null ? "" : v))
          .append("</td>");
    }

    private static void cellStatus(StringBuilder sb, String v) {
        String s = v == null ? "" : v;
        String colour = "#666";
        if ("sent".equalsIgnoreCase(s))         colour = "#2E7D32";
        else if ("failed".equalsIgnoreCase(s))  colour = "#C62828";
        else if ("dead_letter".equalsIgnoreCase(s)) colour = "#6A1B9A";
        else if ("pending".equalsIgnoreCase(s)) colour = "#F9A825";
        else if ("skipped".equalsIgnoreCase(s)) colour = "#90A4AE";
        sb.append("<td style=\"padding:4px 8px;border-bottom:1px solid #eef0fa;vertical-align:top;color:")
          .append(colour).append(";font-weight:600;\">")
          .append(escape(s)).append("</td>");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static List<Row> loadRows(String correlationId) throws Exception {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        String sql = "SELECT datecreated, c_eventCode, c_channel, c_status, c_backend, "
                   + "       c_actualRecipient, c_intendedRecipientStatus, c_subject "
                   + "  FROM app_fd_notification_queue "
                   + " WHERE c_correlationId = ? "
                   + " ORDER BY datecreated DESC "
                   + " LIMIT 50";
        List<Row> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row r = new Row();
                    Timestamp ts = rs.getTimestamp(1);
                    r.when_at = ts == null ? "" : DISPLAY_FMT.format(ts);
                    r.event_code      = rs.getString(2);
                    r.channel         = rs.getString(3);
                    r.status          = rs.getString(4);
                    r.backend         = rs.getString(5);
                    r.actual          = rs.getString(6);
                    r.intended_status = rs.getString(7);
                    r.subject         = rs.getString(8);
                    out.add(r);
                }
            }
        }
        return out;
    }

    private static final class Row {
        String when_at;
        String event_code;
        String channel;
        String status;
        String backend;
        String actual;
        String intended_status;
        String subject;
    }
}
