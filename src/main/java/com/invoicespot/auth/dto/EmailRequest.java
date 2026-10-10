package com.invoicespot.auth.dto;

import jakarta.validation.constraints.Size;

public record EmailRequest(@Size(max = 254, message = "Enter a valid email address") String email) {}
