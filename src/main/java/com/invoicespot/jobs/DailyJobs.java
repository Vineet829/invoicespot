package com.invoicespot.jobs;

import com.invoicespot.auth.TokenCleanup;
import com.invoicespot.document.AutomaticReminders;
import com.invoicespot.document.OverdueAlerts;
import com.invoicespot.document.RecurringService;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The work done once a day: new recurring invoices first, then reminders,
// overdue alerts and token cleanup. A step that fails is logged and the
// others still run. The time and zone are properties.
@Component
public class DailyJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyJobs.class);

    private final RecurringService recurringService;
    private final AutomaticReminders automaticReminders;
    private final OverdueAlerts overdueAlerts;
    private final TokenCleanup tokenCleanup;
    private final Clock clock;

    public DailyJobs(
            RecurringService recurringService,
            AutomaticReminders automaticReminders,
            OverdueAlerts overdueAlerts,
            TokenCleanup tokenCleanup,
            Clock clock) {
        this.recurringService = recurringService;
        this.automaticReminders = automaticReminders;
        this.overdueAlerts = overdueAlerts;
        this.tokenCleanup = tokenCleanup;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.cron:0 0 7 * * *}", zone = "${app.jobs.zone:UTC}")
    public void run() {
        Instant now = Instant.now(clock);
        int made = step("recurring invoices", () -> recurringService.makeDue(now));
        int reminded = step("automatic reminders", () -> automaticReminders.send(now));
        int alerted = step("overdue alerts", () -> overdueAlerts.raise(now));
        int removed = step("expired tokens", tokenCleanup::removeExpired);
        LOGGER.info(
                "Daily jobs: {} recurring invoices made, {} reminders sent, {} overdue alerts raised,"
                        + " {} expired tokens removed",
                made,
                reminded,
                alerted,
                removed);
    }

    private static int step(String name, IntSupplier job) {
        try {
            return job.getAsInt();
        } catch (RuntimeException exception) {
            LOGGER.error("Daily job step '{}' failed", name, exception);
            return 0;
        }
    }
}
