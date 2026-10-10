package com.invoicespot.audit;

import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Records who did what to an account, from which address, browser and session.
// Each entry commits on its own, so a failed sign-in is kept even though the
// sign-in rolls back. An entry for a deletion is written before the account
// goes, and keeps its email after.
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class AuditLog {

    private final AuditEventRepository repository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final Clock clock;

    public AuditLog(
            AuditEventRepository repository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.clock = clock;
    }

    public void record(AuditEventType type, User user) {
        record(type, user, null, null, null);
    }

    public void record(AuditEventType type, User user, User actor) {
        record(type, user, actor, null, null);
    }

    // A sign-in that failed, for the account the email belongs to if any.
    public void signInFailed(String email, User user, String reason) {
        record(AuditEventType.SIGN_IN_FAILED, user, null, email, reason);
    }

    public void record(AuditEventType type, User user, User actor, String email, String detail) {
        record(type, user, actor, email, detail, null);
    }

    // Something that happened in a business, to a member of it or to the
    // business itself.
    public void recordForBusiness(AuditEventType type, Long businessPkid, User user, User actor, String detail) {
        save(type, businessPkid, user, actor, null, detail, null);
    }

    // For someone who may not have an account yet, such as a person invited.
    public void recordForBusiness(
            AuditEventType type, Long businessPkid, User user, User actor, String email, String detail) {
        save(type, businessPkid, user, actor, email, detail, null);
    }

    // The session is the request's own unless another is named, as when a
    // sign-in starts one.
    public void record(AuditEventType type, User user, User actor, String email, String detail, UUID sessionId) {
        save(type, null, user, actor, email, detail, sessionId);
    }

    private void save(
            AuditEventType type,
            Long businessPkid,
            User user,
            User actor,
            String email,
            String detail,
            UUID sessionId) {
        AuditEvent event = new AuditEvent();
        event.setBusiness(businessPkid == null ? null : businessRepository.getReferenceById(businessPkid));
        event.setType(type);
        event.setUser(user == null ? null : userRepository.getReferenceById(user.getPkid()));
        event.setActor(actor == null ? null : userRepository.getReferenceById(actor.getPkid()));
        String address = email != null ? email : user == null ? null : user.getEmail();
        event.setEmail(clip(address == null ? null : address.trim().toLowerCase(Locale.ROOT), 254));
        event.setDetail(clip(detail, 255));
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            event.setIp(clip(request.getRemoteAddr(), 64));
            event.setUserAgent(clip(request.getHeader(HttpHeaders.USER_AGENT), 255));
        }
        event.setSessionId(sessionId != null ? sessionId : CurrentSession.id().orElse(null));
        event.setOccurredAt(clock.instant());
        repository.save(event);
    }

    private static String clip(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
