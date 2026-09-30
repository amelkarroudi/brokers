package com.brokers.api.vehicle;

import com.brokers.api.support.FakeChannels;
import com.brokers.api.support.IntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/** Fleet changes on the platform must reach both channels automatically, and only when needed. */
class VehicleSyncIntegrationTest extends IntegrationTest {

    @Test
    void publishingRequiresAPhoto() throws Exception {
        Tenant tenant = signup();
        String vehicle = createVehicle(tenant, createLocation(tenant));

        Response response = post("/api/vehicles/" + vehicle + "/publish", tenant.token(), null);

        response.expectStatus(422);
        assertThat(response.code()).isEqualTo("photos_required");
    }

    @Test
    void publishingCreatesTheListingWithPhotosAndAvailabilityOnBothChannels() throws Exception {
        Fleet fleet = publishedFleet();
        String bookingVehicle = "/suppliers/" + fleet.booking().accountId() + "/vehicles";
        String rentalcarsVehicle = "/v2/suppliers/" + fleet.rentalcars().accountId() + "/fleet";

        FakeChannels.BOOKING.verify(postRequestedFor(urlPathEqualTo(bookingVehicle))
                .withRequestBody(matchingJsonPath("$.price_per_day.amount", equalTo("39.9")))
                .withRequestBody(matchingJsonPath("$.pickup_location.iata", equalTo("CMN"))));
        FakeChannels.BOOKING.verify(putRequestedFor(urlPathEqualTo(bookingVehicle + "/" + fleet.bookingListingId() + "/photos")));
        FakeChannels.BOOKING.verify(putRequestedFor(urlPathEqualTo(bookingVehicle + "/" + fleet.bookingListingId() + "/availability")));

        FakeChannels.RENTALCARS.verify(postRequestedFor(urlPathEqualTo(rentalcarsVehicle))
                .withRequestBody(matchingJsonPath("$.dailyRate.valueMinor", equalTo("3990"))));
        FakeChannels.RENTALCARS.verify(putRequestedFor(urlPathEqualTo(rentalcarsVehicle + "/" + fleet.rentalcarsListingId() + "/images")));
        FakeChannels.RENTALCARS.verify(putRequestedFor(urlPathEqualTo(rentalcarsVehicle + "/" + fleet.rentalcarsListingId() + "/stop-sales")));

        JsonNode listings = get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("listings");
        assertThat(listings.findValuesAsString("status")).containsExactly("PUBLISHED", "PUBLISHED");
        assertThat(jobStatuses(fleet)).containsOnly("SUCCEEDED");
    }

    @Test
    void editingAPublishedCarUpdatesOnlyTheListingContent() throws Exception {
        Fleet fleet = publishedFleet();
        Map<String, Object> changed = vehicleRequest(fleet.locationId(), reference(fleet));
        changed.put("dailyRate", "44.50");
        resetRequestLogs();

        put("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token(), changed).expectStatus(200);
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(exactly(1), putRequestedFor(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .withRequestBody(matchingJsonPath("$.price_per_day.amount", equalTo("44.5"))));
        FakeChannels.BOOKING.verify(exactly(0), putRequestedFor(urlPathMatching(".*/photos")));
        FakeChannels.BOOKING.verify(exactly(0), putRequestedFor(urlPathMatching(".*/availability")));
        FakeChannels.RENTALCARS.verify(exactly(1), putRequestedFor(urlPathMatching(".*/fleet/" + fleet.rentalcarsListingId()))
                .withRequestBody(matchingJsonPath("$.dailyRate.valueMinor", equalTo("4450"))));
    }

    @Test
    void savingWithoutChangesSendsNothing() throws Exception {
        Fleet fleet = publishedFleet();
        resetRequestLogs();

        put("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token(),
                vehicleRequest(fleet.locationId(), reference(fleet))).expectStatus(200);
        syncWorker.runUntilIdle();

        assertThat(FakeChannels.BOOKING.getAllServeEvents()).isEmpty();
        assertThat(FakeChannels.RENTALCARS.getAllServeEvents()).allSatisfy(event ->
                assertThat(event.getRequest().getUrl()).isEqualTo("/oauth/token"));
    }

    @Test
    void burstsOfEditsAreMergedIntoOnePush() throws Exception {
        Fleet fleet = publishedFleet();
        resetRequestLogs();

        for (String rate : List.of("41.00", "42.00", "43.00")) {
            Map<String, Object> changed = vehicleRequest(fleet.locationId(), reference(fleet));
            changed.put("dailyRate", rate);
            put("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token(), changed).expectStatus(200);
        }
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(exactly(1), putRequestedFor(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .withRequestBody(matchingJsonPath("$.price_per_day.amount", equalTo("43.0"))));
    }

    @Test
    void photoChangesPushOnlyPhotosInTheNewOrder() throws Exception {
        Fleet fleet = publishedFleet();
        resetRequestLogs();

        post("/api/vehicles/" + fleet.vehicleId() + "/photos", fleet.tenant().token(),
                Map.of("url", "https://cdn.example.com/new-cover.jpg", "position", 0)).expectStatus(201);
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(exactly(1), putRequestedFor(urlPathMatching(".*/photos"))
                .withRequestBody(matchingJsonPath("$.photos[0].url", equalTo("https://cdn.example.com/new-cover.jpg")))
                .withRequestBody(matchingJsonPath("$.photos[0].is_main", equalTo("true"))));
        FakeChannels.BOOKING.verify(exactly(0), putRequestedFor(urlPathMatching(".*/vehicles/" + fleet.bookingListingId())));
    }

    @Test
    void photoRulesAreEnforced() throws Exception {
        Fleet fleet = publishedFleet();
        String photos = "/api/vehicles/" + fleet.vehicleId() + "/photos";
        JsonNode vehicle = get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body();
        String onlyPhoto = vehicle.at("/photos/0/id").asString();
        String existingUrl = vehicle.at("/photos/0/url").asString();

        Response duplicate = post(photos, fleet.tenant().token(), Map.of("url", existingUrl));
        Response notHttp = post(photos, fleet.tenant().token(), Map.of("url", "ftp://example.com/a.jpg"));
        Response removeLast = delete(photos + "/" + onlyPhoto, fleet.tenant().token());
        Response badOrder = put(photos + "/order", fleet.tenant().token(), Map.of("photoIds", List.of(onlyPhoto, onlyPhoto)));

        duplicate.expectStatus(409);
        notHttp.expectStatus(400);
        removeLast.expectStatus(422);
        assertThat(removeLast.code()).isEqualTo("photos_required");
        badOrder.expectStatus(422);
    }

    @Test
    void recreatesAListingTheChannelLost() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.stubFor(WireMock.put(urlPathEqualTo(
                        "/suppliers/" + fleet.booking().accountId() + "/vehicles/" + fleet.bookingListingId()))
                .willReturn(aResponse().withStatus(404)));
        resetRequestLogs();

        post("/api/vehicles/" + fleet.vehicleId() + "/sync", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        String newId = externalId(get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("listings"), "BOOKING");
        assertThat(newId).isNotEqualTo(fleet.bookingListingId()).startsWith("BK-");
        FakeChannels.BOOKING.verify(exactly(1), postRequestedFor(urlPathMatching(".*/vehicles")));
        FakeChannels.BOOKING.verify(putRequestedFor(urlPathEqualTo(
                "/suppliers/" + fleet.booking().accountId() + "/vehicles/" + newId + "/photos")));
    }

    @Test
    void transientFailuresAreRetriedWithBackoff() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.RENTALCARS.stubFor(WireMock.put(urlPathMatching(".*/fleet/" + fleet.rentalcarsListingId()))
                .willReturn(aResponse().withStatus(503)));

        post("/api/vehicles/" + fleet.vehicleId() + "/sync", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        JsonNode job = latestJob(fleet, "RENTALCARS");
        assertThat(job.get("status").asString()).isEqualTo("PENDING");
        assertThat(job.get("attempts").asInt()).isEqualTo(1);
        assertThat(job.get("lastError").asString()).contains("503");
        assertThat(Instant.parse(job.get("nextAttemptAt").asString())).isAfter(Instant.now().plusSeconds(5));
    }

    @Test
    void rateLimitsHonourRetryAfter() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.stubFor(WireMock.put(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "600")));

        post("/api/vehicles/" + fleet.vehicleId() + "/sync", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        JsonNode job = latestJob(fleet, "BOOKING");
        assertThat(job.get("status").asString()).isEqualTo("PENDING");
        assertThat(Instant.parse(job.get("nextAttemptAt").asString()))
                .isAfter(Instant.now().plus(Duration.ofSeconds(590)));
    }

    @Test
    void retriesStopAfterTheAttemptBudget() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.stubFor(WireMock.put(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .willReturn(aResponse().withStatus(500)));
        post("/api/vehicles/" + fleet.vehicleId() + "/sync", fleet.tenant().token(), null).expectStatus(200);

        for (int attempt = 0; attempt < 10; attempt++) {
            jdbc.update("update sync_jobs set next_attempt_at = ? where status = 'PENDING' and organization_id = ?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(1)), fleet.tenant().organizationId());
            syncWorker.runUntilIdle();
        }

        JsonNode job = latestJob(fleet, "BOOKING");
        assertThat(job.get("status").asString()).isEqualTo("FAILED");
        assertThat(job.get("attempts").asInt()).isEqualTo(8);
        assertThat(job.get("lastError").asString()).startsWith("Gave up after 8 attempts");
    }

    @Test
    void rejectedPayloadsFailImmediatelyAndCanBeRetriedManually() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.stubFor(WireMock.put(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .willReturn(aResponse().withStatus(422).withBody("{\"error\":\"acriss_code not allowed\"}")));

        post("/api/vehicles/" + fleet.vehicleId() + "/sync", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        JsonNode job = latestJob(fleet, "BOOKING");
        assertThat(job.get("status").asString()).isEqualTo("FAILED");
        assertThat(job.get("attempts").asInt()).isEqualTo(1);
        JsonNode listings = get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("listings");
        assertThat(listings.get(0).get("status").asString()).isEqualTo("FAILED");
        assertThat(listings.get(0).get("lastError").asString()).contains("acriss_code not allowed");

        FakeChannels.resetToHealthy();
        post("/api/sync-jobs/" + job.get("id").asString() + "/retry", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        assertThat(latestJob(fleet, "BOOKING").get("status").asString()).isEqualTo("SUCCEEDED");
        listings = get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("listings");
        assertThat(listings.get(0).get("status").asString()).isEqualTo("PUBLISHED");
    }

    @Test
    void revokedCredentialsPauseTheChannelUntilFixed() throws Exception {
        Fleet fleet = publishedFleet();
        FakeChannels.BOOKING.stubFor(WireMock.put(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .willReturn(aResponse().withStatus(401)));

        post("/api/vehicles/" + fleet.vehicleId() + "/sync", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        assertThat(latestJob(fleet, "BOOKING").get("status").asString()).isEqualTo("FAILED");
        JsonNode connections = get("/api/connections", fleet.tenant().token()).body();
        assertThat(connections.findValuesAsString("status")).containsExactly("INVALID_CREDENTIALS", "CONNECTED");

        FakeChannels.resetToHealthy();
        post("/api/connections/booking/verify", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        assertThat(get("/api/connections", fleet.tenant().token()).body().findValuesAsString("status"))
                .containsExactly("CONNECTED", "CONNECTED");
        assertThat(latestJob(fleet, "BOOKING").get("trigger").asString()).isEqualTo("CHANNEL_RECONNECTED");
        assertThat(latestJob(fleet, "BOOKING").get("status").asString()).isEqualTo("SUCCEEDED");
    }

    @Test
    void unpublishingTakesTheCarOffBothChannels() throws Exception {
        Fleet fleet = publishedFleet();

        post("/api/vehicles/" + fleet.vehicleId() + "/unpublish", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(WireMock.deleteRequestedFor(urlPathEqualTo(
                "/suppliers/" + fleet.booking().accountId() + "/vehicles/" + fleet.bookingListingId())));
        FakeChannels.RENTALCARS.verify(postRequestedFor(urlPathEqualTo(
                "/v2/suppliers/" + fleet.rentalcars().accountId() + "/fleet/" + fleet.rentalcarsListingId() + "/deactivate")));
        JsonNode vehicle = get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body();
        assertThat(vehicle.get("status").asString()).isEqualTo("DRAFT");
        assertThat(vehicle.get("listings").findValuesAsString("status")).containsOnly("UNPUBLISHED");
    }

    @Test
    void republishingAfterUnpublishPushesEverythingAgain() throws Exception {
        Fleet fleet = publishedFleet();
        post("/api/vehicles/" + fleet.vehicleId() + "/unpublish", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();
        resetRequestLogs();

        post("/api/vehicles/" + fleet.vehicleId() + "/publish", fleet.tenant().token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        FakeChannels.RENTALCARS.verify(exactly(1), putRequestedFor(urlPathMatching(".*/fleet/" + fleet.rentalcarsListingId())));
        FakeChannels.RENTALCARS.verify(exactly(1), putRequestedFor(urlPathMatching(".*/images")));
        FakeChannels.RENTALCARS.verify(exactly(1), putRequestedFor(urlPathMatching(".*/stop-sales")));
        assertThat(get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("listings")
                .findValuesAsString("status")).containsOnly("PUBLISHED");
    }

    @Test
    void lifecycleRulesAreEnforced() throws Exception {
        Fleet fleet = publishedFleet();
        String vehicle = "/api/vehicles/" + fleet.vehicleId();

        Response duplicateReference = post("/api/vehicles", fleet.tenant().token(),
                vehicleRequest(fleet.locationId(), reference(fleet)));
        Response deleteUsedLocation = delete("/api/locations/" + fleet.locationId(), fleet.tenant().token());

        duplicateReference.expectStatus(409);
        assertThat(duplicateReference.code()).isEqualTo("vehicle_reference_taken");
        deleteUsedLocation.expectStatus(409);

        delete(vehicle, fleet.tenant().token()).expectStatus(204);
        syncWorker.runUntilIdle();

        Response editArchived = put(vehicle, fleet.tenant().token(), vehicleRequest(fleet.locationId(), reference(fleet)));
        editArchived.expectStatus(409);
        assertThat(editArchived.code()).isEqualTo("vehicle_archived");
        FakeChannels.BOOKING.verify(WireMock.deleteRequestedFor(urlPathMatching(".*/vehicles/" + fleet.bookingListingId())));
    }

    @Test
    void locationChangesAreRepublished() throws Exception {
        Fleet fleet = publishedFleet();
        resetRequestLogs();

        put("/api/locations/" + fleet.locationId(), fleet.tenant().token(), Map.of(
                "code", "CMN-T2", "name", "Casablanca Airport T2", "addressLine", "Terminal 2",
                "city", "Casablanca", "countryCode", "MA", "iataCode", "CMN")).expectStatus(200);
        syncWorker.runUntilIdle();

        FakeChannels.BOOKING.verify(exactly(1), putRequestedFor(urlPathMatching(".*/vehicles/" + fleet.bookingListingId()))
                .withRequestBody(matchingJsonPath("$.pickup_location.name", equalTo("Casablanca Airport T2"))));
    }

    @Test
    void searchesAndFiltersTheFleet() throws Exception {
        Fleet fleet = publishedFleet();
        createVehicle(fleet.tenant(), fleet.locationId());

        JsonNode active = get("/api/vehicles?status=ACTIVE", fleet.tenant().token()).body();
        JsonNode search = get("/api/vehicles?search=" + reference(fleet).toLowerCase(), fleet.tenant().token()).body();

        assertThat(active.get("totalItems").asLong()).isEqualTo(1);
        assertThat(search.get("items").get(0).get("id").asString()).isEqualTo(fleet.vehicleId());
        assertThat(search.get("items").get(0).get("coverPhotoUrl").asString()).startsWith("https://");
    }

    private String reference(Fleet fleet) throws Exception {
        return get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("reference").asString();
    }

    private JsonNode latestJob(Fleet fleet, String channel) throws Exception {
        return get("/api/sync-jobs?vehicleId=" + fleet.vehicleId() + "&channel=" + channel + "&size=1",
                fleet.tenant().token()).body().get("items").get(0);
    }

    private List<String> jobStatuses(Fleet fleet) throws Exception {
        return get("/api/sync-jobs?vehicleId=" + fleet.vehicleId() + "&size=100", fleet.tenant().token())
                .body().get("items").findValuesAsString("status");
    }

    private static void resetRequestLogs() {
        FakeChannels.BOOKING.resetRequests();
        FakeChannels.RENTALCARS.resetRequests();
    }
}
