package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

// Emails about a document carry its link to view and pay it, and how to pay,
// without ever breaking a link a client already has, unless the person
// sending was told first.
class DocumentEmailLinkIntegrationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("https?://\\S+/p/[A-Za-z0-9_-]{43}");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutomaticReminders reminders;

    private User owner;

    private record Sent(String to, String text) {}

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        mockMvc.perform(put("/api/v1/settings/payment-details")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\": \"50100123456789\", \"bankCodeType\": \"IFSC\","
                                + " \"bankCode\": \"HDFC0001234\"}"))
                .andExpect(status().isOk());
    }

    private record Invoice(String id, String email) {}

    private Invoice invoice(LocalDate due) throws Exception {
        String email = "client-" + UUID.randomUUID() + "@clients.test";
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                """.formatted(
                                        (String) JsonPath.read(customer, "$.createdCustomer._id"),
                                        due.atTime(12, 0).toInstant(ZoneOffset.UTC))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Invoice(JsonPath.read(body, "$.newDocument._id"), email);
    }

    private ResultActions draft(Invoice invoice) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/email", invoice.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private void email(Invoice invoice) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/email", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // Every email sent so far, with the text of its message.
    private List<Sent> sent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        List<Sent> sent = new ArrayList<>();
        for (MimeMessage message : captor.getAllValues()) {
            message.saveChanges();
            sent.add(new Sent(message.getAllRecipients()[0].toString(), text(message.getContent())));
        }
        return sent;
    }

    private static String text(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
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
        return "";
    }

    private Sent lastTo(Invoice invoice) throws Exception {
        List<Sent> to = sent().stream().filter(email -> email.to().equals(invoice.email())).toList();
        assertThat(to).isNotEmpty();
        return to.get(to.size() - 1);
    }

    private static String link(String text) {
        Matcher link = LINK.matcher(text);
        assertThat(link.find()).as("a share link in: " + text).isTrue();
        return link.group();
    }

    private String token(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    // As if the link was made before links were kept sealed.
    private void forgetSealed(Invoice invoice) {
        jdbcTemplate.update(
                "update share_links set token_sealed = null"
                        + " where document_pkid = (select pkid from documents where id = ?)",
                UUID.fromString(invoice.id()));
    }

    private long links(Invoice invoice) {
        return jdbcTemplate.queryForObject(
                "select count(*) from share_links l join documents d on d.pkid = l.document_pkid where d.id = ?",
                Long.class,
                UUID.fromString(invoice.id()));
    }

    @Test
    @DisplayName("an invoice's email carries its link to view and pay and how to pay, and later emails the same link")
    void sameLink() throws Exception {
        Invoice invoice = invoice(LocalDate.now(ZoneOffset.UTC).plusDays(30));
        draft(invoice)
                .andExpect(jsonPath("$.message", containsString("View and pay it online: {link}")))
                .andExpect(jsonPath("$.message", containsString(
                        "How to pay:\nAccount number: 50100123456789\nIFSC: HDFC0001234")))
                .andExpect(jsonPath("$.note").doesNotExist());

        email(invoice);
        String url = link(lastTo(invoice).text());
        assertThat(lastTo(invoice).text())
                .contains("View and pay it online: " + url)
                .contains("Account number: 50100123456789")
                .doesNotContain("{link}");
        mockMvc.perform(get("/api/v1/public/share/{token}", token(url))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/document/{id}/activity", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[?(@.type == 'SHARED')].detail", contains("for an email")));

        draft(invoice).andExpect(jsonPath("$.message", containsString("View and pay it online: " + url)));
        email(invoice);
        assertThat(link(lastTo(invoice).text())).isEqualTo(url);
        assertThat(links(invoice)).isEqualTo(1);
    }

    @Test
    @DisplayName("a link that can't be shown again is replaced only by an email someone sends, after a warning")
    void legacyLink() throws Exception {
        Invoice invoice = invoice(LocalDate.now(ZoneOffset.UTC).plusDays(30));
        String old = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", invoice.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        forgetSealed(invoice);

        draft(invoice)
                .andExpect(jsonPath("$.note", containsString("Sending replaces this document's share link made on")))
                .andExpect(jsonPath("$.message", containsString("{link}")));
        email(invoice);
        String url = link(lastTo(invoice).text());
        assertThat(url).isNotEqualTo(old);
        mockMvc.perform(get("/api/v1/public/share/{token}", token(old))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/share/{token}", token(url))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an automatic reminder leaves the link out rather than replace one the client already has")
    void automaticReminder() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 14, "defaultTaxRate": 0,
                                 "taxLabel": "Tax", "timeZone": "UTC", "remindersEnabled": true,
                                 "reminderDaysBefore": 3, "reminderOnDue": true, "reminderEveryDays": 7,
                                 "reminderMax": 3}
                                """))
                .andExpect(status().isOk());
        Invoice legacy = invoice(LocalDate.now(ZoneOffset.UTC));
        String old = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", legacy.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        forgetSealed(legacy);
        Invoice fresh = invoice(LocalDate.now(ZoneOffset.UTC));

        reminders.send(Instant.now());

        assertThat(lastTo(legacy).text())
                .doesNotContain("/p/", "View and pay it online", "{link}")
                .contains("A friendly reminder that", "Account number: 50100123456789");
        mockMvc.perform(get("/api/v1/public/share/{token}", token(old))).andExpect(status().isOk());
        assertThat(lastTo(fresh).text()).contains("View and pay it online: " + link(lastTo(fresh).text()));
    }

    @Test
    @DisplayName("an email without {link} makes no link, and a paid invoice's email leaves out how to pay")
    void noLink() throws Exception {
        mockMvc.perform(put("/api/v1/settings/email-templates/INVOICE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject": "{document}", "body": "Hello {client},\\n\\n{payment}\\n\\n{business}"}
                                """))
                .andExpect(status().isOk());
        Invoice invoice = invoice(LocalDate.now(ZoneOffset.UTC).plusDays(30));
        mockMvc.perform(post("/api/v1/document/{id}/payment", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 200}"))
                .andExpect(status().isCreated());

        draft(invoice)
                .andExpect(jsonPath("$.message").value("Hello Beta LLC,\n\nTest User"))
                .andExpect(jsonPath("$.message", not(containsString("How to pay"))));
        email(invoice);
        assertThat(lastTo(invoice).text()).isEqualTo("Hello Beta LLC,\n\nTest User");
        assertThat(links(invoice)).isZero();
    }
}
