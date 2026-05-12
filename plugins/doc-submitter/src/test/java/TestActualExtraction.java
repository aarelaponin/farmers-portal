import java.sql.*;

public class TestActualExtraction {
    public static void main(String[] args) {
        String recordId = "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78";

        try {
            // Connect to the database directly
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3307/jwdb", "root", "at456vkm");

            System.out.println("=== STEP 1: Check what's in farmer_registry table ===");
            String query1 = "SELECT id, c_agriculturalManagementSkills, c_mainSourceAgriculturalInfo " +
                           "FROM app_fd_farmer_registry WHERE id = ?";
            PreparedStatement ps1 = conn.prepareStatement(query1);
            ps1.setString(1, recordId);
            ResultSet rs1 = ps1.executeQuery();
            if (rs1.next()) {
                System.out.println("Found in farmer_registry:");
                System.out.println("  id: " + rs1.getString("id"));
                System.out.println("  c_agriculturalManagementSkills: " + rs1.getString("c_agriculturalManagementSkills"));
                System.out.println("  c_mainSourceAgriculturalInfo: " + rs1.getString("c_mainSourceAgriculturalInfo"));
            } else {
                System.out.println("Record NOT found in farmer_registry!");
            }

            System.out.println("\n=== STEP 2: Check what's in farmerRegistrationForm table ===");
            String query2 = "SELECT * FROM app_fd_farmerRegistrationForm WHERE id = ?";
            PreparedStatement ps2 = conn.prepareStatement(query2);
            ps2.setString(1, recordId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                System.out.println("Found in farmerRegistrationForm:");
                ResultSetMetaData rsmd = rs2.getMetaData();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String colName = rsmd.getColumnName(i);
                    String value = rs2.getString(i);
                    if (value != null && !value.isEmpty()) {
                        System.out.println("  " + colName + ": " + value);
                    }
                }
            } else {
                System.out.println("Record NOT found in farmerRegistrationForm!");
            }

            System.out.println("\n=== STEP 3: Check if UUID reference fields exist ===");
            String query3 = "SELECT c_activities_data FROM app_fd_farmerRegistrationForm WHERE id = ?";
            try {
                PreparedStatement ps3 = conn.prepareStatement(query3);
                ps3.setString(1, recordId);
                ResultSet rs3 = ps3.executeQuery();
                if (rs3.next()) {
                    System.out.println("c_activities_data: " + rs3.getString(1));
                }
            } catch (SQLException e) {
                System.out.println("Field c_activities_data does not exist: " + e.getMessage());
            }

            System.out.println("\n=== STEP 4: Check farmer_registry table ===");
            String query4 = "SELECT c_activities_data FROM app_fd_farmer_registry WHERE id = ?";
            try {
                PreparedStatement ps4 = conn.prepareStatement(query4);
                ps4.setString(1, recordId);
                ResultSet rs4 = ps4.executeQuery();
                if (rs4.next()) {
                    System.out.println("c_activities_data in farmer_registry: " + rs4.getString(1));
                }
            } catch (SQLException e) {
                System.out.println("Field c_activities_data does not exist in farmer_registry: " + e.getMessage());
            }

            System.out.println("\n=== STEP 5: Check what FormDataDao would load ===");
            // Simulate what FormDataDao.load does
            String[] formTables = {"farmerRegistrationForm", "farmer_registry", "farms_registry"};
            for (String table : formTables) {
                String query = "SELECT * FROM app_fd_" + table + " WHERE id = ?";
                try {
                    PreparedStatement ps = conn.prepareStatement(query);
                    ps.setString(1, recordId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        System.out.println("\nFound record in app_fd_" + table);
                        // Check for our specific fields
                        try {
                            String val1 = rs.getString("c_agriculturalManagementSkills");
                            System.out.println("  HAS c_agriculturalManagementSkills: " + val1);
                        } catch (SQLException e) {
                            System.out.println("  NO c_agriculturalManagementSkills field");
                        }
                        try {
                            String val2 = rs.getString("c_mainSourceAgriculturalInfo");
                            System.out.println("  HAS c_mainSourceAgriculturalInfo: " + val2);
                        } catch (SQLException e) {
                            System.out.println("  NO c_mainSourceAgriculturalInfo field");
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("Table app_fd_" + table + " error: " + e.getMessage());
                }
            }

            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}