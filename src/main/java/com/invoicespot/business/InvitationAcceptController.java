package com.invoicespot.business;

import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.business.dto.BusinessSummaryResponse;
import com.invoicespot.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// Accepting an invitation belongs to the account, not to a business: it adds
// the account to one.
@RestController
public class InvitationAcceptController {

    private final InvitationService invitationService;
    private final BusinessService businessService;
    private final ActiveBusinesses activeBusinesses;

    public InvitationAcceptController(
            InvitationService invitationService, BusinessService businessService, ActiveBusinesses activeBusinesses) {
        this.invitationService = invitationService;
        this.businessService = businessService;
        this.activeBusinesses = activeBusinesses;
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    public BusinessSummaryResponse accept(@AuthenticationPrincipal User principal, @PathVariable String token) {
        BusinessAccess access = invitationService.accept(principal, CurrentSession.id().orElse(null), token);
        return BusinessSummaryResponse.from(
                businessService.require(access.businessPkid()),
                access.role(),
                true,
                activeBusinesses.unread(principal.getPkid(), access.businessPkid()));
    }
}
