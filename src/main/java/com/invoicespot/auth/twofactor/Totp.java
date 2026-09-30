package com.invoicespot.auth.twofactor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Time-based one-time codes (RFC 6238): HMAC-SHA1 over 30-second steps, six
// digits, as authenticator apps make them. A code from the step before or
// after is accepted too, for clocks that drift.
final class Totp {

    static final int SECRET_BYTES = 20;
    private static final long STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    static byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), STEP_SECONDS);
    }

    static String code(byte[] secret, long step) {
        return code(secret, step, 6);
    }

    static String code(byte[] secret, long step, int digits) {
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", exception);
        }
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format("%0" + digits + "d", binary % (int) Math.pow(10, digits));
    }

    // The step a code belongs to, near now, compared in constant time.
    static OptionalLong match(byte[] secret, String code, Instant now) {
        long current = step(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (MessageDigest.isEqual(
                    code(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    static String base32(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                text.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            text.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return text.toString();
    }
}
