package com.invoicespot.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessService;
import com.invoicespot.business.dto.BusinessProfileResponse;
import com.invoicespot.common.CsvFile;
import com.invoicespot.customer.CustomerSearch;
import com.invoicespot.customer.CustomerService;
import com.invoicespot.document.BillingItem;
import com.invoicespot.document.DocumentExport;
import com.invoicespot.document.DocumentSearch;
import com.invoicespot.document.DocumentTotals;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.expense.ExpenseSearch;
import com.invoicespot.expense.ExpenseService;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.EmailTemplateService;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.settings.dto.EmailTemplateResponse;
import com.invoicespot.settings.dto.SettingsResponse;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.dto.UserProfileResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Everything a business keeps in the app, in one ZIP file: a CSV file for each
// kind of record, the settings, business and profile as JSON, and a note on
// what each file holds. It is read in one snapshot, so the files agree with
// each other.
@Service
public class DataExport {

    public record Archive(String fileName, byte[] content) {}

    private static final String README = """
            Invoice Spot export, made on %s.

            documents.csv    Every invoice, quotation, receipt and credit note.
            line-items.csv   The lines on each document.
            document-tax.csv The tax on each document at each rate.
            payments.csv     Every payment and refund recorded on a document.
            clients.csv      Your clients, and what each owes in every currency.
            items.csv        Your saved products and services.
            expenses.csv     Every expense, with the tax paid on it.
            recurring.csv    The invoices that repeat, and how.
            settings.json    Your defaults for new documents, numbering and reminders.
            email-templates.json
                             The subject and message you wrote for each kind of email.
            business.json    Your business details, as documents show them.
            profile.json     Your account details.

            Dates follow your time zone, %s. Amounts are plain numbers in the
            currency of their document. CSV files are UTF-8 and open in any
            spreadsheet.
            """;

    private final DocumentExport documentExport;
    private final CustomerService customerService;
    private final ExpenseService expenseService;
    private final SettingsService settingsService;
    private final EmailTemplateService emailTemplates;
    private final BusinessService businessService;
    private final DemoAccountGuard demoAccountGuard;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DataExport(
            DocumentExport documentExport,
            CustomerService customerService,
            ExpenseService expenseService,
            SettingsService settingsService,
            EmailTemplateService emailTemplates,
            BusinessService businessService,
            DemoAccountGuard demoAccountGuard,
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            Clock clock) {
        this.documentExport = documentExport;
        this.customerService = customerService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.emailTemplates = emailTemplates;
        this.businessService = businessService;
        this.demoAccountGuard = demoAccountGuard;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Archive all(BusinessAccess access) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("README.txt", README.formatted(today, zone.getId()).getBytes(StandardCharsets.UTF_8));
        files.put("documents.csv", documentExport.documents(access, DocumentSearch.all()).content());
        files.put("line-items.csv", lineItems(businessPkid).content());
        files.put("document-tax.csv", documentTax(businessPkid).content());
        files.put("payments.csv", documentExport.payments(access).content());
        files.put(
                "clients.csv",
                customerService.csv(access, CustomerSearch.of(null, "name", "asc", 1, 1)).content());
        files.put("items.csv", items(businessPkid).content());
        files.put("expenses.csv", expenseService.csv(access, ExpenseSearch.all()).content());
        files.put("recurring.csv", recurring(businessPkid).content());
        files.put("settings.json", json(SettingsResponse.from(settings)));
        files.put(
                "email-templates.json",
                json(emailTemplates.list(access).stream().filter(EmailTemplateResponse::custom).toList()));
        files.put(
                "business.json",
                json(BusinessProfileResponse.from(businessService.require(businessPkid), access.role())));
        files.put(
                "profile.json",
                json(UserProfileResponse.from(
                        access.user(),
                        demoAccountGuard.isDemo(access.user()),
                        businessService.sharedOwnedBy(access.userPkid()))));
        return new Archive("invoice-spot-export-" + today + ".zip", zip(files));
    }

    // Each line's total is worked out the way its document's totals are.
    private CsvFile lineItems(Long businessPkid) {
        return CsvFile.of(
                "line-items.csv",
                List.of("Document", "Type", "Line", "Item", "Quantity", "Unit price", "Discount %", "Line total",
                        "Unit", "Tax %", "HSN/SAC"),
                jdbc.sql("""
                                select d.document_number, d.document_type, i.position, i.item_name, i.quantity,
                                       i.unit_price, i.discount, i.unit, i.tax_rate, i.hsn_sac
                                from document_billing_items i
                                join documents d on d.pkid = i.document_pkid
                                where d.business_pkid = :business
                                order by d.created_at, d.pkid, i.position
                                """)
                        .param("business", businessPkid)
                        .query((result, rowNumber) -> {
                            BillingItem item = new BillingItem();
                            item.setQuantity(result.getBigDecimal("quantity"));
                            item.setUnitPrice(result.getBigDecimal("unit_price"));
                            item.setDiscount(result.getBigDecimal("discount"));
                            return Arrays.<Object>asList(
                                    result.getString("document_number"),
                                    result.getString("document_type"),
                                    result.getInt("position") + 1,
                                    result.getString("item_name"),
                                    BillingItemResponse.plain(item.getQuantity()).toPlainString(),
                                    price(item.getUnitPrice()),
                                    item.getDiscount().stripTrailingZeros().toPlainString(),
                                    DocumentTotals.lineTotal(item),
                                    result.getString("unit"),
                                    BillingItemResponse.plain(result.getBigDecimal("tax_rate")).toPlainString(),
                                    result.getString("hsn_sac"));
                        })
                        .list());
    }

    // The tax on each document at each rate, as the document worked it out;
    // the GST split and reverse charge only on documents made in GST mode.
    private CsvFile documentTax(Long businessPkid) {
        return CsvFile.of(
                "document-tax.csv",
                List.of("Document", "Type", "Rate %", "Taxable", "Tax", "CGST", "SGST", "IGST", "Reverse charge"),
                jdbc.sql("""
                                select d.document_number, d.document_type, d.tax_regime, d.reverse_charge,
                                       s.rate, s.taxable, s.tax, s.cgst, s.sgst, s.igst
                                from document_tax_summaries s
                                join documents d on d.pkid = s.document_pkid
                                where d.business_pkid = :business
                                order by d.created_at, d.pkid, s.rate
                                """)
                        .param("business", businessPkid)
                        .query((result, rowNumber) -> {
                            boolean gst = "GST_INDIA".equals(result.getString("tax_regime"));
                            return Arrays.<Object>asList(
                                    result.getString("document_number"),
                                    result.getString("document_type"),
                                    rate(result.getBigDecimal("rate")),
                                    result.getBigDecimal("taxable"),
                                    result.getBigDecimal("tax"),
                                    gst ? result.getBigDecimal("cgst") : null,
                                    gst ? result.getBigDecimal("sgst") : null,
                                    gst ? result.getBigDecimal("igst") : null,
                                    gst ? (result.getBoolean("reverse_charge") ? "Yes" : "No") : null);
                        })
                        .list());
    }

    private CsvFile items(Long businessPkid) {
        return CsvFile.of(
                "items.csv",
                List.of("Name", "Description", "Unit price", "Unit", "Tax %", "HSN/SAC"),
                jdbc.sql("""
                                select name, description, unit_price, unit, tax_rate, hsn_sac from catalog_items
                                where business_pkid = :business
                                order by lower(name), pkid
                                """)
                        .param("business", businessPkid)
                        .query((result, rowNumber) -> Arrays.<Object>asList(
                                result.getString("name"),
                                result.getString("description"),
                                price(result.getBigDecimal("unit_price")),
                                result.getString("unit"),
                                rate(result.getBigDecimal("tax_rate")),
                                result.getString("hsn_sac")))
                        .list());
    }

    private CsvFile recurring(Long businessPkid) {
        return CsvFile.of(
                "recurring.csv",
                List.of("Invoice", "Client", "Repeats", "First invoice", "Next invoice", "Ends on", "Most invoices",
                        "Made", "Emailed automatically", "Paused"),
                jdbc.sql("""
                                select d.document_number, d.customer_name, s.frequency, s.start_date, s.next_run,
                                       s.end_date, s.max_runs, s.runs_done, s.email_automatically, s.paused
                                from recurring_schedules s
                                join documents d on d.pkid = s.template_pkid
                                where s.business_pkid = :business
                                order by s.created_at, s.pkid
                                """)
                        .param("business", businessPkid)
                        .query((result, rowNumber) -> Arrays.<Object>asList(
                                result.getString("document_number"),
                                result.getString("customer_name"),
                                result.getString("frequency").toLowerCase(),
                                result.getObject("start_date", LocalDate.class),
                                result.getObject("next_run", LocalDate.class),
                                result.getObject("end_date", LocalDate.class),
                                result.getObject("max_runs"),
                                result.getInt("runs_done"),
                                result.getBoolean("email_automatically") ? "Yes" : "No",
                                result.getBoolean("paused") ? "Yes" : "No"))
                        .list());
    }

    // A unit price keeps up to four decimals, and at least two.
    // A rate without trailing zeros, when there is one.
    private static String rate(BigDecimal value) {
        return value == null ? null : BillingItemResponse.plain(value).toPlainString();
    }

    private static BigDecimal price(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal exact = value.stripTrailingZeros();
        return exact.scale() < 2 ? exact.setScale(2) : exact;
    }

    private byte[] json(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] zip(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }
}
