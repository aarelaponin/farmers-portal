package global.govstack.regbb.engine.binder;

import global.govstack.regbb.engine.budget.BudgetEngine;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * StoreBinder for the budget_adjustment_request form. Implements maker-checker
 * for manual budget adjustments per the methodology's safety layer:
 *
 * <ul>
 *   <li><b>requested</b>: maker fills the form. Binder stamps requested_by =
 *       current user, requested_at = now. No engine dispatch.</li>
 *   <li><b>approved</b>: checker flips status. Binder asserts checker ≠ maker
 *       (self-approval rejected — silently flips status back to requested
 *       and writes a reject_reason indicating self-approval). On valid
 *       approval, dispatches the appropriate BudgetEngine transactions:
 *       <ul>
 *         <li>TOP_UP → MANUAL_TOP_UP on to_envelope</li>
 *         <li>CLAWBACK → MANUAL_CLAWBACK on from_envelope</li>
 *         <li>REALLOCATION → MANUAL_CLAWBACK on from_envelope, then
 *             MANUAL_TOP_UP on to_envelope, sharing the same correlation_id</li>
 *       </ul></li>
 *   <li><b>rejected</b>: checker rejects with reason. No engine dispatch.</li>
 * </ul>
 *
 * <p>Idempotent on (request_id × event_type): if BudgetEngine has already
 * processed this request's events, the second save (e.g., re-clicking Save)
 * is a no-op rather than a double-debit.
 *
 * <p>HARD-RULE compliant: reads/writes only through Joget's standard form
 * binder pipeline; the engine dispatch is a downstream call that itself
 * uses FormDataDao. No raw SQL on Joget metadata.
 */
public class BudgetAdjustmentBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = BudgetAdjustmentBinder.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Override public String getName()        { return "Budget Adjustment Binder"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "Budget Adjustment Binder"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "Maker-checker storage binder for manual budget adjustments. Dispatches BudgetEngine on approved status with maker≠checker enforcement."; }

    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        // Pre-process: stamp maker fields on first save (status=requested),
        // resolve maker≠checker on approval (status=approved).
        if (rowSet != null && !rowSet.isEmpty()) {
            FormRow row = rowSet.get(0);
            String currentUser = nz(currentUsername(), "system");
            String status = nz(row.getProperty("status"), "requested");
            String requestedBy = nz(row.getProperty("requested_by"), "");

            if (requestedBy.isEmpty()) {
                // First save: stamp maker.
                row.setProperty("requested_by", currentUser);
                row.setProperty("requested_at", ISO_UTC.format(new Date()));
                row.setProperty("status", "requested");
            } else if ("approved".equalsIgnoreCase(status)) {
                // Approval transition: enforce maker ≠ checker.
                if (currentUser.equalsIgnoreCase(requestedBy)) {
                    LogUtil.warn(CLASS_NAME,
                        "self-approval rejected: requested_by=" + requestedBy
                        + " currentUser=" + currentUser);
                    row.setProperty("status", "requested");
                    row.setProperty("reject_reason",
                        "Self-approval rejected: the user who filed this request cannot also approve it. "
                        + "Ask a different operator to approve.");
                } else {
                    row.setProperty("approved_by", currentUser);
                    row.setProperty("approved_at", ISO_UTC.format(new Date()));
                }
            }
        }

        // Standard persistence (tx 1).
        FormRowSet stored = super.store(element, rowSet, formData);

        // Dispatch engine on approval (tx 2).
        try {
            if (stored != null && !stored.isEmpty()) {
                FormRow row = stored.get(0);
                String status = nz(row.getProperty("status"), "");
                if ("approved".equalsIgnoreCase(status)) {
                    String dispatchOutcome = nz(row.getProperty("dispatch_outcome"), "");
                    if (!"ok".equalsIgnoreCase(dispatchOutcome)) {
                        dispatchEngine(stored, row);
                    } else {
                        LogUtil.info(CLASS_NAME,
                            "request already dispatched (dispatch_outcome=ok); skipping idempotently");
                    }
                }
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                "BudgetAdjustmentBinder dispatch failed (request stays in 'approved' state, "
                + "engine NOT dispatched — manual recovery required)");
            if (stored != null && !stored.isEmpty()) {
                FormRow row = stored.get(0);
                row.setProperty("dispatch_outcome", "error:" + t.getClass().getSimpleName());
            }
        }
        return stored;
    }

    private void dispatchEngine(FormRowSet stored, FormRow row) {
        String requestId    = nz(row.getProperty("id"), "");
        String code         = nz(row.getProperty("code"), requestId);
        String type         = nz(row.getProperty("adjustment_type"), "");
        String fromEnvelope = nz(row.getProperty("from_envelope_code"), "");
        String toEnvelope   = nz(row.getProperty("to_envelope_code"), "");
        String amountStr    = nz(row.getProperty("amount"), "");
        String reason       = nz(row.getProperty("reason"), "");
        String approver     = nz(row.getProperty("approved_by"), "system");

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid_amount:" + amountStr);
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount_must_be_positive:" + amount.toPlainString());
        }

        // One correlation id ties the two halves of a REALLOCATION together
        // in the audit trail. Idempotency keys per leg are derived from the
        // request id + event type so re-saves never double-dispatch.
        String correlationId = "ADJ-" + code;

        BudgetEngine engine = new BudgetEngine();
        StringBuilder outcome = new StringBuilder();

        Map<String, Object> ctx = adjustmentData(reason, code);

        switch (type.toUpperCase()) {
            case "TOP_UP": {
                if (toEnvelope.isEmpty()) {
                    throw new IllegalArgumentException("to_envelope_required_for_TOP_UP");
                }
                BudgetEngine.DispatchResult r = engine.dispatchDirect(
                        toEnvelope, "MANUAL_TOP_UP", amount, approver,
                        "manual_adjustment", correlationId,
                        "manual:" + code + ":TOP_UP", ctx);
                outcome.append("TOP_UP=").append(r.status);
                if (!isAcceptable(r.status)) {
                    throw new IllegalStateException("TOP_UP_failed:" + r.errorCause);
                }
                break;
            }
            case "CLAWBACK": {
                if (fromEnvelope.isEmpty()) {
                    throw new IllegalArgumentException("from_envelope_required_for_CLAWBACK");
                }
                BudgetEngine.DispatchResult r = engine.dispatchDirect(
                        fromEnvelope, "MANUAL_CLAWBACK", amount, approver,
                        "manual_adjustment", correlationId,
                        "manual:" + code + ":CLAWBACK", ctx);
                outcome.append("CLAWBACK=").append(r.status);
                if (!isAcceptable(r.status)) {
                    throw new IllegalStateException("CLAWBACK_failed:" + r.errorCause);
                }
                break;
            }
            case "REALLOCATION": {
                if (fromEnvelope.isEmpty() || toEnvelope.isEmpty()) {
                    throw new IllegalArgumentException("from_and_to_envelope_required_for_REALLOCATION");
                }
                if (fromEnvelope.equalsIgnoreCase(toEnvelope)) {
                    throw new IllegalArgumentException("from_and_to_envelope_must_differ");
                }
                // Leg 1: clawback from source. If this fails (insufficient
                // budget on source), we MUST NOT proceed to leg 2 — that
                // would mint funds.
                BudgetEngine.DispatchResult r1 = engine.dispatchDirect(
                        fromEnvelope, "MANUAL_CLAWBACK", amount, approver,
                        "manual_adjustment", correlationId,
                        "manual:" + code + ":REALLOC_OUT", ctx);
                outcome.append("REALLOC_OUT=").append(r1.status);
                if (!isAcceptable(r1.status)) {
                    throw new IllegalStateException("REALLOC_OUT_failed:" + r1.errorCause);
                }
                BudgetEngine.DispatchResult r2 = engine.dispatchDirect(
                        toEnvelope, "MANUAL_TOP_UP", amount, approver,
                        "manual_adjustment", correlationId,
                        "manual:" + code + ":REALLOC_IN", ctx);
                outcome.append(",REALLOC_IN=").append(r2.status);
                if (!isAcceptable(r2.status)) {
                    // CLAWBACK already posted; admin needs to reverse it
                    // manually if TOP_UP fails. This is rare (envelope
                    // existence is the only thing that can fail here),
                    // but document the recovery in dispatch_outcome.
                    throw new IllegalStateException("REALLOC_IN_failed_after_OUT_posted:"
                            + r2.errorCause + " — admin must reverse REALLOC_OUT manually");
                }
                break;
            }
            default:
                throw new IllegalArgumentException("unsupported_adjustment_type:" + type);
        }

        // Mark dispatch_outcome so a second save doesn't re-dispatch (the
        // BudgetEngine's idempotency_key would catch it anyway, but recording
        // the outcome here makes the form-side state explicit).
        row.setProperty("dispatch_outcome", "ok:" + outcome.toString());
        row.setProperty("dispatch_idempotency_key", correlationId);
    }

    /** Both "posted" and "no_op_idempotent" are acceptable — the latter
     *  means a re-save after a successful first dispatch. */
    private static boolean isAcceptable(String status) {
        return "posted".equalsIgnoreCase(status) || "no_op_idempotent".equalsIgnoreCase(status);
    }

    private static Map<String, Object> adjustmentData(String reason, String requestCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("reason",        reason);
        data.put("request_code",  requestCode);
        return data;
    }

    static String currentUsername() {
        try {
            Object wum = AppUtil.getApplicationContext().getBean("workflowUserManager");
            if (wum == null) return null;
            java.lang.reflect.Method m = wum.getClass().getMethod("getCurrentUsername");
            Object u = m.invoke(wum);
            return u == null ? null : u.toString();
        } catch (Throwable t) { return null; }
    }

    private static String nz(Object v, String fallback) {
        if (v == null) return fallback;
        String s = v.toString();
        return s.isEmpty() ? fallback : s;
    }
    private static String nz(Object v) { return nz(v, ""); }
}
