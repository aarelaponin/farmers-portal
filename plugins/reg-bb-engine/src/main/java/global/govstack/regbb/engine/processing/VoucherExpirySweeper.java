package global.govstack.regbb.engine.processing;

import global.govstack.regbb.engine.budget.BudgetEngine;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * IM Phase 3 Slice 9 — Voucher Expiry Sweeper.
 *
 * <p>Production hardening: a voucher is issued with an {@code expiry_date}
 * (typically issuance date + 90 days). If the farmer doesn't redeem before
 * that date, the voucher should be marked {@code expired} AND the budget
 * COMMITMENT posted at issue should be released back to the envelope's
 * AVAILABLE balance. Without this, expired-but-still-COMMITTED vouchers
 * silently consume budget capacity forever.
 *
 * <p>Pipeline per sweep:
 * <ol>
 *   <li>SELECT vouchers WHERE {@code c_status = 'issued'} AND
 *       {@code c_expiry_date::date < CURRENT_DATE}.</li>
 *   <li>For each, look up the original COMMITMENT event by idempotency key
 *       {@code voucher_issued:<code>} to recover the exact amount that was
 *       posted (robust against price drift since issuance).</li>
 *   <li>Dispatch {@code RELEASE_COMMITMENT} via {@link BudgetEngine#dispatchDirect}
 *       with idempotency key {@code voucher_expired:<code>}. The Budget
 *       Engine's RELEASE_COMMITMENT shape is {@code AVAILABLE += amount;
 *       COMMITTED -= amount} (source-verified BudgetEngine.composeEnvelopeJournal).</li>
 *   <li>Flip voucher status: {@code issued} → {@code expired}, write
 *       {@code expired_at} timestamp, append to {@code notes}.</li>
 * </ol>
 *
 * <p>Idempotent: re-running the sweep on the same expired voucher set
 * returns {@code no_op_idempotent} for each (already-expired vouchers are
 * filtered out in step 1, but even if one slipped through the dispatchDirect
 * deduplicates by idempotency key).
 *
 * <p>Failure handling: if the COMMITMENT lookup fails (e.g. the original
 * event row was deleted or the idempotency key drifted), the voucher is
 * still flipped to {@code expired} but the budget release is skipped with a
 * loud log entry. The voucher's legal status is the source of truth; the
 * financial record can be reconciled later by manual adjustment.
 *
 * <p>HARD-RULE compliant: voucher write goes through {@link FormDataDao};
 * budget release goes through {@link BudgetEngine}; lookups are SELECT.
 *
 * <p>Wiring options:
 * <ul>
 *   <li><b>Scheduled</b> — drop into a Joget workflow process tool step
 *       that runs daily at 02:00 (or hourly during the redemption season).</li>
 *   <li><b>Ad-hoc</b> — POST {@code /api/budget/expire-vouchers} for an
 *       operator-triggered sweep (e.g. after a bulk programme close-out).</li>
 * </ul>
 */
public class VoucherExpirySweeper extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = VoucherExpirySweeper.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static final String FORM_VOUCHER = "im_voucher";

    @Override public String getName()        { return "Voucher Expiry Sweeper"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "IM Slice 9 — flips expired vouchers to status=expired and releases their budget COMMITMENT."; }
    @Override public String getLabel()       { return "Voucher Expiry Sweeper"; }
    @Override public String getClassName()   { return CLASS_NAME; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Voucher Expiry Sweeper\", \"properties\":[] } ]";
    }

    /** Result envelope for callers (the REST endpoint serialises it to JSON). */
    public static class Result {
        public String status;        // "ok" | "error"
        public String message;
        public int    scanned;       // total expired vouchers found
        public int    flipped;       // status flipped to 'expired'
        public int    released;      // budget COMMITMENT successfully released
        public int    releaseSkipped; // flipped but no budget event found / dispatch errored
        public List<String> voucherCodes = new ArrayList<>();
        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"status\":\"").append(status).append("\"");
            sb.append(",\"scanned\":").append(scanned);
            sb.append(",\"flipped\":").append(flipped);
            sb.append(",\"released\":").append(released);
            sb.append(",\"releaseSkipped\":").append(releaseSkipped);
            sb.append(",\"message\":\"").append(esc(message)).append("\"");
            sb.append(",\"voucherCodes\":[");
            for (int i = 0; i < voucherCodes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(voucherCodes.get(i)).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
        private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    @Override
    public Object execute(Map props) {
        String actor = (props != null && props.get("actor") != null)
                ? String.valueOf(props.get("actor"))
                : "system:expiry-sweeper";
        Result r = sweep(actor);
        LogUtil.info(CLASS_NAME, "Sweep complete: " + r.toJson());
        return null;
    }

    /** Externally callable entry point — used by the {@code /budget/expire-vouchers}
     *  REST endpoint and by test harnesses. */
    public Result sweep(String actor) {
        Result r = new Result();
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            List<ExpiredVoucher> expired = readExpiredVouchers(ds);
            r.scanned = expired.size();
            if (expired.isEmpty()) {
                r.status = "ok";
                r.message = "No expired vouchers found.";
                return r;
            }

            BudgetEngine engine = new BudgetEngine();
            for (ExpiredVoucher v : expired) {
                r.voucherCodes.add(v.code);

                // Step A: read original COMMITMENT amount from budget_event by
                // idempotency_key. Robust against md27input price drift since
                // issuance — we release exactly what was committed.
                BigDecimal commitAmount = readCommittedAmount(ds, v.code);

                // Step B: flip voucher status (always, even if budget release fails).
                if (flipVoucherToExpired(dao, v)) {
                    r.flipped++;
                }

                // Step C: dispatch RELEASE_COMMITMENT. Skip + log if no
                // commitment was found (voucher was issued without budget
                // posting — pre-Slice-6c, or earlier dispatch failure).
                if (commitAmount == null) {
                    r.releaseSkipped++;
                    LogUtil.warn(CLASS_NAME, "Voucher " + v.code
                        + " has no COMMITMENT event with idem='voucher_issued:" + v.code
                        + "' — flipped to expired, budget release skipped");
                    continue;
                }
                if (commitAmount.signum() <= 0) {
                    r.releaseSkipped++;
                    continue;
                }
                String envelopeCode = "ENV_" + v.programmeCode + "_FY2526";
                String idem = "voucher_expired:" + v.code;
                BudgetEngine.DispatchResult dr = engine.dispatchDirect(
                        envelopeCode, "RELEASE_COMMITMENT", commitAmount, actor,
                        "voucher_expiry", v.code, idem,
                        new HashMap<>());
                if ("posted".equals(dr.status) || "no_op_idempotent".equals(dr.status)) {
                    r.released++;
                    LogUtil.info(CLASS_NAME, "Voucher " + v.code + " expired: released "
                        + commitAmount + " from " + envelopeCode + " (" + dr.status + ")");
                } else {
                    r.releaseSkipped++;
                    LogUtil.warn(CLASS_NAME, "Voucher " + v.code + " expired but RELEASE_COMMITMENT "
                        + "failed: " + dr.status + " (envelope=" + envelopeCode + ")");
                }

                // W2 — fire voucher_expired email (template 09). Best-effort.
                try {
                    String firstName = firstWord(v.farmerName);
                    String benefit = nullSafe(v.quantity) + " unit(s) of " + nullSafe(v.inputCode);

                    java.util.Map<String, String> vars = new java.util.HashMap<>();
                    vars.put("national_id",         nullSafe(v.farmerNid));
                    vars.put("first_name",          firstName);
                    vars.put("farmer_name",         nullSafe(v.farmerName));
                    vars.put("voucher_code",        v.code);
                    vars.put("programme_name",      nullSafe(v.programmeCode));
                    vars.put("program_name",        nullSafe(v.programmeCode));
                    vars.put("issued_date",         nullSafe(v.issuedDate));
                    vars.put("expiry_date",         nullSafe(v.expiryDate));
                    vars.put("unredeemed_qty",      nullSafe(v.quantity));
                    vars.put("benefit",             benefit);
                    vars.put("benefit_description", benefit);
                    vars.put("district_name",       "your local");
                    vars.put("district_phone",      "your district office");

                    global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                            "VOUCHER_EXPIRED", "EN", vars);
                    global.govstack.regbb.engine.notification.SmsDispatcher.sendByEvent(
                            "VOUCHER_EXPIRED", vars);
                } catch (Throwable emailEx) {
                    LogUtil.error(CLASS_NAME, emailEx,
                        "Email notification failed for expired voucher " + v.code);
                }
            }
            r.status = "ok";
            r.message = "Swept " + r.scanned + " expired voucher(s): "
                      + r.flipped + " flipped, " + r.released + " released, "
                      + r.releaseSkipped + " release-skipped.";
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "Voucher expiry sweep failed");
            r.status = "error";
            r.message = "internal:" + t.getClass().getSimpleName() + ":"
                      + (t.getMessage() == null ? "" : t.getMessage());
        }
        return r;
    }

    // ---------------- helpers ----------------

    private static class ExpiredVoucher {
        String id, code, programmeCode, status, expiryDate, notes;
        // W2 — fields needed for the voucher_expired email (template 09).
        String farmerName, farmerNid, inputCode, issuedDate, quantity;
    }

    private List<ExpiredVoucher> readExpiredVouchers(DataSource ds) {
        List<ExpiredVoucher> out = new ArrayList<>();
        // Slice 11: 'partially_redeemed' is also expirable — it just means
        // some of the entitlement was already collected. Sweeper releases the
        // unredeemed remainder (full COMMITMENT minus sum of EXPENSEs).
        String sql = "SELECT id, c_code, c_programme_code, c_status, c_expiry_date, c_notes, "
                   + "       c_farmer_name, c_farmer_nid, c_input_code, c_issued_date, c_quantity "
                   + "  FROM app_fd_im_voucher "
                   + " WHERE c_status IN ('issued', 'partially_redeemed') "
                   + "   AND NULLIF(c_expiry_date, '')::date < CURRENT_DATE";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                ExpiredVoucher v = new ExpiredVoucher();
                v.id            = rs.getString(1);
                v.code          = rs.getString(2);
                v.programmeCode = rs.getString(3);
                v.status        = rs.getString(4);
                v.expiryDate    = rs.getString(5);
                v.notes         = rs.getString(6);
                v.farmerName    = rs.getString(7);
                v.farmerNid     = rs.getString(8);
                v.inputCode     = rs.getString(9);
                v.issuedDate    = rs.getString(10);
                v.quantity      = rs.getString(11);
                out.add(v);
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readExpiredVouchers failed: " + e.getSQLState() + ":" + e.getMessage());
        }
        return out;
    }

    /** Slice 11 — release the UNSPENT portion of the original COMMITMENT.
     *  Computes (original COMMITMENT debit) − (sum of EXPENSE debits for this voucher).
     *  The EXPENSE matcher accepts BOTH idempotency key shapes:
     *    - {@code voucher_redeemed:CODE} (pre-Slice-11, single redemption)
     *    - {@code voucher_redeemed:CODE:RDM-XXXX} (post-Slice-11, per-redemption)
     *  Returns null if no original COMMITMENT exists; returns 0 if fully consumed. */
    private BigDecimal readCommittedAmount(DataSource ds, String voucherCode) {
        BigDecimal originalCommit = readSingleAmount(ds,
            "SELECT c_amount FROM app_fd_budget_event "
          + " WHERE c_idempotency_key = ? "
          + "   AND c_event_type = 'COMMITMENT' AND c_direction = 'debit' "
          + " ORDER BY id LIMIT 1",
            "voucher_issued:" + voucherCode);
        if (originalCommit == null) return null;

        BigDecimal totalExpensed = readSingleAmount(ds,
            "SELECT COALESCE(SUM(CAST(c_amount AS NUMERIC(15,2))), 0) AS total "
          + "  FROM app_fd_budget_event "
          + " WHERE (c_idempotency_key = ? OR c_idempotency_key LIKE ?) "
          + "   AND c_event_type = 'EXPENSE' AND c_direction = 'debit'",
            "voucher_redeemed:" + voucherCode,
            "voucher_redeemed:" + voucherCode + ":%");
        if (totalExpensed == null) totalExpensed = BigDecimal.ZERO;

        BigDecimal unspent = originalCommit.subtract(totalExpensed);
        if (unspent.signum() < 0) {
            LogUtil.warn(CLASS_NAME, "Voucher " + voucherCode
                + " has expensed (" + totalExpensed + ") > committed (" + originalCommit + ") — "
                + "ledger drift; releasing 0 to avoid negative.");
            return BigDecimal.ZERO;
        }
        return unspent;
    }

    private BigDecimal readSingleAmount(DataSource ds, String sql, String... params) {
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) p.setString(i + 1, params[i]);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                String amt = rs.getString(1);
                if (amt == null || amt.isEmpty()) return BigDecimal.ZERO;
                return new BigDecimal(amt);
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readSingleAmount failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        } catch (NumberFormatException nfe) {
            LogUtil.warn(CLASS_NAME, "readSingleAmount non-numeric: " + nfe.getMessage());
            return null;
        }
    }

    private boolean flipVoucherToExpired(FormDataDao dao, ExpiredVoucher v) {
        try {
            Date now = new Date();
            FormRow update = new FormRow();
            update.setId(v.id);
            update.setProperty("dateModified", ISO_UTC.format(now));
            update.setProperty("status",       "expired");
            update.setProperty("notes",        nz(v.notes)
                + " | Expired by sweeper at " + ISO_UTC.format(now));
            FormRowSet rs = new FormRowSet();
            rs.add(update);
            global.govstack.regbb.engine.support.RowWriter.save(FORM_VOUCHER, FORM_VOUCHER, rs);
            return true;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "flipVoucherToExpired failed for " + v.code);
            return false;
        }
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String firstWord(String s) {
        if (s == null) return "there";
        String t = s.trim();
        if (t.isEmpty()) return "there";
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }
}
