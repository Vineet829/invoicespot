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
    List<Notification> findByUserPkidOrderByCreatedAtDescPkidDesc(Long userPkid, Pageable pageable);

    long countByUserPkidAndReadAtIsNull(Long userPkid);

    Optional<Notification> findByIdAndUserPkid(UUID id, Long userPkid);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.pkid = :userPkid and n.readAt is null")
    int markAllRead(@Param("userPkid") Long userPkid, @Param("now") Instant now);
}
