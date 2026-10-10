package com.invoicespot.auth.oauth;

import com.invoicespot.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

// Sign-in with a provider: it starts at /api/v1/auth/oauth2/authorization/
// {provider} and comes back to the callback ConfiguredProviders names. The
// request on its way is a cookie, PKCE guards the code, and the provider's
// tokens are dropped once the person is known.
@Configuration
public class OAuthSignInConfig {

    static final String AUTHORIZATION_PATH = "/api/v1/auth/oauth2/authorization";

    @Bean
    Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauthSignIn(
            ConfiguredProviders providers,
            CookieAuthorizationRequests requests,
            OAuthSuccessHandler successHandler,
            OAuthFailureHandler failureHandler,
            AppProperties properties) {
        // The app's own sign-in page, so Spring makes none of its own.
        return login -> login.loginPage(properties.site().clientUrl() + "/login")
                .clientRegistrationRepository(providers)
                .authorizedClientRepository(new DiscardedAuthorizedClients())
                .authorizationEndpoint(endpoint -> endpoint.baseUri(AUTHORIZATION_PATH)
                        .authorizationRequestRepository(requests)
                        .authorizationRequestResolver(new ConfiguredRequestResolver(providers, AUTHORIZATION_PATH)))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(ConfiguredProviders.CALLBACK_PATH + "*"))
                .successHandler(successHandler)
                .failureHandler(failureHandler);
    }
}
