package global.govstack.ruleeditor.element;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Rule Editor Form Element
 *
 * A Joget form element that provides a CodeMirror-based editor for
 * writing Rules Script definitions.
 *
 * Features:
 * - Syntax highlighting for Rules Script keywords
 * - Real-time validation via API
 * - Field dictionary panel with autocomplete
 * - Save/load ruleset integration
 */
public class RuleEditorElement extends Element implements FormBuilderPaletteElement {

    private static final String CLASS_NAME = RuleEditorElement.class.getName();

    @Override
    public String getName() {
        return "Rule Editor";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "CodeMirror-based editor for writing Rules Script definitions";
    }

    @Override
    public String getLabel() {
        return "Rule Editor";
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
        return "<i class=\"fa fa-code\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class='form-cell'><label class='label'>Rule Editor</label><div class='form-cell-value'>[Editor placeholder]</div></div>";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/RuleEditorElement.json",
            null,
            true,
            null
        );
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "RuleEditorElement.ftl";

        // Get properties
        String fieldId = getPropertyString("id");
        String scopeCode = getPropertyString("scopeCode");
        String height = getPropertyString("height");
        String showDictionary = getPropertyString("showDictionary");
        String showSaveButton = getPropertyString("showSaveButton");

        // API credentials
        String apiId = getPropertyString("apiId");
        String apiKey = getPropertyString("apiKey");

        // Ruleset binding properties
        String rulesetCodeField = getPropertyString("rulesetCodeField");
        String rulesetNameField = getPropertyString("rulesetNameField");

        // Filter properties
        String filterCategories = getPropertyString("filterCategories");
        String filterFieldTypes = getPropertyString("filterFieldTypes");
        String filterIsGrid = getPropertyString("filterIsGrid");
        String filterLookupFormId = getPropertyString("filterLookupFormId");

        // Defaults
        if (scopeCode == null || scopeCode.isEmpty()) {
            scopeCode = "FARMER_ELIGIBILITY";
        }
        if (height == null || height.isEmpty()) {
            height = "auto";
        }
        if (showDictionary == null || showDictionary.isEmpty()) {
            showDictionary = "true";
        }
        if (showSaveButton == null || showSaveButton.isEmpty()) {
            showSaveButton = "false";
        }
        if (apiId == null) {
            apiId = "";
        }
        if (apiKey == null) {
            apiKey = "";
        }

        // Build filter config JSON
        String filterConfigJson = buildFilterConfigJson(
            filterCategories, filterFieldTypes, filterIsGrid, filterLookupFormId);

        // Get current value
        String value = FormUtil.getElementPropertyValue(this, formData);
        if (value == null) {
            value = "";
        }

        // Base URL for static resources (PluginWebSupport)
        String resourceBase = "/jw/web/json/plugin/" + RuleEditorResources.class.getName() + "/service?file=";

        // API base URL (configurable, defaults to /jw/api/jre/jre)
        String apiBase = getPropertyString("apiEndpoint");
        if (apiBase == null || apiBase.isEmpty()) {
            apiBase = "/jw/api/jre/jre";
        }

        // Add to data model
        dataModel.put("fieldId", fieldId);
        dataModel.put("value", value);
        dataModel.put("scopeCode", scopeCode);
        dataModel.put("height", height);
        dataModel.put("showDictionary", "true".equals(showDictionary));
        dataModel.put("showSaveButton", "true".equals(showSaveButton));
        dataModel.put("resourceBase", resourceBase);
        dataModel.put("apiBase", apiBase);
        dataModel.put("apiId", apiId);
        dataModel.put("apiKey", apiKey);
        dataModel.put("elementId", "jre_" + fieldId + "_" + System.currentTimeMillis());
        dataModel.put("filterConfig", filterConfigJson);
        dataModel.put("rulesetCodeFieldId", rulesetCodeField != null ? rulesetCodeField : "");
        dataModel.put("rulesetNameFieldId", rulesetNameField != null ? rulesetNameField : "");

        // Render
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    @Override
    public FormData formatDataForValidation(FormData formData) {
        // No special validation formatting needed
        return formData;
    }

    @Override
    public Boolean selfValidate(FormData formData) {
        // Optional: validate Rules Script syntax
        String fieldId = FormUtil.getElementParameterName(this);
        String value = formData.getRequestParameter(fieldId);

        if (value != null && !value.isEmpty()) {
            // For now, accept all non-empty values
            // Could add server-side validation here
        }

        return true;
    }

    /**
     * Builds a JSON string containing filter configuration for the frontend.
     *
     * @param categories comma-separated category codes
     * @param fieldTypes comma-separated field type codes
     * @param isGrid "Y", "N", or empty for all
     * @param lookupFormId specific lookup form ID or empty for all
     * @return JSON string with filter configuration
     */
    private String buildFilterConfigJson(String categories, String fieldTypes,
                                         String isGrid, String lookupFormId) {
        try {
            JSONObject filterConfig = new JSONObject();

            // Convert comma-separated categories to JSON array
            JSONArray categoriesArray = new JSONArray();
            if (categories != null && !categories.isEmpty()) {
                for (String cat : categories.split(",")) {
                    String trimmed = cat.trim();
                    if (!trimmed.isEmpty()) {
                        categoriesArray.put(trimmed);
                    }
                }
            }
            filterConfig.put("categories", categoriesArray);

            // Convert comma-separated field types to JSON array
            JSONArray fieldTypesArray = new JSONArray();
            if (fieldTypes != null && !fieldTypes.isEmpty()) {
                for (String ft : fieldTypes.split(",")) {
                    String trimmed = ft.trim();
                    if (!trimmed.isEmpty()) {
                        fieldTypesArray.put(trimmed);
                    }
                }
            }
            filterConfig.put("fieldTypes", fieldTypesArray);

            // isGrid filter (Y/N or empty)
            filterConfig.put("isGrid", isGrid != null ? isGrid : "");

            // lookupFormId filter
            filterConfig.put("lookupFormId", lookupFormId != null ? lookupFormId : "");

            return filterConfig.toString();
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error building filter config JSON");
            return "{}";
        }
    }
}
