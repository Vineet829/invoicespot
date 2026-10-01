package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

// A client's portal: their issued documents, balances and statement, and
// what they may do there, with nothing more than a share link shows.
class ClientPortalIntegrationTest extends IntegrationTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private String network;
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void owner() {
        owner = createUser();
        network = "10.30." + NETWORKS.incrementAndGet() + ".1";
    }

    private String customer(String name) throws Exception {
        return JsonPath.read(
                mockMvc.perform(post("/api/v1/customer/create")
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"%s\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                        .formatted(name, "client-" + UUID.randomUUID() + "@clients.test")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.createdCustomer._id");
    }

    private record Made(String id, String number) {}

    private Made document(String customer, String type, String currency, int price, boolean draft) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %d, "quantity": 1}]}
                                """.formatted(
                                        customer, type, currency, draft,
                                        today.plusDays(30).atTime(12, 0).toInstant(ZoneOffset.UTC), price)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Made(JsonPath.read(body, "$.newDocument._id"), JsonPath.read(body, "$.newDocument.documentNumber"));
    }

    private String portal(String customer) throws Exception {
        String url = JsonPath.read(
                mockMvc.perform(post("/api/v1/customer/{id}/portal", customer)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.url", matchesPattern(
                                "^https?://.+/c/[A-Za-z0-9_-]{43}$")))
                        .andExpect(jsonPath("$.kept").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
        return request.with(sent -> {
            sent.setRemoteAddr(network);
            return sent;
        });
    }

    private ResultActions open(String token) throws Exception {
        return mockMvc.perform(from(get("/api/v1/public/portal/{token}", token)));
    }

    @Test
    @DisplayName("a client's portal lists only their issued documents, void ones marked, with balances per currency")
    void portal() throws Exception {
        String acme = customer("Acme");
        String other = customer("Other Co");
        Made dollars = document(acme, "Invoice", "USD", 200, false);
        Made rupees = document(acme, "Invoice", "INR", 1000, false);
        mockMvc.perform(post("/api/v1/document/{id}/payment", rupees.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 400}"))
                .andExpect(status().isCreated());
        Made quotation = document(acme, "Quotation", "USD", 500, false);
        document(acme, "Invoice", "USD", 50, true);
        Made voided = document(acme, "Invoice", "USD", 70, false);
        mockMvc.perform(post("/api/v1/document/{id}/void", voided.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Sent twice\"}"))
                .andExpect(status().isOk());
        Made elsewhere = document(other, "Invoice", "USD", 999, false);
        String token = portal(acme);

        open(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client").value("Acme"))
                .andExpect(jsonPath("$.business.name").value("Test User"))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.documents[*].documentNumber", contains(
                        voided.number(), quotation.number(), rupees.number(), dollars.number())))
                .andExpect(jsonPath("$.documents[0].state").value("void"))
                .andExpect(jsonPath("$.documents[0].balance").value(0.0))
                .andExpect(jsonPath("$.documents[1].balance").doesNotExist())
                .andExpect(jsonPath("$.documents[2].balance").value(600.0))
                .andExpect(jsonPath("$.balances[?(@.currency == 'INR')].due", contains(600.0)))
                .andExpect(jsonPath("$.balances[?(@.currency == 'INR')].paid", contains(400.0)))
                .andExpect(jsonPath("$.balances[?(@.currency == 'USD')].due", contains(200.0)));

        mockMvc.perform(from(get("/api/v1/public/portal/{token}/document", token).param("number", dollars.number())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value(dollars.number()))
                .andExpect(jsonPath("$.balance").value(200.0));
        for (String hidden : List.of(elsewhere.number(), "NOPE-1")) {
            mockMvc.perform(from(get("/api/v1/public/portal/{token}/document", token).param("number", hidden)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("There is no such document in this portal"));
        }
        mockMvc.perform(get("/api/v1/customer/{id}/portal", acme).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.lastViewedAt", notNullValue()))
                .andExpect(jsonPath("$.url").value(endsWith("/c/" + token)));
    }

    @Test
    @DisplayName("from the portal a client gets PDFs and their statement, answers a quotation and reports a payment")
    void actions() throws Exception {
        String acme = customer("Acme");
        Made invoice = document(acme, "Invoice", "USD", 200, false);
        Made quotation = document(acme, "Quotation", "USD", 500, false);
        String token = portal(acme);

        byte[] pdf = mockMvc.perform(from(get("/api/v1/public/portal/{token}/document/pdf", token)
                        .param("number", invoice.number())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(PdfText.of(pdf)).contains(invoice.number());
        byte[] statement = mockMvc.perform(from(get("/api/v1/public/portal/{token}/statement", token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(PdfText.of(statement)).contains("Acme", invoice.number());

        mockMvc.perform(from(post("/api/v1/public/portal/{token}/document/accept", token)
                        .param("number", quotation.number())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAt", notNullValue()));
        mockMvc.perform(from(post("/api/v1/public/portal/{token}/document/payment-claims", token)
                        .param("number", invoice.number())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 120, \"paidOn\": \"%s\", \"method\": \"UPI\"}".formatted(today))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReports[0].status").value("PENDING"));
        mockMvc.perform(get("/api/v1/document/{id}/payment-claims", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.claims[0].amount").value(120.0));
    }

    @Test
    @DisplayName("the portal names documents by number and holds no ids or emails, field for field")
    void contract() throws Exception {
        String acme = customer("Acme");
        document(acme, "Invoice", "USD", 200, false);
        JsonNode portal = objectMapper.readTree(
                open(portal(acme)).andReturn().getResponse().getContentAsString());

        assertThat(fields(portal)).containsExactly(
                "balances", "business", "client", "documents", "page", "pages", "success", "total");
        assertThat(fields(portal.get("business"))).containsExactly("lines", "logo", "name");
        assertThat(fields(portal.get("balances").get(0))).containsExactly(
                "credited", "currency", "due", "invoiced", "overdue", "paid", "refunded");
        assertThat(fields(portal.get("documents").get(0))).containsExactly(
                "balance", "currency", "documentNumber", "documentType", "dueDate", "expired", "issueDate",
                "overdue", "state", "status", "total");
        assertThat(portal.toString()).doesNotContain("@clients.test", acme);
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("a new link replaces the old one, a turned-off one is gone, and only those who edit records manage it")
    void links() throws Exception {
        String acme = customer("Acme");
        String first = portal(acme);
        String second = portal(acme);
        open(first).andExpect(status().isNotFound());
        open(second).andExpect(status().isOk());

        Business business = businessOf(owner);
        User viewer = createMember(business, BusinessRole.VIEWER);
        String viewing = bearer(viewer, business);
        mockMvc.perform(get("/api/v1/customer/{id}/portal", acme).header(HttpHeaders.AUTHORIZATION, viewing))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customer/{id}/portal", acme).header(HttpHeaders.AUTHORIZATION, viewing))
                .andExpect(status().isForbidden());
        String stranger = bearer(createUser());
        mockMvc.perform(get("/api/v1/customer/{id}/portal", acme).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/customer/{id}/portal", acme).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("The portal link was turned off."));
        open(second)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This link doesn't work anymore"));
        mockMvc.perform(get("/api/v1/customer/{id}/portal", acme).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("a network asking for portals more than 60 times a minute is asked to wait")
    void slowedDown() throws Exception {
        String token = portal(customer("Acme"));
        List<Integer> statuses = new ArrayList<>();
        for (int request = 0; request < 75; request++) {
            statuses.add(open(token).andReturn().getResponse().getStatus());
        }
        // The allowance refills a request a second, so a few more get in.
        assertThat(statuses.subList(0, 60)).containsOnly(200);
        assertThat(statuses.subList(60, 75)).contains(429);
    }

    // Every email sent so far, as recipient and message.
    private List<String[]> sentEmails() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        List<String[]> sent = new ArrayList<>();
        for (MimeMessage message : captor.getAllValues()) {
            message.saveChanges();
            sent.add(new String[] {message.getAllRecipients()[0].toString(), message.getContent().toString()});
        }
        return sent;
    }

    private static String portalLink(String text) {
        Matcher link = Pattern.compile("https?://\\S+/c/([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(link.find()).as("a portal link in: " + text).isTrue();
        return link.group(1);
    }

    @Test
    @DisplayName("a client gets their portal link by email, the same link each time, or on WhatsApp")
    void send() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        String acme = customer("Acme");
        String email = JsonPath.read(
                mockMvc.perform(get("/api/v1/customer/{id}", acme).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.customer.email");
        ResultActions draft = mockMvc.perform(
                get("/api/v1/customer/{id}/portal/email", acme).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
        draft.andExpect(jsonPath("$.to").value(email))
                .andExpect(jsonPath("$.subject").value("Your documents from Test User"))
                .andExpect(jsonPath("$.message", containsString("statement: {link}")))
                .andExpect(jsonPath("$.note").doesNotExist());

        for (int time = 0; time < 2; time++) {
            mockMvc.perform(post("/api/v1/customer/{id}/portal/email", acme)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("The portal link was emailed."));
        }
        List<String[]> sent = sentEmails().stream().filter(message -> message[0].equals(email)).toList();
        assertThat(sent).hasSize(2);
        String token = portalLink(sent.get(0)[1]);
        assertThat(portalLink(sent.get(1)[1])).isEqualTo(token);
        open(token).andExpect(status().isOk());

        String whatsApp = JsonPath.read(
                mockMvc.perform(post("/api/v1/customer/{id}/portal/whatsapp", acme)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.linkReplaced").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        assertThat(whatsApp).startsWith("https://wa.me/?text=");
        assertThat(portalLink(URLDecoder.decode(whatsApp, StandardCharsets.UTF_8))).isEqualTo(token);

        jdbcTemplate.update(
                "update portal_links set token_sealed = null"
                        + " where customer_pkid = (select pkid from customers where id = ?)",
                UUID.fromString(acme));
        mockMvc.perform(get("/api/v1/customer/{id}/portal/email", acme)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.note", containsString("Sending replaces this client's portal link made on")));
        mockMvc.perform(post("/api/v1/customer/{id}/portal/whatsapp", acme)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.linkReplaced").value(true));
        open(token).andExpect(status().isNotFound());
    }
}
