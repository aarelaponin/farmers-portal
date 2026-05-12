package global.govstack.rules.grammar.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Represents a complete Rules Script containing multiple rules.
 */
public record Script(List<Rule> rules) {

    public Script {
        Objects.requireNonNull(rules, "rules cannot be null");
        rules = List.copyOf(rules);
    }

    /**
     * Creates an empty script.
     */
    public static Script empty() {
        return new Script(List.of());
    }

    /**
     * Creates a script from a list of rules.
     */
    public static Script of(Rule... rules) {
        return new Script(List.of(rules));
    }

    /**
     * Returns true if the script has no rules.
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Returns the number of rules in the script.
     */
    public int size() {
        return rules.size();
    }

    /**
     * Returns a stream of rules.
     */
    public Stream<Rule> stream() {
        return rules.stream();
    }

    /**
     * Returns rules of a specific type.
     */
    public List<Rule> rulesOfType(RuleType type) {
        return rules.stream()
                .filter(r -> r.type() == type)
                .toList();
    }

    /**
     * Returns all inclusion rules.
     */
    public List<Rule> inclusionRules() {
        return rulesOfType(RuleType.INCLUSION);
    }

    /**
     * Returns all exclusion rules.
     */
    public List<Rule> exclusionRules() {
        return rulesOfType(RuleType.EXCLUSION);
    }

    /**
     * Returns all priority rules.
     */
    public List<Rule> priorityRules() {
        return rulesOfType(RuleType.PRIORITY);
    }

    /**
     * Returns all bonus rules.
     */
    public List<Rule> bonusRules() {
        return rulesOfType(RuleType.BONUS);
    }
}
