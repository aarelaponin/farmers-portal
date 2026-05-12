package global.govstack.regbb.engine.element;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Render the engine's verdict on a subsidy application — status, eligibility
 * disposition, score, and failed-rule code — as a read-only HTML panel.
 *
 * <p>The same renderer feeds the operator decision form (via the
 * {@code eligibility_summary} widget case in
 * {@link MetaScreenElement#synthesiseField}) and the citizen-side review tab
 * (via {@link MetaScreenElement#renderReview} when a saved application
 * exists). Single SELECT against {@code app_fd_subsidy_app_2025} per call;
 * {@code c_eligibility_outcome} is parsed as JSON to recover the
 * {@code disposition}, {@code score}, and {@code failedRule} fields the
 * RegBB §6.5 submit endpoint persists there.
 *
 * <p>Disposition colour scheme matches the operator inbox list:
 * <ul>
 *   <li>{@code eligibility_passed}        — green</li>
 *   <li>{@code eligibility_failed_mandatory} — red</li>
 *   <li>{@code pending_review}            — amber</li>
 *   <li>anything else / null              — grey</li>
 * </ul>
 *
 * <p>Returns a graceful fallback panel ("not yet evaluated") when the
 * application has no eligibility outcome yet — happens during the brief
 * window between row creation and the post-save hook firing, and for any
 * row pre-dating the eligibility persistence rollout.
 */
public final class EligibilitySummaryRenderer {

    private static final String CLASS_NAME = EligibilitySummaryRenderer.class.getName();

    private EligibilitySummaryRenderer() { /* helpers only */ }

    /** Operator-form-style panel: a bordered card with a heading.
     *  Same visual weight as the budget-hint card next to it. */
    public static String renderForForm(String applicationId) {
        String inner = render(applicationId);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"")
          .append("border:1px solid #d0d0e8;background:#f6f6fc;border-radius:6px;")
          .append("padding:12px 16px;margin:8px 0;\">");
        sb.append("<div style=\"font-weight:600;color:#1a1a4a;margin-bottom:6px;font-size:0.9em;\">")
          .append("Eligibility outcome")
          .append("</div>");
        sb.append(inner);
        sb.append("</div>");
        return sb.toString();
    }

    /** Compact panel — the body content used by both surfaces. */
    public static String render(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            return small("not yet evaluated");
        }
        try {
            State s = readState(applicationId);
            if (s == null) {
                return small("application not found");
            }
            if (isEmpty(s.status) && isEmpty(s.eligibilityOutcomeJson)) {
                return small("not yet evaluated");
            }
            return renderPanel(s);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "render failed: " + t.getClass().getSimpleName()
                    + ":" + t.getMessage());
            return "<span style=\"color:#c2a000;font-size:0.85em;\">eligibility summary unavailable</span>";
        }
    }

    // -----------------------------------------------------------------
    //  Internals
    // -----------------------------------------------------------------

    private static final class State {
        String status;
        String eligibilityOutcomeJson;
    }

    private static State readState(String applicationId) throws SQLException {
        String sql = "SELECT c_status, c_eligibility_outcome "
                   + "  FROM app_fd_subsidy_app_2025 "
                   + " WHERE id = ?";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, applicationId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    State s = new State();
                    s.status = rs.getString(1);
                    s.eligibilityOutcomeJson = rs.getString(2);
                    return s;
                }
            }
        }
        return null;
    }

    private static String renderPanel(State s) {
        String disposition = null;
        String scoreStr    = null;
        String failedRule  = null;
        if (!isEmpty(s.eligibilityOutcomeJson)) {
            try {
                JSONObject jo = new JSONObject(s.eligibilityOutcomeJson);
                if (jo.has("disposition") && !jo.isNull("disposition")) {
                    disposition = String.valueOf(jo.get("disposition"));
                }
                if (jo.has("score") && !jo.isNull("score")) {
                    scoreStr = String.valueOf(jo.get("score"));
                }
                if (jo.has("failedRule") && !jo.isNull("failedRule")) {
                    failedRule = String.valueOf(jo.get("failedRule"));
                }
            } catch (Throwable t) {
                LogUtil.warn(CLASS_NAME, "eligibility_outcome JSON parse failed: "
                        + t.getClass().getSimpleName() + ":" + t.getMessage());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-size:0.85em;line-height:1.45em;min-width:240px;\">");

        // Status row
        sb.append("<div style=\"margin-bottom:4px;\">");
        sb.append("<span style=\"color:#555;\">Status:</span> ");
        sb.append(statusBadge(s.status));
        sb.append("</div>");

        // Eligibility row
        sb.append("<div style=\"margin-bottom:4px;\">");
        sb.append("<span style=\"color:#555;\">Eligibility:</span> ");
        sb.append(dispositionBadge(disposition));
        sb.append("</div>");

        // Score (only when populated)
        if (!isEmpty(scoreStr)) {
            sb.append("<div style=\"margin-bottom:4px;\">");
            sb.append("<span style=\"color:#555;\">Score:</span> ");
            sb.append("<b>").append(htmlEscape(scoreStr)).append("</b>");
            sb.append("</div>");
        }

        // Failed rule (only when populated)
        if (!isEmpty(failedRule)) {
            sb.append("<div>");
            sb.append("<span style=\"color:#555;\">Failed rule:</span> ");
            sb.append("<code style=\"background:#f4eee0;padding:1px 6px;border-radius:3px;color:#7a3d00;\">")
              .append(htmlEscape(failedRule))
              .append("</code>");
            sb.append("</div>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String statusBadge(String status) {
        if (isEmpty(status)) status = "—";
        String bg, fg;
        switch (status) {
            case "auto_approved":
            case "approved":         bg = "#e6f4ea"; fg = "#1e6b34"; break;
            case "auto_rejected":
            case "rejected":         bg = "#fdecea"; fg = "#9b2520"; break;
            case "pending_review":   bg = "#fff4e0"; fg = "#7a5d00"; break;
            case "needs_clarification": bg = "#e7f0fb"; fg = "#1a4480"; break;
            default:                 bg = "#eee";    fg = "#333";    break;
        }
        return "<span style=\"background:" + bg + ";color:" + fg
             + ";padding:2px 8px;border-radius:10px;font-weight:600;\">"
             + htmlEscape(status) + "</span>";
    }

    private static String dispositionBadge(String disposition) {
        if (isEmpty(disposition)) {
            return "<span style=\"color:#888;\">—</span>";
        }
        String fg;
        switch (disposition) {
            case "eligibility_passed":           fg = "#1e6b34"; break;
            case "eligibility_failed_mandatory": fg = "#9b2520"; break;
            case "pending_review":               fg = "#7a5d00"; break;
            default:                             fg = "#444";    break;
        }
        return "<b style=\"color:" + fg + ";\">" + htmlEscape(disposition) + "</b>";
    }

    private static String small(String msg) {
        return "<span style=\"color:#888;font-size:0.85em;\">" + msg + "</span>";
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
