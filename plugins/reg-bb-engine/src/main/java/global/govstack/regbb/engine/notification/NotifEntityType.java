package global.govstack.regbb.engine.notification;

import global.govstack.statusframework.api.EntityType;

/**
 * Entity type for the notification lifecycle managed by reg-bb-engine.
 *
 * <p>One value today — {@code NOTIFICATION} — covering every email or SMS
 * dispatched by the platform. Each dispatch attempt corresponds to one
 * row in {@code app_fd_notification_queue}, transitioned through the
 * {@link NotifStatus} state machine by {@link global.govstack.statusframework.core.StatusFramework}.
 *
 * <p>Per the joget-status-framework convention, this is an enum (not a
 * constant) so future channel-specific lifecycles (e.g. distinct shapes
 * for SMS-only or push-notification flows) can slot in as new enum
 * constants without changing call sites.
 */
public enum NotifEntityType implements EntityType {

    /**
     * A single notification dispatch — one email or one SMS, IMMEDIATE or
     * SCHEDULED. Stored in {@code app_fd_notification_queue}.
     */
    NOTIFICATION("notification_queue");

    private final String tableName;

    NotifEntityType(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public String toString() {
        return name();
    }
}
