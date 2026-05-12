package global.govstack.smartsearch.element;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

import java.util.Map;

/**
 * Smart Farmer Search Form Element
 *
 * A Joget form element that provides intelligent farmer lookup with:
 * - Single text box with pattern auto-detection (ID/Phone → instant result)
 * - Progressive criteria builder with confidence indicator
 * - Statistical estimation (no real-time DB counts)
 * - Fuzzy matching (Levenshtein + Soundex)
 * - Recent farmers quick-access (localStorage)
 *
 * Phase 3: Basic UI Implementation
 */
public class SmartSearchElement extends Element implements FormBuilderPaletteElement {

    private static final String CLASS_NAME = SmartSearchElement.class.getName();
    private static final String VERSION = "8.1-SNAPSHOT-phase3";

    @Override
    public String getName() {
        return "Smart Farmer Search";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Progressive farmer search with confidence estimation and fuzzy matching";
    }

    @Override
    public String getLabel() {
        return "Smart Farmer Search";
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
        return "<i class=\"fa fa-search\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class='form-cell'><label class='label'>Smart Farmer Search</label>" +
               "<div class='form-cell-value'>" +
               "<div style='padding:10px;border:1px dashed #ccc;border-radius:4px;background:#f9f9f9;'>" +
               "<i class='fa fa-search'></i> Farmer Search (configure in properties)" +
               "</div></div></div>";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/SmartSearchElement.json",
            null,
            true,
            null
        );
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "SmartSearchElement.ftl";

        // Get properties with defaults
        String fieldId = getPropertyString("id");
        if (fieldId == null || fieldId.isEmpty()) {
            fieldId = "farmerId";
        }

        // Which value to store (nationalId, id, or phone)
        String storeValue = getPropertyString("storeValue");
        if (storeValue == null || storeValue.isEmpty()) {
            storeValue = "nationalId"; // Default to nationalId for interoperability
        }

        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = "Select Farmer";
        }

        String displayMode = getPropertyString("displayMode");
        if (displayMode == null || displayMode.isEmpty()) {
            displayMode = "popup";
        }

        // API configuration
        String apiEndpoint = getPropertyString("apiEndpoint");
        if (apiEndpoint == null || apiEndpoint.isEmpty()) {
            apiEndpoint = "/jw/api/fss";
        }

        String apiId = getPropertyString("apiId");
        if (apiId == null) {
            apiId = "";
        }

        String apiKey = getPropertyString("apiKey");
        if (apiKey == null) {
            apiKey = "";
        }

        // Display columns configuration
        String displayColumns = getPropertyString("displayColumns");
        if (displayColumns == null || displayColumns.isEmpty()) {
            displayColumns = "nationalId,firstName,lastName,district,village";
        }

        // Input detection patterns
        String nationalIdPattern = getPropertyString("nationalIdPattern");
        if (nationalIdPattern == null || nationalIdPattern.isEmpty()) {
            nationalIdPattern = "^\\d{9,13}$";
        }

        String nationalIdMinLengthStr = getPropertyString("nationalIdMinLength");
        int nationalIdMinLength = 4;
        try {
            nationalIdMinLength = Integer.parseInt(nationalIdMinLengthStr);
        } catch (Exception e) {
            // Use default
        }

        String phonePattern = getPropertyString("phonePattern");
        if (phonePattern == null || phonePattern.isEmpty()) {
            phonePattern = "^\\+?\\d{8,}$";
        }

        String phoneMinLengthStr = getPropertyString("phoneMinLength");
        int phoneMinLength = 8;
        try {
            phoneMinLength = Integer.parseInt(phoneMinLengthStr);
        } catch (Exception e) {
            // Use default
        }

        // Auto-select configuration
        // Default to true if property hasn't been configured yet (null)
        // If explicitly unchecked (empty string after save), use false
        String autoSelectSingleResultStr = getPropertyString("autoSelectSingleResult");
        boolean autoSelectSingleResult;
        if (autoSelectSingleResultStr == null) {
            autoSelectSingleResult = true; // Default for new/unconfigured elements
        } else {
            autoSelectSingleResult = "true".equalsIgnoreCase(autoSelectSingleResultStr);
        }

        String autoSelectMinScoreStr = getPropertyString("autoSelectMinScore");
        int autoSelectMinScore = 90;
        try {
            autoSelectMinScore = Integer.parseInt(autoSelectMinScoreStr);
        } catch (Exception e) {
            // Use default
        }

        // Same logic for notification - default to true if not configured
        String showAutoSelectNotificationStr = getPropertyString("showAutoSelectNotification");
        boolean showAutoSelectNotification;
        if (showAutoSelectNotificationStr == null) {
            showAutoSelectNotification = true;
        } else {
            showAutoSelectNotification = "true".equalsIgnoreCase(showAutoSelectNotificationStr);
        }

        // Recent farmers config (Issue #1, #2)
        // Default to true to maintain backward compatibility (panel was always shown before)
        String showRecentFarmersStr = getPropertyString("showRecentFarmers");
        boolean showRecentFarmers;
        if (showRecentFarmersStr == null) {
            showRecentFarmers = true; // Default for new/unconfigured elements (backward compat)
        } else {
            showRecentFarmers = "true".equalsIgnoreCase(showRecentFarmersStr);
        }

        String maxRecentFarmersStr = getPropertyString("maxRecentFarmers");
        int maxRecentFarmers = 5; // Match JSON default
        try {
            if (maxRecentFarmersStr != null && !maxRecentFarmersStr.isEmpty()) {
                maxRecentFarmers = Integer.parseInt(maxRecentFarmersStr);
                // Clamp to reasonable range
                if (maxRecentFarmers < 1) maxRecentFarmers = 1;
                if (maxRecentFarmers > 20) maxRecentFarmers = 20;
            }
        } catch (NumberFormatException e) {
            // Use default
        }

        // Get current value (farmer ID)
        String value = FormUtil.getElementPropertyValue(this, formData);
        if (value == null) {
            value = "";
        }

        // Base URL for static resources (PluginWebSupport)
        String resourceBase = "/jw/web/json/plugin/" + SmartSearchResources.class.getName() + "/service?file=";

        // Generate unique element ID to avoid collisions
        // Use fieldId + timestamp for uniqueness
        String elementId = "fss_" + fieldId + "_" + System.currentTimeMillis();

        // Add to data model - these become FreeMarker variables
        dataModel.put("fieldId", fieldId);
        dataModel.put("storeValue", storeValue);
        dataModel.put("value", value);
        dataModel.put("displayMode", displayMode);
        dataModel.put("resourceBase", resourceBase);
        dataModel.put("apiEndpoint", apiEndpoint);
        dataModel.put("apiId", apiId);
        dataModel.put("apiKey", apiKey);
        dataModel.put("elementId", elementId);
        dataModel.put("displayColumns", displayColumns);

        // Input detection patterns
        dataModel.put("nationalIdPattern", nationalIdPattern);
        dataModel.put("nationalIdMinLength", nationalIdMinLength);
        dataModel.put("phonePattern", phonePattern);
        dataModel.put("phoneMinLength", phoneMinLength);

        // Auto-select configuration
        dataModel.put("autoSelectSingleResult", autoSelectSingleResult);
        dataModel.put("autoSelectMinScore", autoSelectMinScore);
        dataModel.put("showAutoSelectNotification", showAutoSelectNotification);

        // Recent farmers configuration (Issue #1, #2)
        dataModel.put("showRecentFarmers", showRecentFarmers);
        dataModel.put("maxRecentFarmers", maxRecentFarmers);

        LogUtil.debug(CLASS_NAME, "Rendering SmartSearchElement: fieldId=" + fieldId + 
                      ", displayMode=" + displayMode + ", apiEndpoint=" + apiEndpoint);

        // Render template
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
        // Validate that a farmer is selected if field is required
        String required = getPropertyString("required");
        if ("true".equalsIgnoreCase(required)) {
            String value = FormUtil.getElementPropertyValue(this, formData);
            if (value == null || value.trim().isEmpty()) {
                formData.addFormError(getPropertyString("id"), "Please select a farmer");
                return false;
            }
        }
        return true;
    }
}
