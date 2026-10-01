package com.invoicespot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invoicespot.config.AppProperties;
import com.invoicespot.metrics.AppMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class MailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MailService mailService = new MailService(
            mailSender,
            new AppMetrics(registry),
            new AppProperties(
                    null,
                    null,
                    new AppProperties.Site(
                            "Invoice Spot", "http://localhost:3000", "http://localhost:8080", "from@invoicespot.local"),
                    null,
                    null));

    @Test
    @DisplayName("each email is counted by what it is, as sent or as failed when the mail server refuses it")
    void counted() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        doThrow(new MailSendException("The mail server is down"))
                .doNothing()
                .when(mailSender)
                .send(any(MimeMessage.class));

        mailService.send("ada@example.test", "Verify your email", "Hello");
        assertThatThrownBy(() -> mailService.sendDocument(
                        "client@example.test", null, "Invoice INV-1", "Attached", "INV-1.pdf", new byte[] {1}))
                .isInstanceOf(MailSendException.class);
        mailService.sendFromBusiness("client@example.test", "owner@example.test", "Your portal", "Here it is");

        assertThat(emails("account", "sent")).isEqualTo(1);
        assertThat(emails("document", "failed")).isEqualTo(1);
        assertThat(emails("business", "sent")).isEqualTo(1);
        assertThat(registry.get("invoicespot.emails").counters()).hasSize(3);
    }

    private double emails(String kind, String outcome) {
        return registry.get("invoicespot.emails").tag("kind", kind).tag("outcome", outcome).counter().count();
    }
}
