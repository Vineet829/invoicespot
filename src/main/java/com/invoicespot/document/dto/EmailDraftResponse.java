package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record EmailDraftResponse(
        boolean success,
        String to,
        String subject,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String note) {}
