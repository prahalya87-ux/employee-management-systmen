package src;

import java.time.LocalDate;

/** A Developer is an Employee with a 10% bonus. Demonstrates inheritance and overriding. */
public class Developer extends Employee {

    public Developer(int empId, String name, int age, String gender, String email,
                     String phone, int deptId, String jobRole,
                     LocalDate joiningDate, double basicSalary) {
        super(empId, name, age, gender, email, phone, deptId, jobRole, joiningDate, basicSalary);
    }

    @Override
    public double calculateBonus() {
        return getBasicSalary() * 0.10;
    }

    @Override
    public String getEmployeeType() {
        return "Developer";
    }
}
