package global.govstack.rules.grammar;

import global.govstack.rules.grammar.model.*;
import global.govstack.rules.grammar.model.Condition.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RulesScriptAstBuilder - verifies correct AST construction.
 */
@DisplayName("RulesScript AST Builder Tests")
class RulesScriptAstBuilderTest {

    @Nested
    @DisplayName("Script Parsing")
    class ScriptParsingTests {

        @Test
        @DisplayName("Parse empty script")
        void parseEmptyScript() {
            Script script = RulesScript.parse("").getScriptOrThrow();
            assertTrue(script.isEmpty());
            assertEquals(0, script.size());
        }

        @Test
        @DisplayName("Parse single rule")
        void parseSingleRule() {
            Script script = RulesScript.parse("""
                RULE "Test Rule"
                TYPE: INCLUSION
                """).getScriptOrThrow();

            assertEquals(1, script.size());
            Rule rule = script.rules().get(0);
            assertEquals("Test Rule", rule.name());
            assertEquals(RuleType.INCLUSION, rule.type());
        }

        @Test
        @DisplayName("Parse multiple rules")
        void parseMultipleRules() {
            Script script = RulesScript.parse("""
                RULE "Rule 1"
                TYPE: INCLUSION

                RULE "Rule 2"
                TYPE: EXCLUSION

                RULE "Rule 3"
                TYPE: PRIORITY
                """).getScriptOrThrow();

            assertEquals(3, script.size());
            assertEquals("Rule 1", script.rules().get(0).name());
            assertEquals("Rule 2", script.rules().get(1).name());
            assertEquals("Rule 3", script.rules().get(2).name());
        }
    }

    @Nested
    @DisplayName("Rule Properties")
    class RulePropertiesTests {

        @Test
        @DisplayName("Parse rule with all properties")
        void parseRuleWithAllProperties() {
            Script script = RulesScript.parse("""
                RULE "Complete Rule"
                TYPE: INCLUSION
                CATEGORY: social_protection
                MANDATORY: YES
                ORDER: 10
                WHEN age >= 18
                SCORE: +50
                WEIGHT: 1.5
                PASS MESSAGE: "You passed"
                FAIL MESSAGE: "You failed"
                """).getScriptOrThrow();

            Rule rule = script.rules().get(0);
            assertEquals("Complete Rule", rule.name());
            assertEquals(RuleType.INCLUSION, rule.type());
            assertEquals("social_protection", rule.category());
            assertTrue(rule.isMandatory());
            assertEquals(10, rule.orderOrDefault());
            assertNotNull(rule.condition());
            assertEquals(50.0, rule.scoreOrDefault());
            assertEquals(1.5, rule.weightOrDefault());
            assertEquals("You passed", rule.passMessage());
            assertEquals("You failed", rule.failMessage());
        }

        @Test
        @DisplayName("Parse all rule types")
        void parseAllRuleTypes() {
            Script script = RulesScript.parse("""
                RULE "A" TYPE: INCLUSION
                RULE "B" TYPE: EXCLUSION
                RULE "C" TYPE: PRIORITY
                RULE "D" TYPE: BONUS
                """).getScriptOrThrow();

            assertEquals(RuleType.INCLUSION, script.rules().get(0).type());
            assertEquals(RuleType.EXCLUSION, script.rules().get(1).type());
            assertEquals(RuleType.PRIORITY, script.rules().get(2).type());
            assertEquals(RuleType.BONUS, script.rules().get(3).type());
        }

        @Test
        @DisplayName("Parse negative score")
        void parseNegativeScore() {
            Script script = RulesScript.parse("""
                RULE "Test"
                SCORE: -25
                """).getScriptOrThrow();

            assertEquals(-25.0, script.rules().get(0).scoreOrDefault());
        }
    }

    @Nested
    @DisplayName("Condition Parsing")
    class ConditionParsingTests {

        @Test
        @DisplayName("Parse simple comparison")
        void parseSimpleComparison() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN age >= 18
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(SimpleComparison.class, condition);

            SimpleComparison comp = (SimpleComparison) condition;
            assertEquals("age", comp.field().toString());
            assertEquals(ComparisonOperator.GTE, comp.operator());
            assertInstanceOf(Value.NumberValue.class, comp.value());
            assertEquals(18.0, ((Value.NumberValue) comp.value()).value());
        }

        @Test
        @DisplayName("Parse IS EMPTY comparison")
        void parseIsEmptyComparison() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN notes IS EMPTY
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(IsEmpty.class, condition);
            assertEquals("notes", ((IsEmpty) condition).field().toString());
        }

        @Test
        @DisplayName("Parse IS NOT EMPTY comparison")
        void parseIsNotEmptyComparison() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN phone IS NOT EMPTY
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(IsNotEmpty.class, condition);
            assertEquals("phone", ((IsNotEmpty) condition).field().toString());
        }

        @Test
        @DisplayName("Parse BETWEEN comparison")
        void parseBetweenComparison() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN age BETWEEN 18 AND 65
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(Between.class, condition);

            Between between = (Between) condition;
            assertEquals("age", between.field().toString());
            assertEquals(18.0, ((Value.NumberValue) between.low()).value());
            assertEquals(65.0, ((Value.NumberValue) between.high()).value());
        }

        @Test
        @DisplayName("Parse IN comparison")
        void parseInComparison() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN status IN ("active", "pending")
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(In.class, condition);

            In in = (In) condition;
            assertEquals("status", in.field().toString());
            assertEquals(2, in.values().size());
            assertEquals("active", ((Value.StringValue) in.values().get(0)).value());
            assertEquals("pending", ((Value.StringValue) in.values().get(1)).value());
        }

        @Test
        @DisplayName("Parse NOT IN comparison")
        void parseNotInComparison() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN status NOT IN ("rejected", "cancelled")
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(NotIn.class, condition);
        }

        @Test
        @DisplayName("Parse AND condition")
        void parseAndCondition() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN age >= 18 AND income < 50000
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(And.class, condition);

            And and = (And) condition;
            assertEquals(2, and.operands().size());
        }

        @Test
        @DisplayName("Parse OR condition")
        void parseOrCondition() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN status = "employed" OR status = "self-employed"
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(Or.class, condition);

            Or or = (Or) condition;
            assertEquals(2, or.operands().size());
        }

        @Test
        @DisplayName("Parse NOT condition")
        void parseNotCondition() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN NOT status = "rejected"
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(Not.class, condition);
        }

        @Test
        @DisplayName("Parse grouped condition")
        void parseGroupedCondition() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN (age >= 18 OR hasGuardian = YES) AND income < 50000
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(And.class, condition);

            And and = (And) condition;
            assertEquals(2, and.operands().size());
            assertInstanceOf(Or.class, and.operands().get(0));
        }
    }

    @Nested
    @DisplayName("Function Call Parsing")
    class FunctionCallParsingTests {

        @Test
        @DisplayName("Parse COUNT function")
        void parseCountFunction() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN COUNT(items) > 0
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(Aggregation.class, condition);

            Aggregation agg = (Aggregation) condition;
            assertEquals(AggregationFunction.COUNT, agg.function());
            assertEquals("items", agg.field().toString());
            assertTrue(agg.hasComparison());
            assertEquals(ComparisonOperator.GT, agg.operator());
        }

        @Test
        @DisplayName("Parse SUM function")
        void parseSumFunction() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN SUM(household.income) >= 50000
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(Aggregation.class, condition);

            Aggregation agg = (Aggregation) condition;
            assertEquals(AggregationFunction.SUM, agg.function());
            assertEquals("household.income", agg.field().toString());
        }

        @Test
        @DisplayName("Parse HAS_ANY function")
        void parseHasAnyFunction() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN HAS_ANY(documents, "passport", "id")
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(GridCheck.class, condition);

            GridCheck gc = (GridCheck) condition;
            assertEquals(GridCheckFunction.HAS_ANY, gc.function());
            assertEquals("documents", gc.field().toString());
            assertTrue(gc.hasValues());
            assertEquals(2, gc.values().size());
        }

        @Test
        @DisplayName("Parse HAS_ALL function")
        void parseHasAllFunction() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN HAS_ALL(requiredDocs, "id", "income_proof")
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(GridCheck.class, condition);

            GridCheck gc = (GridCheck) condition;
            assertEquals(GridCheckFunction.HAS_ALL, gc.function());
        }

        @Test
        @DisplayName("Parse HAS_NONE function")
        void parseHasNoneFunction() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN HAS_NONE(flags)
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            assertInstanceOf(GridCheck.class, condition);

            GridCheck gc = (GridCheck) condition;
            assertEquals(GridCheckFunction.HAS_NONE, gc.function());
            assertFalse(gc.hasValues());
        }
    }

    @Nested
    @DisplayName("Field Reference Parsing")
    class FieldRefParsingTests {

        @Test
        @DisplayName("Parse simple field reference")
        void parseSimpleFieldRef() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN age >= 18
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            SimpleComparison comp = (SimpleComparison) condition;

            assertTrue(comp.field().isSimple());
            assertEquals("age", comp.field().root());
        }

        @Test
        @DisplayName("Parse compound field reference")
        void parseCompoundFieldRef() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN household.members.primary.age >= 18
                """).getScriptOrThrow();

            Condition condition = script.rules().get(0).condition();
            SimpleComparison comp = (SimpleComparison) condition;

            assertTrue(comp.field().isCompound());
            assertEquals("household", comp.field().root());
            assertEquals(List.of("household", "members", "primary", "age"), comp.field().path());
        }
    }

    @Nested
    @DisplayName("Value Parsing")
    class ValueParsingTests {

        @Test
        @DisplayName("Parse string value")
        void parseStringValue() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN status = "active"
                """).getScriptOrThrow();

            SimpleComparison comp = (SimpleComparison) script.rules().get(0).condition();
            assertInstanceOf(Value.StringValue.class, comp.value());
            assertEquals("active", ((Value.StringValue) comp.value()).value());
        }

        @Test
        @DisplayName("Parse number value")
        void parseNumberValue() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN income >= 50000.50
                """).getScriptOrThrow();

            SimpleComparison comp = (SimpleComparison) script.rules().get(0).condition();
            assertInstanceOf(Value.NumberValue.class, comp.value());
            assertEquals(50000.50, ((Value.NumberValue) comp.value()).value());
        }

        @Test
        @DisplayName("Parse boolean value")
        void parseBooleanValue() {
            Script script = RulesScript.parse("""
                RULE "Test"
                WHEN disabled = YES
                """).getScriptOrThrow();

            SimpleComparison comp = (SimpleComparison) script.rules().get(0).condition();
            assertInstanceOf(Value.BooleanValue.class, comp.value());
            assertTrue(((Value.BooleanValue) comp.value()).value());
        }
    }

    @Nested
    @DisplayName("Parse Result API")
    class ParseResultApiTests {

        @Test
        @DisplayName("Successful parse result")
        void successfulParseResult() {
            ParseResult result = RulesScript.parse("""
                RULE "Test"
                TYPE: INCLUSION
                """);

            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
            assertTrue(result.getScript().isPresent());
        }

        @Test
        @DisplayName("Failed parse result")
        void failedParseResult() {
            ParseResult result = RulesScript.parse("""
                RULE
                TYPE: INCLUSION
                """);

            assertFalse(result.isSuccess());
            assertTrue(result.hasErrors());
        }

        @Test
        @DisplayName("Validation only")
        void validationOnly() {
            assertTrue(RulesScript.isValid("RULE \"Test\" TYPE: INCLUSION"));
            assertFalse(RulesScript.isValid("RULE TYPE:"));

            var errors = RulesScript.validate("RULE TYPE:");
            assertFalse(errors.isEmpty());
        }
    }
}
