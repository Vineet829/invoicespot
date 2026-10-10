package com.invoicespot.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.invoicespot.jobs.DailyJobs;
import com.invoicespot.jobs.JobsConfig;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
class DemoRenewalTest {

    @Mock
    private DemoSeedRunner seedRunner;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(JobsConfig.class, DemoRenewal.class)
                .withBean(DemoSeedRunner.class, () -> seedRunner);
    }

    private static List<String> cronTasks(ApplicationContext context) {
        return context.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();
    }

    @Test
    @DisplayName("a seeded demo set to reset is renewed every day at the jobs' time, under a lock of its own")
    void daily() throws Exception {
        runner().withPropertyValues("app.demo.seed=true", "app.demo.reset=true")
                .run(context -> assertThat(cronTasks(context)).containsExactly("0 0 7 * * *"));
        runner().withPropertyValues("app.demo.seed=true", "app.demo.reset=true", "app.jobs.cron=0 30 6 * * *")
                .run(context -> assertThat(cronTasks(context)).containsExactly("0 30 6 * * *"));
        SchedulerLock lock = DemoRenewal.class.getMethod("run").getAnnotation(SchedulerLock.class);
        assertThat(lock.name())
                .isEqualTo("demo-renewal")
                .isNotEqualTo(DailyJobs.class.getMethod("run").getAnnotation(SchedulerLock.class).name());

        new DemoRenewal(seedRunner).run();
        verify(seedRunner).renew();
    }

    @Test
    @DisplayName("it isn't renewed when reset is off, when the demo isn't seeded, or when the jobs are off")
    void switchedOff() {
        runner().withPropertyValues("app.demo.seed=true")
                .run(context -> assertThat(context).doesNotHaveBean(DemoRenewal.class));
        runner().withPropertyValues("app.demo.seed=true", "app.demo.reset=false")
                .run(context -> assertThat(cronTasks(context)).isEmpty());
        runner().withPropertyValues("app.demo.reset=true")
                .run(context -> assertThat(context).doesNotHaveBean(DemoRenewal.class));
        runner().withPropertyValues("app.demo.seed=true", "app.demo.reset=true", "app.jobs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledTaskHolder.class));
    }
}
