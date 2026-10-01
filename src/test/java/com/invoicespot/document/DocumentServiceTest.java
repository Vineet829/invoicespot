package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentCustomerDto;
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private PdfService pdfService;
    @Mock private SettingsService settingsService;
    @Mock private BusinessRepository businessRepository;
    @Mock private DocumentIssuer documentIssuer;
    @Mock private CreditNoteService creditNotes;
    @Mock private RefundService refunds;
    @Mock private GstSupply gstSupply;
    @Mock private RecurringScheduleRepository scheduleRepository;
    @Mock private DocumentActivity activity;
    @Mock private NotificationService notificationService;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                documentRepository,
                customerRepository,
                userRepository,
                pdfService,
                settingsService,
                businessRepository,
                documentIssuer,
                creditNotes,
                refunds,
                gstSupply,
                scheduleRepository,
                activity,
                notificationService,
                CLOCK);
        lenient().when(settingsService.forBusiness(any())).thenReturn(new BusinessSettings());
        lenient().when(businessRepository.getReferenceById(1L)).thenReturn(businessWithPkid(1L));
    }

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC);

    private void ownsCustomer(long businessPkid) {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        customer.setBusiness(businessWithPkid(businessPkid));
        customer.setName("Beta LLC");
        customer.setEmail("billing@beta.test");
        customer.setAccountNo("CUS-000001");
        when(customerRepository.findByExternalId(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    }

    private static User userWithPkid(long pkid) {
        User user = new User();
        user.setPkid(pkid);
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Business businessWithPkid(long pkid) {
        Business business = new Business();
        business.setPkid(pkid);
        return business;
    }

    private static BusinessAccess access(long businessPkid) {
        return new BusinessAccess(businessPkid, UUID.randomUUID(), BusinessRole.OWNER, userWithPkid(businessPkid));
    }

    private static CreateDocumentRequest documentRequest(String type) {
        return documentRequest(type, null);
    }

    private static CreateDocumentRequest documentRequest(String type, Boolean draft) {
        return new CreateDocumentRequest(
                CUSTOMER_ID.toString(),
                new DocumentCustomerDto("Beta", "b@x.com", null, null, null, null, null, "+1", null, null),
                type, null, null, null, null, "USD", null, null, draft, null, null, null, null, null);
    }

    @Test
    @DisplayName("a document can only be created for one of the business's own customers")
    void requiresACustomer() {
        ownsCustomer(99L);

        assertThatThrownBy(() -> service.create(access(1L), documentRequest("Invoice")))
                .isInstanceOf(ApiException.class)
                .hasMessage("That customer does not exist for the currently logged in user")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a created document takes the type/status defaults and is issued as it is made")
    void createsDocumentWithDefaults() {
        ownsCustomer(1L);
        User member = userWithPkid(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(member);

        service.create(access(1L), documentRequest(null));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);
        assertThat(saved.getCustomer().getId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getCustomerSnapshot().getName()).isEqualTo("Beta LLC");
        assertThat(saved.getCustomerSnapshot().getEmail()).isEqualTo("billing@beta.test");
        assertThat(saved.getBillingItems()).isEmpty();
        verify(documentIssuer).issue(saved, null, member);
    }

    @Test
    @DisplayName("a document saved as a draft isn't issued")
    void savesDraft() {
        ownsCustomer(1L);

        service.create(access(1L), documentRequest(null, true));

        verify(documentRepository).save(any());
        verify(documentIssuer, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("an unknown document type is rejected")
    void rejectsUnknownType() {
        ownsCustomer(1L);
        assertThatThrownBy(() -> service.create(access(1L), documentRequest("Nonsense")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid document type");
    }

    @Test
    @DisplayName("viewing another business's document is forbidden")
    void forbidsForeignDocument() {
        Document document = new Document();
        document.setBusiness(businessWithPkid(99L));
        UUID id = UUID.randomUUID();
        when(documentRepository.findByExternalId(id)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.getOne(access(1L), id.toString()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a draft can't take a payment until it is issued")
    void refusesPaymentOnDraft() {
        Document draft = paidInvoice(null);
        draft.setIssuedAt(null);
        UUID id = UUID.randomUUID();
        when(documentRepository.findForUpdateByExternalId(id)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.addPayment(
                        access(1L), id.toString(), new CreatePaymentRequest(null, new BigDecimal("50"), null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Issue this draft before recording a payment")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(draft.getPayments()).isEmpty();
    }

    @Test
    @DisplayName("recording a payment appends it with the customer name and default method")
    void recordsPayment() {
        Document document = paidInvoice(null);
        CustomerSnapshot snapshot = new CustomerSnapshot();
        snapshot.setName("Beta LLC");
        document.setCustomerSnapshot(snapshot);
        UUID id = UUID.randomUUID();
        when(documentRepository.findForUpdateByExternalId(id)).thenReturn(Optional.of(document));
        User member = userWithPkid(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(member);

        service.addPayment(access(1L), id.toString(), new CreatePaymentRequest(null, new BigDecimal("50"), null, "note", null));

        assertThat(document.getPayments()).hasSize(1);
        Payment payment = document.getPayments().get(0);
        assertThat(payment.getPaidBy()).isEqualTo("Beta LLC");
        assertThat(payment.getAmountPaid()).isEqualByComparingTo("50");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getDatePaid()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NOT_FULLY_PAID);
        verify(documentRepository).flush();
        verify(activity).record(document, DocumentEventType.PAYMENT_RECORDED, "50.00 · Cash", null, member);
    }

    private static CreateDocumentRequest requestWith(String rates, String discount) {
        return new CreateDocumentRequest(
                CUSTOMER_ID.toString(),
                new DocumentCustomerDto("Beta", "b@x.com", null, null, null, null, null, "+1", null, null),
                "Invoice", null, null, null, rates, "USD",
                List.of(new BillingItemDto("Design", new BigDecimal("100"), BigDecimal.ONE, discount, null, null, null)),
                null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("tax and discount percentages are stored as exact numbers")
    void storesPercentages() {
        ownsCustomer(1L);
        service.create(access(1L), requestWith(" 18.5 ", "12.25"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getBillingItems().get(0).getTaxRate()).isEqualByComparingTo("18.5");
        assertThat(captor.getValue().getBillingItems().get(0).getDiscount()).isEqualByComparingTo("12.25");
    }

    @Test
    @DisplayName("a percentage that isn't a number from 0 to 100 is refused")
    void refusesBadPercentages() {
        ownsCustomer(1L);
        for (String bad : new String[] {"abc", "-1", "100.5", "0.00001"}) {
            assertThatThrownBy(() -> service.create(access(1L), requestWith(bad, "0")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Tax rate must be a number from 0 to 100, with up to 4 decimals");
            assertThatThrownBy(() -> service.create(access(1L), requestWith("0", bad)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Discount must be a number from 0 to 100, with up to 4 decimals");
        }
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("the server computes line totals, tax, total and status, whatever the client sends")
    void computesTotals() {
        ownsCustomer(1L);
        service.create(access(1L), requestWith("18", "10"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getSubTotal()).isEqualByComparingTo("90.00");
        assertThat(saved.getSalesTax()).isEqualByComparingTo("16.20");
        assertThat(saved.getTotal()).isEqualByComparingTo("106.20");
        assertThat(saved.getTotalAmountReceived()).isEqualByComparingTo("0");
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);
    }

    // An issued invoice, with a payment when one is given.
    private Document paidInvoice(BigDecimal paid) {
        Document document = new Document();
        document.setBusiness(businessWithPkid(1L));
        document.setCreatedBy(userWithPkid(1L));
        document.setIssuedAt(Instant.parse("2026-09-01T10:00:00Z"));
        BillingItem item = new BillingItem();
        item.setItemName("Design");
        item.setUnitPrice(new BigDecimal("100"));
        item.setQuantity(BigDecimal.ONE);
        document.getBillingItems().add(item);
        if (paid != null) {
            Payment payment = new Payment();
            payment.setAmountPaid(paid);
            document.getPayments().add(payment);
        }
        DocumentTotals.apply(document);
        return document;
    }

    private static UpdateDocumentRequest update(String type, List<BillingItemDto> items) {
        return new UpdateDocumentRequest(
                null, null, type, null, null, null, null, null, items, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("an issued document's type and lines don't change; a void one doesn't change at all")
    void refusesLockedChanges() {
        UUID id = UUID.randomUUID();
        Document issued = paidInvoice(new BigDecimal("40"));
        when(documentRepository.findForUpdateByExternalId(id)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> service.update(access(1L), id.toString(), update("Quotation", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageStartingWith("An issued document keeps its client, lines, tax and issue date")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> service.update(
                        access(1L), id.toString(), update(null, List.of(new BillingItemDto("Design", new BigDecimal("50"), BigDecimal.ONE, "0", null, null, null)))))
                .isInstanceOf(ApiException.class)
                .hasMessageStartingWith("An issued document keeps its client, lines, tax and issue date");
        issued.setVoidedAt(Instant.parse("2026-09-20T10:00:00Z"));
        assertThatThrownBy(() -> service.update(access(1L), id.toString(), update(null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("A void document can't be changed");
        verify(documentRepository, never()).save(any());
    }
}
