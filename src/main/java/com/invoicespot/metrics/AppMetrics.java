package com.invoicespot.metrics;

import com.invoicespot.document.DocumentType;
import com.invoicespot.ratelimit.RateLimit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.function.IntSupplier;
import org.springframework.stereotype.Component;

// The app's own counts, scraped by Prometheus beside Spring's. A tag only
// ever takes a few fixed values, never an email, id or address, so each
// metric stays a handful of series.
@Component
public class AppMetrics {

    // How someone signed in, besides a provider's id such as google: with a
    // password alone, or with the code asked for after a password or a
    // provider.
    public static final String PASSWORD = "password";
    public static final String TWO_FACTOR = "two_factor";

    private final MeterRegistry registry;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void signedIn(String method) {
        registry.counter("invoicespot.signins", "method", method, "outcome", "success").increment();
    }

    public void signInFailed(String method) {
        registry.counter("invoicespot.signins", "method", method, "outcome", "failure").increment();
    }

    public void emailSent(String kind) {
        registry.counter("invoicespot.emails", "kind", kind, "outcome", "sent").increment();
    }

    public void emailFailed(String kind) {
        registry.counter("invoicespot.emails", "kind", kind, "outcome", "failed").increment();
    }

    public void documentIssued(DocumentType type) {
        registry.counter("invoicespot.documents.issued", "type", tag(type)).increment();
    }

    // Times a step of the daily jobs, as a failure when it throws.
    public int timeJob(String step, IntSupplier job) {
        Timer.Sample started = Timer.start(registry);
        String outcome = "failure";
        try {
            int done = job.getAsInt();
            outcome = "success";
            return done;
        } finally {
            started.stop(registry.timer("invoicespot.jobs", "step", step, "outcome", outcome));
        }
    }

    public void rateLimited(RateLimit limit) {
        registry.counter("invoicespot.ratelimit.rejections", "limit", tag(limit)).increment();
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
