package com.invoicespot.document;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class DocumentExportIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private User owner;
    private String client;
    private String email;

    @BeforeEach
    void documents() throws Exception {
        owner = createUser();
        email = "acme-" + UUID.randomUUID() + "@clients.test";
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}".formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        client = JsonPath.read(body, "$.createdCustomer._id");
        pay(document("Invoice", "100", today.minusDays(1)), "40", "Bank Transfer", "First part");
        pay(document("Invoice", "50", today.plusDays(7)), "50", "Cash", null);
        document("Quotation", "70", today.plusDays(7));
        document("Receipt", "20", today);
    }

    private String document(String type, String amount, LocalDate due) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "USD",
                                 "issueDate": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        due.isBefore(today) ? due : today,
                                        due.atTime(12, 0).toInstant(ZoneOffset.UTC),
                                        amount)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private void pay(String document, String amount, String method, String note) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", document)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": %s, \"paymentMethod\": \"%s\", \"additionalInfo\": %s}"
                                .formatted(amount, method, note == null ? "null" : "\"" + note + "\"")))
                .andExpect(status().isCreated());
    }

    private List<String> csv(User user, String path, String... params) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, bearer(user));
        for (int index = 0; index < params.length; index += 2) {
            request = request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .lines()
                .toList();
    }

    @Test
    @DisplayName("documents export in the list's order, with nothing paid or owed on a quotation")
    void documentsCsv() throws Exception {
        String dates = today + "," + today.plusDays(7);
        assertThat(csv(owner, "/api/v1/document/csv")).containsExactly(
                "﻿Number,Type,Status,Client,Client email,Currency,Subtotal,Tax,Total,Paid,Owed,Issued,Due,Overdue,"
                        + "Tax withheld,Exchange rate,Exchange rate to,Credits,Credited,Refunded",
                // A receipt has no due date.
                "REC-0001,Receipt,Paid,Acme,%s,USD,20.00,0.00,20.00,20.00,0.00,%s,,No,0.00,,,,0.00,0.00"
                        .formatted(email, today),
                "QUO-0001,Quotation,Unpaid,Acme,%s,USD,70.00,0.00,70.00,,,%s,No,,,,,,".formatted(email, dates),
                "INV-0002,Invoice,Paid,Acme,%s,USD,50.00,0.00,50.00,50.00,0.00,%s,No,0.00,,,,0.00,0.00"
                        .formatted(email, dates),
                "INV-0001,Invoice,Partially paid,Acme,%s,USD,100.00,0.00,100.00,40.00,60.00,%s,%s,Yes,0.00,,,,0.00,0.00"
                        .formatted(email, today.minusDays(1), today.minusDays(1)));
    }

    @Test
    @DisplayName("the list's filters and sort apply to the export")
    void filtered() throws Exception {
        assertThat(csv(owner, "/api/v1/document/csv", "overdue", "true"))
                .hasSize(2)
                .last()
                .asString()
                .startsWith("INV-0001,");
        assertThat(csv(owner, "/api/v1/document/csv", "type", "Invoice", "sort", "total", "dir", "asc"))
                .extracting(line -> line.substring(0, line.indexOf(',')))
                .containsExactly("﻿Number", "INV-0002", "INV-0001");
    }

    @Test
    @DisplayName("a list longer than the export reads at once comes out whole, in its order")
    void longList() throws Exception {
        // 1,200 copies of the receipt, each with a number of its own.
        String columns = String.join(", ", jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = current_schema() and table_name = 'documents'
                  and column_name not in ('pkid', 'id', 'document_number')
                order by ordinal_position
                """, String.class));
        jdbcTemplate.update("""
                insert into documents (id, document_number, %1$s)
                select gen_random_uuid(), 'COPY-' || lpad(cast(n as text), 4, '0'), %1$s
                from documents cross join generate_series(1, 1200) n
                where business_pkid = ? and document_number = 'REC-0001'
                """.formatted(columns), businessOf(owner).getPkid());

        List<String> lines = csv(owner, "/api/v1/document/csv", "type", "Receipt", "sort", "number", "dir", "asc");
        assertThat(lines).hasSize(1202);
        assertThat(lines.subList(1, 1202))
                .extracting(line -> line.substring(0, line.indexOf(',')))
                .startsWith("COPY-0001", "COPY-0002")
                .contains("COPY-0500", "COPY-0501", "COPY-1000", "COPY-1001")
                .endsWith("COPY-1200", "REC-0001")
                .doesNotHaveDuplicates()
                .isSorted();
    }

    @Test
    @DisplayName("payments export newest first, and each owner gets only their own")
    void paymentsCsv() throws Exception {
        assertThat(csv(owner, "/api/v1/document/payments/csv")).containsExactly(
                "﻿Date,Document,Type,Client,Currency,Amount,Method,Paid by,Notes,Tax withheld,Kind",
                today + ",INV-0002,Invoice,Acme,USD,50.00,Cash,Acme,,0.00,Payment",
                today + ",INV-0001,Invoice,Acme,USD,40.00,Bank Transfer,Acme,First part,0.00,Payment");
        assertThat(csv(createUser(), "/api/v1/document/payments/csv")).hasSize(1);
        assertThat(csv(createUser(), "/api/v1/document/csv")).hasSize(1);
    }
}
