package com.brokers.channel.rentalcars.webhook;

import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.webhook.ReservationEvent;
import com.brokers.channel.core.webhook.ReservationEventType;
import com.brokers.channel.core.webhook.UnsupportedEvent;
import com.brokers.channel.core.webhook.WebhookParseException;
import com.brokers.channel.core.webhook.WebhookRequest;
import com.brokers.channel.core.webhook.WebhookVerificationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalcarsWebhookHandlerTest {

    private static final String SECRET = "rc_whsec";
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String CONFIRMED = """
            {
              "id": "evt_abc",
              "type": "booking.confirmed",
              "createdAt": "2026-10-01T11:59:00Z",
              "data": {
                "bookingReference": "RC-BK-1",
                "vehicleId": "RC-555",
                "pickUp": {"dateTime": "2026-10-20T10:00:00Z"},
                "dropOff": {"dateTime": "2026-10-25T10:00:00Z"},
                "customer": {"fullName": "Youssef Alaoui", "email": "y@example.com"},
                "price": {"valueMinor": 27750, "currency": "EUR"}
              }
            }""";

    private final RentalcarsWebhookHandler handler =
            new RentalcarsWebhookHandler(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsFreshCorrectlySignedDelivery() {
        assertThatCode(() -> handler.verify(signed(CONFIRMED, NOW.getEpochSecond()), SECRET)).doesNotThrowAnyException();
    }

    @Test
    void rejectsReplayedDelivery() {
        long tenMinutesAgo = NOW.minusSeconds(600).getEpochSecond();

        assertThatThrownBy(() -> handler.verify(signed(CONFIRMED, tenMinutesAgo), SECRET))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("tolerance");
    }

    @Test
    void rejectsTimestampSwappedAfterSigning() {
        byte[] body = CONFIRMED.getBytes(StandardCharsets.UTF_8);
        String original = RentalcarsWebhookHandler.sign(SECRET, NOW.minusSeconds(600).getEpochSecond(), body);
        String forged = original.replaceFirst("t=\\d+", "t=" + NOW.getEpochSecond());

        assertThatThrownBy(() -> handler.verify(
                new WebhookRequest(Map.of(RentalcarsWebhookHandler.SIGNATURE_HEADER, forged), body), SECRET))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsMalformedHeader() {
        byte[] body = CONFIRMED.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> handler.verify(
                new WebhookRequest(Map.of(RentalcarsWebhookHandler.SIGNATURE_HEADER, "v1=abc"), body), SECRET))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> handler.verify(
                new WebhookRequest(Map.of(RentalcarsWebhookHandler.SIGNATURE_HEADER, "t=notanumber,v1=abc"), body), SECRET))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> handler.verify(new WebhookRequest(Map.of(), body), SECRET))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void parsesConfirmedBookingWithMinorUnitPrice() {
        ReservationEvent event = (ReservationEvent) handler.parse(signed(CONFIRMED, NOW.getEpochSecond()));

        assertThat(event.type()).isEqualTo(ReservationEventType.CREATED);
        assertThat(event.externalReservationId()).isEqualTo("RC-BK-1");
        assertThat(event.externalListingId()).isEqualTo("RC-555");
        assertThat(event.pickupAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
        assertThat(event.returnAt()).isEqualTo(Instant.parse("2026-10-25T10:00:00Z"));
        assertThat(event.customerName()).isEqualTo("Youssef Alaoui");
        assertThat(event.totalPrice()).isEqualTo(Money.of("277.50", "EUR"));
    }

    @Test
    void mapsAmendAndCancelEvents() {
        String amended = CONFIRMED.replace("booking.confirmed", "booking.amended");
        String cancelled = """
                {"id":"evt_c","type":"booking.cancelled","createdAt":"2026-10-01T11:59:00Z",
                 "data":{"bookingReference":"RC-BK-1","vehicleId":"RC-555"}}""";

        assertThat(((ReservationEvent) handler.parse(signed(amended, 0))).type()).isEqualTo(ReservationEventType.MODIFIED);
        assertThat(((ReservationEvent) handler.parse(signed(cancelled, 0))).type()).isEqualTo(ReservationEventType.CANCELLED);
    }

    @Test
    void unknownTypesAreUnsupported() {
        String body = "{\"id\":\"evt_x\",\"type\":\"invoice.issued\",\"createdAt\":\"2026-10-01T11:59:00Z\"}";

        assertThat(handler.parse(signed(body, 0))).isInstanceOf(UnsupportedEvent.class);
    }

    @Test
    void rejectsIncompletePayloads() {
        String missingDates = """
                {"id":"evt_d","type":"booking.confirmed","createdAt":"2026-10-01T11:59:00Z",
                 "data":{"bookingReference":"RC-BK-1","vehicleId":"RC-555"}}""";

        assertThatThrownBy(() -> handler.parse(signed(missingDates, 0))).isInstanceOf(WebhookParseException.class);
        assertThatThrownBy(() -> handler.parse(signed("[]", 0))).isInstanceOf(WebhookParseException.class);
    }

    private static WebhookRequest signed(String body, long timestamp) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new WebhookRequest(
                Map.of(RentalcarsWebhookHandler.SIGNATURE_HEADER, RentalcarsWebhookHandler.sign(SECRET, timestamp, bytes)),
                bytes);
    }
}
