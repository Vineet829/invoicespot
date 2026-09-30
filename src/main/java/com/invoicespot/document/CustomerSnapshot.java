package com.invoicespot.document;

import com.invoicespot.customer.Customer;
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

    // The client's details as they are now.
    static CustomerSnapshot of(Customer customer) {
        CustomerSnapshot snapshot = new CustomerSnapshot();
        snapshot.setName(customer.getName());
        snapshot.setEmail(customer.getEmail());
        snapshot.setAccountNo(customer.getAccountNo());
        snapshot.setVatTinNo(customer.getVatTinNo());
        snapshot.setAddress(customer.getAddress());
        snapshot.setCity(customer.getCity());
        snapshot.setCountry(customer.getCountry());
        snapshot.setPhoneNumber(customer.getPhoneNumber());
        return snapshot;
    }
}
