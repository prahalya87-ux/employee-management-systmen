package src;
import java.time.LocalDate;

/**
 * Stores employee information.
 * All fields are private (encapsulation); data is changed only through
 * getters/setters, and the setters validate the values.
 * Manager and Developer extend this class.
 */
public class Employee {

    private int empId;
    private String name;
    private int age;
    private String gender;
    private String email;
    private String phone;
    private int deptId;               // 0 means "not assigned"
    private String departmentName;
    private String jobRole;
    private LocalDate joiningDate;
    private double basicSalary;

    public Employee(int empId, String name, int age, String gender, String email,
                    String phone, int deptId, String jobRole,
                    LocalDate joiningDate, double basicSalary) {
        setEmpId(empId);
        setName(name);
        setAge(age);
        setGender(gender);
        setEmail(email);
        setPhone(phone);
        this.deptId = deptId;
        setJobRole(jobRole);
        this.joiningDate = joiningDate;
        setBasicSalary(basicSalary);
    }

    // ---------- Getters ----------
    public int getEmpId() { return empId; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public int getDeptId() { return deptId; }
    public String getDepartmentName() { return departmentName == null ? "-" : departmentName; }
    public String getJobRole() { return jobRole; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public double getBasicSalary() { return basicSalary; }

    // ---------- Setters (with validation) ----------
    public void setEmpId(int empId) {
        if (empId <= 0) throw new IllegalArgumentException("Employee ID must be a positive number.");
        this.empId = empId;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
        this.name = name.trim();
    }

    public void setAge(int age) {
        if (!Validator.isValidAge(age)) throw new IllegalArgumentException("Age must be between 18 and 65.");
        this.age = age;
    }

    public void setGender(String gender) {
        if (!Validator.isValidGender(gender)) throw new IllegalArgumentException("Gender must be Male, Female or Other.");
        this.gender = gender;
    }

    public void setEmail(String email) {
        if (!Validator.isValidEmail(email)) throw new IllegalArgumentException("Invalid email address.");
        this.email = email;
    }

    public void setPhone(String phone) {
        if (!Validator.isValidPhone(phone)) throw new IllegalArgumentException("Phone number must be 10 digits.");
        this.phone = phone;
    }

    public void setDeptId(int deptId) { this.deptId = deptId; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public void setJobRole(String jobRole) {
        if (jobRole == null || jobRole.trim().isEmpty()) throw new IllegalArgumentException("Job role cannot be empty.");
        this.jobRole = jobRole.trim();
    }

    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }

    public void setBasicSalary(double basicSalary) {
        if (!Validator.isValidSalary(basicSalary)) throw new IllegalArgumentException("Salary must be greater than 0.");
        this.basicSalary = basicSalary;
    }

    // ---------- Methods that subclasses override (polymorphism) ----------

    /** Bonus paid to a normal employee: 5% of basic salary. */
    public double calculateBonus() {
        return basicSalary * 0.05;
    }

    /** Employee type, also stored in the emp_type column of the database. */
    public String getEmployeeType() {
        return "Employee";
    }

    // ---------- Display ----------
    public void displayDetails() {
        System.out.println("Employee ID   : " + empId);
        System.out.println("Name          : " + name);
        System.out.println("Type          : " + getEmployeeType());
        System.out.println("Age / Gender  : " + age + " / " + gender);
        System.out.println("Email         : " + email);
        System.out.println("Phone         : " + phone);
        System.out.println("Department    : " + getDepartmentName());
        System.out.println("Job Role      : " + jobRole);
        System.out.println("Joining Date  : " + joiningDate);
        System.out.printf("Basic Salary  : %.2f%n", basicSalary);
    }

    public static void printTableHeader() {
        System.out.printf("%-6s %-20s %-10s %-12s %-18s %-12s %10s%n",
                "ID", "Name", "Type", "Department", "Job Role", "Joined", "Salary");
        System.out.println("-".repeat(95));
    }

    public void printTableRow() {
        System.out.printf("%-6d %-20s %-10s %-12s %-18s %-12s %10.2f%n",
                empId, name, getEmployeeType(), getDepartmentName(), jobRole, joiningDate, basicSalary);
    }
}