package com.invoicespot.business.dto;

import com.invoicespot.business.BusinessRole;
import java.time.Instant;

// What an invitation link shows before anyone signs in: whose business, the
// role, and whether it can still be accepted. Status is PENDING, EXPIRED,
// ACCEPTED or REVOKED.
public record InvitationPreviewResponse(
        boolean success,
        String businessName,
        BusinessRole role,
        String email,
        String invitedBy,
        Instant expiresAt,
        String status) {}
