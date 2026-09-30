package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Issuing a draft: it is dated, takes the next number, and keeps the client,
// the business and the name of its tax as they are at that moment.
@Component
public class DocumentIssuer {

    private final SettingsService settingsService;
    private final DocumentNumbering documentNumbering;
    private final DocumentActivity activity;
    private final Clock clock;

    public DocumentIssuer(
            SettingsService settingsService,
            DocumentNumbering documentNumbering,
            DocumentActivity activity,
            Clock clock) {
        this.settingsService = settingsService;
        this.documentNumbering = documentNumbering;
        this.activity = activity;
        this.clock = clock;
    }

    // The day asked for, else the day planned on the draft, else today in
    // the business's time zone. An invoice without a due date is due as the
    // business's payment terms say, counted from that day.
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(Document document, LocalDate requested, User actor) {
        if (!document.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, "This document is already issued");
        }
        if (document.getCustomerSnapshot() == null || document.getCustomerSnapshot().getName() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Choose a client before issuing this draft");
        }
        if (document.getBillingItems().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Add a product or service before issuing this draft");
        }
        Long businessPkid = document.getBusiness().getPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate issueDate = requested != null
                ? requested
                : document.getIssueDate() != null ? document.getIssueDate() : today;
        if (issueDate.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A document can't be dated after today");
        }
        document.setIssueDate(issueDate);
        Customer customer = document.getCustomer();
        if (customer != null) {
            document.setCustomerSnapshot(CustomerSnapshot.of(customer));
        }
        document.setSeller(SellerSnapshot.of(document.getBusiness()));
        document.setTaxLabel(settings.getTaxLabel());
        if (document.getDocumentType() == DocumentType.INVOICE && document.getDueDate() == null) {
            document.setDueDate(issueDate.plusDays(settings.getPaymentTermsDays())
                    .atTime(LocalTime.NOON)
                    .atZone(zone)
                    .toInstant());
        }
        DocumentTotals.apply(document);
        document.setDocumentNumber(documentNumbering.next(businessPkid, document.getDocumentType()));
        document.setIssuedAt(Instant.now(clock));
        activity.record(document, DocumentEventType.ISSUED, document.getDocumentNumber(), actor);
    }
}
