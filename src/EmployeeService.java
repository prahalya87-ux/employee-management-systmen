package src;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All employee database operations (CRUD, search and reports) using JDBC PreparedStatements. */
public class EmployeeService {

    private static final String SELECT_BASE =
            "SELECT e.*, d.dept_name FROM employees e LEFT JOIN departments d ON e.dept_id = d.dept_id ";

    // ---------------- Create ----------------
    public int getNextEmployeeId() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(MAX(emp_id), 1000) + 1 FROM employees");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            int next = rs.getInt(1);
            return Math.max(next, 1001);
        }
    }

    public void addEmployee(Employee emp) throws SQLException {
        if (employeeExists(emp.getEmpId()))
            throw new IllegalArgumentException("Employee ID " + emp.getEmpId() + " already exists.");
        if (emailInUse(emp.getEmail(), 0))
            throw new IllegalArgumentException("Email " + emp.getEmail() + " is already used by another employee.");

        String sql = "INSERT INTO employees (emp_id, name, age, gender, email, phone, dept_id, "
                + "job_role, joining_date, basic_salary, emp_type, date_of_birth, address, employment_status) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, emp.getEmpId());
            ps.setString(2, emp.getName());
            ps.setInt(3, emp.getAge());
            ps.setString(4, emp.getGender());
            ps.setString(5, emp.getEmail());
            ps.setString(6, emp.getPhone());
            if (emp.getDeptId() == 0) ps.setNull(7, Types.INTEGER); else ps.setInt(7, emp.getDeptId());
            ps.setString(8, emp.getJobRole());
            ps.setDate(9, Date.valueOf(emp.getJoiningDate()));
            ps.setDouble(10, emp.getBasicSalary());
            ps.setString(11, emp.getEmployeeType());
            if (emp.getDateOfBirth() == null) ps.setNull(12, Types.DATE);
            else ps.setDate(12, Date.valueOf(emp.getDateOfBirth()));
            ps.setString(13, emp.getAddress());
            ps.setString(14, emp.getEmploymentStatus());
            ps.executeUpdate();
        }
    }

    // ---------------- Read ----------------
    public List<Employee> getAllEmployees() throws SQLException {
        return query(SELECT_BASE + "ORDER BY e.emp_id", null);
    }

    public Employee getEmployeeById(int empId) throws SQLException, EmployeeNotFoundException {
        List<Employee> list = query(SELECT_BASE + "WHERE e.emp_id = ?", empId);
        if (list.isEmpty()) throw new EmployeeNotFoundException(empId);
        return list.get(0);
    }

    public boolean employeeExists(int empId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM employees WHERE emp_id = ?")) {
            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** True if another employee (different ID) already uses this email. */
    public boolean emailInUse(String email, int excludeEmpId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM employees WHERE email = ? AND emp_id <> ?")) {
            ps.setString(1, email);
            ps.setInt(2, excludeEmpId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ---------------- Update ----------------
    public void updateEmployee(Employee emp) throws SQLException {
        if (emailInUse(emp.getEmail(), emp.getEmpId()))
            throw new IllegalArgumentException("Email " + emp.getEmail() + " is already used by another employee.");

        String sql = "UPDATE employees SET name=?, age=?, gender=?, email=?, phone=?, dept_id=?, "
                + "job_role=?, joining_date=?, basic_salary=?, date_of_birth=?, address=?, employment_status=? "
                + "WHERE emp_id=?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, emp.getName());
            ps.setInt(2, emp.getAge());
            ps.setString(3, emp.getGender());
            ps.setString(4, emp.getEmail());
            ps.setString(5, emp.getPhone());
            if (emp.getDeptId() == 0) ps.setNull(6, Types.INTEGER); else ps.setInt(6, emp.getDeptId());
            ps.setString(7, emp.getJobRole());
            ps.setDate(8, Date.valueOf(emp.getJoiningDate()));
            ps.setDouble(9, emp.getBasicSalary());
            if (emp.getDateOfBirth() == null) ps.setNull(10, Types.DATE);
            else ps.setDate(10, Date.valueOf(emp.getDateOfBirth()));
            ps.setString(11, emp.getAddress());
            ps.setString(12, emp.getEmploymentStatus());
            ps.setInt(13, emp.getEmpId());
            ps.executeUpdate();
        }
    }

    // ---------------- Delete ----------------
    public void deleteEmployee(int empId) throws SQLException, EmployeeNotFoundException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE employees SET employment_status='Inactive', deactivated_at=CURRENT_TIMESTAMP WHERE emp_id=?")) {
            ps.setInt(1, empId);
            if (ps.executeUpdate() == 0) throw new EmployeeNotFoundException(empId);
        }
    }

    // ---------------- Search ----------------
    public List<Employee> searchByName(String name) throws SQLException {
        return query(SELECT_BASE + "WHERE e.name LIKE ? ORDER BY e.name", "%" + name + "%");
    }

    public List<Employee> searchByDepartment(String deptName) throws SQLException {
        return query(SELECT_BASE + "WHERE d.dept_name LIKE ? ORDER BY e.name", "%" + deptName + "%");
    }

    public List<Employee> getEmployeesByDepartmentId(int deptId) throws SQLException {
        return query(SELECT_BASE + "WHERE e.dept_id = ? ORDER BY e.emp_id", deptId);
    }

    // ---------------- Reports ----------------
    public List<Employee> getBySalaryDescending() throws SQLException {
        return query(SELECT_BASE + "ORDER BY e.basic_salary DESC", null);
    }

    /** Returns the highest paid employee, or null if there are no employees. */
    public Employee getHighestPaid() throws SQLException {
        List<Employee> list = query(SELECT_BASE + "ORDER BY e.basic_salary DESC LIMIT 1", null);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Department name -> number of employees (includes departments with 0 employees). */
    public Map<String, Integer> countByDepartment() throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        String sql = "SELECT d.dept_name, COUNT(e.emp_id) AS total FROM departments d "
                + "LEFT JOIN employees e ON d.dept_id = e.dept_id GROUP BY d.dept_id, d.dept_name ORDER BY d.dept_name";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.put(rs.getString("dept_name"), rs.getInt("total"));
        }
        return result;
    }

    // ---------------- Helpers ----------------
    /** Runs a SELECT with at most one parameter (String or Integer) and converts rows to Employee objects. */
    private List<Employee> query(String sql, Object param) throws SQLException {
        List<Employee> list = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (param instanceof Integer) ps.setInt(1, (Integer) param);
            else if (param instanceof String) ps.setString(1, (String) param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Builds the right class (Employee / Manager / Developer) from the emp_type column. */
    private Employee mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("emp_id");
        String name = rs.getString("name");
        int age = rs.getInt("age");
        String gender = rs.getString("gender");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        int deptId = rs.getInt("dept_id");          // 0 when NULL
        String role = rs.getString("job_role");
        LocalDate joined = rs.getDate("joining_date").toLocalDate();
        double salary = rs.getDouble("basic_salary");
        String type = rs.getString("emp_type");

        Employee emp;
        if ("Manager".equals(type))
            emp = new Manager(id, name, age, gender, email, phone, deptId, role, joined, salary);
        else if ("Developer".equals(type))
            emp = new Developer(id, name, age, gender, email, phone, deptId, role, joined, salary);
        else
            emp = new Employee(id, name, age, gender, email, phone, deptId, role, joined, salary);

        emp.setDepartmentName(rs.getString("dept_name"));
        try {
            Date dob = rs.getDate("date_of_birth");
            emp.setDateOfBirth(dob == null ? null : dob.toLocalDate());
            emp.setAddress(rs.getString("address"));
            emp.setEmploymentStatus(rs.getString("employment_status"));
        } catch (SQLException ignored) {
            // Supports databases created before the HR profile columns were added.
        }
        return emp;
    }
}