package com.invoicespot.report.dto;

import java.time.LocalDate;

// The days the overview covers and the days it is compared with, in the
// business's calendar, and how its trend is cut up: "day", "week" or "month".
public record OverviewPeriod(
        String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, String step) {}
