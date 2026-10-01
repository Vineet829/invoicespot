package com.invoicespot.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentClaimRepository extends JpaRepository<PaymentClaim, Long> {

    List<PaymentClaim> findByDocumentOrderByCreatedAtDescPkidDesc(Document document);

    List<PaymentClaim> findByDocumentAndStatusOrderByCreatedAtAscPkidAsc(
            Document document, PaymentClaim.Status status);

    long countByDocumentAndStatus(Document document, PaymentClaim.Status status);

    boolean existsByDocumentAndReferenceIgnoreCaseAndStatusIn(
            Document document, String reference, List<PaymentClaim.Status> statuses);

    Optional<PaymentClaim> findByIdAndDocument(UUID id, Document document);

    // Reports waiting in a business, newest first, with their documents.
    @Query("""
            select c from PaymentClaim c join fetch c.document
            where c.business.pkid = :businessPkid and c.status = :status
            order by c.createdAt desc, c.pkid desc
            """)
    List<PaymentClaim> findWaiting(
            @Param("businessPkid") Long businessPkid,
            @Param("status") PaymentClaim.Status status,
            Pageable pageable);
}
