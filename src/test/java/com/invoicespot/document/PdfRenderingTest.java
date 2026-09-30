package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.business.Business;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Every kind of PDF rendered for real, and read back as text: the pieces its
// template is built from come together into a PDF that says what it should.
class PdfRenderingTest {

    @TempDir
    Path uploads;

    private PdfService service;
    private Business seller;

    @BeforeEach
    void setUp() throws IOException {
        service = PdfFixtures.service(uploads);
        seller = PdfFixtures.seller(uploads);
    }

    @Test
    @DisplayName("an invoice names both parties and shows its lines, totals, notes and page number")
    void invoice() {
        String text = PdfText.of(service.render(PdfFixtures.invoice(), seller, PdfFixtures.settings()));

        assertThat(text).contains(
                "Ada Analytics Private Limited",
                "Invoice",
                "INV-0042",
                "Beta & <Sons> LLP",
                "Prototype & <review>",
                "GST (18%)",
                "Balance due",
                "INR 1,00,000.00",
                "Late payments carry 1.5% interest a month.",
                "Page 1 of 1");
    }

    @Test
    @DisplayName("PDFs print in the embedded Noto Sans, so the rupee sign and accented names show")
    void notoSans() throws IOException {
        for (String font : List.of("fonts/NotoSans-Regular.ttf", "fonts/NotoSans-Bold.ttf")) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(font)) {
                assertThat(new TTFParser().parse(in).getUnicodeCmapLookup().getGlyphId(0x20B9)).as(font).isPositive();
            }
        }
        Document invoice = PdfFixtures.invoice();
        invoice.setAdditionalInfo("Pay ₹1,00,000 to Zoë Sánchez-Dvořák.");

        byte[] pdf = service.render(invoice, seller, PdfFixtures.settings());

        assertThat(PdfText.of(pdf)).contains("Pay ₹1,00,000 to Zoë Sánchez-Dvořák.");
        assertThat(fontNames(pdf)).isNotEmpty().allMatch(name -> name.contains("NotoSans"));
    }

    private static List<String> fontNames(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            Set<String> names = new TreeSet<>();
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (COSName name : resources.getFontNames()) {
                    names.add(resources.getFont(name).getName());
                }
            }
            return List.copyOf(names);
        }
    }

    @Test
    @DisplayName("a draft is marked on every page, with no number or payment status, and is dated on issue")
    void draft() {
        Document draft = PdfFixtures.draft();
        for (int line = 2; line <= 40; line++) {
            draft.getBillingItems().add(PdfFixtures.line("Workshop day " + line, "1000", 1, "0"));
        }

        List<String> pages = PdfText.pages(service.render(draft, seller, PdfFixtures.settings()));

        assertThat(pages).hasSizeGreaterThan(1).allSatisfy(page -> assertThat(page).contains("DRAFT"));
        assertThat(pages.get(0)).contains("Invoice", "On issue", "Ada Analytics").doesNotContain("Unpaid", "INV-");
        assertThat(String.join("", pages)).contains("GST (18%)");
    }

    @Test
    @DisplayName("a void invoice keeps its number, is marked void, and says when and why")
    void voided() {
        String text = PdfText.of(service.render(PdfFixtures.voided(), seller, PdfFixtures.settings()))
                .replaceAll("\\s+", " ");

        assertThat(text).contains(
                "INV-0042", "VOID", "Voided on Oct 3, 2026: Sent to the wrong client & <branch>", "Balance due INR 0.00");
        assertThat(text).doesNotContain("Unpaid");
    }

    @Test
    @DisplayName("a quotation shows its total and when it is valid until")
    void quotation() {
        String text = PdfText.of(service.render(PdfFixtures.quotation(), seller, PdfFixtures.settings()));

        assertThat(text).contains("Quotation", "QUO-0007", "Valid until", "Oct 30, 2026", "USD 3,504.60");
        assertThat(text).doesNotContain("Balance due");
    }

    @Test
    @DisplayName("a payment receipt shows the amount received and the balance left")
    void paymentReceipt() {
        Document invoice = PdfFixtures.invoice();
        String text = PdfText.of(service.renderReceipt(
                invoice, invoice.getPayments().get(0), "INV-0042-P1", new BigDecimal("44550.00"), seller));

        assertThat(text).contains(
                "Payment receipt",
                "INV-0042-P1",
                "INR 44,550.00",
                "Paid by Bank Transfer",
                "Invoice INV-0042 total",
                "Balance after this payment",
                "INR 1,00,000.00");
    }

    @Test
    @DisplayName("a statement shows each currency's ledger, what is overdue and the amount due")
    void statement() {
        String text = PdfText.of(service.renderStatement(
                seller, PdfFixtures.customer(), LocalDate.of(2026, 10, 3), PdfFixtures.accounts()));

        assertThat(text).contains(
                "Statement",
                "Payment for INV-0040 (Bank Transfer)",
                "Overdue",
                "INR 1,00,000.00",
                "Amount due",
                "USD 0.00");
    }
}
