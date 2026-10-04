package com.invoicespot.document;

import java.time.LocalDate;
import java.util.Locale;

// The weeks the overview groups what is owed by, counted from today: this
// week and the two after it, then later, which takes the documents with no
// due date too.
public enum DueWeek {
    THIS_WEEK("this-week"),
    NEXT_WEEK("next-week"),
    WEEK_AFTER("week-after"),
    LATER("later");

    private final String value;

    DueWeek(String value) {
        this.value = value;
    }

    public static DueWeek fromValue(String value) {
        for (DueWeek week : values()) {
            if (week.value.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return week;
            }
        }
        throw new IllegalArgumentException("Unknown due week: " + value);
    }

    // The week's first day; later runs on from there.
    LocalDate firstDay(LocalDate today) {
        return today.plusWeeks(ordinal());
    }
}
