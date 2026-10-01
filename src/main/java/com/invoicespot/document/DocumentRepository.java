package com.invoicespot.document;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
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
    List<Document> findByBusinessPkidOrderByCreatedAtDesc(Long businessPkid);

    @EntityGraph(attributePaths = {"customer"})
    Page<Document> findByBusinessPkid(Long businessPkid, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "billingItems"})
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findByExternalId(@Param("externalId") UUID externalId);

    @EntityGraph(attributePaths = {"payments"})
    @Query("""
            select d from Document d
            where d.customer.pkid = :customerPkid and d.documentType = :type
              and d.issuedAt is not null and d.voidedAt is null
            order by d.issueDate, d.createdAt, d.pkid
            """)
    List<Document> findForStatement(@Param("customerPkid") Long customerPkid, @Param("type") DocumentType type);

    List<Document> findBySourceOrderByCreatedAtAscPkidAsc(Document source);

    // A business's issued documents of some types dated in a period, made in
    // India GST mode.
    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.documentType in :types
              and d.issuedAt is not null and d.voidedAt is null
              and d.issueDate between :from and :to
              and d.taxRegime = com.invoicespot.tax.TaxRegime.GST_INDIA and d.supplyType is not null
            order by d.issueDate, d.documentNumber, d.pkid
            """)
    List<Document> findGstDatedIn(
            @Param("businessPkid") Long businessPkid,
            @Param("types") Collection<DocumentType> types,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // How many of them were made outside GST mode.
    @Query("""
            select count(d) from Document d
            where d.business.pkid = :businessPkid and d.documentType in :types
              and d.issuedAt is not null and d.voidedAt is null
              and d.issueDate between :from and :to
              and (d.taxRegime <> com.invoicespot.tax.TaxRegime.GST_INDIA or d.supplyType is null)
            """)
    long countOutsideGstDatedIn(
            @Param("businessPkid") Long businessPkid,
            @Param("types") Collection<DocumentType> types,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // Unpaid issued invoices due before a moment that nobody was yet told about.
    @EntityGraph(attributePaths = {"business"})
    @Query("""
            select d from Document d
            where d.documentType = :invoice and d.issuedAt is not null and d.voidedAt is null
              and d.status <> :paid
              and d.dueDate < :before and d.overdueAlertedAt is null
            """)
    List<Document> findUnalertedOverdue(
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("before") Instant before);

    // Unpaid issued invoices that may still get an automatic reminder.
    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.documentType = :invoice
              and d.issuedAt is not null and d.voidedAt is null
              and d.status <> :paid and d.dueDate is not null and d.remindersSent < :most
            """)
    List<Document> findRemindable(
            @Param("businessPkid") Long businessPkid,
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("most") int most);

    Optional<Document> findFirstBySourceAndDocumentType(Document source, DocumentType documentType);

    boolean existsByBusinessPkidAndDocumentNumber(Long businessPkid, String documentNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findForUpdateByExternalId(@Param("externalId") UUID externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.pkid = :pkid")
    Optional<Document> findForUpdateByPkid(@Param("pkid") Long pkid);

    // The document a credit note credits, read without loading the note, so
    // that the invoice can be locked first.
    @Query(value = "select credited_pkid from documents where id = :externalId", nativeQuery = true)
    Optional<Long> findCreditedPkid(@Param("externalId") UUID externalId);
}
