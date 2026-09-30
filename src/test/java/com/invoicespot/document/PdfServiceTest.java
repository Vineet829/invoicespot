package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.config.AppProperties;
import com.invoicespot.customer.Customer;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.upload.UploadService;
import com.invoicespot.user.User;
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

    private static User owner(String businessName) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@x.com");
        user.setCity("Pune");
        user.setCountry("India");
        user.setBusinessName(businessName);
        return user;
    }

    private static BillingItem item(String name, String price, int quantity, String discount) {
        BillingItem item = new BillingItem();
        item.setItemName(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(quantity);
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
        document.setDueDate(Instant.parse("2026-10-15T06:30:00Z"));
        document.setCurrency("USD");
        document.setRates(new BigDecimal("18"));
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
        String html = service.html(sampleDocument(DocumentType.INVOICE), owner("Ada Analytics"), SETTINGS);

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
    @DisplayName("a quotation shows a validity date and its total, with no payment status")
    void quotationHtml() {
        String html = service.html(sampleDocument(DocumentType.QUOTATION), owner("Ada Analytics"), SETTINGS);

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
                document, payment, "2026-September-ABC123-P1", new BigDecimal("445.50"), owner("Ada Analytics"));

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
                new BigDecimal("1000.00"),
                new BigDecimal(overdue));
    }

    @Test
    @DisplayName("a statement lists each invoice and payment with the running balance and the amount due")
    void statementHtml() {
        String html = service.statementHtml(
                owner("Ada Analytics"), client(), LocalDate.of(2026, 9, 30), List.of(account("USD", "1000.00")));

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
                owner("Ada Analytics"), client(), LocalDate.of(2026, 9, 30), List.of(account("USD", "0")));
        String mixed = service.statementHtml(
                owner("Ada Analytics"),
                client(),
                LocalDate.of(2026, 9, 30),
                List.of(account("USD", "0"), account("EUR", "0")));
        String empty = service.statementHtml(owner("Ada Analytics"), client(), LocalDate.of(2026, 9, 30), List.of());

        assertThat(settled).doesNotContain("Overdue");
        assertThat(mixed).contains("<div class=\"label\">EUR</div>");
        assertThat(empty).contains("No invoices yet, so nothing is due.");
    }

    @Test
    @DisplayName("a logo uploaded to this server is embedded; a remote one is never fetched")
    void logo() throws Exception {
        Files.write(uploads.resolve("brand.png"), PNG);
        User local = owner("Ada Analytics");
        local.setAvatar("/uploads/brand.png");
        User remote = owner("Ada Analytics");
        remote.setAvatar("https://example.com/brand.png");

        assertThat(service.html(sampleDocument(DocumentType.INVOICE), local, SETTINGS)).contains("data:image/png;base64,");
        assertThat(service.html(sampleDocument(DocumentType.INVOICE), remote, SETTINGS)).doesNotContain("example.com");
        assertThat(new String(service.render(sampleDocument(DocumentType.INVOICE), local, SETTINGS), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("the sender is the business name, or the owner's full name without one")
    void senderName() {
        assertThat(PdfService.senderName(owner("Ada Analytics"))).isEqualTo("Ada Analytics");
        assertThat(PdfService.senderName(owner(" "))).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("the tax is named as in the settings, and dates are days in the owner's time zone")
    void settingsApply() {
        BusinessSettings settings = new BusinessSettings();
        settings.setTaxLabel("GST");
        settings.setTimeZone("Asia/Kolkata");
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setCreatedAt(Instant.parse("2026-09-30T20:00:00Z"));

        String html = service.html(document, owner("Ada Analytics"), settings);

        assertThat(html).contains("GST (18%)").contains("Oct 1, 2026").doesNotContain("Tax (18%)");
    }
}
