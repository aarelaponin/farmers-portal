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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * IM Phase 3 Slice 10 — Voucher Cancellation.
 *
 * <p>Production hardening: when a beneficiary withdraws their application
 * after voucher issuance but before redemption, the operator needs to void
 * the voucher and release the budget COMMITMENT. Without this, withdrawn
 * vouchers sit in {@code status=issued} forever, distorting both the
 * "outstanding vouchers" view and the envelope's COMMITTED line.
 *
 * <p>Distinguished from Slice 9 (expiry sweeper) on three axes:
 * <ul>
 *   <li><b>Trigger</b> — operator action, not time-based sweep.</li>
 *   <li><b>Granularity</b> — one voucher at a time (per applicant withdrawal),
 *       not a batch.</li>
 *   <li><b>Reason</b> — operator records WHY (applicant requested withdrawal,
 *       fraud detected, programme cancelled, etc.). The reason flows into
 *       the voucher's notes column and the budget event's c_notes for audit.</li>
 * </ul>
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Validate input: voucherCode required, reason required (operator must
 *       state why; not just a click).</li>
 *   <li>Load the voucher row. If status != 'issued', return a status code
 *       describing the actual state — already_redeemed / already_expired /
 *       already_cancelled / not_found / wrong_status.</li>
 *   <li>Read original COMMITMENT amount via c_idempotency_key='voucher_issued:CODE'.</li>
 *   <li>Dispatch RELEASE_COMMITMENT with idempotency_key='voucher_cancelled:CODE'.
 *       Same accounting shape as Slice 9: AVAILABLE += amount; COMMITTED -= amount.</li>
 *   <li>Flip voucher status: issued → cancelled, write cancellation note + reason.</li>
 * </ol>
 *
 * <p>Idempotent: calling cancel twice on the same voucher returns
 * {@code already_cancelled} on the second call. The dispatchDirect call
 * also dedupes on idempotency_key, so even if the second call somehow
 * passed status validation it wouldn't double-release.
 *
 * <p>HARD-RULE compliant: voucher write goes through {@link FormDataDao};
 * budget release goes through {@link BudgetEngine}; lookups are SELECT.
 *
 * <p>Reachable via {@code POST /api/budget/cancel-voucher} for operator-
 * triggered actions. Could also be invoked from a workflow tool step if
 * MAFSN wants to wire it into a "withdrawal" process.
 */
public class VoucherCancellationTool extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = VoucherCancellationTool.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static final String FORM_VOUCHER = "im_voucher";

    @Override public String getName()        { return "Voucher Cancellation Tool"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "IM Slice 10 — voids an issued voucher and releases its budget COMMITMENT (operator-triggered)."; }
    @Override public String getLabel()       { return "Voucher Cancellation Tool"; }
    @Override public String getClassName()   { return CLASS_NAME; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Voucher Cancellation Tool\", \"properties\":[] } ]";
    }

    public static class Result {
        public String status;          // ok | not_found | already_redeemed | already_expired | already_cancelled | wrong_status | error
        public String message;
        public String voucherCode;
        public String voucherStatusBefore;
        public String voucherStatusAfter;
        public String releasedAmount;  // bare-number string, "0" if no commitment found
        public String releaseEventStatus; // "posted" | "no_op_idempotent" | "skipped" | "(none)"
        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"status\":\"").append(status).append("\"");
            sb.append(",\"voucherCode\":\"").append(esc(voucherCode)).append("\"");
            sb.append(",\"message\":\"").append(esc(message)).append("\"");
            if (voucherStatusBefore != null) sb.append(",\"voucherStatusBefore\":\"").append(voucherStatusBefore).append("\"");
            if (voucherStatusAfter  != null) sb.append(",\"voucherStatusAfter\":\"").append(voucherStatusAfter).append("\"");
            if (releasedAmount      != null) sb.append(",\"releasedAmount\":\"").append(releasedAmount).append("\"");
            if (releaseEventStatus  != null) sb.append(",\"releaseEventStatus\":\"").append(releaseEventStatus).append("\"");
            sb.append("}");
            return sb.toString();
        }
        private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    @Override
    public Object execute(Map props) {
        // Process-tool entry; called from a workflow step. Properties expected:
        // voucherCode, reason, actor.
        String voucherCode = stringProp(props, "voucherCode");
        String reason      = stringProp(props, "reason");
        String actor       = stringProp(props, "actor");
        Result r = cancel(voucherCode, reason, actor);
        LogUtil.info(CLASS_NAME, "Cancel result: " + r.toJson());
        return null;
    }

    /** Externally callable entry point — used by the {@code /budget/cancel-voucher}
     *  REST endpoint and by the workflow tool path. */
    public Result cancel(String voucherCode, String reason, String actor) {
        Result r = new Result();
        r.voucherCode = voucherCode;
        r.releasedAmount = "0";
        r.releaseEventStatus = "(none)";

        if (voucherCode == null || voucherCode.isEmpty()) {
            r.status = "error";
            r.message = "voucherCode is required";
            return r;
        }
        if (reason == null || reason.isEmpty()) {
            r.status = "error";
            r.message = "reason is required (operator must state why the voucher is being cancelled)";
            return r;
        }
        if (actor == null || actor.isEmpty()) actor = "operator:cancel-voucher";

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            VoucherRow v = readVoucher(ds, voucherCode);
            if (v == null) {
                r.status = "not_found";
                r.message = "No voucher with code " + voucherCode;
                return r;
            }
            r.voucherStatusBefore = nz(v.status);

            // State validation: 'issued' or 'partially_redeemed' (Slice 11) can be
            // cancelled. The cancellation releases only the UNSPENT portion of
            // the original COMMITMENT (i.e. excludes any partial EXPENSE events
            // that already posted), so a partially-redeemed voucher can still
            // be voided cleanly without double-releasing what was already given.
            String s = nz(v.status).toLowerCase();
            if ("redeemed".equals(s)) {
                r.status = "already_redeemed";
                r.message = "Voucher " + voucherCode + " has already been fully redeemed; cannot cancel. "
                          + "Use a refund/reversal flow instead (out of scope for Slice 10).";
                return r;
            }
            if ("expired".equals(s)) {
                r.status = "already_expired";
                r.message = "Voucher " + voucherCode + " has already expired (commitment already released by sweeper).";
                return r;
            }
            if ("cancelled".equals(s)) {
                r.status = "already_cancelled";
                r.message = "Voucher " + voucherCode + " has already been cancelled.";
                return r;
            }
            if (!"issued".equals(s) && !"partially_redeemed".equals(s)) {
                r.status = "wrong_status";
                r.message = "Voucher " + voucherCode + " has status '" + v.status
                          + "'; only 'issued' or 'partially_redeemed' vouchers can be cancelled.";
                return r;
            }

            // Read original COMMITMENT amount.
            BigDecimal commitAmount = readCommittedAmount(ds, voucherCode);

            // Dispatch RELEASE_COMMITMENT first — if budget release succeeds
            // we then flip the voucher. If we flipped first and the release
            // failed, we'd have an orphaned cancelled voucher with stuck
            // commitment. Order matters here.
            BudgetEngine engine = new BudgetEngine();
            if (commitAmount != null && commitAmount.signum() > 0) {
                String envelopeCode = "ENV_" + nz(v.programmeCode) + "_FY2526";
                String idem = "voucher_cancelled:" + voucherCode;
                BudgetEngine.DispatchResult dr = engine.dispatchDirect(
                        envelopeCode, "RELEASE_COMMITMENT", commitAmount, actor,
                        "voucher_cancellation", voucherCode, idem,
                        new HashMap<>());
                r.releasedAmount = commitAmount.toPlainString();
                r.releaseEventStatus = dr.status;
                if (!"posted".equals(dr.status) && !"no_op_idempotent".equals(dr.status)) {
                    r.status = "error";
                    r.message = "Budget RELEASE_COMMITMENT failed: " + dr.status
                              + " — voucher NOT cancelled (would orphan commitment)";
                    LogUtil.warn(CLASS_NAME, r.message + " (envelope=" + envelopeCode + ")");
                    return r;
                }
            } else if (commitAmount == null) {
                // No prior COMMITMENT (voucher pre-Slice-6c, or earlier dispatch
                // failed). Cancellation proceeds without a release.
                r.releaseEventStatus = "skipped_no_commitment";
                LogUtil.warn(CLASS_NAME, "Voucher " + voucherCode
                    + " has no COMMITMENT event — cancellation proceeds without budget release");
            } else {
                // commitAmount == 0 — Slice 11: voucher was fully consumed by
                // partial redemptions. Nothing left to release. The voucher
                // can still be cancelled (operator might want to record the
                // withdrawal even after partial collection), but no budget event.
                r.releaseEventStatus = "skipped_fully_consumed";
                LogUtil.info(CLASS_NAME, "Voucher " + voucherCode
                    + " unspent portion is 0 (fully consumed by partial redemptions); "
                    + "cancellation proceeds without budget release");
            }

            // Flip voucher status with reason annotation.
            if (!flipVoucherToCancelled(dao, v, reason, actor)) {
                r.status = "error";
                r.message = "Voucher write failed; budget release may have already posted "
                          + "(idempotency_key voucher_cancelled:" + voucherCode + " — re-running "
                          + "this call will be a no-op for the budget).";
                return r;
            }
            r.voucherStatusAfter = "cancelled";
            r.status = "ok";
            r.message = "Voucher " + voucherCode + " cancelled. Reason: " + reason
                      + ". Released " + r.releasedAmount + " back to envelope.";
            LogUtil.info(CLASS_NAME, r.message);

            // W2 — fire voucher_cancelled email (template 10). Best-effort.
            try {
                String firstName = firstWord(v.farmerName);
                String programmeName = nz(v.programmeCode);
                String cancelledAt = ISO_UTC.format(new Date());

                java.util.Map<String, String> vars = new java.util.HashMap<>();
                vars.put("national_id",    nz(v.farmerNid));
                vars.put("first_name",     firstName);
                vars.put("farmer_name",    nz(v.farmerName));
                vars.put("voucher_code",   voucherCode);
                vars.put("programme_name", programmeName);
                vars.put("program_name",   programmeName);
                vars.put("cancelled_date", cancelledAt);
                vars.put("reason",         reason == null ? "" : reason);
                vars.put("district_name",  "your local");
                vars.put("district_phone", "your district office");

                global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                        "VOUCHER_CANCELLED", "EN", vars);
                global.govstack.regbb.engine.notification.SmsDispatcher.sendByEvent(
                        "VOUCHER_CANCELLED", vars);
            } catch (Throwable emailEx) {
                LogUtil.error(CLASS_NAME, emailEx,
                    "Email notification failed for cancelled voucher " + voucherCode);
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "Voucher cancellation failed for " + voucherCode);
            r.status = "error";
            r.message = "internal:" + t.getClass().getSimpleName() + ":"
                      + (t.getMessage() == null ? "" : t.getMessage());
        }
        return r;
    }

    // ---------------- helpers ----------------

    private static class VoucherRow {
        String id, code, programmeCode, status, notes, quantity;
        // W2 — fields needed for the voucher_cancelled email (template 10).
        String farmerName, farmerNid;
    }

    private VoucherRow readVoucher(DataSource ds, String code) {
        String sql = "SELECT id, c_code, c_programme_code, c_status, c_notes, c_quantity, "
                   + "       c_farmer_name, c_farmer_nid "
                   + "  FROM app_fd_im_voucher WHERE c_code = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, code);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                VoucherRow v = new VoucherRow();
                v.id            = rs.getString(1);
                v.code          = rs.getString(2);
                v.programmeCode = rs.getString(3);
                v.status        = rs.getString(4);
                v.notes         = rs.getString(5);
                v.quantity      = rs.getString(6);
                v.farmerName    = rs.getString(7);
                v.farmerNid     = rs.getString(8);
                return v;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readVoucher failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    /** Slice 11 — return the UNSPENT portion of the original COMMITMENT.
     *  Computes (original COMMITMENT debit) − (sum of EXPENSE debits for this voucher).
     *  EXPENSE matcher accepts both pre-Slice-11 ({@code voucher_redeemed:CODE})
     *  and post-Slice-11 ({@code voucher_redeemed:CODE:RDM-XXXX}) idempotency
     *  key shapes. Returns null if no original COMMITMENT exists; returns 0 if
     *  fully consumed by partial redemptions. */
    private BigDecimal readCommittedAmount(DataSource ds, String voucherCode) {
        BigDecimal originalCommit = readSingleAmount(ds,
            "SELECT c_amount FROM app_fd_budget_event "
          + " WHERE c_idempotency_key = ? "
          + "   AND c_event_type = 'COMMITMENT' AND c_direction = 'debit' "
          + " ORDER BY id LIMIT 1",
            "voucher_issued:" + voucherCode);
        if (originalCommit == null) return null;

        BigDecimal totalExpensed = readSingleAmount(ds,
            "SELECT COALESCE(SUM(CAST(c_amount AS NUMERIC(15,2))), 0) "
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

    private boolean flipVoucherToCancelled(FormDataDao dao, VoucherRow v, String reason, String actor) {
        try {
            Date now = new Date();
            FormRow update = new FormRow();
            update.setId(v.id);
            update.setProperty("dateModified", ISO_UTC.format(now));
            update.setProperty("status",       "cancelled");
            update.setProperty("notes",        nz(v.notes)
                + " | Cancelled by " + actor + " at " + ISO_UTC.format(now)
                + ". Reason: " + reason);
            FormRowSet rs = new FormRowSet();
            rs.add(update);
            global.govstack.regbb.engine.support.RowWriter.save(FORM_VOUCHER, FORM_VOUCHER, rs);
            return true;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "flipVoucherToCancelled failed for " + v.code);
            return false;
        }
    }

    private static String stringProp(Map props, String key) {
        Object v = props == null ? null : props.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String firstWord(String s) {
        if (s == null) return "there";
        String t = s.trim();
        if (t.isEmpty()) return "there";
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }
}
