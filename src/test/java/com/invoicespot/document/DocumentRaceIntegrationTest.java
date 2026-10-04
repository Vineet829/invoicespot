package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
// never lands on a draft that was issued meanwhile, two conversions of a
// quotation make one invoice, and a reminder undoes no payment. A client's
// answer to a quotation, or report of a payment, takes its turn too. Each
// test holds the document locked in a transaction of its own while the
// requests arrive.
class DocumentRaceIntegrationTest extends IntegrationTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentIssuer documentIssuer;

    @Autowired
    private DocumentService documentService;

    private String session;
    private String client;
    private String network;

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
        network = "10.40." + NETWORKS.incrementAndGet() + ".1";
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
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
    // document locked and does `meanwhile` to it, then lets it go once each
    // request waits for it, and gives each request's status.
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
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (waitingForLocks() < requests.size() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
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

    // How many connections to the test's database wait for a lock, as a
    // request does for the held document, whether it reads or writes it.
    private int waitingForLocks() {
        return jdbc.queryForObject(
                "select count(*) from pg_stat_activity"
                        + " where datname = current_database() and wait_event_type = 'Lock'",
                Integer.class);
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

    // A reminder written by hand, without the document's link, so nothing
    // locks the invoice before the reminder is counted.
    private ResultActions remind(String id) throws Exception {
        return send(post("/api/v1/document/{id}/remind", id), "{\"message\": \"Please pay.\"}");
    }

    @Test
    @DisplayName("a reminder counted while the daily run counts one is counted as well")
    void remindWhileReminded() throws Exception {
        String invoice = document("Invoice", false);

        List<Integer> statuses = whileLocked(
                invoice,
                document -> document.setRemindersSent(document.getRemindersSent() + 1),
                List.of(() -> remind(invoice)));

        assertThat(statuses).containsExactly(200);
        send(get("/api/v1/document/{id}", invoice), "").andExpect(jsonPath("$.document.remindersSent").value(2));
    }

    @Test
    @DisplayName("a reminder counted while the invoice is paid leaves it paid")
    void remindWhilePaid() throws Exception {
        String invoice = document("Invoice", false);

        List<Integer> statuses = whileLocked(
                invoice,
                document -> documentService.recordPayment(
                        document, new CreatePaymentRequest(null, new BigDecimal("100"), null, null, null), null),
                List.of(() -> remind(invoice)));

        assertThat(statuses).containsExactly(200);
        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(100.0))
                .andExpect(jsonPath("$.document.remindersSent").value(1));
    }

    // A quotation, issued, and the token of its share link.
    private String[] sharedQuotation() throws Exception {
        String id = document("Quotation", false);
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        return new String[] {id, url.substring(url.lastIndexOf('/') + 1)};
    }

    private String number(String id) throws Exception {
        return JsonPath.read(body(send(get("/api/v1/document/{id}", id), "")), "$.document.documentNumber");
    }

    // The token of the client's portal link.
    private String portal() throws Exception {
        String url = JsonPath.read(body(send(post("/api/v1/customer/{id}/portal", client), "")), "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    // A request from the client, by a link, with no sign-in.
    private ResultActions fromClient(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(sent -> {
                    sent.setRemoteAddr(network);
                    return sent;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int count(String sql, String id) {
        return jdbc.queryForObject(sql, Integer.class, UUID.fromString(id));
    }

    // The answers the quotation's timeline records.
    private int answers(String id) {
        return count(
                "select count(*) from document_events where type in ('ACCEPTED', 'DECLINED')"
                        + " and document_pkid = (select pkid from documents where id = ?)",
                id);
    }

    @Test
    @DisplayName("a quotation accepted and declined at once takes one answer, and the other is refused")
    void answerTwice() throws Exception {
        String[] quotation = sharedQuotation();

        List<Integer> statuses = whileLocked(quotation[0], document -> {}, List.of(
                () -> fromClient(post("/api/v1/public/share/{token}/accept", quotation[1]), ""),
                () -> fromClient(post("/api/v1/public/share/{token}/decline", quotation[1]), "")));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(answers(quotation[0])).isEqualTo(1);
    }

    @Test
    @DisplayName("a quotation answered at once through its link and its client's portal takes one answer")
    void answerTwiceThroughPortal() throws Exception {
        String[] quotation = sharedQuotation();
        String number = number(quotation[0]);
        String portal = portal();

        List<Integer> statuses = whileLocked(quotation[0], document -> {}, List.of(
                () -> fromClient(post("/api/v1/public/share/{token}/accept", quotation[1]), ""),
                () -> fromClient(
                        post("/api/v1/public/portal/{token}/document/decline", portal).param("number", number), "")));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(answers(quotation[0])).isEqualTo(1);
    }

    @Test
    @DisplayName("a quotation withdrawn while its client accepts it stays withdrawn, and the answer is refused")
    void acceptWhileWithdrawn() throws Exception {
        String[] quotation = sharedQuotation();

        List<Integer> statuses = whileLocked(
                quotation[0],
                document -> {
                    document.setVoidedAt(Instant.now());
                    document.setVoidReason("Wrong price");
                },
                List.of(() -> fromClient(post("/api/v1/public/share/{token}/accept", quotation[1]), "")));

        assertThat(statuses).containsExactly(409);
        send(get("/api/v1/document/{id}", quotation[0]), "")
                .andExpect(jsonPath("$.document.state").value("void"))
                .andExpect(jsonPath("$.document.acceptedAt").doesNotExist());
    }

    @Test
    @DisplayName("a payment reported through the portal while the invoice is voided is refused, and nothing waits")
    void reportWhileVoided() throws Exception {
        String invoice = document("Invoice", false);
        String number = number(invoice);
        String portal = portal();
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();

        List<Integer> statuses = whileLocked(
                invoice,
                document -> {
                    document.setVoidedAt(Instant.now());
                    document.setVoidReason("Sent twice");
                },
                List.of(() -> fromClient(
                        post("/api/v1/public/portal/{token}/document/payment-claims", portal).param("number", number),
                        "{\"amount\": 40, \"paidOn\": \"%s\", \"method\": \"BANK_TRANSFER\"}".formatted(yesterday))));

        assertThat(statuses).containsExactly(409);
        assertThat(count(
                        "select count(*) from payment_claims"
                                + " where document_pkid = (select pkid from documents where id = ?)",
                        invoice))
                .isZero();
    }
}
