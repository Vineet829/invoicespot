package com.invoicespot.document;

import com.invoicespot.business.Business;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

// The business as a document went out: its name, address, contact details,
// logo and tax ID. Changing the business's details later never changes a
// document already made.
@Embeddable
@Getter
@Setter
public class SellerSnapshot {

    @Column(name = "seller_name", length = 255)
    private String name;

    @Column(name = "seller_legal_name", length = 255)
    private String legalName;

    @Column(name = "seller_email", length = 254)
    private String email;

    @Column(name = "seller_phone", length = 30)
    private String phoneNumber;

    @Column(name = "seller_address", length = 255)
    private String address;

    @Column(name = "seller_city", length = 255)
    private String city;

    @Column(name = "seller_state", length = 255)
    private String state;

    @Column(name = "seller_postal_code", length = 30)
    private String postalCode;

    @Column(name = "seller_country", length = 255)
    private String country;

    @Column(name = "seller_logo", length = 500)
    private String logo;

    @Column(name = "seller_tax_id", length = 50)
    private String taxId;

    public static SellerSnapshot of(Business business) {
        SellerSnapshot seller = new SellerSnapshot();
        seller.setName(business.getName());
        seller.setLegalName(business.getLegalName());
        seller.setEmail(business.getEmail());
        seller.setPhoneNumber(business.getPhoneNumber());
        seller.setAddress(business.getAddress());
        seller.setCity(business.getCity());
        seller.setState(business.getState());
        seller.setPostalCode(business.getPostalCode());
        seller.setCountry(business.getCountry());
        seller.setLogo(business.getLogo());
        seller.setTaxId(business.getTaxId());
        return seller;
    }
}
