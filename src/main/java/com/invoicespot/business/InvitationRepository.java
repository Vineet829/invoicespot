package com.invoicespot.business;

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

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    @EntityGraph(attributePaths = "invitedBy")
    @Query("""
            select i from Invitation i
            where i.business.pkid = :businessPkid and i.acceptedAt is null and i.revokedAt is null
            order by i.createdAt desc, i.pkid desc
            """)
    List<Invitation> findWaiting(@Param("businessPkid") Long businessPkid);

    @Query("""
            select i from Invitation i
            where i.business.pkid = :businessPkid and lower(i.email) = lower(:email)
              and i.acceptedAt is null and i.revokedAt is null
            """)
    Optional<Invitation> findWaitingFor(@Param("businessPkid") Long businessPkid, @Param("email") String email);

    @Query("select i from Invitation i where i.business.pkid = :businessPkid and i.id = :id")
    Optional<Invitation> findInBusiness(@Param("businessPkid") Long businessPkid, @Param("id") UUID id);

    @EntityGraph(attributePaths = {"business", "invitedBy"})
    Optional<Invitation> findByTokenHash(String tokenHash);

    // Locked, so an invitation is accepted once.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :tokenHash")
    Optional<Invitation> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    // Invitations accepted, revoked or expired a while ago.
    @Modifying
    @Query("""
            delete from Invitation i
            where i.acceptedAt < :before or i.revokedAt < :before
               or (i.acceptedAt is null and i.revokedAt is null and i.expiresAt < :before)
            """)
    int deleteFinished(@Param("before") Instant before);
}
