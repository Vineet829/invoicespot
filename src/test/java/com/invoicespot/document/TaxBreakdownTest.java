package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.tax.SupplyType;
import com.invoicespot.tax.TaxRegime;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// How India's GST splits each rate's tax: halved into CGST and SGST within
// the state, IGST at the full rate otherwise, kept but not billed under
// reverse charge, and nothing on an export under a LUT.
class TaxBreakdownTest {

    private static Document gst(SupplyType supply, boolean reverseCharge) {
        Document document = new Document();
        document.setDocumentType(DocumentType.INVOICE);
        document.setTaxRegime(TaxRegime.GST_INDIA);
        document.setSupplyType(supply);
        document.setReverseCharge(reverseCharge);
        document.getBillingItems().add(line("101", "5"));
        document.getBillingItems().add(line("1000", "18"));
        return document;
    }

    private static BillingItem line(String price, String rate) {
        BillingItem item = new BillingItem();
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(BigDecimal.ONE);
        item.setDiscount(BigDecimal.ZERO);
        item.setTaxRate(new BigDecimal(rate));
        return item;
    }

    private static TaxSummary at(Document document, int index) {
        return document.getTaxSummaries().get(index);
    }

    @Test
    @DisplayName("within the state each half is rounded on its own, so CGST equals SGST and tax can gain a paisa")
    void intraState() {
        Document document = gst(SupplyType.INTRA, false);
        DocumentTotals.apply(document);

        assertThat(at(document, 0).getCgst()).isEqualByComparingTo("2.53");
        assertThat(at(document, 0).getSgst()).isEqualByComparingTo("2.53");
        assertThat(at(document, 0).getTax()).isEqualByComparingTo("5.06");
        assertThat(at(document, 0).getIgst()).isEqualByComparingTo("0");
        assertThat(at(document, 1).getCgst()).isEqualByComparingTo("90.00");
        assertThat(document.getSalesTax()).isEqualByComparingTo("185.06");
        assertThat(document.getTotal()).isEqualByComparingTo("1286.06");
    }

    @Test
    @DisplayName("to another state, or an export with IGST, the whole rate is IGST")
    void interState() {
        for (SupplyType supply : new SupplyType[] {SupplyType.INTER, SupplyType.EXPORT_IGST}) {
            Document document = gst(supply, false);
            DocumentTotals.apply(document);

            assertThat(at(document, 0).getIgst()).isEqualByComparingTo("5.05");
            assertThat(at(document, 0).getCgst()).isEqualByComparingTo("0");
            assertThat(document.getTotal()).isEqualByComparingTo("1286.05");
        }
    }

    @Test
    @DisplayName("under reverse charge the tax is kept per rate for the recipient to pay, and not billed")
    void reverseCharge() {
        Document document = gst(SupplyType.INTER, true);
        DocumentTotals.apply(document);

        assertThat(at(document, 1).getIgst()).isEqualByComparingTo("180.00");
        assertThat(document.getSalesTax()).isEqualByComparingTo("0");
        assertThat(document.getTotal()).isEqualByComparingTo("1101.00");
    }

    @Test
    @DisplayName("an export under a LUT is stored at 0% and charges nothing")
    void underLut() {
        Document document = gst(SupplyType.EXPORT_LUT, false);
        GstSupply.zeroRatedExport(document);
        DocumentTotals.apply(document);

        assertThat(document.getBillingItems()).allSatisfy(item -> assertThat(item.getTaxRate()).isZero());
        assertThat(document.getSalesTax()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a standard document isn't split")
    void standard() {
        Document document = gst(SupplyType.INTRA, false);
        document.setTaxRegime(TaxRegime.STANDARD);
        DocumentTotals.apply(document);

        assertThat(at(document, 0).getTax()).isEqualByComparingTo("5.05");
        assertThat(at(document, 0).getCgst()).isEqualByComparingTo("0");
    }
}
