package com.invoicespot.auth.oauth;

import com.invoicespot.auth.LoginOutcome;
import com.invoicespot.auth.RefreshCookies;
import com.invoicespot.auth.TokenBundle;
import com.invoicespot.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

// Finishes a sign-in the provider confirmed: a session and on to the app,
// the two-factor step, or back to the sign-in page saying why not. The
// two-factor challenge travels in the fragment, so it never reaches a server
// log or a Referer header. Nothing of the provider's sign-in is kept.
@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthSuccessHandler.class);

    private final OAuthSignIn signIn;
    private final RefreshCookies refreshCookies;
    private final String clientUrl;

    public OAuthSuccessHandler(OAuthSignIn signIn, RefreshCookies refreshCookies, AppProperties properties) {
        this.signIn = signIn;
        this.refreshCookies = refreshCookies;
        this.clientUrl = properties.site().clientUrl();
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        String provider = "";
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken token)) {
                throw new OAuthSignInException("failed");
            }
            provider = token.getAuthorizedClientRegistrationId();
            LoginOutcome outcome = signIn.signIn(
                    ProviderProfile.of(provider, token.getPrincipal()), refreshCookies.read(request).orElse(null));
            switch (outcome) {
                case TokenBundle bundle -> {
                    refreshCookies.set(response, bundle.refreshToken());
                    response.sendRedirect(clientUrl + "/auth/callback");
                }
                case LoginOutcome.TwoFactorChallenge challenge -> response.sendRedirect(clientUrl
                        + "/auth/callback#challenge="
                        + URLEncoder.encode(challenge.challenge(), StandardCharsets.UTF_8));
            }
        } catch (OAuthSignInException exception) {
            response.sendRedirect(refused(exception.code(), provider));
        } catch (RuntimeException exception) {
            // Such as two first sign-ins at once both making the account: the
            // sign-in page rather than a bare error page.
            LOGGER.warn("A sign-in with a provider failed", exception);
            response.sendRedirect(refused("failed", provider));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String refused(String code, String provider) {
        return OAuthFailureHandler.refused(
                clientUrl, code, SignInProvider.of(provider).map(SignInProvider::id).orElse(""));
    }
}
