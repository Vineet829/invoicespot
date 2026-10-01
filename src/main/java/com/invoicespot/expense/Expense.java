package com.invoicespot.expense;

import com.invoicespot.business.Business;
import com.invoicespot.common.BaseEntity;
import com.invoicespot.document.PaymentMethod;
import com.invoicespot.user.User;
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
@Table(name = "expenses")
@Getter
@Setter
public class Expense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pkid", nullable = false, updatable = false)
    private Business business;

    // The member who recorded it, forgotten if their account is deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_pkid", updatable = false)
    private User recordedBy;

    // The day it was paid.
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "vendor")
    private String vendor;

    @Column(name = "category", nullable = false, length = 60)
    private String category;

    // What it cost before tax.
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);

    // Tax the business claims back, such as GST input tax credit, is not
    // part of what the expense cost it.
    @Column(name = "tax_reclaimable", nullable = false)
    private boolean taxReclaimable;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // "1 {currency} = exchangeRate {exchangeBase}", when it was typed in.
    @Column(name = "exchange_rate", precision = 20, scale = 10)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_base", length = 3)
    private String exchangeBase;

    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    // What was paid, tax included.
    public BigDecimal total() {
        return amount.add(taxAmount);
    }
}
