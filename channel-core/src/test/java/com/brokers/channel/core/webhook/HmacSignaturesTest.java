package com.brokers.channel.core.webhook;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignaturesTest {

    @Test
    void producesKnownRfc4231Vector() {
        String signature = HmacSignatures.sha256Hex("key",
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));

        assertThat(signature).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void matchesIgnoringCaseAndWhitespace() {
        assertThat(HmacSignatures.matches("abcdef", " ABCDEF ")).isTrue();
        assertThat(HmacSignatures.matches("abcdef", "abcdee")).isFalse();
        assertThat(HmacSignatures.matches("abcdef", null)).isFalse();
    }

    @Test
    void headersAreCaseInsensitive() {
        var request = new WebhookRequest(java.util.Map.of("X-Signature", "abc"), new byte[0]);

        assertThat(request.header("x-signature")).contains("abc");
        assertThat(request.header("X-SIGNATURE")).contains("abc");
    }
}
