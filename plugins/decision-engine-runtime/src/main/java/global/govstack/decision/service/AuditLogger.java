package global.govstack.decision.service;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.UUID;

/**
 * Writes structured rows into {@code app_fd_audit_log}. Used by the decision
 * tools to record every approve / reject / request-info action with the
 * actor, timestamp, and a reason string the operator typed.
 *
 * <p>Schema is whatever the existing {@code auditLog} form declares. We
 * write a minimal set of well-known fields:
 * <ul>
 *   <li>{@code event_type} — STATUS_TRANSITION | DECISION</li>
 *   <li>{@code entity_type} — sp_application</li>
 *   <li>{@code entity_id} — wrapper id</li>
 *   <li>{@code old_status} / {@code new_status}</li>
 *   <li>{@code actor} — Joget username</li>
 *   <li>{@code reason} — free text</li>
 *   <li>{@code payload} — JSON blob (optional, for future extensions)</li>
 * </ul>
 */
public class AuditLogger {

    private static final String CLASS_NAME = AuditLogger.class.getName();
    private static final String F_AUDIT = "auditLog";
    private static final String T_AUDIT = "audit_log";

    private final FormDataDao dao;

    public AuditLogger(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * Writes a row aligned with the EXISTING {@code app_fd_audit_log} schema
     * (created by form-quality-runtime). Field IDs:
     * {@code entity_type}, {@code entity_id}, {@code from_status},
     * {@code to_status}, {@code triggered_by}, {@code reason}, {@code timestamp}.
     *
     * <p>For decisions we encode entity_id as {@code sp_application:<wrapperId>}
     * (form_id:record_id) — the same convention form-quality-runtime uses.
     */
    public void logDecision(String entityId, String oldStatus, String newStatus,
                            String actor, String reason) {
        try {
            FormRow row = new FormRow();
            row.setId(UUID.randomUUID().toString());
            row.setProperty("entity_type", "DECISION");
            row.setProperty("entity_id",   "sp_application:" + nz(entityId));
            row.setProperty("from_status", nz(oldStatus));
            row.setProperty("to_status",   nz(newStatus));
            row.setProperty("triggered_by", "decision-engine-runtime / " + nz(actor));
            row.setProperty("reason",      nz(reason));
            row.setProperty("timestamp",   java.time.Instant.now().toString());
            FormRowSet batch = new FormRowSet();
            batch.add(row);
            dao.saveOrUpdate(F_AUDIT, T_AUDIT, batch);
            LogUtil.info(CLASS_NAME, "audit row written: " + entityId
                    + " " + oldStatus + " -> " + newStatus + " by " + actor);
        } catch (Throwable t) {
            // Audit failure must NEVER block the decision — log and continue.
            LogUtil.error(CLASS_NAME, t,
                    "audit write failed for " + entityId + ": " + t.getMessage());
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
