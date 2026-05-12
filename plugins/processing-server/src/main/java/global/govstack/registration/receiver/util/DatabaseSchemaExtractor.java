package global.govstack.registration.receiver.util;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.commons.util.LogUtil;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility to extract actual database schema information for forms
 * This provides 100% accurate table and column mappings
 */
public class DatabaseSchemaExtractor {
    private static final String CLASS_NAME = DatabaseSchemaExtractor.class.getName();

    private DataSource dataSource;
    private FormDataDao formDataDao;
    private AppService appService;
    private String appId;

    public DatabaseSchemaExtractor() {
        try {
            ApplicationContext appContext = AppUtil.getApplicationContext();

            // Get the data source
            this.dataSource = (DataSource) appContext.getBean("setupDataSource");

            // Get FormDataDao for additional queries
            this.formDataDao = (FormDataDao) appContext.getBean("formDataDao");

            // Get AppService
            this.appService = (AppService) appContext.getBean("appService");

            // Get current app
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef != null) {
                this.appId = appDef.getId();
            }

            LogUtil.info(CLASS_NAME, "DatabaseSchemaExtractor initialized successfully");
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Failed to initialize DatabaseSchemaExtractor");
        }
    }

    /**
     * Extract complete schema information
     * @return Map with table names as keys and table info as values
     */
    public Map<String, TableInfo> extractSchema() {
        Map<String, TableInfo> schema = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            // Get database metadata
            DatabaseMetaData metadata = conn.getMetaData();
            String catalog = conn.getCatalog();

            LogUtil.info(CLASS_NAME, "Connected to database: " + catalog);
            LogUtil.info(CLASS_NAME, "Database product: " + metadata.getDatabaseProductName());

            // Get all tables that might be form tables
            List<String> formTables = findFormTables(conn, metadata);

            // Extract schema for each table
            for (String tableName : formTables) {
                TableInfo tableInfo = extractTableInfo(conn, metadata, tableName);
                if (tableInfo != null) {
                    schema.put(tableName, tableInfo);
                }
            }

            LogUtil.info(CLASS_NAME, "Extracted schema for " + schema.size() + " tables");

        } catch (SQLException e) {
            LogUtil.error(CLASS_NAME, e, "Failed to extract schema");
        }

        return schema;
    }

    /**
     * Find all tables that appear to be form tables
     */
    private List<String> findFormTables(Connection conn, DatabaseMetaData metadata) throws SQLException {
        List<String> tables = new ArrayList<>();

        // Get all tables from INFORMATION_SCHEMA (no filtering for truly generic operation)
        String query = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                      "WHERE TABLE_SCHEMA = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, conn.getCatalog());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                    LogUtil.debug(CLASS_NAME, "Found table: " + tableName);
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "INFORMATION_SCHEMA query failed, using metadata: " + e.getMessage());

            // Fallback to DatabaseMetaData
            try (ResultSet rs = metadata.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // No filtering - discover all tables for truly generic operation
                    tables.add(tableName);
                    LogUtil.debug(CLASS_NAME, "Found table via metadata: " + tableName);
                }
            }
        }

        return tables;
    }

    /**
     * Extract detailed information about a table
     */
    private TableInfo extractTableInfo(Connection conn, DatabaseMetaData metadata, String tableName) {
        TableInfo tableInfo = new TableInfo(tableName);

        try {
            // Get columns
            try (ResultSet rs = metadata.getColumns(conn.getCatalog(), null, tableName, null)) {
                while (rs.next()) {
                    ColumnInfo column = new ColumnInfo();
                    column.name = rs.getString("COLUMN_NAME");
                    column.dataType = rs.getString("TYPE_NAME");
                    column.size = rs.getInt("COLUMN_SIZE");
                    column.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    column.defaultValue = rs.getString("COLUMN_DEF");

                    tableInfo.columns.add(column);
                    LogUtil.debug(CLASS_NAME, "  Column: " + column.name + " (" + column.dataType + ")");
                }
            }

            // Get primary keys
            try (ResultSet rs = metadata.getPrimaryKeys(conn.getCatalog(), null, tableName)) {
                while (rs.next()) {
                    String pkColumn = rs.getString("COLUMN_NAME");
                    tableInfo.primaryKeys.add(pkColumn);
                    LogUtil.debug(CLASS_NAME, "  Primary Key: " + pkColumn);
                }
            }

            // Get foreign keys (to identify parent-child relationships)
            try (ResultSet rs = metadata.getImportedKeys(conn.getCatalog(), null, tableName)) {
                while (rs.next()) {
                    ForeignKeyInfo fk = new ForeignKeyInfo();
                    fk.columnName = rs.getString("FKCOLUMN_NAME");
                    fk.referencedTable = rs.getString("PKTABLE_NAME");
                    fk.referencedColumn = rs.getString("PKCOLUMN_NAME");

                    tableInfo.foreignKeys.add(fk);
                    LogUtil.debug(CLASS_NAME, "  Foreign Key: " + fk.columnName +
                                 " -> " + fk.referencedTable + "(" + fk.referencedColumn + ")");
                }
            }

            // Get indexes
            try (ResultSet rs = metadata.getIndexInfo(conn.getCatalog(), null, tableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    if (indexName != null && columnName != null) {
                        tableInfo.indexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                    }
                }
            }

            // Try to get row count for context
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
                if (rs.next()) {
                    tableInfo.rowCount = rs.getLong(1);
                }
            } catch (SQLException e) {
                // Ignore count errors
            }

        } catch (SQLException e) {
            LogUtil.error(CLASS_NAME, e, "Failed to extract info for table: " + tableName);
            return null;
        }

        return tableInfo;
    }

    /**
     * Generate a detailed schema report
     */
    public String generateSchemaReport(Map<String, TableInfo> schema) {
        StringBuilder report = new StringBuilder();
        report.append("# Database Schema Report\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        report.append("## Summary\n");
        report.append("Total Tables: ").append(schema.size()).append("\n\n");

        report.append("## Table Details\n\n");

        for (Map.Entry<String, TableInfo> entry : schema.entrySet()) {
            TableInfo table = entry.getValue();

            report.append("### Table: `").append(table.tableName).append("`\n");
            report.append("- Columns: ").append(table.columns.size()).append("\n");
            report.append("- Primary Keys: ").append(String.join(", ", table.primaryKeys)).append("\n");
            report.append("- Row Count: ").append(table.rowCount).append("\n\n");

            report.append("#### Columns:\n");
            report.append("| Column Name | Data Type | Size | Nullable | Default |\n");
            report.append("|-------------|-----------|------|----------|---------|\\n");

            for (ColumnInfo col : table.columns) {
                report.append("| `").append(col.name).append("` ");
                report.append("| ").append(col.dataType).append(" ");
                report.append("| ").append(col.size > 0 ? col.size : "-").append(" ");
                report.append("| ").append(col.nullable ? "YES" : "NO").append(" ");
                report.append("| ").append(col.defaultValue != null ? col.defaultValue : "-").append(" |\n");
            }

            if (!table.foreignKeys.isEmpty()) {
                report.append("\n#### Foreign Keys:\n");
                for (ForeignKeyInfo fk : table.foreignKeys) {
                    report.append("- `").append(fk.columnName).append("` -> `");
                    report.append(fk.referencedTable).append("(").append(fk.referencedColumn).append(")`\n");
                }
            }

            if (!table.indexes.isEmpty()) {
                report.append("\n#### Indexes:\n");
                for (Map.Entry<String, List<String>> index : table.indexes.entrySet()) {
                    report.append("- ").append(index.getKey()).append(": ");
                    report.append(String.join(", ", index.getValue())).append("\n");
                }
            }

            report.append("\n---\n\n");
        }

        return report.toString();
    }

    /**
     * Save schema report to file
     */
    public void saveSchemaReport(String filename) {
        try {
            Map<String, TableInfo> schema = extractSchema();
            String report = generateSchemaReport(schema);

            // Write to file
            java.nio.file.Path path = java.nio.file.Paths.get(filename);
            java.nio.file.Files.write(path, report.getBytes());

            LogUtil.info(CLASS_NAME, "Schema report saved to: " + filename);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Failed to save schema report");
        }
    }

    /**
     * Get schema as JSON for programmatic use
     */
    public String getSchemaAsJson() {
        try {
            Map<String, TableInfo> schema = extractSchema();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Failed to convert schema to JSON");
            return "{}";
        }
    }

    // Inner classes for schema information
    public static class TableInfo {
        public String tableName;
        public List<ColumnInfo> columns = new ArrayList<>();
        public List<String> primaryKeys = new ArrayList<>();
        public List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        public Map<String, List<String>> indexes = new HashMap<>();
        public long rowCount;

        public TableInfo(String tableName) {
            this.tableName = tableName;
        }
    }

    public static class ColumnInfo {
        public String name;
        public String dataType;
        public int size;
        public boolean nullable;
        public String defaultValue;
    }

    public static class ForeignKeyInfo {
        public String columnName;
        public String referencedTable;
        public String referencedColumn;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        DatabaseSchemaExtractor extractor = new DatabaseSchemaExtractor();

        // Extract and print schema
        Map<String, TableInfo> schema = extractor.extractSchema();

        // Generate report
        String report = extractor.generateSchemaReport(schema);
        System.out.println(report);

        // Save to file
        extractor.saveSchemaReport("database-schema-report.md");

        // Get as JSON
        String json = extractor.getSchemaAsJson();
        System.out.println("\nSchema as JSON:\n" + json);
    }
}