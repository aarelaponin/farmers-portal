package global.govstack.smartsearch.api;

import global.govstack.smartsearch.service.FarmerSearchService;
import global.govstack.smartsearch.service.FarmerSearchService.*;
import global.govstack.smartsearch.service.StatisticsService;
import global.govstack.smartsearch.service.StatisticsService.Statistics;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiDefinition;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Smart Farmer Search REST API Plugin
 * 
 * Provides REST API endpoints for farmer search:
 * - POST /search - Main search endpoint
 * - GET /lookup/{id} - Single farmer lookup by index ID
 * - GET /villages - Villages autocomplete (filtered by district)
 * 
 * Uses API Builder plugin architecture.
 */
public class SmartSearchApiPlugin extends ApiPluginAbstract {

    private static final String CLASS_NAME = SmartSearchApiPlugin.class.getName();
    
    private final FarmerSearchService searchService;
    private final StatisticsService statisticsService;
    
    public SmartSearchApiPlugin() {
        this.searchService = FarmerSearchService.getInstance();
        this.statisticsService = StatisticsService.getInstance();
    }
    
    // =========================================================================
    // PLUGIN METADATA
    // =========================================================================
    
    @Override
    public String getName() {
        return "SmartSearchAPI";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Smart Farmer Search API - Progressive criteria-based farmer lookup";
    }

    @Override
    public String getLabel() {
        return "Smart Farmer Search API";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/SmartSearchApiPlugin.json",
            null, true, null
        );
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-search\"></i>";
    }

    @Override
    public String getTag() {
        return "fss";
    }
    
    @Override
    public String getTagDesc() {
        return "Farmer Smart Search";
    }
    
    // =========================================================================
    // API ENDPOINTS
    // =========================================================================
    
    /**
     * POST /search - Main search endpoint
     */
    @Operation(
        path = "/search",
        type = Operation.MethodType.POST,
        summary = "Search for farmers",
        description = "Search for farmers using various criteria including name, ID, phone, district, and village"
    )
    @Responses({
        @Response(responseCode = 200, description = "Search results returned successfully"),
        @Response(responseCode = 400, description = "Invalid search criteria"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse search(
            @Param(value = "body", description = "Search criteria") JSONObject body) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse search criteria from request body
            SearchCriteria criteria = parseSearchCriteria(body);
            
            if (!criteria.hasCriteria()) {
                return errorResponse(400, "No search criteria provided");
            }
            
            // Execute search
            SearchResult result = searchService.search(criteria);
            
            // Format response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            response.put("resultType", result.getResultType().toString());
            response.put("totalCount", result.getTotalCount());
            
            // Format farmer results
            List<Map<String, Object>> farmers = new ArrayList<>();
            for (FarmerResult farmer : result.getFarmers()) {
                farmers.add(formatFarmer(farmer));
            }
            response.put("farmers", farmers);
            response.put("searchTime", System.currentTimeMillis() - startTime);
            
            // Add suggestions for no results
            if (result.getFarmers().isEmpty()) {
                response.put("suggestions", Arrays.asList(
                    "Try a broader search term",
                    "Check the spelling of the name",
                    "Try a different village",
                    "Search by National ID or Phone instead"
                ));
            }
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search failed");
            return errorResponse(500, "Search failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /lookup/{id} - Lookup single farmer by index ID
     */
    @Operation(
        path = "/lookup/{id}",
        type = Operation.MethodType.GET,
        summary = "Get farmer by ID",
        description = "Retrieve a single farmer record by their index ID"
    )
    @Responses({
        @Response(responseCode = 200, description = "Farmer found"),
        @Response(responseCode = 404, description = "Farmer not found"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse lookup(
            @Param(value = "id", description = "Farmer index ID") String id) {
        
        try {
            if (id == null || id.trim().isEmpty()) {
                return errorResponse(400, "Farmer ID is required");
            }
            
            FarmerResult farmer = searchService.getFarmerById(id);
            
            if (farmer == null) {
                return errorResponse(404, "Farmer not found");
            }
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("farmer", formatFarmer(farmer));
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Lookup failed");
            return errorResponse(500, "Lookup failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /villages - Villages autocomplete
     */
    @Operation(
        path = "/villages",
        type = Operation.MethodType.GET,
        summary = "Get villages list",
        description = "Get list of villages for autocomplete, optionally filtered by district"
    )
    @Responses({
        @Response(responseCode = 200, description = "Villages list returned"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse villages(
            @Param(value = "district", required = false, description = "District code filter") String district,
            @Param(value = "q", required = false, description = "Search query") String query) {
        
        try {
            List<Map<String, Object>> villages = searchService.getVillages(district, query);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("villages", villages);
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get villages failed");
            return errorResponse(500, "Failed to get villages: " + e.getMessage());
        }
    }
    
    /**
     * GET /community-councils - Community councils autocomplete
     */
    @Operation(
        path = "/community-councils",
        type = Operation.MethodType.GET,
        summary = "Get community councils list",
        description = "Get list of community councils for autocomplete, optionally filtered by district"
    )
    @Responses({
        @Response(responseCode = 200, description = "Community councils list returned"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse communityCouncils(
            @Param(value = "district", required = false, description = "District code filter") String district) {
        
        try {
            List<Map<String, Object>> councils = searchService.getCommunityCouncils(district);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("councils", councils);
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get community councils failed");
            return errorResponse(500, "Failed to get community councils: " + e.getMessage());
        }
    }
    
    /**
     * GET /cooperatives - Cooperatives autocomplete
     */
    @Operation(
        path = "/cooperatives",
        type = Operation.MethodType.GET,
        summary = "Get cooperatives list",
        description = "Get list of cooperatives for autocomplete, optionally filtered by district and search query"
    )
    @Responses({
        @Response(responseCode = 200, description = "Cooperatives list returned"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse cooperatives(
            @Param(value = "district", required = false, description = "District code filter") String district,
            @Param(value = "q", required = false, description = "Search query") String query) {
        
        try {
            List<Map<String, Object>> cooperatives = searchService.getCooperatives(district, query);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("cooperatives", cooperatives);
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get cooperatives failed");
            return errorResponse(500, "Failed to get cooperatives: " + e.getMessage());
        }
    }
    
    /**
     * GET /search/byNationalId/{nationalId} - Search by national ID
     */
    @Operation(
        path = "/search/byNationalId/{nationalId}",
        type = Operation.MethodType.GET,
        summary = "Search by National ID",
        description = "Find farmer by exact national ID match"
    )
    @Responses({
        @Response(responseCode = 200, description = "Search results returned"),
        @Response(responseCode = 404, description = "Farmer not found"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse searchByNationalId(
            @Param(value = "nationalId", description = "National ID to search") String nationalId) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            if (nationalId == null || nationalId.trim().isEmpty()) {
                return errorResponse(400, "National ID is required");
            }
            
            SearchResult result = searchService.searchByNationalId(nationalId);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            response.put("resultType", result.getResultType().toString());
            response.put("totalCount", result.getTotalCount());
            
            List<Map<String, Object>> farmers = new ArrayList<>();
            for (FarmerResult farmer : result.getFarmers()) {
                farmers.add(formatFarmer(farmer));
            }
            response.put("farmers", farmers);
            response.put("searchTime", System.currentTimeMillis() - startTime);
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search by national ID failed");
            return errorResponse(500, "Search failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /search/byPhone/{phone} - Search by phone number
     */
    @Operation(
        path = "/search/byPhone/{phone}",
        type = Operation.MethodType.GET,
        summary = "Search by Phone",
        description = "Find farmer by phone number"
    )
    @Responses({
        @Response(responseCode = 200, description = "Search results returned"),
        @Response(responseCode = 404, description = "Farmer not found"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse searchByPhone(
            @Param(value = "phone", description = "Phone number to search") String phone) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            if (phone == null || phone.trim().isEmpty()) {
                return errorResponse(400, "Phone number is required");
            }
            
            SearchResult result = searchService.searchByPhone(phone);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            response.put("resultType", result.getResultType().toString());
            response.put("totalCount", result.getTotalCount());
            
            List<Map<String, Object>> farmers = new ArrayList<>();
            for (FarmerResult farmer : result.getFarmers()) {
                farmers.add(formatFarmer(farmer));
            }
            response.put("farmers", farmers);
            response.put("searchTime", System.currentTimeMillis() - startTime);
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search by phone failed");
            return errorResponse(500, "Search failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /statistics - Get search statistics for confidence calculation
     */
    @Operation(
        path = "/statistics",
        type = Operation.MethodType.GET,
        summary = "Get search statistics",
        description = "Returns statistics for client-side confidence calculation including name frequencies and effectiveness factors"
    )
    @Responses({
        @Response(responseCode = 200, description = "Statistics returned successfully"),
        @Response(responseCode = 500, description = "Internal server error")
    })
    public ApiResponse statistics(
            @Param(value = "refresh", required = false, description = "Force refresh statistics (bypass cache)") String refresh) {
        
        try {
            Statistics stats;
            
            // Check if refresh is requested
            if ("true".equalsIgnoreCase(refresh)) {
                LogUtil.info(CLASS_NAME, "Forcing statistics refresh...");
                stats = statisticsService.refreshStatistics();
            } else {
                stats = statisticsService.getStatistics();
            }
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("statistics", stats.toJson());
            response.put("cache_age_ms", statisticsService.getCacheAgeMs());
            response.put("is_stale", statisticsService.isStale());
            
            return new ApiResponse(200, new JSONObject(response));
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get statistics failed");
            return errorResponse(500, "Failed to get statistics: " + e.getMessage());
        }
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Parse search criteria from JSON request body
     */
    private SearchCriteria parseSearchCriteria(JSONObject body) {
        SearchCriteria criteria = new SearchCriteria();
        
        if (body == null) {
            return criteria;
        }
        
        // Check for criteria object or direct properties
        JSONObject criteriaObj = body.optJSONObject("criteria");
        if (criteriaObj == null) {
            criteriaObj = body;
        }
        
        criteria.setNationalId(criteriaObj.optString("nationalId", null));
        criteria.setPhone(criteriaObj.optString("phone", null));
        criteria.setName(criteriaObj.optString("name", null));
        criteria.setDistrictCode(criteriaObj.optString("districtCode", null));
        criteria.setDistrictName(criteriaObj.optString("districtName", null));  // For flexible matching
        criteria.setVillage(criteriaObj.optString("village", null));
        criteria.setCommunityCouncil(criteriaObj.optString("communityCouncil", null));
        criteria.setPartialId(criteriaObj.optString("partialId", null));
        criteria.setPartialPhone(criteriaObj.optString("partialPhone", null));
        criteria.setCooperative(criteriaObj.optString("cooperative", null));
        
        int limit = body.optInt("limit", 20);
        criteria.setLimit(limit);
        
        return criteria;
    }
    
    /**
     * Format farmer result for JSON response
     */
    private Map<String, Object> formatFarmer(FarmerResult farmer) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("id", farmer.getId());
        map.put("nationalId", farmer.getNationalId());              // Full value for storing
        map.put("nationalIdMasked", farmer.getNationalIdMasked());  // Masked for display
        map.put("firstName", farmer.getFirstName());
        map.put("lastName", farmer.getLastName());
        map.put("gender", farmer.getGender());
        map.put("dateOfBirth", farmer.getDateOfBirth());
        map.put("phone", farmer.getPhone());                        // Full value for storing
        map.put("phoneMasked", farmer.getPhoneMasked());            // Masked for display
        map.put("districtCode", farmer.getDistrictCode());
        map.put("districtName", farmer.getDistrictName());
        map.put("village", farmer.getVillage());
        map.put("communityCouncil", farmer.getCommunityCouncil());
        map.put("cooperativeName", farmer.getCooperativeName());
        map.put("sourceRecordId", farmer.getSourceRecordId());
        map.put("relevanceScore", farmer.getRelevanceScore());

        return map;
    }
    
    /**
     * Create error response
     */
    private ApiResponse errorResponse(int status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("error", message);
        return new ApiResponse(status, new JSONObject(response));
    }
    
    @Override
    public Map<String, ApiDefinition> getDefinitions() {
        // API definitions for Swagger documentation
        Map<String, ApiDefinition> defs = new HashMap<>();
        
        // Could add model definitions here if needed
        
        return defs;
    }
}
