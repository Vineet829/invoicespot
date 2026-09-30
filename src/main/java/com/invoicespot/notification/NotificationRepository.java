package com.invoicespot.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = {"document"})
    List<Notification> findByUserPkidAndBusinessPkidOrderByCreatedAtDescPkidDesc(
            Long userPkid, Long businessPkid, Pageable pageable);

    long countByUserPkidAndBusinessPkidAndReadAtIsNull(Long userPkid, Long businessPkid);

    // How much unread news someone has in each of their businesses.
    @Query("""
            select n.business.pkid as businessPkid, count(n) as unread from Notification n
            where n.user.pkid = :userPkid and n.readAt is null
            group by n.business.pkid
            """)
    List<UnreadCount> countUnreadByBusiness(@Param("userPkid") Long userPkid);

    interface UnreadCount {

        Long getBusinessPkid();

        long getUnread();
    }

    // A member leaving takes nothing about the business with them.
    @Modifying
    @Query("delete from Notification n where n.user.pkid = :userPkid and n.business.pkid = :businessPkid")
    int deleteForMember(@Param("userPkid") Long userPkid, @Param("businessPkid") Long businessPkid);

    Optional<Notification> findByIdAndUserPkidAndBusinessPkid(UUID id, Long userPkid, Long businessPkid);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.user.pkid = :userPkid and n.business.pkid = :businessPkid and n.readAt is null
            """)
    int markAllRead(
            @Param("userPkid") Long userPkid, @Param("businessPkid") Long businessPkid, @Param("now") Instant now);
}
