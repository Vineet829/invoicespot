package com.invoicespot.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByGoogleId(String googleId);

    @Query("select u from User u where u.id = :externalId")
    Optional<User> findByExternalId(@Param("externalId") UUID externalId);

    @Query("select u from User u join u.refreshTokens t where t = :token")
    Optional<User> findByRefreshToken(@Param("token") String token);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);
}
