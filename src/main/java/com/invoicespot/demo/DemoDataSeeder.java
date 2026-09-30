package com.invoicespot.demo;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessService;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.customer.CustomerService;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.document.DocumentService;
import com.invoicespot.document.DocumentType;
import com.invoicespot.document.PaymentMethod;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentResponse;
import com.invoicespot.document.dto.PaymentRecordDto;
import com.invoicespot.item.CatalogItemService;
import com.invoicespot.item.dto.CreateItemRequest;
import com.invoicespot.settings.BusinessSettingsRepository;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.settings.dto.UpdateSettingsRequest;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DemoDataSeeder {

    private static final String ZONE = "America/Los_Angeles";
    private static final BigDecimal TAX_RATE = new BigDecimal("7.25");
    private static final PaymentMethod[] METHODS = {
        PaymentMethod.BANK_TRANSFER, PaymentMethod.BANK_TRANSFER, PaymentMethod.BANK_TRANSFER,
        PaymentMethod.CREDIT_CARD, PaymentMethod.CREDIT_CARD, PaymentMethod.PAYPAL,
        PaymentMethod.CASH, PaymentMethod.MOBILE_MONEY, PaymentMethod.OTHERS
    };

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final CustomerRepository customerRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final SettingsService settingsService;
    private final CatalogItemService itemService;
    private final CustomerService customerService;
    private final DocumentService documentService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public DemoDataSeeder(
            UserRepository userRepository,
            BusinessService businessService,
            CustomerRepository customerRepository,
            BusinessSettingsRepository settingsRepository,
            SettingsService settingsService,
            CatalogItemService itemService,
            CustomerService customerService,
            DocumentService documentService,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.customerRepository = customerRepository;
        this.settingsRepository = settingsRepository;
        this.settingsService = settingsService;
        this.itemService = itemService;
        this.customerService = customerService;
        this.documentService = documentService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public record Result(boolean seeded, String reason, int clients, int items, int documents, int payments) {

        static Result skipped(String reason) {
            return new Result(false, reason, 0, 0, 0, 0);
        }
    }

    public Result seed(String demoEmail) {
        Optional<User> demo = userRepository.findByEmailIgnoreCase(demoEmail == null ? "" : demoEmail.trim());
        if (demo.isEmpty()) {
            return Result.skipped("the demo account " + demoEmail + " does not exist");
        }
        BusinessAccess owner = businessService.ownerAccess(businessService.ownedBy(demo.get().getPkid()).getPkid());
        if (customerRepository.existsByBusinessPkidAndEmailIgnoreCase(owner.businessPkid(), DemoData.MARKER_EMAIL)) {
            return Result.skipped("the demo account already has the sample data");
        }
        return transactionTemplate.execute(status -> seed(owner));
    }

    private Result seed(BusinessAccess owner) {
        ZoneId zone = ZoneId.of(ZONE);
        if (settingsRepository.findByBusinessPkid(owner.businessPkid()).isEmpty()) {
            settingsService.update(owner, new UpdateSettingsRequest(
                    "USD", 14, TAX_RATE, "Sales tax", DemoData.NOTES, DemoData.TERMS, DemoData.EMAIL_MESSAGE,
                    ZONE, "INV-", "QUO-", "REC-", 1001, 201, 301, null, null, null, null, null));
        }
        DemoData.ITEMS.forEach(item -> itemService.create(
                owner, new CreateItemRequest(item.name(), item.description(), item.price())));
        List<String> clients = DemoData.CLIENTS.stream()
                .map(client -> customerService.create(owner, new CreateCustomerRequest(
                                client.name(), client.email(), client.phone(), null, client.address(),
                                client.city(), "United States"))
                        .id()
                        .toString())
                .toList();

        LocalDate today = LocalDate.now(clock.withZone(zone));
        Random random = new Random(20260930L);
        List<Plan> plans = plans(today, random, clients.size());
        int payments = 0;
        for (Plan plan : plans) {
            payments += create(owner, plan, clients.get(plan.client()), today, zone, random);
        }
        return new Result(true, null, clients.size(), DemoData.ITEMS.size(), plans.size(), payments);
    }

    private record Plan(LocalDate issued, DocumentType type, int client) {}

    private static List<Plan> plans(LocalDate today, Random random, int clientCount) {
        List<Plan> plans = new ArrayList<>();
        int sequence = 0;
        for (int monthsAgo = 11; monthsAgo >= 0; monthsAgo--) {
            int count = monthsAgo > 7 ? 3 : monthsAgo > 3 ? 4 : 5;
            LocalDate month = today.minusMonths(monthsAgo).withDayOfMonth(1);
            for (int i = 0; i < count; i++) {
                LocalDate issued = month.plusDays(random.nextInt(month.lengthOfMonth()));
                if (issued.isAfter(today)) {
                    issued = today.minusDays(random.nextInt(4));
                }
                sequence++;
                DocumentType type = sequence % 11 == 0
                        ? DocumentType.RECEIPT
                        : sequence % 7 == 0 ? DocumentType.QUOTATION : DocumentType.INVOICE;
                plans.add(new Plan(issued, type, random.nextInt(clientCount)));
            }
        }
        plans.sort(Comparator.comparing(Plan::issued));
        return plans;
    }

    private int create(BusinessAccess owner, Plan plan, String clientId, LocalDate today, ZoneId zone, Random random) {
        DocumentResponse document = documentService.create(owner, new CreateDocumentRequest(
                clientId,
                null,
                plan.type().getValue(),
                plan.issued().plusDays(14).atTime(LocalTime.NOON).atZone(zone).toInstant(),
                DemoData.NOTES,
                DemoData.TERMS,
                TAX_RATE.toPlainString(),
                "USD",
                lines(random)));
        List<LocalDate> paymentDates = new ArrayList<>();
        if (plan.type() == DocumentType.INVOICE) {
            paymentDates.addAll(pay(owner, document, plan.issued(), today, random));
        }
        entityManager.flush();
        backdate("documents", document.id(), plan.issued(), zone);
        if (!paymentDates.isEmpty()) {
            List<PaymentRecordDto> records =
                    documentService.getOne(owner, document.id().toString()).paymentRecords();
            for (PaymentRecordDto record : records) {
                backdate("payments", record.id(), record.datePaid(), zone);
            }
        }
        backdateHistory(document.id());
        return paymentDates.size();
    }

    private static List<BillingItemDto> lines(Random random) {
        List<DemoData.Item> pool = new ArrayList<>(DemoData.ITEMS);
        int count = 1 + random.nextInt(4);
        List<BillingItemDto> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DemoData.Item item = pool.remove(random.nextInt(pool.size()));
            int quantity = item.countable() ? 2 + random.nextInt(10) : 1;
            String discount = random.nextInt(10) == 0 ? "10" : "0";
            lines.add(new BillingItemDto(item.name(), item.price(), quantity, discount));
        }
        return lines;
    }

    private List<LocalDate> pay(
            BusinessAccess owner, DocumentResponse document, LocalDate issued, LocalDate today, Random random) {
        long age = today.toEpochDay() - issued.toEpochDay();
        int roll = random.nextInt(100);
        boolean full = age > 60 ? roll < 95 : age > 30 ? roll < 60 : roll < 25;
        boolean partial = !full && (age > 60 ? roll < 100 : age > 30 ? roll < 85 : roll < 50);
        List<LocalDate> dates = new ArrayList<>();
        BigDecimal total = document.total();
        if (partial || full) {
            BigDecimal deposit = full && random.nextBoolean()
                    ? total
                    : total.multiply(BigDecimal.valueOf(30 + random.nextInt(21)))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            LocalDate first = earliest(issued.plusDays(1 + random.nextInt(8)), today);
            String note = deposit.compareTo(total) == 0 ? "Paid in full" : "Deposit";
            recordPayment(owner, document, first, deposit, note, random);
            dates.add(first);
            if (full && deposit.compareTo(total) < 0) {
                LocalDate second = earliest(first.plusDays(7 + random.nextInt(21)), today);
                recordPayment(owner, document, second, total.subtract(deposit), "Final payment", random);
                dates.add(second);
            }
        }
        return dates;
    }

    private void recordPayment(
            BusinessAccess owner,
            DocumentResponse document,
            LocalDate date,
            BigDecimal amount,
            String note,
            Random random) {
        documentService.addPayment(owner, document.id().toString(), new CreatePaymentRequest(
                date.toString(), amount, METHODS[random.nextInt(METHODS.length)].getValue(), note));
    }

    private static LocalDate earliest(LocalDate date, LocalDate today) {
        return date.isAfter(today) ? today : date;
    }

    // A document's history happens when the document and its payments did.
    private void backdateHistory(UUID documentId) {
        jdbcTemplate.update("""
                update document_events event set occurred_at = doc.created_at
                from documents doc
                where event.document_pkid = doc.pkid and doc.id = ? and event.type = 'CREATED'
                """, documentId);
        jdbcTemplate.update("""
                update document_events event set occurred_at = payment.created_at
                from payments payment
                where event.payment_pkid = payment.pkid
                  and payment.document_pkid = (select pkid from documents where id = ?)
                """, documentId);
    }

    private void backdate(String table, UUID id, LocalDate day, ZoneId zone) {
        Timestamp at = Timestamp.from(day.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant());
        jdbcTemplate.update("update " + table + " set created_at = ?, updated_at = ? where id = ?", at, at, id);
    }
}
