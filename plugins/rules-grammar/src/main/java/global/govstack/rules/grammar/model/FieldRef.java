package global.govstack.rules.grammar.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a field reference in Rules Script, supporting dot notation.
 * Examples: "age", "applicant.age", "household.members.primary.income"
 */
public record FieldRef(List<String> path) {

    public FieldRef {
        Objects.requireNonNull(path, "path cannot be null");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path cannot be empty");
        }
        path = List.copyOf(path);
    }

    /**
     * Creates a field reference from a single identifier.
     */
    public static FieldRef of(String identifier) {
        return new FieldRef(List.of(identifier));
    }

    /**
     * Creates a field reference from multiple path segments.
     */
    public static FieldRef of(String... segments) {
        return new FieldRef(List.of(segments));
    }

    /**
     * Returns the root field name.
     */
    public String root() {
        return path.get(0);
    }

    /**
     * Returns true if this is a simple field (no dot notation).
     */
    public boolean isSimple() {
        return path.size() == 1;
    }

    /**
     * Returns true if this is a compound field (has dot notation).
     */
    public boolean isCompound() {
        return path.size() > 1;
    }

    @Override
    public String toString() {
        return String.join(".", path);
    }
}
