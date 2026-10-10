package com.invoicespot.document;

import java.time.LocalDate;

public enum RecurringFrequency {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    QUARTERLY("quarterly"),
    YEARLY("yearly");

    private final String label;

    RecurringFrequency(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // The date of a run, counted from the first one so that month ends don't
    // drift: a schedule from Jan 31 runs on Feb 28, then Mar 31.
    LocalDate run(LocalDate start, int runs) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(runs);
            case MONTHLY -> start.plusMonths(runs);
            case QUARTERLY -> start.plusMonths(3L * runs);
            case YEARLY -> start.plusYears(runs);
        };
    }
}
