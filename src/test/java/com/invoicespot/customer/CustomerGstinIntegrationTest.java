package com.invoicespot.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.document.PdfText;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A client's GSTIN is checked, sets their GST state, goes onto each document
// as it is issued and is printed wherever the client is named.
class CustomerGstinIntegrationTest extends IntegrationTest {

    private static final String GSTIN = "27AAPFU0939F1ZV";
    private String session;

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
        session = bearer(createUser());
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "INR", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "GST", "timeZone": "UTC"}
                """).andExpect(status().isOk());
    }

    private ResultActions create(String name, String extra) throws Exception {
        return send(post("/api/v1/customer/create"), """
                {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+91 1" %s}
                """.formatted(name, UUID.randomUUID(), extra));
    }

    @Test
    @DisplayName("a GSTIN is upper-cased and checked, and sets the client's state; a state alone is a GST code")
    void validation() throws Exception {
        String client = JsonPath.read(
                body(create("Acme", ", \"gstin\": \" 27aapfu0939f1zv \", \"stateCode\": \"29\"")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.createdCustomer.gstin").value(GSTIN))
                        .andExpect(jsonPath("$.createdCustomer.stateCode").value("27"))),
                "$.createdCustomer._id");
        create("Wrong", ", \"gstin\": \"27AAPFU0939F1ZW\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid GSTIN, such as 27AAPFU0939F1ZV"));
        create("Nowhere", ", \"stateCode\": \"99\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose the client's state from the list"));
        create("Abroad", ", \"stateCode\": \"96\"").andExpect(jsonPath("$.createdCustomer.stateCode").value("96"));

        send(patch("/api/v1/customer/{id}", client), "{\"gstin\": \"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCustomerInfo.gstin").doesNotExist())
                .andExpect(jsonPath("$.updatedCustomerInfo.stateCode").value("27"));
        send(patch("/api/v1/customer/{id}", client), "{\"stateCode\": \"33\"}")
                .andExpect(jsonPath("$.updatedCustomerInfo.stateCode").value("33"));
        send(patch("/api/v1/customer/{id}", client), "{\"gstin\": \"%s\"}".formatted(GSTIN))
                .andExpect(jsonPath("$.updatedCustomerInfo.gstin").value(GSTIN))
                .andExpect(jsonPath("$.updatedCustomerInfo.stateCode").value("27"));
        send(get("/api/v1/customer/all").param("q", "aapfu0939"), "")
                .andExpect(jsonPath("$.myCustomers.length()").value(1));

        send(get("/api/v1/gst/states"), "")
                .andExpect(jsonPath("$.states[26].code").value("27"))
                .andExpect(jsonPath("$.states[26].name").value("Maharashtra"))
                .andExpect(jsonPath("$.states[-1:].code").value("96"));
    }

    @Test
    @DisplayName("each document keeps the GSTIN as issued and prints it; the CSV, the share page and TDS PANs have it")
    void onDocuments() throws Exception {
        String client = JsonPath.read(
                body(create("Acme", ", \"gstin\": \"%s\"".formatted(GSTIN))), "$.createdCustomer._id");
        String invoice = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "INR",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 1000, "quantity": 1}]}
                                """.formatted(client))
                        .andExpect(jsonPath("$.newDocument.customer.gstin").value(GSTIN))
                        .andExpect(jsonPath("$.newDocument.customer.stateCode").value("27"))),
                "$.newDocument._id");
        send(post("/api/v1/document/{id}/payment", invoice), "{\"amountPaid\": 900, \"tdsAmount\": 100}")
                .andExpect(status().isCreated());
        send(patch("/api/v1/customer/{id}", client), "{\"gstin\": \"29AAGCB7383J1Z4\"}").andExpect(status().isOk());

        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(jsonPath("$.document.customer.gstin").value(GSTIN));
        String pdf = PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", invoice)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
        assertThat(pdf).contains("GSTIN " + GSTIN);
        String link = JsonPath.read(body(send(post("/api/v1/document/{id}/share", invoice), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", link.substring(link.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.client.lines[-1:]").value("GSTIN " + GSTIN));

        assertThat(body(send(get("/api/v1/customer/csv"), ""))).contains(",29AAGCB7383J1Z4,");
        send(get("/api/v1/report/tds").param("period", "12m"), "")
                .andExpect(jsonPath("$.currencies[0].clients[0].pan").value("AAGCB7383J"))
                .andExpect(jsonPath("$.currencies[0].payments[0].pan").value("AAGCB7383J"));
        assertThat(body(send(get("/api/v1/report/csv/tds").param("period", "12m"), "")))
                .contains("Client,Client PAN,Document")
                .contains(",Acme,AAGCB7383J,INV-0001,");
    }

    @Test
    @DisplayName("an import reads a GSTIN column and marks one that isn't valid")
    void importing() throws Exception {
        String tag = UUID.randomUUID().toString();
        byte[] file = ("Name,Email,Phone,GSTIN\r\n"
                        + "Good,good-%1$s@clients.test,+91 1,%2$s\r\n"
                        + "Bad,bad-%1$s@clients.test,+91 1,27AAPFU0939F1ZW\r\n")
                .formatted(tag, GSTIN)
                .getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/customer/import/preview")
                        .file(new MockMultipartFile("file", "clients.csv", "text/csv", file))
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.ready").value(1))
                .andExpect(jsonPath("$.rows[0].gstin").value(GSTIN))
                .andExpect(jsonPath("$.rows[1].problems[0]").value("The GSTIN isn't valid"));
        mockMvc.perform(multipart("/api/v1/customer/import")
                        .file(new MockMultipartFile("file", "clients.csv", "text/csv", file))
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.added").value(1));
        send(get("/api/v1/customer/all").param("q", "good-" + tag), "")
                .andExpect(jsonPath("$.myCustomers[0].gstin").value(GSTIN))
                .andExpect(jsonPath("$.myCustomers[0].stateCode").value("27"));
    }
}
