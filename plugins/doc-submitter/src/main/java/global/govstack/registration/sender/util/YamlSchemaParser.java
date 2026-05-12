package global.govstack.registration.sender.util;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

public class YamlSchemaParser {

    public Map<String, Set<String>> parseTableColumnMappings(InputStream yamlInputStream) {
        Map<String, Set<String>> tableColumnMap = new LinkedHashMap<>();

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(yamlInputStream);

            if (data == null || !data.containsKey("forms")) {
                return tableColumnMap;
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> forms = (Map<String, Map<String, Object>>) data.get("forms");

            for (Map.Entry<String, Map<String, Object>> formEntry : forms.entrySet()) {
                Map<String, Object> formData = formEntry.getValue();

                // Get the table name for this form
                String tableName = (String) formData.get("table_name");
                if (tableName == null) {
                    continue;
                }

                Set<String> columns = tableColumnMap.computeIfAbsent(tableName, k -> new LinkedHashSet<>());

                // Extract columns from sections.fields
                if (formData.containsKey("sections")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sections = (List<Map<String, Object>>) formData.get("sections");
                    extractColumnsFromFields(sections, columns);
                }

                // Extract columns from all_fields (if not already covered by sections)
                if (formData.containsKey("all_fields")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> allFields = (List<Map<String, Object>>) formData.get("all_fields");
                    for (Map<String, Object> field : allFields) {
                        String column = (String) field.get("column");
                        if (column != null && !column.isEmpty()) {
                            columns.add(column);
                        }
                    }
                }

                // Extract columns from grids.sub_form_fields
                if (formData.containsKey("grids")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> grids = (List<Map<String, Object>>) formData.get("grids");
                    for (Map<String, Object> grid : grids) {
                        // Get the grid's table name (might be different from form table)
                        String gridTableName = (String) grid.get("table_name");
                        if (gridTableName != null) {
                            Set<String> gridColumns = tableColumnMap.computeIfAbsent(gridTableName, k -> new LinkedHashSet<>());

                            if (grid.containsKey("sub_form_fields")) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> subFormFields = (List<Map<String, Object>>) grid.get("sub_form_fields");
                                for (Map<String, Object> field : subFormFields) {
                                    String column = (String) field.get("column");
                                    if (column != null && !column.isEmpty()) {
                                        gridColumns.add(column);
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAML schema: " + e.getMessage(), e);
        }

        return tableColumnMap;
    }

    private void extractColumnsFromFields(List<Map<String, Object>> sections, Set<String> columns) {
        for (Map<String, Object> section : sections) {
            if (section.containsKey("fields")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
                for (Map<String, Object> field : fields) {
                    String column = (String) field.get("column");
                    if (column != null && !column.isEmpty()) {
                        columns.add(column);
                    }
                }
            }
        }
    }
}
