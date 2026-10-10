package com.invoicespot.document.dto;

import jakarta.validation.constraints.Size;

// Why a client declines a quotation, if they want to say.
public record DeclineRequest(@Size(max = 500, message = "The reason can be at most 500 characters") String reason) {}
