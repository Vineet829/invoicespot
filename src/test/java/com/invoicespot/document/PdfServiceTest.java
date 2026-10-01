package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.business.Business;
import com.invoicespot.config.AppProperties;
import com.invoicespot.customer.Customer;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.tax.SupplyType;
import com.invoicespot.tax.TaxRegime;
import com.invoicespot.upload.UploadService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class PdfServiceTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private static final BusinessSettings SETTINGS = new BusinessSettings();

    @TempDir
    Path uploads;

    private PdfService service;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("a", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site("Invoice Spot", "http://localhost:3000", "http://localhost:8080", "from@invoicespot.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload(uploads.toString()));
        service = new PdfService(engine, new UploadService(properties));
    }

    private static Business seller(String name) {
        Business business = new Business();
        business.setName(name);
        business.setEmail("ada@x.com");
        business.setCity("Pune");
        business.setCountry("India");
        return business;
    }

    private static BillingItem item(String name, String price, int quantity, String discount) {
        BillingItem item = new BillingItem();
        item.setItemName(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(BigDecimal.valueOf(quantity));
        item.setTaxRate(new BigDecimal("18"));
        item.setDiscount(new BigDecimal(discount));
        return item;
    }

    private static Document sampleDocument(DocumentType type) {
        Document document = new Document();
        document.setDocumentType(type);
        document.setStatus(DocumentStatus.NOT_FULLY_PAID);
        CustomerSnapshot customer = new CustomerSnapshot();
        customer.setName("Beta LLC");
        customer.setEmail("beta@x.com");
        customer.setAccountNo("CUS-123456");
        customer.setVatTinNo("0");
        document.setCustomerSnapshot(customer);
        document.setDocumentNumber("2026-September-ABC123");
        document.setCreatedAt(Instant.parse("2026-09-30T06:00:00Z"));
        document.setIssuedAt(Instant.parse("2026-09-30T06:00:00Z"));
        document.setIssueDate(LocalDate.of(2026, 9, 30));
        document.setDueDate(Instant.parse("2026-10-15T06:30:00Z"));
        document.setCurrency("USD");
        document.getTaxSummaries().add(
                new TaxSummary(new BigDecimal("18.0000"), new BigDecimal("1225.00"), new BigDecimal("220.50")));
        document.setSubTotal(new BigDecimal("1225.00"));
        document.setSalesTax(new BigDecimal("220.50"));
        document.setTotal(new BigDecimal("1445.50"));
        document.setTotalAmountReceived(new BigDecimal("445.50"));
        document.setAdditionalInfo("Thanks for your business.");
        document.getBillingItems().add(item("Design sprint", "1000", 1, "0"));
        document.getBillingItems().add(item("Prototype & <review>", "125", 2, "10"));
        Payment payment = new Payment();
        payment.setAmountPaid(new BigDecimal("445.50"));
        document.getPayments().add(payment);
        return document;
    }

    @Test
    @DisplayName("an invoice lists line amounts, what was paid and the balance due, text escaped")
    void invoiceHtml() {
        String html = service.html(sampleDocument(DocumentType.INVOICE), seller("Ada Analytics"), SETTINGS);

        assertThat(html)
                .contains("Prototype &amp; &lt;review&gt;")
                .contains("225.00")
                .contains("Tax (18%)")
                .contains("USD 1,445.50")
                .contains("Amount paid")
                .contains("Balance due")
                .contains("USD 1,000.00")
                .contains("Partially paid")
                .contains("Sep 30, 2026")
                .contains("Oct 15, 2026")
                .contains("Account no. CUS-123456")
                .contains("Pune, India")
                .contains("Thanks for your business.")
                .doesNotContain("VAT / TIN");
    }

    @Test
    @DisplayName("within a union territory without a legislature, SGST is called UTGST")
    void unionTerritory() {
        Document document = sampleDocument(DocumentType.INVOICE);
        document.getBillingItems().forEach(item -> item.setTaxRate(new BigDecimal("18")));
        document.setTaxRegime(TaxRegime.GST_INDIA);
        document.setSellerStateCode("04");
        document.setPlaceOfSupply("04");
        document.setSupplyType(SupplyType.INTRA);
        DocumentTotals.apply(document);

        String html = service.html(document, seller("Ada Analytics"), SETTINGS);

        assertThat(html).contains(
                "UTGST 9%",
                "<th class=\"num\">UTGST</th>",
                "Place of supply: 04 – Chandigarh",
                "State: Chandigarh (04)",
                "Tax Invoice");
    }

    @Test
    @DisplayName("a quotation shows a validity date and its total, with no payment status")
    void quotationHtml() {
        String html = service.html(sampleDocument(DocumentType.QUOTATION), seller("Ada Analytics"), SETTINGS);

        assertThat(html).contains("Valid until").contains("USD 1,445.50");
        assertThat(html).doesNotContain("Amount paid").doesNotContain("Balance due").doesNotContain("Partially paid");
    }

    @Test
    @DisplayName("a payment receipt shows what was received, how, and the balance left afterwards")
    void receiptHtml() {
        Document document = sampleDocument(DocumentType.INVOICE);
        Payment payment = document.getPayments().get(0);
        payment.setDatePaid(LocalDate.of(2026, 9, 12));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setAdditionalInfo("Deposit & <thanks>");

        String html = service.receiptHtml(
                document, payment, "2026-September-ABC123-P1", new BigDecimal("445.50"), seller("Ada Analytics"));

        assertThat(html)
                .contains("Payment receipt 2026-September-ABC123-P1")
                .contains("Sep 12, 2026")
                .contains("Invoice 2026-September-ABC123")
                .contains("USD 445.50")
                .contains("Paid by Bank Transfer")
                .contains("Deposit &amp; &lt;thanks&gt;")
                .contains("USD 1,445.50")
                .contains("Balance after this payment")
                .contains("USD 1,000.00");
    }

    private static Customer client() {
        Customer customer = new Customer();
        customer.setName("Beta & <Sons>");
        customer.setEmail("beta@x.com");
        customer.setAccountNo("CUS-123456");
        customer.setCity("Mumbai");
        return customer;
    }

    private static Statements.Account account(String currency, String overdue) {
        return new Statements.Account(
                currency,
                List.of(
                        new Statements.Line(LocalDate.of(2026, 8, 1), "Invoice INV-1", new BigDecimal("1200.00"),
                                null, new BigDecimal("1200.00")),
                        new Statements.Line(LocalDate.of(2026, 9, 2), "Payment for INV-1 (Cash)", null,
                                new BigDecimal("200.00"), new BigDecimal("1000.00"))),
                new BigDecimal("1200.00"),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                new BigDecimal(overdue));
    }

    @Test
    @DisplayName("a statement lists each invoice and payment with the running balance and the amount due")
    void statementHtml() {
        String html = service.statementHtml(
                seller("Ada Analytics"), client(), LocalDate.of(2026, 9, 30), List.of(account("USD", "1000.00")));

        assertThat(html)
                .contains("Statement for Beta &amp; &lt;Sons&gt;")
                .contains("Account no. CUS-123456")
                .contains("Sep 30, 2026")
                .contains("Aug 1, 2026")
                .contains("Payment for INV-1 (Cash)")
                .contains("1,200.00")
                .contains("USD 200.00")
                .contains("Overdue")
                .contains("USD 1,000.00")
                .contains("Amount due");
    }

    @Test
    @DisplayName("a settled statement shows no overdue line, and several currencies are labelled")
    void statementCurrencies() {
        String settled = service.statementHtml(
                seller("Ada Analytics"), client(), LocalDate.of(2026, 9, 30), List.of(account("USD", "0")));
        String mixed = service.statementHtml(
                seller("Ada Analytics"),
                client(),
                LocalDate.of(2026, 9, 30),
                List.of(account("USD", "0"), account("EUR", "0")));
        String empty = service.statementHtml(seller("Ada Analytics"), client(), LocalDate.of(2026, 9, 30), List.of());

        assertThat(settled).doesNotContain("Overdue");
        assertThat(mixed).contains("<div class=\"label\">EUR</div>");
        assertThat(empty).contains("No invoices yet, so nothing is due.");
    }

    @Test
    @DisplayName("a logo uploaded to this server is embedded; a remote one is never fetched")
    void logo() throws Exception {
        Files.write(uploads.resolve("brand.png"), PNG);
        Business local = seller("Ada Analytics");
        local.setLogo("/uploads/brand.png");
        Business remote = seller("Ada Analytics");
        remote.setLogo("https://example.com/brand.png");

        assertThat(service.html(sampleDocument(DocumentType.INVOICE), local, SETTINGS)).contains("data:image/png;base64,");
        assertThat(service.html(sampleDocument(DocumentType.INVOICE), remote, SETTINGS)).doesNotContain("example.com");
        assertThat(new String(service.render(sampleDocument(DocumentType.INVOICE), local, SETTINGS), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("the sender is the business, with its legal name when it differs, its address and tax ID")
    void sender() {
        Business business = seller("Ada Analytics");
        business.setLegalName("Ada Analytics Private Limited");
        business.setState("Maharashtra");
        business.setPostalCode("411001");
        business.setTaxId("27AAPFU0939F1ZV");

        assertThat(PdfService.senderName(business)).isEqualTo("Ada Analytics");
        assertThat(PdfService.senderLines(business)).containsExactly(
                "Ada Analytics Private Limited", "ada@x.com", "Pune, Maharashtra 411001, India", "Tax ID 27AAPFU0939F1ZV");

        business.setLegalName("ada analytics");
        assertThat(PdfService.senderLines(business)).doesNotContain("ada analytics");
    }

    @Test
    @DisplayName("the tax is named as the document keeps it, and the due date is a day in the owner's time zone")
    void settingsApply() {
        BusinessSettings settings = new BusinessSettings();
        settings.setTaxLabel("VAT");
        settings.setTimeZone("Asia/Kolkata");
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setTaxLabel("GST");
        document.setDueDate(Instant.parse("2026-10-15T20:00:00Z"));

        String html = service.html(document, seller("Ada Analytics"), settings);

        assertThat(html).contains("GST (18%)").contains("Oct 16, 2026").doesNotContain("VAT (18%)");
    }

    @Test
    @DisplayName("a document shows the business as it went out with it, whatever it is called now")
    void keptSeller() {
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setSeller(SellerSnapshot.of(seller("Ada Analytics")));
        Payment payment = document.getPayments().get(0);
        payment.setDatePaid(LocalDate.of(2026, 9, 12));
        payment.setPaymentMethod(PaymentMethod.CASH);

        assertThat(service.html(document, seller("Borealis Studio"), SETTINGS))
                .contains("Ada Analytics")
                .doesNotContain("Borealis Studio");
        assertThat(service.receiptHtml(document, payment, "INV-1-P1", BigDecimal.TEN, seller("Borealis Studio")))
                .contains("Ada Analytics")
                .doesNotContain("Borealis Studio");
    }

    @Test
    @DisplayName("a document shows the day it is dated, not the day it was typed in")
    void issueDate() {
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setIssueDate(LocalDate.of(2026, 9, 1));

        assertThat(service.html(document, seller("Ada Analytics"), SETTINGS)).contains("Sep 1, 2026");
    }
}
