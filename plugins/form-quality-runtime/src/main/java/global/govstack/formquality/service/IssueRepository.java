package global.govstack.formquality.service;

import global.govstack.formquality.model.QualityIssue;
import global.govstack.formquality.model.QualityRule;
import global.govstack.formquality.status.QualityEntityType;
import global.govstack.formquality.status.QualityStatus;
import global.govstack.statusframework.api.InvalidTransitionException;
import global.govstack.statusframework.api.Status;
import global.govstack.statusframework.core.StatusFramework;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.time.Instant;
import java.util.*;

/**
 * Persists the result of a rule-evaluation run.
 * <p>
 * For one (formId, recordId) we maintain:
 * <ul>
 *   <li>One row in {@code qa_record_status} = current quality lifecycle state</li>
 *   <li>N rows in {@code qa_issue}, one per active issue, plus history</li>
 * </ul>
 * <p>
 * On every save:
 * <ol>
 *   <li>Mark all currently-active issues for (formId, recordId) as resolved
 *       (sets {@code dateResolved} + {@code isActive='N'}).</li>
 *   <li>Insert one new active issue for each rule that fires this run.</li>
 *   <li>Compute the next quality status (NOT_VALIDATED → VERIFIED if no
 *       issues, → ISSUES_DETECTED if any errors/warnings).</li>
 *   <li>Transition the {@code qa_record_status} row via {@link StatusFramework}
 *       — auto-creates the row on first call, audit-logged.</li>
 * </ol>
 * <p>
 * <b>No DDL, no raw INSERT/UPDATE/DELETE on form tables.</b> All persistence
 * goes through {@link FormDataDao}.
 */
public class IssueRepository {

    private static final String CLASS_NAME = IssueRepository.class.getName();
    private static final String QA_ISSUE_FORM = "qa_issue";
    private static final String QA_RECORD_STATUS_FORM = "qa_record_status";

    private final FormDataDao dao;

    public IssueRepository(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * Persist the outcome of one evaluation run.
     */
    public void persistRun(String serviceId, String formId, String recordId,
                           List<QualityIssue> liveIssues) {
        // 1. resolve any previously-active issues for this record
        resolveExisting(formId, recordId);

        // 2. write fresh issue rows
        for (QualityIssue issue : liveIssues) writeIssue(issue);

        // 3. update lifecycle status
        Counts c = countBySeverity(liveIssues);
        QualityStatus next = (c.errors == 0 && c.warnings == 0)
                ? QualityStatus.VERIFIED
                : QualityStatus.ISSUES_DETECTED;

        upsertRecordStatus(serviceId, formId, recordId, next, c.errors, c.warnings);
    }

    // ── steps ────────────────────────────────────────────────────────────

    void resolveExisting(String formId, String recordId) {
        String condition = "WHERE e.customProperties.formId = ? "
                         + "AND   e.customProperties.recordId = ? "
                         + "AND   e.customProperties.isActive = ?";
        Object[] args = { formId, recordId, "Y" };
        FormRowSet existing = dao.find(QA_ISSUE_FORM, QA_ISSUE_FORM,
                condition, args, null, false, null, null);
        if (existing == null || existing.isEmpty()) return;

        String now = Instant.now().toString();
        FormRowSet toSave = new FormRowSet();
        for (FormRow row : existing) {
            row.setProperty("dateResolved", now);
            row.setProperty("isActive", "N");
            toSave.add(row);
        }
        dao.saveOrUpdate(QA_ISSUE_FORM, QA_ISSUE_FORM, toSave);
        LogUtil.info(CLASS_NAME, "Resolved " + toSave.size()
                + " prior issue(s) for " + formId + "/" + recordId);
    }

    void writeIssue(QualityIssue issue) {
        FormRow row = new FormRow();
        row.setId(UUID.randomUUID().toString());
        row.setProperty("serviceId",      nullSafe(issue.getServiceId()));
        row.setProperty("formId",         nullSafe(issue.getFormId()));
        row.setProperty("recordId",       nullSafe(issue.getRecordId()));
        row.setProperty("ruleCode",       nullSafe(issue.getRuleCode()));
        row.setProperty("tabCode",        nullSafe(issue.getTabCode()));
        row.setProperty("severity",       issue.getSeverity().name());
        row.setProperty("affectedFields", nullSafe(issue.getAffectedFields()));
        row.setProperty("message",        nullSafe(issue.getMessage()));
        row.setProperty("dateRaised",     Instant.now().toString());
        row.setProperty("dateResolved",   "");
        row.setProperty("isActive",       "Y");
        FormRowSet rs = new FormRowSet();
        rs.add(row);
        dao.saveOrUpdate(QA_ISSUE_FORM, QA_ISSUE_FORM, rs);
    }

    void upsertRecordStatus(String serviceId, String formId, String recordId,
                            QualityStatus targetStatus, int errors, int warnings) {
        // Composite key: formId + ':' + recordId. Stable, idempotent.
        String statusRowId = formId + ":" + recordId;

        FormRow current = dao.load(QA_RECORD_STATUS_FORM, QA_RECORD_STATUS_FORM, statusRowId);
        if (current == null) {
            // First eval — create the row in NOT_VALIDATED so the framework
            // can transition it to the next state via canTransition rules.
            FormRow row = new FormRow();
            row.setId(statusRowId);
            row.setProperty("serviceId", nullSafe(serviceId));
            row.setProperty("formId", formId);
            row.setProperty("recordId", recordId);
            row.setProperty("status", QualityStatus.NOT_VALIDATED.getCode());
            row.setProperty("errorCount", "0");
            row.setProperty("warningCount", "0");
            row.setProperty("lastEvaluated", Instant.now().toString());
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            dao.saveOrUpdate(QA_RECORD_STATUS_FORM, QA_RECORD_STATUS_FORM, rs);
            current = row;  // local handle for the transition step below
        }

        // Now transition via StatusFramework — this also writes the audit row.
        try {
            StatusFramework.transition(dao, QA_RECORD_STATUS_FORM,
                    QualityEntityType.FORM_QUALITY_ISSUE, statusRowId,
                    targetStatus,
                    "form-quality-runtime",
                    errors + " errors, " + warnings + " warnings");
        } catch (InvalidTransitionException e) {
            // The lifecycle allows NOT_VALIDATED→{VERIFIED,ISSUES_DETECTED} and
            // VERIFIED↔ISSUES_DETECTED, so this normally shouldn't fire. If it
            // does (e.g. record is BLOCKED_FROM_PUBLISH), log + skip transition
            // — issues themselves are already persisted.
            LogUtil.warn(CLASS_NAME, "Skipped status transition for "
                    + statusRowId + ": " + e.getMessage());
        }

        // After successful transition, refresh the count fields. We re-load
        // the row (the framework just touched it) and patch the two counters.
        FormRow refreshed = dao.load(QA_RECORD_STATUS_FORM, QA_RECORD_STATUS_FORM, statusRowId);
        if (refreshed != null) {
            refreshed.setProperty("errorCount",   String.valueOf(errors));
            refreshed.setProperty("warningCount", String.valueOf(warnings));
            refreshed.setProperty("lastEvaluated", Instant.now().toString());
            FormRowSet rs = new FormRowSet();
            rs.add(refreshed);
            dao.saveOrUpdate(QA_RECORD_STATUS_FORM, QA_RECORD_STATUS_FORM, rs);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static class Counts {
        int errors, warnings, infos;
    }

    private static Counts countBySeverity(List<QualityIssue> issues) {
        Counts c = new Counts();
        for (QualityIssue i : issues) {
            QualityRule.Severity s = i.getSeverity();
            if (s == QualityRule.Severity.ERROR)   c.errors++;
            else if (s == QualityRule.Severity.WARNING) c.warnings++;
            else c.infos++;
        }
        return c;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
