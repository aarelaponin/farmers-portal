package org.joget.marketplace;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embedded Datalist Form Element
 *
 * A Joget form element that displays a read-only datalist inline within a form,
 * with the ability to filter based on form field values.
 *
 * Features:
 * - Display any datalist embedded within a form
 * - Filter datalist based on form field values (e.g., show farmer's parcels)
 * - Works with both Form Data Binder and JDBC Datalist Binder
 * - Pagination support
 * - AJAX-based loading with refresh on field change
 * - Customizable styling
 *
 * Use Cases:
 * - Show farmer's land parcels in farmer registration form
 * - Display order line items in order details form
 * - Show audit history for a record
 */
public class EmbeddedDatalist extends Element implements FormBuilderPaletteElement {

    private static final String CLASS_NAME = EmbeddedDatalist.class.getName();

    @Override
    public String getName() {
        return "Embedded Datalist";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Display a read-only datalist inline within a form with filtering capabilities";
    }

    @Override
    public String getLabel() {
        return "Embedded Datalist";
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
        return 300;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fas fa-table\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class='form-cell'><label class='label'>Embedded Datalist</label><div class='form-cell-value'>[Datalist placeholder]</div></div>";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/embeddedDatalist.json",
            null,
            true,
            "/messages/EmbeddedDatalist"
        );
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "embeddedDatalist.ftl";

        // Get configuration properties
        String elementId = getPropertyString("id");
        String label = getPropertyString("label");
        String datalistId = getPropertyString("datalistId");
        String height = getPropertyString("height");
        String emptyMessage = getPropertyString("emptyMessage");
        boolean showPagination = "true".equals(getPropertyString("showPagination"));
        String pageSize = getPropertyString("pageSize");
        String refreshOnChange = getPropertyString("refreshOnChange");
        String customCss = getPropertyString("customCss");
        boolean showExport = "true".equals(getPropertyString("showExport"));
        boolean showFilter = "true".equals(getPropertyString("showFilter"));
        String rowClickAction = getPropertyString("rowClickAction");
        String rowClickFormId = getPropertyString("rowClickFormId");

        // Apply defaults
        if (height == null || height.isEmpty()) height = "400px";
        if (emptyMessage == null || emptyMessage.isEmpty()) emptyMessage = "No records found.";
        if (pageSize == null || pageSize.isEmpty()) pageSize = "10";

        // Build filter parameters from configuration
        Map<String, Object> filterParamsConfig = buildFilterParamsConfig(formData);

        // Get app context
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getAppId();
        String appVersion = String.valueOf(appDef.getVersion());

        // Get the unique element key for DOM identification
        String elementUniqueKey = FormUtil.getElementUniqueKey(this, 0);

        // Add data to model
        dataModel.put("elementId", elementId);
        dataModel.put("elementUniqueKey", elementUniqueKey);
        dataModel.put("label", label);
        dataModel.put("datalistId", datalistId != null ? datalistId : "");
        dataModel.put("height", height);
        dataModel.put("emptyMessage", emptyMessage);
        dataModel.put("showPagination", showPagination);
        dataModel.put("pageSize", pageSize);
        dataModel.put("refreshOnChange", refreshOnChange != null ? refreshOnChange : "");
        dataModel.put("customCss", customCss != null ? customCss : "");
        dataModel.put("showExport", showExport);
        dataModel.put("showFilter", showFilter);
        dataModel.put("rowClickAction", rowClickAction != null ? rowClickAction : "");
        dataModel.put("rowClickFormId", rowClickFormId != null ? rowClickFormId : "");
        dataModel.put("appId", appId);
        dataModel.put("appVersion", appVersion);
        dataModel.put("contextPath", AppUtil.getRequestContextPath());

        // Build filter params JSON for JavaScript
        dataModel.put("filterParamsJson", buildFilterParamsJson(filterParamsConfig));

        // Render template
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    /**
     * Build filter parameters configuration from plugin properties and form data.
     * Returns a map with paramName -> {fieldId, defaultValue, currentValue}
     */
    private Map<String, Object> buildFilterParamsConfig(FormData formData) {
        Map<String, Object> config = new LinkedHashMap<>();

        Object[] filterParamsArray = (Object[]) getProperty("filterParams");
        if (filterParamsArray != null) {
            for (Object obj : filterParamsArray) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> row = (Map<String, String>) obj;
                    String paramName = row.get("paramName");
                    String fieldId = row.get("fieldId");
                    String defaultValue = row.get("defaultValue");

                    if (paramName != null && !paramName.trim().isEmpty()) {
                        Map<String, String> paramConfig = new LinkedHashMap<>();
                        paramConfig.put("fieldId", fieldId != null ? fieldId : "");
                        paramConfig.put("defaultValue", defaultValue != null ? defaultValue : "");

                        // Try to get current value from form data
                        String currentValue = null;
                        if (fieldId != null && !fieldId.isEmpty()) {
                            // Try request parameter first
                            currentValue = formData.getRequestParameter(fieldId);
                            
                            // If not found and it's "id", try primary key
                            if ((currentValue == null || currentValue.isEmpty()) && "id".equals(fieldId)) {
                                currentValue = formData.getPrimaryKeyValue();
                            }
                            
                            // Try loaded binder data
                            if (currentValue == null || currentValue.isEmpty()) {
                                Form rootForm = FormUtil.findRootForm(this);
                                if (rootForm != null) {
                                    currentValue = formData.getLoadBinderDataProperty(rootForm, fieldId);
                                }
                            }
                        }
                        
                        // Fall back to default value
                        if (currentValue == null || currentValue.isEmpty()) {
                            currentValue = defaultValue;
                        }
                        
                        paramConfig.put("currentValue", currentValue != null ? currentValue : "");
                        config.put(paramName.trim(), paramConfig);
                    }
                }
            }
        }

        return config;
    }

    /**
     * Convert filter params config to JSON string for JavaScript use.
     */
    private String buildFilterParamsJson(Map<String, Object> config) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, String> paramConfig = (Map<String, String>) entry.getValue();
                
                JSONObject obj = new JSONObject();
                obj.put("paramName", entry.getKey());
                obj.put("fieldId", paramConfig.get("fieldId"));
                obj.put("defaultValue", paramConfig.get("defaultValue"));
                obj.put("currentValue", paramConfig.get("currentValue"));
                jsonArray.put(obj);
            }
            return jsonArray.toString();
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error building filter params JSON");
            return "[]";
        }
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        // Read-only element, no data to store
        return null;
    }

    @Override
    public FormData formatDataForValidation(FormData formData) {
        return formData;
    }

    @Override
    public Boolean selfValidate(FormData formData) {
        // Read-only element, always valid
        return true;
    }
}
