package com.brokers.api.listing;

import com.brokers.api.common.BaseEntity;
import com.brokers.channel.core.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A vehicle as published on one channel. The hashes fingerprint what was last pushed so that
 * unchanged data is never sent twice.
 */
@Entity
@Table(name = "channel_listings")
public class ChannelListing extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Column(name = "external_id", length = 120)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "photos_hash", length = 64)
    private String photosHash;

    @Column(name = "availability_hash", length = 64)
    private String availabilityHash;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected ChannelListing() {
    }

    public ChannelListing(UUID organizationId, UUID vehicleId, Channel channel) {
        this.organizationId = organizationId;
        this.vehicleId = vehicleId;
        this.channel = channel;
        this.status = ListingStatus.PENDING;
    }

    public void markPublished(String externalId, String contentHash, Instant now) {
        this.externalId = externalId;
        this.contentHash = contentHash;
        this.status = ListingStatus.PUBLISHED;
        this.lastSyncedAt = now;
        this.lastError = null;
    }

    public void recordPhotos(String photosHash, Instant now) {
        this.photosHash = photosHash;
        this.lastSyncedAt = now;
    }

    public void recordAvailability(String availabilityHash, Instant now) {
        this.availabilityHash = availabilityHash;
        this.lastSyncedAt = now;
    }

    public void markFailed(String error) {
        status = ListingStatus.FAILED;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    /** Off sale. Fingerprints are cleared so a later re-publish pushes everything again. */
    public void markUnpublished(Instant now) {
        status = ListingStatus.UNPUBLISHED;
        lastSyncedAt = now;
        lastError = null;
        clearFingerprints();
    }

    /** The channel no longer knows this listing; the next push creates it again. */
    public void forgetExternalId() {
        externalId = null;
        status = ListingStatus.PENDING;
        clearFingerprints();
    }

    public boolean isPublished() {
        return status == ListingStatus.PUBLISHED && externalId != null;
    }

    private void clearFingerprints() {
        contentHash = null;
        photosHash = null;
        availabilityHash = null;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getExternalId() {
        return externalId;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getPhotosHash() {
        return photosHash;
    }

    public String getAvailabilityHash() {
        return availabilityHash;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
