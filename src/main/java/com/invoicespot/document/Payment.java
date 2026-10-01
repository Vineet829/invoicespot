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

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_pkid", nullable = false)
    private Document document;

    @Column(name = "paid_by")
    private String paidBy;

    @Column(name = "date_paid", nullable = false)
    private LocalDate datePaid;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    // Tax the client withheld from this payment and paid in for the business.
    @Column(name = "tds_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "additional_info")
    private String additionalInfo;
}
