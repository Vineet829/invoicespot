package com.invoicespot.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.config.AppProperties;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class UploadCleanupIntegrationTest extends IntegrationTest {

    @Autowired
    private AppProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private Path uploads;

    private String session;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Path logo(String name, Duration age) throws Exception {
        Path file = Files.write(uploads.resolve(name), TestImages.image("png", 1, 1));
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
        return file;
    }

    private void showLogo(Path file) throws Exception {
        send(patch("/api/v1/business"), "{\"logo\": \"/uploads/%s\"}".formatted(file.getFileName()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a logo a business shows, one a document was made with, and one uploaded today stay; others go")
    void removesUnused() throws Exception {
        session = bearer(createUser());
        Path onDocument = logo(UUID.randomUUID() + ".png", Duration.ofDays(30));
        Path shown = logo(UUID.randomUUID() + ".png", Duration.ofDays(2));
        Path replaced = logo(UUID.randomUUID() + ".png", Duration.ofDays(2));
        Path neverSaved = logo(UUID.randomUUID() + ".png", Duration.ofDays(2));
        Path justUploaded = logo(UUID.randomUUID() + ".png", Duration.ofMinutes(5));

        showLogo(onDocument);
        String client = JsonPath.read(
                send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.createdCustomer._id");
        send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "rates": "0",
                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                """.formatted(client)).andExpect(status().isOk());
        showLogo(replaced);
        showLogo(shown);

        AppProperties here = new AppProperties(
                properties.jwt(),
                properties.cookie(),
                properties.site(),
                properties.verification(),
                new AppProperties.Upload(uploads.toString()));
        int removed = new UploadCleanup(here, jdbcTemplate, Clock.systemUTC()).removeUnused();

        assertThat(removed).isEqualTo(2);
        assertThat(onDocument).exists();
        assertThat(shown).exists();
        assertThat(justUploaded).exists();
        assertThat(replaced).doesNotExist();
        assertThat(neverSaved).doesNotExist();
    }
}
