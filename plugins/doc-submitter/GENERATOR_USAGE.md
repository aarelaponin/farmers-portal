# Configuration Generator Utilities - Usage Guide

## Overview

These utilities generate `services.yml` and `validation-rules.yaml` deterministically from minimal input files, reducing configuration time from hours to minutes.

### What Gets Generated

1. **`services.yml`** - Complete field mappings for both sender and receiver
   - Auto-detects: master data fields, normalization settings, grids, transforms
   - Requires: Key field mappings (identifiers, names, contacts)

2. **`validation-rules.yaml`** - Conditional validation rules
   - Generated from: Business logic definitions
   - Supports: Required fields, required grids, minimum entries

## Prerequisites

- Java 8 or higher
- Maven 3.6+
- `form_structure.yaml` already generated from your Joget forms

## Quick Start

### Step 1: Compile the Generators

```bash
cd doc-submitter
mvn clean compile
```

### Step 2: Create Hint Files

```bash
# Copy templates
cp templates/mapping-hints-template.yaml mapping-hints.yaml
cp templates/business-rules-template.yaml business-rules.yaml

# Edit with your service-specific values
vim mapping-hints.yaml
vim business-rules.yaml
```

### Step 3: Generate Configuration Files

```bash
# Generate services.yml
mvn exec:java \
  -Dexec.mainClass="global.govstack.farmreg.registration.util.ServicesYamlGenerator" \
  -Dexec.args="--form-structure src/main/resources/docs-metadata/form_structure.yaml --mapping-hints mapping-hints.yaml --output services.yml"

# Generate validation-rules.yaml
mvn exec:java \
  -Dexec.mainClass="global.govstack.farmreg.registration.util.ValidationRulesGenerator" \
  -Dexec.args="--form-structure src/main/resources/docs-metadata/form_structure.yaml --business-rules business-rules.yaml --output validation-rules.yaml"
```

### Step 4: Review and Deploy

```bash
# Review generated files
less services.yml
less validation-rules.yaml

# Copy to plugin resources
cp services.yml src/main/resources/docs-metadata/
cp validation-rules.yaml ../processing-server/src/main/resources/docs-metadata/

# Build plugins
mvn clean package
cd ../processing-server && mvn clean package
```

## Detailed Usage

### ServicesYamlGenerator

#### Required Inputs

1. **form_structure.yaml** - Describes your Joget forms
   - Contains: Forms, fields, types, grids, lookups
   - Generate with: FormStructureExtractor utility

2. **mapping-hints.yaml** - Defines GovStack JSON mappings
   - Minimal file (10-30 lines)
   - Example:
```yaml
service:
  id: student_enrollment
  name: "Student Enrollment Service"

field_mappings:
  student_id: "identifiers[0].value"
  first_name: "name.given[0]"
  last_name: "name.family"
  date_of_birth: "birthDate"

default_mapping: "extension.{fieldName}"
```

#### What Gets Auto-Detected

The generator automatically detects from `form_structure.yaml`:

- ✅ **Master data fields**: Any field with `lookup_form != null`
- ✅ **Yes/No fields**: `type=radio/select`, `options_count=2`, no lookup
- ✅ **Date transformations**: `type=date` or `transform_hint=date_*`
- ✅ **Numeric transformations**: `transform_hint=numeric`
- ✅ **Multiselect checkboxes**: `type=checkbox`, `transform_hint=multiCheckbox`
- ✅ **Grid relationships**: Forms with `child_of` property
- ✅ **Foreign keys**: Hidden fields ending with `_id`

#### Output

Generated `services.yml` contains:
- `service`: Metadata from mapping-hints
- `metadata`: Auto-detected master data + normalization
- `entities`: Standard Person entity structure
- `serviceConfig`: Parent form, section mappings, grid configs
- `formMappings`: Complete field mappings for all forms

#### Example Output Stats

```
ServicesYamlGenerator
====================
Form structure: form_structure.yaml
Mapping hints:  mapping-hints.yaml
Output:         services.yml

Auto-detected 32 master data fields
Auto-detected 27 normalization fields
Mapped 127/135 fields (94% coverage)

✓ Successfully generated: services.yml
```

### ValidationRulesGenerator

#### Required Inputs

1. **form_structure.yaml** - Same as above

2. **business-rules.yaml** - Defines when fields become required
   - Example:
```yaml
conditional_rules:
  - trigger_field: has_scholarship
    trigger_value: "yes"
    requires_fields: [scholarship_type, scholarship_amount]

  - trigger_field: participates_in_sports
    trigger_value: "yes"
    requires_grid: sportsEnrollment
    min_entries: 1
```

#### Output

Generated `validation-rules.yaml`:
```yaml
validation_rules:
  conditional_validations:
    - condition: "has_scholarship == 'yes'"
      required_fields: [scholarship_type, scholarship_amount]
      message: "Scholarship type and amount are required when student has scholarship"

    - condition: "participates_in_sports == 'yes'"
      required_grids: [sportsEnrollment]
      min_entries: 1
      message: "At least 1 sport must be selected when student participates in sports"
```

## Configuration Strategies

### Strategy 1: Minimal Hints (Recommended for Standard Services)

Define only essential mappings, use conventions for the rest:

```yaml
# mapping-hints.yaml (minimal)
service:
  id: student_enrollment
  name: "Student Enrollment Service"

field_mappings:
  # Only specify non-standard mappings
  student_id: "identifiers[0].value"
  first_name: "name.given[0]"
  last_name: "name.family"

default_mapping: "extension.{fieldName}"  # Everything else goes here
```

**Result**: 90% of fields use convention, 10% explicit mappings

### Strategy 2: Explicit Mappings (For Complex Services)

Define all important mappings explicitly:

```yaml
# mapping-hints.yaml (explicit)
field_mappings:
  student_id: "identifiers[0].value"
  national_id: "identifiers[1].value"
  first_name: "name.given[0]"
  middle_name: "name.given[1]"
  last_name: "name.family"
  date_of_birth: "birthDate"
  gender: "gender"
  email_address: "telecom[0].value"
  mobile_number: "telecom[1].value"
  district: "address[0].district"
  city: "address[0].city"
  # ... all fields listed explicitly
```

**Result**: 100% control, no surprises

### Strategy 3: Hybrid Approach (Best for Most Cases)

Mix explicit mappings for core fields + conventions for extensions:

```yaml
field_mappings:
  # Core Person fields (explicit)
  student_id: "identifiers[0].value"
  first_name: "name.given[0]"
  last_name: "name.family"
  date_of_birth: "birthDate"
  email_address: "telecom[0].value"

  # Domain-specific fields use default convention
  # enrollment_date → extension.enrollment_date
  # grade_level → extension.grade_level
```

**Result**: Best balance of control and simplicity

## Mapping Conventions

### Field Name Patterns

The generator recognizes these patterns:

| Pattern | GovStack Path |
|---------|---------------|
| `*_id` (not parent_id) | `identifiers[0].value` |
| `first_name` | `name.given[0]` |
| `middle_name` | `name.given[1]` |
| `last_name` | `name.family` |
| `date_of_birth` | `birthDate` |
| `gender` | `gender` |
| Everything else | `extension.{fieldName}` |

### Transform Detection

| Field Type | Transform Applied |
|------------|-------------------|
| `type: date` | `date_ISO8601` |
| `type: signature` | `base64` |
| `transform_hint: numeric` | `numeric` |
| `transform_hint: multiCheckbox` | `multiCheckbox` |
| `transform_hint: date_ISO8601` | `date_ISO8601` |

### Normalization Detection

| Field Characteristics | Normalization |
|-----------------------|---------------|
| `type: radio/select`, `options_count: 2`, starts with "has_"/"can_"/"is_" | `yesNo` |
| `type: radio/select`, `options_count: 2`, other names | `oneTwo` (default) |
| Has `lookup_form` | Not normalized (master data) |

## Validation Rules Patterns

### Pattern 1: Required Fields When Boolean is True

```yaml
- trigger_field: has_scholarship
  trigger_value: "yes"
  requires_fields:
    - scholarship_type
    - scholarship_amount
```

### Pattern 2: Required Grid When Boolean is True

```yaml
- trigger_field: participates_in_sports
  trigger_value: "yes"
  requires_grid: sportsEnrollment
  min_entries: 1  # At least 1 row required
```

### Pattern 3: Required Fields for Dropdown Value

```yaml
- trigger_field: enrollment_type
  trigger_value: "transfer"
  requires_fields:
    - previous_school
    - transfer_credits
    - transfer_date
```

### Pattern 4: Multiple Fields Triggered

```yaml
- trigger_field: is_international
  trigger_value: "yes"
  requires_fields:
    - nationality
    - visa_type
    - passport_number
    - visa_expiry_date
```

## Troubleshooting

### Issue: "Failed to load form_structure.yaml"

**Cause**: File not found or invalid YAML syntax

**Solution**:
```bash
# Check file exists
ls -la src/main/resources/docs-metadata/form_structure.yaml

# Validate YAML syntax
python3 -c "import yaml; yaml.safe_load(open('form_structure.yaml'))"
```

### Issue: "Low field coverage (50% mapped)"

**Cause**: Many fields are hidden/system fields or HTML displays

**Solution**: This is normal. Check output carefully:
- Hidden `*_id` fields are intentionally skipped
- HTML display fields are skipped
- Only data fields are mapped

### Issue: "Generated services.yml has wrong GovStack paths"

**Cause**: Need to override default conventions

**Solution**: Add explicit mappings to `mapping-hints.yaml`:
```yaml
field_mappings:
  your_field_name: "correct.govstack.path"
```

### Issue: "All fields go to extension.{fieldName}"

**Cause**: No explicit mappings, all use default convention

**Solution**: Add key field mappings to `mapping-hints.yaml`

## Advanced Usage

### Using with Maven Exec Plugin

Add to `pom.xml`:
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.0.0</version>
    <executions>
        <execution>
            <id>generate-services</id>
            <phase>generate-resources</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>global.govstack.farmreg.registration.util.ServicesYamlGenerator</mainClass>
                <arguments>
                    <argument>--form-structure</argument>
                    <argument>src/main/resources/docs-metadata/form_structure.yaml</argument>
                    <argument>--mapping-hints</argument>
                    <argument>mapping-hints.yaml</argument>
                    <argument>--output</argument>
                    <argument>src/main/resources/docs-metadata/services.yml</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then run: `mvn generate-resources`

### Building Standalone JARs

```bash
# Add to pom.xml <build><plugins>:
<plugin>
    <artifactId>maven-assembly-plugin</artifactId>
    <configuration>
        <archive>
            <manifest>
                <mainClass>global.govstack.farmreg.registration.util.ServicesYamlGenerator</mainClass>
            </manifest>
        </archive>
        <descriptorRefs>
            <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
    </configuration>
</plugin>

# Build
mvn clean package assembly:single

# Run standalone
java -jar target/doc-submitter-8.1-SNAPSHOT-jar-with-dependencies.jar \
  --form-structure form_structure.yaml \
  --mapping-hints mapping-hints.yaml \
  --output services.yml
```

## Best Practices

1. **Start with templates**: Always copy from provided templates
2. **Minimal hints**: Define only what's non-standard
3. **Review output**: Always check generated files before deploying
4. **Version control hints**: Keep `mapping-hints.yaml` and `business-rules.yaml` in git
5. **Regenerate on changes**: When forms change, regenerate configuration
6. **Document overrides**: Comment why you override normalization defaults

## Example Workflow

### Configuring Student Registration Service

```bash
# 1. Copy templates
cp templates/mapping-hints-template.yaml student-mapping-hints.yaml
cp templates/business-rules-template.yaml student-business-rules.yaml

# 2. Edit hints (5 minutes)
vim student-mapping-hints.yaml
# Set service.id: student_enrollment
# Map: student_id, first_name, last_name, etc.

# 3. Edit business rules (5 minutes)
vim student-business-rules.yaml
# Define: scholarship rules, sports rules, etc.

# 4. Generate (2 seconds)
mvn exec:java \
  -Dexec.mainClass="global.govstack.farmreg.registration.util.ServicesYamlGenerator" \
  -Dexec.args="--form-structure src/main/resources/docs-metadata/form_structure.yaml --mapping-hints student-mapping-hints.yaml --output services.yml"

mvn exec:java \
  -Dexec.mainClass="global.govstack.farmreg.registration.util.ValidationRulesGenerator" \
  -Dexec.args="--form-structure src/main/resources/docs-metadata/form_structure.yaml --business-rules student-business-rules.yaml --output validation-rules.yaml"

# 5. Review (2 minutes)
less services.yml
less validation-rules.yaml

# 6. Deploy (1 minute)
cp services.yml src/main/resources/docs-metadata/
cp validation-rules.yaml ../processing-server/src/main/resources/docs-metadata/

# Total time: ~15 minutes (vs 3 hours manual)
```

## Support

For issues or questions:
1. Check this guide's troubleshooting section
2. Review template files for examples
3. Check main CONFIGURATION_GUIDE.md
4. Examine generated output for patterns

## Version History

- **v1.0** (2025-10-06): Initial release
  - ServicesYamlGenerator with auto-detection
  - ValidationRulesGenerator with business rules
  - Template files and documentation
