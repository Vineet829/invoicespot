package com.invoicespot.auth.twofactor;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.auth.twofactor.dto.CodeRequest;
import com.invoicespot.auth.twofactor.dto.PasswordRequest;
import com.invoicespot.auth.twofactor.dto.RecoveryCodesResponse;
import com.invoicespot.auth.twofactor.dto.TurnOffRequest;
import com.invoicespot.auth.twofactor.dto.TwoFactorSetupResponse;
import com.invoicespot.auth.twofactor.dto.TwoFactorStatusResponse;
import com.invoicespot.common.ApiException;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account/two-factor")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    @GetMapping
    public TwoFactorStatusResponse status(@AuthenticationPrincipal User principal) {
        return twoFactorService.status(principal);
    }

    @PostMapping("/setup")
    public TwoFactorSetupResponse setup(
            @AuthenticationPrincipal User principal, @Valid @RequestBody PasswordRequest request) {
        return twoFactorService.setup(principal, request.password());
    }

    @PostMapping("/enable")
    public RecoveryCodesResponse enable(
            @AuthenticationPrincipal User principal, @Valid @RequestBody CodeRequest request) {
        return new RecoveryCodesResponse(
                true,
                "Two-factor sign-in is on, and your other devices were signed out. Keep your recovery codes"
                        + " somewhere safe",
                twoFactorService.enable(principal, currentSession(), request.code()));
    }

    @PostMapping("/disable")
    public MessageResponse disable(
            @AuthenticationPrincipal User principal, @Valid @RequestBody TurnOffRequest request) {
        twoFactorService.disable(principal, currentSession(), request.password(), request.code());
        return new MessageResponse(true, "Two-factor sign-in is off, and your other devices were signed out");
    }

    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse renewRecoveryCodes(
            @AuthenticationPrincipal User principal, @Valid @RequestBody CodeRequest request) {
        return new RecoveryCodesResponse(
                true,
                "Your new recovery codes are ready. The old ones no longer work",
                twoFactorService.renewRecoveryCodes(principal, request.code()));
    }

    private static UUID currentSession() {
        return CurrentSession.id().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in again"));
    }
}
