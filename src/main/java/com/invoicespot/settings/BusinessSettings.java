package com.invoicespot.settings;

import com.invoicespot.common.BaseEntity;
import com.invoicespot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "business_settings")
@Getter
@Setter
public class BusinessSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_pkid", nullable = false, unique = true)
    private User user;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "USD";

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 7;

    @Column(name = "default_tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal defaultTaxRate = BigDecimal.ZERO;

    @Column(name = "tax_label", nullable = false, length = 20)
    private String taxLabel = "Tax";

    @Column(name = "default_notes", columnDefinition = "text")
    private String defaultNotes;

    @Column(name = "default_terms", columnDefinition = "text")
    private String defaultTerms;

    @Column(name = "email_message", columnDefinition = "text")
    private String emailMessage;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "UTC";

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "INV-";

    @Column(name = "quotation_prefix", nullable = false, length = 20)
    private String quotationPrefix = "QUO-";

    @Column(name = "receipt_prefix", nullable = false, length = 20)
    private String receiptPrefix = "REC-";

    @Column(name = "next_invoice_number", nullable = false)
    private int nextInvoiceNumber = 1;

    @Column(name = "next_quotation_number", nullable = false)
    private int nextQuotationNumber = 1;

    @Column(name = "next_receipt_number", nullable = false)
    private int nextReceiptNumber = 1;
}
