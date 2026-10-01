package com.invoicespot.auth.twofactor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

// A server started without its encryption key can't open the secrets codes
// from authenticator apps are checked against. Such a code is refused with a
// way in, rather than a server error, and a recovery code still signs in.
@TestPropertySource(properties = "app.two-factor.encryption-key=")
class TwoFactorWithoutKeyIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    private ResultActions secondStep(String challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/two-factor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challenge\": \"%s\", \"code\": \"%s\"}".formatted(challenge, code)));
    }

    private String challenge(User user) throws Exception {
        return JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\", \"password\": \"%s\"}"
                                        .formatted(user.getEmail(), PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.challenge");
    }

    @Test
    @DisplayName("an app code is refused with a way in, and a recovery code still signs in")
    void recoveryCodesStillWork() throws Exception {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setTwoFactorSecret("sealed-with-a-key-this-server-no-longer-has");
        user.setTwoFactorEnabledAt(Instant.now());
        userRepository.save(user);
        RecoveryCode recovery = new RecoveryCode();
        recovery.setUser(user);
        recovery.setCodeHash(SecretTokens.hash("abcde23456"));
        recoveryCodeRepository.save(recovery);

        secondStep(challenge(user), "123456")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Codes from your authenticator app can't be checked right now. Use a recovery code"));
        secondStep(challenge(user), "abcde-23456").andExpect(status().isOk());
    }
}
