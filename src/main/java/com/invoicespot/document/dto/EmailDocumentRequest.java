package com.invoicespot.document.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record EmailDocumentRequest(
        @Email(message = "Enter a valid email address")
                @Size(max = 254, message = "Enter a valid email address")
                String to,
        @Size(max = 200, message = "Keep the subject under 200 characters") String subject,
        @Size(max = 5000, message = "Keep the message under 5,000 characters") String message) {

    public static final EmailDocumentRequest DEFAULTS = new EmailDocumentRequest(null, null, null);
}
