package global.govstack.registration.sender.util;

import global.govstack.registration.sender.service.metadata.YamlMetadataService;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Validates that all fields from form_structure.yaml have corresponding mappings in services.yml
 *
 * This utility ensures complete coverage between:
 * - form_structure.yaml (Joget Forms → Database structure)
 * - services.yml (Database Fields → GovStack JSON mappings)
 */
public class MappingValidator {

    private static final String CLASS_NAME = MappingValidator.class.getName();

    private final YamlMetadataService metadataService;

    public MappingValidator() {
        this.metadataService = new YamlMetadataService();
    }

    /**
     * Main validation method - returns coverage statistics
     */
    public ValidationReport validate() {
        ValidationReport report = new ValidationReport();

        try {
            // Get all form mappings from services.yml
            Map<String, Object> formMappings = metadataService.getFormMappings();

            if (formMappings == null) {
                report.addError("Failed to load services.yml form mappings");
                return report;
            }

            // Process each form from form_structure.yaml
            for (String formName : getAllFormNames()) {
                processForm(formName, formMappings, report);
            }

            // Process grid forms separately
            processGridForms(formMappings, report);

        } catch (Exception e) {
            report.addError("Validation failed: " + e.getMessage());
            LogUtil.error(CLASS_NAME, e, "Error during validation");
        }

        return report;
    }

    /**
     * Process a single form and check field coverage
     */
    private void processForm(String formName, Map<String, Object> formMappings, ValidationReport report) {
        try {
            // Get section name from form name (they should match in most cases)
            String sectionName = formName;

            // Get all fields from form_structure.yaml for this form
            List<Map<String, Object>> structureFields = metadataService.getFormStructureFields(formName);

            if (structureFields == null || structureFields.isEmpty()) {
                return; // Skip forms with no fields
            }

            // Get mapped fields from services.yml for this section
            Map<String, Object> sectionConfig = (Map<String, Object>) formMappings.get(sectionName);
            List<Map<String, Object>> mappedFields = new ArrayList<>();

            if (sectionConfig != null) {
                Object fieldsObj = sectionConfig.get("fields");
                if (fieldsObj instanceof List) {
                    mappedFields = (List<Map<String, Object>>) fieldsObj;
                }
            }

            // Build set of mapped field IDs
            Set<String> mappedFieldIds = new HashSet<>();
            for (Map<String, Object> field : mappedFields) {
                String joget = (String) field.get("joget");
                String fieldId = (String) field.get("field_id");

                if (joget != null) {
                    mappedFieldIds.add(joget);
                }
                if (fieldId != null) {
                    mappedFieldIds.add(fieldId);
                }
            }

            // Check each field from form_structure
            List<String> unmappedFields = new ArrayList<>();
            int totalFields = 0;

            for (Map<String, Object> field : structureFields) {
                String fieldId = (String) field.get("field_id");
                String fieldType = (String) field.get("type");

                // Skip system/internal fields that don't need mapping
                if (isSystemField(fieldId, fieldType)) {
                    continue;
                }

                totalFields++;

                if (!mappedFieldIds.contains(fieldId)) {
                    unmappedFields.add(fieldId);
                }
            }

            // Add to report
            if (totalFields > 0) {
                int mappedCount = totalFields - unmappedFields.size();
                double coverage = (totalFields > 0) ? (mappedCount * 100.0 / totalFields) : 0;

                report.addFormCoverage(sectionName, totalFields, mappedCount, unmappedFields, coverage);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error processing form: " + formName);
        }
    }

    /**
     * Process grid/array forms
     */
    private void processGridForms(Map<String, Object> formMappings, ValidationReport report) {
        // Map form_structure.yaml form names to services.yml section names
        String[][] gridMappings = {
            {"householdMemberForm", "householdMembers"},
            {"cropManagementForm", "cropManagement"},
            {"livestockDetailsForm", "livestockDetails"}
        };

        for (String[] mapping : gridMappings) {
            String formName = mapping[0];      // form_structure.yaml name
            String sectionName = mapping[1];   // services.yml section name

            processGridForm(formName, sectionName, formMappings, report);
        }
    }

    /**
     * Process a grid form with explicit section name mapping
     */
    private void processGridForm(String formName, String sectionName, Map<String, Object> formMappings, ValidationReport report) {
        try {
            // Get all fields from form_structure.yaml for this form
            List<Map<String, Object>> structureFields = metadataService.getFormStructureFields(formName);

            if (structureFields == null || structureFields.isEmpty()) {
                return; // Skip forms with no fields
            }

            // Get mapped fields from services.yml for this section
            Map<String, Object> sectionConfig = (Map<String, Object>) formMappings.get(sectionName);
            List<Map<String, Object>> mappedFields = new ArrayList<>();

            if (sectionConfig != null) {
                Object fieldsObj = sectionConfig.get("fields");
                if (fieldsObj instanceof List) {
                    mappedFields = (List<Map<String, Object>>) fieldsObj;
                }
            }

            // Build set of mapped field IDs
            Set<String> mappedFieldIds = new HashSet<>();
            for (Map<String, Object> field : mappedFields) {
                String joget = (String) field.get("joget");
                if (joget != null) {
                    mappedFieldIds.add(joget);
                }
            }

            // Check each field from form_structure
            List<String> unmappedFields = new ArrayList<>();
            int totalFields = 0;

            for (Map<String, Object> field : structureFields) {
                String fieldId = (String) field.get("field_id");
                String fieldType = (String) field.get("type");

                // Skip system/internal fields that don't need mapping
                if (isSystemField(fieldId, fieldType)) {
                    continue;
                }

                totalFields++;

                if (!mappedFieldIds.contains(fieldId)) {
                    unmappedFields.add(fieldId);
                }
            }

            // Add to report
            if (totalFields > 0) {
                int mappedCount = totalFields - unmappedFields.size();
                double coverage = (totalFields > 0) ? (mappedCount * 100.0 / totalFields) : 0;

                report.addFormCoverage(formName + " → " + sectionName, totalFields, mappedCount, unmappedFields, coverage);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error processing grid form: " + formName);
        }
    }

    /**
     * Get all form names from form_structure.yaml
     */
    private List<String> getAllFormNames() {
        List<String> formNames = new ArrayList<>();

        // Get main forms from metadata
        formNames.add("farmerBasicInfo");
        formNames.add("farmerLocation");
        formNames.add("farmerAgriculture");
        formNames.add("farmerCropsLivestock");
        formNames.add("farmerHousehold");
        formNames.add("farmerIncomePrograms");
        formNames.add("farmerDeclaration");

        return formNames;
    }

    /**
     * Check if a field is a system field that doesn't need GovStack mapping
     */
    private boolean isSystemField(String fieldId, String fieldType) {
        if (fieldId == null) {
            return true;
        }

        // System fields
        if (fieldId.equals("id") || fieldId.equals("dateCreated") ||
            fieldId.equals("dateModified") || fieldId.equals("createdBy") ||
            fieldId.equals("modifiedBy") || fieldId.equals("createdByName") ||
            fieldId.equals("modifiedByName")) {
            return true;
        }

        // Parent/foreign key references (handled separately)
        // Use generic pattern matching instead of hardcoded field names
        if (fieldId.endsWith("_id") || fieldId.endsWith("_key")) {
            return true;
        }

        return false;
    }

    /**
     * Validation report holder
     */
    public static class ValidationReport {
        private final List<FormCoverage> formCoverages = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public void addFormCoverage(String formName, int totalFields, int mappedFields,
                                   List<String> unmappedFields, double coverage) {
            formCoverages.add(new FormCoverage(formName, totalFields, mappedFields, unmappedFields, coverage));
        }

        public void addError(String error) {
            errors.add(error);
        }

        public int getTotalFields() {
            return formCoverages.stream().mapToInt(fc -> fc.totalFields).sum();
        }

        public int getTotalMapped() {
            return formCoverages.stream().mapToInt(fc -> fc.mappedFields).sum();
        }

        public double getOverallCoverage() {
            int total = getTotalFields();
            return total > 0 ? (getTotalMapped() * 100.0 / total) : 0;
        }

        public String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== GovStack Mapping Coverage Report ===\n\n");

            if (!errors.isEmpty()) {
                sb.append("ERRORS:\n");
                for (String error : errors) {
                    sb.append("  ✗ ").append(error).append("\n");
                }
                sb.append("\n");
            }

            // Sort by coverage (lowest first to highlight gaps)
            formCoverages.sort(Comparator.comparingDouble(fc -> fc.coverage));

            for (FormCoverage fc : formCoverages) {
                sb.append(String.format("%s (%d fields): %d mapped, %d unmapped (%.1f%%)\n",
                    fc.formName, fc.totalFields, fc.mappedFields,
                    fc.unmappedFields.size(), fc.coverage));

                if (!fc.unmappedFields.isEmpty()) {
                    for (String field : fc.unmappedFields) {
                        sb.append("  ✗ ").append(field).append("\n");
                    }
                }
            }

            sb.append(String.format("\nOVERALL: %d/%d fields mapped (%.1f%%)\n",
                getTotalMapped(), getTotalFields(), getOverallCoverage()));

            return sb.toString();
        }

        public List<FormCoverage> getFormCoverages() {
            return formCoverages;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * Coverage information for a single form
     */
    public static class FormCoverage {
        public final String formName;
        public final int totalFields;
        public final int mappedFields;
        public final List<String> unmappedFields;
        public final double coverage;

        public FormCoverage(String formName, int totalFields, int mappedFields,
                          List<String> unmappedFields, double coverage) {
            this.formName = formName;
            this.totalFields = totalFields;
            this.mappedFields = mappedFields;
            this.unmappedFields = unmappedFields;
            this.coverage = coverage;
        }
    }

    /**
     * Main method for standalone execution
     */
    public static void main(String[] args) {
        System.out.println("Starting GovStack Mapping Validation...\n");

        MappingValidator validator = new MappingValidator();
        ValidationReport report = validator.validate();

        System.out.println(report.generateReport());

        // Exit with error code if coverage is below threshold
        if (report.getOverallCoverage() < 80.0) {
            System.out.println("\nWARNING: Coverage is below 80%");
            System.exit(1);
        }

        System.out.println("\nValidation complete!");
    }
}
