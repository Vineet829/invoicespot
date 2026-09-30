package com.invoicespot.document;

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
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.document.dto.UpdatePaymentRequest;
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

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PdfService pdfService;
    private final SettingsService settingsService;
    private final DocumentNumbering documentNumbering;
    private final DocumentActivity activity;
    private final NotificationService notificationService;
    private final Clock clock;

    public DocumentService(
            DocumentRepository documentRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            PdfService pdfService,
            SettingsService settingsService,
            DocumentNumbering documentNumbering,
            DocumentActivity activity,
            NotificationService notificationService,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.pdfService = pdfService;
        this.settingsService = settingsService;
        this.documentNumbering = documentNumbering;
        this.activity = activity;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public DocumentResponse create(Long ownerPkid, CreateDocumentRequest request) {
        Customer customer = findCustomer(ownerPkid, request.customerId(), request.customer())
                .orElseThrow(DocumentService::customerNotFound);
        User owner = userRepository
                .findById(ownerPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));

        Document document = new Document();
        document.setCreatedBy(owner);
        document.setCustomer(customer);
        document.setCustomerSnapshot(snapshotOf(customer));
        document.setDocumentType(documentType(request.documentType()));
        document.setDueDate(request.dueDate());
        document.setAdditionalInfo(request.additionalInfo());
        document.setTermsConditions(request.termsConditions());
        document.setRates(percentage(request.rates(), "Tax rate"));
        document.setCurrency(request.currency());
        document.setBillingItems(toBillingItems(request.billingItems()));
        document.setDocumentNumber(documentNumbering.next(ownerPkid, document.getDocumentType()));
        DocumentTotals.apply(document);
        documentRepository.save(document);
        activity.record(document, DocumentEventType.CREATED, null);
        return respond(document);
    }

    @Transactional(readOnly = true)
    public DocumentListResponse listMine(Long ownerPkid, DocumentSearch search) {
        ZoneId zone = zoneOf(ownerPkid);
        Instant startOfToday = startOfToday(zone);
        Page<Document> result = documentRepository.findAll(
                DocumentSpecifications.matching(ownerPkid, search, startOfToday, zone),
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

    private ZoneId zoneOf(Long ownerPkid) {
        return ZoneId.of(settingsService.forUser(ownerPkid).getTimeZone());
    }

    private Instant startOfToday(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
    }

    private DocumentResponse respond(Document document) {
        return DocumentResponse.detail(
                document,
                startOfToday(zoneOf(document.getCreatedBy().getPkid())),
                documentRepository.findBySourceOrderByCreatedAtAscPkidAsc(document));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getOne(Long ownerPkid, String id) {
        return respond(requireOwned(
                ownerPkid, id, "document not found",
                "You are not authorized to view this document. It's not yours"));
    }

    @Transactional(readOnly = true)
    public DocumentPdf renderPdf(User owner, String id) {
        Document document = requireReadable(owner, id);
        return new DocumentPdf(
                pdfFileName(document),
                pdfService.render(document, owner, settingsService.forUser(owner.getPkid())),
                envelope(document));
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope envelope(User owner, String id) {
        return envelope(requireReadable(owner, id));
    }

    private Document requireReadable(User owner, String id) {
        return requireOwned(
                owner.getPkid(), id, "document not found",
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

    @Transactional
    public DocumentResponse update(Long ownerPkid, String id, UpdateDocumentRequest request) {
        Document document = requireOwned(
                ownerPkid, id, "That document does not exist",
                "You are not authorized to update this document. It's not yours");
        if (request.customerId() != null) {
            Customer customer = findCustomer(ownerPkid, request.customerId(), null)
                    .orElseThrow(DocumentService::customerNotFound);
            document.setCustomer(customer);
            document.setCustomerSnapshot(snapshotOf(customer));
        } else if (request.customer() != null) {
            Optional<Customer> customer = findCustomer(ownerPkid, null, request.customer());
            document.setCustomer(customer.orElse(null));
            document.setCustomerSnapshot(customer.map(DocumentService::snapshotOf)
                    .orElseGet(() -> toSnapshot(request.customer())));
        }
        if (request.documentType() != null) {
            document.setDocumentType(documentType(request.documentType()));
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
        DocumentTotals.apply(document);
        requireConsistentPayments(document);
        if (!DocumentTotals.isOverdue(document, startOfToday(zoneOf(ownerPkid)))) {
            document.setOverdueAlertedAt(null);
        }
        documentRepository.save(document);
        activity.record(document, DocumentEventType.EDITED, null);
        return respond(document);
    }

    @Transactional
    public DocumentResponse duplicate(Long ownerPkid, String id) {
        Document original = requireOwned(
                ownerPkid, id, "That document does not exist",
                "You are not authorized to copy this document. It's not yours");
        Document copy = copyOf(original, original.getDocumentType(), true);
        activity.record(copy, DocumentEventType.COPIED_FROM, original.getDocumentNumber());
        activity.record(original, DocumentEventType.COPIED_TO, copy.getDocumentNumber());
        return respond(copy);
    }

    @Transactional
    public DocumentResponse convert(Long ownerPkid, String id) {
        Document quotation = requireOwned(
                ownerPkid, id, "That document does not exist",
                "You are not authorized to convert this document. It's not yours");
        if (quotation.getDocumentType() != DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a quotation can become an invoice");
        }
        documentRepository.findFirstBySourceAndDocumentType(quotation, DocumentType.INVOICE).ifPresent(invoice -> {
            throw new ApiException(
                    HttpStatus.CONFLICT, "This quotation is already invoice " + invoice.getDocumentNumber());
        });
        Document invoice = copyOf(quotation, DocumentType.INVOICE, true);
        activity.record(invoice, DocumentEventType.CONVERTED_FROM, quotation.getDocumentNumber());
        activity.record(quotation, DocumentEventType.CONVERTED_TO, invoice.getDocumentNumber());
        return respond(invoice);
    }

    // The next invoice of a recurring schedule. It isn't linked to the
    // template as a copy, or a monthly template would list every month.
    Document createRecurring(Document template, RecurringFrequency frequency) {
        Document invoice = copyOf(template, DocumentType.INVOICE, false);
        activity.record(
                invoice, DocumentEventType.RECURRING, template.getDocumentNumber() + " · " + frequency.label());
        return invoice;
    }

    // A new document with the original's client and lines, a fresh number,
    // and a due date from the business's payment terms. Payments stay behind.
    private Document copyOf(Document original, DocumentType type, boolean linkSource) {
        Long ownerPkid = original.getCreatedBy().getPkid();
        BusinessSettings settings = settingsService.forUser(ownerPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        Document copy = new Document();
        copy.setCreatedBy(original.getCreatedBy());
        copy.setSource(linkSource ? original : null);
        copy.setCustomer(original.getCustomer());
        copy.setCustomerSnapshot(original.getCustomer() == null
                ? copyOf(original.getCustomerSnapshot())
                : snapshotOf(original.getCustomer()));
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
        copy.setDocumentNumber(documentNumbering.next(ownerPkid, type));
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

    @Transactional
    public void delete(Long ownerPkid, String id) {
        Document document = requireOwned(
                ownerPkid, id, "That document does not exist!",
                "You are not authorized to delete this document. It's not yours");
        documentRepository.delete(document);
    }

    @Transactional
    public DocumentResponse addPayment(Long ownerPkid, String id, CreatePaymentRequest request) {
        Document document = requireOwnedForPayment(ownerPkid, id);
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
        DocumentTotals.apply(document);
        documentRepository.flush();
        activity.record(
                document,
                DocumentEventType.PAYMENT_RECORDED,
                DocumentActivity.paymentDetail(document, payment),
                payment.getPkid());
        return respond(document);
    }

    @Transactional
    public DocumentResponse updatePayment(
            Long ownerPkid, String id, String paymentId, UpdatePaymentRequest request) {
        Document document = requireOwnedForPayment(ownerPkid, id);
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
        DocumentTotals.apply(document);
        activity.record(
                document,
                DocumentEventType.PAYMENT_EDITED,
                DocumentActivity.paymentDetail(document, payment),
                payment.getPkid());
        return respond(document);
    }

    @Transactional
    public DocumentResponse deletePayment(Long ownerPkid, String id, String paymentId) {
        Document document = requireOwnedForPayment(ownerPkid, id);
        Payment payment = requirePayment(document, paymentId);
        document.getPayments().remove(payment);
        DocumentTotals.apply(document);
        activity.record(
                document, DocumentEventType.PAYMENT_DELETED, DocumentActivity.paymentDetail(document, payment));
        return respond(document);
    }

    @Transactional(readOnly = true)
    public ActivityResponse activity(Long ownerPkid, String id) {
        Document document = requireOwned(
                ownerPkid, id, "document not found",
                "You are not authorized to view this document. It's not yours");
        return new ActivityResponse(
                true,
                activity.history(document).stream()
                        .map(event -> new ActivityEntry(
                                event.getType().name(), event.getDetail(), event.getOccurredAt()))
                        .toList());
    }

    @Transactional
    public void recordEmailed(User owner, String id, String to) {
        activity.record(requireReadable(owner, id), DocumentEventType.EMAILED, "to " + to);
    }

    @Transactional
    public void recordEmailedAutomatically(User owner, String id, String to) {
        activity.record(requireReadable(owner, id), DocumentEventType.EMAILED, "automatically to " + to);
    }

    @Transactional(readOnly = true)
    public DocumentPdf renderReceipt(User owner, String id, String paymentId) {
        Document document = requireReadable(owner, id);
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
                        owner),
                receiptEnvelope(document, number));
    }

    @Transactional(readOnly = true)
    public DocumentEnvelope receiptEnvelope(User owner, String id, String paymentId) {
        Document document = requireReadable(owner, id);
        return receiptEnvelope(document, receiptNumber(document, requirePayment(document, paymentId)));
    }

    @Transactional
    public void recordReceiptEmailed(User owner, String id, String paymentId, String to) {
        Document document = requireReadable(owner, id);
        Payment payment = requirePayment(document, paymentId);
        activity.record(
                document,
                DocumentEventType.RECEIPT_EMAILED,
                receiptNumber(document, payment) + " to " + to,
                payment.getPkid());
    }

    @Transactional(readOnly = true)
    public ReminderFacts reminderFacts(User owner, String id) {
        Document document = requireReadable(owner, id);
        if (document.getDocumentType() != DocumentType.INVOICE || document.getStatus() == DocumentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an unpaid invoice needs a reminder");
        }
        return reminderFacts(document);
    }

    ReminderFacts reminderFacts(Document document) {
        ZoneId zone = zoneOf(document.getCreatedBy().getPkid());
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
    public void recordReminder(User owner, String id, String to, boolean automatic) {
        Document document = requireReadable(owner, id);
        document.setRemindersSent(document.getRemindersSent() + 1);
        document.setLastRemindedAt(Instant.now(clock));
        activity.record(document, DocumentEventType.REMINDED, (automatic ? "automatically " : "") + "to " + to);
        if (automatic) {
            notificationService.notify(
                    document.getCreatedBy(),
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

    private Document requireOwnedForPayment(Long ownerPkid, String id) {
        Document document = externalId(id)
                .flatMap(documentRepository::findForUpdateByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist!"));
        if (!document.getCreatedBy().getPkid().equals(ownerPkid)) {
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

    private static void requireConsistentPayments(Document document) {
        BigDecimal received = DocumentTotals.received(document);
        if (received.signum() == 0 || document.getDocumentType() == DocumentType.RECEIPT) {
            return;
        }
        if (document.getDocumentType() == DocumentType.QUOTATION) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "This document has payments, so it can't become a quotation");
        }
        if (document.getTotal().compareTo(received) < 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "The new total is less than the " + received.toPlainString() + " already received");
        }
    }

    private Document requireOwned(
            Long ownerPkid, String id, String notFoundMessage, String forbiddenMessage) {
        Document document = externalId(id)
                .flatMap(documentRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFoundMessage));
        if (!document.getCreatedBy().getPkid().equals(ownerPkid)) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return document;
    }

    private Optional<Customer> findCustomer(Long ownerPkid, String customerId, DocumentCustomerDto snapshot) {
        if (customerId != null) {
            return externalId(customerId)
                    .flatMap(customerRepository::findByExternalId)
                    .filter(customer -> customer.getCreatedBy().getPkid().equals(ownerPkid));
        }
        if (snapshot != null && snapshot.accountNo() != null && !snapshot.accountNo().isBlank()) {
            return customerRepository.findByCreatedByPkidAndAccountNo(ownerPkid, snapshot.accountNo());
        }
        return Optional.empty();
    }

    private static ApiException customerNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "That customer does not exist for the currently logged in user");
    }

    private static CustomerSnapshot snapshotOf(Customer customer) {
        CustomerSnapshot snapshot = new CustomerSnapshot();
        snapshot.setName(customer.getName());
        snapshot.setEmail(customer.getEmail());
        snapshot.setAccountNo(customer.getAccountNo());
        snapshot.setVatTinNo(customer.getVatTinNo());
        snapshot.setAddress(customer.getAddress());
        snapshot.setCity(customer.getCity());
        snapshot.setCountry(customer.getCountry());
        snapshot.setPhoneNumber(customer.getPhoneNumber());
        return snapshot;
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
