# Rules Grammar - Developer Guide

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Grammar Design](#grammar-design)
4. [Domain Model](#domain-model)
5. [Extending the Grammar](#extending-the-grammar)
6. [Adding New Features](#adding-new-features)
7. [Testing Strategy](#testing-strategy)
8. [Maintenance Guide](#maintenance-guide)
9. [Troubleshooting](#troubleshooting)
10. [Integration with joget-rule-editor](#integration-with-joget-rule-editor)

---

## Project Overview

This project provides an ANTLR 4-based parser for the **Rules Script DSL** - a human-readable domain-specific language for defining eligibility rules in social protection programs, agricultural subsidies, healthcare benefits, and similar use cases.

### Key Benefits

- **Formal Grammar**: 291 lines of ANTLR grammar vs ~47,000 lines of hand-written parser code
- **Maintainability**: Declarative grammar is easier to understand and modify
- **Type Safety**: Sealed interfaces and records provide compile-time safety
- **Extensibility**: Adding new language features follows a clear pattern
- **Tool Support**: ANTLR generates listeners, visitors, and can generate documentation

### Technology Stack

- **ANTLR 4.13.1**: Parser generator
- **Java 17**: Language level (required for sealed interfaces, records, text blocks)
- **JUnit 5**: Testing framework
- **Maven**: Build system

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           RulesScript.parse()                           │
│                          (Facade/Entry Point)                           │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        ANTLR Lexer & Parser                             │
│  ┌──────────────────────┐     ┌──────────────────────┐                  │
│  │  RulesScriptLexer.g4 │ ──▶ │ RulesScriptParser.g4 │                  │
│  │  (Token definitions) │     │ (Grammar rules)      │                  │
│  └──────────────────────┘     └──────────────────────┘                  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      RulesScriptAstBuilder                              │
│                  (Visitor: Parse Tree → Domain Model)                   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Domain Model                                   │
│  ┌────────┐  ┌───────────┐  ┌───────────┐  ┌─────────┐  ┌──────────┐   │
│  │ Script │  │   Rule    │  │ Condition │  │  Value  │  │ FieldRef │   │
│  └────────┘  └───────────┘  └───────────┘  └─────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### File Structure

```
src/
├── main/
│   ├── antlr4/global/govstack/rules/grammar/
│   │   ├── RulesScriptLexer.g4      # Token definitions
│   │   └── RulesScriptParser.g4     # Grammar rules
│   └── java/global/govstack/rules/grammar/
│       ├── RulesScript.java         # Main facade API
│       ├── RulesScriptAstBuilder.java  # ANTLR visitor
│       ├── ParseResult.java         # Parse result wrapper
│       ├── ParseError.java          # Error representation
│       ├── RulesScriptParseException.java
│       └── model/
│           ├── Script.java          # Root AST node
│           ├── Rule.java            # Rule definition
│           ├── RuleType.java        # INCLUSION, EXCLUSION, etc.
│           ├── Condition.java       # Sealed condition hierarchy
│           ├── Value.java           # Sealed value hierarchy
│           ├── FieldRef.java        # Field reference with dot notation
│           └── ComparisonOperator.java
└── test/
    └── java/global/govstack/rules/grammar/
        ├── RulesScriptParserTest.java      # Grammar tests
        └── RulesScriptAstBuilderTest.java  # AST building tests
```

---

## Grammar Design

### Lexer Grammar (RulesScriptLexer.g4)

The lexer defines all tokens (keywords, operators, literals). Key design decisions:

#### Case-Insensitive Keywords

Keywords are case-insensitive using letter fragments:

```antlr
fragment A: [aA];
fragment B: [bB];
// ... etc

RULE: R U L E;  // Matches "RULE", "rule", "Rule", "rUlE", etc.
```

**Why**: User-friendly DSL that doesn't require exact casing.

#### Multi-Word Keywords

Multi-word keywords must be defined BEFORE their single-word counterparts:

```antlr
// CORRECT ORDER - longest match first
IS_NOT_EMPTY  : I S WS_INLINE+ N O T WS_INLINE+ E M P T Y;
IS_EMPTY      : I S WS_INLINE+ E M P T Y;
NOT_IN        : N O T WS_INLINE+ I N;
```

**Why**: ANTLR uses longest-match semantics. If `IS_EMPTY` came first, "IS NOT EMPTY" would be tokenized as `IS_EMPTY` followed by `NOT`.

#### Token Ordering

```antlr
// Order matters! Keywords must come before IDENTIFIER
RULE      : R U L E;
// ... other keywords ...
IDENTIFIER: LETTER (LETTER | DIGIT | '_')*;  // Must be last
```

**Why**: If IDENTIFIER came first, "RULE" would match as an identifier, not a keyword.

### Parser Grammar (RulesScriptParser.g4)

#### Operator Precedence

Condition expressions use explicit precedence through grammar structure:

```antlr
condition: orExpr;
orExpr: andExpr (OR andExpr)*;       // Lowest precedence
andExpr: unaryExpr (AND unaryExpr)*; // Medium precedence
unaryExpr: NOT? primaryExpr;          // Highest precedence
```

This means: `a AND b OR c` is parsed as `(a AND b) OR c`.

#### Labeled Alternatives

Labels create separate visitor methods:

```antlr
comparison
    : fieldRef IS_EMPTY           # IsEmptyComparison
    | fieldRef IS_NOT_EMPTY       # IsNotEmptyComparison
    | fieldRef comparisonOp value # SimpleComparison
    ;
```

Generates: `visitIsEmptyComparison()`, `visitIsNotEmptyComparison()`, `visitSimpleComparison()`.

**Note**: The parent rule (`visitComparison`) is NOT generated when labels are used.

---

## Domain Model

### Design Principles

1. **Immutability**: All model classes are records or have immutable fields
2. **Sealed Hierarchies**: `Condition` and `Value` are sealed interfaces for exhaustive pattern matching
3. **Defensive Copies**: Collections are copied in constructors via `List.copyOf()`
4. **Null Safety**: Constructors validate required fields with `Objects.requireNonNull()`

### Condition Hierarchy

```
Condition (sealed interface)
├── And(List<Condition>)           # Logical AND
├── Or(List<Condition>)            # Logical OR
├── Not(Condition)                 # Logical NOT
├── SimpleComparison(field, op, value)
├── IsEmpty(field)
├── IsNotEmpty(field)
├── Between(field, low, high)
├── In(field, values)
├── NotIn(field, values)
├── Aggregation(func, field, op?, value?)  # COUNT, SUM, AVG, MIN, MAX
└── GridCheck(func, field, values?)         # HAS_ANY, HAS_ALL, HAS_NONE
```

### Value Hierarchy

```
Value (sealed interface)
├── StringValue(String)
├── NumberValue(double)
├── BooleanValue(boolean)
└── IdentifierValue(String)   # Field reference as value
```

### Pattern Matching Example

```java
// Exhaustive pattern matching (Java 17+)
switch (condition) {
    case Condition.And and -> processAnd(and.operands());
    case Condition.Or or -> processOr(or.operands());
    case Condition.Not not -> processNot(not.operand());
    case Condition.SimpleComparison sc -> processSimple(sc);
    case Condition.IsEmpty ie -> processIsEmpty(ie.field());
    case Condition.IsNotEmpty ine -> processIsNotEmpty(ine.field());
    case Condition.Between b -> processBetween(b);
    case Condition.In in -> processIn(in);
    case Condition.NotIn notIn -> processNotIn(notIn);
    case Condition.Aggregation agg -> processAggregation(agg);
    case Condition.GridCheck gc -> processGridCheck(gc);
}
```

---

## Extending the Grammar

### Adding a New Keyword

**Example**: Add a `DESCRIPTION` clause to rules.

#### Step 1: Add Token to Lexer

```antlr
// In RulesScriptLexer.g4, add in the "Rule structure keywords" section
DESCRIPTION : D E S C R I P T I O N;
```

#### Step 2: Add Grammar Rule

```antlr
// In RulesScriptParser.g4, add to ruleClause alternatives
ruleClause
    : TYPE COLON ruleType
    | CATEGORY COLON IDENTIFIER
    | DESCRIPTION COLON STRING    // <-- Add this line
    // ... other clauses
    ;
```

#### Step 3: Update Domain Model

```java
// In Rule.java, add field to record
public record Rule(
    String name,
    RuleType type,
    String category,
    String description,  // <-- Add this
    // ... other fields
) { ... }

// Update Builder class
public Builder description(String description) {
    this.description = description;
    return this;
}
```

#### Step 4: Update AST Builder

```java
// In RulesScriptAstBuilder.processRuleClause()
} else if (ctx.DESCRIPTION() != null) {
    builder.description(unquote(ctx.STRING().getText()));
}
```

#### Step 5: Add Tests

```java
@Test
void parseRuleWithDescription() {
    Script script = RulesScript.parse("""
        RULE "Test"
        DESCRIPTION: "This rule checks age eligibility"
        TYPE: INCLUSION
        """).getScriptOrThrow();

    assertEquals("This rule checks age eligibility",
                 script.rules().get(0).description());
}
```

### Adding a New Comparison Operator

**Example**: Add a `MATCHES` operator for regex matching.

#### Step 1: Add Token

```antlr
// In RulesScriptLexer.g4
MATCHES : M A T C H E S;
```

#### Step 2: Add to comparisonOp

```antlr
// In RulesScriptParser.g4
comparisonOp
    : EQ | NEQ | GT | GTE | LT | LTE
    | CONTAINS | STARTS_WITH | ENDS_WITH
    | MATCHES   // <-- Add this
    ;
```

#### Step 3: Update ComparisonOperator Enum

```java
// In ComparisonOperator.java
MATCHES("MATCHES");
```

#### Step 4: Update AST Builder

```java
// In visitComparisonOp()
if (ctx.MATCHES() != null) return ComparisonOperator.MATCHES;
```

### Adding a New Function

**Example**: Add a `FIRST(field)` function that returns the first item.

#### Step 1: Add Token

```antlr
// In RulesScriptLexer.g4
FIRST : F I R S T;
```

#### Step 2: Add to Function Grammar

Option A - Add to existing aggregationFunc:
```antlr
aggregationFunc: COUNT | SUM | AVG | MIN | MAX | FIRST;
```

Option B - Create new function category:
```antlr
selectionFunc: FIRST | LAST;

functionCall
    : aggregationFunc LPAREN fieldRef RPAREN (comparisonOp value)?  # AggregationCall
    | gridCheckFunc LPAREN fieldRef (COMMA valueList)? RPAREN      # GridCheckCall
    | selectionFunc LPAREN fieldRef RPAREN                         # SelectionCall
    ;
```

#### Step 3: Update Domain Model

If creating new category:
```java
// In Condition.java
enum SelectionFunction { FIRST, LAST }

record Selection(SelectionFunction function, FieldRef field) implements Condition {
    // ...
}
```

#### Step 4: Update AST Builder

```java
// Add new visitor method
@Override
public Condition visitSelectionCall(RulesScriptParser.SelectionCallContext ctx) {
    // ...
}
```

---

## Adding New Features

### Adding a New Rule Type

**Example**: Add `SCORING` rule type.

1. **Lexer**: Add token
   ```antlr
   SCORING : S C O R I N G;
   ```

2. **Parser**: Add to ruleType
   ```antlr
   ruleType: INCLUSION | EXCLUSION | PRIORITY | BONUS | SCORING;
   ```

3. **Domain Model**: Add to RuleType enum
   ```java
   public enum RuleType {
       INCLUSION, EXCLUSION, PRIORITY, BONUS, SCORING
   }
   ```

4. **AST Builder**: Add case
   ```java
   if (ctx.SCORING() != null) return RuleType.SCORING;
   ```

5. **Tests**: Add comprehensive tests

### Adding Complex Syntax

**Example**: Add array indexing `field[0]` or `field[*]`.

This requires more significant changes:

1. **Lexer**: Add bracket tokens
   ```antlr
   LBRACKET : '[';
   RBRACKET : ']';
   STAR     : '*';
   ```

2. **Parser**: Modify fieldRef
   ```antlr
   fieldRef
       : IDENTIFIER (DOT IDENTIFIER)* arrayIndex?
       ;

   arrayIndex
       : LBRACKET (NUMBER | STAR) RBRACKET
       ;
   ```

3. **Domain Model**: Update FieldRef
   ```java
   public record FieldRef(List<String> path, ArrayIndex index) {
       // ...
   }

   public sealed interface ArrayIndex {
       record Numeric(int index) implements ArrayIndex {}
       record All() implements ArrayIndex {}  // for [*]
   }
   ```

4. **AST Builder**: Update visitFieldRef

---

## Testing Strategy

### Test Categories

1. **Grammar Tests** (`RulesScriptParserTest.java`)
   - Validate syntax is correctly parsed
   - Test all token types and combinations
   - Test error detection

2. **AST Builder Tests** (`RulesScriptAstBuilderTest.java`)
   - Validate correct domain objects are created
   - Test all condition types
   - Test value parsing

### Test Patterns

```java
// Pattern 1: Syntax validation
@Test
void syntaxTest() {
    parse(input);
    assertNoSyntaxErrors();
}

// Pattern 2: AST structure validation
@Test
void astTest() {
    Script script = RulesScript.parse(input).getScriptOrThrow();
    assertInstanceOf(ExpectedType.class, script.rules().get(0).condition());
}

// Pattern 3: Error detection
@Test
void errorTest() {
    parse(invalidInput);
    assertHasSyntaxErrors();
}

// Pattern 4: Parameterized tests for variations
@ParameterizedTest
@ValueSource(strings = {"INCLUSION", "inclusion", "Inclusion"})
void caseInsensitive(String value) {
    // Test all case variations
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RulesScriptParserTest

# Run with verbose output
mvn test -X
```

---

## Maintenance Guide

### Regenerating Parser

ANTLR parser is regenerated automatically during build:

```bash
mvn clean compile
```

Generated files are in `target/generated-sources/antlr4/`.

### Updating ANTLR Version

1. Update version in `pom.xml`:
   ```xml
   <antlr4.version>4.13.2</antlr4.version>
   ```

2. Clean and rebuild:
   ```bash
   mvn clean compile
   ```

3. Run tests to verify compatibility:
   ```bash
   mvn test
   ```

### Common Maintenance Tasks

#### Adding Debug Output

```java
// In RulesScript.java, add token stream debugging
CommonTokenStream tokens = new CommonTokenStream(lexer);
tokens.fill();
for (Token token : tokens.getTokens()) {
    System.out.println(token);
}
```

#### Viewing Parse Tree

```java
RulesScriptParser.ScriptContext tree = parser.script();
System.out.println(tree.toStringTree(parser));
```

#### Debugging Grammar Ambiguities

Enable ANTLR diagnostic mode:

```java
parser.addErrorListener(new DiagnosticErrorListener());
parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
```

### Performance Considerations

1. **Lexer/Parser Reuse**: Create once, reuse for multiple parses
2. **Lazy AST Building**: For validation-only, skip AST building with `validate()`
3. **Error Recovery**: ANTLR provides good error recovery by default

---

## Troubleshooting

### Common Issues

#### "method does not override or implement a method from a supertype"

**Cause**: Using `@Override` on a method that isn't in the base visitor.

**Solution**: When grammar rules use labels (e.g., `# IsEmptyComparison`), the parent method (`visitComparison`) is NOT generated. Remove `@Override` and make it a private helper method.

#### "rule" method not found

**Cause**: `rule` is a reserved word in ANTLR.

**Solution**: ANTLR generates `rule_()` instead of `rule()`. Use `ctx.rule_()` in visitor code.

#### Keyword not recognized

**Cause**: Token order issue - IDENTIFIER is matching before keyword.

**Solution**: Ensure keywords are defined BEFORE `IDENTIFIER` in lexer grammar.

#### Multi-word keyword partially matched

**Cause**: Token order issue - shorter keyword matching first.

**Solution**: Define longer tokens before shorter ones:
```antlr
IS_NOT_EMPTY: ...;  // Before IS_EMPTY
IS_EMPTY: ...;
```

#### Case sensitivity issues

**Cause**: Missing case-insensitive fragments.

**Solution**: Use letter fragments for all letters in keywords:
```antlr
KEYWORD: K E Y W O R D;  // Not 'KEYWORD'
```

### Debugging Tips

1. **Print Token Stream**: See what tokens the lexer produces
2. **Print Parse Tree**: Visualize the parse tree structure
3. **Enable Diagnostics**: Find grammar ambiguities
4. **Use ANTLR Lab**: Online tool at http://lab.antlr.org/

---

## Integration with joget-rule-editor

### Dependency Setup

Add to `joget-rule-editor/pom.xml`:

```xml
<dependency>
    <groupId>global.govstack</groupId>
    <artifactId>rules-grammar</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Migration Path

1. **Phase 1** (Complete): Grammar definition
2. **Phase 2** (Complete): Parser infrastructure
3. **Phase 3** (TODO): Integration
   - Create adapter from ANTLR AST to existing internal model
   - Replace `RuleScriptParser.java` calls with `RulesScript.parse()`
   - Maintain backward compatibility

### Adapter Pattern

```java
// Bridge between ANTLR model and existing model
public class RulesScriptAdapter {

    public LegacyRule convert(Rule rule) {
        LegacyRule legacy = new LegacyRule();
        legacy.setName(rule.name());
        legacy.setType(convertType(rule.type()));
        legacy.setCondition(convertCondition(rule.condition()));
        // ... etc
        return legacy;
    }

    private LegacyCondition convertCondition(Condition condition) {
        return switch (condition) {
            case Condition.And and -> convertAnd(and);
            case Condition.Or or -> convertOr(or);
            // ... exhaustive pattern matching
        };
    }
}
```

---

## Appendix: Grammar Quick Reference

### Keywords

| Category | Keywords |
|----------|----------|
| Rule Structure | `RULE`, `TYPE`, `CATEGORY`, `MANDATORY`, `ORDER`, `WHEN`, `SCORE`, `WEIGHT` |
| Messages | `PASS MESSAGE`, `FAIL MESSAGE` |
| Rule Types | `INCLUSION`, `EXCLUSION`, `PRIORITY`, `BONUS` |
| Boolean | `YES`, `NO`, `TRUE`, `FALSE` |
| Logical | `AND`, `OR`, `NOT` |
| Comparison | `BETWEEN`, `IN`, `NOT IN`, `IS EMPTY`, `IS NOT EMPTY`, `CONTAINS`, `STARTS WITH`, `ENDS WITH` |
| Aggregation | `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` |
| Grid Checks | `HAS_ANY`, `HAS_ALL`, `HAS_NONE` |

### Operators

| Operator | Meaning |
|----------|---------|
| `=` | Equals |
| `!=` | Not equals |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `<` | Less than |
| `<=` | Less than or equal |

### Example Rule

```
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
```
