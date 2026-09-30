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

    Optional<BusinessSettings> findByUserPkid(Long userPkid);

    @EntityGraph(attributePaths = {"user"})
    List<BusinessSettings> findByRemindersEnabledTrue();

    @Modifying
    @Query(
            value = "insert into business_settings (id, user_pkid) values (gen_random_uuid(), :userPkid)"
                    + " on conflict (user_pkid) do nothing",
            nativeQuery = true)
    void insertDefaultsIfMissing(@Param("userPkid") Long userPkid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BusinessSettings s where s.user.pkid = :userPkid")
    Optional<BusinessSettings> findForUpdateByUserPkid(@Param("userPkid") Long userPkid);
}
