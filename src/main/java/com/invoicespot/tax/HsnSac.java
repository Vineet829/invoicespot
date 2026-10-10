package com.invoicespot.tax;

import com.invoicespot.common.ApiException;
import org.springframework.http.HttpStatus;

// An HSN code (goods) or SAC code (services): 4 to 8 digits.
public final class HsnSac {

    private HsnSac() {}

    // Spaces dropped, or null when nothing was given.
    public static String parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.replaceAll("\\s+", "");
        if (!code.matches("[0-9]{4,8}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an HSN or SAC code of 4 to 8 digits");
        }
        return code;
    }
}
