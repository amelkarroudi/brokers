package com.brokers.api.sync;

import com.brokers.api.config.BrokersProperties;
import com.brokers.api.connection.ChannelConnection;
import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.connection.ConnectionStatus;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.vehicle.VehicleRepository;
import com.brokers.api.vehicle.VehicleStatus;
import com.brokers.channel.core.Channel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Writes sync jobs in the caller's transaction (transactional outbox): a change and the jobs
 * that propagate it are committed together or not at all.
 *
 * <p>Requests for work that is already pending are merged into the pending job, so a burst of
 * edits results in a single push.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class SyncJobScheduler {

    private final SyncJobRepository jobs;
    private final ChannelConnectionRepository connections;
    private final ChannelListingRepository listings;
    private final VehicleRepository vehicles;
    private final int maxAttempts;
    private final Clock clock;

    public SyncJobScheduler(SyncJobRepository jobs, ChannelConnectionRepository connections,
                            ChannelListingRepository listings, VehicleRepository vehicles,
                            BrokersProperties properties, Clock clock) {
        this.jobs = jobs;
        this.connections = connections;
        this.listings = listings;
        this.vehicles = vehicles;
        this.maxAttempts = properties.sync().maxAttempts();
        this.clock = clock;
    }

    public void enqueueListingUpsert(UUID organizationId, UUID vehicleId, SyncTrigger trigger) {
        connectedChannels(organizationId)
                .forEach(channel -> enqueue(organizationId, vehicleId, channel, SyncJobType.UPSERT_LISTING, trigger, false));
    }

    public void enqueueForcedResync(UUID organizationId, UUID vehicleId, SyncTrigger trigger) {
        connectedChannels(organizationId)
                .forEach(channel -> enqueue(organizationId, vehicleId, channel, SyncJobType.UPSERT_LISTING, trigger, true));
    }

    public void enqueuePhotoSync(UUID organizationId, UUID vehicleId, SyncTrigger trigger) {
        connectedChannels(organizationId)
                .forEach(channel -> enqueue(organizationId, vehicleId, channel, SyncJobType.REPLACE_PHOTOS, trigger, false));
    }

    public void enqueueAvailabilitySync(UUID organizationId, UUID vehicleId, SyncTrigger trigger) {
        connectedChannels(organizationId)
                .forEach(channel -> enqueue(organizationId, vehicleId, channel, SyncJobType.REPLACE_AVAILABILITY, trigger, false));
    }

    /** Deactivates the vehicle on every channel where it currently has a listing. */
    public void enqueueListingDeactivation(UUID organizationId, UUID vehicleId, SyncTrigger trigger) {
        listings.findByVehicleIdOrderByChannel(vehicleId).stream()
                .filter(listing -> listing.getExternalId() != null)
                .forEach(listing -> enqueue(organizationId, vehicleId, listing.getChannel(),
                        SyncJobType.DEACTIVATE_LISTING, trigger, false));
    }

    /** Pushes every published vehicle to a channel, e.g. right after it was connected. */
    public void enqueueChannelResync(UUID organizationId, Channel channel, SyncTrigger trigger) {
        vehicles.findIdsByStatus(organizationId, VehicleStatus.ACTIVE)
                .forEach(vehicleId -> enqueue(organizationId, vehicleId, channel, SyncJobType.UPSERT_LISTING, trigger, true));
    }

    /** Takes every listing off a channel, e.g. when the organization disconnects it. */
    public void enqueueChannelDeactivation(UUID organizationId, Channel channel, SyncTrigger trigger) {
        listings.findVehicleIdsOnChannel(organizationId, channel)
                .forEach(vehicleId -> enqueue(organizationId, vehicleId, channel, SyncJobType.DEACTIVATE_LISTING, trigger, false));
    }

    private void enqueue(UUID organizationId, UUID vehicleId, Channel channel, SyncJobType type,
                         SyncTrigger trigger, boolean force) {
        var pending = jobs.findFirstByVehicleIdAndChannelAndTypeAndStatus(vehicleId, channel, type, SyncJobStatus.PENDING);
        if (pending.isPresent()) {
            pending.get().absorb(force, clock.instant());
            return;
        }
        jobs.save(new SyncJob(organizationId, vehicleId, channel, type, trigger, force, maxAttempts, clock.instant()));
    }

    private List<Channel> connectedChannels(UUID organizationId) {
        return connections.findByOrganizationIdAndStatus(organizationId, ConnectionStatus.CONNECTED).stream()
                .map(ChannelConnection::getChannel)
                .toList();
    }
}
