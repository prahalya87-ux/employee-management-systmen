package src;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HRService {
    private static final SecureRandom RANDOM = new SecureRandom();

    public void ensureAdmin() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement check = con.prepareStatement("SELECT 1 FROM users WHERE role='ADMIN' LIMIT 1");
             ResultSet rs = check.executeQuery()) {
            if (rs.next()) return;
        }
        boolean production = "production".equalsIgnoreCase(System.getenv("APP_ENV"));
        String username = System.getenv("ADMIN_USERNAME");
        String password = System.getenv("ADMIN_PASSWORD");
        if (production && (username == null || username.trim().isEmpty()
                || password == null || password.length() < 12)) {
            throw new SQLException("Set ADMIN_USERNAME and an ADMIN_PASSWORD of at least 12 characters.");
        }
        createUser(null, username == null || username.trim().isEmpty() ? "admin" : username,
                password == null || password.isEmpty() ? "admin123" : password, "ADMIN");
    }

    public String ensureAdithyaDemoAccount() throws SQLException {
        Integer empId = null;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT emp_id FROM employees WHERE LOWER(email)=? OR LOWER(name)=? ORDER BY emp_id LIMIT 1")) {
            ps.setString(1, "adithyac@company.com");
            ps.setString(2, "adithya");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) empId = rs.getInt("emp_id");
            }
        }
        if (empId == null) return null;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT username FROM users WHERE emp_id=?")) {
            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return null;
            }
        }

        String username = "adithya";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) username += empId;
            }
        }
        createUser(empId, username, "adithya123", "EMPLOYEE");
        return username;
    }

    public void createUser(Integer empId, String username, String password, String role) throws SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.length() < 6)
            throw new IllegalArgumentException("Username is required and password must be at least 6 characters.");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String hash = encodeHash(salt, hashPassword(password.toCharArray(), salt));
        String sql = "INSERT INTO users (emp_id, username, password_hash, role) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE emp_id=VALUES(emp_id), password_hash=VALUES(password_hash), role=VALUES(role)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (empId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, empId);
            ps.setString(2, username.trim());
            ps.setString(3, hash);
            ps.setString(4, role);
            ps.executeUpdate();
        }
    }

    public Map<String, String> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT u.emp_id, u.username, u.password_hash, u.role FROM users u "
                + "LEFT JOIN employees e ON e.emp_id=u.emp_id "
                + "WHERE u.username=? OR e.email=? LIMIT 1";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Invalid username or password.");
                if (!passwordMatches(password, rs.getString("password_hash")))
                    throw new IllegalArgumentException("Invalid username or password.");
                int empId = rs.getInt("emp_id");
                boolean empIdNull = rs.wasNull();
                Map<String, String> user = new HashMap<>();
                user.put("username", rs.getString("username"));
                user.put("role", rs.getString("role"));
                user.put("empId", empIdNull ? "" : String.valueOf(empId));
                try (PreparedStatement login = con.prepareStatement(
                        "UPDATE users SET last_login=CURRENT_TIMESTAMP WHERE username=?")) {
                    login.setString(1, user.get("username"));
                    login.executeUpdate();
                }
                return user;
            }
        }
    }

    public void initializeLeaveBalances(int empId) throws SQLException {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("Casual Leave", 12);
        defaults.put("Sick Leave", 10);
        defaults.put("Earned Leave", 15);
        String sql = "INSERT IGNORE INTO leave_balances (emp_id, leave_type, allocated_days, used_days) VALUES (?,?,?,0)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
                ps.setInt(1, empId);
                ps.setString(2, entry.getKey());
                ps.setInt(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void applyLeave(int empId, String type, LocalDate start, LocalDate end, String reason) throws SQLException {
        if (start.isBefore(LocalDate.now())) throw new IllegalArgumentException("Leave cannot start in the past.");
        if (end.isBefore(start)) throw new IllegalArgumentException("End date cannot be before start date.");
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1) throw new IllegalArgumentException("Invalid leave period.");

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                initializeLeaveBalances(con, empId);
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT allocated_days-used_days AS remaining FROM leave_balances WHERE emp_id=? AND leave_type=? FOR UPDATE")) {
                    ps.setInt(1, empId);
                    ps.setString(2, type);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getInt("remaining") < days)
                            throw new IllegalArgumentException("Not enough leave balance.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT 1 FROM leave_requests WHERE emp_id=? AND status='APPROVED' "
                        + "AND NOT (end_date < ? OR start_date > ?) LIMIT 1")) {
                    ps.setInt(1, empId);
                    ps.setDate(2, Date.valueOf(start));
                    ps.setDate(3, Date.valueOf(end));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new IllegalArgumentException("This period overlaps an approved leave.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO leave_requests (emp_id,leave_type,start_date,end_date,reason,status) "
                        + "VALUES (?,?,?,?,?, 'PENDING')")) {
                    ps.setInt(1, empId);
                    ps.setString(2, type);
                    ps.setDate(3, Date.valueOf(start));
                    ps.setDate(4, Date.valueOf(end));
                    ps.setString(5, reason);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void decideLeave(int leaveId, String status) throws SQLException {
        if (!status.equals("APPROVED") && !status.equals("REJECTED"))
            throw new IllegalArgumentException("Invalid leave status.");
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int empId;
                String type;
                LocalDate start;
                LocalDate end;
                String current;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT emp_id,leave_type,start_date,end_date,status FROM leave_requests WHERE leave_id=? FOR UPDATE")) {
                    ps.setInt(1, leaveId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Leave request not found.");
                        empId = rs.getInt("emp_id");
                        type = rs.getString("leave_type");
                        start = rs.getDate("start_date").toLocalDate();
                        end = rs.getDate("end_date").toLocalDate();
                        current = rs.getString("status");
                    }
                }
                if (!"PENDING".equals(current))
                    throw new IllegalArgumentException("This leave request has already been decided.");
                if ("APPROVED".equals(status)) {
                    try (PreparedStatement overlap = con.prepareStatement(
                            "SELECT 1 FROM leave_requests WHERE leave_id<>? AND emp_id=? AND status='APPROVED' "
                            + "AND NOT (end_date < ? OR start_date > ?) LIMIT 1")) {
                        overlap.setInt(1, leaveId);
                        overlap.setInt(2, empId);
                        overlap.setDate(3, Date.valueOf(start));
                        overlap.setDate(4, Date.valueOf(end));
                        try (ResultSet rs = overlap.executeQuery()) {
                            if (rs.next()) throw new IllegalArgumentException("This period overlaps an approved leave.");
                        }
                    }
                    long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE leave_balances SET used_days=used_days+? "
                            + "WHERE emp_id=? AND leave_type=?")) {
                        ps.setLong(1, days);
                        ps.setInt(2, empId);
                        ps.setString(3, type);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE leave_requests SET status=?, decided_at=CURRENT_TIMESTAMP WHERE leave_id=?")) {
                    ps.setString(1, status);
                    ps.setInt(2, leaveId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public List<Map<String, Object>> leaveRequests(Integer empId) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = empId == null
                ? "SELECT l.*, e.name FROM leave_requests l JOIN employees e ON e.emp_id=l.emp_id ORDER BY l.created_at DESC"
                : "SELECT l.*, e.name FROM leave_requests l JOIN employees e ON e.emp_id=l.emp_id WHERE l.emp_id=? ORDER BY l.created_at DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (empId != null) ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("leaveId", rs.getInt("leave_id"));
                    row.put("empId", rs.getInt("emp_id"));
                    row.put("employeeName", rs.getString("name"));
                    row.put("leaveType", rs.getString("leave_type"));
                    row.put("startDate", rs.getDate("start_date").toString());
                    row.put("endDate", rs.getDate("end_date").toString());
                    row.put("reason", rs.getString("reason"));
                    row.put("status", rs.getString("status"));
                    result.add(row);
                }
            }
        }
        return result;
    }

    public List<Map<String, Object>> leaveBalances(int empId) throws SQLException {
        initializeLeaveBalances(empId);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT leave_type,allocated_days,used_days FROM leave_balances WHERE emp_id=?")) {
            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("leaveType", rs.getString("leave_type"));
                    row.put("allocated", rs.getInt("allocated_days"));
                    row.put("used", rs.getInt("used_days"));
                    row.put("remaining", rs.getInt("allocated_days") - rs.getInt("used_days"));
                    result.add(row);
                }
            }
        }
        return result;
    }

    private void initializeLeaveBalances(Connection con, int empId) throws SQLException {
        String sql = "INSERT IGNORE INTO leave_balances (emp_id,leave_type,allocated_days,used_days) VALUES (?,?,?,0)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Object[] b : new Object[][]{{"Casual Leave",12},{"Sick Leave",10},{"Earned Leave",15}}) {
                ps.setInt(1, empId);
                ps.setString(2, (String)b[0]);
                ps.setInt(3, (Integer)b[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static byte[] hashPassword(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, 10000, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing is unavailable.", e);
        }
    }

    private static String encodeHash(byte[] salt, byte[] hash) {
        return Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean passwordMatches(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            byte[] actual = hashPassword(password.toCharArray(), salt);
            if (expected.length != actual.length) return false;
            int diff = 0;
            for (int i = 0; i < expected.length; i++) diff |= expected[i] ^ actual[i];
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
