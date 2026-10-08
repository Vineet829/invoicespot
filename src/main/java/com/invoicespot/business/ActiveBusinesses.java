package com.invoicespot.business;

import com.invoicespot.auth.session.UserSessionRepository;
import com.invoicespot.business.dto.BusinessSummaryResponse;
import com.invoicespot.common.ApiException;
import com.invoicespot.notification.NotificationRepository;
import com.invoicespot.notification.NotificationRepository.UnreadCount;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.BusinessSettingsRepository;
import com.invoicespot.user.User;
import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The business a session works in: the one it last chose, while its user is
// still a member there, else the one the user last worked in. Membership is
// checked on every request, so someone removed from a business loses it at
// once.
@Service
public class ActiveBusinesses {

    private final BusinessMemberRepository memberRepository;
    private final UserSessionRepository sessionRepository;
    private final NotificationRepository notificationRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final Clock clock;

    public ActiveBusinesses(
            BusinessMemberRepository memberRepository,
            UserSessionRepository sessionRepository,
            NotificationRepository notificationRepository,
            BusinessSettingsRepository settingsRepository,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.notificationRepository = notificationRepository;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<BusinessAccess> resolve(User user, UUID sessionId, Long preferred) {
        Optional<BusinessMember> member = Optional.ofNullable(preferred)
                .flatMap(pkid -> memberRepository.findByBusinessPkidAndUserPkid(pkid, user.getPkid()));
        if (member.isEmpty()) {
            member = memberRepository.findByUserInUseOrder(user.getPkid()).stream().findFirst();
            if (member.isPresent() && sessionId != null) {
                sessionRepository.useBusiness(sessionId, member.get().getBusiness().getPkid());
            }
        }
        return member.map(found -> access(found, user));
    }

    // Every business the user works in, by name, marking the session's, with
    // how many of the user's notifications wait unread in each and its time
    // zone, each read for all of them at once.
    @Transactional
    public List<BusinessSummaryResponse> list(User user, UUID sessionId, Long preferred) {
        Long active = resolve(user, sessionId, preferred).map(BusinessAccess::businessPkid).orElse(null);
        Map<Long, Long> unread = notificationRepository.countUnreadByBusiness(user.getPkid()).stream()
                .collect(Collectors.toMap(UnreadCount::getBusinessPkid, UnreadCount::getUnread));
        List<BusinessMember> members = memberRepository.findByUserInUseOrder(user.getPkid());
        Map<Long, String> zones = timeZones(
                members.stream().map(member -> member.getBusiness().getPkid()).toList());
        return members.stream()
                .sorted(Comparator.comparing((BusinessMember member) -> member.getBusiness().getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .map(member -> {
                    Long pkid = member.getBusiness().getPkid();
                    return BusinessSummaryResponse.from(
                            member.getBusiness(),
                            member.getRole(),
                            pkid.equals(active),
                            unread.getOrDefault(pkid, 0L),
                            zones.get(pkid));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public long unread(Long userPkid, Long businessPkid) {
        return notificationRepository.countByUserPkidAndBusinessPkidAndReadAtIsNull(userPkid, businessPkid);
    }

    // The time zone the server counts a business's days in, as the overview
    // and the receivables read it from its settings, or the settings'
    // default while it has none yet.
    @Transactional(readOnly = true)
    public String timeZone(Long businessPkid) {
        return timeZones(List.of(businessPkid)).get(businessPkid);
    }

    private Map<Long, String> timeZones(Collection<Long> businessPkids) {
        Map<Long, String> zones = settingsRepository.findTimeZones(businessPkids).stream()
                .collect(Collectors.toMap(
                        BusinessSettingsRepository.Zone::getBusinessPkid,
                        BusinessSettingsRepository.Zone::getTimeZone));
        businessPkids.forEach(pkid -> zones.putIfAbsent(pkid, BusinessSettings.DEFAULT_TIME_ZONE));
        return zones;
    }

    // Switching business is per session, so two browsers can work in two
    // businesses at once.
    @Transactional
    public BusinessAccess use(User user, UUID sessionId, UUID businessId) {
        BusinessMember member = memberRepository.findByBusinessIdAndUserPkid(businessId, user.getPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You aren't a member of that business"));
        sessionRepository.useBusiness(sessionId, member.getBusiness().getPkid());
        memberRepository.markActive(member.getPkid(), clock.instant());
        return access(member, user);
    }

    static BusinessAccess access(BusinessMember member, User user) {
        Business business = member.getBusiness();
        return new BusinessAccess(business.getPkid(), business.getId(), member.getRole(), user);
    }
}
