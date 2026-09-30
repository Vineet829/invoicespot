package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class ReportIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private String client(User user, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"email\": \"%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(name, UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document(User user, String client, String type, String currency, String amount, LocalDate due)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, type, currency, noon(due), amount)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private String invoice(String client, String amount, LocalDate due) throws Exception {
        return document(owner, client, "Invoice", "USD", amount, due);
    }

    private void pay(String document, String amount, LocalDate date, String method) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", document)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": %s, \"datePaid\": \"%s\", \"paymentMethod\": \"%s\"}"
                                .formatted(amount, date, method)))
                .andExpect(status().is2xxSuccessful());
    }

    private void issuedAt(String document, Instant at) {
        jdbcTemplate.update(
                "update documents set created_at = ? where id = ?", Timestamp.from(at), UUID.fromString(document));
    }

    private static String noon(LocalDate day) {
        return day.atTime(12, 0).toInstant(ZoneOffset.UTC).toString();
    }

    private ResultActions report(String period) throws Exception {
        return mockMvc.perform(get("/api/v1/report")
                .param("period", period)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    @Test
    @DisplayName("billed and received are summed per month and currency; receipts count as received when issued")
    void months() throws Exception {
        LocalDate thisMonth = today.withDayOfMonth(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        String client = client(owner, "Acme");
        pay(invoice(client, "100", today.plusDays(10)), "40", today, "Cash");
        String older = invoice(client, "200", today.plusDays(10));
        issuedAt(older, lastMonth.atTime(12, 0).toInstant(ZoneOffset.UTC));
        pay(older, "50", lastMonth.plusDays(1), "Bank Transfer");
        pay(older, "30", today, "Bank Transfer");
        document(owner, client, "Receipt", "USD", "70", today.plusDays(10));
        document(owner, client, "Quotation", "USD", "500", today.plusDays(10));
        document(owner, client, "Invoice", "EUR", "80", today.plusDays(10));

        report("3m")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("3m"))
                .andExpect(jsonPath("$.from").value(thisMonth.minusMonths(2).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencies[0].months[0].month")
                        .value(YearMonth.from(thisMonth.minusMonths(2)).toString()))
                .andExpect(jsonPath("$.currencies[0].months[0].billed").value(0.0))
                .andExpect(jsonPath("$.currencies[0].months[1].billed").value(200.0))
                .andExpect(jsonPath("$.currencies[0].months[1].received").value(50.0))
                .andExpect(jsonPath("$.currencies[0].months[2].billed").value(170.0))
                .andExpect(jsonPath("$.currencies[0].months[2].received").value(140.0))
                .andExpect(jsonPath("$.currencies[0].billed").value(370.0))
                .andExpect(jsonPath("$.currencies[0].received").value(190.0))
                .andExpect(jsonPath("$.currencies[0].methods[0].method").value("Bank Transfer"))
                .andExpect(jsonPath("$.currencies[0].methods[0].payments").value(2))
                .andExpect(jsonPath("$.currencies[0].methods[0].amount").value(80.0))
                .andExpect(jsonPath("$.currencies[0].methods[1].method").value("Cash"))
                .andExpect(jsonPath("$.currencies[0].methods.length()").value(2))
                .andExpect(jsonPath("$.currencies[1].currency").value("EUR"))
                .andExpect(jsonPath("$.currencies[1].billed").value(80.0))
                .andExpect(jsonPath("$.currencies[1].received").value(0.0));
    }

    @Test
    @DisplayName("months follow the owner's time zone")
    void timeZone() throws Exception {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0,
                                 "taxLabel": "Tax", "timeZone": "America/Los_Angeles"}
                                """))
                .andExpect(status().isOk());
        Instant monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        String client = client(owner, "Acme");
        issuedAt(invoice(client, "10", today.plusDays(10)), monthStart.minusSeconds(3600));
        issuedAt(invoice(client, "20", today.plusDays(10)), monthStart.plusSeconds(3600));

        report("3m")
                .andExpect(jsonPath("$.currencies[0].months[1].billed").value(10.0))
                .andExpect(jsonPath("$.currencies[0].months[2].billed").value(20.0));
    }

    @Test
    @DisplayName("unpaid invoices are grouped by how many days they are overdue today")
    void ageing() throws Exception {
        String client = client(owner, "Acme");
        invoice(client, "100", today.plusDays(5));
        invoice(client, "10", today);
        invoice(client, "20", today.minusDays(1));
        invoice(client, "30", today.minusDays(30));
        invoice(client, "40", today.minusDays(31));
        pay(invoice(client, "100", today.minusDays(45)), "25", today, "Cash");
        pay(invoice(client, "100", today.minusDays(45)), "100", today, "Cash");
        document(owner, client, "Quotation", "USD", "100", today.minusDays(45));
        invoice(client, "50", today.minusDays(75));
        invoice(client, "60", today.minusDays(91));

        report("12m")
                .andExpect(jsonPath("$.currencies[0].ageing[0].key").value("notDue"))
                .andExpect(jsonPath("$.currencies[0].ageing[0].label").value("Not due yet"))
                .andExpect(jsonPath("$.currencies[0].ageing[0].invoices").value(2))
                .andExpect(jsonPath("$.currencies[0].ageing[0].outstanding").value(110.0))
                .andExpect(jsonPath("$.currencies[0].ageing[1].invoices").value(2))
                .andExpect(jsonPath("$.currencies[0].ageing[1].outstanding").value(50.0))
                .andExpect(jsonPath("$.currencies[0].ageing[2].invoices").value(2))
                .andExpect(jsonPath("$.currencies[0].ageing[2].outstanding").value(115.0))
                .andExpect(jsonPath("$.currencies[0].ageing[3].invoices").value(1))
                .andExpect(jsonPath("$.currencies[0].ageing[3].outstanding").value(50.0))
                .andExpect(jsonPath("$.currencies[0].ageing[4].key").value("over90"))
                .andExpect(jsonPath("$.currencies[0].ageing[4].invoices").value(1))
                .andExpect(jsonPath("$.currencies[0].ageing[4].outstanding").value(60.0))
                .andExpect(jsonPath("$.currencies[0].outstanding").value(385.0))
                .andExpect(jsonPath("$.currencies[0].overdue").value(275.0));
    }

    @Test
    @DisplayName("the five clients billed the most are listed, and the rest are summed together")
    void topClients() throws Exception {
        String first = client(owner, "Client 7");
        invoice(first, "350", today.plusDays(10));
        invoice(first, "350", today.plusDays(10));
        pay(invoice(client(owner, "Client 6"), "600", today.plusDays(10)), "100", today, "Cash");
        for (int number = 5; number >= 1; number--) {
            invoice(client(owner, "Client " + number), String.valueOf(number * 100), today.plusDays(10));
        }

        report("12m")
                .andExpect(jsonPath("$.currencies[0].clients.length()").value(5))
                .andExpect(jsonPath("$.currencies[0].clients[0].customerId").value(first))
                .andExpect(jsonPath("$.currencies[0].clients[0].name").value("Client 7"))
                .andExpect(jsonPath("$.currencies[0].clients[0].documents").value(2))
                .andExpect(jsonPath("$.currencies[0].clients[0].billed").value(700.0))
                .andExpect(jsonPath("$.currencies[0].clients[1].name").value("Client 6"))
                .andExpect(jsonPath("$.currencies[0].clients[1].paid").value(100.0))
                .andExpect(jsonPath("$.currencies[0].clients[1].outstanding").value(500.0))
                .andExpect(jsonPath("$.currencies[0].clients[4].name").value("Client 3"))
                .andExpect(jsonPath("$.currencies[0].otherClients.clients").value(2))
                .andExpect(jsonPath("$.currencies[0].otherClients.documents").value(2))
                .andExpect(jsonPath("$.currencies[0].otherClients.billed").value(300.0))
                .andExpect(jsonPath("$.currencies[0].otherClients.outstanding").value(300.0));
    }

    @Test
    @DisplayName("an owner sees only their own figures, and can pick any earlier year since their first")
    void periods() throws Exception {
        User other = createUser();
        document(other, client(other, "Someone"), "Invoice", "USD", "999", today);
        report("12m").andExpect(jsonPath("$.currencies").isEmpty()).andExpect(jsonPath("$.years").isEmpty());

        int year = today.getYear() - 2;
        issuedAt(invoice(client(owner, "Acme"), "25", today), LocalDate.of(year, 6, 15).atTime(12, 0)
                .toInstant(ZoneOffset.UTC));

        report("12m").andExpect(jsonPath("$.years[0]").value(year + 1)).andExpect(jsonPath("$.years[1]").value(year));
        report(String.valueOf(year))
                .andExpect(jsonPath("$.from").value(year + "-01-01"))
                .andExpect(jsonPath("$.to").value(year + "-12-31"))
                .andExpect(jsonPath("$.currencies[0].months.length()").value(12))
                .andExpect(jsonPath("$.currencies[0].months[5].billed").value(25.0));
        report("forever")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period from the list"));
    }

    @Test
    @DisplayName("each section downloads as CSV for every currency, with every client ranked")
    void csv() throws Exception {
        for (int number = 1; number <= 6; number++) {
            invoice(client(owner, "Client " + number), String.valueOf(number * 10), today.plusDays(10));
        }
        String eur = client(owner, "=cmd|' /C calc'!A0");
        pay(document(owner, eur, "Invoice", "EUR", "80", today.plusDays(10)), "30", today, "Bank Transfer");
        String month = YearMonth.from(today).toString();

        String income = mockMvc.perform(get("/api/v1/report/csv/income")
                        .param("period", "3m")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"invoice-spot-income-%s-to-%s.csv\""
                                .formatted(today.withDayOfMonth(1).minusMonths(2), today)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(income.lines().toList())
                .startsWith("\uFEFFCurrency,Month,Billed,Received")
                .contains("USD," + month + ",210.00,0.00", "EUR," + month + ",80.00,30.00")
                .hasSize(7);

        String clients = mockMvc.perform(get("/api/v1/report/csv/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(clients.lines().toList())
                .containsExactly(
                        "\uFEFFCurrency,Client,Documents,Billed,Paid,Owed",
                        "USD,Client 6,1,60.00,0.00,60.00",
                        "USD,Client 5,1,50.00,0.00,50.00",
                        "USD,Client 4,1,40.00,0.00,40.00",
                        "USD,Client 3,1,30.00,0.00,30.00",
                        "USD,Client 2,1,20.00,0.00,20.00",
                        "USD,Client 1,1,10.00,0.00,10.00",
                        "EUR,'=cmd|' /C calc'!A0,1,80.00,30.00,50.00");

        mockMvc.perform(get("/api/v1/report/csv/secrets").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That report does not exist"));
    }
}
