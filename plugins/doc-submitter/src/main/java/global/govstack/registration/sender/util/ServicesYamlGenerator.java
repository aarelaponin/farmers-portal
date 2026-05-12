package global.govstack.registration.sender.util;

import global.govstack.registration.sender.model.MappingHints;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates services.yml deterministically from form_structure.yaml + mapping-hints.yaml
 *
 * Usage:
 *   java ServicesYamlGenerator \
 *     --form-structure form_structure.yaml \
 *     --mapping-hints mapping-hints.yaml \
 *     --output services.yml
 */
public class ServicesYamlGenerator {

    public static void main(String[] args) {
        try {
            // Parse command line arguments
            Map<String, String> params = parseArgs(args);

            String formStructurePath = params.get("form-structure");
            String mappingHintsPath = params.get("mapping-hints");
            String outputPath = params.get("output");

            if (formStructurePath == null || mappingHintsPath == null || outputPath == null) {
                printUsage();
                System.exit(1);
            }

            System.out.println("ServicesYamlGenerator");
            System.out.println("====================");
            System.out.println("Form structure: " + formStructurePath);
            System.out.println("Mapping hints:  " + mappingHintsPath);
            System.out.println("Output:         " + outputPath);
            System.out.println();

            // Generate services.yml
            ServicesYamlGenerator generator = new ServicesYamlGenerator();
            generator.generate(formStructurePath, mappingHintsPath, outputPath);

            System.out.println("\n✓ Successfully generated: " + outputPath);
            System.exit(0);

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Generate services.yml from inputs
     */
    public void generate(String formStructurePath, String mappingHintsPath, String outputPath) throws IOException {
        // Load inputs
        Map<String, Object> formStructure = loadYaml(formStructurePath);
        MappingHints hints = loadMappingHints(mappingHintsPath);

        // Build services configuration
        Map<String, Object> servicesConfig = new LinkedHashMap<>();

        // 1. Service metadata
        buildServiceSection(servicesConfig, hints);

        // 2. Metadata section (masterDataFields, fieldNormalization)
        buildMetadataSection(servicesConfig, formStructure, hints);

        // 3. Entities section (optional, from hints or defaults)
        buildEntitiesSection(servicesConfig, hints);

        // 4. Service configuration
        buildServiceConfigSection(servicesConfig, formStructure, hints);

        // 5. Form mappings (the main field mappings)
        buildFormMappingsSection(servicesConfig, formStructure, hints);

        // Write output
        writeYaml(servicesConfig, outputPath);
    }

    /**
     * Build service section
     */
    private void buildServiceSection(Map<String, Object> config, MappingHints hints) {
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("id", hints.getService().getId());
        service.put("name", hints.getService().getName());
        service.put("version", hints.getService().getVersion());
        service.put("govstackVersion", hints.getService().getGovstackVersion());
        config.put("service", service);
    }

    /**
     * Build metadata section with auto-detected master data fields and normalization
     */
    private void buildMetadataSection(Map<String, Object> config, Map<String, Object> formStructure, MappingHints hints) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        // Auto-detect master data fields (fields with lookup_form)
        List<String> masterDataFields = detectMasterDataFields(formStructure);
        if (!masterDataFields.isEmpty()) {
            metadata.put("masterDataFields", masterDataFields);
            System.out.println("Auto-detected " + masterDataFields.size() + " master data fields");
        }

        // Auto-detect field normalization
        Map<String, List<String>> normalization = detectFieldNormalization(formStructure, hints);
        if (!normalization.isEmpty()) {
            metadata.put("fieldNormalization", normalization);
            System.out.println("Auto-detected " +
                (normalization.get("yesNo").size() + normalization.get("oneTwo").size()) +
                " normalization fields");
        }

        if (!metadata.isEmpty()) {
            config.put("metadata", metadata);
        }
    }

    /**
     * Detect master data fields from form structure
     * Master data fields have lookup_form specified
     */
    @SuppressWarnings("unchecked")
    private List<String> detectMasterDataFields(Map<String, Object> formStructure) {
        Set<String> masterData = new TreeSet<>(); // TreeSet for sorted output

        Map<String, Object> forms = (Map<String, Object>) formStructure.get("forms");
        if (forms == null) return new ArrayList<>(masterData);

        for (Map.Entry<String, Object> formEntry : forms.entrySet()) {
            Map<String, Object> form = (Map<String, Object>) formEntry.getValue();
            List<Map<String, Object>> allFields = (List<Map<String, Object>>) form.get("all_fields");

            if (allFields == null) continue;

            for (Map<String, Object> field : allFields) {
                String lookupForm = (String) field.get("lookup_form");
                String fieldId = (String) field.get("field_id");

                // Master data field = has lookup_form (not null)
                if (lookupForm != null && !lookupForm.isEmpty() && !lookupForm.equals("null")) {
                    masterData.add(fieldId);
                }
            }
        }

        return new ArrayList<>(masterData);
    }

    /**
     * Detect field normalization settings
     * Fields with type=radio/select and options_count=2 and no lookup_form are yes/no fields
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> detectFieldNormalization(Map<String, Object> formStructure, MappingHints hints) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Set<String> yesNoFields = new TreeSet<>();
        Set<String> oneTwoFields = new TreeSet<>();

        Map<String, Object> forms = (Map<String, Object>) formStructure.get("forms");
        if (forms == null) {
            result.put("yesNo", new ArrayList<>());
            result.put("oneTwo", new ArrayList<>());
            return result;
        }

        for (Map.Entry<String, Object> formEntry : forms.entrySet()) {
            Map<String, Object> form = (Map<String, Object>) formEntry.getValue();
            List<Map<String, Object>> allFields = (List<Map<String, Object>>) form.get("all_fields");

            if (allFields == null) continue;

            for (Map<String, Object> field : allFields) {
                String type = (String) field.get("type");
                Object optionsCountObj = field.get("options_count");
                String lookupForm = (String) field.get("lookup_form");
                String fieldId = (String) field.get("field_id");

                // Skip if not a yes/no candidate
                if (!("radio".equals(type) || "select".equals(type))) continue;
                if (optionsCountObj == null) continue;
                if (lookupForm != null && !lookupForm.equals("null")) continue; // Has lookup = not yes/no

                int optionsCount = (Integer) optionsCountObj;
                if (optionsCount != 2) continue; // Not binary choice

                // Determine normalization type
                if (hints.shouldForceYesNo(fieldId)) {
                    yesNoFields.add(fieldId);
                } else if (hints.shouldForceOneTwo(fieldId)) {
                    oneTwoFields.add(fieldId);
                } else {
                    // Default heuristic: fields starting with "has_", "can_", "is_" use yesNo
                    if (fieldId.startsWith("has") || fieldId.startsWith("can") ||
                        fieldId.startsWith("is_") || fieldId.contains("member_of")) {
                        yesNoFields.add(fieldId);
                    } else {
                        // Others use oneTwo for compatibility
                        oneTwoFields.add(fieldId);
                    }
                }
            }
        }

        result.put("yesNo", new ArrayList<>(yesNoFields));
        result.put("oneTwo", new ArrayList<>(oneTwoFields));
        return result;
    }

    /**
     * Build entities section (optional, basic Person entity)
     */
    private void buildEntitiesSection(Map<String, Object> config, MappingHints hints) {
        Map<String, Object> entities = new LinkedHashMap<>();
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("type", "Person");

        List<String> identifierTypes = new ArrayList<>();
        identifierTypes.add("NationalId");
        identifierTypes.add(capitalizeFirst(hints.getService().getId()) + "Number");
        primary.put("identifierTypes", identifierTypes);

        entities.put("primary", primary);
        config.put("entities", entities);
    }

    /**
     * Build serviceConfig section
     */
    @SuppressWarnings("unchecked")
    private void buildServiceConfigSection(Map<String, Object> config, Map<String, Object> formStructure, MappingHints hints) {
        Map<String, Object> serviceConfig = new LinkedHashMap<>();

        // Find parent form
        String parentFormId = findParentForm(formStructure);
        if (parentFormId != null) {
            serviceConfig.put("parentFormId", parentFormId);
        }

        // Build defaults section
        Map<String, String> defaults = new LinkedHashMap<>();
        String parentEntityName = extractEntityName(hints.getService().getId());
        defaults.put("gridParentField", parentEntityName + "_id");
        defaults.put("gridParentColumn", "c_" + parentEntityName + "_id");
        serviceConfig.put("defaults", defaults);

        // Build sectionToFormMap
        Map<String, String> sectionToFormMap = buildSectionToFormMap(formStructure);
        if (!sectionToFormMap.isEmpty()) {
            serviceConfig.put("sectionToFormMap", sectionToFormMap);
        }

        // Build gridMappings
        Map<String, Map<String, String>> gridMappings = buildGridMappings(formStructure, parentEntityName);
        if (!gridMappings.isEmpty()) {
            serviceConfig.put("gridMappings", gridMappings);
        }

        config.put("serviceConfig", serviceConfig);
    }

    /**
     * Find parent form (is_parent_form = true)
     */
    @SuppressWarnings("unchecked")
    private String findParentForm(Map<String, Object> formStructure) {
        Map<String, Object> forms = (Map<String, Object>) formStructure.get("forms");
        if (forms == null) return null;

        for (Map.Entry<String, Object> entry : forms.entrySet()) {
            Map<String, Object> form = (Map<String, Object>) entry.getValue();
            Boolean isParent = (Boolean) form.get("is_parent_form");
            if (Boolean.TRUE.equals(isParent)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Build section to form mapping (all child forms)
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> buildSectionToFormMap(Map<String, Object> formStructure) {
        Map<String, String> sectionMap = new LinkedHashMap<>();
        Map<String, Object> forms = (Map<String, Object>) formStructure.get("forms");
        if (forms == null) return sectionMap;

        for (Map.Entry<String, Object> entry : forms.entrySet()) {
            String formKey = entry.getKey();
            Map<String, Object> form = (Map<String, Object>) entry.getValue();
            Boolean isParent = (Boolean) form.get("is_parent_form");

            // Skip parent form and grid forms
            if (Boolean.TRUE.equals(isParent)) continue;

            List<Object> gridForms = (List<Object>) formStructure.get("grid_forms");
            if (gridForms != null && gridForms.contains(formKey)) continue;

            sectionMap.put(formKey, formKey);
        }

        return sectionMap;
    }

    /**
     * Build grid mappings (for grid/subforms)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> buildGridMappings(Map<String, Object> formStructure, String parentEntityName) {
        Map<String, Map<String, String>> gridMappings = new LinkedHashMap<>();
        Map<String, Object> forms = (Map<String, Object>) formStructure.get("forms");
        if (forms == null) return gridMappings;

        for (Map.Entry<String, Object> formEntry : forms.entrySet()) {
            Map<String, Object> form = (Map<String, Object>) formEntry.getValue();
            List<Map<String, Object>> grids = (List<Map<String, Object>>) form.get("grids");

            if (grids == null) continue;

            for (Map<String, Object> grid : grids) {
                String gridId = (String) grid.get("grid_id");
                String subFormId = (String) grid.get("sub_form_id");

                Map<String, String> gridConfig = new LinkedHashMap<>();
                gridConfig.put("formId", subFormId);
                gridConfig.put("parentField", parentEntityName + "_id");
                gridConfig.put("parentColumn", "c_" + parentEntityName + "_id");

                gridMappings.put(gridId, gridConfig);
            }
        }

        return gridMappings;
    }

    /**
     * Build form mappings section (the main field-to-path mappings)
     */
    @SuppressWarnings("unchecked")
    private void buildFormMappingsSection(Map<String, Object> config, Map<String, Object> formStructure, MappingHints hints) {
        Map<String, Object> formMappings = new LinkedHashMap<>();
        Map<String, Object> forms = (Map<String, Object>) formStructure.get("forms");
        if (forms == null) {
            config.put("formMappings", formMappings);
            return;
        }

        int totalFields = 0;
        int mappedFields = 0;

        for (Map.Entry<String, Object> formEntry : forms.entrySet()) {
            String formKey = formEntry.getKey();
            Map<String, Object> form = (Map<String, Object>) formEntry.getValue();

            // Skip parent form
            Boolean isParent = (Boolean) form.get("is_parent_form");
            if (Boolean.TRUE.equals(isParent)) continue;

            // Build form mapping
            Map<String, Object> formMapping = new LinkedHashMap<>();
            formMapping.put("formId", formKey);
            formMapping.put("tableName", "app_fd_" + form.get("table_name"));

            // Determine primaryKey and uuidReferenceField
            String childOf = (String) form.get("child_of");
            if (childOf != null) {
                String entityName = extractEntityName(hints.getService().getId());
                formMapping.put("primaryKey", "c_" + entityName + "_id");
                // Generate UUID reference field name from form key
                String uuidRef = formKey.replaceFirst("^" + entityName, "");
                if (!uuidRef.isEmpty() && Character.isUpperCase(uuidRef.charAt(0))) {
                    uuidRef = Character.toLowerCase(uuidRef.charAt(0)) + uuidRef.substring(1);
                }
                if (uuidRef.isEmpty()) uuidRef = "data";
                formMapping.put("uuidReferenceField", uuidRef);
            }

            // Build field mappings
            List<Map<String, Object>> fieldMappings = new ArrayList<>();
            List<Map<String, Object>> allFields = (List<Map<String, Object>>) form.get("all_fields");

            if (allFields != null) {
                for (Map<String, Object> field : allFields) {
                    totalFields++;
                    Map<String, Object> fieldMapping = buildFieldMapping(field, hints);
                    if (fieldMapping != null) {
                        fieldMappings.add(fieldMapping);
                        mappedFields++;
                    }
                }
            }

            if (!fieldMappings.isEmpty()) {
                formMapping.put("fields", fieldMappings);
            }

            formMappings.put(formKey, formMapping);
        }

        config.put("formMappings", formMappings);
        System.out.println("Mapped " + mappedFields + "/" + totalFields + " fields (" +
            (totalFields > 0 ? (mappedFields * 100 / totalFields) : 0) + "% coverage)");
    }

    /**
     * Build field mapping for a single field
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFieldMapping(Map<String, Object> field, MappingHints hints) {
        String fieldId = (String) field.get("field_id");
        String type = (String) field.get("type");
        Boolean required = (Boolean) field.get("required");

        // Skip hidden system fields
        if ("hidden".equals(type) && (fieldId.endsWith("_id") || fieldId.endsWith("_key"))) {
            return null;
        }

        // Skip HTML display fields
        if ("html".equals(type)) {
            return null;
        }

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("joget", fieldId);

        // Get GovStack path from hints or apply convention
        String govstackPath = hints.getMapping(fieldId);
        if (govstackPath == null) {
            govstackPath = applyMappingConvention(fieldId, hints);
        }
        mapping.put("govstack", govstackPath);

        // Add transformation if needed
        String transform = determineTransform(field);
        if (transform != null) {
            mapping.put("transform", transform);
        }

        // Add required flag if true
        if (Boolean.TRUE.equals(required)) {
            mapping.put("required", true);
        }

        return mapping;
    }

    /**
     * Apply mapping convention for unmapped fields
     */
    private String applyMappingConvention(String fieldId, MappingHints hints) {
        // Special field name patterns
        if (fieldId.endsWith("_id") && !fieldId.equals("parent_id")) {
            return "identifiers[0].value";
        }
        if (fieldId.equals("first_name")) return "name.given[0]";
        if (fieldId.equals("last_name")) return "name.family";
        if (fieldId.equals("date_of_birth")) return "birthDate";
        if (fieldId.equals("gender")) return "gender";

        // Default: use convention from hints
        return hints.applyDefaultMapping(fieldId);
    }

    /**
     * Determine transform type from field metadata
     */
    private String determineTransform(Map<String, Object> field) {
        String type = (String) field.get("type");
        String transformHint = (String) field.get("transform_hint");

        // Use explicit transform_hint if present
        if (transformHint != null && !transformHint.isEmpty()) {
            return transformHint;
        }

        // Auto-detect based on type
        if ("date".equals(type)) {
            return "date_ISO8601";
        }
        if ("signature".equals(type)) {
            return "base64";
        }

        return null;
    }

    // Utility methods

    private String extractEntityName(String serviceId) {
        // Extract entity name from service ID (e.g., "farmers_registry" -> "farmer")
        String[] parts = serviceId.split("_");
        if (parts.length > 0) {
            String entity = parts[0];
            // Remove trailing 's' if present
            if (entity.endsWith("s")) {
                entity = entity.substring(0, entity.length() - 1);
            }
            return entity;
        }
        return "entity";
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            return yaml.load(is);
        }
    }

    @SuppressWarnings("unchecked")
    private MappingHints loadMappingHints(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            Map<String, Object> data = yaml.load(is);
            MappingHints hints = new MappingHints();

            // Parse service section
            Map<String, Object> serviceData = (Map<String, Object>) data.get("service");
            if (serviceData != null) {
                MappingHints.ServiceInfo service = new MappingHints.ServiceInfo();
                service.setId((String) serviceData.get("id"));
                service.setName((String) serviceData.get("name"));
                if (serviceData.containsKey("version")) {
                    service.setVersion((String) serviceData.get("version"));
                }
                if (serviceData.containsKey("govstackVersion")) {
                    service.setGovstackVersion((String) serviceData.get("govstackVersion"));
                }
                hints.setService(service);
            }

            // Parse field mappings
            Map<String, String> mappings = (Map<String, String>) data.get("field_mappings");
            if (mappings != null) {
                hints.setFieldMappings(mappings);
            }

            // Parse default mapping
            if (data.containsKey("default_mapping")) {
                hints.setDefaultMapping((String) data.get("default_mapping"));
            }

            // Parse normalization preferences
            Map<String, Object> normData = (Map<String, Object>) data.get("normalization");
            if (normData != null) {
                MappingHints.NormalizationPreferences norm = new MappingHints.NormalizationPreferences();
                norm.setForceYesNo((List<String>) normData.get("force_yesNo"));
                norm.setForceOneTwo((List<String>) normData.get("force_oneTwo"));
                hints.setNormalization(norm);
            }

            return hints;
        }
    }

    private void writeYaml(Map<String, Object> data, String path) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        try (Writer writer = new FileWriter(path)) {
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
        System.out.println("Usage: java ServicesYamlGenerator \\");
        System.out.println("  --form-structure <form_structure.yaml> \\");
        System.out.println("  --mapping-hints <mapping-hints.yaml> \\");
        System.out.println("  --output <services.yml>");
    }
}
