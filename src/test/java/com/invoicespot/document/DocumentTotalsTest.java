package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentTotalsTest {

    private static BillingItem item(String price, int quantity, String discount) {
        BillingItem item = new BillingItem();
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setDiscount(new BigDecimal(discount));
        return item;
    }

    private static Document document(DocumentType type, String rate, BillingItem... items) {
        Document document = new Document();
        document.setDocumentType(type);
        document.setRates(new BigDecimal(rate));
        for (BillingItem item : items) {
            document.getBillingItems().add(item);
        }
        return document;
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
