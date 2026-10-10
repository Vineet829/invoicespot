package com.invoicespot.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.business.Invitation;
import com.invoicespot.user.User;
import java.time.Instant;
import java.util.UUID;

// An invitation still waiting for an answer, as the team page lists it.
public record InvitationResponse(
        @JsonProperty("_id") UUID id,
        String email,
        BusinessRole role,
        String invitedBy,
        Instant sentAt,
        Instant expiresAt,
        boolean expired) {

    public static InvitationResponse from(Invitation invitation, Instant now) {
        User inviter = invitation.getInvitedBy();
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                inviter == null ? null : (inviter.getFirstName() + " " + inviter.getLastName()).trim(),
                invitation.getSentAt(),
                invitation.getExpiresAt(),
                invitation.isExpiredAt(now));
    }
}
