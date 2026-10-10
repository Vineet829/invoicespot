package com.invoicespot.auth.session.dto;

import java.util.List;

public record SessionListResponse(boolean success, List<SessionResponse> sessions) {}
