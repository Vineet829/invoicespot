package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A document keeps the business's details and the name of its tax as they
// were when it was issued. Changing either later leaves it as it went out; a
// document issued afterwards is made out by the business as it is, and a
// draft shows the business as it is until it is issued.
class SellerSnapshotIntegrationTest extends IntegrationTest {

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

    private void taxCalled(String label) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 18,
                 "taxLabel": "%s", "timeZone": "UTC"}
                """.formatted(label)).andExpect(status().isOk());
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        send(patch("/api/v1/business"), """
                {"name": "Ada Analytics", "legalName": "Ada Analytics Private Limited",
                 "address": "12 MG Road", "city": "Pune", "taxId": "27AAPFU0939F1ZV"}
                """).andExpect(status().isOk());
        taxCalled("GST");
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private String created() throws Exception {
        return created(false);
    }

    private String created(boolean draft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "rates": "18",
                         "draft": %s, "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client, draft)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // The business renames itself, moves, drops its legal name and tax ID,
    // and calls its tax something else.
    private void businessChanges() throws Exception {
        send(patch("/api/v1/business"), """
                {"name": "Borealis Studio", "legalName": "", "address": "1 Hill Road", "city": "Mumbai",
                 "taxId": ""}
                """).andExpect(status().isOk());
        taxCalled("VAT");
    }

    private String pdf(String url, Object... variables) throws Exception {
        return PdfText.of(mockMvc.perform(get(url, variables).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    @Test
    @DisplayName("changing the business or the name of its tax later leaves a document as it went out")
    void kept() throws Exception {
        String id = created();
        send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": 50}").andExpect(status().isCreated());
        businessChanges();

        send(get("/api/v1/document/{id}", id), "")
                .andExpect(jsonPath("$.document.seller.name").value("Ada Analytics"))
                .andExpect(jsonPath("$.document.seller.legalName").value("Ada Analytics Private Limited"))
                .andExpect(jsonPath("$.document.seller.city").value("Pune"))
                .andExpect(jsonPath("$.document.seller.taxId").value("27AAPFU0939F1ZV"))
                .andExpect(jsonPath("$.document.taxLabel").value("GST"));
        assertThat(pdf("/api/v1/document/{id}/pdf", id))
                .contains("Ada Analytics Private Limited", "12 MG Road, Pune", "Tax ID 27AAPFU0939F1ZV", "GST (18%)")
                .doesNotContain("Borealis", "Hill Road", "VAT");
        String payment = JsonPath.<List<String>>read(
                        body(send(get("/api/v1/document/{id}", id), "")), "$.document.paymentRecords[*]._id")
                .get(0);
        assertThat(pdf("/api/v1/document/{id}/payment/{payment}/receipt", id, payment))
                .contains("Ada Analytics", "12 MG Road, Pune")
                .doesNotContain("Borealis");
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.business.name").value("Ada Analytics"))
                .andExpect(jsonPath("$.business.lines", hasItem("Tax ID 27AAPFU0939F1ZV")))
                .andExpect(jsonPath("$.taxLabel").value("GST"));
    }

    @Test
    @DisplayName("a document issued afterwards and a statement show the business as it is")
    void current() throws Exception {
        created();
        businessChanges();

        String newer = created();
        send(get("/api/v1/document/{id}", newer), "")
                .andExpect(jsonPath("$.document.seller.name").value("Borealis Studio"))
                .andExpect(jsonPath("$.document.taxLabel").value("VAT"));
        assertThat(pdf("/api/v1/document/{id}/pdf", newer))
                .contains("Borealis Studio", "1 Hill Road, Mumbai", "VAT (18%)")
                .doesNotContain("Ada Analytics", "GST");
        assertThat(pdf("/api/v1/customer/{id}/statement", client))
                .contains("Borealis Studio")
                .doesNotContain("Ada Analytics");
    }

    @Test
    @DisplayName("a draft keeps nothing and shows the business as it is, until it is issued and keeps it")
    void drafts() throws Exception {
        String draft = created(true);
        send(get("/api/v1/document/{id}", draft), "")
                .andExpect(jsonPath("$.document.seller").doesNotExist())
                .andExpect(jsonPath("$.document.taxLabel").value(nullValue()));
        businessChanges();

        assertThat(pdf("/api/v1/document/{id}/pdf", draft))
                .contains("Borealis Studio", "VAT (18%)")
                .doesNotContain("Ada Analytics", "GST");
        send(post("/api/v1/document/{id}/issue", draft), "{}")
                .andExpect(jsonPath("$.updatedDocument.seller.name").value("Borealis Studio"))
                .andExpect(jsonPath("$.updatedDocument.taxLabel").value("VAT"));
        send(patch("/api/v1/business"), "{\"name\": \"Cygnus Labs\"}").andExpect(status().isOk());
        assertThat(pdf("/api/v1/document/{id}/pdf", draft)).contains("Borealis Studio").doesNotContain("Cygnus");
    }
}
