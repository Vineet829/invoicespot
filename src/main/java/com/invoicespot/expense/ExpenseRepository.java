package com.invoicespot.expense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @Query("select e from Expense e where e.id = :externalId")
    Optional<Expense> findByExternalId(@Param("externalId") UUID externalId);

    // The categories a business has used, in alphabetical order.
    @Query("""
            select e.category from Expense e
            where e.business.pkid = :businessPkid
            group by e.category
            order by lower(e.category), e.category
            """)
    List<String> categories(@Param("businessPkid") Long businessPkid);

    // How the business's other expenses spell a category, in any letter case.
    @Query("""
            select e.category from Expense e
            where e.business.pkid = :businessPkid and lower(e.category) = lower(:category) and e.pkid <> :exceptPkid
            group by e.category
            """)
    List<String> spellingsOf(
            @Param("businessPkid") Long businessPkid,
            @Param("category") String category,
            @Param("exceptPkid") long exceptPkid);
}
