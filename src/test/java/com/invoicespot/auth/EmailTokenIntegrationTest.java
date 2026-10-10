package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.user.User;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

// The token in a password reset link is kept only as its hash: the link
// works, and what the database holds doesn't work as a link.
class EmailTokenIntegrationTest extends IntegrationTest {

    private static final Pattern LINK_TOKEN = Pattern.compile("emailToken=([0-9a-f]{64})&userId=");

    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions reset(User user, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset_password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "N3w!password", "passwordConfirm": "N3w!password",
                         "emailToken": "%s", "userId": "%s"}
                        """.formatted(token, user.getId())));
    }

    @Test
    @DisplayName("a reset link's token is kept hashed: the link works, the stored hash doesn't")
    void keptHashed() throws Exception {
        User user = createUser();
        mockMvc.perform(post("/api/v1/auth/reset_password_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(user.getEmail())))
                .andExpect(status().isOk());
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(mail.capture());
        Matcher link = LINK_TOKEN.matcher(mail.getValue().getText());
        assertThat(link.find()).isTrue();
        String sent = link.group(1);
        String kept = jdbc.queryForObject(
                "select token from verify_reset_tokens t join users u on u.pkid = t.user_pkid where u.id = ?",
                String.class,
                user.getId());

        assertThat(kept).isEqualTo(SecretTokens.hash(sent)).isNotEqualTo(sent);
        reset(user, kept).andExpect(status().isBadRequest());
        reset(user, sent).andExpect(status().isOk());
    }
}
