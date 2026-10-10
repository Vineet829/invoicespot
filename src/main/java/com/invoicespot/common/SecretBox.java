package com.invoicespot.common;

import com.invoicespot.config.EncryptionProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Seals secrets with AES-GCM under the app's one encryption key, so a copy of
// the database alone can't use them. Each value keeps its own random nonce in
// front of it.
@Component
public class SecretBox {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretBox.class);

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String UNREADABLE = "A sealed value could not be read";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(EncryptionProperties properties) {
        String encoded = properties.key();
        if (encoded == null || encoded.isBlank()) {
            LOGGER.warn(
                    "APP_ENCRYPTION_KEY isn't set, so two-factor sign-in can't be turned on and share links are shown only once");
            key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(encoded.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be 32 random bytes, base64-encoded");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    public boolean available() {
        return key != null;
    }

    public String seal(byte[] plain) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] sealed = cipher(Cipher.ENCRYPT_MODE, nonce, plain);
        return Base64.getEncoder()
                .encodeToString(ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
    }

    // Anything this key didn't seal, whether changed, cut short or sealed under
    // another key, is refused with an IllegalStateException.
    public byte[] open(String sealed) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(sealed);
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException(UNREADABLE, notBase64);
        }
        if (bytes.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException(UNREADABLE);
        }
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
            throw new IllegalStateException(UNREADABLE, exception);
        }
    }
}
