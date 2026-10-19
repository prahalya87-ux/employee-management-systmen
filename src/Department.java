package src;

/** Holds department details. */
public class Department {

    private int deptId;
    private String deptName;
    private String location;

    public Department(int deptId, String deptName, String location) {
        this.deptId = deptId;
        setDeptName(deptName);
        this.location = location;
    }

    public int getDeptId() { return deptId; }
    public String getDeptName() { return deptName; }
    public String getLocation() { return location; }

    public void setDeptName(String deptName) {
        if (deptName == null || deptName.trim().isEmpty())
            throw new IllegalArgumentException("Department name cannot be empty.");
        this.deptName = deptName.trim();
    }

    public void setLocation(String location) { this.location = location; }

    @Override
    public String toString() {
        return String.format("%-6d %-20s %-15s", deptId, deptName, location == null ? "-" : location);
    }
}
