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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Render the operator-only case-notes thread for one application as a
 * read-only HTML card. Drives the {@code note_thread} widget in
 * {@link MetaScreenElement#synthesiseField}.
 *
 * <p>Pattern matches {@link NotificationTimelineRenderer}: server-side
 * SELECT against {@code app_fd_case_note} (filtered by application id)
 * + an inline {@code JOIN}-equivalent in-memory lookup against
 * {@code app_fd_md_case_note_kind} to resolve kind codes to display
 * labels. No client-side JS — column labels and value resolution happen
 * at render time, so the HTML the citizen / operator browser receives
 * is already complete.
 *
 * <p>Why server-side rendering. The original v1 used Joget's marketplace
 * {@code EmbeddedDatalist} element. That element ignores the underlying
 * datalist's {@code columns[].label} and {@code formats[]}, so column
 * headers came out as form-field labels and {@code note_kind} cells
 * showed raw codes ({@code decision_rationale}) instead of friendly
 * labels ({@code Decision rationale}). We worked around it for v1 with
 * inline JS in a CustomHTML; that was a band-aid that put per-form JS
 * into hand-authored form JSON, violating the configuration-as-code
 * shape of mm_screen + mm_field. Lifting the whole thing into a kernel
 * widget keeps form authoring clean — analysts add a single
 * {@code mm_field} row with {@code widget=note_thread} and the kernel
 * synthesises a fully-rendered card.
 *
 * <p>{@link #renderForForm(String)} is the entry point called from
 * {@code MetaScreenElement.synthesiseField} when a field's widget kind
 * is {@code note_thread}. It also builds the "+ Add note" button that
 * opens the {@code case_note_add} userview menu in a new tab.
 */
public final class CaseNoteThreadRenderer {

    private static final String CLASS_NAME = CaseNoteThreadRenderer.class.getName();
    private static final SimpleDateFormat DISPLAY_FMT =
            new SimpleDateFormat("dd MMM yyyy HH:mm");

    private CaseNoteThreadRenderer() { /* helpers only */ }

    /** Operator-form card: matches the visual idiom of the eligibility
     *  outcome and budget hint panels on the operator review screen. */
    public static String renderForForm(String applicationId) {
        String inner = render(applicationId);
        String addBtn = renderAddButton(applicationId);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"")
          .append("border:1px solid #d0d0e8;background:#f6f6fc;border-radius:6px;")
          .append("padding:12px 16px;margin:8px 0;\">");
        sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;\">");
        sb.append("<div style=\"font-weight:600;color:#1a1a4a;font-size:0.95em;\">")
          .append("Operator case notes")
          .append("</div>");
        sb.append(addBtn);
        sb.append("</div>");
        sb.append(inner);
        sb.append("</div>");
        return sb.toString();
    }

    /** "+ Add note" button — disabled when there's no applicationId yet
     *  (the application hasn't been saved, so we have nothing to attach
     *  a note to). When present, opens the case_note_add userview menu
     *  in a new tab with app_id pre-filled. */
    private static String renderAddButton(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            return "<span style=\"color:#999;font-size:0.85em;font-style:italic;\">"
                 + "Save the application to enable notes</span>";
        }
        // Same-tab navigation. After save, the case_note_add menu's
        // redirectUrlOnSave brings the operator back to this application's
        // review form (with id=app_id). New-tab via window.open was
        // unreliable — Firefox / browser popup-blockers downgrade _blank
        // to same-tab silently, which then breaks the opener.refresh()
        // pattern and strands the operator on the CRUD list page.
        StringBuilder sb = new StringBuilder();
        sb.append("<a href=\"/jw/web/userview/farmersPortal/v/_/case_note_add?_mode=add&amp;app_id=")
          .append(escape(applicationId))
          .append("\" ")
          .append("style=\"display:inline-flex;align-items:center;padding:8px 14px;")
          .append("background:#3388ff;color:#fff;border-radius:4px;text-decoration:none;")
          .append("font-weight:600;font-size:0.85em;min-height:34px;\">")
          .append("+ Add note")
          .append("</a>");
        return sb.toString();
    }

    /** Build just the inner HTML (table or placeholder, no card chrome). */
    public static String render(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            return placeholder("This application has not been saved yet — notes can be added after first save.");
        }
        Map<String, String> kindLabels;
        List<Row> rows;
        try {
            kindLabels = loadKindLabels();
            rows = loadRows(applicationId);
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "loadRows failed for " + applicationId + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return placeholder("Could not load case notes: " + e.getClass().getSimpleName());
        }
        if (rows.isEmpty()) {
            return placeholder("No notes yet. Click “+ Add note” above to record one.");
        }
        return renderTable(rows, kindLabels);
    }

    private static String placeholder(String msg) {
        return "<div style=\"color:#666;font-style:italic;font-size:0.9em;padding:6px 0;\">"
                + escape(msg) + "</div>";
    }

    private static String renderTable(List<Row> rows, Map<String, String> kindLabels) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:0.85em;\">");
        sb.append("<thead><tr style=\"background:#eef0fa;\">");
        for (String h : new String[]{"When", "By", "Kind", "Note"}) {
            sb.append("<th style=\"text-align:left;padding:4px 8px;border-bottom:1px solid #c0c8e0;\">")
              .append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (Row r : rows) {
            sb.append("<tr>");
            cell(sb, r.when_at, "white-space:nowrap;");
            cell(sb, r.author, "white-space:nowrap;");
            // Kind: resolve code to label
            String kindLabel = kindLabels.getOrDefault(r.note_kind == null ? "" : r.note_kind,
                                                       r.note_kind == null ? "" : r.note_kind);
            cell(sb, kindLabel, "");
            cell(sb, r.note_text, "white-space:pre-wrap;");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static void cell(StringBuilder sb, String v, String extraStyle) {
        sb.append("<td style=\"padding:4px 8px;border-bottom:1px solid #eef0fa;vertical-align:top;")
          .append(extraStyle == null ? "" : extraStyle)
          .append("\">")
          .append(escape(v == null ? "" : v))
          .append("</td>");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Load every kind code → display name mapping from md_case_note_kind.
     *  Tiny lookup table (5-10 rows) so it's cheaper to fetch all than
     *  parameterised per-row joins. */
    private static Map<String, String> loadKindLabels() throws Exception {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        Map<String, String> out = new HashMap<>();
        String sql = "SELECT c_code, c_name FROM app_fd_md_case_note_kind";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString(1);
                String name = rs.getString(2);
                if (code != null && !code.isEmpty()) out.put(code, name);
            }
        }
        return out;
    }

    private static List<Row> loadRows(String applicationId) throws Exception {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        String sql = "SELECT c_created_at, c_author, c_note_kind, c_note_text "
                   + "  FROM app_fd_case_note "
                   + " WHERE c_application_id = ? "
                   + " ORDER BY c_created_at DESC NULLS LAST, datecreated DESC NULLS LAST "
                   + " LIMIT 200";
        List<Row> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row r = new Row();
                    r.when_at   = nz(rs.getString(1));
                    r.author    = nz(rs.getString(2));
                    r.note_kind = nz(rs.getString(3));
                    r.note_text = nz(rs.getString(4));
                    out.add(r);
                }
            }
        }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static final class Row {
        String when_at;
        String author;
        String note_kind;
        String note_text;
    }
}
