package com.invoicespot.document;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringRunRepository extends JpaRepository<RecurringRun, Long> {

    @EntityGraph(attributePaths = {"document"})
    Optional<RecurringRun> findFirstByScheduleOrderByRunDateDesc(RecurringSchedule schedule);

    boolean existsByScheduleAndRunDate(RecurringSchedule schedule, LocalDate runDate);

    @Modifying
    @Query("update RecurringRun r set r.emailed = true where r.pkid = :pkid")
    void markEmailed(@Param("pkid") Long pkid);
}
