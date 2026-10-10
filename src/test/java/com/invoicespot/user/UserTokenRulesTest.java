package com.invoicespot.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTokenRulesTest {

    private static final Instant CHANGED = Instant.parse("2026-09-30T10:00:00.700Z");

    private static User user(boolean active, Instant passwordChangedAt) {
        User user = new User();
        user.setActive(active);
        user.setPasswordChangedAt(passwordChangedAt);
        return user;
    }

    @Test
    @DisplayName("an active user without a password change accepts any token")
    void noPasswordChange() {
        assertThat(user(true, null).acceptsTokenIssuedAt(Instant.EPOCH)).isTrue();
    }

    @Test
    @DisplayName("tokens from before the password change's second are refused; that second onwards is fine")
    void passwordChange() {
        User user = user(true, CHANGED);

        assertThat(user.acceptsTokenIssuedAt(Instant.parse("2026-09-30T09:59:59Z"))).isFalse();
        assertThat(user.acceptsTokenIssuedAt(Instant.parse("2026-09-30T10:00:00Z"))).isTrue();
        assertThat(user.acceptsTokenIssuedAt(Instant.parse("2026-09-30T10:00:01Z"))).isTrue();
    }

    @Test
    @DisplayName("a deactivated user accepts no token at all")
    void deactivated() {
        assertThat(user(false, null).acceptsTokenIssuedAt(Instant.now())).isFalse();
    }
}
