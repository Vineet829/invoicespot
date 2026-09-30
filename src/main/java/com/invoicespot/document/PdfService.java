package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.common.MoneyFormat;
import com.invoicespot.customer.Customer;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.upload.UploadService;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class PdfService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // Noto Sans, embedded in every PDF, so the rupee sign and names with
    // accents print however the reader's machine is set up. The templates ask
    // for it by this name.
    private static final String FONT = "Noto Sans";
    private static final byte[] REGULAR = font("fonts/NotoSans-Regular.ttf");
    private static final byte[] BOLD = font("fonts/NotoSans-Bold.ttf");

    private final ITemplateEngine templateEngine;
    private final UploadService uploadService;

    public PdfService(ITemplateEngine templateEngine, UploadService uploadService) {
        this.templateEngine = templateEngine;
        this.uploadService = uploadService;
    }

    public byte[] render(Document document, Business seller, BusinessSettings settings) {
        return toPdf(html(document, seller, settings));
    }

    String html(Document document, Business seller, BusinessSettings settings) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("pdf", view(document, seller, settings));
        return templateEngine.process("pdf/document", context);
    }

    byte[] renderReceipt(
            Document document, Payment payment, String number, BigDecimal paidToDate, Business seller) {
        return toPdf(receiptHtml(document, payment, number, paidToDate, seller));
    }

    // A payment receipt: what was received, how, and what the document still
    // owed once this payment had been made.
    String receiptHtml(
            Document document, Payment payment, String number, BigDecimal paidToDate, Business seller) {
        CustomerSnapshot client = Optional.ofNullable(document.getCustomerSnapshot()).orElseGet(CustomerSnapshot::new);
        BigDecimal total = orZero(document.getTotal());
        Context context = new Context(Locale.ENGLISH);
        String documentTitle = document.getDocumentType().getValue() + " " + value(document.getDocumentNumber());
        String currency = value(document.getCurrency());
        context.setVariable("receipt", new ReceiptView(
                seller(document, seller),
                number,
                DATE.format(payment.getDatePaid()),
                new Party(null, value(client.getName()), lines(
                        client.getEmail(),
                        client.getPhoneNumber(),
                        joined(client.getAddress(), client.getCity(), client.getCountry()),
                        client.getAccountNo() == null ? null : "Account no. " + client.getAccountNo())),
                documentTitle,
                currency,
                money(payment.getAmountPaid(), currency),
                payment.getPaymentMethod().getValue(),
                blankToNull(payment.getAdditionalInfo()),
                List.of(
                        new Total(documentTitle + " total", money(total, currency)),
                        new Total("Paid to date", money(paidToDate, currency))),
                money(total.subtract(paidToDate).max(BigDecimal.ZERO), currency)));
        return templateEngine.process("pdf/receipt", context);
    }

    byte[] renderStatement(Business seller, Customer customer, LocalDate day, List<Statements.Account> accounts) {
        return toPdf(statementHtml(seller, customer, day, accounts));
    }

    String statementHtml(Business seller, Customer customer, LocalDate day, List<Statements.Account> accounts) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("statement", new StatementView(
                seller(seller),
                DATE.format(day),
                new Party(null, value(customer.getName()), lines(
                        customer.getEmail(),
                        customer.getPhoneNumber(),
                        joined(customer.getAddress(), customer.getCity(), customer.getCountry()),
                        customer.getAccountNo() == null ? null : "Account no. " + customer.getAccountNo(),
                        vatTin(customer.getVatTinNo()))),
                accounts.stream().map(PdfService::statementAccount).toList()));
        return templateEngine.process("pdf/statement", context);
    }

    static String senderName(Business seller) {
        return value(seller.getName());
    }

    // Someone a PDF names, the business or its client: a logo, a name and the
    // lines under it.
    public record Party(String logo, String name, List<String> lines) {}

    // A draft has no number yet, and is marked as one on every page; a void
    // document is marked void, and says when and why.
    public record View(
            String watermark,
            String voidNote,
            Party seller,
            String title,
            String number,
            String issued,
            String dueLabel,
            String due,
            String status,
            String statusTone,
            Party client,
            String currency,
            List<Line> items,
            List<Total> totals,
            String balanceLabel,
            String balance,
            String notes,
            String terms) {}

    public record Line(int index, String name, String quantity, String unitPrice, String discount, String amount) {}

    // A row of amounts; the tone, when there is one, is its CSS class.
    public record Total(String label, String value, String tone) {

        public Total(String label, String value) {
            this(label, value, null);
        }
    }

    public record ReceiptView(
            Party seller,
            String number,
            String date,
            Party client,
            String documentTitle,
            String currency,
            String amount,
            String method,
            String note,
            List<Total> totals,
            String balance) {}

    public record StatementView(Party seller, String date, Party client, List<StatementAccount> accounts) {}

    public record StatementAccount(String currency, List<StatementLine> lines, List<Total> totals, String due) {}

    public record StatementLine(String date, String description, String charge, String payment, String balance) {}

    private static StatementAccount statementAccount(Statements.Account account) {
        return new StatementAccount(
                account.currency(),
                account.lines().stream()
                        .map(line -> new StatementLine(
                                DATE.format(line.date()),
                                line.description(),
                                line.charge() == null ? "" : money(line.charge(), account.currency()),
                                line.payment() == null ? "" : money(line.payment(), account.currency()),
                                money(line.balance(), account.currency())))
                        .toList(),
                Stream.of(
                                new Total("Invoiced", money(account.charged(), account.currency())),
                                new Total("Paid", money(account.paid(), account.currency())),
                                account.overdue().signum() > 0
                                        ? new Total("Overdue", money(account.overdue(), account.currency()), "overdue")
                                        : null)
                        .filter(Objects::nonNull)
                        .toList(),
                money(account.due(), account.currency()));
    }

    private Party seller(Business seller) {
        return new Party(logo(seller.getLogo()), senderName(seller), senderLines(seller));
    }

    // A document shows the business as it went out with it. Until it keeps its
    // own copy, it shows the business as it is now.
    private Party seller(Document document, Business business) {
        SellerSnapshot kept = document.getSeller();
        return kept == null
                ? seller(business)
                : new Party(logo(kept.getLogo()), value(kept.getName()), senderLines(kept));
    }

    private View view(Document document, Business seller, BusinessSettings settings) {
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        DocumentType type = document.getDocumentType();
        String currency = value(document.getCurrency());
        CustomerSnapshot client = Optional.ofNullable(document.getCustomerSnapshot()).orElseGet(CustomerSnapshot::new);

        List<Line> items = new ArrayList<>();
        List<BillingItem> billingItems = document.getBillingItems();
        for (int index = 0; index < billingItems.size(); index++) {
            BillingItem item = billingItems.get(index);
            items.add(new Line(
                    index + 1,
                    value(item.getItemName()),
                    item.getQuantity() == null ? "0" : item.getQuantity().toString(),
                    money(item.getUnitPrice(), currency),
                    percent(item.getDiscount()),
                    money(DocumentTotals.lineTotal(item), currency)));
        }

        BigDecimal received = orZero(document.getTotalAmountReceived());
        BigDecimal total = orZero(document.getTotal());
        boolean draft = document.isDraft();
        boolean voided = document.isVoid();
        List<Total> totals = new ArrayList<>();
        totals.add(new Total("Subtotal", money(document.getSubTotal(), currency)));
        totals.add(new Total(
                Objects.requireNonNullElse(document.getTaxLabel(), settings.getTaxLabel())
                        + " (" + percent(document.getRates()) + ")",
                money(document.getSalesTax(), currency)));
        boolean quotation = type == DocumentType.QUOTATION;
        if (!quotation) {
            totals.add(new Total("Total", money(total, currency)));
            totals.add(new Total("Amount paid", money(received, currency)));
        }

        return new View(
                draft ? "DRAFT" : voided ? "VOID" : null,
                voided ? "Voided on " + date(document.getVoidedAt(), zone) + ": " + document.getVoidReason() : null,
                seller(document, seller),
                type.getValue(),
                draft ? null : value(document.getDocumentNumber()),
                document.getIssueDate() == null ? "On issue" : DATE.format(document.getIssueDate()),
                quotation ? "Valid until" : "Due",
                date(document.getDueDate(), zone),
                quotation || draft || voided ? null : statusLabel(document.getStatus()),
                statusTone(document.getStatus()),
                new Party(null, value(client.getName()), lines(
                        client.getEmail(),
                        client.getPhoneNumber(),
                        joined(client.getAddress(), client.getCity(), client.getCountry()),
                        client.getAccountNo() == null ? null : "Account no. " + client.getAccountNo(),
                        vatTin(client.getVatTinNo()))),
                currency,
                items,
                totals,
                quotation ? "Total" : "Balance due",
                money(quotation ? total : voided ? BigDecimal.ZERO : total.subtract(received), currency),
                blankToNull(document.getAdditionalInfo()),
                blankToNull(document.getTermsConditions()));
    }

    static List<String> senderLines(Business seller) {
        return senderLines(SellerSnapshot.of(seller));
    }

    // The legal name shows when it differs from the name the business trades
    // under.
    static List<String> senderLines(SellerSnapshot seller) {
        return lines(
                legalName(seller),
                seller.getEmail(),
                seller.getPhoneNumber(),
                joined(
                        seller.getAddress(),
                        seller.getCity(),
                        String.join(" ", lines(seller.getState(), seller.getPostalCode())),
                        seller.getCountry()),
                isBlank(seller.getTaxId()) ? null : "Tax ID " + seller.getTaxId());
    }

    private static String legalName(SellerSnapshot seller) {
        String legal = seller.getLegalName();
        return isBlank(legal) || legal.trim().equalsIgnoreCase(value(seller.getName()).trim()) ? null : legal;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String logo(String avatar) {
        return uploadService.localFile(avatar).flatMap(PdfService::dataUri).orElse(null);
    }

    private static Optional<String> dataUri(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = name.endsWith(".png")
                ? "image/png"
                : name.endsWith(".jpg") || name.endsWith(".jpeg")
                        ? "image/jpeg"
                        : name.endsWith(".gif") ? "image/gif" : null;
        if (type == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    "data:" + type + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static byte[] toPdf(String html) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> new ByteArrayInputStream(REGULAR), FONT, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> new ByteArrayInputStream(BOLD), FONT, 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render PDF", exception);
        }
    }

    private static byte[] font(String path) {
        try (InputStream in = PdfService.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("The font " + path + " is missing");
            }
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String vatTin(String value) {
        return value == null || value.isBlank() || value.trim().equals("0") ? null : "VAT / TIN " + value;
    }

    private static String statusLabel(DocumentStatus status) {
        return switch (status) {
            case PAID -> "Paid";
            case NOT_FULLY_PAID -> "Partially paid";
            case NOT_PAID -> "Unpaid";
        };
    }

    private static String statusTone(DocumentStatus status) {
        return switch (status) {
            case PAID -> "paid";
            case NOT_FULLY_PAID -> "partial";
            case NOT_PAID -> "unpaid";
        };
    }

    private static List<String> lines(String... values) {
        return Stream.of(values).filter(line -> line != null && !line.isBlank()).toList();
    }

    private static String joined(String... values) {
        return String.join(", ", lines(values));
    }

    private static String money(BigDecimal amount, String currency) {
        return MoneyFormat.format(amount, currency);
    }

    private static String percent(BigDecimal value) {
        return orZero(value).stripTrailingZeros().toPlainString() + "%";
    }

    private static String date(Instant instant, ZoneId zone) {
        return instant == null ? null : DATE.format(instant.atZone(zone));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
