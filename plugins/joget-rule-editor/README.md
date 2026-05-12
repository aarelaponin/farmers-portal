# Joget Rule Editor

A Joget plugin for validating, compiling, and editing Rules Script definitions. Rules Script is a domain-specific language designed for defining eligibility rules in social protection programs, subsidy management, and similar use cases.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.java.net/)
[![Joget Version](https://img.shields.io/badge/Joget-8.1-green)](https://www.joget.org/)

## Features

- **Domain-Specific Language** - Human-readable syntax for eligibility rules
- **Visual Editor** - CodeMirror-based editor with syntax highlighting
- **REST API** - Validate and compile scripts via HTTP
- **SQL Compilation** - Transform rules to database queries
- **Extensible Grammar** - Easy to add new keywords, operators, functions
- **Joget Integration** - Form element for seamless embedding
- **Standalone Mode** - Use editor independently of Joget

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- (Optional) Joget DX 8.1 for deployment

### Installation

```bash
# Clone the repository
git clone https://github.com/aarelaponin/joget-rule-editor.git
cd joget-rule-editor

# Download CodeMirror (one-time setup)
chmod +x download-codemirror.sh
./download-codemirror.sh

# Build the plugin
mvn clean package

# Output JAR
ls target/joget-rule-editor-8.1-SNAPSHOT.jar
```

### Deploy to Joget

1. Go to **Settings** → **Manage Plugins** → **Upload Plugin**
2. Upload `target/joget-rule-editor-8.1-SNAPSHOT.jar`
3. Add the **Rule Editor** element to your forms

## Rules Script Syntax

Rules Script is designed to be readable by non-programmers while being precise enough for automated processing:

```text
# Agricultural Subsidy Eligibility Rules

RULE "Adult Farmer"
  TYPE: INCLUSION
  MANDATORY: YES
  ORDER: 10
  WHEN age >= 18
  FAIL MESSAGE: "Applicant must be 18 years or older"

RULE "Has Agricultural Activity"
  TYPE: INCLUSION
  MANDATORY: YES
  ORDER: 20
  WHEN hasCrops = true OR hasLivestock = true
  FAIL MESSAGE: "Must be engaged in farming activities"

RULE "Female-Headed Household Bonus"
  TYPE: BONUS
  SCORE: +10
  WHEN femaleHeadedHousehold = true AND householdSize >= 3
  PASS MESSAGE: "Priority given to female-headed households"

RULE "Large Farm Exclusion"
  TYPE: EXCLUSION
  WHEN farmSize > 50
  FAIL MESSAGE: "Farm size exceeds program limit"
```

### Supported Constructs

| Category | Syntax |
|----------|--------|
| **Rule Types** | `INCLUSION`, `EXCLUSION`, `PRIORITY`, `BONUS` |
| **Comparisons** | `=`, `!=`, `>`, `>=`, `<`, `<=` |
| **Logic** | `AND`, `OR`, `NOT`, `( )` |
| **Ranges** | `BETWEEN x AND y`, `IN ("a", "b", "c")` |
| **Nulls** | `IS EMPTY`, `IS NOT EMPTY` |
| **Strings** | `CONTAINS`, `STARTS WITH`, `ENDS WITH` |
| **Aggregations** | `COUNT(grid)`, `SUM(grid.field)`, `AVG`, `MIN`, `MAX` |
| **Grid Checks** | `HAS_ANY`, `HAS_ALL`, `HAS_NONE` |

## API Reference

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/jw/api/erel/rules/validate` | Validate Rules Script script |
| POST | `/jw/api/erel/rules/compile` | Compile script to SQL |
| GET | `/jw/api/erel/rules/fields` | Get field definitions |
| POST | `/jw/api/erel/rules/saveRuleset` | Save ruleset |
| GET | `/jw/api/erel/rules/loadRuleset` | Load ruleset |
| POST | `/jw/api/erel/rules/publishRuleset` | Publish ruleset |

### Example: Validate Script

```bash
curl -X POST "http://localhost:8080/jw/api/erel/rules/validate" \
  -H "Content-Type: application/json" \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -d '{"script": "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18"}'
```

**Response:**
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

## Documentation

Comprehensive documentation is available in the `/docs` folder:

| Document | Description |
|----------|-------------|
| [**DEVELOPER.md**](docs/DEVELOPER.md) | Developer guide and architecture overview |
| [**EXTENDING-GRAMMAR.md**](docs/EXTENDING-GRAMMAR.md) | How to add new keywords, operators, functions |
| [**UI-CUSTOMIZATION.md**](docs/UI-CUSTOMIZATION.md) | Styling the editor, themes, colors |
| [**INTEGRATION.md**](docs/INTEGRATION.md) | Integrating with different systems |

## Project Structure

```
joget-rule-editor/
├── src/main/java/global/govstack/ruleeditor/
│   ├── adapter/         # ANTLR model adapters (converts new→old model)
│   ├── parser/          # RuleScriptParser (uses rules-grammar)
│   ├── compiler/        # SQL compilation
│   ├── model/           # Data models (Rule, Condition)
│   ├── service/         # Field registry, ruleset persistence
│   ├── lib/             # REST API endpoints
│   └── element/         # Joget form elements
├── src/main/resources/
│   ├── static/          # JavaScript, CSS (CodeMirror, editor)
│   ├── templates/       # FreeMarker templates
│   └── properties/      # Plugin configurations
├── docs/                # Documentation
└── examples/            # HTML integration examples
```

### Dependencies

This plugin embeds the [rules-grammar](../rules-grammar) library which provides an ANTLR-based parser for the Rules Script DSL.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Rule Editor (UI)                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │ [Validate] [Save]  │ CodeMirror with Rules Script syntax highlighting ││
│  └─────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
                           │ REST API
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Joget Rule Editor                                                       │
│  ┌────────────────────────────┐   ┌──────────┐   ┌──────────┐          │
│  │  rules-grammar (ANTLR)    │──▶│ Adapters │──▶│ Compiler │──▶ SQL   │
│  │  Lexer → Parser → AST     │   │ (new→old)│   │          │          │
│  └────────────────────────────┘   └──────────┘   └──────────┘          │
└─────────────────────────────────────────────────────────────────────────┘
```

The parser pipeline uses:
1. **rules-grammar** - ANTLR-generated lexer/parser producing immutable AST
2. **Adapters** - Convert new model records to old mutable model classes
3. **Compiler** - Transform rules to SQL queries

## Use Cases

- **Agricultural Subsidies** - Define farmer eligibility criteria
- **Social Protection** - Target beneficiaries based on vulnerability indicators
- **Healthcare Benefits** - Determine coverage eligibility
- **Financial Assistance** - Screen loan/grant applications
- **Program Enrollment** - Automate eligibility checks

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Quick Contribution Steps

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make changes and add tests
4. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [CodeMirror](https://codemirror.net/) - Editor component
- [Joget](https://www.joget.org/) - Low-code platform integration
- [GovStack](https://www.govstack.global/) - Digital government building blocks

---

**Questions?** Open an issue or start a discussion.
