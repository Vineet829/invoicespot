package com.invoicespot.auth;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.auth.twofactor.TwoFactorService;
import com.invoicespot.common.ApiException;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final AuditLog auditLog;
    private final String unusedHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionService sessionService,
            TwoFactorService twoFactorService,
            AuditLog auditLog) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.twoFactorService = twoFactorService;
        this.auditLog = auditLog;
        this.unusedHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // A sign-in starts a session; one the browser still had is ended. With
    // two-factor sign-in on, a correct password only earns a challenge.
    @Transactional
    public LoginOutcome login(String email, String password, String presentedRefreshToken) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please provide an email and password");
        }

        Optional<User> account = userRepository.findByEmailIgnoreCase(email.trim());
        String hash = account.map(User::getPassword).orElse(null);
        // Without an account the password is still checked, against a hash
        // nobody's password matches, so the answer takes as long either way.
        boolean matches = passwordEncoder.matches(password, hash == null ? unusedHash : hash);
        if (hash == null || !matches) {
            auditLog.signInFailed(
                    email, account.orElse(null), account.isEmpty() ? "No account with this email" : "Wrong password");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
        User user = account.get();

        if (!user.isEmailVerified()) {
            auditLog.signInFailed(email, user, "Email not verified");
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You are not verified. Check your email, a verification email link was"
                            + " sent when you registered");
        }
        if (!user.isActive()) {
            auditLog.signInFailed(email, user, "Account deactivated");
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You have been deactivated by the admin and login is impossible. Contact us"
                            + " for enquiries");
        }

        if (user.isTwoFactorEnabled()) {
            return new LoginOutcome.TwoFactorChallenge(jwtService.issueTwoFactorChallenge(user));
        }
        return startSession(user, presentedRefreshToken, null);
    }

    // The second step of a two-factor sign-in: the challenge from the first,
    // and a code from the app or a recovery code.
    @Transactional
    public TokenBundle completeTwoFactor(String challenge, String code, String presentedRefreshToken) {
        User user = jwtService.readTwoFactorChallenge(challenge)
                .flatMap(userRepository::findForUpdateByExternalId)
                .filter(candidate -> candidate.isActive() && candidate.isTwoFactorEnabled())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Your sign-in timed out. Log in again"));
        Optional<TwoFactorService.Method> method = twoFactorService.verify(user, code);
        if (method.isEmpty()) {
            auditLog.signInFailed(user.getEmail(), user, "Wrong two-factor code");
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code didn't work");
        }
        return startSession(user, presentedRefreshToken, method.get().description());
    }

    private TokenBundle startSession(User user, String presentedRefreshToken, String detail) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            sessionService.end(presentedRefreshToken);
        }
        SessionService.Continued started = sessionService.start(user);
        UUID sessionId = started.session().getId();
        auditLog.record(AuditEventType.SIGN_IN, user, null, null, detail, sessionId);
        return new TokenBundle(jwtService.issueAccessToken(user, sessionId), started.refreshToken(), user);
    }

    // A new access token for the session, and its next refresh token unless a
    // refresh a moment ago already swapped it.
    @Transactional
    public Optional<TokenBundle> refresh(String presentedRefreshToken) {
        return sessionService.refresh(presentedRefreshToken).map(continued -> {
            User user = continued.session().getUser();
            return new TokenBundle(
                    jwtService.issueAccessToken(user, continued.session().getId()), continued.refreshToken(), user);
        });
    }

    @Transactional
    public Optional<String> logout(String presentedRefreshToken) {
        return sessionService.end(presentedRefreshToken).map(session -> {
            User user = session.getUser();
            auditLog.record(AuditEventType.SIGN_OUT, user, null, null, null, session.getId());
            return user.getFirstName() + ",you have been logged out successfully";
        });
    }
}
