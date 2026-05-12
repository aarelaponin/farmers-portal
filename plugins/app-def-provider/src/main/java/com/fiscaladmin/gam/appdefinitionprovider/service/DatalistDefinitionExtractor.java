package com.fiscaladmin.gam.appdefinitionprovider.service;

import com.fiscaladmin.gam.appdefinitionprovider.exception.ExportException;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * DatalistDefinitionExtractor - Extracts datalist definitions from Joget applications
 *
 * Key Features:
 * - Reads datalist JSON from file system
 * - Filters out metadata datalists (List - MD. prefix)
 * - Supports single datalist export
 * - Supports bulk app export
 * - Compatible with FormCreator import format
 */
public class DatalistDefinitionExtractor {

    private static final String CLASS_NAME = DatalistDefinitionExtractor.class.getName();
    private static final String METADATA_PREFIX = "List - MD.";

    /**
     * Extracts a single datalist definition by app ID and datalist ID.
     *
     * Searches the specified application for the datalist and exports it.
     * Automatically rejects metadata datalists with "List - MD." prefix.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @param datalistId The datalist definition ID (e.g., "list_farmerBasicInfo")
     * @return JSONObject containing datalist metadata and definition
     * @throws ExportException if datalist not found, is metadata, or error occurs
     */
    public JSONObject extractDatalist(String appId, String datalistId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting datalist: " + datalistId + " from app: " + appId);

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

        // Search each version directory for the datalist
        for (File versionDir : versionDirs) {
            Path datalistPath = Paths.get(versionDir.getAbsolutePath(), "lists", datalistId + ".json");

            if (Files.exists(datalistPath)) {
                // Found the datalist!
                LogUtil.info(CLASS_NAME, "Found datalist in app: " + appId + ", version: " + versionDir.getName());
                return extractDatalistFromFile(datalistPath, datalistId, appId);
            }
        }

        // Datalist not found in the specified application
        throw ExportException.formNotFound(datalistId + " in application " + appId);
    }

    /**
     * Extracts all non-metadata datalists from an application.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @return JSONObject containing app metadata and all datalist definitions
     * @throws ExportException if app not found or error occurs
     */
    public JSONObject extractAllDatalistsFromApp(String appId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting all datalists from app: " + appId);

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

        // Get lists directory
        Path listsPath = Paths.get(versionDir.getAbsolutePath(), "lists");

        if (!Files.exists(listsPath)) {
            LogUtil.warn(CLASS_NAME, "No lists directory found for app: " + appId);
            // Return empty datalists list
            JSONObject appData = new JSONObject();
            appData.put("appId", appId);
            appData.put("appName", appId);
            appData.put("version", version);
            appData.put("datalists", new JSONArray());
            appData.put("totalDataLists", 0);
            appData.put("excludedMetadataDataLists", 0);
            return appData;
        }

        // Scan all datalist files
        File listsDir = listsPath.toFile();
        File[] datalistFiles = listsDir.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("v.json"));

        List<JSONObject> exportedDataLists = new ArrayList<>();
        int excludedCount = 0;

        if (datalistFiles != null) {
            for (File datalistFile : datalistFiles) {
                String datalistId = datalistFile.getName().replace(".json", "");

                try {
                    // Read and parse datalist (Java 8 compatible)
                    String jsonContent = new String(Files.readAllBytes(datalistFile.toPath()), StandardCharsets.UTF_8);
                    JSONObject datalistJson = new JSONObject(jsonContent);

                    String datalistName = datalistJson.optString("name", "");

                    // Check for metadata prefix
                    if (datalistName.startsWith(METADATA_PREFIX)) {
                        LogUtil.debug(CLASS_NAME, "Excluding metadata datalist: " + datalistName);
                        excludedCount++;
                        continue;
                    }

                    // Include this datalist
                    JSONObject datalistData = new JSONObject();
                    datalistData.put("datalistId", datalistId);
                    datalistData.put("datalistName", datalistName);
                    datalistData.put("definition", datalistJson);

                    exportedDataLists.add(datalistData);

                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error processing datalist: " + datalistId);
                    // Continue with other datalists
                }
            }
        }

        // Build response
        JSONObject appData = new JSONObject();
        appData.put("appId", appId);
        appData.put("appName", appId); // TODO: Read from app.properties
        appData.put("version", version);
        appData.put("datalists", new JSONArray(exportedDataLists));
        appData.put("totalDataLists", exportedDataLists.size());
        appData.put("excludedMetadataDataLists", excludedCount);

        LogUtil.info(CLASS_NAME, "Exported " + exportedDataLists.size() +
            " datalists, excluded " + excludedCount + " metadata datalists");

        return appData;
    }

    /**
     * Extracts datalist definition from a file path.
     *
     * Validates the datalist is not a metadata datalist before returning.
     *
     * @param datalistPath Path to datalist JSON file
     * @param datalistId Datalist ID
     * @param appId Application ID
     * @return JSONObject containing datalist data
     * @throws ExportException if datalist is metadata or read error occurs
     */
    private JSONObject extractDatalistFromFile(Path datalistPath, String datalistId, String appId)
        throws ExportException {

        try {
            // DEBUG: Log the exact file being read
            LogUtil.info(CLASS_NAME, "Reading datalist file: " + datalistPath.toString());

            // Read datalist JSON (Java 8 compatible)
            String jsonContent = new String(Files.readAllBytes(datalistPath), StandardCharsets.UTF_8);
            JSONObject datalistJson = new JSONObject(jsonContent);

            // Get datalist name
            String datalistName = datalistJson.optString("name", "");

            // DEBUG: Log the exact datalist name for tracing
            LogUtil.info(CLASS_NAME, "Datalist ID: " + datalistId + " -> Datalist Name: '" + datalistName +
                "' (starts with '" + METADATA_PREFIX + "': " + datalistName.startsWith(METADATA_PREFIX) + ")");

            // ===== CRITICAL: Check for metadata prefix =====
            if (datalistName.startsWith(METADATA_PREFIX)) {
                LogUtil.warn(CLASS_NAME, "REJECTING metadata datalist: " + datalistName);
                throw ExportException.metadataNotExported(datalistName);
            }

            // Build response
            JSONObject datalistData = new JSONObject();
            datalistData.put("datalistId", datalistId);
            datalistData.put("datalistName", datalistName);
            datalistData.put("appId", appId);
            datalistData.put("definition", datalistJson);

            LogUtil.info(CLASS_NAME, "Successfully extracted datalist: " + datalistName);
            return datalistData;

        } catch (ExportException e) {
            // Re-throw export exceptions
            throw e;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error reading datalist file: " + datalistPath);
            throw ExportException.ioError(datalistId, e);
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
