package com.invoicespot.report;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Profit and loss in each currency, before tax: issued invoices and receipts
// less credit notes, by the day each is dated, against expenses by the day
// each was paid, with the tax on them unless it can be claimed back.
class ProfitReportIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private final LocalDate lastMonth = today.withDayOfMonth(1).minusMonths(1);
    private final LocalDate fourMonthsAgo = today.withDayOfMonth(1).minusMonths(4);
    private String session;
    private String client;

    private ResultActions send(String as, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, as)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String business() throws Exception {
        String owner = bearer(createUser());
        send(owner, put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """).andExpect(status().isOk());
        return owner;
    }

    private String document(String type, String currency, String price, String rate, LocalDate issued, boolean draft)
            throws Exception {
        return JsonPath.read(
                send(session, post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "issueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1,
                                                   "taxRate": "%s"}]}
                                """.formatted(client, type, currency, draft, issued, price, rate))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
    }

    private void expense(String as, LocalDate date, String category, String currency, String amount, String tax,
            boolean reclaimable) throws Exception {
        send(as, post("/api/v1/expense"), """
                        {"date": "%s", "category": "%s", "currency": "%s", "amount": %s, "taxAmount": %s,
                         "taxReclaimable": %s}
                        """.formatted(date, category, currency, amount, tax, reclaimable))
                .andExpect(status().isCreated());
    }

    private ResultActions report(String period) throws Exception {
        return send(session, get("/api/v1/report/profit").param("period", period), "");
    }

    @BeforeEach
    void setUp() throws Exception {
        session = business();
        client = JsonPath.read(
                send(session, post("/api/v1/customer/create"), """
                                {"name": "Acme", "email": "%s@clients.test", "phoneNumber": "+1"}
                                """.formatted(UUID.randomUUID()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.createdCustomer._id");

        // Income: 1,000 before its tax this month, less a credit note of 200,
        // and a receipt of 500 last month.
        String invoice = document("Invoice", "USD", "1000", "18", today, false);
        String note = JsonPath.read(
                send(session, post("/api/v1/document/{id}/credit-note", invoice), "")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
        send(session, patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": 200, "quantity": 1}]}
                """).andExpect(status().isOk());
        send(session, post("/api/v1/document/{id}/issue", note), "{}").andExpect(status().isOk());
        document("Receipt", "USD", "500", "0", lastMonth, false);
        document("Invoice", "EUR", "400", "0", today, false);
        document("Invoice", "USD", "3000", "0", fourMonthsAgo, false);
        // Not income: a draft, a void invoice and a quotation.
        document("Invoice", "USD", "700", "0", today, true);
        String voided = document("Invoice", "USD", "300", "0", today, false);
        send(session, post("/api/v1/document/{id}/void", voided), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
        document("Quotation", "USD", "900", "0", today, false);

        // Expenses: tax counts only when it can't be claimed back.
        expense(session, today, "Office", "USD", "500", "90", false);
        expense(session, today, "Software", "USD", "100", "18", true);
        expense(session, lastMonth, "Office", "USD", "50", "0", false);
        expense(session, today, "Travel", "EUR", "30", "0", false);
        expense(session, fourMonthsAgo, "Rent", "USD", "800", "0", false);
        expense(business(), today, "Office", "USD", "999", "0", false);
    }

    @Test
    @DisplayName("each month with income before tax less credit notes, or with expenses, and what each category cost")
    void months() throws Exception {
        report("3m")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("3m"))
                .andExpect(jsonPath("$.from").value(today.withDayOfMonth(1).minusMonths(2).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencies[0].income").value(1300.0))
                .andExpect(jsonPath("$.currencies[0].expenses").value(740.0))
                .andExpect(jsonPath("$.currencies[0].profit").value(560.0))
                .andExpect(jsonPath("$.currencies[0].months.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].months[0].month").value(YearMonth.from(lastMonth).toString()))
                .andExpect(jsonPath("$.currencies[0].months[0].income").value(500.0))
                .andExpect(jsonPath("$.currencies[0].months[0].expenses").value(50.0))
                .andExpect(jsonPath("$.currencies[0].months[0].profit").value(450.0))
                .andExpect(jsonPath("$.currencies[0].months[1].month").value(YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.currencies[0].months[1].income").value(800.0))
                .andExpect(jsonPath("$.currencies[0].months[1].expenses").value(690.0))
                .andExpect(jsonPath("$.currencies[0].months[1].profit").value(110.0))
                .andExpect(jsonPath("$.currencies[0].categories[0].category").value("Office"))
                .andExpect(jsonPath("$.currencies[0].categories[0].expenses").value(640.0))
                .andExpect(jsonPath("$.currencies[0].categories[1].category").value("Software"))
                .andExpect(jsonPath("$.currencies[0].categories[1].expenses").value(100.0))
                .andExpect(jsonPath("$.currencies[0].categories.length()").value(2))
                .andExpect(jsonPath("$.currencies[1].currency").value("EUR"))
                .andExpect(jsonPath("$.currencies[1].income").value(400.0))
                .andExpect(jsonPath("$.currencies[1].expenses").value(30.0))
                .andExpect(jsonPath("$.currencies[1].profit").value(370.0))
                .andExpect(jsonPath("$.currencies[1].categories[0].category").value("Travel"));
    }

    @Test
    @DisplayName("a longer period takes in older months, a loss is a negative profit, and periods come from a list")
    void periods() throws Exception {
        report("12m")
                .andExpect(jsonPath("$.currencies[0].months.length()").value(3))
                .andExpect(jsonPath("$.currencies[0].months[0].month").value(YearMonth.from(fourMonthsAgo).toString()))
                .andExpect(jsonPath("$.currencies[0].months[0].income").value(3000.0))
                .andExpect(jsonPath("$.currencies[0].months[0].expenses").value(800.0))
                .andExpect(jsonPath("$.currencies[0].income").value(4300.0))
                .andExpect(jsonPath("$.currencies[0].expenses").value(1540.0))
                .andExpect(jsonPath("$.currencies[0].categories[*].category")
                        .value(contains("Rent", "Office", "Software")));

        expense(session, today, "Equipment", "EUR", "1000", "0", false);
        report("3m")
                .andExpect(jsonPath("$.currencies[1].expenses").value(1030.0))
                .andExpect(jsonPath("$.currencies[1].profit").value(-630.0));

        report("forever")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period from the list"));
    }

    @Test
    @DisplayName("categories that cost the same go by name, whatever its case")
    void ties() throws Exception {
        session = business();
        expense(session, today, "delta", "USD", "300", "0", false);
        expense(session, today, "Fees", "USD", "50", "0", false);
        expense(session, today, "ads", "USD", "50", "0", false);
        expense(session, today, "Books", "USD", "50", "0", false);
        expense(session, today, "cloud", "USD", "50", "0", false);
        // Each 50 once tax that can't be claimed back is added and tax that
        // can is left out.
        expense(session, today, "Equipment", "USD", "40", "10", false);
        expense(session, today, "Dues", "USD", "50", "5", true);

        report("90d")
                .andExpect(jsonPath(
                        "$.currencies[0].categories[*].category",
                        contains("delta", "ads", "Books", "cloud", "Dues", "Equipment", "Fees")))
                .andExpect(jsonPath(
                        "$.currencies[0].categories[*].expenses",
                        contains(300.0, 50.0, 50.0, 50.0, 50.0, 50.0, 50.0)));
    }

    @Test
    @DisplayName("a category in two spellings is one row, spelled as first used, as the expenses list finds it")
    void twoSpellings() throws Exception {
        User owner = createUser();
        session = bearer(owner);
        // Two members can save a new category at the same moment, each in
        // their own spelling; otherwise a new expense takes the one in use.
        for (String spelling : List.of("Travel", "travel", "travel")) {
            jdbcTemplate.update("""
                    insert into expenses (business_pkid, expense_date, category, amount, currency)
                    values (?, ?, ?, 100, 'USD')
                    """, businessOf(owner).getPkid(), today, spelling);
        }
        expense(session, today, "Office", "USD", "50", "0", false);

        report("30d")
                .andExpect(jsonPath("$.currencies[0].categories[*].category", contains("Travel", "Office")))
                .andExpect(jsonPath("$.currencies[0].categories[*].expenses", contains(300.0, 50.0)));
        send(session, get("/api/v1/expense")
                                .param("period", "30d")
                                .param("currency", "USD")
                                .param("category", "Travel"),
                        "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(3))
                .andExpect(jsonPath("$.totals[0].spent").value(300.0));
    }
}
