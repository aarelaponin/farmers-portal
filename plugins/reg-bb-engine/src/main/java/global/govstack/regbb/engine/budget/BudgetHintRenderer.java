package global.govstack.regbb.engine.budget;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * L3-1 3a — shared budget-hint renderer.
 *
 * <p>The same panel needs to appear on the operator inbox datalist
 * (via {@link BudgetHintFormatter}) AND on the operator decision form
 * (via {@code MetaScreenElement} synthesising a {@code budget_hint}
 * widget). This helper keeps the rendering logic in one place so
 * both surfaces give the operator the identical view.
 *
 * <p>Read-only — single SELECT per call against
 * {@code budget_projection} + lookup against
 * {@code app_fd_mm_determinant}. HARD-RULE-compliant.
 */
public final class BudgetHintRenderer {

    private static final String CLASS_NAME = BudgetHintRenderer.class.getName();

    private BudgetHintRenderer() { /* helpers only */ }

    /**
     * Render the budget hint HTML for the given programme. Returns a
     * graceful fallback ("budget hint unavailable") if any lookup fails;
     * never throws.
     */
    public static String render(String programmeCode) {
        try {
            if (programmeCode == null || programmeCode.isEmpty()) {
                return small("no programme");
            }
            String envelopeCode = resolveEnvelope(programmeCode);
            ProjectionState state = readProjection(envelopeCode);
            if (state == null) {
                return small("envelope not found");
            }
            BigDecimal perApplicant = readPerApplicantAmount(programmeCode);
            if (perApplicant == null) {
                return small("no benefit amount rule");
            }

            BigDecimal availableAfter = state.available.subtract(perApplicant)
                    .setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal utilisationAfter;
            if (state.allocated.signum() == 0) {
                utilisationAfter = BigDecimal.ZERO;
            } else {
                BigDecimal used = state.allocated.subtract(availableAfter);
                utilisationAfter = used.multiply(BigDecimal.valueOf(100))
                        .divide(state.allocated, 1, RoundingMode.HALF_EVEN);
            }
            return renderPanel(perApplicant, availableAfter, state.allocated,
                    utilisationAfter, state.currency);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "render failed: " + t.getClass().getSimpleName()
                    + ":" + t.getMessage());
            return "<span style=\"color:#c2a000;font-size:0.85em;\">budget hint unavailable</span>";
        }
    }

    /**
     * Same as {@link #render(String)} but wrapped in a larger panel
     * suitable for use inside a form (more padding, an explanatory
     * heading, full width). Used by the form-side widget.
     */
    public static String renderForForm(String programmeCode) {
        String inner = render(programmeCode);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"")
          .append("border:1px solid #d0e0e8;background:#f4fafd;border-radius:6px;")
          .append("padding:12px 16px;margin:8px 0;\">");
        sb.append("<div style=\"font-weight:600;color:#1a4040;margin-bottom:6px;font-size:0.9em;\">")
          .append("Budget impact of approving this application")
          .append("</div>");
        sb.append(inner);
        sb.append("</div>");
        return sb.toString();
    }

    // -----------------------------------------------------------------
    //  Internals
    // -----------------------------------------------------------------

    private static String small(String msg) {
        return "<span style=\"color:#888;font-size:0.85em;\">" + msg + "</span>";
    }

    /** Same naming convention as BudgetEngine. Hardcoded for the 2025/26
     *  cycle; future cycles need a fiscal-year resolution layer. */
    private static String resolveEnvelope(String programmeCode) {
        if (programmeCode == null || programmeCode.isEmpty()) return null;
        String[] parts = programmeCode.split("_");
        if (parts.length >= 3) {
            try {
                int year = Integer.parseInt(parts[1]);
                int next = year + 1;
                String fy = "FY" + (year % 100) + (next % 100);
                return "ENV_" + programmeCode + "_" + fy;
            } catch (NumberFormatException ignore) {}
        }
        return "ENV_" + programmeCode + "_FY2526";
    }

    private static final class ProjectionState {
        BigDecimal allocated;
        BigDecimal available;
        BigDecimal preCommitted;
        String currency = "LSL";
    }

    private static ProjectionState readProjection(String envelopeCode) throws SQLException {
        String sql = "SELECT bp.allocated, bp.available, bp.pre_committed, "
                   + "       env.c_currency "
                   + "  FROM budget_projection bp "
                   + "  LEFT JOIN app_fd_budget_envelope env "
                   + "         ON env.c_code = bp.envelope_code "
                   + " WHERE bp.envelope_code = ?";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, envelopeCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    ProjectionState s = new ProjectionState();
                    s.allocated    = nz(rs.getBigDecimal(1));
                    s.available    = nz(rs.getBigDecimal(2));
                    s.preCommitted = nz(rs.getBigDecimal(3));
                    String cur = rs.getString(4);
                    if (cur != null && !cur.isEmpty()) s.currency = cur;
                    return s;
                }
            }
        }
        return null;
    }

    private static BigDecimal readPerApplicantAmount(String programmeCode) throws SQLException {
        String sql = "SELECT c_targetvalue FROM app_fd_mm_determinant WHERE c_code = ?";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, "BENEFIT_AMOUNT_" + programmeCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    if (s == null || s.isEmpty()) return null;
                    try { return new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_EVEN); }
                    catch (NumberFormatException nfe) { return null; }
                }
            }
        }
        return null;
    }

    private static String renderPanel(BigDecimal perApplicant, BigDecimal availableAfter,
                                      BigDecimal allocated, BigDecimal utilisationPct,
                                      String currency) {
        String barColour;
        if (utilisationPct.compareTo(BigDecimal.valueOf(100)) > 0) barColour = "#c62828";
        else if (utilisationPct.compareTo(BigDecimal.valueOf(80)) > 0) barColour = "#e6a700";
        else barColour = "#2e7d32";
        BigDecimal barPct = utilisationPct.min(BigDecimal.valueOf(100));

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-size:0.85em;line-height:1.35em;min-width:220px;\">");
        sb.append("<div><b>+").append(formatAmount(perApplicant)).append(" ").append(currency)
          .append("</b> on approve</div>");
        sb.append("<div style=\"color:#555;\">After: ").append(formatAmount(availableAfter))
          .append(" / ").append(formatAmount(allocated))
          .append(" <span style=\"color:#888\">(").append(utilisationPct.toPlainString())
          .append("%)</span></div>");
        sb.append("<div style=\"background:#e8e8e8;border-radius:3px;height:6px;margin-top:4px;\">");
        sb.append("<div style=\"background:").append(barColour)
          .append(";width:").append(barPct.toPlainString())
          .append("%;height:100%;border-radius:3px;\"></div>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String formatAmount(BigDecimal v) {
        return v == null ? "—" : String.format("%,.2f", v);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
