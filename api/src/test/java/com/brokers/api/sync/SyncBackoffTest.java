package com.brokers.api.sync;

import com.brokers.api.config.BrokersProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SyncBackoffTest {

    private final SyncBackoff backoff = new SyncBackoff(new BrokersProperties(null, null, null,
            new BrokersProperties.Sync(false, Duration.ofSeconds(1), 10, 8,
                    Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofDays(365)),
            null));

    @Test
    void growsExponentiallyWithBoundedJitter() {
        assertThat(backoff.delay(1, null)).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(12));
        assertThat(backoff.delay(2, null)).isBetween(Duration.ofSeconds(20), Duration.ofSeconds(24));
        assertThat(backoff.delay(4, null)).isBetween(Duration.ofSeconds(80), Duration.ofSeconds(96));
    }

    @Test
    void isCappedAtTheMaximum() {
        assertThat(backoff.delay(30, null)).isBetween(Duration.ofMinutes(5), Duration.ofMinutes(6));
    }

    @Test
    void neverRetriesSoonerThanTheChannelAsked() {
        assertThat(backoff.delay(1, Duration.ofMinutes(15))).isEqualTo(Duration.ofMinutes(15));
        assertThat(backoff.delay(1, Duration.ofSeconds(1))).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(12));
    }
}
