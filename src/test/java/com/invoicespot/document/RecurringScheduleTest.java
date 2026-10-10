package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringScheduleTest {

    private static final LocalDate JANUARY_31 = LocalDate.of(2026, 1, 31);

    // A monthly schedule moved to the end of January after one invoice, with
    // the given limits.
    private static RecurringSchedule movedToJanuary(Integer maxRuns, LocalDate endDate) {
        RecurringSchedule schedule = new RecurringSchedule();
        schedule.setFrequency(RecurringFrequency.MONTHLY);
        schedule.setStartDate(JANUARY_31);
        schedule.setStartOffset(1);
        schedule.setRunsDone(2);
        schedule.setMaxRuns(maxRuns);
        schedule.setEndDate(endDate);
        return schedule;
    }

    @Test
    @DisplayName("each run is counted from the start date, less the runs made before it was set, so month ends"
            + " don't drift: Jan 31, Feb 28, then Mar 31")
    void monthEnds() {
        RecurringSchedule schedule = movedToJanuary(null, null);

        assertThat(schedule.runAfter(1)).isEqualTo(JANUARY_31);
        assertThat(schedule.runAfter(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedule.runAfter(3)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(schedule.runAfter(4)).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("there is no run once the most allowed are made, counting those made before the start date, nor"
            + " after the end date")
    void limits() {
        RecurringSchedule most = movedToJanuary(3, null);
        assertThat(most.runAfter(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(most.runAfter(3)).isNull();

        RecurringSchedule ending = movedToJanuary(null, LocalDate.of(2026, 3, 15));
        assertThat(ending.runAfter(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ending.runAfter(3)).isNull();
    }
}
