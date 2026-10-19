package src;

public class Validator {
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
 
    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("\\d{10}");
    }
 
    public static boolean isValidSalary(double salary) {
        return salary > 0;
    }
 
    public static boolean isValidAge(int age) {
        return age >= 18 && age <= 65;
    }
 
    public static boolean isValidGender(String gender) {
        return gender != null && (gender.equalsIgnoreCase("Male")
                || gender.equalsIgnoreCase("Female")
                || gender.equalsIgnoreCase("Other"));
    }
}

