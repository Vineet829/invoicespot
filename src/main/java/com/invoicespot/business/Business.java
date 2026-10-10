package com.invoicespot.business;

import com.invoicespot.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// The business whose details its documents carry.
@Entity
@Table(name = "businesses")
@Getter
@Setter
public class Business extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "postal_code", length = 30)
    private String postalCode;

    @Column(name = "country")
    private String country;

    @Column(name = "logo", length = 500)
    private String logo;

    @Column(name = "tax_id", length = 50)
    private String taxId;
}
