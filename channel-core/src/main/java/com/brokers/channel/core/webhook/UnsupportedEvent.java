package com.brokers.channel.core.webhook;

import java.time.Instant;

/** An event type the platform does not act on. It is acknowledged and recorded, never retried. */
public record UnsupportedEvent(String eventId, Instant occurredAt, String type) implements ChannelEvent {
}
