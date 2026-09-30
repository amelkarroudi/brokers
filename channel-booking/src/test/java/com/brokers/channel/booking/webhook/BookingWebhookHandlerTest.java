package com.brokers.channel.booking.webhook;

import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.webhook.ChannelEvent;
import com.brokers.channel.core.webhook.ReservationEvent;
import com.brokers.channel.core.webhook.ReservationEventType;
import com.brokers.channel.core.webhook.UnsupportedEvent;
import com.brokers.channel.core.webhook.WebhookParseException;
import com.brokers.channel.core.webhook.WebhookRequest;
import com.brokers.channel.core.webhook.WebhookVerificationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingWebhookHandlerTest {

    private static final String SECRET = "whsec_booking";
    private static final String CREATED = """
            {
              "event_id": "evt-1",
              "event_type": "reservation.created",
              "occurred_at": "2026-10-01T08:00:00Z",
              "reservation": {
                "reservation_id": "RES-77",
                "vehicle_id": "BK-9001",
                "pickup_datetime": "2026-10-10T09:00:00Z",
                "dropoff_datetime": "2026-10-14T09:00:00Z",
                "driver": {"first_name": "Amina", "last_name": "Benali", "email": "amina@example.com"},
                "total_price": {"amount": 159.60, "currency": "EUR"},
                "unknown_field": "ignored"
              }
            }""";

    private final BookingWebhookHandler handler = new BookingWebhookHandler();

    @Test
    void acceptsCorrectSignature() {
        WebhookRequest request = signed(CREATED, SECRET);

        assertThatCode(() -> handler.verify(request, SECRET)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongSecret() {
        WebhookRequest request = signed(CREATED, "another-secret");

        assertThatThrownBy(() -> handler.verify(request, SECRET)).isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void rejectsTamperedBody() {
        byte[] original = CREATED.getBytes(StandardCharsets.UTF_8);
        String signature = BookingWebhookHandler.sign(SECRET, original);
        WebhookRequest tampered = new WebhookRequest(Map.of(BookingWebhookHandler.SIGNATURE_HEADER, signature),
                CREATED.replace("159.60", "1.00").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> handler.verify(tampered, SECRET)).isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void rejectsMissingOrMalformedSignature() {
        byte[] body = CREATED.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> handler.verify(new WebhookRequest(Map.of(), body), SECRET))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("Missing");
        assertThatThrownBy(() -> handler.verify(
                new WebhookRequest(Map.of(BookingWebhookHandler.SIGNATURE_HEADER, "md5=abc"), body), SECRET))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void parsesReservationCreated() {
        ChannelEvent event = handler.parse(signed(CREATED, SECRET));

        assertThat(event).isEqualTo(new ReservationEvent(
                "evt-1", Instant.parse("2026-10-01T08:00:00Z"), ReservationEventType.CREATED,
                "RES-77", "BK-9001",
                Instant.parse("2026-10-10T09:00:00Z"), Instant.parse("2026-10-14T09:00:00Z"),
                "Amina Benali", "amina@example.com", Money.of("159.60", "EUR")));
    }

    @Test
    void parsesCancellationWithoutDates() {
        String body = """
                {"event_id":"evt-2","event_type":"reservation.cancelled","occurred_at":"2026-10-02T08:00:00Z",
                 "reservation":{"reservation_id":"RES-77","vehicle_id":"BK-9001"}}""";

        ReservationEvent event = (ReservationEvent) handler.parse(signed(body, SECRET));

        assertThat(event.type()).isEqualTo(ReservationEventType.CANCELLED);
        assertThat(event.pickupAt()).isNull();
        assertThat(event.totalPrice()).isNull();
    }

    @Test
    void unknownEventTypesAreUnsupported() {
        String body = """
                {"event_id":"evt-3","event_type":"review.published","occurred_at":"2026-10-02T08:00:00Z"}""";

        assertThat(handler.parse(signed(body, SECRET)))
                .isEqualTo(new UnsupportedEvent("evt-3", Instant.parse("2026-10-02T08:00:00Z"), "review.published"));
    }

    @Test
    void rejectsInvalidPayloads() {
        assertThatThrownBy(() -> handler.parse(signed("not json", SECRET))).isInstanceOf(WebhookParseException.class);
        assertThatThrownBy(() -> handler.parse(signed("{\"event_type\":\"reservation.created\"}", SECRET)))
                .isInstanceOf(WebhookParseException.class);
        assertThatThrownBy(() -> handler.parse(signed("""
                {"event_id":"e","event_type":"reservation.created","occurred_at":"2026-10-02T08:00:00Z",
                 "reservation":{"reservation_id":"R","vehicle_id":"V"}}""", SECRET)))
                .isInstanceOf(WebhookParseException.class)
                .hasMessageContaining("pickup_datetime");
    }

    private static WebhookRequest signed(String body, String secret) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new WebhookRequest(Map.of(BookingWebhookHandler.SIGNATURE_HEADER, BookingWebhookHandler.sign(secret, bytes)), bytes);
    }
}
