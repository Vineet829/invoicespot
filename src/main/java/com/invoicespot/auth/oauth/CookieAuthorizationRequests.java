package com.invoicespot.auth.oauth;

import com.invoicespot.auth.JwtService;
import com.invoicespot.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

// A sign-in on its way to a provider, kept in the browser rather than in a
// server session, so the server stays stateless: a cookie holding a signed
// token for ten minutes, sent back only to the sign-in URLs. Lax, so it
// comes back on the provider's redirect. Nothing in it is deserialized as
// Java; a cookie that doesn't verify is no sign-in at all.
@Component
public class CookieAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "oauth2_request";
    static final String PATH = "/api/v1/auth/oauth2";
    static final Duration TTL = Duration.ofMinutes(10);

    private final JwtService jwtService;
    private final boolean secure;

    public CookieAuthorizationRequests(JwtService jwtService, AppProperties properties) {
        this.jwtService = jwtService;
        this.secure = properties.cookie().secure();
    }

    // Only the sign-in the callback's state names, as Spring's session store
    // does: a callback left over from an earlier start, in another tab say,
    // finds nothing.
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        return Optional.ofNullable(request.getCookies())
                .flatMap(cookies -> Arrays.stream(cookies)
                        .filter(cookie -> COOKIE.equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst())
                .flatMap(jwtService::readSignedRequest)
                .flatMap(CookieAuthorizationRequests::request)
                .filter(stored -> state.equals(stored.getState()))
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            clear(response);
            return;
        }
        String token = jwtService.issueSignedRequest(claims(authorizationRequest), TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, TTL).toString());
    }

    // A stale callback leaves the cookie alone, so the newer sign-in it holds
    // can still finish.
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest found = loadAuthorizationRequest(request);
        if (found != null) {
            clear(response);
        }
        return found;
    }

    static Map<String, Object> claims(OAuth2AuthorizationRequest request) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("authorizationUri", request.getAuthorizationUri());
        claims.put("clientId", request.getClientId());
        claims.put("redirectUri", request.getRedirectUri());
        claims.put("scopes", Set.copyOf(request.getScopes()));
        claims.put("state", request.getState());
        claims.put("additionalParameters", texts(request.getAdditionalParameters()));
        claims.put("attributes", texts(request.getAttributes()));
        claims.put("authorizationRequestUri", request.getAuthorizationRequestUri());
        return claims;
    }

    static Optional<OAuth2AuthorizationRequest> request(Map<String, Object> claims) {
        try {
            return Optional.of(OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(text(claims.get("authorizationUri")))
                    .clientId(text(claims.get("clientId")))
                    .redirectUri(text(claims.get("redirectUri")))
                    .scopes(scopes(claims.get("scopes")))
                    .state(text(claims.get("state")))
                    .additionalParameters(texts(claims.get("additionalParameters")))
                    .attributes(texts(claims.get("attributes")))
                    .authorizationRequestUri(text(claims.get("authorizationRequestUri")))
                    .build());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    private void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Set<String> scopes(Object value) {
        Set<String> scopes = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(scope -> scopes.add(scope.toString()));
        }
        return scopes;
    }

    // Every value as text: what Spring puts here, a nonce, a code verifier and
    // the like, is text already.
    private static Map<String, Object> texts(Object value) {
        Map<String, Object> texts = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> {
                if (key != null && entry != null) {
                    texts.put(key.toString(), entry.toString());
                }
            });
        }
        return texts;
    }
}
