package com.invoicespot.demo;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessService;
import com.invoicespot.customer.CustomerRepository;
import com.invoicespot.customer.CustomerService;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.document.DocumentLedger;
import com.invoicespot.document.DocumentService;
import com.invoicespot.document.DocumentType;
import com.invoicespot.document.PaymentMethod;
import com.invoicespot.document.RecurringScheduleRepository;
import com.invoicespot.document.RecurringService;
import com.invoicespot.document.dto.BillingItemDto;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DocumentResponse;
import com.invoicespot.document.dto.PaymentRecordDto;
import com.invoicespot.document.dto.RecurringRequest;
import com.invoicespot.expense.ExpenseRepository;
import com.invoicespot.expense.ExpenseService;
import com.invoicespot.expense.dto.ExpenseRequest;
import com.invoicespot.expense.dto.ExpenseResponse;
import com.invoicespot.item.CatalogItemService;
import com.invoicespot.item.dto.CreateItemRequest;
import com.invoicespot.settings.BusinessSettingsRepository;
import com.invoicespot.settings.EmailTemplateKind;
import com.invoicespot.settings.EmailTemplateService;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.settings.dto.EmailTemplateRequest;
import com.invoicespot.settings.dto.UpdateSettingsRequest;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DemoDataSeeder {

    private static final String ZONE = "America/Los_Angeles";
    // The sample expenses and recurring invoices go with the rest of the
    // sample data, never into another business.
    private static final String NO_SAMPLE_DATA = "the demo account's business doesn't hold the sample data";
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
    private final EmailTemplateService emailTemplateService;
    private final CatalogItemService itemService;
    private final CustomerService customerService;
    private final DocumentService documentService;
    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;
    private final RecurringService recurringService;
    private final RecurringScheduleRepository scheduleRepository;
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
            EmailTemplateService emailTemplateService,
            CatalogItemService itemService,
            CustomerService customerService,
            DocumentService documentService,
            ExpenseService expenseService,
            ExpenseRepository expenseRepository,
            RecurringService recurringService,
            RecurringScheduleRepository scheduleRepository,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.customerRepository = customerRepository;
        this.settingsRepository = settingsRepository;
        this.settingsService = settingsService;
        this.emailTemplateService = emailTemplateService;
        this.itemService = itemService;
        this.customerService = customerService;
        this.documentService = documentService;
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.recurringService = recurringService;
        this.scheduleRepository = scheduleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public record Result(boolean seeded, String reason, int clients, int items, int documents, int payments) {

        static Result skipped(String reason) {
            return new Result(false, reason, 0, 0, 0, 0);
        }
    }

    // How many sample expenses were added, or why none were.
    public record ExpenseResult(boolean seeded, String reason, int expenses) {

        static ExpenseResult skipped(String reason) {
            return new ExpenseResult(false, reason, 0);
        }
    }

    // How many sample recurring invoices were added, or why none were.
    public record RecurringResult(boolean seeded, String reason, int schedules) {

        static RecurringResult skipped(String reason) {
            return new RecurringResult(false, reason, 0);
        }
    }

    public Result seed(String demoEmail) {
        Optional<BusinessAccess> owner = owner(demoEmail);
        if (owner.isEmpty()) {
            return Result.skipped(missing(demoEmail));
        }
        // Sample data goes only into an empty business, so a demo email set to
        // someone's real account never mixes it in with their records.
        if (customerRepository.existsByBusinessPkid(owner.get().businessPkid())) {
            return Result.skipped(holdsSampleData(owner.get().businessPkid())
                    ? "the demo account already has the sample data"
                    : "the demo account has clients of its own, so no sample data was added");
        }
        return transactionTemplate.execute(status -> seed(owner.get()));
    }

    // The sample expenses, for an account without any expenses: neither the
    // sample ones nor its own. They came after the rest of the sample data,
    // so they are added on their own, to an account that has the rest too.
    public ExpenseResult seedExpenses(String demoEmail) {
        Optional<BusinessAccess> owner = owner(demoEmail);
        if (owner.isEmpty()) {
            return ExpenseResult.skipped(missing(demoEmail));
        }
        Long businessPkid = owner.get().businessPkid();
        if (expenseRepository.existsByBusinessPkidAndVendorIn(businessPkid, DemoData.EXPENSE_VENDORS)) {
            return ExpenseResult.skipped("the demo account already has the sample expenses");
        }
        if (expenseRepository.existsByBusinessPkid(businessPkid)) {
            return ExpenseResult.skipped("the demo account already has expenses of its own");
        }
        if (!holdsSampleData(businessPkid)) {
            return ExpenseResult.skipped(NO_SAMPLE_DATA);
        }
        return transactionTemplate.execute(status -> new ExpenseResult(true, null, expenses(owner.get())));
    }

    // Sample recurring invoices, for an account without any schedules, each
    // repeating one of its sample invoices. They came after the rest of the
    // sample data, so they are added on their own, to an account that has
    // the rest too.
    public RecurringResult seedRecurring(String demoEmail) {
        Optional<BusinessAccess> owner = owner(demoEmail);
        if (owner.isEmpty()) {
            return RecurringResult.skipped(missing(demoEmail));
        }
        if (scheduleRepository.existsByBusinessPkid(owner.get().businessPkid())) {
            return RecurringResult.skipped("the demo account already has recurring invoices");
        }
        if (!holdsSampleData(owner.get().businessPkid())) {
            return RecurringResult.skipped(NO_SAMPLE_DATA);
        }
        int schedules = transactionTemplate.execute(status -> schedules(owner.get()));
        return schedules == 0
                ? RecurringResult.skipped("the demo account has no sample invoices to repeat")
                : new RecurringResult(true, null, schedules);
    }

    // The demo account's own business, as its owner, if the account exists.
    private Optional<BusinessAccess> owner(String demoEmail) {
        return userRepository.findByEmailIgnoreCase(demoEmail == null ? "" : demoEmail.trim())
                .map(demo -> businessService.ownerAccess(businessService.ownedBy(demo.getPkid()).getPkid()));
    }

    private static String missing(String demoEmail) {
        return "the demo account " + demoEmail + " does not exist";
    }

    // Whether the sample data was added to the business: marked then, where
    // nothing in the app changes the mark, unlike its records, which anyone
    // trying the demo can delete or rename.
    private boolean holdsSampleData(Long businessPkid) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select sample_data from businesses where pkid = ?", Boolean.class, businessPkid));
    }

    private Result seed(BusinessAccess owner) {
        ZoneId zone = ZoneId.of(ZONE);
        jdbcTemplate.update("update businesses set sample_data = true where pkid = ?", owner.businessPkid());
        if (settingsRepository.findByBusinessPkid(owner.businessPkid()).isEmpty()) {
            settingsService.update(owner, new UpdateSettingsRequest(
                    "USD", 14, TAX_RATE, "Sales tax", DemoData.NOTES, DemoData.TERMS,
                    ZONE, "INV-", "QUO-", "REC-", 1001, 201, 301, null, null, null, null, null, null, null, null, null,
                    null, null));
            for (EmailTemplateKind kind : EmailTemplateKind.values()) {
                if (kind != EmailTemplateKind.REMINDER) {
                    emailTemplateService.save(owner, kind.name(), new EmailTemplateRequest(
                            kind.standardSubject(), DemoData.EMAIL_MESSAGE));
                }
            }
        }
        DemoData.ITEMS.forEach(item -> itemService.create(
                owner, new CreateItemRequest(item.name(), item.description(), item.price(), null, null, null)));
        List<String> clients = DemoData.CLIENTS.stream()
                .map(client -> customerService.create(owner, new CreateCustomerRequest(
                                client.name(), client.email(), client.phone(), null, client.address(),
                                client.city(), "United States", null, null))
                        .id()
                        .toString())
                .toList();

        LocalDate today = LocalDate.now(clock.withZone(zone));
        Random random = new Random(20260930L);
        List<Plan> plans = new ArrayList<>(plans(today, random, clients.size()));
        // The invoices the sample schedules last made, one period before their
        // next, for the schedules to repeat. They take nothing from the random
        // numbers, so the rest of the sample data comes out the same.
        for (DemoData.SampleSchedule schedule : DemoData.SCHEDULES) {
            plans.add(new Plan(
                    lastRun(schedule, today),
                    DocumentType.INVOICE,
                    DemoData.CLIENTS.stream().map(DemoData.Client::name).toList().indexOf(schedule.client()),
                    line(schedule)));
        }
        plans.sort(Comparator.comparing(Plan::issued));
        int payments = 0;
        for (Plan plan : plans) {
            payments += create(owner, plan, clients.get(plan.client()), today, zone, random);
        }
        return new Result(true, null, clients.size(), DemoData.ITEMS.size(), plans.size(), payments);
    }

    // The sample expenses, in dollars as the sample documents are, each paid
    // the given number of days before today and recorded on the day it was
    // paid.
    private int expenses(BusinessAccess owner) {
        ZoneId zone = ZoneId.of(ZONE);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<ExpenseResponse> recorded = new ArrayList<>();
        for (DemoData.SampleExpense expense : DemoData.EXPENSES) {
            recorded.add(expenseService.create(owner, new ExpenseRequest(
                    today.minusDays(expense.daysAgo()),
                    expense.vendor(),
                    expense.category(),
                    expense.amount(),
                    expense.tax(),
                    expense.taxReclaimable(),
                    "USD",
                    null,
                    expense.method().getValue(),
                    null,
                    null)));
        }
        entityManager.flush();
        recorded.forEach(expense -> backdate("expenses", expense.id(), expense.date(), zone));
        return recorded.size();
    }

    // Each sample schedule repeats an issued invoice in dollars that bills its
    // item and nothing else, as many of it as the schedule bills, so that a
    // monthly invoice bills one month of a monthly item: the latest of those
    // that no other sample schedule repeats. The item is matched by name
    // whatever its letter case and the spaces around it, so an account seeded
    // when it was spelled another way still has it. Its next invoice is made
    // the given number of days after today, issued as it is made and not
    // emailed. A schedule with no such invoice to repeat is left out.
    private int schedules(BusinessAccess owner) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(ZONE)));
        List<UUID> repeated = new ArrayList<>();
        for (DemoData.SampleSchedule schedule : DemoData.SCHEDULES) {
            Optional<UUID> invoice = jdbcTemplate
                    .queryForList(
                            """
                            select d.id from documents d
                            where d.business_pkid = ? and %s and d.document_type = 'Invoice' and d.currency = 'USD'
                              and (select count(*) from document_billing_items i where i.document_pkid = d.pkid) = 1
                              and exists (select 1 from document_billing_items i
                                          where i.document_pkid = d.pkid and lower(btrim(i.item_name)) = ?
                                            and i.quantity = ?)
                            order by d.issue_date desc, d.pkid desc
                            """.formatted(DocumentLedger.COUNTED),
                            UUID.class,
                            owner.businessPkid(),
                            schedule.item().toLowerCase(Locale.ROOT),
                            schedule.quantity())
                    .stream()
                    .filter(id -> !repeated.contains(id))
                    .findFirst();
            if (invoice.isPresent()) {
                recurringService.create(owner, new RecurringRequest(
                        invoice.get().toString(),
                        schedule.frequency().name(),
                        today.plusDays(schedule.daysAhead()),
                        null,
                        null,
                        false,
                        true));
                repeated.add(invoice.get());
            }
        }
        return repeated.size();
    }

    // A document to add: dated the given day for one of the clients, with
    // lines drawn at random, or with the one line given.
    private record Plan(LocalDate issued, DocumentType type, int client, BillingItemDto line) {

        Plan(LocalDate issued, DocumentType type, int client) {
            this(issued, type, client, null);
        }
    }

    // The day a sample schedule last made its invoice: one period before the
    // next.
    private static LocalDate lastRun(DemoData.SampleSchedule schedule, LocalDate today) {
        LocalDate next = today.plusDays(schedule.daysAhead());
        return switch (schedule.frequency()) {
            case WEEKLY -> next.minusWeeks(1);
            case MONTHLY -> next.minusMonths(1);
            case QUARTERLY -> next.minusMonths(3);
            case YEARLY -> next.minusYears(1);
        };
    }

    // The one line a sample schedule's invoice bills, at the item's price.
    private static BillingItemDto line(DemoData.SampleSchedule schedule) {
        DemoData.Item item = DemoData.ITEMS.stream()
                .filter(found -> found.name().equals(schedule.item()))
                .findFirst()
                .orElseThrow();
        return new BillingItemDto(
                item.name(), item.price(), BigDecimal.valueOf(schedule.quantity()), "0", null, null, null);
    }

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
                plan.issued().plusDays(14).toString(),
                DemoData.NOTES,
                DemoData.TERMS,
                TAX_RATE.toPlainString(),
                "USD",
                plan.line() == null ? lines(random) : List.of(plan.line()),
                plan.issued(),
                null,
                null,
                null,
                null,
                null,
                null));
        List<LocalDate> paymentDates = new ArrayList<>();
        if (plan.line() != null) {
            paymentDates.add(paidInFull(owner, document, plan.issued(), today));
        } else if (plan.type() == DocumentType.INVOICE) {
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
            lines.add(new BillingItemDto(
                    item.name(), item.price(), BigDecimal.valueOf(quantity), discount, null, null, null));
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

    // A schedule's invoice was paid in full by bank transfer a week after it
    // was made.
    private LocalDate paidInFull(BusinessAccess owner, DocumentResponse document, LocalDate issued, LocalDate today) {
        LocalDate date = earliest(issued.plusDays(7), today);
        documentService.addPayment(owner, document.id().toString(), new CreatePaymentRequest(
                date.toString(), document.total(), PaymentMethod.BANK_TRANSFER.getValue(), "Paid in full", null));
        return date;
    }

    private void recordPayment(
            BusinessAccess owner,
            DocumentResponse document,
            LocalDate date,
            BigDecimal amount,
            String note,
            Random random) {
        documentService.addPayment(owner, document.id().toString(), new CreatePaymentRequest(
                date.toString(), amount, METHODS[random.nextInt(METHODS.length)].getValue(), note, null));
    }

    private static LocalDate earliest(LocalDate date, LocalDate today) {
        return date.isAfter(today) ? today : date;
    }

    // A document's history happens when the document and its payments did.
    // It was issued as it was made.
    private void backdateHistory(UUID documentId) {
        jdbcTemplate.update("update documents set issued_at = created_at where id = ?", documentId);
        jdbcTemplate.update("""
                update document_events event set occurred_at = doc.created_at
                from documents doc
                where event.document_pkid = doc.pkid and doc.id = ? and event.type in ('CREATED', 'ISSUED')
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
