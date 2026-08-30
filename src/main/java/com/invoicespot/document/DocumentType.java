package com.invoicespot.document;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentType {
    INVOICE("Invoice"),
    RECEIPT("Receipt"),
    QUOTATION("Quotation");

    private final String value;

    DocumentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DocumentType fromValue(String value) {
        for (DocumentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown document type: " + value);
    }
}
