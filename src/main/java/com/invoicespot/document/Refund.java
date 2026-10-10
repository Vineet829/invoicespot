package com.invoicespot.document;

import com.invoicespot.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

// Money paid back to the client on an invoice or receipt.
@Entity
@Table(name = "refunds")
@Getter
@Setter
public class Refund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_pkid", nullable = false)
    private Document document;

    @Column(name = "date_refunded", nullable = false)
    private LocalDate dateRefunded;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "reference")
    private String reference;
}
