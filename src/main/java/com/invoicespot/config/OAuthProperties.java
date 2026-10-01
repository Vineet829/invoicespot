package com.invoicespot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Sign-in with another account, such as Google. `redirectBase` is the origin
// the browser reaches the API through, where providers send people back.
@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(String redirectBase, Provider google) {

    public record Provider(String clientId, String clientSecret, Endpoints endpoints) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    // Where a provider is reached, set only to point tests at a stand-in.
    public record Endpoints(
            String authorizationUri, String tokenUri, String userInfoUri, String jwkSetUri, String issuerUri) {}
}
