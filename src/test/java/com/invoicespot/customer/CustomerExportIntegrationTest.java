package com.invoicespot.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CustomerExportIntegrationTest extends IntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    private String[] client(User owner, String name, String city) throws Exception {
        String email = name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID() + "@clients.test";
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "phoneNumber": "+1 555 0100", "vatTinNo": "29AB",
                                 "address": "1 Main St", "city": "%s", "country": "Canada"}
                                """.formatted(name, email, city)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new String[] {
            JsonPath.read(body, "$.createdCustomer._id"), email, JsonPath.read(body, "$.createdCustomer.accountNo")
        };
    }

    private String invoice(User owner, String client, String currency, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, currency, amount)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private void pay(User owner, String document, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", document)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": %s}".formatted(amount)))
                .andExpect(status().isCreated());
    }

    private List<String> csv(User owner, String... params) throws Exception {
        var request = get("/api/v1/customer/csv").header(HttpHeaders.AUTHORIZATION, bearer(owner));
        for (int index = 0; index < params.length; index += 2) {
            request = request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition", "attachment; filename=\"invoice-spot-clients-" + today + ".csv\""))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .lines()
                .toList();
    }

    @Test
    @DisplayName("clients export in the list's order, with what each owes in every currency; a phone number that a"
            + " spreadsheet would read as a formula stays text")
    void clientsCsv() throws Exception {
        User owner = createUser();
        String[] alpha = client(owner, "Alpha Ltd", "Toronto");
        String[] beta = client(owner, "Beta LLC", "Ottawa, ON");
        String[] gamma = client(owner, "Gamma", "Halifax");
        pay(owner, invoice(owner, beta[0], "USD", "100"), "40");
        invoice(owner, beta[0], "EUR", "10");
        pay(owner, invoice(owner, gamma[0], "USD", "25"), "25");

        assertThat(csv(owner, "sort", "name", "dir", "asc")).containsExactly(
                "﻿Name,Email,Phone,Account number,VAT/TIN,GSTIN,Address,City,Country,Documents,Owed EUR,Owed USD,"
                        + "Added",
                "Alpha Ltd,%s,'+1 555 0100,%s,29AB,,1 Main St,Toronto,Canada,0,0.00,0.00,%s"
                        .formatted(alpha[1], alpha[2], today),
                "Beta LLC,%s,'+1 555 0100,%s,29AB,,1 Main St,\"Ottawa, ON\",Canada,2,10.00,60.00,%s"
                        .formatted(beta[1], beta[2], today),
                "Gamma,%s,'+1 555 0100,%s,29AB,,1 Main St,Halifax,Canada,1,0.00,0.00,%s"
                        .formatted(gamma[1], gamma[2], today));
        assertThat(csv(owner, "q", "beta")).hasSize(2).last().asString().startsWith("Beta LLC,");
        assertThat(csv(createUser())).containsExactly(
                "﻿Name,Email,Phone,Account number,VAT/TIN,GSTIN,Address,City,Country,Documents,Added");
    }
}
