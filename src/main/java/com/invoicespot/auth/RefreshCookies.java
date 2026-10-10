package com.invoicespot.auth;

import com.invoicespot.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// The cookie that carries a session's refresh token: set when a sign-in
// starts a session, and cleared when it ends.
@Component
public class RefreshCookies {

    private final AppProperties.Cookie properties;
    private final Duration ttl;

    public RefreshCookies(AppProperties properties) {
        this.properties = properties.cookie();
        this.ttl = properties.jwt().refreshTokenTtl();
    }

    public void set(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(token).maxAge(ttl).build().toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base("").maxAge(0).build().toString());
    }

    // The refresh token the browser sent, if any.
    public Optional<String> read(HttpServletRequest request) {
        return Optional.ofNullable(request.getCookies())
                .flatMap(cookies -> Arrays.stream(cookies)
                        .filter(cookie -> properties.refreshName().equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .filter(value -> !value.isBlank())
                        .findFirst());
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.refreshName(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .path(properties.path())
                .sameSite(properties.sameSite());
    }
}
