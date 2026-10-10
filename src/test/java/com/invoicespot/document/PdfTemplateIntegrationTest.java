package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The PDF template a business picks reaches every PDF it makes: Compact sets
// body text, such as the client's email, smaller than Classic does.
class PdfTemplateIntegrationTest extends IntegrationTest {

    private String session;
    private String email;
    private String client;
    private String invoice;
    private String payment;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions settings(String template) throws Exception {
        return send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"%s}
                """.formatted(template == null ? "" : ", \"pdfTemplate\": \"%s\"".formatted(template)));
    }

    private float bodySize(String path, Object... ids) throws Exception {
        byte[] pdf = mockMvc.perform(get(path, ids).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return PdfText.fontSizeOf(pdf, email);
    }

    // The size of the client's email on the invoice, a payment receipt and
    // the statement.
    private List<Float> bodySizes() throws Exception {
        List<Float> sizes = new ArrayList<>();
        sizes.add(bodySize("/api/v1/document/{id}/pdf", invoice));
        sizes.add(bodySize("/api/v1/document/{id}/payment/{payment}/receipt", invoice, payment));
        sizes.add(bodySize("/api/v1/customer/{id}/statement", client));
        return sizes;
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        email = "acme-" + UUID.randomUUID() + "@clients.test";
        client = JsonPath.read(
                send(post("/api/v1/customer/create"), """
                                {"name": "Acme", "email": "%s", "phoneNumber": "+1"}
                                """.formatted(email))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.createdCustomer._id");
        invoice = JsonPath.read(
                send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(client))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
        send(post("/api/v1/document/{id}/payment", invoice), "{\"amountPaid\": 40}").andExpect(status().isCreated());
        List<String> payments = JsonPath.read(
                send(get("/api/v1/document/{id}", invoice), "").andReturn().getResponse().getContentAsString(),
                "$.document.paymentRecords[*]._id");
        payment = payments.get(0);
    }

    @Test
    @DisplayName("the template chosen reaches invoices, payment receipts and statements")
    void everyPdf() throws Exception {
        List<Float> classic = bodySizes();
        settings("COMPACT").andExpect(status().isOk()).andExpect(jsonPath("$.settings.pdfTemplate").value("COMPACT"));
        List<Float> compact = bodySizes();

        assertThat(classic).allMatch(size -> size > 0);
        for (int pdf = 0; pdf < classic.size(); pdf++) {
            assertThat(compact.get(pdf)).as("PDF %d", pdf).isLessThan(classic.get(pdf));
        }
    }

    @Test
    @DisplayName("PDFs start Classic, a template stays chosen when settings leave it out, and others are refused")
    void choices() throws Exception {
        send(get("/api/v1/settings"), "").andExpect(jsonPath("$.settings.pdfTemplate").value("CLASSIC"));
        settings("MODERN").andExpect(jsonPath("$.settings.pdfTemplate").value("MODERN"));
        settings(null).andExpect(jsonPath("$.settings.pdfTemplate").value("MODERN"));
        settings("FANCY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose the Classic, Modern or Compact template"));
    }
}
