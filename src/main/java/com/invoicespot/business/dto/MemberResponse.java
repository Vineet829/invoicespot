package com.invoicespot.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.business.BusinessMember;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import java.time.Instant;
import java.util.UUID;

// Someone in the team, as the team page lists them. `you` marks the viewer.
public record MemberResponse(
        @JsonProperty("_id") UUID id,
        String firstName,
        String lastName,
        String email,
        BusinessRole role,
        Instant joinedAt,
        boolean you,
        boolean active) {

    public static MemberResponse from(BusinessMember member, Long viewerPkid) {
        User user = member.getUser();
        return new MemberResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                member.getRole(),
                member.getJoinedAt(),
                user.getPkid().equals(viewerPkid),
                user.isActive());
    }
}
