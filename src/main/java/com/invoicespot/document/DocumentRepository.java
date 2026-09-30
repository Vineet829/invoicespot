package com.invoicespot.document;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Document> findAll(Specification<Document> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"billingItems"})
    List<Document> findByCreatedByPkidOrderByCreatedAtDesc(Long createdByPkid);

    @EntityGraph(attributePaths = {"customer"})
    Page<Document> findByCreatedByPkid(Long createdByPkid, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "billingItems"})
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findByExternalId(@Param("externalId") UUID externalId);

    @EntityGraph(attributePaths = {"payments"})
    @Query("""
            select d from Document d
            where d.customer.pkid = :customerPkid and d.documentType = :type
            order by d.createdAt, d.pkid
            """)
    List<Document> findForStatement(@Param("customerPkid") Long customerPkid, @Param("type") DocumentType type);

    List<Document> findBySourceOrderByCreatedAtAscPkidAsc(Document source);

    // Unpaid invoices due before a moment that nobody was yet told about.
    @EntityGraph(attributePaths = {"createdBy"})
    @Query("""
            select d from Document d
            where d.documentType = :invoice and d.status <> :paid
              and d.dueDate < :before and d.overdueAlertedAt is null
            """)
    List<Document> findUnalertedOverdue(
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("before") Instant before);

    // Unpaid invoices that may still get an automatic reminder.
    @Query("""
            select d from Document d
            where d.createdBy.pkid = :ownerPkid and d.documentType = :invoice and d.status <> :paid
              and d.dueDate is not null and d.remindersSent < :most
            """)
    List<Document> findRemindable(
            @Param("ownerPkid") Long ownerPkid,
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("most") int most);

    Optional<Document> findFirstBySourceAndDocumentType(Document source, DocumentType documentType);

    boolean existsByCreatedByPkidAndDocumentNumber(Long createdByPkid, String documentNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findForUpdateByExternalId(@Param("externalId") UUID externalId);
}
