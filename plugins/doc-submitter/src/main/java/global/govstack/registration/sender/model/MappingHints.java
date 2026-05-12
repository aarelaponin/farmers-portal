package global.govstack.registration.sender.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class for mapping-hints.yaml configuration file
 * Provides minimal hints for generating services.yml deterministically
 */
public class MappingHints {

    private ServiceInfo service;
    private Map<String, String> fieldMappings;
    private String defaultMapping;
    private NormalizationPreferences normalization;

    public MappingHints() {
        this.fieldMappings = new HashMap<>();
        this.defaultMapping = "extension.{fieldName}";
        this.normalization = new NormalizationPreferences();
    }

    /**
     * Service information section
     */
    public static class ServiceInfo {
        private String id;
        private String name;
        private String version;
        private String govstackVersion;

        public ServiceInfo() {
            this.version = "1.0";
            this.govstackVersion = "1.0";
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getGovstackVersion() { return govstackVersion; }
        public void setGovstackVersion(String govstackVersion) { this.govstackVersion = govstackVersion; }
    }

    /**
     * Normalization preferences for overriding auto-detection
     */
    public static class NormalizationPreferences {
        private List<String> forceYesNo;
        private List<String> forceOneTwo;

        public List<String> getForceYesNo() { return forceYesNo; }
        public void setForceYesNo(List<String> forceYesNo) { this.forceYesNo = forceYesNo; }

        public List<String> getForceOneTwo() { return forceOneTwo; }
        public void setForceOneTwo(List<String> forceOneTwo) { this.forceOneTwo = forceOneTwo; }
    }

    // Getters and setters
    public ServiceInfo getService() { return service; }
    public void setService(ServiceInfo service) { this.service = service; }

    public Map<String, String> getFieldMappings() { return fieldMappings; }
    public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }

    public String getDefaultMapping() { return defaultMapping; }
    public void setDefaultMapping(String defaultMapping) { this.defaultMapping = defaultMapping; }

    public NormalizationPreferences getNormalization() { return normalization; }
    public void setNormalization(NormalizationPreferences normalization) { this.normalization = normalization; }

    /**
     * Get GovStack JSON path for a Joget field
     * @param jogetFieldName The Joget field name
     * @return GovStack path or null if using default convention
     */
    public String getMapping(String jogetFieldName) {
        return fieldMappings.get(jogetFieldName);
    }

    /**
     * Apply default mapping convention
     * @param fieldName The field name
     * @return The GovStack path using default convention
     */
    public String applyDefaultMapping(String fieldName) {
        return defaultMapping.replace("{fieldName}", fieldName);
    }

    /**
     * Check if field should be forced to yesNo normalization
     */
    public boolean shouldForceYesNo(String fieldName) {
        return normalization.getForceYesNo() != null &&
               normalization.getForceYesNo().contains(fieldName);
    }

    /**
     * Check if field should be forced to oneTwo normalization
     */
    public boolean shouldForceOneTwo(String fieldName) {
        return normalization.getForceOneTwo() != null &&
               normalization.getForceOneTwo().contains(fieldName);
    }
}
