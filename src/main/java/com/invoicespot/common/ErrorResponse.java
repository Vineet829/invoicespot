package com.invoicespot.common;

public record ErrorResponse(boolean success, String message, int statusCode, Object stack) {

    public static ErrorResponse of(int statusCode, String message) {
        return new ErrorResponse(false, message, statusCode, null);
    }
}
