package com.invoicespot.user;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select u from User u where u.isEmailVerified = false and u.createdAt < :cutoff")
    List<User> findUnverifiedCreatedBefore(@Param("cutoff") Instant cutoff);

    Optional<User> findByUsername(String username);

    @Query("select u from User u where u.id = :externalId")
    Optional<User> findByExternalId(@Param("externalId") UUID externalId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    // Locked, so two sign-ins can't spend one two-factor code at once: the
    // second waits, then sees it spent.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.pkid = :pkid")
    Optional<User> findForUpdateByPkid(@Param("pkid") Long pkid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :externalId")
    Optional<User> findForUpdateByExternalId(@Param("externalId") UUID externalId);

    // Locked, so two admins removing each other at once can't leave none.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u join u.roles role where role = 'Admin' and u.active = true")
    List<User> lockActiveAdmins();
}
