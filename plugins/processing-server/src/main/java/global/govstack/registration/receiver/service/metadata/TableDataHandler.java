package global.govstack.registration.receiver.service.metadata;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import global.govstack.registration.receiver.exception.FormSubmissionException;

import java.util.List;
import java.util.Map;

/**
 * Handler for saving sub-table/grid data with parent-child relationships
 */
public class TableDataHandler {
    private static final String CLASS_NAME = TableDataHandler.class.getName();

    private final AppService appService;
    private final FormDataDao formDataDao;
    private final String appId;
    private final String appVersion;
    private YamlMetadataService metadataService;  // Optional for configuration support

    /**
     * Constructor
     * @throws FormSubmissionException if initialization fails
     */
    public TableDataHandler() throws FormSubmissionException {
        this(null);  // Use hardcoded defaults
    }

    /**
     * Constructor with optional metadata service for configuration support
     * @param metadataService Optional metadata service for reading configuration
     * @throws FormSubmissionException if initialization fails
     */
    public TableDataHandler(YamlMetadataService metadataService) throws FormSubmissionException {
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            this.appId = appDef.getId();
            this.appVersion = appDef.getVersion().toString();
            this.appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            this.formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            this.metadataService = metadataService;

            if (metadataService != null) {
                LogUtil.info(CLASS_NAME, "TableDataHandler initialized with configuration support");
            } else {
                LogUtil.info(CLASS_NAME, "TableDataHandler using hardcoded defaults");
            }
        } catch (Exception e) {
            throw new FormSubmissionException("Failed to initialize TableDataHandler: " + e.getMessage(), e);
        }
    }

    /**
     * Save array data to sub-tables/grids
     * @param arrayDataList List of array data configurations
     * @param parentId The parent record ID
     * @throws FormSubmissionException if saving fails
     */
    public void saveArrayData(List<Map<String, Object>> arrayDataList, String parentId) throws FormSubmissionException {
        if (arrayDataList == null || arrayDataList.isEmpty()) {
            LogUtil.info(CLASS_NAME, "No array data to save");
            return;
        }

        for (Map<String, Object> arrayData : arrayDataList) {
            saveGrid(arrayData, parentId);
        }
    }

    /**
     * Delete existing grid rows for a parent ID before inserting new ones
     * This prevents duplication when updating records - uses Joget API only
     *
     * @param gridName The grid name (for configuration lookup)
     * @param formId The form ID
     * @param tableName The table name
     * @param parentFieldName The field name that references the parent (e.g., "farmer_id")
     * @param parentId The parent record ID
     */
    private void deleteExistingGridRows(String gridName, String formId, String tableName, String parentFieldName, String parentId) {
        try {
            LogUtil.info(CLASS_NAME, "========================================");
            LogUtil.info(CLASS_NAME, "DELETING EXISTING GRID ROWS");
            LogUtil.info(CLASS_NAME, "gridName: " + gridName + ", formId: " + formId + ", tableName: " + tableName);
            LogUtil.info(CLASS_NAME, "parentFieldName: " + parentFieldName + ", parentId: " + parentId);

            // Get the database column name from configuration (no hardcoded defaults)
            String parentColumnName = getParentColumnName(gridName);
            LogUtil.info(CLASS_NAME, "Using parent column: " + parentColumnName);

            // Use FormDataDao.find() to query rows matching the parent ID
            // Note: WHERE clause needs the database column name (e.g., "c_farmer_id")
            String condition = "WHERE " + parentColumnName + " = ?";
            Object[] params = {parentId};

            LogUtil.info(CLASS_NAME, "Querying with condition: " + condition);
            LogUtil.info(CLASS_NAME, "Parameter: " + parentId);

            FormRowSet rowsToDelete = formDataDao.find(formId, tableName, condition, params, null, null, null, null);

            if (rowsToDelete == null || rowsToDelete.isEmpty()) {
                LogUtil.info(CLASS_NAME, "No existing rows found in table: " + tableName + " for " + parentColumnName + " = " + parentId);
                return;
            }

            int matchCount = rowsToDelete.size();
            LogUtil.info(CLASS_NAME, "Found " + matchCount + " existing rows to delete");

            // Log details of rows to be deleted
            for (int i = 0; i < rowsToDelete.size(); i++) {
                FormRow row = rowsToDelete.get(i);
                LogUtil.info(CLASS_NAME, "  Row " + (i+1) + ": id=" + row.getId() +
                    ", " + parentColumnName + "=" + row.getProperty(parentFieldName));
            }

            // Delete the rows using FormDataDao
            LogUtil.info(CLASS_NAME, "Calling formDataDao.delete() to delete " + matchCount + " rows");
            formDataDao.delete(formId, tableName, rowsToDelete);
            LogUtil.info(CLASS_NAME, "✓ Successfully deleted " + matchCount + " existing rows");

            LogUtil.info(CLASS_NAME, "========================================");

        } catch (Exception e) {
            // Log but don't fail - we still want to insert new rows even if delete fails
            LogUtil.error(CLASS_NAME, e, "ERROR deleting existing grid rows: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save a single grid/sub-table
     * @param gridData The grid data configuration
     * @param parentId The parent record ID
     * @throws FormSubmissionException if saving fails
     */
    private void saveGrid(Map<String, Object> gridData, String parentId) throws FormSubmissionException {
        String gridName = (String) gridData.get("gridName");
        List<Map<String, String>> rows = (List<Map<String, String>>) gridData.get("rows");

        if (gridName == null || rows == null || rows.isEmpty()) {
            LogUtil.debug(CLASS_NAME, "No data to save for grid: " + gridName);
            return;
        }

        try {
            LogUtil.info(CLASS_NAME, "Processing " + rows.size() + " rows for grid: " + gridName);

            // Determine the parent field name for this grid
            String parentFieldName = getParentFieldName(gridName);
            LogUtil.info(CLASS_NAME, "Using parent field name: " + parentFieldName + " for grid: " + gridName);

            // Determine the form/table name for the grid
            String formId = getGridFormId(gridName);

            // Get table name from form
            Form form = appService.viewDataForm(
                appId,
                appVersion,
                formId,
                null,  // saveButtonLabel
                null,  // submitButtonLabel
                null,  // cancelButtonLabel
                null,  // formData
                "#",   // formUrl
                null   // cancelUrl
            );

            if (form == null) {
                throw new FormSubmissionException("Form not found: " + formId);
            }

            String tableName = form.getPropertyString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                tableName = formId;
            }

            // Delete existing rows for this parent ID (using Joget API only)
            deleteExistingGridRows(gridName, formId, tableName, parentFieldName, parentId);

            // Create new rows
            FormRowSet rowSet = new FormRowSet();
            rowSet.setMultiRow(true);

            for (Map<String, String> rowData : rows) {
                FormRow row = new FormRow();

                // Generate unique ID for this row
                String rowId = UuidGenerator.getInstance().getUuid();
                row.setId(rowId);

                // Add parent ID reference using the correct field name
                if (parentFieldName != null && !parentFieldName.isEmpty()) {
                    row.setProperty(parentFieldName, parentId);
                    LogUtil.debug(CLASS_NAME, "Set " + parentFieldName + " = " + parentId + " for row " + rowId);
                }

                // Add all field values
                for (Map.Entry<String, String> field : rowData.entrySet()) {
                    row.setProperty(field.getKey(), field.getValue());
                }

                rowSet.add(row);
            }

            try {
                // Save the FormRowSet - Joget will delete marked rows and insert new ones
                saveGridData(formId, rowSet, null);
                LogUtil.info(CLASS_NAME, "Successfully saved " + rows.size() + " rows to grid: " + gridName);
            } catch (FormSubmissionException e) {
                // If form not found, log the data instead of failing
                if (e.getMessage().contains("Form not found")) {
                    LogUtil.warn(CLASS_NAME, "Grid form not configured yet: " + formId + ". Grid data prepared but not saved.");
                    LogUtil.info(CLASS_NAME, "Grid " + gridName + " data (parent: " + parentId + "):");
                    for (int i = 0; i < rows.size() && i < 3; i++) { // Log first 3 rows as sample
                        LogUtil.info(CLASS_NAME, "  Row " + (i+1) + ": " + rows.get(i).toString());
                    }
                    if (rows.size() > 3) {
                        LogUtil.info(CLASS_NAME, "  ... and " + (rows.size() - 3) + " more rows");
                    }
                } else {
                    // Re-throw other errors
                    throw e;
                }
            }

        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error processing grid " + gridName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get the form ID for a grid name
     * @param gridName The grid name from metadata
     * @return The Joget form ID
     * @throws global.govstack.registration.receiver.exception.ConfigurationException if grid mapping not found in YAML
     */
    private String getGridFormId(String gridName) throws global.govstack.registration.receiver.exception.ConfigurationException {
        // First try to get from configuration if available
        if (metadataService != null) {
            String configFormId = metadataService.getGridFormId(gridName);
            if (configFormId != null && !configFormId.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Using configured form ID for grid " + gridName + ": " + configFormId);
                return configFormId;
            }
        }

        // No hardcoded fallbacks - configuration is mandatory for truly generic operation
        // If grid mapping is not found in YAML configuration, throw an exception
        throw new global.govstack.registration.receiver.exception.ConfigurationException(
            "Grid form mapping not found in YAML configuration for grid: " + gridName +
            ". Please add gridMappings." + gridName + ".formId to serviceConfig in your service YAML file."
        );
    }

    /**
     * Get the parent field name for a grid
     * @param gridName The grid name from metadata
     * @return The field name that stores the parent ID
     * @throws global.govstack.registration.receiver.exception.ConfigurationException if config is missing
     */
    private String getParentFieldName(String gridName) throws global.govstack.registration.receiver.exception.ConfigurationException {
        // Try grid-specific configuration first
        if (metadataService != null) {
            String configParentField = metadataService.getGridParentField(gridName);
            if (configParentField != null && !configParentField.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Using configured parent field for grid " + gridName + ": " + configParentField);
                return configParentField;
            }
        }

        // Try service-level default from configuration
        if (metadataService != null) {
            String defaultParentField = metadataService.getDefaultGridParentField();
            if (defaultParentField != null && !defaultParentField.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Using default parent field from config for grid " + gridName + ": " + defaultParentField);
                return defaultParentField;
            }
        }

        // No configuration found - fail fast with helpful error message
        throw new global.govstack.registration.receiver.exception.ConfigurationException(
            "Missing parentField configuration for grid '" + gridName + "'. " +
            "Add to services.yml:\n" +
            "  serviceConfig.gridMappings." + gridName + ".parentField: \"your_field_name\"\n" +
            "OR set a default:\n" +
            "  serviceConfig.defaults.gridParentField: \"your_default_field_name\""
        );
    }

    /**
     * Get the parent column name for a grid (database column with c_ prefix)
     * @param gridName The grid name from metadata
     * @return The database column name that stores the parent ID (e.g., "c_farmer_id")
     * @throws global.govstack.registration.receiver.exception.ConfigurationException if config is missing
     */
    private String getParentColumnName(String gridName) throws global.govstack.registration.receiver.exception.ConfigurationException {
        // Try grid-specific configuration first
        if (metadataService != null) {
            String configParentColumn = metadataService.getGridParentColumn(gridName);
            if (configParentColumn != null && !configParentColumn.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Using configured parent column for grid " + gridName + ": " + configParentColumn);
                return configParentColumn;
            }
        }

        // Try service-level default from configuration
        if (metadataService != null) {
            String defaultParentColumn = metadataService.getDefaultGridParentColumn();
            if (defaultParentColumn != null && !defaultParentColumn.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Using default parent column from config for grid " + gridName + ": " + defaultParentColumn);
                return defaultParentColumn;
            }
        }

        // No configuration found - fail fast with helpful error message
        throw new global.govstack.registration.receiver.exception.ConfigurationException(
            "Missing parentColumn configuration for grid '" + gridName + "'. " +
            "Add to services.yml:\n" +
            "  serviceConfig.gridMappings." + gridName + ".parentColumn: \"c_your_column_name\"\n" +
            "OR set a default:\n" +
            "  serviceConfig.defaults.gridParentColumn: \"c_your_default_column_name\""
        );
    }

    /**
     * Save grid data using AppService
     * @param formId The form ID
     * @param rowSet The data to save
     * @param primaryKeyValue The primary key value (null for multi-row grids)
     * @throws FormSubmissionException if saving fails
     */
    private void saveGridData(String formId, FormRowSet rowSet, String primaryKeyValue) throws FormSubmissionException {
        try {
            // Create form data
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(primaryKeyValue); // NULL for multi-row grids

            // Load the form
            Form form = appService.viewDataForm(
                appId,
                appVersion,
                formId,
                null,  // saveButtonLabel
                null,  // submitButtonLabel
                null,  // cancelButtonLabel
                formData,
                "#",   // formUrl
                null   // cancelUrl
            );

            if (form == null) {
                throw new FormSubmissionException("Form not found: " + formId);
            }

            // Store the data - using primaryKeyValue (null for grids)
            FormRowSet storedData = appService.storeFormData(form, rowSet, primaryKeyValue);

            if (storedData == null || storedData.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No data returned after storing to form: " + formId);
            } else {
                LogUtil.debug(CLASS_NAME, "Stored " + storedData.size() + " rows to form: " + formId);
            }

        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error storing grid data: " + e.getMessage(), e);
        }
    }

    /**
     * Alternative method to save grid data directly to table
     * This can be used if the grid is stored as a separate table
     * @param tableName The table name
     * @param rows The rows to save
     * @param parentId The parent record ID
     * @param parentFieldName The field name that stores the parent ID
     * @throws FormSubmissionException if saving fails
     */
    public void saveToTable(String tableName, List<Map<String, String>> rows, String parentId, String parentFieldName)
            throws FormSubmissionException {

        if (rows == null || rows.isEmpty()) {
            return;
        }

        try {
            FormRowSet rowSet = new FormRowSet();

            for (Map<String, String> rowData : rows) {
                FormRow row = new FormRow();

                // Generate unique ID
                String rowId = UuidGenerator.getInstance().getUuid();
                row.setId(rowId);

                // Add parent reference
                if (parentFieldName != null && !parentFieldName.isEmpty()) {
                    row.setProperty(parentFieldName, parentId);
                }

                // Add all fields
                for (Map.Entry<String, String> field : rowData.entrySet()) {
                    row.setProperty(field.getKey(), field.getValue());
                }

                rowSet.add(row);
            }

            // Note: Direct table storage would require a different approach
            // For now, this method would need to be implemented differently
            // based on your specific Joget setup and table structure
            LogUtil.warn(CLASS_NAME, "Direct table storage not implemented. Use saveGrid method instead.");

            // LogUtil.info(CLASS_NAME, "Saved " + rows.size() + " rows to table: " + tableName);

        } catch (Exception e) {
            throw new FormSubmissionException("Error saving to table " + tableName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convert camelCase to underscore_case
     * @param camelCase The camelCase string
     * @return underscore_case string
     */
    private String camelCaseToUnderscore(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}