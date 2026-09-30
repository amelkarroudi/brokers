package com.brokers.channel.rentalcars;

import com.brokers.channel.core.Channel;
import com.brokers.channel.core.ChannelClient;
import com.brokers.channel.core.ChannelCredentials;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import com.brokers.channel.core.http.ChannelHttp;
import com.brokers.channel.core.http.ChannelHttpSettings;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.VehicleListing;
import com.brokers.channel.rentalcars.auth.RentalcarsTokenProvider;
import com.brokers.channel.rentalcars.dto.RcFleetVehicleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

/**
 * Rentalcars.com supplier fleet API client.
 *
 * <p>Authenticates with OAuth2 client credentials. When the API rejects a cached token (it can be
 * revoked before its advertised expiry) the token is dropped and the call is retried exactly once.
 */
public class RentalcarsClient implements ChannelClient {

    private static final Logger log = LoggerFactory.getLogger(RentalcarsClient.class);

    private final RestClient restClient;
    private final RentalcarsTokenProvider tokens;

    public RentalcarsClient(RestClient.Builder builder, ChannelHttpSettings settings, Clock clock) {
        this.restClient = builder.clone()
                .baseUrl(settings.baseUrl().toString())
                .requestFactory(settings.requestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.tokens = new RentalcarsTokenProvider(restClient, clock);
    }

    @Override
    public Channel channel() {
        return Channel.RENTALCARS;
    }

    @Override
    public void verifyCredentials(ChannelCredentials credentials) {
        withToken(credentials, token -> ChannelHttp.call(channel(), "verify credentials", () -> restClient.get()
                .uri("/v2/suppliers/{supplierCode}", credentials.accountId())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity()));
    }

    @Override
    public String createListing(ChannelCredentials credentials, VehicleListing listing) {
        RcFleetVehicleResponse response = withToken(credentials, token -> ChannelHttp.call(channel(), "create listing",
                () -> restClient.post()
                        .uri("/v2/suppliers/{supplierCode}/fleet", credentials.accountId())
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(RentalcarsMapper.toFleetVehicle(listing))
                        .retrieve()
                        .body(RcFleetVehicleResponse.class)));
        if (response == null || response.vehicleId() == null || response.vehicleId().isBlank()) {
            throw new ChannelException(channel(), ChannelErrorKind.UNEXPECTED, "create listing returned no vehicleId");
        }
        log.info("Created Rentalcars.com vehicle {} for reference {}", response.vehicleId(), listing.reference());
        return response.vehicleId();
    }

    @Override
    public void updateListing(ChannelCredentials credentials, String externalId, VehicleListing listing) {
        withToken(credentials, token -> ChannelHttp.call(channel(), "update listing", () -> restClient.put()
                .uri("/v2/suppliers/{supplierCode}/fleet/{vehicleId}", credentials.accountId(), externalId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(RentalcarsMapper.toFleetVehicle(listing))
                .retrieve()
                .toBodilessEntity()));
    }

    @Override
    public void replacePhotos(ChannelCredentials credentials, String externalId, List<ListingPhoto> photos) {
        withToken(credentials, token -> ChannelHttp.call(channel(), "replace photos", () -> restClient.put()
                .uri("/v2/suppliers/{supplierCode}/fleet/{vehicleId}/images", credentials.accountId(), externalId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(RentalcarsMapper.toImages(photos))
                .retrieve()
                .toBodilessEntity()));
    }

    @Override
    public void replaceAvailability(ChannelCredentials credentials, String externalId, AvailabilityUpdate update) {
        withToken(credentials, token -> ChannelHttp.call(channel(), "replace availability", () -> restClient.put()
                .uri("/v2/suppliers/{supplierCode}/fleet/{vehicleId}/stop-sales", credentials.accountId(), externalId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(RentalcarsMapper.toStopSales(update))
                .retrieve()
                .toBodilessEntity()));
    }

    @Override
    public void deactivateListing(ChannelCredentials credentials, String externalId) {
        try {
            withToken(credentials, token -> ChannelHttp.call(channel(), "deactivate listing", () -> restClient.post()
                    .uri("/v2/suppliers/{supplierCode}/fleet/{vehicleId}/deactivate", credentials.accountId(), externalId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity()));
        } catch (ChannelException e) {
            if (e.kind() != ChannelErrorKind.NOT_FOUND) {
                throw e;
            }
            log.info("Rentalcars.com vehicle {} was already removed", externalId);
        }
    }

    private <T> T withToken(ChannelCredentials credentials, Function<String, T> call) {
        try {
            return call.apply(tokens.accessToken(credentials));
        } catch (ChannelException e) {
            if (e.kind() != ChannelErrorKind.AUTHENTICATION) {
                throw e;
            }
            tokens.invalidate(credentials);
            return call.apply(tokens.accessToken(credentials));
        }
    }
}
