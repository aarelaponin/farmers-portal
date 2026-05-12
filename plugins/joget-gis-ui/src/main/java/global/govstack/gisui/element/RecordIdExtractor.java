package global.govstack.gisui.element;

import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts record ID from various sources in Joget forms.
 *
 * Uses a 4-strategy fallback chain to reliably get the record ID even in
 * complex scenarios like multi-tab forms, AJAX loads, and malformed URLs.
 *
 * Strategy order:
 * 1. formData.getPrimaryKeyValue() - Works for direct form load
 * 2. FormRowSet from root form's load binder - Most reliable for multi-tab/sub-forms
 * 3. URL parameter 'id' with UUID extraction - Fallback for malformed URLs
 * 4. URL parameter 'recordId' - Alternative parameter name
 *
 * Usage:
 * <pre>
 * String recordId = RecordIdExtractor.extract(formData, element);
 * </pre>
 */
public class RecordIdExtractor {

    private static final String CLASS_NAME = RecordIdExtractor.class.getName();

    // UUID pattern for extracting clean UUIDs from potentially malformed parameters
    // Allows 8-16 hex chars in last segment to handle both standard (12) and non-standard UUIDs
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{8,16})"
    );

    /**
     * Extract record ID using 4-strategy fallback chain.
     *
     * @param formData The form data object
     * @param element The current element (used to find root form)
     * @return The record ID, or empty string if not found
     */
    public static String extract(FormData formData, Element element) {
        if (formData == null) {
            LogUtil.debug(CLASS_NAME, "FormData is null, returning empty recordId");
            return "";
        }

        String recordId = "";

        // Strategy 1: Try primary key value (works for direct form load)
        recordId = tryPrimaryKeyValue(formData);
        if (isValidRecordId(recordId)) {
            LogUtil.debug(CLASS_NAME, "RecordId from primaryKey: " + recordId);
            return recordId;
        }

        // Strategy 2: Try loaded binder data via root form (most reliable for multi-tab/sub-forms)
        recordId = tryFormRowSet(formData, element);
        if (isValidRecordId(recordId)) {
            LogUtil.debug(CLASS_NAME, "RecordId from FormRowSet: " + recordId);
            return recordId;
        }

        // Strategy 3: Try request parameter 'id' with UUID extraction
        recordId = tryUrlParameter(formData, "id");
        if (isValidRecordId(recordId)) {
            LogUtil.debug(CLASS_NAME, "RecordId from URL param 'id': " + recordId);
            return recordId;
        }

        // Strategy 4: Try 'recordId' request parameter
        recordId = tryUrlParameter(formData, "recordId");
        if (isValidRecordId(recordId)) {
            LogUtil.debug(CLASS_NAME, "RecordId from URL param 'recordId': " + recordId);
            return recordId;
        }

        LogUtil.debug(CLASS_NAME, "No valid recordId found, returning empty string");
        return "";
    }

    /**
     * Strategy 1: Get record ID from FormData primary key.
     */
    private static String tryPrimaryKeyValue(FormData formData) {
        String primaryKey = formData.getPrimaryKeyValue();
        LogUtil.debug(CLASS_NAME, "tryPrimaryKeyValue: raw value = " + primaryKey);

        if (isValidRecordId(primaryKey)) {
            return primaryKey;
        }
        return "";
    }

    /**
     * Strategy 2: Get record ID from FormRowSet loaded by root form.
     * This is the most reliable method for multi-tab forms and sub-forms.
     */
    private static String tryFormRowSet(FormData formData, Element element) {
        Form rootForm = FormUtil.findRootForm(element);
        if (rootForm == null) {
            LogUtil.debug(CLASS_NAME, "tryFormRowSet: rootForm is null");
            return "";
        }

        // First try getLoadBinderData which returns the actual loaded row data
        FormRowSet rowSet = formData.getLoadBinderData(rootForm);
        LogUtil.debug(CLASS_NAME, "tryFormRowSet: rowSet = " + (rowSet != null ? "size=" + rowSet.size() : "null"));

        if (rowSet != null && !rowSet.isEmpty()) {
            FormRow row = rowSet.get(0);
            if (row != null) {
                // FormRow.getId() returns the actual primary key value
                String rowId = row.getId();
                LogUtil.debug(CLASS_NAME, "tryFormRowSet: FormRow.getId() = " + rowId);
                if (isValidRecordId(rowId)) {
                    return rowId;
                }
            }
        }

        // Fallback: try getLoadBinderDataProperty
        String binderId = formData.getLoadBinderDataProperty(rootForm, "id");
        LogUtil.debug(CLASS_NAME, "tryFormRowSet: getLoadBinderDataProperty('id') = " + binderId);
        if (isValidRecordId(binderId)) {
            return binderId;
        }

        return "";
    }

    /**
     * Strategy 3/4: Get record ID from URL parameter with UUID extraction.
     * Handles malformed URLs like "uuid_mode=edit" instead of "uuid&_mode=edit".
     */
    private static String tryUrlParameter(FormData formData, String paramName) {
        String paramValue = formData.getRequestParameter(paramName);
        LogUtil.debug(CLASS_NAME, "tryUrlParameter('" + paramName + "'): raw value = " + paramValue);

        if (paramValue == null || paramValue.isEmpty()) {
            return "";
        }

        // Extract UUID from potentially malformed parameter
        String extracted = extractUUID(paramValue);
        if (isValidRecordId(extracted)) {
            return extracted;
        }

        return "";
    }

    /**
     * Extract a valid UUID from a potentially malformed input string.
     *
     * @param input The input string that may contain a UUID
     * @return The extracted UUID, or the original input if no UUID pattern found
     */
    public static String extractUUID(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        Matcher matcher = UUID_PATTERN.matcher(input);
        if (matcher.find()) {
            String extracted = matcher.group(1);
            LogUtil.debug(CLASS_NAME, "extractUUID: '" + input + "' -> '" + extracted + "'");
            return extracted;
        }

        LogUtil.debug(CLASS_NAME, "extractUUID: '" + input + "' -> no UUID match, returning original");
        return input; // Return original if no UUID found (might be numeric ID)
    }

    /**
     * Check if a record ID is valid.
     * A valid ID is non-null, non-empty, and doesn't contain URL artifacts like "_mode".
     *
     * @param id The ID to validate
     * @return true if the ID appears to be valid
     */
    public static boolean isValidRecordId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        // Reject IDs that contain URL artifacts (indicates malformed URL parsing)
        if (id.contains("_mode")) {
            LogUtil.debug(CLASS_NAME, "isValidRecordId: rejected '" + id + "' - contains '_mode'");
            return false;
        }

        return true;
    }

    /**
     * Check if the input looks like a UUID.
     *
     * @param input The input to check
     * @return true if the input matches UUID format
     */
    public static boolean isUUID(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(input).matches();
    }
}
