package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// The documents list reads which documents a page shows, then the documents
// themselves. Another transaction deletes a draft and settles an invoice in
// between: it holds the clients table too, which the list reads only with
// the documents, and commits once the list waits there.
class DocumentListSnapshotIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DocumentDirectory documentDirectory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private String draft;
    private String invoice;

    @BeforeEach
    void documents() throws Exception {
        owner = createUser();
        String client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
        draft = document(client, "300", true);
        invoice = document(client, "100", false);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String document(String client, String amount, boolean asDraft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, asDraft, amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // Reads while the draft is deleted and the invoice settled: the change
    // is made first, and committed once the read waits for the clients table.
    private <T> T whileChanged(Callable<T> read) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection(); Connection watcher = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            watcher.setAutoCommit(true);
            try (PreparedStatement delete = holder.prepareStatement("delete from documents where id = ?");
                    PreparedStatement settle = holder.prepareStatement(
                            "update documents set status = 'Paid', total_amount_received = total where id = ?");
                    Statement lock = holder.createStatement()) {
                delete.setObject(1, UUID.fromString(draft));
                delete.executeUpdate();
                settle.setObject(1, UUID.fromString(invoice));
                settle.executeUpdate();
                lock.execute("lock table customers in access exclusive mode");
            }
            int holderPid = pid(holder);
            Future<T> result = pool.submit(read);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!blocks(watcher, holderPid)) {
                if (result.isDone()) {
                    holder.rollback();
                    throw new AssertionError("The read finished without waiting for the change: " + result.get());
                }
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("The read never waited for the change");
                }
                Thread.sleep(20);
            }
            holder.commit();
            return result.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static int pid(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_backend_pid()");
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static boolean blocks(Connection watcher, int holderPid) throws SQLException {
        try (PreparedStatement statement = watcher.prepareStatement(
                "select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))")) {
            statement.setInt(1, holderPid);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) > 0;
            }
        }
    }

    @Test
    @DisplayName("a page shows its documents as they stood when it was asked for, whatever changes meanwhile")
    void asTheyStood() throws Exception {
        String page = whileChanged(() -> body(mockMvc.perform(get("/api/v1/document/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))));

        int total = JsonPath.read(page, "$.totalDocuments");
        List<String> ids = JsonPath.read(page, "$.myDocuments[*]._id");
        List<String> statuses = JsonPath.read(page, "$.myDocuments[*].status");
        assertThat(total).isEqualTo(2);
        assertThat(ids).containsExactly(invoice, draft);
        assertThat(statuses).containsExactly("Not Paid", "Not Paid");
        // Read again, the draft is gone and the invoice paid.
        String now = body(send(get("/api/v1/document/all"), "").andExpect(status().isOk()));
        List<String> left = JsonPath.read(now, "$.myDocuments[*]._id");
        List<String> paid = JsonPath.read(now, "$.myDocuments[*].status");
        assertThat(left).containsExactly(invoice);
        assertThat(paid).containsExactly("Paid");
    }

    @Test
    @DisplayName("read without one moment's view, a document deleted before it is loaded is left out")
    void gone() throws Exception {
        TransactionTemplate readCommitted = new TransactionTemplate(transactionManager);
        readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        readCommitted.setReadOnly(true);
        Long businessPkid = businessOf(owner).getPkid();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<Document> loaded = whileChanged(() -> readCommitted.execute(status -> documentDirectory
                .page(
                        businessPkid,
                        DocumentSearch.of(
                                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                                null, 1, 10),
                        today,
                        ZoneOffset.UTC)));

        assertThat(loaded).extracting(document -> document.getId().toString()).containsExactly(invoice);
        assertThat(loaded).extracting(Document::getStatus).containsExactly(DocumentStatus.PAID);
    }
}
