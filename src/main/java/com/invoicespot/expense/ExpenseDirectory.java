package com.invoicespot.expense;

import com.invoicespot.common.SearchText;
import com.invoicespot.expense.dto.ExpenseListTotals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// The expenses list's queries. Its filters are read in SQL, as the overview
// and the reports read expenses, so that the list, its CSV file and what it
// comes to cover the same expenses. Callers read in a transaction that sees
// the expenses as they were at one moment, so the keys read first and the
// expenses then read by them agree.
@Repository
class ExpenseDirectory {

    // Newest first, and the last recorded first within a day, for expenses
    // aliased e.
    private static final String ORDER_BY = "e.expense_date desc, e.pkid desc";

    // The most keys one query for a CSV file reads expenses by, so that a
    // long list never becomes one query with a key for every expense.
    private static final int BATCH = 500;

    // A search's conditions on expenses aliased e, and their values.
    private record Filter(String where, Map<String, Object> params) {}

    private final JdbcClient jdbc;
    private final ExpenseRepository expenseRepository;

    ExpenseDirectory(JdbcClient jdbc, ExpenseRepository expenseRepository) {
        this.jdbc = jdbc;
        this.expenseRepository = expenseRepository;
    }

    // One page of the expenses the search matches, in its order; the totals
    // count them all.
    List<Expense> page(Long businessPkid, ExpenseSearch search) {
        Filter filter = filter(businessPkid, search);
        List<Long> pkids = jdbc.sql("""
                        select e.pkid from expenses e
                        where %s
                        order by %s
                        limit :limit offset :offset
                        """.formatted(filter.where(), ORDER_BY))
                .params(filter.params())
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query(Long.class)
                .list();
        return load(pkids);
    }

    // Every expense the search matches, in its order.
    List<Expense> all(Long businessPkid, ExpenseSearch search) {
        Filter filter = filter(businessPkid, search);
        List<Long> pkids = jdbc.sql("select e.pkid from expenses e where %s order by %s"
                        .formatted(filter.where(), ORDER_BY))
                .params(filter.params())
                .query(Long.class)
                .list();
        List<Expense> expenses = new ArrayList<>();
        for (int start = 0; start < pkids.size(); start += BATCH) {
            expenses.addAll(load(pkids.subList(start, Math.min(start + BATCH, pkids.size()))));
        }
        return expenses;
    }

    // What every expense the search matches comes to in each currency: how
    // many there are, and what was paid for them, tax and all, as their rows'
    // totals add up.
    List<ExpenseListTotals> totals(Long businessPkid, ExpenseSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select e.currency, count(*) as expenses, sum(%s) as spent
                        from expenses e
                        where %s
                        group by 1
                        order by 1
                        """.formatted(ExpenseLedger.PAID, filter.where()))
                .params(filter.params())
                .query((result, rowNumber) -> new ExpenseListTotals(
                        result.getString("currency"),
                        result.getLong("expenses"),
                        result.getBigDecimal("spent").setScale(2)))
                .list();
    }

    // The expenses with these keys, in the same order. Read without one
    // moment's view, an expense deleted since its key was read is left out.
    private List<Expense> load(List<Long> pkids) {
        if (pkids.isEmpty()) {
            return List.of();
        }
        Map<Long, Expense> found = expenseRepository.findAllById(pkids).stream()
                .collect(Collectors.toMap(Expense::getPkid, Function.identity()));
        return pkids.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private static Filter filter(Long businessPkid, ExpenseSearch search) {
        List<String> conditions = new ArrayList<>(List.of("e.business_pkid = :business"));
        Map<String, Object> params = new HashMap<>(Map.of("business", businessPkid));
        if (search.query() != null && !search.query().isBlank()) {
            conditions.add("""
                    (lower(e.vendor) like :pattern escape '!'
                     or lower(e.category) like :pattern escape '!'
                     or lower(e.reference) like :pattern escape '!'
                     or lower(e.notes) like :pattern escape '!')""");
            params.put("pattern", SearchText.containing(search.query()));
        }
        // A category in any letter case. The database lowercases both sides,
        // so a stored spelling always finds itself.
        if (search.category() != null) {
            conditions.add("lower(e.category) = lower(:category)");
            params.put("category", search.category());
        }
        if (search.currency() != null) {
            conditions.add("e.currency = :currency");
            params.put("currency", search.currency());
        }
        if (search.from() != null) {
            conditions.add("e.expense_date >= :from");
            params.put("from", search.from());
        }
        if (search.to() != null) {
            conditions.add("e.expense_date <= :to");
            params.put("to", search.to());
        }
        return new Filter(String.join(" and ", conditions), params);
    }
}
