package com.invoicespot.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

// A password is kept as a bcrypt hash, which can hold at most 72 bytes of it:
// a longer one is refused with a message that says so, wherever it is set.
class LongPasswordIntegrationTest extends IntegrationTest {

    private static final String TOO_LONG =
            "Password can be at most 72 characters long, or fewer with letters outside the English alphabet";
    // 29 characters, but 79 bytes: each Devanagari letter takes three.
    private static final String HINDI = Character.toString(0x0915).repeat(25) + "Aa1!";
    private static final String ENGLISH = "Aa1!" + "a".repeat(69);
    private static final String CURRENT = "Str0ng!pass";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService verificationTokenService;

    private ResultActions send(String path, String authorization, String json) throws Exception {
        return mockMvc.perform(post(path)
                .with(request -> {
                    request.setRemoteAddr("10.72.0." + (1 + (int) (Math.random() * 250)));
                    return request;
                })
                .headers(authorization == null ? new HttpHeaders() : authorizationHeader(authorization))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static HttpHeaders authorizationHeader(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        return headers;
    }

    @Test
    @DisplayName("signing up with a password over 72 bytes is refused, saying how long one can be")
    void signUp() throws Exception {
        for (String password : new String[] {HINDI, ENGLISH}) {
            String handle = "long" + UUID.randomUUID().toString().substring(0, 8);
            send("/api/v1/auth/register", null, """
                    {"email": "%s@example.test", "username": "%s", "firstName": "Long", "lastName": "Password",
                     "password": "%s", "passwordConfirm": "%s"}
                    """.formatted(handle, handle, password, password))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(TOO_LONG));
        }
    }

    @Test
    @DisplayName("resetting or changing to a password over 72 bytes is refused the same way")
    void resetAndChange() throws Exception {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(CURRENT));
        userRepository.save(user);

        send("/api/v1/auth/reset_password", null, """
                {"emailToken": "%s", "userId": "%s", "password": "%s", "passwordConfirm": "%s"}
                """.formatted(verificationTokenService.issue(user), user.getId(), HINDI, HINDI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TOO_LONG));
        send("/api/v1/account/password", bearer(user), """
                {"currentPassword": "%s", "newPassword": "%s", "confirmPassword": "%s"}
                """.formatted(CURRENT, HINDI, HINDI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TOO_LONG));
    }
}
