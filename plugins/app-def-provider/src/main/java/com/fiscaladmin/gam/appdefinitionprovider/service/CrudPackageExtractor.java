package com.fiscaladmin.gam.appdefinitionprovider.service;

import com.fiscaladmin.gam.appdefinitionprovider.exception.ExportException;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * CrudPackageExtractor - Orchestrates extraction of complete CRUD packages
 *
 * A CRUD package consists of:
 * - Form (data entry)
 * - Datalist (data display)
 * - Userview CrudMenu (UI integration)
 *
 * Key Features:
 * - Combines Form, Datalist, and Userview components
 * - Filters metadata CRUDs (based on form MD. prefix)
 * - Supports single CRUD export
 * - Supports bulk app CRUD export
 */
public class CrudPackageExtractor {

    private static final String CLASS_NAME = CrudPackageExtractor.class.getName();
    private static final String METADATA_PREFIX = "MD.";

    private FormDefinitionExtractor formExtractor;
    private DatalistDefinitionExtractor datalistExtractor;
    private UserviewDefinitionExtractor userviewExtractor;

    /**
     * Initialize the CRUD package extractor with all component extractors.
     */
    public CrudPackageExtractor() {
        this.formExtractor = new FormDefinitionExtractor();
        this.datalistExtractor = new DatalistDefinitionExtractor();
        this.userviewExtractor = new UserviewDefinitionExtractor();
    }

    /**
     * Extracts a single CRUD package by app ID and CRUD ID.
     *
     * The CRUD ID is the customId from the CrudMenu (e.g., "subsidyApplication_crud").
     * This method:
     * 1. Finds the CrudMenu in the userview
     * 2. Extracts the referenced form (using addFormId)
     * 3. Extracts the referenced datalist (using datalistId)
     * 4. Bundles all components together
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @param crudId The CRUD customId (e.g., "subsidyApplication_crud")
     * @return JSONObject containing complete CRUD package
     * @throws ExportException if CRUD not found, is metadata, or error occurs
     */
    public JSONObject extractCrudPackage(String appId, String crudId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting CRUD package: " + crudId + " from app: " + appId);

        // Step 1: Get the CRUD menu definition
        JSONObject crudMenu = userviewExtractor.extractCrudMenu(appId, crudId);

        String label = crudMenu.optString("label", "");
        String category = crudMenu.optString("category", "");
        String addFormId = crudMenu.optString("addFormId", "");
        String datalistId = crudMenu.optString("datalistId", "");

        LogUtil.info(CLASS_NAME, "CRUD menu found: " + label + " (form: " + addFormId + ", datalist: " + datalistId + ")");

        // Step 2: Extract the form
        JSONObject formData;
        try {
            formData = formExtractor.extractForm(appId, addFormId);
        } catch (ExportException e) {
            // Check if this is a metadata form rejection
            if ("METADATA_NOT_EXPORTED".equals(e.getErrorCode())) {
                // Re-throw with CRUD context
                throw new ExportException(400, "METADATA_CRUD_NOT_EXPORTED",
                    "Metadata CRUD packages cannot be exported: " + label);
            }
            throw e;
        }

        String formName = formData.optString("formName", "");

        // Additional metadata check (defensive)
        if (formName.startsWith(METADATA_PREFIX)) {
            LogUtil.warn(CLASS_NAME, "REJECTING metadata CRUD package: " + label);
            throw new ExportException(400, "METADATA_CRUD_NOT_EXPORTED",
                "Metadata CRUD packages cannot be exported: " + label);
        }

        // Step 3: Extract the datalist
        JSONObject datalistData;
        try {
            datalistData = datalistExtractor.extractDatalist(appId, datalistId);
        } catch (ExportException e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting datalist for CRUD: " + crudId);
            throw new ExportException(500, "CRUD_INCOMPLETE",
                "Cannot extract datalist for CRUD package: " + label, e);
        }

        // Step 4: Build the complete CRUD package
        JSONObject crudPackage = new JSONObject();
        crudPackage.put("packageId", crudId);
        crudPackage.put("category", category);
        crudPackage.put("label", label);

        // Build components object
        JSONObject components = new JSONObject();

        // Add form component
        JSONObject formComponent = new JSONObject();
        formComponent.put("id", addFormId);
        formComponent.put("name", formData.optString("formName", ""));
        formComponent.put("definition", formData.getJSONObject("definition"));
        components.put("form", formComponent);

        // Add datalist component
        JSONObject datalistComponent = new JSONObject();
        datalistComponent.put("id", datalistId);
        datalistComponent.put("name", datalistData.optString("datalistName", ""));
        datalistComponent.put("definition", datalistData.getJSONObject("definition"));
        components.put("datalist", datalistComponent);

        // Add CRUD menu component
        JSONObject crudMenuComponent = new JSONObject();
        crudMenuComponent.put("customId", crudId);
        crudMenuComponent.put("properties", crudMenu.getJSONObject("properties"));
        components.put("crudMenu", crudMenuComponent);

        crudPackage.put("components", components);

        LogUtil.info(CLASS_NAME, "Successfully extracted CRUD package: " + label);
        return crudPackage;
    }

    /**
     * Extracts all non-metadata CRUD packages from an application.
     *
     * Iterates through all CrudMenu entries in the userview and builds
     * complete CRUD packages for each, filtering out metadata CRUDs.
     *
     * @param appId The application ID (e.g., "farmersPortal")
     * @return JSONObject containing all CRUD packages and statistics
     * @throws ExportException if app not found or error occurs
     */
    public JSONObject extractAllCrudPackages(String appId) throws ExportException {
        LogUtil.info(CLASS_NAME, "Extracting all CRUD packages from app: " + appId);

        // Get all CRUD menus
        JSONArray allCrudMenus = userviewExtractor.extractCrudMenus(appId);

        List<JSONObject> exportedPackages = new ArrayList<>();
        int excludedMetadataCount = 0;
        int errorCount = 0;

        // Iterate through each CRUD menu
        for (int i = 0; i < allCrudMenus.length(); i++) {
            JSONObject crudMenu = allCrudMenus.getJSONObject(i);
            String customId = crudMenu.optString("customId", "");
            String label = crudMenu.optString("label", "");
            String addFormId = crudMenu.optString("addFormId", "");

            try {
                // Try to extract the complete CRUD package
                JSONObject crudPackage = extractCrudPackage(appId, customId);
                exportedPackages.add(crudPackage);

                LogUtil.debug(CLASS_NAME, "Exported CRUD package: " + label);

            } catch (ExportException e) {
                if ("METADATA_NOT_EXPORTED".equals(e.getErrorCode()) ||
                    "METADATA_CRUD_NOT_EXPORTED".equals(e.getErrorCode())) {
                    // Metadata CRUD - expected exclusion
                    LogUtil.debug(CLASS_NAME, "Excluding metadata CRUD: " + label);
                    excludedMetadataCount++;
                } else {
                    // Unexpected error
                    LogUtil.warn(CLASS_NAME, "Error extracting CRUD package '" + label + "': " + e.getMessage());
                    errorCount++;
                }
            } catch (Exception e) {
                // Unexpected exception
                LogUtil.error(CLASS_NAME, e, "Unexpected error extracting CRUD package: " + label);
                errorCount++;
            }
        }

        // Build response
        JSONObject response = new JSONObject();
        response.put("appId", appId);
        response.put("packages", new JSONArray(exportedPackages));
        response.put("totalPackages", exportedPackages.size());
        response.put("excludedMetadataPackages", excludedMetadataCount);
        response.put("errorCount", errorCount);

        LogUtil.info(CLASS_NAME, "Exported " + exportedPackages.size() + " CRUD packages, " +
            "excluded " + excludedMetadataCount + " metadata packages, " +
            errorCount + " errors");

        return response;
    }

    /**
     * Counts CRUD packages in an application (for catalog).
     *
     * Returns counts of total CRUDs, non-metadata CRUDs, and metadata CRUDs.
     *
     * @param appId The application ID
     * @return JSONObject with counts
     */
    public JSONObject countCrudPackages(String appId) {
        JSONObject counts = new JSONObject();
        counts.put("totalCrudMenus", 0);
        counts.put("crudPackageCount", 0);
        counts.put("metadataCrudPackageCount", 0);

        try {
            // Get all CRUD menus
            JSONArray allCrudMenus = userviewExtractor.extractCrudMenus(appId);
            int totalCruds = allCrudMenus.length();
            int metadataCount = 0;

            // Count metadata CRUDs by checking the form
            for (int i = 0; i < allCrudMenus.length(); i++) {
                JSONObject crudMenu = allCrudMenus.getJSONObject(i);
                String addFormId = crudMenu.optString("addFormId", "");

                try {
                    // Try to extract the form (will throw exception if metadata)
                    formExtractor.extractForm(appId, addFormId);
                } catch (ExportException e) {
                    if ("METADATA_NOT_EXPORTED".equals(e.getErrorCode())) {
                        metadataCount++;
                    }
                }
            }

            counts.put("totalCrudMenus", totalCruds);
            counts.put("crudPackageCount", totalCruds - metadataCount);
            counts.put("metadataCrudPackageCount", metadataCount);

        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error counting CRUD packages for " + appId + ": " + e.getMessage());
        }

        return counts;
    }
}
