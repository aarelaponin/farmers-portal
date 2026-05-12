package global.govstack.formquality.status;

import global.govstack.statusframework.api.Status;

/**
 * Quality-validation lifecycle states for a record under quality management.
 * <p>
 * <b>Lifecycle:</b>
 * <pre>
 *
 *                    ┌───────────────┐
 *                    │ NOT_VALIDATED │  initial — record exists but rules
 *                    └───────┬───────┘             have not yet run
 *                            │
 *                            ▼ on save (post-processor runs all rules)
 *                  ┌─────────┴──────────┐
 *                  ▼                    ▼
 *         ┌──────────────┐      ┌──────────────────┐
 *         │   VERIFIED   │      │ ISSUES_DETECTED  │
 *         └──────┬───────┘      └────────┬─────────┘
 *                │                       │
 *                │                       │ on next save
 *                │                       │ (re-run rules)
 *                │       ┌───────────────┘
 *                │       │
 *                ▼       ▼
 *         ┌──────────────────────────┐
 *         │   BLOCKED_FROM_PUBLISH   │  any gated transition (e.g. status →
 *         │      (terminal)          │  ACTIVE) attempted while ERROR-severity
 *         └──────────────────────────┘  issues exist
 *
 *         Any state can return to NOT_VALIDATED via "force re-evaluate"
 *         (admin-only) — used after rule library changes.
 * </pre>
 * <p>
 * <b>Codes:</b> lowercase + underscore, used in the {@code c_status} column
 * of {@code app_fd_qa_record_status}. Globally unique across the
 * joget-status-framework registry per the {@link Status} contract.
 */
public enum QualityStatus implements Status {

    NOT_VALIDATED       ("not_validated",        "Not Validated"),
    VERIFIED            ("verified",             "Verified"),
    ISSUES_DETECTED     ("issues_detected",      "Issues Detected"),
    BLOCKED_FROM_PUBLISH("blocked_from_publish", "Blocked From Publish");

    private final String code;
    private final String label;

    QualityStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return code;
    }
}
