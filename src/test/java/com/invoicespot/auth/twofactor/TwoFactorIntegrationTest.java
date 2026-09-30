package com.invoicespot.auth.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class TwoFactorIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    private record Enabled(User user, byte[] secret, List<String> recoveryCodes, long step) {}

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User withPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private ResultActions account(String path, String authorization, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/account/two-factor" + path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions login(User user) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)));
    }

    private ResultActions secondStep(String challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/two-factor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challenge\": \"%s\", \"code\": \"%s\"}".formatted(challenge, code)));
    }

    private String challenge(User user) throws Exception {
        return JsonPath.read(
                login(user).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.challenge");
    }

    // Sets it up and turns it on with the code for this step, so a sign-in
    // right after needs the next step's code.
    private Enabled enabled() throws Exception {
        User user = withPassword();
        String authorization = bearer(user);
        String setup = account("/setup", authorization, "{\"password\": \"%s\"}".formatted(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        byte[] secret = new Base32().decode((String) JsonPath.read(setup, "$.secret"));
        long step = Totp.step(Instant.now());
        String enable = account("/enable", authorization, "{\"code\": \"%s\"}".formatted(Totp.code(secret, step)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Enabled(user, secret, JsonPath.read(enable, "$.recoveryCodes"), step);
    }

    @Test
    @DisplayName("turning it on takes the password and a code from the app, and gives ten recovery codes")
    void turnOn() throws Exception {
        Enabled enabled = enabled();

        assertThat(enabled.recoveryCodes()).hasSize(10).allMatch(code -> code.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        mockMvc.perform(get("/api/v1/account/two-factor").header(HttpHeaders.AUTHORIZATION, bearer(enabled.user())))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10));
        account("/setup", bearer(withPassword()), "{\"password\": \"Wrong!pass1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your password is wrong"));
    }

    @Test
    @DisplayName("a password then earns only a challenge; a code completes it, and never twice")
    void signIn() throws Exception {
        Enabled enabled = enabled();
        String challenge = challenge(enabled.user());
        login(enabled.user())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("jwt"));
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + challenge))
                .andExpect(status().isForbidden());

        secondStep(challenge, Totp.code(enabled.secret(), enabled.step()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That code didn't work"));
        String token = JsonPath.read(
                secondStep(challenge, Totp.code(enabled.secret(), enabled.step() + 1))
                        .andExpect(status().isOk())
                        .andExpect(cookie().exists("jwt"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.accessToken");
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        secondStep("not-a-challenge", "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your sign-in timed out. Log in again"));
    }

    @Test
    @DisplayName("a recovery code works once, and wrong codes are limited")
    void recoveryCodes() throws Exception {
        Enabled enabled = enabled();
        String code = enabled.recoveryCodes().get(0);

        secondStep(challenge(enabled.user()), code.toUpperCase()).andExpect(status().isOk());
        secondStep(challenge(enabled.user()), code).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/account/two-factor").header(HttpHeaders.AUTHORIZATION, bearer(enabled.user())))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(9));
        String challenge = challenge(enabled.user());
        for (int attempt = 1; attempt <= 4; attempt++) {
            secondStep(challenge, "00000" + attempt).andExpect(status().isBadRequest());
        }
        secondStep(challenge, enabled.recoveryCodes().get(1)).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("turning it off takes the password and a code; then a password signs in again")
    void turnOff() throws Exception {
        Enabled enabled = enabled();
        String authorization = bearer(enabled.user());
        String recovery = enabled.recoveryCodes().get(0);
        account("/disable", authorization, "{\"password\": \"Wrong!pass1\", \"code\": \"%s\"}".formatted(recovery))
                .andExpect(status().isBadRequest());
        account("/disable", authorization, "{\"password\": \"%s\", \"code\": \"%s\"}".formatted(PASSWORD, recovery))
                .andExpect(status().isOk());

        login(enabled.user()).andExpect(jsonPath("$.accessToken").exists()).andExpect(cookie().exists("jwt"));
    }

    @Test
    @DisplayName("the shared demo account can't turn it on")
    void demo() throws Exception {
        User demo = demoUser();
        demo.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(demo);

        account("/setup", bearer(demo), "{\"password\": \"%s\"}".formatted(PASSWORD))
                .andExpect(status().isForbidden());
    }
}
