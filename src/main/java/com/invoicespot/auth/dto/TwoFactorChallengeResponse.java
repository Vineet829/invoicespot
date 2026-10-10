package com.invoicespot.auth.dto;

public record TwoFactorChallengeResponse(boolean success, boolean twoFactorRequired, String challenge) {}
