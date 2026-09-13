package com.invoicespot.document;

import com.invoicespot.config.AppProperties;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.DocumentCustomerDto;
import com.invoicespot.document.dto.GeneratePdfRequest;
import com.invoicespot.document.dto.PdfDocumentDto;
import com.invoicespot.document.dto.PdfProfileDto;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PdfService {

    private static final String FILE_NAME = "myDocument.pdf";

    private final Path storageDir;

    public PdfService(AppProperties properties) {
        this.storageDir = Path.of(properties.pdf().storageDir());
    }

    public byte[] generateAndSave(GeneratePdfRequest request) {
        byte[] pdf = render(buildHtml(request));
        try {
            Files.createDirectories(storageDir);
            Files.write(storageDir.resolve(FILE_NAME), pdf);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write PDF", exception);
        }
        return pdf;
    }

    public Optional<byte[]> readLatest() {
        Path file = storageDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read PDF", exception);
        }
    }

    private static byte[] render(String html) {
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

    private static String buildHtml(GeneratePdfRequest request) {
        PdfDocumentDto document = request.document();
        PdfProfileDto profile = request.profile();
        String currency = document != null && document.currency() != null ? document.currency() : "";
        String heading = request.balanceDue() != null && request.balanceDue() <= 0
                ? "Receipt"
                : value(document == null ? null : document.documentType(), "Invoice");

        StringBuilder html = new StringBuilder();
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>")
                .append("<meta charset=\"utf-8\" />")
                .append("<style>")
                .append("body{font-family:sans-serif;font-size:12px;color:#222;padding:24px;}")
                .append("h1{font-size:32px;font-weight:normal;margin:0;}")
                .append(".muted{color:#666;font-size:11px;text-transform:uppercase;letter-spacing:1px;}")
                .append(".row{width:100%;overflow:hidden;margin-bottom:16px;}")
                .append(".col{width:48%;float:left;}")
                .append(".right{text-align:right;}")
                .append(".divider{height:3px;background:#11418d;margin:12px 0;}")
                .append("table{border-collapse:collapse;width:100%;margin-top:12px;}")
                .append("th,td{border:1px solid #ddd;padding:8px;text-align:left;font-size:11px;}")
                .append("th{background:#11418d;color:#fff;}")
                .append(".summary{margin-top:16px;width:40%;float:right;}")
                .append(".summary td{border:none;padding:4px;}")
                .append("</style></head><body>");

        html.append("<div class=\"row\"><div class=\"col\"><h1>").append(esc(heading)).append("</h1>")
                .append("<div class=\"muted\">No: ").append(esc(document == null ? null : document.documentNumber()))
                .append("</div></div>")
                .append("<div class=\"col right\"><strong>")
                .append(esc(senderName(profile))).append("</strong><br />")
                .append(esc(profile == null ? null : profile.email())).append("<br />")
                .append(esc(profile == null ? null : profile.phoneNumber())).append("</div></div>");

        html.append("<div class=\"divider\"></div>");

        html.append("<div class=\"row\"><div class=\"col\"><div class=\"muted\">From</div>")
                .append(esc(senderName(profile))).append("<br />")
                .append(esc(addressLine(profile))).append("</div>");
        DocumentCustomerDto customer = document == null ? null : document.customer();
        html.append("<div class=\"col\"><div class=\"muted\">Bill To</div>")
                .append(esc(customer == null ? null : customer.name())).append("<br />")
                .append(esc(customer == null ? null : customer.email())).append("<br />")
                .append(esc(customer == null ? null : customer.phoneNumber())).append("</div></div>");

        html.append("<table><thead><tr><th>Item</th><th>Unit Price</th><th>Qty</th>")
                .append("<th>Discount</th></tr></thead><tbody>");
        List<BillingItemDto> items = document == null || document.billingItems() == null
                ? List.of()
                : document.billingItems();
        if (items.isEmpty()) {
            html.append("<tr><td colspan=\"4\">No items</td></tr>");
        } else {
            for (BillingItemDto item : items) {
                html.append("<tr><td>").append(esc(item.itemName()))
                        .append("</td><td>").append(esc(number(item.unitPrice())))
                        .append("</td><td>").append(esc(item.quantity() == null ? "" : item.quantity().toString()))
                        .append("</td><td>").append(esc(item.discount()))
                        .append("</td></tr>");
            }
        }
        html.append("</tbody></table>");

        html.append("<table class=\"summary\">")
                .append(summaryRow("Sub Total", currency, document == null ? null : document.subTotal()))
                .append(summaryRow("Sales Tax", currency, document == null ? null : document.salesTax()))
                .append(summaryRow("Total", currency, document == null ? null : document.total()))
                .append(summaryRow("Amount Received", currency, request.totalAmountReceived()))
                .append(summaryRow("Balance Due", currency, request.balanceDue()))
                .append("</table>");

        html.append("</body></html>");
        return html.toString();
    }

    private static String summaryRow(String label, String currency, Double amount) {
        if (amount == null) {
            return "";
        }
        return "<tr><td>" + esc(label) + "</td><td class=\"right\">"
                + esc(currency + " " + number(amount)) + "</td></tr>";
    }

    private static String senderName(PdfProfileDto profile) {
        if (profile == null) {
            return "";
        }
        if (profile.businessName() != null && !profile.businessName().isBlank()) {
            return profile.businessName();
        }
        return value(profile.firstName(), "") + " " + value(profile.lastName(), "");
    }

    private static String addressLine(PdfProfileDto profile) {
        if (profile == null) {
            return "";
        }
        return String.join(
                ", ",
                List.of(value(profile.address(), ""), value(profile.city(), ""), value(profile.country(), "")));
    }

    private static String number(Double value) {
        return value == null ? "" : Optional.of(value).map(String::valueOf).orElse("");
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
