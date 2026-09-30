package com.brokers.api.security;

import com.brokers.api.config.BrokersProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CredentialCipher cipher = new CredentialCipher(properties(KEY));

    @Test
    void roundTripsAndRandomizesCiphertext() {
        String first = cipher.encrypt("s3cret");
        String second = cipher.encrypt("s3cret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("s3cret");
        assertThat(cipher.decrypt(second)).isEqualTo("s3cret");
    }

    @Test
    void detectsTampering() {
        String encrypted = cipher.encrypt("s3cret");
        byte[] raw = Base64.getDecoder().decode(encrypted.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysThatAreNot256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CredentialCipher(properties(shortKey))).isInstanceOf(IllegalStateException.class);
    }

    private static BrokersProperties properties(String key) {
        return new BrokersProperties(null,
                new BrokersProperties.Security(key, null, 5, null, java.util.List.of()), null, null, null);
    }
}
