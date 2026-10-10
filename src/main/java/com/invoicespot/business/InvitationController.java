package com.invoicespot.business;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.dto.InvitationListResponse;
import com.invoicespot.business.dto.InvitationResponse;
import com.invoicespot.business.dto.InviteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// The invitations the business has sent that are still waiting.
@RestController
@RequestMapping("/api/v1/business/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public InvitationListResponse waiting(@Requires(Permission.MANAGE_TEAM) BusinessAccess access) {
        return new InvitationListResponse(true, invitationService.waiting(access));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(
            @Requires(Permission.MANAGE_TEAM) BusinessAccess access, @Valid @RequestBody InviteRequest request) {
        return invitationService.invite(access, request.email(), request.role());
    }

    @PostMapping("/{id}/resend")
    public InvitationResponse resend(@Requires(Permission.MANAGE_TEAM) BusinessAccess access, @PathVariable String id) {
        return invitationService.resend(access, id);
    }

    @DeleteMapping("/{id}")
    public MessageResponse revoke(@Requires(Permission.MANAGE_TEAM) BusinessAccess access, @PathVariable String id) {
        invitationService.revoke(access, id);
        return new MessageResponse(true, "The invitation was withdrawn");
    }
}
