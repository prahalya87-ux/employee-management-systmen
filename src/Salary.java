package src;

/**
 * Calculates and stores one month's salary details for an employee.
 *
 * Allowances : HRA = 20% of basic, DA = 10% of basic, Bonus (depends on employee type)
 * Deductions : PF = 12% of basic, Tax = 5% of gross (10% if gross > 50,000),
 *              Absence = (basic / 30) per absent day
 */
public class Salary {

    private int empId;
    private String empName;
    private int month;
    private int year;
    private double basic, hra, da, bonus, gross;
    private double pf, tax, absentDeduction, totalDeductions, net;
    private int absentDays;

    /** Full constructor - used when loading a saved record from the database. */
    public Salary(int empId, String empName, int month, int year, double basic, double hra,
                  double da, double bonus, double gross, double pf, double tax,
                  double absentDeduction, double totalDeductions, double net, int absentDays) {
        this.empId = empId; this.empName = empName; this.month = month; this.year = year;
        this.basic = basic; this.hra = hra; this.da = da; this.bonus = bonus; this.gross = gross;
        this.pf = pf; this.tax = tax; this.absentDeduction = absentDeduction;
        this.totalDeductions = totalDeductions; this.net = net; this.absentDays = absentDays;
    }

    /** Calculates a new salary. calculateBonus() is polymorphic: it depends on the object's real class. */
    public static Salary calculate(Employee emp, int month, int year, int absentDays) {
        double basic = emp.getBasicSalary();
        double hra = basic * 0.20;
        double da = basic * 0.10;
        double bonus = emp.calculateBonus();
        double gross = basic + hra + da + bonus;

        double pf = basic * 0.12;
        double tax = gross > 50000 ? gross * 0.10 : gross * 0.05;
        double absentDeduction = (basic / 30.0) * absentDays;
        double totalDeductions = pf + tax + absentDeduction;
        double net = Math.max(0, gross - totalDeductions);

        return new Salary(emp.getEmpId(), emp.getName(), month, year, basic, hra, da, bonus,
                gross, pf, tax, absentDeduction, totalDeductions, net, absentDays);
    }

    public int getEmpId() { return empId; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public double getBasic() { return basic; }
    public double getHra() { return hra; }
    public double getDa() { return da; }
    public double getBonus() { return bonus; }
    public double getGross() { return gross; }
    public double getPf() { return pf; }
    public double getTax() { return tax; }
    public double getAbsentDeduction() { return absentDeduction; }
    public double getTotalDeductions() { return totalDeductions; }
    public double getNet() { return net; }
    public int getAbsentDays() { return absentDays; }

    public void printSlip() {
        System.out.println("\n==============================================");
        System.out.println("                 SALARY SLIP");
        System.out.println("==============================================");
        System.out.println("Employee ID : " + empId);
        System.out.println("Name        : " + empName);
        System.out.printf("Month/Year  : %02d/%d%n", month, year);
        System.out.println("----------------------------------------------");
        System.out.println("EARNINGS");
        System.out.printf("  Basic Salary          : %10.2f%n", basic);
        System.out.printf("  HRA (20%%)             : %10.2f%n", hra);
        System.out.printf("  DA (10%%)              : %10.2f%n", da);
        System.out.printf("  Bonus                 : %10.2f%n", bonus);
        System.out.printf("  Gross Salary          : %10.2f%n", gross);
        System.out.println("----------------------------------------------");
        System.out.println("DEDUCTIONS");
        System.out.printf("  PF (12%% of basic)     : %10.2f%n", pf);
        System.out.printf("  Income Tax            : %10.2f%n", tax);
        System.out.printf("  Absence (%d day(s))     : %10.2f%n", absentDays, absentDeduction);
        System.out.printf("  Total Deductions      : %10.2f%n", totalDeductions);
        System.out.println("----------------------------------------------");
        System.out.printf("NET SALARY              : %10.2f%n", net);
        System.out.println("==============================================");
    }
}
