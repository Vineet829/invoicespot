package com.invoicespot.report;

import com.invoicespot.document.DocumentLedger;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.report.dto.ItemSalesDetailResponse;
import com.invoicespot.report.dto.OverviewItem;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// What the lines of one currency's issued invoices and receipts dated between
// two days sold, less what the lines of its credit notes took off, before
// tax: the overview's top items, and every item the sales by item view lists.
// An item is its name, whatever its letter case and the spaces around it,
// spelled as it was first used, and lines without a name are one item. As
// each document's lines add up to it before tax, they all add up to the
// income of the same days.
@Repository
class ItemQueries {

    // What lines without a name are called.
    static final String OTHER_LINES = "Other lines";

    // What tells an item apart, for SQL over billing items aliased i: its
    // name in lower case without the spaces around it, or none for lines
    // without a name.
    private static final String ITEM = "lower(nullif(btrim(i.item_name), ''))";

    // The item a name asks for, told apart the same way; no name asks for the
    // lines without one.
    private static final String NAMED = ITEM + " is not distinct from lower(nullif(btrim(cast(:name as text)), ''))";

    // What a line comes to before tax, as DocumentTotals works it out: its
    // quantity at its unit price, less its discount as a percentage, rounded
    // half up to the cent, for SQL over billing items aliased i. Only the
    // discount is divided, so that nothing is rounded before the cent.
    private static final String LINE_NET =
            "round(i.quantity * coalesce(i.unit_price, 0) * (1 - i.discount / 100), 2)";

    // A credit note's lines take theirs off, for SQL over documents aliased d.
    private static final String SIGN = "(case when d.document_type = 'Credit Note' then -1 else 1 end)";

    // The lines of documents aliased d, as billing items aliased i, and those
    // that count.
    private static final String LINES = "documents d join document_billing_items i on i.document_pkid = d.pkid";
    private static final String COUNTED = """
            d.business_pkid = :business and %s and coalesce(d.currency, '') = :currency
              and d.issue_date between :from and :to""".formatted(DocumentLedger.BILLED_DOCUMENT);

    // An item sold, and what tells it apart: its name in lower case, or none
    // for lines without a name.
    record Sold(String key, OverviewItem item) {}

    // Some of an item's lines, and how many there are in all.
    record Lines(List<ItemSalesDetailResponse.Line> lines, long total) {}

    private record Line(ItemSalesDetailResponse.Line line, long lines) {}

    private final JdbcClient jdbc;

    ItemQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Every item sold, largest first and by name when two came to the same.
    List<Sold> sold(long businessPkid, String currency, LocalDate from, LocalDate to) {
        return sold("true", params(businessPkid, currency, from, to, null));
    }

    // The item with the name, if the days sold any of it: the row the list of
    // every item has for it.
    List<Sold> sold(long businessPkid, String currency, LocalDate from, LocalDate to, String name) {
        return sold(NAMED, params(businessPkid, currency, from, to, name));
    }

    private List<Sold> sold(String condition, Map<String, Object> params) {
        return jdbc.sql("""
                        select %1$s as key, (array_agg(btrim(i.item_name) order by d.pkid, i.position))[1] as item,
                               sum(%2$s * i.quantity) as quantity, sum(%2$s * %3$s) as amount,
                               count(distinct d.pkid) as documents
                        from %4$s
                        where %5$s and %6$s
                        group by 1
                        """.formatted(ITEM, SIGN, LINE_NET, LINES, COUNTED, condition))
                .params(params)
                .query((result, rowNumber) -> {
                    String item = result.getString("item");
                    return new Sold(
                            result.getString("key"),
                            new OverviewItem(
                                    item == null || item.isEmpty() ? OTHER_LINES : item,
                                    BillingItemResponse.plain(result.getBigDecimal("quantity")),
                                    result.getBigDecimal("amount"),
                                    result.getLong("documents")));
                })
                .list()
                .stream()
                .sorted(ProfitReport.largestFirst(sold -> sold.item().amount(), sold -> sold.item().item()))
                .toList();
    }

    // Who bought the item with the name, and how much of it, as the overview
    // tells its top clients apart: the client a document is linked to, named
    // as it is now, or for documents linked to none, no id and the name they
    // were saved with. Largest first and by name when two came to the same;
    // clients named alike stay in the order they were added.
    List<ItemSalesDetailResponse.Client> clients(
            long businessPkid, String currency, LocalDate from, LocalDate to, String name) {
        return jdbc.sql("""
                        select c.id as client_id, coalesce(c.name, %1$s) as client,
                               sum(%2$s * i.quantity) as quantity, sum(%2$s * %3$s) as amount
                        from %4$s
                        left join customers c on c.pkid = d.customer_pkid
                        where %5$s and %6$s
                        group by d.customer_pkid, %1$s, c.id, c.name
                        order by d.customer_pkid, %1$s
                        """.formatted(Overview.SAVED_NAME, SIGN, LINE_NET, LINES, COUNTED, NAMED))
                .params(params(businessPkid, currency, from, to, name))
                .query((result, rowNumber) -> new ItemSalesDetailResponse.Client(
                        result.getObject("client_id", UUID.class),
                        result.getString("client"),
                        BillingItemResponse.plain(result.getBigDecimal("quantity")),
                        result.getBigDecimal("amount")))
                .list()
                .stream()
                .sorted(ProfitReport.largestFirst(
                        ItemSalesDetailResponse.Client::amount,
                        client -> Objects.requireNonNullElse(client.client(), "")))
                .toList();
    }

    // The item's lines, the newest document first and each document's in
    // order, as many as asked for, each with what it sold and the client its
    // document names; and how many there are in all.
    Lines lines(long businessPkid, String currency, LocalDate from, LocalDate to, String name, int most) {
        List<Line> rows = jdbc.sql("""
                        select d.id as document_id, d.document_number, d.document_type, d.issue_date,
                               d.customer_name, %1$s * i.quantity as quantity, %1$s * %2$s as amount,
                               count(*) over () as lines
                        from %3$s
                        where %4$s and %5$s
                        order by d.issue_date desc, d.pkid desc, i.position
                        limit :most
                        """.formatted(SIGN, LINE_NET, LINES, COUNTED, NAMED))
                .params(params(businessPkid, currency, from, to, name))
                .param("most", most)
                .query((result, rowNumber) -> new Line(
                        new ItemSalesDetailResponse.Line(
                                result.getObject("document_id", UUID.class),
                                result.getString("document_number"),
                                result.getString("document_type"),
                                result.getObject("issue_date", LocalDate.class),
                                result.getString("customer_name"),
                                BillingItemResponse.plain(result.getBigDecimal("quantity")),
                                result.getBigDecimal("amount")),
                        result.getLong("lines")))
                .list();
        return new Lines(rows.stream().map(Line::line).toList(), rows.isEmpty() ? 0 : rows.get(0).lines());
    }

    // The business, the currency, the days, and the item's name, which may be
    // none.
    private static Map<String, Object> params(
            long businessPkid, String currency, LocalDate from, LocalDate to, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("business", businessPkid);
        params.put("currency", currency);
        params.put("from", from);
        params.put("to", to);
        params.put("name", name);
        return params;
    }
}
