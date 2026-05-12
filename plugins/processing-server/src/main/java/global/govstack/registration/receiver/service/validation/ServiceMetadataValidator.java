package global.govstack.registration.receiver.service.validation;

import global.govstack.registration.receiver.service.metadata.YamlMetadataService;
import global.govstack.registration.receiver.util.DatabaseSchemaExtractor;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

/**
 * Validates services.yml metadata against actual database schema.
 * Ensures all configured columns exist and match database structure.
 */
public class ServiceMetadataValidator {

    private static final String CLASS_NAME = ServiceMetadataValidator.class.getName();
    private final DataSource dataSource;
    private final Map<String, Object> metadata;
    private final List<ValidationError> errors = new ArrayList<>();
    private final List<ValidationWarning> warnings = new ArrayList<>();

    public static class ValidationError {
        public String section;
        public String field;
        public String message;
        public String expectedColumn;
        public String actualColumn;

        public ValidationError(String section, String field, String message) {
            this.section = section;
            this.field = field;
            this.message = message;
        }

        public ValidationError(String section, String field, String message, String expectedColumn, String actualColumn) {
            this(section, field, message);
            this.expectedColumn = expectedColumn;
            this.actualColumn = actualColumn;
        }

        @Override
        public String toString() {
            return String.format("[%s.%s] %s", section, field, message);
        }
    }

    public static class ValidationWarning {
        public String section;
        public String field;
        public String message;

        public ValidationWarning(String section, String field, String message) {
            this.section = section;
            this.field = field;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("[%s.%s] %s", section, field, message);
        }
    }

    public static class ValidationResult {
        public final boolean valid;
        public final List<ValidationError> errors;
        public final List<ValidationWarning> warnings;
        public final Map<String, Map<String, String>> fieldToColumnMappings;

        public ValidationResult(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings,
                               Map<String, Map<String, String>> fieldToColumnMappings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
            this.fieldToColumnMappings = fieldToColumnMappings;
        }

        public String getReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== Services.yml Validation Report ===\n");
            report.append("Status: ").append(valid ? "VALID" : "INVALID").append("\n");
            report.append("Errors: ").append(errors.size()).append("\n");
            report.append("Warnings: ").append(warnings.size()).append("\n");

            if (!errors.isEmpty()) {
                report.append("\n=== Errors ===\n");
                for (ValidationError error : errors) {
                    report.append("  - ").append(error).append("\n");
                    if (error.expectedColumn != null) {
                        report.append("    Expected: ").append(error.expectedColumn).append("\n");
                        report.append("    Actual: ").append(error.actualColumn != null ? error.actualColumn : "NOT FOUND").append("\n");
                    }
                }
            }

            if (!warnings.isEmpty()) {
                report.append("\n=== Warnings ===\n");
                for (ValidationWarning warning : warnings) {
                    report.append("  - ").append(warning).append("\n");
                }
            }

            return report.toString();
        }
    }

    /**
     * Constructor with DataSource for database validation
     */
    public ServiceMetadataValidator(DataSource dataSource) throws Exception {
        this.dataSource = dataSource;

        // Load metadata using YamlMetadataService
        YamlMetadataService metadataService = new YamlMetadataService();
        metadataService.loadMetadata("farmers_registry");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("formMappings", metadataService.getAllFormMappings());
        metadata.put("service", metadataService.getServiceId());

        this.metadata = metadata;
    }

    /**
     * Constructor with metadata for testing
     */
    public ServiceMetadataValidator(Map<String, Object> metadata, DataSource dataSource) {
        this.metadata = metadata;
        this.dataSource = dataSource;
    }


    /**
     * Validate all metadata configurations
     */
    public ValidationResult validate() {
        errors.clear();
        warnings.clear();

        Map<String, Map<String, String>> fieldToColumnMappings = new HashMap<>();

        try {
            Map<String, Object> formMappings = (Map<String, Object>) metadata.get("formMappings");
            if (formMappings == null) {
                errors.add(new ValidationError("root", "formMappings", "formMappings section not found"));
                return new ValidationResult(false, errors, warnings, fieldToColumnMappings);
            }

            // Validate each form section
            for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> section = (Map<String, Object>) entry.getValue();

                validateFormSection(sectionName, section, fieldToColumnMappings);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error during validation: " + e.getMessage());
            errors.add(new ValidationError("root", "general", "Validation failed: " + e.getMessage()));
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, new ArrayList<>(errors), new ArrayList<>(warnings), fieldToColumnMappings);
    }

    /**
     * Validate a single form section
     */
    private void validateFormSection(String sectionName, Map<String, Object> section,
                                    Map<String, Map<String, String>> fieldToColumnMappings) {
        try {
            String tableName = (String) section.get("tableName");
            if (tableName == null) {
                warnings.add(new ValidationWarning(sectionName, "tableName", "No tableName specified"));
                return;
            }

            // Get table columns from database
            Set<String> tableColumns = getTableColumns(tableName);
            if (tableColumns.isEmpty()) {
                errors.add(new ValidationError(sectionName, "tableName",
                    "Table not found or inaccessible: " + tableName));
                return;
            }

            // Create field-to-column mapping for this section
            Map<String, String> sectionMappings = new HashMap<>();
            fieldToColumnMappings.put(sectionName, sectionMappings);

            // Validate fields
            List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    validateField(sectionName, field, tableColumns, sectionMappings);
                }
            }

        } catch (Exception e) {
            errors.add(new ValidationError(sectionName, "general",
                "Error validating section: " + e.getMessage()));
        }
    }

    /**
     * Validate a single field configuration
     */
    private void validateField(String sectionName, Map<String, Object> field,
                              Set<String> tableColumns, Map<String, String> sectionMappings) {
        String jogetField = (String) field.get("joget");
        if (jogetField == null) {
            warnings.add(new ValidationWarning(sectionName, "unknown", "Field missing 'joget' property"));
            return;
        }

        // Skip column validation for grid/array fields (they don't have physical columns)
        String transform = (String) field.get("transform");
        if ("grid".equals(transform)) {
            LogUtil.debug(CLASS_NAME, "Skipping column validation for grid field: " + sectionName + "." + jogetField);
            return;
        }

        String type = (String) field.get("type");
        if ("array".equals(type)) {
            LogUtil.debug(CLASS_NAME, "Skipping column validation for array field: " + sectionName + "." + jogetField);
            return;
        }

        // Determine the expected column name
        String columnName = (String) field.get("column");
        if (columnName == null || columnName.isEmpty()) {
            // Default to c_[fieldName] pattern
            columnName = "c_" + jogetField;
        }

        // Store the mapping
        sectionMappings.put(jogetField, columnName);

        // Check if column exists in table
        if (!tableColumns.contains(columnName.toLowerCase())) {
            // Try case-insensitive match
            String actualColumn = findColumnCaseInsensitive(tableColumns, columnName);
            if (actualColumn != null) {
                warnings.add(new ValidationWarning(sectionName, jogetField,
                    "Column case mismatch. Expected: " + columnName + ", Found: " + actualColumn));
                // Update mapping with actual column name
                sectionMappings.put(jogetField, actualColumn);
            } else {
                errors.add(new ValidationError(sectionName, jogetField,
                    "Column not found in table", columnName, null));
            }
        }

        // Validate other field properties
        String govstack = (String) field.get("govstack");
        if (govstack == null || govstack.isEmpty()) {
            warnings.add(new ValidationWarning(sectionName, jogetField, "Missing 'govstack' path"));
        }

        // Check for both jsonPath and govstack (indicates misalignment)
        String jsonPath = (String) field.get("jsonPath");
        if (jsonPath != null && !jsonPath.equals(govstack)) {
            warnings.add(new ValidationWarning(sectionName, jogetField,
                "jsonPath differs from govstack path, indicating test data misalignment"));
        }
    }

    /**
     * Get all column names from a database table
     */
    private Set<String> getTableColumns(String tableName) {
        Set<String> columns = new HashSet<>();

        if (dataSource == null) {
            LogUtil.warn(CLASS_NAME, "No DataSource available for validation");
            return columns;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();

            // Try with exact table name first
            try (ResultSet rs = metadata.getColumns(conn.getCatalog(), null, tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }

            // If no columns found, try with different case
            if (columns.isEmpty()) {
                try (ResultSet rs = metadata.getColumns(conn.getCatalog(), null, tableName.toUpperCase(), null)) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    }
                }
            }

            if (columns.isEmpty()) {
                try (ResultSet rs = metadata.getColumns(conn.getCatalog(), null, tableName.toLowerCase(), null)) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    }
                }
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting columns for table " + tableName);
        }

        return columns;
    }

    /**
     * Find column name with case-insensitive match
     */
    private String findColumnCaseInsensitive(Set<String> columns, String columnName) {
        String lowerColumn = columnName.toLowerCase();
        for (String col : columns) {
            if (col.equalsIgnoreCase(lowerColumn)) {
                // Return the actual column name from the set
                return col;
            }
        }
        return null;
    }

    /**
     * Get the field-to-column mappings from validation
     */
    public Map<String, Map<String, String>> getFieldToColumnMappings() {
        ValidationResult result = validate();
        return result.fieldToColumnMappings;
    }
}