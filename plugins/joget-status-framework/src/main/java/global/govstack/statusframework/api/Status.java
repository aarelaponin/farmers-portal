package global.govstack.statusframework.api;

/**
 * Marker interface for any status value that participates in a lifecycle.
 * <p>
 * Implementations are typically enums whose values are registered with
 * {@link global.govstack.statusframework.core.StatusFramework} alongside
 * their owning {@link EntityType}.
 * <p>
 * Example:
 * <pre>{@code
 * public enum BankStatus implements Status {
 *     NEW("new", "New"),
 *     IMPORTING("importing", "Importing");
 *
 *     private final String code, label;
 *     BankStatus(String code, String label) { this.code = code; this.label = label; }
 *
 *     @Override public String getCode()  { return code;  }
 *     @Override public String getLabel() { return label; }
 * }
 * }</pre>
 * <p>
 * <b>Status code uniqueness contract:</b> codes are intended to be globally
 * unique across all registered Status implementations. The framework's
 * {@code code → Status} index is shared across entities; if two entities
 * happen to use the same code (e.g. {@code "new"}) they MUST refer to the
 * same enum constant. If two distinct enum constants share a code, the
 * second {@code register()} call wins and the first becomes unreachable
 * via {@link global.govstack.statusframework.core.StatusFramework#fromCode(String)}.
 * <p>
 * Codes should follow the {@code [a-z0-9_]+} convention (lowercase + underscore
 * only, no spaces, no camelCase). Validators on the consuming side typically
 * enforce this.
 */
public interface Status {

    /**
     * @return the enum constant name (e.g. {@code "NEW"}).
     */
    String name();

    /**
     * @return the lowercase database value (e.g. {@code "new"}).
     *         Stored in the entity's {@code c_status} column.
     */
    String getCode();

    /**
     * @return the human-readable label (e.g. {@code "New"}).
     *         Shown in Joget UI dropdowns.
     */
    String getLabel();
}
