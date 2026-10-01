package com.invoicespot.auth.twofactor;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.auth.twofactor.dto.TwoFactorSetupResponse;
import com.invoicespot.auth.twofactor.dto.TwoFactorStatusResponse;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.SecretBox;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

// Two-factor sign-in with an authenticator app. Setup needs the password,
// so a borrowed session can't lock the owner out; turning it off needs the
// password and a code. Ten single-use recovery codes stand in for the app.
// Wrong passwords and wrong codes are each limited per account.
@Service
public class TwoFactorService {

    public enum Method {
        AUTHENTICATOR("With a code from an authenticator app"),
        RECOVERY_CODE("With a recovery code");

        private final String description;

        Method(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private static final int RECOVERY_CODES = 10;
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final SessionService sessionService;
    private final SecretBox secretBox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final MailService mailService;
    private final AppProperties.Site site;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(
            UserRepository userRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            SessionService sessionService,
            SecretBox secretBox,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            MailService mailService,
            AppProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.sessionService = sessionService;
        this.secretBox = secretBox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.mailService = mailService;
        this.site = properties.site();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse status(User principal) {
        User user = require(principal);
        return new TwoFactorStatusResponse(
                true,
                secretBox.available(),
                user.isTwoFactorEnabled(),
                user.getTwoFactorEnabledAt(),
                user.isTwoFactorEnabled() ? recoveryCodeRepository.countByUserPkidAndUsedAtIsNull(user.getPkid()) : 0);
    }

    // A new secret for the app to scan. It counts once a code from it is
    // confirmed; starting again replaces it.
    @Transactional
    public TwoFactorSetupResponse setup(User principal, String password) {
        User user = requireLocked(principal);
        demoAccountGuard.requireNotDemo(user, "two-factor sign-in");
        if (!secretBox.available()) {
            throw new ApiException(HttpStatus.CONFLICT, "Two-factor sign-in isn't available on this server yet");
        }
        if (user.isTwoFactorEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "Two-factor sign-in is already on");
        }
        requirePassword(user, password);
        byte[] secret = Totp.newSecret();
        user.setTwoFactorSecret(secretBox.seal(secret));
        user.setTwoFactorLastStep(null);
        String key = Totp.base32(secret);
        String label = UriUtils.encode(site.name() + ":" + user.getEmail(), StandardCharsets.UTF_8);
        String issuer = UriUtils.encode(site.name(), StandardCharsets.UTF_8);
        return new TwoFactorSetupResponse(
                true,
                key,
                "otpauth://totp/" + label + "?secret=" + key + "&issuer=" + issuer
                        + "&algorithm=SHA1&digits=6&period=30");
    }

    // Turning it on or off signs out every other device, as a new password
    // does: whoever else was signed in has to sign in again under the new
    // rules.
    @Transactional
    public List<String> enable(User principal, UUID currentSession, String code) {
        User user = requireLocked(principal);
        demoAccountGuard.requireNotDemo(user, "two-factor sign-in");
        if (user.isTwoFactorEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "Two-factor sign-in is already on");
        }
        if (user.getTwoFactorSecret() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Start setting up two-factor sign-in first");
        }
        if (!acceptsAuthenticatorCode(user, code)) {
            throw wrongCode();
        }
        user.setTwoFactorEnabledAt(clock.instant());
        List<String> codes = newRecoveryCodes(user);
        sessionService.endOthers(user, currentSession);
        auditLog.record(AuditEventType.TWO_FACTOR_ENABLED, user);
        notifyOwner(user, "Two-factor sign-in was turned on", "turned on");
        return codes;
    }

    @Transactional
    public void disable(User principal, UUID currentSession, String password, String code) {
        User user = requireEnabled(principal);
        requirePassword(user, password);
        if (verify(user, code).isEmpty()) {
            throw wrongCode();
        }
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabledAt(null);
        user.setTwoFactorLastStep(null);
        recoveryCodeRepository.deleteAllOf(user.getPkid());
        sessionService.endOthers(user, currentSession);
        auditLog.record(AuditEventType.TWO_FACTOR_DISABLED, user);
        notifyOwner(user, "Two-factor sign-in was turned off", "turned off");
    }

    // New recovery codes replace the old ones, which stop working.
    @Transactional
    public List<String> renewRecoveryCodes(User principal, String code) {
        User user = requireEnabled(principal);
        if (!acceptsAuthenticatorCode(user, code)) {
            throw wrongCode();
        }
        recoveryCodeRepository.deleteAllOf(user.getPkid());
        List<String> codes = newRecoveryCodes(user);
        auditLog.record(AuditEventType.RECOVERY_CODES_RENEWED, user);
        return codes;
    }

    // Checks the code a sign-in gives, from the app or a recovery code. A
    // recovery code is used up; an app code can't be used again.
    @Transactional
    public Optional<Method> verify(User principal, String code) {
        User user = requireLocked(principal);
        String attempts = "code:" + user.getPkid();
        rateLimiter.requireAvailable(RateLimit.WRONG_TWO_FACTOR_CODE, attempts);
        Optional<Method> method = check(user, code == null ? "" : code);
        if (method.isEmpty()) {
            rateLimiter.recordFailure(RateLimit.WRONG_TWO_FACTOR_CODE, attempts);
        } else {
            rateLimiter.reset(RateLimit.WRONG_TWO_FACTOR_CODE, attempts);
        }
        return method;
    }

    private Optional<Method> check(User user, String code) {
        String digits = code.replaceAll("\\s", "");
        if (digits.matches("\\d{6}")) {
            // The app's secret is kept sealed with the server's key; without
            // it a code can't be checked, but recovery codes, kept hashed,
            // still sign in.
            if (!secretBox.available()) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Codes from your authenticator app can't be checked right now. Use a recovery code");
            }
            OptionalLong step = Totp.match(secretBox.open(user.getTwoFactorSecret()), digits, clock.instant());
            Long last = user.getTwoFactorLastStep();
            if (step.isPresent() && (last == null || step.getAsLong() > last)) {
                user.setTwoFactorLastStep(step.getAsLong());
                return Optional.of(Method.AUTHENTICATOR);
            }
            return Optional.empty();
        }
        if (!user.isTwoFactorEnabled()) {
            return Optional.empty();
        }
        String normalized = code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
        return recoveryCodeRepository
                .findByUserPkidAndCodeHashAndUsedAtIsNull(user.getPkid(), SecretTokens.hash(normalized))
                .map(recovery -> {
                    recovery.setUsedAt(clock.instant());
                    auditLog.record(AuditEventType.RECOVERY_CODE_USED, user);
                    return Method.RECOVERY_CODE;
                });
    }

    private boolean acceptsAuthenticatorCode(User user, String code) {
        String digits = code == null ? "" : code.replaceAll("\\s", "");
        return digits.matches("\\d{6}") && verify(user, digits).isPresent();
    }

    private List<String> newRecoveryCodes(User user) {
        List<String> codes = new ArrayList<>();
        for (int count = 0; count < RECOVERY_CODES; count++) {
            StringBuilder code = new StringBuilder();
            for (int index = 0; index < 10; index++) {
                code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            RecoveryCode recovery = new RecoveryCode();
            recovery.setUser(user);
            recovery.setCodeHash(SecretTokens.hash(code.toString()));
            recoveryCodeRepository.save(recovery);
            codes.add(code.substring(0, 5) + "-" + code.substring(5));
        }
        return codes;
    }

    private void requirePassword(User user, String password) {
        String attempts = "password:" + user.getPkid();
        rateLimiter.requireAvailable(RateLimit.WRONG_PASSWORD, attempts);
        if (user.getPassword() == null || password == null || !passwordEncoder.matches(password, user.getPassword())) {
            rateLimiter.recordFailure(RateLimit.WRONG_PASSWORD, attempts);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your password is wrong");
        }
        rateLimiter.reset(RateLimit.WRONG_PASSWORD, attempts);
    }

    private User requireEnabled(User principal) {
        User user = requireLocked(principal);
        demoAccountGuard.requireNotDemo(user, "two-factor sign-in");
        if (!user.isTwoFactorEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "Two-factor sign-in is off");
        }
        return user;
    }

    private User require(User principal) {
        return userRepository.findById(principal.getPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    // The account, locked until the transaction ends, so a code checked here
    // can't be spent by another request at the same time.
    private User requireLocked(User principal) {
        return userRepository.findForUpdateByPkid(principal.getPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private static ApiException wrongCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "That code didn't work");
    }

    private void notifyOwner(User user, String subject, String change) {
        mailService.send(
                user.getEmail(),
                subject,
                "Hello " + user.getFirstName() + ",\n\n"
                        + "Two-factor sign-in was just " + change + " for your " + site.name()
                        + " account, and your other devices were signed out.\n\n"
                        + "If this wasn't you, reset your password at " + site.clientUrl()
                        + "/reset_password_request and check the devices signed in to your account.");
    }
}
