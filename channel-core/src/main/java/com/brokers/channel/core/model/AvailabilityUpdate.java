package com.brokers.channel.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Full availability state of a listing inside {@code [horizonStart, horizonEnd)}.
 *
 * <p>Channels must treat this as a replacement: any blocked period inside the horizon that is
 * not listed here becomes available again.
 */
public record AvailabilityUpdate(Instant horizonStart, Instant horizonEnd, List<AvailabilityWindow> blocked) {

    public AvailabilityUpdate {
        Objects.requireNonNull(horizonStart, "horizonStart");
        Objects.requireNonNull(horizonEnd, "horizonEnd");
        blocked = List.copyOf(blocked);
    }
}
