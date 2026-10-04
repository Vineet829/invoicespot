package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.ExchangeRate;
import com.invoicespot.common.Percentage;
import com.invoicespot.common.Today;
import com.invoicespot.customer.Customer;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.document.dto.ActivityEntry;
import com.invoicespot.document.dto.ActivityResponse;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentCustomerDto;
import com.invoicespot.document.dto.DocumentListResponse;
import com.invoicespot.document.dto.DocumentResponse;
import com.invoicespot.document.dto.IssueDocumentRequest;
import com.invoicespot.document.dto.RefundRequest;
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.document.dto.UpdatePaymentRequest;
import com.invoicespot.document.dto.VoidDocumentRequest;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.EmailTemplateKind;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.tax.Gstin;
import com.invoicespot.tax.HsnSac;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final String LOCKED = "An issued document keeps its client, lines, tax and issue date."
            + " Void it and duplicate it to change them";

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PdfService pdfService;
    private final SettingsService settingsService;
    private final BusinessRepository businessRepository;
    private final DocumentIssuer documentIssuer;
    private final CreditNoteService creditNotes;
    private final RefundService refunds;
    private final GstSupply gstSupply;
    private final RecurringScheduleRepository scheduleRepository;
    private final PaymentClaimRepository claimRepository;
    private final DocumentActivity activity;
    private final NotificationService notificationService;
    private final Clock clock;

    public DocumentService(
            DocumentRepository documentRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            PdfService pdfService,
            SettingsService settingsService,
            BusinessRepository businessRepository,
            DocumentIssuer documentIssuer,
            CreditNoteService creditNotes,
            RefundService refunds,
            GstSupply gstSupply,
            RecurringScheduleRepository scheduleRepository,
            PaymentClaimRepository claimRepository,
            DocumentActivity activity,
            NotificationService notificationService,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.pdfService = pdfService;
        this.settingsService = settingsService;
        this.businessRepository = businessRepository;
        this.documentIssuer = documentIssuer;
        this.creditNotes = creditNotes;
        this.refunds = refunds;
        this.gstSupply = gstSupply;
        this.scheduleRepository = scheduleRepository;
        this.claimRepository = claimRepository;
        this.activity = activity;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // A new document is issued as it is made, unless it is saved as a draft.
    @Transactional
    public DocumentResponse create(BusinessAccess access, CreateDocumentRequest request) {
        Customer customer = findCustomer(access.businessPkid(), request.customerId(), request.customer())
                .orElseThrow(DocumentService::customerNotFound);

        Document document = new Document();
        document.setBusiness(businessRepository.getReferenceById(access.businessPkid()));
        document.setCreatedBy(actor(access));
        document.setCustomer(customer);
        document.setCustomerSnapshot(CustomerSnapshot.of(customer));
        document.setDocumentType(documentType(request.documentType()));
        ZoneId zone = zoneOf(access.businessPkid());
        document.setIssueDate(issueDate(request.issueDate(), zone));
        document.setDueDate(takesDueDate(document) ? dueAt(request.dueDate(), zone) : null);
        DocumentIssuer.requireDueFromIssue(document, zone);
        document.setAdditionalInfo(request.additionalInfo());
        document.setTermsConditions(request.termsConditions());
        document.setShowPaymentDetails(!Boolean.FALSE.equals(request.showPaymentDetails()));
        document.setCurrency(request.currency());
        exchangeRate(document, request.exchangeRate(), false, access.businessPkid());
        document.setBillingItems(toBillingItems(request.billingItems(), percentage(request.rates(), "Tax rate")));
        gstSupply.place(document, request.placeOfSupply(), request.reverseCharge(), request.exportType());
        DocumentTotals.apply(document);
        documentRepository.save(document);
        activity.record(document, DocumentEventType.CREATED, null, actor(access));
        if (!Boolean.TRUE.equals(request.draft())) {
            documentIssuer.issue(document, null, actor(access));
        }
        return respond(document);
    }

    // Voids an issued document instead of deleting it: it keeps its number,
    // says why, and no longer counts toward anything owed. One with payments,
    // or that a schedule copies, is refused.
    @Transactional
    public DocumentResponse voidDocument(BusinessAccess access, String id, VoidDocumentRequest request) {
        Document document = lockInBusiness(access, id);
        if (document.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, "A draft isn't issued, so delete it instead");
        }
        if (document.isVoid()) {
            throw new ApiException(HttpStatus.CONFLICT, "This document is already void");
        }
        if (!document.getPayments().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Delete its payments before voiding it");
        }
        CreditNoteService.requireNoCreditNotes(document);
        if (scheduleRepository.findByTemplate(document).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice repeats. Stop its schedule before voiding it");
        }
        document.setVoidedAt(Instant.now(clock));
        document.setVoidReason(request.reason().trim());
        document.setOverdueAlertedAt(null);
        activity.record(document, DocumentEventType.VOIDED, document.getVoidReason(), actor(access));
        // Reports still waiting can't be confirmed on a void document, so they are dismissed.
        claimRepository.findByDocumentAndStatusOrderByCreatedAtAscPkidAsc(document, PaymentClaim.Status.PENDING)
                .forEach(claim -> PaymentClaimService.dismissed(
                        claim, "The document was voided", actor(access), document.getVoidedAt(), activity));
        if (document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            creditNotes.voided(document, actor(access));
        }
        return respond(document);
    }

    // Issues a draft: it takes its number, and the day and due date asked for.
    @Transactional
    public DocumentResponse issue(BusinessAccess access, String id, IssueDocumentRequest request) {
        Document document = lockInBusiness(access, id);
        ZoneId zone = zoneOf(access.businessPkid());
        Instant due = dueAt(request.dueDate(), zone);
        boolean creditNote = document.getDocumentType() == DocumentType.CREDIT_NOTE;
        if (document.isDraft() && due != null && takesDueDate(document)) {
            document.setDueDate(due);
        }
        if (document.isDraft() && !takesDueDate(document)) {
            document.setDueDate(null);
        }
        if (document.isDraft() && request.exchangeRate() != null && !creditNote) {
            exchangeRate(document, request.exchangeRate(), false, access.businessPkid());
        }
        documentIssuer.issue(document, issueDate(request.issueDate(), zone), actor(access));
        if (creditNote) {
            creditNotes.issued(document, actor(access));
        }
        return respond(document);
    }

    @Transactional(readOnly = true)
    public DocumentListResponse listMine(BusinessAccess access, DocumentSearch search) {
        ZoneId zone = zoneOf(access.businessPkid());
        Instant startOfToday = startOfToday(zone);
        Page<Document> result = documentRepository.findAll(
                DocumentSpecifications.matching(access.businessPkid(), search, startOfToday),
                PageRequest.of(search.page() - 1, search.size(), search.sort()));
        return new DocumentListResponse(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                search.page(),
                search.size(),
                result.getContent().stream()
                        .map(document -> DocumentResponse.from(document, startOfToday, zone))
                        .toList());
    }

    private User actor(BusinessAccess access) {
        return userRepository.getReferenceById(access.userPkid());
    }

    private ZoneId zoneOf(Long businessPkid) {
        return ZoneId.of(settingsService.forBusiness(businessPkid).getTimeZone());
    }

    private Instant startOfToday(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
    }

    // The day a document is to be dated, if one is chosen. A day still to
    // come is refused; without one, a document is dated the day it is issued.
    private LocalDate issueDate(LocalDate requested, ZoneId zone) {
        if (requested != null && requested.isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A document can't be dated after today");
        }
        return requested;
    }

    private DocumentResponse respond(Document document) {
        ZoneId zone = zoneOf(document.getBusiness().getPkid());
        return DocumentResponse.detail(
                document,
                startOfToday(zone),
                zone,
                documentRepository.findBySourceOrderByCreatedAtAscPkidAsc(document));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getOne(BusinessAccess access, String id) {
        return respond(requireInBusiness(
                access, id, "document not found",
                "You are not authorized to view this document. It's not yours"));
    }

    @Transactional(readOnly = true)
    public DocumentPdf renderPdf(BusinessAccess access, String id) {
        Document document = requireReadable(access, id);
        return new DocumentPdf(
                pdfFileName(document),
                pdfService.render(
                        document, document.getBusiness(), settingsService.forBusiness(access.businessPkid())),
                envelope(document));
    }

    // What an email of the document starts from. Only an issued document
    // goes to a client, and a void one no longer does.
    @Transactional(readOnly = true)
    public DocumentEnvelope envelopeToSend(BusinessAccess access, String id) {
        Document document = requireReadable(access, id);
        requireSendable(document);
        return envelope(document);
    }

    @Transactional(readOnly = true)
    public DocumentPdf renderPdfToSend(BusinessAccess access, String id) {
        requireSendable(requireReadable(access, id));
        return renderPdf(access, id);
    }

    private static void requireSendable(Document document) {
        requireIssued(document, "Issue this draft before sending it");
        requireNotVoid(document, "A void document isn't sent");
    }

    static void requireIssued(Document document, String refusal) {
        if (document.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, refusal);
        }
    }

    static void requireNotVoid(Document document, String refusal) {
        if (document.isVoid()) {
            throw new ApiException(HttpStatus.CONFLICT, refusal);
        }
    }

    private Document requireReadable(BusinessAccess access, String id) {
        return requireInBusiness(
                access, id, "document not found",
                "You are not authorized to view this document. It's not yours");
    }

    // A document's email: its title, number and total, and for an invoice
    // what is left to pay and when it is due, or for a quotation the day it is
    // valid until.
    private DocumentEnvelope envelope(Document document) {
        DocumentType type = document.getDocumentType();
        String title = document.getDocumentNumber() == null
                ? type.getValue()
                : type.getValue() + " " + document.getDocumentNumber();
        Map<String, String> values = new HashMap<>();
        values.put("document", title);
        values.put("number", Objects.requireNonNullElse(document.getDocumentNumber(), ""));
        values.put("amount", total(document));
        if (type == DocumentType.INVOICE) {
            values.put("balance", DocumentActivity.amount(document, DocumentTotals.balanceDue(document)));
            values.put("payment", howToPay(document));
        }
        if (type == DocumentType.INVOICE || type == DocumentType.QUOTATION) {
            values.put("due", Objects.requireNonNullElse(dueDate(document), ""));
        }
        return envelope(emailKind(type), title, document, values);
    }

    private static DocumentEnvelope envelope(
            EmailTemplateKind kind, String title, Document document, Map<String, String> values) {
        CustomerSnapshot customer = document.getCustomerSnapshot();
        return new DocumentEnvelope(
                kind,
                title,
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getEmail(),
                values,
                document.getId());
    }

    // How to pay an unpaid invoice that shows it, as email lines; nothing
    // otherwise.
    private String howToPay(Document document) {
        return PaymentSection.of(
                        document, settingsService.forBusiness(document.getBusiness().getPkid()).getPaymentDetails())
                .map(PaymentSection::text)
                .orElse("");
    }

    private static EmailTemplateKind emailKind(DocumentType type) {
        return switch (type) {
            case INVOICE -> EmailTemplateKind.INVOICE;
            case QUOTATION -> EmailTemplateKind.QUOTATION;
            case RECEIPT -> EmailTemplateKind.RECEIPT;
            case CREDIT_NOTE -> EmailTemplateKind.CREDIT_NOTE;
        };
    }

    private static String total(Document document) {
        return DocumentActivity.amount(document, Objects.requireNonNullElse(document.getTotal(), BigDecimal.ZERO));
    }

    // The day a document is due, in the business's time zone.
    private String dueDate(Document document) {
        return document.getDueDate() == null
                ? null
                : PdfService.DATE.format(document.getDueDate().atZone(zoneOf(document.getBusiness().getPkid())));
    }

    // The latest email of a kind the business could send, for previewing what
    // its subject and message would say.
    @Transactional(readOnly = true)
    public DocumentEnvelope latestEnvelope(BusinessAccess access, EmailTemplateKind kind) {
        Long businessPkid = access.businessPkid();
        PageRequest latest = PageRequest.of(0, 1);
        return switch (kind) {
            case REMINDER -> documentRepository
                    .findLatestUnpaid(businessPkid, DocumentType.INVOICE, DocumentStatus.PAID, latest)
                    .stream()
                    .findFirst()
                    .map(this::reminderEnvelope)
                    .orElseThrow(() -> nothingToPreview("unpaid invoice"));
            case PAYMENT_RECEIPT -> documentRepository.findLatestPayments(businessPkid, latest).stream()
                    .findFirst()
                    .map(payment -> receiptEnvelope(payment.getDocument(), payment))
                    .orElseThrow(() -> nothingToPreview("payment"));
            case STATEMENT -> throw new IllegalArgumentException("A statement isn't a document's email");
            default -> {
                DocumentType type = DocumentType.valueOf(kind.name());
                yield documentRepository.findLatestIssued(businessPkid, type, latest).stream()
                        .findFirst()
                        .map(this::envelope)
                        .orElseThrow(() -> nothingToPreview("issued " + kind.noun()));
            }
        };
    }

    static ApiException nothingToPreview(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "There's no " + what + " to preview this email with yet");
    }

    // A draft changes freely. An issued document keeps its client, lines,
    // currency, tax and issue date: only its due date, notes and terms, and
    // whether it shows how to pay, change. Its amounts stay as they went out.
    // It is locked while it changes, so an edit can't land on a draft that was
    // issued meanwhile.
    @Transactional
    public DocumentResponse update(BusinessAccess access, String id, UpdateDocumentRequest request) {
        Document document = lockInBusiness(
                access, id, "That document does not exist",
                "You are not authorized to update this document. It's not yours");
        requireNotVoid(document, "A void document can't be changed");
        if (!document.isDraft()) {
            requireOnlyDetails(document, request);
        }
        if (document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            requireOnlyCreditLines(request);
            if (request.creditReason() != null) {
                document.setCreditReason(request.creditReason().isBlank() ? null : request.creditReason().trim());
            }
        } else if (request.creditReason() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a credit note has a reason to credit");
        }
        if (request.customerId() != null) {
            Customer customer = findCustomer(access.businessPkid(), request.customerId(), null)
                    .orElseThrow(DocumentService::customerNotFound);
            document.setCustomer(customer);
            document.setCustomerSnapshot(CustomerSnapshot.of(customer));
        } else if (request.customer() != null) {
            Optional<Customer> customer = findCustomer(access.businessPkid(), null, request.customer());
            document.setCustomer(customer.orElse(null));
            document.setCustomerSnapshot(customer.map(CustomerSnapshot::of)
                    .orElseGet(() -> toSnapshot(request.customer())));
        }
        if (request.documentType() != null) {
            document.setDocumentType(documentType(request.documentType()));
        }
        ZoneId zone = zoneOf(access.businessPkid());
        // Only dates that change are checked, so a document saved due before
        // its issue date still takes other changes.
        LocalDate issuedOn = document.getIssueDate();
        LocalDate dueOn = document.dueDay(zone);
        if (request.issueDate() != null) {
            document.setIssueDate(issueDate(request.issueDate(), zone));
        }
        Instant due = dueAt(request.dueDate(), zone);
        if (due != null) {
            document.setDueDate(due);
        }
        if (!takesDueDate(document)) {
            document.setDueDate(null);
        }
        if (!Objects.equals(document.getIssueDate(), issuedOn) || !Objects.equals(document.dueDay(zone), dueOn)) {
            DocumentIssuer.requireDueFromIssue(document, zone);
        }
        if (request.additionalInfo() != null) {
            document.setAdditionalInfo(request.additionalInfo());
        }
        if (request.termsConditions() != null) {
            document.setTermsConditions(request.termsConditions());
        }
        if (request.showPaymentDetails() != null) {
            document.setShowPaymentDetails(request.showPaymentDetails());
        }
        boolean currencyChanged = request.currency() != null && !request.currency().equals(document.getCurrency());
        if (request.currency() != null) {
            document.setCurrency(request.currency());
        }
        if (document.isDraft()) {
            exchangeRate(document, request.exchangeRate(), currencyChanged, access.businessPkid());
        }
        // Lines sent without a rate take the rate sent with them, else the one
        // rate the draft had; a rate sent alone applies to every line.
        if (request.billingItems() != null) {
            BigDecimal rate = request.rates() != null
                    ? percentage(request.rates(), "Tax rate")
                    : Objects.requireNonNullElse(DocumentTotals.singleRate(document), BigDecimal.ZERO);
            document.setBillingItems(toBillingItems(request.billingItems(), rate));
        } else if (request.rates() != null) {
            BigDecimal rate = percentage(request.rates(), "Tax rate");
            document.getBillingItems().forEach(item -> item.setTaxRate(rate));
        }
        if (document.isDraft() && document.getDocumentType() != DocumentType.CREDIT_NOTE) {
            gstSupply.place(document, request.placeOfSupply(), request.reverseCharge(), request.exportType());
        } else if (document.isDraft()) {
            GstSupply.zeroRatedExport(document);
        }
        if (document.isDraft()) {
            DocumentTotals.apply(document);
        }
        if (!DocumentTotals.isOverdue(document, startOfToday(zoneOf(access.businessPkid())))) {
            document.setOverdueAlertedAt(null);
        }
        documentRepository.save(document);
        activity.record(document, DocumentEventType.EDITED, null, actor(access));
        return respond(document);
    }

    // The original is locked while it is copied, so the copy is of one
    // version of it.
    @Transactional
    public DocumentResponse duplicate(BusinessAccess access, String id) {
        Document original = lockInBusiness(
                access, id, "That document does not exist",
                "You are not authorized to copy this document. It's not yours");
        if (original.getDocumentType() == DocumentType.CREDIT_NOTE) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Make a new credit note from the document it credits instead");
        }
        User actor = actor(access);
        Document copy = copyOf(original, original.getDocumentType(), true, actor);
        activity.record(copy, DocumentEventType.COPIED_FROM, original.getDocumentNumber(), actor);
        activity.record(original, DocumentEventType.COPIED_TO, null, actor);
        return respond(copy);
    }

    // A draft credit note for an issued invoice or receipt, made while the
    // document is locked, so it isn't voided at the same moment.
    @Transactional
    public DocumentResponse creditNote(BusinessAccess access, String id) {
        Document credited = lockInBusiness(
                access, id, "That document does not exist",
                "You are not authorized to credit this document. It's not yours");
        return respond(creditNotes.create(credited, actor(access)));
    }

    // The quotation is locked, so two conversions at once take turns and the
    // second finds the invoice the first made.
    @Transactional
    public DocumentResponse convert(BusinessAccess access, String id) {
        Document quotation = lockInBusiness(
                access, id, "That document does not exist",
                "You are not authorized to convert this document. It's not yours");
        if (quotation.getDocumentType() != DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a quotation can become an invoice");
        }
        if (quotation.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, "Issue this quotation before it becomes an invoice");
        }
        requireNotVoid(quotation, "This quotation is void");
        documentRepository.findFirstBySourceAndDocumentType(quotation, DocumentType.INVOICE).ifPresent(invoice -> {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    invoice.isDraft()
                            ? "This quotation already has a draft invoice"
                            : "This quotation is already invoice " + invoice.getDocumentNumber());
        });
        User actor = actor(access);
        Document invoice = copyOf(quotation, DocumentType.INVOICE, true, actor);
        activity.record(invoice, DocumentEventType.CONVERTED_FROM, quotation.getDocumentNumber(), actor);
        activity.record(quotation, DocumentEventType.CONVERTED_TO, null, actor);
        return respond(invoice);
    }

    // The next invoice of a recurring schedule, made in the name of whoever
    // set up the schedule, and issued at once or left as a draft to review.
    // It isn't linked to the template as a copy, or a monthly template would
    // list every month.
    Document createRecurring(Document template, RecurringFrequency frequency, User author, boolean issue) {
        Document invoice = copyOf(template, DocumentType.INVOICE, false, author);
        activity.record(
                invoice,
                DocumentEventType.RECURRING,
                Objects.requireNonNullElse(template.getDocumentNumber(), "a draft") + " · " + frequency.label(),
                null);
        if (issue) {
            documentIssuer.issue(invoice, null, null);
        }
        return invoice;
    }

    // A new draft with the original's client and lines, due as the
    // business's payment terms say from today, unless it is a receipt.
    // Payments stay behind.
    private Document copyOf(Document original, DocumentType type, boolean linkSource, User author) {
        Long businessPkid = original.getBusiness().getPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        Document copy = new Document();
        copy.setBusiness(original.getBusiness());
        copy.setCreatedBy(author);
        copy.setSource(linkSource ? original : null);
        copy.setCustomer(original.getCustomer());
        copy.setCustomerSnapshot(original.getCustomer() == null
                ? copyOf(original.getCustomerSnapshot())
                : CustomerSnapshot.of(original.getCustomer()));
        copy.setDocumentType(type);
        copy.setDueDate(takesDueDate(copy)
                ? Document.dueAt(LocalDate.now(clock.withZone(zone)).plusDays(settings.getPaymentTermsDays()), zone)
                : null);
        copy.setAdditionalInfo(original.getAdditionalInfo());
        copy.setTermsConditions(original.getTermsConditions());
        copy.setShowPaymentDetails(original.isShowPaymentDetails());
        copy.setCurrency(original.getCurrency());
        // The original's rate, while it is still to the reporting currency;
        // like any draft's, it can change until the copy is issued.
        if (settings.getBaseCurrency().equals(original.getExchangeBase())) {
            copy.setExchangeRate(original.getExchangeRate());
            copy.setExchangeBase(original.getExchangeBase());
        }
        copy.setBillingItems(original.getBillingItems().stream().map(DocumentService::copyOf).toList());
        // A GST copy goes where the original was sent by choice, else where its
        // client is now, with the original's reverse charge and export choice,
        // under the business's regime as a draft is saved.
        copy.setSupplyType(original.getSupplyType());
        String chosen = GstSupply.chosenPlace(original, original.getCustomerSnapshot());
        gstSupply.place(copy, chosen, original.isReverseCharge(), null);
        DocumentTotals.apply(copy);
        return documentRepository.save(copy);
    }

    static BillingItem copyOf(BillingItem original) {
        BillingItem item = new BillingItem();
        item.setItemName(original.getItemName());
        item.setUnitPrice(original.getUnitPrice());
        item.setQuantity(original.getQuantity());
        item.setUnit(original.getUnit());
        item.setTaxRate(original.getTaxRate());
        item.setDiscount(original.getDiscount());
        item.setHsnSac(original.getHsnSac());
        return item;
    }

    static CustomerSnapshot copyOf(CustomerSnapshot original) {
        if (original == null) {
            return null;
        }
        CustomerSnapshot snapshot = new CustomerSnapshot();
        snapshot.setName(original.getName());
        snapshot.setEmail(original.getEmail());
        snapshot.setAccountNo(original.getAccountNo());
        snapshot.setVatTinNo(original.getVatTinNo());
        snapshot.setAddress(original.getAddress());
        snapshot.setCity(original.getCity());
        snapshot.setCountry(original.getCountry());
        snapshot.setPhoneNumber(original.getPhoneNumber());
        snapshot.setGstin(original.getGstin());
        snapshot.setStateCode(original.getStateCode());
        return snapshot;
    }

    // Only a draft is deleted: an issued document keeps its number, and is
    // voided instead. It is locked first, so a draft being issued meanwhile
    // isn't deleted. A draft a schedule copies stays until the schedule is
    // stopped, or the schedule and its runs would go with it.
    @Transactional
    public void delete(BusinessAccess access, String id) {
        Document document = lockInBusiness(
                access, id, "That document does not exist!",
                "You are not authorized to delete this document. It's not yours");
        if (!document.isDraft()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    document.isVoid()
                            ? "A void document is kept with its number"
                            : "An issued document can't be deleted. Void it instead");
        }
        if (scheduleRepository.findByTemplate(document).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This draft repeats. Stop its schedule before deleting it");
        }
        documentRepository.delete(document);
    }

    @Transactional
    public DocumentResponse addPayment(BusinessAccess access, String id, CreatePaymentRequest request) {
        Document document = lockInBusiness(access, id);
        recordPayment(document, request, actor(access));
        return respond(document);
    }

    // Records a payment on a document the caller has locked, as everything
    // that takes a payment does, and returns it.
    public Payment recordPayment(Document document, CreatePaymentRequest request, User actor) {
        requireIssued(document, "Issue this draft before recording a payment");
        requireNotVoid(document, "A void document can't take payments");
        if (document.getDocumentType() == DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "A quotation can't take payments");
        }
        if (document.getDocumentType() == DocumentType.RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "A receipt is already paid in full");
        }
        if (document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            throw new ApiException(HttpStatus.CONFLICT, "A credit note can't take payments");
        }
        Payment payment = new Payment();
        payment.setDocument(document);
        document.setReceiptsNumbered(document.getReceiptsNumbered() + 1);
        payment.setReceiptNumber(document.getReceiptsNumbered());
        CustomerSnapshot customer = document.getCustomerSnapshot();
        payment.setPaidBy(customer == null ? null : customer.getName());
        payment.setDatePaid(paymentDate(request.datePaid(), document.getBusiness().getPkid()));
        Amounts amounts = paymentAmounts(request.amountPaid(), request.tdsAmount());
        payment.setAmountPaid(amounts.received());
        payment.setTdsAmount(amounts.withheld());
        payment.setPaymentMethod(paymentMethod(request.paymentMethod()));
        payment.setAdditionalInfo(request.additionalInfo());
        requireWithinBalance(document, amounts.settled());
        document.getPayments().add(payment);
        DocumentTotals.settle(document);
        documentRepository.flush();
        activity.record(
                document,
                DocumentEventType.PAYMENT_RECORDED,
                DocumentActivity.paymentDetail(document, payment),
                payment.getPkid(),
                actor);
        return payment;
    }

    @Transactional
    public DocumentResponse updatePayment(
            BusinessAccess access, String id, String paymentId, UpdatePaymentRequest request) {
        Document document = lockInBusiness(access, id);
        Payment payment = requirePayment(document, paymentId);
        if (request.datePaid() != null) {
            payment.setDatePaid(paymentDate(request.datePaid(), access.businessPkid()));
        }
        if (request.paymentMethod() != null) {
            payment.setPaymentMethod(paymentMethod(request.paymentMethod()));
        }
        if (request.additionalInfo() != null) {
            payment.setAdditionalInfo(request.additionalInfo());
        }
        if (request.amountPaid() != null || request.tdsAmount() != null) {
            Amounts amounts = paymentAmounts(
                    Objects.requireNonNullElse(request.amountPaid(), payment.getAmountPaid()),
                    Objects.requireNonNullElse(request.tdsAmount(), payment.getTdsAmount()));
            BigDecimal change = amounts.settled().subtract(payment.getAmountPaid()).subtract(payment.getTdsAmount());
            requireWithinBalance(document, change);
            RefundService.requireCovered(
                    document,
                    DocumentTotals.received(document).add(DocumentTotals.withheld(document)).add(change));
            payment.setAmountPaid(amounts.received());
            payment.setTdsAmount(amounts.withheld());
        }
        DocumentTotals.settle(document);
        activity.record(
                document,
                DocumentEventType.PAYMENT_EDITED,
                DocumentActivity.paymentDetail(document, payment),
                payment.getPkid(),
                actor(access));
        return respond(document);
    }

    @Transactional
    public DocumentResponse deletePayment(BusinessAccess access, String id, String paymentId) {
        Document document = lockInBusiness(access, id);
        Payment payment = requirePayment(document, paymentId);
        RefundService.requireCovered(
                document,
                DocumentTotals.received(document)
                        .add(DocumentTotals.withheld(document))
                        .subtract(payment.getAmountPaid())
                        .subtract(payment.getTdsAmount()));
        // Found before the payment goes, as deleting it unlinks the report.
        Optional<PaymentClaim> confirmed = claimRepository.findByPayment(payment);
        document.getPayments().remove(payment);
        DocumentTotals.settle(document);
        activity.record(
                document,
                DocumentEventType.PAYMENT_DELETED,
                DocumentActivity.paymentDetail(document, payment),
                actor(access));
        confirmed.ifPresent(claim -> PaymentClaimService.dismissed(
                claim, "Its payment was deleted", actor(access), Instant.now(clock), activity));
        return respond(document);
    }

    @Transactional
    public DocumentResponse addRefund(BusinessAccess access, String id, RefundRequest request) {
        Document document = lockInBusiness(access, id);
        refunds.add(document, request, actor(access));
        return respond(document);
    }

    @Transactional
    public DocumentResponse updateRefund(BusinessAccess access, String id, String refundId, RefundRequest request) {
        Document document = lockInBusiness(access, id);
        refunds.update(document, refundId, request, actor(access));
        return respond(document);
    }

    @Transactional
    public DocumentResponse deleteRefund(BusinessAccess access, String id, String refundId) {
        Document document = lockInBusiness(access, id);
        refunds.delete(document, refundId, actor(access));
        return respond(document);
    }

    @Transactional(readOnly = true)
    public ActivityResponse activity(BusinessAccess access, String id) {
        Document document = requireInBusiness(
                access, id, "document not found",
                "You are not authorized to view this document. It's not yours");
        return new ActivityResponse(
                true,
                activity.history(document).stream()
                        .map(event -> ActivityEntry.from(event, access.userPkid()))
                        .toList());
    }

    @Transactional
    public void recordEmailed(BusinessAccess access, String id, String to) {
        activity.record(requireReadable(access, id), DocumentEventType.EMAILED, "to " + to, actor(access));
    }

    @Transactional
    public void recordEmailedAutomatically(BusinessAccess access, String id, String to) {
        activity.record(requireReadable(access, id), DocumentEventType.EMAILED, "automatically to " + to, null);
    }

    @Transactional(readOnly = true)
    public DocumentPdf renderReceipt(BusinessAccess access, String id, String paymentId) {
        Document document = requireReadable(access, id);
        Payment payment = requirePayment(document, paymentId);
        List<Payment> paidSoFar = document.getPayments().subList(0, document.getPayments().indexOf(payment) + 1);
        String number = receiptNumber(document, payment);
        return new DocumentPdf(
                "Receipt-" + number.replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf",
                pdfService.renderReceipt(
                        document,
                        payment,
                        number,
                        paidSoFar.stream()
                                .map(paid -> paid.getAmountPaid().add(paid.getTdsAmount()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        document.getBusiness(),
                        settingsService.forBusiness(document.getBusiness().getPkid()).getPdfTemplate()),
                receiptEnvelope(document, payment));
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope receiptEnvelope(BusinessAccess access, String id, String paymentId) {
        Document document = requireReadable(access, id);
        return receiptEnvelope(document, requirePayment(document, paymentId));
    }

    @Transactional
    public void recordReceiptEmailed(BusinessAccess access, String id, String paymentId, String to) {
        Document document = requireReadable(access, id);
        Payment payment = requirePayment(document, paymentId);
        activity.record(
                document,
                DocumentEventType.RECEIPT_EMAILED,
                receiptNumber(document, payment) + " to " + to,
                payment.getPkid(),
                actor(access));
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope reminderEnvelope(BusinessAccess access, String id) {
        Document document = requireReadable(access, id);
        requireIssued(document, "Issue this draft before sending a reminder");
        requireNotVoid(document, "A void document needs no reminder");
        if (document.getDocumentType() != DocumentType.INVOICE || document.getStatus() == DocumentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an unpaid invoice needs a reminder");
        }
        return reminderEnvelope(document);
    }

    // A reminder names what is still owed and when it was, or is, due.
    private DocumentEnvelope reminderEnvelope(Document document) {
        String title = document.getDocumentType().getValue() + " " + document.getDocumentNumber();
        String due = dueDate(document);
        boolean overdue = DocumentTotals.isOverdue(
                document, startOfToday(zoneOf(document.getBusiness().getPkid())));
        Map<String, String> values = new HashMap<>();
        values.put("document", title);
        values.put("number", document.getDocumentNumber());
        values.put("amount", total(document));
        values.put("balance", DocumentActivity.amount(document, DocumentTotals.balanceDue(document)));
        values.put("due", Objects.requireNonNullElse(due, ""));
        values.put("due_status", due == null
                ? "is still open"
                : overdue ? "was due on " + due + " and is still open" : "is due on " + due);
        values.put("payment", howToPay(document));
        return envelope(EmailTemplateKind.REMINDER, title, document, values);
    }

    // Counted with the invoice locked, as any change to it is, so a reminder
    // counted while the invoice is paid or voided adds to that change and
    // undoes none of it.
    @Transactional
    public void recordReminder(BusinessAccess access, String id, String to, boolean automatic) {
        Document document = lockInBusiness(access, id);
        document.setRemindersSent(document.getRemindersSent() + 1);
        document.setLastRemindedAt(Instant.now(clock));
        activity.record(
                document,
                DocumentEventType.REMINDED,
                (automatic ? "automatically " : "") + "to " + to,
                automatic ? null : actor(access));
        if (automatic) {
            notificationService.notify(
                    document,
                    NotificationType.REMINDER_SENT,
                    "Reminder sent to " + to + " for " + document.getDocumentType().getValue() + " "
                            + document.getDocumentNumber());
        }
    }

    // A receipt is numbered in the order its payment was recorded, and keeps
    // that number.
    private static String receiptNumber(Document document, Payment payment) {
        return document.getDocumentNumber() + "-P" + payment.getReceiptNumber();
    }

    // A payment receipt's email: the receipt, what was paid, and what is
    // still to pay on the document.
    private static DocumentEnvelope receiptEnvelope(Document document, Payment payment) {
        String number = receiptNumber(document, payment);
        String title = "Payment receipt " + number;
        Map<String, String> values = new HashMap<>();
        values.put("document", title);
        values.put("number", number);
        values.put("amount", DocumentActivity.amount(document, payment.getAmountPaid()));
        values.put("balance", DocumentActivity.amount(document, DocumentTotals.balanceDue(document)));
        return envelope(EmailTemplateKind.PAYMENT_RECEIPT, title, document, values);
    }

    // The document, locked until the transaction ends, so two changes to it
    // take turns.
    // A credit note's document is locked before the note, as everything that
    // changes both does.
    private Document lockInBusiness(BusinessAccess access, String id) {
        return lockInBusiness(
                access, id, "That document does not exist!",
                "You are not authorized to update this document. It's not yours");
    }

    private Document lockInBusiness(
            BusinessAccess access, String id, String notFoundMessage, String forbiddenMessage) {
        Optional<UUID> external = externalId(id);
        external.flatMap(documentRepository::findCreditedPkid).ifPresent(documentRepository::findForUpdateByPkid);
        Document document = external
                .flatMap(documentRepository::findForUpdateByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFoundMessage));
        if (!document.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return document;
    }

    // An invoice falls due and a quotation is valid until a day; a receipt is
    // paid as it is made, and a credit note takes its invoice's terms.
    private static boolean takesDueDate(Document document) {
        return document.getDocumentType() == DocumentType.INVOICE
                || document.getDocumentType() == DocumentType.QUOTATION;
    }

    private static Payment requirePayment(Document document, String paymentId) {
        return externalId(paymentId)
                .flatMap(external -> document.getPayments().stream()
                        .filter(payment -> external.equals(payment.getId()))
                        .findFirst())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That payment does not exist"));
    }

    // A payment settles at most what is still owed, counting the tax the
    // client withholds and what credit notes took off.
    private static void requireWithinBalance(Document document, BigDecimal extra) {
        BigDecimal owed = document.getTotal()
                .subtract(document.getTotalCredited())
                .subtract(DocumentTotals.received(document))
                .subtract(DocumentTotals.withheld(document))
                .add(document.getTotalRefunded());
        if (extra.compareTo(owed) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "That is more than the " + owed.max(BigDecimal.ZERO).setScale(2).toPlainString()
                            + " still owed");
        }
    }

    // Money received and tax the client withheld from a payment.
    private record Amounts(BigDecimal received, BigDecimal withheld) {

        BigDecimal settled() {
            return received.add(withheld);
        }
    }

    // Neither below nothing, each to the cent, and together more than nothing:
    // a payment can be only the tax withheld.
    private static Amounts paymentAmounts(BigDecimal received, BigDecimal withheld) {
        Amounts amounts = new Amounts(cents(received, "An amount"), cents(withheld, "Tax withheld"));
        if (amounts.settled().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an amount greater than zero");
        }
        return amounts;
    }

    private static BigDecimal cents(BigDecimal amount, String label) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an amount greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " can have at most 2 decimals");
        }
        return amount.setScale(2);
    }

    // The day a payment was made, today where the business is unless one is
    // given. A day still to come everywhere is refused: money not received
    // yet, such as a post-dated cheque, is recorded the day it comes in.
    private LocalDate paymentDate(String value, Long businessPkid) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(clock.withZone(zoneOf(businessPkid)));
        }
        LocalDate day;
        try {
            day = value.length() == 10
                    ? LocalDate.parse(value)
                    : LocalDate.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the payment date as YYYY-MM-DD");
        }
        if (day.isAfter(Today.latest(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A payment can't be dated after today");
        }
        return day;
    }

    // A due date comes as a day in the business's calendar, YYYY-MM-DD. A
    // moment from an older client counts as the day it falls on in the
    // business's time zone, the day its PDF and emails always gave it.
    private static Instant dueAt(String value, ZoneId zone) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate day = value.length() == 10
                    ? LocalDate.parse(value)
                    : LocalDate.ofInstant(Instant.parse(value), zone);
            return Document.dueAt(day, zone);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the due date as YYYY-MM-DD");
        }
    }

    // The rate from a draft's currency to the reporting currency, typed in as
    // "1 {currency} = x {base}"; a blank one removes it. A document in the
    // reporting currency has none, and a new currency needs a new rate.
    private void exchangeRate(Document document, String value, boolean currencyChanged, Long businessPkid) {
        BigDecimal rate = ExchangeRate.parse(value);
        if (rate != null) {
            String base = settingsService.forBusiness(businessPkid).getBaseCurrency();
            if (document.getCurrency() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the currency before its exchange rate");
            }
            if (document.getCurrency().equals(base)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "A document in " + base + ", your reporting currency, needs no exchange rate");
            }
            document.setExchangeRate(rate);
            document.setExchangeBase(base);
        } else if (value != null
                || currencyChanged
                || Objects.equals(document.getCurrency(), document.getExchangeBase())) {
            document.setExchangeRate(null);
            document.setExchangeBase(null);
        }
    }

    // A credit note keeps the client, currency and exchange rate of what it
    // credits, and has no due date; its lines, date, notes and reason change
    // while it is a draft.
    private static void requireOnlyCreditLines(UpdateDocumentRequest request) {
        if (request.customerId() != null
                || request.customer() != null
                || request.documentType() != null
                || request.currency() != null
                || request.exchangeRate() != null
                || request.placeOfSupply() != null
                || request.reverseCharge() != null
                || request.exportType() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "A credit note keeps the client, currency and exchange rate of the document it credits");
        }
        if (hasDueDate(request)) {
            throw new ApiException(HttpStatus.CONFLICT, "A credit note has no due date");
        }
    }

    // A blank due date is none, as dueAt reads it.
    private static boolean hasDueDate(UpdateDocumentRequest request) {
        return request.dueDate() != null && !request.dueDate().isBlank();
    }

    // Only the due date, notes and terms of an issued document change; a
    // receipt has no due date, and a quotation the client answered stays as
    // it was. An expired quotation opens again with a later date.
    private static void requireOnlyDetails(Document document, UpdateDocumentRequest request) {
        if (document.getAcceptedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "An accepted quotation can't be changed");
        }
        if (document.getDeclinedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "A declined quotation can't be changed");
        }
        if (request.customerId() != null
                || request.customer() != null
                || request.documentType() != null
                || request.issueDate() != null
                || request.rates() != null
                || request.currency() != null
                || request.exchangeRate() != null
                || request.creditReason() != null
                || request.placeOfSupply() != null
                || request.reverseCharge() != null
                || request.exportType() != null
                || request.billingItems() != null) {
            throw new ApiException(HttpStatus.CONFLICT, LOCKED);
        }
        if (hasDueDate(request) && document.getDocumentType() == DocumentType.RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "A receipt has no due date");
        }
        if (hasDueDate(request) && document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            throw new ApiException(HttpStatus.CONFLICT, "A credit note has no due date");
        }
    }

    private Document requireInBusiness(
            BusinessAccess access, String id, String notFoundMessage, String forbiddenMessage) {
        Document document = externalId(id)
                .flatMap(documentRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFoundMessage));
        if (!document.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return document;
    }

    private Optional<Customer> findCustomer(Long businessPkid, String customerId, DocumentCustomerDto snapshot) {
        if (customerId != null) {
            return externalId(customerId)
                    .flatMap(customerRepository::findByExternalId)
                    .filter(customer -> customer.getBusiness().getPkid().equals(businessPkid));
        }
        if (snapshot != null && snapshot.accountNo() != null && !snapshot.accountNo().isBlank()) {
            return customerRepository.findByBusinessPkidAndAccountNo(businessPkid, snapshot.accountNo());
        }
        return Optional.empty();
    }

    private static ApiException customerNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "That customer does not exist for the currently logged in user");
    }

    private static CustomerSnapshot toSnapshot(DocumentCustomerDto dto) {
        if (dto == null) {
            return null;
        }
        CustomerSnapshot snapshot = new CustomerSnapshot();
        snapshot.setName(dto.name());
        snapshot.setEmail(dto.email());
        snapshot.setAccountNo(dto.accountNo());
        snapshot.setVatTinNo(dto.vatTinNo());
        snapshot.setAddress(dto.address());
        snapshot.setCity(dto.city());
        snapshot.setCountry(dto.country());
        snapshot.setPhoneNumber(dto.phoneNumber());
        snapshot.setGstin(Gstin.parse(dto.gstin()));
        snapshot.setStateCode(snapshot.getGstin() == null ? null : Gstin.stateCode(snapshot.getGstin()));
        return snapshot;
    }

    // Each line is taxed at its own rate, or at the given one when it has none.
    private static List<BillingItem> toBillingItems(List<BillingItemDto> items, BigDecimal defaultRate) {
        List<BillingItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (BillingItemDto dto : items) {
            BillingItem item = new BillingItem();
            item.setItemName(dto.itemName());
            item.setUnitPrice(dto.unitPrice());
            item.setQuantity(dto.quantity() == null ? BigDecimal.ZERO : dto.quantity());
            item.setUnit(dto.unit() == null || dto.unit().isBlank() ? null : dto.unit().trim());
            item.setDiscount(percentage(dto.discount(), "Discount"));
            item.setTaxRate(dto.taxRate() == null || dto.taxRate().isBlank()
                    ? defaultRate
                    : percentage(dto.taxRate(), "Tax rate"));
            item.setHsnSac(HsnSac.parse(dto.hsnSac()));
            result.add(item);
        }
        return result;
    }

    private static BigDecimal percentage(String value, String label) {
        return Objects.requireNonNullElse(Percentage.parse(value, label), BigDecimal.ZERO);
    }

    static String pdfFileName(Document document) {
        if (document.isDraft()) {
            return "Draft-" + document.getDocumentType().getValue().toLowerCase(Locale.ROOT) + ".pdf";
        }
        String number = document.getDocumentNumber() == null
                ? ""
                : document.getDocumentNumber().replaceAll("[^A-Za-z0-9._-]", "-");
        return (number.isBlank() ? "document" : number) + ".pdf";
    }

    // A credit note is made from the document it credits, not chosen.
    private static DocumentType documentType(String value) {
        if (value == null) {
            return DocumentType.INVOICE;
        }
        DocumentType type;
        try {
            type = DocumentType.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document type: " + value);
        }
        if (type == DocumentType.CREDIT_NOTE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Make a credit note from the invoice or receipt it credits");
        }
        return type;
    }

    private static PaymentMethod paymentMethod(String value) {
        if (value == null) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment method: " + value);
        }
    }

    private static Optional<UUID> externalId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
