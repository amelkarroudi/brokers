package com.brokers.channel.core.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 helpers used to sign and verify webhook payloads. */
public final class HmacSignatures {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSignatures() {
    }

    public static String sha256Hex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Compares two signatures in constant time to avoid leaking information through timing. */
    public static boolean matches(String expectedHex, String providedHex) {
        if (expectedHex == null || providedHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHex.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                providedHex.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
