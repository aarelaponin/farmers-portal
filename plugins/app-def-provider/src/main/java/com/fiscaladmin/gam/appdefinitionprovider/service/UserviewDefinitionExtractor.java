package com.fiscaladmin.gam.appdefinitionprovider.service;

import com.fiscaladmin.gam.appdefinitionprovider.exception.ExportException;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * UserviewDefinitionExtractor - Extracts userview definitions from Joget applications
 *
 * Key Features:
 * - Reads userview JSON from file system (v.json)
 * - Extracts complete userview structure (categories, menus)
 * - Filters CrudMenu entries for CRUD package export
 * - Supports full userview export
 */
public class UserviewDefinitionExtractor {

    private static final String CLASS_NAME = UserviewDefinitionExtractor.class.getName();
    private static final String CRUD_MENU_CLASS = "org.joget.plugin.enterprise.CrudMenu";

    /**
     * Extracts the complete userview definition for an application.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @return JSONObject containing full userview definition
     * @throws ExportException if app or userview not found or error occurs
     */
    public JSONObject extractUserview(String appId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting userview from app: " + appId);

        String wflowHome = getWflowHome();
        Path appPath = Paths.get(wflowHome, "app_src", appId);

        if (!Files.exists(appPath)) {
            throw ExportException.appNotFound(appId);
        }

        // Find version directory
        File appDir = appPath.toFile();
        File[] versionDirs = appDir.listFiles(File::isDirectory);

        if (versionDirs == null || versionDirs.length == 0) {
            throw ExportException.appNotFound(appId + " (no versions found)");
        }

        // Use first version directory found
        File versionDir = versionDirs[0];

        // Get userview file (always named v.json)
        Path userviewPath = Paths.get(versionDir.getAbsolutePath(), "userviews", "v.json");

        if (!Files.exists(userviewPath)) {
            throw new ExportException(404, "USERVIEW_NOT_FOUND",
                "Userview not found for application: " + appId);
        }

        try {
            // Read and parse userview JSON (Java 8 compatible)
            LogUtil.info(CLASS_NAME, "Reading userview file: " + userviewPath.toString());
            String jsonContent = new String(Files.readAllBytes(userviewPath), StandardCharsets.UTF_8);
            JSONObject userviewJson = new JSONObject(jsonContent);

            // Build response
            JSONObject userviewData = new JSONObject();
            userviewData.put("appId", appId);
            userviewData.put("definition", userviewJson);

            LogUtil.info(CLASS_NAME, "Successfully extracted userview for app: " + appId);
            return userviewData;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error reading userview file: " + userviewPath);
            throw ExportException.ioError("userview for " + appId, e);
        }
    }

    /**
     * Extracts all CRUD menu entries from an application's userview.
     *
     * Parses the userview structure and returns only CrudMenu items with their
     * category information and menu properties.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @return JSONArray containing all CRUD menu definitions
     * @throws ExportException if app or userview not found or error occurs
     */
    public JSONArray extractCrudMenus(String appId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting CRUD menus from app: " + appId);

        // Get the full userview
        JSONObject userviewData = extractUserview(appId);
        JSONObject userviewJson = userviewData.getJSONObject("definition");

        // Extract categories
        JSONArray categories = userviewJson.optJSONArray("categories");
        if (categories == null) {
            LogUtil.warn(CLASS_NAME, "No categories found in userview for app: " + appId);
            return new JSONArray();
        }

        List<JSONObject> crudMenus = new ArrayList<>();

        // Iterate through categories
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            JSONObject categoryProps = category.optJSONObject("properties");
            String categoryLabel = categoryProps != null ? categoryProps.optString("label", "Unknown") : "Unknown";

            // Extract menus from this category
            JSONArray menus = category.optJSONArray("menus");
            if (menus == null) {
                continue;
            }

            // Iterate through menus
            for (int j = 0; j < menus.length(); j++) {
                JSONObject menu = menus.getJSONObject(j);
                String className = menu.optString("className", "");

                // Check if this is a CrudMenu
                if (CRUD_MENU_CLASS.equals(className)) {
                    JSONObject menuProps = menu.optJSONObject("properties");

                    if (menuProps != null) {
                        // Extract CRUD menu data
                        JSONObject crudMenuData = new JSONObject();
                        crudMenuData.put("category", categoryLabel);
                        crudMenuData.put("className", className);
                        crudMenuData.put("properties", menuProps);

                        // Extract key identifiers
                        String customId = menuProps.optString("customId", "");
                        String label = menuProps.optString("label", "");
                        String addFormId = menuProps.optString("addFormId", "");
                        String editFormId = menuProps.optString("editFormId", "");
                        String datalistId = menuProps.optString("datalistId", "");

                        crudMenuData.put("customId", customId);
                        crudMenuData.put("label", label);
                        crudMenuData.put("addFormId", addFormId);
                        crudMenuData.put("editFormId", editFormId);
                        crudMenuData.put("datalistId", datalistId);

                        crudMenus.add(crudMenuData);

                        LogUtil.debug(CLASS_NAME, "Found CRUD menu: " + label + " (customId: " + customId + ")");
                    }
                }
            }
        }

        LogUtil.info(CLASS_NAME, "Found " + crudMenus.size() + " CRUD menus in app: " + appId);
        return new JSONArray(crudMenus);
    }

    /**
     * Extracts a single CRUD menu by its customId.
     *
     * @param appId The application ID
     * @param crudId The CRUD customId (e.g., "subsidyApplication_crud")
     * @return JSONObject containing the CRUD menu definition
     * @throws ExportException if CRUD menu not found
     */
    public JSONObject extractCrudMenu(String appId, String crudId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting CRUD menu: " + crudId + " from app: " + appId);

        JSONArray allCrudMenus = extractCrudMenus(appId);

        // Find the specific CRUD menu
        for (int i = 0; i < allCrudMenus.length(); i++) {
            JSONObject crudMenu = allCrudMenus.getJSONObject(i);
            String customId = crudMenu.optString("customId", "");

            if (crudId.equals(customId)) {
                LogUtil.info(CLASS_NAME, "Found CRUD menu: " + crudId);
                return crudMenu;
            }
        }

        // CRUD menu not found
        throw new ExportException(404, "CRUD_NOT_FOUND",
            "CRUD menu not found: " + crudId + " in application " + appId);
    }

    /**
     * Gets the Joget wflow home directory from configuration.
     *
     * @return wflow home path
     * @throws ExportException if not configured
     */
    private String getWflowHome() throws ExportException {
        try {
            // Get wflow home directory using static method
            String wflowHome = SetupManager.getBaseDirectory();

            if (wflowHome == null || wflowHome.isEmpty()) {
                throw new ExportException(500, "CONFIG_ERROR",
                    "Joget wflow.home not configured");
            }

            return wflowHome;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting wflow.home");
            throw new ExportException(500, "CONFIG_ERROR",
                "Cannot access Joget configuration", e);
        }
    }
}
