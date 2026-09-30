package com.invoicespot.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidDocumentRequest(
        @NotBlank(message = "Say why the document is void")
                @Size(min = 3, max = 500, message = "The reason needs 3 to 500 characters")
                String reason) {}
