# Extending the Rules Script Grammar

This guide explains how to extend the Rules Script language with new keywords, operators, functions, and rule types. The parser uses **ANTLR 4** for grammar definition, with an adapter layer to convert to the legacy model.

## Table of Contents

1. [Understanding the Parser Pipeline](#understanding-the-parser-pipeline)
2. [Project Structure](#project-structure)
3. [Adding a New Keyword](#adding-a-new-keyword)
4. [Adding a New Operator](#adding-a-new-operator)
5. [Adding a New Function](#adding-a-new-function)
6. [Adding a New Rule Type](#adding-a-new-rule-type)
7. [Adding a New Rule Clause](#adding-a-new-rule-clause)
8. [Updating the Adapters](#updating-the-adapters)
9. [Updating the UI](#updating-the-ui)

---

## Understanding the Parser Pipeline

```
Rules Script (text)
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│  rules-grammar library (ANTLR-based)                          │
│  ┌────────────────┐   ┌────────────────┐   ┌──────────────┐  │
│  │ RulesScriptLexer│──▶│RulesScriptParser│──▶│ AST Builder  │  │
│  │ (Generated)     │   │ (Generated)     │   │ (Visitor)    │  │
│  └────────────────┘   └────────────────┘   └──────────────┘  │
│                                                    │          │
│                              Script, Rule, Condition records  │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│  joget-rule-editor (Adapter Layer)                            │
│  ┌────────────────┐   ┌────────────────┐   ┌──────────────┐  │
│  │ RuleAdapter    │   │ConditionAdapter│   │ ValueAdapter │  │
│  │ (new→old)      │   │ (new→old)      │   │ (new→old)    │  │
│  └────────────────┘   └────────────────┘   └──────────────┘  │
│                              │                                │
│                     Legacy Rule, Condition classes            │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────┐
│  RuleScriptCompiler   │
│  (SQL generation)│
└──────────────────┘
```

### Files to Modify for Grammar Changes

| Change Type | rules-grammar | joget-rule-editor | UI Files |
|-------------|:-------------:|:-----------------:|:--------:|
| New Keyword | `RulesScript.g4`, Model classes, AST Builder | Adapters (if needed) | `jre-mode.js` |
| New Operator | `RulesScript.g4`, `ComparisonOperator.java` | `ConditionAdapter.java`, `Condition.java` | `jre-mode.js` |
| New Function | `RulesScript.g4`, `Condition.java` subclasses | `ConditionAdapter.java`, `Condition.java` | `jre-mode.js` |
| New Rule Type | `RulesScript.g4`, `RuleType.java` | `RuleAdapter.java`, `Rule.java` | `jre-mode.js` |
| New Rule Clause | `RulesScript.g4`, `Rule.java` | `RuleAdapter.java` | `jre-mode.js` |

---

## Project Structure

The grammar is split across two projects:

```
plugins/
├── rules-grammar/                    # ANTLR grammar library
│   ├── src/main/antlr4/
│   │   └── global/govstack/rules/grammar/
│   │       └── RulesScript.g4        # ANTLR grammar definition
│   └── src/main/java/
│       └── global/govstack/rules/grammar/
│           ├── RulesScript.java      # Facade API
│           ├── ParseResult.java      # Parse result with errors
│           ├── RulesScriptAstBuilder.java  # Visitor implementation
│           └── model/
│               ├── Script.java       # Immutable AST root
│               ├── Rule.java         # Immutable rule record
│               ├── Condition.java    # Sealed interface
│               ├── Value.java        # Sealed interface
│               └── ...
│
└── joget-rule-editor/                # Joget plugin
    └── src/main/java/
        └── global/govstack/ruleeditor/
            ├── adapter/              # Model adapters
            │   ├── ConditionAdapter.java
            │   ├── RuleAdapter.java
            │   └── ValueAdapter.java
            ├── parser/
            │   └── RuleScriptParser.java  # Facade
            └── model/                # Legacy model
                ├── Condition.java
                └── Rule.java
```

---

## Adding a New Keyword

**Example**: Add `DEPENDS ON "RuleName"` clause for rule dependencies.

### Step 1: Update Grammar (rules-grammar/RulesScript.g4)

```antlr
// Add tokens
DEPENDS : D E P E N D S ;

// Update rule body
ruleBody
    : (TYPE COLON ruleType)?
      (CATEGORY COLON identifier)?
      (MANDATORY COLON boolValue)?
      (ORDER COLON NUMBER)?
      (DEPENDS ON STRING)?           // New clause
      (WHEN condition)?
      (scoreClause)?
      (PASS_MESSAGE COLON STRING)?
      (FAIL_MESSAGE COLON STRING)?
    ;
```

### Step 2: Update Model (rules-grammar)

```java
// In Rule.java record
public record Rule(
    String name,
    RuleType type,
    String category,
    boolean isMandatory,
    Integer order,
    List<String> dependencies,       // New field
    Condition condition,
    Integer score,
    Integer weight,
    String passMessage,
    String failMessage
) { }
```

### Step 3: Update AST Builder (rules-grammar)

```java
// In RulesScriptAstBuilder.java
@Override
public Object visitRuleBody(RulesScriptParser.RuleBodyContext ctx) {
    // ... existing code ...

    List<String> dependencies = new ArrayList<>();
    if (ctx.DEPENDS() != null) {
        String depName = unquote(ctx.STRING(dependsIndex).getText());
        dependencies.add(depName);
    }

    return new Rule(name, type, category, mandatory, order,
                   dependencies, condition, score, weight, passMsg, failMsg);
}
```

### Step 4: Rebuild rules-grammar

```bash
cd rules-grammar
mvn clean install
```

### Step 5: Update Adapter (joget-rule-editor)

```java
// In RuleAdapter.java
public static Rule toOldModel(global.govstack.rules.grammar.model.Rule newRule) {
    Rule oldRule = new Rule();
    // ... existing mappings ...

    if (newRule.dependencies() != null) {
        for (String dep : newRule.dependencies()) {
            oldRule.addDependency(dep);
        }
    }

    return oldRule;
}
```

### Step 6: Update Legacy Model (joget-rule-editor)

```java
// In model/Rule.java
private List<String> dependencies = new ArrayList<>();

public void addDependency(String ruleName) {
    dependencies.add(ruleName);
}

public List<String> getDependencies() {
    return dependencies;
}
```

### Step 7: Rebuild joget-rule-editor

```bash
cd joget-rule-editor
mvn clean package
```

---

## Adding a New Operator

**Example**: Add `MATCHES` operator for regex matching.

### Step 1: Update Grammar (rules-grammar/RulesScript.g4)

```antlr
// Add token
MATCHES : M A T C H E S ;

// Update comparisonOp rule
comparisonOp
    : EQ | NEQ | GT | GTE | LT | LTE
    | CONTAINS | STARTS_WITH | ENDS_WITH
    | MATCHES                          // New operator
    ;
```

### Step 2: Update ComparisonOperator Enum (rules-grammar)

```java
// In model/ComparisonOperator.java
public enum ComparisonOperator {
    EQ, NEQ, GT, GTE, LT, LTE,
    CONTAINS, STARTS_WITH, ENDS_WITH,
    MATCHES;                           // New operator

    public static ComparisonOperator fromToken(String token) {
        switch (token.toUpperCase()) {
            // ... existing cases ...
            case "MATCHES": return MATCHES;
            default: throw new IllegalArgumentException("Unknown operator: " + token);
        }
    }
}
```

### Step 3: Update Legacy Condition (joget-rule-editor)

```java
// In model/Condition.java
public enum Operator {
    EQ("="),
    NEQ("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    CONTAINS("CONTAINS"),
    STARTS_WITH("STARTS WITH"),
    ENDS_WITH("ENDS WITH"),
    MATCHES("MATCHES");               // New operator
}
```

### Step 4: Update ConditionAdapter (joget-rule-editor)

```java
// In adapter/ConditionAdapter.java
private static Operator toOldOperator(ComparisonOperator op) {
    switch (op) {
        // ... existing cases ...
        case MATCHES: return Operator.MATCHES;
        default:
            throw new IllegalArgumentException("Unknown operator: " + op);
    }
}
```

### Step 5: Update Compiler (joget-rule-editor)

```java
// In compiler/RuleScriptCompiler.java
private String operatorToSql(Condition.Operator op) {
    switch (op) {
        // ... existing cases ...
        case MATCHES: return "REGEXP";  // MySQL syntax
        default: return "=";
    }
}
```

---

## Adding a New Function

**Example**: Add `LATEST(grid.field)` to get the most recent value.

### Step 1: Update Grammar (rules-grammar/RulesScript.g4)

```antlr
// Add token
LATEST : L A T E S T ;

// Update aggregationFunc rule
aggregationFunc
    : COUNT | SUM | AVG | MIN | MAX
    | LATEST                           // New function
    ;
```

### Step 2: Add to AggregationFunction Enum (rules-grammar)

```java
// In model/Condition.java (inner enum)
public enum AggregationFunction {
    COUNT, SUM, AVG, MIN, MAX,
    LATEST                             // New function
}
```

### Step 3: Update Legacy Condition (joget-rule-editor)

```java
// In model/Condition.java
public enum FunctionType {
    COUNT, SUM, AVG, MIN, MAX,
    LATEST,                            // New function
    HAS_ANY, HAS_ALL, HAS_NONE
}
```

### Step 4: Update ConditionAdapter (joget-rule-editor)

```java
private static FunctionType toOldAggregationFunction(AggregationFunction func) {
    switch (func) {
        // ... existing cases ...
        case LATEST: return FunctionType.LATEST;
        default:
            throw new IllegalArgumentException("Unknown function: " + func);
    }
}
```

### Step 5: Update Compiler (joget-rule-editor)

```java
private String compileLatest(String fieldPath, CompiledRule compiled) {
    // Generate SQL for LATEST function
    return String.format(
        "(SELECT %s FROM %s WHERE %s ORDER BY created_date DESC LIMIT 1)",
        fieldRef, tableName, correlation
    );
}
```

---

## Adding a New Rule Type

**Example**: Add `SCORING_TIER` rule type.

### Step 1: Update Grammar (rules-grammar/RulesScript.g4)

```antlr
// Add token
SCORING_TIER : S C O R I N G '_' T I E R ;

// Update ruleType rule
ruleType
    : INCLUSION | EXCLUSION | PRIORITY | BONUS
    | SCORING_TIER                     // New type
    ;
```

### Step 2: Update RuleType Enum (rules-grammar)

```java
// In model/RuleType.java
public enum RuleType {
    INCLUSION,
    EXCLUSION,
    PRIORITY,
    BONUS,
    SCORING_TIER                       // New type
}
```

### Step 3: Update Legacy Rule (joget-rule-editor)

```java
// In model/Rule.java
public enum RuleType {
    INCLUSION,
    EXCLUSION,
    PRIORITY,
    BONUS,
    SCORING_TIER                       // New type
}
```

### Step 4: Update RuleAdapter (joget-rule-editor)

```java
private static RuleType toOldRuleType(global.govstack.rules.grammar.model.RuleType newType) {
    switch (newType) {
        // ... existing cases ...
        case SCORING_TIER: return RuleType.SCORING_TIER;
        default:
            throw new IllegalArgumentException("Unknown rule type: " + newType);
    }
}
```

---

## Updating the Adapters

The adapter layer in `joget-rule-editor/src/main/java/global/govstack/ruleeditor/adapter/` converts between the immutable ANTLR model and the legacy mutable model.

### ConditionAdapter

Handles conversion of the `Condition` sealed interface hierarchy:

```java
// Condition types handled:
// - Condition.And         → Condition.and(left, right)
// - Condition.Or          → Condition.or(left, right)
// - Condition.Not         → Condition.not(inner)
// - Condition.SimpleComparison → Condition.comparison(field, op, value)
// - Condition.Between     → Condition.between(field, min, max)
// - Condition.In          → Condition.in(field, values)
// - Condition.NotIn       → Condition.not(Condition.in(...))
// - Condition.IsEmpty     → Condition.isEmpty(field)
// - Condition.IsNotEmpty  → Condition.isNotEmpty(field)
// - Condition.Aggregation → Condition.functionCall(func, field)
// - Condition.GridCheck   → Condition.functionCall(func, field)
```

### RuleAdapter

Handles conversion of `Rule` records:

```java
// Fields mapped:
// - name()        → setName()
// - type()        → setType()
// - category()    → setCategory()
// - isMandatory() → setMandatory()
// - order()       → setOrder()
// - condition()   → setCondition() via ConditionAdapter
// - score()       → setScore()
// - weight()      → setWeight()
// - passMessage() → setPassMessage()
// - failMessage() → setFailMessage()
```

### ValueAdapter

Handles conversion of `Value` sealed interface:

```java
// Value types mapped:
// - Value.StringValue     → String
// - Value.NumberValue     → Double
// - Value.BooleanValue    → Boolean
// - Value.IdentifierValue → String (field reference)
```

---

## Updating the UI

After modifying the grammar, update the UI syntax highlighting:

### jre-mode.js (Syntax Highlighting)

```javascript
CodeMirror.defineMode("jre", function() {
    // Add new keywords to appropriate category
    var keywords = new RegExp(
        "^(RULE|WHEN|AND|OR|NOT|DEPENDS|ON|MATCHES|LATEST)\\b", "i"
    );

    var types = new RegExp(
        "^(INCLUSION|EXCLUSION|PRIORITY|BONUS|SCORING_TIER)\\b", "i"
    );

    // ... rest of mode definition
});
```

### jre-editor.js (Help Panel)

Update the help panel to include new syntax:

```javascript
'<li><code>DEPENDS ON</code> "rule-name"</li>\
<li><code>MATCHES</code> "regex-pattern"</li>\
<li><code>LATEST(grid.field)</code> - most recent value</li>'
```

---

## Testing Your Extensions

### Rebuild Both Projects

```bash
# Rebuild rules-grammar first
cd rules-grammar
mvn clean install

# Then rebuild joget-rule-editor
cd ../joget-rule-editor
mvn clean package
```

### Unit Test Template

```java
@Test
void testNewKeyword() {
    String script = """
        RULE "Test Dependency"
          TYPE: INCLUSION
          DEPENDS ON "Parent Rule"
          WHEN age >= 18
        """;

    RuleScriptParser parser = new RuleScriptParser();
    ValidationResult result = parser.parse(script);

    assertTrue(result.isValid());
    assertEquals(1, result.getRuleCount());

    Rule rule = result.getRules().get(0);
    assertEquals(1, rule.getDependencies().size());
    assertEquals("Parent Rule", rule.getDependencies().get(0));
}
```

---

## Next Steps

- [UI-CUSTOMIZATION.md](./UI-CUSTOMIZATION.md) - Styling the editor
- [INTEGRATION.md](./INTEGRATION.md) - Integrating with other systems
- [DEVELOPER.md](./DEVELOPER.md) - General developer guide
