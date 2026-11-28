package src;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Calculates salaries and saves/loads them from the salary_records table. */
public class SalaryService {

    private final AttendanceService attendanceService = new AttendanceService();

    /** Calculates the salary for a month (using that month's absences), saves it, and returns it. */
    public Salary calculateAndSave(Employee emp, int month, int year) throws SQLException {
        return calculateAndSave(emp, month, year, 0);
    }

    private Salary calculateAndSave(Employee emp, int month, int year, double bonusAdjustment) throws SQLException {
        int absentDays = attendanceService.countAbsentDaysInMonth(emp.getEmpId(), month, year);
        Salary s = Salary.calculate(emp, month, year, absentDays, bonusAdjustment);

        // Recalculating the same month replaces the old record.
        String sql = "INSERT INTO salary_records (emp_id, salary_month, salary_year, basic_salary, hra, da, bonus, "
                + "gross_salary, pf, tax, absent_deduction, total_deductions, net_salary, absent_days) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE basic_salary=VALUES(basic_salary), hra=VALUES(hra), da=VALUES(da), "
                + "bonus=VALUES(bonus), gross_salary=VALUES(gross_salary), pf=VALUES(pf), tax=VALUES(tax), "
                + "absent_deduction=VALUES(absent_deduction), total_deductions=VALUES(total_deductions), "
                + "net_salary=VALUES(net_salary), absent_days=VALUES(absent_days)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, emp.getEmpId());
            ps.setInt(2, month);
            ps.setInt(3, year);
            ps.setDouble(4, s.getBasic());
            ps.setDouble(5, s.getHra());
            ps.setDouble(6, s.getDa());
            ps.setDouble(7, s.getBonus());
            ps.setDouble(8, s.getGross());
            ps.setDouble(9, s.getPf());
            ps.setDouble(10, s.getTax());
            ps.setDouble(11, s.getAbsentDeduction());
            ps.setDouble(12, s.getTotalDeductions());
            ps.setDouble(13, s.getNet());
            ps.setInt(14, s.getAbsentDays());
            ps.executeUpdate();
        }
        String slipSql = "INSERT INTO salary_slips "
                + "(emp_id,salary_month,salary_year,basic_salary,hra,allowances,bonus,deductions,gross_salary,net_salary) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE basic_salary=VALUES(basic_salary), hra=VALUES(hra), "
                + "allowances=VALUES(allowances), bonus=VALUES(bonus), deductions=VALUES(deductions), "
                + "gross_salary=VALUES(gross_salary), net_salary=VALUES(net_salary)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(slipSql)) {
            ps.setInt(1, emp.getEmpId());
            ps.setInt(2, month);
            ps.setInt(3, year);
            ps.setDouble(4, s.getBasic());
            ps.setDouble(5, s.getHra());
            ps.setDouble(6, s.getDa());
            ps.setDouble(7, s.getBonus());
            ps.setDouble(8, s.getTotalDeductions());
            ps.setDouble(9, s.getGross());
            ps.setDouble(10, s.getNet());
            ps.executeUpdate();
        }
        return s;
    }

    /** Loads a saved salary record; returns null if it has not been calculated yet. */
    public Salary getSalary(Employee emp, int month, int year) throws SQLException {
        String sql = "SELECT * FROM salary_records WHERE emp_id = ? AND salary_month = ? AND salary_year = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, emp.getEmpId());
            ps.setInt(2, month);
            ps.setInt(3, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Salary(emp.getEmpId(), emp.getName(), month, year,
                        rs.getDouble("basic_salary"), rs.getDouble("hra"), rs.getDouble("da"),
                        rs.getDouble("bonus"), rs.getDouble("gross_salary"), rs.getDouble("pf"),
                        rs.getDouble("tax"), rs.getDouble("absent_deduction"),
                        rs.getDouble("total_deductions"), rs.getDouble("net_salary"), rs.getInt("absent_days"));
            }
        }
    }

    public List<Salary> getSalaryHistory(Employee emp) throws SQLException {
        List<Salary> history = new ArrayList<>();
        String sql = "SELECT * FROM salary_records WHERE emp_id = ? ORDER BY salary_year DESC, salary_month DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, emp.getEmpId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new Salary(emp.getEmpId(), emp.getName(),
                            rs.getInt("salary_month"), rs.getInt("salary_year"),
                            rs.getDouble("basic_salary"), rs.getDouble("hra"), rs.getDouble("da"),
                            rs.getDouble("bonus"), rs.getDouble("gross_salary"), rs.getDouble("pf"),
                            rs.getDouble("tax"), rs.getDouble("absent_deduction"),
                            rs.getDouble("total_deductions"), rs.getDouble("net_salary"),
                            rs.getInt("absent_days")));
                }
            }
        }
        return history;
    }

    public boolean ensureDemoSalary(Employee emp, int month, int year) throws SQLException {
        int absentDays = attendanceService.countAbsentDaysInMonth(emp.getEmpId(), month, year);
        double bonusAdjustment = demoBonusAdjustment(month);
        Salary existing = getSalary(emp, month, year);
        double expectedBonus = emp.calculateBonus() + bonusAdjustment;
        if (existing != null && existing.getAbsentDays() == absentDays
                && Math.abs(existing.getBonus() - expectedBonus) < 0.01) return false;
        calculateAndSave(emp, month, year, bonusAdjustment);
        return true;
    }

    public int ensureDemoSalaryHistory(Employee emp, int year, int finalMonth) throws SQLException {
        int generated = 0;
        for (int month = 1; month <= finalMonth; month++) {
            if (ensureDemoSalary(emp, month, year)) generated++;
        }
        return generated;
    }

    private double demoBonusAdjustment(int month) {
        double[] adjustments = {0, 2000, 5000, 2000, 5000, 2000, 5000, 2000, 5000, 3000, 2000, 5000};
        if (month < 1 || month > adjustments.length)
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        return adjustments[month - 1];
    }
}
