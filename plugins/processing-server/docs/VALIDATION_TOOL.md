# Joget Registry Validator

A Python utility that validates whether test data from `test-data.json` has been correctly stored in the Joget MySQL database according to the mappings defined in `services.yml`.

## Features

- **Database Validation**: Validates data stored in Joget MySQL database against test data
- **Field Mapping**: Uses services.yml for flexible field mappings and transformations
- **Multiple Report Formats**: Console, JSON, and HTML reports
- **Selective Validation**: Validate specific farmers, forms, or grids
- **Type-Aware Comparisons**: Handles different data types correctly
- **Connection Testing**: Test database and configuration connectivity

## Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Configure database connection in `config/validation.yaml`

3. Ensure `services.yml` and `test-data.json` are available in the shared_data directory

## Usage

### Basic Validation
```bash
# Validate all farmers with default settings
python ../validate_registry.py

# Validate with custom configuration
python ../validate_registry.py --config /path/to/validation.yaml
```

### Selective Validation
```bash
# Validate specific farmer
python ../validate_registry.py --farmer farmer-001

# Validate specific form across all farmers
python ../validate_registry.py --form farmerBasicInfo

# Validate with custom data sources
python ../validate_registry.py --services /path/to/services.yml --test-data /path/to/test.json
```

### Report Generation
```bash
# Generate only JSON report
python ../validate_registry.py --format json --output ./reports

# Generate all reports with verbose output
python ../validate_registry.py --format all --verbose

# Quiet mode (minimal output)
python ../validate_registry.py --quiet
```

### Testing and Debugging
```bash
# Test connections only
python ../validate_registry.py --test-connections

# Debug mode with detailed logging
python ../validate_registry.py --debug --verbose
```

## Configuration

The validation utility is configured through `config/validation.yaml`:

### Database Configuration
```yaml
database:
  host: localhost
  port: 3306
  database: jogetdb
  user: joget
  password: joget
```

### Data Sources
```yaml
data_sources:
  services_yml: ../shared_data/services.yml
  test_data: ../shared_data/test-data.json
  metadata_dir: ../shared_data/metadata
```

### Validation Settings
```yaml
validation:
  forms_to_validate: []  # Empty = all forms
  grids_to_validate: []  # Empty = all grids
  ignore_fields:
    - dateCreated
    - dateModified
    - createdBy
    - modifiedBy
  case_sensitive: false
  trim_strings: true
  null_equals_empty: true
```

### Reporting Options
```yaml
reporting:
  formats: [console, json, html]
  output_directory: ./validation_reports
  include_passed_fields: false
  max_errors_per_form: 10
  pretty_print: true
```

## Data Structure

### Services.yml
Defines mappings between test data and database columns:

```yaml
govstack:
  forms:
    farmerBasicInfo:
      table: app_fd_farmer_basic
      fields:
        first_name:
          joget: c_first_name
          govstack:
            jsonPath: personalInfo.firstName
          required: true
          type: string
```

### Test-data.json
Contains test data to validate against:

```json
[
  {
    "personalInfo": {
      "farmerId": "farmer-001",
      "firstName": "Thabo",
      "lastName": "Mthembu"
    }
  }
]
```

## Report Formats

### Console Output
- Real-time progress updates
- Formatted summary and detailed results
- Color-coded status indicators

### JSON Reports
- Machine-readable structured data
- Full validation report with metadata
- Summary-only reports for overview

### HTML Reports
- Interactive web-viewable reports
- Expandable/collapsible sections
- Professional styling

## Exit Codes

- **0**: Validation passed successfully
- **1**: Validation failed (data mismatches found)
- **2**: Configuration or connection errors

## Architecture

The validator consists of several key components:

- **Core Validator**: Orchestrates the validation process
- **Parsers**: Handle services.yml and test-data.json parsing
- **Database Connector**: Manages MySQL connections and queries
- **Form/Grid Validators**: Perform actual data validation
- **Field Validator**: Type-aware value comparisons
- **Report Generators**: Generate output in multiple formats

## Dependencies

- `mysql-connector-python`: MySQL database connectivity
- `PyYAML`: YAML configuration file parsing

## Error Handling

The validator includes comprehensive error handling for:

- Database connection failures
- Missing configuration files
- Invalid data formats
- Field mapping errors
- Transformation failures

## Logging

Configurable logging levels (DEBUG, INFO, WARNING, ERROR) with file and console output options.

## Contributing

When extending the validator:

1. Follow the existing code structure
2. Add appropriate error handling
3. Update configuration options as needed
4. Maintain backwards compatibility
5. Add tests for new functionality