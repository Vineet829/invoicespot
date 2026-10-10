package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.CsvFile;
import com.invoicespot.document.BillingItem;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentRepository;
import com.invoicespot.document.DocumentTotals;
import com.invoicespot.document.DocumentType;
import com.invoicespot.document.TaxSummary;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.report.dto.GstFile;
import com.invoicespot.report.dto.GstReturnsResponse;
import com.invoicespot.report.dto.Gstr3bRow;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.tax.Apportion;
import com.invoicespot.tax.GstStates;
import com.invoicespot.tax.SupplyType;
import com.invoicespot.tax.Uqc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Helper files for India's GST returns, from the invoices, receipts and credit
// notes issued in GST mode and dated in the chosen months: the GSTR-1 tables
// in the columns of the GST offline tool's templates, and the GSTR-3B figures
// they add up to. Amounts are in rupees; a document in another currency is
// valued at its own rate, each rate's taxable value rounded to the paisa and
// its tax worked out on that. They are a starting point to check against the
// books and the GST portal, not a return.
@Service
public class GstReturns {

    // Above this, an invoice to an unregistered buyer in another state is a
    // B2C large one; its credit notes follow it.
    public static final BigDecimal B2CL_LIMIT = new BigDecimal("100000");
    static final int MOST_MONTHS = 12;
    static final String MISSING_HSN = "(missing HSN/SAC)";
    private static final YearMonth FIRST_MONTH = YearMonth.of(2017, 7);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);
    private static final List<DocumentType> TYPES =
            List.of(DocumentType.INVOICE, DocumentType.RECEIPT, DocumentType.CREDIT_NOTE);
    private static final List<String> HSN_HEADER = List.of("HSN", "Description", "UQC", "Total Quantity",
            "Total Value", "Rate", "Taxable Value", "Integrated Tax Amount", "Central Tax Amount",
            "State/UT Tax Amount", "Cess Amount");

    // The files, by the key their address uses.
    enum Section {
        B2B("b2b", "gstr1-b2b", "B2B invoices (4A, 4B, 6B, 6C)"),
        B2CL("b2cl", "gstr1-b2cl", "B2C large invoices (5A, 5B)"),
        B2CS("b2cs", "gstr1-b2cs", "B2C small supplies (7)"),
        EXP("exp", "gstr1-exp", "Exports (6A)"),
        CDNR("cdnr", "gstr1-cdnr", "Credit notes to registered buyers (9B)"),
        CDNUR("cdnur", "gstr1-cdnur", "Credit notes to unregistered buyers (9B)"),
        HSN_B2B("hsn-b2b", "gstr1-hsn-b2b", "HSN summary of supplies to registered buyers (12)"),
        HSN_B2C("hsn-b2c", "gstr1-hsn-b2c", "HSN summary of supplies to unregistered buyers (12)"),
        GSTR3B("gstr3b", "gstr3b", "GSTR-3B outward supplies (3.1, 3.2)");

        private final String key;
        private final String file;
        private final String label;

        Section(String key, String file, String label) {
            this.key = key;
            this.file = file;
            this.label = label;
        }

        static Section of(String key) {
            return Arrays.stream(values())
                    .filter(section -> section.key.equals(key))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That GST file does not exist"));
        }
    }

    // Where an invoice or receipt goes in GSTR-1; a credit note goes where the
    // document it credits went.
    private enum Kind {
        B2B,
        B2CL,
        B2CS,
        EXPORT
    }

    // The months a set of files covers, first to last.
    record Months(YearMonth first, YearMonth last) {

        LocalDate from() {
            return first.atDay(1);
        }

        LocalDate to() {
            return last.atEndOfMonth();
        }

        String name() {
            return first.equals(last) ? first.toString() : first + "-to-" + last;
        }
    }

    // A rate's taxable value and tax in rupees.
    private record Rated(BigDecimal rate, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {

        BigDecimal tax() {
            return cgst.add(sgst).add(igst);
        }
    }

    // A document as the returns see it, with its value in rupees: what it was
    // billed for, tax included unless the client pays it under reverse charge.
    // `placed` is the document that decides where it goes: itself, or the one
    // a credit note credits.
    private record Entry(Document document, Document placed, Kind kind, BigDecimal value, List<Rated> rates) {

        boolean creditNote() {
            return document.getDocumentType() == DocumentType.CREDIT_NOTE;
        }

        // Invoices and receipts add to a total; credit notes take off it.
        BigDecimal signed(BigDecimal amount) {
            return creditNote() ? amount.negate() : amount;
        }

        SupplyType supply() {
            return document.getSupplyType();
        }
    }

    // One row of the HSN summary, as it adds up; codes in order, lines
    // without one last.
    private record HsnKey(String hsn, String uqc, BigDecimal rate) {}

    private static final Comparator<HsnKey> HSN_ORDER = Comparator
            .comparing((HsnKey key) -> key.hsn().equals(MISSING_HSN))
            .thenComparing(HsnKey::hsn)
            .thenComparing(HsnKey::uqc)
            .thenComparing(HsnKey::rate);

    private static final class HsnSum {

        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal taxable = ZERO;
        private BigDecimal cgst = ZERO;
        private BigDecimal sgst = ZERO;
        private BigDecimal igst = ZERO;
    }

    private record Returns(Months months, List<Entry> entries, long skipped, long unconverted, long missingHsn) {}

    private final DocumentRepository documentRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public GstReturns(DocumentRepository documentRepository, SettingsService settingsService, Clock clock) {
        this.documentRepository = documentRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GstReturnsResponse summary(BusinessAccess access, String from, String to) {
        Returns returns = load(access, from, to);
        return new GstReturnsResponse(
                true,
                returns.months().first(),
                returns.months().last(),
                B2CL_LIMIT,
                Arrays.stream(Section.values())
                        .filter(section -> section != Section.GSTR3B)
                        .map(section -> new GstFile(section.key, section.label, rows(section, returns).size()))
                        .toList(),
                gstr3b(returns.entries()),
                returns.entries().size(),
                returns.skipped(),
                returns.unconverted(),
                returns.missingHsn());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CsvFile csv(BusinessAccess access, String key, String from, String to) {
        Section section = Section.of(key);
        Returns returns = load(access, from, to);
        String name = "invoice-spot-" + section.file + "-" + returns.months().name() + ".csv";
        if (section == Section.GSTR3B) {
            return CsvFile.of(
                    name,
                    List.of("Table", "Nature of supplies", "Total taxable value", "Integrated tax", "Central tax",
                            "State/UT tax", "Cess"),
                    gstr3b(returns.entries()).stream()
                            .map(row -> Arrays.<Object>asList(
                                    row.table(), row.description(), row.taxable(), row.igst(), row.cgst(), row.sgst(),
                                    null))
                            .toList());
        }
        return CsvFile.of(name, header(section), rows(section, returns));
    }

    private Returns load(BusinessAccess access, String from, String to) {
        ZoneId zone = ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone());
        Months months = months(from, to, YearMonth.now(clock.withZone(zone)));
        List<Entry> entries = new ArrayList<>();
        long unconverted = 0;
        long missingHsn = 0;
        for (Document document : documentRepository.findGstDatedIn(
                access.businessPkid(), TYPES, months.from(), months.to())) {
            Document placed = document.getDocumentType() == DocumentType.CREDIT_NOTE
                    ? document.getCredited()
                    : document;
            if (!inRupees(document) || !inRupees(placed)) {
                unconverted++;
                continue;
            }
            List<Rated> rates = rates(document);
            entries.add(new Entry(document, placed, kind(placed), value(document, rates), rates));
            missingHsn += document.getBillingItems().stream()
                    .filter(item -> item.getHsnSac() == null)
                    .count();
        }
        long skipped = documentRepository.countOutsideGstDatedIn(
                access.businessPkid(), TYPES, months.from(), months.to());
        return new Returns(months, entries, skipped, unconverted, missingHsn);
    }

    // The months asked for, last month when none were; one alone is both
    // first and last.
    static Months months(String from, String to, YearMonth current) {
        YearMonth first = month(from);
        YearMonth last = month(to);
        if (first == null && last == null) {
            first = current.minusMonths(1);
            last = first;
        } else if (first == null) {
            first = last;
        } else if (last == null) {
            last = first;
        }
        if (last.isAfter(current)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose months up to this one");
        }
        if (first.isBefore(FIRST_MONTH)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GST returns start from July 2017");
        }
        if (first.isAfter(last)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The first month can't come after the last");
        }
        if (ChronoUnit.MONTHS.between(first, last) >= MOST_MONTHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose at most " + MOST_MONTHS + " months");
        }
        return new Months(first, last);
    }

    private static YearMonth month(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter months as YYYY-MM");
        }
    }

    // A document in another currency counts once it has a rate to rupees,
    // which GST mode asks for before issuing it.
    private static boolean inRupees(Document document) {
        return document.getCurrency() == null
                || "INR".equals(document.getCurrency())
                || (document.getExchangeRate() != null && "INR".equals(document.getExchangeBase()));
    }

    private static BigDecimal rateToRupees(Document document) {
        return document.getCurrency() == null || "INR".equals(document.getCurrency())
                ? null
                : document.getExchangeRate();
    }

    // Each rate's value and tax as charged, or in rupees at the document's
    // rate: the taxable value rounded first, and the tax worked out on it.
    private static List<Rated> rates(Document document) {
        BigDecimal fx = rateToRupees(document);
        return document.getTaxSummaries().stream()
                .sorted(Comparator.comparing(TaxSummary::getRate))
                .map(summary -> fx == null
                        ? new Rated(summary.getRate(), summary.getTaxable(), summary.getCgst(), summary.getSgst(),
                                summary.getIgst())
                        : taxed(summary.getRate(), money(summary.getTaxable().multiply(fx)), document.getSupplyType()))
                .toList();
    }

    private static Rated taxed(BigDecimal rate, BigDecimal taxable, SupplyType supply) {
        return switch (supply) {
            case INTRA -> {
                BigDecimal half = taxable.multiply(rate).divide(TWO_HUNDRED, 2, RoundingMode.HALF_UP);
                yield new Rated(rate, taxable, half, half, ZERO);
            }
            case EXPORT_LUT -> new Rated(rate, taxable, ZERO, ZERO, ZERO);
            case INTER, EXPORT_IGST ->
                new Rated(rate, taxable, ZERO, ZERO, money(taxable.multiply(rate.movePointLeft(2))));
        };
    }

    private static BigDecimal value(Document document, List<Rated> rates) {
        BigDecimal value = ZERO;
        for (Rated rate : rates) {
            value = value.add(rate.taxable()).add(document.isReverseCharge() ? ZERO : rate.tax());
        }
        return value;
    }

    // An export; a sale to a registered buyer; a large one to an unregistered
    // buyer in another state; or any other sale to an unregistered buyer.
    private static Kind kind(Document document) {
        if (document.getSupplyType().export()) {
            return Kind.EXPORT;
        }
        if (gstin(document) != null) {
            return Kind.B2B;
        }
        return document.getSupplyType() == SupplyType.INTER
                        && value(document, rates(document)).compareTo(B2CL_LIMIT) > 0
                ? Kind.B2CL
                : Kind.B2CS;
    }

    private static String gstin(Document document) {
        return document.getCustomerSnapshot() == null ? null : document.getCustomerSnapshot().getGstin();
    }

    private static List<String> header(Section section) {
        return switch (section) {
            case B2B -> List.of("GSTIN/UIN of Recipient", "Receiver Name", "Invoice Number", "Invoice date",
                    "Invoice Value", "Place Of Supply", "Reverse Charge", "Applicable % of Tax Rate", "Invoice Type",
                    "E-Commerce GSTIN", "Rate", "Taxable Value", "Cess Amount");
            case B2CL -> List.of("Invoice Number", "Invoice date", "Invoice Value", "Place Of Supply",
                    "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount", "E-Commerce GSTIN");
            case B2CS -> List.of("Type", "Place Of Supply", "Applicable % of Tax Rate", "Rate", "Taxable Value",
                    "Cess Amount", "E-Commerce GSTIN");
            case EXP -> List.of("Export Type", "Invoice Number", "Invoice date", "Invoice Value", "Port Code",
                    "Shipping Bill Number", "Shipping Bill Date", "Rate", "Taxable Value", "Cess Amount");
            case CDNR -> List.of("GSTIN/UIN of Recipient", "Receiver Name", "Note Number", "Note Date", "Note Type",
                    "Place Of Supply", "Reverse Charge", "Note Supply Type", "Note Value", "Applicable % of Tax Rate",
                    "Rate", "Taxable Value", "Cess Amount");
            case CDNUR -> List.of("UR Type", "Note Number", "Note Date", "Note Type", "Place Of Supply", "Note Value",
                    "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount");
            case HSN_B2B, HSN_B2C -> HSN_HEADER;
            case GSTR3B -> throw new IllegalArgumentException(section.key);
        };
    }

    private static List<List<Object>> rows(Section section, Returns returns) {
        List<Entry> entries = returns.entries();
        return switch (section) {
            case B2B -> perRate(entries, entry -> !entry.creditNote() && entry.kind() == Kind.B2B, GstReturns::b2b);
            case B2CL -> perRate(entries, entry -> !entry.creditNote() && entry.kind() == Kind.B2CL, GstReturns::b2cl);
            case B2CS -> b2cs(entries);
            case EXP -> perRate(entries, entry -> !entry.creditNote() && entry.kind() == Kind.EXPORT, GstReturns::exp);
            case CDNR -> perRate(entries, entry -> entry.creditNote() && entry.kind() == Kind.B2B, GstReturns::cdnr);
            case CDNUR -> perRate(
                    entries,
                    entry -> entry.creditNote() && (entry.kind() == Kind.B2CL || entry.kind() == Kind.EXPORT),
                    GstReturns::cdnur);
            case HSN_B2B -> hsn(entries, true);
            case HSN_B2C -> hsn(entries, false);
            case GSTR3B -> throw new IllegalArgumentException(section.key);
        };
    }

    private static List<Object> b2b(Entry entry, Rated rate) {
        Document document = entry.document();
        return List.of(
                gstin(document), name(document), document.getDocumentNumber(), date(entry), entry.value(),
                place(document), reverseCharge(entry), "", "Regular B2B", "", plain(rate.rate()), rate.taxable(), "");
    }

    private static List<Object> b2cl(Entry entry, Rated rate) {
        Document document = entry.document();
        return List.of(
                document.getDocumentNumber(), date(entry), entry.value(), place(document), "", plain(rate.rate()),
                rate.taxable(), "", "");
    }

    // With IGST paid, or under the business's letter of undertaking.
    private static List<Object> exp(Entry entry, Rated rate) {
        return List.of(
                entry.supply() == SupplyType.EXPORT_IGST ? "WPAY" : "WOPAY", entry.document().getDocumentNumber(),
                date(entry), entry.value(), "", "", "", plain(rate.rate()), rate.taxable(), "");
    }

    // The buyer's GSTIN is the one on the invoice credited.
    private static List<Object> cdnr(Entry entry, Rated rate) {
        Document document = entry.document();
        return List.of(
                gstin(entry.placed()), name(document), document.getDocumentNumber(), date(entry), "C",
                place(document), reverseCharge(entry), "Regular B2B", entry.value(), "", plain(rate.rate()),
                rate.taxable(), "");
    }

    // On a B2C large invoice, or on an export with or without IGST paid.
    private static List<Object> cdnur(Entry entry, Rated rate) {
        Document document = entry.document();
        boolean large = entry.kind() == Kind.B2CL;
        return List.of(
                large ? "B2CL" : entry.supply() == SupplyType.EXPORT_IGST ? "EXPWP" : "EXPWOP",
                document.getDocumentNumber(), date(entry), "C", large ? place(document) : "", entry.value(), "",
                plain(rate.rate()), rate.taxable(), "");
    }

    // A row per document and rate, in date order.
    private static List<List<Object>> perRate(
            List<Entry> entries, Predicate<Entry> included, BiFunction<Entry, Rated, List<Object>> row) {
        return entries.stream()
                .filter(included)
                .flatMap(entry -> entry.rates().stream().map(rate -> row.apply(entry, rate)))
                .toList();
    }

    // Small sales to unregistered buyers, per place of supply and rate, less
    // the credit notes on them; a total of nothing is left out.
    private static List<List<Object>> b2cs(List<Entry> entries) {
        Map<String, Map<BigDecimal, BigDecimal>> places = new TreeMap<>();
        for (Entry entry : entries) {
            if (entry.kind() != Kind.B2CS) {
                continue;
            }
            for (Rated rate : entry.rates()) {
                places.computeIfAbsent(place(entry.document()), key -> new TreeMap<>())
                        .merge(BillingItemResponse.plain(rate.rate()), entry.signed(rate.taxable()), BigDecimal::add);
            }
        }
        List<List<Object>> rows = new ArrayList<>();
        places.forEach((place, rates) -> rates.forEach((rate, taxable) -> {
            if (taxable.signum() != 0) {
                rows.add(List.<Object>of("OE", place, "", rate.toPlainString(), taxable, "", ""));
            }
        }));
        return rows;
    }

    // Every line by HSN or SAC code, unit quantity code and rate, for sales to
    // registered buyers or to everyone else, less credit notes. Each rate's
    // taxable value and tax are apportioned among a document's codes, so the
    // rows add up to the documents.
    private static List<List<Object>> hsn(List<Entry> entries, boolean registered) {
        Map<HsnKey, HsnSum> sums = new TreeMap<>(HSN_ORDER);
        for (Entry entry : entries) {
            if ((entry.kind() == Kind.B2B) != registered) {
                continue;
            }
            BigDecimal fx = rateToRupees(entry.document());
            for (Rated rate : entry.rates()) {
                Map<HsnKey, BigDecimal> values = new TreeMap<>(HSN_ORDER);
                Map<HsnKey, BigDecimal> quantities = new LinkedHashMap<>();
                for (BillingItem item : entry.document().getBillingItems()) {
                    BigDecimal itemRate = item.getTaxRate() == null ? BigDecimal.ZERO : item.getTaxRate();
                    if (itemRate.compareTo(rate.rate()) != 0) {
                        continue;
                    }
                    String uqc = Uqc.of(item.getHsnSac(), item.getUnit());
                    HsnKey key = new HsnKey(
                            item.getHsnSac() == null ? MISSING_HSN : item.getHsnSac(),
                            uqc,
                            BillingItemResponse.plain(rate.rate()));
                    values.merge(key, DocumentTotals.lineTotal(item), BigDecimal::add);
                    quantities.merge(
                            key,
                            Uqc.NONE.equals(uqc) || item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity(),
                            BigDecimal::add);
                }
                Map<HsnKey, BigDecimal> taxable = fx == null ? values : Apportion.of(values, fx, rate.taxable());
                BigDecimal half = rate.rate().movePointLeft(2).divide(BigDecimal.valueOf(2));
                Map<HsnKey, BigDecimal> cgst = Apportion.of(taxable, half, rate.cgst());
                Map<HsnKey, BigDecimal> sgst = Apportion.of(taxable, half, rate.sgst());
                Map<HsnKey, BigDecimal> igst = Apportion.of(taxable, rate.rate().movePointLeft(2), rate.igst());
                for (HsnKey key : values.keySet()) {
                    HsnSum sum = sums.computeIfAbsent(key, ignored -> new HsnSum());
                    sum.quantity = sum.quantity.add(entry.signed(quantities.get(key)));
                    sum.taxable = sum.taxable.add(entry.signed(taxable.get(key)));
                    sum.cgst = sum.cgst.add(entry.signed(cgst.get(key)));
                    sum.sgst = sum.sgst.add(entry.signed(sgst.get(key)));
                    sum.igst = sum.igst.add(entry.signed(igst.get(key)));
                }
            }
        }
        List<List<Object>> rows = new ArrayList<>();
        sums.forEach((key, sum) -> rows.add(List.<Object>of(
                key.hsn(),
                "",
                key.uqc(),
                plain(sum.quantity),
                sum.taxable.add(sum.igst).add(sum.cgst).add(sum.sgst),
                key.rate().toPlainString(),
                sum.taxable,
                sum.igst,
                sum.cgst,
                sum.sgst,
                "")));
        return rows;
    }

    // The outward supplies GSTR-3B asks for, less credit notes: taxable ones,
    // exports, nil-rated or exempt ones, and of the taxable ones, those to
    // unregistered buyers in other states by place of supply. Supplies whose
    // tax the client pays under reverse charge are listed apart.
    private static List<Gstr3bRow> gstr3b(List<Entry> entries) {
        BigDecimal[] taxable = {ZERO, ZERO, ZERO, ZERO, ZERO};
        BigDecimal[] taxed = {ZERO, ZERO, ZERO, ZERO};
        Map<String, BigDecimal[]> unregistered = new TreeMap<>();
        for (Entry entry : entries) {
            for (Rated rate : entry.rates()) {
                BigDecimal value = entry.signed(rate.taxable());
                if (entry.document().isReverseCharge()) {
                    taxable[4] = taxable[4].add(value);
                } else if (entry.supply().export()) {
                    taxable[1] = taxable[1].add(value);
                    taxed[3] = taxed[3].add(entry.signed(rate.igst()));
                } else if (rate.rate().signum() == 0) {
                    taxable[2] = taxable[2].add(value);
                } else {
                    taxable[0] = taxable[0].add(value);
                    taxed[0] = taxed[0].add(entry.signed(rate.igst()));
                    taxed[1] = taxed[1].add(entry.signed(rate.cgst()));
                    taxed[2] = taxed[2].add(entry.signed(rate.sgst()));
                    if (entry.supply() == SupplyType.INTER && entry.kind() != Kind.B2B) {
                        BigDecimal[] place = unregistered.computeIfAbsent(
                                place(entry.document()), key -> new BigDecimal[] {ZERO, ZERO});
                        place[0] = place[0].add(value);
                        place[1] = place[1].add(entry.signed(rate.igst()));
                    }
                }
            }
        }
        List<Gstr3bRow> rows = new ArrayList<>();
        rows.add(new Gstr3bRow(
                "3.1(a)", "Outward taxable supplies (other than zero rated, nil rated and exempted)",
                taxable[0], taxed[0], taxed[1], taxed[2]));
        rows.add(new Gstr3bRow("3.1(b)", "Outward taxable supplies (zero rated)", taxable[1], taxed[3], null, null));
        rows.add(new Gstr3bRow("3.1(c)", "Other outward supplies (nil rated, exempted)", taxable[2], null, null, null));
        unregistered.forEach((place, amounts) -> rows.add(new Gstr3bRow(
                "3.2", "Inter-state supplies to unregistered persons, " + place, amounts[0], amounts[1], null, null)));
        rows.add(new Gstr3bRow(
                null,
                "Outward supplies under reverse charge, whose tax the recipient pays (not in 3.1(a))",
                taxable[4],
                null,
                null,
                null));
        return rows;
    }

    private static String name(Document document) {
        return document.getCustomerSnapshot() == null || document.getCustomerSnapshot().getName() == null
                ? ""
                : document.getCustomerSnapshot().getName();
    }

    private static String date(Entry entry) {
        return DATE.format(entry.document().getIssueDate());
    }

    private static String reverseCharge(Entry entry) {
        return entry.document().isReverseCharge() ? "Y" : "N";
    }

    // As the offline tool writes it: "27-Maharashtra"; exports go to "96-Other
    // Countries".
    private static String place(Document document) {
        String code = document.getPlaceOfSupply();
        if (code == null) {
            return "";
        }
        return code + "-" + (GstStates.OUTSIDE_INDIA.equals(code) ? "Other Countries" : GstStates.name(code));
    }

    private static String plain(BigDecimal value) {
        return BillingItemResponse.plain(value).toPlainString();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
