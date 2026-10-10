package com.invoicespot.document;

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

@Entity
@Table(name = "share_links")
@Getter
@Setter
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_pkid", nullable = false, updatable = false)
    private Document document;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    // The token sealed with the app's key, so the link can be shown again;
    // none on links made before that, or without a key.
    @Column(name = "token_sealed", updatable = false)
    private String tokenSealed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;
}
