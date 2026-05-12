package global.govstack.registration.sender.service.metadata;

import org.joget.commons.util.LogUtil;
import global.govstack.registration.sender.exception.ConfigurationException;
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
    private static final String FORM_STRUCTURE_FILE = "docs-metadata/form_structure.yaml";

    private Map<String, Object> serviceMetadata;
    private Map<String, Object> formMappings;
    private Map<String, Object> formStructureData;
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
            Map<String, Object> yamlData = yaml.load(inputStream);

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

            // Load form_structure.yaml (required for merged field mappings)
            try {
                Yaml formStructureYaml = new Yaml();
                InputStream formStructureStream = loadYamlFile(FORM_STRUCTURE_FILE);
                formStructureData = formStructureYaml.load(formStructureStream);
                LogUtil.info(CLASS_NAME, "Successfully loaded form_structure.yaml");
            } catch (Exception e) {
                LogUtil.warn(CLASS_NAME, "Could not load form_structure.yaml: " + e.getMessage());
                formStructureData = new HashMap<>();
            }

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
     * @return The parent form ID
     * @throws ConfigurationException if not configured
     */
    public String getParentFormId() throws ConfigurationException {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig != null && serviceConfig.containsKey("parentFormId")) {
            return (String) serviceConfig.get("parentFormId");
        }
        // No default - require explicit configuration for each service
        throw new ConfigurationException("parentFormId must be specified in service configuration for: " + serviceId);
    }

    /**
     * Get the parent table name from service configuration
     * @return The parent table name (without app_fd_ prefix)
     * @throws ConfigurationException if not configured
     */
    public String getParentTableName() throws ConfigurationException {
        Map<String, Object> serviceConfig = getServiceConfig();
        if (serviceConfig != null && serviceConfig.containsKey("parentTableName")) {
            return (String) serviceConfig.get("parentTableName");
        }
        // No default - require explicit configuration for each service
        throw new ConfigurationException("parentTableName must be specified in service configuration for: " + serviceId);
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
     * Get the UUID reference field name for a section
     * This is the field in farms_registry that holds the UUID pointing to the section's record
     *
     * @param sectionName The section name (e.g., "farmerBasicInfo")
     * @return The UUID reference field name (e.g., "basic_data"), or null if not configured
     */
    @SuppressWarnings("unchecked")
    public String getUuidReferenceField(String sectionName) {
        Map<String, Object> formMappings = getFormMappings();
        if (formMappings != null && formMappings.containsKey(sectionName)) {
            Map<String, Object> sectionConfig = (Map<String, Object>) formMappings.get(sectionName);
            if (sectionConfig != null && sectionConfig.containsKey("uuidReferenceField")) {
                return (String) sectionConfig.get("uuidReferenceField");
            }
        }
        return null;
    }

    /**
     * Default constructor - metadata must be loaded explicitly via loadMetadata(serviceId)
     */
    public YamlMetadataService() {
        // No auto-loading in multi-service architecture
        // Services will be loaded explicitly via loadMetadata(serviceId)
        LogUtil.info(CLASS_NAME, "YamlMetadataService initialized. Call loadMetadata(serviceId) to load configuration.");
    }

    /**
     * Load metadata without validating service ID (DEPRECATED - for backward compatibility only)
     * @deprecated Use loadMetadata(serviceId) instead for multi-service support
     */
    @Deprecated
    private void loadMetadataWithoutValidation() throws ConfigurationException {
        try {
            // Attempt to load default services.yml for backward compatibility
            String legacyFile = METADATA_DIR + "services.yml";
            InputStream inputStream = loadYamlFile(legacyFile);
            Yaml yaml = new Yaml();
            Map<String, Object> yamlData = yaml.load(inputStream);

            // Extract service metadata
            serviceMetadata = (Map<String, Object>) yamlData.get("service");
            if (serviceMetadata != null) {
                this.serviceId = (String) serviceMetadata.get("id");
            }

            // Extract form mappings
            formMappings = (Map<String, Object>) yamlData.get("formMappings");

            LogUtil.info(CLASS_NAME, "Successfully loaded legacy services.yml metadata");

            // Load form_structure.yaml
            try {
                InputStream formStructureStream = loadYamlFile(FORM_STRUCTURE_FILE);
                formStructureData = yaml.load(formStructureStream);
                LogUtil.info(CLASS_NAME, "Successfully loaded form_structure.yaml");
            } catch (Exception e) {
                LogUtil.warn(CLASS_NAME, "Could not load form_structure.yaml: " + e.getMessage());
                formStructureData = new HashMap<>();
            }

        } catch (Exception e) {
            if (e instanceof ConfigurationException) {
                throw (ConfigurationException) e;
            }
            throw new ConfigurationException("Error loading metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to load YAML file from various locations
     */
    private InputStream loadYamlFile(String fileName) throws ConfigurationException {
        InputStream inputStream = null;

        // Try to load from classpath first
        inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (inputStream != null) {
            LogUtil.info(CLASS_NAME, "Loading from classpath: " + fileName);
            return inputStream;
        }

        // Try to load from file system
        Path filePath = Paths.get(fileName);
        if (Files.exists(filePath)) {
            LogUtil.info(CLASS_NAME, "Loading from file: " + filePath.toAbsolutePath());
            try {
                return new FileInputStream(filePath.toFile());
            } catch (Exception e) {
                throw new ConfigurationException("Error reading file: " + fileName, e);
            }
        }

        // Try src/main/resources path
        filePath = Paths.get("src/main/resources/" + fileName);
        if (Files.exists(filePath)) {
            LogUtil.info(CLASS_NAME, "Loading from resources: " + filePath.toAbsolutePath());
            try {
                return new FileInputStream(filePath.toFile());
            } catch (Exception e) {
                throw new ConfigurationException("Error reading file: " + fileName, e);
            }
        }

        throw new ConfigurationException("File not found: " + fileName);
    }

    /**
     * Get all form mappings
     * @return Map of all form mappings
     */
    public Map<String, Object> getFormMappings() {
        return formMappings != null ? formMappings : new HashMap<>();
    }

    /**
     * Get service metadata
     * @return Map of service metadata
     */
    public Map<String, Object> getServiceMetadata() {
        return serviceMetadata != null ? serviceMetadata : new HashMap<>();
    }

    /**
     * Get form structure data (from form_structure.yaml)
     * @return Map of form structure data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFormStructureData() {
        if (formStructureData == null) {
            return new HashMap<>();
        }
        return (Map<String, Object>) formStructureData.getOrDefault("forms", new HashMap<>());
    }

    /**
     * Get form structure for a specific form
     * @param formName The form name (e.g., "farmerBasicInfo")
     * @return Map containing form structure or null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFormStructure(String formName) {
        Map<String, Object> forms = getFormStructureData();
        return (Map<String, Object>) forms.get(formName);
    }

    /**
     * Get table name for a form from form_structure.yaml
     * @param formName The form name
     * @return Table name or null
     */
    public String getTableName(String formName) {
        Map<String, Object> formStructure = getFormStructure(formName);
        return formStructure != null ? (String) formStructure.get("table_name") : null;
    }

    /**
     * Get foreign key column for a form from services.yml or form_structure.yaml
     * @param sectionName The section/form name
     * @return Foreign key column name (e.g., "c_farmer_id") or default
     * @throws global.govstack.registration.sender.exception.ConfigurationException if config is missing
     */
    public String getForeignKeyColumn(String sectionName) throws global.govstack.registration.sender.exception.ConfigurationException {
        // First try services.yml formMappings (handles both formName and sectionName)
        Map<String, Object> formMappings = getFormMappings();
        Map<String, Object> sectionConfig = (Map<String, Object>) formMappings.get(sectionName);
        if (sectionConfig != null) {
            // Try parentKey first (services.yml convention)
            String parentKey = (String) sectionConfig.get("parentKey");
            if (parentKey != null) {
                return parentKey;
            }

            // Try parentField with c_ prefix
            String parentField = (String) sectionConfig.get("parentField");
            if (parentField != null && !parentField.startsWith("c_")) {
                return "c_" + parentField;
            }
            if (parentField != null) {
                return parentField;
            }
        }

        // Fallback to form_structure.yaml
        Map<String, Object> formStructure = getFormStructure(sectionName);
        if (formStructure != null) {
            String parentKey = (String) formStructure.get("parent_key");
            if (parentKey != null) {
                return parentKey;
            }

            String parentField = (String) formStructure.get("parentField");
            if (parentField != null && !parentField.startsWith("c_")) {
                return "c_" + parentField;
            }
            if (parentField != null) {
                return parentField;
            }
        }

        // Try default from configuration
        String defaultColumn = getDefaultGridParentColumn();
        if (defaultColumn != null && !defaultColumn.isEmpty()) {
            LogUtil.info(CLASS_NAME, "Using default parent column from config for section '" + sectionName + "': " + defaultColumn);
            return defaultColumn;
        }

        // No configuration found - fail fast with helpful error message
        throw new global.govstack.registration.sender.exception.ConfigurationException(
            "Missing foreign key column configuration for section '" + sectionName + "'. " +
            "Add to services.yml:\n" +
            "  serviceConfig.defaults.gridParentColumn: \"c_your_field_name\""
        );
    }

    /**
     * Get all fields for a form from form_structure.yaml
     * @param formName The form name
     * @return List of field maps or empty list
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getFormStructureFields(String formName) {
        Map<String, Object> formStructure = getFormStructure(formName);
        if (formStructure == null) {
            return new ArrayList<>();
        }

        // Try all_fields first
        Object allFields = formStructure.get("all_fields");
        if (allFields instanceof List) {
            return (List<Map<String, Object>>) allFields;
        }

        // Otherwise, extract from sections
        List<Map<String, Object>> fields = new ArrayList<>();
        Object sections = formStructure.get("sections");
        if (sections instanceof List) {
            for (Object sectionObj : (List<?>) sections) {
                if (sectionObj instanceof Map) {
                    Map<String, Object> section = (Map<String, Object>) sectionObj;
                    Object sectionFields = section.get("fields");
                    if (sectionFields instanceof List) {
                        fields.addAll((List<Map<String, Object>>) sectionFields);
                    }
                }
            }
        }

        return fields;
    }

    /**
     * Merge form structure fields with services.yml field mappings
     * Returns enhanced field definitions with both table/column AND govstack path
     *
     * @param formName The form name
     * @return List of merged field definitions
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMergedFieldMappings(String formName) {
        List<Map<String, Object>> mergedFields = new ArrayList<>();

        // Get fields from form_structure.yaml (has table/column info)
        List<Map<String, Object>> structureFields = getFormStructureFields(formName);

        // Get fields from services.yml (has govstack path info)
        List<Map<String, Object>> serviceFields = getFieldMappings(formName);

        // Create map of service fields by joget field name for quick lookup
        Map<String, Map<String, Object>> serviceFieldMap = new HashMap<>();
        for (Map<String, Object> serviceField : serviceFields) {
            String jogetName = (String) serviceField.get("joget");
            if (jogetName != null) {
                serviceFieldMap.put(jogetName, serviceField);
            }
        }

        // Merge fields from form_structure with services.yml
        for (Map<String, Object> structureField : structureFields) {
            Map<String, Object> mergedField = new HashMap<>(structureField);

            String fieldId = (String) structureField.get("field_id");
            if (fieldId != null && serviceFieldMap.containsKey(fieldId)) {
                // Add govstack mapping from services.yml
                Map<String, Object> serviceField = serviceFieldMap.get(fieldId);
                mergedField.put("govstack", serviceField.get("govstack"));
                mergedField.put("transform", serviceField.get("transform"));
                mergedField.put("valueMapping", serviceField.get("valueMapping"));
                mergedField.put("govstackType", serviceField.get("govstackType"));
                mergedField.put("typeValue", serviceField.get("typeValue"));
            }

            mergedFields.add(mergedField);
        }

        return mergedFields;
    }

    /**
     * Get grid foreign key column name from form_structure.yaml
     *
     * Reads the grid definition to find:
     * 1. foreign_key field (e.g., "farmer_id")
     * 2. sub_form_id (e.g., "householdMemberForm")
     * 3. Looks up that field in the sub-form to get column name (e.g., "c_farmer_id")
     *
     * @param gridName Name of the grid (e.g., "householdMembers")
     * @param formId The sub-form ID for the grid (e.g., "householdMemberForm")
     * @return Column name for the foreign key (e.g., "c_farmer_id") or null
     */
    @SuppressWarnings("unchecked")
    public String getGridForeignKeyColumn(String gridName, String formId) {
        try {
            // First, find the grid definition in form_structure.yaml
            // Grids are nested under parent forms, so we need to search through all forms
            Map<String, Object> forms = getFormStructureData();

            String foreignKeyFieldId = null;
            String subFormId = null;

            // Search all forms for the grid definition
            for (Map.Entry<String, Object> formEntry : forms.entrySet()) {
                Map<String, Object> formDef = (Map<String, Object>) formEntry.getValue();
                if (formDef == null) continue;

                // Check if this form has grids
                List<Map<String, Object>> grids = (List<Map<String, Object>>) formDef.get("grids");
                if (grids == null) continue;

                // Look for our grid
                for (Map<String, Object> grid : grids) {
                    String gridId = (String) grid.get("grid_id");
                    if (gridName.equals(gridId)) {
                        foreignKeyFieldId = (String) grid.get("foreign_key");
                        subFormId = (String) grid.get("sub_form_id");
                        LogUtil.info(CLASS_NAME, "Found grid '" + gridName + "': foreign_key=" +
                            foreignKeyFieldId + ", sub_form_id=" + subFormId);
                        break;
                    }
                }

                if (foreignKeyFieldId != null) break;
            }

            if (foreignKeyFieldId == null) {
                LogUtil.warn(CLASS_NAME, "Grid '" + gridName + "' not found in form_structure.yaml");
                return null;
            }

            // Now look up the foreign key field in the sub-form to get column name
            Map<String, Object> subForm = getFormStructure(subFormId != null ? subFormId : formId);
            if (subForm == null) {
                LogUtil.warn(CLASS_NAME, "Sub-form '" + (subFormId != null ? subFormId : formId) +
                    "' not found in form_structure.yaml");
                return null;
            }

            // Get fields from the sub-form
            List<Map<String, Object>> fields = getFormStructureFields(subFormId != null ? subFormId : formId);

            for (Map<String, Object> field : fields) {
                String fieldId = (String) field.get("field_id");
                if (foreignKeyFieldId.equals(fieldId)) {
                    String column = (String) field.get("column");
                    LogUtil.info(CLASS_NAME, "Grid '" + gridName + "' foreign key: field_id='" +
                        fieldId + "' -> column='" + column + "'");
                    return column;
                }
            }

            LogUtil.warn(CLASS_NAME, "Foreign key field '" + foreignKeyFieldId +
                "' not found in sub-form '" + (subFormId != null ? subFormId : formId) + "'");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting grid foreign key for: " + gridName);
        }

        return null;
    }

    /**
     * Get the default grid parent field from service configuration defaults
     * This is the field name used as foreign key in grid tables (e.g., "farmer_id", "student_id")
     *
     * @return Default parent field name, or null if not configured
     */
    @SuppressWarnings("unchecked")
    public String getDefaultGridParentField() {
        if (serviceMetadata == null) {
            LogUtil.warn(CLASS_NAME, "Service metadata not loaded, cannot get default gridParentField");
            return null;
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) serviceMetadata.get("serviceConfig");
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
        if (serviceMetadata == null) {
            LogUtil.warn(CLASS_NAME, "Service metadata not loaded, cannot get default gridParentColumn");
            return null;
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) serviceMetadata.get("serviceConfig");
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