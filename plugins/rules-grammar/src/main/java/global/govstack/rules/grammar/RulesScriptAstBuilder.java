package global.govstack.rules.grammar;

import global.govstack.rules.grammar.model.*;
import global.govstack.rules.grammar.model.Condition.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Visitor that builds domain model objects from ANTLR parse tree.
 * Converts RulesScriptParser contexts into Rule, Condition, and Value objects.
 */
public class RulesScriptAstBuilder extends RulesScriptParserBaseVisitor<Object> {

    // ========================================================================
    // Script & Rule Building
    // ========================================================================

    @Override
    public Script visitScript(RulesScriptParser.ScriptContext ctx) {
        List<Rule> rules = new ArrayList<>();
        for (RulesScriptParser.RuleContext ruleCtx : ctx.rule_()) {
            rules.add(visitRule(ruleCtx));
        }
        return new Script(rules);
    }

    @Override
    public Rule visitRule(RulesScriptParser.RuleContext ctx) {
        String name = unquote(ctx.STRING().getText());
        Rule.Builder builder = Rule.builder(name);

        for (RulesScriptParser.RuleClauseContext clause : ctx.ruleBody().ruleClause()) {
            processRuleClause(clause, builder);
        }

        return builder.build();
    }

    private void processRuleClause(RulesScriptParser.RuleClauseContext ctx, Rule.Builder builder) {
        if (ctx.TYPE() != null) {
            builder.type(visitRuleType(ctx.ruleType()));
        } else if (ctx.CATEGORY() != null) {
            builder.category(ctx.IDENTIFIER().getText());
        } else if (ctx.MANDATORY() != null) {
            builder.mandatory(visitBoolValue(ctx.boolValue()));
        } else if (ctx.ORDER() != null) {
            builder.order(Integer.parseInt(ctx.NUMBER().getText()));
        } else if (ctx.WHEN() != null) {
            builder.condition(visitCondition(ctx.condition()));
        } else if (ctx.SCORE() != null) {
            builder.score(parseScore(ctx));
        } else if (ctx.WEIGHT() != null) {
            builder.weight(Double.parseDouble(ctx.NUMBER().getText()));
        } else if (ctx.PASS_MESSAGE() != null) {
            builder.passMessage(unquote(ctx.STRING().getText()));
        } else if (ctx.FAIL_MESSAGE() != null) {
            builder.failMessage(unquote(ctx.STRING().getText()));
        }
    }

    private double parseScore(RulesScriptParser.RuleClauseContext ctx) {
        double value = Double.parseDouble(ctx.NUMBER().getText());
        if (ctx.MINUS() != null) {
            return -Math.abs(value);
        }
        return value;
    }

    @Override
    public RuleType visitRuleType(RulesScriptParser.RuleTypeContext ctx) {
        if (ctx.INCLUSION() != null) return RuleType.INCLUSION;
        if (ctx.EXCLUSION() != null) return RuleType.EXCLUSION;
        if (ctx.PRIORITY() != null) return RuleType.PRIORITY;
        if (ctx.BONUS() != null) return RuleType.BONUS;
        throw new IllegalStateException("Unknown rule type: " + ctx.getText());
    }

    @Override
    public Boolean visitBoolValue(RulesScriptParser.BoolValueContext ctx) {
        if (ctx.YES() != null || ctx.TRUE() != null) return true;
        if (ctx.NO() != null || ctx.FALSE() != null) return false;
        if (ctx.NUMBER() != null) {
            return !"0".equals(ctx.NUMBER().getText());
        }
        throw new IllegalStateException("Unknown boolean value: " + ctx.getText());
    }

    // ========================================================================
    // Condition Building
    // ========================================================================

    @Override
    public Condition visitCondition(RulesScriptParser.ConditionContext ctx) {
        return visitOrExpr(ctx.orExpr());
    }

    @Override
    public Condition visitOrExpr(RulesScriptParser.OrExprContext ctx) {
        List<RulesScriptParser.AndExprContext> andExprs = ctx.andExpr();
        if (andExprs.size() == 1) {
            return visitAndExpr(andExprs.get(0));
        }

        List<Condition> operands = new ArrayList<>();
        for (RulesScriptParser.AndExprContext andExpr : andExprs) {
            operands.add(visitAndExpr(andExpr));
        }
        return new Or(operands);
    }

    @Override
    public Condition visitAndExpr(RulesScriptParser.AndExprContext ctx) {
        List<RulesScriptParser.UnaryExprContext> unaryExprs = ctx.unaryExpr();
        if (unaryExprs.size() == 1) {
            return visitUnaryExpr(unaryExprs.get(0));
        }

        List<Condition> operands = new ArrayList<>();
        for (RulesScriptParser.UnaryExprContext unaryExpr : unaryExprs) {
            operands.add(visitUnaryExpr(unaryExpr));
        }
        return new And(operands);
    }

    @Override
    public Condition visitUnaryExpr(RulesScriptParser.UnaryExprContext ctx) {
        Condition inner = visitPrimaryExpr(ctx.primaryExpr());
        if (ctx.NOT() != null) {
            return new Not(inner);
        }
        return inner;
    }

    /**
     * Visits a primary expression (grouped, function, or comparison).
     */
    private Condition visitPrimaryExpr(RulesScriptParser.PrimaryExprContext ctx) {
        if (ctx instanceof RulesScriptParser.GroupedConditionContext grouped) {
            return visitCondition(grouped.condition());
        } else if (ctx instanceof RulesScriptParser.FunctionConditionContext func) {
            return visitFunctionCall(func.functionCall());
        } else if (ctx instanceof RulesScriptParser.ComparisonConditionContext comp) {
            return visitComparison(comp.comparison());
        }
        throw new IllegalStateException("Unknown primary expression type: " + ctx.getClass());
    }

    // ========================================================================
    // Comparison Building
    // ========================================================================

    /**
     * Visits a comparison expression.
     */
    private Condition visitComparison(RulesScriptParser.ComparisonContext ctx) {
        if (ctx instanceof RulesScriptParser.IsEmptyComparisonContext isEmpty) {
            return new IsEmpty(visitFieldRef(isEmpty.fieldRef()));
        } else if (ctx instanceof RulesScriptParser.IsNotEmptyComparisonContext isNotEmpty) {
            return new IsNotEmpty(visitFieldRef(isNotEmpty.fieldRef()));
        } else if (ctx instanceof RulesScriptParser.BetweenComparisonContext between) {
            return new Between(
                    visitFieldRef(between.fieldRef()),
                    visitValue(between.value(0)),
                    visitValue(between.value(1))
            );
        } else if (ctx instanceof RulesScriptParser.InComparisonContext in) {
            return new In(
                    visitFieldRef(in.fieldRef()),
                    visitValueList(in.valueList())
            );
        } else if (ctx instanceof RulesScriptParser.NotInComparisonContext notIn) {
            return new NotIn(
                    visitFieldRef(notIn.fieldRef()),
                    visitValueList(notIn.valueList())
            );
        } else if (ctx instanceof RulesScriptParser.SimpleComparisonContext simple) {
            return new SimpleComparison(
                    visitFieldRef(simple.fieldRef()),
                    visitComparisonOp(simple.comparisonOp()),
                    visitValue(simple.value())
            );
        }
        throw new IllegalStateException("Unknown comparison type: " + ctx.getClass());
    }

    @Override
    public ComparisonOperator visitComparisonOp(RulesScriptParser.ComparisonOpContext ctx) {
        if (ctx.EQ() != null) return ComparisonOperator.EQ;
        if (ctx.NEQ() != null) return ComparisonOperator.NEQ;
        if (ctx.GT() != null) return ComparisonOperator.GT;
        if (ctx.GTE() != null) return ComparisonOperator.GTE;
        if (ctx.LT() != null) return ComparisonOperator.LT;
        if (ctx.LTE() != null) return ComparisonOperator.LTE;
        if (ctx.CONTAINS() != null) return ComparisonOperator.CONTAINS;
        if (ctx.STARTS_WITH() != null) return ComparisonOperator.STARTS_WITH;
        if (ctx.ENDS_WITH() != null) return ComparisonOperator.ENDS_WITH;
        throw new IllegalStateException("Unknown comparison operator: " + ctx.getText());
    }

    // ========================================================================
    // Function Call Building
    // ========================================================================

    /**
     * Visits a function call expression.
     */
    private Condition visitFunctionCall(RulesScriptParser.FunctionCallContext ctx) {
        if (ctx instanceof RulesScriptParser.AggregationCallContext agg) {
            return visitAggregationCall(agg);
        } else if (ctx instanceof RulesScriptParser.GridCheckCallContext grid) {
            return visitGridCheckCall(grid);
        }
        throw new IllegalStateException("Unknown function call type: " + ctx.getClass());
    }

    @Override
    public Aggregation visitAggregationCall(RulesScriptParser.AggregationCallContext ctx) {
        AggregationFunction func = visitAggregationFunc(ctx.aggregationFunc());
        FieldRef field = visitFieldRef(ctx.fieldRef());

        if (ctx.comparisonOp() != null && ctx.value() != null) {
            return new Aggregation(
                    func,
                    field,
                    visitComparisonOp(ctx.comparisonOp()),
                    visitValue(ctx.value())
            );
        }
        return Aggregation.of(func, field);
    }

    @Override
    public AggregationFunction visitAggregationFunc(RulesScriptParser.AggregationFuncContext ctx) {
        if (ctx.COUNT() != null) return AggregationFunction.COUNT;
        if (ctx.SUM() != null) return AggregationFunction.SUM;
        if (ctx.AVG() != null) return AggregationFunction.AVG;
        if (ctx.MIN() != null) return AggregationFunction.MIN;
        if (ctx.MAX() != null) return AggregationFunction.MAX;
        throw new IllegalStateException("Unknown aggregation function: " + ctx.getText());
    }

    @Override
    public GridCheck visitGridCheckCall(RulesScriptParser.GridCheckCallContext ctx) {
        GridCheckFunction func = visitGridCheckFunc(ctx.gridCheckFunc());
        FieldRef field = visitFieldRef(ctx.fieldRef());

        if (ctx.valueList() != null) {
            return new GridCheck(func, field, visitValueList(ctx.valueList()));
        }
        return GridCheck.of(func, field);
    }

    @Override
    public GridCheckFunction visitGridCheckFunc(RulesScriptParser.GridCheckFuncContext ctx) {
        if (ctx.HAS_ANY() != null) return GridCheckFunction.HAS_ANY;
        if (ctx.HAS_ALL() != null) return GridCheckFunction.HAS_ALL;
        if (ctx.HAS_NONE() != null) return GridCheckFunction.HAS_NONE;
        throw new IllegalStateException("Unknown grid check function: " + ctx.getText());
    }

    // ========================================================================
    // Field Reference & Value Building
    // ========================================================================

    @Override
    public FieldRef visitFieldRef(RulesScriptParser.FieldRefContext ctx) {
        List<String> path = new ArrayList<>();
        for (var id : ctx.IDENTIFIER()) {
            path.add(id.getText());
        }
        return new FieldRef(path);
    }

    @Override
    public Value visitValue(RulesScriptParser.ValueContext ctx) {
        if (ctx.STRING() != null) {
            return new Value.StringValue(unquote(ctx.STRING().getText()));
        } else if (ctx.NUMBER() != null) {
            return new Value.NumberValue(Double.parseDouble(ctx.NUMBER().getText()));
        } else if (ctx.boolValue() != null) {
            return new Value.BooleanValue(visitBoolValue(ctx.boolValue()));
        } else if (ctx.IDENTIFIER() != null) {
            return new Value.IdentifierValue(ctx.IDENTIFIER().getText());
        }
        throw new IllegalStateException("Unknown value type: " + ctx.getText());
    }

    @Override
    public List<Value> visitValueList(RulesScriptParser.ValueListContext ctx) {
        List<Value> values = new ArrayList<>();
        for (var valueCtx : ctx.value()) {
            values.add(visitValue(valueCtx));
        }
        return values;
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Removes surrounding quotes from a string literal.
     */
    private String unquote(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return text.substring(1, text.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\");
        }
        return text;
    }
}
