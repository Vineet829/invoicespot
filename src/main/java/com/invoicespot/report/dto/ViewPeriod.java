package com.invoicespot.report.dto;

import java.time.LocalDate;

// The days a view covers, in the business's calendar, and the days just
// before them it is compared with. All dates have neither, and the key is
// empty for them.
public record ViewPeriod(String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {}
