package com.invoicespot.item;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {

    @Query("""
            select i from CatalogItem i
            where i.business.pkid = :businessPkid
              and (lower(i.name) like :pattern escape '!'
                   or lower(coalesce(i.description, '')) like :pattern escape '!')
            """)
    Page<CatalogItem> search(
            @Param("businessPkid") Long businessPkid, @Param("pattern") String pattern, Pageable pageable);

    @Query("select i from CatalogItem i where i.id = :externalId")
    Optional<CatalogItem> findByExternalId(@Param("externalId") UUID externalId);

    boolean existsByBusinessPkidAndNameIgnoreCase(Long businessPkid, String name);

    boolean existsByBusinessPkidAndNameIgnoreCaseAndPkidNot(Long businessPkid, String name, Long pkid);
}
