package com.invoicespot.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// The tax a document charges at one rate: what was taxable at that rate, and
// the tax on it, which India's GST splits into CGST and SGST or IGST.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class TaxSummary {

    @Column(name = "rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(name = "taxable", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxable;

    @Column(name = "tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal tax;

    @Column(name = "cgst", nullable = false, precision = 19, scale = 2)
    private BigDecimal cgst = BigDecimal.ZERO.setScale(2);

    @Column(name = "sgst", nullable = false, precision = 19, scale = 2)
    private BigDecimal sgst = BigDecimal.ZERO.setScale(2);

    @Column(name = "igst", nullable = false, precision = 19, scale = 2)
    private BigDecimal igst = BigDecimal.ZERO.setScale(2);

    public TaxSummary(BigDecimal rate, BigDecimal taxable, BigDecimal tax) {
        this.rate = rate;
        this.taxable = taxable;
        this.tax = tax;
    }

    public TaxSummary(
            BigDecimal rate, BigDecimal taxable, BigDecimal tax, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        this(rate, taxable, tax);
        this.cgst = cgst;
        this.sgst = sgst;
        this.igst = igst;
    }
}
