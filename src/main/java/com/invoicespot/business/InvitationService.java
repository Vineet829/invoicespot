package com.invoicespot.business;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.business.dto.InvitationPreviewResponse;
import com.invoicespot.business.dto.InvitationResponse;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Inviting people by email to join a business with a role. Only a token's
// hash is kept; the link in the email carries the token itself.
@Service
public class InvitationService {

    static final Duration LIFETIME = Duration.ofDays(7);
    private static final int MOST_WAITING = 50;
    private static final Map<BusinessRole, String> AS_A = Map.of(
            BusinessRole.ADMIN, "an admin",
            BusinessRole.MEMBER, "a member",
            BusinessRole.VIEWER, "a viewer");

    private final InvitationRepository invitationRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ActiveBusinesses activeBusinesses;
    private final AuditLog auditLog;
    private final DemoAccountGuard demoAccountGuard;
    private final RateLimiter rateLimiter;
    private final MailService mailService;
    private final AppProperties.Site site;
    private final Clock clock;

    public InvitationService(
            InvitationRepository invitationRepository,
            BusinessRepository businessRepository,
            BusinessMemberRepository memberRepository,
            UserRepository userRepository,
            ActiveBusinesses activeBusinesses,
            AuditLog auditLog,
            DemoAccountGuard demoAccountGuard,
            RateLimiter rateLimiter,
            MailService mailService,
            AppProperties properties,
            Clock clock) {
        this.invitationRepository = invitationRepository;
        this.businessRepository = businessRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.activeBusinesses = activeBusinesses;
        this.auditLog = auditLog;
        this.demoAccountGuard = demoAccountGuard;
        this.rateLimiter = rateLimiter;
        this.mailService = mailService;
        this.site = properties.site();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> waiting(BusinessAccess access) {
        Instant now = clock.instant();
        return invitationRepository.findWaiting(access.businessPkid()).stream()
                .map(invitation -> InvitationResponse.from(invitation, now))
                .toList();
    }

    @Transactional
    public InvitationResponse invite(BusinessAccess access, String address, BusinessRole role) {
        demoAccountGuard.requireNotDemo(access.user(), "inviting people");
        String email = address.trim().toLowerCase(Locale.ROOT);
        if (role == BusinessRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invite them first, then hand the business over");
        }
        if (demoAccountGuard.isDemoEmail(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The shared demo account can't be invited");
        }
        Business business = lock(access);
        boolean member = userRepository.findByEmailIgnoreCase(email)
                .flatMap(user -> memberRepository.findByBusinessPkidAndUserPkid(business.getPkid(), user.getPkid()))
                .isPresent();
        if (member) {
            throw new ApiException(HttpStatus.CONFLICT, "They're already part of this business");
        }
        Instant now = clock.instant();
        invitationRepository.findWaitingFor(business.getPkid(), email).ifPresent(waiting -> {
            if (!waiting.isExpiredAt(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "They already have an invitation. Resend it instead");
            }
            waiting.setRevokedAt(now);
            invitationRepository.saveAndFlush(waiting);
        });
        // Only invitations that can still be accepted count: one that has
        // expired is waiting for nothing.
        if (invitationRepository.findWaiting(business.getPkid()).stream()
                        .filter(waiting -> !waiting.isExpiredAt(now))
                        .count()
                >= MOST_WAITING) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A business can have at most " + MOST_WAITING + " invitations waiting");
        }
        rateLimiter.consume(RateLimit.INVITATION, business.getId().toString());
        Invitation invitation = new Invitation();
        invitation.setBusiness(business);
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setInvitedBy(userRepository.getReferenceById(access.userPkid()));
        invitation.setCreatedAt(now);
        String token = issue(invitation, now);
        invitationRepository.save(invitation);
        send(invitation, business, access.user(), token);
        auditLog.recordForBusiness(
                AuditEventType.MEMBER_INVITED, business.getPkid(), null, access.user(), email,
                TeamService.LABELS.get(role));
        return InvitationResponse.from(invitation, now);
    }

    // A new link replaces the old one, which stops working, and the
    // invitation runs for another week.
    @Transactional
    public InvitationResponse resend(BusinessAccess access, String id) {
        Business business = lock(access);
        Invitation invitation = requireWaiting(access, id);
        rateLimiter.consume(RateLimit.INVITATION, business.getId().toString());
        Instant now = clock.instant();
        String token = issue(invitation, now);
        send(invitation, business, access.user(), token);
        auditLog.recordForBusiness(
                AuditEventType.INVITATION_RESENT, business.getPkid(), null, access.user(), invitation.getEmail(),
                null);
        return InvitationResponse.from(invitation, now);
    }

    @Transactional
    public void revoke(BusinessAccess access, String id) {
        lock(access);
        Invitation invitation = requireWaiting(access, id);
        invitation.setRevokedAt(clock.instant());
        auditLog.recordForBusiness(
                AuditEventType.INVITATION_REVOKED, access.businessPkid(), null, access.user(), invitation.getEmail(),
                null);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String token) {
        Invitation invitation = requireToken(token);
        User inviter = invitation.getInvitedBy();
        return new InvitationPreviewResponse(
                true,
                invitation.getBusiness().getName(),
                invitation.getRole(),
                invitation.getEmail(),
                inviter == null ? null : inviter.getFirstName(),
                invitation.getExpiresAt(),
                status(invitation, clock.instant()));
    }

    // Only the account the invitation was sent to accepts it, once its email
    // is verified. The session then works in the business joined.
    @Transactional
    public BusinessAccess accept(User user, UUID sessionId, String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw invitationGone();
        }
        String tokenHash = SecretTokens.hash(token);
        // The business is locked before the invitation, as a resend, a
        // withdrawal or a deletion locks them, so one of those at the same
        // time takes turns with this instead of deadlocking.
        Business business = invitationRepository.findBusinessPkidByTokenHash(tokenHash)
                .flatMap(businessRepository::findForUpdate)
                .orElseThrow(InvitationService::invitationGone);
        Invitation invitation = invitationRepository
                .findForUpdateByTokenHash(tokenHash)
                .orElseThrow(InvitationService::invitationGone);
        Instant now = clock.instant();
        if (!invitation.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "This invitation was already used or withdrawn");
        }
        if (invitation.isExpiredAt(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "This invitation has expired. Ask for a new one");
        }
        demoAccountGuard.requireNotDemo(user, "joining other businesses");
        if (!user.isEmailVerified() || !user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "This invitation is for " + invitation.getEmail() + ". Sign in with that email to accept it");
        }
        if (memberRepository.findByBusinessPkidAndUserPkid(business.getPkid(), user.getPkid()).isEmpty()) {
            BusinessMember member = new BusinessMember();
            member.setBusiness(business);
            member.setUser(userRepository.getReferenceById(user.getPkid()));
            member.setRole(invitation.getRole());
            member.setJoinedAt(now);
            memberRepository.saveAndFlush(member);
        }
        invitation.setAcceptedAt(now);
        invitation.setAcceptedBy(userRepository.getReferenceById(user.getPkid()));
        auditLog.recordForBusiness(
                AuditEventType.MEMBER_JOINED, business.getPkid(), user, user,
                TeamService.LABELS.get(invitation.getRole()));
        return activeBusinesses.use(user, sessionId, business.getId());
    }

    private String issue(Invitation invitation, Instant now) {
        String token = SecretTokens.generate();
        invitation.setTokenHash(SecretTokens.hash(token));
        invitation.setSentAt(now);
        invitation.setExpiresAt(now.plus(LIFETIME));
        return token;
    }

    // Sent once the invitation is saved, so no email goes out for one that
    // wasn't.
    private void send(Invitation invitation, Business business, User inviter, String token) {
        String name = (inviter.getFirstName() + " " + inviter.getLastName()).trim();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mail(invitation, business, name, token);
            }
        });
    }

    private void mail(Invitation invitation, Business business, String name, String token) {
        mailService.send(
                invitation.getEmail(),
                name + " invited you to " + business.getName() + " on " + site.name(),
                "Hello,\n\n" + name + " invited you to join " + business.getName() + " on " + site.name() + " as "
                        + AS_A.get(invitation.getRole()) + ". The invitation is open for 7 days:\n\n"
                        + site.clientUrl() + "/invite/" + token + "\n\nNo account yet? Create one with this email"
                        + " address, " + invitation.getEmail() + ", then open the link again.");
    }

    private Business lock(BusinessAccess access) {
        return businessRepository
                .findForUpdate(access.businessPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    private Invitation requireWaiting(BusinessAccess access, String id) {
        UUID externalId;
        try {
            externalId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw notWaiting();
        }
        return invitationRepository.findInBusiness(access.businessPkid(), externalId)
                .filter(Invitation::isPending)
                .orElseThrow(InvitationService::notWaiting);
    }

    private Invitation requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw invitationGone();
        }
        return invitationRepository.findByTokenHash(SecretTokens.hash(token))
                .orElseThrow(InvitationService::invitationGone);
    }

    private static String status(Invitation invitation, Instant now) {
        if (invitation.getAcceptedAt() != null) {
            return "ACCEPTED";
        }
        if (invitation.getRevokedAt() != null) {
            return "REVOKED";
        }
        return invitation.isExpiredAt(now) ? "EXPIRED" : "PENDING";
    }

    private static ApiException notWaiting() {
        return new ApiException(HttpStatus.NOT_FOUND, "That invitation isn't waiting any more");
    }

    private static ApiException invitationGone() {
        return new ApiException(HttpStatus.NOT_FOUND, "This invitation link doesn't work");
    }
}
