package com.invoicespot.document;

import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Tells owners, once per invoice, when an unpaid invoice becomes overdue by
// their own calendar.
@Service
public class OverdueAlerts {

    private final DocumentRepository documentRepository;
    private final SettingsService settingsService;
    private final NotificationService notificationService;

    public OverdueAlerts(
            DocumentRepository documentRepository,
            SettingsService settingsService,
            NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    @Transactional
    public int raise(Instant now) {
        int raised = 0;
        for (Document invoice : documentRepository.findUnalertedOverdue(
                DocumentType.INVOICE, DocumentStatus.PAID, now)) {
            ZoneId zone = ZoneId.of(settingsService.forBusiness(invoice.getBusiness().getPkid()).getTimeZone());
            if (!DocumentTotals.isOverdue(invoice, LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant())) {
                continue;
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
            raised++;
        }
        return raised;
    }
}
