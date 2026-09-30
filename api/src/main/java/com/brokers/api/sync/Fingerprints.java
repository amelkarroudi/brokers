package com.brokers.api.sync;

import com.brokers.api.common.Hashing;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.VehicleListing;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stable hashes of what is pushed to a channel. Records have deterministic {@code toString}
 * output and {@code Money} normalizes its scale, so equal data always hashes equally.
 */
final class Fingerprints {

    private Fingerprints() {
    }

    static String of(VehicleListing listing) {
        return Hashing.sha256Hex(listing.toString());
    }

    static String ofPhotos(List<ListingPhoto> photos) {
        return Hashing.sha256Hex(photos.stream().map(ListingPhoto::toString).collect(Collectors.joining("\n")));
    }

    /** Only the blocked periods are hashed; the horizon moves every day and alone is no reason to push. */
    static String ofAvailability(AvailabilityUpdate update) {
        return Hashing.sha256Hex(update.blocked().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }
}
