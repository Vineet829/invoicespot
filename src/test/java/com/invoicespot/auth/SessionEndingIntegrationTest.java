package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

class SessionEndingIntegrationTest extends IntegrationTest {

    @Autowired
    private AppProperties properties;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private JwtService minutesAgo(int minutes) {
        return new JwtService(properties, Clock.fixed(Instant.now().minusSeconds(60L * minutes), ZoneOffset.UTC));
    }

    private void storeRefreshToken(User user, String token) {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findByExternalId(user.getId()).orElseThrow().getRefreshTokens().add(token));
    }

    @Test
    @DisplayName("a deactivated user's access token stops working, and refreshing ends every session")
    void deactivatedUser() throws Exception {
        User user = createUser();
        String access = bearer(user);
        String refresh = jwtService.issueRefreshToken(user);
        storeRefreshToken(user, refresh);
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk());

        transactionTemplate.executeWithoutResult(status ->
                userRepository.findByExternalId(user.getId()).orElseThrow().setActive(false));

        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", refresh)))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByRefreshToken(refresh)).isEmpty();
    }

    @Test
    @DisplayName("tokens issued before a password change are refused; newer ones still work")
    void passwordChange() throws Exception {
        User user = createUser();
        String old = "Bearer " + minutesAgo(2).issueAccessToken(user);
        user.setPasswordChangedAt(Instant.now().minusSeconds(60));
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, old))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a password reset ends older sessions without an old token signing out newer ones")
    void passwordReset() throws Exception {
        User user = createUser();
        String oldRefresh = minutesAgo(2).issueRefreshToken(user);
        storeRefreshToken(user, oldRefresh);
        String resetToken = verificationTokenService.issue(user);

        mockMvc.perform(post("/api/v1/auth/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailToken\": \"%s\", \"userId\": \"%s\", \"password\": \"N3w!password\", \"passwordConfirm\": \"N3w!password\"}"
                                .formatted(resetToken, user.getId())))
                .andExpect(status().isOk());
        assertThat(userRepository.findByRefreshToken(oldRefresh)).isEmpty();

        String fresh = jwtService.issueRefreshToken(user);
        storeRefreshToken(user, fresh);
        mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", oldRefresh)))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByRefreshToken(fresh)).isPresent();
    }
}
