package com.invoicespot.auth;

import com.invoicespot.auth.dto.ResetPasswordRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final AppProperties.Site site;

    public PasswordResetService(
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
    public String requestReset(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You must enter your email address");
        }
        User user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "That email is not associated with any account"));

        String token = tokenService.issue(user);
        if (user.isEmailVerified()) {
            String link = site.clientUrl()
                    + "/auth/reset_password?emailToken="
                    + token
                    + "&userId="
                    + user.getId();
            mailService.send(
                    user.getEmail(),
                    "Password Reset Request",
                    "Hello "
                            + user.getFirstName()
                            + ", use this link to reset your "
                            + site.name()
                            + " password: "
                            + link);
        }
        return "Hey "
                + user.getFirstName()
                + ", an email has been sent to your account with the password reset link";
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A password is required");
        }
        if (request.passwordConfirm() == null || request.passwordConfirm().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A confirm password field is required");
        }
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        if (request.password().length() < 8) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Passwords must be at least 8 characters long");
        }

        VerifyResetToken token = tokenService
                .findUsable(request.emailToken())
                .filter(entity -> belongsTo(entity, request.userId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Your token is either invalid or expired. Try resetting your password again"));

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        tokenService.consume(token);

        mailService.send(
                user.getEmail(),
                "Password Reset Success",
                "Hello "
                        + user.getFirstName()
                        + ", your "
                        + site.name()
                        + " password was reset successfully.");
        return "Hey "
                + user.getFirstName()
                + ",Your password reset was successful. An email has been sent to confirm the same";
    }

    private static boolean belongsTo(VerifyResetToken token, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        try {
            return token.getUser().getId().equals(UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
