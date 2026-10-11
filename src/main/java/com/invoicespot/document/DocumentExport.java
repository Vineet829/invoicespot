package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.CsvFile;
import com.invoicespot.common.ExchangeRate;
import com.invoicespot.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Documents and payments as CSV files, with dates on the owner's calendar
// and statuses in the words the app uses.
@Service
public class DocumentExport {

    private static final Map<DocumentStatus, String> STATUS = Map.of(
            DocumentStatus.PAID, "Paid",
            DocumentStatus.NOT_FULLY_PAID, "Partially paid",
            DocumentStatus.NOT_PAID, "Unpaid");

    private final DocumentDirectory documentDirectory;
    private final SettingsService settingsService;
    private final JdbcClient jdbc;
    private final Clock clock;

    public DocumentExport(
            DocumentDirectory documentDirectory, SettingsService settingsService, JdbcClient jdbc, Clock clock) {
        this.documentDirectory = documentDirectory;
        this.settingsService = settingsService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // Every document the list's filters match, in the list's order, as they
    // all stood when the file was begun. A quotation is an offer, a draft
    // isn't billed yet, a void document no longer is and a credit note is
    // money taken off another document, so nothing is paid or owed on them.
    // A credit note names what it credits.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CsvFile documents(BusinessAccess access, DocumentSearch search) {
        ZoneId zone = zoneOf(access.businessPkid());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        List<List<Object>> rows = new ArrayList<>();
        documentDirectory.forEach(
                access.businessPkid(),
                search.on(today),
                today,
                zone,
                document -> rows.add(documentRow(document, zone, startOfToday)));
        return CsvFile.of(
                "invoice-spot-documents-" + today + ".csv",
                List.of("Number", "Type", "Status", "Client", "Client email", "Currency", "Subtotal", "Tax", "Total",
                        "Paid", "Owed", "Issued", "Due", "Overdue", "Tax withheld", "Exchange rate",
                        "Exchange rate to", "Credits", "Credited", "Refunded"),
                rows);
    }

    private static List<Object> documentRow(Document document, ZoneId zone, Instant startOfToday) {
        CustomerSnapshot client = document.getCustomerSnapshot();
        boolean creditNote = document.getDocumentType() == DocumentType.CREDIT_NOTE;
        boolean unbilled = document.getDocumentType() == DocumentType.QUOTATION
                || creditNote
                || document.state() != DocumentState.ISSUED;
        return Arrays.<Object>asList(
                document.getDocumentNumber(),
                document.getDocumentType().getValue(),
                switch (document.state()) {
                    case DRAFT -> "Draft";
                    case VOID -> "Void";
                    case ISSUED -> creditNote ? "Issued" : STATUS.get(document.getStatus());
                },
                client == null ? null : client.getName(),
                client == null ? null : client.getEmail(),
                document.getCurrency(),
                document.getSubTotal(),
                document.getSalesTax(),
                document.getTotal(),
                unbilled ? null : document.getTotalAmountReceived(),
                unbilled ? null : DocumentTotals.balanceDue(document),
                document.getIssueDate(),
                document.getDueDate() == null ? null : LocalDate.ofInstant(document.getDueDate(), zone),
                DocumentTotals.isOverdue(document, startOfToday) ? "Yes" : "No",
                unbilled ? null : document.getTotalTds(),
                ExchangeRate.plain(document.getExchangeRate()),
                document.getExchangeBase(),
                document.getCredited() == null ? null : document.getCredited().getDocumentNumber(),
                unbilled ? null : document.getTotalCredited(),
                unbilled ? null : document.getTotalRefunded());
    }

    // The money the payments page counts, newest first: every payment on an
    // issued invoice, every issued receipt at its total on the day it is
    // dated, and every refund on an issued document as a negative amount, so
    // that the file adds up to what the page received. A receipt has no
    // method, payer, note or tax withheld of its own.
    @Transactional(readOnly = true)
    public CsvFile payments(BusinessAccess access) {
        return CsvFile.of(
                "invoice-spot-payments-" + LocalDate.now(clock.withZone(zoneOf(access.businessPkid()))) + ".csv",
                List.of("Date", "Document", "Type", "Client", "Currency", "Amount", "Method", "Paid by", "Notes",
                        "Tax withheld", "Kind"),
                jdbc.sql("""
                                select p.date_paid as day, p.pkid as sequence, d.document_number, d.document_type,
                                       d.customer_name, d.currency, p.amount_paid as amount, p.payment_method,
                                       p.paid_by, p.additional_info as notes, p.tds_amount, 'Payment' as kind
                                from payments p
                                join documents d on d.pkid = p.document_pkid
                                where d.business_pkid = :business and %1$s and d.document_type = 'Invoice'
                                union all
                                select d.issue_date, d.pkid, d.document_number, d.document_type,
                                       d.customer_name, d.currency, d.total, null, null, null, null, 'Receipt'
                                from documents d
                                where d.business_pkid = :business and %1$s and d.document_type = 'Receipt'
                                union all
                                select r.date_refunded, r.pkid, d.document_number, d.document_type,
                                       d.customer_name, d.currency, -r.amount, r.payment_method,
                                       null, r.reference, null, 'Refund'
                                from refunds r
                                join documents d on d.pkid = r.document_pkid
                                where d.business_pkid = :business and %1$s
                                order by day desc, kind desc, sequence desc
                                """.formatted(DocumentLedger.COUNTED))
                        .param("business", access.businessPkid())
                        .query((result, rowNumber) -> Arrays.<Object>asList(
                                result.getObject("day", LocalDate.class),
                                result.getString("document_number"),
                                result.getString("document_type"),
                                result.getString("customer_name"),
                                result.getString("currency"),
                                result.getBigDecimal("amount"),
                                result.getString("payment_method"),
                                result.getString("paid_by"),
                                result.getString("notes"),
                                result.getBigDecimal("tds_amount"),
                                result.getString("kind")))
                        .list());
    }

    private ZoneId zoneOf(Long businessPkid) {
        return ZoneId.of(settingsService.forBusiness(businessPkid).getTimeZone());
    }
}
