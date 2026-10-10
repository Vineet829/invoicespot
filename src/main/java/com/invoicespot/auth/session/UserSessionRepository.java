package com.invoicespot.auth.session;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // Locked, so two refreshes of one session take turns; only the session's
    // row is locked.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByPreviousHash(String previousHash);

    // The user and their roles come in the same query, for the request's
    // authentication.
    @EntityGraph(attributePaths = {"user", "user.roles"})
    @Query("select s from UserSession s where s.id = :id")
    Optional<UserSession> findByExternalId(@Param("id") UUID id);

    @Query("""
            select s from UserSession s
            where s.user.pkid = :userPkid and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc, s.pkid desc
            """)
    List<UserSession> findActive(@Param("userPkid") Long userPkid, @Param("now") Instant now);

    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now
            where s.user.pkid = :userPkid and s.revokedAt is null
            """)
    int revokeAll(@Param("userPkid") Long userPkid, @Param("now") Instant now);

    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now
            where s.user.pkid = :userPkid and s.revokedAt is null and s.id <> :keep
            """)
    int revokeOthers(@Param("userPkid") Long userPkid, @Param("keep") UUID keep, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.revokedAt is not null or s.expiresAt < :now")
    int deleteEnded(@Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.businessPkid = :businessPkid where s.id = :id")
    int useBusiness(@Param("id") UUID id, @Param("businessPkid") Long businessPkid);

    // Sessions working in a business someone left start again in another.
    @Modifying
    @Query("""
            update UserSession s set s.businessPkid = null
            where s.user.pkid = :userPkid and s.businessPkid = :businessPkid
            """)
    int clearBusiness(@Param("userPkid") Long userPkid, @Param("businessPkid") Long businessPkid);
}
