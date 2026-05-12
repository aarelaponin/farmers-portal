# Rules Grammar

ANTLR 4-based parser for the **Rules Script DSL** - a human-readable domain-specific language for defining eligibility rules in social protection programs, agricultural subsidies, healthcare benefits, and similar use cases.

## Quick Start

### Parsing Rules

```java
import global.govstack.rules.grammar.*;
import global.govstack.rules.grammar.model.*;

// Parse from string
Script script = RulesScript.parse("""
    RULE "Age Eligibility"
    TYPE: INCLUSION
    WHEN age >= 18 AND age <= 65
    SCORE: +100
    PASS MESSAGE: "Age requirement met"
    """).getScriptOrThrow();

// Access rules
for (Rule rule : script.rules()) {
    System.out.println(rule.name() + ": " + rule.type());
}
```

### Validation Only

```java
// Quick validation without building AST
if (RulesScript.isValid(input)) {
    // Valid syntax
}

// Get detailed errors
List<ParseError> errors = RulesScript.validate(input);
for (ParseError error : errors) {
    System.err.println(error);
}
```

### Parse Result Handling

```java
ParseResult result = RulesScript.parse(input);

if (result.isSuccess()) {
    Script script = result.script();
    // Process rules...
} else {
    System.err.println(result.formatErrors());
}

// Or use Optional API
result.getScript().ifPresent(script -> {
    script.rules().forEach(this::processRule);
});
```

## Rules Script Syntax

### Basic Rule Structure

```
RULE "Rule Name"
TYPE: INCLUSION | EXCLUSION | PRIORITY | BONUS
CATEGORY: category_name
MANDATORY: YES | NO
ORDER: 1
WHEN <condition>
SCORE: +100 | -50
WEIGHT: 1.5
PASS MESSAGE: "Success message"
FAIL MESSAGE: "Failure message"
```

### Conditions

```
# Simple comparisons
age >= 18
status = "active"
income != 0

# String operations
name CONTAINS "John"
email STARTS WITH "admin"
file ENDS WITH ".pdf"

# Null checks
notes IS EMPTY
phone IS NOT EMPTY

# Range checks
age BETWEEN 18 AND 65
status IN ("active", "pending", "approved")
status NOT IN ("rejected", "banned")

# Logical operators
age >= 18 AND income < 50000
status = "employed" OR status = "self-employed"
NOT status = "rejected"

# Grouping
(age >= 18 OR hasGuardian = YES) AND income < 50000
```

### Functions

```
# Aggregation functions
COUNT(items) > 0
SUM(household.income) >= 50000
AVG(scores) > 75
MIN(ages) >= 18
MAX(amounts) <= 10000

# Grid check functions
HAS_ANY(documents, "passport", "id_card")
HAS_ALL(required_docs, "id", "proof_of_income")
HAS_NONE(flags, "fraud", "duplicate")
```

### Field References

```
# Simple field
age >= 18

# Nested field (dot notation)
applicant.age >= 18
household.members.primary.income > 0
```

## Working with the AST

### Pattern Matching on Conditions

```java
private void processCondition(Condition condition) {
    switch (condition) {
        case Condition.And and -> {
            for (Condition operand : and.operands()) {
                processCondition(operand);
            }
        }
        case Condition.Or or -> {
            // Handle OR
        }
        case Condition.SimpleComparison sc -> {
            System.out.println(sc.field() + " " + sc.operator() + " " + sc.value());
        }
        case Condition.Aggregation agg -> {
            System.out.println(agg.function() + "(" + agg.field() + ")");
        }
        // ... other cases
    }
}
```

### Filtering Rules

```java
Script script = RulesScript.parse(input).getScriptOrThrow();

// Get inclusion rules only
List<Rule> inclusionRules = script.inclusionRules();

// Filter by category
List<Rule> socialRules = script.rules().stream()
    .filter(r -> "social_protection".equals(r.category()))
    .toList();

// Get mandatory rules
List<Rule> mandatoryRules = script.rules().stream()
    .filter(Rule::isMandatory)
    .toList();
```

## Building

```bash
# Compile (regenerates ANTLR parser)
mvn clean compile

# Run tests
mvn test

# Package JAR
mvn package
```

## Project Structure

```
src/main/
├── antlr4/global/govstack/rules/grammar/
│   ├── RulesScriptLexer.g4    # Token definitions
│   └── RulesScriptParser.g4   # Grammar rules
└── java/global/govstack/rules/grammar/
    ├── RulesScript.java       # Main API facade
    ├── ParseResult.java       # Parse result wrapper
    └── model/
        ├── Script.java        # Collection of rules
        ├── Rule.java          # Rule definition
        ├── Condition.java     # Condition expressions
        ├── Value.java         # Value types
        └── FieldRef.java      # Field references
```

## Documentation

- **[DEVELOPER.md](DEVELOPER.md)** - Comprehensive developer guide covering:
  - Architecture and design decisions
  - Grammar structure and how to extend it
  - Adding new keywords, operators, and functions
  - Testing strategy
  - Maintenance and troubleshooting

## Requirements

- Java 17+
- Maven 3.6+

## License

Copyright GovStack
