package global.govstack.regbb.engine.lifecycle;

/**
 * One-way mapping between the existing fine-grained {@code c_status}
 * values (rules-driven, in use since 2025) and the new coarse-grained
 * {@link AppLifecycleStatus} values introduced by W3.1.
 *
 * <p>The two columns coexist. {@code c_status} remains the source of truth
 * for rules-driven decisions (and for every dashboard / report that
 * currently filters on it). {@code c_lifecycleState} is the operator-facing
 * audit-trail-anchored phase, computed from {@code c_status} via this
 * mapper at write time.
 *
 * <p>If a future c_status value isn't covered here, it falls through to
 * {@code SUBMITTED} (safest assumption — at least we know the application
 * exists). Add new mappings as the c_status vocabulary grows.
 */
public final class AppLifecycleMapper {

    private AppLifecycleMapper() {}

    /**
     * Map a fine-grained {@code c_status} value to a coarse lifecycle state.
     *
     * @param cStatus the existing c_status value (case-insensitive). May
     *                be null/empty — returns {@link AppLifecycleStatus#SUBMITTED}
     *                in that case (a row exists but has no rules outcome yet).
     */
    public static AppLifecycleStatus fromStatus(String cStatus) {
        if (cStatus == null || cStatus.trim().isEmpty()) {
            // Row exists but rules haven't fired yet — citizen has just
            // submitted, EligibilityProcessingWorker hasn't picked it up.
            return AppLifecycleStatus.SUBMITTED;
        }
        String s = cStatus.trim().toLowerCase();
        switch (s) {
            case "approved":
            case "auto_approved":
                return AppLifecycleStatus.APPROVED;
            case "rejected":
            case "auto_rejected":
                return AppLifecycleStatus.REJECTED;
            case "pending_review":
            case "pending_data_clarification":
            case "pending":
                return AppLifecycleStatus.PENDING_REVIEW;
            case "withdrawn":
            case "cancelled":
                return AppLifecycleStatus.WITHDRAWN;
            case "draft":
                return AppLifecycleStatus.DRAFT;
            case "submitted":
                return AppLifecycleStatus.SUBMITTED;
            case "under_review":
            case "in_review":
                return AppLifecycleStatus.UNDER_REVIEW;
            default:
                // Unknown / new c_status — default to SUBMITTED to avoid
                // dropping rows out of the dashboard. Log so the operator
                // can investigate.
                return AppLifecycleStatus.SUBMITTED;
        }
    }
}
