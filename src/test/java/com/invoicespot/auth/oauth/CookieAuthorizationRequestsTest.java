package com.invoicespot.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.auth.JwtService;
import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

// The sign-in on its way to a provider comes back from its cookie exactly as
// it went out, and nothing else passes for it.
class CookieAuthorizationRequestsTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final String SECRET = "test-access-secret-test-access-secret-0123456789";

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Jwt(SECRET, Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site(
                        "Invoice Spot", "http://localhost:3000", "http://localhost:8080", "support@invoicespot.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload("uploads"));
    }

    private static JwtService jwtAt(Instant now) {
        return new JwtService(properties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static CookieAuthorizationRequests requestsAt(Instant now) {
        return new CookieAuthorizationRequests(jwtAt(now), properties());
    }

    private static OAuth2AuthorizationRequest request() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.example/authorize")
                .clientId("client-1")
                .redirectUri("http://localhost:3000/api/v1/auth/oauth2/callback/google")
                .scopes(Set.of("openid", "email"))
                .state("state-1")
                .additionalParameters(Map.of("nonce", "nonce-hash", "code_challenge", "challenge-1"))
                .attributes(Map.of("registration_id", "google", "nonce", "nonce-1", "code_verifier", "verifier-1"))
                .authorizationRequestUri("https://accounts.example/authorize?state=state-1")
                .build();
    }

    // A callback with the cookie, carrying the state the request went out with.
    private static MockHttpServletRequest withCookie(String value) {
        return withCookie(value, "state-1");
    }

    private static MockHttpServletRequest withCookie(String value, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieAuthorizationRequests.COOKIE, value));
        if (state != null) {
            request.setParameter("state", state);
        }
        return request;
    }

    // The token with one character in the middle of its signature changed,
    // so its claims still read and only checking the signature refuses it.
    static String tampered(String token) {
        int signature = token.lastIndexOf('.') + 1;
        int at = signature + (token.length() - signature) / 2;
        return token.substring(0, at) + (token.charAt(at) == 'A' ? 'B' : 'A') + token.substring(at + 1);
    }

    private static String saved(CookieAuthorizationRequests requests) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        requests.saveAuthorizationRequest(request(), new MockHttpServletRequest(), response);
        return response.getCookie(CookieAuthorizationRequests.COOKIE).getValue();
    }

    @Test
    @DisplayName("every part of the request comes back to a callback with its state, from a cookie for sign-in URLs")
    void roundTrips() {
        CookieAuthorizationRequests requests = requestsAt(NOW);
        MockHttpServletResponse response = new MockHttpServletResponse();
        requests.saveAuthorizationRequest(request(), new MockHttpServletRequest(), response);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/v1/auth/oauth2", "Max-Age=600", "Secure", "HttpOnly", "SameSite=Lax");

        OAuth2AuthorizationRequest sent = request();
        OAuth2AuthorizationRequest loaded = requests.loadAuthorizationRequest(
                withCookie(response.getCookie(CookieAuthorizationRequests.COOKIE).getValue(), sent.getState()));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAuthorizationUri()).isEqualTo(sent.getAuthorizationUri());
        assertThat(loaded.getClientId()).isEqualTo(sent.getClientId());
        assertThat(loaded.getRedirectUri()).isEqualTo(sent.getRedirectUri());
        assertThat(loaded.getScopes()).isEqualTo(sent.getScopes());
        assertThat(loaded.getState()).isEqualTo(sent.getState());
        assertThat(loaded.getAdditionalParameters()).isEqualTo(sent.getAdditionalParameters());
        assertThat(loaded.getAttributes()).isEqualTo(sent.getAttributes());
        assertThat(loaded.getAuthorizationRequestUri()).isEqualTo(sent.getAuthorizationRequestUri());
    }

    @Test
    @DisplayName("an altered, expired or other kind of token is no request, and removing one clears the cookie")
    void refusesOthers() {
        String cookie = saved(requestsAt(NOW));
        JwtService jwt = jwtAt(NOW);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRoles(Set.of("User"));

        assertThat(requestsAt(NOW).loadAuthorizationRequest(withCookie(tampered(cookie)))).isNull();
        assertThat(requestsAt(NOW.plus(Duration.ofMinutes(11))).loadAuthorizationRequest(withCookie(cookie))).isNull();
        assertThat(requestsAt(NOW).loadAuthorizationRequest(
                        withCookie(jwt.issueAccessToken(user, UUID.randomUUID()))))
                .isNull();
        assertThat(requestsAt(NOW).loadAuthorizationRequest(withCookie(jwt.issueTwoFactorChallenge(user, null))))
                .isNull();
        assertThat(requestsAt(NOW).loadAuthorizationRequest(new MockHttpServletRequest())).isNull();

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(requestsAt(NOW).removeAuthorizationRequest(withCookie(cookie), response)).isNotNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("a callback with another state, or none, finds no request and leaves the cookie alone")
    void otherState() {
        String cookie = saved(requestsAt(NOW));
        CookieAuthorizationRequests requests = requestsAt(NOW);

        for (String state : new String[] {"state-0", null}) {
            assertThat(requests.loadAuthorizationRequest(withCookie(cookie, state))).isNull();
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(requests.removeAuthorizationRequest(withCookie(cookie, state), response)).isNull();
            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        }
    }
}
