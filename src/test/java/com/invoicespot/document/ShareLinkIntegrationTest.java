package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.common.SecretBox;
import com.invoicespot.config.EncryptionProperties;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class ShareLinkIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private String document(String type) throws Exception {
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
                                {"customerId": "%s", "documentType": "%s", "currency": "USD",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"), type)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private String share(String id) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/{id}/share", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", matchesPattern("^https?://.+/p/[A-Za-z0-9_-]{43}$")))
                .andExpect(jsonPath("$.kept").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ResultActions open(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/public/share/{token}", token));
    }

    private ResultActions linkStatus(String id) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private ResultActions activity(String id) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/activity", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    @Test
    @DisplayName("a share link shows the document to anyone holding it, and only a hash of it is stored")
    void view() throws Exception {
        String id = document("Invoice");
        mockMvc.perform(post("/api/v1/document/{id}/payment", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": 50}"));
        String token = share(id);

        String stored = jdbcTemplate.queryForObject(
                "select l.token_hash from share_links l join documents d on d.pkid = l.document_pkid where d.id = ?",
                String.class,
                UUID.fromString(id));
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(stored).isEqualTo(expected).isNotEqualTo(token);
        open(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.business.name").value("Test User"))
                .andExpect(jsonPath("$.client.name").value("Beta LLC"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(200.0))
                .andExpect(jsonPath("$.balance").value(150.0))
                .andExpect(jsonPath("$.canAccept").value(false));
        open(token).andExpect(status().isOk());
        byte[] pdf = mockMvc.perform(get("/api/v1/public/share/{token}/pdf", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        mockMvc.perform(get("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.viewCount").value(2))
                .andExpect(jsonPath("$.lastViewedAt", notNullValue()));
        activity(id).andExpect(
                jsonPath("$.events[*].type", contains("VIEWED", "SHARED", "PAYMENT_RECORDED", "ISSUED", "CREATED")));
    }

    @Test
    @DisplayName("a new link replaces the old one, and a revoked or unknown link is gone")
    void replaceAndRevoke() throws Exception {
        String id = document("Invoice");
        String first = share(id);
        String second = share(id);

        open(first)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This link doesn't work anymore"));
        open(second).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        open(second).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(delete("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        open("not-a-real-token").andExpect(status().isNotFound());
        activity(id).andExpect(jsonPath("$.events[0].type").value("SHARE_REVOKED"));
    }

    @Test
    @DisplayName("a link is kept sealed so it can be shown again, and one that can't be opened can only be replaced")
    void shownAgain() throws Exception {
        String id = document("Invoice");
        String token = share(id);
        String sealed = jdbcTemplate.queryForObject(
                "select l.token_sealed from share_links l join documents d on d.pkid = l.document_pkid"
                        + " where d.id = ? and l.revoked_at is null",
                String.class,
                UUID.fromString(id));

        assertThat(sealed).isNotBlank().doesNotContain(token);
        linkStatus(id).andExpect(jsonPath("$.url", endsWith("/p/" + token)));

        String otherKey = Base64.getEncoder()
                .encodeToString("another-key-of-thirty-two-bytes!".getBytes(StandardCharsets.UTF_8));
        String elsewhere = new SecretBox(new EncryptionProperties(otherKey))
                .seal(token.getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("update share_links set token_sealed = ? where token_sealed = ?", elsewhere, sealed);
        linkStatus(id).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.url").doesNotExist());

        jdbcTemplate.update("update share_links set token_sealed = null where token_sealed = ?", elsewhere);
        linkStatus(id).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.url").doesNotExist());
        open(token).andExpect(status().isOk());

        String replacement = share(id);
        linkStatus(id).andExpect(jsonPath("$.url", endsWith("/p/" + replacement)));
        open(token).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a client can accept a quotation through its link, once, and nothing else")
    void accept() throws Exception {
        String quotation = document("Quotation");
        String token = share(quotation);

        String accepted = mockMvc.perform(post("/api/v1/public/share/{token}/accept", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAt", notNullValue()))
                .andExpect(jsonPath("$.canAccept").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        mockMvc.perform(post("/api/v1/public/share/{token}/accept", token))
                .andExpect(jsonPath("$.acceptedAt").value((String) JsonPath.read(accepted, "$.acceptedAt")));
        mockMvc.perform(get("/api/v1/document/{id}", quotation).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.acceptedAt", notNullValue()));
        activity(quotation).andExpect(jsonPath("$.events[0].type").value("ACCEPTED"))
                .andExpect(jsonPath("$.events[1].type").value("SHARED"));

        mockMvc.perform(post("/api/v1/public/share/{token}/accept", share(document("Invoice"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a quotation can be accepted"));
    }

    @Test
    @DisplayName("only a document's owner can share it")
    void ownership() throws Exception {
        String id = document("Invoice");

        mockMvc.perform(post("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/document/{id}/share", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }
}
