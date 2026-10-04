package com.invoicespot.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

// The latest day it is anywhere: today in UTC+14, the time zone furthest
// ahead. Dates are refused only after it, so nobody is refused today's date
// because of time zones; a date later than it is in the future everywhere.
public final class Today {

    private static final ZoneId FURTHEST_AHEAD = ZoneId.of("Pacific/Kiritimati");

    private Today() {}

    public static LocalDate latest(Clock clock) {
        return LocalDate.now(clock.withZone(FURTHEST_AHEAD));
    }
}
