package com.invoicespot.business.dto;

import java.util.List;

public record InvitationListResponse(boolean success, List<InvitationResponse> invitations) {}
