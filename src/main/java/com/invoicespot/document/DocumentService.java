package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
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
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.document.dto.UpdatePaymentRequest;
import com.invoicespot.document.dto.VoidDocumentRequest;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String LOCKED = "An issued document keeps its client, lines, tax and issue date."
            + " Void it and duplicate it to change them";

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PdfService pdfService;
    private final SettingsService settingsService;
    private final BusinessRepository businessRepository;
    private final DocumentIssuer documentIssuer;
    private final RecurringScheduleRepository scheduleRepository;
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
            RecurringScheduleRepository scheduleRepository,
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
        this.scheduleRepository = scheduleRepository;
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
        document.setIssueDate(issueDate(request.issueDate(), zoneOf(access.businessPkid())));
        document.setDueDate(request.dueDate());
        document.setAdditionalInfo(request.additionalInfo());
        document.setTermsConditions(request.termsConditions());
        document.setRates(percentage(request.rates(), "Tax rate"));
        document.setCurrency(request.currency());
        document.setBillingItems(toBillingItems(request.billingItems()));
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
        if (scheduleRepository.findByTemplate(document).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice repeats. Stop its schedule before voiding it");
        }
        document.setVoidedAt(Instant.now(clock));
        document.setVoidReason(request.reason().trim());
        document.setOverdueAlertedAt(null);
        activity.record(document, DocumentEventType.VOIDED, document.getVoidReason(), actor(access));
        return respond(document);
    }

    // Issues a draft: it takes its number, and the day and due date asked for.
    @Transactional
    public DocumentResponse issue(BusinessAccess access, String id, IssueDocumentRequest request) {
        Document document = lockInBusiness(access, id);
        if (document.isDraft() && request.dueDate() != null) {
            document.setDueDate(request.dueDate());
        }
        documentIssuer.issue(document, issueDate(request.issueDate(), zoneOf(access.businessPkid())), actor(access));
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
                        .map(document -> DocumentResponse.from(document, startOfToday))
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
        return DocumentResponse.detail(
                document,
                startOfToday(zoneOf(document.getBusiness().getPkid())),
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

    private static DocumentEnvelope envelope(Document document) {
        CustomerSnapshot customer = document.getCustomerSnapshot();
        String title = document.getDocumentNumber() == null
                ? document.getDocumentType().getValue()
                : document.getDocumentType().getValue() + " " + document.getDocumentNumber();
        return new DocumentEnvelope(
                title,
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getEmail());
    }

    // A draft changes freely. An issued document keeps its client, lines,
    // currency, tax and issue date: only its due date, notes and terms change,
    // and its amounts stay as they went out.
    @Transactional
    public DocumentResponse update(BusinessAccess access, String id, UpdateDocumentRequest request) {
        Document document = requireInBusiness(
                access, id, "That document does not exist",
                "You are not authorized to update this document. It's not yours");
        requireNotVoid(document, "A void document can't be changed");
        if (!document.isDraft()) {
            requireOnlyDetails(document, request);
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
        if (request.issueDate() != null) {
            document.setIssueDate(issueDate(request.issueDate(), zoneOf(access.businessPkid())));
        }
        if (request.dueDate() != null) {
            document.setDueDate(request.dueDate());
        }
        if (request.additionalInfo() != null) {
            document.setAdditionalInfo(request.additionalInfo());
        }
        if (request.termsConditions() != null) {
            document.setTermsConditions(request.termsConditions());
        }
        if (request.rates() != null) {
            document.setRates(percentage(request.rates(), "Tax rate"));
        }
        if (request.currency() != null) {
            document.setCurrency(request.currency());
        }
        if (request.billingItems() != null) {
            document.setBillingItems(toBillingItems(request.billingItems()));
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

    @Transactional
    public DocumentResponse duplicate(BusinessAccess access, String id) {
        Document original = requireInBusiness(
                access, id, "That document does not exist",
                "You are not authorized to copy this document. It's not yours");
        User actor = actor(access);
        Document copy = copyOf(original, original.getDocumentType(), true, actor);
        activity.record(copy, DocumentEventType.COPIED_FROM, original.getDocumentNumber(), actor);
        activity.record(original, DocumentEventType.COPIED_TO, null, actor);
        return respond(copy);
    }

    @Transactional
    public DocumentResponse convert(BusinessAccess access, String id) {
        Document quotation = requireInBusiness(
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
    // business's payment terms say from today. Payments stay behind.
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
        copy.setDueDate(LocalDate.now(clock.withZone(zone))
                .plusDays(settings.getPaymentTermsDays())
                .atTime(LocalTime.NOON)
                .atZone(zone)
                .toInstant());
        copy.setAdditionalInfo(original.getAdditionalInfo());
        copy.setTermsConditions(original.getTermsConditions());
        copy.setRates(original.getRates());
        copy.setCurrency(original.getCurrency());
        copy.setBillingItems(original.getBillingItems().stream().map(DocumentService::copyOf).toList());
        DocumentTotals.apply(copy);
        return documentRepository.save(copy);
    }

    private static BillingItem copyOf(BillingItem original) {
        BillingItem item = new BillingItem();
        item.setItemName(original.getItemName());
        item.setUnitPrice(original.getUnitPrice());
        item.setQuantity(original.getQuantity());
        item.setDiscount(original.getDiscount());
        return item;
    }

    private static CustomerSnapshot copyOf(CustomerSnapshot original) {
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
        return snapshot;
    }

    // Only a draft is deleted: an issued document keeps its number, and is
    // voided instead.
    @Transactional
    public void delete(BusinessAccess access, String id) {
        Document document = requireInBusiness(
                access, id, "That document does not exist!",
                "You are not authorized to delete this document. It's not yours");
        if (!document.isDraft()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    document.isVoid()
                            ? "A void document is kept with its number"
                            : "An issued document can't be deleted. Void it instead");
        }
        documentRepository.delete(document);
    }

    @Transactional
    public DocumentResponse addPayment(BusinessAccess access, String id, CreatePaymentRequest request) {
        Document document = lockInBusiness(access, id);
        requireIssued(document, "Issue this draft before recording a payment");
        requireNotVoid(document, "A void document can't take payments");
        if (document.getDocumentType() == DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "A quotation can't take payments");
        }
        if (document.getDocumentType() == DocumentType.RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "A receipt is already paid in full");
        }
        Payment payment = new Payment();
        payment.setDocument(document);
        CustomerSnapshot customer = document.getCustomerSnapshot();
        payment.setPaidBy(customer == null ? null : customer.getName());
        payment.setDatePaid(paymentDate(request.datePaid()));
        payment.setAmountPaid(paymentAmount(request.amountPaid()));
        payment.setPaymentMethod(paymentMethod(request.paymentMethod()));
        payment.setAdditionalInfo(request.additionalInfo());
        requireWithinBalance(document, payment.getAmountPaid());
        document.getPayments().add(payment);
        DocumentTotals.settle(document);
        documentRepository.flush();
        activity.record(
                document,
                DocumentEventType.PAYMENT_RECORDED,
                DocumentActivity.paymentDetail(document, payment),
                payment.getPkid(),
                actor(access));
        return respond(document);
    }

    @Transactional
    public DocumentResponse updatePayment(
            BusinessAccess access, String id, String paymentId, UpdatePaymentRequest request) {
        Document document = lockInBusiness(access, id);
        Payment payment = requirePayment(document, paymentId);
        if (request.datePaid() != null) {
            payment.setDatePaid(paymentDate(request.datePaid()));
        }
        if (request.paymentMethod() != null) {
            payment.setPaymentMethod(paymentMethod(request.paymentMethod()));
        }
        if (request.additionalInfo() != null) {
            payment.setAdditionalInfo(request.additionalInfo());
        }
        if (request.amountPaid() != null) {
            BigDecimal amount = paymentAmount(request.amountPaid());
            requireWithinBalance(document, amount.subtract(payment.getAmountPaid()));
            payment.setAmountPaid(amount);
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
        document.getPayments().remove(payment);
        DocumentTotals.settle(document);
        activity.record(
                document,
                DocumentEventType.PAYMENT_DELETED,
                DocumentActivity.paymentDetail(document, payment),
                actor(access));
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
                        paidSoFar.stream().map(Payment::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add),
                        document.getBusiness()),
                receiptEnvelope(document, number));
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope receiptEnvelope(BusinessAccess access, String id, String paymentId) {
        Document document = requireReadable(access, id);
        return receiptEnvelope(document, receiptNumber(document, requirePayment(document, paymentId)));
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
    public ReminderFacts reminderFacts(BusinessAccess access, String id) {
        Document document = requireReadable(access, id);
        requireIssued(document, "Issue this draft before sending a reminder");
        requireNotVoid(document, "A void document needs no reminder");
        if (document.getDocumentType() != DocumentType.INVOICE || document.getStatus() == DocumentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an unpaid invoice needs a reminder");
        }
        return reminderFacts(document);
    }

    ReminderFacts reminderFacts(Document document) {
        ZoneId zone = zoneOf(document.getBusiness().getPkid());
        CustomerSnapshot customer = document.getCustomerSnapshot();
        BigDecimal total = document.getTotal() == null ? BigDecimal.ZERO : document.getTotal();
        BigDecimal received =
                document.getTotalAmountReceived() == null ? BigDecimal.ZERO : document.getTotalAmountReceived();
        return new ReminderFacts(
                document.getDocumentType().getValue() + " " + document.getDocumentNumber(),
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getEmail(),
                DocumentActivity.amount(document, total.subtract(received)),
                document.getDueDate() == null ? null : PdfService.DATE.format(document.getDueDate().atZone(zone)),
                DocumentTotals.isOverdue(document, startOfToday(zone)));
    }

    @Transactional
    public void recordReminder(BusinessAccess access, String id, String to, boolean automatic) {
        Document document = requireReadable(access, id);
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

    // Payments are kept in date order, so a receipt's number follows it.
    private static String receiptNumber(Document document, Payment payment) {
        return document.getDocumentNumber() + "-P" + (document.getPayments().indexOf(payment) + 1);
    }

    private static DocumentEnvelope receiptEnvelope(Document document, String number) {
        CustomerSnapshot customer = document.getCustomerSnapshot();
        return new DocumentEnvelope(
                "Payment receipt " + number,
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getEmail());
    }

    // The document, locked until the transaction ends, so two changes to it
    // take turns.
    private Document lockInBusiness(BusinessAccess access, String id) {
        Document document = externalId(id)
                .flatMap(documentRepository::findForUpdateByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist!"));
        if (!document.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "You are not authorized to update this document. It's not yours");
        }
        return document;
    }

    private static Payment requirePayment(Document document, String paymentId) {
        return externalId(paymentId)
                .flatMap(external -> document.getPayments().stream()
                        .filter(payment -> external.equals(payment.getId()))
                        .findFirst())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That payment does not exist"));
    }

    private static void requireWithinBalance(Document document, BigDecimal extra) {
        BigDecimal owed = document.getTotal().subtract(DocumentTotals.received(document));
        if (extra.compareTo(owed) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "That is more than the " + owed.max(BigDecimal.ZERO).setScale(2).toPlainString()
                            + " still owed");
        }
    }

    private static BigDecimal paymentAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an amount greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An amount can have at most 2 decimals");
        }
        return amount.setScale(2);
    }

    private LocalDate paymentDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return value.length() == 10
                    ? LocalDate.parse(value)
                    : LocalDate.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the payment date as YYYY-MM-DD");
        }
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
                || request.billingItems() != null) {
            throw new ApiException(HttpStatus.CONFLICT, LOCKED);
        }
        if (request.dueDate() != null && document.getDocumentType() == DocumentType.RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "A receipt has no due date");
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
        return snapshot;
    }

    private static List<BillingItem> toBillingItems(List<BillingItemDto> items) {
        List<BillingItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (BillingItemDto dto : items) {
            BillingItem item = new BillingItem();
            item.setItemName(dto.itemName());
            item.setUnitPrice(dto.unitPrice());
            item.setQuantity(dto.quantity());
            item.setDiscount(percentage(dto.discount(), "Discount"));
            result.add(item);
        }
        return result;
    }

    private static BigDecimal percentage(String value, String label) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal number = new BigDecimal(value.trim());
            if (number.signum() >= 0
                    && number.compareTo(ONE_HUNDRED) <= 0
                    && number.stripTrailingZeros().scale() <= 4) {
                return number;
            }
        } catch (NumberFormatException exception) {
            throw percentageError(label);
        }
        throw percentageError(label);
    }

    private static ApiException percentageError(String label) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, label + " must be a number from 0 to 100, with up to 4 decimals");
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

    private static DocumentType documentType(String value) {
        if (value == null) {
            return DocumentType.INVOICE;
        }
        try {
            return DocumentType.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document type: " + value);
        }
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
