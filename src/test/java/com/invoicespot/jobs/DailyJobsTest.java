package com.invoicespot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.auth.TokenCleanup;
import com.invoicespot.document.AutomaticReminders;
import com.invoicespot.document.OverdueAlerts;
import com.invoicespot.document.RecurringService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

@ExtendWith(MockitoExtension.class)
class DailyJobsTest {

    private static final Instant NOW = Instant.parse("2026-09-30T07:00:00Z");

    @Mock private RecurringService recurringService;
    @Mock private AutomaticReminders automaticReminders;
    @Mock private OverdueAlerts overdueAlerts;
    @Mock private TokenCleanup tokenCleanup;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JobsConfig.class)
            .withBean(DailyJobs.class, () -> new DailyJobs(
                    recurringService, automaticReminders, overdueAlerts, tokenCleanup, Clock.systemUTC()));

    private static List<String> cronTasks(ApplicationContext context) {
        return context.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();
    }

    @Test
    @DisplayName("every step runs at the same moment, even after one of them fails")
    void everyStepRuns() {
        when(recurringService.makeDue(NOW)).thenThrow(new IllegalStateException("database is down"));
        when(automaticReminders.send(NOW)).thenReturn(2);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        new DailyJobs(recurringService, automaticReminders, overdueAlerts, tokenCleanup, clock).run();

        verify(recurringService).makeDue(NOW);
        verify(automaticReminders).send(NOW);
        verify(overdueAlerts).raise(NOW);
        verify(tokenCleanup).removeExpired();
    }

    @Test
    @DisplayName("the jobs run once a day at 07:00 by default, at a set time, or not at all when turned off")
    void schedule() {
        runner.run(context -> assertThat(cronTasks(context)).containsExactly("0 0 7 * * *"));
        runner.withPropertyValues("app.jobs.cron=0 30 6 * * *")
                .run(context -> assertThat(cronTasks(context)).containsExactly("0 30 6 * * *"));
        runner.withPropertyValues("app.jobs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledTaskHolder.class));
    }
}
