package global.govstack.rules.grammar.model;

import java.util.Objects;

/**
 * Represents a value in Rules Script (string, number, boolean, or identifier).
 */
public sealed interface Value {

    /**
     * String value literal.
     */
    record StringValue(String value) implements Value {
        public StringValue {
            Objects.requireNonNull(value, "value cannot be null");
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    /**
     * Numeric value (integer or decimal).
     */
    record NumberValue(double value) implements Value {
        @Override
        public String toString() {
            if (value == (long) value) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
    }

    /**
     * Boolean value (YES, NO, TRUE, FALSE).
     */
    record BooleanValue(boolean value) implements Value {
        @Override
        public String toString() {
            return value ? "YES" : "NO";
        }
    }

    /**
     * Identifier reference (field name).
     */
    record IdentifierValue(String name) implements Value {
        public IdentifierValue {
            Objects.requireNonNull(name, "name cannot be null");
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
