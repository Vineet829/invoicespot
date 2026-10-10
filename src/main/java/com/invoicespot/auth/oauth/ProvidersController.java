package com.invoicespot.auth.oauth;

import com.invoicespot.auth.dto.SignInProvidersResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The providers the sign-in page offers: only those this server is set up for.
@RestController
@RequestMapping("/api/v1/auth/providers")
public class ProvidersController {

    private final ConfiguredProviders providers;

    public ProvidersController(ConfiguredProviders providers) {
        this.providers = providers;
    }

    @GetMapping
    public SignInProvidersResponse providers() {
        return new SignInProvidersResponse(
                true,
                providers.configured().stream()
                        .map(provider -> new SignInProvidersResponse.Provider(provider.id(), provider.label()))
                        .toList());
    }
}
