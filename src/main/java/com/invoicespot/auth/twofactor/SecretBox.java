package com.invoicespot.auth.twofactor;

import com.invoicespot.config.TwoFactorProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

// Encrypts two-factor secrets with AES-GCM, so a copy of the database can't
// make codes. Each value keeps its own random nonce in front of it.
@Component
class SecretBox {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    SecretBox(TwoFactorProperties properties) {
        String encoded = properties.encryptionKey();
        if (encoded == null || encoded.isBlank()) {
            key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(encoded.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be 32 random bytes, base64-encoded");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    boolean available() {
        return key != null;
    }

    String seal(byte[] plain) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] sealed = cipher(Cipher.ENCRYPT_MODE, nonce, plain);
        return Base64.getEncoder()
                .encodeToString(ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
    }

    byte[] open(String sealed) {
        byte[] bytes = Base64.getDecoder().decode(sealed);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] rest = new byte[buffer.remaining()];
        buffer.get(rest);
        return cipher(Cipher.DECRYPT_MODE, nonce, rest);
    }

    private byte[] cipher(int mode, byte[] nonce, byte[] input) {
        if (key == null) {
            throw new IllegalStateException("No encryption key is set");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("A two-factor secret could not be read", exception);
        }
    }
}
