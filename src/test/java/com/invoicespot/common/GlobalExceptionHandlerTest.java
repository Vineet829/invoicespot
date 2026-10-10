package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(OutputCaptureExtension.class)
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
    @DisplayName("an unexpected error is masked as a 500 without leaking internals, and logged with its request")
    void unexpectedError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again later."))
                .andExpect(jsonPath("$.statusCode").value(500));
        assertThat(output.getOut() + output.getErr())
                .contains("Unexpected error on GET /probe/boom")
                .contains("IllegalStateException: sensitive internal detail");
    }

    @Test
    @DisplayName("a method the route doesn't take is a 405 that says so, not a 500")
    void methodNotAllowed(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/probe/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Method 'POST' is not supported."));
        assertThat(output.getOut() + output.getErr()).doesNotContain("Unexpected error");
    }

    @Test
    @DisplayName("an upload over the size limit is a 413 with the limit in the message")
    void uploadTooLarge() throws Exception {
        mockMvc.perform(get("/probe/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Logos can be at most 2 MB"))
                .andExpect(jsonPath("$.statusCode").value(413));
    }

    @Test
    @DisplayName("a multipart body that can't be read is a 400, not a 500 logged as an error")
    void malformedMultipart(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/malformed-multipart"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.statusCode").value(400));
        assertThat(output.getOut() + output.getErr()).doesNotContain("Unexpected error");
    }

    // Tomcat failing on its own side lands in the same handler, such as when
    // it can't write a part to its temp folder, so each one is logged too.
    @Test
    @DisplayName("a part Tomcat can't write to disk is a 400 too, logged as one warning line without a stack trace")
    void partNotWritten(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/part-not-written"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.statusCode").value(400));
        String log = output.getOut() + output.getErr();
        assertThat(log.lines().filter(line -> line.contains("Unreadable multipart body")))
                .singleElement(STRING)
                .contains("WARN")
                .endsWith("Unreadable multipart body on GET /probe/part-not-written: "
                        + "java.io.IOException: The temporary upload location [/tmp/tomcat/work] is not valid");
        assertThat(log).doesNotContain("Unexpected error").doesNotContain("at " + ProbeController.class.getName());
    }

    @Test
    @DisplayName("a number too large for its column is a 400 that says so, not a clash with a saved record")
    void amountTooLarge() throws Exception {
        mockMvc.perform(get("/probe/out-of-range"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An amount is too large to save"))
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("text with a NUL character in it is a 400 that says so, not a clash with a saved record")
    void nulCharacter() throws Exception {
        mockMvc.perform(get("/probe/nul-character"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Text can't contain a NUL character"))
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("a clash with a record already saved is still a 409")
    void duplicateRecord() throws Exception {
        mockMvc.perform(get("/probe/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That conflicts with a record that already exists"))
                .andExpect(jsonPath("$.statusCode").value(409));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/out-of-range")
        String outOfRange() {
            throw new DataIntegrityViolationException(
                    "could not execute statement", new SQLException("ERROR: numeric field overflow", "22003"));
        }

        @GetMapping("/probe/nul-character")
        String nulCharacter() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLException("ERROR: invalid byte sequence for encoding \"UTF8\": 0x00", "22021"));
        }

        @GetMapping("/probe/duplicate")
        String duplicate() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLException("ERROR: duplicate key value violates unique constraint", "23505"));
        }

        @GetMapping("/probe/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(2L * 1024 * 1024);
        }

        @GetMapping("/probe/malformed-multipart")
        String malformedMultipart() {
            throw new MultipartException("Failed to parse multipart servlet request");
        }

        @GetMapping("/probe/part-not-written")
        String partNotWritten() {
            throw new MultipartException(
                    "Failed to parse multipart servlet request",
                    new IOException("The temporary upload location [/tmp/tomcat/work] is not valid"));
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
