package com.invoicespot.auth;

import com.invoicespot.auth.dto.ConfirmIdentityRequest;
import com.invoicespot.auth.twofactor.TwoFactorService;
import com.invoicespot.common.ApiException;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Before something that can't be undone, such as deleting an account or a
// business, the person proves again that it is them: with the account's
// password, and a code from their app or a recovery code when two-factor
// sign-in is on. A browser left signed in, or a token that leaked, isn't
// enough. Wrong passwords count toward the same limit as everywhere else.
@Component
public class IdentityCheck {

    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final TwoFactorService twoFactorService;

    public IdentityCheck(PasswordEncoder passwordEncoder, RateLimiter rateLimiter, TwoFactorService twoFactorService) {
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.twoFactorService = twoFactorService;
    }

    public void require(User user, ConfirmIdentityRequest request) {
        // An account made by signing in with Google has no password to confirm.
        if (user.getPassword() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Set a password first, with Forgot password on the sign-in page");
        }
        String password = request == null ? null : request.password();
        if (password == null || password.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your password to confirm");
        }
        String attempts = "password:" + user.getPkid();
        rateLimiter.requireAvailable(RateLimit.WRONG_PASSWORD, attempts);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            rateLimiter.recordFailure(RateLimit.WRONG_PASSWORD, attempts);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your password is wrong");
        }
        rateLimiter.reset(RateLimit.WRONG_PASSWORD, attempts);
        if (user.isTwoFactorEnabled() && twoFactorService.verify(user, request.code()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code didn't work");
        }
    }
}
