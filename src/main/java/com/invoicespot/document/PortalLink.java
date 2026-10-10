package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.customer.Customer;
import com.invoicespot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

// A client's link to their portal. Like a share link it is found by its
// token's hash, and kept sealed so it can be shown again.
@Entity
@Table(name = "portal_links")
@Getter
@Setter
public class PortalLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pkid", nullable = false, updatable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_pkid", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "token_sealed", updatable = false)
    private String tokenSealed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_pkid", updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;
}
