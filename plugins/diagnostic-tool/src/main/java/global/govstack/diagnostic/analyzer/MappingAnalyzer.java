package global.govstack.diagnostic.analyzer;

import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Analyzes services.yml field mappings
 */
public class MappingAnalyzer {

    private final Yaml yaml = new Yaml();

    public ServiceMappings analyzeMappings(File servicesFile) throws Exception {
        if (!servicesFile.exists()) {
            throw new IllegalArgumentException("Services file not found: " + servicesFile.getPath());
        }

        ServiceMappings mappings = new ServiceMappings();

        try (FileInputStream input = new FileInputStream(servicesFile)) {
            Map<String, Object> data = yaml.load(input);

            // Extract service info
            Map<String, Object> service = (Map<String, Object>) data.get("service");
            if (service != null) {
                mappings.setServiceId((String) service.get("id"));
                mappings.setServiceName((String) service.get("name"));
            }

            // Extract form mappings
            Map<String, Object> formMappings = (Map<String, Object>) data.get("formMappings");
            if (formMappings != null) {
                for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
                    String sectionName = entry.getKey();
                    Map<String, Object> section = (Map<String, Object>) entry.getValue();

                    SectionMapping sectionMapping = new SectionMapping();
                    sectionMapping.setSectionName(sectionName);
                    sectionMapping.setType((String) section.get("type"));

                    // Extract fields
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
                    if (fields != null) {
                        for (Map<String, Object> field : fields) {
                            FieldMapping fieldMapping = new FieldMapping();
                            fieldMapping.setJogetField((String) field.get("joget"));
                            fieldMapping.setGovstackPath((String) field.get("govstack"));
                            fieldMapping.setTransform((String) field.get("transform"));
                            fieldMapping.setRequired(Boolean.TRUE.equals(field.get("required")));
                            fieldMapping.setValueMapping((Map<String, String>) field.get("valueMapping"));

                            sectionMapping.addField(fieldMapping);
                        }
                    }

                    mappings.addSection(sectionMapping);
                }
            }
        }

        return mappings;
    }

    /**
     * Service mappings data class
     */
    public static class ServiceMappings {
        private String serviceId;
        private String serviceName;
        private Map<String, SectionMapping> sections = new HashMap<>();

        public void addSection(SectionMapping section) {
            sections.put(section.getSectionName(), section);
        }

        public int getTotalFieldCount() {
            return sections.values().stream()
                .mapToInt(s -> s.getFields().size())
                .sum();
        }

        public Set<String> getAllJogetFields() {
            Set<String> allFields = new HashSet<>();
            for (SectionMapping section : sections.values()) {
                for (FieldMapping field : section.getFields()) {
                    allFields.add(field.getJogetField());
                }
            }
            return allFields;
        }

        public Set<String> getAllGovstackPaths() {
            Set<String> allPaths = new HashSet<>();
            for (SectionMapping section : sections.values()) {
                for (FieldMapping field : section.getFields()) {
                    allPaths.add(field.getGovstackPath());
                }
            }
            return allPaths;
        }

        public FieldMapping findFieldByJogetName(String jogetField) {
            for (SectionMapping section : sections.values()) {
                for (FieldMapping field : section.getFields()) {
                    if (jogetField.equals(field.getJogetField())) {
                        return field;
                    }
                }
            }
            return null;
        }

        // Getters and setters
        public String getServiceId() { return serviceId; }
        public void setServiceId(String serviceId) { this.serviceId = serviceId; }

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public Map<String, SectionMapping> getSections() { return sections; }
    }

    /**
     * Section mapping data class
     */
    public static class SectionMapping {
        private String sectionName;
        private String type; // regular or array
        private List<FieldMapping> fields = new ArrayList<>();

        public void addField(FieldMapping field) {
            fields.add(field);
        }

        // Getters and setters
        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<FieldMapping> getFields() { return fields; }

        public boolean isArray() {
            return "array".equals(type);
        }
    }

    /**
     * Field mapping data class
     */
    public static class FieldMapping {
        private String jogetField;
        private String govstackPath;
        private String transform;
        private boolean required;
        private Map<String, String> valueMapping;

        // Getters and setters
        public String getJogetField() { return jogetField; }
        public void setJogetField(String jogetField) { this.jogetField = jogetField; }

        public String getGovstackPath() { return govstackPath; }
        public void setGovstackPath(String govstackPath) { this.govstackPath = govstackPath; }

        public String getTransform() { return transform; }
        public void setTransform(String transform) { this.transform = transform; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public Map<String, String> getValueMapping() { return valueMapping; }
        public void setValueMapping(Map<String, String> valueMapping) { this.valueMapping = valueMapping; }
    }
}