package com.invoicespot.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class BillingItem {

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    // Fractions are allowed, such as 1.5 hours.
    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    // What the quantity counts, such as hours; none for plain units.
    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "discount", nullable = false, precision = 7, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;

    // The percentage of tax charged on this line.
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    // Its HSN code (goods) or SAC code (services), for GST.
    @Column(name = "hsn_sac", length = 8)
    private String hsnSac;
}
