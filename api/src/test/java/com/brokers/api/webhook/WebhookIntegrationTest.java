package com.brokers.api.webhook;

import com.brokers.api.support.FakeChannels;
import com.brokers.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reservations arrive by webhook from one channel and must block the car on every channel.
 */
class WebhookIntegrationTest extends IntegrationTest {

    private static final Instant PICKUP = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    private static final Instant DROPOFF = PICKUP.plus(4, ChronoUnit.DAYS);

    @Test
    void bookingReservationBlocksTheCarOnRentalcars() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.RENTALCARS.resetRequests();

        Response receipt = sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created",
                Instant.now(), "RES-1-" + fleet.vehicleId(), fleet.bookingListingId(), PICKUP, DROPOFF));
        syncWorker.runUntilIdle();

        receipt.expectStatus(200);
        assertThat(receipt.body().get("status").asString()).isEqualTo("PROCESSED");
        assertThat(receipt.body().get("duplicate").asBoolean()).isFalse();

        JsonNode reservation = reservations(fleet).get(0);
        assertThat(reservation.get("channel").asString()).isEqualTo("BOOKING");
        assertThat(reservation.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(reservation.get("customerName").asString()).isEqualTo("Amina Benali");
        assertThat(reservation.get("totalAmount").decimalValue()).isEqualByComparingTo("159.60");
        assertThat(reservation.get("hasConflict").asBoolean()).isFalse();

        JsonNode blocks = get("/api/vehicles/" + fleet.vehicleId() + "/availability", fleet.tenant().token()).body();
        assertThat(blocks.get(0).get("reason").asString()).isEqualTo("RESERVATION");

        FakeChannels.RENTALCARS.verify(exactly(1), putRequestedFor(urlPathEqualTo(stopSalesPath(fleet)))
                .withRequestBody(matchingJsonPath("$.stopSales[0].start", equalTo(PICKUP.toString())))
                .withRequestBody(matchingJsonPath("$.stopSales[0].end", equalTo(DROPOFF.toString()))));
    }

    @Test
    void rentalcarsReservationBlocksTheCarOnBooking() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.resetRequests();

        sendRentalcarsWebhook(fleet.rentalcars(), rentalcarsBooking(unique("evt"), "booking.confirmed", Instant.now(),
                "RC-BK-" + fleet.vehicleId(), fleet.rentalcarsListingId(), PICKUP, DROPOFF), Instant.now())
                .expectStatus(200);
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(exactly(1), putRequestedFor(urlPathEqualTo(availabilityPath(fleet)))
                .withRequestBody(matchingJsonPath("$.closed_periods[0].start", equalTo(PICKUP.toString()))));
        assertThat(reservations(fleet).get(0).get("totalAmount").decimalValue()).isEqualByComparingTo("277.50");
    }

    @Test
    void duplicateDeliveriesAreAcknowledgedButAppliedOnce() throws Exception {
        Fleet fleet = publishedFleet();
        String body = bookingReservation("evt-dup-" + fleet.vehicleId(), "reservation.created", Instant.now(),
                "RES-DUP-" + fleet.vehicleId(), fleet.bookingListingId(), PICKUP, DROPOFF);

        Response first = sendBookingWebhook(fleet.booking(), body).expectStatus(200);
        Response second = sendBookingWebhook(fleet.booking(), body).expectStatus(200);

        assertThat(second.body().get("duplicate").asBoolean()).isTrue();
        assertThat(second.body().get("eventId")).isEqualTo(first.body().get("eventId"));
        assertThat(get("/api/reservations", fleet.tenant().token()).body().get("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    void invalidSignaturesAreRejectedAndNotStored() throws Exception {
        Fleet fleet = publishedFleet();
        Connection forged = new Connection(fleet.booking().id(), fleet.booking().accountId(), "attacker-guess");

        Response response = sendBookingWebhook(forged, bookingReservation(unique("evt"), "reservation.created",
                Instant.now(), unique("RES"), fleet.bookingListingId(), PICKUP, DROPOFF));

        response.expectStatus(401);
        assertThat(response.code()).isEqualTo("invalid_signature");
        assertThat(get("/api/webhook-events", fleet.tenant().token()).body().get("totalItems").asLong()).isZero();
    }

    @Test
    void replayedRentalcarsDeliveriesAreRejected() throws Exception {
        Fleet fleet = publishedFleet();

        Response response = sendRentalcarsWebhook(fleet.rentalcars(), rentalcarsBooking(unique("evt"), "booking.confirmed",
                Instant.now(), unique("RC"), fleet.rentalcarsListingId(), PICKUP, DROPOFF), Instant.now().minusSeconds(3600));

        response.expectStatus(401);
    }

    @Test
    void unknownEndpointsAndMalformedBodiesAreRejected() throws Exception {
        Fleet fleet = publishedFleet();
        Connection unknown = new Connection(UUID.randomUUID(), "x", fleet.booking().webhookSecret());

        sendBookingWebhook(unknown, "{}").expectStatus(404);
        Response malformed = sendBookingWebhook(fleet.booking(), "{\"event_type\":\"reservation.created\"}");
        malformed.expectStatus(400);
        assertThat(malformed.code()).isEqualTo("invalid_payload");
    }

    @Test
    void oversizedBodiesAreRejected() throws Exception {
        Fleet fleet = publishedFleet();
        byte[] huge = new byte[WebhookIngestionService.MAX_BODY_BYTES + 1];

        int status = mvc.perform(MockMvcRequestBuilders.post("/api/webhooks/booking/" + fleet.booking().id())
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(413);
    }

    @Test
    void cancellationReleasesTheCarEverywhere() throws Exception {
        Fleet fleet = publishedFleet();
        String reservationId = "RES-C-" + fleet.vehicleId();
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created",
                Instant.now().minusSeconds(60), reservationId, fleet.bookingListingId(), PICKUP, DROPOFF)).expectStatus(200);
        syncWorker.runUntilIdle();
        FakeChannels.RENTALCARS.resetRequests();

        Response cancelled = sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.cancelled",
                Instant.now(), reservationId, fleet.bookingListingId(), null, null));
        syncWorker.runUntilIdle();

        assertThat(cancelled.body().get("status").asString()).isEqualTo("PROCESSED");
        assertThat(reservations(fleet).get(0).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(get("/api/vehicles/" + fleet.vehicleId() + "/availability", fleet.tenant().token()).body().size()).isZero();
        FakeChannels.RENTALCARS.verify(putRequestedFor(urlPathEqualTo(stopSalesPath(fleet)))
                .withRequestBody(matchingJsonPath("$.stopSales.size()", equalTo("0"))));
    }

    @Test
    void modificationsMoveTheBlock() throws Exception {
        Fleet fleet = publishedFleet();
        String reservationId = "RES-M-" + fleet.vehicleId();
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created",
                Instant.now().minusSeconds(60), reservationId, fleet.bookingListingId(), PICKUP, DROPOFF)).expectStatus(200);

        Instant newDropoff = DROPOFF.plus(2, ChronoUnit.DAYS);
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.modified",
                Instant.now(), reservationId, fleet.bookingListingId(), PICKUP, newDropoff)).expectStatus(200);

        JsonNode blocks = get("/api/vehicles/" + fleet.vehicleId() + "/availability", fleet.tenant().token()).body();
        assertThat(blocks.size()).isEqualTo(1);
        assertThat(blocks.get(0).get("endsAt").asString()).isEqualTo(newDropoff.toString());
    }

    @Test
    void outOfOrderEventsDoNotResurrectCancelledReservations() throws Exception {
        Fleet fleet = publishedFleet();
        String reservationId = "RES-O-" + fleet.vehicleId();
        Instant created = Instant.now().minusSeconds(120);
        Instant cancelled = Instant.now().minusSeconds(60);

        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created",
                created, reservationId, fleet.bookingListingId(), PICKUP, DROPOFF)).expectStatus(200);
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.cancelled",
                cancelled, reservationId, fleet.bookingListingId(), null, null)).expectStatus(200);
        Response late = sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.modified",
                created.plusSeconds(10), reservationId, fleet.bookingListingId(), PICKUP, DROPOFF));

        assertThat(late.body().get("status").asString()).isEqualTo("IGNORED");
        assertThat(reservations(fleet).get(0).get("status").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAnUnknownReservationIsIgnored() throws Exception {
        Fleet fleet = publishedFleet();

        Response response = sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.cancelled",
                Instant.now(), "NEVER-SEEN", fleet.bookingListingId(), null, null));

        assertThat(response.body().get("status").asString()).isEqualTo("IGNORED");
    }

    @Test
    void overbookingIsKeptAndFlaggedUntilResolved() throws Exception {
        Fleet fleet = publishedFleet();
        String bookingReservation = "RES-OB-" + fleet.vehicleId();
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created",
                Instant.now().minusSeconds(30), bookingReservation, fleet.bookingListingId(), PICKUP, DROPOFF)).expectStatus(200);

        // Rentalcars sold the same car before our stop-sale reached it.
        sendRentalcarsWebhook(fleet.rentalcars(), rentalcarsBooking(unique("evt"), "booking.confirmed", Instant.now(),
                "RC-OB-" + fleet.vehicleId(), fleet.rentalcarsListingId(), PICKUP.plus(1, ChronoUnit.DAYS),
                DROPOFF.plus(1, ChronoUnit.DAYS)), Instant.now()).expectStatus(200);

        JsonNode conflicts = get("/api/reservations?conflictOnly=true", fleet.tenant().token()).body();
        assertThat(conflicts.get("totalItems").asLong()).isEqualTo(2);
        assertThat(get("/api/dashboard", fleet.tenant().token()).body().at("/reservations/conflicts").asLong()).isEqualTo(2);

        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.cancelled",
                Instant.now().plusSeconds(1), bookingReservation, fleet.bookingListingId(), null, null)).expectStatus(200);

        assertThat(get("/api/reservations?conflictOnly=true", fleet.tenant().token()).body().get("totalItems").asLong()).isZero();
    }

    @Test
    void mergesOverlappingBlocksBeforePushing() throws Exception {
        Fleet fleet = publishedFleet();
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created", Instant.now(),
                "RES-A-" + fleet.vehicleId(), fleet.bookingListingId(), PICKUP, DROPOFF)).expectStatus(200);
        sendRentalcarsWebhook(fleet.rentalcars(), rentalcarsBooking(unique("evt"), "booking.confirmed", Instant.now(),
                "RC-B-" + fleet.vehicleId(), fleet.rentalcarsListingId(), DROPOFF.minus(1, ChronoUnit.DAYS),
                DROPOFF.plus(2, ChronoUnit.DAYS)), Instant.now()).expectStatus(200);
        FakeChannels.BOOKING.resetRequests();

        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(putRequestedFor(urlPathEqualTo(availabilityPath(fleet)))
                .withRequestBody(matchingJsonPath("$.closed_periods.size()", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.closed_periods[0].end",
                        equalTo(DROPOFF.plus(2, ChronoUnit.DAYS).toString()))));
    }

    @Test
    void failedEventsAreKeptAndCanBeReplayed() throws Exception {
        Fleet fleet = publishedFleet();

        Response response = sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created",
                Instant.now(), unique("RES"), "BK-UNKNOWN", PICKUP, DROPOFF)).expectStatus(200);

        assertThat(response.body().get("status").asString()).isEqualTo("FAILED");
        String eventId = response.body().get("eventId").asString();
        JsonNode event = get("/api/webhook-events/" + eventId, fleet.tenant().token()).body();
        assertThat(event.get("lastError").asString()).contains("BK-UNKNOWN");
        assertThat(event.get("payload").asString()).contains("reservation.created");

        JsonNode replayed = post("/api/webhook-events/" + eventId + "/replay", fleet.tenant().token(), null)
                .expectStatus(200).body();
        assertThat(replayed.get("status").asString()).isEqualTo("FAILED");
        assertThat(replayed.get("attempts").asInt()).isEqualTo(2);
        assertThat(get("/api/dashboard", fleet.tenant().token()).body().get("failedWebhooks").asLong()).isEqualTo(1);
    }

    @Test
    void unsupportedEventTypesAreIgnored() throws Exception {
        Fleet fleet = publishedFleet();

        Response response = sendBookingWebhook(fleet.booking(),
                "{\"event_id\":\"" + unique("evt") + "\",\"event_type\":\"review.published\",\"occurred_at\":\"" + Instant.now() + "\"}");

        assertThat(response.body().get("status").asString()).isEqualTo("IGNORED");
    }

    @Test
    void archivingIsRefusedWhileReservationsAreUpcoming() throws Exception {
        Fleet fleet = publishedFleet();
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created", Instant.now(),
                "RES-AR-" + fleet.vehicleId(), fleet.bookingListingId(), PICKUP, DROPOFF)).expectStatus(200);

        Response response = delete("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token());

        response.expectStatus(409);
        assertThat(response.code()).isEqualTo("vehicle_has_upcoming_reservations");
    }

    @Test
    void sandboxSimulatesSignedChannelReservations() throws Exception {
        Fleet fleet = publishedFleet();

        Response simulated = post("/api/sandbox/reservations", fleet.tenant().token(), Map.of(
                "vehicleId", fleet.vehicleId(), "channel", "RENTALCARS", "type", "CREATED",
                "pickupAt", PICKUP.toString(), "returnAt", DROPOFF.toString(),
                "customerName", "Test Driver", "totalAmount", "250.00")).expectStatus(200);

        assertThat(simulated.body().at("/receipt/status").asString()).isEqualTo("PROCESSED");
        JsonNode reservation = reservations(fleet).get(0);
        assertThat(reservation.get("externalReservationId").asString()).isEqualTo(simulated.body().get("externalReservationId").asString());
        assertThat(reservation.get("totalAmount").decimalValue()).isEqualByComparingTo("250.00");
    }

    private JsonNode reservations(Fleet fleet) throws Exception {
        return get("/api/reservations?vehicleId=" + fleet.vehicleId(), fleet.tenant().token()).body().get("items");
    }

    private static String stopSalesPath(Fleet fleet) {
        return "/v2/suppliers/" + fleet.rentalcars().accountId() + "/fleet/" + fleet.rentalcarsListingId() + "/stop-sales";
    }

    private static String availabilityPath(Fleet fleet) {
        return "/suppliers/" + fleet.booking().accountId() + "/vehicles/" + fleet.bookingListingId() + "/availability";
    }
}
