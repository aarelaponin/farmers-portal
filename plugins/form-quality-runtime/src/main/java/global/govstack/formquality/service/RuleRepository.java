package global.govstack.formquality.service;

import global.govstack.formquality.model.QualityRule;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads quality rules from the unified {@code mm_determinant} table
 * (filtered to {@code scope='quality'}). Per ADR-031 Slice C: legacy
 * {@code qa_rule} reads are retired in favour of the unified store.
 * Service primary-form lookup still goes through {@code qa_service} —
 * that table has no equivalent in mm_* yet (deferred to a later slice).
 * <p>
 * <b>No DDL, no raw SQL on form tables.</b> All reads go through
 * {@link FormDataDao} per the gam-plugins methodology rule.
 *
 * <p><b>Column mapping (qa_rule → mm_determinant)</b>:
 * <ul>
 *   <li>{@code ruleCode} → {@code code}</li>
 *   <li>{@code message}  → {@code failMessage}</li>
 *   <li>{@code ruleScript} → {@code ruleJson}</li>
 *   <li>{@code severity / serviceId / tabCode / affectedFields}: same
 *       names, lower-cased severity values ('error' / 'warning' instead
 *       of 'ERROR' / 'WARNING' — handled in {@link #toRule}).</li>
 *   <li>{@code isActive}: removed; mm_determinant rows are always
 *       considered active. Filtering happens via {@code scope='quality'}
 *       and {@code triggerOn='save'} which existing rows always satisfy.</li>
 * </ul>
 */
public class RuleRepository {

    private static final String CLASS_NAME = RuleRepository.class.getName();
    private static final String MM_DETERMINANT_FORM = "mm_determinant";
    private static final String QA_SERVICE_FORM = "qa_service";

    private final FormDataDao dao;

    public RuleRepository(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * Looks up the primary form id configured for a service. Returns null if
     * the service is unknown or has no primaryFormId. Still reads from
     * {@code qa_service} — that table is not migrated yet.
     */
    public String findPrimaryFormId(String serviceId) {
        if (serviceId == null || serviceId.isEmpty()) return null;
        String condition = "WHERE e.customProperties.serviceId = ?";
        Object[] args = new Object[] { serviceId };
        FormRowSet rows = dao.find(QA_SERVICE_FORM, QA_SERVICE_FORM,
                condition, args, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        String pf = rows.get(0).getProperty("primaryFormId");
        return (pf == null || pf.isEmpty()) ? null : pf;
    }

    /**
     * Returns all quality-scope rules for the given serviceId from
     * {@code mm_determinant}, sorted by tabCode for stable UI grouping.
     */
    public List<QualityRule> findActiveRulesForService(String serviceId) {
        if (serviceId == null || serviceId.isEmpty()) return Collections.emptyList();

        // Filter to scope=quality so eligibility / decision_to_status /
        // budget_amount rules don't leak into the form-quality runtime.
        String condition = "WHERE e.customProperties.serviceId = ? "
                         + "AND   e.customProperties.scope = ?";
        Object[] args = new Object[] { serviceId, "quality" };
        FormRowSet rows = dao.find(MM_DETERMINANT_FORM, MM_DETERMINANT_FORM,
                condition, args,
                "tabCode",       // sort column
                false,           // ascending
                null, null);     // no paging

        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        List<QualityRule> rules = new ArrayList<>(rows.size());
        for (FormRow row : rows) {
            try {
                rules.add(toRule(row));
            } catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME, "Skipping malformed rule row " + row.getId()
                        + ": " + e.getMessage());
            }
        }
        return rules;
    }

    private static QualityRule toRule(FormRow r) {
        // mm_determinant stores severity lower-cased ('error'/'warning')
        // per ADR-031 D44; the Severity enum is upper-case. Normalise.
        String rawSeverity = r.getProperty("severity");
        String severityStr = (rawSeverity == null) ? "" : rawSeverity.trim().toUpperCase();
        QualityRule.Severity severity;
        try {
            severity = QualityRule.Severity.valueOf(severityStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown severity '" + rawSeverity
                    + "' on rule " + r.getProperty("code"));
        }
        return new QualityRule(
                r.getId(),
                r.getProperty("serviceId"),
                emptyToNull(r.getProperty("tabCode")),
                r.getProperty("code"),                   // mm_determinant.code (was qa_rule.ruleCode)
                severity,
                emptyToNull(r.getProperty("affectedFields")),
                r.getProperty("ruleJson"),               // mm_determinant.ruleJson (was qa_rule.ruleScript)
                r.getProperty("failMessage"));           // mm_determinant.failMessage (was qa_rule.message)
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
