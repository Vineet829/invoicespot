package com.invoicespot.auth.session;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.session.dto.SessionResponse;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.config.AppProperties;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Starts, continues and ends sessions. A refresh swaps the session's token
// for a new one, at most once in a few seconds. The token it replaced still
// works for those seconds and the new one isn't swapped again, so tabs that
// refresh together all get an access token, whichever of the two cookies
// each sent; after that, the old token coming back means it was copied, and
// every session of that account is ended.
@Service
public class SessionService {

    static final Duration GRACE = Duration.ofSeconds(30);

    // A session and, when it was rotated, the refresh token that now opens it.
    public record Continued(UserSession session, String refreshToken) {}

    private final UserSessionRepository repository;
    private final AuditLog auditLog;
    private final DemoAccountGuard demoAccountGuard;
    private final Duration lifetime;
    private final Clock clock;

    public SessionService(
            UserSessionRepository repository,
            AuditLog auditLog,
            DemoAccountGuard demoAccountGuard,
            AppProperties properties,
            Clock clock) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.demoAccountGuard = demoAccountGuard;
        this.lifetime = properties.jwt().refreshTokenTtl();
        this.clock = clock;
    }

    @Transactional
    public Continued start(User user) {
        Instant now = clock.instant();
        String token = SecretTokens.generate();
        UserSession session = new UserSession();
        session.setUser(user);
        session.setTokenHash(SecretTokens.hash(token));
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(lifetime));
        describe(session);
        repository.save(session);
        return new Continued(session, token);
    }

    @Transactional
    public Optional<Continued> refresh(String token) {
        Instant now = clock.instant();
        String hash = SecretTokens.hash(token);
        Optional<UserSession> current = repository.findByTokenHash(hash);
        if (current.isPresent()) {
            UserSession session = current.get();
            if (!session.isActiveAt(now) || !session.getUser().isActive()) {
                return Optional.empty();
            }
            // Swapped a moment ago: another tab may still be sending the
            // token before it, which a second swap would make unknown.
            if (justRotated(session, now)) {
                return Optional.of(new Continued(session, null));
            }
            String next = SecretTokens.generate();
            session.setPreviousHash(hash);
            session.setTokenHash(SecretTokens.hash(next));
            session.setRotatedAt(now);
            session.setLastUsedAt(now);
            session.setExpiresAt(now.plus(lifetime));
            describe(session);
            return Optional.of(new Continued(session, next));
        }
        Optional<UserSession> replaced = repository.findByPreviousHash(hash);
        if (replaced.isEmpty()) {
            return Optional.empty();
        }
        UserSession session = replaced.get();
        if (session.isActiveAt(now) && session.getUser().isActive() && justRotated(session, now)) {
            return Optional.of(new Continued(session, null));
        }
        if (session.isActiveAt(now)) {
            revokeAll(session.getUser());
            auditLog.record(
                    AuditEventType.SESSION_TOKEN_REUSED,
                    session.getUser(),
                    null,
                    null,
                    "An old sign-in token was used again, so every session was signed out",
                    session.getId());
        }
        return Optional.empty();
    }

    private static boolean justRotated(UserSession session, Instant now) {
        return session.getRotatedAt() != null && session.getRotatedAt().plus(GRACE).isAfter(now);
    }

    // Ends the session a refresh token opens, and says whose it was. The token
    // a refresh has just replaced still names it: a tab may sign out while
    // another tab's refresh is on its way back.
    @Transactional
    public Optional<UserSession> end(String token) {
        String hash = SecretTokens.hash(token);
        Optional<UserSession> session = repository.findByTokenHash(hash)
                .or(() -> repository.findByPreviousHash(hash))
                .filter(found -> found.getRevokedAt() == null);
        session.ifPresent(found -> found.setRevokedAt(clock.instant()));
        return session;
    }

    @Transactional
    public void revokeAll(User user) {
        repository.revokeAll(user.getPkid(), clock.instant());
    }

    // The account's open sessions, most recently used first. The shared demo
    // account shows only the visitor's own, never another visitor's.
    @Transactional(readOnly = true)
    public List<SessionResponse> list(User user, UUID current) {
        boolean demo = demoAccountGuard.isDemo(user);
        return repository.findActive(user.getPkid(), clock.instant()).stream()
                .filter(session -> !demo || session.getId().equals(current))
                .map(session -> new SessionResponse(
                        session.getId(),
                        DeviceName.of(session.getUserAgent()),
                        session.getIp(),
                        session.getCreatedAt(),
                        session.getLastUsedAt(),
                        session.getId().equals(current)))
                .toList();
    }

    // Signs out another of the account's devices at once. This one signs
    // out with Log out, so its cookie goes too.
    @Transactional
    public void signOut(User user, String id, UUID current) {
        demoAccountGuard.requireNotDemo(user, "signing out other devices");
        UUID target = parse(id).orElseThrow(SessionService::alreadyEnded);
        if (target.equals(current)) {
            throw new ApiException(HttpStatus.CONFLICT, "Use Log out to sign out of this device");
        }
        Instant now = clock.instant();
        UserSession session = repository.findByExternalId(target)
                .filter(found -> found.getUser().getPkid().equals(user.getPkid()) && found.isActiveAt(now))
                .orElseThrow(SessionService::alreadyEnded);
        session.setRevokedAt(now);
        auditLog.record(
                AuditEventType.SESSION_SIGNED_OUT, user, null, null, DeviceName.of(session.getUserAgent()));
    }

    @Transactional
    public int signOutOthers(User user, UUID current) {
        demoAccountGuard.requireNotDemo(user, "signing out other devices");
        int ended = repository.revokeOthers(user.getPkid(), current, clock.instant());
        auditLog.record(
                AuditEventType.OTHER_SESSIONS_SIGNED_OUT,
                user,
                null,
                null,
                ended == 1 ? "1 device" : ended + " devices");
        return ended;
    }

    // Signs out every other session of the account, as a password change does.
    @Transactional
    public void endOthers(User user, UUID keep) {
        repository.revokeOthers(user.getPkid(), keep, clock.instant());
    }

    private static ApiException alreadyEnded() {
        return new ApiException(HttpStatus.NOT_FOUND, "That device is already signed out");
    }

    private static Optional<UUID> parse(String id) {
        try {
            return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    // The session an access token names, while it lasts.
    @Transactional(readOnly = true)
    public Optional<UserSession> active(UUID id) {
        Instant now = clock.instant();
        return repository.findByExternalId(id).filter(session -> session.isActiveAt(now));
    }

    // The address and browser the session was last used from.
    private static void describe(UserSession session) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            session.setIp(clip(request.getRemoteAddr(), 64));
            session.setUserAgent(clip(request.getHeader(HttpHeaders.USER_AGENT), 255));
        }
    }

    private static String clip(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
