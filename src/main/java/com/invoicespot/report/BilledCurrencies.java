package com.invoicespot.report;

import com.invoicespot.document.DocumentLedger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// The currencies a business has billed in, as the overview offers them, and
// the one its figures are shown in: the overview's, and those of the views
// its figures open, so that each shows the same currency for the same ask.
@Repository
class BilledCurrencies {

    private final JdbcClient jdbc;

    BilledCurrencies(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // The currencies of the business's issued invoices, receipts and credit
    // notes, its default first; documents without one are under none.
    List<String> list(Long businessPkid, String defaultCurrency) {
        List<String> found = jdbc.sql("""
                        select distinct coalesce(d.currency, '') as currency from documents d
                        where d.business_pkid = :business and %s and d.document_type <> 'Quotation'
                        order by 1
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .query(String.class)
                .list();
        List<String> currencies = new ArrayList<>();
        if (found.contains(defaultCurrency)) {
            currencies.add(defaultCurrency);
        }
        found.stream().filter(currency -> !currency.equals(defaultCurrency)).forEach(currencies::add);
        return currencies;
    }

    // The currency asked for when the business has money in it; else the
    // first it has money in, its default one coming first; else the default.
    static String chosen(List<String> currencies, String requested, String defaultCurrency) {
        return currencies.contains(requested)
                ? requested
                : currencies.isEmpty() ? defaultCurrency : currencies.get(0);
    }
}
