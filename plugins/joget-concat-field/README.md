# Joget Concatenation Field Plugin

A Joget DX 8 form element plugin that concatenates values from multiple source fields into a single output value.

## Features

- **Multiple Source Fields**: Configure any number of fields to concatenate
- **Configurable Separator**: Choose the character(s) between values (e.g., `_`, `-`, ` `)
- **Format Pattern**: Use custom patterns like `{0}-{1}_{2}` for precise control
- **Transformations**: Apply uppercase, lowercase, capitalize, or trim to each field
- **Display Options**: Hidden, read-only, or editable output field
- **Real-time Updates**: Updates automatically as source fields change
- **Skip Empty Values**: Optionally exclude empty fields from concatenation
- **Prefix/Suffix**: Add text before or after the result

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Deploy `target/joget-concat-field-8.1-SNAPSHOT.jar` to Joget:
   - Go to **Settings > Manage Plugins**
   - Click **Upload Plugin**
   - Select the JAR file

3. The plugin appears in the **GovStack** category in the Form Builder palette

## Configuration

### Basic Settings

| Property | Description | Default |
|----------|-------------|---------|
| **Field ID** | Database column name for storing the result. Must be unique within the form. | `concatenated_value` |
| **Label** | Display label shown for non-hidden modes | `Concatenated Value` |
| **Display Type** | How to render the field (see below) | `Hidden Field` |
| **Required** | Whether the field must have a value on submit | unchecked |
| **Required Error Message** | Custom error message when required validation fails | `This field is required` |

#### Display Types

| Type | Description |
|------|-------------|
| **Hidden Field** | Value is stored but not visible to users. Use for computed keys/IDs. |
| **Read-only Text Field** | Value is displayed in a styled box but cannot be edited. |
| **Editable Text Field** | User can modify the concatenated value before submission. |

### Source Fields

Add fields in the order you want them concatenated:

| Column | Description |
|--------|-------------|
| **Field ID** | The ID of the source field (must match the field's ID property exactly) |
| **Transform** | Optional transformation applied to the field value |

#### Available Transforms

| Transform | Description | Example |
|-----------|-------------|---------|
| **None** | Use value as-is | `John Doe` → `John Doe` |
| **UPPERCASE** | Convert to uppercase | `John Doe` → `JOHN DOE` |
| **lowercase** | Convert to lowercase | `John Doe` → `john doe` |
| **Capitalize** | Capitalize first letter only | `john doe` → `John doe` |
| **Trim whitespace** | Remove leading/trailing spaces | `  John  ` → `John` |

### Format Settings

| Property | Description | Default |
|----------|-------------|---------|
| **Separator** | Character(s) inserted between field values when not using format pattern | `_` |
| **Format Pattern** | Custom pattern using `{0}`, `{1}`, `{2}` placeholders for positional values. Leave empty for simple concatenation. | (empty) |
| **Prefix** | Text added before the concatenated result | (empty) |
| **Suffix** | Text added after the concatenated result | (empty) |
| **Skip Empty Values** | When checked, empty fields are omitted and their separator is skipped | checked |

#### Format Pattern Examples

| Pattern | Source Values | Result |
|---------|---------------|--------|
| (empty, separator=`_`) | `John`, `Doe`, `1990` | `John_Doe_1990` |
| `{0}-{1}_{2}` | `John`, `Doe`, `1990` | `John-Doe_1990` |
| `{2}/{1}/{0}` | `John`, `Doe`, `1990` | `1990/Doe/John` |
| `ID:{0}` | `12345` | `ID:12345` |

### Behavior

| Property | Description | Default |
|----------|-------------|---------|
| **Update On** | When to recalculate the concatenated value | `Field Change` |

#### Update Triggers

| Trigger | Description |
|---------|-------------|
| **Field Change (real-time)** | Updates immediately as user types or selects values |
| **Field Blur (on focus out)** | Updates when user clicks/tabs away from a source field |
| **Form Submit Only** | Only computes value on form submission (server-side) |

## Use Cases

### Unique Identifiers
Generate composite keys from multiple fields:
- Fields: `national_id`, `first_name`, `last_name`, `date_of_birth`
- Separator: `_`
- Result: `12345678_John_Doe_1990-01-15`

### Full Names
Combine name parts:
- Fields: `first_name`, `last_name`
- Separator: ` ` (space)
- Result: `John Doe`

### Document References
Create structured references:
- Fields: `year`, `department`, `sequence`
- Format Pattern: `{0}-{1}-{2}`
- Prefix: `DOC-`
- Result: `DOC-2024-HR-0042`

### Standardized Codes
Ensure consistent formatting:
- Fields: `country_code`, `region`, `identifier`
- Transform: UPPERCASE on all fields
- Separator: `-`
- Result: `US-CA-ABC123`

## Troubleshooting

### Field Not Found
- Verify the source field ID matches exactly (case-sensitive)
- Joget may prefix field IDs in the DOM; the plugin tries multiple selector patterns
- Enable debug mode: set `DEBUG = true` in `ConcatFieldElement.ftl` line 79
- Check browser console for `[ConcatField]` log messages

### Value Not Updating
- Check the **Update On** setting matches expected behavior
- For date pickers and other complex widgets, try **Field Blur** instead of **Field Change**
- Verify source fields exist and have values

### Value Empty on Submit
- Server-side fallback computes the value if JavaScript didn't run
- Check that source field IDs match the actual form field names
- Verify fields are not conditionally hidden/removed

### Debug Mode
Access the component state in browser console:
```javascript
window.concatField_yourFieldId.compute()  // Manually compute value
window.concatField_yourFieldId.update()   // Force update
window.concatField_yourFieldId.config     // View configuration
```

## Technical Details

### Architecture
- **OSGi Bundle**: Packaged with Felix Bundle Plugin for Joget DX 8.x
- **Java 17**: Required for compilation
- **Client-side**: JavaScript handles real-time updates
- **Server-side fallback**: Java computes value if JS didn't execute

### Files
```
src/main/java/global/govstack/concatfield/
├── Activator.java              # OSGi bundle activator
└── element/
    ├── ConcatFieldElement.java # Form element implementation
    └── ConcatFieldResources.java # Static file server

src/main/resources/
├── properties/
│   └── ConcatFieldElement.json # Plugin configuration UI
├── templates/
│   └── ConcatFieldElement.ftl  # FreeMarker template with JS
└── static/
    └── concat-field.css        # Styling
```

## License

MIT License

## Author

GovStack Team
