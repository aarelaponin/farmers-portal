# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Compile (regenerates ANTLR parser from .g4 files)
mvn clean compile

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RulesScriptParserTest

# Package JAR
mvn package
```

## Architecture

This is an ANTLR 4-based parser for the **Rules Script DSL** - a domain-specific language for defining eligibility rules.

### Processing Pipeline

```
Input String → ANTLR Lexer → ANTLR Parser → RulesScriptAstBuilder → Domain Model (Script/Rule/Condition)
                 ↓                ↓
         RulesScriptLexer.g4   RulesScriptParser.g4
```

**Entry Point**: `RulesScript.java` - facade providing `parse()`, `validate()`, `isValid()` static methods

**AST Builder**: `RulesScriptAstBuilder.java` - ANTLR visitor that transforms parse tree into domain model

### Domain Model (Sealed Hierarchies)

- `Script` - collection of rules
- `Rule` - rule definition with name, type, condition, messages, score
- `Condition` - sealed interface with 11 variants: `And`, `Or`, `Not`, `SimpleComparison`, `IsEmpty`, `IsNotEmpty`, `Between`, `In`, `NotIn`, `Aggregation`, `GridCheck`
- `Value` - sealed interface: `StringValue`, `NumberValue`, `BooleanValue`, `IdentifierValue`

Pattern matching with `switch` expressions works exhaustively on these sealed types.

## Grammar Notes

**Token Order Matters**: In `RulesScriptLexer.g4`:
- Multi-word keywords (e.g., `IS_NOT_EMPTY`) must come before single-word variants (`IS_EMPTY`)
- All keywords must come before `IDENTIFIER`

**Case Insensitivity**: Keywords use letter fragments (`R U L E` matches "RULE", "rule", "Rule")

**Reserved Word**: `rule` is reserved in ANTLR - use `ctx.rule_()` not `ctx.rule()` in visitor code

## Extending the Grammar

1. Add token to `RulesScriptLexer.g4` (respect ordering)
2. Add grammar rule to `RulesScriptParser.g4`
3. Update domain model in `model/` package
4. Update `RulesScriptAstBuilder.java` visitor
5. Add tests to `RulesScriptParserTest.java` (syntax) and `RulesScriptAstBuilderTest.java` (AST)

## Technology Stack

- Java 17+ (required for sealed interfaces, records, pattern matching)
- ANTLR 4.13.1
- JUnit 5
- Maven
