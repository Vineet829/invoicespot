package com.invoicespot.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DataExportIntegrationTest extends IntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    private String send(User owner, String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Map<String, String> export(User owner) throws Exception {
        byte[] zip = mockMvc.perform(get("/api/v1/export/all").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string(
                        "Content-Disposition", "attachment; filename=\"invoice-spot-export-" + today + ".zip\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                files.put(entry.getName(), new String(entries.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static List<String> lines(String csv) {
        return csv.lines().toList();
    }

    @Test
    @DisplayName("the archive holds every kind of record, the owner's only, with a note on each file")
    void everything() throws Exception {
        User owner = createUser();
        String client = JsonPath.read(
                send(owner, "/api/v1/customer/create", """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "555 0100"}
                        """.formatted(UUID.randomUUID())),
                "$.createdCustomer._id");
        String invoice = JsonPath.read(
                send(owner, "/api/v1/document/create", """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                         "billingItems": [{"itemName": "Design", "unitPrice": 50, "quantity": 2, "discount": "10",
                                           "unit": "hours", "hsnSac": "9983 14"},
                                          {"itemName": "Hosting", "unitPrice": 0.1234, "quantity": 3.5, "taxRate": "18"}]}
                        """.formatted(client)),
                "$.newDocument._id");
        send(owner, "/api/v1/document/" + invoice + "/payment", "{\"amountPaid\": 40, \"paymentMethod\": \"Cash\"}");
        send(owner, "/api/v1/item", """
                {"name": "Logo design", "unitPrice": 12.5, "unit": "hours", "taxRate": "18", "hsnSac": "998391"}
                """);
        send(owner, "/api/v1/recurring", """
                {"documentId": "%s", "frequency": "monthly", "startDate": "%s", "maxRuns": 3}
                """.formatted(invoice, today.plusDays(1)));

        Map<String, String> files = export(owner);

        assertThat(files.keySet()).containsExactly(
                "README.txt", "documents.csv", "line-items.csv", "document-tax.csv", "payments.csv", "clients.csv",
                "items.csv", "recurring.csv", "settings.json", "business.json", "profile.json");
        assertThat(files.get("README.txt")).startsWith("Invoice Spot export, made on " + today + ".");
        assertThat(lines(files.get("documents.csv"))).hasSize(2).last().asString()
                .startsWith("INV-0001,Invoice,Partially paid,Acme,");
        assertThat(lines(files.get("line-items.csv"))).containsExactly(
                "﻿Document,Type,Line,Item,Quantity,Unit price,Discount %,Line total,Unit,Tax %,HSN/SAC",
                "INV-0001,Invoice,1,Design,2,50.00,10,90.00,hours,0,998314",
                "INV-0001,Invoice,2,Hosting,3.5,0.1234,0,0.43,,18,");
        assertThat(lines(files.get("document-tax.csv"))).containsExactly(
                "﻿Document,Type,Rate %,Taxable,Tax,CGST,SGST,IGST,Reverse charge",
                "INV-0001,Invoice,0,90.00,0.00,,,,",
                "INV-0001,Invoice,18,0.43,0.08,,,,");
        assertThat(lines(files.get("payments.csv"))).hasSize(2).last().asString()
                .isEqualTo(today + ",INV-0001,Invoice,Acme,USD,40.00,Cash,Acme,,0.00,Payment");
        assertThat(lines(files.get("clients.csv"))).hasSize(2).last().asString().startsWith("Acme,acme-");
        assertThat(lines(files.get("items.csv"))).containsExactly(
                "﻿Name,Description,Unit price,Unit,Tax %,HSN/SAC", "Logo design,,12.50,hours,18,998391");
        assertThat(lines(files.get("recurring.csv"))).containsExactly(
                "﻿Invoice,Client,Repeats,First invoice,Next invoice,Ends on,Most invoices,Made,"
                        + "Emailed automatically,Paused",
                "INV-0001,Acme,monthly,%s,%s,,3,0,No,No".formatted(today.plusDays(1), today.plusDays(1)));
        assertThat((String) JsonPath.read(files.get("settings.json"), "$.defaultCurrency")).isEqualTo("USD");
        assertThat((String) JsonPath.read(files.get("business.json"), "$.name")).isEqualTo("Test User");
        assertThat((String) JsonPath.read(files.get("profile.json"), "$.email")).isEqualTo(owner.getEmail());
        assertThat(files.get("profile.json")).doesNotContain("password");

        Map<String, String> other = export(createUser());
        assertThat(lines(other.get("documents.csv"))).hasSize(1);
        assertThat(lines(other.get("line-items.csv"))).hasSize(1);
        assertThat(lines(other.get("recurring.csv"))).hasSize(1);
    }
}
