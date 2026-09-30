package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.common.BaseEntity;
import com.invoicespot.customer.Customer;
import com.invoicespot.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    @JoinColumn(name = "business_pkid", nullable = false, updatable = false)
    private Business business;

    // The member who made it, forgotten if their account is deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_pkid", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_pkid")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_pkid")
    private Document source;

    // A client answers a quotation once: they accept it, or decline it and
    // may say why.
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "reminders_sent", nullable = false)
    private int remindersSent;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    @Column(name = "overdue_alerted_at")
    private Instant overdueAlertedAt;

    @Embedded
    private CustomerSnapshot customerSnapshot;

    // The business as the document went out, and what it called its tax;
    // a draft keeps neither until it is issued.
    @Embedded
    private SellerSnapshot seller;

    @Column(name = "tax_label", length = 20)
    private String taxLabel;

    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType = DocumentType.INVOICE;

    @Column(name = "document_number", length = 60)
    private String documentNumber;

    // The day the document is dated: the day it was issued in its business's
    // time zone, or an earlier day chosen for it. A draft may have none yet.
    @Column(name = "issue_date")
    private LocalDate issueDate;

    // When a draft was issued and numbered; every document made before drafts
    // were possible was issued as it was made.
    @Column(name = "issued_at")
    private Instant issuedAt;

    // An issued document voided instead of deleted keeps its number, and why.
    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "additional_info", columnDefinition = "text")
    private String additionalInfo;

    @Column(name = "terms_conditions", columnDefinition = "text")
    private String termsConditions;

    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.NOT_PAID;

    @Column(name = "sub_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal subTotal = BigDecimal.ZERO;

    @Column(name = "sales_tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal salesTax = BigDecimal.ZERO;

    @Column(name = "rates", nullable = false, precision = 7, scale = 4)
    private BigDecimal rates = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "total_amount_received", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmountReceived = BigDecimal.ZERO;

    @ElementCollection
    @CollectionTable(name = "document_billing_items", joinColumns = @JoinColumn(name = "document_pkid"))
    @OrderColumn(name = "position")
    private List<BillingItem> billingItems = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("datePaid ASC, pkid ASC")
    private List<Payment> payments = new ArrayList<>();

    public boolean isDraft() {
        return issuedAt == null;
    }

    public boolean isVoid() {
        return voidedAt != null;
    }

    public DocumentState state() {
        return isVoid() ? DocumentState.VOID : isDraft() ? DocumentState.DRAFT : DocumentState.ISSUED;
    }

    // An issued quotation nobody answered is no longer open once the last day
    // it is valid has gone by.
    public boolean isExpired(Instant startOfToday) {
        return documentType == DocumentType.QUOTATION
                && state() == DocumentState.ISSUED
                && acceptedAt == null
                && declinedAt == null
                && dueDate != null
                && dueDate.isBefore(startOfToday);
    }
}
