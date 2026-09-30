package com.brokers.channel.rentalcars.webhook;

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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Rentalcars.com signs deliveries with {@code X-Rentalcars-Signature: t=<unix seconds>,v1=<hex>}
 * where the HMAC-SHA256 covers {@code "<t>." + body}. Binding the timestamp into the signature lets
 * us reject replays of old deliveries.
 */
public class RentalcarsWebhookHandler implements ChannelWebhookHandler {

    public static final String SIGNATURE_HEADER = "X-Rentalcars-Signature";
    static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private static final Map<String, ReservationEventType> RESERVATION_EVENTS = Map.of(
            "booking.confirmed", ReservationEventType.CREATED,
            "booking.amended", ReservationEventType.MODIFIED,
            "booking.cancelled", ReservationEventType.CANCELLED);

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Clock clock;
    private final Duration tolerance;

    public RentalcarsWebhookHandler(Clock clock) {
        this(clock, DEFAULT_TOLERANCE);
    }

    public RentalcarsWebhookHandler(Clock clock, Duration tolerance) {
        this.clock = clock;
        this.tolerance = tolerance;
    }

    @Override
    public Channel channel() {
        return Channel.RENTALCARS;
    }

    @Override
    public void verify(WebhookRequest request, String secret) {
        String header = request.header(SIGNATURE_HEADER)
                .orElseThrow(() -> new WebhookVerificationException("Missing " + SIGNATURE_HEADER + " header"));
        SignatureHeader signature = SignatureHeader.parse(header);

        Duration age = Duration.between(Instant.ofEpochSecond(signature.timestamp()), clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            throw new WebhookVerificationException("Signature timestamp is outside the allowed tolerance");
        }

        String expected = HmacSignatures.sha256Hex(secret, signedPayload(signature.timestamp(), request.body()));
        if (!HmacSignatures.matches(expected, signature.v1())) {
            throw new WebhookVerificationException("Signature does not match");
        }
    }

    @Override
    public ChannelEvent parse(WebhookRequest request) {
        RcWebhookPayload payload = read(request.body());
        if (isBlank(payload.id()) || isBlank(payload.type()) || payload.createdAt() == null) {
            throw new WebhookParseException("id, type and createdAt are required");
        }

        ReservationEventType type = RESERVATION_EVENTS.get(payload.type());
        if (type == null) {
            return new UnsupportedEvent(payload.id(), payload.createdAt(), payload.type());
        }
        return toReservationEvent(payload, type);
    }

    /** Builds the signature header the way Rentalcars.com does. Used by tests and the local sandbox. */
    public static String sign(String secret, long timestamp, byte[] body) {
        return "t=" + timestamp + ",v1=" + HmacSignatures.sha256Hex(secret, signedPayload(timestamp, body));
    }

    private static byte[] signedPayload(long timestamp, byte[] body) {
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        return payload;
    }

    private ReservationEvent toReservationEvent(RcWebhookPayload payload, ReservationEventType type) {
        RcWebhookPayload.Data data = payload.data();
        if (data == null || isBlank(data.bookingReference()) || isBlank(data.vehicleId())) {
            throw new WebhookParseException("data.bookingReference and data.vehicleId are required");
        }
        Instant pickupAt = data.pickUp() == null ? null : data.pickUp().dateTime();
        Instant returnAt = data.dropOff() == null ? null : data.dropOff().dateTime();
        if (type != ReservationEventType.CANCELLED && (pickupAt == null || returnAt == null)) {
            throw new WebhookParseException("pickUp.dateTime and dropOff.dateTime are required");
        }

        RcWebhookPayload.Customer customer = data.customer();
        Money total = data.price() == null ? null : Money.ofMinorUnits(data.price().valueMinor(), data.price().currency());
        return new ReservationEvent(
                payload.id(),
                payload.createdAt(),
                type,
                data.bookingReference(),
                data.vehicleId(),
                pickupAt,
                returnAt,
                customer == null ? null : customer.fullName(),
                customer == null ? null : customer.email(),
                total);
    }

    private RcWebhookPayload read(byte[] body) {
        try {
            return jsonMapper.readValue(body, RcWebhookPayload.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new WebhookParseException("Malformed Rentalcars.com webhook body", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SignatureHeader(long timestamp, String v1) {

        static SignatureHeader parse(String header) {
            Long timestamp = null;
            String v1 = null;
            for (String part : header.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                if (pair[0].equals("t")) {
                    timestamp = parseTimestamp(pair[1]);
                }
                if (pair[0].equals("v1")) {
                    v1 = pair[1];
                }
            }
            if (timestamp == null || isBlank(v1)) {
                throw new WebhookVerificationException("Malformed " + SIGNATURE_HEADER + " header");
            }
            return new SignatureHeader(timestamp, v1);
        }

        private static Long parseTimestamp(String value) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
