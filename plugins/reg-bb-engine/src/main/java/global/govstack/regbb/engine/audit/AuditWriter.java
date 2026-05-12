package global.govstack.regbb.engine.audit;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Per ADR-007 / spec §7.4: writes one row to {@code app_fd_reg_bb_eval_audit}
 * per {@code DeterminantEvaluator.evaluate()} call.
 *
 * <p><b>Hard rule honoured:</b> writes go through Joget's
 * {@link FormDataDao#saveOrUpdate} — never raw SQL on Joget tables.
 *
 * <p><b>Failure policy:</b> audit is observability, not consensus. Every
 * write is wrapped in {@code try/catch}; any failure is logged and
 * swallowed so the eval result reaches the caller intact. Operators may
 * lose audit rows if the audit table itself goes wrong, but they will
 * never get a bad eval outcome because of an audit issue.
 *
 * <p>Slice 1 ships synchronous audit writes — one DB round-trip per evaluate.
 * If profiling shows the audit hop is non-negligible, a future slice can
 * batch + flush asynchronously (a {@code BlockingQueue} drained by a
 * scheduler) without changing this writer's contract.
 */
public final class AuditWriter {

    private static final String CLASS_NAME  = AuditWriter.class.getName();
    private static final String FORM_ID     = "reg_bb_eval_audit";
    private static final String TABLE_NAME  = "reg_bb_eval_audit";
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private AuditWriter() { /* static helpers only */ }

    /**
     * Persist one audit row.
     *
     * @param determinantCode the determinant id evaluated
     * @param ctx             evaluation context (applicationId, data snapshot, correlationId, currentUsername, ...)
     * @param result          final EvalResult returned by the evaluator
     * @param ruleSource      verbatim ruleJson at evaluation time (snapshot for traceability)
     * @param startedAtMs     System.currentTimeMillis() when evaluate() was entered
     * @param finishedAtMs    System.currentTimeMillis() when evaluate() returned
     */
    public static void write(String determinantCode,
                             EvalContext ctx,
                             EvalResult result,
                             String ruleSource,
                             long startedAtMs,
                             long finishedAtMs) {
        try {
            FormRow row = new FormRow();
            row.setId(UuidGenerator.getInstance().getUuid());
            // Joget's FormDataDao.saveOrUpdate doesn't auto-populate
            // datecreated/datemodified when the FormRow has an explicit id
            // (it treats that as "caller-provided, persist verbatim"). Set
            // both metadata fields here so the audit list view's "When"
            // column actually has a value.
            Date now = new Date();
            row.setDateCreated(now);
            row.setDateModified(now);
            row.setCreatedBy(nz(ctx == null ? null : ctx.currentUsername));
            row.setModifiedBy(nz(ctx == null ? null : ctx.currentUsername));
            row.setProperty("application_id",   nz(ctx == null ? null : ctx.applicationId));
            // Snapshot applicant identifiers from ctx.data — operators pivot
            // between the audit list and the operator-review list using these.
            // Stored verbatim so the audit row survives the application's
            // later deletion or modification (CQRS-style audit immutability).
            row.setProperty("applicant_name",     dataValue(ctx, "full_name"));
            row.setProperty("national_id",        dataValue(ctx, "national_id"));
            row.setProperty("applied_programme",  dataValue(ctx, "applied_programme"));
            row.setProperty("determinant_code", nz(determinantCode));
            row.setProperty("evaluator",        nz(result == null ? null : result.evaluator));
            row.setProperty("outcome",          result == null || result.outcome == null
                    ? "UNKNOWN" : result.outcome.name());
            row.setProperty("error_cause",      nz(result == null ? null : result.errorCause));
            row.setProperty("correlation_id",   nz(ctx == null ? null : ctx.correlationId));
            row.setProperty("current_username", nz(ctx == null ? null : ctx.currentUsername));
            row.setProperty("service_id",       nz(ctx == null ? null : ctx.serviceId));
            row.setProperty("service_version",  nz(ctx == null ? null : ctx.serviceVersion));
            row.setProperty("rule_version",     nz(ruleSource));
            synchronized (ISO_UTC) {
                row.setProperty("eval_started_at", ISO_UTC.format(new Date(startedAtMs)));
            }
            row.setProperty("eval_duration_ms", String.valueOf(Math.max(0L, finishedAtMs - startedAtMs)));
            row.setProperty("inputs_json",      buildInputsSnapshot(ctx, ruleSource));
            row.setProperty("outputs_json",     buildOutputsSnapshot(result));

            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            global.govstack.regbb.engine.support.RowWriter.save(FORM_ID, TABLE_NAME, rs);
        } catch (Throwable t) {
            // Swallow — audit failure must never propagate to the eval caller.
            LogUtil.warn(CLASS_NAME, "audit write failed for " + determinantCode + ": " + t.getMessage());
        }
    }

    /**
     * Snapshot the subset of {@code ctx.data} that the rule actually
     * referenced. We don't introspect the AST in slice 1 — full data
     * dump is cheap enough at this scale and gives operators the most
     * complete picture for forensic debugging. If applicant data ever
     * grows large, switch to a referenced-fields-only snapshot.
     */
    private static String buildInputsSnapshot(EvalContext ctx, String ruleSource) {
        if (ctx == null || ctx.data == null || ctx.data.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : ctx.data.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            // Skip large blobs that would bloat the audit row — file values,
            // JSON columns, the eligibility_outcome column itself. Anything
            // over 500 chars gets an ellipsis.
            String vs = v == null ? null : v.toString();
            if (vs != null && vs.length() > 500) vs = vs.substring(0, 500) + "…[truncated]";
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(k)).append('"').append(':');
            if (vs == null) sb.append("null");
            else sb.append('"').append(escape(vs)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String buildOutputsSnapshot(EvalResult result) {
        if (result == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"outcome\":\"").append(result.outcome == null ? "UNKNOWN" : result.outcome.name()).append("\"");
        sb.append(",\"evaluator\":\"").append(escape(nz(result.evaluator))).append("\"");
        if (result.numericValue != null) {
            sb.append(",\"numericValue\":").append(result.numericValue);
        }
        if (result.actionTarget != null) {
            sb.append(",\"actionTarget\":\"").append(escape(result.actionTarget)).append("\"");
        }
        if (result.errorCause != null) {
            sb.append(",\"errorCause\":\"").append(escape(result.errorCause)).append("\"");
        }
        sb.append('}');
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Pull a string value from {@code ctx.data}, case-tolerant. Returns ""
     *  when not present so the audit row never has nulls in identifier
     *  columns. */
    private static String dataValue(EvalContext ctx, String key) {
        if (ctx == null || ctx.data == null || key == null) return "";
        Object v = ctx.data.get(key);
        if (v == null) v = ctx.data.get(key.toLowerCase());
        return v == null ? "" : v.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
