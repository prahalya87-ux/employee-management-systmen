package src;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
 
/**
 * Creates connections to the MySQL database using JDBC.
 * Change USER and PASSWORD to match your own MySQL setup.
 */


    private static final String URL =
            "jdbc:mysql://localhost:3306/employee_management?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "SmartBiz2026";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Called once at start-up so connection problems are reported clearly. */
    public static boolean testConnection() {
        try (Connection con = getConnection()) {
            return con != null;
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
            System.out.println("Check that MySQL is running, the database exists,");
            System.out.println("and USER / PASSWORD in DatabaseConnection.java are correct.");
            return false;
        }
    }
}