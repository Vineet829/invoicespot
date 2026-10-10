package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.settings.BusinessSettings;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReminderPlannerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDate DUE = LocalDate.of(2026, 10, 1);

    private static BusinessSettings settings(int before, boolean onDue, int every, int most) {
        BusinessSettings settings = new BusinessSettings();
        settings.setReminderDaysBefore(before);
        settings.setReminderOnDue(onDue);
        settings.setReminderEveryDays(every);
        settings.setReminderMax(most);
        return settings;
    }

    private static Document invoice(int sent, LocalDate lastReminded) {
        Document invoice = new Document();
        invoice.setDueDate(DUE.atTime(LocalTime.NOON).atZone(ZONE).toInstant());
        invoice.setRemindersSent(sent);
        if (lastReminded != null) {
            invoice.setLastRemindedAt(lastReminded.atTime(9, 0).atZone(ZONE).toInstant());
        }
        return invoice;
    }

    // The days the job sends a reminder on, running every day from `first` to
    // `last` except the days it was down.
    private static List<LocalDate> sent(
            Document invoice, BusinessSettings settings, LocalDate first, LocalDate last, LocalDate... down) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate today = first; !today.isAfter(last); today = today.plusDays(1)) {
            if (List.of(down).contains(today) || !ReminderPlanner.isDue(invoice, settings, today, ZONE)) {
                continue;
            }
            days.add(today);
            invoice.setRemindersSent(invoice.getRemindersSent() + 1);
            invoice.setLastRemindedAt(today.atTime(9, 0).atZone(ZONE).toInstant());
        }
        return days;
    }

    @Test
    @DisplayName("a reminder goes out before, on and every so often after the due date")
    void schedule() {
        assertThat(sent(invoice(0, null), settings(3, true, 7, 5), DUE.minusDays(6), DUE.plusDays(15)))
                .containsExactly(DUE.minusDays(3), DUE, DUE.plusDays(7), DUE.plusDays(14));
    }

    @Test
    @DisplayName("a reminder missed on a day the job didn't run goes out the next time it does, once")
    void catchesUp() {
        assertThat(sent(invoice(0, null), settings(3, true, 7, 5), DUE.minusDays(6), DUE.plusDays(15),
                        DUE.minusDays(3), DUE.minusDays(2), DUE.plusDays(7)))
                .containsExactly(DUE.minusDays(1), DUE, DUE.plusDays(8), DUE.plusDays(14));
    }

    @Test
    @DisplayName("no catching up on days before the invoice was issued, or before the due date once it has passed")
    void notTooLate() {
        Document backdated = invoice(0, null);
        backdated.setIssueDate(DUE.plusDays(2));
        assertThat(sent(backdated, settings(3, true, 7, 5), DUE.plusDays(2), DUE.plusDays(8)))
                .containsExactly(DUE.plusDays(7));
        assertThat(sent(invoice(0, null), settings(3, false, 0, 5), DUE.minusDays(6), DUE.plusDays(5),
                        DUE.minusDays(3), DUE.minusDays(2), DUE.minusDays(1), DUE))
                .isEmpty();
        assertThat(sent(invoice(0, null), settings(3, false, 0, 5), DUE.minusDays(6), DUE.plusDays(5),
                        DUE.minusDays(3), DUE.minusDays(2)))
                .containsExactly(DUE.minusDays(1));
    }

    @Test
    @DisplayName("a reminder sent by hand since the missed day counts for it")
    void sentByHand() {
        assertThat(sent(invoice(1, DUE.minusDays(2)), settings(3, true, 7, 5), DUE.minusDays(2), DUE.plusDays(1),
                        DUE.minusDays(3)))
                .containsExactly(DUE);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "turned-off steps send nothing, 0, false, 0, 5, 0, , 2026-10-01",
        "the most allowed were sent, 3, true, 7, 3, 3, , 2026-10-01",
        "already reminded today, 3, true, 7, 5, 1, 2026-10-01, 2026-10-01",
    })
    @DisplayName("no reminder when a step is off, the most allowed went out, or one went out today")
    void none(
            String reason, int before, boolean onDue, int every, int most, int sent, LocalDate last, LocalDate today) {
        assertThat(ReminderPlanner.isDue(invoice(sent, last), settings(before, onDue, every, most), today, ZONE))
                .isFalse();
    }
}
