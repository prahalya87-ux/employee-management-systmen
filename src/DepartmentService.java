package src;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Department database operations. */
public class DepartmentService {

    public void addDepartment(Department dept) throws SQLException {
        if (nameInUse(dept.getDeptName(), 0))
            throw new IllegalArgumentException("A department named '" + dept.getDeptName() + "' already exists.");
        String sql = "INSERT INTO departments (dept_name, location) VALUES (?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, dept.getDeptName());
            ps.setString(2, dept.getLocation());
            ps.executeUpdate();
        }
    }

    public List<Department> getAllDepartments() throws SQLException {
        List<Department> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM departments ORDER BY dept_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                list.add(new Department(rs.getInt("dept_id"), rs.getString("dept_name"), rs.getString("location")));
        }
        return list;
    }

    public boolean departmentExists(int deptId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM departments WHERE dept_id = ?")) {
            ps.setInt(1, deptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void updateDepartment(Department dept) throws SQLException {
        if (nameInUse(dept.getDeptName(), dept.getDeptId()))
            throw new IllegalArgumentException("A department named '" + dept.getDeptName() + "' already exists.");
        String sql = "UPDATE departments SET dept_name = ?, location = ? WHERE dept_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, dept.getDeptName());
            ps.setString(2, dept.getLocation());
            ps.setInt(3, dept.getDeptId());
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Department not found.");
        }
    }

    /** A department that still has employees cannot be deleted. */
    public void deleteDepartment(int deptId) throws SQLException {
        if (!departmentExists(deptId)) throw new IllegalArgumentException("Department not found.");
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement check = con.prepareStatement("SELECT COUNT(*) FROM employees WHERE dept_id = ?")) {
            check.setInt(1, deptId);
            try (ResultSet rs = check.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0)
                    throw new IllegalArgumentException("Cannot delete: this department still has employees. Reassign them first.");
            }
            try (PreparedStatement del = con.prepareStatement("DELETE FROM departments WHERE dept_id = ?")) {
                del.setInt(1, deptId);
                del.executeUpdate();
            }
        }
    }

    /** Puts an employee into a department. */
    public void assignEmployee(int empId, int deptId) throws SQLException {
        if (!departmentExists(deptId)) throw new IllegalArgumentException("Department not found.");
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE employees SET dept_id = ? WHERE emp_id = ?")) {
            ps.setInt(1, deptId);
            ps.setInt(2, empId);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Employee not found.");
        }
    }

    private boolean nameInUse(String name, int excludeDeptId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM departments WHERE dept_name = ? AND dept_id <> ?")) {
            ps.setString(1, name);
            ps.setInt(2, excludeDeptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
