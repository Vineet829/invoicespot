package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.business.Business;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The HTML of every kind of PDF, pinned in src/test/resources/pdf-golden, so
// any change to a template or to what fills it shows up as a diff to review.
// After an intended change, rewrite the files with -Dgolden.update=true.
class PdfGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/pdf-golden");
    private static final Pattern STYLE = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);
    private static final Pattern CSS_BREAK = Pattern.compile("\\s*([{};])\\s*");

    @TempDir
    Path uploads;

    private PdfService service;
    private Business seller;

    @BeforeEach
    void setUp() throws IOException {
        service = PdfFixtures.service(uploads);
        seller = PdfFixtures.seller(uploads);
    }

    // Issued, so it keeps the seller as it went out.
    private Document kept(Document document) {
        document.setSeller(SellerSnapshot.of(seller));
        return document;
    }

    // One tag, and one style rule or declaration, per line, without the
    // whitespace the templates' own layout adds, so moving markup between
    // files doesn't count as a change.
    static String normalized(String html) {
        String flat = html.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ").trim();
        String styled = STYLE.matcher(flat).replaceAll(style -> Matcher.quoteReplacement("<style>\n"
                + CSS_BREAK.matcher(style.group(1).trim())
                        .replaceAll(mark -> mark.group(1).equals("{") ? " {\n" : mark.group(1) + "\n")
                + "</style>"));
        return styled.replace("><", ">\n<") + "\n";
    }

    private static void assertGolden(String name, String html) throws IOException {
        Path file = GOLDEN.resolve(name + ".html");
        String actual = normalized(html);
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN);
            Files.writeString(file, actual);
            return;
        }
        assertThat(file).as("%s is missing: run the test with -Dgolden.update=true", file).exists();
        assertThat(actual).as("%s changed: review it, then rewrite with -Dgolden.update=true", file)
                .isEqualTo(Files.readString(file));
    }

    @Test
    @DisplayName("an invoice, part paid")
    void invoice() throws IOException {
        assertGolden("invoice", service.html(kept(PdfFixtures.invoice()), seller, PdfFixtures.settings()));
    }

    @Test
    @DisplayName("a quotation")
    void quotation() throws IOException {
        assertGolden("quotation", service.html(kept(PdfFixtures.quotation()), seller, PdfFixtures.settings()));
    }

    @Test
    @DisplayName("a receipt")
    void receipt() throws IOException {
        assertGolden("receipt", service.html(kept(PdfFixtures.receipt()), seller, PdfFixtures.settings()));
    }

    @Test
    @DisplayName("a draft invoice")
    void draft() throws IOException {
        assertGolden("draft-invoice", service.html(PdfFixtures.draft(), seller, PdfFixtures.settings()));
    }

    @Test
    @DisplayName("a void invoice")
    void voided() throws IOException {
        assertGolden("void-invoice", service.html(kept(PdfFixtures.voided()), seller, PdfFixtures.settings()));
    }

    @Test
    @DisplayName("a payment receipt")
    void paymentReceipt() throws IOException {
        Document invoice = kept(PdfFixtures.invoice());
        assertGolden(
                "payment-receipt",
                service.receiptHtml(
                        invoice, invoice.getPayments().get(0), "INV-0042-P1", new BigDecimal("44550.00"), seller));
    }

    @Test
    @DisplayName("a statement in two currencies, one overdue")
    void statement() throws IOException {
        assertGolden(
                "statement",
                service.statementHtml(seller, PdfFixtures.customer(), LocalDate.of(2026, 10, 3), PdfFixtures.accounts()));
    }
}
