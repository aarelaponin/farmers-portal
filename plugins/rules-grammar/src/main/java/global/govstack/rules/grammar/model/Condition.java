package global.govstack.rules.grammar.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a condition expression in Rules Script.
 * Conditions can be comparisons, logical operations, or function calls.
 */
public sealed interface Condition {

    // ========================================================================
    // Logical Operations
    // ========================================================================

    /**
     * Logical AND operation between conditions.
     */
    record And(List<Condition> operands) implements Condition {
        public And {
            Objects.requireNonNull(operands, "operands cannot be null");
            if (operands.size() < 2) {
                throw new IllegalArgumentException("AND requires at least 2 operands");
            }
            operands = List.copyOf(operands);
        }

        public static And of(Condition... conditions) {
            return new And(List.of(conditions));
        }
    }

    /**
     * Logical OR operation between conditions.
     */
    record Or(List<Condition> operands) implements Condition {
        public Or {
            Objects.requireNonNull(operands, "operands cannot be null");
            if (operands.size() < 2) {
                throw new IllegalArgumentException("OR requires at least 2 operands");
            }
            operands = List.copyOf(operands);
        }

        public static Or of(Condition... conditions) {
            return new Or(List.of(conditions));
        }
    }

    /**
     * Logical NOT operation.
     */
    record Not(Condition operand) implements Condition {
        public Not {
            Objects.requireNonNull(operand, "operand cannot be null");
        }
    }

    // ========================================================================
    // Simple Comparisons
    // ========================================================================

    /**
     * Simple comparison: field op value
     * Examples: age >= 18, status = "active"
     */
    record SimpleComparison(FieldRef field, ComparisonOperator operator, Value value) implements Condition {
        public SimpleComparison {
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(operator, "operator cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
        }
    }

    /**
     * IS EMPTY check.
     */
    record IsEmpty(FieldRef field) implements Condition {
        public IsEmpty {
            Objects.requireNonNull(field, "field cannot be null");
        }
    }

    /**
     * IS NOT EMPTY check.
     */
    record IsNotEmpty(FieldRef field) implements Condition {
        public IsNotEmpty {
            Objects.requireNonNull(field, "field cannot be null");
        }
    }

    /**
     * BETWEEN comparison: field BETWEEN low AND high
     */
    record Between(FieldRef field, Value low, Value high) implements Condition {
        public Between {
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(low, "low cannot be null");
            Objects.requireNonNull(high, "high cannot be null");
        }
    }

    /**
     * IN comparison: field IN (value1, value2, ...)
     */
    record In(FieldRef field, List<Value> values) implements Condition {
        public In {
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(values, "values cannot be null");
            if (values.isEmpty()) {
                throw new IllegalArgumentException("IN requires at least one value");
            }
            values = List.copyOf(values);
        }
    }

    /**
     * NOT IN comparison: field NOT IN (value1, value2, ...)
     */
    record NotIn(FieldRef field, List<Value> values) implements Condition {
        public NotIn {
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(values, "values cannot be null");
            if (values.isEmpty()) {
                throw new IllegalArgumentException("NOT IN requires at least one value");
            }
            values = List.copyOf(values);
        }
    }

    // ========================================================================
    // Function Calls
    // ========================================================================

    /**
     * Aggregation function type.
     */
    enum AggregationFunction {
        COUNT, SUM, AVG, MIN, MAX
    }

    /**
     * Grid check function type.
     */
    enum GridCheckFunction {
        HAS_ANY, HAS_ALL, HAS_NONE
    }

    /**
     * Aggregation function call: FUNC(field) [op value]
     * Examples: COUNT(items) > 0, SUM(income) >= 50000
     */
    record Aggregation(
            AggregationFunction function,
            FieldRef field,
            ComparisonOperator operator,
            Value value
    ) implements Condition {

        /**
         * Creates an aggregation with comparison.
         */
        public Aggregation {
            Objects.requireNonNull(function, "function cannot be null");
            Objects.requireNonNull(field, "field cannot be null");
            // operator and value can be null (for standalone aggregation)
        }

        /**
         * Creates an aggregation without comparison.
         */
        public static Aggregation of(AggregationFunction function, FieldRef field) {
            return new Aggregation(function, field, null, null);
        }

        /**
         * Creates an aggregation with comparison.
         */
        public static Aggregation of(
                AggregationFunction function,
                FieldRef field,
                ComparisonOperator operator,
                Value value
        ) {
            return new Aggregation(function, field, operator, value);
        }

        /**
         * Returns true if this aggregation has a comparison.
         */
        public boolean hasComparison() {
            return operator != null && value != null;
        }
    }

    /**
     * Grid check function call: FUNC(field [, values...])
     * Examples: HAS_ANY(documents), HAS_ALL(docs, "id", "passport")
     */
    record GridCheck(
            GridCheckFunction function,
            FieldRef field,
            List<Value> values
    ) implements Condition {

        public GridCheck {
            Objects.requireNonNull(function, "function cannot be null");
            Objects.requireNonNull(field, "field cannot be null");
            values = values != null ? List.copyOf(values) : List.of();
        }

        /**
         * Creates a grid check without values.
         */
        public static GridCheck of(GridCheckFunction function, FieldRef field) {
            return new GridCheck(function, field, List.of());
        }

        /**
         * Creates a grid check with values.
         */
        public static GridCheck of(GridCheckFunction function, FieldRef field, List<Value> values) {
            return new GridCheck(function, field, values);
        }

        /**
         * Returns true if this grid check has value arguments.
         */
        public boolean hasValues() {
            return !values.isEmpty();
        }
    }
}
