package com.invoicespot.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentIssuer;
import com.invoicespot.document.DocumentRepository;
import com.invoicespot.user.User;
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

// Saving settings and issuing a document both write the business's settings
// row, and each reads it under the row's lock. So whichever comes second sees
// what the first saved: issuing never puts back settings saved while it ran,
// and settings and payment details saved at once both stay. Each test holds a
// transaction of its own open while the requests arrive.
class SettingsRaceIntegrationTest extends IntegrationTest {

    private static final String SETTINGS = """
            {"defaultCurrency": "USD", "paymentTermsDays": 30, "defaultTaxRate": 0, "taxLabel": "VAT",
             "timeZone": "Europe/Berlin", "remindersEnabled": true}
            """;
    private static final String DETAILS = """
            {"accountHolder": "Ada & Co", "bankName": "HDFC Bank", "accountNumber": "50100123456789",
             "bankCodeType": "IFSC", "bankCode": "hdfc0001234", "upiId": "Ada@OKHDFCBANK",
             "payLink": "https://pay.example.com/ada", "instructions": "Use the invoice number as the reference."}
            """;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentIssuer documentIssuer;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private String session;
    private Long businessPkid;
    private String draft;

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
        session = bearer(owner);
        businessPkid = businessOf(owner).getPkid();
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0, "taxLabel": "Tax",
                 "timeZone": "UTC"}
                """).andExpect(status().isOk());
        String client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
        draft = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": true,
                         "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // Issues the draft as the issue endpoint does, reading the settings for
    // the business's time zone first and taking the number last, with the
    // request made in between. Gives the request's status.
    private int whileIssuing(Callable<ResultActions> request) throws Exception {
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch answered = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> issuing = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                Document document = documentRepository.findForUpdateByExternalId(UUID.fromString(draft)).orElseThrow();
                settingsService.forBusiness(businessPkid);
                read.countDown();
                try {
                    answered.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                documentIssuer.issue(document, null, null);
            }));
            if (!read.await(30, TimeUnit.SECONDS)) {
                issuing.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The draft was never being issued");
            }
            int status = request.call().andReturn().getResponse().getStatus();
            answered.countDown();
            issuing.get(30, TimeUnit.SECONDS);
            return status;
        } finally {
            answered.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("settings saved while a document is being issued stay saved")
    void settingsSavedWhileIssuing() throws Exception {
        assertThat(whileIssuing(() -> send(put("/api/v1/settings"), SETTINGS))).isEqualTo(200);

        send(get("/api/v1/settings"), "")
                .andExpect(jsonPath("$.settings.taxLabel").value("VAT"))
                .andExpect(jsonPath("$.settings.timeZone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.settings.paymentTermsDays").value(30))
                .andExpect(jsonPath("$.settings.remindersEnabled").value(true))
                .andExpect(jsonPath("$.settings.nextInvoiceNumber").value(2));
    }

    @Test
    @DisplayName("payment details saved while a document is being issued stay saved")
    void paymentDetailsSavedWhileIssuing() throws Exception {
        assertThat(whileIssuing(() -> send(put("/api/v1/settings/payment-details"), DETAILS))).isEqualTo(200);

        send(get("/api/v1/settings/payment-details"), "")
                .andExpect(jsonPath("$.paymentDetails.accountHolder").value("Ada & Co"))
                .andExpect(jsonPath("$.paymentDetails.upiId").value("ada@okhdfcbank"));
    }

    @Test
    @DisplayName("settings and payment details saved at the same moment both stay")
    void savedAtOnce() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Integer> statuses = new ArrayList<>();
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                jdbc.queryForObject(
                        "select pkid from business_settings where business_pkid = ? for update",
                        Long.class,
                        businessPkid);
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!held.await(30, TimeUnit.SECONDS)) {
                holder.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The settings were never held");
            }
            Future<Integer> settings = pool.submit(() ->
                    send(put("/api/v1/settings"), SETTINGS).andReturn().getResponse().getStatus());
            Thread.sleep(500);
            Future<Integer> details = pool.submit(() ->
                    send(put("/api/v1/settings/payment-details"), DETAILS).andReturn().getResponse().getStatus());
            Thread.sleep(500);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            statuses.add(settings.get(30, TimeUnit.SECONDS));
            statuses.add(details.get(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactly(200, 200);
        send(get("/api/v1/settings"), "").andExpect(jsonPath("$.settings.taxLabel").value("VAT"));
        send(get("/api/v1/settings/payment-details"), "")
                .andExpect(jsonPath("$.paymentDetails.accountHolder").value("Ada & Co"));
    }
}
