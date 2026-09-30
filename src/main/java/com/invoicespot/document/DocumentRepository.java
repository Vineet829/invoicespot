package com.invoicespot.document;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"billingItems"})
    List<Document> findByCreatedByPkidOrderByCreatedAtDesc(Long createdByPkid);

    @EntityGraph(attributePaths = {"customer"})
    Page<Document> findByCreatedByPkid(Long createdByPkid, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "billingItems"})
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findByExternalId(@Param("externalId") UUID externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findForUpdateByExternalId(@Param("externalId") UUID externalId);
}
