package global.govstack.registration.sender.service;

import global.govstack.registration.sender.model.TableValidation;
import global.govstack.registration.sender.model.ValidationResult;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Set;

public class DatabaseSchemaValidator {

    private final DataSource dataSource;

    public DatabaseSchemaValidator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ValidationResult validate(Map<String, Set<String>> tableColumnMap) {
        ValidationResult result = new ValidationResult();

        result.setTotalTables(tableColumnMap.size());
        int totalColumns = tableColumnMap.values().stream().mapToInt(Set::size).sum();
        result.setTotalColumns(totalColumns);

        int validatedTables = 0;
        int validatedColumns = 0;

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();

            LogUtil.info(getClass().getName(), "Database: " + metadata.getDatabaseProductName() + " " + metadata.getDatabaseProductVersion());

            for (Map.Entry<String, Set<String>> entry : tableColumnMap.entrySet()) {
                String tableName = entry.getKey();
                Set<String> columns = entry.getValue();

                TableValidation tableValidation = new TableValidation(tableName);
                tableValidation.setTotalColumns(columns.size());

                // Check if table exists (try both lowercase and uppercase)
                boolean tableExists = checkTableExists(metadata, tableName);
                tableValidation.setExists(tableExists);

                if (tableExists) {
                    validatedTables++;

                    // Check each column
                    for (String columnName : columns) {
                        if (checkColumnExists(metadata, tableName, columnName)) {
                            tableValidation.addValidatedColumn(columnName);
                            validatedColumns++;
                        } else {
                            result.addMissingColumn(tableName, columnName);
                        }
                    }

                    result.addFoundTable(tableValidation);
                } else {
                    result.addMissingTable(tableName);
                }
            }

            result.setValidatedTables(validatedTables);
            result.setValidatedColumns(validatedColumns);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error validating database schema: " + e.getMessage());
            throw new RuntimeException("Database validation failed: " + e.getMessage(), e);
        }

        return result;
    }

    private boolean checkTableExists(DatabaseMetaData metadata, String tableName) throws Exception {
        // Try with original case
        try (ResultSet rs = metadata.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        // Try uppercase
        try (ResultSet rs = metadata.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        // Try lowercase
        try (ResultSet rs = metadata.getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        return false;
    }

    private boolean checkColumnExists(DatabaseMetaData metadata, String tableName, String columnName) throws Exception {
        // Try with original case
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                return true;
            }
        }

        // Try table uppercase, column original
        try (ResultSet rs = metadata.getColumns(null, null, tableName.toUpperCase(), columnName)) {
            if (rs.next()) {
                return true;
            }
        }

        // Try table lowercase, column original
        try (ResultSet rs = metadata.getColumns(null, null, tableName.toLowerCase(), columnName)) {
            if (rs.next()) {
                return true;
            }
        }

        // Try column uppercase
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName.toUpperCase())) {
            if (rs.next()) {
                return true;
            }
        }

        // Try column lowercase
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName.toLowerCase())) {
            if (rs.next()) {
                return true;
            }
        }

        // Try both uppercase
        try (ResultSet rs = metadata.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) {
                return true;
            }
        }

        // Try both lowercase
        try (ResultSet rs = metadata.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
            if (rs.next()) {
                return true;
            }
        }

        return false;
    }
}
