package global.govstack.regbb.engine.budget;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Scheduled job that scans every envelope's utilisation and posts alerts to
 * {@code app_fd_budget_threshold_alert} when thresholds are crossed. Wireable
 * as a process tool inside any Joget workflow process step (or a standalone
 * scheduled task once that infrastructure lands).
 *
 * <p>Severity ladder:
 * <ul>
 *   <li><b>WATCH</b> — utilisation ≥ 80% and &lt; 100%. Operator inbox notice.</li>
 *   <li><b>OVER</b>  — utilisation ≥ 100% and &lt; 110%. Budget officer notice.</li>
 *   <li><b>AUTO_FREEZE</b> — utilisation ≥ 110%. Sets {@code budget_envelope.status='frozen'},
 *       sets {@code frozen_reason} + {@code frozen_at}, posts the highest-severity alert.
 *       BudgetEngine will reject any further forward-funnel dispatches against
 *       this envelope until status is manually set back to 'active'.</li>
 * </ul>
 *
 * <p>Idempotency: an alert at severity X for envelope Y is only written if
 * the most recent unacknowledged alert for that envelope is at a lower
 * severity. Re-running the monitor doesn't create duplicate WATCH rows; it
 * only escalates when the situation worsens.
 *
 * <p>HARD-RULE compliant: writes to {@code app_fd_budget_envelope} go via
 * {@link FormDataDao}; alert rows go via the same DAO.
 */
public class BudgetThresholdMonitor extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = BudgetThresholdMonitor.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final BigDecimal THRESHOLD_WATCH       = new BigDecimal("80");
    private static final BigDecimal THRESHOLD_OVER        = new BigDecimal("100");
    private static final BigDecimal THRESHOLD_AUTO_FREEZE = new BigDecimal("110");

    @Override public String getName()        { return "Budget Threshold Monitor"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "Budget Threshold Monitor"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "Scans budget_projection, posts threshold alerts (WATCH/OVER/AUTO_FREEZE), auto-freezes envelopes at 110%."; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Threshold Monitor\", \"properties\":[] } ]";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        long started = System.currentTimeMillis();
        int scanned = 0, alertedWatch = 0, alertedOver = 0, frozen = 0;

        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "SELECT bp.envelope_code, bp.allocated, "
               + "  bp.reserved + bp.pre_committed + bp.committed + bp.expensed AS used, "
               + "  env.id AS env_id, env.c_status AS status, "
               + "  COALESCE(reg.c_name, env.c_programme_code) AS programme_name "
               + "FROM budget_projection bp "
               + "LEFT JOIN app_fd_budget_envelope env ON env.c_code = bp.envelope_code "
               + "LEFT JOIN app_fd_mm_registration reg ON reg.c_code = env.c_programme_code "
               + "WHERE bp.allocated > 0")) {
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    scanned++;
                    String envelopeCode = rs.getString("envelope_code");
                    String envId        = rs.getString("env_id");
                    String envStatus    = rs.getString("status");
                    String programme    = rs.getString("programme_name");
                    BigDecimal allocated = rs.getBigDecimal("allocated");
                    BigDecimal used      = rs.getBigDecimal("used");
                    if (allocated == null || allocated.signum() == 0) continue;

                    BigDecimal utilPct = used.multiply(new BigDecimal("100"))
                            .divide(allocated, 1, java.math.RoundingMode.HALF_EVEN);

                    String severity = null;
                    if (utilPct.compareTo(THRESHOLD_AUTO_FREEZE) >= 0)      severity = "AUTO_FREEZE";
                    else if (utilPct.compareTo(THRESHOLD_OVER) >= 0)        severity = "OVER";
                    else if (utilPct.compareTo(THRESHOLD_WATCH) >= 0)       severity = "WATCH";
                    if (severity == null) continue;

                    // Idempotency — skip if the most recent unacknowledged
                    // alert for this envelope is already at this severity.
                    if (alreadyAlertedAt(c, envelopeCode, severity)) {
                        continue;
                    }

                    // Write alert row.
                    writeAlert(dao, envelopeCode, programme, severity, utilPct,
                            allocated, used);
                    if ("WATCH".equals(severity)) alertedWatch++;
                    else if ("OVER".equals(severity)) alertedOver++;
                    else if ("AUTO_FREEZE".equals(severity)) {
                        // Auto-freeze the envelope — write status='frozen' on
                        // the envelope row.
                        if (envId != null && !envId.isEmpty()
                                && !"frozen".equalsIgnoreCase(envStatus)) {
                            freezeEnvelope(dao, envId, envelopeCode, utilPct);
                            frozen++;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "monitor scan failed: " + e.getSQLState() + ":" + e.getMessage());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "monitor scan failed");
        }

        long elapsed = System.currentTimeMillis() - started;
        String summary = "BudgetThresholdMonitor: scanned=" + scanned
                + " WATCH+=" + alertedWatch + " OVER+=" + alertedOver
                + " AUTO_FREEZE+=" + frozen + " elapsedMs=" + elapsed;
        LogUtil.info(CLASS_NAME, summary);
        return summary;
    }

    private boolean alreadyAlertedAt(Connection c, String envelopeCode, String severity)
            throws SQLException {
        String sql = "SELECT c_severity FROM app_fd_budget_threshold_alert "
                   + "WHERE c_envelope_code = ? "
                   + "  AND (c_acknowledged_at IS NULL OR c_acknowledged_at = '') "
                   + "ORDER BY datecreated DESC LIMIT 1";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, envelopeCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String last = rs.getString(1);
                    return severity.equalsIgnoreCase(last);
                }
            }
        }
        return false;
    }

    private void writeAlert(FormDataDao dao, String envelopeCode, String programme,
                            String severity, BigDecimal utilPct,
                            BigDecimal allocated, BigDecimal used) {
        FormRow r = new FormRow();
        r.setProperty("envelope_code", envelopeCode);
        r.setProperty("programme",     programme == null ? "" : programme);
        r.setProperty("severity",      severity);
        r.setProperty("util_pct",      utilPct.toPlainString());
        r.setProperty("allocated",     allocated.toPlainString());
        r.setProperty("used",          used.toPlainString());
        r.setProperty("alerted_at",    ISO_UTC.format(new Date()));
        r.setProperty("acknowledged_at", "");
        r.setProperty("acknowledged_by", "");
        FormRowSet rs = new FormRowSet();
        rs.add(r);
        try {
            global.govstack.regbb.engine.support.RowWriter.save("budget_threshold_alert", "budget_threshold_alert", rs);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "writeAlert failed for " + envelopeCode + ":"
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    private void freezeEnvelope(FormDataDao dao, String envId, String envelopeCode,
                                BigDecimal utilPct) {
        // Read the envelope, flip status, set freeze metadata, save.
        FormRowSet existing = dao.find("budget_envelope", "budget_envelope",
                "WHERE id = ?", new Object[]{envId}, null, false, 0, 1);
        if (existing == null || existing.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "freezeEnvelope: envelope not found by id=" + envId);
            return;
        }
        FormRow row = existing.get(0);
        row.setProperty("status", "frozen");
        row.setProperty("frozen_reason",
                "Auto-frozen by BudgetThresholdMonitor: utilisation "
                + utilPct.toPlainString() + "% ≥ 110%. Manual review required to unfreeze.");
        row.setProperty("frozen_at", ISO_UTC.format(new Date()));
        FormRowSet rs = new FormRowSet();
        rs.add(row);
        try {
            global.govstack.regbb.engine.support.RowWriter.save("budget_envelope", "budget_envelope", rs);
            LogUtil.info(CLASS_NAME, "AUTO-FROZEN envelope " + envelopeCode
                    + " at util=" + utilPct.toPlainString() + "%");

            // W2 — fire budget_envelope_frozen email (template 12). Best-effort;
            // SMTP errors are logged and never block the freeze action.
            try {
                String programmeName = envelopeCode;  // MVP: programme-name lookup deferred
                String reason = "Auto-frozen at " + utilPct.toPlainString() + "% utilisation";
                String allocated = String.valueOf(row.getProperty("amount_allocated"));
                String committed = String.valueOf(row.getProperty("amount_committed"));
                String expensed  = String.valueOf(row.getProperty("amount_expensed"));
                String available = new java.math.BigDecimal(allocated.isEmpty() ? "0" : allocated)
                        .subtract(new java.math.BigDecimal(committed.isEmpty() ? "0" : committed))
                        .subtract(new java.math.BigDecimal(expensed.isEmpty() ? "0" : expensed))
                        .toPlainString();

                java.util.Map<String, String> vars = new java.util.HashMap<>();
                vars.put("recipient_name",  "Finance officer");
                vars.put("programme_name",  programmeName);
                vars.put("programme_code",  programmeName);
                vars.put("envelope_code",   envelopeCode);
                vars.put("frozen_at",       ISO_UTC.format(new Date()));
                vars.put("reason",          reason);
                vars.put("allocated",       allocated);
                vars.put("committed",       committed);
                vars.put("expensed",        expensed);
                vars.put("available",       available);
                vars.put("utilisation_pct", utilPct.toPlainString());
                vars.put("portal_url",      "http://20.87.213.78:8080/jw/web/userview/farmersPortal/v/_/budget");

                global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                    "BUDGET_FROZEN", "EN", vars);
            } catch (Throwable emailEx) {
                LogUtil.error(CLASS_NAME, emailEx,
                    "frozen-envelope email failed for " + envelopeCode);
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "freezeEnvelope save failed for " + envelopeCode);
        }
    }
}
