package global.govstack.regbb.engine.processing;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * IM Phase 3 Slice 6d — form post-processor for {@code im_stock_transaction}.
 *
 * <p>Replaces the synchronous {@link StockTransactionStoreBinder}. With the
 * Slice 6d restructure ({@code input_code} + {@code quantity} moved off the
 * parent into the {@code im_stock_txn_line} child grid), the inventory delta
 * can no longer be applied inside the parent's storeBinder — Joget walks
 * FormGrid storeBinders AFTER the parent binder, so child rows aren't
 * committed yet at parent-binder time.
 *
 * <p>Form post-processors run AFTER {@code submitForm} returns, i.e. after
 * the whole form tree (parent + grids) has been persisted. Source-verified:
 * {@code AppServiceImpl.submitForm} ≈ line 1985 calls
 * {@code FormUtil.executePostFormSubmissionProccessor(form, formData)} after
 * {@code formService.submitForm(...)} completes.
 *
 * <p>Pipeline per save:
 * <ol>
 *   <li>Read {@code recordId} from props (= the parent {@code im_stock_txn} row id).
 *   <li>Idempotency check: load the parent row, skip if {@code posted_at} is
 *       already set (means inventory deltas already applied; e.g. operator
 *       re-saved for a typo correction).
 *   <li>Read {@code txn_type}, {@code point_code} from the parent.
 *   <li>SELECT line rows from {@code app_fd_im_stock_txn_line} WHERE
 *       {@code c_txn_id = ?} (the parent id).
 *   <li>For each line, compute the signed delta and apply it to the matching
 *       {@code im_inventory} row (point_code, input_code).
 *   <li>Write {@code posted_at = now()} on the parent row.
 * </ol>
 *
 * <p>Runs only on {@code create} per the form definition — operator edits
 * after first save don't re-trigger inventory updates. Append-only design:
 * to correct a typo, the operator records an ADJUSTMENT or TRANSFER_OUT to
 * undo, then a fresh RECEIPT/etc. for the correct shape.
 *
 * <p>HARD-RULE compliant: every write goes through {@link FormDataDao};
 * every read is a SELECT (allowed).
 */
public class StockTransactionPostProcessor extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = StockTransactionPostProcessor.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static final String FORM_INVENTORY = "im_inventory";
    private static final String FORM_STOCK_TXN = "im_stock_transaction";
    private static final String TABLE_STOCK_TXN = "im_stock_txn";

    @Override public String getName()        { return "Stock Transaction Post Processor"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "IM Slice 6d — applies inventory deltas after im_stock_transaction save (header + line grid)."; }
    @Override public String getLabel()       { return "Stock Transaction Post Processor"; }
    @Override public String getClassName()   { return CLASS_NAME; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Stock Txn Post Processor\", \"properties\":[] } ]";
    }

    @Override
    public Object execute(Map props) {
        String recordId = stringProp(props, "recordId");
        if (recordId == null || recordId.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "execute() called without recordId — skipping");
            return null;
        }
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            ParentRow parent = readParent(ds, recordId);
            if (parent == null) {
                LogUtil.warn(CLASS_NAME, "Parent stock-txn row " + recordId + " not found — skipping");
                return null;
            }
            if (!nz(parent.postedAt).isEmpty()) {
                LogUtil.info(CLASS_NAME,
                    "Txn " + parent.code + " already posted at " + parent.postedAt + " — skipping (idempotent)");
                return null;
            }

            List<LineRow> lines = readLines(ds, recordId);
            if (lines.isEmpty()) {
                LogUtil.warn(CLASS_NAME,
                    "Txn " + parent.code + " has no line rows — nothing to apply, parent left unposted");
                return null;
            }

            int applied = 0, skipped = 0;
            for (LineRow line : lines) {
                if (applyLine(ds, dao, parent, line)) applied++; else skipped++;
            }

            // Mark parent as posted — only if at least one line applied; otherwise
            // leave unposted so a manual re-save (after fixing inventory rows) can
            // retry. (This is rare; usually 'no inventory row' just means the
            // operator forgot to seed im_inventory for that point/SKU first.)
            if (applied > 0) {
                markPosted(dao, parent);
            }
            LogUtil.info(CLASS_NAME,
                "Txn " + parent.code + " (" + parent.txnType + " @ " + parent.pointCode + ") "
                + "lines: " + applied + " applied, " + skipped + " skipped");
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                "Post-processor failed for recordId=" + recordId
                + " — header row was saved, inventory NOT fully updated");
        }
        return null;
    }

    private boolean applyLine(DataSource ds, FormDataDao dao, ParentRow parent, LineRow line) {
        String txnCode  = nz(parent.code);
        String txnType  = nz(parent.txnType).toUpperCase();
        String pointCd  = nz(parent.pointCode);
        String inputCd  = nz(line.inputCode);
        String qtyStr   = nz(line.quantity);

        if (pointCd.isEmpty() || inputCd.isEmpty() || qtyStr.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "Txn " + txnCode + " line missing point/input/quantity; skipped");
            return false;
        }
        BigDecimal qty;
        try { qty = new BigDecimal(qtyStr); }
        catch (NumberFormatException nfe) {
            LogUtil.warn(CLASS_NAME, "Txn " + txnCode + " line non-numeric quantity '" + qtyStr + "'; skipped");
            return false;
        }

        BigDecimal delta;
        switch (txnType) {
            case "RECEIPT":
            case "TRANSFER_IN":
                delta = qty.abs();           break;
            case "ISSUE":
            case "TRANSFER_OUT":
                delta = qty.abs().negate();  break;
            case "ADJUSTMENT":
                delta = qty;                 break;  // signed
            default:
                LogUtil.warn(CLASS_NAME, "Txn " + txnCode + " unknown type '" + txnType + "'; skipped");
                return false;
        }

        InventoryRow inv = readInventory(ds, pointCd, inputCd);
        if (inv == null) {
            LogUtil.warn(CLASS_NAME,
                "Txn " + txnCode + " no im_inventory row for (point=" + pointCd + ", input=" + inputCd
                + ") — line skipped. Seed the inventory row first then re-save the txn.");
            return false;
        }

        BigDecimal before = new BigDecimal(nz(inv.quantityOnHand).isEmpty() ? "0" : inv.quantityOnHand);
        BigDecimal after  = before.add(delta).setScale(2, RoundingMode.HALF_EVEN);
        Date now = new Date();

        FormRow update = new FormRow();
        update.setId(inv.id);
        update.setProperty("dateModified",          ISO_UTC.format(now));
        update.setProperty("code",                  inv.code);
        update.setProperty("point_code",            inv.pointCode);
        update.setProperty("input_code",            inv.inputCode);
        update.setProperty("quantity_on_hand",      after.toPlainString());
        update.setProperty("quantity_reserved",     nz(inv.quantityReserved));
        update.setProperty("reorder_threshold",     nz(inv.reorderThreshold));
        update.setProperty("last_stock_count_date", nz(inv.lastStockCountDate));
        update.setProperty("notes",                 nz(inv.notes)
            + " | " + (delta.signum() >= 0 ? "+" : "") + delta.toPlainString()
            + " on " + ISO_UTC.format(now).substring(0,10) + " (txn=" + txnCode + ")");
        FormRowSet rs = new FormRowSet();
        rs.add(update);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_INVENTORY, FORM_INVENTORY, rs);

        LogUtil.info(CLASS_NAME,
            "Txn " + txnCode + " (" + txnType + ") line applied: "
            + pointCd + "." + inputCd + " " + before.toPlainString()
            + " → " + after.toPlainString() + " (Δ" + delta.toPlainString() + ")");
        return true;
    }

    private void markPosted(FormDataDao dao, ParentRow parent) {
        Date now = new Date();
        FormRow update = new FormRow();
        update.setId(parent.id);
        update.setProperty("dateModified", ISO_UTC.format(now));
        // Re-write everything we read so we don't null out other columns.
        update.setProperty("code",                 nz(parent.code));
        update.setProperty("txn_type",             nz(parent.txnType));
        update.setProperty("txn_date",             nz(parent.txnDate));
        update.setProperty("district_code",        nz(parent.districtCode));
        update.setProperty("point_code",           nz(parent.pointCode));
        update.setProperty("supplier_code",        nz(parent.supplierCode));
        update.setProperty("supplier_invoice_no",  nz(parent.supplierInvoiceNo));
        update.setProperty("voucher_code",         nz(parent.voucherCode));
        update.setProperty("transfer_partner_point", nz(parent.transferPartnerPoint));
        update.setProperty("reason_code",          nz(parent.reasonCode));
        update.setProperty("performed_by",         nz(parent.performedBy));
        update.setProperty("notes",                nz(parent.notes));
        update.setProperty("posted_at",            ISO_UTC.format(now));
        FormRowSet rs = new FormRowSet();
        rs.add(update);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_STOCK_TXN, TABLE_STOCK_TXN, rs);
    }

    // ---- DB readers ------------------------------------------------------

    private static class ParentRow {
        String id, code, txnType, txnDate, districtCode, pointCode,
               supplierCode, supplierInvoiceNo, voucherCode,
               transferPartnerPoint, reasonCode, performedBy, notes, postedAt;
    }

    private ParentRow readParent(DataSource ds, String id) {
        String sql = "SELECT id, c_code, c_txn_type, c_txn_date, c_district_code, c_point_code, "
                   + "       c_supplier_code, c_supplier_invoice_no, c_voucher_code, "
                   + "       c_transfer_partner_point, c_reason_code, c_performed_by, c_notes, c_posted_at "
                   + "  FROM app_fd_im_stock_txn WHERE id = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, id);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                ParentRow pr = new ParentRow();
                pr.id                    = rs.getString(1);
                pr.code                  = rs.getString(2);
                pr.txnType               = rs.getString(3);
                pr.txnDate               = rs.getString(4);
                pr.districtCode          = rs.getString(5);
                pr.pointCode             = rs.getString(6);
                pr.supplierCode          = rs.getString(7);
                pr.supplierInvoiceNo     = rs.getString(8);
                pr.voucherCode           = rs.getString(9);
                pr.transferPartnerPoint  = rs.getString(10);
                pr.reasonCode            = rs.getString(11);
                pr.performedBy           = rs.getString(12);
                pr.notes                 = rs.getString(13);
                pr.postedAt              = rs.getString(14);
                return pr;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readParent failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    private static class LineRow {
        String id, inputCode, quantity, unitPrice, lineNotes;
    }

    private List<LineRow> readLines(DataSource ds, String parentId) {
        List<LineRow> out = new ArrayList<>();
        String sql = "SELECT id, c_input_code, c_quantity, c_unit_price, c_line_notes "
                   + "  FROM app_fd_im_stock_txn_line WHERE c_txn_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, parentId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    LineRow lr = new LineRow();
                    lr.id        = rs.getString(1);
                    lr.inputCode = rs.getString(2);
                    lr.quantity  = rs.getString(3);
                    lr.unitPrice = rs.getString(4);
                    lr.lineNotes = rs.getString(5);
                    out.add(lr);
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readLines failed: " + e.getSQLState() + ":" + e.getMessage());
        }
        return out;
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

    // ---- helpers ---------------------------------------------------------

    private static String stringProp(Map props, String key) {
        Object v = props == null ? null : props.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
}
