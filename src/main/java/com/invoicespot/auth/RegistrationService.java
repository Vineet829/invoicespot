package com.invoicespot.auth;

import com.invoicespot.auth.dto.RegisterRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final AppProperties.Site site;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            VerificationTokenService tokenService,
            MailService mailService,
            AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.site = properties.site();
    }

    @Transactional
    public String register(RegisterRequest request) {
        requireText(request.email(), "An email address is required");
        requireText(request.username(), "A username is required");
        if (isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw badRequest("You must enter a full name with a first and last name");
        }
        requireText(request.password(), "You must enter a password");
        requireText(request.passwordConfirm(), "Confirm password field is required");
        if (!request.password().equals(request.passwordConfirm())) {
            throw badRequest("Passwords do not match");
        }
        if (!isStrongPassword(request.password())) {
            throw badRequest(
                    "Password must be at least 8 characters long, with at least 1 uppercase and"
                            + " lowercase letters and at least 1 symbol");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw badRequest(
                    "The email address you've entered is already associated with another account");
        }
        if (userRepository.existsByUsername(username)) {
            throw badRequest("That username is already taken");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        sendVerification(user);
        return "A new user "
                + user.getFirstName()
                + " has been registered! A Verification email has been sent to your account. Please"
                + " verify within 15 minutes";
    }

    @Transactional
    public void verify(String userId, String token) {
        User user = parseId(userId)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> badRequest("We were unable to find a user for this token"));
        if (user.isEmailVerified()) {
            throw badRequest("This user has already been verified. Please login");
        }

        VerifyResetToken verificationToken = tokenService
                .findUsable(token)
                .filter(entity -> entity.getUser().getPkid().equals(user.getPkid()))
                .orElseThrow(() -> badRequest("Token invalid! Your token may have expired"));

        user.setEmailVerified(true);
        userRepository.save(user);
        tokenService.consume(verificationToken);

        mailService.send(
                user.getEmail(),
                "Welcome - Account Verified",
                "Hello "
                        + user.getFirstName()
                        + ", your "
                        + site.name()
                        + " account is now verified. You can log in at "
                        + site.clientUrl()
                        + "/login");
    }

    @Transactional
    public String resend(String email) {
        if (isBlank(email)) {
            throw badRequest("An email must be provided");
        }
        User user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> badRequest("We were unable to find a user with that email address"));
        if (user.isEmailVerified()) {
            throw badRequest("This account has already been verified. Please login");
        }
        sendVerification(user);
        return user.getFirstName()
                + ", an email has been sent to your account, please verify within 15 minutes";
    }

    private void sendVerification(User user) {
        String token = tokenService.issue(user);
        String link = site.clientUrl() + "/verify-email/" + token + "/" + user.getId();
        mailService.send(
                user.getEmail(),
                "Account Verification",
                "Hello "
                        + user.getFirstName()
                        + ", welcome to "
                        + site.name()
                        + ". Please verify your account within 15 minutes using this link: "
                        + link);
    }

    private static Optional<UUID> parseId(String userId) {
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw badRequest(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isStrongPassword(String password) {
        if (password.length() < 8) {
            return false;
        }
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (char character : password.toCharArray()) {
            if (Character.isLowerCase(character)) {
                lower = true;
            } else if (Character.isUpperCase(character)) {
                upper = true;
            } else if (Character.isDigit(character)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        return lower && upper && digit && symbol;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
