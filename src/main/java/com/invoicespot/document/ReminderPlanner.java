package com.invoicespot.document;

import com.invoicespot.settings.BusinessSettings;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// Whether an unpaid invoice gets an automatic reminder today, by the
// business's calendar: some days before it is due, on the day, then every
// few days after, never twice in a day and never more than the most allowed.
// A reminder the job missed, because it didn't run that day, goes out the
// next time it runs, once, unless one went out since or the invoice was
// issued after that day. One before the due date is only sent until then.
final class ReminderPlanner {

    private ReminderPlanner() {}

    static boolean isDue(Document invoice, BusinessSettings settings, LocalDate today, ZoneId zone) {
        if (invoice.getDueDate() == null || invoice.getRemindersSent() >= settings.getReminderMax()) {
            return false;
        }
        LocalDate lastSent = invoice.getLastRemindedAt() == null
                ? null
                : LocalDate.ofInstant(invoice.getLastRemindedAt(), zone);
        if (today.equals(lastSent)) {
            return false;
        }
        return latestScheduled(LocalDate.ofInstant(invoice.getDueDate(), zone), settings, today)
                .filter(day -> invoice.getIssueDate() == null || !day.isBefore(invoice.getIssueDate()))
                .filter(day -> lastSent == null || day.isAfter(lastSent))
                .isPresent();
    }

    // The latest day, up to today, that the schedule asks for a reminder on.
    private static Optional<LocalDate> latestScheduled(LocalDate due, BusinessSettings settings, LocalDate today) {
        LocalDate latest = null;
        int before = settings.getReminderDaysBefore();
        if (before > 0 && !today.isBefore(due.minusDays(before)) && !today.isAfter(due)) {
            latest = due.minusDays(before);
        }
        if (settings.isReminderOnDue() && !today.isBefore(due)) {
            latest = due;
        }
        int every = settings.getReminderEveryDays();
        long late = ChronoUnit.DAYS.between(due, today);
        if (every > 0 && late >= every) {
            latest = due.plusDays(late - late % every);
        }
        return Optional.ofNullable(latest);
    }
}
