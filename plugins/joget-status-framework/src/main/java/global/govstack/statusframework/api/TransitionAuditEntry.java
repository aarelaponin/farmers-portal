package global.govstack.statusframework.api;

import org.joget.apps.form.model.FormRow;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing one status-transition audit-log record.
 * <p>
 * Timestamp is auto-generated at creation time using ISO-8601 format.
 * <p>
 * Persisted to the {@code app_fd_audit_log} Joget form table by
 * {@link global.govstack.statusframework.core.StatusFramework} via
 * {@link FormRow}.
 */
public final class TransitionAuditEntry {

    private final EntityType entityType;
    private final String entityId;
    private final Status fromStatus;
    private final Status toStatus;
    private final String triggeredBy;
    private final String reason;
    private final String timestamp;

    /**
     * Creates a new audit entry. Timestamp is set automatically to now.
     *
     * @param entityType  the entity type (non-null)
     * @param entityId    the record ID (non-null)
     * @param fromStatus  previous status (may be {@code null} for initial transitions)
     * @param toStatus    new status (non-null)
     * @param triggeredBy plugin name (e.g., {@code "statement-importer"}) or {@code "OPERATOR"}
     * @param reason      human-readable explanation
     */
    public TransitionAuditEntry(EntityType entityType, String entityId,
                                Status fromStatus, Status toStatus,
                                String triggeredBy, String reason) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.triggeredBy = triggeredBy;
        this.reason = reason;
        this.timestamp = Instant.now().toString();
    }

    public EntityType getEntityType()  { return entityType; }
    public String     getEntityId()    { return entityId; }
    public Status     getFromStatus()  { return fromStatus; }
    public Status     getToStatus()    { return toStatus; }
    public String     getTriggeredBy() { return triggeredBy; }
    public String     getReason()      { return reason; }
    public String     getTimestamp()   { return timestamp; }

    /**
     * Converts this DTO to a Joget {@link FormRow} for persistence
     * via {@code FormDataDao.saveOrUpdate("audit_log", "audit_log", rowSet)}.
     * <p>
     * Column shape (locked by the {@code AuditRowFieldsTest} regression test):
     * <ul>
     *   <li>{@code id}            — random UUID</li>
     *   <li>{@code entity_type}   — {@link EntityType#name()}</li>
     *   <li>{@code entity_id}     — the record id</li>
     *   <li>{@code from_status}   — code, or the literal string {@code "null"}
     *       when {@code fromStatus} is null (initial transitions)</li>
     *   <li>{@code to_status}     — code</li>
     *   <li>{@code triggered_by}  — plugin name</li>
     *   <li>{@code reason}        — free text</li>
     *   <li>{@code timestamp}     — ISO-8601 instant</li>
     * </ul>
     */
    public FormRow toFormRow() {
        FormRow row = new FormRow();
        row.setId(UUID.randomUUID().toString());
        row.setProperty("entity_type", entityType.name());
        row.setProperty("entity_id",   entityId);
        row.setProperty("from_status", fromStatus != null ? fromStatus.getCode() : "null");
        row.setProperty("to_status",   toStatus.getCode());
        row.setProperty("triggered_by", triggeredBy);
        row.setProperty("reason",      reason);
        row.setProperty("timestamp",   timestamp);
        return row;
    }
}
