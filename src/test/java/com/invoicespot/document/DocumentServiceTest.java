package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentCustomerDto;
import com.invoicespot.document.dto.UpdateDocumentRequest;
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
    @Mock private DocumentNumbering documentNumbering;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                documentRepository,
                customerRepository,
                userRepository,
                pdfService,
                settingsService,
                documentNumbering,
                CLOCK);
        lenient().when(settingsService.forUser(any())).thenReturn(new BusinessSettings());
    }

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC);

    private void ownsCustomer(long ownerPkid) {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        customer.setCreatedBy(userWithPkid(ownerPkid));
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

    private static CreateDocumentRequest documentRequest(String type) {
        return new CreateDocumentRequest(
                CUSTOMER_ID.toString(),
                new DocumentCustomerDto("Beta", "b@x.com", null, null, null, null, null, "+1"),
                type, null, null, null, null, "USD", null);
    }

    @Test
    @DisplayName("a document can only be created for one of the user's own customers")
    void requiresACustomer() {
        ownsCustomer(99L);

        assertThatThrownBy(() -> service.create(1L, documentRequest("Invoice")))
                .isInstanceOf(ApiException.class)
                .hasMessage("That customer does not exist for the currently logged in user")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a created document gets a generated number and the type/status defaults")
    void createsDocumentWithDefaults() {
        ownsCustomer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPkid(1L)));

        when(documentNumbering.next(1L, DocumentType.INVOICE)).thenReturn("INV-0001");

        service.create(1L, documentRequest(null));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);
        assertThat(saved.getDocumentNumber()).isEqualTo("INV-0001");
        assertThat(saved.getCustomer().getId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getCustomerSnapshot().getName()).isEqualTo("Beta LLC");
        assertThat(saved.getCustomerSnapshot().getEmail()).isEqualTo("billing@beta.test");
        assertThat(saved.getBillingItems()).isEmpty();
    }

    @Test
    @DisplayName("an unknown document type is rejected")
    void rejectsUnknownType() {
        ownsCustomer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPkid(1L)));

        assertThatThrownBy(() -> service.create(1L, documentRequest("Nonsense")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid document type");
    }

    @Test
    @DisplayName("viewing another user's document is forbidden")
    void forbidsForeignDocument() {
        Document document = new Document();
        document.setCreatedBy(userWithPkid(99L));
        UUID id = UUID.randomUUID();
        when(documentRepository.findByExternalId(id)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.getOne(1L, id.toString()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
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

        service.addPayment(1L, id.toString(), new CreatePaymentRequest(null, new BigDecimal("50"), null, "note"));

        assertThat(document.getPayments()).hasSize(1);
        Payment payment = document.getPayments().get(0);
        assertThat(payment.getPaidBy()).isEqualTo("Beta LLC");
        assertThat(payment.getAmountPaid()).isEqualByComparingTo("50");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getDatePaid()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NOT_FULLY_PAID);
        verify(documentRepository).save(document);
    }

    private static CreateDocumentRequest requestWith(String rates, String discount) {
        return new CreateDocumentRequest(
                CUSTOMER_ID.toString(),
                new DocumentCustomerDto("Beta", "b@x.com", null, null, null, null, null, "+1"),
                "Invoice", null, null, null, rates, "USD",
                List.of(new BillingItemDto("Design", new BigDecimal("100"), 1, discount)));
    }

    @Test
    @DisplayName("tax and discount percentages are stored as exact numbers")
    void storesPercentages() {
        ownsCustomer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPkid(1L)));

        service.create(1L, requestWith(" 18.5 ", "12.25"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getRates()).isEqualByComparingTo("18.5");
        assertThat(captor.getValue().getBillingItems().get(0).getDiscount()).isEqualByComparingTo("12.25");
    }

    @Test
    @DisplayName("a percentage that isn't a number from 0 to 100 is refused")
    void refusesBadPercentages() {
        ownsCustomer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPkid(1L)));

        for (String bad : new String[] {"abc", "-1", "100.5", "0.00001"}) {
            assertThatThrownBy(() -> service.create(1L, requestWith(bad, "0")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Tax rate must be a number from 0 to 100, with up to 4 decimals");
            assertThatThrownBy(() -> service.create(1L, requestWith("0", bad)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Discount must be a number from 0 to 100, with up to 4 decimals");
        }
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("the server computes line totals, tax, total and status, whatever the client sends")
    void computesTotals() {
        ownsCustomer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPkid(1L)));

        service.create(1L, requestWith("18", "10"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getSubTotal()).isEqualByComparingTo("90.00");
        assertThat(saved.getSalesTax()).isEqualByComparingTo("16.20");
        assertThat(saved.getTotal()).isEqualByComparingTo("106.20");
        assertThat(saved.getTotalAmountReceived()).isEqualByComparingTo("0");
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);
    }

    private Document paidInvoice(BigDecimal paid) {
        Document document = new Document();
        document.setCreatedBy(userWithPkid(1L));
        BillingItem item = new BillingItem();
        item.setItemName("Design");
        item.setUnitPrice(new BigDecimal("100"));
        item.setQuantity(1);
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
        return new UpdateDocumentRequest(null, null, type, null, null, null, null, null, items);
    }

    @Test
    @DisplayName("an edit can't turn a document with payments into a quotation")
    void refusesQuotationWithPayments() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByExternalId(id)).thenReturn(Optional.of(paidInvoice(new BigDecimal("40"))));

        assertThatThrownBy(() -> service.update(1L, id.toString(), update("Quotation", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("This document has payments, so it can't become a quotation")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("an edit can't bring the total below what was already received")
    void refusesTotalBelowReceived() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByExternalId(id)).thenReturn(Optional.of(paidInvoice(new BigDecimal("80"))));

        assertThatThrownBy(() -> service.update(
                        1L, id.toString(), update(null, List.of(new BillingItemDto("Design", new BigDecimal("50"), 1, "0")))))
                .isInstanceOf(ApiException.class)
                .hasMessage("The new total is less than the 80.00 already received");
        verify(documentRepository, never()).save(any());
    }
}
