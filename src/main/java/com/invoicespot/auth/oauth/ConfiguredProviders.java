package com.invoicespot.auth.oauth;

import com.invoicespot.config.OAuthProperties;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

// The providers this server signs people in with: each whose client ID and
// secret are both set. With none, the sign-in buttons stay hidden and a
// sign-in URL finds nothing.
@Component
public class ConfiguredProviders implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    static final String CALLBACK_PATH = "/api/v1/auth/oauth2/callback/";

    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

    public ConfiguredProviders(OAuthProperties properties) {
        String base = properties.redirectBase() == null ? "" : properties.redirectBase().replaceAll("/+$", "");
        String redirectUri = base + CALLBACK_PATH + "{registrationId}";
        OAuthProperties.Provider google = properties.google();
        if (google != null && google.configured()) {
            registrations.put(
                    SignInProvider.GOOGLE.id(),
                    registration(CommonOAuth2Provider.GOOGLE, SignInProvider.GOOGLE, google, redirectUri));
        }
    }

    private static ClientRegistration registration(
            CommonOAuth2Provider common,
            SignInProvider provider,
            OAuthProperties.Provider settings,
            String redirectUri) {
        ClientRegistration.Builder builder = common.getBuilder(provider.id())
                .clientId(settings.clientId().trim())
                .clientSecret(settings.clientSecret().trim())
                .redirectUri(redirectUri);
        OAuthProperties.Endpoints endpoints = settings.endpoints();
        if (endpoints != null) {
            Optional.ofNullable(endpoints.authorizationUri()).ifPresent(builder::authorizationUri);
            Optional.ofNullable(endpoints.tokenUri()).ifPresent(builder::tokenUri);
            Optional.ofNullable(endpoints.userInfoUri()).ifPresent(builder::userInfoUri);
            Optional.ofNullable(endpoints.jwkSetUri()).ifPresent(builder::jwkSetUri);
            Optional.ofNullable(endpoints.issuerUri()).ifPresent(builder::issuerUri);
        }
        return builder.build();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }

    public List<SignInProvider> configured() {
        return registrations.keySet().stream().map(SignInProvider::of).flatMap(Optional::stream).toList();
    }
}
