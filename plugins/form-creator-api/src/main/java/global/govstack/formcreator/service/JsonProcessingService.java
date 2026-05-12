package global.govstack.formcreator.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Service for JSON processing operations including parsing, validation, and generation
 */
public class JsonProcessingService {

    private static final String CLASS_NAME = JsonProcessingService.class.getName();

    /**
     * Reg-BB engine plugin's dynamic-screen element. When this className appears
     * in a form definition, the user-visible fields are NOT in the form JSON —
     * they live in mm_field rows keyed by screenId. {@link #extractMetaScreenFields}
     * resolves them via FormDataDao so generated datalists carry the right columns.
     */
    private static final String META_SCREEN_CLASSNAME =
            "global.govstack.regbb.engine.element.MetaScreenElement";

    /**
     * Reg-BB engine's wizard element (D24). Wraps multiple mm_screen rows as
     * tabs; the wizard's fields are the union of fields across all referenced
     * screens. Walked by {@link #extractMetaWizardFields} to populate generated
     * datalist columns.
     */
    private static final String META_WIZARD_CLASSNAME =
            "global.govstack.regbb.engine.element.MetaWizardElement";

    /**
     * Validate if a string is valid JSON
     */
    public boolean isValidJson(String content) {
        try {
            // Simple JSON validation - try to parse it
            content = content.trim();
            return (content.startsWith("{") && content.endsWith("}")) ||
                   (content.startsWith("[") && content.endsWith("]"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse and validate form JSON using Joget's FormService
     */
    public Form parseAndValidateFormJson(String jsonContent, FormService formService) {
        try {
            LogUtil.info(CLASS_NAME, "Parsing form JSON content");

            // Remove any BOM or whitespace issues
            jsonContent = jsonContent.trim();
            if (jsonContent.startsWith("\uFEFF")) {
                jsonContent = jsonContent.substring(1);
            }

            // Use Joget's FormService to create a Form object from JSON
            Form form = (Form) formService.createElementFromJson(jsonContent);

            if (form != null) {
                LogUtil.info(CLASS_NAME, "JSON parsed successfully into Form object");
                LogUtil.info(CLASS_NAME, "Form properties: ID=" + form.getPropertyString("id") +
                           ", Name=" + form.getPropertyString("name") +
                           ", Table=" + form.getPropertyString("tableName"));
                return form;
            } else {
                LogUtil.info(CLASS_NAME, "ERROR: FormService.createElementFromJson returned null");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error parsing form JSON: " + e.getMessage());
        }

        return null;
    }

    /**
     * Generate API definition JSON for a form. Defaults to {@code crud}
     * apiKind for backward compatibility with the existing
     * {@code FormCreationService} caller.
     */
    public String generateApiDefinitionJson(String formId, String apiName, String apiUuid) {
        return generateApiDefinitionJson(formId, apiName, apiUuid, "crud");
    }

    /**
     * Generate API definition JSON for a form with a configurable kind.
     * Accepts the analyst-facing {@code apiKind} (CRUD vs read-only vs
     * submit-only) and emits the appropriate {@code ENABLED_PATHS} string
     * for Joget's {@code AppFormAPI} element.
     *
     * <p>Supported kinds (case-insensitive):
     * <ul>
     *   <li>{@code crud} — full CRUD: post, get, put, delete, list, saveOrUpdate,
     *       addWithFiles, updateWithFiles. The default for backward compatibility.</li>
     *   <li>{@code read_only} — list + get-by-id only. For operator-side
     *       result inspection where writes must come through other surfaces.</li>
     *   <li>{@code submit_only} — post + saveOrUpdate + addWithFiles only. For
     *       citizen-submit endpoints that must not allow direct read or
     *       modification of arbitrary records.</li>
     * </ul>
     * Unknown kinds fall back to {@code crud} with a logged warning so a
     * misspelt analyst declaration doesn't silently lose paths.
     */
    public String generateApiDefinitionJson(String formId, String apiName,
                                            String apiUuid, String apiKind) {
        try {
            String enabledPaths = enabledPathsFor(apiKind);
            // Generate unique element ID
            String elementId = UUID.randomUUID().toString().toUpperCase();

            // Build JSON structure
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"elements\": [{\n");
            json.append("        \"className\": \"org.joget.api.lib.AppFormAPI\",\n");
            json.append("        \"properties\": {\n");
            json.append("            \"formDefId\": \"").append(formId).append("\",\n");
            json.append("            \"ignorePermission\": \"\",\n");
            json.append("            \"id\": \"").append(elementId).append("\",\n");
            json.append("            \"label\": \"\",\n");
            json.append("            \"ENABLED_PATHS\": \"").append(enabledPaths).append("\"\n");
            json.append("        }\n");
            json.append("    }],\n");
            json.append("    \"properties\": {\n");
            json.append("        \"name\": \"").append(apiName).append("\",\n");
            json.append("        \"description\": \"Auto-generated API for form: ").append(formId)
                .append(" (kind=").append(apiKind == null ? "crud" : apiKind.toLowerCase()).append(")\",\n");
            json.append("        \"id\": \"API-").append(apiUuid).append("\"\n");
            json.append("    }\n");
            json.append("}\n");

            return json.toString();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating API JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate API definition JSON for a CUSTOM plugin (not AppFormAPI) —
     * caller supplies the className + ENABLED_PATHS verbatim. Used for
     * plugin-bound APIs like {@code BudgetApi} where the path semantics
     * are defined by the plugin's own {@code @Operation} annotations,
     * not the form-binding kind enum.
     *
     * <p>Path conventions follow the analyst-facing {@code mm_api}
     * pattern: id = {@code API-<code>}; properties.name = {@code apiName};
     * elements[0].properties.{className,ENABLED_PATHS} = caller-supplied.
     */
    public String generateCustomApiDefinitionJson(String apiName, String apiUuid,
                                                  String className, String enabledPaths) {
        try {
            String elementId = UUID.randomUUID().toString().toUpperCase();
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"elements\": [{\n");
            json.append("        \"className\": \"").append(className).append("\",\n");
            json.append("        \"properties\": {\n");
            json.append("            \"id\": \"").append(elementId).append("\",\n");
            json.append("            \"label\": \"\",\n");
            json.append("            \"ENABLED_PATHS\": \"").append(enabledPaths).append("\"\n");
            json.append("        }\n");
            json.append("    }],\n");
            json.append("    \"properties\": {\n");
            json.append("        \"name\": \"").append(apiName).append("\",\n");
            json.append("        \"description\": \"Custom plugin API for ").append(className).append("\",\n");
            json.append("        \"id\": \"API-").append(apiUuid).append("\"\n");
            json.append("    }\n");
            json.append("}\n");
            return json.toString();
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating custom API JSON");
            return null;
        }
    }

    /** Map analyst-facing apiKind → Joget AppFormAPI ENABLED_PATHS string.
     *  Source-verified path tokens (api-builder/apibuilder_plugins/.../AppFormAPI.java). */
    private String enabledPathsFor(String apiKind) {
        String k = (apiKind == null) ? "crud" : apiKind.trim().toLowerCase();
        switch (k) {
            case "crud":
                return "post:/;get:/{recordId};put:/;delete:/{recordId};"
                     + "post:/saveOrUpdate;post:/updateWithFiles;post:/addWithFiles;get:/list";
            case "read_only":
            case "readonly":
                return "get:/{recordId};get:/list";
            case "submit_only":
            case "submitonly":
                return "post:/;post:/saveOrUpdate;post:/addWithFiles";
            default:
                LogUtil.warn(CLASS_NAME, "Unknown apiKind '" + apiKind + "' — falling back to crud");
                return "post:/;get:/{recordId};put:/;delete:/{recordId};"
                     + "post:/saveOrUpdate;post:/updateWithFiles;post:/addWithFiles;get:/list";
        }
    }

    /**
     * Generate datalist definition JSON for a form
     */
    public String generateDatalistDefinitionJson(String formId, String datalistName, String datalistId, String formJson) {
        try {
            // Extract form fields to generate columns
            // Note: This extracts only user-defined fields (max 6), excluding system columns
            List<Map<String, String>> columns = extractFormFieldsForDatalist(formJson);

            // Build JSON structure
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"useSession\": \"false\",\n");
            json.append("    \"showPageSizeSelector\": \"true\",\n");
            json.append("    \"rowActions\": [],\n");
            json.append("    \"columns\": [\n");

            // Add columns (if empty, Joget will use default columns)
            for (int i = 0; i < columns.size(); i++) {
                Map<String, String> field = columns.get(i);
                json.append("        {\n");
                json.append("            \"name\": \"").append(field.get("name")).append("\",\n");
                json.append("            \"id\": \"column_").append(i).append("\",\n");
                json.append("            \"label\": \"").append(field.get("label")).append("\"\n");
                json.append("        }");
                if (i < columns.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("    ],\n");
            json.append("    \"pageSize\": 0,\n");
            json.append("    \"orderBy\": \"\",\n");
            json.append("    \"filters\": [],\n");
            json.append("    \"pageSizeSelectorOptions\": \"10,20,30,40,50,100\",\n");
            json.append("    \"buttonPosition\": \"bothLeft\",\n");
            json.append("    \"checkboxPosition\": \"left\",\n");
            json.append("    \"name\": \"").append(datalistName).append("\",\n");
            json.append("    \"id\": \"").append(datalistId).append("\",\n");
            json.append("    \"binder\": {\n");
            json.append("        \"className\": \"org.joget.plugin.enterprise.AdvancedFormRowDataListBinder\",\n");
            json.append("        \"properties\": {\"formDefId\": \"").append(formId).append("\"}\n");
            json.append("    },\n");
            json.append("    \"actions\": [],\n");
            json.append("    \"order\": \"\"\n");
            json.append("}\n");

            return json.toString();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating datalist JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate userview definition JSON with CRUD menu for a form
     */
    public String generateUserviewDefinitionJson(String formId, String datalistId, String userviewName, String userviewId) {
        try {
            // Generate unique IDs
            String categoryId = "category-" + UUID.randomUUID().toString();
            String menuId = UUID.randomUUID().toString();
            String welcomePageId = UUID.randomUUID().toString();
            String homeCategory = "category-" + UUID.randomUUID().toString();

            // Build JSON structure
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"className\": \"org.joget.apps.userview.model.Userview\",\n");
            json.append("    \"categories\": [\n");

            // Home category with welcome page
            json.append("        {\n");
            json.append("            \"className\": \"org.joget.apps.userview.model.UserviewCategory\",\n");
            json.append("            \"menus\": [{\n");
            json.append("                \"className\": \"org.joget.apps.userview.lib.HtmlPage\",\n");
            json.append("                \"properties\": {\n");
            json.append("                    \"id\": \"").append(welcomePageId).append("\",\n");
            json.append("                    \"label\": \"Welcome\",\n");
            json.append("                    \"customId\": \"welcome\",\n");
            json.append("                    \"content\": \"<h3>Welcome to ").append(escapeJson(userviewName)).append("</h3>\"\n");
            json.append("                }\n");
            json.append("            }],\n");
            json.append("            \"properties\": {\n");
            json.append("                \"id\": \"").append(homeCategory).append("\",\n");
            json.append("                \"label\": \"<i class='fa fa-home'></i> Home\"\n");
            json.append("            }\n");
            json.append("        },\n");

            // CRUD category
            json.append("        {\n");
            json.append("            \"className\": \"org.joget.apps.userview.model.UserviewCategory\",\n");
            json.append("            \"menus\": [{\n");
            json.append("                \"className\": \"org.joget.plugin.enterprise.CrudMenu\",\n");
            json.append("                \"properties\": {\n");
            json.append("                    \"datalistId\": \"").append(datalistId).append("\",\n");
            json.append("                    \"addFormId\": \"").append(formId).append("\",\n");
            json.append("                    \"editFormId\": \"").append(formId).append("\",\n");
            json.append("                    \"id\": \"").append(menuId).append("\",\n");
            json.append("                    \"customId\": \"").append(formId).append("_crud\",\n");
            json.append("                    \"label\": \"").append(escapeJson(userviewName)).append("\",\n");
            json.append("                    \"list-showDeleteButton\": \"yes\",\n");
            json.append("                    \"add-afterSaved\": \"list\",\n");
            json.append("                    \"edit-afterSaved\": \"list\",\n");
            json.append("                    \"buttonPosition\": \"bothLeft\",\n");
            json.append("                    \"checkboxPosition\": \"left\",\n");
            json.append("                    \"selectionType\": \"multiple\",\n");
            json.append("                    \"rowCount\": \"true\"\n");
            json.append("                }\n");
            json.append("            }],\n");
            json.append("            \"properties\": {\n");
            json.append("                \"id\": \"").append(categoryId).append("\",\n");
            json.append("                \"label\": \"<i class='fa fa-list'></i> Manage\"\n");
            json.append("            }\n");
            json.append("        }\n");
            json.append("    ],\n");
            json.append("    \"properties\": {\n");
            json.append("        \"id\": \"").append(userviewId).append("\",\n");
            json.append("        \"name\": \"").append(escapeJson(userviewName)).append("\",\n");
            json.append("        \"description\": \"Auto-generated userview for ").append(escapeJson(formId)).append("\",\n");
            json.append("        \"welcomeMessage\": \"#date.EEE, d MMM yyyy#\",\n");
            json.append("        \"logoutText\": \"Logout\",\n");
            json.append("        \"footerMessage\": \"Powered by Joget\"\n");
            json.append("    },\n");
            json.append("    \"setting\": {\n");
            json.append("        \"properties\": {\n");
            json.append("            \"userviewId\": \"").append(userviewId).append("\",\n");
            json.append("            \"userviewName\": \"").append(escapeJson(userviewName)).append("\",\n");
            json.append("            \"theme\": {\n");
            json.append("                \"className\": \"org.joget.apps.userview.lib.DefaultTheme\",\n");
            json.append("                \"properties\": {}\n");
            json.append("            },\n");
            json.append("            \"permission\": {\n");
            json.append("                \"className\": \"org.joget.apps.userview.lib.LoggedInUserPermission\",\n");
            json.append("                \"properties\": {}\n");
            json.append("            }\n");
            json.append("        }\n");
            json.append("    }\n");
            json.append("}\n");

            return json.toString();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating userview JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate JSON for a single category with CRUD menu
     */
    public String generateCategoryJson(String formId, String datalistId, String formLabel) {
        String categoryId = "category-" + UUID.randomUUID().toString();
        String menuId = UUID.randomUUID().toString();
        String customId = formId + "_crud";

        StringBuilder json = new StringBuilder();
        json.append("        {\n");
        json.append("            \"className\": \"org.joget.apps.userview.model.UserviewCategory\",\n");
        json.append("            \"menus\": [{\n");
        json.append("                \"className\": \"org.joget.plugin.enterprise.CrudMenu\",\n");
        json.append("                \"properties\": {\n");
        json.append("                    \"list-customFooter\": \"\",\n");
        json.append("                    \"add-afterSavedRedirectUrl\": \"\",\n");
        json.append("                    \"editFormId\": \"").append(formId).append("\",\n");
        json.append("                    \"cacheAllLinks\": \"\",\n");
        json.append("                    \"edit-saveButtonLabel\": \"\",\n");
        json.append("                    \"list-showDeleteButton\": \"yes\",\n");
        json.append("                    \"list-newButtonLabel\": \"\",\n");
        json.append("                    \"add-afterSavedRedirectParamName\": \"\",\n");
        json.append("                    \"list-deleteSubformData\": \"\",\n");
        json.append("                    \"enableOffline\": \"\",\n");
        json.append("                    \"selectionType\": \"multiple\",\n");
        json.append("                    \"addFormId\": \"").append(formId).append("\",\n");
        json.append("                    \"id\": \"").append(menuId).append("\",\n");
        json.append("                    \"iconIncluded\": false,\n");
        json.append("                    \"add-messageShowAfterComplete\": \"\",\n");
        json.append("                    \"edit-readonlyLabel\": \"\",\n");
        json.append("                    \"list-editLinkLabel\": \"\",\n");
        json.append("                    \"add-cancelButtonLabel\": \"\",\n");
        json.append("                    \"list-deleteFiles\": \"\",\n");
        json.append("                    \"add-customHeader\": \"\",\n");
        json.append("                    \"edit-readonly\": \"\",\n");
        json.append("                    \"datalistId\": \"").append(datalistId).append("\",\n");
        json.append("                    \"edit-nextButtonLabel\": \"\",\n");
        json.append("                    \"list-confirmation\": \"\",\n");
        json.append("                    \"userviewCacheDuration\": \"\",\n");
        json.append("                    \"add-afterSaved\": \"list\",\n");
        json.append("                    \"add-afterSavedRedirectParamvalue\": \"\",\n");
        json.append("                    \"list-customHeader\": \"\",\n");
        json.append("                    \"edit-customHeader\": \"\",\n");
        json.append("                    \"edit-afterSavedRedirectParamName\": \"\",\n");
        json.append("                    \"list-abortRelatedRunningProcesses\": \"\",\n");
        json.append("                    \"edit-afterSavedRedirectUrl\": \"\",\n");
        json.append("                    \"edit-prevButtonLabel\": \"\",\n");
        json.append("                    \"customId\": \"").append(customId).append("\",\n");
        json.append("                    \"edit-afterSaved\": \"list\",\n");
        json.append("                    \"list-deleteButtonLabel\": \"\",\n");
        json.append("                    \"checkboxPosition\": \"left\",\n");
        json.append("                    \"add-customFooter\": \"\",\n");
        json.append("                    \"list-deleteGridData\": \"\",\n");
        json.append("                    \"edit-allowRecordTraveling\": \"\",\n");
        json.append("                    \"rowCount\": \"true\",\n");
        json.append("                    \"edit-afterSavedRedirectParamvalue\": \"\",\n");
        json.append("                    \"edit-customFooter\": \"\",\n");
        json.append("                    \"keyName\": \"\",\n");
        json.append("                    \"label\": \"").append(escapeJson(formLabel)).append("\",\n");
        json.append("                    \"list-newLinkTarget\": \"\",\n");
        json.append("                    \"edit-lastButtonLabel\": \"\",\n");
        json.append("                    \"buttonPosition\": \"bothLeft\",\n");
        json.append("                    \"edit-firstButtonLabel\": \"\",\n");
        json.append("                    \"add-saveButtonLabel\": \"\",\n");
        json.append("                    \"edit-messageShowAfterComplete\": \"\",\n");
        json.append("                    \"cacheListAction\": \"\",\n");
        json.append("                    \"userviewCacheScope\": \"\",\n");
        json.append("                    \"edit-moreActions\": [],\n");
        json.append("                    \"list-moreActions\": [],\n");
        json.append("                    \"edit-backButtonLabel\": \"\",\n");
        json.append("                    \"list-editLinkTarget\": \"\"\n");
        json.append("                }\n");
        json.append("            }],\n");
        json.append("            \"properties\": {\n");
        json.append("                \"id\": \"").append(categoryId).append("\",\n");
        json.append("                \"label\": \"<i class='fa fa-tasks'></i> ").append(escapeJson(formLabel)).append("\"\n");
        json.append("            }\n");
        json.append("        }");

        return json.toString();
    }

    /**
     * Find matching closing bracket for an opening bracket
     */
    public int findMatchingBracket(String json, int openingBracketPos) {
        int depth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = openingBracketPos; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }

        return -1; // Not found
    }

    /**
     * Escape JSON special characters
     */
    public String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Extract form fields from form JSON to generate datalist columns.
     * Uses proper JSON parsing (Gson) instead of regex for robust handling of nested objects.
     */
    public List<Map<String, String>> extractFormFieldsForDatalist(String formJson) {
        List<Map<String, String>> fields = new ArrayList<>();

        try {
            // System columns to exclude from datalist (Joget internal fields)
            Set<String> systemColumns = new HashSet<>(java.util.Arrays.asList(
                "id", "dateCreated", "dateModified",
                "createdBy", "createdByName",
                "modifiedBy", "modifiedByName"
            ));

            // Maximum number of columns to show in datalist
            final int MAX_COLUMNS = 6;

            // Parse JSON using Gson (architecturally correct: JSON parser for JSON, not regex)
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(formJson);

            // Recursively traverse the JSON tree to find form fields
            extractFieldsFromElement(root, fields, systemColumns, MAX_COLUMNS);

            // Log results
            if (fields.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No user-defined fields extracted from form JSON");
                LogUtil.warn(CLASS_NAME, "Datalist will be created with no columns - Joget will use default columns");
            } else {
                LogUtil.info(CLASS_NAME, "Successfully extracted " + fields.size() + " user-defined fields for datalist columns");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting fields from form JSON: " + e.getMessage());
            LogUtil.warn(CLASS_NAME, "Datalist will be created with no columns - Joget will use default columns");
        }

        return fields;
    }

    /**
     * Recursively traverse JSON tree to find form field elements.
     */
    private void extractFieldsFromElement(JsonElement element,
                                         List<Map<String, String>> fields,
                                         Set<String> systemColumns,
                                         int maxColumns) {

        // Stop when we have enough columns
        if (fields.size() >= maxColumns) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Check if this is a form field element
            if (obj.has("className") && obj.has("properties")) {
                String className = obj.get("className").getAsString();
                JsonObject props = obj.getAsJsonObject("properties");

                // Special case: MetaScreenElement - dynamic fields live in mm_field rows,
                // not in the form JSON. Walk the meta-model to discover them.
                if (META_SCREEN_CLASSNAME.equals(className)) {
                    String screenId = (props.has("screenId") && !props.get("screenId").isJsonNull())
                            ? props.get("screenId").getAsString() : null;
                    if (screenId != null && !screenId.isEmpty()) {
                        extractMetaScreenFields(screenId, fields, systemColumns, maxColumns);
                    } else {
                        LogUtil.warn(CLASS_NAME,
                            "MetaScreenElement encountered with no screenId; skipping");
                    }
                    // MetaScreenElement is a leaf in the form tree: no nested elements to recurse into
                    return;
                }

                // Special case: MetaWizardElement (D24) — wraps multiple
                // mm_screen rows as tabs. The wizard's effective fields are
                // the union of fields across every referenced screen, in
                // resolution order. Three configuration modes (mirror of
                // MetaWizardElement.resolveScreensWithModes): roleScreenId
                // (highest precedence) → screenIds → serviceId.
                if (META_WIZARD_CLASSNAME.equals(className)) {
                    String roleScreenId = optProp(props, "roleScreenId");
                    String screenIdsCsv = optProp(props, "screenIds");
                    String serviceId    = optProp(props, "serviceId");
                    extractMetaWizardFields(roleScreenId, screenIdsCsv, serviceId,
                                            fields, systemColumns, maxColumns);
                    return;
                }

                // Is it a form field? (not Section, Column, Form, etc.)
                if (className.contains("org.joget.apps.form.lib.")) {
                    if (props.has("id")) {
                        String fieldId = props.get("id").getAsString();

                        // Filter out system columns and layout elements
                        if (fieldId != null && !fieldId.isEmpty() &&
                            !fieldId.startsWith("section") &&
                            !fieldId.startsWith("column") &&
                            !systemColumns.contains(fieldId)) {

                            String fieldLabel = props.has("label") ?
                                props.get("label").getAsString() : fieldId;

                            Map<String, String> field = new HashMap<>();
                            field.put("name", fieldId);
                            field.put("label", fieldLabel);
                            fields.add(field);

                            LogUtil.info(CLASS_NAME, "Extracted field " + fields.size() +
                                ": id=" + fieldId + ", label=" + fieldLabel);
                        }
                    }
                }
            }

            // Recursively process all properties in this object
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                extractFieldsFromElement(entry.getValue(), fields, systemColumns, maxColumns);
            }

        } else if (element.isJsonArray()) {
            // Recursively process array elements
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                extractFieldsFromElement(item, fields, systemColumns, maxColumns);
            }
        }
        // Primitives (strings, numbers, etc.) are ignored
    }

    /**
     * Resolve the dynamic columns contributed by a {@code MetaScreenElement} by
     * reading {@code mm_field} rows for the given {@code screenId} via Joget's
     * {@link FormDataDao} (no raw SQL — see CLAUDE.md HARD RULE).
     *
     * <p>Each surviving row contributes one datalist column whose
     * {@code name} = mm_field.storageKey and {@code label} = mm_field.label.
     * Rows are returned in {@code orderIndex} order so the datalist matches
     * the rendered form layout.
     *
     * <p>Failures (no DAO bean, no rows, FormDataDao exception) are logged and
     * swallowed — the worst-case outcome is a datalist with fewer columns
     * than expected, which is still a usable list (Joget falls back to
     * default columns when {@code columns} is empty).
     */
    private void extractMetaScreenFields(String screenId,
                                         List<Map<String, String>> fields,
                                         Set<String> systemColumns,
                                         int maxColumns) {
        // Standalone-MetaScreen path (not via the wizard). Compute
        // strict-flagged mode locally — same semantics, single screen scope.
        extractMetaScreenFields(screenId, fields, systemColumns, maxColumns, null);
    }

    /**
     * @param strictFlagged when non-null, callers (typically
     *                      {@link #extractMetaWizardFields}) have already
     *                      decided wizard-globally whether displayOnList
     *                      filtering is in effect. Pass {@code true} to
     *                      include only flagged rows; {@code false} to fall
     *                      back to the legacy first-N walk; {@code null} to
     *                      decide locally per the rows on this screen.
     */
    private void extractMetaScreenFields(String screenId,
                                         List<Map<String, String>> fields,
                                         Set<String> systemColumns,
                                         int maxColumns,
                                         Boolean strictFlagged) {
        try {
            Object bean = AppUtil.getApplicationContext().getBean("formDataDao");
            if (!(bean instanceof FormDataDao)) {
                LogUtil.warn(CLASS_NAME,
                    "formDataDao bean missing or wrong type; cannot resolve MetaScreen fields for screenId=" + screenId);
                return;
            }
            FormDataDao dao = (FormDataDao) bean;

            FormRowSet rows = dao.find("mm_field", "mm_field",
                "WHERE e.customProperties.screenId = ?",
                new Object[] { screenId },
                "orderIndex", false, null, null);

            if (rows == null || rows.isEmpty()) {
                LogUtil.warn(CLASS_NAME,
                    "No mm_field rows for MetaScreenElement screenId=" + screenId
                        + "; generated datalist will omit dynamic columns");
                return;
            }

            // If callers haven't decided, decide locally: any flag → strict.
            boolean strict;
            if (strictFlagged != null) {
                strict = strictFlagged.booleanValue();
            } else {
                strict = false;
                for (FormRow row : rows) {
                    if (isDisplayOnList(row)) { strict = true; break; }
                }
            }

            int added = 0;
            for (FormRow row : rows) {
                if (!strict && fields.size() >= maxColumns) {
                    break;
                }

                String storageKey = row.getProperty("storageKey");
                if (storageKey == null || storageKey.isEmpty()) {
                    continue;
                }
                if (storageKey.startsWith("section") || storageKey.startsWith("column")) {
                    continue;
                }
                if (systemColumns.contains(storageKey)) {
                    continue;
                }

                if (strict && !isDisplayOnList(row)) {
                    continue;
                }

                String label = row.getProperty("label");
                if (label == null || label.isEmpty()) {
                    label = storageKey;
                }

                Map<String, String> field = new HashMap<>();
                field.put("name", storageKey);
                field.put("label", label);
                fields.add(field);
                added++;

                LogUtil.info(CLASS_NAME, "Extracted MetaScreen field " + fields.size()
                    + ": storageKey=" + storageKey + ", label=" + label
                    + " (screenId=" + screenId + ")");
            }

            LogUtil.info(CLASS_NAME, "MetaScreenElement contributed " + added
                + " column(s) to datalist for screenId=" + screenId
                + (strict ? " [displayOnList curated]" : " [auto first-" + maxColumns + "]"));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e,
                "Failed to extract mm_field rows for MetaScreenElement screenId=" + screenId);
        }
    }

    /**
     * Read the {@code displayOnList} flag off an {@code mm_field} row,
     * tolerant of Postgres unquoted-identifier column folding. The form-field
     * id is camelCase ({@code displayOnList}) but the underlying column is
     * lowercased ({@code c_displayonlist}). Joget's FormRow may expose the
     * value under either key depending on the load path. Returns true only
     * when the value is exactly {@code "Y"} (case-insensitive); any other
     * value (including null and the explicit default {@code "N"}) returns
     * false.
     */
    private static boolean isDisplayOnList(FormRow row) {
        if (row == null) return false;
        Object v = row.get("displayOnList");
        if (v == null) v = row.get("displayonlist");
        return v != null && "Y".equalsIgnoreCase(v.toString().trim());
    }

    /**
     * Walk a {@code MetaWizardElement} configuration and resolve its
     * effective field list into datalist columns. The wizard's three
     * configuration modes mirror {@code MetaWizardElement.resolveScreensWithModes}
     * (D24, decision-log entry):
     *
     * <ol>
     *   <li><b>roleScreenId</b> set → look up the {@code mm_role_screen} row,
     *       parse its {@code sectionsJson}, walk every referenced screen.</li>
     *   <li><b>screenIds</b> set → semicolon-separated screen-code list.</li>
     *   <li><b>serviceId</b> set → all {@code mm_screen} rows for that
     *       service, in {@code orderIndex} order.</li>
     * </ol>
     *
     * For each resolved screen, look up its UUID by code and call
     * {@link #extractMetaScreenFields} so the per-screen field walker is
     * shared with the standalone-MetaScreen path.
     */
    @SuppressWarnings("unchecked")
    private void extractMetaWizardFields(String roleScreenCode,
                                         String screenIdsCsv,
                                         String serviceCode,
                                         List<Map<String, String>> fields,
                                         Set<String> systemColumns,
                                         int maxColumns) {
        try {
            Object bean = AppUtil.getApplicationContext().getBean("formDataDao");
            if (!(bean instanceof FormDataDao)) {
                LogUtil.warn(CLASS_NAME, "formDataDao bean missing; cannot resolve MetaWizard fields");
                return;
            }
            FormDataDao dao = (FormDataDao) bean;

            List<String> screenCodes = new ArrayList<>();

            if (roleScreenCode != null && !roleScreenCode.isEmpty()) {
                // Mode 1: role-screen — parse sectionsJson.
                FormRowSet rsRows = dao.find("mm_role_screen", "mm_role_screen",
                        "WHERE e.customProperties.code = ?",
                        new Object[] { roleScreenCode }, null, false, null, null);
                if (rsRows != null && !rsRows.isEmpty()) {
                    String sectionsJson = rsRows.get(0).getProperty("sectionsJson");
                    if (sectionsJson != null && !sectionsJson.isEmpty()) {
                        try {
                            org.json.JSONObject root = new org.json.JSONObject(sectionsJson);
                            org.json.JSONArray sections = root.optJSONArray("sections");
                            if (sections != null) {
                                for (int i = 0; i < sections.length(); i++) {
                                    org.json.JSONObject sec = sections.getJSONObject(i);
                                    String c = sec.optString("screen", "");
                                    if (!c.isEmpty()) screenCodes.add(c);
                                }
                            }
                        } catch (Exception parseErr) {
                            LogUtil.warn(CLASS_NAME, "Failed to parse sectionsJson for role-screen "
                                + roleScreenCode + ": " + parseErr.getMessage());
                        }
                    }
                }
            } else if (screenIdsCsv != null && !screenIdsCsv.isEmpty()) {
                // Mode 2: explicit list.
                for (String c : screenIdsCsv.split(";")) {
                    String trimmed = c.trim();
                    if (!trimmed.isEmpty()) screenCodes.add(trimmed);
                }
            } else if (serviceCode != null && !serviceCode.isEmpty()) {
                // Mode 3: derive from service. Mirror MetaWizardElement —
                // filter by audience so OP_DECISION (audience=operator) and
                // similar operator-only screens don't leak into the citizen
                // datalist's columns.
                FormRowSet srows = dao.find("mm_screen", "mm_screen",
                        "WHERE e.customProperties.serviceId = ?",
                        new Object[] { serviceCode },
                        "orderIndex", false, null, null);
                if (srows != null) {
                    for (FormRow s : srows) {
                        String c = s.getProperty("code");
                        String audience = s.getProperty("audience");
                        if (audience == null || audience.isEmpty()) audience = "citizen";
                        if (!"citizen".equalsIgnoreCase(audience) && !"both".equalsIgnoreCase(audience)) {
                            continue;  // operator-only — skip
                        }
                        if (c != null && !c.isEmpty()) screenCodes.add(c);
                    }
                }
            }

            if (screenCodes.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "MetaWizardElement: no screens resolved (roleScreenId="
                    + roleScreenCode + ", screenIds=" + screenIdsCsv + ", serviceId=" + serviceCode + ")");
                return;
            }

            // First pass: resolve all screen UUIDs and check whether ANY field
            // anywhere in the wizard is flagged with displayOnList=Y.
            // displayOnList semantics are wizard-global, not per-screen — we
            // don't want a screen with no flags to dilute the curated set with
            // its first-N fallback. If any field is flagged, every screen uses
            // strict-flagged-only mode (effectiveMaxColumns=Integer.MAX_VALUE
            // so all flagged fields make the cut). If nothing is flagged,
            // every screen falls back to legacy first-N.
            List<String> screenUuids = new ArrayList<>();
            boolean anyFlaggedAcrossWizard = false;
            for (String code : screenCodes) {
                FormRowSet srows = dao.find("mm_screen", "mm_screen",
                        "WHERE e.customProperties.code = ?",
                        new Object[] { code }, null, false, null, null);
                if (srows == null || srows.isEmpty()) {
                    LogUtil.warn(CLASS_NAME, "MetaWizardElement: mm_screen not found for code=" + code);
                    continue;
                }
                String screenUuid = srows.get(0).getId();
                screenUuids.add(screenUuid);
                if (!anyFlaggedAcrossWizard) {
                    FormRowSet frows = dao.find("mm_field", "mm_field",
                            "WHERE e.customProperties.screenId = ?",
                            new Object[] { screenUuid }, null, false, null, null);
                    if (frows != null) {
                        for (FormRow fr : frows) {
                            if (isDisplayOnList(fr)) { anyFlaggedAcrossWizard = true; break; }
                        }
                    }
                }
            }

            int effectiveMax = anyFlaggedAcrossWizard ? Integer.MAX_VALUE : maxColumns;
            LogUtil.info(CLASS_NAME, "MetaWizardElement walking " + screenUuids.size()
                    + " screens — mode=" + (anyFlaggedAcrossWizard ? "displayOnList curated" : "auto first-" + maxColumns));

            for (String screenUuid : screenUuids) {
                if (fields.size() >= effectiveMax) break;
                extractMetaScreenFields(screenUuid, fields, systemColumns, effectiveMax,
                        anyFlaggedAcrossWizard);
            }
            LogUtil.info(CLASS_NAME, "MetaWizardElement walked " + screenCodes.size()
                + " screen(s); contributed " + fields.size() + " column(s) total");
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Failed to walk MetaWizardElement");
        }
    }

    private static String optProp(JsonObject props, String key) {
        return (props.has(key) && !props.get(key).isJsonNull())
                ? props.get(key).getAsString() : "";
    }

    /**
     * Extract a JSON value for a given key
     */
    public String extractJsonValue(String jsonBlock, String key) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\":\\s*\"([^\"]*)\""
            );
            java.util.regex.Matcher matcher = pattern.matcher(jsonBlock);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
