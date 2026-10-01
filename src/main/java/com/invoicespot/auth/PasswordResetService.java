package com.invoicespot.auth;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.dto.ResetPasswordRequest;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    public static final String RESET_REQUESTED =
            "If an account uses that email, we've sent it a link to reset the password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final AppProperties.Site site;
    private final SessionService sessionService;
    private final AuditLog auditLog;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            VerificationTokenService tokenService,
            MailService mailService,
            AppProperties properties,
            SessionService sessionService,
            AuditLog auditLog,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.site = properties.site();
        this.sessionService = sessionService;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // The answer is the same whether or not the email has an account, so the
    // form can't be used to find out who has one. Only a verified account is
    // sent a link.
    @Transactional
    public String requestReset(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You must enter your email address");
        }
        userRepository
                .findByEmailIgnoreCase(email.trim())
                .filter(User::isEmailVerified)
                .ifPresent(this::sendResetLink);
        return RESET_REQUESTED;
    }

    private void sendResetLink(User user) {
        String token = tokenService.issue(user);
        String link = site.clientUrl() + "/auth/reset_password?emailToken=" + token + "&userId=" + user.getId();
        mailService.send(
                user.getEmail(),
                "Password Reset Request",
                "Hello " + user.getFirstName() + ", use this link to reset your " + site.name() + " password: "
                        + link);
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
        if (!PasswordPolicy.isStrong(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PasswordPolicy.REQUIREMENTS);
        }

        VerifyResetToken token = tokenService
                .findUsable(request.emailToken())
                .filter(entity -> belongsTo(entity, request.userId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Your token is either invalid or expired. Try resetting your password again"));

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(clock.instant());
        userRepository.save(user);
        sessionService.revokeAll(user);
        tokenService.revokeAll(user);
        auditLog.record(AuditEventType.PASSWORD_RESET, user);

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
