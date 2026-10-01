package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentTotalsTest {

    private static BillingItem item(String price, int quantity, String discount) {
        return item(price, String.valueOf(quantity), discount);
    }

    private static BillingItem item(String price, String quantity, String discount) {
        BillingItem item = new BillingItem();
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(new BigDecimal(quantity));
        item.setDiscount(new BigDecimal(discount));
        return item;
    }

    // A document whose lines are all taxed at one rate.
    private static Document document(DocumentType type, String rate, BillingItem... items) {
        Document document = new Document();
        document.setDocumentType(type);
        for (BillingItem item : items) {
            item.setTaxRate(new BigDecimal(rate));
            document.getBillingItems().add(item);
        }
        return document;
    }

    private static BillingItem taxed(BillingItem item, String rate) {
        item.setTaxRate(new BigDecimal(rate));
        return item;
    }

    private static void pay(Document document, String amount) {
        Payment payment = new Payment();
        payment.setAmountPaid(new BigDecimal(amount));
        document.getPayments().add(payment);
    }

    @Test
    @DisplayName("each line is quantity times price less its discount, rounded half up to the cent")
    void lineTotals() {
        assertThat(DocumentTotals.lineTotal(item("19.99", 3, "12.5"))).isEqualByComparingTo("52.47");
        assertThat(DocumentTotals.lineTotal(item("1.005", 1, "0"))).isEqualByComparingTo("1.01");
        assertThat(DocumentTotals.lineTotal(item("150", 2, "10"))).isEqualByComparingTo("270.00");
        assertThat(DocumentTotals.lineTotal(item("0.125", 1000, "0"))).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("a quantity can be a fraction, and its line is still rounded half up to the cent")
    void fractionalQuantities() {
        assertThat(DocumentTotals.lineTotal(item("1000", "1.5", "0"))).isEqualByComparingTo("1500.00");
        assertThat(DocumentTotals.lineTotal(item("33.33", "1.5", "0"))).isEqualByComparingTo("50.00");
        assertThat(DocumentTotals.lineTotal(item("3", "0.3333", "0"))).isEqualByComparingTo("1.00");
        assertThat(DocumentTotals.lineTotal(item("80", "2.25", "10"))).isEqualByComparingTo("162.00");
    }

    @Test
    @DisplayName("with one rate, the totals are exactly what the one-rate formula gave, on any document")
    void oneRateAsBefore() {
        Random random = new Random(20261001);
        for (int round = 0; round < 500; round++) {
            BigDecimal rate = BigDecimal.valueOf(random.nextInt(2001), 2);
            BillingItem[] items = new BillingItem[1 + random.nextInt(6)];
            for (int i = 0; i < items.length; i++) {
                String quantity = random.nextBoolean()
                        ? String.valueOf(random.nextInt(50))
                        : BigDecimal.valueOf(random.nextInt(100000), 4).toPlainString();
                items[i] = item(
                        BigDecimal.valueOf(random.nextInt(1000000), 2).toPlainString(),
                        quantity,
                        BigDecimal.valueOf(random.nextInt(10001), 2).toPlainString());
            }
            Document document = document(DocumentType.INVOICE, rate.toPlainString(), items);

            DocumentTotals.apply(document);

            BigDecimal subTotal = Arrays.stream(items)
                    .map(DocumentTotals::lineTotal)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            BigDecimal tax = subTotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            assertThat(document.getSubTotal()).isEqualByComparingTo(subTotal);
            assertThat(document.getSalesTax()).isEqualByComparingTo(tax);
            assertThat(document.getTotal()).isEqualByComparingTo(subTotal.add(tax));
            assertThat(document.getTaxSummaries()).singleElement()
                    .satisfies(summary -> assertThat(summary.getTaxable()).isEqualByComparingTo(subTotal));
        }
    }

    @Test
    @DisplayName("lines at different rates are taxed per rate, each rounded, and 18 and 18.00 are one rate")
    void ratePerLine() {
        Document document = document(DocumentType.INVOICE, "0");
        document.getBillingItems().add(taxed(item("100.05", 1, "0"), "5"));
        document.getBillingItems().add(taxed(item("200.03", 1, "0"), "18"));
        document.getBillingItems().add(taxed(item("50", 1, "0"), "18.0000"));
        document.getBillingItems().add(taxed(item("99.99", 1, "0"), "5.00"));

        DocumentTotals.apply(document);

        assertThat(document.getTaxSummaries())
                .extracting(summary -> summary.getRate().toPlainString() + " " + summary.getTaxable() + " "
                        + summary.getTax())
                .containsExactly("5.0000 200.04 10.00", "18.0000 250.03 45.01");
        assertThat(document.getSubTotal()).isEqualByComparingTo("450.07");
        assertThat(document.getSalesTax()).isEqualByComparingTo("55.01");
        assertThat(document.getTotal()).isEqualByComparingTo("505.08");
        assertThat(DocumentTotals.singleRate(document)).isNull();
    }

    @Test
    @DisplayName("tax is the rate applied to the rounded subtotal, itself rounded half up")
    void taxAndTotal() {
        Document document = document(DocumentType.INVOICE, "10", item("150", 2, "10"), item("0.05", 1, "0"));

        DocumentTotals.apply(document);

        assertThat(document.getSubTotal()).isEqualByComparingTo("270.05");
        assertThat(document.getSalesTax()).isEqualByComparingTo("27.01");
        assertThat(document.getTotal()).isEqualByComparingTo("297.06");
    }

    @Test
    @DisplayName("an invoice is unpaid, partially paid, then paid as payments add up")
    void invoiceStatus() {
        Document document = document(DocumentType.INVOICE, "0", item("100", 1, "0"));
        DocumentTotals.apply(document);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);

        pay(document, "40");
        DocumentTotals.apply(document);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NOT_FULLY_PAID);
        assertThat(document.getTotalAmountReceived()).isEqualByComparingTo("40");

        pay(document, "60");
        DocumentTotals.apply(document);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PAID);
    }

    @Test
    @DisplayName("a document totalling zero stays unpaid")
    void zeroTotal() {
        Document document = document(DocumentType.INVOICE, "0");

        DocumentTotals.apply(document);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NOT_PAID);
    }

    @Test
    @DisplayName("a receipt is always paid in full")
    void receipt() {
        Document document = document(DocumentType.RECEIPT, "18", item("100", 1, "0"));

        DocumentTotals.apply(document);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PAID);
        assertThat(document.getTotalAmountReceived()).isEqualByComparingTo("118.00");
    }
}
