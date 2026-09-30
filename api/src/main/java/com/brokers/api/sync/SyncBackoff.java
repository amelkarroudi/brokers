package com.brokers.api.sync;

import com.brokers.api.config.BrokersProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter, never shorter than the delay a channel asked for with
 * {@code Retry-After}.
 */
@Component
class SyncBackoff {

    private final Duration initial;
    private final Duration max;

    SyncBackoff(BrokersProperties properties) {
        this.initial = properties.sync().initialBackoff();
        this.max = properties.sync().maxBackoff();
    }

    Duration delay(int attempt, Duration retryAfter) {
        int exponent = Math.min(Math.max(attempt - 1, 0), 20);
        Duration exponential = initial.multipliedBy(1L << exponent);
        Duration capped = exponential.compareTo(max) > 0 ? max : exponential;
        long jitterMillis = ThreadLocalRandom.current().nextLong(capped.toMillis() / 5 + 1);
        Duration withJitter = capped.plusMillis(jitterMillis);
        if (retryAfter != null && retryAfter.compareTo(withJitter) > 0) {
            return retryAfter;
        }
        return withJitter;
    }
}
