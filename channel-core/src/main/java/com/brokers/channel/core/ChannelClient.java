package com.brokers.channel.core;

import com.brokers.channel.core.error.ChannelException;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.VehicleListing;

import java.util.List;

/**
 * Outbound operations every channel integration supports.
 *
 * <p>Every method is expected to be idempotent from the caller's point of view: calling it twice
 * with the same input leaves the channel in the same state. Failures are reported as
 * {@link ChannelException} so callers can decide whether to retry.
 */
public interface ChannelClient {

    Channel channel();

    /** Performs a cheap authenticated call to check that the credentials are valid. */
    void verifyCredentials(ChannelCredentials credentials);

    /** Creates a new listing and returns the identifier the channel assigned to it. */
    String createListing(ChannelCredentials credentials, VehicleListing listing);

    void updateListing(ChannelCredentials credentials, String externalId, VehicleListing listing);

    /** Replaces the full, ordered photo set of a listing. */
    void replacePhotos(ChannelCredentials credentials, String externalId, List<ListingPhoto> photos);

    /** Replaces every blocked period of a listing inside the update's horizon. */
    void replaceAvailability(ChannelCredentials credentials, String externalId, AvailabilityUpdate update);

    /** Takes a listing off sale. Deactivating an already removed listing must not fail. */
    void deactivateListing(ChannelCredentials credentials, String externalId);
}
