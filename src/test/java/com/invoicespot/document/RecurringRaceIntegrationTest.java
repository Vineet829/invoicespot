package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.document.dto.RecurringRequest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// A schedule is made with its invoice locked, as deleting a draft locks it,
// so a delete that arrives while a schedule is made for the draft waits, then
// finds the schedule and keeps the draft instead of deleting both. The test
// makes the schedule in a transaction of its own while the delete arrives.
class RecurringRaceIntegrationTest extends IntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecurringService recurringService;

    private BusinessAccess access;
    private String session;
    private String client;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    @BeforeEach
    void setUp() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        access = new BusinessAccess(business.getPkid(), business.getId(), BusinessRole.OWNER, owner);
        session = bearer(owner);
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private String draft() throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": true,
                         "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // Makes a monthly schedule for the document in a transaction that stays
    // open while the request arrives, and commits it once the request waits.
    // Gives the request's response.
    private MockHttpServletResponse whileScheduling(String document, Callable<ResultActions> request) throws Exception {
        RecurringRequest monthly = new RecurringRequest(
                document, "monthly", LocalDate.now(ZoneOffset.UTC).plusDays(1), null, null, null, null);
        CountDownLatch made = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> making = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                recurringService.create(access, monthly);
                made.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!made.await(30, TimeUnit.SECONDS)) {
                making.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The schedule was never made");
            }
            Future<MockHttpServletResponse> response = pool.submit(() -> request.call().andReturn().getResponse());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (waitingForLocks() < 1 && !response.isDone() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            release.countDown();
            making.get(30, TimeUnit.SECONDS);
            return response.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private int waitingForLocks() {
        return jdbc.queryForObject(
                "select count(*) from pg_stat_activity"
                        + " where datname = current_database() and wait_event_type = 'Lock'",
                Integer.class);
    }

    @Test
    @DisplayName("a delete that waits while a schedule is made for the draft is refused, and both stay")
    void deleteWhileScheduling() throws Exception {
        String draft = draft();

        MockHttpServletResponse response =
                whileScheduling(draft, () -> send(delete("/api/v1/document/{id}", draft), ""));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.message"))
                .isEqualTo("This draft repeats. Stop its schedule before deleting it");
        send(get("/api/v1/document/{id}", draft), "").andExpect(jsonPath("$.document.state").value("draft"));
        send(get("/api/v1/document/{id}/recurring", draft), "")
                .andExpect(jsonPath("$.schedule.documentId").value(draft));
    }
}
