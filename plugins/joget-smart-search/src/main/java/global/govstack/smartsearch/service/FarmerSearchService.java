package global.govstack.smartsearch.service;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Farmer Search Service
 * 
 * Core search logic for the Smart Farmer Search plugin.
 * All search logic is in application layer - database is for indexing only.
 * 
 * Search Strategy:
 * 1. Check for exact match fields (nationalId, phone) → instant result
 * 2. Build parameterized SQL query with filters
 * 3. Execute query, get raw results (up to MAX_DB_RESULTS)
 * 4. Score and rank in application layer
 * 5. Return top MAX_RETURN_RESULTS sorted by relevance
 */
public class FarmerSearchService {

    private static final String CLASS_NAME = FarmerSearchService.class.getName();

    // Search result limits
    private static final int MAX_DB_RESULTS = 50;     // Fetch extra for app-level filtering
    private static final int MAX_RETURN_RESULTS = 20; // Return to client

    // Autocomplete result limits (Issue #19)
    private static final int MAX_AUTOCOMPLETE_RESULTS = 50;      // Villages, cooperatives
    private static final int MAX_CC_AUTOCOMPLETE_RESULTS = 100;  // Community councils (larger list)

    // Scoring constants (Issues #22, #23)
    private static final int EXACT_MATCH_SCORE = 100;  // Exact national ID or phone match
    private static final int BASE_FUZZY_SCORE = 50;    // Base score for fuzzy/criteria matches
    
    // View name (replaces separate index table for zero-latency search)
    private static final String INDEX_TABLE = "v_farmer_search";
    
    // Singleton
    private static FarmerSearchService instance;
    private final FuzzyMatchService fuzzyService;
    
    private FarmerSearchService() {
        this.fuzzyService = FuzzyMatchService.getInstance();
    }
    
    public static synchronized FarmerSearchService getInstance() {
        if (instance == null) {
            instance = new FarmerSearchService();
        }
        return instance;
    }
    
    // =========================================================================
    // SEARCH CRITERIA MODEL
    // =========================================================================
    
    /**
     * Search criteria for farmer lookup
     */
    public static class SearchCriteria {
        private String nationalId;
        private String phone;
        private String name;
        private String districtCode;
        private String districtName;  // For flexible matching
        private String village;
        private String communityCouncil;
        private String partialId;
        private String partialPhone;
        private String cooperative;
        private int limit = MAX_RETURN_RESULTS;
        
        public String getNationalId() { return nationalId; }
        public void setNationalId(String nationalId) { this.nationalId = nationalId; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDistrictCode() { return districtCode; }
        public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }

        public String getDistrictName() { return districtName; }
        public void setDistrictName(String districtName) { this.districtName = districtName; }

        public String getVillage() { return village; }
        public void setVillage(String village) { this.village = village; }
        
        public String getCommunityCouncil() { return communityCouncil; }
        public void setCommunityCouncil(String communityCouncil) { this.communityCouncil = communityCouncil; }
        
        public String getPartialId() { return partialId; }
        public void setPartialId(String partialId) { this.partialId = partialId; }
        
        public String getPartialPhone() { return partialPhone; }
        public void setPartialPhone(String partialPhone) { this.partialPhone = partialPhone; }
        
        public String getCooperative() { return cooperative; }
        public void setCooperative(String cooperative) { this.cooperative = cooperative; }
        
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = Math.min(limit, MAX_RETURN_RESULTS); }
        
        public boolean hasExactMatch() {
            return isNotEmpty(nationalId) || isNotEmpty(phone);
        }
        
        public boolean hasCriteria() {
            return isNotEmpty(nationalId) || isNotEmpty(phone) || isNotEmpty(name) ||
                   isNotEmpty(districtCode) || isNotEmpty(village) || 
                   isNotEmpty(communityCouncil) || isNotEmpty(partialId) || isNotEmpty(partialPhone) ||
                   isNotEmpty(cooperative);
        }
        
        private boolean isNotEmpty(String s) {
            return s != null && !s.trim().isEmpty();
        }
    }
    
    // =========================================================================
    // SEARCH RESULT MODELS
    // =========================================================================
    
    /**
     * Type of search result
     */
    public enum SearchResultType {
        EXACT_ID_MATCH,
        EXACT_PHONE_MATCH,
        CRITERIA_MATCH,
        NO_RESULTS
    }
    
    /**
     * Individual farmer result
     */
    public static class FarmerResult {
        private String id;
        private String nationalId;
        private String nationalIdMasked;
        private String firstName;
        private String lastName;
        private String gender;
        private String dateOfBirth;
        private String phone;
        private String phoneMasked;
        private String districtCode;
        private String districtName;
        private String village;
        private String communityCouncil;
        private String cooperativeName;
        private String sourceRecordId;
        private String soundex;
        private int relevanceScore;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getNationalId() { return nationalId; }
        public void setNationalId(String nationalId) { this.nationalId = nationalId; }
        
        public String getNationalIdMasked() { return nationalIdMasked; }
        public void setNationalIdMasked(String nationalIdMasked) { this.nationalIdMasked = nationalIdMasked; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        
        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        
        public String getPhoneMasked() { return phoneMasked; }
        public void setPhoneMasked(String phoneMasked) { this.phoneMasked = phoneMasked; }
        
        public String getDistrictCode() { return districtCode; }
        public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }
        
        public String getDistrictName() { return districtName; }
        public void setDistrictName(String districtName) { this.districtName = districtName; }
        
        public String getVillage() { return village; }
        public void setVillage(String village) { this.village = village; }
        
        public String getCommunityCouncil() { return communityCouncil; }
        public void setCommunityCouncil(String communityCouncil) { this.communityCouncil = communityCouncil; }
        
        public String getCooperativeName() { return cooperativeName; }
        public void setCooperativeName(String cooperativeName) { this.cooperativeName = cooperativeName; }
        
        public String getSourceRecordId() { return sourceRecordId; }
        public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
        
        public String getSoundex() { return soundex; }
        public void setSoundex(String soundex) { this.soundex = soundex; }
        
        public int getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(int relevanceScore) { this.relevanceScore = relevanceScore; }
    }
    
    /**
     * Search result container
     */
    public static class SearchResult {
        private boolean success = true;
        private SearchResultType resultType = SearchResultType.NO_RESULTS;
        private int totalCount = 0;
        private List<FarmerResult> farmers = new ArrayList<>();
        private long searchTimeMs = 0;
        private String errorMessage;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public SearchResultType getResultType() { return resultType; }
        public void setResultType(SearchResultType resultType) { this.resultType = resultType; }
        
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        
        public List<FarmerResult> getFarmers() { return farmers; }
        public void setFarmers(List<FarmerResult> farmers) { this.farmers = farmers; }
        
        public long getSearchTimeMs() { return searchTimeMs; }
        public void setSearchTimeMs(long searchTimeMs) { this.searchTimeMs = searchTimeMs; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    // =========================================================================
    // MAIN SEARCH METHODS
    // =========================================================================
    
    /**
     * Main search dispatcher
     */
    public SearchResult search(SearchCriteria criteria) {
        long startTime = System.currentTimeMillis();
        SearchResult result = new SearchResult();
        
        try {
            if (criteria == null || !criteria.hasCriteria()) {
                result.setSuccess(false);
                result.setErrorMessage("No search criteria provided");
                result.setResultType(SearchResultType.NO_RESULTS);
                return result;
            }
            
            // Check for exact match fields first
            if (isNotEmpty(criteria.getNationalId())) {
                result = searchByNationalId(criteria.getNationalId());
                if (!result.getFarmers().isEmpty()) {
                    result.setResultType(SearchResultType.EXACT_ID_MATCH);
                }
            } else if (isNotEmpty(criteria.getPhone())) {
                result = searchByPhone(criteria.getPhone());
                if (!result.getFarmers().isEmpty()) {
                    result.setResultType(SearchResultType.EXACT_PHONE_MATCH);
                }
            } else {
                // Criteria-based search
                result = searchByCriteria(criteria);
                if (!result.getFarmers().isEmpty()) {
                    result.setResultType(SearchResultType.CRITERIA_MATCH);
                }
            }
            
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search failed");
            result.setSuccess(false);
            result.setErrorMessage("Search failed: " + e.getMessage());
        }
        
        result.setSearchTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }
    
    /**
     * Exact match by National ID
     */
    public SearchResult searchByNationalId(String nationalId) {
        SearchResult result = new SearchResult();
        
        if (!isNotEmpty(nationalId)) {
            result.setSuccess(false);
            result.setErrorMessage("National ID is required");
            return result;
        }
        
        String sql = "SELECT * FROM " + INDEX_TABLE + " WHERE c_national_id = ?";
        
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, nationalId.trim());
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        FarmerResult farmer = mapResultSetToFarmer(rs);
                        farmer.setRelevanceScore(EXACT_MATCH_SCORE); // Exact match = 100%
                        result.getFarmers().add(farmer);
                    }
                }
            }
            
            result.setTotalCount(result.getFarmers().size());
            result.setResultType(result.getFarmers().isEmpty() ? 
                SearchResultType.NO_RESULTS : SearchResultType.EXACT_ID_MATCH);
                
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search by national ID failed");
            result.setSuccess(false);
            result.setErrorMessage("Database error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Exact match by Phone number
     */
    public SearchResult searchByPhone(String phone) {
        SearchResult result = new SearchResult();
        
        if (!isNotEmpty(phone)) {
            result.setSuccess(false);
            result.setErrorMessage("Phone number is required");
            return result;
        }
        
        // Normalize phone to digits only
        String normalizedPhone = fuzzyService.normalizePhone(phone);
        
        String sql = "SELECT * FROM " + INDEX_TABLE + " WHERE c_phone_normalized = ?";
        
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, normalizedPhone);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        FarmerResult farmer = mapResultSetToFarmer(rs);
                        farmer.setRelevanceScore(EXACT_MATCH_SCORE); // Exact match = 100%
                        result.getFarmers().add(farmer);
                    }
                }
            }
            
            result.setTotalCount(result.getFarmers().size());
            result.setResultType(result.getFarmers().isEmpty() ? 
                SearchResultType.NO_RESULTS : SearchResultType.EXACT_PHONE_MATCH);
                
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search by phone failed");
            result.setSuccess(false);
            result.setErrorMessage("Database error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Search by multiple criteria with fuzzy matching
     */
    public SearchResult searchByCriteria(SearchCriteria criteria) {
        SearchResult result = new SearchResult();
        
        try {
            // Build dynamic query
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM ").append(INDEX_TABLE).append(" WHERE 1=1");
            
            List<Object> params = new ArrayList<>();
            
            // District filter - match code OR name, case-insensitive
            // Supports both district code (e.g., "LEI") and district name (e.g., "Leribe", "leribe")
            if (isNotEmpty(criteria.getDistrictCode()) || isNotEmpty(criteria.getDistrictName())) {
                sql.append(" AND (");
                List<String> districtConditions = new ArrayList<>();

                if (isNotEmpty(criteria.getDistrictCode())) {
                    districtConditions.add("LOWER(c_district_code) = LOWER(?)");
                    params.add(criteria.getDistrictCode().trim());
                    districtConditions.add("LOWER(c_district_name) = LOWER(?)");
                    params.add(criteria.getDistrictCode().trim());
                }
                if (isNotEmpty(criteria.getDistrictName())) {
                    districtConditions.add("LOWER(c_district_code) = LOWER(?)");
                    params.add(criteria.getDistrictName().trim());
                    districtConditions.add("LOWER(c_district_name) = LOWER(?)");
                    params.add(criteria.getDistrictName().trim());
                }

                sql.append(String.join(" OR ", districtConditions));
                sql.append(")");
            }
            
            // Village filter - case-insensitive
            if (isNotEmpty(criteria.getVillage())) {
                sql.append(" AND LOWER(c_village) = LOWER(?)");
                params.add(criteria.getVillage().trim());
            }
            
            // Community council filter
            if (isNotEmpty(criteria.getCommunityCouncil())) {
                sql.append(" AND c_community_council = ?");
                params.add(criteria.getCommunityCouncil().trim());
            }
            
            // Partial ID filter
            if (isNotEmpty(criteria.getPartialId())) {
                sql.append(" AND c_national_id LIKE ?");
                params.add("%" + criteria.getPartialId().trim() + "%");
            }
            
            // Partial phone filter
            if (isNotEmpty(criteria.getPartialPhone())) {
                String normalizedPartial = fuzzyService.normalizePhone(criteria.getPartialPhone());
                sql.append(" AND c_phone_normalized LIKE ?");
                params.add("%" + normalizedPartial + "%");
            }
            
            // Cooperative filter
            if (isNotEmpty(criteria.getCooperative())) {
                sql.append(" AND c_cooperative_name = ?");
                params.add(criteria.getCooperative().trim());
            }
            
            // Name search (fuzzy via LIKE, trigram similarity, and soundex)
            if (isNotEmpty(criteria.getName())) {
                String searchName = fuzzyService.normalizeName(criteria.getName());
                String searchSoundex = generateSearchSoundex(criteria.getName());

                // Use pg_trgm similarity() for fuzzy matching (finds "Tabo" when searching "Thabo")
                // Compare against first_name and last_name separately for better matching
                // Threshold 0.3 = 30% similarity minimum
                sql.append(" AND (c_search_name LIKE ? OR c_name_soundex LIKE ?");
                sql.append(" OR similarity(LOWER(c_first_name), ?) > 0.3");
                sql.append(" OR similarity(LOWER(c_last_name), ?) > 0.3)");
                params.add("%" + searchName + "%");
                params.add("%" + searchSoundex + "%");
                params.add(searchName);
                params.add(searchName);
            }
            
            // Limit results
            sql.append(" LIMIT ?");
            params.add(MAX_DB_RESULTS);
            
            // Execute query
            DataSource ds = getDataSource();
            List<FarmerResult> rawResults = new ArrayList<>();
            
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                
                // Set parameters
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String) {
                        ps.setString(i + 1, (String) param);
                    } else if (param instanceof Integer) {
                        ps.setInt(i + 1, (Integer) param);
                    }
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rawResults.add(mapResultSetToFarmer(rs));
                    }
                }
            }
            
            // Score and rank results in application layer
            List<FarmerResult> scoredResults = scoreAndRank(rawResults, criteria);
            
            // Apply limit
            int limit = Math.min(criteria.getLimit(), MAX_RETURN_RESULTS);
            if (scoredResults.size() > limit) {
                scoredResults = scoredResults.subList(0, limit);
            }
            
            result.setFarmers(scoredResults);
            result.setTotalCount(rawResults.size());
            result.setResultType(scoredResults.isEmpty() ? 
                SearchResultType.NO_RESULTS : SearchResultType.CRITERIA_MATCH);
                
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Search by criteria failed");
            result.setSuccess(false);
            result.setErrorMessage("Database error: " + e.getMessage());
        }
        
        return result;
    }
    
    // =========================================================================
    // SCORING AND RANKING
    // =========================================================================
    
    /**
     * Score and rank results in application layer
     */
    public List<FarmerResult> scoreAndRank(List<FarmerResult> results, SearchCriteria criteria) {
        for (FarmerResult farmer : results) {
            int score = calculateRelevanceScore(farmer, criteria);
            farmer.setRelevanceScore(score);
        }
        
        // Sort by relevance score descending
        results.sort((a, b) -> Integer.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        
        return results;
    }
    
    /**
     * Calculate relevance score for a single farmer result
     */
    private int calculateRelevanceScore(FarmerResult farmer, SearchCriteria criteria) {
        // Name matching score (Issue #22)
        int nameScore = BASE_FUZZY_SCORE;
        
        if (isNotEmpty(criteria.getName())) {
            nameScore = fuzzyService.calculateNameRelevanceScore(
                criteria.getName(),
                farmer.getFirstName(),
                farmer.getLastName(),
                farmer.getSoundex()
            );
        }
        
        // Location matching
        boolean districtMatch = isNotEmpty(criteria.getDistrictCode()) && 
            criteria.getDistrictCode().equalsIgnoreCase(farmer.getDistrictCode());
        boolean villageMatch = isNotEmpty(criteria.getVillage()) && 
            criteria.getVillage().equalsIgnoreCase(farmer.getVillage());
        
        // Combined score
        return fuzzyService.calculateCombinedRelevanceScore(nameScore, districtMatch, villageMatch);
    }
    
    // =========================================================================
    // VILLAGES AUTOCOMPLETE
    // =========================================================================
    
    /**
     * Get villages for autocomplete, optionally filtered by district
     */
    public List<Map<String, Object>> getVillages(String districtCode, String query) {
        List<Map<String, Object>> villages = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c_village, COUNT(*) as farmer_count FROM ").append(INDEX_TABLE);
        sql.append(" WHERE c_village IS NOT NULL AND c_village != ''");
        
        List<Object> params = new ArrayList<>();
        
        if (isNotEmpty(districtCode)) {
            sql.append(" AND c_district_code = ?");
            params.add(districtCode.trim());
        }
        
        if (isNotEmpty(query)) {
            sql.append(" AND c_village LIKE ?");
            params.add(query.trim() + "%");
        }
        
        sql.append(" GROUP BY c_village ORDER BY farmer_count DESC LIMIT " + MAX_AUTOCOMPLETE_RESULTS);
        
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                
                for (int i = 0; i < params.size(); i++) {
                    ps.setString(i + 1, (String) params.get(i));
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> village = new LinkedHashMap<>();
                        village.put("name", rs.getString("c_village"));
                        village.put("count", rs.getInt("farmer_count"));
                        villages.add(village);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get villages failed");
        }
        
        return villages;
    }
    
    /**
     * Get community councils for autocomplete, optionally filtered by district
     */
    public List<Map<String, Object>> getCommunityCouncils(String districtCode) {
        List<Map<String, Object>> councils = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c_community_council, COUNT(*) as farmer_count FROM ").append(INDEX_TABLE);
        sql.append(" WHERE c_community_council IS NOT NULL AND c_community_council != ''");
        
        List<Object> params = new ArrayList<>();
        
        if (isNotEmpty(districtCode)) {
            sql.append(" AND c_district_code = ?");
            params.add(districtCode.trim());
        }
        
        sql.append(" GROUP BY c_community_council ORDER BY c_community_council ASC LIMIT " + MAX_CC_AUTOCOMPLETE_RESULTS);
        
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                
                for (int i = 0; i < params.size(); i++) {
                    ps.setString(i + 1, (String) params.get(i));
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> council = new LinkedHashMap<>();
                        council.put("name", rs.getString("c_community_council"));
                        council.put("count", rs.getInt("farmer_count"));
                        councils.add(council);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get community councils failed");
        }
        
        return councils;
    }
    
    /**
     * Get cooperatives for autocomplete, optionally filtered by district and query
     */
    public List<Map<String, Object>> getCooperatives(String districtCode, String query) {
        List<Map<String, Object>> cooperatives = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c_cooperative_name, COUNT(*) as farmer_count FROM ").append(INDEX_TABLE);
        sql.append(" WHERE c_cooperative_name IS NOT NULL AND c_cooperative_name != ''");
        
        List<Object> params = new ArrayList<>();
        
        if (isNotEmpty(districtCode)) {
            sql.append(" AND c_district_code = ?");
            params.add(districtCode.trim());
        }
        
        if (isNotEmpty(query)) {
            sql.append(" AND c_cooperative_name LIKE ?");
            params.add("%" + query.trim() + "%");
        }
        
        sql.append(" GROUP BY c_cooperative_name ORDER BY farmer_count DESC LIMIT " + MAX_AUTOCOMPLETE_RESULTS);
        
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                
                for (int i = 0; i < params.size(); i++) {
                    ps.setString(i + 1, (String) params.get(i));
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> coop = new LinkedHashMap<>();
                        coop.put("name", rs.getString("c_cooperative_name"));
                        coop.put("count", rs.getInt("farmer_count"));
                        cooperatives.add(coop);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get cooperatives failed");
        }
        
        return cooperatives;
    }
    
    /**
     * Get single farmer by index ID
     */
    public FarmerResult getFarmerById(String id) {
        if (!isNotEmpty(id)) {
            return null;
        }
        
        String sql = "SELECT * FROM " + INDEX_TABLE + " WHERE id = ?";
        
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, id.trim());
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        FarmerResult farmer = mapResultSetToFarmer(rs);
                        farmer.setRelevanceScore(EXACT_MATCH_SCORE);
                        return farmer;
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Get farmer by ID failed");
        }
        
        return null;
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Map ResultSet row to FarmerResult
     */
    private FarmerResult mapResultSetToFarmer(ResultSet rs) throws SQLException {
        FarmerResult farmer = new FarmerResult();
        
        farmer.setId(rs.getString("id"));
        farmer.setNationalId(rs.getString("c_national_id"));
        farmer.setNationalIdMasked(maskNationalId(rs.getString("c_national_id")));
        farmer.setFirstName(rs.getString("c_first_name"));
        farmer.setLastName(rs.getString("c_last_name"));
        farmer.setGender(rs.getString("c_gender"));
        
        java.sql.Date dob = rs.getDate("c_date_of_birth");
        farmer.setDateOfBirth(dob != null ? dob.toString() : null);
        
        farmer.setPhone(rs.getString("c_phone_display"));
        farmer.setPhoneMasked(maskPhone(rs.getString("c_phone_display")));
        farmer.setDistrictCode(rs.getString("c_district_code"));
        farmer.setDistrictName(rs.getString("c_district_name"));
        farmer.setVillage(rs.getString("c_village"));
        farmer.setCommunityCouncil(rs.getString("c_community_council"));
        farmer.setCooperativeName(rs.getString("c_cooperative_name"));
        farmer.setSourceRecordId(rs.getString("c_source_record_id"));
        farmer.setSoundex(rs.getString("c_name_soundex"));
        
        return farmer;
    }
    
    /**
     * Mask national ID for display (show last 4 digits)
     */
    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.length() <= 4) {
            return nationalId;
        }
        return "..." + nationalId.substring(nationalId.length() - 4);
    }
    
    /**
     * Mask phone for display (show last 4 digits)
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return phone;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return phone;
        }
        return "..." + digits.substring(digits.length() - 4);
    }
    
    /**
     * Generate soundex for search query (handle multi-word names)
     */
    private String generateSearchSoundex(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        
        String[] parts = name.trim().split("\\s+");
        StringBuilder soundexBuilder = new StringBuilder();
        for (String part : parts) {
            if (soundexBuilder.length() > 0) {
                soundexBuilder.append(" ");
            }
            soundexBuilder.append(fuzzyService.soundex(part));
        }
        return soundexBuilder.toString();
    }
    
    /**
     * Get Joget DataSource
     */
    private DataSource getDataSource() {
        return (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
    }
    
    /**
     * Check if string is not empty
     */
    private boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
