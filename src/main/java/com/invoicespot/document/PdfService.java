package com.invoicespot.document;

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
import java.time.ZoneOffset;
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

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final ITemplateEngine templateEngine;
    private final UploadService uploadService;

    public PdfService(ITemplateEngine templateEngine, UploadService uploadService) {
        this.templateEngine = templateEngine;
        this.uploadService = uploadService;
    }

    public byte[] render(Document document, User owner) {
        return toPdf(html(document, owner));
    }

    String html(Document document, User owner) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("pdf", view(document, owner));
        return templateEngine.process("pdf/document", context);
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

    private View view(Document document, User owner) {
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
        totals.add(new Total("Tax (" + percent(document.getRates()) + ")", money(document.getSalesTax())));
        boolean quotation = type == DocumentType.QUOTATION;
        if (!quotation) {
            totals.add(new Total("Total", money(total)));
            totals.add(new Total("Amount paid", money(received)));
        }

        return new View(
                logo(owner.getAvatar()),
                senderName(owner),
                lines(
                        owner.getEmail(),
                        owner.getPhoneNumber(),
                        joined(owner.getAddress(), owner.getCity(), owner.getCountry())),
                type.getValue(),
                value(document.getDocumentNumber()),
                date(document.getCreatedAt()),
                quotation ? "Valid until" : "Due",
                date(document.getDueDate()),
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

    private static String date(Instant instant) {
        return instant == null ? null : DATE.format(instant);
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
