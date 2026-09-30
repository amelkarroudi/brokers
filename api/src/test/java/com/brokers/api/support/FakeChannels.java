package com.brokers.api.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Stand-ins for the real Booking.com and Rentalcars.com APIs, started once for the whole test
 * run so every integration test shares one Spring context.
 */
public final class FakeChannels {

    public static final WireMockServer BOOKING = start();
    public static final WireMockServer RENTALCARS = start();

    private FakeChannels() {
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    /** Happy-path behaviour; tests override individual endpoints as needed. */
    public static void resetToHealthy() {
        BOOKING.resetAll();
        RENTALCARS.resetAll();

        BOOKING.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(204)));
        BOOKING.stubFor(WireMock.get(urlPathMatching("/suppliers/[^/]+")).atPriority(5).willReturn(okJson("{}")));
        BOOKING.stubFor(post(urlPathMatching("/suppliers/[^/]+/vehicles")).atPriority(5)
                .willReturn(okJson("{\"vehicle_id\":\"BK-{{randomValue length=12 type='ALPHANUMERIC'}}\"}")
                        .withStatus(201).withTransformers("response-template")));

        RENTALCARS.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(204)));
        RENTALCARS.stubFor(post("/oauth/token").atPriority(5)
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        RENTALCARS.stubFor(WireMock.get(urlPathMatching("/v2/suppliers/[^/]+")).atPriority(5).willReturn(okJson("{}")));
        RENTALCARS.stubFor(post(urlPathMatching("/v2/suppliers/[^/]+/fleet")).atPriority(5)
                .willReturn(okJson("{\"vehicleId\":\"RC-{{randomValue length=12 type='ALPHANUMERIC'}}\"}")
                        .withStatus(201).withTransformers("response-template")));
    }
}
