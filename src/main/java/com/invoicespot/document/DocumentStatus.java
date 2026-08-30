package com.invoicespot.document;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentStatus {
    PAID("Paid"),
    NOT_FULLY_PAID("Not Fully Paid"),
    NOT_PAID("Not Paid");

    private final String value;

    DocumentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DocumentStatus fromValue(String value) {
        for (DocumentStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown document status: " + value);
    }
}
