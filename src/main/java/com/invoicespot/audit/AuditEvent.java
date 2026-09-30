package com.invoicespot.audit;

import com.invoicespot.business.Business;
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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

// One entry in the audit log. The email is the account's at the time, so an
// entry still says whose it was after the account is deleted.
@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private AuditEventType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_pkid")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_pkid")
    private User actor;

    // The business an event happened in, when it is about one.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_pkid", updatable = false)
    private Business business;

    @Column(name = "email", length = 254, updatable = false)
    private String email;

    @Column(name = "detail", length = 255, updatable = false)
    private String detail;

    @Column(name = "ip", length = 64, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
