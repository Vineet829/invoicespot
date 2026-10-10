package com.invoicespot.document;

import java.time.LocalDate;
import java.util.Locale;

// The weeks the documents list can narrow what is owed to, counted from
// today: this week and the two after it, then later, which takes the
// documents with no due date too. Current is what the overview counts as not
// due yet: the invoices with money still owed on them that fall due today or
// later, or have no due date.
public enum DueWeek {
    THIS_WEEK("this-week", 0),
    NEXT_WEEK("next-week", 1),
    WEEK_AFTER("week-after", 2),
    LATER("later", 3),
    CURRENT("current", 0);

    private final String value;
    private final int weeksAhead;

    DueWeek(String value, int weeksAhead) {
        this.value = value;
        this.weeksAhead = weeksAhead;
    }

    public static DueWeek fromValue(String value) {
        for (DueWeek week : values()) {
            if (week.value.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return week;
            }
        }
        throw new IllegalArgumentException("Unknown due week: " + value);
    }

    // The week's first day; later and current run on from there.
    LocalDate firstDay(LocalDate today) {
        return today.plusWeeks(weeksAhead);
    }

    // Whether it has no last day, and so takes the documents with no due date
    // too.
    boolean runsOn() {
        return this == LATER || this == CURRENT;
    }
}
