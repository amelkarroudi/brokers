package com.brokers.channel.booking.webhook;

import com.brokers.channel.core.Channel;
import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.webhook.ChannelEvent;
import com.brokers.channel.core.webhook.ChannelWebhookHandler;
import com.brokers.channel.core.webhook.HmacSignatures;
import com.brokers.channel.core.webhook.ReservationEvent;
import com.brokers.channel.core.webhook.ReservationEventType;
import com.brokers.channel.core.webhook.UnsupportedEvent;
import com.brokers.channel.core.webhook.WebhookParseException;
import com.brokers.channel.core.webhook.WebhookRequest;
import com.brokers.channel.core.webhook.WebhookVerificationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Booking.com signs each delivery with {@code X-Booking-Signature: sha256=<hex>}, an HMAC-SHA256
 * of the raw body keyed with the webhook secret configured in the extranet.
 */
public class BookingWebhookHandler implements ChannelWebhookHandler {

    public static final String SIGNATURE_HEADER = "X-Booking-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private static final Map<String, ReservationEventType> RESERVATION_EVENTS = Map.of(
            "reservation.created", ReservationEventType.CREATED,
            "reservation.modified", ReservationEventType.MODIFIED,
            "reservation.cancelled", ReservationEventType.CANCELLED);

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Override
    public Channel channel() {
        return Channel.BOOKING;
    }

    @Override
    public void verify(WebhookRequest request, String secret) {
        String header = request.header(SIGNATURE_HEADER)
                .orElseThrow(() -> new WebhookVerificationException("Missing " + SIGNATURE_HEADER + " header"));
        if (!header.startsWith(SIGNATURE_PREFIX)) {
            throw new WebhookVerificationException("Unsupported signature scheme");
        }
        String expected = HmacSignatures.sha256Hex(secret, request.body());
        if (!HmacSignatures.matches(expected, header.substring(SIGNATURE_PREFIX.length()))) {
            throw new WebhookVerificationException("Signature does not match");
        }
    }

    @Override
    public ChannelEvent parse(WebhookRequest request) {
        BookingWebhookPayload payload = read(request.body());
        if (isBlank(payload.eventId()) || isBlank(payload.eventType()) || payload.occurredAt() == null) {
            throw new WebhookParseException("event_id, event_type and occurred_at are required");
        }

        ReservationEventType type = RESERVATION_EVENTS.get(payload.eventType());
        if (type == null) {
            return new UnsupportedEvent(payload.eventId(), payload.occurredAt(), payload.eventType());
        }
        return toReservationEvent(payload, type);
    }

    /** Signs a body the way Booking.com does. Used by tests and the local sandbox. */
    public static String sign(String secret, byte[] body) {
        return SIGNATURE_PREFIX + HmacSignatures.sha256Hex(secret, body);
    }

    private ReservationEvent toReservationEvent(BookingWebhookPayload payload, ReservationEventType type) {
        BookingWebhookPayload.Reservation reservation = payload.reservation();
        if (reservation == null || isBlank(reservation.reservationId()) || isBlank(reservation.vehicleId())) {
            throw new WebhookParseException("reservation.reservation_id and reservation.vehicle_id are required");
        }
        if (type != ReservationEventType.CANCELLED
                && (reservation.pickupDatetime() == null || reservation.dropoffDatetime() == null)) {
            throw new WebhookParseException("pickup_datetime and dropoff_datetime are required");
        }

        BookingWebhookPayload.Driver driver = reservation.driver();
        Money total = reservation.totalPrice() == null
                ? null
                : new Money(reservation.totalPrice().amount(), reservation.totalPrice().currency());
        return new ReservationEvent(
                payload.eventId(),
                payload.occurredAt(),
                type,
                reservation.reservationId(),
                reservation.vehicleId(),
                reservation.pickupDatetime(),
                reservation.dropoffDatetime(),
                driver == null ? null : driver.fullName(),
                driver == null ? null : driver.email(),
                total);
    }

    private BookingWebhookPayload read(byte[] body) {
        try {
            return jsonMapper.readValue(body, BookingWebhookPayload.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new WebhookParseException("Malformed Booking.com webhook body", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
