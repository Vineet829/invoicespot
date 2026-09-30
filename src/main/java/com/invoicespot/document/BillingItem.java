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

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "discount", nullable = false, precision = 7, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;
}
