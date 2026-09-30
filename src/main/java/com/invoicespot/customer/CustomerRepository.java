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
    List<Customer> findByBusinessPkidOrderByCreatedAtDesc(Long businessPkid);

    @EntityGraph(attributePaths = {"createdBy"})
    @Query("select c from Customer c where c.id = :externalId")
    Optional<Customer> findByExternalId(@Param("externalId") UUID externalId);

    boolean existsByBusinessPkidAndEmailIgnoreCase(Long businessPkid, String email);

    boolean existsByBusinessPkidAndEmailIgnoreCaseAndPkidNot(Long businessPkid, String email, Long pkid);

    boolean existsByBusinessPkid(Long businessPkid);

    Optional<Customer> findByBusinessPkidAndAccountNo(Long businessPkid, String accountNo);
}
