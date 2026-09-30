package com.invoicespot.business.dto;

import java.util.List;

public record MemberListResponse(boolean success, List<MemberResponse> members) {}
