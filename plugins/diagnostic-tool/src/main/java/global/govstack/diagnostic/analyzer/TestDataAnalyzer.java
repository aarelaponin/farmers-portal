package global.govstack.diagnostic.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.*;

/**
 * Analyzes test data JSON structure
 */
public class TestDataAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestDataStructure analyzeTestData(File testDataFile) throws Exception {
        if (!testDataFile.exists()) {
            throw new IllegalArgumentException("Test data file not found: " + testDataFile.getPath());
        }

        TestDataStructure structure = new TestDataStructure();
        JsonNode root = objectMapper.readTree(testDataFile);

        // Handle both wrapped and unwrapped formats
        JsonNode dataNode = root;
        if (root.has("testData") && root.get("testData").isArray()) {
            dataNode = root.get("testData").get(0);
        }

        // Extract all field paths
        extractFieldPaths(dataNode, "", structure.getFieldPaths());

        // Extract specific structures
        analyzeExtensionStructure(dataNode.path("extension"), structure);

        return structure;
    }

    private void extractFieldPaths(JsonNode node, String prefix, Set<String> paths) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldPath = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                JsonNode value = field.getValue();

                if (value.isValueNode()) {
                    paths.add(fieldPath);
                } else if (value.isObject()) {
                    extractFieldPaths(value, fieldPath, paths);
                } else if (value.isArray() && value.size() > 0) {
                    JsonNode firstElement = value.get(0);
                    if (firstElement.isObject()) {
                        extractFieldPaths(firstElement, fieldPath + "[0]", paths);
                    } else {
                        paths.add(fieldPath);
                    }
                }
            }
        }
    }

    private void analyzeExtensionStructure(JsonNode extension, TestDataStructure structure) {
        if (extension == null || extension.isNull()) {
            return;
        }

        // Check for agricultural activities
        JsonNode agActivities = extension.path("agriculturalActivities");
        if (!agActivities.isMissingNode()) {
            structure.addFoundStructure("agriculturalActivities", extractFieldNames(agActivities));

            // Check specific problematic fields
            if (agActivities.has("cropProduction")) {
                structure.addIssue("Found 'cropProduction' instead of 'engagedInCropProduction'");
            }
            if (agActivities.has("livestockProduction")) {
                structure.addIssue("Found 'livestockProduction' instead of 'engagedInLivestockProduction'");
            }
        }

        // Check for agricultural data (crops/livestock)
        JsonNode agData = extension.path("agriculturalData");
        if (!agData.isMissingNode()) {
            structure.addFoundStructure("agriculturalData", extractFieldNames(agData));
        }

        // Check for crops and livestock arrays
        JsonNode crops = extension.path("cropManagement");
        if (!crops.isMissingNode() && crops.isArray()) {
            structure.setHasCrops(true);
            structure.setCropCount(crops.size());
        }

        JsonNode livestock = extension.path("livestockDetails");
        if (!livestock.isMissingNode() && livestock.isArray()) {
            structure.setHasLivestock(true);
            structure.setLivestockCount(livestock.size());
        }
    }

    private Set<String> extractFieldNames(JsonNode node) {
        Set<String> fieldNames = new HashSet<>();
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                fieldNames.add(names.next());
            }
        }
        return fieldNames;
    }

    /**
     * Test data structure analysis result
     */
    public static class TestDataStructure {
        private Set<String> fieldPaths = new HashSet<>();
        private Map<String, Set<String>> foundStructures = new HashMap<>();
        private List<String> issues = new ArrayList<>();
        private boolean hasCrops;
        private boolean hasLivestock;
        private int cropCount;
        private int livestockCount;

        public void addFoundStructure(String name, Set<String> fields) {
            foundStructures.put(name, fields);
        }

        public void addIssue(String issue) {
            issues.add(issue);
        }

        public boolean hasFieldPath(String path) {
            // Check exact match
            if (fieldPaths.contains(path)) {
                return true;
            }

            // Check if it's a nested path that exists
            for (String existingPath : fieldPaths) {
                if (existingPath.startsWith(path + ".") || path.startsWith(existingPath + ".")) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Find if a field name exists anywhere in the test data, returning the actual path
         */
        public String findFieldByName(String fieldName) {
            for (String existingPath : fieldPaths) {
                String[] pathParts = existingPath.split("\\.");
                String lastPart = pathParts[pathParts.length - 1];

                // Handle array notation like [0]
                if (lastPart.contains("[")) {
                    lastPart = lastPart.substring(0, lastPart.indexOf("["));
                }

                if (lastPart.equals(fieldName)) {
                    return existingPath;
                }
            }
            return null;
        }

        /**
         * Check if field exists at wrong path and return mismatch info
         */
        public PathMismatch checkPathMismatch(String expectedPath) {
            // First check if exact path exists
            if (hasFieldPath(expectedPath)) {
                return null; // No mismatch
            }

            // Extract field name from expected path
            String[] pathParts = expectedPath.split("\\.");
            String fieldName = pathParts[pathParts.length - 1];

            // Search for field name anywhere
            String actualPath = findFieldByName(fieldName);
            if (actualPath != null) {
                return new PathMismatch(expectedPath, actualPath, fieldName);
            }

            return null; // Field truly doesn't exist
        }

        /**
         * Data class for path mismatch information
         */
        public static class PathMismatch {
            private String expectedPath;
            private String actualPath;
            private String fieldName;

            public PathMismatch(String expectedPath, String actualPath, String fieldName) {
                this.expectedPath = expectedPath;
                this.actualPath = actualPath;
                this.fieldName = fieldName;
            }

            public String getExpectedPath() { return expectedPath; }
            public String getActualPath() { return actualPath; }
            public String getFieldName() { return fieldName; }

            public String getSuggestion() {
                return "Move '" + fieldName + "' from '" + actualPath + "' to '" + expectedPath + "'";
            }
        }

        // Getters and setters
        public Set<String> getFieldPaths() { return fieldPaths; }
        public Map<String, Set<String>> getFoundStructures() { return foundStructures; }
        public List<String> getIssues() { return issues; }

        public boolean hasCrops() { return hasCrops; }
        public void setHasCrops(boolean hasCrops) { this.hasCrops = hasCrops; }

        public boolean hasLivestock() { return hasLivestock; }
        public void setHasLivestock(boolean hasLivestock) { this.hasLivestock = hasLivestock; }

        public int getCropCount() { return cropCount; }
        public void setCropCount(int cropCount) { this.cropCount = cropCount; }

        public int getLivestockCount() { return livestockCount; }
        public void setLivestockCount(int livestockCount) { this.livestockCount = livestockCount; }
    }
}