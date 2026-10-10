package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.metrics.AppMetrics;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Issuing a draft: it is dated, takes the next number, and keeps the client,
// the business and the name of its tax as they are at that moment. A credit
// note is first checked against the document it credits.
@Component
public class DocumentIssuer {

    private final SettingsService settingsService;
    private final DocumentNumbering documentNumbering;
    private final GstSupply gstSupply;
    private final DocumentActivity activity;
    private final AppMetrics metrics;
    private final Clock clock;

    public DocumentIssuer(
            SettingsService settingsService,
            DocumentNumbering documentNumbering,
            GstSupply gstSupply,
            DocumentActivity activity,
            AppMetrics metrics,
            Clock clock) {
        this.settingsService = settingsService;
        this.documentNumbering = documentNumbering;
        this.gstSupply = gstSupply;
        this.activity = activity;
        this.metrics = metrics;
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
        // The client as the draft was saved, before it is refreshed: it tells
        // a chosen place of supply from one worked out from the client.
        CustomerSnapshot saved = document.getCustomerSnapshot();
        // A credit note keeps the client as its invoice named them.
        Customer customer = document.getCustomer();
        if (customer != null && document.getDocumentType() != DocumentType.CREDIT_NOTE) {
            document.setCustomerSnapshot(CustomerSnapshot.of(customer));
        }
        document.setSeller(SellerSnapshot.of(document.getBusiness()));
        document.setTaxLabel(settings.getTaxLabel());
        if (document.getDocumentType() == DocumentType.INVOICE && document.getDueDate() == null) {
            document.setDueDate(Document.dueAt(issueDate.plusDays(settings.getPaymentTermsDays()), zone));
        }
        requireDueFromIssue(document, zone);
        // A credit note keeps the supply of what it credits.
        boolean taxDocument = document.getDocumentType() != DocumentType.QUOTATION;
        if (document.getDocumentType() != DocumentType.CREDIT_NOTE) {
            gstSupply.placeAtIssue(document, saved);
        }
        DocumentTotals.apply(document);
        if (document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            CreditNoteService.requireRoom(document, issueDate);
        }
        if (taxDocument) {
            GstSupply.requireIssuable(document);
        }
        document.setDocumentNumber(documentNumbering.next(businessPkid, document.getDocumentType()));
        if (taxDocument) {
            GstSupply.requireNumber(document);
        }
        document.setIssuedAt(Instant.now(clock));
        activity.record(document, DocumentEventType.ISSUED, document.getDocumentNumber(), actor);
        // Counted once the issue commits, so one rolled back isn't.
        DocumentType type = document.getDocumentType();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metrics.documentIssued(type);
            }
        });
    }

    // An invoice falls due, and a quotation stays valid, no earlier than the
    // day it is dated. A draft not dated yet is checked as it is issued.
    static void requireDueFromIssue(Document document, ZoneId zone) {
        LocalDate due = document.dueDay(zone);
        if (due != null && document.getIssueDate() != null && due.isBefore(document.getIssueDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    document.getDocumentType() == DocumentType.QUOTATION
                            ? "A quotation can't expire before its issue date"
                            : "An invoice can't be due before its issue date");
        }
    }
}
