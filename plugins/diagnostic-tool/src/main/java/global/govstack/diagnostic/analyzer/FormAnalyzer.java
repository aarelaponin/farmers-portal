package global.govstack.diagnostic.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.*;

/**
 * Analyzes Joget form JSON definitions
 */
public class FormAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, FormDefinition> analyzeForms(File formsDir) throws Exception {
        Map<String, FormDefinition> forms = new HashMap<>();

        if (!formsDir.exists() || !formsDir.isDirectory()) {
            throw new IllegalArgumentException("Forms directory not found: " + formsDir.getPath());
        }

        File[] formFiles = formsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (formFiles == null) {
            return forms;
        }

        for (File formFile : formFiles) {
            try {
                FormDefinition formDef = analyzeForm(formFile);
                forms.put(formDef.getFormId(), formDef);
            } catch (Exception e) {
                System.err.println("Warning: Could not parse form " + formFile.getName() + ": " + e.getMessage());
            }
        }

        return forms;
    }

    private FormDefinition analyzeForm(File formFile) throws Exception {
        JsonNode root = objectMapper.readTree(formFile);

        FormDefinition formDef = new FormDefinition();
        formDef.setFileName(formFile.getName());

        // Extract form properties
        JsonNode properties = root.get("properties");
        if (properties != null) {
            formDef.setFormId(properties.path("id").asText("unknown"));
            formDef.setFormName(properties.path("name").asText("Unknown Form"));
            formDef.setTableName(properties.path("tableName").asText(null));
        }

        // Extract fields recursively
        Set<String> fields = new HashSet<>();
        extractFields(root, fields);
        formDef.setFields(fields);

        return formDef;
    }

    private void extractFields(JsonNode node, Set<String> fields) {
        if (node == null || node.isNull()) {
            return;
        }

        // Check if this node represents a field
        if (node.has("properties") && node.get("properties").has("id")) {
            String fieldId = node.get("properties").get("id").asText();
            String className = node.path("className").asText("");

            // Only include actual form fields, not sections or structural elements
            if (isFormField(className, fieldId)) {
                fields.add(fieldId);
            }
        }

        // Recursively check elements
        if (node.has("elements") && node.get("elements").isArray()) {
            for (JsonNode element : node.get("elements")) {
                extractFields(element, fields);
            }
        }

        // Check for grid columns (subforms)
        if (node.has("columns") && node.get("columns").isArray()) {
            for (JsonNode column : node.get("columns")) {
                extractFields(column, fields);
            }
        }
    }

    private boolean isFormField(String className, String fieldId) {
        // Exclude structural elements
        if (fieldId.startsWith("section") || fieldId.startsWith("column") ||
            fieldId.endsWith("Section") || fieldId.endsWith("Column") ||
            fieldId.endsWith("Wizard") || fieldId.equals("mainSection")) {
            return false;
        }

        // Include actual form fields
        return className.contains("Field") ||
               className.contains("Grid") ||
               className.contains("SelectBox") ||
               className.contains("CheckBox") ||
               className.contains("Radio") ||
               className.contains("TextArea") ||
               className.contains("DatePicker") ||
               className.contains("TextField");
    }

    /**
     * Form definition data class
     */
    public static class FormDefinition {
        private String fileName;
        private String formId;
        private String formName;
        private String tableName;
        private Set<String> fields = new HashSet<>();

        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFormId() { return formId; }
        public void setFormId(String formId) { this.formId = formId; }

        public String getFormName() { return formName; }
        public void setFormName(String formName) { this.formName = formName; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public Set<String> getFields() { return fields; }
        public void setFields(Set<String> fields) { this.fields = fields; }

        public boolean hasField(String fieldId) {
            return fields.contains(fieldId);
        }
    }
}