package com.invoicespot.notification;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessMember;
import com.invoicespot.business.BusinessMemberRepository;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.common.ApiException;
import com.invoicespot.document.Document;
import com.invoicespot.notification.dto.NotificationListResponse;
import com.invoicespot.notification.dto.NotificationResponse;
import com.invoicespot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int LATEST = 30;
    private static final int MESSAGE_LENGTH = 300;
    private static final Set<BusinessRole> RUNNING_THE_BUSINESS = EnumSet.of(BusinessRole.OWNER, BusinessRole.ADMIN);

    private final NotificationRepository notificationRepository;
    private final BusinessMemberRepository memberRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository, BusinessMemberRepository memberRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    // News about a document goes to those who run its business, the owner
    // and the admins, and to whoever made the document while they're still a
    // member. Each reads their own copy.
    @Transactional
    public void notify(Document document, NotificationType type, String message) {
        Long businessPkid = document.getBusiness().getPkid();
        Map<Long, User> recipients = new LinkedHashMap<>();
        for (BusinessMember member : memberRepository.findInRoles(businessPkid, RUNNING_THE_BUSINESS)) {
            recipients.put(member.getUser().getPkid(), member.getUser());
        }
        User author = document.getCreatedBy();
        if (author != null
                && memberRepository.findByBusinessPkidAndUserPkid(businessPkid, author.getPkid()).isPresent()) {
            recipients.putIfAbsent(author.getPkid(), author);
        }
        String text = message.length() > MESSAGE_LENGTH ? message.substring(0, MESSAGE_LENGTH) : message;
        Instant now = Instant.now(clock);
        notificationRepository.saveAll(recipients.values().stream()
                .map(recipient -> {
                    Notification notification = new Notification();
                    notification.setUser(recipient);
                    notification.setBusiness(document.getBusiness());
                    notification.setDocument(document);
                    notification.setType(type);
                    notification.setMessage(text);
                    notification.setCreatedAt(now);
                    return notification;
                })
                .toList());
    }

    @Transactional(readOnly = true)
    public NotificationListResponse latest(BusinessAccess access) {
        return new NotificationListResponse(
                true,
                notificationRepository.countByUserPkidAndBusinessPkidAndReadAtIsNull(
                        access.userPkid(), access.businessPkid()),
                notificationRepository
                        .findByUserPkidAndBusinessPkidOrderByCreatedAtDescPkidDesc(
                                access.userPkid(), access.businessPkid(), PageRequest.of(0, LATEST))
                        .stream()
                        .map(NotificationResponse::from)
                        .toList());
    }

    // One notification, or all of them when no id is given.
    @Transactional
    public void markRead(BusinessAccess access, String id) {
        Instant now = Instant.now(clock);
        if (id == null || id.isBlank()) {
            notificationRepository.markAllRead(access.userPkid(), access.businessPkid(), now);
            return;
        }
        UUID externalId;
        try {
            externalId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        Notification notification = notificationRepository
                .findByIdAndUserPkidAndBusinessPkid(externalId, access.userPkid(), access.businessPkid())
                .orElseThrow(NotificationService::notFound);
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
    }
}
