package global.govstack.identity.model;

/**
 * One row from {@code app_resolver_field_map}. Defines how a single source-field
 * value is exposed as a target-field on the consuming form.
 *
 * <p>See {@code _design/identity-resolver-design.md} §3.2 for column semantics.
 */
public final class FieldMap {

    public enum ResolveStrategy {
        /** Always overwrite the target's current value. */
        OVERWRITE,
        /** Only set the target if it is empty (never overwrite an existing value). */
        FILL_IF_EMPTY,
        /** Skip if the user has visibly edited the target since last resolve. */
        IGNORE_IF_USER_EDITED
    }

    private final String rowId;
    private final String resolverConfigId;
    private final String sourceFieldId;
    private final String targetFieldId;
    private final String chainedSourceFormId;
    private final String chainedJoinField;
    private final boolean readonlyAfterResolve;
    private final ResolveStrategy resolveStrategy;
    private final int displayOrder;
    private final boolean active;

    public FieldMap(String rowId, String resolverConfigId,
                    String sourceFieldId, String targetFieldId,
                    String chainedSourceFormId, String chainedJoinField,
                    boolean readonlyAfterResolve, ResolveStrategy resolveStrategy,
                    int displayOrder, boolean active) {
        this.rowId = rowId;
        this.resolverConfigId = resolverConfigId;
        this.sourceFieldId = sourceFieldId;
        this.targetFieldId = targetFieldId;
        this.chainedSourceFormId = chainedSourceFormId;
        this.chainedJoinField = chainedJoinField;
        this.readonlyAfterResolve = readonlyAfterResolve;
        this.resolveStrategy = resolveStrategy;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public String  getRowId()                  { return rowId; }
    public String  getResolverConfigId()       { return resolverConfigId; }
    public String  getSourceFieldId()          { return sourceFieldId; }
    public String  getTargetFieldId()          { return targetFieldId; }
    public String  getChainedSourceFormId()    { return chainedSourceFormId; }
    public String  getChainedJoinField()       { return chainedJoinField; }
    public boolean isReadonlyAfterResolve()    { return readonlyAfterResolve; }
    public ResolveStrategy getResolveStrategy(){ return resolveStrategy; }
    public int     getDisplayOrder()           { return displayOrder; }
    public boolean isActive()                  { return active; }

    public boolean isChained() {
        return chainedSourceFormId != null && !chainedSourceFormId.isEmpty();
    }
}
