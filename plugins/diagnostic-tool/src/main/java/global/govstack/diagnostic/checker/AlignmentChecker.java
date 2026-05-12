package global.govstack.diagnostic.checker;

import global.govstack.diagnostic.analyzer.*;
import java.util.*;

/**
 * Cross-references forms, mappings, and test data to find alignment issues
 */
public class AlignmentChecker {

    public AlignmentResult checkAlignment(
            Map<String, FormAnalyzer.FormDefinition> forms,
            MappingAnalyzer.ServiceMappings mappings,
            TestDataAnalyzer.TestDataStructure testData,
            String specificForm) {

        AlignmentResult result = new AlignmentResult();

        // Check 1: Validate all mapped Joget fields exist in forms
        checkJogetFieldsExist(forms, mappings, result, specificForm);

        // Check 2: Validate test data paths match GovStack paths
        checkTestDataPaths(mappings, testData, result);

        // Check 3: Find unmapped form fields
        checkUnmappedFormFields(forms, mappings, result, specificForm);

        // Check 4: Check for specific known issues
        checkKnownIssues(testData, mappings, result);

        // Check 5: Analyze structural patterns in mismatches
        analyzeStructuralPatterns(mappings, testData, result);

        return result;
    }

    private void checkJogetFieldsExist(
            Map<String, FormAnalyzer.FormDefinition> forms,
            MappingAnalyzer.ServiceMappings mappings,
            AlignmentResult result,
            String specificForm) {

        for (MappingAnalyzer.SectionMapping section : mappings.getSections().values()) {
            // Try to find corresponding form
            FormAnalyzer.FormDefinition form = findFormForSection(forms, section.getSectionName());

            if (form == null) {
                result.addIssue(new AlignmentIssue(
                    AlignmentIssue.Severity.WARNING,
                    "MISSING_FORM",
                    "No form found for section: " + section.getSectionName(),
                    null
                ));
                continue;
            }

            // Skip if checking specific form and this isn't it
            if (specificForm != null && !form.getFormId().equals(specificForm)) {
                continue;
            }

            for (MappingAnalyzer.FieldMapping field : section.getFields()) {
                if (!form.hasField(field.getJogetField())) {
                    result.addIssue(new AlignmentIssue(
                        AlignmentIssue.Severity.ERROR,
                        "FIELD_NOT_IN_FORM",
                        "Field '" + field.getJogetField() + "' mapped but not found in form '" + form.getFormId() + "'",
                        "Check if field ID in form matches mapping, or update services.yml"
                    ));
                } else {
                    result.incrementAlignedFields();
                }
            }
        }
    }

    private void checkTestDataPaths(
            MappingAnalyzer.ServiceMappings mappings,
            TestDataAnalyzer.TestDataStructure testData,
            AlignmentResult result) {

        for (String govstackPath : mappings.getAllGovstackPaths()) {
            // Special handling for problematic fields
            if (govstackPath.equals("extension.agriculturalActivities.engagedInCropProduction")) {
                // Check if test data has the wrong field name
                if (testData.hasFieldPath("extension.agriculturalActivities.cropProduction")) {
                    result.addIssue(new AlignmentIssue(
                        AlignmentIssue.Severity.ERROR,
                        "FIELD_NAME_MISMATCH",
                        "Test data uses 'cropProduction' but mapping expects 'engagedInCropProduction'",
                        "Update test data: change 'cropProduction' to 'engagedInCropProduction'"
                    ));
                }
            } else if (govstackPath.equals("extension.agriculturalActivities.engagedInLivestockProduction")) {
                // Check if test data has the wrong field name
                if (testData.hasFieldPath("extension.agriculturalActivities.livestockProduction")) {
                    result.addIssue(new AlignmentIssue(
                        AlignmentIssue.Severity.ERROR,
                        "FIELD_NAME_MISMATCH",
                        "Test data uses 'livestockProduction' but mapping expects 'engagedInLivestockProduction'",
                        "Update test data: change 'livestockProduction' to 'engagedInLivestockProduction'"
                    ));
                }
            }

            // Enhanced path checking with mismatch detection
            if (!testData.hasFieldPath(govstackPath) && !isArrayPath(govstackPath)) {
                // Check if field exists at wrong path
                TestDataAnalyzer.TestDataStructure.PathMismatch mismatch =
                    testData.checkPathMismatch(govstackPath);

                if (mismatch != null) {
                    // Field exists but at wrong path - this is an ERROR
                    result.addIssue(new AlignmentIssue(
                        AlignmentIssue.Severity.ERROR,
                        "PATH_MISMATCH",
                        "Field '" + mismatch.getFieldName() + "' found at '" +
                        mismatch.getActualPath() + "' but expected at '" +
                        mismatch.getExpectedPath() + "'",
                        mismatch.getSuggestion()
                    ));
                } else {
                    // Field truly missing - this is a WARNING
                    result.addIssue(new AlignmentIssue(
                        AlignmentIssue.Severity.WARNING,
                        "MISSING_TEST_DATA",
                        "GovStack path not found in test data: " + govstackPath,
                        "Add this field to test data or verify the path is correct"
                    ));
                }
            }
        }
    }

    private void checkUnmappedFormFields(
            Map<String, FormAnalyzer.FormDefinition> forms,
            MappingAnalyzer.ServiceMappings mappings,
            AlignmentResult result,
            String specificForm) {

        Set<String> mappedFields = mappings.getAllJogetFields();

        for (FormAnalyzer.FormDefinition form : forms.values()) {
            // Skip if checking specific form and this isn't it
            if (specificForm != null && !form.getFormId().equals(specificForm)) {
                continue;
            }

            for (String formField : form.getFields()) {
                if (!mappedFields.contains(formField) && !isSystemField(formField)) {
                    result.addIssue(new AlignmentIssue(
                        AlignmentIssue.Severity.INFO,
                        "UNMAPPED_FIELD",
                        "Form field '" + formField + "' in form '" + form.getFormId() + "' has no mapping",
                        "Add mapping to services.yml if this field should be saved"
                    ));
                }
            }
        }
    }

    private void checkKnownIssues(
            TestDataAnalyzer.TestDataStructure testData,
            MappingAnalyzer.ServiceMappings mappings,
            AlignmentResult result) {

        // Check for issues identified by test data analyzer
        for (String issue : testData.getIssues()) {
            result.addIssue(new AlignmentIssue(
                AlignmentIssue.Severity.ERROR,
                "TEST_DATA_ISSUE",
                issue,
                "Update test data to match expected field names"
            ));
        }
    }

    private void analyzeStructuralPatterns(
            MappingAnalyzer.ServiceMappings mappings,
            TestDataAnalyzer.TestDataStructure testData,
            AlignmentResult result) {

        // Track patterns of mismatched paths
        Map<String, Integer> wrongParentPaths = new HashMap<>();
        Map<String, String> suggestedMoves = new HashMap<>();

        for (String govstackPath : mappings.getAllGovstackPaths()) {
            if (!testData.hasFieldPath(govstackPath) && !isArrayPath(govstackPath)) {
                TestDataAnalyzer.TestDataStructure.PathMismatch mismatch =
                    testData.checkPathMismatch(govstackPath);

                if (mismatch != null) {
                    // Extract parent paths
                    String expectedParent = getParentPath(mismatch.getExpectedPath());
                    String actualParent = getParentPath(mismatch.getActualPath());

                    String pattern = actualParent + " -> " + expectedParent;
                    wrongParentPaths.put(pattern, wrongParentPaths.getOrDefault(pattern, 0) + 1);

                    if (wrongParentPaths.get(pattern) >= 3) { // 3+ fields indicate structural issue
                        suggestedMoves.put(pattern, "Move multiple fields from '" + actualParent +
                                                  "' to '" + expectedParent + "'");
                    }
                }
            }
        }

        // Report structural patterns
        for (Map.Entry<String, String> entry : suggestedMoves.entrySet()) {
            String pattern = entry.getKey();
            int count = wrongParentPaths.get(pattern);

            result.addIssue(new AlignmentIssue(
                AlignmentIssue.Severity.ERROR,
                "STRUCTURAL_MISMATCH",
                "Structural mismatch detected: " + count + " fields need path restructuring - " + pattern,
                entry.getValue() + " (affects " + count + " fields)"
            ));
        }
    }

    private String getParentPath(String fullPath) {
        int lastDot = fullPath.lastIndexOf('.');
        if (lastDot > 0) {
            return fullPath.substring(0, lastDot);
        }
        return "";
    }

    private FormAnalyzer.FormDefinition findFormForSection(
            Map<String, FormAnalyzer.FormDefinition> forms,
            String sectionName) {

        // Direct match
        if (forms.containsKey(sectionName)) {
            return forms.get(sectionName);
        }

        // Try to find by similar name
        for (FormAnalyzer.FormDefinition form : forms.values()) {
            if (form.getFormId().equalsIgnoreCase(sectionName)) {
                return form;
            }
        }

        return null;
    }

    private boolean isArrayPath(String path) {
        return path.contains("[") && path.contains("]");
    }

    private boolean isSystemField(String fieldName) {
        return fieldName.equals("parent_id") ||
               fieldName.endsWith("_key") ||
               fieldName.endsWith("Instruction") ||
               fieldName.endsWith("Header") ||
               fieldName.endsWith("Text");
    }

    /**
     * Alignment check result
     */
    public static class AlignmentResult {
        private List<AlignmentIssue> issues = new ArrayList<>();
        private int alignedFields = 0;
        private Map<String, Object> metadata = new HashMap<>();

        public void addIssue(AlignmentIssue issue) {
            issues.add(issue);
        }

        public void incrementAlignedFields() {
            alignedFields++;
        }

        public boolean hasErrors() {
            return issues.stream()
                .anyMatch(i -> i.getSeverity() == AlignmentIssue.Severity.ERROR);
        }

        public List<AlignmentIssue> getIssues() {
            return issues;
        }

        public List<AlignmentIssue> getIssuesBySeverity(AlignmentIssue.Severity severity) {
            List<AlignmentIssue> filtered = new ArrayList<>();
            for (AlignmentIssue issue : issues) {
                if (issue.getSeverity() == severity) {
                    filtered.add(issue);
                }
            }
            return filtered;
        }

        public int getAlignedFields() {
            return alignedFields;
        }

        public String toJson() {
            // Simple JSON output
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"alignedFields\": ").append(alignedFields).append(",\n");
            json.append("  \"issueCount\": ").append(issues.size()).append(",\n");
            json.append("  \"hasErrors\": ").append(hasErrors()).append(",\n");
            json.append("  \"issues\": [\n");

            for (int i = 0; i < issues.size(); i++) {
                AlignmentIssue issue = issues.get(i);
                json.append("    {\n");
                json.append("      \"severity\": \"").append(issue.getSeverity()).append("\",\n");
                json.append("      \"type\": \"").append(issue.getType()).append("\",\n");
                json.append("      \"description\": \"").append(issue.getDescription()).append("\",\n");
                json.append("      \"suggestion\": \"").append(issue.getSuggestion()).append("\"\n");
                json.append("    }");
                if (i < issues.size() - 1) json.append(",");
                json.append("\n");
            }

            json.append("  ]\n");
            json.append("}");

            return json.toString();
        }
    }

    /**
     * Alignment issue
     */
    public static class AlignmentIssue {
        public enum Severity { ERROR, WARNING, INFO }

        private Severity severity;
        private String type;
        private String description;
        private String suggestion;

        public AlignmentIssue(Severity severity, String type, String description, String suggestion) {
            this.severity = severity;
            this.type = type;
            this.description = description;
            this.suggestion = suggestion;
        }

        // Getters
        public Severity getSeverity() { return severity; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public String getSuggestion() { return suggestion; }
    }
}