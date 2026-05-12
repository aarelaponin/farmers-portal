package global.govstack.lookupfield.element;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

import java.util.Map;

/**
 * Lookup Field Form Element
 *
 * A Joget form element that watches a source SelectBox field, fetches a record
 * from another form by the selected primary key, and displays a specified column
 * value from that record.
 *
 * Features:
 * - Configure a source SelectBox to watch
 * - Specify target form and column to look up
 * - Display as hidden, readonly, or editable field
 * - Client-side AJAX lookup with caching for performance
 * - Server-side fallback via FormDataDao on form submission
 * - Multiple LookupFields watching the same source share a single AJAX cache
 */
public class LookupFieldElement extends Element implements FormBuilderPaletteElement {

    private static final String CLASS_NAME = LookupFieldElement.class.getName();

    @Override
    public String getName() {
        return "Lookup Field";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Watches a SelectBox and auto-populates from a related form record";
    }

    @Override
    public String getLabel() {
        return "Lookup Field";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getFormBuilderCategory() {
        return "GovStack";
    }

    @Override
    public int getFormBuilderPosition() {
        return 110;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fas fa-search\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class='form-cell'><label class='label'>Lookup Field</label>"
             + "<div class='form-cell-value'>[Looked-up Value]</div></div>";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/LookupFieldElement.json",
            null,
            true,
            null
        );
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "LookupFieldElement.ftl";

        // Get properties
        String fieldId = getPropertyString("id");
        String label = getPropertyString("label");
        String displayType = getPropertyString("displayType");
        String sourceFieldId = getPropertyString("sourceFieldId");
        String lookupFormId = getPropertyString("lookupFormId");
        String lookupColumn = getPropertyString("lookupColumn");
        String lookupKeyColumn = getPropertyString("lookupKeyColumn");
        String updateOn = getPropertyString("updateOn");

        // Apply defaults
        if (displayType == null || displayType.isEmpty()) displayType = "readonly";
        if (updateOn == null || updateOn.isEmpty()) updateOn = "change";
        if (lookupKeyColumn == null) lookupKeyColumn = "";

        // Get current app ID for REST API calls
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef != null ? appDef.getAppId() : "";

        // Get current value (may be pre-loaded from DB for existing records)
        String value = FormUtil.getElementPropertyValue(this, formData);
        if (value == null) {
            value = "";
        }

        // Build configuration JSON for JavaScript
        JSONObject config = new JSONObject();
        try {
            config.put("fieldId", fieldId);
            config.put("displayType", displayType);
            config.put("sourceFieldId", sourceFieldId);
            config.put("lookupFormId", lookupFormId);
            config.put("lookupColumn", lookupColumn);
            config.put("lookupKeyColumn", lookupKeyColumn);
            config.put("appId", appId);
            config.put("updateOn", updateOn);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error building config JSON");
        }

        // Resource base URL for static files
        String resourceBase = "/jw/web/json/plugin/"
            + LookupFieldResources.class.getName() + "/service?file=";

        // Add to data model
        dataModel.put("fieldId", fieldId);
        dataModel.put("label", label != null ? label : "");
        dataModel.put("value", value);
        dataModel.put("displayType", displayType);
        dataModel.put("resourceBase", resourceBase);
        dataModel.put("elementId", "lookup_" + fieldId + "_" + System.currentTimeMillis());
        dataModel.put("config", config.toString());

        // Render
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        FormRowSet rowSet = null;

        String fieldId = getPropertyString(FormUtil.PROPERTY_ID);
        if (fieldId != null) {
            String value = FormUtil.getElementPropertyValue(this, formData);

            // If value is empty, try to resolve it server-side via FormDataDao
            if (value == null || value.isEmpty()) {
                value = lookupValue(formData);
            }

            if (value != null) {
                FormRow result = new FormRow();
                result.setProperty(fieldId, value);
                rowSet = new FormRowSet();
                rowSet.add(result);
            }
        }

        return rowSet;
    }

    /**
     * Server-side lookup: fetch value from the target form using FormDataDao.
     * This is the fallback when JavaScript didn't populate the field.
     */
    private String lookupValue(FormData formData) {
        String sourceFieldId = getPropertyString("sourceFieldId");
        String lookupFormId = getPropertyString("lookupFormId");
        String lookupColumn = getPropertyString("lookupColumn");
        String lookupKeyColumn = getPropertyString("lookupKeyColumn");

        if (sourceFieldId == null || lookupFormId == null || lookupColumn == null) {
            return "";
        }

        // Get the selected value from the source field
        String sourceValue = formData.getRequestParameter(sourceFieldId);
        if (sourceValue == null || sourceValue.isEmpty()) {
            return "";
        }

        try {
            // Resolve table name from form definition (Joget native way)
            String tableName = resolveTableName(lookupFormId);

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            FormRow row = null;

            if (lookupKeyColumn != null && !lookupKeyColumn.isEmpty()) {
                // Query by column value (when SelectBox value != Joget primary key)
                String condition = "WHERE c_" + lookupKeyColumn + " = ?";
                Object[] params = { sourceValue };
                FormRowSet rows = formDataDao.find(lookupFormId, tableName,
                    condition, params, null, null, 0, 1);
                if (rows != null && !rows.isEmpty()) {
                    row = rows.get(0);
                }
            } else {
                // Direct load by primary key
                row = formDataDao.load(lookupFormId, tableName, sourceValue);
            }

            if (row != null) {
                String result = row.getProperty(lookupColumn);
                return result != null ? result : "";
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e,
                "Error looking up value from form=" + lookupFormId
                + " column=" + lookupColumn
                + " keyColumn=" + lookupKeyColumn
                + " key=" + sourceValue);
        }

        return "";
    }

    /**
     * Resolve DB table name from form definition using AppService.
     */
    private String resolveTableName(String formId) {
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef != null) {
                AppService appService = (AppService) AppUtil.getApplicationContext()
                    .getBean("appService");
                String tableName = appService.getFormTableName(appDef, formId);
                if (tableName != null && !tableName.isEmpty()) {
                    return tableName;
                }
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME,
                "Could not resolve table for form '" + formId + "': " + e.getMessage());
        }
        return formId;
    }

    @Override
    public FormData formatDataForValidation(FormData formData) {
        return formData;
    }

    @Override
    public Boolean selfValidate(FormData formData) {
        String fieldId = FormUtil.getElementParameterName(this);
        String value = formData.getRequestParameter(fieldId);

        // Check if required
        String required = getPropertyString("required");
        if ("true".equals(required) && (value == null || value.isEmpty())) {
            String errorMsg = getPropertyString("requiredMessage");
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = "This field is required";
            }
            formData.addFormError(fieldId, errorMsg);
            return false;
        }

        return true;
    }
}
