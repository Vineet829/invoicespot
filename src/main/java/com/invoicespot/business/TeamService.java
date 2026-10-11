package com.invoicespot.business;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.IdentityCheck;
import com.invoicespot.auth.dto.ConfirmIdentityRequest;
import com.invoicespot.auth.session.UserSessionRepository;
import com.invoicespot.business.dto.MemberResponse;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.notification.NotificationRepository;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// The people who work in a business. Every change locks the business first,
// so two admins changing the team at once take turns, and the business always
// keeps exactly one owner.
@Service
public class TeamService {

    // How the app names each role, in the audit log as on screen.
    static final Map<BusinessRole, String> LABELS = Map.of(
            BusinessRole.OWNER, "Owner",
            BusinessRole.ADMIN, "Admin",
            BusinessRole.MEMBER, "Member",
            BusinessRole.VIEWER, "Viewer");

    private final BusinessRepository businessRepository;
    private final BusinessMemberRepository memberRepository;
    private final UserSessionRepository sessionRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLog auditLog;
    private final DemoAccountGuard demoAccountGuard;
    private final MailService mailService;
    private final AppProperties.Site site;
    private final IdentityCheck identityCheck;

    public TeamService(
            BusinessRepository businessRepository,
            BusinessMemberRepository memberRepository,
            UserSessionRepository sessionRepository,
            NotificationRepository notificationRepository,
            AuditLog auditLog,
            DemoAccountGuard demoAccountGuard,
            MailService mailService,
            AppProperties properties,
            IdentityCheck identityCheck) {
        this.businessRepository = businessRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.notificationRepository = notificationRepository;
        this.auditLog = auditLog;
        this.demoAccountGuard = demoAccountGuard;
        this.mailService = mailService;
        this.site = properties.site();
        this.identityCheck = identityCheck;
    }

    // The owner first, then by role and name.
    @Transactional(readOnly = true)
    public List<MemberResponse> members(BusinessAccess access) {
        return memberRepository.findTeam(access.businessPkid()).stream()
                .sorted(Comparator.comparing(BusinessMember::getRole)
                        .thenComparing(member -> fullName(member.getUser()), String.CASE_INSENSITIVE_ORDER))
                .map(member -> MemberResponse.from(member, access.userPkid()))
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(BusinessAccess access, String userId, BusinessRole role) {
        lock(access);
        BusinessMember member = requireMember(access, userId);
        if (isYou(access, member)) {
            throw new ApiException(HttpStatus.CONFLICT, "You can't change your own role");
        }
        if (member.getRole() == BusinessRole.OWNER) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "The owner's role can't be changed. They can hand the business over instead");
        }
        if (role == BusinessRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hand the business over to make someone its owner");
        }
        if (member.getRole() == role) {
            throw new ApiException(HttpStatus.CONFLICT, "They already have that role");
        }
        String change = LABELS.get(member.getRole()) + " → " + LABELS.get(role);
        member.setRole(role);
        auditLog.recordForBusiness(
                AuditEventType.MEMBER_ROLE_CHANGED, access.businessPkid(), member.getUser(), access.user(), change);
        return MemberResponse.from(member, access.userPkid());
    }

    @Transactional
    public void remove(BusinessAccess access, String userId) {
        lock(access);
        BusinessMember member = requireMember(access, userId);
        if (isYou(access, member)) {
            throw new ApiException(HttpStatus.CONFLICT, "You can't remove yourself. Leave the business instead");
        }
        if (member.getRole() == BusinessRole.OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "The owner can't be removed");
        }
        detach(member);
        auditLog.recordForBusiness(
                AuditEventType.MEMBER_REMOVED, access.businessPkid(), member.getUser(), access.user(), null);
    }

    @Transactional
    public void leave(BusinessAccess access) {
        lock(access);
        BusinessMember member = memberRepository
                .findByBusinessPkidAndUserPkid(access.businessPkid(), access.userPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You aren't a member of that business"));
        if (member.getRole() == BusinessRole.OWNER) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Hand the business over to someone else, or delete it, before you leave");
        }
        detach(member);
        auditLog.recordForBusiness(
                AuditEventType.MEMBER_LEFT, access.businessPkid(), access.user(), access.user(), null);
    }

    // The old owner stays on as an admin. They are demoted before the new
    // owner is promoted, since a business may never have two.
    @Transactional
    public MemberResponse transfer(BusinessAccess access, String userId) {
        Business business = lock(access);
        BusinessMember target = requireMember(access, userId);
        if (isYou(access, target)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already own this business");
        }
        User newOwner = target.getUser();
        if (!newOwner.isActive() || !newOwner.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Only someone with an active, verified account can own a business");
        }
        BusinessMember owner = memberRepository
                .findByBusinessPkidAndUserPkid(access.businessPkid(), access.userPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You aren't a member of that business"));
        owner.setRole(BusinessRole.ADMIN);
        memberRepository.saveAndFlush(owner);
        target.setRole(BusinessRole.OWNER);
        memberRepository.saveAndFlush(target);
        auditLog.recordForBusiness(
                AuditEventType.OWNERSHIP_TRANSFERRED, access.businessPkid(), newOwner, access.user(),
                fullName(access.user()) + " → " + fullName(newOwner));
        String to = newOwner.getEmail();
        String subject = "You now own " + business.getName();
        String body = "Hello " + newOwner.getFirstName() + ",\n\n" + fullName(access.user()) + " handed "
                + business.getName() + " over to you on " + site.name() + ". As its owner, you manage its"
                + " team and can hand it over or delete it.\n\n" + site.clientUrl() + "/settings/team";
        // Sent once the hand-over commits, so no email goes out for one that
        // rolled back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailService.send(to, subject, body);
            }
        });
        return MemberResponse.from(target, access.userPkid());
    }

    // Everything the business keeps goes with it; the event survives, with
    // the business's name. The owner confirms it with their password.
    @Transactional
    public void delete(BusinessAccess access, ConfirmIdentityRequest confirmation) {
        demoAccountGuard.requireNotDemo(access.user(), "deleting the business");
        identityCheck.require(access.user(), confirmation);
        Business business = lock(access);
        auditLog.recordForBusiness(
                AuditEventType.BUSINESS_DELETED, access.businessPkid(), access.user(), access.user(),
                business.getName());
        businessRepository.delete(business);
    }

    private Business lock(BusinessAccess access) {
        return businessRepository
                .findForUpdate(access.businessPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    private BusinessMember requireMember(BusinessAccess access, String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw notAMember();
        }
        return memberRepository
                .findByBusinessPkidAndUserId(access.businessPkid(), id)
                .orElseThrow(TeamService::notAMember);
    }

    // A member who goes loses the business at once: their sessions in it start
    // over elsewhere, and its news about the business goes too.
    private void detach(BusinessMember member) {
        Long businessPkid = member.getBusiness().getPkid();
        Long userPkid = member.getUser().getPkid();
        memberRepository.delete(member);
        notificationRepository.deleteForMember(userPkid, businessPkid);
        sessionRepository.clearBusiness(userPkid, businessPkid);
    }

    private static boolean isYou(BusinessAccess access, BusinessMember member) {
        return member.getUser().getPkid().equals(access.userPkid());
    }

    private static String fullName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }

    private static ApiException notAMember() {
        return new ApiException(HttpStatus.NOT_FOUND, "That person isn't a member of this business");
    }
}
