package com.invoicespot.document;

import com.invoicespot.tax.Apportion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// A GST invoice's tax by HSN or SAC code, within each rate. Each component of
// a rate's tax is apportioned among the codes by their taxable value at that
// component's rate (the lowest code first on a tie, lines without a code
// last), so each rate's rows add up to its tax exactly, as the GST returns
// need.
final class HsnSummary {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    // Codes in order, lines without one last.
    private static final Comparator<String> CODES = Comparator.comparing(String::isEmpty).thenComparing(code -> code);

    record Row(String hsnSac, BigDecimal rate, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {

        BigDecimal tax() {
            return cgst.add(sgst).add(igst);
        }
    }

    private HsnSummary() {}

    static List<Row> of(Document document) {
        List<Row> rows = new ArrayList<>();
        for (TaxSummary group : document.getTaxSummaries()) {
            Map<String, BigDecimal> taxable = new TreeMap<>(CODES);
            for (BillingItem item : document.getBillingItems()) {
                BigDecimal rate = item.getTaxRate() == null ? BigDecimal.ZERO : item.getTaxRate();
                if (rate.compareTo(group.getRate()) == 0) {
                    String code = item.getHsnSac() == null ? "" : item.getHsnSac();
                    taxable.merge(code, DocumentTotals.lineTotal(item), BigDecimal::add);
                }
            }
            BigDecimal half = group.getRate().divide(TWO).movePointLeft(2);
            BigDecimal full = group.getRate().movePointLeft(2);
            Map<String, BigDecimal> cgst = Apportion.of(taxable, half, group.getCgst());
            Map<String, BigDecimal> sgst = Apportion.of(taxable, half, group.getSgst());
            Map<String, BigDecimal> igst = Apportion.of(taxable, full, group.getIgst());
            taxable.forEach((code, value) -> rows.add(new Row(
                    code.isEmpty() ? null : code,
                    group.getRate(),
                    value,
                    cgst.get(code),
                    sgst.get(code),
                    igst.get(code))));
        }
        rows.sort(Comparator.comparing((Row row) -> row.hsnSac() == null ? "" : row.hsnSac(), CODES)
                .thenComparing(Row::rate));
        return rows;
    }
}
