package com.brokers.channel.core.webhook;

import com.brokers.channel.core.model.Money;

import java.time.Instant;

/**
 * A reservation was created, changed or cancelled on a channel.
 *
 * @param externalListingId the channel's identifier of the booked listing
 */
public record ReservationEvent(
        String eventId,
        Instant occurredAt,
        ReservationEventType type,
        String externalReservationId,
        String externalListingId,
        Instant pickupAt,
        Instant returnAt,
        String customerName,
        String customerEmail,
        Money totalPrice) implements ChannelEvent {
}
