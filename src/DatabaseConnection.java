package src;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
 
    private static final String URL = System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:mysql://localhost:3306/employee_management?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

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
            System.out.println("and DB_URL, DB_USER, and DB_PASSWORD are configured correctly.");
            return false;
        }
    }

    /** Creates or extends the HR tables when the application starts. */
    public static void initializeSchema() throws SQLException {
        String[] statements = {
            "CREATE TABLE IF NOT EXISTS users (user_id INT AUTO_INCREMENT PRIMARY KEY, emp_id INT NULL, username VARCHAR(100) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, role ENUM('ADMIN','EMPLOYEE') NOT NULL, last_login TIMESTAMP NULL, FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE SET NULL)",
            "CREATE TABLE IF NOT EXISTS leave_requests (leave_id INT AUTO_INCREMENT PRIMARY KEY, emp_id INT NOT NULL, leave_type ENUM('Casual Leave','Sick Leave','Earned Leave') NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, reason VARCHAR(500) NOT NULL, status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING', decided_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS leave_balances (emp_id INT NOT NULL, leave_type ENUM('Casual Leave','Sick Leave','Earned Leave') NOT NULL, allocated_days INT NOT NULL, used_days INT NOT NULL DEFAULT 0, PRIMARY KEY (emp_id, leave_type), FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS salary_slips (slip_id INT AUTO_INCREMENT PRIMARY KEY, emp_id INT NOT NULL, salary_month TINYINT NOT NULL, salary_year SMALLINT NOT NULL, basic_salary DECIMAL(10,2) NOT NULL, hra DECIMAL(10,2) NOT NULL, allowances DECIMAL(10,2) NOT NULL DEFAULT 0, bonus DECIMAL(10,2) NOT NULL, deductions DECIMAL(10,2) NOT NULL, gross_salary DECIMAL(10,2) NOT NULL, net_salary DECIMAL(10,2) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (emp_id, salary_month, salary_year), FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE)"
        };
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            executeOptionalColumn(st, "ALTER TABLE employees ADD COLUMN date_of_birth DATE NULL");
            executeOptionalColumn(st, "ALTER TABLE employees ADD COLUMN address VARCHAR(255) NULL");
            executeOptionalColumn(st, "ALTER TABLE employees ADD COLUMN employment_status ENUM('Active','Inactive','On Leave') NOT NULL DEFAULT 'Active'");
            executeOptionalColumn(st, "ALTER TABLE employees ADD COLUMN deactivated_at TIMESTAMP NULL");
            for (String sql : statements) st.execute(sql);
        }
    }

    private static void executeOptionalColumn(Statement st, String sql) throws SQLException {
        try {
            st.execute(sql);
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) throw e;
        }
    }
}