package com.invoicespot.auth;

import com.invoicespot.auth.dto.LoginRequest;
import com.invoicespot.auth.dto.LoginResponse;
import com.invoicespot.auth.dto.LogoutResponse;
import com.invoicespot.auth.dto.TwoFactorChallengeResponse;
import com.invoicespot.auth.dto.TwoFactorSignInRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final AppProperties.Cookie cookieProperties;
    private final Duration refreshTokenTtl;

    public AuthController(AuthService authService, RateLimiter rateLimiter, AppProperties properties) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.cookieProperties = properties.cookie();
        this.refreshTokenTtl = properties.jwt().refreshTokenTtl();
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletRequest http,
            HttpServletResponse response) {
        String attempt = http.getRemoteAddr() + "|"
                + (request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT));
        rateLimiter.requireAvailable(RateLimit.FAILED_SIGN_IN, attempt);
        LoginOutcome outcome;
        try {
            outcome = authService.login(request.email(), request.password(), refreshCookie);
        } catch (ApiException exception) {
            rateLimiter.recordFailure(RateLimit.FAILED_SIGN_IN, attempt);
            throw exception;
        }
        rateLimiter.reset(RateLimit.FAILED_SIGN_IN, attempt);
        return switch (outcome) {
            case LoginOutcome.TwoFactorChallenge challenge ->
                    ResponseEntity.ok(new TwoFactorChallengeResponse(true, true, challenge.challenge()));
            case TokenBundle bundle -> signedIn(bundle, response);
        };
    }

    @PostMapping("/login/two-factor")
    public ResponseEntity<Object> completeTwoFactor(
            @Valid @RequestBody TwoFactorSignInRequest request,
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletResponse response) {
        return signedIn(authService.completeTwoFactor(request.challenge(), request.code(), refreshCookie), response);
    }

    private ResponseEntity<Object> signedIn(TokenBundle bundle, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(bundle.refreshToken()).toString());
        return ResponseEntity.ok(bodyOf(bundle));
    }

    @GetMapping("/new_access_token")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<TokenBundle> refreshed = authService.refresh(refreshCookie);
        if (refreshed.isEmpty()) {
            response.addHeader(HttpHeaders.SET_COOKIE, clearedCookie().toString());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // A refresh within moments of another keeps the cookie the other set.
        TokenBundle bundle = refreshed.get();
        if (bundle.refreshToken() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(bundle.refreshToken()).toString());
        }
        return ResponseEntity.ok(bodyOf(bundle));
    }

    @GetMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        Optional<String> message = authService.logout(refreshCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, clearedCookie().toString());
        return message.map(text -> ResponseEntity.ok(new LogoutResponse(true, text)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private LoginResponse bodyOf(TokenBundle bundle) {
        User user = bundle.user();
        return new LoginResponse(
                true,
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getProvider(),
                user.getAvatar(),
                bundle.accessToken());
    }

    private ResponseCookie refreshCookie(String value) {
        return baseCookie(value).maxAge(refreshTokenTtl).build();
    }

    private ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieProperties.refreshName(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(cookieProperties.path())
                .sameSite(cookieProperties.sameSite());
    }
}
