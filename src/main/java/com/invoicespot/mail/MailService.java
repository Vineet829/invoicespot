package com.invoicespot.mail;

import com.invoicespot.config.AppProperties;
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

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
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
        } catch (MailException exception) {
            LOGGER.error("Failed to send \"{}\" to {}", subject, to, exception);
        }
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
        mailSender.send(message);
    }
}
