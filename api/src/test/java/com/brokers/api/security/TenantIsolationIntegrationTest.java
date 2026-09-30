package com.brokers.api.security;

import com.brokers.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** One organization must never see or change another organization's data. */
class TenantIsolationIntegrationTest extends IntegrationTest {

    @Test
    void organizationsCannotReachEachOthersResources() throws Exception {
        Fleet owner = publishedFleet();
        Tenant intruder = signup();
        String vehicle = "/api/vehicles/" + owner.vehicleId();

        get(vehicle, intruder.token()).expectStatus(404);
        put(vehicle, intruder.token(), vehicleRequest(owner.locationId(), "HIJACK")).expectStatus(404);
        post(vehicle + "/unpublish", intruder.token(), null).expectStatus(404);
        delete(vehicle, intruder.token()).expectStatus(404);
        get(vehicle + "/availability", intruder.token()).expectStatus(404);
        get("/api/locations/" + owner.locationId(), intruder.token()).expectStatus(404);

        assertThat(get("/api/vehicles", intruder.token()).body().get("totalItems").asLong()).isZero();
        assertThat(get("/api/locations", intruder.token()).body().size()).isZero();
        assertThat(get("/api/sync-jobs", intruder.token()).body().get("totalItems").asLong()).isZero();
    }

    @Test
    void cannotCreateCarsAtAnotherOrganizationsLocation() throws Exception {
        Fleet owner = publishedFleet();
        Tenant intruder = signup();

        Response response = post("/api/vehicles", intruder.token(), vehicleRequest(owner.locationId(), "FLEET-X"));

        response.expectStatus(422);
        assertThat(response.code()).isEqualTo("unknown_location");
    }

    @Test
    void webhooksOnlyTouchTheConnectionsOwnOrganization() throws Exception {
        Fleet owner = publishedFleet();
        Fleet other = publishedFleet();

        // A delivery on the other organization's endpoint that names the owner's listing is rejected.
        String body = bookingReservation(unique("evt"), "reservation.created", Instant.now(),
                unique("RES"), owner.bookingListingId(),
                Instant.parse("2027-01-10T10:00:00Z"), Instant.parse("2027-01-12T10:00:00Z"));
        Response response = sendBookingWebhook(other.booking(), body).expectStatus(200);

        assertThat(response.body().get("status").asString()).isEqualTo("FAILED");
        assertThat(get("/api/reservations", owner.tenant().token()).body().get("totalItems").asLong()).isZero();
        assertThat(get("/api/reservations", other.tenant().token()).body().get("totalItems").asLong()).isZero();
    }
}
