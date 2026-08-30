package com.invoicespot.customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @EntityGraph(attributePaths = {"createdBy"})
    List<Customer> findByCreatedByPkidOrderByCreatedAtDesc(Long createdByPkid);

    @EntityGraph(attributePaths = {"createdBy"})
    @Query("select c from Customer c where c.id = :externalId")
    Optional<Customer> findByExternalId(@Param("externalId") UUID externalId);

    boolean existsByEmailIgnoreCase(String email);
}
