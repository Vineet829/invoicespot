package com.invoicespot.auth;

import com.invoicespot.auth.dto.ChangePasswordRequest;
import com.invoicespot.auth.dto.PasswordChangedResponse;
import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.common.ApiException;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account/password")
public class PasswordController {

    private final PasswordChangeService passwordChangeService;

    public PasswordController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @PostMapping
    public PasswordChangedResponse change(
            @AuthenticationPrincipal User principal, @Valid @RequestBody ChangePasswordRequest request) {
        String accessToken = passwordChangeService.change(
                principal,
                CurrentSession.id().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in again")),
                request);
        return new PasswordChangedResponse(
                true, "Your password was changed, and your other devices were signed out", accessToken);
    }
}
