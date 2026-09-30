package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.settings.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
        Customer customer = requireInBusiness(access, customerId);
        ZoneId zone = ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Statements.Account> accounts = Statements.accounts(
                documentRepository.findForStatement(customer.getPkid(), DocumentType.INVOICE),
                today.atStartOfDay(zone).toInstant());
        return new DocumentPdf(
                fileName(customer, today),
                pdfService.renderStatement(customer.getBusiness(), customer, today, accounts),
                envelope(customer));
    }

    private static DocumentEnvelope envelope(Customer customer) {
        return new DocumentEnvelope(TITLE, customer.getName(), customer.getEmail());
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
