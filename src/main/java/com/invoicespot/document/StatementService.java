package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.settings.EmailTemplateKind;
import com.invoicespot.settings.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {

    static final String TITLE = "Statement of account";

    private final CustomerRepository customerRepository;
    private final DocumentRepository documentRepository;
    private final SettingsService settingsService;
    private final PdfService pdfService;
    private final Clock clock;

    public StatementService(
            CustomerRepository customerRepository,
            DocumentRepository documentRepository,
            SettingsService settingsService,
            PdfService pdfService,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.settingsService = settingsService;
        this.pdfService = pdfService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope envelope(BusinessAccess access, String customerId) {
        return envelope(requireInBusiness(access, customerId));
    }

    @Transactional(readOnly = true)
    public DocumentPdf render(BusinessAccess access, String customerId) {
        return render(requireInBusiness(access, customerId));
    }

    // A client's statement as of today in the business's time zone.
    DocumentPdf render(Customer customer) {
        ZoneId zone = ZoneId.of(settingsService.forBusiness(customer.getBusiness().getPkid()).getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Statements.Account> accounts = Statements.accounts(
                documentRepository.findForStatement(customer.getPkid(), DocumentType.INVOICE),
                today.atStartOfDay(zone).toInstant());
        return new DocumentPdf(
                fileName(customer, today),
                pdfService.renderStatement(customer.getBusiness(), customer, today, accounts),
                envelope(customer));
    }

    // The statement of the client invoiced last, for previewing what a
    // statement's email would say.
    // A client's balances in each currency, as their statement shows them.
    List<Statements.Account> accounts(Customer customer) {
        ZoneId zone = ZoneId.of(settingsService.forBusiness(customer.getBusiness().getPkid()).getTimeZone());
        return Statements.accounts(
                documentRepository.findForStatement(customer.getPkid(), DocumentType.INVOICE),
                LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant());
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope latestEnvelope(BusinessAccess access) {
        return documentRepository
                .findLatestInvoicedCustomers(access.businessPkid(), DocumentType.INVOICE, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(StatementService::envelope)
                .orElseThrow(() -> DocumentService.nothingToPreview("client with an invoice"));
    }

    private static DocumentEnvelope envelope(Customer customer) {
        return new DocumentEnvelope(
                EmailTemplateKind.STATEMENT,
                TITLE,
                customer.getName(),
                customer.getEmail(),
                Map.of("document", TITLE),
                null);
    }

    private static String fileName(Customer customer, LocalDate day) {
        String account = customer.getAccountNo() == null || customer.getAccountNo().isBlank()
                ? customer.getName()
                : customer.getAccountNo();
        String safe = account.replaceAll("[^A-Za-z0-9-]+", "-").replaceAll("^-+|-+$", "");
        return "Statement-" + (safe.isEmpty() ? "client" : safe) + "-" + day + ".pdf";
    }

    private Customer requireInBusiness(BusinessAccess access, String customerId) {
        UUID id;
        try {
            id = UUID.fromString(customerId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        Customer customer = customerRepository.findByExternalId(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!customer.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only see statements for your own clients");
        }
        return customer;
    }
}
