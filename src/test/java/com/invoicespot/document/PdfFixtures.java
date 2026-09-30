package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.config.AppProperties;
import com.invoicespot.customer.Customer;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.upload.UploadService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

// Fixed documents, a seller and a client to render PDFs from in tests, and a
// PdfService wired as the app wires it. Every detail is filled in, so a
// change to any part of a PDF shows up.
final class PdfFixtures {

    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private PdfFixtures() {}

    static PdfService service(Path uploads) {
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
                new AppProperties.Site(
                        "Invoice Spot", "http://localhost:3000", "http://localhost:8080", "from@invoicespot.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload(uploads.toString()));
        return new PdfService(engine, new UploadService(properties));
    }

    // The business, with a logo saved in the upload folder.
    static Business seller(Path uploads) throws IOException {
        Files.write(uploads.resolve("logo.png"), PNG);
        Business business = new Business();
        business.setName("Ada Analytics");
        business.setLegalName("Ada Analytics Private Limited");
        business.setEmail("accounts@ada.example");
        business.setPhoneNumber("+91 20 5555 0100");
        business.setAddress("12 MG Road");
        business.setCity("Pune");
        business.setState("Maharashtra");
        business.setPostalCode("411001");
        business.setCountry("India");
        business.setTaxId("27AAPFU0939F1ZV");
        business.setLogo("/uploads/logo.png");
        return business;
    }

    static BusinessSettings settings() {
        BusinessSettings settings = new BusinessSettings();
        settings.setTaxLabel("GST");
        settings.setTimeZone("Asia/Kolkata");
        return settings;
    }

    private static CustomerSnapshot client(String name) {
        CustomerSnapshot client = new CustomerSnapshot();
        client.setName(name);
        client.setEmail("accounts@beta.example");
        client.setPhoneNumber("+91 22 5555 0199");
        client.setAddress("4 Marine Drive");
        client.setCity("Mumbai");
        client.setCountry("India");
        client.setAccountNo("CUS-100042");
        client.setVatTinNo("27AABCB1234C1Z5");
        return client;
    }

    static BillingItem line(String name, String price, int quantity, String discount) {
        BillingItem item = new BillingItem();
        item.setItemName(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setDiscount(new BigDecimal(discount));
        return item;
    }

    private static Document document(DocumentType type, String number, String currency) {
        Document document = new Document();
        document.setDocumentType(type);
        document.setDocumentNumber(number);
        document.setCurrency(currency);
        document.setCustomerSnapshot(client("Beta & <Sons> LLP"));
        document.setCreatedAt(Instant.parse("2026-09-30T20:00:00Z"));
        document.setIssuedAt(Instant.parse("2026-09-30T20:00:00Z"));
        document.setIssueDate(LocalDate.of(2026, 10, 1));
        document.setRates(new BigDecimal("18"));
        document.setTaxLabel("GST");
        document.setAdditionalInfo("Thanks for your business.\nPayments within 15 days, please.");
        document.setTermsConditions("Late payments carry 1.5% interest a month.");
        return document;
    }

    // An invoice part paid, with a discounted line, characters to escape and
    // an amount over a lakh.
    static Document invoice() {
        Document invoice = document(DocumentType.INVOICE, "INV-0042", "INR");
        invoice.setDueDate(Instant.parse("2026-10-15T06:30:00Z"));
        invoice.setStatus(DocumentStatus.NOT_FULLY_PAID);
        invoice.getBillingItems().add(line("Design sprint", "100000", 1, "0"));
        invoice.getBillingItems().add(line("Prototype & <review>", "12500", 2, "10"));
        invoice.setSubTotal(new BigDecimal("122500.00"));
        invoice.setSalesTax(new BigDecimal("22050.00"));
        invoice.setTotal(new BigDecimal("144550.00"));
        invoice.setTotalAmountReceived(new BigDecimal("44550.00"));
        invoice.getPayments().add(payment(invoice));
        return invoice;
    }

    // A draft: no number and no date until it is issued, and the business
    // and the name of its tax as they are now.
    static Document draft() {
        Document draft = document(DocumentType.INVOICE, null, "INR");
        draft.setIssuedAt(null);
        draft.setIssueDate(null);
        draft.setTaxLabel(null);
        draft.setDueDate(Instant.parse("2026-10-15T06:30:00Z"));
        draft.setStatus(DocumentStatus.NOT_PAID);
        draft.getBillingItems().add(line("Design sprint", "100000", 1, "0"));
        draft.setSubTotal(new BigDecimal("100000.00"));
        draft.setSalesTax(new BigDecimal("18000.00"));
        draft.setTotal(new BigDecimal("118000.00"));
        draft.setTotalAmountReceived(BigDecimal.ZERO);
        return draft;
    }

    // An invoice voided because it went to the wrong client.
    static Document voided() {
        Document voided = invoice();
        voided.getPayments().clear();
        voided.setTotalAmountReceived(BigDecimal.ZERO);
        voided.setStatus(DocumentStatus.NOT_PAID);
        voided.setVoidedAt(Instant.parse("2026-10-03T05:00:00Z"));
        voided.setVoidReason("Sent to the wrong client & <branch>");
        return voided;
    }

    static Payment payment(Document invoice) {
        Payment payment = new Payment();
        payment.setDocument(invoice);
        payment.setDatePaid(LocalDate.of(2026, 10, 2));
        payment.setAmountPaid(new BigDecimal("44550.00"));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setAdditionalInfo("Deposit & <thanks>");
        return payment;
    }

    static Document quotation() {
        Document quotation = document(DocumentType.QUOTATION, "QUO-0007", "USD");
        quotation.setDueDate(Instant.parse("2026-10-30T06:30:00Z"));
        quotation.setStatus(DocumentStatus.NOT_PAID);
        quotation.getBillingItems().add(line("Discovery workshop", "2400", 1, "0"));
        quotation.getBillingItems().add(line("Weekly check-in", "150", 4, "5"));
        quotation.setSubTotal(new BigDecimal("2970.00"));
        quotation.setSalesTax(new BigDecimal("534.60"));
        quotation.setTotal(new BigDecimal("3504.60"));
        quotation.setTotalAmountReceived(BigDecimal.ZERO);
        return quotation;
    }

    // A receipt: a document for a sale paid in full on the spot.
    static Document receipt() {
        Document receipt = document(DocumentType.RECEIPT, "REC-0003", "INR");
        receipt.setStatus(DocumentStatus.PAID);
        receipt.getBillingItems().add(line("Consulting hour", "4000", 3, "0"));
        receipt.setSubTotal(new BigDecimal("12000.00"));
        receipt.setSalesTax(new BigDecimal("2160.00"));
        receipt.setTotal(new BigDecimal("14160.00"));
        receipt.setTotalAmountReceived(new BigDecimal("14160.00"));
        return receipt;
    }

    static Customer customer() {
        Customer customer = new Customer();
        customer.setName("Beta & <Sons> LLP");
        customer.setEmail("accounts@beta.example");
        customer.setPhoneNumber("+91 22 5555 0199");
        customer.setAddress("4 Marine Drive");
        customer.setCity("Mumbai");
        customer.setCountry("India");
        customer.setAccountNo("CUS-100042");
        customer.setVatTinNo("27AABCB1234C1Z5");
        return customer;
    }

    // Two currencies: rupees still overdue, dollars settled.
    static List<Statements.Account> accounts() {
        return List.of(
                new Statements.Account(
                        "INR",
                        List.of(
                                new Statements.Line(LocalDate.of(2026, 8, 1), "Invoice INV-0040",
                                        new BigDecimal("144550.00"), null, new BigDecimal("144550.00")),
                                new Statements.Line(LocalDate.of(2026, 9, 2), "Payment for INV-0040 (Bank Transfer)",
                                        null, new BigDecimal("44550.00"), new BigDecimal("100000.00"))),
                        new BigDecimal("144550.00"),
                        new BigDecimal("44550.00"),
                        new BigDecimal("100000.00"),
                        new BigDecimal("100000.00")),
                new Statements.Account(
                        "USD",
                        List.of(
                                new Statements.Line(LocalDate.of(2026, 9, 5), "Invoice INV-0041",
                                        new BigDecimal("1200.00"), null, new BigDecimal("1200.00")),
                                new Statements.Line(LocalDate.of(2026, 9, 20), "Payment for INV-0041 (Cash)",
                                        null, new BigDecimal("1200.00"), BigDecimal.ZERO)),
                        new BigDecimal("1200.00"),
                        new BigDecimal("1200.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));
    }
}
