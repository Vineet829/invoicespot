package com.invoicespot.auth;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    // What isStrong asks for, every kind of character named.
    public static final String REQUIREMENTS = "Password must be at least 8 characters long, with at least 1"
            + " uppercase letter, 1 lowercase letter, 1 number and 1 symbol";

    // Passwords are kept as bcrypt hashes, which take at most 72 bytes of
    // a password: one letter of English is a byte, an accented letter two,
    // a letter of most other scripts three. A longer password can't be kept.
    public static final int MAX_BYTES = 72;
    public static final String TOO_LONG = "Password can be at most 72 characters long, or fewer with letters"
            + " outside the English alphabet";

    private PasswordPolicy() {}

    public static boolean fits(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

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
