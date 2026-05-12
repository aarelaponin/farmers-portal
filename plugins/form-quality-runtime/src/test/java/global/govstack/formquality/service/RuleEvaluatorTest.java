package global.govstack.formquality.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests the SQL templating in {@link RuleEvaluator#renderRuleSql}.
 * Live JDBC evaluation is covered by integration tests on the dev environment.
 */
public class RuleEvaluatorTest {

    @Test
    public void renderRuleSql_substitutesRecordId() {
        String sql = RuleEvaluator.renderRuleSql(
                "SELECT 1 FROM app_fd_x WHERE id='#recordId#'",
                "myForm", "rec123");
        assertEquals("SELECT 1 FROM app_fd_x WHERE id='rec123'", sql);
    }

    @Test
    public void renderRuleSql_substitutesFormId() {
        String sql = RuleEvaluator.renderRuleSql(
                "SELECT 1 FROM x WHERE form='#formId#'",
                "myForm", "rec123");
        assertEquals("SELECT 1 FROM x WHERE form='myForm'", sql);
    }

    @Test
    public void renderRuleSql_substitutesBoth() {
        String sql = RuleEvaluator.renderRuleSql(
                "SELECT 1 FROM x WHERE form='#formId#' AND id='#recordId#'",
                "f", "r");
        assertEquals("SELECT 1 FROM x WHERE form='f' AND id='r'", sql);
    }

    @Test
    public void renderRuleSql_stripsSingleQuotes() {
        // Defence-in-depth: rule authors are admins but not infallible
        String sql = RuleEvaluator.renderRuleSql(
                "SELECT 1 WHERE id='#recordId#'",
                "f", "rec'; DROP TABLE x;--");
        assertEquals("SELECT 1 WHERE id='rec; DROP TABLE x;--'", sql);
    }

    @Test
    public void renderRuleSql_nullScript_returnsNull() {
        assertNull(RuleEvaluator.renderRuleSql(null, "f", "r"));
    }

    @Test
    public void renderRuleSql_nullPlaceholders_safe() {
        String sql = RuleEvaluator.renderRuleSql(
                "SELECT 1 WHERE id='#recordId#'",
                null, null);
        assertEquals("SELECT 1 WHERE id=''", sql);
    }
}
