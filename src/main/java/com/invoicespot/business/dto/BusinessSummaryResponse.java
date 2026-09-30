package com.invoicespot.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.business.Permission;
import java.util.List;
import java.util.UUID;

// A business someone works in, with what their role lets them do there, so
// the app never keeps its own copy of the rules, and how many of their
// notifications there are unread.
public record BusinessSummaryResponse(
        @JsonProperty("_id") UUID id,
        String name,
        String logo,
        BusinessRole role,
        List<Permission> permissions,
        boolean active,
        long unread) {

    public static BusinessSummaryResponse from(Business business, BusinessRole role, boolean active, long unread) {
        return new BusinessSummaryResponse(
                business.getId(),
                business.getName(),
                business.getLogo(),
                role,
                List.copyOf(role.permissions()),
                active,
                unread);
    }
}
