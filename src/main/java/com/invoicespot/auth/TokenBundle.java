package com.invoicespot.auth;

import com.invoicespot.user.User;

public record TokenBundle(String accessToken, String refreshToken, User user) {}
