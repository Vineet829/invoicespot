package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

// A payment a client reported on a shared invoice, waiting for someone in the
// business to confirm it as a payment or dismiss it.
@Entity
@Table(name = "payment_claims")
@Getter
@Setter
public class PaymentClaim {

    public enum Method {
        UPI("UPI", PaymentMethod.UPI),
        BANK_TRANSFER("Bank transfer", PaymentMethod.BANK_TRANSFER),
        OTHER("Another way", PaymentMethod.OTHERS);

        private final String label;
        private final PaymentMethod recordedAs;

        Method(String label, PaymentMethod recordedAs) {
            this.label = label;
            this.recordedAs = recordedAs;
        }

        public String label() {
            return label;
        }

        PaymentMethod recordedAs() {
            return recordedAs;
        }
    }

    public enum Status {
        PENDING,
        CONFIRMED,
        DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pkid", nullable = false, updatable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_pkid", nullable = false, updatable = false)
    private Document document;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10, updatable = false)
    private String currency;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20, updatable = false)
    private Method method;

    @Column(name = "reference", length = 60, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_pkid")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_pkid")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "dismiss_reason", length = 200)
    private String dismissReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
