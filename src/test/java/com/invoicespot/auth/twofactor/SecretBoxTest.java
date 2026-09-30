package com.invoicespot.auth.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.config.TwoFactorProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretBoxTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("a sealed secret opens again, and sealing twice never gives the same text")
    void roundTrip() {
        SecretBox box = new SecretBox(new TwoFactorProperties(KEY));
        byte[] secret = "a secret".getBytes(StandardCharsets.UTF_8);

        String once = box.seal(secret);
        String twice = box.seal(secret);

        assertThat(box.open(once)).isEqualTo(secret);
        assertThat(once).isNotEqualTo(twice);
    }

    @Test
    @DisplayName("a changed value is refused rather than opened")
    void tampered() {
        SecretBox box = new SecretBox(new TwoFactorProperties(KEY));
        byte[] sealed = Base64.getDecoder().decode(box.seal(new byte[] {1, 2, 3}));
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> box.open(Base64.getEncoder().encodeToString(sealed)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("without a key the app runs, but nothing can be sealed; a key of the wrong size is refused")
    void keys() {
        assertThat(new SecretBox(new TwoFactorProperties("")).available()).isFalse();
        assertThat(new SecretBox(new TwoFactorProperties(null)).available()).isFalse();
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new SecretBox(new TwoFactorProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class);
    }
}
