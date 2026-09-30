package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentCustomerDto;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
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
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documentRepository, customerRepository, userRepository, pdfService);
    }

    private static User userWithPkid(long pkid) {
        User user = new User();
        user.setPkid(pkid);
        user.setId(UUID.randomUUID());
        return user;
    }

    private static CreateDocumentRequest documentRequest(String type) {
        return new CreateDocumentRequest(
                new DocumentCustomerDto("Beta", "b@x.com", null, null, null, null, null, "+1"),
                type, null, null, null, null, 100.0, 18.0, null, 118.0, "USD", null, null);
    }

    @Test
    @DisplayName("creating a document requires the user to have at least one customer")
    void requiresACustomer() {
        when(customerRepository.existsByCreatedByPkid(1L)).thenReturn(false);

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
        when(customerRepository.existsByCreatedByPkid(1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPkid(1L)));

        service.create(1L, documentRequest(null));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);
        assertThat(saved.getDocumentNumber()).matches("\\d{4}-[A-Za-z]+-[0-9A-F]{6}");
        assertThat(saved.getBillingItems()).isEmpty();
    }

    @Test
    @DisplayName("an unknown document type is rejected")
    void rejectsUnknownType() {
        when(customerRepository.existsByCreatedByPkid(1L)).thenReturn(true);
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
        Document document = new Document();
        document.setCreatedBy(userWithPkid(1L));
        CustomerSnapshot snapshot = new CustomerSnapshot();
        snapshot.setName("Beta LLC");
        document.setCustomer(snapshot);
        UUID id = UUID.randomUUID();
        when(documentRepository.findByExternalId(id)).thenReturn(Optional.of(document));

        service.addPayment(1L, id.toString(), new CreatePaymentRequest("2026-09-02", 50.0, null, "note"));

        assertThat(document.getPaymentRecords()).hasSize(1);
        PaymentRecord record = document.getPaymentRecords().get(0);
        assertThat(record.getPaidBy()).isEqualTo("Beta LLC");
        assertThat(record.getAmountPaid()).isEqualTo(50.0);
        assertThat(record.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        verify(documentRepository).save(document);
    }
}
