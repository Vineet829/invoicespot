package com.invoicespot.document;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    Optional<ShareLink> findByDocumentAndRevokedAtIsNull(Document document);
}
