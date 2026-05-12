package global.govstack.smartsearch.service;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Statistics Service
 * 
 * Generates and caches statistics for client-side confidence estimation.
 * Statistics include:
 * - Total farmer count
 * - District distribution
 * - Surname and firstname frequency
 * - Village average size
 * - Effectiveness factors
 * 
 * Statistics are cached in memory with a 24-hour TTL.
 */
public class StatisticsService {

    private static final String CLASS_NAME = StatisticsService.class.getName();
    
    // Cache TTL: 24 hours in milliseconds
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000;
    
    // View name for farmer search
    private static final String INDEX_TABLE = "v_farmer_search";
    
    // Top N names to include in frequency maps
    private static final int TOP_NAME_COUNT = 100;
    
    // Default frequency for names not in top list
    private static final double DEFAULT_SURNAME_FREQUENCY = 0.0002;
    private static final double DEFAULT_FIRSTNAME_FREQUENCY = 0.0003;
    
    // Singleton instance
    private static StatisticsService instance;
    
    // Cached statistics
    private Statistics cachedStatistics;
    private long cacheTimestamp = 0;
    
    // Lock for thread safety
    private final Object cacheLock = new Object();
    
    private StatisticsService() {
        // Private constructor for singleton
    }
    
    public static synchronized StatisticsService getInstance() {
        if (instance == null) {
            instance = new StatisticsService();
        }
        return instance;
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Get current statistics (from cache or freshly computed if stale)
     * 
     * @return Statistics object
     */
    public Statistics getStatistics() {
        synchronized (cacheLock) {
            if (cachedStatistics == null || isStale()) {
                LogUtil.info(CLASS_NAME, "Statistics cache miss or stale, refreshing...");
                refreshStatistics();
            }
            return cachedStatistics;
        }
    }
    
    /**
     * Force refresh statistics (bypass cache)
     * 
     * @return Freshly computed Statistics object
     */
    public Statistics refreshStatistics() {
        synchronized (cacheLock) {
            try {
                long startTime = System.currentTimeMillis();
                LogUtil.info(CLASS_NAME, "Starting statistics computation...");
                
                Statistics stats = computeStatistics();
                
                cachedStatistics = stats;
                cacheTimestamp = System.currentTimeMillis();
                
                long elapsed = System.currentTimeMillis() - startTime;
                LogUtil.info(CLASS_NAME, "Statistics computed in " + elapsed + "ms");
                
                return stats;
                
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Failed to compute statistics");
                
                // Return a default statistics object if computation fails
                if (cachedStatistics == null) {
                    cachedStatistics = createDefaultStatistics();
                }
                return cachedStatistics;
            }
        }
    }
    
    /**
     * Check if cached statistics are stale (older than TTL)
     * 
     * @return true if stale
     */
    public boolean isStale() {
        return (System.currentTimeMillis() - cacheTimestamp) > CACHE_TTL_MS;
    }
    
    /**
     * Get cache age in milliseconds
     * 
     * @return Age of cache in ms, or -1 if never computed
     */
    public long getCacheAgeMs() {
        if (cacheTimestamp == 0) {
            return -1;
        }
        return System.currentTimeMillis() - cacheTimestamp;
    }
    
    // =========================================================================
    // STATISTICS COMPUTATION
    // =========================================================================
    
    /**
     * Compute all statistics from the database
     */
    private Statistics computeStatistics() throws Exception {
        Statistics stats = new Statistics();
        stats.setVersion("1.0");
        stats.setGeneratedAt(new Date());
        
        DataSource ds = getDataSource();
        
        try (Connection conn = ds.getConnection()) {
            // Total farmers
            int totalFarmers = computeTotalFarmers(conn);
            stats.setTotalFarmers(totalFarmers);
            
            if (totalFarmers == 0) {
                LogUtil.warn(CLASS_NAME, "No farmers in database, returning default statistics");
                return createDefaultStatistics();
            }
            
            // District counts
            Map<String, Integer> districtCounts = computeDistrictCounts(conn);
            stats.setDistrictCounts(districtCounts);
            
            // Surname frequency
            Map<String, Double> surnameFreq = computeNameFrequency(conn, "c_last_name", totalFarmers);
            surnameFreq.put("_default", DEFAULT_SURNAME_FREQUENCY);
            stats.setSurnameFrequency(surnameFreq);
            
            // Firstname frequency
            Map<String, Double> firstnameFreq = computeNameFrequency(conn, "c_first_name", totalFarmers);
            firstnameFreq.put("_default", DEFAULT_FIRSTNAME_FREQUENCY);
            stats.setFirstnameFrequency(firstnameFreq);
            
            // Village average size
            int villageAvgSize = computeVillageAverageSize(conn);
            stats.setVillageAvgSize(villageAvgSize);
            
            // Effectiveness factors (pre-computed estimates)
            Map<String, Double> factors = computeEffectivenessFactors(conn, totalFarmers, districtCounts, villageAvgSize);
            stats.setEffectivenessFactors(factors);
        }
        
        return stats;
    }
    
    /**
     * Compute total farmer count
     */
    private int computeTotalFarmers(Connection conn) throws Exception {
        String sql = "SELECT COUNT(*) AS total_farmers FROM " + INDEX_TABLE;
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total_farmers");
            }
        }
        return 0;
    }
    
    /**
     * Compute farmer counts per district
     */
    private Map<String, Integer> computeDistrictCounts(Connection conn) throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();
        
        String sql = "SELECT c_district_code, COUNT(*) AS count " +
                     "FROM " + INDEX_TABLE + " " +
                     "WHERE c_district_code IS NOT NULL AND c_district_code != '' " +
                     "GROUP BY c_district_code " +
                     "ORDER BY count DESC";
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("c_district_code");
                int count = rs.getInt("count");
                counts.put(code, count);
            }
        }
        
        return counts;
    }
    
    /**
     * Compute name frequency (for surnames or first names)
     * 
     * @param conn Database connection
     * @param columnName Column to analyze (c_last_name or c_first_name)
     * @param totalFarmers Total farmer count for frequency calculation
     * @return Map of name -> frequency (0.0 to 1.0)
     */
    private Map<String, Double> computeNameFrequency(Connection conn, String columnName, int totalFarmers) throws Exception {
        Map<String, Double> frequencies = new LinkedHashMap<>();
        
        String sql = "SELECT LOWER(" + columnName + ") AS name, COUNT(*) AS count " +
                     "FROM " + INDEX_TABLE + " " +
                     "WHERE " + columnName + " IS NOT NULL AND " + columnName + " != '' " +
                     "GROUP BY LOWER(" + columnName + ") " +
                     "ORDER BY count DESC " +
                     "LIMIT ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, TOP_NAME_COUNT);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    int count = rs.getInt("count");
                    double frequency = (double) count / totalFarmers;
                    frequencies.put(name, Math.round(frequency * 10000) / 10000.0); // 4 decimal places
                }
            }
        }
        
        return frequencies;
    }
    
    /**
     * Compute average village size
     */
    private int computeVillageAverageSize(Connection conn) throws Exception {
        String sql = "SELECT AVG(village_count) AS avg_size FROM (" +
                     "  SELECT c_village, COUNT(*) AS village_count " +
                     "  FROM " + INDEX_TABLE + " " +
                     "  WHERE c_village IS NOT NULL AND c_village != '' " +
                     "  GROUP BY c_village" +
                     ") AS village_sizes";
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double avg = rs.getDouble("avg_size");
                return (int) Math.round(avg);
            }
        }
        
        return 100; // Default
    }
    
    /**
     * Compute effectiveness factors for confidence calculation
     * 
     * These are estimates of how much each filter narrows down the results.
     * A factor of 0.85 means the filter removes 85% of candidates.
     */
    private Map<String, Double> computeEffectivenessFactors(Connection conn, 
                                                            int totalFarmers, 
                                                            Map<String, Integer> districtCounts,
                                                            int villageAvgSize) throws Exception {
        Map<String, Double> factors = new LinkedHashMap<>();
        
        // Village effectiveness: based on average village size
        // If average village has 100 farmers out of 200k total, village filter effectiveness = 1 - (100/200000) ≈ 0.9995
        // But we use a simpler estimate: village typically narrows to ~150 farmers (0.85 effectiveness)
        factors.put("village", 0.85);
        
        // District effectiveness: based on average district size
        if (!districtCounts.isEmpty()) {
            int avgDistrictSize = districtCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum() / districtCounts.size();
            double districtEffectiveness = 1.0 - ((double) avgDistrictSize / Math.max(totalFarmers, 1));
            factors.put("district", Math.round(districtEffectiveness * 100) / 100.0);
        } else {
            factors.put("district", 0.12); // Default: 10 districts, so ~10% of data
        }
        
        // Partial ID (4 digits): Very effective - usually unique or near-unique
        factors.put("partial_id_4", 0.92);
        
        // Partial phone (4 digits): Very effective
        factors.put("partial_phone_4", 0.90);
        
        // Community council: Typically 20-30 per district
        factors.put("community_council", 0.55);
        
        // Cooperative: Variable, ~45% reduction
        factors.put("cooperative", 0.45);
        
        return factors;
    }
    
    /**
     * Create default statistics when database is empty or error occurs
     */
    private Statistics createDefaultStatistics() {
        Statistics stats = new Statistics();
        stats.setVersion("1.0");
        stats.setGeneratedAt(new Date());
        stats.setTotalFarmers(0);
        stats.setDistrictCounts(new LinkedHashMap<>());
        
        // Default surname frequencies (common Lesotho names)
        Map<String, Double> surnameFreq = new LinkedHashMap<>();
        surnameFreq.put("mohapi", 0.02);
        surnameFreq.put("sello", 0.019);
        surnameFreq.put("mohale", 0.015);
        surnameFreq.put("mokoena", 0.014);
        surnameFreq.put("letsie", 0.013);
        surnameFreq.put("_default", DEFAULT_SURNAME_FREQUENCY);
        stats.setSurnameFrequency(surnameFreq);
        
        // Default firstname frequencies
        Map<String, Double> firstnameFreq = new LinkedHashMap<>();
        firstnameFreq.put("thabo", 0.025);
        firstnameFreq.put("lerato", 0.02);
        firstnameFreq.put("mamosa", 0.015);
        firstnameFreq.put("nthabiseng", 0.012);
        firstnameFreq.put("_default", DEFAULT_FIRSTNAME_FREQUENCY);
        stats.setFirstnameFrequency(firstnameFreq);
        
        stats.setVillageAvgSize(100);
        
        // Default effectiveness factors
        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("village", 0.85);
        factors.put("district", 0.12);
        factors.put("partial_id_4", 0.92);
        factors.put("partial_phone_4", 0.90);
        factors.put("community_council", 0.55);
        factors.put("cooperative", 0.45);
        stats.setEffectivenessFactors(factors);
        
        return stats;
    }
    
    /**
     * Get Joget DataSource
     */
    private DataSource getDataSource() {
        return (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
    }
    
    // =========================================================================
    // STATISTICS MODEL CLASS
    // =========================================================================
    
    /**
     * Statistics data model
     */
    public static class Statistics {
        private String version;
        private Date generatedAt;
        private int totalFarmers;
        private Map<String, Double> surnameFrequency;
        private Map<String, Double> firstnameFrequency;
        private Map<String, Integer> districtCounts;
        private int villageAvgSize;
        private Map<String, Double> effectivenessFactors;
        
        // Getters and setters
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public Date getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Date generatedAt) { this.generatedAt = generatedAt; }
        
        public int getTotalFarmers() { return totalFarmers; }
        public void setTotalFarmers(int totalFarmers) { this.totalFarmers = totalFarmers; }
        
        public Map<String, Double> getSurnameFrequency() { return surnameFrequency; }
        public void setSurnameFrequency(Map<String, Double> surnameFrequency) { this.surnameFrequency = surnameFrequency; }
        
        public Map<String, Double> getFirstnameFrequency() { return firstnameFrequency; }
        public void setFirstnameFrequency(Map<String, Double> firstnameFrequency) { this.firstnameFrequency = firstnameFrequency; }
        
        public Map<String, Integer> getDistrictCounts() { return districtCounts; }
        public void setDistrictCounts(Map<String, Integer> districtCounts) { this.districtCounts = districtCounts; }
        
        public int getVillageAvgSize() { return villageAvgSize; }
        public void setVillageAvgSize(int villageAvgSize) { this.villageAvgSize = villageAvgSize; }
        
        public Map<String, Double> getEffectivenessFactors() { return effectivenessFactors; }
        public void setEffectivenessFactors(Map<String, Double> effectivenessFactors) { this.effectivenessFactors = effectivenessFactors; }
        
        /**
         * Convert to JSON for API response
         */
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            
            json.put("version", version != null ? version : "1.0");
            json.put("generated_at", generatedAt != null ? 
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(generatedAt) : null);
            json.put("total_farmers", totalFarmers);
            json.put("surname_frequency", surnameFrequency != null ? new JSONObject(surnameFrequency) : new JSONObject());
            json.put("firstname_frequency", firstnameFrequency != null ? new JSONObject(firstnameFrequency) : new JSONObject());
            json.put("district_counts", districtCounts != null ? new JSONObject(districtCounts) : new JSONObject());
            json.put("village_avg_size", villageAvgSize);
            json.put("effectiveness_factors", effectivenessFactors != null ? new JSONObject(effectivenessFactors) : new JSONObject());
            
            return json;
        }
    }
}
