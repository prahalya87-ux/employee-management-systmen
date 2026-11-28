package src;

import java.sql.*;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/** Attendance database operations. */
public class AttendanceService {

    private final EmployeeService employeeService = new EmployeeService();

    /** Saves one attendance record after checking that it is valid. */
    public void markAttendance(Attendance att) throws SQLException {
        if (!employeeService.employeeExists(att.getEmpId()))
            throw new IllegalArgumentException("Employee with ID " + att.getEmpId() + " does not exist.");
        if (att.getDate().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Attendance cannot be marked for a future date.");
        String sql = "INSERT INTO attendance (emp_id, att_date, status) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE status=VALUES(status)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, att.getEmpId());
            ps.setDate(2, Date.valueOf(att.getDate()));
            ps.setString(3, att.getStatus());
            ps.executeUpdate();
        }
    }

    public void submitAttendance(LocalDate date, Map<Integer, String> statuses) throws SQLException {
        if (date == null || date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Attendance cannot be submitted for a future date.");
        if (statuses == null || statuses.isEmpty())
            throw new IllegalArgumentException("Change at least one employee's attendance before submitting.");

        String checkSql = "SELECT 1 FROM employees WHERE emp_id=?";
        String saveSql = "INSERT INTO attendance (emp_id,att_date,status) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE status=VALUES(status)";
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement check = con.prepareStatement(checkSql);
                 PreparedStatement save = con.prepareStatement(saveSql)) {
                for (Map.Entry<Integer, String> entry : statuses.entrySet()) {
                    if (!"Present".equals(entry.getValue()) && !"Absent".equals(entry.getValue()))
                        throw new IllegalArgumentException("Attendance status must be Present or Absent.");
                    check.setInt(1, entry.getKey());
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next())
                            throw new IllegalArgumentException("Employee with ID " + entry.getKey() + " does not exist.");
                    }
                    save.setInt(1, entry.getKey());
                    save.setDate(2, Date.valueOf(date));
                    save.setString(3, entry.getValue());
                    save.addBatch();
                }
                save.executeBatch();
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public int ensureDemoMonth() throws SQLException {
        return ensureDemoAttendance(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    public int ensureDemoAttendance(LocalDate startDate, LocalDate endDate) throws SQLException {
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new IllegalArgumentException("Invalid demo attendance date range.");
        LocalDate finalDate = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;
        if (finalDate.isBefore(startDate)) return 0;
        int inserted = 0;
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement employees = con.prepareStatement(
                    "SELECT emp_id FROM employees WHERE emp_id BETWEEN 101 AND 112");
                 ResultSet rs = employees.executeQuery();
                 PreparedStatement attendance = con.prepareStatement(
                         "INSERT IGNORE INTO attendance (emp_id,att_date,status) VALUES (?,?,?)")) {
                while (rs.next()) {
                    int empId = rs.getInt("emp_id");
                    for (LocalDate day = startDate; !day.isAfter(finalDate); day = day.plusDays(1)) {
                        DayOfWeek weekday = day.getDayOfWeek();
                        if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) continue;
                        attendance.setInt(1, empId);
                        attendance.setDate(2, Date.valueOf(day));
                        attendance.setString(3, (empId * 7 + day.getDayOfMonth() * 3) % 11 == 0 ? "Absent" : "Present");
                        attendance.addBatch();
                    }
                }
                for (int count : attendance.executeBatch()) {
                    if (count > 0) inserted += count;
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
        return inserted;
    }

    public List<Attendance> getHistory(int empId) throws SQLException {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT emp_id, att_date, status FROM attendance WHERE emp_id = ? ORDER BY att_date DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new Attendance(rs.getInt("emp_id"), rs.getDate("att_date").toLocalDate(), rs.getString("status")));
            }
        }
        return list;
    }

    public int countPresentDays(int empId) throws SQLException {
        return count("SELECT COUNT(*) FROM attendance WHERE emp_id = ? AND status = 'Present'", empId, 0, 0);
    }

    /** Absent days in one month - used by the salary calculation. */
    public int countAbsentDaysInMonth(int empId, int month, int year) throws SQLException {
        return count("SELECT COUNT(*) FROM attendance WHERE emp_id = ? AND status = 'Absent' "
                + "AND MONTH(att_date) = ? AND YEAR(att_date) = ?", empId, month, year);
    }

    public String getStatus(int empId, LocalDate date) throws SQLException {
        String sql = "SELECT status FROM attendance WHERE emp_id=? AND att_date=?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("status") : "Absent";
            }
        }
    }

    public int countPresentOnDate(LocalDate date) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM attendance WHERE att_date=? AND status='Present'")) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int countPresentInMonth(int empId, int month, int year) throws SQLException {
        return count("SELECT COUNT(*) FROM attendance WHERE emp_id=? AND status='Present' "
                + "AND MONTH(att_date)=? AND YEAR(att_date)=?", empId, month, year);
    }

    public int countRecordedInMonth(int empId, int month, int year) throws SQLException {
        return count("SELECT COUNT(*) FROM attendance WHERE emp_id=? "
                + "AND MONTH(att_date)=? AND YEAR(att_date)=?", empId, month, year);
    }

    private boolean attendanceExists(int empId, LocalDate date) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM attendance WHERE emp_id = ? AND att_date = ?")) {
            ps.setInt(1, empId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Runs a COUNT query. Parameters 2 and 3 are only set when the SQL has them (month/year > 0). */
    private int count(String sql, int empId, int month, int year) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            if (month > 0) {
                ps.setInt(2, month);
                ps.setInt(3, year);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
