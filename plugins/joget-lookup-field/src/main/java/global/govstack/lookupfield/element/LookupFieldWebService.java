package global.govstack.lookupfield.element;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Web service endpoint for the Lookup Field plugin.
 *
 * Provides server-side data lookup using FormDataDao, avoiding the need
 * for API key authentication required by Joget's external REST API.
 *
 * The table name is resolved automatically from the form definition
 * using AppService.getFormTableName() — no manual table config needed.
 *
 * Endpoint: /jw/web/json/plugin/global.govstack.lookupfield.element.LookupFieldWebService/service
 *
 * Parameters:
 *   action   = "lookup" (required)
 *   appId    = Joget app ID (required, for resolving form → table name)
 *   formId   = form definition ID to query (required)
 *   keyCol   = column name to match against (optional; if empty, looks up by primary key)
 *   keyVal   = the value to search for (required)
 *
 * Returns: JSON object with the matching record's properties, or {} if not found.
 */
public class LookupFieldWebService extends ExtDefaultPlugin implements PluginWebSupport {

    private static final String CLASS_NAME = LookupFieldWebService.class.getName();

    public String getName() {
        return "Lookup Field Web Service";
    }

    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    public String getDescription() {
        return "AJAX endpoint for Lookup Field form element";
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        if (!"lookup".equals(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Missing or invalid 'action' parameter. Expected: lookup");
            return;
        }

        String appId = request.getParameter("appId");
        String formId = request.getParameter("formId");
        String keyColumn = request.getParameter("keyCol");
        String keyValue = request.getParameter("keyVal");

        if (appId == null || appId.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'appId' parameter");
            return;
        }
        if (formId == null || formId.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'formId' parameter");
            return;
        }
        if (keyValue == null || keyValue.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'keyVal' parameter");
            return;
        }

        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        try {
            // Ensure the AppDefinition is set in thread context — required by formDataDao
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef == null || !appId.equals(appDef.getAppId())) {
                AppDefinitionDao appDefDao = (AppDefinitionDao) AppUtil.getApplicationContext()
                    .getBean("appDefinitionDao");
                Long publishedVersion = appDefDao.getPublishedVersion(appId);
                if (publishedVersion != null) {
                    appDef = appDefDao.loadVersion(appId, publishedVersion);
                }
                if (appDef != null) {
                    AppUtil.setCurrentAppDefinition(appDef);
                }
            }

            // Resolve the table name from the form definition (Joget native way)
            String tableName = resolveTableName(appId, formId);

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            FormRow row = null;

            if (keyColumn != null && !keyColumn.isEmpty()) {
                // Query by column value
                String condition = "WHERE c_" + keyColumn + " = ?";
                Object[] params = { keyValue };
                FormRowSet rows = formDataDao.find(formId, tableName,
                    condition, params, null, null, 0, 1);
                if (rows != null && !rows.isEmpty()) {
                    row = rows.get(0);
                }
            } else {
                // Direct load by primary key
                row = formDataDao.load(formId, tableName, keyValue);
            }

            JSONObject result = new JSONObject();

            if (row != null) {
                // Copy all properties from the FormRow to the JSON response
                for (Map.Entry<Object, Object> entry : row.entrySet()) {
                    String key = entry.getKey().toString();
                    Object value = entry.getValue();
                    result.put(key, value != null ? value.toString() : "");
                }
            }

            PrintWriter writer = response.getWriter();
            writer.write(result.toString());
            writer.flush();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e,
                "Error looking up record: appId=" + appId
                + " formId=" + formId
                + " keyCol=" + keyColumn
                + " keyVal=" + keyValue);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Lookup failed: " + e.getMessage());
        }
    }

    /**
     * Resolve the DB table name from the Joget form definition.
     * Uses AppService.getFormTableName() which reads the tableName
     * from the form JSON — the same way Joget does it internally.
     */
    private String resolveTableName(String appId, String formId) {
        try {
            // App definition should already be set in thread context by webService()
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();

            if (appDef != null) {
                AppService appService = (AppService) AppUtil.getApplicationContext()
                    .getBean("appService");
                String tableName = appService.getFormTableName(appDef, formId);
                if (tableName != null && !tableName.isEmpty()) {
                    LogUtil.debug(CLASS_NAME,
                        "Resolved table name for form '" + formId + "': " + tableName);
                    return tableName;
                }
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME,
                "Could not resolve table name for form '" + formId + "', falling back to formId: "
                + e.getMessage());
        }

        // Fallback: use formId as table name
        return formId;
    }
}
