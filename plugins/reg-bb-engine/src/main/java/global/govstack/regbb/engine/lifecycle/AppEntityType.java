package global.govstack.regbb.engine.lifecycle;

import global.govstack.statusframework.api.EntityType;

/**
 * Entity type for the subsidy-application lifecycle managed by reg-bb-engine.
 *
 * <p>One value today — {@code APPLICATION} — covering every row in
 * {@code app_fd_subsidy_app_2025}. Each application transitions through the
 * {@link AppLifecycleStatus} state machine driven by
 * {@link global.govstack.statusframework.core.StatusFramework}.
 *
 * <p>Per W3.1 (May 2026), this is the operator-facing coarse-grained
 * lifecycle, separate from the existing fine-grained {@code c_status}
 * column (which carries rules-driven outcomes like {@code auto_approved},
 * {@code auto_rejected}, {@code pending_data_clarification}). The
 * lifecycle state lives in {@code c_lifecycleState}; the audit_log holds
 * the full transition history.
 */
public enum AppEntityType implements EntityType {

    /**
     * A single subsidy application. Stored in
     * {@code app_fd_subsidy_app_2025}. The actual underlying table name is
     * declared here because joget-status-framework reads it to scope its
     * audit lookups.
     */
    APPLICATION("subsidy_app_2025");

    private final String tableName;

    AppEntityType(String tableName) {
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
