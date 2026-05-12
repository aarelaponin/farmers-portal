package global.govstack.formquality.service;

import global.govstack.formquality.model.QualityIssue;
import global.govstack.formquality.model.QualityRule;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates quality rules against the database.
 * <p>
 * <b>Day 2 contract:</b> {@code ruleScript} is interpreted as raw SQL.
 * The convention:
 * <ul>
 *   <li>If the SQL returns <b>zero rows</b>, the rule passes — no issue.</li>
 *   <li>If the SQL returns <b>one or more rows</b>, the rule fails — emit
 *       one {@link QualityIssue} regardless of how many rows came back
 *       (we just need a yes/no answer).</li>
 * </ul>
 * <p>
 * Two placeholders are substituted in the SQL before execution:
 * <ul>
 *   <li>{@code #recordId#} → the record being evaluated</li>
 *   <li>{@code #formId#}   → the form being evaluated</li>
 * </ul>
 * <p>
 * <b>Day 4 plan:</b> the SQL path will be wrapped behind a JRE-DSL compile
 * step so authors write {@code FARMER.programName != ''} instead of raw SQL.
 * The IO contract here stays the same.
 * <p>
 * Reads use the existing Joget {@code setupDataSource} obtained from the
 * Spring context via {@link AppUtil} — no separate JDBC config. (Verified
 * against jw-community {@code commonsApplicationContext.xml} — that's the
 * canonical bean used by {@code FormDataDaoImpl}, {@code DataListService},
 * and the governance checks. {@code jwDataSource} doesn't exist.)
 */
public class RuleEvaluator {

    private static final String CLASS_NAME = RuleEvaluator.class.getName();
    private static final String DATA_SOURCE_BEAN = "setupDataSource";

    /**
     * Evaluate every rule against (formId, recordId). Returns the set of
     * issues that fired (rules whose SQL returned ≥1 row). Issues are
     * returned in input order.
     *
     * @param serviceId the service these rules belong to (for issue rows)
     * @param formId    the form being saved
     * @param recordId  the record being saved
     * @param rules     list of rules to evaluate
     */
    public List<QualityIssue> evaluate(String serviceId, String formId, String recordId,
                                       List<QualityRule> rules) {
        List<QualityIssue> issues = new ArrayList<>();
        if (rules == null || rules.isEmpty()) return issues;

        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean(DATA_SOURCE_BEAN);

        try (Connection conn = ds.getConnection()) {
            for (QualityRule rule : rules) {
                String sql = renderRuleSql(rule.getRuleScript(), formId, recordId);
                if (sql == null || sql.trim().isEmpty()) {
                    LogUtil.warn(CLASS_NAME, "Skipping rule " + rule.getRuleCode()
                            + " — empty ruleScript");
                    continue;
                }
                if (fires(conn, sql, rule)) {
                    issues.add(toIssue(rule, serviceId, formId, recordId));
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Rule evaluation failed for record "
                    + formId + "/" + recordId);
        }
        return issues;
    }

    /**
     * Substitutes the two well-known placeholders. Quoting is the rule
     * author's responsibility — the SQL is admin-curated, not user input.
     */
    static String renderRuleSql(String ruleScript, String formId, String recordId) {
        if (ruleScript == null) return null;
        return ruleScript
                .replace("#recordId#", safe(recordId))
                .replace("#formId#",   safe(formId));
    }

    private static String safe(String s) {
        // Conservative: strip single quotes. Rule authors with admin
        // privilege can do worse than this anyway, but defence-in-depth.
        return s == null ? "" : s.replace("'", "");
    }

    private boolean fires(Connection conn, String sql, QualityRule rule) {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();      // any row returned ⇒ rule violated
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Rule " + rule.getRuleCode()
                    + " errored — treating as PASS to avoid false positives. "
                    + "SQL was: " + truncate(sql, 200) + " | error: " + e.getMessage());
            return false;
        }
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "…");
    }

    private static QualityIssue toIssue(QualityRule rule, String serviceId,
                                        String formId, String recordId) {
        return new QualityIssue(serviceId, formId, recordId,
                rule.getRuleCode(), rule.getTabCode(),
                rule.getSeverity(), rule.getAffectedFields(), rule.getMessage());
    }
}
