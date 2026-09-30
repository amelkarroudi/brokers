package com.brokers.channel.booking;

import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import com.brokers.channel.core.http.ChannelHttpSettings;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.AvailabilityWindow;
import com.brokers.channel.core.model.ListingPhoto;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.brokers.channel.booking.BookingFixtures.CREDENTIALS;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
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

class BookingClientTest {

    @RegisterExtension
    static WireMockExtension booking = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private BookingClient client;

    @BeforeEach
    void setUp() {
        client = new BookingClient(RestClient.builder(), ChannelHttpSettings.of(booking.baseUrl()));
    }

    @Test
    void verifiesCredentialsWithBasicAuthAndSupplierHeader() {
        booking.stubFor(get("/suppliers/SUP-42").willReturn(okJson("{\"supplier_id\":\"SUP-42\"}")));

        client.verifyCredentials(CREDENTIALS);

        booking.verify(getRequestedFor(urlEqualTo("/suppliers/SUP-42"))
                .withBasicAuth(new BasicCredentials("machine-user", "s3cret"))
                .withHeader(BookingClient.SUPPLIER_HEADER, equalTo("SUP-42")));
    }

    @Test
    void rejectedCredentialsAreAuthenticationErrors() {
        booking.stubFor(get("/suppliers/SUP-42").willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.verifyCredentials(CREDENTIALS))
                .isInstanceOfSatisfying(ChannelException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ChannelErrorKind.AUTHENTICATION);
                    assertThat(e.retryable()).isFalse();
                });
    }

    @Test
    void createsListingInSnakeCaseAndReturnsVehicleId() {
        booking.stubFor(post("/suppliers/SUP-42/vehicles")
                .willReturn(okJson("{\"vehicle_id\":\"BK-9001\",\"status\":\"pending_review\"}").withStatus(201)));

        String externalId = client.createListing(CREDENTIALS, BookingFixtures.listing());

        assertThat(externalId).isEqualTo("BK-9001");
        booking.verify(postRequestedFor(urlEqualTo("/suppliers/SUP-42/vehicles"))
                .withRequestBody(matchingJsonPath("$.supplier_reference", equalTo("FLEET-001")))
                .withRequestBody(matchingJsonPath("$.title", equalTo("Peugeot 208 2024")))
                .withRequestBody(matchingJsonPath("$.acriss_code", equalTo("EDMR")))
                .withRequestBody(matchingJsonPath("$.transmission", equalTo("manual")))
                .withRequestBody(matchingJsonPath("$.fuel_type", equalTo("petrol")))
                .withRequestBody(matchingJsonPath("$.mileage.unlimited", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.price_per_day.amount", equalTo("39.9")))
                .withRequestBody(matchingJsonPath("$.price_per_day.currency", equalTo("EUR")))
                .withRequestBody(matchingJsonPath("$.pickup_location.iata", equalTo("CMN"))));
    }

    @Test
    void createWithoutVehicleIdIsUnexpected() {
        booking.stubFor(post("/suppliers/SUP-42/vehicles").willReturn(okJson("{}")));

        assertThatThrownBy(() -> client.createListing(CREDENTIALS, BookingFixtures.listing()))
                .isInstanceOfSatisfying(ChannelException.class,
                        e -> assertThat(e.kind()).isEqualTo(ChannelErrorKind.UNEXPECTED));
    }

    @Test
    void validationErrorsAreNotRetryable() {
        booking.stubFor(put("/suppliers/SUP-42/vehicles/BK-1")
                .willReturn(aResponse().withStatus(422).withBody("{\"error\":\"acriss_code invalid\"}")));

        assertThatThrownBy(() -> client.updateListing(CREDENTIALS, "BK-1", BookingFixtures.listing()))
                .isInstanceOfSatisfying(ChannelException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ChannelErrorKind.VALIDATION);
                    assertThat(e.getMessage()).contains("acriss_code invalid");
                });
    }

    @Test
    void serverErrorsAreRetryableAndCarryRetryAfter() {
        booking.stubFor(put("/suppliers/SUP-42/vehicles/BK-1")
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "120")));

        assertThatThrownBy(() -> client.updateListing(CREDENTIALS, "BK-1", BookingFixtures.listing()))
                .isInstanceOfSatisfying(ChannelException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ChannelErrorKind.UNAVAILABLE);
                    assertThat(e.retryAfter()).contains(Duration.ofMinutes(2));
                });
    }

    @Test
    void replacesPhotosInDisplayOrderMarkingTheCover() {
        booking.stubFor(put("/suppliers/SUP-42/vehicles/BK-1/photos").willReturn(aResponse().withStatus(204)));

        client.replacePhotos(CREDENTIALS, "BK-1", List.of(
                new ListingPhoto("https://cdn.example.com/side.jpg", 1, "Side"),
                new ListingPhoto("https://cdn.example.com/front.jpg", 0, "Front")));

        booking.verify(putRequestedFor(urlEqualTo("/suppliers/SUP-42/vehicles/BK-1/photos"))
                .withRequestBody(equalToJson("""
                        {"photos":[
                          {"url":"https://cdn.example.com/front.jpg","sort_order":0,"is_main":true,"caption":"Front"},
                          {"url":"https://cdn.example.com/side.jpg","sort_order":1,"is_main":false,"caption":"Side"}
                        ]}""")));
    }

    @Test
    void replacesAvailabilityWithClosedPeriods() {
        booking.stubFor(put("/suppliers/SUP-42/vehicles/BK-1/availability").willReturn(aResponse().withStatus(204)));
        Instant start = Instant.parse("2026-10-01T00:00:00Z");

        client.replaceAvailability(CREDENTIALS, "BK-1", new AvailabilityUpdate(start, start.plus(Duration.ofDays(365)),
                List.of(new AvailabilityWindow(Instant.parse("2026-10-05T10:00:00Z"), Instant.parse("2026-10-08T10:00:00Z")))));

        booking.verify(putRequestedFor(urlEqualTo("/suppliers/SUP-42/vehicles/BK-1/availability"))
                .withRequestBody(equalToJson("""
                        {"from":"2026-10-01T00:00:00Z","to":"2027-10-01T00:00:00Z",
                         "closed_periods":[{"start":"2026-10-05T10:00:00Z","end":"2026-10-08T10:00:00Z"}]}""")));
    }

    @Test
    void deactivatingAMissingListingSucceeds() {
        booking.stubFor(delete("/suppliers/SUP-42/vehicles/BK-GONE").willReturn(aResponse().withStatus(404)));

        assertThatCode(() -> client.deactivateListing(CREDENTIALS, "BK-GONE")).doesNotThrowAnyException();
        booking.verify(deleteRequestedFor(urlEqualTo("/suppliers/SUP-42/vehicles/BK-GONE")));
    }

    @Test
    void networkFailuresAreRetryable() {
        BookingClient unreachable = new BookingClient(RestClient.builder(), ChannelHttpSettings.of("http://127.0.0.1:1"));

        assertThatThrownBy(() -> unreachable.verifyCredentials(CREDENTIALS))
                .isInstanceOfSatisfying(ChannelException.class, e -> assertThat(e.retryable()).isTrue());
    }
}
