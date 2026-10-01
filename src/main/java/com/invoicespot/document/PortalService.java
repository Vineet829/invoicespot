package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.SecretBox;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.config.AppProperties;
import com.invoicespot.customer.Customer;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.document.dto.PortalResponse;
import com.invoicespot.document.dto.PublicDocumentResponse;
import com.invoicespot.document.dto.PublicParty;
import com.invoicespot.document.dto.ReportPaymentRequest;
import com.invoicespot.document.dto.ShareLinkResponse;
import com.invoicespot.document.dto.ShareStatusResponse;
import com.invoicespot.document.dto.WhatsAppShareResponse;
import com.invoicespot.mail.Placeholders;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Each client's portal: one link to all their issued documents, what they owe
// in each currency, and their statement. It shows only what their share links
// would, and from it they answer quotations and report payments the same way.
@Service
public class PortalService {

    private static final int MOST_PER_PAGE = 50;
    private static final String WHATSAPP_MESSAGE =
            "Hello {client}, here are all your documents from {business}: {link}";

    private final PortalLinkRepository linkRepository;
    private final CustomerRepository customerRepository;
    private final DocumentRepository documentRepository;
    private final PublicDocuments publicDocuments;
    private final StatementService statementService;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final SecretBox secretBox;
    private final AppProperties properties;
    private final Clock clock;

    public PortalService(
            PortalLinkRepository linkRepository,
            CustomerRepository customerRepository,
            DocumentRepository documentRepository,
            PublicDocuments publicDocuments,
            StatementService statementService,
            SettingsService settingsService,
            UserRepository userRepository,
            SecretBox secretBox,
            AppProperties properties,
            Clock clock) {
        this.linkRepository = linkRepository;
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.publicDocuments = publicDocuments;
        this.statementService = statementService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.secretBox = secretBox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ShareStatusResponse status(BusinessAccess access, String customerId) {
        return linkRepository.findByCustomerAndRevokedAtIsNull(requireInBusiness(access, customerId))
                .map(link -> new ShareStatusResponse(
                        true,
                        true,
                        link.getCreatedAt(),
                        link.getLastViewedAt(),
                        link.getViewCount(),
                        recovered(link).orElse(null)))
                .orElseGet(() -> new ShareStatusResponse(true, false, null, null, 0, null));
    }

    // A new link replaces the client's current one.
    @Transactional
    public ShareLinkResponse create(BusinessAccess access, String customerId) {
        return newLink(requireInBusiness(access, customerId), access);
    }

    private ShareLinkResponse newLink(Customer customer, BusinessAccess access) {
        Instant now = Instant.now(clock);
        linkRepository.findByCustomerAndRevokedAtIsNull(customer).ifPresent(active -> {
            active.setRevokedAt(now);
            linkRepository.saveAndFlush(active);
        });
        String token = SecretTokens.generate();
        PortalLink link = new PortalLink();
        link.setBusiness(customer.getBusiness());
        link.setCustomer(customer);
        link.setTokenHash(SecretTokens.hash(token));
        if (secretBox.available()) {
            link.setTokenSealed(secretBox.seal(token.getBytes(StandardCharsets.UTF_8)));
        }
        link.setCreatedBy(userRepository.getReferenceById(access.userPkid()));
        link.setCreatedAt(now);
        linkRepository.save(link);
        return new ShareLinkResponse(true, address(token), now, link.getTokenSealed() != null);
    }

    // What an email draft can say about the client's portal link: the link
    // itself when it can be shown again, or, when it can't, the day it was
    // made, as sending will replace it.
    @Transactional(readOnly = true)
    public ShareService.EmailLink peekForEmail(BusinessAccess access, String customerId) {
        Customer customer = requireInBusiness(access, customerId);
        ZoneId zone = ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone());
        return linkRepository.findByCustomerAndRevokedAtIsNull(customer)
                .map(link -> recovered(link)
                        .map(url -> new ShareService.EmailLink(url, null))
                        .orElseGet(() -> new ShareService.EmailLink(
                                null, PdfService.DATE.format(link.getCreatedAt().atZone(zone)))))
                .orElseGet(() -> new ShareService.EmailLink(null, null));
    }

    // The link to send a client: their current one when it can be shown
    // again, or a new one. The client is locked, so two messages at once
    // share one new link.
    @Transactional
    public ShareService.SentLink linkToSend(BusinessAccess access, String customerId) {
        Customer customer = requireInBusiness(access, customerId, true);
        Optional<PortalLink> active = linkRepository.findByCustomerAndRevokedAtIsNull(customer);
        Optional<String> current = active.flatMap(this::recovered);
        return current.isPresent()
                ? new ShareService.SentLink(current.get(), false)
                : new ShareService.SentLink(newLink(customer, access).url(), active.isPresent());
    }

    // A WhatsApp message with the client's portal link, to their number when
    // there is one WhatsApp can use.
    @Transactional
    public WhatsAppShareResponse whatsApp(BusinessAccess access, String customerId) {
        ShareService.SentLink link = linkToSend(access, customerId);
        Customer customer = requireInBusiness(access, customerId);
        Business business = customer.getBusiness();
        String text = Placeholders.fill(WHATSAPP_MESSAGE, Map.of(
                "client", customer.getName() == null || customer.getName().isBlank() ? "there" : customer.getName(),
                "business", PdfService.senderName(business),
                "link", link.url()));
        return new WhatsAppShareResponse(
                true,
                WhatsAppLink.of(
                        customer.getPhoneNumber(),
                        WhatsAppLink.inIndia(business, settingsService.forBusiness(business.getPkid()).getTaxRegime()),
                        text),
                link.replaced());
    }

    // The client a portal email goes to.
    @Transactional(readOnly = true)
    public Customer client(BusinessAccess access, String customerId) {
        return requireInBusiness(access, customerId);
    }

    @Transactional
    public void revoke(BusinessAccess access, String customerId) {
        PortalLink link = linkRepository.findByCustomerAndRevokedAtIsNull(requireInBusiness(access, customerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This client has no portal link"));
        link.setRevokedAt(Instant.now(clock));
    }

    // The portal's front page; each visit is counted.
    @Transactional
    public PortalResponse view(String token, int page, int size) {
        PortalLink link = requireLink(token);
        link.setLastViewedAt(Instant.now(clock));
        link.setViewCount(link.getViewCount() + 1);
        Customer customer = link.getCustomer();
        Business business = customer.getBusiness();
        ZoneId zone = ZoneId.of(settingsService.forBusiness(business.getPkid()).getTimeZone());
        Instant startOfToday = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        int number = Math.max(page, 1);
        Page<Document> documents = documentRepository.findForPortal(
                business.getPkid(),
                customer.getPkid(),
                PageRequest.of(number - 1, Math.min(Math.max(size, 1), MOST_PER_PAGE)));
        return new PortalResponse(
                true,
                new PublicParty(PdfService.senderName(business), business.getLogo(), PdfService.senderLines(business)),
                customer.getName(),
                statementService.accounts(customer).stream()
                        .map(account -> new PortalResponse.Balance(
                                account.currency(),
                                account.charged(),
                                account.paid(),
                                account.credited(),
                                account.refunded(),
                                account.due(),
                                account.overdue()))
                        .toList(),
                documents.getContent().stream().map(document -> entry(document, startOfToday)).toList(),
                documents.getTotalElements(),
                number,
                documents.getTotalPages());
    }

    private static PortalResponse.Entry entry(Document document, Instant startOfToday) {
        boolean invoice = document.getDocumentType() == DocumentType.INVOICE;
        return new PortalResponse.Entry(
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.state(),
                document.getStatus(),
                DocumentTotals.isOverdue(document, startOfToday),
                document.isExpired(startOfToday),
                document.getAcceptedAt(),
                document.getDeclinedAt(),
                document.getIssueDate(),
                document.getDueDate(),
                document.getCurrency(),
                document.getTotal(),
                invoice ? (document.isVoid() ? BigDecimal.ZERO : DocumentTotals.balanceDue(document)) : null);
    }

    @Transactional(readOnly = true)
    public PublicDocumentResponse document(String token, String number) {
        return publicDocuments.view(requireDocument(token, number));
    }

    @Transactional(readOnly = true)
    public DocumentPdf pdf(String token, String number) {
        return publicDocuments.pdf(requireDocument(token, number));
    }

    @Transactional
    public PublicDocumentResponse accept(String token, String number) {
        return publicDocuments.accept(requireDocument(token, number));
    }

    @Transactional
    public PublicDocumentResponse decline(String token, String number, String reason) {
        return publicDocuments.decline(requireDocument(token, number), reason);
    }

    @Transactional
    public PublicDocumentResponse reportPayment(String token, String number, ReportPaymentRequest request) {
        return publicDocuments.reportPayment(requireDocument(token, number), request);
    }

    @Transactional(readOnly = true)
    public DocumentPdf statement(String token) {
        return statementService.render(requireLink(token).getCustomer());
    }

    private Document requireDocument(String token, String number) {
        Customer customer = requireLink(token).getCustomer();
        return documentRepository.findInPortal(customer.getBusiness().getPkid(), customer.getPkid(), number)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "There is no such document in this portal"));
    }

    private PortalLink requireLink(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw linkGone();
        }
        return linkRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.hash(token))
                .orElseThrow(PortalService::linkGone);
    }

    private static ApiException linkGone() {
        return new ApiException(HttpStatus.NOT_FOUND, "This link doesn't work anymore");
    }

    private Customer requireInBusiness(BusinessAccess access, String customerId) {
        return requireInBusiness(access, customerId, false);
    }

    private Customer requireInBusiness(BusinessAccess access, String customerId, boolean lock) {
        UUID id;
        try {
            id = UUID.fromString(customerId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return (lock ? customerRepository.findForUpdateByExternalId(id) : customerRepository.findByExternalId(id))
                .filter(customer -> customer.getBusiness().getPkid().equals(access.businessPkid()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    // A link's address again, when its token was kept sealed and the key in
    // use opens it.
    private Optional<String> recovered(PortalLink link) {
        if (link.getTokenSealed() == null || !secretBox.available()) {
            return Optional.empty();
        }
        try {
            return Optional.of(address(new String(secretBox.open(link.getTokenSealed()), StandardCharsets.UTF_8)));
        } catch (IllegalStateException unreadable) {
            return Optional.empty();
        }
    }

    private String address(String token) {
        return properties.site().clientUrl() + "/c/" + token;
    }
}
