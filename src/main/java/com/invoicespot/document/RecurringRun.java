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
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recurring_runs")
@Getter
@Setter
public class RecurringRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkid", updatable = false, nullable = false)
    private Long pkid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_pkid", nullable = false, updatable = false)
    private RecurringSchedule schedule;

    @Column(name = "run_date", nullable = false, updatable = false)
    private LocalDate runDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_pkid")
    private Document document;

    @Column(name = "emailed", nullable = false)
    private boolean emailed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
