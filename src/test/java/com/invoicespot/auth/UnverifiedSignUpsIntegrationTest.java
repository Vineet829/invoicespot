package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class UnverifiedSignUpsIntegrationTest extends IntegrationTest {

    @Autowired
    private UnverifiedSignUps unverifiedSignUps;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ResultActions signUp(String email, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr("10.7.0." + (1 + (int) (Math.random() * 250)));
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "username": "%s", "firstName": "Ada", "lastName": "Lovelace",
                         "password": "%s", "passwordConfirm": "%s"}
                        """.formatted(email, username, password, password)));
    }

    private User unverified(String email) {
        User user = createUser();
        user.setEmail(email);
        user.setEmailVerified(false);
        return userRepository.save(user);
    }

    @Test
    @DisplayName("a sign-up never verified gives way to a new one with its email, which keeps the new password")
    void givesWay() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.test";
        User pending = unverified(email);

        signUp(email, "own" + UUID.randomUUID().toString().substring(0, 8), "N3w!password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(startsWith("A new user Ada")));

        assertThat(userRepository.findById(pending.getPkid())).isEmpty();
        User replacement = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches("N3w!password", replacement.getPassword())).isTrue();
        assertThat(businessService.ownedBy(replacement.getPkid())).isNotNull();
    }

    @Test
    @DisplayName("an email with a verified account gets the same answer, and the account stays as it was")
    void taken() throws Exception {
        User owner = createUser();
        String hash = owner.getPassword();

        signUp(owner.getEmail(), "own" + UUID.randomUUID().toString().substring(0, 8), "N3w!password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(startsWith("A new user Ada")));

        User kept = userRepository.findById(owner.getPkid()).orElseThrow();
        assertThat(kept.getPassword()).isEqualTo(hash);
        assertThat(kept.getFirstName()).isEqualTo(owner.getFirstName());
    }

    @Test
    @DisplayName("sign-ups never verified go after a week; newer ones and verified accounts stay")
    void removedAfterAWeek() {
        User old = unverified("old-" + UUID.randomUUID() + "@example.test");
        User recent = unverified("recent-" + UUID.randomUUID() + "@example.test");
        User verified = createUser();
        jdbcTemplate.update(
                "update users set created_at = now() - interval '8 days' where pkid in (?, ?)",
                old.getPkid(), verified.getPkid());

        assertThat(unverifiedSignUps.removeExpired()).isGreaterThanOrEqualTo(1);

        assertThat(userRepository.findById(old.getPkid())).isEmpty();
        assertThat(userRepository.findById(recent.getPkid())).isPresent();
        assertThat(userRepository.findById(verified.getPkid())).isPresent();
    }
}
