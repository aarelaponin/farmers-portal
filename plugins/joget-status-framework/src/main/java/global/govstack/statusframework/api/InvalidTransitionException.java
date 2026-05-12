package global.govstack.statusframework.api;

/**
 * Thrown when a caller attempts to transition an entity to a status that
 * is not allowed by the registered transition map.
 * <p>
 * Carries enough context (entity type, record id, from/to statuses) for
 * downstream error handlers and audit rendering.
 */
public class InvalidTransitionException extends Exception {

    private final EntityType entityType;
    private final String entityId;
    private final Status fromStatus;
    private final Status toStatus;

    public InvalidTransitionException(EntityType entityType, String entityId,
                                      Status fromStatus, Status toStatus) {
        super(buildMessage(entityType, entityId, fromStatus, toStatus));
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Status getFromStatus() {
        return fromStatus;
    }

    public Status getToStatus() {
        return toStatus;
    }

    private static String buildMessage(EntityType entityType, String entityId,
                                       Status fromStatus, Status toStatus) {
        return "Invalid transition for "
                + (entityType != null ? entityType.name() : "null")
                + " " + entityId
                + ": "
                + (fromStatus != null ? fromStatus.name() : "null")
                + " -> "
                + (toStatus != null ? toStatus.name() : "null");
    }
}
