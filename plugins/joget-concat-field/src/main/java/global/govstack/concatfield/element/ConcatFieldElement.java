package global.govstack.concatfield.element;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Concatenation Field Form Element
 *
 * A Joget form element that concatenates values from multiple source fields
 * into a single output value.
 *
 * Features:
 * - Configure multiple source fields to concatenate
 * - Configurable separator between values
 * - Optional format pattern for custom positioning
 * - Display as hidden, readonly, or editable field
 * - Real-time updates as source fields change
 * - Skip empty values option
 * - Prefix and suffix support
 */
public class ConcatFieldElement extends Element implements FormBuilderPaletteElement {

    private static final String CLASS_NAME = ConcatFieldElement.class.getName();

    @Override
    public String getName() {
        return "Concatenation Field";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Concatenates multiple field values into a single output with configurable separator and format";
    }

    @Override
    public String getLabel() {
        return "Concatenation Field";
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
        return 100;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fas fa-link\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class='form-cell'><label class='label'>Concatenation Field</label><div class='form-cell-value'>[Concatenated Value]</div></div>";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/ConcatFieldElement.json",
            null,
            true,
            null
        );
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "ConcatFieldElement.ftl";

        // Get properties
        String fieldId = getPropertyString("id");
        String label = getPropertyString("label");
        String displayType = getPropertyString("displayType");
        String separator = getPropertyString("separator");
        String formatPattern = getPropertyString("formatPattern");
        String prefix = getPropertyString("prefix");
        String suffix = getPropertyString("suffix");
        String skipEmpty = getPropertyString("skipEmpty");
        String updateOn = getPropertyString("updateOn");

        // Get source fields configuration
        Object sourceFieldsObj = getProperty("sourceFields");
        JSONArray sourceFieldsJson = new JSONArray();
        
        if (sourceFieldsObj != null) {
            if (sourceFieldsObj instanceof Object[]) {
                Object[] sourceFieldsArray = (Object[]) sourceFieldsObj;
                for (Object item : sourceFieldsArray) {
                    if (item instanceof Map) {
                        Map<String, String> fieldMap = (Map<String, String>) item;
                        JSONObject fieldJson = new JSONObject();
                        try {
                            fieldJson.put("fieldId", fieldMap.get("fieldId"));
                            fieldJson.put("transform", fieldMap.get("transform"));
                            sourceFieldsJson.put(fieldJson);
                        } catch (Exception e) {
                            LogUtil.error(CLASS_NAME, e, "Error building source field JSON");
                        }
                    }
                }
            }
        }

        // Apply defaults
        if (displayType == null || displayType.isEmpty()) displayType = "hidden";
        if (separator == null) separator = "_";
        if (skipEmpty == null || skipEmpty.isEmpty()) skipEmpty = "true";
        if (updateOn == null || updateOn.isEmpty()) updateOn = "change";

        // Get current value
        String value = FormUtil.getElementPropertyValue(this, formData);
        if (value == null) {
            value = "";
        }

        // Build configuration JSON for JavaScript
        JSONObject config = new JSONObject();
        try {
            config.put("fieldId", fieldId);
            config.put("displayType", displayType);
            config.put("separator", separator);
            config.put("formatPattern", formatPattern != null ? formatPattern : "");
            config.put("prefix", prefix != null ? prefix : "");
            config.put("suffix", suffix != null ? suffix : "");
            config.put("skipEmpty", "true".equals(skipEmpty));
            config.put("updateOn", updateOn);
            config.put("sourceFields", sourceFieldsJson);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error building config JSON");
        }

        // Resource base URL for static files
        String resourceBase = "/jw/web/json/plugin/" + ConcatFieldResources.class.getName() + "/service?file=";

        // Add to data model
        dataModel.put("fieldId", fieldId);
        dataModel.put("label", label != null ? label : "");
        dataModel.put("value", value);
        dataModel.put("displayType", displayType);
        dataModel.put("resourceBase", resourceBase);
        dataModel.put("elementId", "concat_" + fieldId + "_" + System.currentTimeMillis());
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
            
            // If value is empty, try to compute it server-side
            if (value == null || value.isEmpty()) {
                value = computeConcatenation(formData);
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
     * Compute concatenation server-side (fallback for cases where JS didn't run)
     */
    private String computeConcatenation(FormData formData) {
        StringBuilder result = new StringBuilder();
        
        String separator = getPropertyString("separator");
        String prefix = getPropertyString("prefix");
        String suffix = getPropertyString("suffix");
        boolean skipEmpty = "true".equals(getPropertyString("skipEmpty"));
        String formatPattern = getPropertyString("formatPattern");
        
        if (separator == null) separator = "_";
        
        Object sourceFieldsObj = getProperty("sourceFields");
        
        if (sourceFieldsObj != null && sourceFieldsObj instanceof Object[]) {
            Object[] sourceFieldsArray = (Object[]) sourceFieldsObj;
            
            // If format pattern is provided, use it
            if (formatPattern != null && !formatPattern.isEmpty()) {
                result.append(formatPattern);
                int index = 0;
                for (Object item : sourceFieldsArray) {
                    if (item instanceof Map) {
                        Map<String, String> fieldMap = (Map<String, String>) item;
                        String sourceFieldId = fieldMap.get("fieldId");
                        String transform = fieldMap.get("transform");
                        
                        if (sourceFieldId != null && !sourceFieldId.isEmpty()) {
                            String fieldValue = formData.getRequestParameter(sourceFieldId);
                            if (fieldValue == null) fieldValue = "";
                            fieldValue = applyTransform(fieldValue, transform);
                            
                            // Replace placeholder like {0}, {1}, etc.
                            String placeholder = "{" + index + "}";
                            int placeholderPos = result.indexOf(placeholder);
                            if (placeholderPos >= 0) {
                                result.replace(placeholderPos, placeholderPos + placeholder.length(), fieldValue);
                            }
                        }
                    }
                    index++;
                }
            } else {
                // Simple concatenation with separator
                boolean first = true;
                for (Object item : sourceFieldsArray) {
                    if (item instanceof Map) {
                        Map<String, String> fieldMap = (Map<String, String>) item;
                        String sourceFieldId = fieldMap.get("fieldId");
                        String transform = fieldMap.get("transform");
                        
                        if (sourceFieldId != null && !sourceFieldId.isEmpty()) {
                            String fieldValue = formData.getRequestParameter(sourceFieldId);
                            if (fieldValue == null) fieldValue = "";
                            fieldValue = applyTransform(fieldValue, transform);
                            
                            if (skipEmpty && fieldValue.isEmpty()) {
                                continue;
                            }
                            
                            if (!first) {
                                result.append(separator);
                            }
                            result.append(fieldValue);
                            first = false;
                        }
                    }
                }
            }
        }
        
        // Add prefix and suffix
        String finalResult = result.toString();
        if (!finalResult.isEmpty()) {
            if (prefix != null && !prefix.isEmpty()) {
                finalResult = prefix + finalResult;
            }
            if (suffix != null && !suffix.isEmpty()) {
                finalResult = finalResult + suffix;
            }
        }
        
        return finalResult;
    }

    /**
     * Apply transformation to field value
     */
    private String applyTransform(String value, String transform) {
        if (value == null || transform == null || transform.isEmpty()) {
            return value != null ? value : "";
        }
        
        switch (transform) {
            case "uppercase":
                return value.toUpperCase();
            case "lowercase":
                return value.toLowerCase();
            case "trim":
                return value.trim();
            case "capitalize":
                if (value.isEmpty()) return value;
                return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
            default:
                return value;
        }
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
