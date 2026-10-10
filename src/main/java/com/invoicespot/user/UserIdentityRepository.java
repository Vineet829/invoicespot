package com.invoicespot.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);

    boolean existsByUserAndProvider(User user, String provider);
}
