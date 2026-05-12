package global.govstack.farmreg.registration.service;

import org.junit.Test;

import java.sql.*;

import static org.junit.Assert.*;

/**
 * Unit test to verify formId vs tableName in Joget's app_form table
 *
 * This test queries the database directly to prove:
 * 1. formId = "householdMemberForm" exists in app_form table
 * 2. tableName for that formId is "household_members"
 * 3. Actual database table is "app_fd_household_members" (with app_fd_ prefix)
 */
public class GridExtractionTest {

    private static final String DB_URL = "jdbc:mysql://localhost:3307/jwdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "at456vkm";

    @Test
    public void testJogetFormDefinitions() throws SQLException {
        System.out.println("\n=== Testing Joget Form Definitions ===");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            // Test 1: Verify householdMemberForm exists in app_form
            String query1 = "SELECT formId, name, tableName FROM app_form WHERE formId = 'householdMemberForm'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {

                assertTrue("householdMemberForm should exist in app_form", rs.next());

                String formId = rs.getString("formId");
                String name = rs.getString("name");
                String tableName = rs.getString("tableName");

                System.out.println(String.format("FormId: %s, Name: %s, TableName: %s",
                    formId, name, tableName));

                assertEquals("FormId should be householdMemberForm", "householdMemberForm", formId);
                assertEquals("TableName should be household_members", "household_members", tableName);
            }

            // Test 2: Find actual household member record and get its farmer_id
            String query2 = "SELECT id, c_farmer_id, c_memberName FROM app_fd_household_members WHERE id = '8fa09d5f-a30f-428a-96f6-389267d689b3'";
            String actualFarmerId = null;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {

                assertTrue("Should find record with specific ID", rs.next());
                String id = rs.getString("id");
                actualFarmerId = rs.getString("c_farmer_id");
                String memberName = rs.getString("c_memberName");

                System.out.println(String.format("Found record - ID: %s, Farmer ID: %s, Name: %s",
                    id, actualFarmerId, memberName));
                assertEquals("Should find exact record", "8fa09d5f-a30f-428a-96f6-389267d689b3", id);
            }

            // Test 3: Verify we can query by farmer_id
            assertNotNull("Farmer ID should not be null", actualFarmerId);
            String query3 = "SELECT COUNT(*) as cnt FROM app_fd_household_members WHERE c_farmer_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query3)) {
                stmt.setString(1, actualFarmerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    int count = rs.getInt("cnt");
                    System.out.println(String.format("Records for farmer %s: %d", actualFarmerId, count));
                    assertTrue("Should have at least 1 household member record", count > 0);
                }
            }

            System.out.println("\n✓ All database verifications passed");
            System.out.println("✓ CONCLUSION: GenericFormDataExtractor should use formId='householdMemberForm', NOT tableName='household_members'");
        }
    }

    @Test
    public void testAllGridForms() throws SQLException {
        System.out.println("\n=== Testing All Grid Form Definitions ===");

        String[][] expectedGrids = {
            {"householdMemberForm", "household_members", "app_fd_household_members"},
            {"cropManagementForm", "crop_management", "app_fd_crop_management"},
            {"livestockDetailsForm", "livestock_details", "app_fd_livestock_details"}
        };

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            for (String[] grid : expectedGrids) {
                String formId = grid[0];
                String expectedTableName = grid[1];
                String actualDbTable = grid[2];

                // Verify form definition
                String query = "SELECT formId, tableName FROM app_form WHERE formId = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, formId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(formId + " should exist in app_form", rs.next());

                        String tableName = rs.getString("tableName");
                        System.out.println(String.format("%s -> tableName: %s (actual DB table: %s)",
                            formId, tableName, actualDbTable));

                        assertEquals("TableName should match", expectedTableName, tableName);
                    }
                }
            }

            System.out.println("\n✓ All grid forms verified");
        }
    }
}
