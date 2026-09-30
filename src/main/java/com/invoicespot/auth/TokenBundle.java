package com.invoicespot.auth;

import com.invoicespot.user.User;

// The refresh token is null when the session kept the one the browser has.
public record TokenBundle(String accessToken, String refreshToken, User user) implements LoginOutcome {}
