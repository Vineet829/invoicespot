package com.invoicespot.mail;

import com.invoicespot.config.AppProperties;
import com.invoicespot.metrics.AppMetrics;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);

    // What each email is, for counting: to someone about their account, or
    // from a business, alone or with a document attached.
    private static final String ACCOUNT = "account";
    private static final String BUSINESS = "business";
    private static final String DOCUMENT = "document";

    private final JavaMailSender mailSender;
    private final AppMetrics metrics;
    private final String from;

    public MailService(JavaMailSender mailSender, AppMetrics metrics, AppProperties properties) {
        this.mailSender = mailSender;
        this.metrics = metrics;
        this.from = properties.site().defaultFromEmail();
    }

    @Async
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            metrics.emailSent(ACCOUNT);
        } catch (MailException exception) {
            metrics.emailFailed(ACCOUNT);
            LOGGER.error("Failed to send \"{}\" to {}", subject, to, exception);
        }
    }

    // An email from a business with nothing attached, sent at once, so a
    // failure reaches the person sending.
    public void sendFromBusiness(String to, String replyTo, String subject, String body) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);
        helper.setFrom(from);
        helper.setTo(to);
        if (replyTo != null && !replyTo.isBlank()) {
            helper.setReplyTo(replyTo);
        }
        helper.setSubject(subject);
        helper.setText(body);
        deliver(BUSINESS, message);
    }

    public void sendDocument(
            String to,
            String replyTo,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(from);
        helper.setTo(to);
        if (replyTo != null && !replyTo.isBlank()) {
            helper.setReplyTo(replyTo);
        }
        helper.setSubject(subject);
        helper.setText(body);
        helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
        deliver(DOCUMENT, message);
    }

    // Sends the email, counted as sent, or as failed when the server refuses it.
    private void deliver(String kind, MimeMessage message) {
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            metrics.emailFailed(kind);
            throw exception;
        }
        metrics.emailSent(kind);
    }
}
