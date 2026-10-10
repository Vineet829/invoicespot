package com.invoicespot.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

// A request the framework can't take, through the whole app, keeps the status
// that says why rather than becoming a 500.
class RequestErrorsIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("a method, a body type or a part a route doesn't take is a 4xx that says why")
    void requestErrors() throws Exception {
        String authorization = bearer(createUser());

        mockMvc.perform(post("/api/v1/report/overview").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Method 'POST' is not supported."));
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("defaultCurrency=USD"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/upload").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required part 'logo' is not present."));
    }
}
