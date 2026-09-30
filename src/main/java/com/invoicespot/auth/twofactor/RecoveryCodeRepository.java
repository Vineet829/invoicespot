package com.invoicespot.auth.twofactor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByUserPkidAndCodeHashAndUsedAtIsNull(Long userPkid, String codeHash);

    long countByUserPkidAndUsedAtIsNull(Long userPkid);

    @Modifying
    @Query("delete from RecoveryCode c where c.user.pkid = :userPkid")
    void deleteAllOf(@Param("userPkid") Long userPkid);
}
