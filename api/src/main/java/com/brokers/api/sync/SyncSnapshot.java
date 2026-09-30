package com.brokers.api.sync;

import com.brokers.api.listing.ChannelListing;
import com.brokers.channel.core.ChannelCredentials;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.VehicleListing;

import java.util.List;

/**
 * Everything a job needs, read in one short transaction so that no database transaction stays
 * open while the channel is being called.
 */
record SyncSnapshot(
        SyncJob job,
        ChannelCredentials credentials,
        boolean channelConnected,
        boolean vehicleActive,
        ChannelListing listing,
        VehicleListing content,
        List<ListingPhoto> photos,
        AvailabilityUpdate availability) {
}
