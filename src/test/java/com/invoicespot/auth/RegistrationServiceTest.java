package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.auth.dto.RegisterRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationTokenService tokenService;
    @Mock private MailService mailService;

    private RegistrationService service;

    private static final AppProperties PROPERTIES = new AppProperties(
            new AppProperties.Jwt("a", Duration.ofMinutes(10), Duration.ofDays(1)),
            new AppProperties.Cookie("jwt", "/", "None", true),
            new AppProperties.Site("Invoice Spot", "http://localhost:3000", "http://localhost:8080", "from@invoicespot.local"),
            new AppProperties.Verification(Duration.ofMinutes(15)),
            new AppProperties.Upload("uploads"));

    @BeforeEach
    void setUp() {
        service = new RegistrationService(
                userRepository, passwordEncoder, tokenService, mailService, PROPERTIES);
    }

    private static RegisterRequest request(String password, String confirm) {
        return new RegisterRequest("Ada@Example.com", "ada_user", "Ada", "Lovelace", password, confirm);
    }

    @Test
    @DisplayName("a valid registration persists the user, issues a token and sends verification mail")
    void registersValidUser() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("ada_user")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!pw")).thenReturn("hashed");
        when(tokenService.issue(any(User.class))).thenReturn("tok");

        String message = service.register(request("Str0ng!pw", "Str0ng!pw"));

        assertThat(message).startsWith("A new user Ada has been registered!");
        verify(userRepository).save(any(User.class));
        verify(tokenService).issue(any(User.class));
        verify(mailService).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("registration lowercases the email before persisting")
    void lowercasesEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("ada_user")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(tokenService.issue(any(User.class))).thenReturn("tok");

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        service.register(request("Str0ng!pw", "Str0ng!pw"));
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(captor.getValue().getPhoneNumber()).isNull();
    }

    @Test
    @DisplayName("mismatched passwords are rejected before any persistence")
    void rejectsMismatchedPasswords() {
        assertThatThrownBy(() -> service.register(request("Str0ng!pw", "Other1!pw")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Passwords do not match")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a weak password is rejected")
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> service.register(request("weak", "weak")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 1 uppercase");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate email is rejected")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("Str0ng!pw", "Str0ng!pw")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already associated with another account");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blank email yields the email-required message")
    void rejectsBlankEmail() {
        RegisterRequest blank = new RegisterRequest(
                " ", "ada_user", "Ada", "Lovelace", "Str0ng!pw", "Str0ng!pw");

        assertThatThrownBy(() -> service.register(blank))
                .isInstanceOf(ApiException.class)
                .hasMessage("An email address is required");
    }
}
