package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.auth.session.UserSessionRepository;
import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SessionEndingIntegrationTest extends IntegrationTest {

    @Autowired
    private AppProperties properties;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private UserSessionRepository sessionRepository;

    private JwtService minutesAgo(int minutes) {
        return new JwtService(properties, Clock.fixed(Instant.now().minusSeconds(60L * minutes), ZoneOffset.UTC));
    }

    private ResultActions profile(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", token)));
    }

    private boolean signedIn(User user) {
        return !sessionRepository.findActive(user.getPkid(), Instant.now()).isEmpty();
    }

    @Test
    @DisplayName("deactivating a user ends their sessions and stops their access token at once")
    void deactivatedUser() throws Exception {
        User user = createUser();
        SessionService.Continued session = sessionService.start(user);
        String access = "Bearer " + jwtService.issueAccessToken(user, session.session().getId());
        profile(access).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/user/{id}/deactivate", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(createAdmin())))
                .andExpect(status().isOk());

        profile(access).andExpect(status().isForbidden());
        refresh(session.refreshToken()).andExpect(status().isForbidden());
        assertThat(signedIn(user)).isFalse();
    }

    @Test
    @DisplayName("access tokens issued before a password change are refused; newer ones still work")
    void passwordChange() throws Exception {
        User user = createUser();
        UUID session = sessionService.start(user).session().getId();
        String old = "Bearer " + minutesAgo(2).issueAccessToken(user, session);
        user.setPasswordChangedAt(Instant.now().minusSeconds(60));
        userRepository.save(user);

        profile(old).andExpect(status().isForbidden());
        profile("Bearer " + jwtService.issueAccessToken(user, session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a password reset signs out every session")
    void passwordReset() throws Exception {
        User user = createUser();
        String refreshToken = sessionService.start(user).refreshToken();
        sessionService.start(user);

        mockMvc.perform(post("/api/v1/auth/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailToken": "%s", "userId": "%s", "password": "N3w!password",
                                 "passwordConfirm": "N3w!password"}
                                """.formatted(verificationTokenService.issue(user), user.getId())))
                .andExpect(status().isOk());

        refresh(refreshToken).andExpect(status().isForbidden());
        assertThat(signedIn(user)).isFalse();
    }
}
