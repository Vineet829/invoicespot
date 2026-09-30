package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DocumentNumberingIntegrationTest extends IntegrationTest {

    private String customerId(User owner) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Numbers\", \"email\": \"numbers-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    private String create(User owner, String customerId, String type) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 10, "quantity": 1}]}
                                """.formatted(customerId, type)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument.documentNumber");
    }

    private void numbering(User owner, String json) throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0, "taxLabel": "Tax",
                                 "timeZone": "UTC", %s}
                                """.formatted(json)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("each document type counts up on its own, separately for every user")
    void sequences() throws Exception {
        User owner = createUser();
        String customerId = customerId(owner);

        assertThat(create(owner, customerId, "Invoice")).isEqualTo("INV-0001");
        assertThat(create(owner, customerId, "Invoice")).isEqualTo("INV-0002");
        assertThat(create(owner, customerId, "Quotation")).isEqualTo("QUO-0001");
        assertThat(create(owner, customerId, "Receipt")).isEqualTo("REC-0001");
        User other = createUser();
        assertThat(create(other, customerId(other), "Invoice")).isEqualTo("INV-0001");
    }

    @Test
    @DisplayName("prefixes and next numbers follow the settings, and numbers already used are skipped")
    void customNumbering() throws Exception {
        User owner = createUser();
        String customerId = customerId(owner);
        numbering(owner, "\"invoicePrefix\": \"2026/INV-\", \"nextInvoiceNumber\": 100");

        assertThat(create(owner, customerId, "Invoice")).isEqualTo("2026/INV-0100");
        assertThat(create(owner, customerId, "Invoice")).isEqualTo("2026/INV-0101");

        numbering(owner, "\"nextInvoiceNumber\": 100");
        assertThat(create(owner, customerId, "Invoice")).isEqualTo("2026/INV-0102");
    }

    @Test
    @DisplayName("documents created at the same moment still get different numbers")
    void concurrentCreates() throws Exception {
        User owner = createUser();
        String customerId = customerId(owner);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> create(owner, customerId, "Invoice"));
        }
        List<String> numbers = new ArrayList<>();
        for (Future<String> future : pool.invokeAll(tasks)) {
            numbers.add(future.get());
        }
        pool.shutdown();

        assertThat(new HashSet<>(numbers)).hasSize(6);
        assertThat(numbers).allMatch(number -> number.matches("INV-000[1-6]"));
    }
}
