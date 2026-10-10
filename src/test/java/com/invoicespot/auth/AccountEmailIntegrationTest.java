package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.invoicespot.IntegrationTest;
import com.invoicespot.auth.dto.ChangePasswordRequest;
import com.invoicespot.auth.dto.RegisterRequest;
import com.invoicespot.auth.dto.ResetPasswordRequest;
import com.invoicespot.user.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// An email about a change to an account goes out once the change is saved.
// Each change first runs in a transaction of the test's own that rolls back,
// as one does when saving it fails at the very end, and nobody is emailed;
// then it runs for real, and the email goes out.
class AccountEmailIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";
    private static final String NEW_PASSWORD = "N3w!password";

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private PasswordChangeService passwordChangeService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private RegistrationService registrationService;

    private void rolledBack(Runnable change) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            change.run();
            transaction.setRollbackOnly();
        });
    }

    // Waits long enough for an email handed over to have arrived.
    private void nothingSent() {
        verify(mailSender, after(1000).never()).send(any(SimpleMailMessage.class));
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(mail.capture());
        return mail.getValue();
    }

    @Test
    @DisplayName("a password change that rolls back emails nobody; saved, the owner is told")
    void passwordChange() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);
        UUID session = sessionService.start(user).session().getId();
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        rolledBack(() -> passwordChangeService.change(user, session, request));
        nothingSent();

        passwordChangeService.change(user, session, request);
        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly(user.getEmail());
        assertThat(mail.getSubject()).isEqualTo("Your Invoice Spot password was changed");
    }

    @Test
    @DisplayName("a reset link that isn't saved is never sent; saved, it is")
    void resetLink() {
        User user = createUser();

        rolledBack(() -> passwordResetService.requestReset(user.getEmail()));
        nothingSent();

        passwordResetService.requestReset(user.getEmail());
        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly(user.getEmail());
        assertThat(mail.getSubject()).isEqualTo("Password Reset Request");
    }

    @Test
    @DisplayName("a password reset that rolls back emails nobody; saved, the owner is told")
    void reset() {
        User user = createUser();
        String token = tokenService.issue(user);
        ResetPasswordRequest request =
                new ResetPasswordRequest(NEW_PASSWORD, NEW_PASSWORD, user.getId().toString(), token);

        rolledBack(() -> passwordResetService.resetPassword(request));
        nothingSent();

        passwordResetService.resetPassword(request);
        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly(user.getEmail());
        assertThat(mail.getSubject()).isEqualTo("Password Reset Success");
    }

    @Test
    @DisplayName("a sign-up that rolls back sends no verification link; saved, it does")
    void signUp() {
        String handle = "signup-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request =
                new RegisterRequest(handle + "@example.test", handle, "Ada", "Lovelace", PASSWORD, PASSWORD);

        rolledBack(() -> registrationService.register(request));
        nothingSent();

        registrationService.register(request);
        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly(handle + "@example.test");
        assertThat(mail.getSubject()).isEqualTo("Account Verification");
    }

    @Test
    @DisplayName("a verification that rolls back sends no welcome; saved, it does")
    void verification() {
        User user = createUser();
        user.setEmailVerified(false);
        userRepository.save(user);
        String token = tokenService.issue(user);

        rolledBack(() -> registrationService.verify(user.getId().toString(), token));
        nothingSent();

        registrationService.verify(user.getId().toString(), token);
        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly(user.getEmail());
        assertThat(mail.getSubject()).isEqualTo("Welcome - Account Verified");
    }
}
