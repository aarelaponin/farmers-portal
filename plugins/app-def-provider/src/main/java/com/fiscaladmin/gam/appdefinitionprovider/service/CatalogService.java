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
 * CatalogService - Provides application catalog functionality
 *
 * Scans the Joget wflow directory to find available applications
 * and returns metadata about exportable applications.
 */
public class CatalogService {

    private static final String CLASS_NAME = CatalogService.class.getName();

    public CatalogService() {
        // Constructor
    }

    /**
     * Lists all available applications that can be exported.
     *
     * Scans the wflow/app_src directory and returns application metadata.
     *
     * @return JSONObject containing application catalog
     */
    public JSONObject listApplications() throws ExportException {
        LogUtil.info(CLASS_NAME, "Listing available applications");

        try {
            // Get Joget home directory using static method
            String wflowHome = SetupManager.getBaseDirectory();

            if (wflowHome == null || wflowHome.isEmpty()) {
                throw new ExportException(500, "CONFIG_ERROR",
                    "Joget wflow.home not configured");
            }

            // Path to app_src directory
            Path appSrcPath = Paths.get(wflowHome, "app_src");

            if (!Files.exists(appSrcPath)) {
                LogUtil.warn(CLASS_NAME, "app_src directory not found: " + appSrcPath);
                // Return empty catalog
                JSONObject catalog = new JSONObject();
                catalog.put("applications", new JSONArray());
                catalog.put("totalApplications", 0);
                return catalog;
            }

            // Scan for applications
            List<JSONObject> applications = new ArrayList<>();
            File appSrcDir = appSrcPath.toFile();
            File[] appDirs = appSrcDir.listFiles(File::isDirectory);

            if (appDirs != null) {
                for (File appDir : appDirs) {
                    String appId = appDir.getName();

                    // Skip hidden directories
                    if (appId.startsWith(".")) {
                        continue;
                    }

                    try {
                        JSONObject appInfo = getApplicationInfo(wflowHome, appId);
                        if (appInfo != null) {
                            applications.add(appInfo);
                        }
                    } catch (Exception e) {
                        LogUtil.warn(CLASS_NAME, "Error reading app " + appId + ": " + e.getMessage());
                        // Continue with other apps
                    }
                }
            }

            // Build catalog response
            JSONObject catalog = new JSONObject();
            catalog.put("applications", new JSONArray(applications));
            catalog.put("totalApplications", applications.size());

            LogUtil.info(CLASS_NAME, "Found " + applications.size() + " applications");
            return catalog;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error listing applications");
            throw ExportException.ioError("application catalog", e);
        }
    }

    /**
     * Gets metadata for a single application.
     *
     * @param wflowHome Joget wflow home directory
     * @param appId Application ID
     * @return JSONObject with application metadata, or null if not found
     */
    private JSONObject getApplicationInfo(String wflowHome, String appId) {
        try {
            // Find the latest version directory (e.g., farmersPortal_1)
            Path appPath = Paths.get(wflowHome, "app_src", appId);
            File appDir = appPath.toFile();
            File[] versionDirs = appDir.listFiles(File::isDirectory);

            if (versionDirs == null || versionDirs.length == 0) {
                return null;
            }

            // For now, use the first version found
            // TODO: Parse version numbers and use latest
            File latestVersionDir = versionDirs[0];
            String versionDirName = latestVersionDir.getName();

            // Extract version from directory name (e.g., "farmersPortal_1" -> "1")
            String version = "1";
            int underscorePos = versionDirName.lastIndexOf('_');
            if (underscorePos > 0) {
                version = versionDirName.substring(underscorePos + 1);
            }

            // Count forms (excluding metadata forms)
            Path formsPath = Paths.get(latestVersionDir.getAbsolutePath(), "forms");
            int formCount = 0;
            int metadataFormCount = 0;

            if (Files.exists(formsPath)) {
                File formsDir = formsPath.toFile();
                File[] formFiles = formsDir.listFiles((dir, name) -> name.endsWith(".json"));

                if (formFiles != null) {
                    for (File formFile : formFiles) {
                        try {
                            String jsonContent = new String(Files.readAllBytes(formFile.toPath()), StandardCharsets.UTF_8);
                            JSONObject formJson = new JSONObject(jsonContent);
                            JSONObject properties = formJson.optJSONObject("properties");

                            if (properties != null) {
                                String formName = properties.optString("name", "");
                                if (formName.startsWith("MD.")) {
                                    metadataFormCount++;
                                } else {
                                    formCount++;
                                }
                            } else {
                                formCount++; // Count as regular if no name found
                            }
                        } catch (Exception e) {
                            LogUtil.warn(CLASS_NAME, "Error checking form: " + formFile.getName());
                        }
                    }
                }
            }

            // Count datalists (excluding metadata datalists)
            Path listsPath = Paths.get(latestVersionDir.getAbsolutePath(), "lists");
            int datalistCount = 0;
            int metadataDatalistCount = 0;

            if (Files.exists(listsPath)) {
                File listsDir = listsPath.toFile();
                File[] datalistFiles = listsDir.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("v.json"));

                if (datalistFiles != null) {
                    for (File datalistFile : datalistFiles) {
                        try {
                            String jsonContent = new String(Files.readAllBytes(datalistFile.toPath()), StandardCharsets.UTF_8);
                            JSONObject datalistJson = new JSONObject(jsonContent);
                            String datalistName = datalistJson.optString("name", "");

                            if (datalistName.startsWith("List - MD.")) {
                                metadataDatalistCount++;
                            } else {
                                datalistCount++;
                            }
                        } catch (Exception e) {
                            LogUtil.warn(CLASS_NAME, "Error checking datalist: " + datalistFile.getName());
                        }
                    }
                }
            }

            // Count CRUD packages using CrudPackageExtractor
            CrudPackageExtractor crudExtractor = new CrudPackageExtractor();
            JSONObject crudCounts = crudExtractor.countCrudPackages(appId);
            int crudPackageCount = crudCounts.optInt("crudPackageCount", 0);
            int metadataCrudPackageCount = crudCounts.optInt("metadataCrudPackageCount", 0);

            // Build application info
            JSONObject appInfo = new JSONObject();
            appInfo.put("appId", appId);
            appInfo.put("appName", appId); // TODO: Read from app.properties
            appInfo.put("version", version);
            appInfo.put("formCount", formCount);
            appInfo.put("metadataFormCount", metadataFormCount);
            appInfo.put("datalistCount", datalistCount);
            appInfo.put("metadataDatalistCount", metadataDatalistCount);
            appInfo.put("crudPackageCount", crudPackageCount);
            appInfo.put("metadataCrudPackageCount", metadataCrudPackageCount);
            appInfo.put("exportUrl", "/jw/api/appdefinition/apps/" + appId + "/forms");
            appInfo.put("crudsExportUrl", "/jw/api/appdefinition/apps/" + appId + "/cruds");
            appInfo.put("userviewExportUrl", "/jw/api/appdefinition/apps/" + appId + "/userview");

            return appInfo;

        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error getting info for app " + appId + ": " + e.getMessage());
            return null;
        }
    }
}
