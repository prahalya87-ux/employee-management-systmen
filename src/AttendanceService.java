package src;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
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
        if (attendanceExists(att.getEmpId(), att.getDate()))
            throw new IllegalArgumentException("Attendance for this employee on " + att.getDate() + " is already marked.");

        String sql = "INSERT INTO attendance (emp_id, att_date, status) VALUES (?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, att.getEmpId());
            ps.setDate(2, Date.valueOf(att.getDate()));
            ps.setString(3, att.getStatus());
            ps.executeUpdate();
        }
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
