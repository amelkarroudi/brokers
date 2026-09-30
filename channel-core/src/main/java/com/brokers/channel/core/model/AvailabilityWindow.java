package com.brokers.channel.core.model;

import java.time.Instant;
import java.util.Objects;

/** A half-open period {@code [from, to)} during which the car cannot be rented. */
public record AvailabilityWindow(Instant from, Instant to) {

    public AvailabilityWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
    }
}
