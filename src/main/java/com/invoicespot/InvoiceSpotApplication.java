package com.invoicespot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class InvoiceSpotApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceSpotApplication.class, args);
    }
}
