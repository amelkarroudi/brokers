package com.brokers.api.sync;

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
 * A unit of outbound work for one vehicle on one channel. Jobs carry no payload: they read the
 * current state when they run, so a late job always pushes the latest data.
 */
@Entity
@Table(name = "sync_jobs")
public class SyncJob extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, updatable = false, length = 30)
    private SyncJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 60)
    private SyncTrigger trigger;

    @Column(nullable = false)
    private boolean force;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SyncJob() {
    }

    public SyncJob(UUID organizationId, UUID vehicleId, Channel channel, SyncJobType type, SyncTrigger trigger,
                   boolean force, int maxAttempts, Instant now) {
        this.organizationId = organizationId;
        this.vehicleId = vehicleId;
        this.channel = channel;
        this.type = type;
        this.trigger = trigger;
        this.force = force;
        this.maxAttempts = maxAttempts;
        this.status = SyncJobStatus.PENDING;
        this.nextAttemptAt = now;
    }

    /** Merges another request for the same work into this pending job. */
    public void absorb(boolean force, Instant now) {
        this.force = this.force || force;
        if (nextAttemptAt.isAfter(now)) {
            nextAttemptAt = now;
        }
    }

    public void succeed(Instant now) {
        status = SyncJobStatus.SUCCEEDED;
        completedAt = now;
        lockedAt = null;
        lastError = null;
    }

    public void retryAt(Instant nextAttempt, String error) {
        status = SyncJobStatus.PENDING;
        nextAttemptAt = nextAttempt;
        lockedAt = null;
        lastError = truncate(error);
    }

    public void fail(String error, Instant now) {
        status = SyncJobStatus.FAILED;
        completedAt = now;
        lockedAt = null;
        lastError = truncate(error);
    }

    /** Manual retry of a failed job with a fresh attempt budget. */
    public void requeue(Instant now) {
        status = SyncJobStatus.PENDING;
        attempts = 0;
        nextAttemptAt = now;
        completedAt = null;
        force = true;
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
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

    public SyncJobType getType() {
        return type;
    }

    public SyncJobStatus getStatus() {
        return status;
    }

    public SyncTrigger getTrigger() {
        return trigger;
    }

    public boolean isForce() {
        return force;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
