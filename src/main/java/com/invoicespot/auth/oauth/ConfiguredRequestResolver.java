package com.invoicespot.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

// Starts sign-ins, with PKCE, for the providers this server is set up for
// only. Any other is no sign-in at all, so its URL is a 404 like any unknown
// one, rather than the error Spring raises for it.
class ConfiguredRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final ClientRegistrationRepository providers;
    private final DefaultOAuth2AuthorizationRequestResolver resolver;
    private final String basePath;

    ConfiguredRequestResolver(ClientRegistrationRepository providers, String basePath) {
        this.providers = providers;
        this.basePath = basePath + "/";
        this.resolver = new DefaultOAuth2AuthorizationRequestResolver(providers, basePath);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(basePath) && providers.findByRegistrationId(path.substring(basePath.length())) == null) {
            return null;
        }
        return resolver.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return providers.findByRegistrationId(clientRegistrationId) == null
                ? null
                : resolver.resolve(request, clientRegistrationId);
    }
}
