package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentCustomerDto;
import com.invoicespot.document.dto.DocumentListResponse;
import com.invoicespot.document.dto.DocumentResponse;
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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

    private static final int PAGE_SIZE = 10;

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public DocumentService(
            DocumentRepository documentRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DocumentResponse create(Long ownerPkid, CreateDocumentRequest request) {
        if (!customerRepository.existsByCreatedByPkid(ownerPkid)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "That customer does not exist for the currently logged in user");
        }
        User owner = userRepository
                .findById(ownerPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));

        Document document = new Document();
        document.setCreatedBy(owner);
        document.setCustomer(toSnapshot(request.customer()));
        document.setDocumentType(documentType(request.documentType()));
        document.setStatus(documentStatus(request.status()));
        document.setDueDate(request.dueDate());
        document.setAdditionalInfo(request.additionalInfo());
        document.setTermsConditions(request.termsConditions());
        document.setSubTotal(request.subTotal());
        document.setSalesTax(request.salesTax());
        document.setRates(request.rates());
        document.setTotal(request.total());
        document.setCurrency(request.currency());
        document.setTotalAmountReceived(request.totalAmountReceived());
        document.setBillingItems(toBillingItems(request.billingItems()));
        document.setDocumentNumber(generateDocumentNumber());
        documentRepository.save(document);
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentListResponse listMine(Long ownerPkid, int pageNumber) {
        int page = Math.max(pageNumber, 1);
        Page<Document> result = documentRepository.findByCreatedByPkid(
                ownerPkid,
                PageRequest.of(page - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new DocumentListResponse(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream().map(DocumentResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponse getOne(Long ownerPkid, String id) {
        return DocumentResponse.from(requireOwned(
                ownerPkid, id, "document not found",
                "You are not authorized to view this document. It's not yours"));
    }

    @Transactional
    public DocumentResponse update(Long ownerPkid, String id, UpdateDocumentRequest request) {
        Document document = requireOwned(
                ownerPkid, id, "That document does not exist",
                "You are not authorized to update this document. It's not yours");
        if (request.customer() != null) {
            document.setCustomer(toSnapshot(request.customer()));
        }
        if (request.documentType() != null) {
            document.setDocumentType(documentType(request.documentType()));
        }
        if (request.status() != null) {
            document.setStatus(documentStatus(request.status()));
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
        if (request.subTotal() != null) {
            document.setSubTotal(request.subTotal());
        }
        if (request.salesTax() != null) {
            document.setSalesTax(request.salesTax());
        }
        if (request.rates() != null) {
            document.setRates(request.rates());
        }
        if (request.total() != null) {
            document.setTotal(request.total());
        }
        if (request.currency() != null) {
            document.setCurrency(request.currency());
        }
        if (request.totalAmountReceived() != null) {
            document.setTotalAmountReceived(request.totalAmountReceived());
        }
        if (request.billingItems() != null) {
            document.setBillingItems(toBillingItems(request.billingItems()));
        }
        documentRepository.save(document);
        return DocumentResponse.from(document);
    }

    @Transactional
    public void delete(Long ownerPkid, String id) {
        Document document = requireOwned(
                ownerPkid, id, "That document does not exist!",
                "You are not authorized to delete this document. It's not yours");
        documentRepository.delete(document);
    }

    @Transactional
    public void addPayment(Long ownerPkid, String id, CreatePaymentRequest request) {
        Document document = requireOwned(
                ownerPkid, id, "That document does not exist!",
                "You are not authorized to update this document. It's not yours");
        PaymentRecord payment = new PaymentRecord();
        payment.setPaidBy(document.getCustomer() == null ? null : document.getCustomer().getName());
        payment.setDatePaid(request.datePaid());
        payment.setAmountPaid(request.amountPaid());
        payment.setPaymentMethod(paymentMethod(request.paymentMethod()));
        payment.setAdditionalInfo(request.additionalInfo());
        Instant now = Instant.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        document.getPaymentRecords().add(payment);
        documentRepository.save(document);
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
            item.setDiscount(dto.discount());
            result.add(item);
        }
        return result;
    }

    private String generateDocumentNumber() {
        LocalDate now = LocalDate.now();
        byte[] material = new byte[3];
        random.nextBytes(material);
        String suffix = HexFormat.of().formatHex(material).toUpperCase(Locale.ENGLISH);
        return now.getYear()
                + "-"
                + now.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + "-"
                + suffix;
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

    private static DocumentStatus documentStatus(String value) {
        if (value == null) {
            return DocumentStatus.NOT_PAID;
        }
        try {
            return DocumentStatus.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document status: " + value);
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
