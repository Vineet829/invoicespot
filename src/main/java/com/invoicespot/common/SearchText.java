package com.invoicespot.common;

import java.util.Locale;

public final class SearchText {

    public static final char ESCAPE = '!';

    private SearchText() {}

    public static String containing(String text) {
        String term = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        StringBuilder pattern = new StringBuilder("%");
        for (char character : term.toCharArray()) {
            if (character == '%' || character == '_' || character == ESCAPE) {
                pattern.append(ESCAPE);
            }
            pattern.append(character);
        }
        return pattern.append('%').toString();
    }
}
