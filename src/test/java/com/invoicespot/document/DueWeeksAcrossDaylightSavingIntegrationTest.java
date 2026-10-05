package com.invoicespot.document;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The weeks what is owed falls due in are days of the business's calendar,
// not stretches of 168 hours: from Monday 26 October 2026 in Los Angeles,
// this week ends with Sunday 1 November, when the clocks go back an hour, so
// it lasts 169 hours.
class DueWeeksAcrossDaylightSavingIntegrationTest extends IntegrationTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    // Noon on that Monday in Los Angeles.
    @TestConfiguration(proxyBeanMethods = false)
    static class Autumn {

        @Bean
        @Primary
        Clock autumnClock() {
            return Clock.fixed(Instant.parse("2026-10-26T19:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private String client;

    @BeforeEach
    void business() throws Exception {
        owner = createUser();
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(LOS_ANGELES.getId()))
                .andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // An invoice for the amount, due at a moment on the business's clock.
    private String dueAt(String amount, LocalDateTime moment) throws Exception {
        String invoice = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "issueDate": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, TODAY.minusDays(3), TODAY.plusDays(30), amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
        jdbcTemplate.update(
                "update documents set due_date = ? where id = ?",
                Timestamp.from(moment.atZone(LOS_ANGELES).toInstant()),
                UUID.fromString(invoice));
        return invoice;
    }

    @Test
    @DisplayName("a week the clocks go back in runs to the end of its last day")
    void clocksGoBack() throws Exception {
        String first = dueAt("100", TODAY.atTime(0, 30));
        String last = dueAt("200", LocalDate.of(2026, 11, 1).atTime(23, 30));
        String next = dueAt("300", LocalDate.of(2026, 11, 2).atTime(0, 30));
        String late = dueAt("400", TODAY.minusDays(1).atTime(23, 30));

        // This week ends with the last minutes of Sunday 1 November, and the
        // next begins with the first of Monday.
        send(get("/api/v1/document/all").param("outstanding", "true").param("due", "this-week"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myDocuments[*]._id", containsInAnyOrder(first, last)))
                .andExpect(jsonPath("$.myDocuments[*].dueDate", containsInAnyOrder("2026-10-26", "2026-11-01")))
                .andExpect(jsonPath("$.totalDocuments").value(2))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(300.0));
        send(get("/api/v1/document/all").param("outstanding", "true").param("due", "next-week"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", containsInAnyOrder(next)))
                .andExpect(jsonPath("$.myDocuments[*].dueDate", containsInAnyOrder("2026-11-02")))
                .andExpect(jsonPath("$.totalDocuments").value(1))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(300.0));
        // Due in the last minutes of the day before, it is overdue already.
        send(get("/api/v1/document/all").param("overdue", "true"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", containsInAnyOrder(late)))
                .andExpect(jsonPath("$.myDocuments[*].dueDate", containsInAnyOrder("2026-10-25")))
                .andExpect(jsonPath("$.totalDocuments").value(1));
    }
}
