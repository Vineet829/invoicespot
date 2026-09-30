package com.invoicespot.auth;

public final class PasswordPolicy {

    public static final String REQUIREMENTS =
            "Password must be at least 8 characters long, with at least 1 uppercase and"
                    + " lowercase letters and at least 1 symbol";

    private PasswordPolicy() {}

    public static boolean isStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (char character : password.toCharArray()) {
            if (Character.isLowerCase(character)) {
                lower = true;
            } else if (Character.isUpperCase(character)) {
                upper = true;
            } else if (Character.isDigit(character)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        return lower && upper && digit && symbol;
    }
}
