package com.invoicespot.user;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.business.BusinessService;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.SearchText;
import com.invoicespot.user.dto.AdminStatsResponse;
import com.invoicespot.user.dto.UpdateProfileRequest;
import com.invoicespot.user.dto.UserPageResponse;
import com.invoicespot.user.dto.UserProfileResponse;
import com.invoicespot.user.dto.UserSummaryResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MOST_PER_PAGE = 100;
    private static final String ADMIN = "Admin";

    private final UserRepository userRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final SessionService sessionService;
    private final AuditLog auditLog;
    private final AdminStats adminStats;
    private final BusinessService businessService;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            DemoAccountGuard demoAccountGuard,
            SessionService sessionService,
            AuditLog auditLog,
            AdminStats adminStats,
            BusinessService businessService,
            Clock clock) {
        this.userRepository = userRepository;
        this.demoAccountGuard = demoAccountGuard;
        this.sessionService = sessionService;
        this.auditLog = auditLog;
        this.adminStats = adminStats;
        this.businessService = businessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long pkid) {
        User user = require(pkid);
        return profileOf(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long pkid, UpdateProfileRequest request) {
        if (request.password() != null || request.passwordConfirm() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "This route is not for password updates. Please use the password reset"
                            + " functionality instead");
        }
        if (request.email() != null
                || request.isEmailVerified() != null
                || request.provider() != null
                || request.roles() != null
                || request.googleId() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "You are not allowed to update that field on this route");
        }

        User user = require(pkid);
        if (request.username() != null) {
            user.setUsername(request.username());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        userRepository.save(user);
        return profileOf(user);
    }

    private UserProfileResponse profileOf(User user) {
        return UserProfileResponse.from(
                user, demoAccountGuard.isDemo(user), businessService.sharedOwnedBy(user.getPkid()));
    }

    @Transactional
    public void deleteOwnAccount(Long pkid) {
        User user = require(pkid);
        demoAccountGuard.requireNotDemo(user, "deleting it");
        requireNoSharedBusinesses(user, "You own ", "Hand it over or remove them first");
        auditLog.record(AuditEventType.ACCOUNT_DELETED, user);
        businessService.deleteOwnedBy(user);
        userRepository.delete(user);
    }

    // Newest accounts first, searched by name, email or username.
    @Transactional(readOnly = true)
    public UserPageResponse listUsers(int pageNumber, int size, String query) {
        int page = Math.max(pageNumber, 1);
        Specification<User> matching = null;
        if (query != null && !query.isBlank()) {
            String pattern = SearchText.containing(query);
            matching = (root, search, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), pattern, SearchText.ESCAPE),
                    builder.like(builder.lower(root.get("username")), pattern, SearchText.ESCAPE),
                    builder.like(
                            builder.lower(builder.concat(
                                    builder.concat(root.get("firstName"), " "), root.get("lastName"))),
                            pattern,
                            SearchText.ESCAPE));
        }
        Page<User> result = userRepository.findAll(
                matching,
                PageRequest.of(
                        page - 1,
                        Math.min(Math.max(size, 1), MOST_PER_PAGE),
                        Sort.by(Sort.Direction.DESC, "createdAt", "pkid")));
        return new UserPageResponse(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream()
                        .map(user -> UserSummaryResponse.from(user, demoAccountGuard.isDemo(user)))
                        .toList());
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        return adminStats.since(clock.instant().minus(Duration.ofDays(30)));
    }

    @Transactional
    public String deleteUser(String id, User admin) {
        User user = externalId(id)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        demoAccountGuard.requireNotDemoTarget(user, "deleted");
        requireSomeoneElse(user, admin, "Delete your own account from your profile");
        requireNotLastAdmin(user, "deleted");
        requireNoSharedBusinesses(user, "This account owns ", "They must hand it over or remove the others first");
        String firstName = user.getFirstName();
        auditLog.record(AuditEventType.USER_DELETED, user, admin);
        businessService.deleteOwnedBy(user);
        userRepository.delete(user);
        return firstName;
    }

    @Transactional
    public UserSummaryResponse deactivateUser(String id, User admin) {
        User user = externalId(id)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user was not found"));
        demoAccountGuard.requireNotDemoTarget(user, "deactivated");
        requireSomeoneElse(user, admin, "You can't deactivate your own account");
        requireNotLastAdmin(user, "deactivated");
        requireNoSharedBusinesses(user, "This account owns ", "They must hand it over or remove the others first");
        user.setActive(false);
        userRepository.save(user);
        sessionService.revokeAll(user);
        auditLog.record(AuditEventType.USER_DEACTIVATED, user, admin);
        return UserSummaryResponse.from(user, false);
    }

    @Transactional
    public UserSummaryResponse reactivateUser(String id, User admin) {
        User user = requireExternal(id);
        if (user.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "This account is already active");
        }
        user.setActive(true);
        auditLog.record(AuditEventType.USER_REACTIVATED, user, admin);
        return UserSummaryResponse.from(user, demoAccountGuard.isDemo(user));
    }

    // Admins can see and change every account, so the shared demo account is
    // never one, and there is always at least one.
    @Transactional
    public UserSummaryResponse setAdmin(String id, User admin, boolean makeAdmin) {
        User user = requireExternal(id);
        if (makeAdmin) {
            demoAccountGuard.requireNotDemoTarget(user, "made an admin");
            if (!user.isActive()) {
                throw new ApiException(HttpStatus.CONFLICT, "Reactivate this account before making it an admin");
            }
            if (!user.getRoles().add(ADMIN)) {
                throw new ApiException(HttpStatus.CONFLICT, "This account is already an admin");
            }
            auditLog.record(AuditEventType.ADMIN_GRANTED, user, admin);
        } else {
            requireSomeoneElse(user, admin, "You can't remove your own admin role");
            requireNotLastAdmin(user, "made a regular user");
            if (!user.getRoles().remove(ADMIN)) {
                throw new ApiException(HttpStatus.CONFLICT, "This account isn't an admin");
            }
            auditLog.record(AuditEventType.ADMIN_REVOKED, user, admin);
        }
        return UserSummaryResponse.from(user, demoAccountGuard.isDemo(user));
    }

    // An account can't go while it owns a business that others work in; the
    // business would be left without an owner.
    private void requireNoSharedBusinesses(User user, String opening, String closing) {
        List<String> shared = businessService.sharedOwnedBy(user.getPkid());
        if (!shared.isEmpty()) {
            String names = shared.size() == 1
                    ? shared.get(0)
                    : String.join(", ", shared.subList(0, shared.size() - 1)) + " and " + shared.getLast();
            throw new ApiException(
                    HttpStatus.CONFLICT, opening + names + ", which others work in. " + closing);
        }
    }

    private static void requireSomeoneElse(User target, User admin, String message) {
        if (target.getPkid().equals(admin.getPkid())) {
            throw new ApiException(HttpStatus.CONFLICT, message);
        }
    }

    private void requireNotLastAdmin(User target, String action) {
        if (target.isActive()
                && target.getRoles().contains(ADMIN)
                && userRepository.lockActiveAdmins().size() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "The last admin can't be " + action);
        }
    }

    private User requireExternal(String id) {
        return externalId(id)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private User require(Long pkid) {
        return userRepository
                .findById(pkid)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "That user does not exist in our system"));
    }

    private static Optional<UUID> externalId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
