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
                SignInProvider.GOOGLE,
                "google-1",
                "ada.lovelace@gmail.test",
                true,
                false,
                "Ada",
                "Lovelace",
                "ada.lovelace"));
    }

    @Test
    @DisplayName("Google hosts a Gmail address, and a Workspace account's, which carries the hd claim, and no other")
    void emailHosted() {
        assertThat(hosted("ada@gmail.com", null)).isTrue();
        assertThat(hosted("Ada@GoogleMail.com", null)).isTrue();
        assertThat(hosted("ada@example.test", "example.test")).isTrue();
        // Google's rule is only that hd is set, whatever domain it names.
        assertThat(hosted("ada@sales.example.test", "example.test")).isTrue();
        assertThat(hosted("ada@example.test", null)).isFalse();
        assertThat(hosted("ada@example.test", " ")).isFalse();
        assertThat(hosted("ada@gmail.com.example.test", null)).isFalse();
        assertThat(hosted("ada@notgmail.com", null)).isFalse();
    }

    @Test
    @DisplayName("the hd claim in Google's ID token says the email is hosted")
    void hostedDomainFromIdToken() {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token")
                .subject("google-1")
                .claim("email", "ada@example.test")
                .claim("email_verified", true)
                .claim("hd", "example.test")
                .build();

        assertThat(ProviderProfile.of("google", new DefaultOidcUser(List.of(), token)).emailHosted()).isTrue();
    }

    private static boolean hosted(String email, String hostedDomain) {
        Map<String, Object> claims = claims(true);
        claims.put("email", email);
        if (hostedDomain != null) {
            claims.put("hd", hostedDomain);
        }
        return ProviderProfile.of("google", google(claims)).emailHosted();
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
