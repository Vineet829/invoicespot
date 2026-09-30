package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.settings.BusinessSettings;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
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

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "2026-09-28, true, three days before",
        "2026-09-29, false, two days before",
        "2026-10-01, true, on the due date",
        "2026-10-05, false, four days after",
        "2026-10-08, true, a week after",
        "2026-10-15, true, two weeks after",
    })
    @DisplayName("a reminder goes out before, on and every so often after the due date")
    void schedule(LocalDate today, boolean due, String when) {
        assertThat(ReminderPlanner.isDue(invoice(0, null), settings(3, true, 7, 5), today, ZONE)).isEqualTo(due);
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
