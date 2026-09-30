package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.config.AppProperties;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.document.dto.PublicDocumentResponse;
import com.invoicespot.document.dto.PublicParty;
import com.invoicespot.document.dto.ShareLinkResponse;
import com.invoicespot.document.dto.ShareStatusResponse;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {

    private static final Duration VIEW_GAP = Duration.ofHours(1);

    private final DocumentRepository documentRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final DocumentActivity activity;
    private final PdfService pdfService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final Clock clock;

    public ShareService(
            DocumentRepository documentRepository,
            ShareLinkRepository shareLinkRepository,
            DocumentActivity activity,
            PdfService pdfService,
            SettingsService settingsService,
            NotificationService notificationService,
            AppProperties properties,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.activity = activity;
        this.pdfService = pdfService;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    // Only the token's hash is kept, so the link is shown this once. A new
    // link replaces the document's current one.
    @Transactional
    public ShareLinkResponse create(BusinessAccess access, String id) {
        Document document = requireInBusiness(access, id);
        Instant now = Instant.now(clock);
        shareLinkRepository.findByDocumentAndRevokedAtIsNull(document).ifPresent(active -> {
            active.setRevokedAt(now);
            shareLinkRepository.saveAndFlush(active);
        });
        String token = SecretTokens.generate();
        ShareLink link = new ShareLink();
        link.setDocument(document);
        link.setTokenHash(SecretTokens.hash(token));
        link.setCreatedAt(now);
        shareLinkRepository.save(link);
        activity.record(document, DocumentEventType.SHARED, null, access.user());
        return new ShareLinkResponse(true, properties.site().clientUrl() + "/p/" + token, now);
    }

    @Transactional(readOnly = true)
    public ShareStatusResponse status(BusinessAccess access, String id) {
        return shareLinkRepository.findByDocumentAndRevokedAtIsNull(requireInBusiness(access, id))
                .map(link -> new ShareStatusResponse(
                        true, true, link.getCreatedAt(), link.getLastViewedAt(), link.getViewCount()))
                .orElseGet(() -> new ShareStatusResponse(true, false, null, null, 0));
    }

    @Transactional
    public void revoke(BusinessAccess access, String id) {
        Document document = requireInBusiness(access, id);
        ShareLink link = shareLinkRepository.findByDocumentAndRevokedAtIsNull(document)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This document has no share link"));
        link.setRevokedAt(Instant.now(clock));
        activity.record(document, DocumentEventType.SHARE_REVOKED, null, access.user());
    }

    // A view is counted every time, but the timeline and the business hear of it
    // at most once an hour, so a client reloading the page doesn't flood them.
    @Transactional
    public PublicDocumentResponse view(String token) {
        ShareLink link = requireLink(token);
        Instant now = Instant.now(clock);
        if (link.getLastViewedAt() == null || link.getLastViewedAt().isBefore(now.minus(VIEW_GAP))) {
            Document document = link.getDocument();
            activity.record(document, DocumentEventType.VIEWED, null, null);
            notificationService.notify(
                    document,
                    NotificationType.DOCUMENT_VIEWED,
                    clientName(document) + " viewed " + title(document));
        }
        link.setLastViewedAt(now);
        link.setViewCount(link.getViewCount() + 1);
        return publicView(link.getDocument());
    }

    @Transactional(readOnly = true)
    public DocumentPdf pdf(String token) {
        Document document = requireLink(token).getDocument();
        Business seller = document.getBusiness();
        return new DocumentPdf(
                DocumentService.pdfFileName(document),
                pdfService.render(document, seller, settingsService.forBusiness(seller.getPkid())),
                null);
    }

    @Transactional
    public PublicDocumentResponse accept(String token) {
        Document document = requireLink(token).getDocument();
        if (document.getDocumentType() != DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a quotation can be accepted");
        }
        if (document.getAcceptedAt() == null) {
            document.setAcceptedAt(Instant.now(clock));
            activity.record(document, DocumentEventType.ACCEPTED, null, null);
            notificationService.notify(
                    document,
                    NotificationType.QUOTATION_ACCEPTED,
                    clientName(document) + " accepted " + title(document));
        }
        return publicView(document);
    }

    private PublicDocumentResponse publicView(Document document) {
        Business seller = document.getBusiness();
        BusinessSettings settings = settingsService.forBusiness(seller.getPkid());
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        Instant startOfToday = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        CustomerSnapshot client = Objects.requireNonNullElseGet(document.getCustomerSnapshot(), CustomerSnapshot::new);
        BigDecimal total = orZero(document.getTotal());
        BigDecimal received = orZero(document.getTotalAmountReceived());
        boolean quotation = document.getDocumentType() == DocumentType.QUOTATION;
        return new PublicDocumentResponse(
                true,
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getStatus(),
                DocumentTotals.isOverdue(document, startOfToday),
                document.getCreatedAt(),
                document.getDueDate(),
                document.getCurrency(),
                new PublicParty(PdfService.senderName(seller), seller.getLogo(), PdfService.senderLines(seller)),
                new PublicParty(
                        client.getName(),
                        null,
                        lines(client.getEmail(), client.getPhoneNumber(), client.getAddress(), client.getCity(),
                                client.getCountry())),
                document.getBillingItems().stream()
                        .map(item -> new BillingItemResponse(
                                item.getItemName(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                orZero(item.getDiscount()).stripTrailingZeros().toPlainString(),
                                DocumentTotals.lineTotal(item)))
                        .toList(),
                document.getSubTotal(),
                settings.getTaxLabel(),
                orZero(document.getRates()).stripTrailingZeros().toPlainString(),
                document.getSalesTax(),
                total,
                received,
                quotation ? total : total.subtract(received).max(BigDecimal.ZERO),
                document.getAdditionalInfo(),
                document.getTermsConditions(),
                document.getAcceptedAt(),
                quotation && document.getAcceptedAt() == null);
    }

    private static String clientName(Document document) {
        CustomerSnapshot client = document.getCustomerSnapshot();
        return client == null || client.getName() == null || client.getName().isBlank()
                ? "Your client"
                : client.getName();
    }

    private static String title(Document document) {
        return document.getDocumentType().getValue() + " " + document.getDocumentNumber();
    }

    private Document requireInBusiness(BusinessAccess access, String id) {
        UUID externalId;
        try {
            externalId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That document does not exist");
        }
        Document document = documentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist"));
        if (!document.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only share your own documents");
        }
        return document;
    }

    private ShareLink requireLink(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw linkGone();
        }
        return shareLinkRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.hash(token))
                .orElseThrow(ShareService::linkGone);
    }

    private static ApiException linkGone() {
        return new ApiException(HttpStatus.NOT_FOUND, "This link doesn't work anymore");
    }

    private static List<String> lines(String... values) {
        return Stream.of(values).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
