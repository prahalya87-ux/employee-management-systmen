package src;

import java.time.LocalDate;

/** One attendance record: an employee, a date, and Present/Absent. */
public class Attendance {

    private int empId;
    private LocalDate date;
    private String status;

    public Attendance(int empId, LocalDate date, String status) {
        this.empId = empId;
        this.date = date;
        setStatus(status);
    }

    public int getEmpId() { return empId; }
    public LocalDate getDate() { return date; }
    public String getStatus() { return status; }

    /** Only "Present" or "Absent" are accepted (any capitalisation). */
    public void setStatus(String status) {
        if (status == null) throw new IllegalArgumentException("Status is required.");
        if (status.equalsIgnoreCase("Present")) this.status = "Present";
        else if (status.equalsIgnoreCase("Absent")) this.status = "Absent";
        else throw new IllegalArgumentException("Status must be Present or Absent.");
    }
}
