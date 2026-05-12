package global.govstack.registration.receiver.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Classifies form fields by their type to enable appropriate processing
 *
 * Field Types:
 * - LOV_YES_NO: Fields with yes/no options
 * - LOV_ONE_TWO: Fields with 1/2 options
 * - MASTERDATA: Fields using FormOptionsBinder to load from masterdata
 * - TEXT: Plain text fields
 * - DATE: Date fields
 * - NUMERIC: Numeric fields
 * - GRID: Grid/array fields
 */
public class FieldTypeClassifier {

    private static final Logger LOGGER = Logger.getLogger(FieldTypeClassifier.class.getName());

    public enum FieldType {
        LOV_YES_NO,      // Radio/Select with yes/no options
        LOV_ONE_TWO,     // Radio/Select with 1/2 options
        LOV_CUSTOM,      // Radio/Select with other custom options
        MASTERDATA,      // SelectBox with FormOptionsBinder
        TEXT,            // TextField
        DATE,            // DateField
        NUMERIC,         // TextField with numeric validation
        GRID,            // Grid/Subform
        HIDDEN,          // HiddenField
        UNKNOWN          // Unknown type
    }

    public static class FieldInfo {
        public String formId;
        public String fieldId;
        public FieldType type;
        public String className;
        public List<String> options;
        public String masterdataSource; // For MASTERDATA type
        public boolean mandatory;

        public FieldInfo(String formId, String fieldId, FieldType type) {
            this.formId = formId;
            this.fieldId = fieldId;
            this.type = type;
            this.options = new ArrayList<>();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(fieldId).append(" (").append(type).append(")");
            if (masterdataSource != null) {
                sb.append(" [source: ").append(masterdataSource).append("]");
            }
            if (!options.isEmpty()) {
                sb.append(" options: ").append(options);
            }
            return sb.toString();
        }
    }

    private final Map<String, FieldInfo> fieldRegistry = new HashMap<>();
    private final Set<String> lovYesNoFields = new HashSet<>();
    private final Set<String> lovOneTwoFields = new HashSet<>();
    private final Set<String> masterdataFields = new HashSet<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Analyze form JSON files and classify all fields
     */
    public void analyzeFormFiles(String formDirectory) throws IOException {
        File dir = new File(formDirectory);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Form directory not found: " + formDirectory);
        }

        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            LOGGER.warning("No JSON files found in: " + formDirectory);
            return;
        }

        for (File jsonFile : jsonFiles) {
            analyzeFormFile(jsonFile);
        }

        LOGGER.info("Field analysis complete:");
        LOGGER.info("  - Total fields: " + fieldRegistry.size());
        LOGGER.info("  - LOV yes/no fields: " + lovYesNoFields.size());
        LOGGER.info("  - LOV 1/2 fields: " + lovOneTwoFields.size());
        LOGGER.info("  - Masterdata fields: " + masterdataFields.size());
    }

    /**
     * Analyze a single form JSON file
     */
    private void analyzeFormFile(File jsonFile) throws IOException {
        LOGGER.info("Analyzing form file: " + jsonFile.getName());

        JsonNode rootNode = mapper.readTree(jsonFile);
        String formId = extractFormId(jsonFile.getName());

        // Recursively process all elements
        processElements(rootNode, formId);
    }

    /**
     * Extract form ID from filename
     */
    private String extractFormId(String filename) {
        // Remove .json extension and use filename as form ID
        return filename.replace(".json", "");
    }

    /**
     * Recursively process form elements
     */
    private void processElements(JsonNode node, String formId) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.has("elements") && node.get("elements").isArray()) {
            for (JsonNode element : node.get("elements")) {
                processElement(element, formId);
                // Recursively process nested elements
                processElements(element, formId);
            }
        }

        // Also check for direct element
        if (node.has("className") && node.has("properties")) {
            processElement(node, formId);
        }
    }

    /**
     * Process a single form element
     */
    private void processElement(JsonNode element, String formId) {
        if (!element.has("className") || !element.has("properties")) {
            return;
        }

        String className = element.get("className").asText();
        JsonNode properties = element.get("properties");

        if (!properties.has("id")) {
            return;
        }

        String fieldId = properties.get("id").asText();
        if (fieldId.isEmpty()) {
            return;
        }

        FieldInfo fieldInfo = new FieldInfo(formId, fieldId, FieldType.UNKNOWN);
        fieldInfo.className = className;

        // Check if field is mandatory
        if (properties.has("validator")) {
            JsonNode validator = properties.get("validator");
            if (validator.has("properties")) {
                JsonNode validatorProps = validator.get("properties");
                if (validatorProps.has("mandatory")) {
                    fieldInfo.mandatory = "true".equals(validatorProps.get("mandatory").asText());
                }
            }
        }

        // Classify based on className and properties
        switch (className) {
            case "org.joget.apps.form.lib.Radio":
            case "org.joget.apps.form.lib.SelectBox":
                classifySelectField(fieldInfo, properties);
                break;

            case "org.joget.apps.form.lib.TextField":
                fieldInfo.type = FieldType.TEXT;
                break;

            case "org.joget.apps.form.lib.DateField":
                fieldInfo.type = FieldType.DATE;
                break;

            case "org.joget.apps.form.lib.Grid":
            case "org.joget.apps.form.lib.SubForm":
                fieldInfo.type = FieldType.GRID;
                break;

            case "org.joget.apps.form.lib.HiddenField":
                fieldInfo.type = FieldType.HIDDEN;
                break;

            default:
                fieldInfo.type = FieldType.UNKNOWN;
        }

        // Register the field
        fieldRegistry.put(fieldId, fieldInfo);

        // Add to specialized sets
        if (fieldInfo.type == FieldType.LOV_YES_NO) {
            lovYesNoFields.add(fieldId);
        } else if (fieldInfo.type == FieldType.LOV_ONE_TWO) {
            lovOneTwoFields.add(fieldId);
        } else if (fieldInfo.type == FieldType.MASTERDATA) {
            masterdataFields.add(fieldId);
        }

        LOGGER.fine("Registered field: " + fieldInfo);
    }

    /**
     * Classify a select/radio field based on its options
     */
    private void classifySelectField(FieldInfo fieldInfo, JsonNode properties) {
        // Check for FormOptionsBinder (masterdata)
        if (properties.has("optionsBinder")) {
            JsonNode binder = properties.get("optionsBinder");
            if (binder.has("className")) {
                String binderClass = binder.get("className").asText();
                if ("org.joget.apps.form.lib.FormOptionsBinder".equals(binderClass)) {
                    fieldInfo.type = FieldType.MASTERDATA;
                    if (binder.has("properties")) {
                        JsonNode binderProps = binder.get("properties");
                        if (binderProps.has("formDefId")) {
                            fieldInfo.masterdataSource = binderProps.get("formDefId").asText();
                        }
                    }
                    return;
                }
            }
        }

        // Check hardcoded options
        if (properties.has("options") && properties.get("options").isArray()) {
            JsonNode options = properties.get("options");
            List<String> values = new ArrayList<>();

            for (JsonNode option : options) {
                if (option.has("value")) {
                    values.add(option.get("value").asText());
                }
            }

            fieldInfo.options = values;

            // Classify based on option values
            if (values.contains("yes") && values.contains("no")) {
                fieldInfo.type = FieldType.LOV_YES_NO;
            } else if (values.contains("1") && values.contains("2")) {
                fieldInfo.type = FieldType.LOV_ONE_TWO;
            } else if (!values.isEmpty()) {
                fieldInfo.type = FieldType.LOV_CUSTOM;
            }
        }
    }

    /**
     * Get field type for a specific field
     */
    public FieldType getFieldType(String fieldId) {
        FieldInfo info = fieldRegistry.get(fieldId);
        return info != null ? info.type : FieldType.UNKNOWN;
    }

    /**
     * Check if field is a LOV field that needs normalization
     */
    public boolean isLOVField(String fieldId) {
        FieldType type = getFieldType(fieldId);
        return type == FieldType.LOV_YES_NO || type == FieldType.LOV_ONE_TWO;
    }

    /**
     * Check if field is a masterdata field
     */
    public boolean isMasterdataField(String fieldId) {
        return masterdataFields.contains(fieldId);
    }

    /**
     * Get all LOV fields that use yes/no
     */
    public Set<String> getLovYesNoFields() {
        return new HashSet<>(lovYesNoFields);
    }

    /**
     * Get all LOV fields that use 1/2
     */
    public Set<String> getLovOneTwoFields() {
        return new HashSet<>(lovOneTwoFields);
    }

    /**
     * Get all masterdata fields
     */
    public Set<String> getMasterdataFields() {
        return new HashSet<>(masterdataFields);
    }

    /**
     * Get complete field registry
     */
    public Map<String, FieldInfo> getFieldRegistry() {
        return new HashMap<>(fieldRegistry);
    }

    /**
     * Generate a report of all fields
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Field Type Classification Report ===\n\n");

        // Group by type
        Map<FieldType, List<FieldInfo>> byType = new HashMap<>();
        for (FieldInfo field : fieldRegistry.values()) {
            byType.computeIfAbsent(field.type, k -> new ArrayList<>()).add(field);
        }

        // Report each type
        for (FieldType type : FieldType.values()) {
            List<FieldInfo> fields = byType.getOrDefault(type, new ArrayList<>());
            if (!fields.isEmpty()) {
                report.append("\n").append(type).append(" (").append(fields.size()).append(" fields):\n");
                fields.sort(Comparator.comparing(f -> f.fieldId));
                for (FieldInfo field : fields) {
                    report.append("  - ").append(field.toString());
                    if (field.mandatory) {
                        report.append(" [REQUIRED]");
                    }
                    report.append("\n");
                }
            }
        }

        return report.toString();
    }
}