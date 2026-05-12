package global.govstack.registration.receiver.service.metadata;

import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.exception.ConfigurationException;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service to load and manage YAML metadata for GovStack registration services
 * Supports multi-service configuration via serviceId-specific YAML files
 */
public class YamlMetadataService {
    private static final String CLASS_NAME = YamlMetadataService.class.getName();
    private static final String METADATA_DIR = "docs-metadata/";

    private Map<String, Object> serviceMetadata;
    private Map<String, Object> formMappings;
    private Map<String, Object> yamlData;
    private String serviceId;

    /**
     * Load the YAML metadata file for a specific service
     * @param serviceId The service ID to load metadata for (e.g., "farmers_registry", "subsidy_application")
     * @throws ConfigurationException if metadata cannot be loaded
     */
    public void loadMetadata(String serviceId) throws ConfigurationException {
        this.serviceId = serviceId;

        // Construct service-specific filename
        String metadataFile = METADATA_DIR + serviceId + ".yml";

        try {
            InputStream inputStream = null;

            // Try to load from classpath first (for deployed plugin)
            inputStream = getClass().getClassLoader().getResourceAsStream(metadataFile);
            if (inputStream != null) {
                LogUtil.info(CLASS_NAME, "Loading metadata from classpath: " + metadataFile);
            } else {
                // Try to load from file system as fallback (for development)
                Path metadataPath = Paths.get(metadataFile);
                if (Files.exists(metadataPath)) {
                    LogUtil.info(CLASS_NAME, "Loading metadata from file: " + metadataPath.toAbsolutePath());
                    inputStream = new FileInputStream(metadataPath.toFile());
                } else {
                    throw new ConfigurationException("Metadata file not found: " + metadataFile +
                        ". Expected service-specific configuration file.");
                }
            }

            Yaml yaml = new Yaml();
            this.yamlData = yaml.load(inputStream);

            // Extract service metadata
            serviceMetadata = (Map<String, Object>) yamlData.get("service");
            if (serviceMetadata == null) {
                throw new ConfigurationException("Service metadata not found in YAML");
            }

            // Validate service ID
            String configuredServiceId = (String) serviceMetadata.get("id");
            if (!serviceId.equals(configuredServiceId)) {
                throw new ConfigurationException("Service ID mismatch. Expected: " + serviceId + ", Found: " + configuredServiceId);
            }

            // Extract form mappings
            formMappings = (Map<String, Object>) yamlData.get("formMappings");
            if (formMappings == null) {
                throw new ConfigurationException("Form mappings not found in YAML");
            }

            LogUtil.info(CLASS_NAME, "Successfully loaded metadata for service: " + serviceId);

        } catch (Exception e) {
            if (e instanceof ConfigurationException) {
                throw (ConfigurationException) e;
            }
            throw new ConfigurationException("Error loading metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Get field mappings for a specific form section
     * @param sectionName The name of the form section (e.g., "farmerBasicInfo")
     * @return List of field mappings for the section
     */
    public List<Map<String, Object>> getFieldMappings(String sectionName) {
        if (formMappings == null) {
            return new ArrayList<>();
        }

        Map<String, Object> section = (Map<String, Object>) formMappings.get(sectionName);
        if (section == null) {
            return new ArrayList<>();
        }

        Object fields = section.get("fields");
        if (fields instanceof List) {
            return (List<Map<String, Object>>) fields;
        }

        return new ArrayList<>();
    }

    /**
     * Get all form sections
     * @return Map of all form sections
     */
    public Map<String, Object> getAllFormMappings() {
        return formMappings != null ? formMappings : new HashMap<>();
    }

    /**
     * Get form mappings (alias for getAllFormMappings)
     * @return Map of all form sections
     */
    public Map<String, Object> getFormMappings() {
        return getAllFormMappings();
    }

    /**
     * Get array/grid mappings (for household members, crops, etc.)
     * @return List of array field configurations
     */
    public List<Map<String, Object>> getArrayMappings() {
        List<Map<String, Object>> arrayMappings = new ArrayList<>();

        if (formMappings != null) {
            for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
                Map<String, Object> section = (Map<String, Object>) entry.getValue();
                if (section != null && "array".equals(section.get("type"))) {
                    Map<String, Object> arrayConfig = new HashMap<>();
                    arrayConfig.put("sectionName", entry.getKey());
                    arrayConfig.put("govstackPath", section.get("govstack"));
                    arrayConfig.put("jogetGrid", section.get("jogetGrid"));
                    arrayConfig.put("fields", section.get("fields"));
                    arrayMappings.add(arrayConfig);
                }
            }
        }

        return arrayMappings;
    }

    /**
     * Get value mapping for a specific field
     * @param jogetField The Joget field name
     * @return Map of value mappings, or null if not found
     */
    public Map<String, String> getValueMapping(String jogetField) {
        for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
            Map<String, Object> section = (Map<String, Object>) entry.getValue();
            List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");

            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    if (jogetField.equals(field.get("joget"))) {
                        Object valueMapping = field.get("valueMapping");
                        if (valueMapping instanceof Map) {
                            Map<String, String> result = new HashMap<>();
                            Map<?, ?> map = (Map<?, ?>) valueMapping;
                            for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                                result.put(String.valueOf(mapEntry.getKey()), String.valueOf(mapEntry.getValue()));
                            }
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get transformation type for a field
     * @param jogetField The Joget field name
     * @return The transformation type, or null if not found
     */
    public String getTransformation(String jogetField) {
        for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
            Map<String, Object> section = (Map<String, Object>) entry.getValue();
            List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");

            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    if (jogetField.equals(field.get("joget"))) {
                        return (String) field.get("transform");
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check if the metadata is loaded
     * @return true if metadata is loaded
     */
    public boolean isLoaded() {
        return serviceMetadata != null && formMappings != null;
    }

    /**
     * Get the configured service ID
     * @return The service ID
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * Get the metadata version from service configuration
     * @return The metadata version, or "unknown" if not specified
     */
    public String getMetadataVersion() {
        if (serviceMetadata != null && serviceMetadata.containsKey("metadataVersion")) {
            return (String) serviceMetadata.get("metadataVersion");
        }
        return "unknown";
    }

    /**
     * Get the metadata compatibility range from service configuration
     * @return The metadata compatibility range, or null if not specified
     */
    public String getMetadataCompatibility() {
        if (serviceMetadata != null && serviceMetadata.containsKey("metadataCompatibility")) {
            return (String) serviceMetadata.get("metadataCompatibility");
        }
        return null;
    }

    /**
     * Get the form ID from service metadata
     * @return The form ID, or the service ID if not specified
     */
    public String getFormId() {
        if (serviceMetadata != null && serviceMetadata.containsKey("formId")) {
            return (String) serviceMetadata.get("formId");
        }
        // Fall back to service ID if formId not specified
        return serviceId;
    }

    /**
     * Get the parent form ID from service configuration
     * Configuration is mandatory - no default value for truly generic operation
     * @return The parent form ID from YAML configuration
     * @throws global.govstack.registration.receiver.exception.ConfigurationException if parentFormId not configured
     */
    public String getParentFormId() throws global.govstack.registration.receiver.exception.ConfigurationException {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig != null && serviceConfig.containsKey("parentFormId")) {
            return (String) serviceConfig.get("parentFormId");
        }
        // No default - require explicit configuration for each service
        throw new global.govstack.registration.receiver.exception.ConfigurationException(
            "parentFormId must be specified in service configuration for: " + serviceId +
            ". Please add serviceConfig.parentFormId to your service YAML file."
        );
    }

    /**
     * Get the service configuration section
     * @return The serviceConfig map or null if not present
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getServiceConfig() {
        if (serviceMetadata != null && serviceMetadata.containsKey("serviceConfig")) {
            return (Map<String, Object>) serviceMetadata.get("serviceConfig");
        }
        return null;
    }

    /**
     * Get the section to form mapping from service configuration
     * @return Map of section names to form IDs, or null if not configured
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getSectionToFormMap() {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig != null && serviceConfig.containsKey("sectionToFormMap")) {
            return (Map<String, String>) serviceConfig.get("sectionToFormMap");
        }
        return null;
    }

    /**
     * Get the parent reference fields from service configuration
     * These fields link the parent form to child forms (sections)
     * @return List of parent reference field names, or null if not configured
     */
    @SuppressWarnings("unchecked")
    public List<String> getParentReferenceFields() {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig != null && serviceConfig.containsKey("parentReferenceFields")) {
            return (List<String>) serviceConfig.get("parentReferenceFields");
        }
        return null;
    }

    /**
     * Get grid configuration for a specific grid
     * @param gridName The name of the grid
     * @return Map containing grid configuration (formId, parentField) or null
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getGridConfig(String gridName) {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig != null && serviceConfig.containsKey("gridMappings")) {
            Map<String, Object> gridMappings = (Map<String, Object>) serviceConfig.get("gridMappings");
            if (gridMappings != null && gridMappings.containsKey(gridName)) {
                return (Map<String, String>) gridMappings.get(gridName);
            }
        }
        return null;
    }

    /**
     * Get the form ID for a specific grid
     * @param gridName The name of the grid
     * @return The form ID for the grid, or null if not configured
     */
    public String getGridFormId(String gridName) {
        Map<String, String> gridConfig = getGridConfig(gridName);
        if (gridConfig != null && gridConfig.containsKey("formId")) {
            return gridConfig.get("formId");
        }
        return null;
    }

    /**
     * Get the parent field name for a specific grid
     * @param gridName The name of the grid
     * @return The parent field name, or null if not configured
     */
    public String getGridParentField(String gridName) {
        Map<String, String> gridConfig = getGridConfig(gridName);
        if (gridConfig != null && gridConfig.containsKey("parentField")) {
            return gridConfig.get("parentField");
        }
        return null;
    }

    /**
     * Get the parent column name for a specific grid
     * This is the database column name (e.g., "c_farmer_id")
     * @param gridName The name of the grid
     * @return The parent column name for the grid, or null if not configured
     */
    public String getGridParentColumn(String gridName) {
        Map<String, String> gridConfig = getGridConfig(gridName);
        if (gridConfig != null && gridConfig.containsKey("parentColumn")) {
            return gridConfig.get("parentColumn");
        }
        return null;
    }

    /**
     * Get the list of master data fields from metadata configuration
     * Master data fields should NOT be normalized/transformed - they contain codes
     * synchronized from lookup tables and must pass through unchanged
     *
     * @return Set of master data field names, or empty set if not configured
     */
    @SuppressWarnings("unchecked")
    public Set<String> getMasterDataFields() {
        if (yamlData == null) {
            LogUtil.warn(CLASS_NAME, "YAML data not loaded, returning empty masterDataFields set");
            return Collections.emptySet();
        }

        Map<String, Object> metadata = (Map<String, Object>) yamlData.get("metadata");
        if (metadata == null) {
            LogUtil.info(CLASS_NAME, "No metadata section in configuration, returning empty masterDataFields set");
            return Collections.emptySet();
        }

        List<String> fields = (List<String>) metadata.get("masterDataFields");
        if (fields == null || fields.isEmpty()) {
            LogUtil.info(CLASS_NAME, "No masterDataFields configured, returning empty set");
            return Collections.emptySet();
        }

        LogUtil.info(CLASS_NAME, "Loaded " + fields.size() + " master data fields from configuration");
        return new HashSet<>(fields);
    }

    /**
     * Get field normalization configuration from metadata
     * Defines which fields should be normalized to specific LOV formats (yes/no, 1/2, etc.)
     * @return Map of normalization type to list of field names
     */
    public Map<String, List<String>> getFieldNormalizationConfig() {
        if (yamlData == null) {
            LogUtil.warn(CLASS_NAME, "YAML data not loaded, returning empty normalization config");
            return Collections.emptyMap();
        }

        Map<String, Object> metadata = (Map<String, Object>) yamlData.get("metadata");
        if (metadata == null) {
            LogUtil.info(CLASS_NAME, "No metadata section, returning empty normalization config");
            return Collections.emptyMap();
        }

        Map<String, Object> normConfig = (Map<String, Object>) metadata.get("fieldNormalization");
        if (normConfig == null) {
            LogUtil.info(CLASS_NAME, "No fieldNormalization configured, returning empty map");
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : normConfig.entrySet()) {
            List<String> fields = (List<String>) entry.getValue();
            if (fields != null) {
                result.put(entry.getKey(), fields);
            }
        }

        LogUtil.info(CLASS_NAME, "Loaded normalization config for " + result.size() + " normalization types");
        return result;
    }

    /**
     * Get the default grid parent field from service configuration defaults
     * This is the field name used as foreign key in grid tables (e.g., "farmer_id", "student_id")
     *
     * @return Default parent field name, or null if not configured
     */
    @SuppressWarnings("unchecked")
    public String getDefaultGridParentField() {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig == null) {
            LogUtil.warn(CLASS_NAME, "No serviceConfig found, cannot get default gridParentField");
            return null;
        }

        Map<String, Object> defaults = (Map<String, Object>) serviceConfig.get("defaults");
        if (defaults == null) {
            LogUtil.info(CLASS_NAME, "No defaults section in serviceConfig");
            return null;
        }

        String defaultField = (String) defaults.get("gridParentField");
        if (defaultField != null) {
            LogUtil.debug(CLASS_NAME, "Found default gridParentField: " + defaultField);
        }
        return defaultField;
    }

    /**
     * Get the default grid parent column from service configuration defaults
     * This is the Joget database column name with c_ prefix (e.g., "c_farmer_id")
     *
     * @return Default parent column name, or null if not configured
     */
    @SuppressWarnings("unchecked")
    public String getDefaultGridParentColumn() {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig == null) {
            LogUtil.warn(CLASS_NAME, "No serviceConfig found, cannot get default gridParentColumn");
            return null;
        }

        Map<String, Object> defaults = (Map<String, Object>) serviceConfig.get("defaults");
        if (defaults == null) {
            LogUtil.info(CLASS_NAME, "No defaults section in serviceConfig");
            return null;
        }

        String defaultColumn = (String) defaults.get("gridParentColumn");
        if (defaultColumn != null) {
            LogUtil.debug(CLASS_NAME, "Found default gridParentColumn: " + defaultColumn);
        }
        return defaultColumn;
    }
}