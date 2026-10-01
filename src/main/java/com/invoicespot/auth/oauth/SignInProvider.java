package com.invoicespot.auth.oauth;

import java.util.Arrays;
import java.util.Optional;

// The accounts people can sign in with besides a password. The id is the one
// in the sign-in and callback URLs.
public enum SignInProvider {
    GOOGLE("google", "Google");

    private final String id;
    private final String label;

    SignInProvider(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<SignInProvider> of(String id) {
        return Arrays.stream(values()).filter(provider -> provider.id.equals(id)).findFirst();
    }
}
