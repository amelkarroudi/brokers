package com.brokers.api.listing;

import com.brokers.channel.core.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelListingRepository extends JpaRepository<ChannelListing, UUID> {

    List<ChannelListing> findByVehicleIdOrderByChannel(UUID vehicleId);

    Optional<ChannelListing> findByVehicleIdAndChannel(UUID vehicleId, Channel channel);

    Optional<ChannelListing> findByChannelAndExternalId(Channel channel, String externalId);

    @Query("""
            select l.vehicleId from ChannelListing l
            where l.organizationId = :organizationId and l.channel = :channel and l.externalId is not null
              and l.status <> com.brokers.api.listing.ListingStatus.UNPUBLISHED
            """)
    List<UUID> findVehicleIdsOnChannel(UUID organizationId, Channel channel);

    /** Used when an organization switches to a different account on a channel. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ChannelListing l
            set l.externalId = null, l.status = com.brokers.api.listing.ListingStatus.PENDING,
                l.contentHash = null, l.photosHash = null, l.availabilityHash = null, l.version = l.version + 1
            where l.organizationId = :organizationId and l.channel = :channel
            """)
    int resetExternalReferences(UUID organizationId, Channel channel);

    long countByOrganizationIdAndStatus(UUID organizationId, ListingStatus status);
}
