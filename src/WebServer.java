package src;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the website. It does two jobs:
 *   1. Serves the pages in the "web" folder (index.html, style.css, app.js).
 *   2. Provides a small REST API (/api/...) that the pages call. The API reuses the
 *      same service classes as the console application.
 * Uses the HTTP server built into Java, so no extra libraries are needed.
 */
public class WebServer {

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final Path WEB_DIR = Paths.get("web").toAbsolutePath().normalize();

    private static final EmployeeService employeeService = new EmployeeService();
    private static final DepartmentService departmentService = new DepartmentService();
    private static final AttendanceService attendanceService = new AttendanceService();
    private static final SalaryService salaryService = new SalaryService();
    private static final HRService hrService = new HRService();
    private static final Map<String, Map<String, String>> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        boolean production = "production".equalsIgnoreCase(System.getenv("APP_ENV"));
        if (!DatabaseConnection.testConnection()) return;
        try {
            DatabaseConnection.initializeSchema();
            hrService.ensureAdmin();
            if (!production) {
                String adithyaUsername = hrService.ensureAdithyaDemoAccount();
                if (adithyaUsername != null)
                    System.out.println("Adithya demo employee login: " + adithyaUsername + " / adithya123");
                int demoAttendanceRows = attendanceService.ensureDemoMonth();
                if (demoAttendanceRows > 0)
                    System.out.println("Added " + demoAttendanceRows + " October 2026 demo attendance records.");
                int novemberAttendanceRows = attendanceService.ensureDemoAttendance(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
                if (novemberAttendanceRows > 0)
                    System.out.println("Added " + novemberAttendanceRows + " November 2026 demo attendance records through today.");
                if (employeeService.employeeExists(101)) {
                    Employee adithya = employeeService.getEmployeeById(101);
                    if ("Adithya".equalsIgnoreCase(adithya.getName())) {
                        int completedMonth = LocalDate.now().getYear() == 2026
                                ? LocalDate.now().getMonthValue() - 1 : 10;
                        int generatedSlips = salaryService.ensureDemoSalaryHistory(adithya, 2026, completedMonth);
                        if (generatedSlips > 0)
                            System.out.println("Added " + generatedSlips + " completed 2026 salary records for Adithya.");
                    }
                }
            }
        } catch (SQLException | EmployeeNotFoundException e) {
            System.out.println("Database setup failed: " + e.getMessage());
            return;
        }
        if (!Files.isDirectory(WEB_DIR)) {
            System.out.println("Could not find the 'web' folder at: " + WEB_DIR);
            System.out.println("Run the server from the project root folder (the one that contains 'web' and 'src').");
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/", WebServer::handle);
        server.start();
        System.out.println("Employee Management System is running.");
        System.out.println("Open http://localhost:" + PORT + " in your browser. Press Ctrl+C to stop.");
    }

    // ======================================================
    //  Request routing
    // ======================================================
    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) handleApi(ex, path);
            else serveStatic(ex, path);
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Json.error("Server error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private static void handleApi(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        String[] p = path.substring("/api/".length()).split("/");
        try {
            switch (p[0]) {
                case "auth":        auth(ex, method, p); break;
                case "next-employee-id":
                    requireAdmin(ex);
                    sendJson(ex, 200, "{\"empId\":\"EMP" + employeeService.getNextEmployeeId() + "\",\"numericId\":" + employeeService.getNextEmployeeId() + "}"); break;
                case "employees":   employees(ex, method, p); break;
                case "departments": departments(ex, method, p); break;
                case "attendance":  attendance(ex, method, p); break;
                case "leave":       leave(ex, method, p); break;
                case "salary":      salary(ex, method, p); break;
                case "summary":     summary(ex); break;
                case "reports":     reports(ex); break;
                default: sendJson(ex, 404, Json.error("Unknown API path."));
            }
        } catch (AuthenticationRequiredException e) {
            sendJson(ex, 401, Json.error(e.getMessage()));
        } catch (AuthorizationException e) {
            sendJson(ex, 403, Json.error(e.getMessage()));
        } catch (EmployeeNotFoundException e) {
            sendJson(ex, 404, Json.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, Json.error(e.getMessage()));
        } catch (DateTimeException e) {
            sendJson(ex, 400, Json.error("Invalid date. Use the format yyyy-MM-dd."));
        } catch (SQLException e) {
            sendJson(ex, 500, Json.error("Database error: " + e.getMessage()));
        }
    }

    // ======================================================
    //  Authentication and leave management
    // ======================================================
    private static void auth(HttpExchange ex, String method, String[] p) throws IOException, SQLException {
        if (p.length == 2 && p[1].equals("session") && method.equals("GET")) {
            String authorization = ex.getRequestHeaders().getFirst("Authorization");
            String token = authorization == null ? "" : authorization.replaceFirst("^Bearer\\s+", "").trim();
            Map<String, String> user = sessions.get(token);
            if (user == null) sendJson(ex, 401, Json.error("Your session has expired. Please sign in again."));
            else sendJson(ex, 200, "{\"username\":" + Json.quote(user.get("username"))
                    + ",\"role\":" + Json.quote(user.get("role"))
                    + ",\"empId\":" + Json.quote(user.get("empId")) + "}");
        } else if (p.length == 2 && p[1].equals("login") && method.equals("POST")) {
            Map<String, String> d = Json.parseObject(readBody(ex));
            Map<String, String> user = hrService.authenticate(text(d, "username"), text(d, "password"));
            String token = UUID.randomUUID().toString();
            sessions.put(token, user);
            sendJson(ex, 200, "{\"token\":" + Json.quote(token)
                    + ",\"username\":" + Json.quote(user.get("username"))
                    + ",\"role\":" + Json.quote(user.get("role"))
                    + ",\"empId\":" + Json.quote(user.get("empId")) + "}");
        } else if (p.length == 2 && p[1].equals("logout") && method.equals("POST")) {
            String token = ex.getRequestHeaders().getFirst("Authorization");
            if (token != null) sessions.remove(token.replaceFirst("^Bearer\\s+", ""));
            sendJson(ex, 200, Json.message("Logged out."));
        } else {
            notAllowed(ex);
        }
    }

    private static void leave(HttpExchange ex, String method, String[] p) throws IOException, SQLException {
        Map<String, String> user = requireSession(ex);
        if (p.length == 1) {
            if (method.equals("GET")) {
                Integer empId;
                if ("EMPLOYEE".equals(user.get("role"))) empId = requireEmployeeId(user);
                else {
                    requireAdmin(ex);
                    empId = null;
                }
                sendJson(ex, 200, leaveListJson(hrService.leaveRequests(empId)));
            } else if (method.equals("POST")) {
                int empId = requireEmployeeId(user);
                Map<String, String> d = Json.parseObject(readBody(ex));
                hrService.applyLeave(empId, text(d, "leaveType"), LocalDate.parse(text(d, "startDate")),
                        LocalDate.parse(text(d, "endDate")), text(d, "reason"));
                sendJson(ex, 201, Json.message("Leave request submitted."));
            } else notAllowed(ex);
        } else if (p.length == 2 && p[1].equals("balances") && method.equals("GET")) {
            int empId = requireEmployeeId(user);
            sendJson(ex, 200, mapsJson(hrService.leaveBalances(empId)));
        } else if (p.length == 3 && p[1].equals("decide") && method.equals("POST")) {
            requireAdmin(ex);
            Map<String, String> d = Json.parseObject(readBody(ex));
            hrService.decideLeave(parseId(p[2]), text(d, "status"));
            sendJson(ex, 200, Json.message("Leave request updated."));
        } else notAllowed(ex);
    }

    // ======================================================
    //  /api/employees
    // ======================================================
    private static void employees(HttpExchange ex, String method, String[] p)
            throws IOException, SQLException, EmployeeNotFoundException {
        if (p.length == 1) {
            if (method.equals("GET")) {
                Map<String, String> user = requireSession(ex);
                if ("EMPLOYEE".equals(user.get("role"))) {
                    sendJson(ex, 200, employeeListJson(
                            java.util.Collections.singletonList(employeeService.getEmployeeById(requireEmployeeId(user)))));
                } else {
                    requireAdmin(ex);
                    sendJson(ex, 200, employeeListJson(employeeService.getAllEmployees()));
                }
            } else if (method.equals("POST")) {
                requireAdmin(ex);
                Map<String, String> d = Json.parseObject(readBody(ex));
                Employee emp = buildEmployee(d);
                employeeService.addEmployee(emp);
                hrService.initializeLeaveBalances(emp.getEmpId());
                String username = text(d, "username");
                String password = text(d, "password");
                if (!username.isEmpty()) hrService.createUser(emp.getEmpId(), username,
                        password.isEmpty() ? "welcome123" : password, "EMPLOYEE");
                sendJson(ex, 201, Json.message("Employee added successfully."));
            } else {
                notAllowed(ex);
            }
            return;
        }
        int id = parseId(p[1]);
        switch (method) {
            case "GET": {
                Map<String, String> user = requireSession(ex);
                if ("EMPLOYEE".equals(user.get("role")) && id != requireEmployeeId(user))
                    throw new AuthorizationException("You can only view your own employee profile.");
                if (!"EMPLOYEE".equals(user.get("role"))) requireAdmin(ex);
                sendJson(ex, 200, employeeJson(employeeService.getEmployeeById(id)));
                break;
            }
            case "PUT": {
                requireAdmin(ex);
                Employee emp = employeeService.getEmployeeById(id);
                applyUpdates(emp, Json.parseObject(readBody(ex)));
                employeeService.updateEmployee(emp);
                sendJson(ex, 200, Json.message("Employee updated successfully."));
                break;
            }
            case "DELETE":
                requireAdmin(ex);
                employeeService.deleteEmployee(id);
                sendJson(ex, 200, Json.message("Employee deleted."));
                break;
            default:
                notAllowed(ex);
        }
    }

    /** Creates an Employee, Manager or Developer from the submitted form data. */
    private static Employee buildEmployee(Map<String, String> d) throws SQLException {
        int id;
        String rawId = text(d, "empId").replaceAll("(?i)^EMP", "");
        if (rawId.isEmpty()) id = employeeService.getNextEmployeeId();
        else id = Integer.parseInt(rawId);
        String name = text(d, "name");
        int age = toInt(d, "age", "Age");
        String gender = text(d, "gender");
        String email = text(d, "email");
        String phone = text(d, "phone");
        int deptId = toInt(d, "deptId", "Department");
        String role = text(d, "jobRole");
        LocalDate joined = LocalDate.parse(text(d, "joiningDate"));
        double salary = toDouble(d, "basicSalary", "Basic salary");
        String type = text(d, "type");

        if (!departmentService.departmentExists(deptId)) throw new IllegalArgumentException("Department not found.");

        Employee emp;
        if (type.equals("Manager"))
            emp = new Manager(id, name, age, gender, email, phone, deptId, role, joined, salary);
        else if (type.equals("Developer"))
            emp = new Developer(id, name, age, gender, email, phone, deptId, role, joined, salary);
        else
            emp = new Employee(id, name, age, gender, email, phone, deptId, role, joined, salary);
        String dob = text(d, "dateOfBirth");
        if (!dob.isEmpty()) emp.setDateOfBirth(LocalDate.parse(dob));
        emp.setAddress(text(d, "address"));
        String status = text(d, "employmentStatus");
        emp.setEmploymentStatus(status.isEmpty() ? "Active" : status);
        return emp;
    }

    /** Copies the submitted values onto an existing employee (the setters validate them). */
    private static void applyUpdates(Employee emp, Map<String, String> d) throws SQLException {
        int deptId = toInt(d, "deptId", "Department");
        if (!departmentService.departmentExists(deptId)) throw new IllegalArgumentException("Department not found.");
        emp.setName(text(d, "name"));
        emp.setAge(toInt(d, "age", "Age"));
        emp.setGender(text(d, "gender"));
        emp.setEmail(text(d, "email"));
        emp.setPhone(text(d, "phone"));
        emp.setDeptId(deptId);
        emp.setJobRole(text(d, "jobRole"));
        emp.setJoiningDate(LocalDate.parse(text(d, "joiningDate")));
        emp.setBasicSalary(toDouble(d, "basicSalary", "Basic salary"));
        String dob = text(d, "dateOfBirth");
        emp.setDateOfBirth(dob.isEmpty() ? null : LocalDate.parse(dob));
        emp.setAddress(text(d, "address"));
        String status = text(d, "employmentStatus");
        emp.setEmploymentStatus(status.isEmpty() ? "Active" : status);
    }

    // ======================================================
    //  /api/departments
    // ======================================================
    private static void departments(HttpExchange ex, String method, String[] p)
            throws IOException, SQLException {
        requireAdmin(ex);
        if (p.length == 1) {
            if (method.equals("GET")) {
                Map<String, Integer> counts = employeeService.countByDepartment();
                List<String> items = new ArrayList<>();
                for (Department d : departmentService.getAllDepartments()) {
                    items.add("{\"deptId\":" + d.getDeptId()
                            + ",\"deptName\":" + Json.quote(d.getDeptName())
                            + ",\"location\":" + Json.quote(d.getLocation())
                            + ",\"employeeCount\":" + counts.getOrDefault(d.getDeptName(), 0) + "}");
                }
                sendJson(ex, 200, array(items));
            } else if (method.equals("POST")) {
                Map<String, String> d = Json.parseObject(readBody(ex));
                departmentService.addDepartment(new Department(0, text(d, "deptName"), nullIfEmpty(text(d, "location"))));
                sendJson(ex, 201, Json.message("Department added."));
            } else {
                notAllowed(ex);
            }
            return;
        }
        int id = parseId(p[1]);
        if (p.length == 3 && p[2].equals("employees") && method.equals("GET")) {
            if (!departmentService.departmentExists(id)) throw new IllegalArgumentException("Department not found.");
            sendJson(ex, 200, employeeListJson(employeeService.getEmployeesByDepartmentId(id)));
            return;
        }
        if (method.equals("PUT")) {
            Map<String, String> d = Json.parseObject(readBody(ex));
            departmentService.updateDepartment(new Department(id, text(d, "deptName"), nullIfEmpty(text(d, "location"))));
            sendJson(ex, 200, Json.message("Department updated."));
        } else if (method.equals("DELETE")) {
            departmentService.deleteDepartment(id);
            sendJson(ex, 200, Json.message("Department deleted."));
        } else {
            notAllowed(ex);
        }
    }

    // ======================================================
    //  /api/attendance
    // ======================================================
    private static void attendance(HttpExchange ex, String method, String[] p)
            throws IOException, SQLException, EmployeeNotFoundException {
        Map<String, String> user = requireSession(ex);
        if (method.equals("GET") && p.length == 3 && p[1].equals("date")) {
            LocalDate date = LocalDate.parse(p[2]);
            List<String> rows = new ArrayList<>();
            List<Employee> employees;
            if ("EMPLOYEE".equals(user.get("role")))
                employees = java.util.Collections.singletonList(employeeService.getEmployeeById(requireEmployeeId(user)));
            else {
                requireAdmin(ex);
                employees = employeeService.getAllEmployees();
            }
            for (Employee emp : employees) {
                rows.add("{\"empId\":" + emp.getEmpId()
                        + ",\"name\":" + Json.quote(emp.getName())
                        + ",\"department\":" + Json.quote(emp.getDepartmentName())
                        + ",\"designation\":" + Json.quote(emp.getJobRole())
                        + ",\"status\":" + Json.quote(attendanceService.getStatus(emp.getEmpId(), date)) + "}");
            }
            sendJson(ex, 200, array(rows));
        } else if (method.equals("GET") && p.length == 2) {
            int id = parseId(p[1]);
            if ("EMPLOYEE".equals(user.get("role")) && id != requireEmployeeId(user))
                throw new AuthorizationException("You can only view your own attendance.");
            if (!"EMPLOYEE".equals(user.get("role"))) requireAdmin(ex);
            Employee emp = employeeService.getEmployeeById(id);
            List<String> records = new ArrayList<>();
            for (Attendance a : attendanceService.getHistory(id)) {
                records.add("{\"date\":" + Json.quote(a.getDate().toString())
                        + ",\"status\":" + Json.quote(a.getStatus()) + "}");
            }
            sendJson(ex, 200, "{\"employee\":" + Json.quote(emp.getName())
                    + ",\"presentDays\":" + attendanceService.countPresentDays(id)
                    + ",\"records\":" + array(records) + "}");
        } else if (method.equals("POST") && p.length == 2 && p[1].equals("submit")) {
            requireAdmin(ex);
            Map<String, String> d = Json.parseObject(readBody(ex));
            Map<Integer, String> statuses = new HashMap<>();
            for (Map.Entry<String, String> entry : d.entrySet()) {
                if (entry.getKey().startsWith("status_")) {
                    int empId = parseId(entry.getKey().substring("status_".length()));
                    statuses.put(empId, entry.getValue());
                }
            }
            attendanceService.submitAttendance(LocalDate.parse(text(d, "date")), statuses);
            sendJson(ex, 200, Json.message("Attendance submitted successfully."));
        } else if (method.equals("POST") && p.length == 1) {
            requireAdmin(ex);
            Map<String, String> d = Json.parseObject(readBody(ex));
            int empId = toInt(d, "empId", "Employee");
            LocalDate date = LocalDate.parse(text(d, "date"));
            attendanceService.markAttendance(new Attendance(empId, date, text(d, "status")));
            sendJson(ex, 201, Json.message("Attendance marked."));
        } else {
            notAllowed(ex);
        }
    }

    // ======================================================
    //  /api/salary
    // ======================================================
    private static void salary(HttpExchange ex, String method, String[] p)
            throws IOException, SQLException, EmployeeNotFoundException {
        Map<String, String> user = requireSession(ex);
        if (method.equals("GET") && p.length == 2 && p[1].equals("history")) {
            int empId;
            if ("EMPLOYEE".equals(user.get("role"))) empId = requireEmployeeId(user);
            else {
                requireAdmin(ex);
                empId = toInt(parseQuery(ex.getRequestURI().getRawQuery()), "empId", "Employee");
            }
            Employee emp = employeeService.getEmployeeById(empId);
            List<String> history = new ArrayList<>();
            for (Salary salary : salaryService.getSalaryHistory(emp))
                history.add(slipJson(emp, salary));
            sendJson(ex, 200, array(history));
        } else if (p.length != 1) {
            notAllowed(ex);
        } else if (method.equals("POST")) {
            requireAdmin(ex);
            Map<String, String> d = Json.parseObject(readBody(ex));
            Employee emp = employeeService.getEmployeeById(toInt(d, "empId", "Employee"));
            int month = checkMonth(toInt(d, "month", "Month"));
            int year = toInt(d, "year", "Year");
            sendJson(ex, 200, slipJson(emp, salaryService.calculateAndSave(emp, month, year)));
        } else if (method.equals("GET")) {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            int empId = toInt(q, "empId", "Employee");
            if ("EMPLOYEE".equals(user.get("role")) && empId != requireEmployeeId(user))
                throw new AuthorizationException("You can only view your own salary slips.");
            if (!"EMPLOYEE".equals(user.get("role"))) requireAdmin(ex);
            Employee emp = employeeService.getEmployeeById(empId);
            int month = checkMonth(toInt(q, "month", "Month"));
            int year = toInt(q, "year", "Year");
            Salary s = salaryService.getSalary(emp, month, year);
            if (s == null) {
                sendJson(ex, 404, Json.error("Salary for " + month + "/" + year
                        + " has not been calculated yet. Click 'Calculate & Save' first."));
            } else {
                sendJson(ex, 200, slipJson(emp, s));
            }
        } else {
            notAllowed(ex);
        }
    }

    private static String slipJson(Employee emp, Salary s) {
        return "{\"empId\":" + emp.getEmpId()
                + ",\"empName\":" + Json.quote(emp.getName())
                + ",\"department\":" + Json.quote(emp.getDepartmentName())
                + ",\"jobRole\":" + Json.quote(emp.getJobRole())
                + ",\"month\":" + s.getMonth()
                + ",\"year\":" + s.getYear()
                + ",\"basic\":" + Json.num(s.getBasic())
                + ",\"hra\":" + Json.num(s.getHra())
                + ",\"da\":" + Json.num(s.getDa())
                + ",\"bonus\":" + Json.num(s.getBonus())
                + ",\"gross\":" + Json.num(s.getGross())
                + ",\"pf\":" + Json.num(s.getPf())
                + ",\"tax\":" + Json.num(s.getTax())
                + ",\"absentDays\":" + s.getAbsentDays()
                + ",\"absentDeduction\":" + Json.num(s.getAbsentDeduction())
                + ",\"totalDeductions\":" + Json.num(s.getTotalDeductions())
                + ",\"net\":" + Json.num(s.getNet()) + "}";
    }

    // ======================================================
    //  /api/summary  and  /api/reports
    // ======================================================
    private static void summary(HttpExchange ex) throws IOException, SQLException {
        requireAdmin(ex);
        List<Employee> all = employeeService.getAllEmployees();
        double total = 0;
        for (Employee e : all) total += e.getBasicSalary();
        double average = all.isEmpty() ? 0 : total / all.size();

        Employee top = employeeService.getHighestPaid();
        String topJson = top == null ? "null"
                : "{\"name\":" + Json.quote(top.getName()) + ",\"salary\":" + Json.num(top.getBasicSalary()) + "}";

        List<String> counts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : employeeService.countByDepartment().entrySet())
            counts.add("{\"name\":" + Json.quote(e.getKey()) + ",\"count\":" + e.getValue() + "}");

        int presentToday = attendanceService.countPresentOnDate(LocalDate.now());
        int pendingLeaves = 0;
        try (java.sql.Connection con = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM leave_requests WHERE status='PENDING'");
             java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) pendingLeaves = rs.getInt(1);
        }

        sendJson(ex, 200, "{\"totalEmployees\":" + all.size()
                + ",\"presentToday\":" + presentToday
                + ",\"absentToday\":" + Math.max(0, all.size() - presentToday)
                + ",\"pendingLeaves\":" + pendingLeaves
                + ",\"totalDepartments\":" + counts.size()
                + ",\"totalSalary\":" + Json.num(total)
                + ",\"averageSalary\":" + Json.num(average)
                + ",\"highestPaid\":" + topJson
                + ",\"departmentCounts\":" + array(counts) + "}");
    }

    /** Employee details + salary figures, highest paid first (same figures as console report 6). */
    private static void reports(HttpExchange ex) throws IOException, SQLException {
        requireAdmin(ex);
        List<String> rows = new ArrayList<>();
        for (Employee e : employeeService.getBySalaryDescending()) {
            Salary s = Salary.calculate(e, 1, 2000, 0);
            rows.add("{\"empId\":" + e.getEmpId()
                    + ",\"name\":" + Json.quote(e.getName())
                    + ",\"type\":" + Json.quote(e.getEmployeeType())
                    + ",\"department\":" + Json.quote(e.getDepartmentName())
                    + ",\"jobRole\":" + Json.quote(e.getJobRole())
                    + ",\"basic\":" + Json.num(s.getBasic())
                    + ",\"gross\":" + Json.num(s.getGross())
                    + ",\"deductions\":" + Json.num(s.getTotalDeductions())
                    + ",\"net\":" + Json.num(s.getNet()) + "}");
        }
        sendJson(ex, 200, array(rows));
    }

    // ======================================================
    //  JSON building
    // ======================================================
    private static String employeeJson(Employee e) {
        return "{\"empId\":" + e.getEmpId()
                + ",\"name\":" + Json.quote(e.getName())
                + ",\"age\":" + e.getAge()
                + ",\"gender\":" + Json.quote(e.getGender())
                + ",\"email\":" + Json.quote(e.getEmail())
                + ",\"phone\":" + Json.quote(e.getPhone())
                + ",\"deptId\":" + e.getDeptId()
                + ",\"departmentName\":" + Json.quote(e.getDepartmentName())
                + ",\"jobRole\":" + Json.quote(e.getJobRole())
                + ",\"joiningDate\":" + Json.quote(e.getJoiningDate().toString())
                + ",\"basicSalary\":" + Json.num(e.getBasicSalary())
                + ",\"dateOfBirth\":" + Json.quote(e.getDateOfBirth() == null ? "" : e.getDateOfBirth().toString())
                + ",\"address\":" + Json.quote(e.getAddress())
                + ",\"employmentStatus\":" + Json.quote(e.getEmploymentStatus())
                + ",\"type\":" + Json.quote(e.getEmployeeType()) + "}";
    }

    private static String employeeListJson(List<Employee> list) {
        List<String> items = new ArrayList<>();
        for (Employee e : list) items.add(employeeJson(e));
        return array(items);
    }

    private static String array(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    // ======================================================
    //  Small helpers
    // ======================================================
    private static Map<String, String> requireSession(HttpExchange ex) {
        String authorization = ex.getRequestHeaders().getFirst("Authorization");
        if (authorization == null) throw new AuthenticationRequiredException("Please log in to continue.");
        String token = authorization.replaceFirst("^Bearer\\s+", "").trim();
        Map<String, String> user = sessions.get(token);
        if (user == null) throw new AuthenticationRequiredException("Please log in to continue.");
        return user;
    }

    private static void requireAdmin(HttpExchange ex) throws IOException {
        Map<String, String> user = requireSession(ex);
        if (!"ADMIN".equals(user.get("role"))) {
            throw new AuthorizationException("Administrator access required.");
        }
    }

    private static int requireEmployeeId(Map<String, String> user) {
        Integer empId = toNullableId(user.get("empId"));
        if (!"EMPLOYEE".equals(user.get("role")) || empId == null)
            throw new AuthorizationException("This employee account is not linked to an employee profile.");
        return empId;
    }

    private static final class AuthenticationRequiredException extends RuntimeException {
        AuthenticationRequiredException(String message) { super(message); }
    }

    private static final class AuthorizationException extends RuntimeException {
        AuthorizationException(String message) { super(message); }
    }

    private static Integer toNullableId(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
    }

    private static String leaveListJson(List<Map<String, Object>> rows) {
        List<String> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : r.entrySet()) {
                if (!first) b.append(",");
                first = false;
                b.append(Json.quote(entry.getKey())).append(":");
                Object v = entry.getValue();
                if (v instanceof Number) b.append(v);
                else b.append(Json.quote(v == null ? "" : v.toString()));
            }
            b.append("}");
            items.add(b.toString());
        }
        return array(items);
    }

    private static String mapsJson(List<Map<String, Object>> rows) {
        return leaveListJson(rows);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String text(Map<String, String> d, String key) {
        String v = d.get(key);
        return v == null ? "" : v.trim();
    }

    private static int toInt(Map<String, String> d, String key, String label) {
        try {
            return Integer.parseInt(text(d, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static double toDouble(Map<String, String> d, String key, String label) {
        try {
            return Double.parseDouble(text(d, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private static int parseId(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID in URL.");
        }
    }

    private static int checkMonth(int m) {
        if (m < 1 || m > 12) throw new IllegalArgumentException("Month must be between 1 and 12.");
        return m;
    }

    private static String nullIfEmpty(String s) {
        return s.isEmpty() ? null : s;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static void notAllowed(HttpExchange ex) throws IOException {
        sendJson(ex, 405, Json.error("Method not allowed."));
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ======================================================
    //  Serving the website files
    // ======================================================
    private static void serveStatic(HttpExchange ex, String path) throws IOException {
        String relative = path.equals("/") ? "index.html" : path.substring(1);
        Path file = WEB_DIR.resolve(relative).normalize();
        // Safety: never serve anything outside the web folder.
        if (!file.startsWith(WEB_DIR) || !Files.isRegularFile(file)) {
            byte[] msg = "404 - Page not found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}