package com.invoicespot.business;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessMemberRepository extends JpaRepository<BusinessMember, Long> {

    @EntityGraph(attributePaths = "business")
    Optional<BusinessMember> findByBusinessPkidAndUserPkid(Long businessPkid, Long userPkid);

    @EntityGraph(attributePaths = "business")
    Optional<BusinessMember> findByBusinessIdAndUserPkid(UUID businessId, Long userPkid);

    @EntityGraph(attributePaths = "user")
    Optional<BusinessMember> findByBusinessPkidAndUserId(Long businessPkid, UUID userId);

    @EntityGraph(attributePaths = "user")
    @Query("select m from BusinessMember m where m.business.pkid = :businessPkid")
    List<BusinessMember> findTeam(@Param("businessPkid") Long businessPkid);

    @EntityGraph(attributePaths = "user")
    @Query("select m from BusinessMember m where m.business.pkid = :businessPkid and m.role in :roles")
    List<BusinessMember> findInRoles(
            @Param("businessPkid") Long businessPkid, @Param("roles") Collection<BusinessRole> roles);

    // The business someone last worked in comes first, then the one they
    // joined first.
    @EntityGraph(attributePaths = "business")
    @Query("""
            select m from BusinessMember m where m.user.pkid = :userPkid
            order by m.lastActiveAt desc nulls last, m.joinedAt, m.pkid
            """)
    List<BusinessMember> findByUserInUseOrder(@Param("userPkid") Long userPkid);

    @Modifying
    @Query("update BusinessMember m set m.lastActiveAt = :now where m.pkid = :pkid")
    int markActive(@Param("pkid") Long pkid, @Param("now") Instant now);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select m from BusinessMember m
            where m.business.pkid = :businessPkid and m.role = com.invoicespot.business.BusinessRole.OWNER
            """)
    Optional<BusinessMember> findOwner(@Param("businessPkid") Long businessPkid);
}
