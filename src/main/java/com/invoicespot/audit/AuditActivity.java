package com.invoicespot.audit;

import com.invoicespot.audit.dto.ActivityEventResponse;
import com.invoicespot.audit.dto.AuditEventResponse;
import com.invoicespot.audit.dto.EventPageResponse;
import com.invoicespot.audit.dto.TeamActivityResponse;
import com.invoicespot.auth.session.DeviceName;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.Pages;
import com.invoicespot.common.SearchText;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads the audit log: an account's own sign-ins and changes, a business's
// team changes for its owner and admins, and the whole log for admins. The
// shared demo account sees only what the visitor did in this session, never
// another visitor's addresses.
@Service
public class AuditActivity {

    private static final int MOST_PER_PAGE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt", "pkid");

    private final AuditEventRepository repository;
    private final DemoAccountGuard demoAccountGuard;

    public AuditActivity(AuditEventRepository repository, DemoAccountGuard demoAccountGuard) {
        this.repository = repository;
        this.demoAccountGuard = demoAccountGuard;
    }

    @Transactional(readOnly = true)
    public EventPageResponse<ActivityEventResponse> mine(User user, UUID currentSession, int page, int size) {
        Specification<AuditEvent> mine = (root, query, builder) -> builder.and(
                builder.equal(root.get("user").get("pkid"), user.getPkid()),
                builder.not(root.get("type").in(AuditEventType.aboutABusiness())));
        if (demoAccountGuard.isDemo(user)) {
            mine = mine.and((root, query, builder) -> currentSession == null
                    ? builder.disjunction()
                    : builder.equal(root.get("sessionId"), currentSession));
        }
        return page(mine, page, size, event -> new ActivityEventResponse(
                event.getId(),
                event.getType().name(),
                event.getDetail(),
                event.getIp(),
                DeviceName.of(event.getUserAgent()),
                event.getOccurredAt(),
                event.getSessionId() != null && event.getSessionId().equals(currentSession)));
    }

    // Who was invited, joined, left or changed role in a business, and who
    // handed it over, newest first.
    @Transactional(readOnly = true)
    public EventPageResponse<TeamActivityResponse> team(Long businessPkid, int page, int size) {
        Specification<AuditEvent> inBusiness = (root, query, builder) ->
                builder.equal(root.get("business").get("pkid"), businessPkid);
        return page(inBusiness, page, size, TeamActivityResponse::from);
    }

    // The whole log, newest first, by email and kind of event.
    @Transactional(readOnly = true)
    public EventPageResponse<AuditEventResponse> all(String email, String type, int page, int size) {
        Specification<AuditEvent> matching = Specification.where(null);
        if (email != null && !email.isBlank()) {
            String pattern = SearchText.containing(email);
            matching = matching.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("email")), pattern, SearchText.ESCAPE));
        }
        if (type != null && !type.isBlank()) {
            AuditEventType kind = parse(type);
            matching = matching.and((root, query, builder) -> builder.equal(root.get("type"), kind));
        }
        return page(matching, page, size, event -> new AuditEventResponse(
                event.getId(),
                event.getType().name(),
                event.getEmail(),
                event.getActor() == null ? null : event.getActor().getEmail(),
                event.getBusiness() == null ? null : event.getBusiness().getName(),
                event.getDetail(),
                event.getIp(),
                DeviceName.of(event.getUserAgent()),
                event.getOccurredAt()));
    }

    private <T> EventPageResponse<T> page(
            Specification<AuditEvent> specification, int page, int size, Function<AuditEvent, T> view) {
        int perPage = Math.min(Math.max(size, 1), MOST_PER_PAGE);
        int number = Pages.number(page, perPage);
        Page<AuditEvent> result = repository.findAll(specification, PageRequest.of(number - 1, perPage, NEWEST_FIRST));
        return new EventPageResponse<>(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                number,
                perPage,
                result.getContent().stream().map(view).toList());
    }

    private static AuditEventType parse(String type) {
        try {
            return AuditEventType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a kind of event from the list");
        }
    }
}
