package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class WhatsAppShareIntegrationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("https?://\\S+/p/([A-Za-z0-9_-]{43})");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private String invoice(String phone, boolean draft) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"%s\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test", phone)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": %s,
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"), draft)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private String whatsApp(String id) throws Exception {
        return mockMvc.perform(post("/api/v1/document/{id}/share/whatsapp", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // The share link's token in a wa.me link's message.
    private static String token(String whatsAppUrl) {
        Matcher link = LINK.matcher(URLDecoder.decode(whatsAppUrl, StandardCharsets.UTF_8));
        assertThat(link.find()).isTrue();
        return link.group(1);
    }

    @Test
    @DisplayName("a WhatsApp message goes to the client's number with the document's link, the same link each time")
    void message() throws Exception {
        String id = invoice("+1 415 555 0100", false);

        String first = whatsApp(id);
        String url = JsonPath.read(first, "$.url");
        assertThat(url).startsWith("https://wa.me/14155550100?text=");
        assertThat(URLDecoder.decode(url, StandardCharsets.UTF_8))
                .contains("Hello Beta LLC, here is Invoice INV-0001 from Test User: http");
        assertThat((Boolean) JsonPath.read(first, "$.linkReplaced")).isFalse();
        mockMvc.perform(get("/api/v1/public/share/{token}", token(url))).andExpect(status().isOk());
        assertThat(token(JsonPath.read(whatsApp(id), "$.url"))).isEqualTo(token(url));
        mockMvc.perform(get("/api/v1/document/{id}/activity", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[?(@.type == 'SHARED')].detail", contains("for WhatsApp")));
    }

    @Test
    @DisplayName("without a usable number WhatsApp asks whom to send it to, and a link that can't be shown is replaced")
    void otherwise() throws Exception {
        String id = invoice("555 0100", false);
        String old = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", id)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        jdbcTemplate.update(
                "update share_links set token_sealed = null"
                        + " where document_pkid = (select pkid from documents where id = ?)",
                UUID.fromString(id));

        String reply = whatsApp(id);
        assertThat((String) JsonPath.read(reply, "$.url")).startsWith("https://wa.me/?text=");
        assertThat((Boolean) JsonPath.read(reply, "$.linkReplaced")).isTrue();
        mockMvc.perform(get("/api/v1/public/share/{token}", old.substring(old.lastIndexOf('/') + 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/document/{id}/share/whatsapp", invoice("+1 415 555 0100", true))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Issue this draft before sharing it"));
    }
}
