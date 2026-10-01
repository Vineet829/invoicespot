package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.common.MoneyFormat;
import com.invoicespot.common.QrCode;
import com.invoicespot.customer.Customer;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.payment.UpiPayment;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.PdfTemplate;
import com.invoicespot.tax.AmountInWords;
import com.invoicespot.tax.GstStates;
import com.invoicespot.tax.SupplyType;
import com.invoicespot.tax.TaxRegime;
import com.invoicespot.upload.UploadService;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        Context context = context(settings.getPdfTemplate());
        context.setVariable("pdf", view(document, seller, settings));
        return templateEngine.process("pdf/document", context);
    }

    byte[] renderReceipt(
            Document document,
            Payment payment,
            String number,
            BigDecimal paidToDate,
            Business seller,
            PdfTemplate template) {
        return toPdf(receiptHtml(document, payment, number, paidToDate, seller, template));
    }

    // A payment receipt: what was received, how, any tax the client withheld
    // from it, and what the document still owed once this payment had been
    // made.
    String receiptHtml(
            Document document,
            Payment payment,
            String number,
            BigDecimal paidToDate,
            Business seller,
            PdfTemplate template) {
        CustomerSnapshot client = Optional.ofNullable(document.getCustomerSnapshot()).orElseGet(CustomerSnapshot::new);
        BigDecimal total = orZero(document.getTotal());
        // Credit notes issued by the day of the payment, so the receipt stays
        // as it was on that day.
        BigDecimal creditedToDate = document.getCreditNotes().stream()
                .filter(note -> note.state() == DocumentState.ISSUED
                        && !note.getIssueDate().isAfter(payment.getDatePaid()))
                .map(note -> orZero(note.getTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedToDate = document.getRefunds().stream()
                .filter(refund -> !refund.getDateRefunded().isAfter(payment.getDatePaid()))
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Context context = context(template);
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
                withheld(payment) ? money(payment.getTdsAmount(), currency) : null,
                withheld(payment) ? money(payment.getAmountPaid().add(payment.getTdsAmount()), currency) : null,
                payment.getPaymentMethod().getValue(),
                blankToNull(payment.getAdditionalInfo()),
                Stream.of(
                                new Total(documentTitle + " total", money(total, currency)),
                                new Total("Paid to date", money(paidToDate, currency)),
                                creditedToDate.signum() > 0
                                        ? new Total("Credited to date", money(creditedToDate, currency))
                                        : null,
                                refundedToDate.signum() > 0
                                        ? new Total("Refunded to date", money(refundedToDate, currency))
                                        : null)
                        .filter(Objects::nonNull)
                        .toList(),
                money(
                        total.subtract(paidToDate)
                                .subtract(creditedToDate)
                                .add(refundedToDate)
                                .max(BigDecimal.ZERO),
                        currency)));
        return templateEngine.process("pdf/receipt", context);
    }

    byte[] renderStatement(
            Business seller,
            Customer customer,
            LocalDate day,
            List<Statements.Account> accounts,
            PdfTemplate template) {
        return toPdf(statementHtml(seller, customer, day, accounts, template));
    }

    String statementHtml(
            Business seller,
            Customer customer,
            LocalDate day,
            List<Statements.Account> accounts,
            PdfTemplate template) {
        Context context = context(template);
        context.setVariable("statement", new StatementView(
                seller(seller),
                DATE.format(day),
                new Party(null, value(customer.getName()), lines(
                        customer.getEmail(),
                        customer.getPhoneNumber(),
                        joined(customer.getAddress(), customer.getCity(), customer.getCountry()),
                        customer.getAccountNo() == null ? null : "Account no. " + customer.getAccountNo(),
                        vatTin(customer.getVatTinNo()),
                        customer.getGstin() == null ? null : "GSTIN " + customer.getGstin())),
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
    // document is marked void, and says when and why. Lines taxed at more
    // than one rate show each line's rate and a table of what each rate
    // charged. An unpaid invoice says how to pay it, and in rupees with a UPI
    // ID carries a QR code to pay its balance.
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
            boolean ratePerLine,
            List<Total> totals,
            List<Rate> rates,
            String balanceLabel,
            String balance,
            String notes,
            String terms,
            String reference,
            String reason,
            String rateHeader,
            Gst gst,
            PaymentSection payment,
            Upi upi) {}

    // A UPI QR code as a PNG data URI, and what it asks the payer to do.
    public record Upi(String image, String caption) {}

    // What a GST invoice adds: each rate's CGST and SGST (UTGST in a union
    // territory without a legislature) or IGST, whether the recipient pays
    // the tax, the amount in words, an export's endorsement, the exchange
    // rate a foreign-currency invoice was valued at, and who signs it.
    public record Gst(
            String sgstLabel,
            List<GstRate> rates,
            List<HsnRate> hsn,
            String reverseCharge,
            String amountInWords,
            String endorsement,
            String exchangeNote,
            String signatory) {}

    public record GstRate(String rate, String taxable, String cgst, String sgst, String igst, String tax) {}

    public record HsnRate(
            String hsnSac, String rate, String taxable, String cgst, String sgst, String igst, String tax) {}

    public record Line(
            int index,
            String name,
            String quantity,
            String unitPrice,
            String discount,
            String taxRate,
            String amount,
            String hsnSac) {}

    public record Rate(String rate, String taxable, String tax) {}

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
            String withheld,
            String settles,
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
                                account.credited().signum() > 0
                                        ? new Total("Credited", money(account.credited(), account.currency()))
                                        : null,
                                account.refunded().signum() > 0
                                        ? new Total("Refunded", money(account.refunded(), account.currency()))
                                        : null,
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
                    quantity(item),
                    money(item.getUnitPrice(), currency),
                    percent(item.getDiscount()),
                    percent(DocumentTotals.rate(document, item)),
                    money(DocumentTotals.lineTotal(item), currency),
                    item.getHsnSac()));
        }

        BigDecimal received = orZero(document.getTotalAmountReceived());
        BigDecimal total = orZero(document.getTotal());
        boolean draft = document.isDraft();
        boolean voided = document.isVoid();
        String taxLabel = Objects.requireNonNullElse(document.getTaxLabel(), settings.getTaxLabel());
        boolean gst = document.getTaxRegime() == TaxRegime.GST_INDIA && document.getSupplyType() != null;
        List<Total> totals = new ArrayList<>();
        totals.add(new Total("Subtotal", money(document.getSubTotal(), currency)));
        List<Rate> rates = new ArrayList<>();
        for (TaxSummary summary : document.getTaxSummaries()) {
            if (gst) {
                totals.addAll(gstRows(document, summary, currency));
            } else {
                totals.add(new Total(
                        taxLabel + " (" + percent(summary.getRate()) + ")", money(summary.getTax(), currency)));
            }
            rates.add(new Rate(
                    percent(summary.getRate()),
                    money(summary.getTaxable(), currency),
                    money(summary.getTax(), currency)));
        }
        boolean ratePerLine = rates.size() > 1 || gst;
        boolean quotation = type == DocumentType.QUOTATION;
        boolean creditNote = type == DocumentType.CREDIT_NOTE;
        if (!quotation && !creditNote) {
            totals.add(new Total("Total", money(total, currency)));
            totals.add(new Total("Amount paid", money(received, currency)));
            if (orZero(document.getTotalTds()).signum() > 0) {
                totals.add(new Total("Tax withheld", money(document.getTotalTds(), currency)));
            }
            // Each issued credit note takes its total off, and money paid back
            // is owed again.
            for (Document note : document.getCreditNotes()) {
                if (note.state() == DocumentState.ISSUED) {
                    totals.add(new Total(
                            "Credited (" + note.getDocumentNumber() + ")", "−" + money(note.getTotal(), currency)));
                }
            }
            if (orZero(document.getTotalRefunded()).signum() > 0) {
                totals.add(new Total("Refunded", money(document.getTotalRefunded(), currency)));
            }
        }
        BigDecimal refundDue = voided ? BigDecimal.ZERO : DocumentTotals.refundDue(document);
        Document credited = document.getCredited();
        PaymentSection payment = PaymentSection.of(document, settings.getPaymentDetails()).orElse(null);

        return new View(
                draft ? "DRAFT" : voided ? "VOID" : null,
                voided ? "Voided on " + date(document.getVoidedAt(), zone) + ": " + document.getVoidReason() : null,
                gst ? gstSeller(document, seller) : seller(document, seller),
                gst ? gstTitle(document) : type.getValue(),
                draft ? null : value(document.getDocumentNumber()),
                document.getIssueDate() == null ? "On issue" : DATE.format(document.getIssueDate()),
                quotation ? "Valid until" : "Due",
                date(document.getDueDate(), zone),
                quotation || creditNote || draft || voided ? null : statusLabel(document),
                statusTone(document.getStatus()),
                new Party(null, value(client.getName()), lines(
                        client.getEmail(),
                        client.getPhoneNumber(),
                        joined(client.getAddress(), client.getCity(), client.getCountry()),
                        client.getAccountNo() == null ? null : "Account no. " + client.getAccountNo(),
                        vatTin(client.getVatTinNo()),
                        client.getGstin() == null ? (gst ? "Unregistered" : null) : "GSTIN " + client.getGstin(),
                        gst ? placeOfSupply(document) : null,
                        gst && document.getSupplyType().export() && !isBlank(client.getCountry())
                                ? "Country of destination: " + client.getCountry()
                                : null)),
                currency,
                items,
                ratePerLine,
                totals,
                ratePerLine && !gst ? rates : List.of(),
                quotation ? "Total" : creditNote ? "Total credit" : refundDue.signum() > 0 ? "Refund due" : "Balance due",
                money(
                        quotation || creditNote
                                ? total
                                : refundDue.signum() > 0
                                        ? refundDue
                                        : voided ? BigDecimal.ZERO : DocumentTotals.balanceDue(document),
                        currency),
                blankToNull(document.getAdditionalInfo()),
                blankToNull(document.getTermsConditions()),
                credited == null ? null : reference(credited),
                blankToNull(document.getCreditReason()),
                gst ? "GST" : "Tax",
                gst ? gst(document, seller, currency) : null,
                payment,
                payment == null || payment.upi() == null ? null : upi(payment.upi()));
    }

    private static Upi upi(UpiPayment upi) {
        return new Upi(
                QrCode.dataUri(upi.uri()),
                upi.amountIncluded()
                        ? "Scan to pay INR " + money(upi.amount(), "INR") + " with any UPI app"
                        : "Scan with any UPI app and enter the amount. UPI limits apply");
    }

    // A GST invoice is a tax invoice, or a bill of supply when nothing on it
    // is taxed and it isn't an export.
    private static String gstTitle(Document document) {
        String title = switch (document.getDocumentType()) {
            case CREDIT_NOTE -> "Credit Note";
            case QUOTATION -> "Quotation";
            default -> document.getSupplyType().export()
                            || document.getBillingItems().stream().anyMatch(item -> orZero(item.getTaxRate()).signum() > 0)
                    ? "Tax Invoice"
                    : "Bill of Supply";
        };
        return document.isDraft() ? "Draft " + title : title;
    }

    // Each rate's GST: CGST and SGST (or UTGST) at half the rate within the
    // state, IGST at the full rate otherwise. Under reverse charge the
    // recipient pays it, so it isn't in the total.
    private static List<Total> gstRows(Document document, TaxSummary summary, String currency) {
        if (summary.getRate().signum() == 0) {
            return List.of();
        }
        String payer = document.isReverseCharge() ? " (payable by the recipient)" : "";
        if (document.getSupplyType() == SupplyType.INTRA) {
            String half = percent(summary.getRate().divide(BigDecimal.valueOf(2)));
            return List.of(
                    new Total("CGST " + half + payer, money(summary.getCgst(), currency)),
                    new Total(sgstLabel(document) + " " + half + payer, money(summary.getSgst(), currency)));
        }
        return List.of(new Total("IGST " + percent(summary.getRate()) + payer, money(summary.getIgst(), currency)));
    }

    private static String sgstLabel(Document document) {
        return GstStates.isUnionTerritoryWithoutLegislature(document.getPlaceOfSupply()) ? "UTGST" : "SGST";
    }

    private static String placeOfSupply(Document document) {
        String place = document.getPlaceOfSupply();
        return GstStates.OUTSIDE_INDIA.equals(place)
                ? "Place of supply: Outside India (96)"
                : "Place of supply: " + place + " – " + GstStates.name(place);
    }

    private Gst gst(Document document, Business business, String currency) {
        SellerSnapshot kept = Objects.requireNonNullElseGet(document.getSeller(), () -> SellerSnapshot.of(business));
        String legal = isBlank(kept.getLegalName()) ? value(kept.getName()) : kept.getLegalName();
        return new Gst(
                sgstLabel(document),
                document.getTaxSummaries().stream()
                        .map(summary -> new GstRate(
                                percent(summary.getRate()),
                                money(summary.getTaxable(), currency),
                                money(summary.getCgst(), currency),
                                money(summary.getSgst(), currency),
                                money(summary.getIgst(), currency),
                                money(summary.getTax(), currency)))
                        .toList(),
                // By HSN or SAC code, once any line has one.
                document.getBillingItems().stream().anyMatch(item -> item.getHsnSac() != null)
                        ? HsnSummary.of(document).stream()
                                .map(row -> new HsnRate(
                                        row.hsnSac() == null ? "—" : row.hsnSac(),
                                        percent(row.rate()),
                                        money(row.taxable(), currency),
                                        money(row.cgst(), currency),
                                        money(row.sgst(), currency),
                                        money(row.igst(), currency),
                                        money(row.tax(), currency)))
                                .toList()
                        : List.of(),
                document.isReverseCharge() ? "Yes" : "No",
                AmountInWords.of(orZero(document.getTotal()), document.getCurrency()),
                switch (document.getSupplyType()) {
                    case EXPORT_LUT -> "Supply meant for export under LUT " + value(document.getLutReference())
                            + " without payment of IGST";
                    case EXPORT_IGST -> "Supply meant for export on payment of IGST";
                    default -> null;
                },
                exchangeNote(document),
                "For " + legal);
    }

    // "1 USD = ₹83.2500; taxable value ₹1,00,000.00".
    private static String exchangeNote(Document document) {
        if (document.getExchangeRate() == null || !"INR".equals(document.getExchangeBase())) {
            return null;
        }
        BigDecimal taxable = orZero(document.getSubTotal())
                .multiply(document.getExchangeRate())
                .setScale(2, RoundingMode.HALF_UP);
        return "1 " + document.getCurrency() + " = ₹" + document.getExchangeRate().setScale(4, RoundingMode.HALF_UP)
                + "; taxable value ₹" + money(taxable, "INR");
    }

    // The seller as GST asks: its GSTIN, and the state it is registered in.
    private Party gstSeller(Document document, Business business) {
        SellerSnapshot kept = Objects.requireNonNullElseGet(document.getSeller(), () -> SellerSnapshot.of(business));
        return new Party(logo(kept.getLogo()), value(kept.getName()), senderLines(document, kept));
    }

    // The seller's lines as a document shows them; under GST, its tax ID is
    // its GSTIN, followed by the state it is registered in.
    static List<String> senderLines(Document document, SellerSnapshot kept) {
        if (document.getTaxRegime() != TaxRegime.GST_INDIA || document.getSupplyType() == null) {
            return senderLines(kept);
        }
        List<String> lines = new ArrayList<>(senderLines(kept).stream()
                .map(line -> line.startsWith("Tax ID ") ? "GSTIN " + line.substring("Tax ID ".length()) : line)
                .toList());
        String state = document.getSellerStateCode();
        if (state != null) {
            lines.add("State: " + GstStates.name(state) + " (" + state + ")");
        }
        return lines;
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

    // Every PDF starts from the business's template: Classic, or a theme
    // laid over it.
    private static Context context(PdfTemplate template) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("theme", template.theme());
        return context;
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

    // A quantity as it was typed, with what it counts: "1.5 hours", "2".
    private static String quantity(BillingItem item) {
        String amount = BillingItemResponse.plain(item.getQuantity()).toPlainString();
        return item.getUnit() == null ? amount : amount + " " + item.getUnit();
    }

    private static boolean withheld(Payment payment) {
        return payment.getTdsAmount() != null && payment.getTdsAmount().signum() > 0;
    }

    private static String vatTin(String value) {
        return value == null || value.isBlank() || value.trim().equals("0") ? null : "VAT / TIN " + value;
    }

    // "Against invoice INV-0012 dated Sep 1, 2026".
    private static String reference(Document credited) {
        return "Against " + credited.getDocumentType().getValue().toLowerCase(Locale.ROOT) + " "
                + value(credited.getDocumentNumber())
                + (credited.getIssueDate() == null ? "" : " dated " + DATE.format(credited.getIssueDate()));
    }

    // A document settled by credit notes alone reads as credited.
    private static String statusLabel(Document document) {
        return document.getStatus() == DocumentStatus.PAID
                        && orZero(document.getTotalAmountReceived()).signum() == 0
                        && orZero(document.getTotalCredited()).signum() > 0
                ? "Credited"
                : statusLabel(document.getStatus());
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
