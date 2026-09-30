package com.invoicespot.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicespot.common.CsvFile;
import com.invoicespot.customer.CustomerSearch;
import com.invoicespot.customer.CustomerService;
import com.invoicespot.document.BillingItem;
import com.invoicespot.document.DocumentExport;
import com.invoicespot.document.DocumentSearch;
import com.invoicespot.document.DocumentTotals;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.settings.dto.SettingsResponse;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
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

// Everything an owner keeps in the app, in one ZIP file: a CSV file for each
// kind of record, the settings and profile as JSON, and a note on what each
// file holds. It is read in one snapshot, so the files agree with each other.
@Service
public class DataExport {

    public record Archive(String fileName, byte[] content) {}

    private static final String README = """
            Invoice Spot export, made on %s.

            documents.csv    Every invoice, quotation and receipt.
            line-items.csv   The lines on each document.
            payments.csv     Every payment recorded on a document.
            clients.csv      Your clients, and what each owes in every currency.
            items.csv        Your saved products and services.
            recurring.csv    The invoices that repeat, and how.
            settings.json    Your defaults for new documents, numbering and reminders.
            profile.json     Your account and business details.

            Dates follow your time zone, %s. Amounts are plain numbers in the
            currency of their document. CSV files are UTF-8 and open in any
            spreadsheet.
            """;

    private final DocumentExport documentExport;
    private final CustomerService customerService;
    private final SettingsService settingsService;
    private final DemoAccountGuard demoAccountGuard;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DataExport(
            DocumentExport documentExport,
            CustomerService customerService,
            SettingsService settingsService,
            DemoAccountGuard demoAccountGuard,
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            Clock clock) {
        this.documentExport = documentExport;
        this.customerService = customerService;
        this.settingsService = settingsService;
        this.demoAccountGuard = demoAccountGuard;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Archive all(User owner) {
        Long ownerPkid = owner.getPkid();
        BusinessSettings settings = settingsService.forUser(ownerPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("README.txt", README.formatted(today, zone.getId()).getBytes(StandardCharsets.UTF_8));
        files.put("documents.csv", documentExport
                .documents(ownerPkid, DocumentSearch.of(null, null, null, false, null, null, null, null, null, 1, 1))
                .content());
        files.put("line-items.csv", lineItems(ownerPkid).content());
        files.put("payments.csv", documentExport.payments(ownerPkid).content());
        files.put(
                "clients.csv",
                customerService.csv(ownerPkid, CustomerSearch.of(null, "name", "asc", 1, 1)).content());
        files.put("items.csv", items(ownerPkid).content());
        files.put("recurring.csv", recurring(ownerPkid).content());
        files.put("settings.json", json(SettingsResponse.from(settings)));
        files.put("profile.json", json(UserProfileResponse.from(owner, demoAccountGuard.isDemo(owner))));
        return new Archive("invoice-spot-export-" + today + ".zip", zip(files));
    }

    // Each line's total is worked out the way its document's totals are.
    private CsvFile lineItems(Long ownerPkid) {
        return CsvFile.of(
                "line-items.csv",
                List.of("Document", "Type", "Line", "Item", "Quantity", "Unit price", "Discount %", "Line total"),
                jdbc.sql("""
                                select d.document_number, d.document_type, i.position, i.item_name, i.quantity,
                                       i.unit_price, i.discount
                                from document_billing_items i
                                join documents d on d.pkid = i.document_pkid
                                where d.created_by_pkid = :owner
                                order by d.created_at, d.pkid, i.position
                                """)
                        .param("owner", ownerPkid)
                        .query((result, rowNumber) -> {
                            BillingItem item = new BillingItem();
                            item.setQuantity((Integer) result.getObject("quantity"));
                            item.setUnitPrice(result.getBigDecimal("unit_price"));
                            item.setDiscount(result.getBigDecimal("discount"));
                            return Arrays.<Object>asList(
                                    result.getString("document_number"),
                                    result.getString("document_type"),
                                    result.getInt("position") + 1,
                                    result.getString("item_name"),
                                    item.getQuantity(),
                                    price(item.getUnitPrice()),
                                    item.getDiscount().stripTrailingZeros().toPlainString(),
                                    DocumentTotals.lineTotal(item));
                        })
                        .list());
    }

    private CsvFile items(Long ownerPkid) {
        return CsvFile.of(
                "items.csv",
                List.of("Name", "Description", "Unit price"),
                jdbc.sql("""
                                select name, description, unit_price from catalog_items
                                where created_by_pkid = :owner
                                order by lower(name), pkid
                                """)
                        .param("owner", ownerPkid)
                        .query((result, rowNumber) -> Arrays.<Object>asList(
                                result.getString("name"),
                                result.getString("description"),
                                price(result.getBigDecimal("unit_price"))))
                        .list());
    }

    private CsvFile recurring(Long ownerPkid) {
        return CsvFile.of(
                "recurring.csv",
                List.of("Invoice", "Client", "Repeats", "First invoice", "Next invoice", "Ends on", "Most invoices",
                        "Made", "Emailed automatically", "Paused"),
                jdbc.sql("""
                                select d.document_number, d.customer_name, s.frequency, s.start_date, s.next_run,
                                       s.end_date, s.max_runs, s.runs_done, s.email_automatically, s.paused
                                from recurring_schedules s
                                join documents d on d.pkid = s.template_pkid
                                where s.created_by_pkid = :owner
                                order by s.created_at, s.pkid
                                """)
                        .param("owner", ownerPkid)
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
