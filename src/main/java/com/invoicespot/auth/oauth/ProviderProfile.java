package com.invoicespot.auth.oauth;

import java.util.Locale;
import org.springframework.security.oauth2.core.user.OAuth2User;

// Who signed in at a provider: its lasting ID for them, their email, whether
// the provider verified it and whether it hosts it, their name, and a handle
// to make a username from.
public record ProviderProfile(
        SignInProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        boolean emailHosted,
        String firstName,
        String lastName,
        String handle) {

    static ProviderProfile of(String registrationId, OAuth2User user) {
        SignInProvider provider =
                SignInProvider.of(registrationId).orElseThrow(() -> new OAuthSignInException("failed"));
        return switch (provider) {
            case GOOGLE -> google(user);
        };
    }

    // Google's ID token says whether it verified the email, as true or "true".
    // Google hosts a Gmail address, and a Google Workspace account's, which
    // carries the hd claim. Any other address it checked only when the Google
    // account was made.
    private static ProviderProfile google(OAuth2User user) {
        String email = text(user.getAttribute("email"));
        Object verified = user.getAttribute("email_verified");
        return new ProviderProfile(
                SignInProvider.GOOGLE,
                required(text(user.getAttribute("sub"))),
                email,
                Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified)),
                email != null && (text(user.getAttribute("hd")) != null || gmail(email)),
                text(user.getAttribute("given_name")),
                text(user.getAttribute("family_name")),
                email == null ? null : email.substring(0, Math.max(email.indexOf('@'), 0)));
    }

    private static boolean gmail(String email) {
        String address = email.toLowerCase(Locale.ROOT);
        return address.endsWith("@gmail.com") || address.endsWith("@googlemail.com");
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static String required(String subject) {
        if (subject == null) {
            throw new OAuthSignInException("failed");
        }
        return subject;
    }
}
