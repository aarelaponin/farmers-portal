package global.govstack.registration.sender.service.metadata;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Generic form data extractor that uses services.yml metadata to extract data from Joget forms
 *
 * This replaces the hardcoded FormDataExtractor with a metadata-driven approach
 */
public class GenericFormDataExtractor {

    private static final String CLASS_NAME = GenericFormDataExtractor.class.getName();

    // No longer using hardcoded UUID references
    // Data extraction now uses form_structure.yaml metadata

    private final FormDataDao formDataDao;
    private final YamlMetadataService metadataService;

    public GenericFormDataExtractor() {
        this.formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        this.metadataService = new YamlMetadataService();
    }

    public GenericFormDataExtractor(YamlMetadataService metadataService) {
        this.formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        this.metadataService = metadataService;
    }

    /**
     * Extract complete registration data from parent form table
     * This mirrors ProcessingAPI's storage pattern in reverse
     *
     * @param recordId The record ID (primary key/UUID)
     * @return Map containing all form data organized by section
     */
    public Map<String, Object> extractAllFormData(String recordId) {
        LogUtil.info(CLASS_NAME, "Extracting data for record: " + recordId);

        Map<String, Object> allData = new HashMap<>();
        allData.put("id", recordId);

        try {
            // Get parent form configuration from YAML (generic for all services)
            String parentFormId = metadataService.getParentFormId();
            LogUtil.info(CLASS_NAME, "Using parent form ID: " + parentFormId);

            // Get parent table name from YAML configuration
            // FormDataDao expects the table name without the app_fd_ prefix
            String tableName = metadataService.getParentTableName();
            LogUtil.info(CLASS_NAME, "Using parent form table: " + tableName);

            // Load data from parent table using FormDataDao
            // Load parent record using table name
            // FormDataDao.load() requires a Form object, not a string, so we use loadByTableNameAndColumnName
            FormRow parentRow = formDataDao.loadByTableNameAndColumnName(tableName, "id", recordId);
            if (parentRow != null) {
                LogUtil.info(CLASS_NAME, "Successfully loaded parent record using table name: " + tableName);
            }

            if (parentRow == null) {
                LogUtil.warn(CLASS_NAME, "No data found in parent table for record: " + recordId);
                return allData;
            }

            LogUtil.info(CLASS_NAME, "Successfully loaded parent record with " + parentRow.size() + " fields");

            // Debug: Log all fields in the parent record to understand what's available
            LogUtil.info(CLASS_NAME, "Parent record fields available: " + parentRow.keySet());
            for (Object keyObj : parentRow.keySet()) {
                String key = keyObj.toString();
                String value = parentRow.getProperty(key);
                if (value != null && !value.isEmpty()) {
                    // Only log first 100 chars to avoid huge logs
                    String logValue = value.length() > 100 ? value.substring(0, 100) + "..." : value;
                    LogUtil.info(CLASS_NAME, "  Field '" + key + "' = '" + logValue + "'");
                }
            }

            // Get all form sections from metadata
            Map<String, Object> formMappings = metadataService.getFormMappings();
            if (formMappings == null || formMappings.isEmpty()) {
                LogUtil.error(CLASS_NAME, null, "No form mappings found in metadata");
                return allData;
            }

            // Extract data for each section by following UUID references in parent row
            for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> sectionConfig = (Map<String, Object>) entry.getValue();

                if (sectionConfig == null) {
                    continue;
                }

                String type = (String) sectionConfig.get("type");

                if ("array".equals(type)) {
                    // Extract grid/array data (may be in separate tables)

                    // Determine correct parent ID for this grid
                    // Grids nested under sections with UUID references need to use that UUID, not main recordId
                    String parentIdForGrid = recordId; // default to main record ID

                    // Find parent section for this grid
                    Map<String, Object> parentSectionConfig = findParentSectionForGrid(sectionName, sectionConfig, formMappings);

                    if (parentSectionConfig != null) {
                        // Check if parent section has UUID reference field
                        String uuidRefField = (String) parentSectionConfig.get("uuidReferenceField");

                        if (uuidRefField != null) {
                            // Extract UUID from parent row (try with and without c_ prefix)
                            String uuid = parentRow.getProperty("c_" + uuidRefField);
                            if (uuid == null || uuid.trim().isEmpty()) {
                                uuid = parentRow.getProperty(uuidRefField);
                            }

                            if (uuid != null && !uuid.trim().isEmpty()) {
                                parentIdForGrid = uuid;
                                LogUtil.info(CLASS_NAME, "Using parent UUID for grid '" + sectionName +
                                    "': " + uuidRefField + " = " + uuid);
                            } else {
                                LogUtil.warn(CLASS_NAME, "Parent section has uuidReferenceField '" + uuidRefField +
                                    "' but no UUID found in parent row for grid: " + sectionName);
                            }
                        }
                    }

                    List<Map<String, Object>> gridData = extractGridData(sectionName, sectionConfig, parentIdForGrid);
                    if (!gridData.isEmpty()) {
                        allData.put(sectionName, gridData);
                    }
                } else if (Boolean.TRUE.equals(sectionConfig.get("extractFromParent"))) {
                    // NEW: Extract fields from parent row directly (for UUID reference fields)
                    LogUtil.info(CLASS_NAME, "Extracting section '" + sectionName + "' from parent row");
                    Map<String, Object> data = extractFieldsFromParentRow(sectionConfig, parentRow);
                    if (!data.isEmpty()) {
                        allData.put(sectionName, data);
                        LogUtil.info(CLASS_NAME, "Added " + data.size() + " fields from parent row to section: " + sectionName);
                    }
                } else {
                    // Extract regular form data by following UUID reference in parent row
                    Map<String, Object> sectionData = extractSectionDataDirect(sectionName, sectionConfig, parentRow);
                    if (!sectionData.isEmpty()) {
                        allData.put(sectionName, sectionData);
                    }
                }
            }

            LogUtil.info(CLASS_NAME, "Successfully extracted " + allData.size() + " sections for record: " + recordId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting form data: " + e.getMessage());
        }

        return allData;
    }

    /**
     * Extract section data by following UUID reference from parent record
     * This mirrors how ProcessingAPI stores data - each section's record UUID is stored in farms_registry
     *
     * @param sectionName Section name (e.g., "farmerBasicInfo")
     * @param sectionConfig Section configuration from YAML
     * @param parentRow The parent record from farms_registry containing UUID references
     * @return Map of extracted field data
     */
    private Map<String, Object> extractSectionDataDirect(String sectionName, Map<String, Object> sectionConfig, FormRow parentRow) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Get the UUID reference field name from YAML (e.g., "basic_data" for farmerBasicInfo)
            String uuidRefField = metadataService.getUuidReferenceField(sectionName);

            if (uuidRefField == null) {
                LogUtil.warn(CLASS_NAME, "No UUID reference field configured for section: " + sectionName);
                return data;
            }

            // Get the UUID value from parent row
            // Try both with and without c_ prefix
            String uuid = parentRow.getProperty("c_" + uuidRefField);
            if (uuid == null || uuid.trim().isEmpty()) {
                uuid = parentRow.getProperty(uuidRefField);
            }

            if (uuid == null || uuid.trim().isEmpty()) {
                LogUtil.info(CLASS_NAME, "No UUID reference found for section " + sectionName +
                    " (field: " + uuidRefField + ")");
                return data;
            }

            LogUtil.info(CLASS_NAME, "Found UUID reference for section " + sectionName + ": " +
                uuidRefField + " = " + uuid);

            // Get table name for this section
            String tableName = metadataService.getTableName(sectionName);
            if (tableName == null) {
                tableName = (String) sectionConfig.get("tableName");
            }

            if (tableName == null) {
                LogUtil.warn(CLASS_NAME, "No table name configured for section: " + sectionName);
                return data;
            }

            // Remove app_fd_ prefix if present
            if (tableName.startsWith("app_fd_")) {
                tableName = tableName.substring(7);
            }

            LogUtil.info(CLASS_NAME, "Loading sub-record from table " + tableName + " with id = " + uuid);

            // Load the sub-record using the UUID
            FormRow subRecord = formDataDao.loadByTableNameAndColumnName(tableName, "id", uuid);

            if (subRecord == null) {
                LogUtil.warn(CLASS_NAME, "Sub-record not found in table " + tableName + " with id: " + uuid);
                return data;
            }

            LogUtil.info(CLASS_NAME, "Successfully loaded sub-record, extracting fields");
            LogUtil.info(CLASS_NAME, "Sub-record fields available: " + subRecord.keySet());

            // Extract fields using merged field definitions from both YAMLs
            data = extractFieldsFromRowUsingStructure(subRecord, sectionName);

            if (!data.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Extracted " + data.size() + " fields from section: " + sectionName);
            } else {
                LogUtil.warn(CLASS_NAME, "No fields extracted from section: " + sectionName);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting section " + sectionName + ": " + e.getMessage());
        }

        return data;
    }

    /**
     * Find the parent section that contains a specific grid
     *
     * Grid sections have jogetGrid field, parent sections have a field with matching joget value
     * For example:
     *   - Grid section "householdMembers" has jogetGrid: "householdMembers"
     *   - Parent section "farmerHousehold" has field with joget: "householdMembers" and transform: "grid"
     *
     * @param gridSectionName Name of grid section (e.g., "householdMembers")
     * @param gridConfig Configuration of grid section
     * @param formMappings All form mappings from services.yml
     * @return Parent section configuration, or null if not found
     */
    private Map<String, Object> findParentSectionForGrid(
            String gridSectionName,
            Map<String, Object> gridConfig,
            Map<String, Object> formMappings) {

        try {
            // Get the jogetGrid value from grid configuration
            String jogetGridName = (String) gridConfig.get("jogetGrid");

            if (jogetGridName == null) {
                LogUtil.warn(CLASS_NAME, "Grid section " + gridSectionName + " has no jogetGrid field");
                return null;
            }

            // Search all sections for one that has a field with joget matching jogetGridName
            for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> sectionConfig = (Map<String, Object>) entry.getValue();

                if (sectionConfig == null) {
                    continue;
                }

                // Skip array sections (we're looking for parent sections, not other grids)
                String type = (String) sectionConfig.get("type");
                if ("array".equals(type)) {
                    continue;
                }

                // Check if this section has fields
                List<Map<String, Object>> fields = (List<Map<String, Object>>) sectionConfig.get("fields");
                if (fields == null) {
                    continue;
                }

                // Look for a field with joget matching jogetGridName and transform: "grid"
                for (Map<String, Object> field : fields) {
                    String fieldJoget = (String) field.get("joget");
                    String fieldTransform = (String) field.get("transform");

                    if (jogetGridName.equals(fieldJoget) && "grid".equals(fieldTransform)) {
                        LogUtil.info(CLASS_NAME, "Found parent section '" + sectionName +
                            "' for grid '" + gridSectionName + "'");
                        return sectionConfig;
                    }
                }
            }

            LogUtil.warn(CLASS_NAME, "No parent section found for grid: " + gridSectionName);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error finding parent section for grid " + gridSectionName);
        }

        return null;
    }

    /**
     * Extract fields from a FormRow using form_structure.yaml column definitions
     * This uses the exact column names from form_structure.yaml
     */
    private Map<String, Object> extractFieldsFromRowUsingStructure(FormRow row, String sectionName) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Get merged field mappings (from both form_structure.yaml and services.yml)
            List<Map<String, Object>> fields = metadataService.getMergedFieldMappings(sectionName);

            if (fields.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No field mappings found for section: " + sectionName);
                return data;
            }

            LogUtil.info(CLASS_NAME, "Processing " + fields.size() + " fields for section: " + sectionName);

            // Extract each field from the row
            for (Map<String, Object> fieldConfig : fields) {
                String fieldId = (String) fieldConfig.get("field_id");
                String column = (String) fieldConfig.get("column");

                if (fieldId == null || column == null) {
                    continue;
                }

                // Get value using Hibernate property name (field_id), not database column name
                // Joget's Hibernate mapping uses field_id as property name: <property column="c_national_id" name="national_id"/>
                String value = row.getProperty(fieldId);

                if (value != null && !value.trim().isEmpty()) {
                    // Store the extracted value
                    data.put(fieldId, value);
                    LogUtil.info(CLASS_NAME, "Extracted field " + fieldId + " = " +
                        (value.length() > 50 ? value.substring(0, 50) + "..." : value));
                } else {
                    LogUtil.debug(CLASS_NAME, "Field " + fieldId + " is empty or null");
                }
            }

            LogUtil.info(CLASS_NAME, "Successfully extracted " + data.size() + "/" + fields.size() + " fields");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting fields from row using structure: " + e.getMessage());
        }

        return data;
    }

    /**
     * Extract fields from a FormRow based on field configuration (legacy method)
     * This is a helper method that can extract from any FormRow (parent or sub-table)
     */
    private Map<String, Object> extractFieldsFromRow(FormRow row, Map<String, Object> sectionConfig) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Get field mappings for this section
            List<Map<String, Object>> fields = (List<Map<String, Object>>) sectionConfig.get("fields");
            if (fields == null) {
                LogUtil.debug(CLASS_NAME, "No fields defined for section");
                return data;
            }

            // Debug: Log all available fields in this row
            LogUtil.info(CLASS_NAME, "Sub-record fields available: " + row.keySet());

            // Extract each field from the row
            for (Map<String, Object> fieldConfig : fields) {
                String jogetField = (String) fieldConfig.get("joget");
                if (jogetField == null || jogetField.isEmpty()) {
                    continue;
                }

                // Try different field name formats
                String value = null;
                String usedFieldName = null;

                // Try with c_ prefix first
                String columnNameWithPrefix = "c_" + jogetField;
                value = row.getProperty(columnNameWithPrefix);

                if (value != null && !value.trim().isEmpty()) {
                    usedFieldName = columnNameWithPrefix;
                } else {
                    // Try without c_ prefix
                    value = row.getProperty(jogetField);
                    if (value != null && !value.trim().isEmpty()) {
                        usedFieldName = jogetField;
                    }
                }

                if (value != null && !value.trim().isEmpty()) {
                    // Store without c_ prefix for clean field names
                    data.put(jogetField, value);
                    LogUtil.info(CLASS_NAME, "Extracted field " + jogetField + " (from " + usedFieldName + ") = " +
                        (value.length() > 50 ? value.substring(0, 50) + "..." : value));
                } else {
                    LogUtil.debug(CLASS_NAME, "Field " + jogetField + " not found (tried: " + columnNameWithPrefix + ", " + jogetField + ")");
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting fields from row: " + e.getMessage());
        }

        return data;
    }

    /**
     * Extract data from a single form section (legacy method for compatibility)
     */
    private Map<String, Object> extractFormData(String sectionName, Map<String, Object> sectionConfig, String recordId) {
        Map<String, Object> data = new HashMap<>();

        try {
            String tableName = (String) sectionConfig.get("tableName");
            String primaryKey = (String) sectionConfig.get("primaryKey");

            if (tableName == null || primaryKey == null) {
                LogUtil.warn(CLASS_NAME, "Missing table configuration for section: " + sectionName);
                return data;
            }

            // Keep the original table name for now to debug
            String originalTableName = tableName;

            // Remove app_fd_ prefix if present for FormDataDao
            if (tableName.startsWith("app_fd_")) {
                tableName = tableName.substring(7);
            }

            // Query the form data
            // For Joget forms, the primary key is usually just "id"
            // The configured primaryKey in services.yml is the field name in the table
            String queryKey = "id";  // Standard Joget primary key

            // Log the query details
            LogUtil.info(CLASS_NAME, "Querying table '" + tableName + "' (original: " + originalTableName + ") with condition: " + queryKey + " = " + recordId);

            try {
                // First try with the table name without prefix
                FormRowSet rows = formDataDao.find(null, tableName, queryKey + " = ?", new Object[]{recordId}, null, null, null, null);

                if (rows != null && !rows.isEmpty()) {
                    FormRow row = rows.get(0);

                    // Extract all field values
                    for (Object key : row.keySet()) {
                        String fieldName = key.toString();
                        String value = row.getProperty(fieldName);

                        if (value != null && !value.trim().isEmpty()) {
                            // Remove c_ prefix if present for cleaner field names
                            String cleanFieldName = fieldName.startsWith("c_") ? fieldName.substring(2) : fieldName;
                            data.put(cleanFieldName, value);
                        }
                    }

                    LogUtil.info(CLASS_NAME, "Extracted " + data.size() + " fields from section: " + sectionName);
                } else {
                    LogUtil.info(CLASS_NAME, "No data found in section " + sectionName + " for record: " + recordId);
                }
            } catch (Exception ex) {
                LogUtil.error(CLASS_NAME, ex, "Error querying table '" + tableName + "'. The table name might be incorrect or the record doesn't exist.");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting data from section: " + sectionName);
        }

        return data;
    }

    /**
     * Extract grid/array data using form_structure.yaml metadata
     */
    private List<Map<String, Object>> extractGridData(String sectionName, Map<String, Object> sectionConfig, String parentId) {
        List<Map<String, Object>> gridData = new ArrayList<>();

        try {
            // Get tableName from services.yml
            // IMPORTANT: Must use tableName entity (e.g., "app_fd_household_members"), not formId entity
            // because only tableName entity has property mappings for custom fields in hbm.xml
            String tableName = (String) sectionConfig.get("tableName");

            if (tableName == null) {
                LogUtil.warn(CLASS_NAME, "Missing tableName for grid section: " + sectionName);
                return gridData;
            }

            // Remove app_fd_ prefix as Joget expects table name without prefix
            if (tableName.startsWith("app_fd_")) {
                tableName = tableName.substring(7);
            }

            // Get parent key column from form_structure.yaml
            // This reads the grid's foreign_key field and looks up the column name
            // Example: grid has foreign_key="farmer_id", looks up field to get column="c_farmer_id"
            String formId = (String) sectionConfig.get("formId");
            String parentKey = metadataService.getGridForeignKeyColumn(sectionName, formId);

            // Fallback to services.yml if not found in form_structure.yaml
            if (parentKey == null) {
                parentKey = (String) sectionConfig.get("parentKey");
                if (parentKey != null) {
                    LogUtil.warn(CLASS_NAME, "Using fallback parentKey from services.yml for grid: " + sectionName);
                }
            }

            // Try default from configuration
            if (parentKey == null) {
                try {
                    parentKey = metadataService.getDefaultGridParentColumn();
                    if (parentKey != null) {
                        LogUtil.info(CLASS_NAME, "Using default gridParentColumn from config for grid '" + sectionName + "': " + parentKey);
                    }
                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error getting default gridParentColumn");
                }
            }

            if (parentKey == null) {
                LogUtil.warn(CLASS_NAME, "No foreign key found for grid section: " + sectionName +
                    " (not in form_structure.yaml, services.yml, or config defaults)");
                return gridData;
            }

            // Build HQL condition using actual column name
            String condition = " WHERE " + parentKey + " = ?";

            LogUtil.info(CLASS_NAME, "Extracting grid data from tableName=" + tableName +
                " using HQL: " + condition + " with parentId=" + parentId);

            // Query grid data using tableName (not formId)
            // tableName entity (e.g., "household_members") has property mappings in hbm.xml
            // formId entity (e.g., "householdMemberForm") does NOT have mappings
            FormRowSet rows = formDataDao.find(
                tableName,                             // entity with property mappings (e.g., "household_members")
                null,                                  // tableName (null = use first param as entity)
                condition,                             // HQL condition: "WHERE c_farmer_id = ?"
                new Object[]{parentId},
                null, null, null, null
            );

            if (rows != null && !rows.isEmpty()) {
                // For grid sections, use formId (not sectionName) to lookup field mappings
                // Example: sectionName="householdMembers" but form_structure.yaml has fields under "householdMemberForm"
                // Note: formId was already extracted above for foreign key lookup
                String lookupKey = formId != null ? formId : sectionName;

                LogUtil.info(CLASS_NAME, "Found " + rows.size() + " rows for grid " + sectionName + ", extracting using formId: " + lookupKey);

                for (FormRow row : rows) {
                    // Extract fields using form_structure.yaml column definitions
                    Map<String, Object> rowData = extractFieldsFromRowUsingStructure(row, lookupKey);
                    if (!rowData.isEmpty()) {
                        gridData.add(rowData);
                    }
                }

                LogUtil.info(CLASS_NAME, "Extracted " + gridData.size() + " rows from grid: " + sectionName);
            } else {
                LogUtil.info(CLASS_NAME, "No grid data found in " + sectionName + " for " + parentKey + " = " + parentId);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting grid data from: " + sectionName);
        }

        return gridData;
    }

    /**
     * Extract data from a specific form by form ID
     */
    public Map<String, Object> extractFormById(String formId, String recordId) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Remove app_fd_ prefix if present
            if (formId.startsWith("app_fd_")) {
                formId = formId.substring(7);
            }

            // Query by system property "id"
            FormRowSet rows = formDataDao.find(
                formId,              // formDefId
                null,                // tableName (null = use formDefId)
                " WHERE id = ?",     // condition with WHERE prefix
                new Object[]{recordId},
                null, null, null, null
            );

            if (rows != null && !rows.isEmpty()) {
                FormRow row = rows.get(0);

                for (Object key : row.keySet()) {
                    String fieldName = key.toString();
                    String value = row.getProperty(fieldName);

                    if (value != null && !value.trim().isEmpty()) {
                        // Remove c_ prefix
                        String cleanFieldName = fieldName.startsWith("c_") ? fieldName.substring(2) : fieldName;
                        data.put(cleanFieldName, value);
                    }
                }

                LogUtil.info(CLASS_NAME, "Extracted " + data.size() + " fields from form: " + formId);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting data from form: " + formId);
        }

        return data;
    }

    /**
     * Load a single form row
     */
    public FormRow loadFormRow(String formId, String recordId) {
        try {
            // Remove app_fd_ prefix if present
            if (formId.startsWith("app_fd_")) {
                formId = formId.substring(7);
            }

            // Query by system property "id"
            FormRowSet rows = formDataDao.find(
                formId,              // formDefId
                null,                // tableName (null = use formDefId)
                " WHERE id = ?",     // condition with WHERE prefix
                new Object[]{recordId},
                null, null, null, null
            );

            if (rows != null && !rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading form row from " + formId + " with ID: " + recordId);
        }
        return null;
    }

    /**
     * Check if a record exists in a form
     */
    public boolean recordExists(String formId, String recordId) {
        try {
            // Remove app_fd_ prefix if present
            if (formId.startsWith("app_fd_")) {
                formId = formId.substring(7);
            }

            FormRowSet rows = formDataDao.find(null, formId, "id = ?", new Object[]{recordId}, null, null, 0, 1);
            return rows != null && !rows.isEmpty();
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error checking record existence in " + formId);
            return false;
        }
    }

    /**
     * Extract fields from parent row directly (for UUID reference fields)
     *
     * @param sectionConfig Section configuration with field definitions
     * @param parentRow The parent form row containing the fields
     * @return Map of field values extracted from parent row
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFieldsFromParentRow(
            Map<String, Object> sectionConfig,
            FormRow parentRow) {

        Map<String, Object> data = new HashMap<>();

        try {
            List<Map<String, Object>> fields = (List<Map<String, Object>>) sectionConfig.get("fields");

            if (fields == null || fields.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No fields defined for parent row extraction");
                return data;
            }

            LogUtil.info(CLASS_NAME, "Extracting " + fields.size() + " fields from parent row");

            for (Map<String, Object> fieldConfig : fields) {
                String jogetField = (String) fieldConfig.get("joget");

                if (jogetField == null) {
                    continue;
                }

                // FormRow properties don't have c_ prefix (Joget strips it when loading)
                String value = parentRow.getProperty(jogetField);

                if (value != null && !value.trim().isEmpty()) {
                    data.put(jogetField, value);
                    LogUtil.info(CLASS_NAME, "Extracted parent field " + jogetField + " = " + value);
                }
            }

            LogUtil.info(CLASS_NAME, "Extracted " + data.size() + " fields from parent row");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting fields from parent row: " + e.getMessage());
        }

        return data;
    }
}