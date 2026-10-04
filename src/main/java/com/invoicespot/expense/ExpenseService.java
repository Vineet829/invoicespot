package com.invoicespot.expense;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.CsvFile;
import com.invoicespot.common.ExchangeRate;
import com.invoicespot.common.Today;
import com.invoicespot.document.PaymentMethod;
import com.invoicespot.expense.dto.ExpenseListResponse;
import com.invoicespot.expense.dto.ExpenseListTotals;
import com.invoicespot.expense.dto.ExpenseRequest;
import com.invoicespot.expense.dto.ExpenseResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseDirectory expenseDirectory;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseDirectory expenseDirectory,
            BusinessRepository businessRepository,
            UserRepository userRepository,
            SettingsService settingsService,
            Clock clock) {
        this.expenseRepository = expenseRepository;
        this.expenseDirectory = expenseDirectory;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // A page of the expenses, and what all of them come to, read at one
    // moment so the two agree; the totals count them. A period is read as the
    // dates it covers today, by the business's calendar, which the list sends
    // back with the dates it was asked for; without one, today isn't read.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExpenseListResponse list(BusinessAccess access, ExpenseSearch search) {
        ExpenseSearch dated = search.period() == null ? search : search.on(today(access.businessPkid()));
        List<ExpenseListTotals> totals = expenseDirectory.totals(access.businessPkid(), dated);
        long found = totals.stream().mapToLong(ExpenseListTotals::expenses).sum();
        return new ExpenseListResponse(
                true,
                found,
                (int) ((found + search.size() - 1) / search.size()),
                dated.from(),
                dated.to(),
                expenseDirectory.page(access.businessPkid(), dated).stream()
                        .map(ExpenseResponse::from)
                        .toList(),
                totals);
    }

    @Transactional(readOnly = true)
    public List<String> categories(BusinessAccess access) {
        return expenseRepository.categories(access.businessPkid());
    }

    @Transactional
    public ExpenseResponse create(BusinessAccess access, ExpenseRequest request) {
        if (request.date() == null) {
            throw badRequest("Enter the day the expense was paid");
        }
        if (request.amount() == null) {
            throw badRequest("Enter an amount greater than zero");
        }
        BusinessSettings settings = settingsService.forBusiness(access.businessPkid());
        Expense expense = new Expense();
        expense.setBusiness(businessRepository.getReferenceById(access.businessPkid()));
        expense.setRecordedBy(userRepository.getReferenceById(access.userPkid()));
        expense.setExpenseDate(paidOn(request.date()));
        expense.setVendor(blankToNull(request.vendor()));
        expense.setCategory(category(access.businessPkid(), request.category(), 0));
        expense.setAmount(request.amount().setScale(2));
        if (request.taxAmount() != null) {
            expense.setTaxAmount(request.taxAmount().setScale(2));
        }
        expense.setTaxReclaimable(Boolean.TRUE.equals(request.taxReclaimable()));
        expense.setCurrency(request.currency() == null ? settings.getDefaultCurrency() : request.currency());
        exchangeRate(expense, request.exchangeRate(), false, settings.getBaseCurrency());
        expense.setPaymentMethod(paymentMethod(request.paymentMethod()));
        expense.setReference(blankToNull(request.reference()));
        expense.setNotes(blankToNull(request.notes()));
        expenseRepository.save(expense);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public ExpenseResponse update(BusinessAccess access, String id, ExpenseRequest request) {
        Expense expense = requireInBusiness(access, id);
        if (request.date() != null) {
            expense.setExpenseDate(paidOn(request.date()));
        }
        if (request.vendor() != null) {
            expense.setVendor(blankToNull(request.vendor()));
        }
        if (request.category() != null) {
            expense.setCategory(category(access.businessPkid(), request.category(), expense.getPkid()));
        }
        if (request.amount() != null) {
            expense.setAmount(request.amount().setScale(2));
        }
        if (request.taxAmount() != null) {
            expense.setTaxAmount(request.taxAmount().setScale(2));
        }
        if (request.taxReclaimable() != null) {
            expense.setTaxReclaimable(request.taxReclaimable());
        }
        boolean currencyChanged = request.currency() != null && !request.currency().equals(expense.getCurrency());
        if (currencyChanged) {
            expense.setCurrency(request.currency());
        }
        exchangeRate(
                expense,
                request.exchangeRate(),
                currencyChanged,
                settingsService.forBusiness(access.businessPkid()).getBaseCurrency());
        if (request.paymentMethod() != null) {
            expense.setPaymentMethod(paymentMethod(request.paymentMethod()));
        }
        if (request.reference() != null) {
            expense.setReference(blankToNull(request.reference()));
        }
        if (request.notes() != null) {
            expense.setNotes(blankToNull(request.notes()));
        }
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(BusinessAccess access, String id) {
        expenseRepository.delete(requireInBusiness(access, id));
    }

    // Every expense the list's search matches, in its order, as they all
    // stood when the file was begun. A period is read as the list reads it.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CsvFile csv(BusinessAccess access, ExpenseSearch search) {
        LocalDate today = today(access.businessPkid());
        return CsvFile.of(
                "invoice-spot-expenses-" + today + ".csv",
                List.of("Date", "Vendor", "Category", "Currency", "Amount", "Tax", "Tax reclaimable", "Total",
                        "Exchange rate", "Exchange rate to", "Payment method", "Reference", "Notes"),
                expenseDirectory.all(access.businessPkid(), search.on(today)).stream()
                        .map(expense -> Arrays.<Object>asList(
                                expense.getExpenseDate(),
                                expense.getVendor(),
                                expense.getCategory(),
                                expense.getCurrency(),
                                expense.getAmount(),
                                expense.getTaxAmount(),
                                expense.isTaxReclaimable() ? "Yes" : "No",
                                expense.total(),
                                ExchangeRate.plain(expense.getExchangeRate()),
                                expense.getExchangeBase(),
                                expense.getPaymentMethod() == null ? null : expense.getPaymentMethod().getValue(),
                                expense.getReference(),
                                expense.getNotes()))
                        .toList());
    }

    // Today by the business's calendar.
    private LocalDate today(Long businessPkid) {
        return LocalDate.now(clock.withZone(ZoneId.of(settingsService.forBusiness(businessPkid).getTimeZone())));
    }

    // The day an expense was paid, which can't be a day still to come
    // everywhere.
    private LocalDate paidOn(LocalDate date) {
        if (date.isAfter(Today.latest(clock))) {
            throw badRequest("An expense can't be dated after today");
        }
        return date;
    }

    // A category typed in another letter case than the business's other
    // expenses use takes their spelling, so each category is listed and
    // totalled once.
    private String category(Long businessPkid, String value, long expensePkid) {
        String category = value == null ? "" : value.trim();
        if (category.isEmpty()) {
            throw badRequest("An expense needs a category");
        }
        return expenseRepository.spellingsOf(businessPkid, category, expensePkid).stream()
                .findFirst()
                .orElse(category);
    }

    // The rate from the expense's currency to the reporting currency, typed in
    // as "1 {currency} = x {base}"; a blank one removes it. An expense in the
    // reporting currency has none, and a new currency needs a new rate.
    private static void exchangeRate(Expense expense, String value, boolean currencyChanged, String base) {
        BigDecimal rate = ExchangeRate.parse(value);
        if (rate != null) {
            if (expense.getCurrency().equals(base)) {
                throw badRequest("An expense in " + base + ", your reporting currency, needs no exchange rate");
            }
            expense.setExchangeRate(rate);
            expense.setExchangeBase(base);
        } else if (value != null || currencyChanged) {
            expense.setExchangeRate(null);
            expense.setExchangeBase(null);
        }
    }

    private static PaymentMethod paymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid payment method: " + value);
        }
    }

    private Expense requireInBusiness(BusinessAccess access, String id) {
        Expense expense = externalId(id)
                .flatMap(expenseRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That expense does not exist"));
        if (!expense.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only change your own business's expenses");
        }
        return expense;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static Optional<UUID> externalId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
