package com.invoicespot.document;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

// Where a document is in its life: a draft to review, issued with its
// number, or void, keeping its number but counting for nothing.
public enum DocumentState {
    DRAFT,
    ISSUED,
    VOID;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DocumentState fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
