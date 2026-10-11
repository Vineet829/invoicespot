package com.invoicespot.auth;

import com.invoicespot.auth.dto.EmailRequest;
import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.auth.dto.RegisterRequest;
import com.invoicespot.auth.dto.ResetPasswordRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;
    private final String clientUrl;

    public RegistrationController(
            RegistrationService registrationService,
            PasswordResetService passwordResetService,
            RateLimiter rateLimiter,
            AppProperties properties) {
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
        this.clientUrl = properties.site().clientUrl();
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.SIGN_UP, http.getRemoteAddr());
        return new MessageResponse(true, registrationService.register(request));
    }

    // The emailed link opens in a browser, so whatever comes of it is shown
    // on a page of the app rather than as the API's answer.
    @GetMapping("/verify/{emailToken}/{userId}")
    public ResponseEntity<Void> verify(
            @PathVariable String emailToken, @PathVariable String userId) {
        String page = clientUrl + "/auth/verify";
        try {
            registrationService.verify(userId, emailToken);
        } catch (ApiException failed) {
            page += failed.getStatus() == HttpStatus.CONFLICT ? "?already=1" : "?expired=1";
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(page)).build();
    }

    @PostMapping("/resend_email_token")
    public MessageResponse resend(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.VERIFICATION_EMAIL, http.getRemoteAddr());
        limitEmailsTo(request.email());
        return new MessageResponse(true, registrationService.resend(request.email()));
    }

    @PostMapping("/reset_password_request")
    public MessageResponse resetPasswordRequest(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.PASSWORD_RESET, http.getRemoteAddr());
        limitEmailsTo(request.email());
        return new MessageResponse(true, passwordResetService.requestReset(request.email()));
    }

    // Counted whether or not the address has an account, so being refused
    // says nothing about it.
    private void limitEmailsTo(String email) {
        if (email != null && !email.isBlank()) {
            rateLimiter.consume(RateLimit.ACCOUNT_EMAIL_TO, email.trim().toLowerCase(Locale.ROOT));
        }
    }

    @PostMapping("/reset_password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return new MessageResponse(true, passwordResetService.resetPassword(request));
    }
}
