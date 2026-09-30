package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.common.BaseEntity;
import com.invoicespot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recurring_schedules")
@Getter
@Setter
public class RecurringSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pkid", nullable = false, updatable = false)
    private Business business;

    // The member who made it, forgotten if their account is deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_pkid", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_pkid", nullable = false, updatable = false)
    private Document template;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_run")
    private LocalDate nextRun;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_runs")
    private Integer maxRuns;

    @Column(name = "runs_done", nullable = false)
    private int runsDone;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "email_automatically", nullable = false)
    private boolean emailAutomatically;

    @Column(name = "paused", nullable = false)
    private boolean paused;
}
