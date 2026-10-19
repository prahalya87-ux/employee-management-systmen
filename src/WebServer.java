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

/**
 * Runs the website. It does two jobs:
 *   1. Serves the pages in the "web" folder (index.html, style.css, app.js).
 *   2. Provides a small REST API (/api/...) that the pages call. The API reuses the
 *      same service classes as the console application.
 * Uses the HTTP server built into Java, so no extra libraries are needed.
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final Path WEB_DIR = Paths.get("web").toAbsolutePath().normalize();

    private static final EmployeeService employeeService = new EmployeeService();
    private static final DepartmentService departmentService = new DepartmentService();
    private static final AttendanceService attendanceService = new AttendanceService();
    private static final SalaryService salaryService = new SalaryService();

    public static void main(String[] args) throws IOException {
        if (!DatabaseConnection.testConnection()) return;
        if (!Files.isDirectory(WEB_DIR)) {
            System.out.println("Could not find the 'web' folder at: " + WEB_DIR);
            System.out.println("Run the server from the project root folder (the one that contains 'web' and 'src').");
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
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
                case "employees":   employees(ex, method, p); break;
                case "departments": departments(ex, method, p); break;
                case "attendance":  attendance(ex, method, p); break;
                case "salary":      salary(ex, method); break;
                case "summary":     summary(ex); break;
                case "reports":     reports(ex); break;
                default: sendJson(ex, 404, Json.error("Unknown API path."));
            }
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
    //  /api/employees
    // ======================================================
    private static void employees(HttpExchange ex, String method, String[] p)
            throws IOException, SQLException, EmployeeNotFoundException {
        if (p.length == 1) {
            if (method.equals("GET")) {
                sendJson(ex, 200, employeeListJson(employeeService.getAllEmployees()));
            } else if (method.equals("POST")) {
                Employee emp = buildEmployee(Json.parseObject(readBody(ex)));
                employeeService.addEmployee(emp);
                sendJson(ex, 201, Json.message("Employee added successfully."));
            } else {
                notAllowed(ex);
            }
            return;
        }
        int id = parseId(p[1]);
        switch (method) {
            case "GET":
                sendJson(ex, 200, employeeJson(employeeService.getEmployeeById(id)));
                break;
            case "PUT": {
                Employee emp = employeeService.getEmployeeById(id);
                applyUpdates(emp, Json.parseObject(readBody(ex)));
                employeeService.updateEmployee(emp);
                sendJson(ex, 200, Json.message("Employee updated successfully."));
                break;
            }
            case "DELETE":
                employeeService.deleteEmployee(id);
                sendJson(ex, 200, Json.message("Employee deleted."));
                break;
            default:
                notAllowed(ex);
        }
    }

    /** Creates an Employee, Manager or Developer from the submitted form data. */
    private static Employee buildEmployee(Map<String, String> d) throws SQLException {
        int id = toInt(d, "empId", "Employee ID");
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

        if (type.equals("Manager"))
            return new Manager(id, name, age, gender, email, phone, deptId, role, joined, salary);
        if (type.equals("Developer"))
            return new Developer(id, name, age, gender, email, phone, deptId, role, joined, salary);
        return new Employee(id, name, age, gender, email, phone, deptId, role, joined, salary);
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
    }

    // ======================================================
    //  /api/departments
    // ======================================================
    private static void departments(HttpExchange ex, String method, String[] p)
            throws IOException, SQLException {
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
        if (method.equals("GET") && p.length == 2) {
            int id = parseId(p[1]);
            Employee emp = employeeService.getEmployeeById(id);
            List<String> records = new ArrayList<>();
            for (Attendance a : attendanceService.getHistory(id)) {
                records.add("{\"date\":" + Json.quote(a.getDate().toString())
                        + ",\"status\":" + Json.quote(a.getStatus()) + "}");
            }
            sendJson(ex, 200, "{\"employee\":" + Json.quote(emp.getName())
                    + ",\"presentDays\":" + attendanceService.countPresentDays(id)
                    + ",\"records\":" + array(records) + "}");
        } else if (method.equals("POST") && p.length == 1) {
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
    private static void salary(HttpExchange ex, String method)
            throws IOException, SQLException, EmployeeNotFoundException {
        if (method.equals("POST")) {
            Map<String, String> d = Json.parseObject(readBody(ex));
            Employee emp = employeeService.getEmployeeById(toInt(d, "empId", "Employee"));
            int month = checkMonth(toInt(d, "month", "Month"));
            int year = toInt(d, "year", "Year");
            sendJson(ex, 200, slipJson(emp, salaryService.calculateAndSave(emp, month, year)));
        } else if (method.equals("GET")) {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            Employee emp = employeeService.getEmployeeById(toInt(q, "empId", "Employee"));
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

        sendJson(ex, 200, "{\"totalEmployees\":" + all.size()
                + ",\"totalDepartments\":" + counts.size()
                + ",\"totalSalary\":" + Json.num(total)
                + ",\"averageSalary\":" + Json.num(average)
                + ",\"highestPaid\":" + topJson
                + ",\"departmentCounts\":" + array(counts) + "}");
    }

    /** Employee details + salary figures, highest paid first (same figures as console report 6). */
    private static void reports(HttpExchange ex) throws IOException, SQLException {
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