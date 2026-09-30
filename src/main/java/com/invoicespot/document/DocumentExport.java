package com.invoicespot.document;

import com.invoicespot.common.CsvFile;
import com.invoicespot.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Documents and payments as CSV files, with dates on the owner's calendar
// and statuses in the words the app uses.
@Service
public class DocumentExport {

    private static final Map<DocumentStatus, String> STATUS = Map.of(
            DocumentStatus.PAID, "Paid",
            DocumentStatus.NOT_FULLY_PAID, "Partially paid",
            DocumentStatus.NOT_PAID, "Unpaid");

    private final DocumentRepository documentRepository;
    private final SettingsService settingsService;
    private final JdbcClient jdbc;
    private final Clock clock;

    public DocumentExport(
            DocumentRepository documentRepository, SettingsService settingsService, JdbcClient jdbc, Clock clock) {
        this.documentRepository = documentRepository;
        this.settingsService = settingsService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // Every document the list's filters match, in the list's order. A
    // quotation is an offer, so nothing is paid or owed on it.
    @Transactional(readOnly = true)
    public CsvFile documents(Long ownerPkid, DocumentSearch search) {
        ZoneId zone = zoneOf(ownerPkid);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        List<Document> documents = documentRepository.findAll(
                DocumentSpecifications.matching(ownerPkid, search, startOfToday, zone), search.sort());
        return CsvFile.of(
                "invoice-spot-documents-" + today + ".csv",
                List.of("Number", "Type", "Status", "Client", "Client email", "Currency", "Subtotal", "Tax", "Total",
                        "Paid", "Owed", "Issued", "Due", "Overdue"),
                documents.stream()
                        .map(document -> {
                            CustomerSnapshot client = document.getCustomerSnapshot();
                            boolean offer = document.getDocumentType() == DocumentType.QUOTATION;
                            return Arrays.<Object>asList(
                                    document.getDocumentNumber(),
                                    document.getDocumentType().getValue(),
                                    STATUS.get(document.getStatus()),
                                    client == null ? null : client.getName(),
                                    client == null ? null : client.getEmail(),
                                    document.getCurrency(),
                                    document.getSubTotal(),
                                    document.getSalesTax(),
                                    document.getTotal(),
                                    offer ? null : document.getTotalAmountReceived(),
                                    offer ? null : document.getTotal().subtract(document.getTotalAmountReceived()),
                                    LocalDate.ofInstant(document.getCreatedAt(), zone),
                                    document.getDueDate() == null
                                            ? null
                                            : LocalDate.ofInstant(document.getDueDate(), zone),
                                    DocumentTotals.isOverdue(document, startOfToday) ? "Yes" : "No");
                        })
                        .toList());
    }

    // Every payment recorded on the owner's documents, newest first.
    @Transactional(readOnly = true)
    public CsvFile payments(Long ownerPkid) {
        return CsvFile.of(
                "invoice-spot-payments-" + LocalDate.now(clock.withZone(zoneOf(ownerPkid))) + ".csv",
                List.of("Date", "Document", "Type", "Client", "Currency", "Amount", "Method", "Paid by", "Notes"),
                jdbc.sql("""
                                select p.date_paid, d.document_number, d.document_type, d.customer_name, d.currency,
                                       p.amount_paid, p.payment_method, p.paid_by, p.additional_info
                                from payments p
                                join documents d on d.pkid = p.document_pkid
                                where d.created_by_pkid = :owner
                                order by p.date_paid desc, p.pkid desc
                                """)
                        .param("owner", ownerPkid)
                        .query((result, rowNumber) -> Arrays.<Object>asList(
                                result.getObject("date_paid", LocalDate.class),
                                result.getString("document_number"),
                                result.getString("document_type"),
                                result.getString("customer_name"),
                                result.getString("currency"),
                                result.getBigDecimal("amount_paid"),
                                result.getString("payment_method"),
                                result.getString("paid_by"),
                                result.getString("additional_info")))
                        .list());
    }

    private ZoneId zoneOf(Long ownerPkid) {
        return ZoneId.of(settingsService.forUser(ownerPkid).getTimeZone());
    }
}
