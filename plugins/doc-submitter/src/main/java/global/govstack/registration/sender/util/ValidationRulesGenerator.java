package global.govstack.registration.sender.util;

import global.govstack.registration.sender.model.BusinessRules;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates validation-rules.yaml deterministically from form_structure.yaml + business-rules.yaml
 *
 * Usage:
 *   java ValidationRulesGenerator \
 *     --form-structure form_structure.yaml \
 *     --business-rules business-rules.yaml \
 *     --output validation-rules.yaml
 */
public class ValidationRulesGenerator {

    public static void main(String[] args) {
        try {
            // Parse command line arguments
            Map<String, String> params = parseArgs(args);

            String formStructurePath = params.get("form-structure");
            String businessRulesPath = params.get("business-rules");
            String outputPath = params.get("output");

            if (formStructurePath == null || businessRulesPath == null || outputPath == null) {
                printUsage();
                System.exit(1);
            }

            System.out.println("ValidationRulesGenerator");
            System.out.println("=======================");
            System.out.println("Form structure:  " + formStructurePath);
            System.out.println("Business rules:  " + businessRulesPath);
            System.out.println("Output:          " + outputPath);
            System.out.println();

            // Generate validation-rules.yaml
            ValidationRulesGenerator generator = new ValidationRulesGenerator();
            generator.generate(formStructurePath, businessRulesPath, outputPath);

            System.out.println("\n✓ Successfully generated: " + outputPath);
            System.exit(0);

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Generate validation-rules.yaml from inputs
     */
    public void generate(String formStructurePath, String businessRulesPath, String outputPath) throws IOException {
        // Load inputs
        Map<String, Object> formStructure = loadYaml(formStructurePath);
        BusinessRules businessRules = loadBusinessRules(businessRulesPath);

        // Build validation rules configuration
        Map<String, Object> validationConfig = new LinkedHashMap<>();
        Map<String, Object> validationRules = new LinkedHashMap<>();

        // Generate conditional validations
        List<Map<String, Object>> conditionalValidations = new ArrayList<>();

        for (BusinessRules.ConditionalRule rule : businessRules.getConditionalRules()) {
            Map<String, Object> validation = buildConditionalValidation(rule);
            conditionalValidations.add(validation);
        }

        validationRules.put("conditional_validations", conditionalValidations);
        validationConfig.put("validation_rules", validationRules);

        System.out.println("Generated " + conditionalValidations.size() + " conditional validation rules");

        // Write output
        writeYaml(validationConfig, outputPath);
    }

    /**
     * Build a conditional validation entry from a business rule
     */
    private Map<String, Object> buildConditionalValidation(BusinessRules.ConditionalRule rule) {
        Map<String, Object> validation = new LinkedHashMap<>();

        // Build condition string
        validation.put("condition", rule.buildCondition());

        // Add required grids if specified
        if (rule.getRequiresGrid() != null) {
            List<String> grids = new ArrayList<>();
            grids.add(rule.getRequiresGrid());
            validation.put("required_grids", grids);

            if (rule.getMinEntries() != null && rule.getMinEntries() > 0) {
                validation.put("min_entries", rule.getMinEntries());
            }
        }

        // Add required fields if specified
        if (rule.getRequiresFields() != null && !rule.getRequiresFields().isEmpty()) {
            validation.put("required_fields", rule.getRequiresFields());
        }

        // Add message
        validation.put("message", rule.generateMessage());

        return validation;
    }

    // Utility methods

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            return yaml.load(is);
        }
    }

    @SuppressWarnings("unchecked")
    private BusinessRules loadBusinessRules(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            Map<String, Object> data = yaml.load(is);
            BusinessRules businessRules = new BusinessRules();

            // Parse conditional_rules section
            List<Map<String, Object>> rulesData = (List<Map<String, Object>>) data.get("conditional_rules");
            if (rulesData != null) {
                List<BusinessRules.ConditionalRule> rules = new ArrayList<>();

                for (Map<String, Object> ruleData : rulesData) {
                    BusinessRules.ConditionalRule rule = new BusinessRules.ConditionalRule();

                    rule.setTriggerField((String) ruleData.get("trigger_field"));
                    rule.setTriggerValue((String) ruleData.get("trigger_value"));
                    rule.setRequiresGrid((String) ruleData.get("requires_grid"));

                    if (ruleData.containsKey("min_entries")) {
                        rule.setMinEntries((Integer) ruleData.get("min_entries"));
                    }

                    rule.setRequiresFields((List<String>) ruleData.get("requires_fields"));
                    rule.setMessageTemplate((String) ruleData.get("message_template"));

                    rules.add(rule);
                }

                businessRules.setConditionalRules(rules);
            }

            return businessRules;
        }
    }

    private void writeYaml(Map<String, Object> data, String path) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        try (Writer writer = new FileWriter(path)) {
            // Add header comment
            writer.write("# Validation Rules for GovStack Registration Building Block\n");
            writer.write("# This file was generated automatically from business-rules.yaml\n");
            writer.write("# Generated at: " + new Date() + "\n\n");

            yaml.dump(data, writer);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                String key = args[i].substring(2);
                String value = args[i + 1];
                params.put(key, value);
                i++;
                }
        }
        return params;
    }

    private static void printUsage() {
        System.out.println("Usage: java ValidationRulesGenerator \\");
        System.out.println("  --form-structure <form_structure.yaml> \\");
        System.out.println("  --business-rules <business-rules.yaml> \\");
        System.out.println("  --output <validation-rules.yaml>");
    }
}
