package global.govstack.registration.sender.util;

import global.govstack.registration.sender.model.ValidationResult;
import global.govstack.registration.sender.service.DatabaseSchemaValidator;

import javax.sql.DataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Standalone validator that can be run independently to validate database schema
 * against form_structure.yaml
 *
 * Usage:
 * java -cp target/doc-submitter-8.1-SNAPSHOT.jar:path/to/mysql-connector.jar \
 *   global.govstack.farmreg.registration.util.StandaloneDatabaseValidator \
 *   <db_host> <db_port> <db_name> <db_user> <db_password> <yaml_file_path>
 */
public class StandaloneDatabaseValidator {

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Usage: StandaloneDatabaseValidator <db_host> <db_port> <db_name> <db_user> <db_password> <yaml_file_path>");
            System.out.println("Example: StandaloneDatabaseValidator localhost 3307 jwdb root at456vkm src/main/resources/docs-metadata/form_structure.yaml");
            System.exit(1);
        }

        String dbHost = args[0];
        String dbPort = args[1];
        String dbName = args[2];
        String dbUser = args[3];
        String dbPassword = args[4];
        String yamlFilePath = args[5];

        try {
            // Create MySQL DataSource
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setServerName(dbHost);
            dataSource.setPort(Integer.parseInt(dbPort));
            dataSource.setDatabaseName(dbName);
            dataSource.setUser(dbUser);
            dataSource.setPassword(dbPassword);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("Database Schema Validation");
            System.out.println("=".repeat(60));
            System.out.println("Database: " + dbName + "@" + dbHost + ":" + dbPort);
            System.out.println("YAML File: " + yamlFilePath);
            System.out.println("=".repeat(60) + "\n");

            // Parse YAML
            YamlSchemaParser parser = new YamlSchemaParser();
            Map<String, Set<String>> tableColumnMap;

            try (InputStream yamlStream = new FileInputStream(yamlFilePath)) {
                tableColumnMap = parser.parseTableColumnMappings(yamlStream);
            }

            System.out.println("Parsed " + tableColumnMap.size() + " tables from YAML\n");

            // Validate against database
            DatabaseSchemaValidator validator = new DatabaseSchemaValidator(dataSource);
            ValidationResult result = validator.validate(tableColumnMap);

            // Generate and print report
            ValidationReportGenerator reportGenerator = new ValidationReportGenerator();
            String report = reportGenerator.generateReport(result);
            System.out.println(report);

            // Exit with appropriate code
            if (result.hasErrors()) {
                System.exit(1); // Error exit code
            } else {
                System.exit(0); // Success
            }

        } catch (Exception e) {
            System.err.println("ERROR: Validation failed - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
