package com.brokers.channel.core.webhook;

import java.time.Instant;

/** Something that happened on a channel that the platform may need to react to. */
public sealed interface ChannelEvent permits ReservationEvent, UnsupportedEvent {

    /** Identifier the channel assigned to this delivery; stable across retries. */
    String eventId();

    Instant occurredAt();
}
