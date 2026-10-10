package com.invoicespot.mail;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Fills an email's {name} placeholders in one pass over the text the business
// wrote, so no value is read for placeholders itself: a client named
// "{business}" is greeted by that name. A placeholder without a value stays as
// it was typed. A line whose placeholders all came out empty, such as a link
// an email goes without, is left out, along with the blank lines it leaves.
public final class Placeholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)\\}");

    private Placeholders() {}

    public static String fill(String text, Map<String, String> values) {
        StringBuilder filled = new StringBuilder();
        boolean first = true;
        boolean dropped = false;
        for (String line : text.split("\n", -1)) {
            Matcher matcher = PLACEHOLDER.matcher(line);
            StringBuilder out = new StringBuilder();
            boolean placeholders = false;
            boolean allEmpty = true;
            while (matcher.find()) {
                String value = values.get(matcher.group(1));
                placeholders = true;
                allEmpty &= value != null && value.isEmpty();
                matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
            }
            matcher.appendTail(out);
            if (placeholders && allEmpty) {
                dropped = true;
                continue;
            }
            if (!first) {
                filled.append('\n');
            }
            first = false;
            filled.append(out);
        }
        return dropped
                ? filled.toString().replaceAll("\n{3,}", "\n\n").replaceAll("^\n+|\n+$", "")
                : filled.toString();
    }
}
