package com.fiscaladmin.gam.appdefinitionprovider.lib;

import com.fiscaladmin.gam.appdefinitionprovider.service.CatalogService;
import com.fiscaladmin.gam.appdefinitionprovider.service.FormDefinitionExtractor;
import com.fiscaladmin.gam.appdefinitionprovider.service.DatalistDefinitionExtractor;
import com.fiscaladmin.gam.appdefinitionprovider.service.UserviewDefinitionExtractor;
import com.fiscaladmin.gam.appdefinitionprovider.service.CrudPackageExtractor;
import com.fiscaladmin.gam.appdefinitionprovider.exception.ExportException;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.property.model.PropertyEditable;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * AppDefinitionProvider - GovStack-compliant Application Definition Export API
 *
 * This plugin provides REST API endpoints to export Joget application definitions
 * for deployment to other instances. It implements GovStack Registration Building Block
 * Section 6.3.2.10 (Import/Export of Service Descriptions).
 *
 * Key Features:
 * - Export individual forms, processes, datalists, userviews
 * - Export complete application packages
 * - Automatic filtering of metadata forms (MD. prefix)
 * - Compatible with FormCreator import format
 *
 * REST Endpoints:
 * - GET /jw/api/appdefinition/catalog - List all available applications
 * - GET /jw/api/appdefinition/forms/{formId} - Export single form definition
 * - GET /jw/api/appdefinition/apps/{appId}/forms - Export all forms from an application
 *
 * @author FiscalAdmin GAM Team
 * @version 8.1-SNAPSHOT
 */
public class AppDefinitionProvider extends ApiPluginAbstract implements PropertyEditable {

    private static final String CLASS_NAME = AppDefinitionProvider.class.getName();

    // Services
    private CatalogService catalogService;
    private FormDefinitionExtractor formExtractor;
    private DatalistDefinitionExtractor datalistExtractor;
    private UserviewDefinitionExtractor userviewExtractor;
    private CrudPackageExtractor crudPackageExtractor;

    /**
     * Initialize services on plugin load
     */
    public AppDefinitionProvider() {
        System.out.println("========================================");
        System.out.println("=== AppDefinitionProvider CONSTRUCTOR CALLED ===");
        System.out.println("========================================");
        LogUtil.info(CLASS_NAME, "AppDefinitionProvider initializing...");

        this.catalogService = new CatalogService();
        this.formExtractor = new FormDefinitionExtractor();
        this.datalistExtractor = new DatalistDefinitionExtractor();
        this.userviewExtractor = new UserviewDefinitionExtractor();
        this.crudPackageExtractor = new CrudPackageExtractor();

        LogUtil.info(CLASS_NAME, "AppDefinitionProvider initialized successfully");
        System.out.println("=== AppDefinitionProvider INITIALIZED ===");
    }

    // ===== Plugin Metadata Methods =====

    @Override
    public String getName() {
        return "app-definition-provider";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "GovStack-compliant API for exporting Joget application definitions. Provides REST endpoints to export forms, processes, datalists, userviews, and complete service packages.";
    }

    @Override
    public String getLabel() {
        return "App Definition Provider";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return "[]"; // No configuration needed for now
    }

    // ===== API Builder Required Methods =====

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-download\"></i>";
    }

    @Override
    public String getTag() {
        String tag = "appdefinition";  // Creates /jw/api/appdefinition/* endpoints
        LogUtil.info(CLASS_NAME, "getTag() called, returning: " + tag);
        return tag;
    }

    @Override
    public String getTagDesc() {
        return "App Definition Export APIs - GovStack compliant endpoints for exporting Joget application definitions";
    }

    @Override
    public String getResourceBundlePath() {
        // Simplified - no resource bundle needed for now
        return null;
    }

    // ===== API Endpoints =====

    /**
     * GET /jw/api/appdefinition/catalog
     *
     * Returns a catalog of all available applications that can be exported.
     * Lists application ID, name, version, and count of exportable forms
     * (excluding metadata forms with MD. prefix).
     *
     * Response Example:
     * {
     *   "status": "success",
     *   "data": {
     *     "applications": [
     *       {
     *         "appId": "farmersPortal",
     *         "appName": "Farmer Portal (Sender)",
     *         "version": "1",
     *         "formCount": 12,
     *         "exportUrl": "/jw/api/appdefinition/apps/farmersPortal/forms"
     *       }
     *     ]
     *   }
     * }
     */
    @Operation(
        path = "/catalog",
        type = Operation.MethodType.GET,
        summary = "List Available Applications",
        description = "Returns a catalog of all available applications that can be exported"
    )
    @Responses({
        @Response(responseCode = 200, description = "Success"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse getCatalog() {
        LogUtil.info(CLASS_NAME, "Fetching application catalog");

        try {
            JSONObject catalog = catalogService.listApplications();

            // Build successful response
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("data", catalog);

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error fetching catalog: " + e.getMessage());
            return buildErrorResponse(500, "Error fetching catalog", e.getMessage());
        }
    }

    /**
     * GET /jw/api/appdefinition/forms/{appId}/{formId}
     *
     * Exports a single form definition by app ID and form ID.
     * Automatically filters out metadata forms (those with MD. prefix).
     *
     * Path Parameters:
     * - appId: The application ID (e.g., "farmersPortal")
     * - formId: The form definition ID (e.g., "livestockDetailsForm")
     *
     * Response Example:
     * {
     *   "status": "success",
     *   "data": {
     *     "formId": "livestockDetailsForm",
     *     "formName": "01.05-2 - Livestock Details Form",
     *     "definition": { ... form JSON ... }
     *   }
     * }
     *
     * Error Response (Metadata Form):
     * {
     *   "status": "error",
     *   "error": {
     *     "code": "METADATA_NOT_EXPORTED",
     *     "message": "Metadata forms cannot be exported: MD.01 - Marital Status"
     *   }
     * }
     */
    @Operation(
        path = "/apps/{appId}/form",
        type = Operation.MethodType.GET,
        summary = "Export Form Definition",
        description = "Exports a single form definition by app ID (path) and formId (query parameter). Metadata forms are excluded"
    )
    @Responses({
        @Response(responseCode = 200, description = "Form exported successfully"),
        @Response(responseCode = 400, description = "Invalid form ID or metadata form"),
        @Response(responseCode = 404, description = "Form not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse exportForm(
        @Param(value = "appId") String appId,
        @Param(value = "formId", required = true) String formId
    ) {
        LogUtil.info(CLASS_NAME, "Exporting form: " + formId + " from app: " + appId);

        try {
            // Extract form definition from specific app (includes MD. filtering)
            JSONObject formData = formExtractor.extractForm(appId, formId);

            // Extract ONLY the pure form definition for FormCreator compatibility
            // FormCreator expects: {className, properties, elements}
            // Not wrapped in: {status, data, formId, formName, definition}
            JSONObject definition = formData.getJSONObject("definition");

            LogUtil.info(CLASS_NAME, "Form exported successfully: " + formId + " from app: " + appId);

            // Return pure form JSON without wrappers
            return new ApiResponse(200, definition.toString());

        } catch (ExportException e) {
            // Known export error (e.g., metadata form, not found)
            LogUtil.warn(CLASS_NAME, "Export failed for form " + formId + " in app " + appId + ": " + e.getMessage());
            return buildErrorResponse(e.getStatusCode(), e.getErrorCode(), e.getMessage());

        } catch (Exception e) {
            // Unexpected error
            LogUtil.error(CLASS_NAME, e, "Unexpected error exporting form " + formId + " from app " + appId);
            return buildErrorResponse(500, "INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * GET /jw/api/appdefinition/apps/{appId}/forms
     *
     * Exports all non-metadata forms from an application.
     * Automatically filters out forms with MD. prefix.
     *
     * Path Parameters:
     * - appId: The application ID (e.g., "farmersPortal")
     *
     * Response Example:
     * {
     *   "status": "success",
     *   "data": {
     *     "appId": "farmersPortal",
     *     "appName": "Farmer Portal (Sender)",
     *     "forms": [
     *       {
     *         "formId": "livestockDetailsForm",
     *         "formName": "01.05-2 - Livestock Details Form",
     *         "definition": { ... }
     *       },
     *       ...
     *     ],
     *     "totalForms": 12,
     *     "excludedMetadataForms": 20
     *   }
     * }
     */
    @Operation(
        path = "/apps/{appId}/forms",
        type = Operation.MethodType.GET,
        summary = "Export Application Forms",
        description = "Exports all non-metadata forms from the specified application"
    )
    @Responses({
        @Response(responseCode = 200, description = "Application forms exported successfully"),
        @Response(responseCode = 404, description = "Application not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse exportAppForms(
        @Param(value = "appId") String appId
    ) {
        LogUtil.info(CLASS_NAME, "Exporting forms for application: " + appId);

        try {
            JSONObject appData = formExtractor.extractAllFormsFromApp(appId);

            // Build successful response
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("data", appData);

            LogUtil.info(CLASS_NAME, "Application forms exported successfully: " + appId);

            return new ApiResponse(200, response.toString());

        } catch (ExportException e) {
            LogUtil.warn(CLASS_NAME, "Export failed for app " + appId + ": " + e.getMessage());
            return buildErrorResponse(e.getStatusCode(), e.getErrorCode(), e.getMessage());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error exporting app " + appId);
            return buildErrorResponse(500, "INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * GET /jw/api/appdefinition/apps/{appId}/crud?crudId={customId}
     *
     * Exports a single CRUD package (Form + Datalist + Userview menu).
     * A CRUD package is the minimal deployable unit for a complete feature.
     *
     * Path Parameters:
     * - appId: The application ID (e.g., "farmersPortal")
     *
     * Query Parameters:
     * - crudId: The CRUD customId (e.g., "subsidyApplication_crud")
     *
     * Response Example:
     * {
     *   "packageId": "subsidyApplication_crud",
     *   "category": "Home",
     *   "label": "Subsidy Applications",
     *   "components": {
     *     "form": {
     *       "id": "subsidyApplication",
     *       "name": "Subsidy Application Form",
     *       "definition": { ... form JSON ... }
     *     },
     *     "datalist": {
     *       "id": "list_subsidyApplication",
     *       "name": "List - Subsidy Applications",
     *       "definition": { ... datalist JSON ... }
     *     },
     *     "crudMenu": {
     *       "customId": "subsidyApplication_crud",
     *       "properties": { ... menu properties ... }
     *     }
     *   }
     * }
     */
    @Operation(
        path = "/apps/{appId}/crud",
        type = Operation.MethodType.GET,
        summary = "Export CRUD Package",
        description = "Exports a complete CRUD package (Form + Datalist + Userview menu) by app ID and CRUD customId"
    )
    @Responses({
        @Response(responseCode = 200, description = "CRUD package exported successfully"),
        @Response(responseCode = 400, description = "Invalid CRUD ID or metadata CRUD"),
        @Response(responseCode = 404, description = "CRUD package not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse exportCrudPackage(
        @Param(value = "appId") String appId,
        @Param(value = "crudId", required = true) String crudId
    ) {
        LogUtil.info(CLASS_NAME, "Exporting CRUD package: " + crudId + " from app: " + appId);

        try {
            JSONObject crudPackage = crudPackageExtractor.extractCrudPackage(appId, crudId);

            LogUtil.info(CLASS_NAME, "CRUD package exported successfully: " + crudId);
            return new ApiResponse(200, crudPackage.toString());

        } catch (ExportException e) {
            LogUtil.warn(CLASS_NAME, "Export failed for CRUD " + crudId + " in app " + appId + ": " + e.getMessage());
            return buildErrorResponse(e.getStatusCode(), e.getErrorCode(), e.getMessage());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error exporting CRUD " + crudId + " from app " + appId);
            return buildErrorResponse(500, "INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * GET /jw/api/appdefinition/apps/{appId}/cruds
     *
     * Exports all non-metadata CRUD packages from an application.
     * Automatically filters out metadata CRUDs (those based on forms with MD. prefix).
     *
     * Path Parameters:
     * - appId: The application ID (e.g., "farmersPortal")
     *
     * Response Example:
     * {
     *   "appId": "farmersPortal",
     *   "packages": [
     *     {
     *       "packageId": "subsidyApplication_crud",
     *       "category": "Home",
     *       "label": "Subsidy Applications",
     *       "components": { ... }
     *     },
     *     ...
     *   ],
     *   "totalPackages": 12,
     *   "excludedMetadataPackages": 20,
     *   "errorCount": 0
     * }
     */
    @Operation(
        path = "/apps/{appId}/cruds",
        type = Operation.MethodType.GET,
        summary = "Export All CRUD Packages",
        description = "Exports all non-metadata CRUD packages from the specified application"
    )
    @Responses({
        @Response(responseCode = 200, description = "CRUD packages exported successfully"),
        @Response(responseCode = 404, description = "Application not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse exportAllCrudPackages(
        @Param(value = "appId") String appId
    ) {
        LogUtil.info(CLASS_NAME, "Exporting all CRUD packages for application: " + appId);

        try {
            JSONObject crudsData = crudPackageExtractor.extractAllCrudPackages(appId);

            LogUtil.info(CLASS_NAME, "CRUD packages exported successfully for app: " + appId);
            return new ApiResponse(200, crudsData.toString());

        } catch (ExportException e) {
            LogUtil.warn(CLASS_NAME, "Export failed for app " + appId + ": " + e.getMessage());
            return buildErrorResponse(e.getStatusCode(), e.getErrorCode(), e.getMessage());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error exporting CRUDs from app " + appId);
            return buildErrorResponse(500, "INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * GET /jw/api/appdefinition/apps/{appId}/userview
     *
     * Exports the complete userview definition for an application.
     * The userview contains all categories, menus, and UI structure.
     *
     * Path Parameters:
     * - appId: The application ID (e.g., "farmersPortal")
     *
     * Response Example:
     * {
     *   "appId": "farmersPortal",
     *   "definition": {
     *     "className": "org.joget.apps.userview.model.Userview",
     *     "categories": [
     *       {
     *         "className": "org.joget.apps.userview.model.UserviewCategory",
     *         "properties": {
     *           "id": "category-...",
     *           "label": "Home"
     *         },
     *         "menus": [ ... ]
     *       }
     *     ]
     *   }
     * }
     */
    @Operation(
        path = "/apps/{appId}/userview",
        type = Operation.MethodType.GET,
        summary = "Export Userview",
        description = "Exports the complete userview definition for the specified application"
    )
    @Responses({
        @Response(responseCode = 200, description = "Userview exported successfully"),
        @Response(responseCode = 404, description = "Application or userview not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse exportUserview(
        @Param(value = "appId") String appId
    ) {
        LogUtil.info(CLASS_NAME, "Exporting userview for application: " + appId);

        try {
            JSONObject userviewData = userviewExtractor.extractUserview(appId);

            LogUtil.info(CLASS_NAME, "Userview exported successfully for app: " + appId);
            return new ApiResponse(200, userviewData.toString());

        } catch (ExportException e) {
            LogUtil.warn(CLASS_NAME, "Export failed for userview in app " + appId + ": " + e.getMessage());
            return buildErrorResponse(e.getStatusCode(), e.getErrorCode(), e.getMessage());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error exporting userview from app " + appId);
            return buildErrorResponse(500, "INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    // ===== Helper Methods =====

    /**
     * Builds a standardized error response
     */
    private ApiResponse buildErrorResponse(int statusCode, String errorCode, String message) {
        JSONObject error = new JSONObject();
        error.put("code", errorCode);
        error.put("message", message);

        JSONObject response = new JSONObject();
        response.put("status", "error");
        response.put("error", error);

        return new ApiResponse(statusCode, response.toString());
    }
}
