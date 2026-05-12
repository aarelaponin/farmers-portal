# Contributing to Joget Rule Editor

Thank you for your interest in contributing to the Joget Rule Editor! This document provides guidelines and information for contributors.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Setup](#development-setup)
4. [Making Contributions](#making-contributions)
5. [Coding Standards](#coding-standards)
6. [Testing](#testing)
7. [Pull Request Process](#pull-request-process)
8. [Issue Reporting](#issue-reporting)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

---

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Git
- (Optional) Joget DX 8.1 for deployment testing

### Quick Start

```bash
# Fork and clone the repository
git clone https://github.com/YOUR_USERNAME/joget-rule-editor.git
cd joget-rule-editor

# Download CodeMirror dependencies
chmod +x download-codemirror.sh
./download-codemirror.sh

# Build the project
mvn clean package

# Run tests
mvn test
```

---

## Development Setup

### IDE Setup

**IntelliJ IDEA (Recommended):**
1. File → Open → Select `pom.xml`
2. Import as Maven project
3. Enable annotation processing (for Lombok if added later)

**Eclipse:**
1. File → Import → Maven → Existing Maven Projects
2. Select project directory

### Project Structure

```
joget-rule-editor/
├── src/main/java/global/govstack/ruleeditor/
│   ├── adapter/      # Model adapters (ANTLR → legacy)
│   ├── parser/       # RuleScriptParser facade
│   ├── compiler/     # SQL compilation
│   ├── model/        # Legacy data models
│   ├── service/      # Business services
│   ├── lib/          # API endpoints
│   └── element/      # Joget form elements
├── src/main/resources/
│   ├── static/       # JavaScript, CSS
│   ├── templates/    # FreeMarker templates
│   └── properties/   # Plugin configurations
├── src/test/java/    # Unit tests
└── docs/             # Documentation

# Related project (embedded as dependency):
../rules-grammar/     # ANTLR-based parser library
```

---

## Making Contributions

### Types of Contributions

We welcome:

- **Bug fixes** - Fix issues in existing code
- **Features** - Add new language features or UI enhancements
- **Documentation** - Improve docs, add examples
- **Tests** - Increase test coverage
- **Performance** - Optimize parser or compiler

### Contribution Workflow

1. **Check existing issues** - Look for related issues or discussions
2. **Create an issue** - For significant changes, discuss first
3. **Fork the repository**
4. **Create a feature branch** - `git checkout -b feature/your-feature`
5. **Make changes** - Follow coding standards
6. **Write tests** - Cover new functionality
7. **Update documentation** - If adding features
8. **Submit a pull request**

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation changes
- `refactor/description` - Code refactoring
- `test/description` - Test additions

---

## Coding Standards

### Java Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4 spaces for indentation (not tabs)
- Maximum line length: 120 characters
- Add Javadoc for public methods and classes

```java
/**
 * Parse an Rules Script script and return validation results.
 *
 * @param script The Rules Script script text
 * @return ValidationResult containing parsed rules, errors, and warnings
 * @throws ParseException if script contains syntax errors
 */
public ValidationResult parse(String script) {
    // Implementation
}
```

### JavaScript Code Style

- Use 4 spaces for indentation
- Use single quotes for strings
- Add JSDoc comments for functions

```javascript
/**
 * Initialize the Rule Editor
 * @param {string} containerId - DOM element ID
 * @param {Object} options - Configuration options
 * @returns {Object} Editor instance
 */
function init(containerId, options) {
    // Implementation
}
```

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types:
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation
- `style` - Formatting (no code change)
- `refactor` - Code restructuring
- `test` - Adding tests
- `chore` - Maintenance

Examples:
```
feat(parser): add MATCHES operator for regex support

fix(compiler): handle NULL values in BETWEEN clause

docs(readme): add installation instructions
```

---

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RuleScriptParserTest

# Run with coverage
mvn test jacoco:report
```

### Writing Tests

- Place tests in `src/test/java`
- Mirror the source package structure
- Name test classes with `Test` suffix

```java
package global.govstack.ruleeditor.parser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleScriptParserTest {

    @Test
    void testSimpleRule() {
        String script = "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18";

        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult result = parser.parse(script);

        assertTrue(result.isValid());
        assertEquals(1, result.getRuleCount());
    }

    @Test
    void testInvalidSyntax() {
        String script = "RULE TYPE: INCLUSION";  // Missing rule name

        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult result = parser.parse(script);

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }
}
```

### Test Categories

- **Unit tests** - Test individual components in isolation
- **Integration tests** - Test component interactions
- **Parser tests** - Grammar rule coverage
- **Compiler tests** - SQL generation accuracy

---

## Pull Request Process

### Before Submitting

1. **Update from main** - `git pull origin main`
2. **Run tests** - `mvn test`
3. **Build successfully** - `mvn clean package`
4. **Update documentation** - If applicable
5. **Self-review** - Check your code for issues

### PR Template

```markdown
## Description
[Describe your changes]

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation
- [ ] Refactoring

## Testing
- [ ] Unit tests added/updated
- [ ] All tests passing
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No breaking changes (or documented)
```

### Review Process

1. Submit PR with clear description
2. Automated tests run via CI
3. Maintainer reviews code
4. Address feedback if needed
5. Approval and merge

---

## Issue Reporting

### Bug Reports

Include:
- **Description** - What happened
- **Expected behavior** - What should happen
- **Steps to reproduce** - How to trigger the bug
- **Environment** - Java version, OS, Joget version
- **Rules Script script** - If relevant

```markdown
**Bug Description**
Parser fails when rule name contains quotes.

**Expected Behavior**
Escaped quotes in rule names should be handled.

**Steps to Reproduce**
1. Enter script: RULE "Test \"Quoted\" Name" TYPE: INCLUSION
2. Click Validate
3. See error

**Environment**
- Java 11
- Joget DX 8.1
- macOS 14.0

**Rules Script**
```text
RULE "Test \"Quoted\" Name"
  TYPE: INCLUSION
  WHEN age >= 18
```
```

### Feature Requests

Include:
- **Description** - What feature you want
- **Use case** - Why you need it
- **Proposed syntax** - How it might look
- **Alternatives** - Other approaches considered

---

## Getting Help

- **Documentation** - Check `/docs` folder
- **Issues** - Search existing issues
- **Discussions** - Start a discussion for questions

---

## Recognition

Contributors are recognized in:
- `CONTRIBUTORS.md` file
- Release notes
- Project documentation

Thank you for contributing to Joget Rule Editor!
