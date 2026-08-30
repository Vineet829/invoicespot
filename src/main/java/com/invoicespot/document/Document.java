package com.invoicespot.document;

import com.invoicespot.common.BaseEntity;
import com.invoicespot.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_pkid", nullable = false)
    private User createdBy;

    @Embedded
    private CustomerSnapshot customer;

    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType = DocumentType.INVOICE;

    @Column(name = "document_number", length = 60)
    private String documentNumber;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "additional_info", columnDefinition = "text")
    private String additionalInfo;

    @Column(name = "terms_conditions", columnDefinition = "text")
    private String termsConditions;

    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.NOT_PAID;

    @Column(name = "sub_total")
    private Double subTotal;

    @Column(name = "sales_tax")
    private Double salesTax;

    @Column(name = "rates")
    private String rates;

    @Column(name = "total")
    private Double total;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "total_amount_received")
    private Double totalAmountReceived;

    @ElementCollection
    @CollectionTable(name = "document_billing_items", joinColumns = @JoinColumn(name = "document_pkid"))
    @OrderColumn(name = "position")
    private List<BillingItem> billingItems = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "document_payment_records", joinColumns = @JoinColumn(name = "document_pkid"))
    @OrderColumn(name = "position")
    private List<PaymentRecord> paymentRecords = new ArrayList<>();
}
