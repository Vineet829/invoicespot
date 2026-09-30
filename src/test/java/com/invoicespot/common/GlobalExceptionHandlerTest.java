package com.invoicespot.common;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("an ApiException is rendered as the standard error envelope")
    void apiException() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("That email is already taken"))
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.stack").value(nullValue()));
    }

    @Test
    @DisplayName("a bean validation failure becomes a 400 with the field message")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Name is required"))
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("a missing route returns the not-found error body")
    void missingRoute() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(startsWith("That route does not exist - ")))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    @DisplayName("an unexpected error is masked as a 500 without leaking internals")
    void unexpectedError() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again later."))
                .andExpect(jsonPath("$.statusCode").value(500));
    }

    @Test
    @DisplayName("an upload over the size limit is a 413 with the limit in the message")
    void uploadTooLarge() throws Exception {
        mockMvc.perform(get("/probe/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Logos can be at most 2 MB"))
                .andExpect(jsonPath("$.statusCode").value(413));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(2L * 1024 * 1024);
        }

        @GetMapping("/probe/conflict")
        String conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "That email is already taken");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("sensitive internal detail");
        }

        @GetMapping("/probe/missing")
        String missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope");
        }

        @PostMapping("/probe/validate")
        String validate(@Valid @RequestBody ProbeBody body) {
            return body.name();
        }

        record ProbeBody(@NotBlank(message = "Name is required") String name) {}
    }
}
