package global.govstack.statusframework.api;

/**
 * Marker interface for any entity type that participates in a status lifecycle.
 * <p>
 * Implementations are typically enums, e.g.:
 * <pre>{@code
 * public enum BankEntityType implements EntityType {
 *     STATEMENT("bank_statement"),
 *     BANK_TRX("bank_total_trx");
 *
 *     private final String tableName;
 *     BankEntityType(String tableName) { this.tableName = tableName; }
 *
 *     @Override public String getTableName() { return tableName; }
 * }
 * }</pre>
 * <p>
 * Each entity type is identified by its enum {@link #name()} (used for
 * audit-log writes) and its {@link #getTableName() table name} (the bare
 * Joget form table without the {@code app_fd_} prefix that Joget adds
 * automatically).
 * <p>
 * Equality is based on identity (since EnumMap-backed maps in
 * {@code StatusFramework} use the implementing enum's natural identity).
 * Implementations should NOT override {@code equals/hashCode}; using an enum
 * is the safest choice.
 */
public interface EntityType {

    /**
     * @return the enum constant name (e.g. {@code "STATEMENT"}). Used as
     *         the {@code entity_type} column value in audit-log rows.
     */
    String name();

    /**
     * @return the bare Joget form table name without the {@code app_fd_}
     *         prefix (e.g. {@code "bank_statement"} → Joget reads/writes
     *         from {@code app_fd_bank_statement}).
     */
    String getTableName();
}
