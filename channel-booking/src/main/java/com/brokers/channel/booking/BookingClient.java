package com.brokers.channel.booking;

import com.brokers.channel.booking.dto.BookingVehicleResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Booking.com Cars supplier API client.
 *
 * <p>Authentication uses HTTP Basic with the organization's machine account, and every call is
 * scoped to the supplier account in the URL. All payloads are snake_case JSON.
 */
public class BookingClient implements ChannelClient {

    static final String SUPPLIER_HEADER = "X-Booking-Supplier-Id";

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    private final RestClient restClient;

    public BookingClient(RestClient.Builder builder, ChannelHttpSettings settings) {
        this.restClient = builder.clone()
                .baseUrl(settings.baseUrl().toString())
                .requestFactory(settings.requestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Channel channel() {
        return Channel.BOOKING;
    }

    @Override
    public void verifyCredentials(ChannelCredentials credentials) {
        ChannelHttp.run(channel(), "verify credentials", () -> restClient.get()
                .uri("/suppliers/{supplierId}", credentials.accountId())
                .headers(authenticate(credentials))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public String createListing(ChannelCredentials credentials, VehicleListing listing) {
        BookingVehicleResponse response = ChannelHttp.call(channel(), "create listing", () -> restClient.post()
                .uri("/suppliers/{supplierId}/vehicles", credentials.accountId())
                .headers(authenticate(credentials))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingMapper.toVehicleRequest(listing))
                .retrieve()
                .body(BookingVehicleResponse.class));
        if (response == null || response.vehicleId() == null || response.vehicleId().isBlank()) {
            throw new ChannelException(channel(), ChannelErrorKind.UNEXPECTED, "create listing returned no vehicle_id");
        }
        log.info("Created Booking.com vehicle {} for reference {}", response.vehicleId(), listing.reference());
        return response.vehicleId();
    }

    @Override
    public void updateListing(ChannelCredentials credentials, String externalId, VehicleListing listing) {
        ChannelHttp.run(channel(), "update listing", () -> restClient.put()
                .uri("/suppliers/{supplierId}/vehicles/{vehicleId}", credentials.accountId(), externalId)
                .headers(authenticate(credentials))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingMapper.toVehicleRequest(listing))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void replacePhotos(ChannelCredentials credentials, String externalId, List<ListingPhoto> photos) {
        ChannelHttp.run(channel(), "replace photos", () -> restClient.put()
                .uri("/suppliers/{supplierId}/vehicles/{vehicleId}/photos", credentials.accountId(), externalId)
                .headers(authenticate(credentials))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingMapper.toPhotosRequest(photos))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void replaceAvailability(ChannelCredentials credentials, String externalId, AvailabilityUpdate update) {
        ChannelHttp.run(channel(), "replace availability", () -> restClient.put()
                .uri("/suppliers/{supplierId}/vehicles/{vehicleId}/availability", credentials.accountId(), externalId)
                .headers(authenticate(credentials))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingMapper.toAvailabilityRequest(update))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void deactivateListing(ChannelCredentials credentials, String externalId) {
        try {
            ChannelHttp.run(channel(), "deactivate listing", () -> restClient.delete()
                    .uri("/suppliers/{supplierId}/vehicles/{vehicleId}", credentials.accountId(), externalId)
                    .headers(authenticate(credentials))
                    .retrieve()
                    .toBodilessEntity());
        } catch (ChannelException e) {
            if (e.kind() != ChannelErrorKind.NOT_FOUND) {
                throw e;
            }
            log.info("Booking.com vehicle {} was already removed", externalId);
        }
    }

    private static Consumer<HttpHeaders> authenticate(ChannelCredentials credentials) {
        return headers -> {
            headers.setBasicAuth(credentials.apiKey(), credentials.apiSecret());
            headers.set(SUPPLIER_HEADER, credentials.accountId());
        };
    }
}
