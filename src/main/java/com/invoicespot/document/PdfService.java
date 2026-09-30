package com.invoicespot.document;

import com.invoicespot.customer.Customer;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.upload.UploadService;
import com.invoicespot.user.User;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class PdfService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final ITemplateEngine templateEngine;
    private final UploadService uploadService;

    public PdfService(ITemplateEngine templateEngine, UploadService uploadService) {
        this.templateEngine = templateEngine;
        this.uploadService = uploadService;
    }

    public byte[] render(Document document, User owner, BusinessSettings settings) {
        return toPdf(html(document, owner, settings));
    }

    String html(Document document, User owner, BusinessSettings settings) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("pdf", view(document, owner, settings));
        return templateEngine.process("pdf/document", context);
    }

    byte[] renderReceipt(
            Document document, Payment payment, String number, BigDecimal paidToDate, User owner) {
        return toPdf(receiptHtml(document, payment, number, paidToDate, owner));
    }

    // A payment receipt: what was received, how, and what the document still
    // owed once this payment had been made.
    String receiptHtml(Document document, Payment payment, String number, BigDecimal paidToDate, User owner) {
        CustomerSnapshot client = Optional.ofNullable(document.getCustomerSnapshot()).orElseGet(CustomerSnapshot::new);
        BigDecimal total = orZero(document.getTotal());
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("receipt", new ReceiptView(
                logo(owner.getAvatar()),
                senderName(owner),
                senderLines(owner),
                number,
                DATE.format(payment.getDatePaid()),
                value(client.getName()),
                lines(
                        client.getEmail(),
                        client.getPhoneNumber(),
                        joined(client.getAddress(), client.getCity(), client.getCountry()),
                        client.getAccountNo() == null ? null : "Account no. " + client.getAccountNo()),
                document.getDocumentType().getValue() + " " + value(document.getDocumentNumber()),
                value(document.getCurrency()),
                money(payment.getAmountPaid()),
                payment.getPaymentMethod().getValue(),
                blankToNull(payment.getAdditionalInfo()),
                money(total),
                money(paidToDate),
                money(total.subtract(paidToDate).max(BigDecimal.ZERO))));
        return templateEngine.process("pdf/receipt", context);
    }

    byte[] renderStatement(User owner, Customer customer, LocalDate day, List<Statements.Account> accounts) {
        return toPdf(statementHtml(owner, customer, day, accounts));
    }

    String statementHtml(User owner, Customer customer, LocalDate day, List<Statements.Account> accounts) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("statement", new StatementView(
                logo(owner.getAvatar()),
                senderName(owner),
                senderLines(owner),
                DATE.format(day),
                value(customer.getName()),
                lines(
                        customer.getEmail(),
                        customer.getPhoneNumber(),
                        joined(customer.getAddress(), customer.getCity(), customer.getCountry()),
                        customer.getAccountNo() == null ? null : "Account no. " + customer.getAccountNo(),
                        vatTin(customer.getVatTinNo())),
                accounts.stream().map(PdfService::statementAccount).toList()));
        return templateEngine.process("pdf/statement", context);
    }

    static String senderName(User owner) {
        if (owner.getBusinessName() != null && !owner.getBusinessName().isBlank()) {
            return owner.getBusinessName();
        }
        return (value(owner.getFirstName()) + " " + value(owner.getLastName())).trim();
    }

    public record View(
            String logo,
            String sender,
            List<String> senderLines,
            String title,
            String number,
            String issued,
            String dueLabel,
            String due,
            String status,
            String statusTone,
            String clientName,
            List<String> clientLines,
            String currency,
            List<Line> items,
            List<Total> totals,
            String balanceLabel,
            String balance,
            String notes,
            String terms) {}

    public record Line(int index, String name, String quantity, String unitPrice, String discount, String amount) {}

    public record Total(String label, String value) {}

    public record ReceiptView(
            String logo,
            String sender,
            List<String> senderLines,
            String number,
            String date,
            String clientName,
            List<String> clientLines,
            String documentTitle,
            String currency,
            String amount,
            String method,
            String note,
            String total,
            String paidToDate,
            String balance) {}

    public record StatementView(
            String logo,
            String sender,
            List<String> senderLines,
            String date,
            String clientName,
            List<String> clientLines,
            List<StatementAccount> accounts) {}

    public record StatementAccount(
            String currency,
            List<StatementLine> lines,
            String charged,
            String paid,
            String due,
            String overdue,
            boolean hasOverdue) {}

    public record StatementLine(String date, String description, String charge, String payment, String balance) {}

    private static StatementAccount statementAccount(Statements.Account account) {
        return new StatementAccount(
                account.currency(),
                account.lines().stream()
                        .map(line -> new StatementLine(
                                DATE.format(line.date()),
                                line.description(),
                                line.charge() == null ? "" : money(line.charge()),
                                line.payment() == null ? "" : money(line.payment()),
                                money(line.balance())))
                        .toList(),
                money(account.charged()),
                money(account.paid()),
                money(account.due()),
                money(account.overdue()),
                account.overdue().signum() > 0);
    }

    private View view(Document document, User owner, BusinessSettings settings) {
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
                    money(item.getUnitPrice()),
                    percent(item.getDiscount()),
                    money(DocumentTotals.lineTotal(item))));
        }

        BigDecimal received = orZero(document.getTotalAmountReceived());
        BigDecimal total = orZero(document.getTotal());
        List<Total> totals = new ArrayList<>();
        totals.add(new Total("Subtotal", money(document.getSubTotal())));
        totals.add(new Total(
                settings.getTaxLabel() + " (" + percent(document.getRates()) + ")", money(document.getSalesTax())));
        boolean quotation = type == DocumentType.QUOTATION;
        if (!quotation) {
            totals.add(new Total("Total", money(total)));
            totals.add(new Total("Amount paid", money(received)));
        }

        return new View(
                logo(owner.getAvatar()),
                senderName(owner),
                senderLines(owner),
                type.getValue(),
                value(document.getDocumentNumber()),
                date(document.getCreatedAt(), zone),
                quotation ? "Valid until" : "Due",
                date(document.getDueDate(), zone),
                quotation ? null : statusLabel(document.getStatus()),
                statusTone(document.getStatus()),
                value(client.getName()),
                lines(
                        client.getEmail(),
                        client.getPhoneNumber(),
                        joined(client.getAddress(), client.getCity(), client.getCountry()),
                        client.getAccountNo() == null ? null : "Account no. " + client.getAccountNo(),
                        vatTin(client.getVatTinNo())),
                currency,
                items,
                totals,
                quotation ? "Total" : "Balance due",
                money(quotation ? total : total.subtract(received)),
                blankToNull(document.getAdditionalInfo()),
                blankToNull(document.getTermsConditions()));
    }

    private static List<String> senderLines(User owner) {
        return lines(
                owner.getEmail(),
                owner.getPhoneNumber(),
                joined(owner.getAddress(), owner.getCity(), owner.getCountry()));
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
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render PDF", exception);
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

    private static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(orZero(amount));
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
