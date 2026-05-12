package global.govstack.formquality.status;

import global.govstack.statusframework.api.EntityType;

/**
 * The single entity type managed by the form-quality runtime.
 * <p>
 * Despite there being only one value, we keep it as an enum (not a constant)
 * for two reasons:
 * <ul>
 *   <li>Consistency with the {@link EntityType} interface contract — every
 *       implementation in the gs-plugins family is an enum.</li>
 *   <li>Forward-compatibility: future expansions (e.g. separate quality
 *       lifecycle for service definitions vs. issue resolutions) slot in as
 *       new enum constants without changing call sites.</li>
 * </ul>
 * <p>
 * The {@code FORM_QUALITY_ISSUE} entity wraps a single record's quality
 * lifecycle: NOT_VALIDATED → ISSUES_DETECTED ↔ VERIFIED, with
 * BLOCKED_FROM_PUBLISH as the terminal "errors prevent activation" state.
 */
public enum QualityEntityType implements EntityType {

    /**
     * One row per (formId, recordId) representing the current quality state
     * of that record. Stored in {@code app_fd_qa_record_status} via the
     * underlying {@code qa_record_status} Joget form.
     */
    FORM_QUALITY_ISSUE("qa_record_status");

    private final String tableName;

    QualityEntityType(String tableName) {
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
