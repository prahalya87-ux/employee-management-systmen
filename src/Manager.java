package src;
import java.time.LocalDate;

/** A Manager is an Employee with a higher bonus (15%). Demonstrates inheritance and overriding. */
public class Manager extends Employee {

    public Manager(int empId, String name, int age, String gender, String email,
                   String phone, int deptId, String jobRole,
                   LocalDate joiningDate, double basicSalary) {
        super(empId, name, age, gender, email, phone, deptId, jobRole, joiningDate, basicSalary);
    }

    @Override
    public double calculateBonus() {
        return getBasicSalary() * 0.15;
    }

    @Override
    public String getEmployeeType() {
        return "Manager";
    }
}
