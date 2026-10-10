package com.invoicespot.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

// A provider's tokens are only needed to read who signed in, so none are kept.
class DiscardedAuthorizedClients implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {}

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {}
}
