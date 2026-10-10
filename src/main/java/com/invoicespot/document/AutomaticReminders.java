package com.invoicespot.document;

import com.invoicespot.business.BusinessService;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.BusinessSettingsRepository;
import com.invoicespot.user.DemoAccountGuard;
import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Sends the automatic payment reminders due on a given day. Each reminder is
// sent on its own, so one that fails is logged and the rest still go out.
// Its invoice is locked and checked again before it goes, and stays locked
// until the reminder is recorded, so two runs at once never both send it.
@Service
public class AutomaticReminders {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticReminders.class);

    private record Due(
            Long businessPkid,
            Long documentPkid,
            String documentId,
            BusinessSettings settings,
            LocalDate today,
            ZoneId zone) {}

    private final BusinessSettingsRepository settingsRepository;
    private final DocumentRepository documentRepository;
    private final BusinessService businessService;
    private final DocumentMailService documentMailService;
    private final DemoAccountGuard demoAccountGuard;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate transaction;

    public AutomaticReminders(
            BusinessSettingsRepository settingsRepository,
            DocumentRepository documentRepository,
            BusinessService businessService,
            DocumentMailService documentMailService,
            DemoAccountGuard demoAccountGuard,
            PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.documentRepository = documentRepository;
        this.businessService = businessService;
        this.documentMailService = documentMailService;
        this.demoAccountGuard = demoAccountGuard;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public int send(Instant now) {
        List<Due> due = readOnly.execute(status -> due(now));
        int sent = 0;
        for (Due reminder : due) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> remind(reminder)))) {
                    sent++;
                }
            } catch (Exception exception) {
                LOGGER.warn("Automatic reminder for document {} failed", reminder.documentId(), exception);
            }
        }
        return sent;
    }

    // False when the invoice no longer needs the reminder, as when another
    // run sent it after the list was made.
    private boolean remind(Due reminder) {
        Document invoice = documentRepository.findForUpdateByPkid(reminder.documentPkid()).orElse(null);
        if (invoice == null
                || !ReminderPlanner.isDue(invoice, reminder.settings(), reminder.today(), reminder.zone())) {
            return false;
        }
        try {
            documentMailService.remindAutomatically(
                    businessService.ownerAccess(reminder.businessPkid()), reminder.documentId());
        } catch (MessagingException exception) {
            throw new MailSendException("The reminder could not be sent", exception);
        }
        return true;
    }

    private List<Due> due(Instant now) {
        List<Due> due = new ArrayList<>();
        for (BusinessSettings settings : settingsRepository.findByRemindersEnabledTrue()) {
            Long businessPkid = settings.getBusiness().getPkid();
            if (demoAccountGuard.isDemo(businessService.owner(businessPkid))) {
                continue;
            }
            ZoneId zone = ZoneId.of(settings.getTimeZone());
            LocalDate today = LocalDate.ofInstant(now, zone);
            for (Document invoice : documentRepository.findRemindable(
                    businessPkid, DocumentType.INVOICE, DocumentStatus.PAID, settings.getReminderMax())) {
                CustomerSnapshot client = invoice.getCustomerSnapshot();
                boolean reachable = client != null && client.getEmail() != null && !client.getEmail().isBlank();
                if (reachable && ReminderPlanner.isDue(invoice, settings, today, zone)) {
                    due.add(new Due(
                            businessPkid, invoice.getPkid(), invoice.getId().toString(), settings, today, zone));
                }
            }
        }
        return due;
    }
}
