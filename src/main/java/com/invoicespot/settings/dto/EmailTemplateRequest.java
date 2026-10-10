package com.invoicespot.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailTemplateRequest(
        @NotBlank(message = "Write a subject") @Size(max = 200, message = "The subject can be at most 200 characters")
                String subject,
        @NotBlank(message = "Write a message")
                @Size(max = 5000, message = "The message can be at most 5,000 characters")
                String body) {}
