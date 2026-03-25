package pt.unl.fct.di.adc.firstwebapp.util;

public class CreateAccountData {

    public String username;
    public String password;
    public String confirmation;
    public String email;
    public String phone;
    public String address;
    public String role; // USER, BOFFICER, ADMIN

    public CreateAccountData() {
    }

    public boolean isValid() {
        if (username == null || username.trim().isEmpty()) return false;
        if (password == null || password.trim().isEmpty()) return false;
        if (confirmation == null || !password.equals(confirmation)) return false;
        if (email == null || email.trim().isEmpty()) return false;
        if (role == null || role.trim().isEmpty()) return false;
        if (!role.equals("USER") && !role.equals("BOFFICER") && !role.equals("ADMIN")) return false;
        // phone and address are optional
        return true;
    }
}
