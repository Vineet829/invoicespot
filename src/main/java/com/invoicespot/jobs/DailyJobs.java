package com.invoicespot.jobs;

import com.invoicespot.auth.TokenCleanup;
import com.invoicespot.document.AutomaticReminders;
import com.invoicespot.document.OverdueAlerts;
import com.invoicespot.document.RecurringService;
import com.invoicespot.metrics.AppMetrics;
import com.invoicespot.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The work done once a day: new recurring invoices first, then reminders,
// overdue alerts, then token and rate-limit cleanup. A step that fails is
// logged and the others still run. Each step is timed for the metrics, as a
// success or a failure. The time and zone are properties.
@Component
public class DailyJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyJobs.class);

    private final RecurringService recurringService;
    private final AutomaticReminders automaticReminders;
    private final OverdueAlerts overdueAlerts;
    private final TokenCleanup tokenCleanup;
    private final RateLimiter rateLimiter;
    private final AppMetrics metrics;
    private final Clock clock;

    public DailyJobs(
            RecurringService recurringService,
            AutomaticReminders automaticReminders,
            OverdueAlerts overdueAlerts,
            TokenCleanup tokenCleanup,
            RateLimiter rateLimiter,
            AppMetrics metrics,
            Clock clock) {
        this.recurringService = recurringService;
        this.automaticReminders = automaticReminders;
        this.overdueAlerts = overdueAlerts;
        this.tokenCleanup = tokenCleanup;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.clock = clock;
    }

    // One instance runs the jobs each day. Its lock is kept for at least five
    // minutes, so an instance whose clock is a little behind doesn't run them
    // again once the first has finished. A run that dies holding the lock
    // frees it after two hours, and what it didn't do waits for the next day.
    @Scheduled(cron = "${app.jobs.cron:0 0 7 * * *}", zone = "${app.jobs.zone:UTC}")
    @SchedulerLock(name = "daily-jobs", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    public void run() {
        Instant now = Instant.now(clock);
        int made = step("recurring invoices", () -> recurringService.makeDue(now));
        int reminded = step("automatic reminders", () -> automaticReminders.send(now));
        int alerted = step("overdue alerts", () -> overdueAlerts.raise(now));
        int removed = step("expired tokens", tokenCleanup::removeExpired);
        int buckets = step("expired rate limits", rateLimiter::removeExpired);
        LOGGER.info(
                "Daily jobs: {} recurring invoices made, {} reminders sent, {} overdue alerts raised,"
                        + " {} expired tokens and {} expired rate limits removed",
                made,
                reminded,
                alerted,
                removed,
                buckets);
    }

    private int step(String name, IntSupplier job) {
        try {
            return metrics.timeJob(name.replace(' ', '_'), job);
        } catch (RuntimeException exception) {
            LOGGER.error("Daily job step '{}' failed", name, exception);
            return 0;
        }
    }
}
