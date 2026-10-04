package com.invoicespot.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.business.Permission;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ExpenseIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // UTC+14, the time zone furthest ahead: a day after its today is still to
    // come everywhere.
    private static final ZoneId FURTHEST_AHEAD = ZoneId.of("Pacific/Kiritimati");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private String session;

    @BeforeEach
    void setUp() {
        owner = createUser();
        session = bearer(owner);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return send(request, session, json);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String as, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, as)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions record(String json) throws Exception {
        return send(post("/api/v1/expense"), json);
    }

    private String expense(LocalDate date, String vendor, String category, String amount) throws Exception {
        return JsonPath.read(
                record("""
                                {"date": "%s", "vendor": "%s", "category": "%s", "amount": %s}
                                """.formatted(date, vendor, category, amount))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.expense._id");
    }

    private ResultActions list(String query) throws Exception {
        return send(get("/api/v1/expense?" + query), "");
    }

    @Test
    @DisplayName("expenses are recorded, listed newest first, searched, filtered, changed and deleted")
    void lifecycle() throws Exception {
        String hosting = expense(TODAY.minusDays(40), "Hostinger", "Software", "12.50");
        expense(TODAY, "Uber", "Travel", "8");
        expense(TODAY.minusDays(3), "IndiGo", "Travel", "120");

        list("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(3))
                .andExpect(jsonPath("$.expenses[*].vendor").value(contains("Uber", "IndiGo", "Hostinger")))
                .andExpect(jsonPath("$.expenses[0].date").value(TODAY.toString()))
                .andExpect(jsonPath("$.expenses[0].category").value("Travel"))
                .andExpect(jsonPath("$.expenses[0].amount").value(8.0))
                .andExpect(jsonPath("$.expenses[0].currency").value("USD"));
        list("q=INDI").andExpect(jsonPath("$.expenses[*].vendor").value(contains("IndiGo")));
        list("category=travel").andExpect(jsonPath("$.expenses[*].vendor").value(contains("Uber", "IndiGo")));
        list("from=%s&to=%s".formatted(TODAY.minusDays(45), TODAY.minusDays(1)))
                .andExpect(jsonPath("$.expenses[*].vendor").value(contains("IndiGo", "Hostinger")));
        list("size=2&page=2")
                .andExpect(jsonPath("$.numberOfPages").value(2))
                .andExpect(jsonPath("$.expenses[*].vendor").value(contains("Hostinger")));

        send(patch("/api/v1/expense/{id}", hosting), """
                {"amount": 15, "category": " Hosting ", "currency": "EUR", "reference": "INV-88",
                 "notes": "Yearly plan"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Expense updated"))
                .andExpect(jsonPath("$.expense.amount").value(15.0))
                .andExpect(jsonPath("$.expense.category").value("Hosting"))
                .andExpect(jsonPath("$.expense.currency").value("EUR"))
                .andExpect(jsonPath("$.expense.vendor").value("Hostinger"))
                .andExpect(jsonPath("$.expense.reference").value("INV-88"))
                .andExpect(jsonPath("$.expense.notes").value("Yearly plan"));
        list("q=yearly").andExpect(jsonPath("$.expenses[*].vendor").value(contains("Hostinger")));
        send(patch("/api/v1/expense/{id}", hosting), "{\"vendor\": \" \", \"reference\": \"\"}")
                .andExpect(jsonPath("$.expense.vendor").doesNotExist())
                .andExpect(jsonPath("$.expense.reference").doesNotExist())
                .andExpect(jsonPath("$.expense.notes").value("Yearly plan"))
                .andExpect(jsonPath("$.expense.amount").value(15.0));

        send(delete("/api/v1/expense/{id}", hosting), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Expense deleted"));
        list("").andExpect(jsonPath("$.totalExpenses").value(2));
    }

    @Test
    @DisplayName("the amount leaves out the tax paid, which is reclaimable or not, and the total adds them")
    void amountsAndTax() throws Exception {
        record("""
                {"date": "%s", "category": "Office", "amount": 100, "taxAmount": 18, "taxReclaimable": true,
                 "currency": "INR", "paymentMethod": "UPI"}
                """.formatted(TODAY))
                .andExpect(jsonPath("$.message").value("Expense saved"))
                .andExpect(jsonPath("$.expense.paymentMethod").value("UPI"))
                .andExpect(jsonPath("$.expense.amount").value(100.0))
                .andExpect(jsonPath("$.expense.taxAmount").value(18.0))
                .andExpect(jsonPath("$.expense.taxReclaimable").value(true))
                .andExpect(jsonPath("$.expense.total").value(118.0))
                .andExpect(jsonPath("$.expense.currency").value("INR"));
        record("{\"date\": \"%s\", \"category\": \"Office\", \"amount\": 40}".formatted(TODAY))
                .andExpect(jsonPath("$.expense.taxAmount").value(0.0))
                .andExpect(jsonPath("$.expense.taxReclaimable").value(false))
                .andExpect(jsonPath("$.expense.total").value(40.0))
                .andExpect(jsonPath("$.expense.paymentMethod").doesNotExist());
    }

    @Test
    @DisplayName("an expense needs a date, a category and an amount above zero, with sensible tax and method")
    void validation() throws Exception {
        String valid = "\"date\": \"%s\", \"category\": \"Office\"".formatted(TODAY);
        refused("{\"category\": \"Office\", \"amount\": 5}", "Enter the day the expense was paid");
        refused("{%s}".formatted(valid), "Enter an amount greater than zero");
        refused("{%s, \"amount\": 0}".formatted(valid), "Enter an amount greater than zero");
        refused("{%s, \"amount\": 1.234}".formatted(valid), "Amounts can have at most 15 whole digits and 2 decimals");
        refused("{%s, \"amount\": 1000000000000000}".formatted(valid),
                "Amounts can have at most 15 whole digits and 2 decimals");
        refused("{%s, \"amount\": 5, \"taxAmount\": 1.234}".formatted(valid),
                "Tax paid can have at most 15 whole digits and 2 decimals");
        refused("{%s, \"amount\": 5, \"taxAmount\": -1}".formatted(valid), "Tax paid can't be negative");
        refused("{\"date\": \"%s\", \"category\": \" \", \"amount\": 5}".formatted(TODAY),
                "An expense needs a category");
        refused("{\"date\": \"%s\", \"category\": \"%s\", \"amount\": 5}".formatted(TODAY, "x".repeat(61)),
                "Categories can be at most 60 characters");
        refused("{%s, \"amount\": 5, \"currency\": \"usd\"}".formatted(valid),
                "Currency must be a three-letter code such as USD");
        refused("{%s, \"amount\": 5, \"paymentMethod\": \"Barter\"}".formatted(valid),
                "Invalid payment method: Barter");
    }

    private void refused(String json, String message) throws Exception {
        record(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("a category typed in another letter case takes the spelling in use, and each is listed once")
    void categories() throws Exception {
        expense(TODAY, "Uber", "Travel", "8");
        String rail = expense(TODAY, "IRCTC", " travel ", "30");
        String paper = expense(TODAY, "Staples", "office", "4");

        send(get("/api/v1/expense/categories"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").value(contains("office", "Travel")));
        list("q=irctc").andExpect(jsonPath("$.expenses[0].category").value("Travel"));
        // The only expense in a category can still change its spelling.
        send(patch("/api/v1/expense/{id}", paper), "{\"category\": \"Office\"}")
                .andExpect(jsonPath("$.expense.category").value("Office"));
        send(patch("/api/v1/expense/{id}", rail), "{\"category\": \"TRAVEL\"}")
                .andExpect(jsonPath("$.expense.category").value("Travel"));
    }

    @Test
    @DisplayName("a category two members saved at once in their own spellings takes the one used first from then on")
    void categorySavedAtOnce() throws Exception {
        for (List<String> spellings : List.of(List.of("Travel", "travel"), List.of("travel", "Travel"))) {
            User someone = createUser();
            Business business = businessOf(someone);
            spellings.forEach(spelling -> jdbcTemplate.update(
                    "insert into expenses (business_pkid, expense_date, category, amount, currency)"
                            + " values (?, ?, ?, 5, 'USD')",
                    business.getPkid(),
                    TODAY,
                    spelling));

            send(post("/api/v1/expense"), bearer(someone), """
                            {"date": "%s", "category": "TRAVEL", "amount": 5}
                            """.formatted(TODAY))
                    .andExpect(jsonPath("$.expense.category").value(spellings.get(0)));
        }
    }

    @Test
    @DisplayName("an expense in another currency keeps a rate to the reporting currency, until its currency changes")
    void exchangeRates() throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "baseCurrency": "INR", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """)
                .andExpect(status().isOk());
        String id = JsonPath.read(
                record("""
                        {"date": "%s", "category": "Software", "amount": 20, "currency": "USD",
                         "exchangeRate": "83.250"}
                        """.formatted(TODAY))
                        .andExpect(jsonPath("$.expense.exchangeRate").value("83.25"))
                        .andExpect(jsonPath("$.expense.exchangeBase").value("INR"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.expense._id");
        record("""
                {"date": "%s", "category": "Rent", "amount": 20000, "currency": "INR", "exchangeRate": "1"}
                """.formatted(TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("An expense in INR, your reporting currency, needs no exchange rate"));

        send(patch("/api/v1/expense/{id}", id), "{\"notes\": \"Domain\"}")
                .andExpect(jsonPath("$.expense.exchangeRate").value("83.25"));
        send(patch("/api/v1/expense/{id}", id), "{\"currency\": \"EUR\"}")
                .andExpect(jsonPath("$.expense.currency").value("EUR"))
                .andExpect(jsonPath("$.expense.exchangeRate").doesNotExist())
                .andExpect(jsonPath("$.expense.exchangeBase").doesNotExist());
        send(patch("/api/v1/expense/{id}", id), "{\"exchangeRate\": \"90\"}")
                .andExpect(jsonPath("$.expense.exchangeRate").value("90"));
        send(patch("/api/v1/expense/{id}", id), "{\"exchangeRate\": \"\"}")
                .andExpect(jsonPath("$.expense.exchangeRate").doesNotExist());
    }

    @Test
    @DisplayName("the CSV holds every expense the search matches, newest first")
    void csv() throws Exception {
        record("""
                {"date": "%s", "vendor": "=cmd", "category": "Travel", "amount": 8, "taxAmount": 1.44,
                 "currency": "EUR", "paymentMethod": "Credit Card", "reference": "R-1", "notes": "Airport"}
                """.formatted(TODAY))
                .andExpect(status().isCreated());
        expense(TODAY.minusDays(1), "IndiGo", "Travel", "120");
        expense(TODAY, "Staples", "Office", "4");

        String csv = mockMvc.perform(get("/api/v1/expense/csv?category=Travel")
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice-spot-expenses-%s.csv\"".formatted(TODAY)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.lines()).containsExactly(
                "\uFEFFDate,Vendor,Category,Currency,Amount,Tax,Tax reclaimable,Total,Exchange rate,Exchange rate to,"
                        + "Payment method,Reference,Notes",
                TODAY + ",'=cmd,Travel,EUR,8.00,1.44,No,9.44,,,Credit Card,R-1,Airport",
                TODAY.minusDays(1) + ",IndiGo,Travel,USD,120.00,0.00,No,120.00,,,,,");
    }

    @Test
    @DisplayName("another business's expenses are neither listed nor changeable")
    void otherBusinesses() throws Exception {
        String id = expense(TODAY, "Uber", "Travel", "8");
        String stranger = bearer(createUser());

        send(get("/api/v1/expense"), stranger, "").andExpect(jsonPath("$.totalExpenses").value(0));
        send(get("/api/v1/expense/categories"), stranger, "").andExpect(jsonPath("$.categories", hasSize(0)));
        send(patch("/api/v1/expense/{id}", id), stranger, "{\"amount\": 1}").andExpect(status().isForbidden());
        send(delete("/api/v1/expense/{id}", id), stranger, "").andExpect(status().isForbidden());
        send(delete("/api/v1/expense/{id}", "not-an-id"), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That expense does not exist"));
        list("").andExpect(jsonPath("$.expenses[0].amount").value(8.0));
    }

    @Test
    @DisplayName("a viewer reads and downloads expenses but records nothing; a member records, changes and deletes")
    void roles() throws Exception {
        Business business = businessOf(owner);
        String id = expense(TODAY, "Uber", "Travel", "8");
        String viewer = bearer(createMember(business, BusinessRole.VIEWER), business);
        String member = bearer(createMember(business, BusinessRole.MEMBER), business);
        String json = "{\"date\": \"%s\", \"category\": \"Office\", \"amount\": 5}".formatted(TODAY);

        send(get("/api/v1/expense"), viewer, "").andExpect(jsonPath("$.totalExpenses").value(1));
        send(get("/api/v1/expense/csv"), viewer, "").andExpect(status().isOk());
        send(post("/api/v1/expense"), viewer, json)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Permission.EDIT_RECORDS.refusal()));
        send(delete("/api/v1/expense/{id}", id), viewer, "").andExpect(status().isForbidden());

        send(post("/api/v1/expense"), member, json).andExpect(status().isCreated());
        send(patch("/api/v1/expense/{id}", id), member, "{\"amount\": 9}").andExpect(status().isOk());
        send(delete("/api/v1/expense/{id}", id), member, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an expense is dated no later than today somewhere in the world, when recorded and when changed")
    void notAfterToday() throws Exception {
        LocalDate tomorrowFurthestAhead = LocalDate.now(FURTHEST_AHEAD).plusDays(1);

        for (LocalDate future : List.of(TODAY.plusDays(2), tomorrowFurthestAhead)) {
            record("{\"date\": \"%s\", \"category\": \"Office\", \"amount\": 5}".formatted(future))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("An expense can't be dated after today"));
        }
        String id = expense(TODAY, "Uber", "Travel", "8");
        send(patch("/api/v1/expense/{id}", id), "{\"date\": \"%s\"}".formatted(tomorrowFurthestAhead))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An expense can't be dated after today"));
    }

    @Test
    @DisplayName("an expense dated today in UTC+14 is taken while it is still the day before where the business is")
    void todayAheadOfTheBusiness() throws Exception {
        // Ten hours behind UTC, so always a day behind UTC+14.
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "Pacific/Honolulu"}
                """)
                .andExpect(status().isOk());
        LocalDate latest = LocalDate.now(FURTHEST_AHEAD);

        String id = expense(latest.minusDays(1), "Uber", "Travel", "8");
        send(patch("/api/v1/expense/{id}", id), "{\"date\": \"%s\"}".formatted(latest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expense.date").value(latest.toString()));
        expense(latest, "IRCTC", "Travel", "30");
    }
}
