package com.brokers.channel.rentalcars;

import com.brokers.channel.core.ChannelCredentials;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import com.brokers.channel.core.http.ChannelHttpSettings;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.AvailabilityWindow;
import com.brokers.channel.core.model.FuelType;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.model.PickupLocation;
import com.brokers.channel.core.model.Transmission;
import com.brokers.channel.core.model.VehicleListing;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalcarsClientTest {

    private static final ChannelCredentials CREDENTIALS = new ChannelCredentials("RC-SUP-7", "client-id", "client-secret");
    private static final String TOKEN_JSON = "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

    @RegisterExtension
    static WireMockExtension rentalcars = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private RentalcarsClient client;

    @BeforeEach
    void setUp() {
        client = new RentalcarsClient(RestClient.builder(), ChannelHttpSettings.of(rentalcars.baseUrl()),
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void obtainsTokenWithClientCredentialsAndCachesIt() {
        rentalcars.stubFor(post("/oauth/token").willReturn(okJson(TOKEN_JSON)));
        rentalcars.stubFor(get("/v2/suppliers/RC-SUP-7").willReturn(okJson("{}")));

        client.verifyCredentials(CREDENTIALS);
        client.verifyCredentials(CREDENTIALS);

        rentalcars.verify(exactly(1), postRequestedFor(urlEqualTo("/oauth/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=client-id"))
                .withRequestBody(containing("scope=supplier%3ARC-SUP-7")));
        rentalcars.verify(exactly(2), getRequestedFor(urlEqualTo("/v2/suppliers/RC-SUP-7"))
                .withHeader("Authorization", equalTo("Bearer tok-1")));
    }

    @Test
    void refreshesTokenOnceWhenApiRejectsIt() {
        rentalcars.stubFor(post("/oauth/token").inScenario("rotation").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(TOKEN_JSON)).willSetStateTo("rotated"));
        rentalcars.stubFor(post("/oauth/token").inScenario("rotation").whenScenarioStateIs("rotated")
                .willReturn(okJson("{\"access_token\":\"tok-2\",\"expires_in\":3600}")));
        rentalcars.stubFor(get("/v2/suppliers/RC-SUP-7").withHeader("Authorization", equalTo("Bearer tok-1"))
                .willReturn(aResponse().withStatus(401)));
        rentalcars.stubFor(get("/v2/suppliers/RC-SUP-7").withHeader("Authorization", equalTo("Bearer tok-2"))
                .willReturn(okJson("{}")));

        assertThatCode(() -> client.verifyCredentials(CREDENTIALS)).doesNotThrowAnyException();
        rentalcars.verify(exactly(2), postRequestedFor(urlEqualTo("/oauth/token")));
    }

    @Test
    void invalidClientCredentialsAreAuthenticationErrors() {
        rentalcars.stubFor(post("/oauth/token").willReturn(aResponse().withStatus(401)
                .withBody("{\"error\":\"invalid_client\"}")));

        assertThatThrownBy(() -> client.verifyCredentials(CREDENTIALS))
                .isInstanceOfSatisfying(ChannelException.class,
                        e -> assertThat(e.kind()).isEqualTo(ChannelErrorKind.AUTHENTICATION));
    }

    @Test
    void createsFleetVehicleWithMinorUnitPrices() {
        rentalcars.stubFor(post("/oauth/token").willReturn(okJson(TOKEN_JSON)));
        rentalcars.stubFor(post("/v2/suppliers/RC-SUP-7/fleet")
                .willReturn(okJson("{\"vehicleId\":\"RC-555\",\"state\":\"ACTIVE\"}").withStatus(201)));

        String externalId = client.createListing(CREDENTIALS, listing());

        assertThat(externalId).isEqualTo("RC-555");
        rentalcars.verify(postRequestedFor(urlEqualTo("/v2/suppliers/RC-SUP-7/fleet"))
                .withRequestBody(matchingJsonPath("$.supplierVehicleCode", equalTo("FLEET-002")))
                .withRequestBody(matchingJsonPath("$.sippCode", equalTo("CDAR")))
                .withRequestBody(matchingJsonPath("$.vehicle.makeModel", equalTo("Dacia Duster")))
                .withRequestBody(matchingJsonPath("$.vehicle.gearbox", equalTo("AUTOMATIC")))
                .withRequestBody(matchingJsonPath("$.terms.mileagePolicy", equalTo("LIMITED")))
                .withRequestBody(matchingJsonPath("$.terms.includedKmPerDay", equalTo("250")))
                .withRequestBody(matchingJsonPath("$.dailyRate.valueMinor", equalTo("5550")))
                .withRequestBody(matchingJsonPath("$.station.airportCode", equalTo("RAK"))));
    }

    @Test
    void replacesImagesWithPrimaryFirst() {
        rentalcars.stubFor(post("/oauth/token").willReturn(okJson(TOKEN_JSON)));
        rentalcars.stubFor(put("/v2/suppliers/RC-SUP-7/fleet/RC-555/images").willReturn(aResponse().withStatus(204)));

        client.replacePhotos(CREDENTIALS, "RC-555", List.of(
                new ListingPhoto("https://cdn.example.com/b.jpg", 3, null),
                new ListingPhoto("https://cdn.example.com/a.jpg", 1, "Front")));

        rentalcars.verify(putRequestedFor(urlEqualTo("/v2/suppliers/RC-SUP-7/fleet/RC-555/images"))
                .withRequestBody(equalToJson("""
                        {"images":[
                          {"url":"https://cdn.example.com/a.jpg","order":1,"primary":true,"altText":"Front"},
                          {"url":"https://cdn.example.com/b.jpg","order":2,"primary":false}
                        ]}""")));
    }

    @Test
    void pushesBlockedPeriodsAsStopSales() {
        rentalcars.stubFor(post("/oauth/token").willReturn(okJson(TOKEN_JSON)));
        rentalcars.stubFor(put("/v2/suppliers/RC-SUP-7/fleet/RC-555/stop-sales").willReturn(aResponse().withStatus(204)));
        Instant start = Instant.parse("2026-10-01T00:00:00Z");

        client.replaceAvailability(CREDENTIALS, "RC-555", new AvailabilityUpdate(start, start.plus(Duration.ofDays(30)),
                List.of(new AvailabilityWindow(Instant.parse("2026-10-03T00:00:00Z"), Instant.parse("2026-10-04T00:00:00Z")))));

        rentalcars.verify(putRequestedFor(urlEqualTo("/v2/suppliers/RC-SUP-7/fleet/RC-555/stop-sales"))
                .withRequestBody(equalToJson("""
                        {"window":{"start":"2026-10-01T00:00:00Z","end":"2026-10-31T00:00:00Z"},
                         "stopSales":[{"start":"2026-10-03T00:00:00Z","end":"2026-10-04T00:00:00Z"}]}""")));
    }

    @Test
    void rateLimitIsRetryable() {
        rentalcars.stubFor(post("/oauth/token").willReturn(okJson(TOKEN_JSON)));
        rentalcars.stubFor(put("/v2/suppliers/RC-SUP-7/fleet/RC-555")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "15")));

        assertThatThrownBy(() -> client.updateListing(CREDENTIALS, "RC-555", listing()))
                .isInstanceOfSatisfying(ChannelException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ChannelErrorKind.RATE_LIMITED);
                    assertThat(e.retryAfter()).contains(Duration.ofSeconds(15));
                });
    }

    @Test
    void deactivatingAMissingVehicleSucceeds() {
        rentalcars.stubFor(post("/oauth/token").willReturn(okJson(TOKEN_JSON)));
        rentalcars.stubFor(post("/v2/suppliers/RC-SUP-7/fleet/RC-404/deactivate").willReturn(aResponse().withStatus(404)));

        assertThatCode(() -> client.deactivateListing(CREDENTIALS, "RC-404")).doesNotThrowAnyException();
    }

    private static VehicleListing listing() {
        return new VehicleListing(
                "FLEET-002", "Dacia", "Duster", 2025, "CDAR",
                Transmission.AUTOMATIC, FuelType.DIESEL,
                5, 5, 3, true, 250, 23,
                Money.of("55.50", "EUR"), Money.of("1000", "EUR"),
                new PickupLocation("RAK-APT", "Marrakech Menara Airport", "Route de l'Aéroport", "Marrakech",
                        "40000", "MA", null, null, "RAK"));
    }
}
