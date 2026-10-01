package com.invoicespot.auth;

import com.invoicespot.auth.dto.LoginRequest;
import com.invoicespot.auth.dto.LoginResponse;
import com.invoicespot.auth.dto.LogoutResponse;
import com.invoicespot.auth.dto.TwoFactorChallengeResponse;
import com.invoicespot.auth.dto.TwoFactorSignInRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.metrics.AppMetrics;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimitedException;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
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
    private final RefreshCookies refreshCookies;
    private final AppMetrics metrics;

    public AuthController(
            AuthService authService, RateLimiter rateLimiter, RefreshCookies refreshCookies, AppMetrics metrics) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.refreshCookies = refreshCookies;
        this.metrics = metrics;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletRequest http,
            HttpServletResponse response) {
        // Failures count per email from an address, and for the address as a
        // whole; a success clears only its email's count.
        String address = http.getRemoteAddr();
        String attempt = address + "|"
                + (request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT));
        rateLimiter.requireAvailable(RateLimit.FAILED_SIGN_IN_ADDRESS, address);
        rateLimiter.requireAvailable(RateLimit.FAILED_SIGN_IN, attempt);
        LoginOutcome outcome;
        try {
            outcome = authService.login(request.email(), request.password(), refreshCookie);
        } catch (ApiException exception) {
            rateLimiter.recordFailure(RateLimit.FAILED_SIGN_IN, attempt);
            rateLimiter.recordFailure(RateLimit.FAILED_SIGN_IN_ADDRESS, address);
            metrics.signInFailed(AppMetrics.PASSWORD);
            throw exception;
        }
        rateLimiter.reset(RateLimit.FAILED_SIGN_IN, attempt);
        return switch (outcome) {
            case LoginOutcome.TwoFactorChallenge challenge ->
                    ResponseEntity.ok(new TwoFactorChallengeResponse(true, true, challenge.challenge()));
            case TokenBundle bundle -> {
                metrics.signedIn(AppMetrics.PASSWORD);
                yield signedIn(bundle, response);
            }
        };
    }

    @PostMapping("/login/two-factor")
    public ResponseEntity<Object> completeTwoFactor(
            @Valid @RequestBody TwoFactorSignInRequest request,
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletResponse response) {
        TokenBundle bundle;
        try {
            bundle = authService.completeTwoFactor(request.challenge(), request.code(), refreshCookie);
        } catch (ApiException exception) {
            // A code refused for too many tries counts as a rate-limit
            // rejection instead, as a password does.
            if (!(exception instanceof RateLimitedException)) {
                metrics.signInFailed(AppMetrics.TWO_FACTOR);
            }
            throw exception;
        }
        metrics.signedIn(AppMetrics.TWO_FACTOR);
        return signedIn(bundle, response);
    }

    private ResponseEntity<Object> signedIn(TokenBundle bundle, HttpServletResponse response) {
        refreshCookies.set(response, bundle.refreshToken());
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
            refreshCookies.clear(response);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // A refresh within moments of another keeps the cookie the other set.
        TokenBundle bundle = refreshed.get();
        if (bundle.refreshToken() != null) {
            refreshCookies.set(response, bundle.refreshToken());
        }
        return ResponseEntity.ok(bodyOf(bundle));
    }

    // Only a POST signs out: a link or an image on another site can't.
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(name = "jwt", required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        Optional<String> message = authService.logout(refreshCookie);
        refreshCookies.clear(response);
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
                bundle.accessToken());
    }
}
