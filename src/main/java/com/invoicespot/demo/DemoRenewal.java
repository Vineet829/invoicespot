package com.invoicespot.demo;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Renews the demo account's sample data every day, when the demo is seeded and
// set to reset, so its year never ages and what visitors added doesn't pile
// up. It runs at the daily jobs' time and in their zone, and like them only
// while the jobs are on, as scheduling and its locks are switched on with
// them. One instance renews it each day, under a lock of its own.
@Component
@ConditionalOnProperty(prefix = "app.demo", name = {"seed", "reset"}, havingValue = "true")
public class DemoRenewal {

    private final DemoSeedRunner runner;

    public DemoRenewal(DemoSeedRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${app.jobs.cron:0 0 7 * * *}", zone = "${app.jobs.zone:UTC}")
    @SchedulerLock(name = "demo-renewal", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void run() {
        runner.renew();
    }
}
