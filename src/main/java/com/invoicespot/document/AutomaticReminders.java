package com.invoicespot.document;

import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.BusinessSettingsRepository;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Sends the automatic payment reminders due on a given day. Each reminder is
// sent on its own, so one that fails is logged and the rest still go out.
@Service
public class AutomaticReminders {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticReminders.class);

    private record Due(Long ownerPkid, String documentId) {}

    private final BusinessSettingsRepository settingsRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentMailService documentMailService;
    private final DemoAccountGuard demoAccountGuard;
    private final TransactionTemplate readOnly;

    public AutomaticReminders(
            BusinessSettingsRepository settingsRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            DocumentMailService documentMailService,
            DemoAccountGuard demoAccountGuard,
            PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.documentMailService = documentMailService;
        this.demoAccountGuard = demoAccountGuard;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    public int send(Instant now) {
        List<Due> due = readOnly.execute(status -> due(now));
        int sent = 0;
        for (Due reminder : due) {
            try {
                User owner = userRepository.findById(reminder.ownerPkid()).orElseThrow();
                documentMailService.remindAutomatically(owner, reminder.documentId());
                sent++;
            } catch (Exception exception) {
                LOGGER.warn("Automatic reminder for document {} failed", reminder.documentId(), exception);
            }
        }
        return sent;
    }

    private List<Due> due(Instant now) {
        List<Due> due = new ArrayList<>();
        for (BusinessSettings settings : settingsRepository.findByRemindersEnabledTrue()) {
            User owner = settings.getUser();
            if (demoAccountGuard.isDemo(owner)) {
                continue;
            }
            ZoneId zone = ZoneId.of(settings.getTimeZone());
            LocalDate today = LocalDate.ofInstant(now, zone);
            for (Document invoice : documentRepository.findRemindable(
                    owner.getPkid(), DocumentType.INVOICE, DocumentStatus.PAID, settings.getReminderMax())) {
                CustomerSnapshot client = invoice.getCustomerSnapshot();
                boolean reachable = client != null && client.getEmail() != null && !client.getEmail().isBlank();
                if (reachable && ReminderPlanner.isDue(invoice, settings, today, zone)) {
                    due.add(new Due(owner.getPkid(), invoice.getId().toString()));
                }
            }
        }
        return due;
    }
}
