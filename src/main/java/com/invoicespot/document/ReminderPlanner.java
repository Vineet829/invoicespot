package com.invoicespot.document;

import com.invoicespot.settings.BusinessSettings;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

// Whether an unpaid invoice gets an automatic reminder today, by the
// business's calendar: some days before it is due, on the day, then every
// few days after, never twice in a day and never more than the most allowed.
final class ReminderPlanner {

    private ReminderPlanner() {}

    static boolean isDue(Document invoice, BusinessSettings settings, LocalDate today, ZoneId zone) {
        if (invoice.getDueDate() == null || invoice.getRemindersSent() >= settings.getReminderMax()) {
            return false;
        }
        if (invoice.getLastRemindedAt() != null
                && LocalDate.ofInstant(invoice.getLastRemindedAt(), zone).equals(today)) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.ofInstant(invoice.getDueDate(), zone), today);
        int before = settings.getReminderDaysBefore();
        int every = settings.getReminderEveryDays();
        return (before > 0 && days == -before)
                || (settings.isReminderOnDue() && days == 0)
                || (every > 0 && days > 0 && days % every == 0);
    }
}
