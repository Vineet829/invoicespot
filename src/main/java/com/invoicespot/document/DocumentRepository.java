package com.invoicespot.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"billingItems", "paymentRecords"})
    List<Document> findByCreatedByPkidOrderByCreatedAtDesc(Long createdByPkid);

    @EntityGraph(attributePaths = {"billingItems", "paymentRecords"})
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findByExternalId(@Param("externalId") UUID externalId);
}
