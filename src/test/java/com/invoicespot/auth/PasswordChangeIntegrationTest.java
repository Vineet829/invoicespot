package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class PasswordChangeIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";
    private static final String NEW_PASSWORD = "N3w!password";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties properties;

    private User withPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    // An access token a minute old, as the browser holds before the change.
    private String earlierToken(User user, SessionService.Continued session) {
        Clock minuteAgo = Clock.fixed(Instant.now().minusSeconds(60), ZoneOffset.UTC);
        return "Bearer " + new JwtService(properties, minuteAgo).issueAccessToken(user, session.session().getId());
    }

    private ResultActions change(String authorization, String current, String next, String confirm) throws Exception {
        return mockMvc.perform(post("/api/v1/account/password")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\", \"confirmPassword\": \"%s\"}"
                        .formatted(current, next, confirm)));
    }

    private ResultActions profile(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", token)));
    }

    @Test
    @DisplayName("a new password signs out the other devices, keeps this one signed in and emails the owner")
    void changes() throws Exception {
        User user = withPassword();
        SessionService.Continued mine = sessionService.start(user);
        SessionService.Continued other = sessionService.start(user);
        String old = earlierToken(user, mine);

        String body = change(old, PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Your password was changed, and your other devices were signed out"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        profile("Bearer " + JsonPath.read(body, "$.accessToken")).andExpect(status().isOk());
        profile(old).andExpect(status().isForbidden());
        refresh(mine.refreshToken()).andExpect(status().isOk());
        refresh(other.refreshToken()).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), NEW_PASSWORD)))
                .andExpect(status().isOk());
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly(user.getEmail());
        assertThat(mail.getValue().getText()).contains("was just changed").contains("/reset_password_request");
    }

    @Test
    @DisplayName("a wrong current password is refused and counted; after five, the account waits")
    void wrongPassword() throws Exception {
        User user = withPassword();
        String authorization = bearer(user);
        for (int attempt = 1; attempt <= 5; attempt++) {
            change(authorization, "Wrong!pass" + attempt, NEW_PASSWORD, NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Your current password is wrong"));
        }
        change(authorization, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("the new password must match its confirmation, be strong and differ from the current one")
    void rules() throws Exception {
        String authorization = bearer(withPassword());
        change(authorization, PASSWORD, NEW_PASSWORD, "N3w!passwurd")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The new passwords don't match"));
        change(authorization, PASSWORD, "weakpass", "weakpass")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordPolicy.REQUIREMENTS));
        change(authorization, PASSWORD, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a password different from your current one"));
    }

    @Test
    @DisplayName("the shared demo account's password can't be changed")
    void demo() throws Exception {
        User demo = demoUser();
        demo.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(demo);

        change(bearer(demo), PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so changing the password is turned off"));
    }
}
