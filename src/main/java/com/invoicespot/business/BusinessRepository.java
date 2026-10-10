package com.invoicespot.business;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    @Query("select b from Business b where b.id = :externalId")
    Optional<Business> findByExternalId(@Param("externalId") UUID externalId);

    // The businesses an account owns that others work in too.
    @Query("""
            select owner.business.name from BusinessMember owner
            where owner.user.pkid = :userPkid and owner.role = com.invoicespot.business.BusinessRole.OWNER
              and exists (select other.pkid from BusinessMember other
                          where other.business = owner.business and other.user.pkid <> :userPkid)
            order by owner.business.name
            """)
    List<String> findSharedNamesOwnedBy(@Param("userPkid") Long userPkid);

    // Locked, so changes to one business take turns.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Business b where b.pkid = :pkid")
    Optional<Business> findForUpdate(@Param("pkid") Long pkid);

    @Query("""
            select m.business from BusinessMember m
            where m.user.pkid = :userPkid and m.role = com.invoicespot.business.BusinessRole.OWNER
            order by m.joinedAt, m.pkid
            """)
    List<Business> findOwnedBy(@Param("userPkid") Long userPkid);
}
