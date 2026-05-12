package com.fiscaladmin.gam.appdefinitionprovider.service;

import com.fiscaladmin.gam.appdefinitionprovider.exception.ExportException;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * FormDefinitionExtractor - Extracts form definitions from Joget applications
 *
 * Key Features:
 * - Reads form JSON from file system
 * - Filters out metadata forms (MD. prefix)
 * - Supports single form export
 * - Supports bulk app export
 * - Compatible with FormCreator import format
 */
public class FormDefinitionExtractor {

    private static final String CLASS_NAME = FormDefinitionExtractor.class.getName();
    private static final String METADATA_PREFIX = "MD.";

    /**
     * Extracts a single form definition by app ID and form ID.
     *
     * Searches the specified application for the form and exports it.
     * Automatically rejects metadata forms with MD. prefix.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @param formId The form definition ID (e.g., "livestockDetailsForm")
     * @return JSONObject containing form metadata and definition
     * @throws ExportException if form not found, is metadata, or error occurs
     */
    public JSONObject extractForm(String appId, String formId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting form: " + formId + " from app: " + appId);

        // Get Joget home directory
        String wflowHome = getWflowHome();

        // Path to the specific application
        Path appPath = Paths.get(wflowHome, "app_src", appId);

        if (!Files.exists(appPath)) {
            throw ExportException.appNotFound(appId);
        }

        // Check version directories
        File appDir = appPath.toFile();
        File[] versionDirs = appDir.listFiles(File::isDirectory);

        if (versionDirs == null || versionDirs.length == 0) {
            throw ExportException.appNotFound(appId + " (no versions found)");
        }

        // Search each version directory for the form
        for (File versionDir : versionDirs) {
            Path formPath = Paths.get(versionDir.getAbsolutePath(), "forms", formId + ".json");

            if (Files.exists(formPath)) {
                // Found the form!
                LogUtil.info(CLASS_NAME, "Found form in app: " + appId + ", version: " + versionDir.getName());
                return extractFormFromFile(formPath, formId, appId);
            }
        }

        // Form not found in the specified application
        throw ExportException.formNotFound(formId + " in application " + appId);
    }

    /**
     * Extracts all non-metadata forms from an application.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @return JSONObject containing app metadata and all form definitions
     * @throws ExportException if app not found or error occurs
     */
    public JSONObject extractAllFormsFromApp(String appId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting all forms from app: " + appId);

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
        String versionDirName = versionDir.getName();

        // Extract version number
        String version = "1";
        int underscorePos = versionDirName.lastIndexOf('_');
        if (underscorePos > 0) {
            version = versionDirName.substring(underscorePos + 1);
        }

        // Get forms directory
        Path formsPath = Paths.get(versionDir.getAbsolutePath(), "forms");

        if (!Files.exists(formsPath)) {
            LogUtil.warn(CLASS_NAME, "No forms directory found for app: " + appId);
            // Return empty forms list
            JSONObject appData = new JSONObject();
            appData.put("appId", appId);
            appData.put("appName", appId);
            appData.put("version", version);
            appData.put("forms", new JSONArray());
            appData.put("totalForms", 0);
            appData.put("excludedMetadataForms", 0);
            return appData;
        }

        // Scan all form files
        File formsDir = formsPath.toFile();
        File[] formFiles = formsDir.listFiles((dir, name) -> name.endsWith(".json"));

        List<JSONObject> exportedForms = new ArrayList<>();
        int excludedCount = 0;

        if (formFiles != null) {
            for (File formFile : formFiles) {
                String formId = formFile.getName().replace(".json", "");

                try {
                    // Read and parse form
                    String jsonContent = Files.readString(formFile.toPath());
                    JSONObject formJson = new JSONObject(jsonContent);
                    JSONObject properties = formJson.optJSONObject("properties");

                    if (properties == null) {
                        LogUtil.warn(CLASS_NAME, "Form has no properties: " + formId);
                        continue;
                    }

                    String formName = properties.optString("name", "");

                    // Check for metadata prefix
                    if (formName.startsWith(METADATA_PREFIX)) {
                        LogUtil.debug(CLASS_NAME, "Excluding metadata form: " + formName);
                        excludedCount++;
                        continue;
                    }

                    // Include this form
                    JSONObject formData = new JSONObject();
                    formData.put("formId", formId);
                    formData.put("formName", formName);
                    formData.put("tableName", properties.optString("tableName", ""));
                    formData.put("definition", formJson);

                    exportedForms.add(formData);

                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error processing form: " + formId);
                    // Continue with other forms
                }
            }
        }

        // Build response
        JSONObject appData = new JSONObject();
        appData.put("appId", appId);
        appData.put("appName", appId); // TODO: Read from app.properties
        appData.put("version", version);
        appData.put("forms", new JSONArray(exportedForms));
        appData.put("totalForms", exportedForms.size());
        appData.put("excludedMetadataForms", excludedCount);

        LogUtil.info(CLASS_NAME, "Exported " + exportedForms.size() +
            " forms, excluded " + excludedCount + " metadata forms");

        return appData;
    }

    /**
     * Extracts form definition from a file path.
     *
     * Validates the form is not a metadata form before returning.
     *
     * @param formPath Path to form JSON file
     * @param formId Form ID
     * @param appId Application ID
     * @return JSONObject containing form data
     * @throws ExportException if form is metadata or read error occurs
     */
    private JSONObject extractFormFromFile(Path formPath, String formId, String appId)
        throws ExportException {

        try {
            // DEBUG: Log the exact file being read
            LogUtil.info(CLASS_NAME, "Reading form file: " + formPath.toString());

            // Read form JSON
            String jsonContent = Files.readString(formPath);
            JSONObject formJson = new JSONObject(jsonContent);

            // Get form properties
            JSONObject properties = formJson.optJSONObject("properties");
            if (properties == null) {
                throw new ExportException(500, "INVALID_FORM",
                    "Form has no properties section: " + formId);
            }

            String formName = properties.optString("name", "");
            String tableName = properties.optString("tableName", "");

            // DEBUG: Log the exact form name for tracing
            LogUtil.info(CLASS_NAME, "Form ID: " + formId + " -> Form Name: '" + formName + "' (starts with '" + METADATA_PREFIX + "': " + formName.startsWith(METADATA_PREFIX) + ")");

            // ===== CRITICAL: Check for metadata prefix =====
            if (formName.startsWith(METADATA_PREFIX)) {
                LogUtil.warn(CLASS_NAME, "REJECTING metadata form: " + formName);
                throw ExportException.metadataNotExported(formName);
            }

            // Build response
            JSONObject formData = new JSONObject();
            formData.put("formId", formId);
            formData.put("formName", formName);
            formData.put("tableName", tableName);
            formData.put("appId", appId);
            formData.put("definition", formJson);

            LogUtil.info(CLASS_NAME, "Successfully extracted form: " + formName);
            return formData;

        } catch (ExportException e) {
            // Re-throw export exceptions
            throw e;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error reading form file: " + formPath);
            throw ExportException.ioError(formId, e);
        }
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
