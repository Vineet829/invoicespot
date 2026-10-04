package com.invoicespot.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.invoicespot.IntegrationTest;
import com.invoicespot.Uncommitted;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

// The flows that change an account write back only what they changed, so a
// change that lands while another is under way, such as an admin's
// deactivation or a new password, stays.
class AccountRaceIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private int saveFirstName(String authorization) throws Exception {
        return mockMvc.perform(patch("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Grace\"}"))
                .andReturn().getResponse().getStatus();
    }

    private boolean active(User user) {
        return jdbc.queryForObject("select active from users where pkid = ?", Boolean.class, user.getPkid());
    }

    @Test
    @DisplayName("an account deactivated while its profile is being saved stays deactivated, with the new name")
    void deactivatedWhileProfileSaved() throws Exception {
        User user = createUser();
        String session = bearer(user);

        int status = Uncommitted.change(
                dataSource, "update users set active = false where pkid = ?", user.getPkid(),
                () -> saveFirstName(session));

        assertThat(status).isEqualTo(200);
        assertThat(active(user)).isFalse();
        assertThat(jdbc.queryForObject("select first_name from users where pkid = ?", String.class, user.getPkid()))
                .isEqualTo("Grace");
    }

    @Test
    @DisplayName("a password reset while the profile is being saved keeps the new password")
    void newPasswordWhileProfileSaved() throws Exception {
        User user = createUser();
        String session = bearer(user);

        Uncommitted.change(
                dataSource,
                "update users set password = 'new-hash', password_changed_at = now() where pkid = ?",
                user.getPkid(),
                () -> saveFirstName(session));

        assertThat(jdbc.queryForObject("select password from users where pkid = ?", String.class, user.getPkid()))
                .isEqualTo("new-hash");
    }

    @Test
    @DisplayName("an account deactivated while its password is being changed stays deactivated")
    void deactivatedWhilePasswordChanged() throws Exception {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);
        String session = bearer(user);

        int status = Uncommitted.change(
                dataSource, "update users set active = false where pkid = ?", user.getPkid(),
                () -> mockMvc.perform(post("/api/v1/account/password")
                                .header(HttpHeaders.AUTHORIZATION, session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(("{\"currentPassword\": \"%s\", \"newPassword\": \"N3w!password\","
                                        + " \"confirmPassword\": \"N3w!password\"}").formatted(PASSWORD)))
                        .andReturn().getResponse().getStatus());

        assertThat(status).isEqualTo(200);
        assertThat(active(user)).isFalse();
    }
}
