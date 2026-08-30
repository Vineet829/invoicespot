package com.invoicespot.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class PaymentRecord {

    @Column(name = "paid_by")
    private String paidBy;

    @Column(name = "date_paid")
    private String datePaid;

    @Column(name = "amount_paid")
    private Double amountPaid;

    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "additional_info")
    private String additionalInfo;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
