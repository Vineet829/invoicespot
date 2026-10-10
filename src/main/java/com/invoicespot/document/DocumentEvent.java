package com.invoicespot.document;

import com.invoicespot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "document_events")
@Getter
@Setter
public class DocumentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_pkid", nullable = false, updatable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private DocumentEventType type;

    @Column(name = "detail", length = 500, updatable = false)
    private String detail;

    @Column(name = "payment_pkid")
    private Long paymentPkid;

    // The member who did it. None for the client, an automatic run, an
    // account since deleted, or an event from before actors were kept.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_pkid", updatable = false)
    private User actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
