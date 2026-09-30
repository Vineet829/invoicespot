package com.invoicespot.settings;

import com.invoicespot.settings.dto.SettingsEnvelope;
import com.invoicespot.settings.dto.UpdateSettingsRequest;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsEnvelope get(@AuthenticationPrincipal User principal) {
        return new SettingsEnvelope(true, null, settingsService.get(principal.getPkid()));
    }

    @PutMapping
    public SettingsEnvelope update(
            @AuthenticationPrincipal User principal, @Valid @RequestBody UpdateSettingsRequest request) {
        return new SettingsEnvelope(
                true, "Your settings were saved", settingsService.update(principal.getPkid(), request));
    }
}
