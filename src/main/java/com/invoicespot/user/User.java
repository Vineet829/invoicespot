package com.invoicespot.user;

import com.invoicespot.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "username", nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "is_email_verified", nullable = false)
    private boolean isEmailVerified = false;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider = "email";

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_pkid"))
    @Column(name = "role", nullable = false, length = 30)
    private Set<String> roles = new HashSet<>(Set.of("User"));

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "two_factor_enabled_at")
    private Instant twoFactorEnabledAt;

    @Column(name = "two_factor_last_step")
    private Long twoFactorLastStep;

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabledAt != null;
    }

    public boolean acceptsTokenIssuedAt(Instant issuedAt) {
        return active
                && (passwordChangedAt == null
                        || issuedAt.getEpochSecond() >= passwordChangedAt.getEpochSecond());
    }

    // How the app names the account to others: the full name, or the
    // username when no name was given.
    public String displayName() {
        String name = (firstName + " " + lastName).trim();
        return name.isEmpty() ? username : name;
    }
}
