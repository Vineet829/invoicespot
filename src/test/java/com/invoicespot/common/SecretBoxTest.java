package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.config.EncryptionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretBoxTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("a sealed secret opens again, and sealing twice never gives the same text")
    void roundTrip() {
        SecretBox box = new SecretBox(new EncryptionProperties(KEY));
        byte[] secret = "a secret".getBytes(StandardCharsets.UTF_8);

        String once = box.seal(secret);
        String twice = box.seal(secret);

        assertThat(box.open(once)).isEqualTo(secret);
        assertThat(once).isNotEqualTo(twice);
    }

    @Test
    @DisplayName("a changed value is refused rather than opened")
    void tampered() {
        SecretBox box = new SecretBox(new EncryptionProperties(KEY));
        byte[] sealed = Base64.getDecoder().decode(box.seal(new byte[] {1, 2, 3}));
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> box.open(Base64.getEncoder().encodeToString(sealed)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a value sealed under another key, cut short or not sealed at all is refused the same way")
    void unreadable() {
        SecretBox box = new SecretBox(new EncryptionProperties(KEY));
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 7);
        String elsewhere = new SecretBox(new EncryptionProperties(Base64.getEncoder().encodeToString(otherKey)))
                .seal(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> box.open(elsewhere)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.open(Base64.getEncoder().encodeToString(new byte[20])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.open("not-a-sealed-value")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("without a key the app runs, but nothing can be sealed; a key of the wrong size is refused")
    void keys() {
        assertThat(new SecretBox(new EncryptionProperties("")).available()).isFalse();
        assertThat(new SecretBox(new EncryptionProperties(null)).available()).isFalse();
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new SecretBox(new EncryptionProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class);
    }
}
