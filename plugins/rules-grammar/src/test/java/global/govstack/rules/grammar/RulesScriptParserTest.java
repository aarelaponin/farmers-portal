package global.govstack.rules.grammar;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for RulesScript ANTLR parser.
 * Tests all grammar features including:
 * - Rule definitions with all clause types
 * - Condition expressions with proper operator precedence
 * - All comparison types
 * - Function calls (aggregation and grid checks)
 * - Edge cases and error handling
 */
@DisplayName("RulesScript Parser Tests")
class RulesScriptParserTest {

    private List<String> syntaxErrors;

    /**
     * Parse input and return the script context with error collection.
     */
    private RulesScriptParser.ScriptContext parse(String input) {
        syntaxErrors = new ArrayList<>();

        CharStream charStream = CharStreams.fromString(input);
        RulesScriptLexer lexer = new RulesScriptLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RulesScriptParser parser = new RulesScriptParser(tokens);

        // Collect syntax errors
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                syntaxErrors.add(String.format("Line %d:%d - %s", line, charPositionInLine, msg));
            }
        });

        return parser.script();
    }

    private void assertNoSyntaxErrors() {
        assertTrue(syntaxErrors.isEmpty(),
            "Expected no syntax errors but found: " + String.join(", ", syntaxErrors));
    }

    private void assertHasSyntaxErrors() {
        assertFalse(syntaxErrors.isEmpty(), "Expected syntax errors but found none");
    }

    // ========================================================================
    // Basic Rule Structure Tests
    // ========================================================================

    @Nested
    @DisplayName("Basic Rule Structure")
    class BasicRuleStructureTests {

        @Test
        @DisplayName("Empty script should parse successfully")
        void emptyScript() {
            RulesScriptParser.ScriptContext ctx = parse("");
            assertNoSyntaxErrors();
            assertEquals(0, ctx.rule_().size());
        }

        @Test
        @DisplayName("Simple rule with only name")
        void simpleRuleWithName() {
            String input = """
                RULE "My First Rule"
                TYPE: INCLUSION
                """;
            RulesScriptParser.ScriptContext ctx = parse(input);
            assertNoSyntaxErrors();
            assertEquals(1, ctx.rule_().size());
            assertEquals("\"My First Rule\"", ctx.rule_(0).STRING().getText());
        }

        @Test
        @DisplayName("Multiple rules in one script")
        void multipleRules() {
            String input = """
                RULE "Rule One"
                TYPE: INCLUSION

                RULE "Rule Two"
                TYPE: EXCLUSION

                RULE "Rule Three"
                TYPE: PRIORITY
                """;
            RulesScriptParser.ScriptContext ctx = parse(input);
            assertNoSyntaxErrors();
            assertEquals(3, ctx.rule_().size());
        }

        @Test
        @DisplayName("Rule with all clause types")
        void ruleWithAllClauses() {
            String input = """
                RULE "Complete Rule"
                TYPE: INCLUSION
                CATEGORY: social_protection
                MANDATORY: YES
                ORDER: 10
                WHEN age >= 18
                SCORE: +50
                WEIGHT: 1.5
                PASS MESSAGE: "Eligible for benefits"
                FAIL MESSAGE: "Not eligible"
                """;
            RulesScriptParser.ScriptContext ctx = parse(input);
            assertNoSyntaxErrors();
            assertEquals(1, ctx.rule_().size());
            assertEquals(9, ctx.rule_(0).ruleBody().ruleClause().size());
        }
    }

    // ========================================================================
    // Rule Type Tests
    // ========================================================================

    @Nested
    @DisplayName("Rule Types")
    class RuleTypeTests {

        @ParameterizedTest
        @ValueSource(strings = {"INCLUSION", "EXCLUSION", "PRIORITY", "BONUS"})
        @DisplayName("All rule types should be recognized")
        void allRuleTypes(String ruleType) {
            String input = String.format("""
                RULE "Test Rule"
                TYPE: %s
                """, ruleType);
            RulesScriptParser.ScriptContext ctx = parse(input);
            assertNoSyntaxErrors();
            RulesScriptParser.RuleTypeContext typeCtx =
                ctx.rule_(0).ruleBody().ruleClause(0).ruleType();
            assertNotNull(typeCtx);
        }

        @ParameterizedTest
        @ValueSource(strings = {"inclusion", "Inclusion", "INCLUSION", "InClUsIoN"})
        @DisplayName("Rule types should be case-insensitive")
        void caseInsensitiveRuleTypes(String ruleType) {
            String input = String.format("""
                RULE "Test Rule"
                TYPE: %s
                """, ruleType);
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Boolean Values Tests
    // ========================================================================

    @Nested
    @DisplayName("Boolean Values")
    class BooleanValueTests {

        @ParameterizedTest
        @ValueSource(strings = {"YES", "NO", "TRUE", "FALSE", "1", "0"})
        @DisplayName("All boolean values should be recognized in MANDATORY clause")
        void booleanValuesInMandatory(String boolValue) {
            String input = String.format("""
                RULE "Test Rule"
                TYPE: INCLUSION
                MANDATORY: %s
                """, boolValue);
            parse(input);
            assertNoSyntaxErrors();
        }

        @ParameterizedTest
        @ValueSource(strings = {"yes", "Yes", "YES", "yEs"})
        @DisplayName("Boolean values should be case-insensitive")
        void caseInsensitiveBooleans(String boolValue) {
            String input = String.format("""
                RULE "Test Rule"
                MANDATORY: %s
                """, boolValue);
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Score Clause Tests
    // ========================================================================

    @Nested
    @DisplayName("Score Clause")
    class ScoreClauseTests {

        @Test
        @DisplayName("Positive score with plus sign")
        void positiveScoreWithPlus() {
            String input = """
                RULE "Test"
                SCORE: +100
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Negative score with minus sign")
        void negativeScore() {
            String input = """
                RULE "Test"
                SCORE: -50
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Score without sign")
        void scoreWithoutSign() {
            String input = """
                RULE "Test"
                SCORE: 75
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Decimal score")
        void decimalScore() {
            String input = """
                RULE "Test"
                SCORE: 25.5
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Comparison Tests
    // ========================================================================

    @Nested
    @DisplayName("Comparisons")
    class ComparisonTests {

        @Test
        @DisplayName("Simple equality comparison")
        void equalityComparison() {
            String input = """
                RULE "Test"
                WHEN status = "active"
                """;
            RulesScriptParser.ScriptContext ctx = parse(input);
            assertNoSyntaxErrors();
            assertNotNull(ctx.rule_(0).ruleBody().ruleClause(0).condition());
        }

        @ParameterizedTest
        @ValueSource(strings = {"=", "!=", ">", ">=", "<", "<="})
        @DisplayName("All comparison operators should work")
        void allComparisonOperators(String op) {
            String input = String.format("""
                RULE "Test"
                WHEN age %s 18
                """, op);
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("CONTAINS comparison")
        void containsComparison() {
            String input = """
                RULE "Test"
                WHEN name CONTAINS "John"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("STARTS WITH comparison")
        void startsWithComparison() {
            String input = """
                RULE "Test"
                WHEN email STARTS WITH "admin"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("ENDS WITH comparison")
        void endsWithComparison() {
            String input = """
                RULE "Test"
                WHEN file ENDS WITH ".pdf"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("IS EMPTY comparison")
        void isEmptyComparison() {
            String input = """
                RULE "Test"
                WHEN notes IS EMPTY
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("IS NOT EMPTY comparison")
        void isNotEmptyComparison() {
            String input = """
                RULE "Test"
                WHEN phone IS NOT EMPTY
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("BETWEEN comparison")
        void betweenComparison() {
            String input = """
                RULE "Test"
                WHEN age BETWEEN 18 AND 65
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("IN comparison with value list")
        void inComparison() {
            String input = """
                RULE "Test"
                WHEN status IN ("active", "pending", "approved")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("NOT IN comparison")
        void notInComparison() {
            String input = """
                RULE "Test"
                WHEN status NOT IN ("rejected", "cancelled")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Field reference with dot notation")
        void fieldRefWithDotNotation() {
            String input = """
                RULE "Test"
                WHEN applicant.age >= 18
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Deeply nested field reference")
        void deeplyNestedFieldRef() {
            String input = """
                RULE "Test"
                WHEN household.members.primary.age >= 18
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Logical Operator Tests
    // ========================================================================

    @Nested
    @DisplayName("Logical Operators")
    class LogicalOperatorTests {

        @Test
        @DisplayName("AND operator")
        void andOperator() {
            String input = """
                RULE "Test"
                WHEN age >= 18 AND income < 50000
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("OR operator")
        void orOperator() {
            String input = """
                RULE "Test"
                WHEN status = "employed" OR status = "self-employed"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("NOT operator")
        void notOperator() {
            String input = """
                RULE "Test"
                WHEN NOT status = "rejected"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Combined AND and OR with correct precedence")
        void andOrPrecedence() {
            String input = """
                RULE "Test"
                WHEN age >= 18 AND income < 50000 OR status = "exempt"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Grouped conditions with parentheses")
        void groupedConditions() {
            String input = """
                RULE "Test"
                WHEN (age >= 18 OR hasGuardian = YES) AND income < 50000
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Deeply nested conditions")
        void deeplyNestedConditions() {
            String input = """
                RULE "Test"
                WHEN ((age >= 18 AND age < 65) OR (status = "disabled")) AND income < 50000
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("NOT with grouped condition")
        void notWithGroupedCondition() {
            String input = """
                RULE "Test"
                WHEN NOT (status = "rejected" OR status = "cancelled")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Multiple NOT operators")
        void multipleNotOperators() {
            String input = """
                RULE "Test"
                WHEN NOT age < 18 AND NOT status = "inactive"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Function Call Tests
    // ========================================================================

    @Nested
    @DisplayName("Function Calls")
    class FunctionCallTests {

        @ParameterizedTest
        @ValueSource(strings = {"COUNT", "SUM", "AVG", "MIN", "MAX"})
        @DisplayName("All aggregation functions should work")
        void aggregationFunctions(String func) {
            String input = String.format("""
                RULE "Test"
                WHEN %s(income) > 0
                """, func);
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Aggregation function with comparison")
        void aggregationWithComparison() {
            String input = """
                RULE "Test"
                WHEN SUM(household.members.income) >= 50000
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Aggregation function without comparison")
        void aggregationWithoutComparison() {
            String input = """
                RULE "Test"
                WHEN COUNT(dependents)
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @ParameterizedTest
        @ValueSource(strings = {"HAS_ANY", "HAS_ALL", "HAS_NONE"})
        @DisplayName("All grid check functions should work")
        void gridCheckFunctions(String func) {
            String input = String.format("""
                RULE "Test"
                WHEN %s(documents)
                """, func);
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Grid check with value list")
        void gridCheckWithValueList() {
            String input = """
                RULE "Test"
                WHEN HAS_ANY(documents, "passport", "id_card", "birth_certificate")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("HAS_ALL with multiple values")
        void hasAllWithMultipleValues() {
            String input = """
                RULE "Test"
                WHEN HAS_ALL(requiredDocs, "id", "proof_of_income", "address_proof")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("HAS_NONE with values")
        void hasNoneWithValues() {
            String input = """
                RULE "Test"
                WHEN HAS_NONE(flags, "fraud", "duplicate", "incomplete")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Case Insensitivity Tests
    // ========================================================================

    @Nested
    @DisplayName("Case Insensitivity")
    class CaseInsensitivityTests {

        @Test
        @DisplayName("Keywords should be case-insensitive")
        void caseInsensitiveKeywords() {
            String input = """
                rule "Test"
                type: inclusion
                mandatory: yes
                when age >= 18
                score: +10
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Mixed case keywords")
        void mixedCaseKeywords() {
            String input = """
                Rule "Test"
                Type: Inclusion
                When Age >= 18 And Income < 50000
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Case-insensitive functions")
        void caseInsensitiveFunctions() {
            String input = """
                RULE "Test"
                WHEN count(items) > 0 AND sum(values) >= 100
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Case-insensitive multi-word keywords")
        void caseInsensitiveMultiWordKeywords() {
            String input = """
                RULE "Test"
                WHEN field is not empty AND other starts with "test"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Comments Tests
    // ========================================================================

    @Nested
    @DisplayName("Comments")
    class CommentTests {

        @Test
        @DisplayName("Single line comment")
        void singleLineComment() {
            String input = """
                # This is a comment
                RULE "Test"
                TYPE: INCLUSION
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Comment after rule definition")
        void commentAfterRule() {
            String input = """
                RULE "Test"  # Rule for age verification
                TYPE: INCLUSION
                WHEN age >= 18  # Must be adult
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Multiple comments")
        void multipleComments() {
            String input = """
                # Configuration rules for social protection
                # Version 1.0

                RULE "Age Check"
                # Basic eligibility
                TYPE: INCLUSION
                WHEN age >= 18

                # End of rules
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // String Literals Tests
    // ========================================================================

    @Nested
    @DisplayName("String Literals")
    class StringLiteralTests {

        @Test
        @DisplayName("Double-quoted strings")
        void doubleQuotedStrings() {
            String input = """
                RULE "My Rule Name"
                PASS MESSAGE: "You are eligible"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Single-quoted strings")
        void singleQuotedStrings() {
            String input = """
                RULE 'My Rule Name'
                PASS MESSAGE: 'You are eligible'
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Escaped quotes in strings")
        void escapedQuotes() {
            String input = """
                RULE "Rule with \\"quotes\\""
                PASS MESSAGE: "It's working"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("String with special characters")
        void stringWithSpecialChars() {
            String input = """
                RULE "Test: Special & Characters!"
                PASS MESSAGE: "Amount: $500 (approved)"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Number Literals Tests
    // ========================================================================

    @Nested
    @DisplayName("Number Literals")
    class NumberLiteralTests {

        @Test
        @DisplayName("Integer numbers")
        void integerNumbers() {
            String input = """
                RULE "Test"
                WHEN age >= 18
                ORDER: 100
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Decimal numbers")
        void decimalNumbers() {
            String input = """
                RULE "Test"
                WHEN income >= 50000.50
                WEIGHT: 1.25
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Negative numbers")
        void negativeNumbers() {
            String input = """
                RULE "Test"
                WHEN balance >= -500
                SCORE: -25
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Numbers in value list")
        void numbersInValueList() {
            String input = """
                RULE "Test"
                WHEN code IN (100, 200, 300, 404, 500)
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Complex Scenarios Tests
    // ========================================================================

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarioTests {

        @Test
        @DisplayName("Full eligibility rule")
        void fullEligibilityRule() {
            String input = """
                RULE "Social Protection Eligibility"
                TYPE: INCLUSION
                CATEGORY: social_protection
                MANDATORY: YES
                ORDER: 1
                WHEN (age >= 18 AND age <= 65)
                    AND income < 50000
                    AND NOT status = "employed"
                    AND HAS_ANY(documents, "id_card", "passport")
                SCORE: +100
                WEIGHT: 1.0
                PASS MESSAGE: "You are eligible for social protection benefits"
                FAIL MESSAGE: "You do not meet the eligibility criteria"
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Multiple rules with different types")
        void multipleRulesWithDifferentTypes() {
            String input = """
                RULE "Basic Inclusion"
                TYPE: INCLUSION
                WHEN age >= 18
                SCORE: +50

                RULE "Income Exclusion"
                TYPE: EXCLUSION
                WHEN income > 100000
                FAIL MESSAGE: "Income too high"

                RULE "Disability Priority"
                TYPE: PRIORITY
                WHEN disability = YES
                SCORE: +200

                RULE "Employment Bonus"
                TYPE: BONUS
                WHEN status = "employed"
                SCORE: +25
                """;
            parse(input);
            assertNoSyntaxErrors();
            assertEquals(4, parse(input).rule_().size());
        }

        @Test
        @DisplayName("Complex condition with all operators")
        void complexConditionWithAllOperators() {
            String input = """
                RULE "Complex Condition"
                WHEN (
                    (age >= 18 AND age <= 65)
                    OR disability = YES
                )
                AND income < 75000
                AND status NOT IN ("rejected", "banned")
                AND name IS NOT EMPTY
                AND SUM(household.income) < 100000
                AND HAS_ALL(documents, "id", "proof")
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Grid field references")
        void gridFieldReferences() {
            String input = """
                RULE "Grid Check"
                WHEN household.members.primary.age >= 18
                    AND SUM(household.members.income) > 0
                    AND COUNT(household.members) <= 5
                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Missing rule name should report error")
        void missingRuleName() {
            String input = """
                RULE
                TYPE: INCLUSION
                """;
            parse(input);
            assertHasSyntaxErrors();
        }

        @Test
        @DisplayName("Invalid rule type should report error")
        void invalidRuleType() {
            String input = """
                RULE "Test"
                TYPE: INVALID_TYPE
                """;
            parse(input);
            assertHasSyntaxErrors();
        }

        @Test
        @DisplayName("Unclosed parenthesis should report error")
        void unclosedParenthesis() {
            String input = """
                RULE "Test"
                WHEN (age >= 18 AND income < 50000
                """;
            parse(input);
            assertHasSyntaxErrors();
        }

        @Test
        @DisplayName("Missing value after operator should report error")
        void missingValueAfterOperator() {
            String input = """
                RULE "Test"
                WHEN age >=
                """;
            parse(input);
            assertHasSyntaxErrors();
        }

        @Test
        @DisplayName("Invalid IN clause should report error")
        void invalidInClause() {
            String input = """
                RULE "Test"
                WHEN status IN
                """;
            parse(input);
            assertHasSyntaxErrors();
        }
    }

    // ========================================================================
    // Whitespace Handling Tests
    // ========================================================================

    @Nested
    @DisplayName("Whitespace Handling")
    class WhitespaceHandlingTests {

        @Test
        @DisplayName("Extra whitespace should be ignored")
        void extraWhitespace() {
            String input = """
                RULE    "Test"
                TYPE:    INCLUSION
                WHEN    age   >=    18
                """;
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Tabs should be handled correctly")
        void tabsHandled() {
            String input = "RULE\t\"Test\"\nTYPE:\tINCLUSION\nWHEN\tage\t>=\t18";
            parse(input);
            assertNoSyntaxErrors();
        }

        @Test
        @DisplayName("Mixed newlines and spaces")
        void mixedNewlinesAndSpaces() {
            String input = """


                RULE "Test"

                TYPE: INCLUSION

                WHEN age >= 18

                """;
            parse(input);
            assertNoSyntaxErrors();
        }
    }
}
