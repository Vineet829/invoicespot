package com.invoicespot.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class CustomerSnapshot {

    @Column(name = "customer_name")
    private String name;

    @Column(name = "customer_email", length = 254)
    private String email;

    @Column(name = "customer_account_no", length = 30)
    private String accountNo;

    @Column(name = "customer_vat_tin_no")
    private String vatTinNo;

    @Column(name = "customer_address")
    private String address;

    @Column(name = "customer_city")
    private String city;

    @Column(name = "customer_country")
    private String country;

    @Column(name = "customer_phone_number", length = 30)
    private String phoneNumber;
}
