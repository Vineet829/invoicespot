package com.invoicespot.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerifyResetTokenRepository extends JpaRepository<VerifyResetToken, Long> {

    Optional<VerifyResetToken> findByToken(String token);

    void deleteByUserPkid(Long userPkid);
}
