package com.brokers.api.sync;

import com.brokers.api.availability.AvailabilityService;
import com.brokers.api.config.ChannelRegistry;
import com.brokers.api.connection.ChannelConnection;
import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.connection.ConnectionService;
import com.brokers.api.connection.ConnectionStatus;
import com.brokers.api.listing.ChannelListing;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.listing.ListingStatus;
import com.brokers.api.location.Location;
import com.brokers.api.location.LocationRepository;
import com.brokers.api.vehicle.Vehicle;
import com.brokers.api.vehicle.VehicleListings;
import com.brokers.api.vehicle.VehicleRepository;
import com.brokers.channel.core.ChannelClient;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs one sync job against a channel. Jobs are state based: they compare the current desired
 * state with what the listing's fingerprints say was last pushed, and only send the difference.
 * That makes every job safe to run late, twice, or out of order.
 */
@Component
class SyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncExecutor.class);

    private final SyncJobRepository jobs;
    private final ChannelConnectionRepository connections;
    private final ConnectionService connectionService;
    private final VehicleRepository vehicles;
    private final LocationRepository locations;
    private final ChannelListingRepository listings;
    private final AvailabilityService availabilityService;
    private final ChannelRegistry registry;
    private final TransactionTemplate transactions;
    private final Clock clock;

    SyncExecutor(SyncJobRepository jobs, ChannelConnectionRepository connections, ConnectionService connectionService,
                 VehicleRepository vehicles, LocationRepository locations, ChannelListingRepository listings,
                 AvailabilityService availabilityService, ChannelRegistry registry, TransactionTemplate transactions,
                 Clock clock) {
        this.jobs = jobs;
        this.connections = connections;
        this.connectionService = connectionService;
        this.vehicles = vehicles;
        this.locations = locations;
        this.listings = listings;
        this.availabilityService = availabilityService;
        this.registry = registry;
        this.transactions = transactions;
        this.clock = clock;
    }

    void execute(UUID jobId) {
        SyncSnapshot snapshot = transactions.execute(status -> load(jobId));
        SyncJob job = snapshot.job();
        ChannelClient client = registry.client(job.getChannel());
        switch (job.getType()) {
            case UPSERT_LISTING -> upsert(client, snapshot);
            case REPLACE_PHOTOS -> replacePhotos(client, snapshot);
            case REPLACE_AVAILABILITY -> replaceAvailability(client, snapshot);
            case DEACTIVATE_LISTING -> deactivate(client, snapshot);
        }
    }

    private void upsert(ChannelClient client, SyncSnapshot snapshot) {
        if (!snapshot.vehicleActive()) {
            log.debug("Skipping upsert of vehicle {}: no longer published", snapshot.job().getVehicleId());
            return;
        }

        ChannelListing listing = snapshot.listing();
        String contentHash = Fingerprints.of(snapshot.content());
        boolean contentChanged = !listing.isPublished() || !contentHash.equals(listing.getContentHash());
        Pushed pushed;
        if (listing.getExternalId() == null) {
            pushed = create(client, snapshot, contentHash);
        } else if (snapshot.job().isForce() || contentChanged) {
            pushed = update(client, snapshot, listing.getExternalId(), contentHash);
        } else {
            pushed = new Pushed(listing.getExternalId(), false);
        }

        // A listing that was just created has no photos or availability on the channel yet.
        boolean pushEverything = snapshot.job().isForce() || pushed.created();
        pushPhotos(client, snapshot, pushed.externalId(), pushEverything);
        pushAvailability(client, snapshot, pushed.externalId(), pushEverything);
    }

    private Pushed create(ChannelClient client, SyncSnapshot snapshot, String contentHash) {
        String externalId = client.createListing(snapshot.credentials(), snapshot.content());
        // Recorded immediately so a failure in a later step never leads to a duplicate listing.
        recordListing(snapshot, listing -> listing.markPublished(externalId, contentHash, clock.instant()));
        return new Pushed(externalId, true);
    }

    private Pushed update(ChannelClient client, SyncSnapshot snapshot, String externalId, String contentHash) {
        try {
            client.updateListing(snapshot.credentials(), externalId, snapshot.content());
            recordListing(snapshot, listing -> listing.markPublished(externalId, contentHash, clock.instant()));
            return new Pushed(externalId, false);
        } catch (ChannelException e) {
            if (e.kind() != ChannelErrorKind.NOT_FOUND) {
                throw e;
            }
            log.info("Listing {} disappeared from {}; creating it again", externalId, snapshot.job().getChannel());
            recordListing(snapshot, ChannelListing::forgetExternalId);
            return create(client, snapshot, contentHash);
        }
    }

    private void replacePhotos(ChannelClient client, SyncSnapshot snapshot) {
        if (!snapshot.vehicleActive() || !snapshot.listing().isPublished()) {
            return;
        }
        pushPhotos(client, snapshot, snapshot.listing().getExternalId(), snapshot.job().isForce());
    }

    private void replaceAvailability(ChannelClient client, SyncSnapshot snapshot) {
        if (!snapshot.vehicleActive() || !snapshot.listing().isPublished()) {
            return;
        }
        pushAvailability(client, snapshot, snapshot.listing().getExternalId(), snapshot.job().isForce());
    }

    private void pushPhotos(ChannelClient client, SyncSnapshot snapshot, String externalId, boolean force) {
        String hash = Fingerprints.ofPhotos(snapshot.photos());
        if (!force && hash.equals(snapshot.listing().getPhotosHash())) {
            return;
        }
        client.replacePhotos(snapshot.credentials(), externalId, snapshot.photos());
        recordListing(snapshot, listing -> listing.recordPhotos(hash, clock.instant()));
    }

    private void pushAvailability(ChannelClient client, SyncSnapshot snapshot, String externalId, boolean force) {
        String hash = Fingerprints.ofAvailability(snapshot.availability());
        if (!force && hash.equals(snapshot.listing().getAvailabilityHash())) {
            return;
        }
        client.replaceAvailability(snapshot.credentials(), externalId, snapshot.availability());
        recordListing(snapshot, listing -> listing.recordAvailability(hash, clock.instant()));
    }

    private void deactivate(ChannelClient client, SyncSnapshot snapshot) {
        if (snapshot.vehicleActive() && snapshot.channelConnected()) {
            log.debug("Skipping deactivation of vehicle {}: it was published again", snapshot.job().getVehicleId());
            return;
        }
        ChannelListing listing = snapshot.listing();
        if (listing.getExternalId() == null || listing.getStatus() == ListingStatus.UNPUBLISHED) {
            return;
        }
        client.deactivateListing(snapshot.credentials(), listing.getExternalId());
        recordListing(snapshot, current -> current.markUnpublished(clock.instant()));
    }

    private void recordListing(SyncSnapshot snapshot, Consumer<ChannelListing> change) {
        transactions.executeWithoutResult(status -> {
            ChannelListing listing = listings.findById(snapshot.listing().getId()).orElseThrow();
            change.accept(listing);
        });
    }

    /** Identifier of the listing on the channel, and whether this job created it. */
    private record Pushed(String externalId, boolean created) {
    }

    private SyncSnapshot load(UUID jobId) {
        SyncJob job = jobs.findById(jobId).orElseThrow();
        ChannelConnection connection = connections.findByOrganizationIdAndChannel(job.getOrganizationId(), job.getChannel())
                .orElseThrow(() -> new SyncAbortedException(job.getChannel() + " is not connected"));
        boolean deactivation = job.getType() == SyncJobType.DEACTIVATE_LISTING;
        if (connection.getStatus() == ConnectionStatus.INVALID_CREDENTIALS) {
            throw new SyncAbortedException(job.getChannel() + " credentials are invalid; update the connection");
        }
        if (!connection.isConnected() && !deactivation) {
            throw new SyncAbortedException(job.getChannel() + " is disconnected");
        }

        Vehicle vehicle = vehicles.findById(job.getVehicleId()).orElseThrow(() -> new SyncAbortedException("Vehicle was deleted"));
        Location location = locations.findById(vehicle.getLocationId()).orElseThrow();
        ChannelListing listing = listings.findByVehicleIdAndChannel(vehicle.getId(), job.getChannel())
                .orElseGet(() -> listings.save(new ChannelListing(vehicle.getOrganizationId(), vehicle.getId(), job.getChannel())));

        return new SyncSnapshot(
                job,
                connectionService.credentials(connection),
                connection.isConnected(),
                vehicle.isActive(),
                listing,
                VehicleListings.toListing(vehicle, location),
                VehicleListings.toPhotos(vehicle),
                availabilityService.currentUpdate(vehicle.getId()));
    }
}
