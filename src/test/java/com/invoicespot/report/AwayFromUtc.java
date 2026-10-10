package com.invoicespot.report;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

// Time zones to put a business in so that a day read on UTC's calendar comes
// out a day wrong, whatever the hour: one more than twelve hours ahead of UTC,
// where a day begins on UTC's day before and its noon still falls on that
// day, and one far behind UTC, where today is UTC's yesterday until late
// morning UTC. Each is used only while its next midnight is at least an hour
// away, so a test's today doesn't change while it runs.
public class AwayFromUtc implements ArgumentsProvider {

    @Override
    public Stream<Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(
                        // UTC+14, or UTC+13 in the last hour of Kiritimati's day.
                        awayFromMidnight("Pacific/Kiritimati", "Pacific/Tongatapu"),
                        // UTC-11, or UTC-10 in the last hour of Pago Pago's day.
                        awayFromMidnight("Pacific/Pago_Pago", "Pacific/Honolulu"))
                .map(Arguments::of);
    }

    // The zone, unless its day is in its last hour.
    private static ZoneId awayFromMidnight(String zone, String otherwise) {
        ZoneId first = ZoneId.of(zone);
        return LocalTime.now(first).isBefore(LocalTime.of(23, 0)) ? first : ZoneId.of(otherwise);
    }
}
