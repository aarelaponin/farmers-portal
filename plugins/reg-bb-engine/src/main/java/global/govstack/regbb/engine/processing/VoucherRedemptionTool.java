package global.govstack.regbb.engine.processing;

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
import java.util.TimeZone;
import java.util.UUID;

/**
 * Phase 3 IM Slice 5 — redeems an issued voucher at a Resource Centre.
 *
 * <p>This is the closing-loop tool of the IM lifecycle (Phase G in the customer
 * overview). Per IM SAD §6.3, redemption is the moment three things happen
 * atomically:
 * <ol>
 *   <li>The voucher status flips from {@code issued} to {@code redeemed}.</li>
 *   <li>An {@code im_voucher_redemption} row is written as the audit trail.</li>
 *   <li>The matching {@code im_inventory} row's {@code quantity_on_hand} is decremented
 *       by the redeemed quantity.</li>
 * </ol>
 *
 * <p>Validation gates (each returns a distinct status code so the operator UI can
 * show a precise reason for failure):
 * <ul>
 *   <li>{@code voucher_not_found} — no voucher with that code.</li>
 *   <li>{@code already_redeemed} — voucher.status is already {@code redeemed} or terminal.</li>
 *   <li>{@code wrong_status} — voucher.status is not {@code issued} (e.g. {@code cancelled}).</li>
 *   <li>{@code expired} — voucher.expiry_date is in the past.</li>
 *   <li>{@code wrong_point} — caller-provided redemption point doesn't match
 *       voucher.point_code (lowlands voucher cannot be redeemed at a mountain centre).</li>
 *   <li>{@code ok} — redemption written; voucher updated; inventory decremented.</li>
 * </ul>
 *
 * <p>HARD-RULE compliant: every write goes through {@link FormDataDao}; the lookups
 * are read-only SELECTs. Inventory decrement is also a DAO update (we read the
 * existing row, update quantity, save). No raw SQL on app_fd_*.
 *
 * <p>What this slice does NOT do (deferred to Slice 5+ / Phase H polish):
 * <ul>
 *   <li>Budget Engine EXPENSE event when redemption happens.</li>
 *   <li>SMS confirmation to the farmer.</li>
 *   <li>The im_distribution / im_distribution_item per-day session aggregate
 *       (multiple redemptions roll up to one distribution event).</li>
 *   <li>Quality-of-service determinants (IM_VOUCHER_REDEEM_AT_ALLOCATED_POINT
 *       per the SAD §5.3) — Slice 5 enforces the same logic in code; the
 *       configuration-over-code mm_determinant version lands later.</li>
 * </ul>
 */
public class VoucherRedemptionTool extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = VoucherRedemptionTool.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final SimpleDateFormat ISO_DATE =
            new SimpleDateFormat("yyyy-MM-dd");
    static {
        ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        ISO_DATE.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final String FORM_VOUCHER       = "im_voucher";
    private static final String FORM_REDEMPTION    = "im_voucher_redemption";
    private static final String FORM_INVENTORY     = "im_inventory";

    @Override public String getName()        { return "Voucher Redemption Tool"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "Voucher Redemption Tool"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "IM Slice 5 — atomic voucher redemption: writes redemption row, flips voucher.status issued→redeemed, decrements im_inventory."; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Voucher Redemption\", \"properties\":[] } ]";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        String voucherCode    = String.valueOf(properties.getOrDefault("voucherCode", ""));
        String redemptionPoint = String.valueOf(properties.getOrDefault("redemptionPoint", ""));
        String redeemedBy     = String.valueOf(properties.getOrDefault("redeemedBy", ""));
        String quantityStr    = String.valueOf(properties.getOrDefault("quantity", ""));
        Result r = redeem(voucherCode, redemptionPoint, redeemedBy, quantityStr);
        return r.toJson();
    }

    public static class Result {
        public String  status;            // ok / voucher_not_found / already_redeemed / wrong_status / expired / wrong_point / error
        public String  voucherCode;
        public String  redemptionCode;
        public String  message;
        public String  voucherStatusBefore;
        public String  voucherStatusAfter;
        public String  inventoryBefore;
        public String  inventoryAfter;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"status\":\"").append(s(status)).append("\",");
            sb.append("\"voucherCode\":\"").append(s(voucherCode)).append("\",");
            sb.append("\"redemptionCode\":\"").append(s(redemptionCode)).append("\",");
            sb.append("\"message\":\"").append(j(message)).append("\",");
            sb.append("\"voucherStatusBefore\":\"").append(s(voucherStatusBefore)).append("\",");
            sb.append("\"voucherStatusAfter\":\"").append(s(voucherStatusAfter)).append("\",");
            sb.append("\"inventoryBefore\":\"").append(s(inventoryBefore)).append("\",");
            sb.append("\"inventoryAfter\":\"").append(s(inventoryAfter)).append("\"");
            sb.append("}");
            return sb.toString();
        }
        private static String s(String x) { return x == null ? "" : x.replace("\"","\\\""); }
        private static String j(String x) {
            if (x == null) return "";
            return x.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n"," ").replace("\r"," ");
        }
    }

    public Result redeem(String voucherCode, String redemptionPoint,
                         String redeemedBy, String qtyOverride) {
        Result r = new Result();
        r.voucherCode = voucherCode;

        if (voucherCode == null || voucherCode.isEmpty()) {
            r.status = "error"; r.message = "voucherCode is required"; return r;
        }
        if (redemptionPoint == null || redemptionPoint.isEmpty()) {
            r.status = "error"; r.message = "redemptionPoint is required"; return r;
        }
        if (redeemedBy == null || redeemedBy.isEmpty()) {
            redeemedBy = "(unspecified)";
        }

        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

        // 1) Read the voucher
        Voucher v = readVoucher(ds, voucherCode);
        if (v == null) {
            r.status = "voucher_not_found";
            r.message = "No voucher with code=" + voucherCode;
            return r;
        }
        r.voucherStatusBefore = v.status;

        // 2) Status check: 'issued' or 'partially_redeemed' (Slice 11) accepted.
        //    'redeemed' / 'reconciled' / 'expired' / 'cancelled' are terminal.
        if ("redeemed".equalsIgnoreCase(v.status) || "reconciled".equalsIgnoreCase(v.status)) {
            r.status = "already_redeemed";
            r.message = "Voucher fully redeemed (status=" + v.status + "). No remaining quantity.";
            return r;
        }
        if (!"issued".equalsIgnoreCase(v.status) && !"partially_redeemed".equalsIgnoreCase(v.status)) {
            r.status = "wrong_status";
            r.message = "Voucher status is '" + v.status + "' — only 'issued' or 'partially_redeemed' "
                      + "vouchers can be redeemed.";
            return r;
        }

        // 3) Expiry check: expiry_date >= today
        Date today = new Date();
        if (v.expiryDate != null && v.expiryDate.before(stripTime(today))) {
            r.status = "expired";
            r.message = "Voucher expired on " + ISO_DATE.format(v.expiryDate)
                      + ". Today is " + ISO_DATE.format(today) + ".";
            return r;
        }

        // 4) Point match: redemptionPoint must equal voucher.point_code
        if (!redemptionPoint.equalsIgnoreCase(v.pointCode)) {
            r.status = "wrong_point";
            r.message = "Voucher must be redeemed at " + v.pointCode
                      + " (allocated point), not " + redemptionPoint + ".";
            return r;
        }

        // 5) Slice 11 — compute remaining quantity from the running total of
        //    prior redemptions. Sum app_fd_im_voucher_redemption.c_quantity_redeemed
        //    where voucher_code = this voucher. Single source of truth — no risk
        //    of drift between a denormalised voucher.quantity_redeemed column
        //    and the redemption rows themselves.
        BigDecimal entitledQty = new BigDecimal(v.quantity == null || v.quantity.isEmpty() ? "0" : v.quantity);
        BigDecimal alreadyRedeemed = readAlreadyRedeemed(ds, voucherCode);
        BigDecimal remainingQty = entitledQty.subtract(alreadyRedeemed);
        if (remainingQty.signum() <= 0) {
            // Defensive: shouldn't happen if status is 'issued'/'partially_redeemed'
            // and the voucher.quantity invariant holds, but catch the case anyway.
            r.status = "already_redeemed";
            r.message = "Voucher has no remaining quantity (entitled=" + entitledQty
                      + ", already redeemed=" + alreadyRedeemed + ").";
            return r;
        }

        // 6) Decide redemption quantity. Default = remaining (full collection
        //    for this visit). Operator override allowed — must be ≤ remaining.
        BigDecimal qty;
        if (qtyOverride == null || qtyOverride.isEmpty()) {
            qty = remainingQty;
        } else {
            try { qty = new BigDecimal(qtyOverride); }
            catch (NumberFormatException nfe) {
                r.status = "error"; r.message = "Invalid quantity override: " + qtyOverride; return r;
            }
            if (qty.signum() <= 0) {
                r.status = "error";
                r.message = "Quantity must be positive (got " + qty.toPlainString() + ").";
                return r;
            }
            if (qty.compareTo(remainingQty) > 0) {
                r.status = "exceeds_entitlement";
                r.message = "Requested " + qty.toPlainString() + " but only "
                          + remainingQty.toPlainString() + " remaining "
                          + "(entitled=" + entitledQty.toPlainString()
                          + ", already redeemed=" + alreadyRedeemed.toPlainString() + ").";
                return r;
            }
        }

        // 7) Look up the matching inventory row (by point + input). Required for
        //    the decrement step. If absent, redemption blocks (defensive — should
        //    never happen for an approved voucher, but we don't quietly proceed).
        InventoryRow inv = readInventory(ds, v.pointCode, v.inputCode);
        if (inv == null) {
            r.status = "error";
            r.message = "No im_inventory row for (point=" + v.pointCode
                      + ", input=" + v.inputCode + "). Stock receipt missing?";
            return r;
        }
        BigDecimal beforeQty = new BigDecimal(inv.quantityOnHand == null
                ? "0" : inv.quantityOnHand);

        // 8) Slice 11 — explicit insufficient-stock check. Operator must split
        //    into a smaller redemption (use ?quantity=<on-hand>) or wait for
        //    restock. No silent capping — operator visibility into the shortage
        //    is the audit-relevant signal.
        if (qty.compareTo(beforeQty) > 0) {
            r.status = "insufficient_stock";
            r.message = "Requested " + qty.toPlainString() + " but only "
                      + beforeQty.toPlainString() + " on hand at " + v.pointCode
                      + ". Re-call with quantity=" + beforeQty.toPlainString()
                      + " for partial collection, or wait for restock.";
            r.inventoryBefore = beforeQty.toPlainString();
            r.inventoryAfter  = beforeQty.toPlainString();
            return r;
        }

        BigDecimal afterQty = beforeQty.subtract(qty);
        r.inventoryBefore = beforeQty.toPlainString();
        r.inventoryAfter  = afterQty.toPlainString();

        // 7) Generate redemption code
        int seq = nextRedemptionSeq(ds, today);
        String redemptionCode = "RDM-" + new SimpleDateFormat("yyyyMMdd").format(today) + "-"
                              + String.format("%04d", seq);
        r.redemptionCode = redemptionCode;

        // 8) Atomic three-write sequence (best-effort atomicity within Joget's DAO):
        //    a) write redemption audit row
        //    b) update voucher status + redeemed_at + redeemed_by
        //    c) update inventory quantity_on_hand
        // If any step throws, the earlier writes are not auto-rolled-back. For
        // Slice 5 we accept this and log loudly; full transaction support would
        // require Spring @Transactional wiring (production hardening).

        // (a) redemption audit row
        FormRow rdm = new FormRow();
        rdm.setId(UUID.randomUUID().toString());
        rdm.setProperty("dateCreated",  ISO_UTC.format(today));
        rdm.setProperty("dateModified", ISO_UTC.format(today));
        rdm.setProperty("code",            redemptionCode);
        rdm.setProperty("voucher_code",    voucherCode);
        rdm.setProperty("voucher_id",      v.id);
        rdm.setProperty("redemption_date", ISO_DATE.format(today));
        rdm.setProperty("redemption_point", redemptionPoint);
        rdm.setProperty("redeemed_by",     redeemedBy);
        rdm.setProperty("quantity_redeemed", qty.toPlainString());
        rdm.setProperty("status",          "completed");
        rdm.setProperty("notes",           "Redeemed via VoucherRedemptionTool");
        FormRowSet rdmSet = new FormRowSet();
        rdmSet.add(rdm);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_REDEMPTION, FORM_REDEMPTION, rdmSet);

        // (b) voucher status flip — Slice 11: 'redeemed' iff this redemption
        //     completes the entitled quantity; otherwise 'partially_redeemed'
        //     so the operator can come back for the rest.
        BigDecimal newTotalRedeemed = alreadyRedeemed.add(qty);
        boolean fullyRedeemed = newTotalRedeemed.compareTo(entitledQty) >= 0;
        String newVoucherStatus = fullyRedeemed ? "redeemed" : "partially_redeemed";

        FormRow voucherUpdate = new FormRow();
        voucherUpdate.setId(v.id);
        voucherUpdate.setProperty("dateModified", ISO_UTC.format(today));
        // Carry forward all current voucher fields (Joget UPDATE drops keys not
        // present in the rowSet)
        voucherUpdate.setProperty("code",                v.code);
        voucherUpdate.setProperty("application_id",      nullSafe(v.applicationId));
        voucherUpdate.setProperty("farmer_nid",          nullSafe(v.farmerNid));
        voucherUpdate.setProperty("farmer_name",         nullSafe(v.farmerName));
        voucherUpdate.setProperty("programme_code",      nullSafe(v.programmeCode));
        voucherUpdate.setProperty("allocation_line_id",  nullSafe(v.allocationLineId));
        voucherUpdate.setProperty("input_code",          v.inputCode);
        voucherUpdate.setProperty("point_code",          v.pointCode);
        voucherUpdate.setProperty("quantity",            nullSafe(v.quantity));
        voucherUpdate.setProperty("issued_date",         nullSafe(v.issuedDate));
        voucherUpdate.setProperty("expiry_date",         nullSafe(v.expiryDateStr));
        voucherUpdate.setProperty("status",              newVoucherStatus);
        voucherUpdate.setProperty("redeemed_at",         ISO_DATE.format(today));
        voucherUpdate.setProperty("redeemed_by",         redeemedBy);
        voucherUpdate.setProperty("notes",               nullSafe(v.notes)
                + " | " + (fullyRedeemed ? "redeemed" : "partial")
                + " " + qty.toPlainString() + "/" + entitledQty.toPlainString()
                + " on " + ISO_DATE.format(today) + " by " + redeemedBy
                + " (rdm=" + redemptionCode + ")");
        FormRowSet voucherSet = new FormRowSet();
        voucherSet.add(voucherUpdate);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_VOUCHER, FORM_VOUCHER, voucherSet);
        r.voucherStatusAfter = newVoucherStatus;

        // (c) inventory decrement
        FormRow invUpdate = new FormRow();
        invUpdate.setId(inv.id);
        invUpdate.setProperty("dateModified", ISO_UTC.format(today));
        invUpdate.setProperty("code",                inv.code);
        invUpdate.setProperty("point_code",          inv.pointCode);
        invUpdate.setProperty("input_code",          inv.inputCode);
        invUpdate.setProperty("quantity_on_hand",    afterQty.toPlainString());
        invUpdate.setProperty("quantity_reserved",   nullSafe(inv.quantityReserved));
        invUpdate.setProperty("reorder_threshold",   nullSafe(inv.reorderThreshold));
        invUpdate.setProperty("last_stock_count_date", nullSafe(inv.lastStockCountDate));
        invUpdate.setProperty("notes",               nullSafe(inv.notes)
                + " | -" + qty.toPlainString() + " on " + ISO_DATE.format(today)
                + " (rdm=" + redemptionCode + ")");
        FormRowSet invSet = new FormRowSet();
        invSet.add(invUpdate);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_INVENTORY, FORM_INVENTORY, invSet);

        // Slice 6c — Budget EXPENSE event. Resolve unit_price from md27input;
        // amount = redeemed_qty × unit_price. Slice 11: idempotency key carries
        // the redemption-code suffix so multiple partial redemptions on the
        // same voucher each get their own EXPENSE event (the dispatchDirect
        // de-dup is per-key, so a bare voucher_redeemed:CODE would block the
        // second partial). Failure logs but doesn't unwind the redemption —
        // the redemption row + voucher status flip + inventory decrement are
        // the operational truth; financial reconciliation via manual surface.
        try {
            BigDecimal unitPrice = readUnitPrice(ds, v.inputCode);
            BigDecimal amount = unitPrice.multiply(qty)
                    .setScale(2, java.math.RoundingMode.HALF_EVEN);
            if (amount.signum() > 0 && v.programmeCode != null && !v.programmeCode.isEmpty()) {
                String envelopeCode = "ENV_" + v.programmeCode + "_FY2526";
                String idem = "voucher_redeemed:" + voucherCode + ":" + redemptionCode;
                new global.govstack.regbb.engine.budget.BudgetEngine().dispatchDirect(
                        envelopeCode, "EXPENSE", amount, redeemedBy,
                        "voucher_redemption", redemptionCode, idem,
                        new java.util.HashMap<>());
                LogUtil.info(CLASS_NAME, "Budget EXPENSE for redemption " + redemptionCode
                        + ": " + amount + " against " + envelopeCode + " (idem=" + idem + ")");
            }
        } catch (Throwable budgetEx) {
            LogUtil.error(CLASS_NAME, budgetEx,
                "Budget EXPENSE failed for redemption " + redemptionCode
                + " — operational records intact; financial record missing");
        }

        // W2 — fire voucher_redeemed email (template 07). Best-effort; SMTP
        // errors logged inside dispatcher and never block the redemption.
        try {
            String firstName = firstWord(v.farmerName);
            String benefit = qty.toPlainString() + " unit(s) of " + v.inputCode;
            BigDecimal remaining = entitledQty.subtract(newTotalRedeemed);

            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("national_id",         nullSafe(v.farmerNid));
            vars.put("first_name",          firstName);
            vars.put("farmer_name",         nullSafe(v.farmerName));
            vars.put("voucher_code",        voucherCode);
            vars.put("redemption_code",     redemptionCode);
            vars.put("redemption_date",     ISO_DATE.format(today));
            vars.put("point_name",          redemptionPoint);
            vars.put("point_address",       "(address on file)");
            vars.put("quantity_dispensed",  qty.toPlainString());
            vars.put("benefit",             benefit);
            vars.put("benefit_description", benefit);
            vars.put("remaining_qty",       remaining.toPlainString());
            vars.put("expiry_date",         nullSafe(v.expiryDateStr));
            vars.put("district_name",       "your local");
            vars.put("district_phone",      "your district office");

            global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                    "VOUCHER_REDEEMED", "EN", vars);
            global.govstack.regbb.engine.notification.SmsDispatcher.sendByEvent(
                    "VOUCHER_REDEEMED", vars);
        } catch (Throwable emailEx) {
            LogUtil.error(CLASS_NAME, emailEx,
                "Email notification failed for redemption " + redemptionCode);
        }

        r.status = "ok";
        r.message = (fullyRedeemed ? "Fully redeemed " : "Partial redemption: ")
                  + qty.toPlainString() + " of " + entitledQty.toPlainString()
                  + " units of " + v.inputCode + " at " + redemptionPoint
                  + (fullyRedeemed ? "" : " (remaining " + entitledQty.subtract(newTotalRedeemed).toPlainString() + ")")
                  + ". Inventory: " + beforeQty.toPlainString() + " → " + afterQty.toPlainString();
        LogUtil.info(CLASS_NAME, r.toJson());
        return r;
    }

    /** Slice 11 — sum of c_quantity_redeemed across all redemption rows for
     *  this voucher. Single source of truth for "how much has been collected
     *  so far". Returns 0 if no prior redemptions. */
    private BigDecimal readAlreadyRedeemed(DataSource ds, String voucherCode) {
        String sql = "SELECT COALESCE(SUM(CAST(NULLIF(c_quantity_redeemed,'') AS NUMERIC(15,2))), 0) "
                   + "  FROM app_fd_im_voucher_redemption WHERE c_voucher_code = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, voucherCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    if (s != null && !s.isEmpty()) {
                        try { return new BigDecimal(s); } catch (Exception ignore) {}
                    }
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readAlreadyRedeemed failed: "
                    + e.getSQLState() + ":" + e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /** Read md27input.c_estimated_cost_per_unit (mirrors VoucherIssuanceTool helper). */
    private BigDecimal readUnitPrice(DataSource ds, String inputCode) {
        String sql = "SELECT c_estimated_cost_per_unit FROM app_fd_md27input WHERE c_code = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, inputCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    if (s != null && !s.isEmpty()) {
                        try { return new BigDecimal(s); } catch (Exception ignore) {}
                    }
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readUnitPrice failed: "
                    + e.getSQLState() + ":" + e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    // ---------------- Read helpers ----------------

    private static class Voucher {
        String id, code, applicationId, farmerNid, farmerName, programmeCode,
               allocationLineId, inputCode, pointCode, quantity, issuedDate,
               expiryDateStr, status, notes;
        Date expiryDate;
    }

    private Voucher readVoucher(DataSource ds, String code) {
        String sql = "SELECT id, c_code, c_application_id, c_farmer_nid, c_farmer_name, "
                   + "       c_programme_code, c_allocation_line_id, c_input_code, "
                   + "       c_point_code, c_quantity, c_issued_date, c_expiry_date, "
                   + "       c_status, c_notes "
                   + "  FROM app_fd_im_voucher WHERE c_code = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, code);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                Voucher v = new Voucher();
                v.id               = rs.getString(1);
                v.code             = rs.getString(2);
                v.applicationId    = rs.getString(3);
                v.farmerNid        = rs.getString(4);
                v.farmerName       = rs.getString(5);
                v.programmeCode    = rs.getString(6);
                v.allocationLineId = rs.getString(7);
                v.inputCode        = rs.getString(8);
                v.pointCode        = rs.getString(9);
                v.quantity         = rs.getString(10);
                v.issuedDate       = rs.getString(11);
                v.expiryDateStr    = rs.getString(12);
                v.status           = rs.getString(13);
                v.notes            = rs.getString(14);
                v.expiryDate       = parseDate(v.expiryDateStr);
                return v;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readVoucher failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    private static class InventoryRow {
        String id, code, pointCode, inputCode, quantityOnHand, quantityReserved,
               reorderThreshold, lastStockCountDate, notes;
    }

    private InventoryRow readInventory(DataSource ds, String pointCode, String inputCode) {
        String sql = "SELECT id, c_code, c_point_code, c_input_code, c_quantity_on_hand, "
                   + "       c_quantity_reserved, c_reorder_threshold, c_last_stock_count_date, c_notes "
                   + "  FROM app_fd_im_inventory "
                   + " WHERE c_point_code = ? AND c_input_code = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, pointCode);
            p.setString(2, inputCode);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                InventoryRow ir = new InventoryRow();
                ir.id                 = rs.getString(1);
                ir.code               = rs.getString(2);
                ir.pointCode          = rs.getString(3);
                ir.inputCode          = rs.getString(4);
                ir.quantityOnHand     = rs.getString(5);
                ir.quantityReserved   = rs.getString(6);
                ir.reorderThreshold   = rs.getString(7);
                ir.lastStockCountDate = rs.getString(8);
                ir.notes              = rs.getString(9);
                return ir;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readInventory failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    private int nextRedemptionSeq(DataSource ds, Date today) {
        String prefix = "RDM-" + new SimpleDateFormat("yyyyMMdd").format(today) + "-";
        String sql = "SELECT count(*) FROM app_fd_im_voucher_redemption WHERE c_code LIKE ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, prefix + "%");
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "nextRedemptionSeq (non-fatal): "
                    + e.getSQLState() + ":" + e.getMessage());
        }
        return 1;
    }

    private static Date parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return ISO_DATE.parse(s); } catch (Exception e) { return null; }
    }

    private static Date stripTime(Date d) {
        try { return ISO_DATE.parse(ISO_DATE.format(d)); }
        catch (Exception e) { return d; }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String firstWord(String s) {
        if (s == null) return "there";
        String t = s.trim();
        if (t.isEmpty()) return "there";
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }
}
