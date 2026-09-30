package com.invoicespot.settings;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Long> {

    Optional<BusinessSettings> findByBusinessPkid(Long businessPkid);

    @EntityGraph(attributePaths = {"business"})
    List<BusinessSettings> findByRemindersEnabledTrue();

    @Modifying
    @Query(
            value = "insert into business_settings (id, business_pkid) values (gen_random_uuid(), :businessPkid)"
                    + " on conflict (business_pkid) do nothing",
            nativeQuery = true)
    void insertDefaultsIfMissing(@Param("businessPkid") Long businessPkid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BusinessSettings s where s.business.pkid = :businessPkid")
    Optional<BusinessSettings> findForUpdateByBusinessPkid(@Param("businessPkid") Long businessPkid);
}
