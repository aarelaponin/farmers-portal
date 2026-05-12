package global.govstack.farmreg.registration;

import global.govstack.farmreg.registration.model.ValidationResult;
import global.govstack.farmreg.registration.service.DatabaseSchemaValidator;
import global.govstack.farmreg.registration.util.ValidationReportGenerator;
import global.govstack.farmreg.registration.util.YamlSchemaParser;
import org.joget.commons.util.LogUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DatabaseSchemaValidationTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metadata;

    @Mock
    private ResultSet tableResultSet;

    @Mock
    private ResultSet columnResultSet;

    private YamlSchemaParser parser;
    private DatabaseSchemaValidator validator;
    private ValidationReportGenerator reportGenerator;

    @Before
    public void setUp() throws Exception {
        parser = new YamlSchemaParser();
        validator = new DatabaseSchemaValidator(dataSource);
        reportGenerator = new ValidationReportGenerator();

        // Setup mocks
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("8.0.0");
    }

    @Test
    public void testYamlParsing() {
        // Load the YAML file from resources
        InputStream yamlStream = getClass().getClassLoader()
            .getResourceAsStream("docs-metadata/form_structure.yaml");

        assertNotNull("YAML file should be found in resources", yamlStream);

        Map<String, Set<String>> tableColumnMap = parser.parseTableColumnMappings(yamlStream);

        assertNotNull("Table-column map should not be null", tableColumnMap);
        assertFalse("Table-column map should not be empty", tableColumnMap.isEmpty());

        System.out.println("\n=== YAML Parsing Results ===");
        System.out.println("Total tables found: " + tableColumnMap.size());
        for (Map.Entry<String, Set<String>> entry : tableColumnMap.entrySet()) {
            System.out.println("  - " + entry.getKey() + " (" + entry.getValue().size() + " columns)");
        }

        // Verify expected tables exist
        assertTrue("Should contain farmer_basic_data table",
            tableColumnMap.containsKey("farmer_basic_data"));
        assertTrue("Should contain farm_location table",
            tableColumnMap.containsKey("farm_location"));
        assertTrue("Should contain crop_management table",
            tableColumnMap.containsKey("crop_management"));
    }

    @Test
    public void testDatabaseValidationWithMockedDatabase() throws Exception {
        // Setup table exists mock
        when(metadata.getTables(any(), any(), anyString(), any()))
            .thenReturn(tableResultSet);
        when(tableResultSet.next())
            .thenReturn(true) // First call: table exists
            .thenReturn(false); // Subsequent calls

        // Setup column exists mock
        when(metadata.getColumns(any(), any(), anyString(), anyString()))
            .thenReturn(columnResultSet);
        when(columnResultSet.next())
            .thenReturn(true) // All columns exist
            .thenReturn(false);

        // Load YAML
        InputStream yamlStream = getClass().getClassLoader()
            .getResourceAsStream("docs-metadata/form_structure.yaml");
        Map<String, Set<String>> tableColumnMap = parser.parseTableColumnMappings(yamlStream);

        // Validate
        ValidationResult result = validator.validate(tableColumnMap);

        // Generate report
        String report = reportGenerator.generateReport(result);
        System.out.println(report);

        // Assertions
        assertNotNull("Validation result should not be null", result);
        assertTrue("Total tables should be greater than 0", result.getTotalTables() > 0);
        assertTrue("Total columns should be greater than 0", result.getTotalColumns() > 0);
    }

    @Test
    public void testReportGeneration() {
        ValidationResult result = new ValidationResult();
        result.setTotalTables(3);
        result.setValidatedTables(2);
        result.setTotalColumns(10);
        result.setValidatedColumns(8);

        result.addMissingTable("missing_table");
        result.addMissingColumn("farmer_basic_data", "c_missing_column");

        String report = reportGenerator.generateReport(result);

        assertNotNull("Report should not be null", report);
        assertTrue("Report should contain validation results",
            report.contains("VALIDATION RESULTS"));
        assertTrue("Report should show missing table",
            report.contains("missing_table"));
        assertTrue("Report should show missing column",
            report.contains("farmer_basic_data.c_missing_column"));

        System.out.println(report);
    }

    /**
     * Manual test to run against actual database.
     * This requires Joget to be running and configured.
     *
     * To run this manually:
     * 1. Ensure you have access to a running Joget instance
     * 2. Configure the datasource connection
     * 3. Run this test
     */
    @Test
    public void manualTestAgainstRealDatabase() {
        System.out.println("\n=== Manual Database Validation Test ===");
        System.out.println("This test requires manual setup with real database connection.");
        System.out.println("To use this test:");
        System.out.println("1. Configure DataSource connection in your test environment");
        System.out.println("2. Uncomment the implementation below");
        System.out.println("3. Run the test\n");

        // UNCOMMENT AND MODIFY BELOW FOR MANUAL TESTING:
        /*
        try {
            // Get real datasource from Joget
            DataSource realDataSource = (DataSource) AppUtil.getApplicationContext()
                .getBean("setupDataSource");

            DatabaseSchemaValidator realValidator = new DatabaseSchemaValidator(realDataSource);

            // Load YAML
            InputStream yamlStream = getClass().getClassLoader()
                .getResourceAsStream("docs-metadata/form_structure.yaml");
            Map<String, Set<String>> tableColumnMap = parser.parseTableColumnMappings(yamlStream);

            // Validate
            ValidationResult result = realValidator.validate(tableColumnMap);

            // Generate and print report
            String report = reportGenerator.generateReport(result);
            System.out.println(report);

            // Optional: Fail test if there are errors
            if (result.hasErrors()) {
                fail("Database validation found " + result.getErrorCount() + " issues");
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Manual database validation failed");
            e.printStackTrace();
        }
        */
    }
}
