package src;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/** Shows the menus, reads user input, and calls the service classes. */
public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static final EmployeeService employeeService = new EmployeeService();
    private static final DepartmentService departmentService = new DepartmentService();
    private static final AttendanceService attendanceService = new AttendanceService();
    private static final SalaryService salaryService = new SalaryService();

    public static void main(String[] args) {
        System.out.println("=== Employee Management System ===");
        if (!DatabaseConnection.testConnection()) {
            return;
        }
        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");
            try {
                switch (choice) {
                    case 1: addEmployee(); break;
                    case 2: viewAllEmployees(); break;
                    case 3: searchEmployee(); break;
                    case 4: updateEmployee(); break;
                    case 5: deleteEmployee(); break;
                    case 6: manageDepartments(); break;
                    case 7: manageAttendance(); break;
                    case 8: calculateSalary(); break;
                    case 9: generateSalarySlip(); break;
                    case 10: viewReports(); break;
                    case 11:
                        System.out.println("Goodbye!");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid choice. Please enter a number from 1 to 11.");
                }
            } catch (EmployeeNotFoundException | IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n========== MAIN MENU ==========");
        System.out.println(" 1. Add Employee");
        System.out.println(" 2. View All Employees");
        System.out.println(" 3. Search Employee");
        System.out.println(" 4. Update Employee");
        System.out.println(" 5. Delete Employee");
        System.out.println(" 6. Manage Departments");
        System.out.println(" 7. Manage Attendance");
        System.out.println(" 8. Calculate Salary");
        System.out.println(" 9. Generate Salary Slip");
        System.out.println("10. View Employee Reports");
        System.out.println("11. Exit");
    }

    // =====================================================
    //  1-5  Employee operations
    // =====================================================
    private static void addEmployee() throws SQLException {
        System.out.println("\n--- Add Employee ---");
        List<Department> departments = departmentService.getAllDepartments();
        if (departments.isEmpty()) {
            System.out.println("No departments exist yet. Create one first (menu option 6).");
            return;
        }

        int id = readInt("Employee ID: ");
        if (id <= 0) { System.out.println("Error: ID must be positive."); return; }
        if (employeeService.employeeExists(id)) {
            System.out.println("Error: Employee ID " + id + " already exists.");
            return;
        }

        String name = readNonEmpty("Name: ");

        int age;
        while (true) {
            age = readInt("Age (18-65): ");
            if (Validator.isValidAge(age)) break;
            System.out.println("Invalid age.");
        }

        String gender;
        while (true) {
            gender = readNonEmpty("Gender (Male/Female/Other): ");
            if (Validator.isValidGender(gender)) break;
            System.out.println("Invalid gender.");
        }

        String email;
        while (true) {
            email = readNonEmpty("Email: ");
            if (!Validator.isValidEmail(email)) { System.out.println("Invalid email format."); continue; }
            if (employeeService.emailInUse(email, 0)) { System.out.println("That email is already in use."); continue; }
            break;
        }

        String phone;
        while (true) {
            phone = readNonEmpty("Phone (10 digits): ");
            if (Validator.isValidPhone(phone)) break;
            System.out.println("Invalid phone number.");
        }

        System.out.println("Available departments:");
        printDepartments(departments);
        int deptId;
        while (true) {
            deptId = readInt("Department ID: ");
            if (departmentService.departmentExists(deptId)) break;
            System.out.println("Department not found.");
        }

        String role = readNonEmpty("Job Role: ");
        LocalDate joined = readDate("Joining Date (yyyy-MM-dd): ");

        double salary;
        while (true) {
            salary = readDouble("Basic Salary: ");
            if (Validator.isValidSalary(salary)) break;
            System.out.println("Salary must be greater than 0.");
        }

        System.out.println("Employee type: 1. Employee  2. Manager  3. Developer");
        int type;
        while (true) {
            type = readInt("Choose type: ");
            if (type >= 1 && type <= 3) break;
            System.out.println("Enter 1, 2 or 3.");
        }

        Employee emp;
        if (type == 2) emp = new Manager(id, name, age, gender, email, phone, deptId, role, joined, salary);
        else if (type == 3) emp = new Developer(id, name, age, gender, email, phone, deptId, role, joined, salary);
        else emp = new Employee(id, name, age, gender, email, phone, deptId, role, joined, salary);

        employeeService.addEmployee(emp);
        System.out.println("Employee added successfully.");
    }

    private static void viewAllEmployees() throws SQLException {
        System.out.println("\n--- All Employees ---");
        printEmployeeList(employeeService.getAllEmployees());
    }

    private static void searchEmployee() throws SQLException, EmployeeNotFoundException {
        System.out.println("\n--- Search Employee ---");
        int id = readInt("Enter Employee ID: ");
        Employee emp = employeeService.getEmployeeById(id);
        System.out.println();
        emp.displayDetails();
    }

    private static void updateEmployee() throws SQLException, EmployeeNotFoundException {
        System.out.println("\n--- Update Employee ---");
        int id = readInt("Enter Employee ID to update: ");
        Employee emp = employeeService.getEmployeeById(id);
        emp.displayDetails();
        System.out.println("\nPress Enter to keep the current value.");

        String input = readOptional("Name [" + emp.getName() + "]: ");
        if (!input.isEmpty()) emp.setName(input);

        input = readOptional("Age [" + emp.getAge() + "]: ");
        if (!input.isEmpty()) {
            try { emp.setAge(Integer.parseInt(input)); }
            catch (NumberFormatException e) { System.out.println("Not a number - age unchanged."); }
            catch (IllegalArgumentException e) { System.out.println(e.getMessage() + " Age unchanged."); }
        }

        input = readOptional("Email [" + emp.getEmail() + "]: ");
        if (!input.isEmpty()) {
            try { emp.setEmail(input); }
            catch (IllegalArgumentException e) { System.out.println(e.getMessage() + " Email unchanged."); }
        }

        input = readOptional("Phone [" + emp.getPhone() + "]: ");
        if (!input.isEmpty()) {
            try { emp.setPhone(input); }
            catch (IllegalArgumentException e) { System.out.println(e.getMessage() + " Phone unchanged."); }
        }

        input = readOptional("Department ID [" + emp.getDeptId() + " - " + emp.getDepartmentName() + "]: ");
        if (!input.isEmpty()) {
            try {
                int newDept = Integer.parseInt(input);
                if (departmentService.departmentExists(newDept)) emp.setDeptId(newDept);
                else System.out.println("Department not found - unchanged.");
            } catch (NumberFormatException e) { System.out.println("Not a number - department unchanged."); }
        }

        input = readOptional("Job Role [" + emp.getJobRole() + "]: ");
        if (!input.isEmpty()) emp.setJobRole(input);

        input = readOptional("Basic Salary [" + emp.getBasicSalary() + "]: ");
        if (!input.isEmpty()) {
            try { emp.setBasicSalary(Double.parseDouble(input)); }
            catch (NumberFormatException e) { System.out.println("Not a number - salary unchanged."); }
            catch (IllegalArgumentException e) { System.out.println(e.getMessage() + " Salary unchanged."); }
        }

        employeeService.updateEmployee(emp);
        System.out.println("Employee updated successfully.");
    }

    private static void deleteEmployee() throws SQLException, EmployeeNotFoundException {
        System.out.println("\n--- Delete Employee ---");
        int id = readInt("Enter Employee ID to delete: ");
        Employee emp = employeeService.getEmployeeById(id);
        String confirm = readNonEmpty("Delete " + emp.getName() + " and all their attendance/salary records? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            employeeService.deleteEmployee(id);
            System.out.println("Employee deleted.");
        } else {
            System.out.println("Cancelled.");
        }
    }

    // =====================================================
    //  6  Departments
    // =====================================================
    private static void manageDepartments() throws SQLException {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Manage Departments ---");
            System.out.println("1. Add Department");
            System.out.println("2. View All Departments");
            System.out.println("3. Update Department");
            System.out.println("4. Delete Department");
            System.out.println("5. Assign Employee to Department");
            System.out.println("6. View Employees of a Department");
            System.out.println("7. Back");
            int choice = readInt("Enter your choice: ");
            try {
                switch (choice) {
                    case 1: {
                        String name = readNonEmpty("Department name: ");
                        String location = readOptional("Location (optional): ");
                        departmentService.addDepartment(new Department(0, name, location.isEmpty() ? null : location));
                        System.out.println("Department added.");
                        break;
                    }
                    case 2:
                        printDepartments(departmentService.getAllDepartments());
                        break;
                    case 3: {
                        int id = readInt("Department ID to update: ");
                        if (!departmentService.departmentExists(id)) { System.out.println("Department not found."); break; }
                        String name = readNonEmpty("New name: ");
                        String location = readOptional("New location (optional): ");
                        departmentService.updateDepartment(new Department(id, name, location.isEmpty() ? null : location));
                        System.out.println("Department updated.");
                        break;
                    }
                    case 4: {
                        int id = readInt("Department ID to delete: ");
                        departmentService.deleteDepartment(id);
                        System.out.println("Department deleted.");
                        break;
                    }
                    case 5: {
                        int empId = readInt("Employee ID: ");
                        if (!employeeService.employeeExists(empId)) { System.out.println("Employee not found."); break; }
                        printDepartments(departmentService.getAllDepartments());
                        int deptId = readInt("Department ID: ");
                        departmentService.assignEmployee(empId, deptId);
                        System.out.println("Employee assigned to department.");
                        break;
                    }
                    case 6: {
                        int deptId = readInt("Department ID: ");
                        if (!departmentService.departmentExists(deptId)) { System.out.println("Department not found."); break; }
                        printEmployeeList(employeeService.getEmployeesByDepartmentId(deptId));
                        break;
                    }
                    case 7: back = true; break;
                    default: System.out.println("Invalid choice.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    // =====================================================
    //  7  Attendance
    // =====================================================
    private static void manageAttendance() throws SQLException, EmployeeNotFoundException {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Manage Attendance ---");
            System.out.println("1. Mark Attendance");
            System.out.println("2. View Attendance History");
            System.out.println("3. Total Days Present");
            System.out.println("4. Back");
            int choice = readInt("Enter your choice: ");
            try {
                switch (choice) {
                    case 1: {
                        int empId = readInt("Employee ID: ");
                        employeeService.getEmployeeById(empId);   // throws if not found
                        LocalDate date = readDate("Date (yyyy-MM-dd): ");
                        String status;
                        while (true) {
                            status = readNonEmpty("Status (Present/Absent): ");
                            if (status.equalsIgnoreCase("Present") || status.equalsIgnoreCase("Absent")) break;
                            System.out.println("Please type Present or Absent.");
                        }
                        attendanceService.markAttendance(new Attendance(empId, date, status));
                        System.out.println("Attendance marked.");
                        break;
                    }
                    case 2: {
                        int empId = readInt("Employee ID: ");
                        Employee emp = employeeService.getEmployeeById(empId);
                        List<Attendance> history = attendanceService.getHistory(empId);
                        System.out.println("\nAttendance history of " + emp.getName());
                        if (history.isEmpty()) {
                            System.out.println("No attendance records found.");
                        } else {
                            System.out.printf("%-12s %-8s%n", "Date", "Status");
                            for (Attendance a : history)
                                System.out.printf("%-12s %-8s%n", a.getDate(), a.getStatus());
                        }
                        break;
                    }
                    case 3: {
                        int empId = readInt("Employee ID: ");
                        Employee emp = employeeService.getEmployeeById(empId);
                        System.out.println(emp.getName() + " was present for "
                                + attendanceService.countPresentDays(empId) + " day(s).");
                        break;
                    }
                    case 4: back = true; break;
                    default: System.out.println("Invalid choice.");
                }
            } catch (EmployeeNotFoundException | IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    // =====================================================
    //  8-9  Salary
    // =====================================================
    private static void calculateSalary() throws SQLException, EmployeeNotFoundException {
        System.out.println("\n--- Calculate Salary ---");
        int empId = readInt("Employee ID: ");
        Employee emp = employeeService.getEmployeeById(empId);
        int month = readMonth();
        int year = readYear();
        Salary s = salaryService.calculateAndSave(emp, month, year);
        System.out.println("Salary calculated and saved.");
        s.printSlip();
    }

    private static void generateSalarySlip() throws SQLException, EmployeeNotFoundException {
        System.out.println("\n--- Generate Salary Slip ---");
        int empId = readInt("Employee ID: ");
        Employee emp = employeeService.getEmployeeById(empId);
        int month = readMonth();
        int year = readYear();
        Salary s = salaryService.getSalary(emp, month, year);
        if (s == null) {
            System.out.println("Salary for " + month + "/" + year + " has not been calculated yet. Use option 8 first.");
        } else {
            s.printSlip();
        }
    }

    // =====================================================
    //  10  Reports
    // =====================================================
    private static void viewReports() throws SQLException {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Employee Reports ---");
            System.out.println("1. Search by Name");
            System.out.println("2. Search by Department");
            System.out.println("3. Employees by Salary (High to Low)");
            System.out.println("4. Highest Paid Employee");
            System.out.println("5. Employee Count per Department");
            System.out.println("6. Full Employee & Salary Report");
            System.out.println("7. Back");
            int choice = readInt("Enter your choice: ");
            switch (choice) {
                case 1:
                    printEmployeeList(employeeService.searchByName(readNonEmpty("Enter name (or part of it): ")));
                    break;
                case 2:
                    printEmployeeList(employeeService.searchByDepartment(readNonEmpty("Enter department name: ")));
                    break;
                case 3:
                    printEmployeeList(employeeService.getBySalaryDescending());
                    break;
                case 4: {
                    Employee top = employeeService.getHighestPaid();
                    if (top == null) System.out.println("No employees found.");
                    else { System.out.println("\nHighest paid employee:"); top.displayDetails(); }
                    break;
                }
                case 5: {
                    Map<String, Integer> counts = employeeService.countByDepartment();
                    if (counts.isEmpty()) System.out.println("No departments found.");
                    else {
                        System.out.printf("%-20s %s%n", "Department", "Employees");
                        for (Map.Entry<String, Integer> e : counts.entrySet())
                            System.out.printf("%-20s %d%n", e.getKey(), e.getValue());
                    }
                    break;
                }
                case 6:
                    printFullReport();
                    break;
                case 7: back = true; break;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    /** Employee details plus salary figures (calculated with 0 absences, so it is the full monthly pay). */
    private static void printFullReport() throws SQLException {
        List<Employee> list = employeeService.getAllEmployees();
        if (list.isEmpty()) { System.out.println("No employees found."); return; }
        System.out.println("\n=== EMPLOYEE & SALARY REPORT ===");
        System.out.printf("%-5s %-18s %-10s %-12s %10s %10s %10s %10s%n",
                "ID", "Name", "Type", "Department", "Basic", "Gross", "Deduct.", "Net");
        System.out.println("-".repeat(92));
        double totalNet = 0;
        for (Employee e : list) {
            Salary s = Salary.calculate(e, 1, 2000, 0);   // month/year are not shown here
            totalNet += s.getNet();
            System.out.printf("%-5d %-18s %-10s %-12s %10.2f %10.2f %10.2f %10.2f%n",
                    e.getEmpId(), e.getName(), e.getEmployeeType(), e.getDepartmentName(),
                    s.getBasic(), s.getGross(), s.getTotalDeductions(), s.getNet());
        }
        System.out.println("-".repeat(92));
        System.out.printf("Total employees: %d    Total monthly net payout: %.2f%n", list.size(), totalNet);
    }

    // =====================================================
    //  Printing helpers
    // =====================================================
    private static void printEmployeeList(List<Employee> list) {
        if (list.isEmpty()) {
            System.out.println("No employees found.");
            return;
        }
        Employee.printTableHeader();
        for (Employee e : list) e.printTableRow();
        System.out.println("Total: " + list.size());
    }

    private static void printDepartments(List<Department> list) {
        if (list.isEmpty()) {
            System.out.println("No departments found.");
            return;
        }
        System.out.printf("%-6s %-20s %-15s%n", "ID", "Name", "Location");
        for (Department d : list) System.out.println(d);
    }

    // =====================================================
    //  Input helpers (keep asking until the input is valid)
    // =====================================================
    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private static double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Double.parseDouble(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("This field cannot be empty.");
        }
    }

    private static String readOptional(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return LocalDate.parse(scanner.nextLine().trim());
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Use the format yyyy-MM-dd, e.g. 2026-10-14.");
            }
        }
    }

    private static int readMonth() {
        while (true) {
            int m = readInt("Month (1-12): ");
            if (m >= 1 && m <= 12) return m;
            System.out.println("Month must be between 1 and 12.");
        }
    }

    private static int readYear() {
        while (true) {
            int y = readInt("Year (e.g. 2026): ");
            if (y >= 2000 && y <= 2100) return y;
            System.out.println("Please enter a year between 2000 and 2100.");
        }
    }
}
