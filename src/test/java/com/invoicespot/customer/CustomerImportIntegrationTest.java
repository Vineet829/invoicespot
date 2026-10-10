package com.invoicespot.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CustomerImportIntegrationTest extends IntegrationTest {

    @Autowired
    private CustomerImport customerImport;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private String tag;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        tag = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/customer/create")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Existing Co\", \"email\": \"existing-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                        .formatted(tag)));
    }

    private ResultActions send(String path, User user, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/customer/" + path)
                .file(new MockMultipartFile("file", "clients.csv", "text/csv", content))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private byte[] file() {
        return ("﻿EMAIL,Name,Phone,City,Notes\r\n"
                        + "harbor-%1$s@clients.test,Harbor & Pine,+1 555 0100,Portland,ignored\r\n"
                        + "not-an-email,Bad Email,+1,,\r\n"
                        + ",No Email,+1,,\r\n"
                        + "EXISTING-%1$s@clients.test,Dup Existing,+1,,\r\n"
                        + "harbor-%1$s@clients.test,Harbor Again,+1,,\r\n"
                        + "\r\n"
                        + "oak-%1$s@clients.test,\"Oak Street, Ltd\",'+44 20 7946 0958,London,\r\n"
                        + "nophone-%1$s@clients.test,No Phone,,,\r\n")
                .formatted(tag)
                .getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions clients(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/customer/all").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    @Test
    @DisplayName("the preview marks what stops each row, by its row in the sheet, and saves nothing")
    void preview() throws Exception {
        send("import/preview", owner, file())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.ready").value(2))
                .andExpect(jsonPath("$.rows[0].row").value(2))
                .andExpect(jsonPath("$.rows[0].name").value("Harbor & Pine"))
                .andExpect(jsonPath("$.rows[0].phoneNumber").value("+1 555 0100"))
                .andExpect(jsonPath("$.rows[0].city").value("Portland"))
                .andExpect(jsonPath("$.rows[0].problems").isEmpty())
                .andExpect(jsonPath("$.rows[1].problems[0]").value("Enter a valid email address"))
                .andExpect(jsonPath("$.rows[2].problems[0]").value("The email is missing"))
                .andExpect(jsonPath("$.rows[3].problems[0]").value("You already have a client with this email"))
                .andExpect(jsonPath("$.rows[4].problems[0]").value("This email is already on row 2"))
                .andExpect(jsonPath("$.rows[5].row").value(8))
                .andExpect(jsonPath("$.rows[5].name").value("Oak Street, Ltd"))
                .andExpect(jsonPath("$.rows[5].phoneNumber").value("+44 20 7946 0958"))
                .andExpect(jsonPath("$.rows[5].problems").isEmpty())
                .andExpect(jsonPath("$.rows[6].problems[0]").value("The phone number is missing"));
        clients(owner).andExpect(jsonPath("$.totalCustomers").value(1));
    }

    @Test
    @DisplayName("the import adds the ready rows and skips the rest, and never adds a client twice")
    void importsReadyRows() throws Exception {
        send("import", owner, file())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("2 clients added, 5 skipped"))
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.skipped").value(5));
        clients(owner).andExpect(jsonPath("$.totalCustomers").value(3));

        send("import", owner, file())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No row in the file can be added"));
        clients(owner).andExpect(jsonPath("$.totalCustomers").value(3));
    }

    @Test
    @DisplayName("a clients file exported from the app imports as it is")
    void roundTrip() throws Exception {
        send("import", owner, file()).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"'+Acme\", \"email\": \"acme-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(tag)))
                .andExpect(status().isOk());
        byte[] exported = mockMvc.perform(get("/api/v1/customer/csv").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        User other = createUser();
        send("import", other, exported)
                .andExpect(jsonPath("$.message").value("4 clients added"));
        clients(other)
                .andExpect(jsonPath("$.totalCustomers").value(4))
                .andExpect(jsonPath("$.myCustomers[?(@.name == 'Oak Street, Ltd')].phoneNumber")
                        .value("+44 20 7946 0958"))
                .andExpect(jsonPath("$.myCustomers[?(@.email == 'acme-%s@clients.test')].name", tag)
                        .value("'+Acme"));
    }

    @Test
    @DisplayName("semicolon files are read; unreadable, empty, headless and oversized files are refused")
    void files() throws Exception {
        send("import/preview", owner, "Name;Email;Phone\nAcme;acme-%s@x.test;+1\n".formatted(tag).getBytes())
                .andExpect(jsonPath("$.ready").value(1));
        // A heading may hold the other separator, quoted or not.
        send("import/preview", owner, "Name;Email;Phone;Tel, Fax, Mobile, Pager\nAcme;acme-%s@x.test;+1;\n"
                        .formatted(tag)
                        .getBytes())
                .andExpect(jsonPath("$.ready").value(1));
        send("import/preview", owner, "Name;Email;Phone;\"Notes (work, home, fax, other)\"\nAcme;acme-%s@x.test;+1;\n"
                        .formatted(tag)
                        .getBytes())
                .andExpect(jsonPath("$.ready").value(1));
        send("import/preview", owner, "Name,Email,Phone,\"Notes; misc; other; more; extra\"\nAcme,acme-%s@x.test,+1,\n"
                        .formatted(tag)
                        .getBytes())
                .andExpect(jsonPath("$.ready").value(1));
        send("import/preview", owner, "Company,Mail\nAcme,acme@x.test\n".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The first row must name the columns, with at least Name and Email"));
        send("import/preview", owner, "Name,Email,Phone\nCafé,cafe@x.test,+1\n".getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Save the file as CSV UTF-8, then try again"));
        send("import/preview", owner, new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a CSV file to import"));
        StringBuilder many = new StringBuilder("Name,Email,Phone\n");
        for (int row = 0; row <= CustomerImport.MOST_ROWS; row++) {
            many.append("Client ").append(row).append(",c").append(row).append("@x.test,+1\n");
        }
        send("import/preview", owner, many.toString().getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Import at most 1,000 clients at a time"));
    }

    @Test
    @DisplayName("an import sent while another is adding clients waits for it, then skips the clients it added")
    void importsTakeTurns() throws Exception {
        Business business = businessOf(owner);
        BusinessAccess access = new BusinessAccess(business.getPkid(), business.getId(), BusinessRole.OWNER, owner);
        String first = "first-" + tag + "@clients.test";
        String second = "second-" + tag + "@clients.test";
        String third = "third-" + tag + "@clients.test";

        whileImporting(access, csvOf(first, second), () -> send("import", owner, csvOf(second, first, third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("1 client added, 2 skipped"));
    }

    // A file with a client for each email.
    private static byte[] csvOf(String... emails) {
        StringBuilder text = new StringBuilder("Name,Email,Phone\r\n");
        for (String email : emails) {
            text.append(email, 0, email.indexOf('@')).append(',').append(email).append(",+1\r\n");
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    // Sends the request while a transaction of the test's own imports `held`
    // and stays open, and lets that import commit once something waits for it.
    private ResultActions whileImporting(BusinessAccess access, byte[] held, Callable<ResultActions> request)
            throws Exception {
        CountDownLatch imported = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                customerImport.importAll(access, new MockMultipartFile("file", "clients.csv", "text/csv", held));
                imported.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!imported.await(30, TimeUnit.SECONDS)) {
                holder.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The held import never ran");
            }
            Future<ResultActions> sent = pool.submit(request);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!sent.isDone() && waitingForLocks() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            return sent.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // How many connections to the test's database wait for a lock.
    private int waitingForLocks() {
        return jdbc.queryForObject(
                "select count(*) from pg_stat_activity"
                        + " where datname = current_database() and wait_event_type = 'Lock'",
                Integer.class);
    }
}
