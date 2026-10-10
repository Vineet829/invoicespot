package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OverdueAlertsIntegrationTest extends IntegrationTest {

    @Autowired
    private OverdueAlerts overdueAlerts;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    private static String noon(LocalDate day) {
        return day.atTime(12, 0).toInstant(ZoneOffset.UTC).toString();
    }

    private String invoice(User owner, LocalDate due) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "issueDate": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(
                                        (String) JsonPath.read(customer, "$.createdCustomer._id"),
                                        today.minusDays(30),
                                        noon(due))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private void changeDueDate(User owner, String id, LocalDate due) throws Exception {
        mockMvc.perform(patch("/api/v1/document/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\": \"%s\"}".formatted(noon(due))))
                .andExpect(status().isOk());
    }

    private List<String> alerts(User owner) throws Exception {
        String body = mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.notifications[?(@.type == 'INVOICE_OVERDUE')].message");
    }

    @Test
    @DisplayName("an unpaid invoice past its due date raises one alert; paid and future ones none")
    void onceEach() throws Exception {
        User owner = createUser();
        invoice(owner, today.minusDays(1));
        String paid = invoice(owner, today.minusDays(2));
        mockMvc.perform(post("/api/v1/document/{id}/payment", paid)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": 100}"));
        invoice(owner, today.plusDays(1));

        overdueAlerts.raise(Instant.now());
        overdueAlerts.raise(Instant.now());

        assertThat(alerts(owner)).containsExactly("Invoice INV-0001 for Beta LLC is overdue, with USD 100.00 unpaid");
    }

    @Test
    @DisplayName("an overdue invoice alerts again only once a later due date has passed too")
    void newDueDate() throws Exception {
        User owner = createUser();
        String id = invoice(owner, today.minusDays(1));
        overdueAlerts.raise(Instant.now());

        changeDueDate(owner, id, today.minusDays(3));
        overdueAlerts.raise(Instant.now());
        assertThat(alerts(owner)).hasSize(1);

        changeDueDate(owner, id, today.plusDays(1));
        overdueAlerts.raise(Instant.now());
        assertThat(alerts(owner)).hasSize(1);
        overdueAlerts.raise(Instant.now().plus(Duration.ofDays(2)));
        assertThat(alerts(owner)).hasSize(2);
    }

    @Test
    @DisplayName("two runs at once raise an invoice's alert once")
    void twoRunsAtOnce() throws Exception {
        User owner = createUser();
        String id = invoice(owner, today.minusDays(1));
        Instant now = Instant.now();
        // Both runs start while a transaction of the test's own holds the
        // invoice locked, so both look for overdue invoices before either has
        // alerted it.
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                documentRepository.findForUpdateByExternalId(UUID.fromString(id)).orElseThrow();
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Integer> first = pool.submit(() -> overdueAlerts.raise(now));
            Future<Integer> second = pool.submit(() -> overdueAlerts.raise(now));
            Thread.sleep(1500);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(alerts(owner)).hasSize(1);
    }
}
