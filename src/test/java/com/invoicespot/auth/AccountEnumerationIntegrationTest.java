package com.invoicespot.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

// Asking for a password reset or a new verification link gets the same answer
// whatever the email, so neither form tells anyone who has an account, and a
// sign-in without an account still checks a password, so it takes as long.
class AccountEnumerationIntegrationTest extends IntegrationTest {

    @SpyBean
    private PasswordEncoder passwordEncoder;

    private ResultActions ask(String path, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/" + path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private User unverified() {
        User user = createUser();
        user.setEmailVerified(false);
        return userRepository.save(user);
    }

    @Test
    @DisplayName("a password reset request gets one answer; only a verified account is sent a link")
    void resetRequests() throws Exception {
        for (String email : new String[] {"nobody@example.test", unverified().getEmail()}) {
            ask("reset_password_request", email)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(PasswordResetService.RESET_REQUESTED));
        }
        verify(mailSender, after(500).never()).send(any(SimpleMailMessage.class));

        ask("reset_password_request", createUser().getEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(PasswordResetService.RESET_REQUESTED));
        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("a request for a new verification link gets one answer; only an unverified account is sent one")
    void verificationRequests() throws Exception {
        for (String email : new String[] {"nobody@example.test", createUser().getEmail()}) {
            ask("resend_email_token", email)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(RegistrationService.VERIFICATION_RESENT));
        }
        verify(mailSender, after(500).never()).send(any(SimpleMailMessage.class));

        ask("resend_email_token", unverified().getEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(RegistrationService.VERIFICATION_RESENT));
        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("a sign-in with an email nobody uses still checks the password before it is refused")
    void signInWithoutAccount() throws Exception {
        clearInvocations(passwordEncoder);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.test\", \"password\": \"Any!pass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password"));
        verify(passwordEncoder).matches(eq("Any!pass1"), anyString());
    }
}
