package com.brokers.api.security;

import com.brokers.api.config.BrokersProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts channel secrets at rest with AES-256-GCM. The {@code v1:} prefix identifies the key
 * version so keys can be rotated later without ambiguity.
 */
@Component
public class CredentialCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(BrokersProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.security().credentialKey());
        if (raw.length != 32) {
            throw new IllegalStateException("brokers.security.credential-key must be a base64 encoded 256-bit key");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt credential", e);
        }
    }

    public String decrypt(String encrypted) {
        if (!encrypted.startsWith(PREFIX)) {
            throw new IllegalStateException("Unknown credential encryption format");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, combined, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt credential", e);
        }
    }
}
