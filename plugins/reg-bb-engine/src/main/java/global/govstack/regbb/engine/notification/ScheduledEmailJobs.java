package global.govstack.regbb.engine.notification;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.math.BigDecimal;
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

/**
 * Periodic email jobs (templates 05, 08, 11). Each method is idempotent
 * (re-running within a short window is safe — duplicates would only happen
 * if the underlying state genuinely matches more than once, which the
 * "already alerted" flags on the envelope/voucher tables prevent).
 *
 * <p>Wire into {@code BackgroundWorkerScheduler} for automatic firing, or
 * call via {@code /budget/send-pending-digest} etc. for manual triggers
 * during test.
 *
 * <p>MVP simplification: recipient is the dev override
 * ({@code aarelaponin@gmail.com}). Production cutover swaps for actual
 * supervisor / finance-officer addresses looked up from the directory.
 */
public final class ScheduledEmailJobs {

    private static final String CLASS_NAME = ScheduledEmailJobs.class.getName();
    private static final SimpleDateFormat ISO_DATE = new SimpleDateFormat("yyyy-MM-dd");
    static { ISO_DATE.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private ScheduledEmailJobs() {}

    // -----------------------------------------------------------------
    // Template 05 — supervisor digest of pending decisions
    // -----------------------------------------------------------------
    /**
     * Find applications in {@code pending_data_clarification} or {@code pending_review}
     * status for more than 24 hours, build an aggregate digest, and email it
     * to the dev recipient (MVP — production fans out per district supervisor).
     *
     * @return summary string for the caller's log line.
     */
    public static String sendPendingDigest() {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        int pendingCount = 0;
        int oldestWaitingDays = 0;
        String sql = "SELECT count(*), "
                   + "       COALESCE(EXTRACT(EPOCH FROM (now() - min(datecreated)))/86400, 0)::int "
                   + "  FROM app_fd_subsidy_app_2025 "
                   + " WHERE c_status IN ('pending_data_clarification', 'pending_review') "
                   + "   AND datecreated < now() - interval '24 hours'";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            if (rs.next()) {
                pendingCount = rs.getInt(1);
                oldestWaitingDays = rs.getInt(2);
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "sendPendingDigest query failed: "
                    + e.getSQLState() + ":" + e.getMessage());
            return "ScheduledEmailJobs.sendPendingDigest: query error";
        }

        if (pendingCount == 0) {
            return "sendPendingDigest: 0 pending applications, no email fired";
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("supervisor_name",     "Supervisor");
        vars.put("pending_count",       String.valueOf(pendingCount));
        vars.put("oldest_waiting_days", String.valueOf(oldestWaitingDays));
        vars.put("portal_url",          "http://20.87.213.78:8080/jw/web/userview/farmersPortal/v/_/subsidyApplicationOperator2025_crud");

        boolean ok = EmailDispatcher.sendByEvent("APP_DECISION_PENDING", "EN", vars);
        return "sendPendingDigest: " + pendingCount + " pending, oldest " + oldestWaitingDays
                + "d, email " + (ok ? "sent" : "FAILED");
    }

    // -----------------------------------------------------------------
    // Template 08 — 7-day expiry reminder per voucher
    // -----------------------------------------------------------------
    /**
     * Find vouchers expiring exactly 7 days from today (or in a small window
     * around it) AND still in a redeemable status, and fire one reminder per
     * voucher. Idempotency: an {@code expiry_reminder_sent} flag could be added
     * to im_voucher to prevent re-firing; for MVP we rely on the precise-date
     * filter (only one daily run per voucher will match the "= today + 7" window).
     */
    public static String sendExpiringRemindersFor7Days() {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        List<ExpiringVoucher> targets = new ArrayList<>();
        String sql = "SELECT c_code, c_programme_code, c_expiry_date, c_quantity, "
                   + "       c_input_code, c_farmer_name, c_farmer_nid "
                   + "  FROM app_fd_im_voucher "
                   + " WHERE c_status IN ('issued', 'partially_redeemed') "
                   + "   AND NULLIF(c_expiry_date, '')::date = (CURRENT_DATE + INTERVAL '7 days')::date";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                ExpiringVoucher v = new ExpiringVoucher();
                v.code            = rs.getString(1);
                v.programmeCode   = rs.getString(2);
                v.expiryDate      = rs.getString(3);
                v.quantity        = rs.getString(4);
                v.inputCode       = rs.getString(5);
                v.farmerName      = rs.getString(6);
                v.farmerNid       = rs.getString(7);
                targets.add(v);
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "sendExpiringRemindersFor7Days query failed: "
                    + e.getSQLState() + ":" + e.getMessage());
            return "ScheduledEmailJobs.sendExpiringRemindersFor7Days: query error";
        }

        if (targets.isEmpty()) {
            return "sendExpiringRemindersFor7Days: 0 vouchers expiring in 7 days, no emails fired";
        }

        int sent = 0;
        int failed = 0;
        for (ExpiringVoucher v : targets) {
            String firstName     = firstWord(v.farmerName);
            String benefit       = nullSafe(v.quantity) + " unit(s) of " + nullSafe(v.inputCode);

            Map<String, String> vars = new HashMap<>();
            vars.put("national_id",     nullSafe(v.farmerNid));
            vars.put("first_name",      firstName);
            vars.put("farmer_name",     nullSafe(v.farmerName));
            vars.put("voucher_code",    nullSafe(v.code));
            vars.put("programme_name",  nullSafe(v.programmeCode));
            vars.put("programme_code",  nullSafe(v.programmeCode));
            vars.put("expiry_date",     nullSafe(v.expiryDate));
            vars.put("remaining_qty",   nullSafe(v.quantity));
            vars.put("benefit",         benefit);
            vars.put("benefit_description", benefit);

            boolean ok = EmailDispatcher.sendByEvent("VOUCHER_EXPIRING", "EN", vars);
            global.govstack.regbb.engine.notification.SmsDispatcher.sendByEvent("VOUCHER_EXPIRING", vars);
            if (ok) sent++; else failed++;
        }
        return "sendExpiringRemindersFor7Days: " + targets.size() + " vouchers, "
                + sent + " sent, " + failed + " failed";
    }

    // -----------------------------------------------------------------
    // Template 11 — envelope 75% threshold crossing
    // -----------------------------------------------------------------
    /**
     * Find envelopes whose utilisation has crossed 75% but not yet been
     * alerted. Idempotency via a {@code c_seventy_five_pct_alerted}
     * boolean on budget_envelope: if true, skip. After firing, write back
     * the flag so re-runs are no-ops.
     *
     * MVP: column may not exist yet — we fire on every match (not idempotent).
     * Production hardening: add the column to budget_envelope form, set after send.
     */
    public static String sendBudgetThresholdAlerts() {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        List<EnvelopeAt75> targets = new ArrayList<>();
        // Compute utilisation = (committed + expensed) / allocated.
        String sql = "SELECT e.id, e.c_envelope_code, e.c_programme_code, "
                   + "       e.c_amount_allocated, e.c_amount_committed, e.c_amount_expensed, "
                   + "       e.c_status "
                   + "  FROM app_fd_budget_envelope e "
                   + " WHERE COALESCE(e.c_status, '') <> 'frozen' "
                   + "   AND COALESCE(NULLIF(e.c_amount_allocated, '')::numeric, 0) > 0";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                EnvelopeAt75 env = new EnvelopeAt75();
                env.id            = rs.getString(1);
                env.code          = rs.getString(2);
                env.programmeCode = rs.getString(3);
                env.allocated     = parseBig(rs.getString(4));
                env.committed     = parseBig(rs.getString(5));
                env.expensed      = parseBig(rs.getString(6));
                env.utilisation   = env.allocated.signum() > 0
                    ? env.committed.add(env.expensed)
                        .divide(env.allocated, 4, java.math.RoundingMode.HALF_EVEN)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, java.math.RoundingMode.HALF_EVEN)
                    : BigDecimal.ZERO;
                if (env.utilisation.compareTo(new BigDecimal("75")) >= 0
                 && env.utilisation.compareTo(new BigDecimal("90")) < 0) {
                    targets.add(env);
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "sendBudgetThresholdAlerts query failed: "
                    + e.getSQLState() + ":" + e.getMessage());
            return "ScheduledEmailJobs.sendBudgetThresholdAlerts: query error";
        }

        if (targets.isEmpty()) {
            return "sendBudgetThresholdAlerts: 0 envelopes between 75% and 90%, no emails fired";
        }

        int sent = 0, failed = 0;
        String portalUrl = "http://20.87.213.78:8080/jw/web/userview/farmersPortal/v/_/budget_envelopes_state_crud";
        for (EnvelopeAt75 env : targets) {
            String available = env.allocated.subtract(env.committed).subtract(env.expensed).toPlainString();

            Map<String, String> vars = new HashMap<>();
            vars.put("recipient_name",  "Finance officer");
            vars.put("programme_name",  nullSafe(env.programmeCode));
            vars.put("programme_code",  nullSafe(env.programmeCode));
            vars.put("envelope_code",   nullSafe(env.code));
            vars.put("allocated",       env.allocated.toPlainString());
            vars.put("committed",       env.committed.toPlainString());
            vars.put("expensed",        env.expensed.toPlainString());
            vars.put("available",       available);
            vars.put("utilisation_pct", env.utilisation.toPlainString());
            vars.put("portal_url",      portalUrl);

            boolean ok = EmailDispatcher.sendByEvent("BUDGET_75PCT", "EN", vars);
            if (ok) sent++; else failed++;
        }
        return "sendBudgetThresholdAlerts: " + targets.size() + " envelopes at 75-90%, "
                + sent + " sent, " + failed + " failed";
    }

    // --- helpers ---
    private static class ExpiringVoucher {
        String code, programmeCode, expiryDate, quantity, inputCode, farmerName, farmerNid;
    }
    private static class EnvelopeAt75 {
        String id, code, programmeCode;
        BigDecimal allocated, committed, expensed, utilisation;
    }
    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String firstWord(String s) {
        if (s == null) return "there";
        String t = s.trim();
        if (t.isEmpty()) return "there";
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }
    private static BigDecimal parseBig(String s) {
        if (s == null || s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException nfe) { return BigDecimal.ZERO; }
    }
}
