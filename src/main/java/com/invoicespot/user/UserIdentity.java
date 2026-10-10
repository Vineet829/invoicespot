package com.invoicespot.user;

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

// An account at a sign-in provider, such as Google, that signs in to a user.
// The subject is the provider's own lasting ID for it; the email is the one
// it had when it was linked.
@Entity
@Table(name = "user_identities")
@Getter
@Setter
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_pkid", nullable = false, updatable = false)
    private User user;

    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    private String provider;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
