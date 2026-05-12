# GovStack Processing Server - Documentation Index

## 📚 Available Documentation

### Core Documentation
- **[Field Mapping Fixes](FIELD_MAPPING_FIXES.md)** - Quick summary of September 2025 fixes
- **[Complete Fix Documentation](FIXES_DOCUMENTATION.md)** - Detailed technical documentation of all changes
- **[Services YAML Guide](SERVICES_YML_GUIDE.md)** - Complete guide to configuring field mappings
- **[Generic Configuration Guide](GENERIC_CONFIGURATION.md)** - How to configure plugin for any service type (not just farmers)

### Database Documentation
- **[Database Schema](DATABASE_SCHEMA.md)** - Initial database schema analysis
- **[Database Schema Verified](DATABASE_SCHEMA_VERIFIED.md)** - Verified actual database structure

### Tools Documentation
- **[Validation Tool](VALIDATION_TOOL.md)** - Guide to using the validation framework

### Additional Documentation
- **[Test Summary](TEST_SUMMARY.md)** - Initial test results and issues
- **[Selectbox Mapping Notes](SELECTBOX_MAPPING_NOTES.md)** - Notes on dropdown field mappings

## Quick Links

### Most Common Tasks

1. **Fix a field mapping issue**
   - Check [Services YAML Guide](SERVICES_YML_GUIDE.md)
   - Edit `../src/main/resources/docs-metadata/services.yml`
   - Run validation tool

2. **Debug missing data**
   - Review [Complete Fix Documentation](FIXES_DOCUMENTATION.md) Section 3
   - Check common issues in Appendix A

3. **Run validation**
   ```bash
   cd /Users/aarelaponin/PycharmProjects/dev/gam/joget_validator
   ./regenerate_and_validate.sh
   ```

4. **Add new field**
   - Add to form JSON in `../doc-forms/`
   - Add mapping in `services.yml`
   - Add test data in `test-data.json`
   - Regenerate validation spec

## Documentation Versions

| Document | Last Updated | Version |
|----------|--------------|---------|
| Field Mapping Fixes | Sept 25, 2025 | 1.0 |
| Complete Fix Documentation | Sept 25, 2025 | 1.0 |
| Services YAML Guide | Sept 22, 2025 | 1.0 |
| Database Schema Verified | Sept 25, 2025 | 1.0 |
| Validation Tool | Sept 25, 2025 | 1.0 |

---

*For the main README, see [../README.md](../README.md)*