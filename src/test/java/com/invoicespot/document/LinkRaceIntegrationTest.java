package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// A new share or portal link replaces the current one with its document or
// client locked, as sending a link does, so links made at once take turns:
// each is made, one stays working, and an email that replaces a link once it
// has gone carries one that works. Each test holds something in a transaction
// of its own while the requests arrive.
class LinkRaceIntegrationTest extends IntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ShareService shareService;

    @Autowired
    private PortalService portalService;

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
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    private String invoice() throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                         "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // Runs the requests while a transaction of the test's own does `holding`
    // and keeps what it locked, then lets it go once each request waits, and
    // gives each request's status.
    private List<Integer> whileHeld(Runnable holding, List<Callable<ResultActions>> requests) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests.size() + 1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                holding.run();
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!held.await(30, TimeUnit.SECONDS)) {
                holder.get(1, TimeUnit.SECONDS);
                throw new AssertionError("Nothing was ever held");
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
    // request does for what the test holds, or for another request.
    private int waitingForLocks() {
        return jdbc.queryForObject(
                "select count(*) from pg_stat_activity"
                        + " where datname = current_database() and wait_event_type = 'Lock'",
                Integer.class);
    }

    private int activeShareLinks(String document) {
        return jdbc.queryForObject(
                "select count(*) from share_links l join documents d on d.pkid = l.document_pkid"
                        + " where d.id = ? and l.revoked_at is null",
                Integer.class,
                UUID.fromString(document));
    }

    private int activePortalLinks() {
        return jdbc.queryForObject(
                "select count(*) from portal_links l join customers c on c.pkid = l.customer_pkid"
                        + " where c.id = ? and l.revoked_at is null",
                Integer.class,
                UUID.fromString(client));
    }

    // The token of the link in the last email sent, a share link ("p") or a
    // portal link ("c").
    private String emailedToken(String kind) throws Exception {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        Matcher link = Pattern.compile("/" + kind + "/([A-Za-z0-9_-]{43})").matcher(text(message.getContent()));
        assertThat(link.find()).as("a link in the email").isTrue();
        return link.group(1);
    }

    private static String text(Object content) throws Exception {
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int part = 0; part < multipart.getCount(); part++) {
                BodyPart body = multipart.getBodyPart(part);
                if (body.getFileName() == null) {
                    text.append(text(body.getContent()));
                }
            }
            return text.toString();
        }
        return content instanceof String text ? text : "";
    }

    @Test
    @DisplayName("two new share links made at once are both made, and the second replaces the first")
    void shareTwice() throws Exception {
        String invoice = invoice();
        send(post("/api/v1/document/{id}/share", invoice), "").andExpect(status().isCreated());

        List<Integer> statuses = whileHeld(
                () -> jdbc.queryForList(
                        "select l.pkid from share_links l join documents d on d.pkid = l.document_pkid"
                                + " where d.id = ? and l.revoked_at is null for update of l",
                        UUID.fromString(invoice)),
                List.of(
                        () -> send(post("/api/v1/document/{id}/share", invoice), ""),
                        () -> send(post("/api/v1/document/{id}/share", invoice), "")));

        assertThat(statuses).containsExactly(201, 201);
        assertThat(activeShareLinks(invoice)).isEqualTo(1);
    }

    @Test
    @DisplayName("an email that replaces a link once sent, sent while a new link is made, carries a link that works")
    void emailWhileShared() throws Exception {
        String invoice = invoice();
        send(post("/api/v1/document/{id}/share", invoice), "").andExpect(status().isCreated());
        // As if the link was made before links were kept sealed: an email
        // replaces it, once it has gone.
        jdbc.update(
                "update share_links set token_sealed = null"
                        + " where document_pkid = (select pkid from documents where id = ?)",
                UUID.fromString(invoice));

        List<Integer> statuses = whileHeld(
                () -> shareService.create(access, invoice),
                List.of(() -> send(post("/api/v1/document/{id}/email", invoice), "{}")));

        assertThat(statuses).containsExactly(200);
        assertThat(jdbc.queryForObject(
                        "select count(*) from share_links where token_hash = ? and revoked_at is null",
                        Integer.class,
                        SecretTokens.hash(emailedToken("p"))))
                .isEqualTo(1);
        assertThat(activeShareLinks(invoice)).isEqualTo(1);
    }

    @Test
    @DisplayName("two new portal links made at once are both made, and the second replaces the first")
    void portalTwice() throws Exception {
        send(post("/api/v1/customer/{id}/portal", client), "").andExpect(status().isCreated());

        List<Integer> statuses = whileHeld(
                () -> jdbc.queryForList(
                        "select l.pkid from portal_links l join customers c on c.pkid = l.customer_pkid"
                                + " where c.id = ? and l.revoked_at is null for update of l",
                        UUID.fromString(client)),
                List.of(
                        () -> send(post("/api/v1/customer/{id}/portal", client), ""),
                        () -> send(post("/api/v1/customer/{id}/portal", client), "")));

        assertThat(statuses).containsExactly(201, 201);
        assertThat(activePortalLinks()).isEqualTo(1);
    }

    @Test
    @DisplayName("a portal email that replaces a link once sent, sent while a new link is made, carries one that works")
    void portalEmailWhileMade() throws Exception {
        send(post("/api/v1/customer/{id}/portal", client), "").andExpect(status().isCreated());
        jdbc.update(
                "update portal_links set token_sealed = null"
                        + " where customer_pkid = (select pkid from customers where id = ?)",
                UUID.fromString(client));

        List<Integer> statuses = whileHeld(
                () -> portalService.create(access, client),
                List.of(() -> send(post("/api/v1/customer/{id}/portal/email", client), "{}")));

        assertThat(statuses).containsExactly(200);
        assertThat(jdbc.queryForObject(
                        "select count(*) from portal_links where token_hash = ? and revoked_at is null",
                        Integer.class,
                        SecretTokens.hash(emailedToken("c"))))
                .isEqualTo(1);
        assertThat(activePortalLinks()).isEqualTo(1);
    }
}
