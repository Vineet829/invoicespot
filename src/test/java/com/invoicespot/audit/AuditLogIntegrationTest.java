package com.invoicespot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.auth.VerificationTokenService;
import com.invoicespot.user.User;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class AuditLogIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService verificationTokenService;

    private User withPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private List<Map<String, Object>> events(String email) {
        return jdbcTemplate.queryForList(
                "select type, detail, ip, user_agent, user_pkid, actor_pkid from audit_events"
                        + " where email = ? order by pkid",
                email);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.USER_AGENT, "Test Browser/1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    @Test
    @DisplayName("a failed sign-in is kept though the sign-in fails, with the address and browser it came from")
    void signIns() throws Exception {
        User user = withPassword();
        login(user.getEmail(), "Wrong!pass1").andExpect(status().isUnauthorized());
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
        String nobody = "nobody-" + UUID.randomUUID() + "@example.test";
        login(nobody, PASSWORD).andExpect(status().isUnauthorized());

        List<Map<String, Object>> mine = events(user.getEmail());
        assertThat(mine).extracting(row -> row.get("type")).containsExactly("SIGN_IN_FAILED", "SIGN_IN");
        assertThat(mine.get(0))
                .containsEntry("detail", "Wrong password")
                .containsEntry("ip", "127.0.0.1")
                .containsEntry("user_agent", "Test Browser/1.0")
                .containsEntry("user_pkid", user.getPkid());
        assertThat(events(nobody)).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("type", "SIGN_IN_FAILED")
                .containsEntry("detail", "No account with this email")
                .containsEntry("user_pkid", null));
    }

    @Test
    @DisplayName("signing out and resetting a password are recorded")
    void signOutAndReset() throws Exception {
        User user = withPassword();
        Cookie refresh = login(user.getEmail(), PASSWORD).andReturn().getResponse().getCookie("jwt");
        mockMvc.perform(get("/api/v1/auth/logout").cookie(refresh)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailToken": "%s", "userId": "%s", "password": "N3w!password",
                                 "passwordConfirm": "N3w!password"}
                                """.formatted(verificationTokenService.issue(user), user.getId())))
                .andExpect(status().isOk());

        assertThat(events(user.getEmail()))
                .extracting(row -> row.get("type"))
                .containsExactly("SIGN_IN", "SIGN_OUT", "PASSWORD_RESET");
    }

    @Test
    @DisplayName("an admin's actions name the admin, and a deleted account's entries keep its email")
    void adminActionsAndDeletion() throws Exception {
        User admin = createAdmin();
        User target = createUser();
        mockMvc.perform(patch("/api/v1/user/{id}/deactivate", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/user/{id}", target.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        User leaving = createUser();
        mockMvc.perform(delete("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, bearer(leaving)))
                .andExpect(status().isOk());

        assertThat(events(target.getEmail())).satisfiesExactly(
                deactivated -> assertThat(deactivated)
                        .containsEntry("type", "USER_DEACTIVATED")
                        .containsEntry("actor_pkid", admin.getPkid())
                        .containsEntry("user_pkid", null),
                deleted -> assertThat(deleted)
                        .containsEntry("type", "USER_DELETED")
                        .containsEntry("actor_pkid", admin.getPkid()));
        assertThat(events(leaving.getEmail())).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("type", "ACCOUNT_DELETED")
                .containsEntry("user_pkid", null));
    }
}
