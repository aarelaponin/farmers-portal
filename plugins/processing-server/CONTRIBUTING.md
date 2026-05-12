# Contributing to GovStack Processing Server Plugin

## How to Contribute

We welcome contributions to improve the GovStack Processing Server Plugin!

### Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a new branch for your feature/fix
4. Make your changes
5. Test thoroughly
6. Submit a pull request

### Development Setup

1. **Prerequisites**
   - Java 8+
   - Maven 3.6+
   - Joget 7.0+ (for testing)
   - MySQL 5.7+

2. **Build the project**
   ```bash
   mvn clean package -Dmaven.test.skip=true
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

### Making Changes

#### Field Mapping Changes

If you need to modify field mappings:

1. Edit `src/main/resources/docs-metadata/services.yml`
2. Update test data if needed in `test-data.json`
3. Run validation to ensure mappings work:
   ```bash
   cd /Users/aarelaponin/PycharmProjects/dev/gam/joget_validator
   ./regenerate_and_validate.sh
   ```
4. Document your changes in `docs/FIELD_MAPPING_FIXES.md`

#### Code Changes

1. Follow existing code style
2. Add unit tests for new functionality
3. Ensure all tests pass
4. Update documentation as needed

### Testing

Before submitting a PR:

1. **Run unit tests**
   ```bash
   mvn test
   ```

2. **Deploy and test in Joget**
   - Build the JAR
   - Deploy to Joget test instance
   - Submit test data via API
   - Run validation tool

3. **Validate data mapping**
   ```bash
   python3 run_diagnostic_validation.py --spec generated/test-validation.yml
   ```

### Pull Request Process

1. **PR Title**: Use descriptive title (e.g., "Fix: Correct livestock field mappings")

2. **PR Description** should include:
   - What changes were made
   - Why the changes were necessary
   - How to test the changes
   - Any breaking changes

3. **Checklist**:
   - [ ] Tests pass
   - [ ] Documentation updated
   - [ ] Validation passes
   - [ ] No breaking changes (or documented if any)

### Code Style

- Java: Follow standard Java conventions
- YAML: 2-space indentation
- Documentation: Markdown format

### Reporting Issues

When reporting issues, please include:

1. Joget version
2. Plugin version
3. Error messages from logs
4. Steps to reproduce
5. Expected vs actual behavior

### Questions?

- Check existing documentation in `docs/`
- Review closed issues for similar problems
- Open a new issue with the "question" label

## Code of Conduct

- Be respectful and constructive
- Help others learn and grow
- Focus on what is best for the community
- Show empathy towards other community members

Thank you for contributing!