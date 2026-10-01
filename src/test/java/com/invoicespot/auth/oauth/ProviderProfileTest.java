package com.invoicespot.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

// Who Google says signed in, read from its claims: whether it verified the
// email however it says so, and never anyone without Google's ID for them.
class ProviderProfileTest {

    private static OAuth2User google(Map<String, Object> claims) {
        return new DefaultOAuth2User(List.of(), claims, claims.containsKey("sub") ? "sub" : "email");
    }

    private static Map<String, Object> claims(Object emailVerified) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "google-1");
        claims.put("email", "ada.lovelace@gmail.test");
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        return claims;
    }

    @Test
    @DisplayName("Google's ID token gives the ID, the email and whether it is verified, the name, and a handle")
    void fromIdToken() {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token")
                .subject("google-1")
                .claim("email", "ada.lovelace@gmail.test")
                .claim("email_verified", true)
                .claim("given_name", "Ada")
                .claim("family_name", "Lovelace")
                .build();

        ProviderProfile profile = ProviderProfile.of("google", new DefaultOidcUser(List.of(), token));

        assertThat(profile).isEqualTo(new ProviderProfile(
                SignInProvider.GOOGLE, "google-1", "ada.lovelace@gmail.test", true, "Ada", "Lovelace", "ada.lovelace"));
    }

    @Test
    @DisplayName("email_verified as the text \"true\" counts as verified; \"false\" or none does not")
    void emailVerifiedAsText() {
        assertThat(ProviderProfile.of("google", google(claims("true"))).emailVerified()).isTrue();
        assertThat(ProviderProfile.of("google", google(claims("false"))).emailVerified()).isFalse();
        assertThat(ProviderProfile.of("google", google(claims(null))).emailVerified()).isFalse();
    }

    @Test
    @DisplayName("without Google's ID for them, nobody is signed in")
    void missingSubject() {
        Map<String, Object> claims = claims(true);
        claims.remove("sub");

        assertThatThrownBy(() -> ProviderProfile.of("google", google(claims)))
                .isInstanceOfSatisfying(OAuthSignInException.class,
                        exception -> assertThat(exception.code()).isEqualTo("failed"));
    }
}
