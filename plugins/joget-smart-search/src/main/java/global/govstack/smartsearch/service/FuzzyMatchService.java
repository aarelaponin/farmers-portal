package global.govstack.smartsearch.service;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.joget.commons.util.LogUtil;

/**
 * Fuzzy Match Service
 * 
 * Provides fuzzy matching capabilities for name searches:
 * - Levenshtein distance calculation for edit distance
 * - Soundex phonetic matching for similar-sounding names
 * - Combined relevance scoring
 * 
 * All matching logic is in application layer, not database.
 */
public class FuzzyMatchService {

    private static final String CLASS_NAME = FuzzyMatchService.class.getName();
    
    // Apache Commons Text Levenshtein implementation
    private final LevenshteinDistance levenshtein;
    
    // Singleton instance
    private static FuzzyMatchService instance;
    
    private FuzzyMatchService() {
        this.levenshtein = new LevenshteinDistance();
    }
    
    public static synchronized FuzzyMatchService getInstance() {
        if (instance == null) {
            instance = new FuzzyMatchService();
        }
        return instance;
    }
    
    // =========================================================================
    // LEVENSHTEIN DISTANCE
    // =========================================================================
    
    /**
     * Calculate Levenshtein (edit) distance between two strings.
     * Lower values indicate more similar strings.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance (0 = exact match)
     */
    public int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        return levenshtein.apply(s1.toLowerCase().trim(), s2.toLowerCase().trim());
    }
    
    /**
     * Calculate normalized Levenshtein similarity (0.0 to 1.0).
     * Higher values indicate more similar strings.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score (1.0 = exact match)
     */
    public double levenshteinSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        String str1 = s1.toLowerCase().trim();
        String str2 = s2.toLowerCase().trim();
        
        int maxLen = Math.max(str1.length(), str2.length());
        if (maxLen == 0) {
            return 1.0; // Both empty strings
        }
        
        int distance = levenshtein.apply(str1, str2);
        return 1.0 - ((double) distance / maxLen);
    }
    
    // =========================================================================
    // SOUNDEX PHONETIC MATCHING
    // =========================================================================
    
    /**
     * Generate Soundex code for a string.
     * Soundex encodes words by their phonetic sound.
     * 
     * Standard Soundex algorithm:
     * 1. Keep first letter
     * 2. Replace consonants with digits:
     *    B,F,P,V = 1; C,G,J,K,Q,S,X,Z = 2; D,T = 3; L = 4; M,N = 5; R = 6
     * 3. Remove vowels (A,E,I,O,U) and H,W,Y
     * 4. Remove consecutive duplicates
     * 5. Pad/truncate to 4 characters
     * 
     * @param s Input string
     * @return 4-character Soundex code
     */
    public String soundex(String s) {
        if (s == null || s.isEmpty()) {
            return "0000";
        }
        
        String str = s.toUpperCase().trim();
        if (str.isEmpty()) {
            return "0000";
        }
        
        // Keep first letter
        StringBuilder result = new StringBuilder();
        char firstLetter = str.charAt(0);
        result.append(firstLetter);
        
        // Map consonants to digits
        char lastCode = soundexCode(firstLetter);
        
        for (int i = 1; i < str.length() && result.length() < 4; i++) {
            char c = str.charAt(i);
            char code = soundexCode(c);
            
            // Skip if same as previous code or if it's a vowel/skip char (0)
            if (code != '0' && code != lastCode) {
                result.append(code);
            }
            
            // Track last code for duplicate removal (even if we didn't append)
            if (code != '0') {
                lastCode = code;
            }
        }
        
        // Pad with zeros
        while (result.length() < 4) {
            result.append('0');
        }
        
        return result.toString();
    }
    
    /**
     * Get Soundex digit for a character
     */
    private char soundexCode(char c) {
        switch (c) {
            case 'B': case 'F': case 'P': case 'V':
                return '1';
            case 'C': case 'G': case 'J': case 'K': case 'Q': case 'S': case 'X': case 'Z':
                return '2';
            case 'D': case 'T':
                return '3';
            case 'L':
                return '4';
            case 'M': case 'N':
                return '5';
            case 'R':
                return '6';
            default:
                // Vowels (A,E,I,O,U) and H,W,Y are skipped
                return '0';
        }
    }
    
    /**
     * Check if two strings have matching Soundex codes (sound similar)
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return true if phonetically similar
     */
    public boolean soundexMatch(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return false;
        }
        return soundex(s1).equals(soundex(s2));
    }
    
    /**
     * Generate Soundex codes for full name (first + last)
     * 
     * @param firstName First name
     * @param lastName Last name
     * @return Combined soundex codes separated by space
     */
    public String generateFullNameSoundex(String firstName, String lastName) {
        String firstSoundex = soundex(firstName);
        String lastSoundex = soundex(lastName);
        return firstSoundex + " " + lastSoundex;
    }
    
    // =========================================================================
    // COMBINED RELEVANCE SCORING
    // =========================================================================
    
    /**
     * Calculate combined relevance score for a name match.
     * 
     * Scoring algorithm:
     * - Base score: 50
     * - Exact match: +50 (total 100)
     * - Levenshtein penalty: -5 per edit distance
     * - Soundex match bonus: +15
     * - Prefix match bonus: +20
     * 
     * @param searchName The search query name
     * @param candidateFirstName Candidate first name
     * @param candidateLastName Candidate last name
     * @param candidateSoundex Pre-computed soundex of candidate
     * @return Relevance score (0-100)
     */
    public int calculateNameRelevanceScore(String searchName, 
                                           String candidateFirstName,
                                           String candidateLastName,
                                           String candidateSoundex) {
        if (searchName == null || searchName.trim().isEmpty()) {
            return 50; // Neutral score for no name search
        }
        
        String search = searchName.toLowerCase().trim();
        String fullName = ((candidateFirstName != null ? candidateFirstName : "") + " " +
                          (candidateLastName != null ? candidateLastName : "")).toLowerCase().trim();
        
        int score = 50; // Base score
        
        // Exact match bonus
        if (fullName.equals(search)) {
            return 100; // Perfect match
        }
        
        // Check each part of the search against first and last name
        String[] searchParts = search.split("\\s+");
        String first = candidateFirstName != null ? candidateFirstName.toLowerCase() : "";
        String last = candidateLastName != null ? candidateLastName.toLowerCase() : "";
        
        for (String part : searchParts) {
            // Prefix match bonus
            if (first.startsWith(part) || last.startsWith(part)) {
                score += 20;
            }
            
            // Levenshtein penalty
            int firstDist = levenshteinDistance(part, first);
            int lastDist = levenshteinDistance(part, last);
            int minDist = Math.min(firstDist, lastDist);
            score -= minDist * 5;
            
            // Soundex match bonus
            String partSoundex = soundex(part);
            if (candidateSoundex != null && candidateSoundex.contains(partSoundex)) {
                score += 15;
            }
        }
        
        // Clamp to valid range
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Calculate overall relevance score including location matches.
     * 
     * @param nameScore Name matching score (0-100)
     * @param districtMatch Whether district matches
     * @param villageMatch Whether village matches
     * @return Combined relevance score (0-100)
     */
    public int calculateCombinedRelevanceScore(int nameScore, 
                                               boolean districtMatch, 
                                               boolean villageMatch) {
        int score = nameScore;
        
        // Location bonuses
        if (villageMatch) {
            score += 10;
        }
        if (districtMatch) {
            score += 5;
        }
        
        // Clamp to valid range
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Check if a string starts with a prefix (case-insensitive)
     */
    public boolean startsWithIgnoreCase(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        return str.toLowerCase().startsWith(prefix.toLowerCase());
    }
    
    /**
     * Normalize a phone number to digits only
     */
    public String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }
    
    /**
     * Normalize a name for search (lowercase, trim, collapse whitespace)
     */
    public String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase().trim().replaceAll("\\s+", " ");
    }
}
