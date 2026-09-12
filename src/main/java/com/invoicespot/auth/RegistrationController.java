package com.invoicespot.auth;

import com.invoicespot.auth.dto.EmailRequest;
import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.auth.dto.RegisterRequest;
import com.invoicespot.auth.dto.ResetPasswordRequest;
import com.invoicespot.config.AppProperties;
import java.net.URI;
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
    private final String clientUrl;

    public RegistrationController(
            RegistrationService registrationService,
            PasswordResetService passwordResetService,
            AppProperties properties) {
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.clientUrl = properties.site().clientUrl();
    }

    @PostMapping("/register")
    public MessageResponse register(@RequestBody RegisterRequest request) {
        return new MessageResponse(true, registrationService.register(request));
    }

    @GetMapping("/verify/{emailToken}/{userId}")
    public ResponseEntity<Void> verify(
            @PathVariable String emailToken, @PathVariable String userId) {
        registrationService.verify(userId, emailToken);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(clientUrl + "/auth/verify"))
                .build();
    }

    @PostMapping("/resend_email_token")
    public MessageResponse resend(@RequestBody EmailRequest request) {
        return new MessageResponse(true, registrationService.resend(request.email()));
    }

    @PostMapping("/reset_password_request")
    public MessageResponse resetPasswordRequest(@RequestBody EmailRequest request) {
        return new MessageResponse(true, passwordResetService.requestReset(request.email()));
    }

    @PostMapping("/reset_password")
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        return new MessageResponse(true, passwordResetService.resetPassword(request));
    }
}
