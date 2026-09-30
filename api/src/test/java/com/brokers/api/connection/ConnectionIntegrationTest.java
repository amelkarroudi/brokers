package com.brokers.api.connection;

import com.brokers.api.support.FakeChannels;
import com.brokers.api.support.IntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

class ConnectionIntegrationTest extends IntegrationTest {

    @Test
    void connectsAndNeverExposesSecrets() throws Exception {
        Tenant tenant = signup();

        Response response = put("/api/connections/booking", tenant.token(), Map.of(
                "accountId", unique("SUP"), "apiKey", "machine-user-1234", "apiSecret", "top-secret-value"));

        response.expectStatus(200);
        assertThat(response.body().at("/connection/status").asString()).isEqualTo("CONNECTED");
        assertThat(response.body().at("/connection/apiKeyHint").asString()).isEqualTo("****1234");
        assertThat(response.body().at("/connection/webhookUrl").asString())
                .startsWith("https://brokers.test/api/webhooks/booking/");
        assertThat(response.body().get("webhookSecret").asString()).startsWith("whsec_");
        assertThat(response.body().toString()).doesNotContain("top-secret-value");
    }

    @Test
    void storesCredentialsEncrypted() throws Exception {
        Tenant tenant = signup();
        Connection connection = connect(tenant, "booking");

        Map<String, Object> row = jdbc.queryForMap(
                "select api_key_encrypted, api_secret_encrypted, webhook_secret_encrypted from channel_connections where id = ?",
                connection.id());

        assertThat(row.values()).allSatisfy(value -> assertThat(value.toString()).startsWith("v1:"));
        assertThat(row.values().toString()).doesNotContain("api-secret").doesNotContain(connection.webhookSecret());
    }

    @Test
    void listsEveryChannelIncludingUnconnectedOnes() throws Exception {
        Tenant tenant = signup();
        connect(tenant, "rentalcars");

        Response response = get("/api/connections", tenant.token()).expectStatus(200);

        assertThat(response.body().findValuesAsString("status")).containsExactly("NOT_CONNECTED", "CONNECTED");
    }

    @Test
    void rejectedCredentialsAreNotStored() throws Exception {
        Tenant tenant = signup();
        FakeChannels.RENTALCARS.stubFor(WireMock.post("/oauth/token").willReturn(aResponse().withStatus(401)));

        Response response = put("/api/connections/rentalcars", tenant.token(), Map.of(
                "accountId", unique("RC"), "apiKey", "client", "apiSecret", "wrong"));

        response.expectStatus(422);
        assertThat(response.code()).isEqualTo("invalid_channel_credentials");
        assertThat(get("/api/connections", tenant.token()).body().findValuesAsString("status"))
                .containsOnly("NOT_CONNECTED");
    }

    @Test
    void unreachableChannelIsABadGateway() throws Exception {
        Tenant tenant = signup();
        FakeChannels.BOOKING.stubFor(WireMock.get(urlPathMatching("/suppliers/.*")).willReturn(aResponse().withStatus(503)));

        Response response = put("/api/connections/booking", tenant.token(), Map.of(
                "accountId", unique("SUP"), "apiKey", "k", "apiSecret", "s"));

        response.expectStatus(502);
        assertThat(response.code()).isEqualTo("channel_unavailable");
    }

    @Test
    void anAccountCanOnlyBelongToOneOrganization() throws Exception {
        Tenant first = signup();
        Connection connection = connect(first, "booking");
        Tenant second = signup();

        Response response = put("/api/connections/booking", second.token(), Map.of(
                "accountId", connection.accountId(), "apiKey", "k", "apiSecret", "s"));

        response.expectStatus(409);
        assertThat(response.code()).isEqualTo("account_already_connected");
    }

    @Test
    void reconnectingKeepsTheWebhookSecret() throws Exception {
        Tenant tenant = signup();
        Connection connection = connect(tenant, "booking");

        Response again = put("/api/connections/booking", tenant.token(), Map.of(
                "accountId", connection.accountId(), "apiKey", "rotated-key", "apiSecret", "rotated-secret"));

        again.expectStatus(200);
        assertThat(again.body().has("webhookSecret")).isFalse();
        assertThat(again.body().at("/connection/connectionId").asString()).isEqualTo(connection.id().toString());
    }

    @Test
    void acceptsAWebhookSecretFromTheChannelExtranet() throws Exception {
        Tenant tenant = signup();

        Response response = put("/api/connections/booking", tenant.token(), Map.of(
                "accountId", unique("SUP"), "apiKey", "k", "apiSecret", "s",
                "webhookSecret", "secret-from-the-extranet-123"));

        response.expectStatus(200);
        assertThat(response.body().has("webhookSecret")).isFalse();
    }

    @Test
    void rotatesTheWebhookSecret() throws Exception {
        Tenant tenant = signup();
        Connection connection = connect(tenant, "booking");

        Response rotated = post("/api/connections/booking/webhook-secret", tenant.token(), null).expectStatus(200);

        assertThat(rotated.body().get("webhookSecret").asString()).isNotEqualTo(connection.webhookSecret());
    }

    @Test
    void acceptsLowerAndUpperCaseChannelNames() throws Exception {
        Tenant tenant = signup();
        connect(tenant, "booking");

        post("/api/connections/BOOKING/verify", tenant.token(), null).expectStatus(200);
        post("/api/connections/unknown-channel/verify", tenant.token(), null).expectStatus(400);
    }

    @Test
    void connectingAChannelPublishesTheExistingFleet() throws Exception {
        Tenant tenant = signup();
        connect(tenant, "booking");
        String location = createLocation(tenant);
        String vehicle = createVehicle(tenant, location);
        addPhoto(tenant, vehicle, "https://cdn.example.com/a.jpg");
        post("/api/vehicles/" + vehicle + "/publish", tenant.token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        Connection rentalcars = connect(tenant, "rentalcars");
        syncWorker.runUntilIdle();

        FakeChannels.RENTALCARS.verify(postRequestedFor(
                urlPathMatching("/v2/suppliers/" + rentalcars.accountId() + "/fleet")));
        assertThat(externalId(get("/api/vehicles/" + vehicle, tenant.token()).body().get("listings"), "RENTALCARS"))
                .startsWith("RC-");
    }

    @Test
    void disconnectingTakesListingsOffTheChannel() throws Exception {
        Fleet fleet = publishedFleet();

        Response response = delete("/api/connections/booking", fleet.tenant().token()).expectStatus(200);
        syncWorker.runUntilIdle();

        assertThat(response.body().get("status").asString()).isEqualTo("DISCONNECTED");
        FakeChannels.BOOKING.verify(deleteRequestedFor(
                urlPathMatching("/suppliers/" + fleet.booking().accountId() + "/vehicles/" + fleet.bookingListingId())));
        var listings = get("/api/vehicles/" + fleet.vehicleId(), fleet.tenant().token()).body().get("listings");
        assertThat(listings.findValuesAsString("status")).containsExactly("UNPUBLISHED", "PUBLISHED");
    }
}
