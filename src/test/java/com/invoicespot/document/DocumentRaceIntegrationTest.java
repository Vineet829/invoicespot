package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Whatever changes a document locks it first, so a change that arrives while
// another holds it waits, then sees what that one did: an edit or a delete
// never lands on a draft that was issued meanwhile, and two conversions of a
// quotation make one invoice. Each test holds the document locked in a
// transaction of its own while the requests arrive.
class DocumentRaceIntegrationTest extends IntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentIssuer documentIssuer;

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
        session = bearer(createUser());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private String document(String type, boolean draft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "%s", "currency": "USD", "draft": %s,
                         "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client, type, draft)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // Runs the requests while a transaction of the test's own holds the
    // document locked and does `meanwhile` to it, then lets it go, and gives
    // each request's status.
    private List<Integer> whileLocked(String id, Consumer<Document> meanwhile, List<Callable<ResultActions>> requests)
            throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests.size() + 1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                Document document = documentRepository.findForUpdateByExternalId(UUID.fromString(id)).orElseThrow();
                meanwhile.accept(document);
                documentRepository.flush();
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!held.await(30, TimeUnit.SECONDS)) {
                holder.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The document was never held");
            }
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<ResultActions> request : requests) {
                results.add(pool.submit(() -> request.call().andReturn().getResponse().getStatus()));
            }
            Thread.sleep(500);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // Issued by the same code the issue endpoint uses, inside the holder's
    // transaction.
    private void issue(Document document) {
        documentIssuer.issue(document, null, null);
    }

    @Test
    @DisplayName("an edit to a draft's lines that waits while the draft is issued is refused, and the lines stay")
    void editWhileIssued() throws Exception {
        String id = document("Invoice", true);

        List<Integer> statuses = whileLocked(id, this::issue, List.of(() -> send(
                patch("/api/v1/document/{id}", id),
                "{\"billingItems\": [{\"itemName\": \"Work\", \"unitPrice\": 500, \"quantity\": 1}]}")));

        assertThat(statuses).containsExactly(409);
        send(get("/api/v1/document/{id}", id), "")
                .andExpect(jsonPath("$.document.state").value("issued"))
                .andExpect(jsonPath("$.document.billingItems[0].unitPrice").value(100))
                .andExpect(jsonPath("$.document.subTotal").value(100.0));
    }

    @Test
    @DisplayName("a delete that waits while the draft is issued is refused, and the document stays")
    void deleteWhileIssued() throws Exception {
        String id = document("Invoice", true);

        List<Integer> statuses = whileLocked(id, this::issue, List.of(
                () -> send(delete("/api/v1/document/{id}", id), "")));

        assertThat(statuses).containsExactly(409);
        send(get("/api/v1/document/{id}", id), "").andExpect(jsonPath("$.document.state").value("issued"));
    }

    @Test
    @DisplayName("two conversions of one quotation at once take turns, so it becomes one invoice")
    void convertTwice() throws Exception {
        String quotation = document("Quotation", false);

        List<Integer> statuses = whileLocked(quotation, document -> {}, List.of(
                () -> send(post("/api/v1/document/{id}/convert", quotation), ""),
                () -> send(post("/api/v1/document/{id}/convert", quotation), "")));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject(
                        "select count(*) from documents where source_pkid = (select pkid from documents where id = ?)",
                        Integer.class,
                        UUID.fromString(quotation)))
                .isEqualTo(1);
    }
}
