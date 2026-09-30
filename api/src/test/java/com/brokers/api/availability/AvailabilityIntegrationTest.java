package com.brokers.api.availability;

import com.brokers.api.support.FakeChannels;
import com.brokers.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityIntegrationTest extends IntegrationTest {

    private static final Instant START = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    private static final Instant END = START.plus(3, ChronoUnit.DAYS);

    @Test
    void manualBlocksArePushedToEveryChannel() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.resetRequests();
        FakeChannels.RENTALCARS.resetRequests();

        Response created = post(path(fleet), fleet.tenant().token(), Map.of(
                "startsAt", START.toString(), "endsAt", END.toString(), "reason", "MAINTENANCE", "note", "Tyres"));
        syncWorker.runUntilIdle();

        created.expectStatus(201);
        assertThat(created.body().get("reason").asString()).isEqualTo("MAINTENANCE");
        FakeChannels.BOOKING.verify(putRequestedFor(urlPathMatching(".*/availability"))
                .withRequestBody(matchingJsonPath("$.closed_periods[0].start", equalTo(START.toString()))));
        FakeChannels.RENTALCARS.verify(putRequestedFor(urlPathMatching(".*/stop-sales"))
                .withRequestBody(matchingJsonPath("$.stopSales[0].end", equalTo(END.toString()))));
    }

    @Test
    void removingABlockReopensTheCar() throws Exception {
        Fleet fleet = publishedFleet();
        String blockId = post(path(fleet), fleet.tenant().token(), Map.of("startsAt", START.toString(), "endsAt", END.toString()))
                .expectStatus(201).body().get("id").asString();
        syncWorker.runUntilIdle();
        FakeChannels.BOOKING.resetRequests();

        delete(path(fleet) + "/" + blockId, fleet.tenant().token()).expectStatus(204);
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(putRequestedFor(urlPathMatching(".*/availability"))
                .withRequestBody(matchingJsonPath("$.closed_periods.size()", equalTo("0"))));
    }

    @Test
    void blocksCannotOverlapExistingOnes() throws Exception {
        Fleet fleet = publishedFleet();
        post(path(fleet), fleet.tenant().token(), Map.of("startsAt", START.toString(), "endsAt", END.toString()))
                .expectStatus(201);

        Response overlapping = post(path(fleet), fleet.tenant().token(), Map.of(
                "startsAt", START.plus(1, ChronoUnit.DAYS).toString(), "endsAt", END.plus(1, ChronoUnit.DAYS).toString()));
        Response adjacent = post(path(fleet), fleet.tenant().token(), Map.of(
                "startsAt", END.toString(), "endsAt", END.plus(1, ChronoUnit.DAYS).toString()));

        overlapping.expectStatus(409);
        assertThat(overlapping.code()).isEqualTo("availability_conflict");
        adjacent.expectStatus(201);
    }

    @Test
    void validatesPeriods() throws Exception {
        Fleet fleet = publishedFleet();

        Response reversed = post(path(fleet), fleet.tenant().token(), Map.of("startsAt", END.toString(), "endsAt", START.toString()));
        Response past = post(path(fleet), fleet.tenant().token(), Map.of(
                "startsAt", "2020-01-01T00:00:00Z", "endsAt", "2020-01-02T00:00:00Z"));
        Response tooLong = post(path(fleet), fleet.tenant().token(), Map.of(
                "startsAt", START.toString(), "endsAt", START.plus(400, ChronoUnit.DAYS).toString()));
        Response reservationReason = post(path(fleet), fleet.tenant().token(), Map.of(
                "startsAt", START.toString(), "endsAt", END.toString(), "reason", "RESERVATION"));

        reversed.expectStatus(422);
        past.expectStatus(422);
        tooLong.expectStatus(422);
        reservationReason.expectStatus(422);
    }

    @Test
    void reservationBlocksCanOnlyBeReleasedByTheChannel() throws Exception {
        Fleet fleet = publishedFleet();
        sendBookingWebhook(fleet.booking(), bookingReservation(unique("evt"), "reservation.created", Instant.now(),
                "RES-" + fleet.vehicleId(), fleet.bookingListingId(), START, END)).expectStatus(200);
        JsonNode block = get(path(fleet), fleet.tenant().token()).body().get(0);

        Response response = delete(path(fleet) + "/" + block.get("id").asString(), fleet.tenant().token());
        Response overlapping = post(path(fleet), fleet.tenant().token(), Map.of("startsAt", START.toString(), "endsAt", END.toString()));

        response.expectStatus(409);
        assertThat(response.code()).isEqualTo("reservation_block");
        overlapping.expectStatus(409);
    }

    @Test
    void draftCarsKeepTheirCalendarWithoutSyncing() throws Exception {
        Tenant tenant = signup();
        connect(tenant, "booking");
        String vehicle = createVehicle(tenant, createLocation(tenant));

        post("/api/vehicles/" + vehicle + "/availability", tenant.token(),
                Map.of("startsAt", START.toString(), "endsAt", END.toString())).expectStatus(201);

        assertThat(get("/api/sync-jobs?vehicleId=" + vehicle, tenant.token()).body().get("totalItems").asLong()).isZero();
    }

    private static String path(Fleet fleet) {
        return "/api/vehicles/" + fleet.vehicleId() + "/availability";
    }
}
