package com.invoicespot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.config.AppProperties;
import com.invoicespot.metrics.AppMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    @DisplayName("a line break in a name a subject carries becomes a space: one subject line, and no header of its own")
    void oneLineSubjects() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));

        mailService.sendFromBusiness(
                "client@example.test", "owner@example.test", "Your documents from Acme\r\nBcc: eve@example.test", "Hi");
        mailService.sendDocument(
                "client@example.test",
                null,
                "Invoice INV-1 from Acme\nBcc: eve@example.test",
                "Attached",
                "INV-1.pdf",
                new byte[] {1});
        mailService.send("ada@example.test", "You now own Acme\r\nBcc: eve@example.test", "Hello");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).getHeader("Subject"))
                .containsExactly("Your documents from Acme Bcc: eve@example.test");
        assertThat(sent.getAllValues().get(1).getHeader("Subject"))
                .containsExactly("Invoice INV-1 from Acme Bcc: eve@example.test");
        for (MimeMessage message : sent.getAllValues()) {
            assertThat(message.getHeader("Bcc")).isNull();
            assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("client@example.test");
        }
        ArgumentCaptor<SimpleMailMessage> account = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(account.capture());
        assertThat(account.getValue().getSubject()).isEqualTo("You now own Acme Bcc: eve@example.test");
    }

    @Test
    @DisplayName("an account email that fails is logged on one line, whatever names its subject carries")
    void failureLoggedOnOneLine(CapturedOutput output) {
        doThrow(new MailSendException("The mail server is down")).when(mailSender).send(any(SimpleMailMessage.class));

        mailService.send("ada@example.test", "You now own Acme\nERROR c.i.auth.SignIn : forged", "Hello");

        assertThat(output.getOut() + output.getErr())
                .contains("Failed to send \"You now own Acme ERROR c.i.auth.SignIn : forged\" to ada@example.test")
                .doesNotContain("Acme\nERROR");
    }

    private double emails(String kind, String outcome) {
        return registry.get("invoicespot.emails").tag("kind", kind).tag("outcome", outcome).counter().count();
    }
}
