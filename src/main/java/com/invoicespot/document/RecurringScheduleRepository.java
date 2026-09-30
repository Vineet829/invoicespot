package com.invoicespot.document;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringScheduleRepository extends JpaRepository<RecurringSchedule, Long> {

    @EntityGraph(attributePaths = {"template", "createdBy"})
    List<RecurringSchedule> findByBusinessPkidOrderByCreatedAtDescPkidDesc(Long businessPkid);

    @EntityGraph(attributePaths = {"template", "createdBy"})
    @Query("select s from RecurringSchedule s where s.id = :externalId")
    Optional<RecurringSchedule> findByExternalId(@Param("externalId") UUID externalId);

    @EntityGraph(attributePaths = {"template", "createdBy"})
    Optional<RecurringSchedule> findByTemplate(Document template);

    // Schedules with a run on or before the latest possible local date; the
    // caller checks each against its business's own calendar.
    @Query("""
            select s.pkid from RecurringSchedule s
            where s.paused = false and s.nextRun is not null and s.nextRun <= :latest
            """)
    List<Long> findRunnable(@Param("latest") LocalDate latest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecurringSchedule s where s.pkid = :pkid")
    Optional<RecurringSchedule> findForUpdate(@Param("pkid") Long pkid);
}
