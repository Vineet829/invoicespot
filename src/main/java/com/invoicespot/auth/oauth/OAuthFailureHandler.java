package com.invoicespot.auth.oauth;

import com.invoicespot.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

// A provider sign-in that went wrong before anyone could be signed in: they
// cancelled at the provider, the sign-in expired or its cookie didn't verify,
// or something else failed, which is logged. The sign-in page says which.
@Component
public class OAuthFailureHandler implements AuthenticationFailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthFailureHandler.class);

    private final String clientUrl;

    public OAuthFailureHandler(AppProperties properties) {
        this.clientUrl = properties.site().clientUrl();
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String code = exception instanceof OAuth2AuthenticationException oauth
                ? code(oauth.getError().getErrorCode())
                : "failed";
        if (code.equals("failed")) {
            // One line for a callback that was simply wrong, which anyone can
            // send; the stack trace only for what went wrong here.
            if (exception instanceof OAuth2AuthenticationException oauth) {
                OAuth2Error error = oauth.getError();
                LOGGER.warn("A sign-in with a provider failed: {}{}", error.getErrorCode(),
                        error.getDescription() == null ? "" : " (" + error.getDescription() + ")");
            } else {
                LOGGER.warn("A sign-in with a provider failed", exception);
            }
        }
        response.sendRedirect(refused(clientUrl, code, provider(request)));
    }

    static String code(String error) {
        return switch (error) {
            case "access_denied" -> "cancelled";
            case "authorization_request_not_found", "invalid_state_parameter" -> "expired";
            default -> "failed";
        };
    }

    // The sign-in page, saying why, and with which provider when known.
    static String refused(String clientUrl, String code, String provider) {
        return clientUrl + "/login?oauth_error=" + code + (provider.isEmpty() ? "" : "&provider=" + provider);
    }

    // The provider the callback URL names, when this server knows it.
    private static String provider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return SignInProvider.of(uri.substring(uri.lastIndexOf('/') + 1)).map(SignInProvider::id).orElse("");
    }
}
