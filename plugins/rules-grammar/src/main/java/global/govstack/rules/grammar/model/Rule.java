package global.govstack.rules.grammar.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a complete rule definition in Rules Script.
 */
public record Rule(
        String name,
        RuleType type,
        String category,
        Boolean mandatory,
        Integer order,
        Condition condition,
        Double score,
        Double weight,
        String passMessage,
        String failMessage
) {

    /**
     * Creates a Rule with validation.
     */
    public Rule {
        Objects.requireNonNull(name, "name cannot be null");
        // type is typically required but we allow null for flexibility
    }

    /**
     * Returns the rule type, or INCLUSION as default.
     */
    public RuleType typeOrDefault() {
        return type != null ? type : RuleType.INCLUSION;
    }

    /**
     * Returns true if the rule is mandatory.
     */
    public boolean isMandatory() {
        return Boolean.TRUE.equals(mandatory);
    }

    /**
     * Returns the order value, or 0 as default.
     */
    public int orderOrDefault() {
        return order != null ? order : 0;
    }

    /**
     * Returns the condition if present.
     */
    public Optional<Condition> getCondition() {
        return Optional.ofNullable(condition);
    }

    /**
     * Returns the score value, or 0.0 as default.
     */
    public double scoreOrDefault() {
        return score != null ? score : 0.0;
    }

    /**
     * Returns the weight value, or 1.0 as default.
     */
    public double weightOrDefault() {
        return weight != null ? weight : 1.0;
    }

    /**
     * Returns the pass message if present.
     */
    public Optional<String> getPassMessage() {
        return Optional.ofNullable(passMessage);
    }

    /**
     * Returns the fail message if present.
     */
    public Optional<String> getFailMessage() {
        return Optional.ofNullable(failMessage);
    }

    /**
     * Builder for creating Rule instances.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private RuleType type;
        private String category;
        private Boolean mandatory;
        private Integer order;
        private Condition condition;
        private Double score;
        private Double weight;
        private String passMessage;
        private String failMessage;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
        }

        public Builder type(RuleType type) {
            this.type = type;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder mandatory(Boolean mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public Builder order(Integer order) {
            this.order = order;
            return this;
        }

        public Builder condition(Condition condition) {
            this.condition = condition;
            return this;
        }

        public Builder score(Double score) {
            this.score = score;
            return this;
        }

        public Builder weight(Double weight) {
            this.weight = weight;
            return this;
        }

        public Builder passMessage(String passMessage) {
            this.passMessage = passMessage;
            return this;
        }

        public Builder failMessage(String failMessage) {
            this.failMessage = failMessage;
            return this;
        }

        public Rule build() {
            return new Rule(
                    name, type, category, mandatory, order,
                    condition, score, weight, passMessage, failMessage
            );
        }
    }
}
