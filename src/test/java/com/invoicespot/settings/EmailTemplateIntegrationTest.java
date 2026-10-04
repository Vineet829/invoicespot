package com.invoicespot.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.settings.dto.EmailTemplateRequest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EmailTemplateIntegrationTest extends IntegrationTest {

    private static final String TEMPLATES = "/api/v1/settings/email-templates";

    @Autowired
    private EmailTemplateService templateService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmailTemplateRepository templateRepository;

    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private ResultActions save(String bearer, String kind, String json) throws Exception {
        return mockMvc.perform(put(TEMPLATES + "/{kind}", kind)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions preview(String bearer, String kind, String json) throws Exception {
        return mockMvc.perform(post(TEMPLATES + "/{kind}/preview", kind)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions emailDraft(String id) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/email", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    // An issued invoice of 200, due at the end of 2099, for Beta LLC.
    private String invoice() throws Exception {
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
                                 "dueDate": "2099-12-31T12:00:00Z",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    @Test
    @DisplayName("every kind of email starts with the standard subject and message, and the placeholders it fills")
    void standard() throws Exception {
        mockMvc.perform(get(TEMPLATES).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates", hasSize(7)))
                .andExpect(jsonPath("$.templates[*].kind", contains(
                        "INVOICE", "QUOTATION", "RECEIPT", "CREDIT_NOTE", "REMINDER", "PAYMENT_RECEIPT", "STATEMENT")))
                .andExpect(jsonPath("$.templates[0].custom").value(false))
                .andExpect(jsonPath("$.templates[0].subject").value("{document} from {business}"))
                .andExpect(jsonPath("$.templates[0].body").value(
                        "Hello {client},\n\nPlease find {document} from {business} attached.\n\n"
                                + "View and pay it online: {link}\n\n{payment}\n\n{business}"))
                .andExpect(jsonPath("$.templates[2].placeholders[*].name", contains(
                        "client", "document", "number", "amount", "link", "business")))
                .andExpect(jsonPath("$.templates[5].body").value(
                        "Hello {client},\n\nPlease find {document} from {business} attached.\n\n{business}"))
                .andExpect(jsonPath("$.templates[4].subject").value("Reminder: {document} from {business}"))
                .andExpect(jsonPath("$.templates[6].placeholders[*].name", contains("client", "document", "business")));
    }

    @Test
    @DisplayName("a business's own email is used until it goes back to the standard one, and only admins change it")
    void own() throws Exception {
        Business business = businessOf(owner);
        User member = createMember(business, BusinessRole.MEMBER);
        String id = invoice();

        save(bearer(owner), "invoice", """
                {"subject": "Invoice {number}\\nfor {amount}",
                 "body": "Hello {client},\\n\\n{balance} is due on {due}.\\n\\n{business}"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your invoice email was saved"))
                .andExpect(jsonPath("$.template.custom").value(true))
                .andExpect(jsonPath("$.template.subject").value("Invoice {number} for {amount}"));
        emailDraft(id)
                .andExpect(jsonPath("$.subject").value("Invoice INV-0001 for USD 200.00"))
                .andExpect(jsonPath("$.message").value(
                        "Hello Beta LLC,\n\nUSD 200.00 is due on Dec 31, 2099.\n\nTest User"));
        save(bearer(member, business), "INVOICE", "{\"subject\": \"x\", \"body\": \"y\"}")
                .andExpect(status().isForbidden());
        preview(bearer(member, business), "INVOICE", "{\"subject\": \"x\", \"body\": \"y\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get(TEMPLATES).header(HttpHeaders.AUTHORIZATION, bearer(member, business)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].custom").value(true));

        mockMvc.perform(delete(TEMPLATES + "/{kind}", "INVOICE").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your invoice email is the standard one again"))
                .andExpect(jsonPath("$.template.custom").value(false));
        emailDraft(id).andExpect(jsonPath("$.subject").value("Invoice INV-0001 from Test User"));
    }

    @Test
    @DisplayName("a placeholder its kind of email can't fill is refused, naming the ones it can")
    void refused() throws Exception {
        save(bearer(owner), "RECEIPT", "{\"subject\": \"{document}\", \"body\": \"Due {due}\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "{due} can't be used in receipt emails."
                                + " Use {client}, {document}, {number}, {amount}, {link} or {business}"));
        save(bearer(owner), "STATEMENT", "{\"subject\": \"{balance}\", \"body\": \"Hi\"}")
                .andExpect(status().isBadRequest());
        save(bearer(owner), "INVOICE", "{\"subject\": \" \", \"body\": \"Hi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Write a subject"));
        save(bearer(owner), "LETTER", "{\"subject\": \"Hi\", \"body\": \"Hi\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("There is no such kind of email"));
    }

    @Test
    @DisplayName("a preview fills the email being written from the business's latest documents, payments and clients")
    void previews() throws Exception {
        preview(bearer(owner), "INVOICE", "{\"subject\": \"{document}\", \"body\": \"{amount}\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("There's no issued invoice to preview this email with yet"));
        String id = invoice();

        preview(bearer(owner), "INVOICE", "{\"subject\": \"{document} for {client}\", \"body\": \"{amount}\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value("Invoice INV-0001"))
                .andExpect(jsonPath("$.client").value("Beta LLC"))
                .andExpect(jsonPath("$.subject").value("Invoice INV-0001 for Beta LLC"))
                .andExpect(jsonPath("$.message").value("USD 200.00"));
        preview(bearer(owner), "REMINDER", "{\"subject\": \"Reminder\", \"body\": \"{balance} {due_status}\"}")
                .andExpect(jsonPath("$.message").value("USD 200.00 is due on Dec 31, 2099"));
        preview(bearer(owner), "PAYMENT_RECEIPT", "{\"subject\": \"{document}\", \"body\": \"{amount}\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("There's no payment to preview this email with yet"));
        mockMvc.perform(post("/api/v1/document/{id}/payment", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 50}"))
                .andExpect(status().isCreated());
        String receipt = "{\"subject\": \"{document}\", \"body\": \"{amount}, {balance} left\"}";
        preview(bearer(owner), "PAYMENT_RECEIPT", receipt)
                .andExpect(jsonPath("$.subject").value("Payment receipt INV-0001-P1"))
                .andExpect(jsonPath("$.message").value("USD 50.00, USD 150.00 left"));
        preview(bearer(owner), "STATEMENT", "{\"subject\": \"{document}\", \"body\": \"Hi {client}\"}")
                .andExpect(jsonPath("$.document").value("Statement of account"))
                .andExpect(jsonPath("$.message").value("Hi Beta LLC"));
        preview(bearer(owner), "QUOTATION", "{\"subject\": \"{document}\", \"body\": \"{due}\"}")
                .andExpect(status().isNotFound());
        preview(bearer(owner), "STATEMENT", "{\"subject\": \"{document}\", \"body\": \"{amount}\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("two first saves of a kind of email at once both save, the later one last")
    void firstSavesTakeTurns() throws Exception {
        BusinessAccess access = access();

        whileHeld(
                        () -> templateService.save(access, "INVOICE", new EmailTemplateRequest("Held", "A note")),
                        () -> save(bearer(owner), "INVOICE", "{\"subject\": \"Mine\", \"body\": \"A note\"}"))
                .andExpect(status().isOk());

        assertThat(invoiceSubjects(access)).containsExactly("Mine");
    }

    @Test
    @DisplayName("a kind of email saved while it is reset is saved")
    void saveWhileReset() throws Exception {
        BusinessAccess access = access();
        save(bearer(owner), "INVOICE", "{\"subject\": \"First\", \"body\": \"A note\"}")
                .andExpect(status().isOk());

        whileHeld(
                        () -> {
                            templateService.reset(access, "INVOICE");
                            templateRepository.flush();
                        },
                        () -> save(bearer(owner), "INVOICE", "{\"subject\": \"Mine\", \"body\": \"A note\"}"))
                .andExpect(status().isOk());

        assertThat(invoiceSubjects(access)).containsExactly("Mine");
    }

    private BusinessAccess access() {
        Business business = businessOf(owner);
        return new BusinessAccess(business.getPkid(), business.getId(), BusinessRole.OWNER, owner);
    }

    private List<String> invoiceSubjects(BusinessAccess access) {
        return jdbc.queryForList(
                "select subject from email_templates where business_pkid = ? and kind = 'INVOICE'",
                String.class,
                access.businessPkid());
    }

    // Sends the request while a transaction of the test's own does `holding`
    // and stays open, and lets it commit once something waits for it.
    private ResultActions whileHeld(Runnable holding, Callable<ResultActions> request) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
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
