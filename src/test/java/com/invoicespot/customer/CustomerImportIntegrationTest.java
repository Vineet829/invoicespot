package com.invoicespot.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class CustomerImportIntegrationTest extends IntegrationTest {

    private User owner;
    private String tag;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        tag = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/customer/create")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Existing Co\", \"email\": \"existing-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                        .formatted(tag)));
    }

    private ResultActions send(String path, User user, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/customer/" + path)
                .file(new MockMultipartFile("file", "clients.csv", "text/csv", content))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private byte[] file() {
        return ("﻿EMAIL,Name,Phone,City,Notes\r\n"
                        + "harbor-%1$s@clients.test,Harbor & Pine,+1 555 0100,Portland,ignored\r\n"
                        + "not-an-email,Bad Email,+1,,\r\n"
                        + ",No Email,+1,,\r\n"
                        + "EXISTING-%1$s@clients.test,Dup Existing,+1,,\r\n"
                        + "harbor-%1$s@clients.test,Harbor Again,+1,,\r\n"
                        + "\r\n"
                        + "oak-%1$s@clients.test,\"Oak Street, Ltd\",'+44 20 7946 0958,London,\r\n"
                        + "nophone-%1$s@clients.test,No Phone,,,\r\n")
                .formatted(tag)
                .getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions clients(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/customer/all").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    @Test
    @DisplayName("the preview marks what stops each row, by its row in the sheet, and saves nothing")
    void preview() throws Exception {
        send("import/preview", owner, file())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.ready").value(2))
                .andExpect(jsonPath("$.rows[0].row").value(2))
                .andExpect(jsonPath("$.rows[0].name").value("Harbor & Pine"))
                .andExpect(jsonPath("$.rows[0].phoneNumber").value("+1 555 0100"))
                .andExpect(jsonPath("$.rows[0].city").value("Portland"))
                .andExpect(jsonPath("$.rows[0].problems").isEmpty())
                .andExpect(jsonPath("$.rows[1].problems[0]").value("Enter a valid email address"))
                .andExpect(jsonPath("$.rows[2].problems[0]").value("The email is missing"))
                .andExpect(jsonPath("$.rows[3].problems[0]").value("You already have a client with this email"))
                .andExpect(jsonPath("$.rows[4].problems[0]").value("This email is already on row 2"))
                .andExpect(jsonPath("$.rows[5].row").value(8))
                .andExpect(jsonPath("$.rows[5].name").value("Oak Street, Ltd"))
                .andExpect(jsonPath("$.rows[5].phoneNumber").value("+44 20 7946 0958"))
                .andExpect(jsonPath("$.rows[5].problems").isEmpty())
                .andExpect(jsonPath("$.rows[6].problems[0]").value("The phone number is missing"));
        clients(owner).andExpect(jsonPath("$.totalCustomers").value(1));
    }

    @Test
    @DisplayName("the import adds the ready rows and skips the rest, and never adds a client twice")
    void importsReadyRows() throws Exception {
        send("import", owner, file())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("2 clients added, 5 skipped"))
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.skipped").value(5));
        clients(owner).andExpect(jsonPath("$.totalCustomers").value(3));

        send("import", owner, file())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No row in the file can be added"));
        clients(owner).andExpect(jsonPath("$.totalCustomers").value(3));
    }

    @Test
    @DisplayName("a clients file exported from the app imports as it is")
    void roundTrip() throws Exception {
        send("import", owner, file()).andExpect(status().isOk());
        byte[] exported = mockMvc.perform(get("/api/v1/customer/csv").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        User other = createUser();
        send("import", other, exported)
                .andExpect(jsonPath("$.message").value("3 clients added"));
        clients(other)
                .andExpect(jsonPath("$.totalCustomers").value(3))
                .andExpect(jsonPath("$.myCustomers[?(@.name == 'Oak Street, Ltd')].phoneNumber")
                        .value("+44 20 7946 0958"));
    }

    @Test
    @DisplayName("semicolon files are read; unreadable, empty, headless and oversized files are refused")
    void files() throws Exception {
        send("import/preview", owner, "Name;Email;Phone\nAcme;acme-%s@x.test;+1\n".formatted(tag).getBytes())
                .andExpect(jsonPath("$.ready").value(1));
        send("import/preview", owner, "Company,Mail\nAcme,acme@x.test\n".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The first row must name the columns, with at least Name and Email"));
        send("import/preview", owner, "Name,Email,Phone\nCafé,cafe@x.test,+1\n".getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Save the file as CSV UTF-8, then try again"));
        send("import/preview", owner, new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a CSV file to import"));
        StringBuilder many = new StringBuilder("Name,Email,Phone\n");
        for (int row = 0; row <= CustomerImport.MOST_ROWS; row++) {
            many.append("Client ").append(row).append(",c").append(row).append("@x.test,+1\n");
        }
        send("import/preview", owner, many.toString().getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Import at most 1,000 clients at a time"));
    }
}
