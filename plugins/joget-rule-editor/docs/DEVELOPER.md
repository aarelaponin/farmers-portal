# Joget Rule Editor - Developer Guide

A comprehensive guide for extending, customizing, and integrating the Rules Script (Eligibility Rule Expression Language) Parser Plugin.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Quick Start for Developers](#quick-start-for-developers)
3. [Extending the Grammar](#extending-the-grammar)
4. [UI Customization](#ui-customization)
5. [Integration Guide](#integration-guide)
6. [API Reference](#api-reference)
7. [Testing](#testing)
8. [Deployment](#deployment)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Joget Rule Editor                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │  UI Layer        │    │  API Layer       │    │  Storage Layer   │  │
│  │  (JavaScript)    │───▶│  (REST)          │───▶│  (Joget Forms)   │  │
│  └──────────────────┘    └──────────────────┘    └──────────────────┘  │
│           │                       │                                     │
│           ▼                       ▼                                     │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                        Parser Layer (Java)                        │  │
│  │  ┌────────────────────┐   ┌──────────┐   ┌──────────┐           │  │
│  │  │  rules-grammar     │──▶│ Adapters │──▶│ Compiler │           │  │
│  │  │  (ANTLR Parser)    │   │ (Model)  │   │ (SQL)    │           │  │
│  │  └────────────────────┘   └──────────┘   └──────────┘           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Layer Descriptions

| Layer | Location | Purpose |
|-------|----------|---------|
| **UI Layer** | `resources/static/` | CodeMirror editor with Rules Script syntax highlighting |
| **API Layer** | `lib/RulesServiceProvider.java` | REST endpoints for validation, compilation, CRUD |
| **Parser Layer** | `parser/` + `rules-grammar` | ANTLR-based parsing via embedded library |
| **Adapter Layer** | `adapter/` | Converts ANTLR model to legacy model classes |
| **Compiler Layer** | `compiler/` | Transforms rules to SQL for database queries |
| **Model Layer** | `model/` | Data structures (Rule, Condition, etc.) |
| **Service Layer** | `service/` | Field registry, ruleset persistence |

### Key Files

```
src/main/java/global/govstack/ruleeditor/
├── Activator.java                 # OSGi bundle activator
├── adapter/
│   ├── ConditionAdapter.java      # Converts ANTLR Condition → legacy Condition
│   ├── RuleAdapter.java           # Converts ANTLR Rule → legacy Rule
│   └── ValueAdapter.java          # Converts ANTLR Value → Object
├── parser/
│   └── RuleScriptParser.java      # Facade using rules-grammar + adapters
├── compiler/
│   ├── RuleScriptCompiler.java    # AST → SQL transformation
│   ├── FieldMapping.java          # Field-to-column mapping
│   └── CompiledRuleset.java       # Compilation output
├── model/
│   ├── Rule.java                  # Single rule model (legacy)
│   ├── Condition.java             # Condition tree node (legacy)
│   └── ValidationResult.java      # Parse/validation result
├── service/
│   ├── FieldRegistryService.java  # Available fields for autocomplete
│   └── RulesetService.java        # Ruleset persistence
└── lib/
    └── RulesServiceProvider.java  # REST API endpoints

src/main/resources/
├── static/
│   ├── jre-mode.js               # CodeMirror syntax mode
│   ├── jre-editor.js             # Editor component
│   └── jre-editor.css            # Editor styling
└── templates/
    └── RuleEditorElement.ftl      # Joget form element template

# Embedded dependency (inside JAR):
rules-grammar-1.0.0-SNAPSHOT.jar   # ANTLR-based parser library
```

---

## Quick Start for Developers

### Prerequisites

- Java 17+
- Maven 3.6+
- Joget DX 8.1 (for deployment)

### Build and Test

```bash
# Clone the repository
git clone <repo-url>
cd joget-rule-editor

# Download CodeMirror (one-time setup)
chmod +x download-codemirror.sh
./download-codemirror.sh

# Build
mvn clean package

# Run tests
mvn test

# Output JAR
ls target/joget-rule-editor-8.1-SNAPSHOT.jar
```

### Local Development (Without Joget)

You can test the parser independently:

```java
import global.govstack.ruleeditor.parser.RuleScriptParser;
import global.govstack.ruleeditor.model.ValidationResult;

public class ParserTest {
    public static void main(String[] args) {
        String script = """
            RULE "Test Rule"
              TYPE: INCLUSION
              WHEN age >= 18
            """;

        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult result = parser.parse(script);

        System.out.println("Valid: " + result.isValid());
        System.out.println("Rules: " + result.getRuleCount());
    }
}
```

---

## Extending the Grammar

The grammar is implemented using **ANTLR 4** in the separate [rules-grammar](../rules-grammar) library. To extend the grammar, you need to modify the grammar file there and rebuild both projects.

### Current Grammar (BNF)

```bnf
script     := rule*
rule       := RULE STRING ruleBody
ruleBody   := (TYPE COLON ruleType)?
              (CATEGORY COLON IDENTIFIER)?
              (MANDATORY COLON boolValue)?
              (ORDER COLON NUMBER)?
              (WHEN condition)?
              (SCORE COLON NUMBER)?
              (PASS_MESSAGE COLON STRING)?
              (FAIL_MESSAGE COLON STRING)?
ruleType   := INCLUSION | EXCLUSION | PRIORITY | BONUS
boolValue  := YES | NO | TRUE | FALSE
condition  := orExpr
orExpr     := andExpr (OR andExpr)*
andExpr    := unaryExpr (AND unaryExpr)*
unaryExpr  := NOT? primaryExpr
primaryExpr := LPAREN condition RPAREN | comparison | functionExpr
comparison := IDENTIFIER operator value
            | IDENTIFIER BETWEEN value AND value
            | IDENTIFIER IN LPAREN valueList RPAREN
            | IDENTIFIER IS_EMPTY
            | IDENTIFIER IS_NOT_EMPTY
operator   := EQ | NEQ | GT | GTE | LT | LTE | CONTAINS | STARTS_WITH | ENDS_WITH
functionExpr := functionName LPAREN IDENTIFIER (COMMA valueList)? RPAREN (operator value)?
functionName := COUNT | SUM | AVG | MIN | MAX | HAS_ANY | HAS_ALL | HAS_NONE
value      := STRING | NUMBER | boolValue | IDENTIFIER
valueList  := value (COMMA value)*
```

### Extension Examples

See [EXTENDING-GRAMMAR.md](./EXTENDING-GRAMMAR.md) for detailed step-by-step guides:

1. **Adding a New Keyword** (e.g., `EFFECTIVE FROM date TO date`)
2. **Adding a New Operator** (e.g., `MATCHES regex`)
3. **Adding a New Function** (e.g., `LATEST(grid.field)`)
4. **Adding a New Rule Type** (e.g., `SCORING_TIER`)
5. **Adding a New Clause** (e.g., `DEPENDS ON "RuleName"`)

---

## UI Customization

The editor is built on CodeMirror with custom styling. See [UI-CUSTOMIZATION.md](./UI-CUSTOMIZATION.md) for details.

### Quick Customizations

#### Change Colors (jre-editor.css)

```css
/* Syntax highlighting colors */
.cm-s-default .cm-keyword { color: #0000ff; font-weight: bold; }
.cm-s-default .cm-def { color: #6f42c1; font-weight: bold; }
.cm-s-default .cm-type { color: #795e26; }
.cm-s-default .cm-variable { color: #6f42c1; }
.cm-s-default .cm-string { color: #22863a; }
.cm-s-default .cm-number { color: #005cc5; }
.cm-s-default .cm-atom { color: #d73a49; font-weight: bold; }
.cm-s-default .cm-comment { color: #6a737d; font-style: italic; }
```

#### Add New Highlighting (jre-mode.js)

```javascript
// Add new keywords to the regex
var keywords = new RegExp("^(RULE|WHEN|AND|OR|NOT|YOUR_NEW_KEYWORD)\\b", "i");
```

#### Customize Editor Options (jre-editor.js)

```javascript
var cm = CodeMirror.fromTextArea(elements.textarea, {
    mode: 'jre',
    lineNumbers: true,
    theme: 'monokai',        // Change theme
    matchBrackets: true,
    styleActiveLine: true,
    indentUnit: 4,           // Change indent
    tabSize: 4,
    lineWrapping: true
});
```

---

## Integration Guide

See [INTEGRATION.md](./INTEGRATION.md) for comprehensive integration examples.

### Integration Options

| Method | Use Case | Complexity |
|--------|----------|------------|
| **Joget Form Element** | Embedded in Joget forms | Low |
| **Standalone HTML** | Any web page | Low |
| **REST API** | Backend services | Medium |
| **Java Library** | Direct Java integration | Medium |
| **Custom System** | Non-Joget platforms | High |

### REST API Quick Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/jw/api/erel/rules/validate` | POST | Validate Rules Script script |
| `/jw/api/erel/rules/compile` | POST | Compile to SQL |
| `/jw/api/erel/rules/fields` | GET | Get field definitions |
| `/jw/api/erel/rules/saveRuleset` | POST | Save ruleset |
| `/jw/api/erel/rules/loadRuleset` | GET | Load ruleset |
| `/jw/api/erel/rules/publishRuleset` | POST | Publish ruleset |

### Validation Request Example

```bash
curl -X POST "http://localhost:8080/jw/api/erel/rules/validate" \
  -H "Content-Type: application/json" \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -d '{
    "script": "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18",
    "scopeCode": "FARMER_ELIGIBILITY"
  }'
```

### Response Format

```json
{
  "valid": true,
  "ruleCount": 1,
  "rules": [
    {
      "ruleName": "Test",
      "ruleCode": "TEST",
      "ruleType": "INCLUSION",
      "conditionCount": 1
    }
  ],
  "errors": [],
  "warnings": []
}
```

---

## API Reference

### RuleScriptParser

```java
public class RuleScriptParser {
    /**
     * Parse an Rules Script script.
     * @param script The Rules Script script text
     * @return ValidationResult with parsed rules, errors, warnings
     */
    public ValidationResult parse(String script);
}
```

### RuleScriptCompiler

```java
public class RuleScriptCompiler {
    /**
     * Create compiler with field mapping.
     * @param fieldMapping Maps Rules Script fields to database columns
     */
    public RuleScriptCompiler(FieldMapping fieldMapping);

    /**
     * Compile parsed rules to SQL.
     * @param rules List of parsed Rule objects
     * @param rulesetCode Unique identifier for this ruleset
     * @param scopeCode Field scope (e.g., "FARMER_ELIGIBILITY")
     * @return CompiledRuleset with SQL queries
     */
    public CompiledRuleset compile(List<Rule> rules, String rulesetCode, String scopeCode);
}
```

### Condition

```java
public class Condition {
    // Condition types
    public enum ConditionType {
        COMPARISON,      // field op value
        AND, OR, NOT,    // logical
        GROUP,           // (condition)
        FUNCTION_CALL,   // COUNT(...), HAS_ANY(...)
        BETWEEN, IN,     // range/set
        IS_EMPTY, IS_NOT_EMPTY
    }

    // Operators
    public enum Operator {
        EQ, NEQ, GT, GTE, LT, LTE,
        CONTAINS, STARTS_WITH, ENDS_WITH
    }

    // Functions
    public enum FunctionType {
        COUNT, SUM, AVG, MIN, MAX,
        HAS_ANY, HAS_ALL, HAS_NONE
    }

    // Factory methods
    public static Condition comparison(String field, Operator op, Object value);
    public static Condition and(Condition left, Condition right);
    public static Condition or(Condition left, Condition right);
    public static Condition not(Condition inner);
    public static Condition between(String field, Object min, Object max);
    public static Condition in(String field, List<Object> values);
    public static Condition functionCall(FunctionType func, String arg);
}
```

---

## Testing

### Unit Tests

```bash
mvn test
```

### Test Cases to Cover

1. **Lexer Tests** - Token recognition
2. **Parser Tests** - Grammar rules
3. **Compiler Tests** - SQL generation
4. **Validator Tests** - Field validation
5. **Integration Tests** - Full pipeline

### Example Test

```java
@Test
public void testSimpleRule() {
    String script = "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18";

    RuleScriptParser parser = new RuleScriptParser();
    ValidationResult result = parser.parse(script);

    assertTrue(result.isValid());
    assertEquals(1, result.getRuleCount());

    Rule rule = result.getRules().get(0);
    assertEquals("Test", rule.getName());
    assertEquals(RuleType.INCLUSION, rule.getType());
}
```

---

## Deployment

### Joget DX Deployment

1. Build: `mvn clean package`
2. Upload: Settings → Manage Plugins → Upload Plugin
3. Upload `target/joget-rule-editor-8.1-SNAPSHOT.jar`

### Configuration

In Joget Form Builder:
1. Add **Rule Editor Element** to your form
2. Configure:
   - **Field ID**: Unique identifier
   - **Hidden Field ID**: Where script is stored
   - **API ID/Key**: From Joget API Builder
   - **Context Type/Code**: For ruleset association

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Plugin not appearing | Restart Joget after upload |
| API errors | Check API credentials in element config |
| Fields not loading | Verify scopeCode matches field registry |
| Styling issues | Clear browser cache |

---

## Next Steps

- [EXTENDING-GRAMMAR.md](./EXTENDING-GRAMMAR.md) - Detailed grammar extension guide
- [UI-CUSTOMIZATION.md](./UI-CUSTOMIZATION.md) - Visual styling guide
- [INTEGRATION.md](./INTEGRATION.md) - Integration with different systems
- [../README.md](../README.md) - Quick start and overview