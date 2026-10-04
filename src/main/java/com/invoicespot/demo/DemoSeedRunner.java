package com.invoicespot.demo;

import com.invoicespot.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "seed", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DemoDataSeeder seeder;
    private final DemoProperties properties;

    public DemoSeedRunner(DemoDataSeeder seeder, DemoProperties properties) {
        this.seeder = seeder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            DemoDataSeeder.Result result = seeder.seed(properties.email());
            if (result.seeded()) {
                LOGGER.info(
                        "Added sample data to the demo account: {} clients, {} items, {} documents, {} payments",
                        result.clients(), result.items(), result.documents(), result.payments());
            } else {
                LOGGER.info("No sample data added: {}", result.reason());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Adding sample data to the demo account failed", exception);
        }
        // Sample expenses came later, so they are added on their own.
        try {
            DemoDataSeeder.ExpenseResult result = seeder.seedExpenses(properties.email());
            if (result.seeded()) {
                LOGGER.info("Added sample expenses to the demo account: {} expenses", result.expenses());
            } else {
                LOGGER.info("No sample expenses added: {}", result.reason());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Adding sample expenses to the demo account failed", exception);
        }
    }
}
