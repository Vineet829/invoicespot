package com.invoicespot.document;

import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Tells owners, once per invoice, when an unpaid invoice becomes overdue by
// their own calendar. Each invoice is alerted in a transaction of its own,
// and locked only while it is, so one that fails is logged and the rest
// still go, and no business waits on another's.
@Service
public class OverdueAlerts {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverdueAlerts.class);

    private final DocumentRepository documentRepository;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final TransactionTemplate transaction;

    public OverdueAlerts(
            DocumentRepository documentRepository,
            SettingsService settingsService,
            NotificationService notificationService,
            PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public int raise(Instant now) {
        List<Long> overdue = documentRepository.findUnalertedOverdue(DocumentType.INVOICE, DocumentStatus.PAID, now);
        int raised = 0;
        for (Long pkid : overdue) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> alert(pkid, now)))) {
                    raised++;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Overdue alert for document {} failed", pkid, exception);
            }
        }
        return raised;
    }

    // False when it no longer needs one, as when another run alerted it
    // after the list was made, or it isn't overdue yet where its business is.
    private boolean alert(Long pkid, Instant now) {
        Document invoice = documentRepository
                .findUnalertedOverdueForUpdate(pkid, DocumentType.INVOICE, DocumentStatus.PAID, now)
                .orElse(null);
        if (invoice == null) {
            return false;
        }
        ZoneId zone = ZoneId.of(settingsService.forBusiness(invoice.getBusiness().getPkid()).getTimeZone());
        if (!DocumentTotals.isOverdue(invoice, LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant())) {
            return false;
        }
        invoice.setOverdueAlertedAt(now);
        CustomerSnapshot client = invoice.getCustomerSnapshot();
        BigDecimal owed = DocumentTotals.balanceDue(invoice);
        notificationService.notify(
                invoice,
                NotificationType.INVOICE_OVERDUE,
                "Invoice " + invoice.getDocumentNumber()
                        + (client == null || client.getName() == null ? "" : " for " + client.getName())
                        + " is overdue, with " + DocumentActivity.amount(invoice, owed) + " unpaid");
        return true;
    }
}
