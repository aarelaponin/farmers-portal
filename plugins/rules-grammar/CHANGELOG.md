# Changelog

All notable changes to the Rules Grammar project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0-SNAPSHOT] - 2024-12-26

### Added

#### Phase 1: Grammar Definition
- ANTLR 4 lexer grammar (`RulesScriptLexer.g4`) with:
  - Case-insensitive keywords using letter fragments
  - Multi-word keywords: `IS EMPTY`, `IS NOT EMPTY`, `NOT IN`, `STARTS WITH`, `ENDS WITH`, `PASS MESSAGE`, `FAIL MESSAGE`
  - Rule structure keywords: `RULE`, `TYPE`, `CATEGORY`, `MANDATORY`, `ORDER`, `WHEN`, `SCORE`, `WEIGHT`
  - Rule types: `INCLUSION`, `EXCLUSION`, `PRIORITY`, `BONUS`
  - Boolean values: `YES`, `NO`, `TRUE`, `FALSE`
  - Logical operators: `AND`, `OR`, `NOT`
  - Comparison operators: `=`, `!=`, `>`, `>=`, `<`, `<=`, `CONTAINS`, `BETWEEN`, `IN`
  - Aggregation functions: `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`
  - Grid check functions: `HAS_ANY`, `HAS_ALL`, `HAS_NONE`
  - String literals (single and double quoted) with escape support
  - Number literals (integer and decimal, positive and negative)
  - Identifiers with underscore support
  - Comments (`#` style)

- ANTLR 4 parser grammar (`RulesScriptParser.g4`) with:
  - Script as collection of rules
  - Rule definition with all clause types
  - Condition expressions with proper operator precedence (NOT > AND > OR)
  - All comparison types with labeled alternatives
  - Function calls for aggregation and grid checks
  - Field references with dot notation support
  - Value lists for IN clauses

#### Phase 2: Parser Infrastructure
- Domain model classes:
  - `Script` - Root container for rules
  - `Rule` - Complete rule representation with builder pattern
  - `RuleType` - Enum for rule types
  - `Condition` - Sealed interface hierarchy for all condition types
  - `Value` - Sealed interface for value types (string, number, boolean, identifier)
  - `FieldRef` - Field reference with dot notation support
  - `ComparisonOperator` - Enum for comparison operators

- Parser infrastructure:
  - `RulesScript` - Main facade API with parse/validate methods
  - `RulesScriptAstBuilder` - ANTLR visitor for building domain objects
  - `ParseResult` - Result wrapper with success/failure handling
  - `ParseError` - Structured error representation
  - `RulesScriptParseException` - Exception for parse failures

- Comprehensive test suite:
  - 91 grammar tests covering all syntax features
  - 29 AST builder tests covering domain object construction
  - Parameterized tests for variations (case insensitivity, operators)
  - Error handling tests

- Documentation:
  - README.md with quick start and usage examples
  - DEVELOPER.md with comprehensive maintenance and extensibility guide
  - CHANGELOG.md for version tracking

### Technical Details
- Java 17 required (sealed interfaces, records, pattern matching)
- ANTLR 4.13.1
- JUnit 5.10.0 for testing
- Maven build with ANTLR plugin

---

## Migration Notes

### From Hand-Written Parser

This ANTLR-based parser replaces the hand-written parser in `joget-rule-editor`:

| Component | Old (Hand-Written) | New (ANTLR) |
|-----------|-------------------|-------------|
| Lexer | `RuleScriptLexer.java` (13,118 lines) | `RulesScriptLexer.g4` (152 lines) |
| Parser | `RuleScriptParser.java` (20,539 lines) | `RulesScriptParser.g4` (141 lines) |
| Tokens | `Token.java` (4,399 lines) | Generated |
| Total | ~47,000 lines | ~293 lines grammar + ~500 lines Java |

### Integration Path

1. Add dependency to `joget-rule-editor`
2. Create adapter from ANTLR model to existing internal model
3. Replace parser calls with `RulesScript.parse()`
4. Gradually migrate validation and compilation logic
