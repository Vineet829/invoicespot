package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.auth.dto.RegisterRequest;
import com.invoicespot.business.BusinessService;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
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
    @Mock private BusinessService businessService;
    @Mock private UnverifiedSignUps unverifiedSignUps;
    @Mock private RateLimiter rateLimiter;

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
                userRepository, passwordEncoder, tokenService, mailService, businessService, unverifiedSignUps,
                rateLimiter, PROPERTIES);
    }

    private static RegisterRequest request(String password, String confirm) {
        return new RegisterRequest("Ada@Example.com", "ada_user", "Ada", "Lovelace", password, confirm);
    }

    @Test
    @DisplayName("a valid registration persists the user, issues a token and sends verification mail")
    void registersValidUser() {
        when(userRepository.existsByUsername("ada_user")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!pw")).thenReturn("hashed");
        when(tokenService.issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL))).thenReturn("tok");

        String message = service.register(request("Str0ng!pw", "Str0ng!pw"));

        assertThat(message).startsWith("A new user Ada has been registered!");
        verify(userRepository).save(any(User.class));
        verify(businessService).createPersonal(any(User.class));
        verify(tokenService).issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL));
        verify(mailService).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("registration lowercases the email before persisting")
    void lowercasesEmail() {
        when(userRepository.existsByUsername("ada_user")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(tokenService.issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL))).thenReturn("tok");

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        service.register(request("Str0ng!pw", "Str0ng!pw"));
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getEmail()).isEqualTo("ada@example.com");
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
    @DisplayName("an email with an account gets a new sign-up's answer, its password hashed alike, and its owner is"
            + " told instead")
    void takenEmail() {
        User owner = new User();
        owner.setEmail("ada@example.com");
        owner.setFirstName("Ada");
        owner.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(owner));

        String message = service.register(request("Str0ng!pw", "Str0ng!pw"));

        assertThat(message).startsWith("A new user Ada has been registered!");
        verify(passwordEncoder).encode("Str0ng!pw");
        verify(userRepository, never()).save(any());
        verify(tokenService, never()).issue(any(User.class), any(Duration.class));
        verify(mailService).send(eq("ada@example.com"), eq("Someone tried to sign up with your email"), anyString());
    }

    @Test
    @DisplayName("an email whose account was never verified gives way to the new sign-up")
    void unverifiedEmail() {
        User pending = new User();
        pending.setEmail("ada@example.com");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.encode("Str0ng!pw")).thenReturn("hashed");
        when(tokenService.issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL))).thenReturn("tok");

        service.register(request("Str0ng!pw", "Str0ng!pw"));

        verify(unverifiedSignUps).remove(pending);
        verify(userRepository).save(any(User.class));
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
