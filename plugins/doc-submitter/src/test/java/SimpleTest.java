import java.sql.*;

public class SimpleTest {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection(
            "jdbc:mysql://localhost:3307/jwdb", "root", "at456vkm");

        String query = "SELECT c_agriculturalManagementSkills, c_mainSourceAgriculturalInfo " +
                      "FROM app_fd_farms_registry WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78");
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            System.out.println("VALUES IN SENDER DATABASE:");
            System.out.println("c_agriculturalManagementSkills: " + rs.getString(1));
            System.out.println("c_mainSourceAgriculturalInfo: " + rs.getString(2));
        } else {
            System.out.println("Record not found");
        }

        conn.close();
    }
}